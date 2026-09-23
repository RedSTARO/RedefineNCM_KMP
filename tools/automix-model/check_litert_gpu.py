"""Runs a TFLite beat model on LiteRT's GPU accelerator alone and checks it against the CPU.

    python check_litert_gpu.py out.tflite [--reference original.tflite]

Fails unless LiteRT reports the model fully accelerated, which is the property the Android app
relies on: LiteRT quietly runs any op its GPU backend lacks on the CPU instead. With
--reference, also checks that the model computes what the original did.

On Windows, LiteRT's GPU accelerator is WebGPU on Direct3D 12 through Dawn, which loads the DXC
libraries dxil.dll and dxcompiler.dll from the accelerator's own directory
(site-packages/ai_edge_litert). Copy them there from the Windows SDK's bin/x64 directory.
"""
import argparse
import time

import numpy as np
from ai_edge_litert.compiled_model import CompiledModel
from ai_edge_litert.hardware_accelerator import HardwareAccelerator

FRAMES, MELS = 750, 128


def run(path, accelerator, x):
    model = CompiledModel.from_file(path, hardware_accel=accelerator)
    inputs = model.create_input_buffers(0)
    outputs = model.create_output_buffers(0)
    inputs[0].write(x)
    model.run_by_index(0, inputs, outputs)
    start = time.perf_counter()
    for _ in range(5):
        model.run_by_index(0, inputs, outputs)
    ms = (time.perf_counter() - start) / 5 * 1000
    y = np.asarray(outputs[0].read(FRAMES * 2, np.float32)).reshape(FRAMES, 2)
    return model.is_fully_accelerated(), ms, y


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("--reference")
    args = parser.parse_args()
    x = (np.abs(np.random.default_rng(0).standard_normal((1, FRAMES, MELS))) * 3).astype(np.float32)

    _, cpu_ms, cpu = run(args.model, HardwareAccelerator.CPU, x)
    print(f"CPU: {cpu_ms:.1f} ms per chunk")
    if args.reference:
        _, _, ref = run(args.reference, HardwareAccelerator.CPU, x)
        print(f"against the reference on the CPU: max |diff| = {np.abs(cpu - ref).max():.2e}")
        if np.abs(cpu - ref).max() > 1e-3:
            raise SystemExit("the model no longer computes what the reference does")

    full, gpu_ms, gpu = run(args.model, HardwareAccelerator.GPU, x)
    agreement = ((gpu > 0) == (cpu > 0)).mean()
    print(f"GPU: fully accelerated = {full}, {gpu_ms:.1f} ms per chunk, "
          f"max |gpu - cpu| = {np.abs(gpu - cpu).max():.3f}, sign agreement = {agreement:.5f}")
    if not full:
        raise SystemExit("some ops of this model run on the CPU")


if __name__ == "__main__":
    main()
