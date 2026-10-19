// Run the actual inline boot code and markup in the test browser, with controlled fetches.
const fs = require('fs');
const path = require('path');
let root = config.basePath;
while (!fs.existsSync(path.join(root, 'shared/src/wasmJsMain/resources/index.html'))) {
    const parent = path.dirname(root);
    if (parent === root) throw new Error('Cannot locate the web boot page');
    root = parent;
}
const html = fs.readFileSync(path.join(root, 'shared/src/wasmJsMain/resources/index.html'), 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const markup = html.match(/<body>([\s\S]*?)<script>/)[1];
const fixture = path.join(config.basePath, 'web-boot-under-test.js');
fs.writeFileSync(fixture,
    'globalThis.__redefineNcmBootMarkup = ' + JSON.stringify(markup) + ';\n' +
    'globalThis.__initializeBootUnderTest = () => {\n' + source + '\n};\n');
config.files.unshift({pattern: fixture, included: true, served: true, watched: false});
