# API proxies

The config generator runs entirely in the browser, but `vpn-api.proton.me`
sends no CORS headers and rejects preflight, so a page can never read its
responses. These two proxies forward the request and add the missing headers.

Cloudflare is intentionally **not** used for Proton API traffic: Proton
throttles it far more aggressively than the alternatives. The Cloudflare worker
on the `proxy` branch stays in place for other purposes only.

## What the proxies do

- answer `OPTIONS` preflight with `204`
- add `Access-Control-Allow-Origin/Methods/Headers`
- strip `content-security-policy` and `x-frame-options` from Proton's response
- drop `set-cookie`, so no Proton session cookie is ever handed to the page
- route by path prefix:
  - `/verify-api*` → `verify-api.proton.me`
  - `/verify*` → `verify.proton.me`
  - everything else → `vpn-api.proton.me`

Only a fixed allowlist of request headers is forwarded (`authorization`,
`x-pm-uid`, `x-pm-appversion`, …), so a misconfigured client cannot smuggle
extra headers upstream.

## Netlify

```sh
cd proxy/netlify
netlify deploy --prod
```

Current deployment: `https://shimmering-stroopwafel-51675e.netlify.app`

## Deno Deploy

```sh
cd proxy/deno
deployctl deploy --project=quick-bluejay-8760 main.ts
```

Current deployment: `https://quick-bluejay-8760.smh01-mirrors.deno.net`

## After deploying

Both URLs are listed in `src/lib/api.js` (`API_ENDPOINTS`) and are tried in
order; the second one is used when the first is unreachable or blocked. When an
origin changes, update `ALLOWED_ORIGINS` in **both** proxies.

Verify CORS is live:

```sh
curl -i -X OPTIONS \
  -H 'Origin: https://protonnext.qzz.io' \
  -H 'Access-Control-Request-Method: POST' \
  <proxy-url>/vpn/v2/logicals
```

The response must contain `access-control-allow-origin`.
