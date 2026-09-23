"""Converts the export-friendly Beat This! model for the phone runtimes.

    python convert_mobile.py --beat-this beat_this --checkpoint small0.ckpt \
        --tflite beat_this_small0_t750.tflite --coreml BeatThisSmall0.mlpackage

Needs Linux or macOS: litert-torch and coremltools do not convert on Windows. Both models keep
float32 weights; the accelerators choose their own compute precision (the Neural Engine and
LiteRT's GPU backend run float16), which is why the float16 check below exists.
"""
import argparse

import numpy as np
import torch

from export_beat_this import load


def check_float16(exported, probe):
    """Accelerators run this model in float16; make sure nothing overflows or drifts badly."""
    with torch.inference_mode():
        ref = exported(probe).float()
        half = exported.half()(probe.half()).float()
    exported.float()
    finite = bool(torch.isfinite(half).all())
    diff = (ref - half).abs().max().item()
    agree = ((ref > 0) == (half > 0)).float().mean().item()
    print(f"float16: finite={finite} max |diff|={diff:.3f} sign agreement={agree:.5f}")
    if not finite:
        raise SystemExit("the model overflows in float16")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--beat-this", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--frames", type=int, default=750)
    parser.add_argument("--tflite")
    parser.add_argument("--coreml")
    args = parser.parse_args()
    exported, probe = load(args.beat_this, args.checkpoint, args.frames)
    check_float16(exported, probe)

    if args.tflite:
        import litert_torch

        edge = litert_torch.convert(exported, (probe,))
        with torch.inference_mode():
            ref = exported(probe).numpy()
        out = np.asarray(edge(probe))
        print(f"tflite (CPU interpreter) max |diff| = {np.abs(out - ref).max():.2e}")
        edge.export(args.tflite)
        print(f"wrote {args.tflite}")

    if args.coreml:
        import coremltools as ct

        traced = torch.jit.trace(exported, probe)
        model = ct.convert(
            traced,
            inputs=[ct.TensorType(name="spectrogram", shape=tuple(probe.shape), dtype=np.float32)],
            outputs=[ct.TensorType(name="logits", dtype=np.float32)],
            convert_to="mlprogram",
            minimum_deployment_target=ct.target.iOS16,
            compute_precision=ct.precision.FLOAT16,
        )
        model.short_description = "Beat This! small0 (MIT, CPJKU): beat and downbeat logits"
        model.save(args.coreml)
        print(f"wrote {args.coreml}")


if __name__ == "__main__":
    main()
