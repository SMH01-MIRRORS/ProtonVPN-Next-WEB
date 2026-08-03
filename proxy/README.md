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

Both proxies must keep their existing URLs, otherwise `API_ENDPOINTS` in
`src/lib/api.js` has to be updated as well.

## Netlify

Current deployment: `https://shimmering-stroopwafel-51675e.netlify.app`

With the CLI:

```sh
cd proxy/netlify
netlify deploy --prod
```

Without the CLI, connect the existing site to this repository in the Netlify
UI (Site configuration → Build & deploy):

- repository: the website repository, branch `website`
- base directory: `proxy/netlify`
- build command: leave empty
- publish directory: `proxy/netlify/public`
- functions directory: `proxy/netlify/netlify/functions`

The base directory is what makes Netlify read `proxy/netlify/netlify.toml`;
without it the proxy is never bundled and every request returns the old
response.

## Deno Deploy

Current deployment: `https://quick-bluejay-8760.smh01-mirrors.deno.net`

With the CLI:

```sh
cd proxy/deno
deployctl deploy --project=quick-bluejay-8760 main.ts
```

Without the CLI, link the project to the repository in the Deno Deploy
dashboard and set the entrypoint to `proxy/deno/main.ts` on branch `website`.
No build step and no environment variables are required.

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
