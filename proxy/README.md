# API proxies

The config generator runs entirely in the browser, but `vpn-api.proton.me`
sends no CORS headers and rejects preflight, so a page can never read its
responses. This proxy forwards the request and adds the missing headers.

## One codebase, three entrypoints

`core.ts` holds the whole proxy and uses Web APIs only, so the same code runs
unmodified on every host. `routing.ts` holds the path split, so a request lands
on the same handler everywhere:

- `../server.ts` - Deno Deploy: the site and the proxy in one deployment
- `../worker/index.ts` - Cloudflare Worker: the same pair, independently
- `deno/main.ts` - the proxy alone, for a deployment that serves no site

Splitting routing per host would let the generator work on one deployment and
fail on another for reasons that are invisible from the page.

## What the proxies do

- answer `OPTIONS` preflight with `204`
- add `Access-Control-Allow-Origin/Methods/Headers`
- strip `content-security-policy` and `x-frame-options` from Proton's response
- drop `set-cookie`, so no Proton session cookie is ever handed to the page
- route by path prefix:
  - `/verify-api*` -> `verify-api.proton.me`
  - `/verify*` -> `verify.proton.me`
  - everything else -> `vpn-api.proton.me`

Only a fixed allowlist of request headers is forwarded (`authorization`,
`x-pm-uid`, `x-pm-appversion`, ...), so a misconfigured client cannot smuggle
extra headers upstream.

## Allowed origins

Origins are matched by pattern, not by a fixed list, because the site is served
from several hosts and because a missing entry fails in a way that looks like a
broken proxy:

- `https://` + any subdomain of `protonnext.qzz.io`
- `https://<name>.workers.dev` and `https://<name>.pages.dev` previews
- `http://localhost` and `http://127.0.0.1` on any port

An origin outside the patterns gets no `Access-Control-Allow-Origin` at all.
The proxy never answers with a substituted origin: doing so made browsers
report that the header "does not match", which hides the real cause.

Native clients ignore CORS entirely, so the allowlist never affects them.

## Deployments

The site deployment serves the proxy under `/api` on its own origin. There is
deliberately only one Deno project: a second one would spend the same free-plan
quota just to spell the URL without the `/api` prefix.

| Host | URL | Used by |
| --- | --- | --- |
| Deno Deploy | `https://protonvpn-next-web--main.smh01-mirrors.deno.net/api` | website, Android, CLI |
| Cloudflare | `<worker-url>/api` | website only, when Deno is unreachable |
| Netlify | `https://shimmering-stroopwafel-51675e.netlify.app` | Android and CLI only |

The browser reaches its own origin first (`/api` in `API_ENDPOINTS`), so the
absolute Deno URL is only a fallback for a copy of the site served elsewhere.

Proton throttles Cloudflare egress harder than the alternatives, so that path
hits human verification sooner. It exists so the two hosts do not depend on
each other, not as an equal route: keep Deno primary.

Netlify is kept for the native clients, where it is a working bypass for users
in Iran. It is deliberately not used by the website: Netlify is unreachable from
Russia, and the GitHub account it is linked to has been banned, so the live
deployment is frozen on a build that predates CORS support and answers browser
requests without `Access-Control-Allow-Origin`. Native clients do not enforce
CORS, so that stale build keeps working for them.

## Changing the proxy URL

Every consumer hardcodes it:

- `src/lib/api.js` (`API_ENDPOINTS`)
- `app/src/main/java/ru/protonmod/next/di/NetworkModule.kt`
  (`PROTON_PROXY_DENO_URL`, `PROTON_PROXY_NETLIFY_URL`)
- `app/src/main/java/ru/protonmod/next/data/network/TokenAuthenticator.kt`
- `app/src/main/java/ru/protonmod/next/data/network/DohFallbackInterceptor.kt`
- `app/src/main/java/ru/protonmod/next/ui/screens/CaptchaScreen.kt`
- CLI: `pvpn_cli/auth.py`, `pvpn_cli/vpn.py`, `pvpn_cli/captcha.py`,
  `pvpn_cli/cli/commands/{connection,settings}.py`

The `/api` suffix belongs in those constants. Retrofit endpoints and the CLI
both append relative paths, so the prefix survives; a Retrofit endpoint written
with a leading slash would silently drop it. Certificate pinning matches on the
host suffix (`*.deno.net`), so it keeps working across renames.

## Verifying a deployment

```sh
curl -s -H 'Origin: https://protonnext.qzz.io' <site-url>/__proxy/health
curl -i -X OPTIONS \
  -H 'Origin: https://protonnext.qzz.io' \
  -H 'Access-Control-Request-Method: POST' \
  <site-url>/api/vpn/v2/logicals
```

The health endpoint reports the running build and whether the origin was
accepted; the preflight must answer `204` with `access-control-allow-origin`.
