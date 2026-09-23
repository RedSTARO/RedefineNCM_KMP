"""Exports Beat This! (small0) for the NPU and GPU runtimes smart transitions use.

The model in the upstream repository runs fine in PyTorch, but its graph is hostile to
accelerator compilers: einops rearranges produce rank-5 tensors, rotary embeddings are
computed on the fly, batch norms are separate ops and the time axis is dynamic. This file
re-implements the forward pass with the checkpoint's own weights so that

  * no tensor has more than four dimensions,
  * rotary cos/sin tables are constants and rotate_half is a constant matmul,
  * batch norms are folded into the convolutions that precede or follow them,
  * the input is a static [1, T, 128] (T = 750 frames, 15 s at 50 fps),

and checks that it agrees with the original to about 1e-5 before writing anything.

Output: one [1, T, 2] tensor of logits, beat then downbeat, with the model's SumHead applied
(beat = beat + downbeat), which is what beat_this.model.postprocessor expects.

Usage (see README.md for the environment):
    python export_beat_this.py --beat-this path/to/beat_this --checkpoint small0.ckpt --onnx out.onnx
"""
import argparse
import sys

import torch
from torch import nn


def bn_affine(bn):
    scale = bn.weight / torch.sqrt(bn.running_var + bn.eps)
    shift = bn.bias - bn.running_mean * scale
    return scale.detach(), shift.detach()


def fold_conv_bn(conv, bn):
    scale, shift = bn_affine(bn)
    folded = nn.Conv2d(conv.in_channels, conv.out_channels, conv.kernel_size, conv.stride, conv.padding, bias=True)
    folded.weight.data.copy_(conv.weight.detach() * scale[:, None, None, None])
    folded.bias.data.copy_(shift)
    return folded


def rotate_half_matrix(d):
    """rotary_embedding_torch.rotate_half for interleaved pairs, as x @ R."""
    r = torch.zeros(d, d)
    for i in range(0, d, 2):
        r[i + 1, i] = -1.0  # y[i]   = -x[i+1]
        r[i, i + 1] = 1.0   # y[i+1] =  x[i]
    return r


class RMSNorm(nn.Module):
    def __init__(self, orig):
        super().__init__()
        self.register_buffer("g", (orig.gamma.detach() * orig.scale).reshape(-1))

    def forward(self, x):
        n = torch.sqrt(torch.sum(x * x, dim=-1, keepdim=True))
        return x / torch.clamp(n, min=1e-12) * self.g


class Gelu(nn.Module):
    def forward(self, x):
        return 0.5 * x * (1.0 + torch.erf(x * 0.7071067811865476))


class FeedForward(nn.Module):
    def __init__(self, orig):
        super().__init__()
        net = orig.net
        self.norm = RMSNorm(net[0])
        self.l1 = net[1]
        self.l2 = net[4]
        self.act = Gelu()

    def forward(self, x):
        return self.l2(self.act(self.l1(self.norm(x))))


class Attention(nn.Module):
    """(N, L, C) -> (N, L, C), rotary positions 0..L-1 for a fixed L."""

    def __init__(self, orig, length):
        super().__init__()
        self.h = orig.heads
        self.norm = RMSNorm(orig.norm)
        self.qkv = orig.to_qkv
        self.gates = orig.to_gates
        self.out = orig.to_out[0]
        self.d = self.qkv.out_features // 3 // self.h
        freqs = orig.rotary_embed.freqs.detach().float()
        pos = torch.arange(length, dtype=torch.float32)
        f = torch.einsum("n,f->nf", pos, freqs).repeat_interleave(2, dim=-1)
        self.register_buffer("cos", f.cos())
        self.register_buffer("sin", f.sin())
        self.register_buffer("rot", rotate_half_matrix(self.d))
        self.scale = self.d ** -0.5

    def rope(self, t):
        return t * self.cos + torch.matmul(t, self.rot) * self.sin

    def forward(self, x):
        n, length, _ = x.shape
        x = self.norm(x)
        qkv = self.qkv(x)
        hd = self.h * self.d

        def heads(t):
            return t.reshape(n, length, self.h, self.d).permute(0, 2, 1, 3)

        q = heads(qkv[..., 0:hd])
        k = heads(qkv[..., hd:2 * hd])
        v = heads(qkv[..., 2 * hd:3 * hd])
        q = self.rope(q) * self.scale
        k = self.rope(k)
        a = torch.softmax(torch.matmul(q, k.transpose(2, 3)), dim=-1)
        o = torch.matmul(a, v)
        g = torch.sigmoid(self.gates(x)).permute(0, 2, 1).reshape(n, self.h, length, 1)
        o = (o * g).permute(0, 2, 1, 3).reshape(n, length, hd)
        return self.out(o)


