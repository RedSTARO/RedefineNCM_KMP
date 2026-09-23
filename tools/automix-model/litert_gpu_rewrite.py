"""Rewrites the converted TFLite model so that LiteRT's GPU backend runs every op of it.

litert-torch lowers two constructs of export_beat_this.py to ops the GPU backend does not
implement:

  * RMSNorm, x / max(|x|, eps), becomes L2_NORMALIZATION;
  * a rank-4 tensor times the constant rotate-half matrix becomes BROADCAST_TO of the matrix,
    a RESHAPE and a BATCH_MATMUL.

Either one splits the graph into GPU partitions with CPU islands between them; LiteRT 2.2.0
reports 67 of the 1066 ops on the GPU for the unmodified model. This script replaces each
L2_NORMALIZATION with MUL, SUM, MAXIMUM, RSQRT and MUL, which compute the same value, and hands
each such BATCH_MATMUL the constant matrix itself, which the GPU backend runs as a 1x1
convolution and the CPU kernels broadcast.

    python litert_gpu_rewrite.py beat_this_small0_t750.tflite out.tflite
    python check_litert_gpu.py out.tflite --reference beat_this_small0_t750.tflite

Needs ai-edge-litert (for the schema) and flatbuffers; runs on Windows too.
"""
import argparse

import flatbuffers
import numpy as np
from ai_edge_litert import schema_py_generated as schema

Op = schema.BuiltinOperator
Options = schema.BuiltinOptions

# TFLite's L2_NORMALIZATION kernel divides by max(norm, 1e-6); squared, that is 1e-12.
L2_EPSILON_SQUARED = 1e-12


class Graph:
    def __init__(self, model):
        self.model = model
        self.subgraph = model.subgraphs[0]

    def opcode(self, builtin):
        for index, code in enumerate(self.model.operatorCodes):
            if max(code.builtinCode, code.deprecatedBuiltinCode) == builtin:
                return index
        code = schema.OperatorCodeT()
        code.builtinCode = builtin
        code.deprecatedBuiltinCode = min(builtin, 127)
        code.version = 1
        self.model.operatorCodes.append(code)
        return len(self.model.operatorCodes) - 1

    def builtin(self, op):
        code = self.model.operatorCodes[op.opcodeIndex]
        return max(code.builtinCode, code.deprecatedBuiltinCode)

    def tensor(self, name, shape, dtype=schema.TensorType.FLOAT32, data=None):
        buffer = schema.BufferT()
        if data is not None:
            buffer.data = np.frombuffer(np.ascontiguousarray(data).tobytes(), dtype=np.uint8)
        self.model.buffers.append(buffer)
        tensor = schema.TensorT()
        tensor.name = name
        tensor.shape = np.array(shape, dtype=np.int32)
        tensor.type = dtype
        tensor.buffer = len(self.model.buffers) - 1
        self.subgraph.tensors.append(tensor)
        return len(self.subgraph.tensors) - 1

    def operator(self, builtin, inputs, outputs, options_type=Options.NONE, options=None):
        op = schema.OperatorT()
        op.opcodeIndex = self.opcode(builtin)
        op.inputs = np.array(inputs, dtype=np.int32)
        op.outputs = np.array(outputs, dtype=np.int32)
        op.builtinOptionsType = options_type
        op.builtinOptions = options
        return op

    def is_constant(self, index):
        buffer = self.model.buffers[self.subgraph.tensors[index].buffer]
        return buffer.data is not None and len(buffer.data) > 0

    def consumers(self, index):
        return [op for op in self.subgraph.operators if op.inputs is not None and index in list(op.inputs)]


