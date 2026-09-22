# Running the QQ Music gateway (`L-1124/QQMusicApi` web)

The app's QQ Music provider talks to the FastAPI gateway that ships inside
[L-1124/QQMusicApi](https://github.com/L-1124/QQMusicApi) under `web/`. It replaced the earlier
`Rain120/qq-music-api` deployment on 2026-09-22: it computes the real request signature, decrypts
QRC lyrics, logs in by QR code, renews credentials, and reads the caller's account from request
cookies, which is the transport this app already used.

## Deploy (Windows, native)

Python ≥ 3.10 and `uv`:

```bash
git clone https://github.com/L-1124/QQMusicApi.git E:/Repo/QQMusicApi
cd E:/Repo/QQMusicApi
uv sync --group web --no-dev
uv run --no-sync web/run.py
```

It listens on `http://127.0.0.1:8080`; `/swagger` and `/openapi.json` describe every route. Docker
and WSL recipes are in its `web/README.md`. Point 设置 → 多平台 → QQ 音乐后端地址 at it. The
app's default is `http://localhost:8080`; an install that saved the old `http://localhost:3200`
explicitly needs the field changed. Unlike the WSL-hosted predecessor this answers on the IPv4
loopback, so `localhost` works from the JVM. An Android device reaches it over
`adb reverse tcp:8080 tcp:8080`.

Keep it on loopback. It accepts whatever account the caller sends, and if `web/config.toml`
enables `[credential]` it serves a pooled account to anyone who reaches it.

## Routes the app uses

| Purpose | Route | Notes |
| --- | --- | --- |
| Search | `GET /search/search_by_type?keyword=&search_type=0&num=&page=` | enums are integers; `search_type=0` is songs |
| Playlist | `GET /songlist/{id}/detail?num=200&page=` | paged; `hasmore` flags the rest |
| Lyrics | `GET /song/{mid}/lyric?trans=true&roma=true` | LRC; `qrc=true` gives word timing the lyric pipeline cannot parse yet |
| Stream | `GET /song/{mid}/url?file_type=` | 7 FLAC, 12 MP3 320, 13 MP3 128, 15 AAC 96; returns a bare `purl` |
| QR login | `GET /login/qrcode/{qq\|wx}`, then `GET /login/qrcode/{type}/status?identifier=` | events 0 done, 1 waiting, 2 scanned, 3 timeout, 4 refused; the WeChat status route is a ~15 s long poll |
| SMS login | `GET /login/phone/authcode?phone=&country_code=86`, then `GET /login/phone/authorize?phone=&auth_code=` | events 0 sent, 1 slider captcha wanted first (cannot be completed in-app), 2 too frequent; authorize answers a `Credential` |
| Renewal | `GET /login/check_expired`, `GET /login/refresh_credential` | once per process before the first QQ call, when the stored account can be refreshed |

Every answer is `{code, msg, data}`; `code == 0` is success; a route that needs an account answers
`401 {"code": -1, "msg": "未授权"}` without one.

The stream route returns only the CDN path. The app prefixes `https://dl.stream.qqmusic.qq.com/`,
the one host that answered such a path with `206 audio/mpeg`; the SDK's `isure.stream` fallback and
every host from `/song/get_cdn_dispatch` answered 403.

## The credential

The gateway reads the account from request cookies named after its own `Credential` fields:
`musicid` and `musickey` together are required; `refresh_token`, `refresh_key`, `openid`,
`access_token`, `expired_at`, `unionid` and `str_musicid` let it renew the key. The app stores
exactly that cookie string under the `qqCookie` setting (excluded from settings backups like the
NetEase cookie) and sends it on every QQ request.

`QQCredential` also accepts the gateway's `Credential` JSON and a browser cookie copied from
`y.qq.com` (`uin`/`qm_keyst`, plus the `psrf_*` and `wx*` pairs), and translates both into the
same stored form. The `o` prefix and zero padding QQ writes on `uin` are stripped.

Web/WASM cannot set a `Cookie` header from `fetch`, and the gateway takes the credential nowhere
else, so the Web build stays anonymous towards QQ.

## Measured 2026-09-22, signed out

Search, playlist detail and lyrics work. A VIP-tagged track (`pay_month=1`) resolved at MP3 128
and played from the CDN with range requests honoured. An indie upload answered `result 104003`
with no path at any tier. What an account unlocks above 128 has not been measured.