class PartialFT(nn.Module):
    def __init__(self, orig, freqs, frames):
        super().__init__()
        self.attn_f = Attention(orig.attnF, freqs)
        self.ff_f = FeedForward(orig.ffF)
        self.attn_t = Attention(orig.attnT, frames)
        self.ff_t = FeedForward(orig.ffT)

    def forward(self, x):  # (1, c, f, t)
        b, c, f, t = x.shape
        x = x.permute(0, 3, 2, 1).reshape(b * t, f, c)
        x = x + self.attn_f(x)
        x = x + self.ff_f(x)
        x = x.reshape(b, t, f, c).permute(0, 2, 1, 3).reshape(b * f, t, c)
        x = x + self.attn_t(x)
        x = x + self.ff_t(x)
        return x.reshape(b, f, t, c).permute(0, 3, 1, 2)


class BeatThisExport(nn.Module):
    def __init__(self, model, frames):
        super().__init__()
        stem = model.frontend.stem
        scale, shift = bn_affine(stem.bn1d)
        self.register_buffer("in_scale", scale)
        self.register_buffer("in_shift", shift)
        self.stem_conv = fold_conv_bn(stem.conv2d, stem.bn2d)
        self.act = Gelu()
        blocks = []
        freqs = 128 // 4  # the stem convolves 128 mel bins with stride 4
        for block in model.frontend.blocks:
            blocks.append(nn.ModuleDict(dict(
                partial=PartialFT(block.partial, freqs, frames),
                conv=fold_conv_bn(block.conv2d, block.norm),
            )))
            freqs //= 2
        self.blocks = nn.ModuleList(blocks)
        self.proj = model.frontend.linear
        layers = model.transformer_blocks
        self.layers = nn.ModuleList([nn.ModuleList([Attention(a, frames), FeedForward(f)]) for a, f in layers.layers])
        self.norm = RMSNorm(layers.norm)
        self.head = model.task_heads.beat_downbeat_lin

    def forward(self, spectrogram):  # (1, T, 128)
        x = spectrogram * self.in_scale + self.in_shift
        x = x.permute(0, 2, 1).unsqueeze(1)
        x = self.act(self.stem_conv(x))
        for block in self.blocks:
            x = block["partial"](x)
            x = self.act(block["conv"](x))
        b, c, f, t = x.shape
        x = self.proj(x.permute(0, 3, 1, 2).reshape(b, t, c * f))
        for attn, ff in self.layers:
            x = attn(x) + x
            x = ff(x) + x
        y = self.head(self.norm(x))
        return torch.cat([y[..., 0:1] + y[..., 1:2], y[..., 1:2]], dim=-1)


def load(beat_this_dir, checkpoint, frames):
    sys.path.insert(0, beat_this_dir)
    from beat_this.inference import load_model  # noqa: E402

    original = load_model(checkpoint).eval()
    exported = BeatThisExport(original, frames).eval()
    torch.manual_seed(0)
    probe = torch.randn(1, frames, 128).abs() * 3
    with torch.inference_mode():
        ref = original(probe)
        out = exported(probe)
    diff = max(
        (ref["beat"] - out[..., 0]).abs().max().item(),
        (ref["downbeat"] - out[..., 1]).abs().max().item(),
    )
    print(f"export parity: max |diff| = {diff:.2e}")
    if diff > 1e-4:
        raise SystemExit("export does not match the original model")
    return exported, probe


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--beat-this", required=True, help="checkout of github.com/CPJKU/beat_this")
    parser.add_argument("--checkpoint", required=True, help="small0.ckpt")
    parser.add_argument("--frames", type=int, default=750)
    parser.add_argument("--onnx", help="write an ONNX model here")
    args = parser.parse_args()
    exported, probe = load(args.beat_this, args.checkpoint, args.frames)
    if args.onnx:
        torch.onnx.export(
            exported, (probe,), args.onnx,
            input_names=["spectrogram"], output_names=["logits"],
            opset_version=17, dynamo=False, do_constant_folding=True,
        )
        print(f"wrote {args.onnx}")


if __name__ == "__main__":
    main()
