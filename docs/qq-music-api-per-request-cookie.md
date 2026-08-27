# Making `Rain120/qq-music-api` accept a per-request cookie

The app holds the QQ Music cookie and sends it as a `Cookie` header on every request. This backend
does not read it. Applying the change below makes the header take effect; without it, QQ requests
are anonymous no matter what the app has stored.

This is **not applied** to any deployment — it forks a third-party repository, and a later
`git pull` will conflict. Apply it only if you want app-side login to work.

## Why the header is ignored today

Four independent reasons, all in the upstream source:

1. `src/controllers/cookies.ts` — `set` returns `403` unconditionally:
   `'Setting cookie dynamically is disabled for security reasons.'` So `/user/setCookie` cannot
   install a credential.
2. `src/util/cookie.ts` — the middleware only copies `userInfo.cookie` (from the config file) onto
   the request. It never reads the incoming `Cookie` header.
3. `src/util/request.ts` — the outbound axios call never sends a `Cookie` header upstream at all.
   The credential is used only to derive `uin` / `loginUin` into query parameters.
4. `src/config/index.ts` — `export const userInfo = appConfig.user` captures a **reference**, while
   `ConfigManager.updateConfig()` assigns a brand-new config object. So re-enabling `setCookie`
   alone would log a successful update and change nothing the controllers read.

Point 4 is the trap: the obvious one-line fix (delete the 403) appears to work and does nothing.

## The change

Read the caller's cookie per request, and mutate `userInfo` in place rather than replacing it.

### 1. `src/util/cookie.ts` — honour the incoming header

```ts
export default () => async (ctx: Context, next: Next) => {
  // A caller-supplied cookie wins over the config file, so one backend can serve several clients
  // and so a client with no filesystem access (Android) can sign in at all.
  const incoming = ctx.request.headers.cookie;
  const effective = (typeof incoming === 'string' && incoming.trim()) || userInfo.cookie;

  if (effective) {
    (ctx.request as unknown as { cookie: string }).cookie = effective;

    // Mutate in place: `userInfo` is a captured reference (src/config/index.ts), so reassigning
    // the config object would leave every controller reading the old snapshot.
    const list = effective.split(';').map((c) => c.trim()).filter(Boolean);
    const obj: Record<string, string> = {};
    list.forEach((c) => {
      const at = c.indexOf('=');
      if (at > 0) obj[c.slice(0, at).trim()] = c.slice(at + 1).trim();
    });
    userInfo.cookie = effective;
    userInfo.cookieList = list;
    userInfo.cookieObject = obj;
    userInfo.uin = obj.uin || obj.wxuin || '';
  }

  await next();
};
```

### 2. Confirm the middleware actually runs before the controllers

`src/app.ts` must `app.use(cookieMiddleware())` ahead of `app.use(router.routes())`. Check the
order; if the cookie middleware is registered after the router, move it up.

## Caveats worth knowing before applying

- **This makes the backend stateful per request in a global.** `userInfo` is process-wide, so two
  clients sending different cookies concurrently can interleave. Fine for a single-user local
  deployment; not fine for a shared one. A correct multi-user version would thread the credential
  through the request rather than through `userInfo`, which is a much larger change.
- **Do not expose this backend beyond localhost after applying it.** Upstream disabled dynamic
  cookie setting deliberately; accepting a caller's credential is safe on a loopback dev service
  and is not safe on a reachable one.
- **A cookie is not sufficient for VIP tracks.** The gate is account entitlement checked at
  `vkey.GetVkeyServer`. A non-VIP account will still resolve empty for VIP songs at every quality;
  what the cookie should unlock is the `320`/`flac` tiers and personal playlists.

## Getting the cookie

Sign in at `y.qq.com` in a browser, then copy the `Cookie` header of any request to that origin
from DevTools → Network. The `uin` and `qm_keyst` pairs are the ones that matter. Paste it into
设置 → 多平台 → QQ 音乐 Cookie.

The app stores it like the NetEase cookie: obscured in the field, and deliberately excluded from
settings export/import so a shared backup never carries a credential.
