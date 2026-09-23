# Beat model for smart transitions

Smart song transitions find beats and downbeats with
[Beat This!](https://github.com/CPJKU/beat_this) (Foscarin, Schlüter, Widmer, ISMIR 2024).
The code and weights are MIT-licensed. The app ships the `small0` checkpoint: 2.1 M parameters,
about 8 MB. The scripts here turn that checkpoint into the files each platform runtime loads.
They are a reproducible record of how those files were made. Nothing in the Gradle build runs
them.

| Platform | Runtime | File | Made by |
| --- | --- | --- | --- |
| Desktop (Windows, macOS), Web | ONNX Runtime (DirectML / Core ML), ONNX Runtime Web (WebNN / WebGPU) | `beat_this_small0_t750.onnx` | `export_beat_this.py --onnx` |
| Android | LiteRT `CompiledModel` (NPU, then GPU) | `beat_this_small0_t750.tflite` | `convert_tflite.py` |
| iOS | Core ML (Neural Engine, then GPU) | `BeatThisSmall0.mlpackage` | `convert_coreml.py` |

## Why a re-implementation

The upstream graph is built for training and for PyTorch. Accelerator compilers handle it
poorly: einops rearranges create rank-5 tensors, rotary tables are computed per call, batch
norms are separate ops, and the time axis is dynamic. `export_beat_this.py` rebuilds the forward
pass from the checkpoint's own weights with these changes:

- No tensor has more than four dimensions.
- Rotary cos/sin tables are constants, and `rotate_half` is a matmul with a constant ±1 matrix.
- Batch norms are folded into the neighbouring convolutions or into the input scale.
- The input is a static `[1, 750, 128]` log-mel spectrogram, 15 s at 50 frames per second.

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

- **ONNX on DirectML with CPU fallback disabled.** The session was created with
  `session.disable_cpu_ep_fallback=1`, so creation would have failed if any node had been
  placed on the CPU. It ran on a Radeon RX 7900 XT at about 10 ms per 15 s chunk.
- **The desktop runtime path gives the same result.** That path is ONNX Runtime Java with the
  MIT-licensed DirectML build of `onnxruntime.dll`, running on the DirectML that Windows itself
  provides.

## Reproducing

```sh
git clone https://github.com/CPJKU/beat_this
curl -LO https://cloud.cp.jku.at/public.php/dav/files/7ik4RrBKTS273gp/small0.ckpt
# sha256 6074be2c4d490c5f6101fcc374a1ec72ae93456e23bb6019783b849f5dc7d47b
uv venv --python 3.12 && source .venv/bin/activate
uv pip install --index-url https://download.pytorch.org/whl/cpu torch torchaudio
uv pip install einops rotary-embedding-torch soxr numpy onnx
python export_beat_this.py --beat-this beat_this --checkpoint small0.ckpt --onnx beat_this_small0_t750.onnx
```
