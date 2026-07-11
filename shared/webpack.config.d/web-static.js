config.output = config.output || {};
config.output.publicPath = "auto";

if (config.devServer) {
    config.devServer.historyApiFallback = true;
}
