# Beat model for smart transitions

Smart song transitions find beats and downbeats with
[Beat This!](https://github.com/CPJKU/beat_this) (Foscarin, Schlüter, Widmer, ISMIR 2024).
The code and weights are MIT-licensed. The app ships the `small0` checkpoint: 2.1 M parameters,
about 8 MB. The scripts here turn that checkpoint into the files each platform runtime loads.
They are a reproducible record of how those files were made. Nothing in the Gradle build runs
them.

The Android and desktop apps do not ship these files. They download theirs the first time smart
transitions need the model, from this repository at the commit pinned in
`shared/src/commonMain/kotlin/com/leejlredstar/redefinencm/kmp/transition/BeatModelDownloads.kt`:
jsDelivr's CDNs first, then GitHub's raw file host. A download is kept only when its size and
SHA-256 match the pinned values. To ship a new model, commit it and update that file;
`BeatModelDownloadsTest` fails until the two agree. The web app serves the ONNX file with its own
pages.

| Platform | Runtime | File | Made by |
| --- | --- | --- | --- |
| Desktop (Windows, macOS), Web | ONNX Runtime (DirectML / Core ML), ONNX Runtime Web (WebNN / WebGPU) | `beat_this_small0_t750.onnx` | `export_beat_this.py --onnx` |
| Android | LiteRT `CompiledModel` (NPU, then GPU) | `beat_this_small0_t750.tflite` | `convert_mobile.py --tflite`, then `litert_gpu_rewrite.py` |
| iOS | Core ML (Neural Engine, then GPU) | `BeatThisSmall0.mlpackage` | `convert_mobile.py --coreml` |

## Why a re-implementation

The upstream graph is built for training and for PyTorch. Accelerator compilers handle it
poorly: einops rearranges create rank-5 tensors, rotary tables are computed per call, batch
norms are separate ops, and the time axis is dynamic. `export_beat_this.py` rebuilds the forward
pass from the checkpoint's own weights with these changes:

- No tensor has more than four dimensions.
- Rotary cos/sin tables are constants, and `rotate_half` is a matmul with a constant ±1 matrix.
- Batch norms are folded into the neighbouring convolutions or into the input scale.
- The input is a static `[1, 750, 128]` log-mel spectrogram, 15 s at 50 frames per second.
- No size is read from a tensor at run time. Every reshape uses sizes fixed when the model is
  built, because a traced `x.shape` becomes a shape computation that Core ML's converter cannot
  lower.

Before writing any file, the script checks the rebuilt model against the original. The largest
difference is 2.6e-6.

Why 750 frames and not the model's native 1500: the frontend's time attention is quadratic in
the frame count. At 1500 frames its score matrices need 288 MB per block in fp32, which is too
much for a phone NPU. On eight tracks from a real library, beats from 750-frame chunks agreed
with beats from 1500-frame chunks at a mean F-measure of about 0.94, with a 70 ms tolerance.
The downbeat F-measure was 0.82 to 1.0.

## Input and output

- **Input** `spectrogram`, float32 `[1, 750, 128]`. This is `beat_this.preprocessing.LogMelSpect`:
  22 050 Hz mono, `n_fft` 1024, hop 441, Hann window, 128 Slaney mel bins from 30 Hz to 11 kHz,
  magnitude with frame-length normalisation, then `log1p(1000·x)`. The Kotlin implementation is
  `SpectralAnalyzer`. `SpectralFeaturesTest` pins it against values from torchaudio.
- **Output** `logits`, float32 `[1, 750, 2]`, holding the beat logit and then the downbeat logit
  for each frame. The model's sum head is already applied. `runBeatModel` in `BeatTracking.kt`
  splits and re-joins chunks the same way `beat_this.inference.split_predict_aggregate` does.

## Verified

- **Float16.** NPUs and mobile GPUs run the model in float16. On 15 s windows of three real
  tracks the float16 model stayed finite. Its largest logit difference from float32 was 0.08,
  and the sign of 99.93 % or more of the frames agreed. The sign is what peak picking uses.
- **LiteRT on a GPU.** The converted `.tflite` matches the PyTorch model to 8.3e-6 on LiteRT's
  CPU interpreter, but LiteRT's GPU backend does not implement two of its ops:
  `L2_NORMALIZATION`, which the converter makes of every RMSNorm, and `BROADCAST_TO`, which it
  makes of the rotary `rotate_half` matmul on rank-4 tensors. LiteRT does not refuse such a model;
  it runs the unsupported ops on the CPU and splits the graph around them. LiteRT 2.2.0's GPU
  accelerator reported 67 of the 1066 ops on the GPU. `litert_gpu_rewrite.py` replaces each
  `L2_NORMALIZATION` with `MUL`, `SUM`, `MAXIMUM`, `RSQRT` and `MUL`, which compute the same value,
  and gives each such `BATCH_MATMUL` the constant matrix itself, which the GPU backend runs as a
  1x1 convolution. `check_litert_gpu.py` then loaded the result on LiteRT 2.2.0's GPU accelerator
  (WebGPU on Direct3D 12, Radeon RX 7900 XT). LiteRT reported it fully accelerated, at 2.5 ms per
  15 s chunk. On the CPU the rewritten model differs from the converted one by at most 3.3e-6.
  Between GPU and CPU the largest logit difference was 0.35 (float16), and the sign of every
  frame agreed. It has not run on a phone here; a phone's LiteRT uses OpenCL or OpenGL ES instead
  of WebGPU, and its op support comes from the same GPU delegate code.
- **LiteRT's model analyzer cannot be used for this check.** `ModelAnalyzer(..., gpu_compatibility=True)`
  in `ai-edge-litert` 2.2.0 reports every model as compatible: its per-node GPU check is gone, and
  the list of incompatible nodes it prints is always empty.
- **No NPU on Android yet.** LiteRT reaches a phone NPU through the SoC vendor's dispatch and
  compiler-plugin libraries, which the app does not bundle. The app tries the NPU first and falls
  back to the GPU.
- **Core ML.** The `.mlpackage` converts as an ML program with float16 compute precision for
  iOS 16. It has not run here, because Core ML needs macOS or iOS.
- **ONNX on DirectML with CPU fallback disabled.** The session was created with
  `session.disable_cpu_ep_fallback=1`, so creation would have failed if any node had been
  placed on the CPU. It ran on a Radeon RX 7900 XT at about 10 ms per 15 s chunk.
- **The desktop runtime path gives the same result.** That path is ONNX Runtime Java with the
  MIT-licensed DirectML build of `onnxruntime.dll`, running on the DirectML that Windows itself
  provides.
- **A real blend.** `SmartTransitionHarnessTest` analysed a local library on that GPU, planned a
  beat-matched hand-over from a 128 BPM track to a 126 BPM one, and rendered it through the
  desktop mixer. The reference PyTorch tracker, run on the rendered file, found steady beats
  through the blend: a mean interval of 473 ms with a 12 ms spread, against 10 ms from frame
  quantisation alone, and no doubled beats.

## Reproducing

The ONNX export runs anywhere torch does. The phone conversions need Linux or macOS (this was
done in WSL); see the docstring of `convert_mobile.py`.

```sh
git clone https://github.com/CPJKU/beat_this
curl -LO https://cloud.cp.jku.at/public.php/dav/files/7ik4RrBKTS273gp/small0.ckpt
# sha256 6074be2c4d490c5f6101fcc374a1ec72ae93456e23bb6019783b849f5dc7d47b
uv venv --python 3.12 && source .venv/bin/activate
uv pip install --index-url https://download.pytorch.org/whl/cpu torch torchaudio
uv pip install einops rotary-embedding-torch soxr numpy onnx
python export_beat_this.py --beat-this beat_this --checkpoint small0.ckpt --onnx beat_this_small0_t750.onnx
```

The Android model is the converted `.tflite` rewritten for LiteRT's GPU backend. Both scripts
run on Windows too, with `ai-edge-litert` 2.2.0 and `flatbuffers` installed:

```sh
python litert_gpu_rewrite.py beat_this_small0_t750.tflite out.tflite
python check_litert_gpu.py out.tflite --reference beat_this_small0_t750.tflite
```

On Windows, LiteRT's GPU accelerator is WebGPU on Direct3D 12 through Dawn. Dawn loads
`dxil.dll` and `dxcompiler.dll` from the accelerator's own directory
(`site-packages/ai_edge_litert`), so copy them there first; the Windows SDK has both under
`bin/<version>/x64`.