def replace_l2_normalization(graph):
    """x -> x * rsqrt(max(sum(x * x, last axis), eps^2))."""
    replaced = 0
    operators = []
    for op in graph.subgraph.operators:
        if graph.builtin(op) != Op.L2_NORMALIZATION:
            operators.append(op)
            continue
        x, y = int(op.inputs[0]), int(op.outputs[0])
        shape = [int(d) for d in graph.subgraph.tensors[x].shape]
        reduced = shape[:-1] + [1]
        tag = f"l2norm{replaced}"
        squares = graph.tensor(f"{tag}/squares", shape)
        axis = graph.tensor(f"{tag}/axis", [1], schema.TensorType.INT32, np.array([len(shape) - 1], dtype=np.int32))
        total = graph.tensor(f"{tag}/sum", reduced)
        epsilon = graph.tensor(f"{tag}/epsilon", [1], data=np.array([L2_EPSILON_SQUARED], dtype=np.float32))
        floored = graph.tensor(f"{tag}/floored", reduced)
        inverse = graph.tensor(f"{tag}/rsqrt", reduced)
        reducer = schema.ReducerOptionsT()
        reducer.keepDims = True
        operators += [
            graph.operator(Op.MUL, [x, x], [squares], Options.MulOptions, schema.MulOptionsT()),
            graph.operator(Op.SUM, [squares, axis], [total], Options.ReducerOptions, reducer),
            graph.operator(Op.MAXIMUM, [total, epsilon], [floored], Options.MaximumMinimumOptions,
                           schema.MaximumMinimumOptionsT()),
            graph.operator(Op.RSQRT, [floored], [inverse]),
            graph.operator(Op.MUL, [x, inverse], [y], Options.MulOptions, schema.MulOptionsT()),
        ]
        replaced += 1
    graph.subgraph.operators = operators
    return replaced


def unbroadcast_matmul_constants(graph):
    """BROADCAST_TO(C) -> RESHAPE -> BATCH_MATMUL(x, .)  becomes  BATCH_MATMUL(x, C)."""
    removed = set()
    rewired = 0
    for op in graph.subgraph.operators:
        if graph.builtin(op) != Op.BROADCAST_TO:
            continue
        constant, broadcast = int(op.inputs[0]), int(op.outputs[0])
        if not graph.is_constant(constant) or len(graph.subgraph.tensors[constant].shape) != 2:
            raise SystemExit(f"BROADCAST_TO of a non-constant or non-matrix tensor T#{constant}")
        chain = [op]
        feed = broadcast
        users = graph.consumers(feed)
        while len(users) == 1 and graph.builtin(users[0]) == Op.RESHAPE:
            chain.append(users[0])
            feed = int(users[0].outputs[0])
            users = graph.consumers(feed)
        # The query and the key share one broadcast matrix, so it can feed several products.
        if not users or any(
            graph.builtin(user) != Op.BATCH_MATMUL or int(user.inputs[1]) != feed or int(user.inputs[0]) == feed
            for user in users
        ):
            raise SystemExit(f"BROADCAST_TO T#{broadcast} does not only feed BATCH_MATMUL second inputs")
        for matmul in users:
            options = matmul.builtinOptions
            if options is not None and (options.adjX or options.adjY):
                raise SystemExit("an adjoint BATCH_MATMUL is not rewritten")
            inputs = list(matmul.inputs)
            inputs[1] = constant
            matmul.inputs = np.array(inputs, dtype=np.int32)
            rewired += 1
        removed.update(id(o) for o in chain)
    graph.subgraph.operators = [op for op in graph.subgraph.operators if id(op) not in removed]
    return rewired


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    args = parser.parse_args()
    with open(args.source, "rb") as f:
        model = schema.ModelT.InitFromPackedBuf(f.read(), 0)
    if len(model.subgraphs) != 1:
        raise SystemExit("expected one subgraph")
    graph = Graph(model)
    norms = replace_l2_normalization(graph)
    matmuls = unbroadcast_matmul_constants(graph)
    builder = flatbuffers.Builder(16 * 1024 * 1024)
    builder.Finish(model.Pack(builder), file_identifier=b"TFL3")
    with open(args.target, "wb") as f:
        f.write(builder.Output())
    print(f"replaced {norms} L2_NORMALIZATION, rewired {matmuls} broadcast BATCH_MATMUL; "
          f"{len(graph.subgraph.operators)} ops -> {args.target}")


if __name__ == "__main__":
    main()
