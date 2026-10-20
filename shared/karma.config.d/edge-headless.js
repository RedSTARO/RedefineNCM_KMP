// Edge as CHROME_BIN (a Windows machine without Chrome): msedge.exe can relaunch itself through
// its compatibility layer and exit at once, leaving the real browser running as its child. Karma
// then reports "Cannot start ChromeHeadless", retries with the same profile, and the retries open
// further tabs in the browser already running, which run the suite at the same time over the same
// origin storage: OPFS tests then fail at random, and the browsers outlive the run. Asking Edge
// not to relaunch keeps one process that Karma started and can stop.
if (/msedge/i.test(process.env.CHROME_BIN || '')) {
    config.customLaunchers = Object.assign({}, config.customLaunchers, {
        EdgeHeadlessNoRelaunch: {
            base: 'ChromeHeadless',
            flags: ['--edge-skip-compat-layer-relaunch'],
        },
    });
    config.browsers = (config.browsers || []).map(browser =>
        browser === 'ChromeHeadless' ? 'EdgeHeadlessNoRelaunch' : browser
    );
}
