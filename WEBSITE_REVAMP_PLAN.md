# Website revamp + config generator — implementation plan

Repository: `ProtonVPN-Next-WEB`, branch `main`, checkout at
`/home/smh01/Studio canaryProjects/ProtonVPN-Next-WEB`.

The site used to be the `website` branch of the app repository. It moved to a
repository of its own because Deno Deploy builds *every* branch of a linked
repository, not just the default one, so a site sharing a repository with the
Android client could not be deployed cleanly. The same repository is deployed
twice: to Deno Deploy and to Cloudflare. History was preserved, so the OTA
metadata commits and the old landing page are still in the log.

## Goals

1. Rebuild the site (design untouched for ~6 months) on Vite + Tailwind.
2. Replace the single download button with the real build matrix:
   channel (nightly / stable) x type (standard / privacy) x build (debug / release).
3. Refresh the icon/logo from the current Android launcher icon.
4. Add a full in-browser config generator that mirrors the CLI:
   guest login with Android spoofing, server list, server loads, extended
   certificate, obfuscation settings, and a downloadable `.conf`.

## Constraints discovered while exploring

- Proton API cannot be called directly from a browser (CORS). Requests must go
  through a proxy.
- The user does not want Cloudflare for API traffic (Proton treats it poorly).
  Deno Deploy is the only proxy the site uses:
  - Deno: `https://protonvpn-next-mirror.smh01-mirrors.deno.net`
- Netlify was dropped: it is not reachable from Russia, where most of the users
  are, and the GitHub account its site was linked to has been banned, so it
  cannot be redeployed from the repository either.
- The proxy sources live under `proxy/` in this repository and are deployed as
  a separate Deno project. The live deployment answers CORS preflight and
  echoes the caller's origin; `proxy/README.md` documents how to verify it.
- Captcha (API code 9001) is not solved with an iframe. Instead the client keeps
  several Android device profiles and transparently retries the flow with a
  different profile, the way a different device would look to the API.
- The site itself stays on Cloudflare Workers assets (`wrangler.jsonc`); only
  API traffic avoids Cloudflare.

## Reference logic in the CLI

`/home/smh01/Studio canaryProjects/pvpn-next-desktop/ProtonVPN-Next-CLI/`

| Concern | CLI source | Site source |
| --- | --- | --- |
| Device spoofing / challenge payload | `pvpn_cli/device_info.py` | `src/lib/spoof.js` |
| Guest login (2 phases) | `pvpn_cli/auth.py` | `src/lib/auth.js` |
| Servers / loads / tier / cities | `pvpn_cli/vpn.py` | `src/lib/servers.js` |
| Certificate (`Mode: persistent`) | `pvpn_cli/vpn.py:register_cert` | `src/lib/cert.js` |
| Ed25519 -> WireGuard key | `pvpn_cli/crypto.py` | `src/lib/crypto.js` |
| Obfuscation presets + `I1` | `pvpn_cli/awg.py` | `src/lib/awg.js` |
| `.conf` layout | `pvpn_cli/cli/commands/connection.py` | `src/lib/conf.js` |

## Steps

- [x] 1. Scaffold Vite + Tailwind, keep `public/` assets and the `lite/` page.
- [x] 2. Extract the launcher icon from the Android repo into web assets.
- [x] 3. Rebuild the landing page: hero, features, download matrix, generator, about.
- [x] 4. Move every user-facing string into `src/i18n/*.json` (en, ru, uk, be, fa, zh).
- [x] 5. Implement the generator libraries listed in the table above.
- [x] 6. Add a CORS-enabled Deno proxy (source on `protonvpn-next-dev`).
- [x] 7. Update CI so `update.json` carries all eight build variants.
- [x] 8. Build, verify, commit.

Step 7 lives in the app repo (`scripts/publish_ota.py` on `protonvpn-next-dev`,
commit `d07f6ad`), so it is committed separately. Until that pipeline runs, the
privacy rows of the matrix render as "not published" instead of breaking.

## Deployment order

1. Push `main` to both remotes (`origin` on GitLab, `mirror` on GitHub).
2. **Deno Deploy — site and proxy in one project.** Entrypoint `server.ts`,
   install `npm install`, build `npm run build`. It serves `dist/` and mounts
   the proxy under `/api`, so the generator calls its own origin and CORS never
   enters the picture. Verify with `/__proxy/health`, which stays at the root.
3. **Cloudflare — site and proxy too.** Same repository, build `npm run build`,
   output `dist/`, Worker entry `worker/index.ts` (all in `wrangler.jsonc`).
   It mounts the same proxy under `/api`, so the two hosts share no runtime
   dependency and neither spends the other's free-plan quota. `run_worker_first`
   must stay enabled, otherwise static assets are matched first and `/api`
   returns a 404 instead of reaching the Worker.

   Tradeoff: Proton rate-limits Cloudflare egress harder than other providers,
   so this path hits human verification sooner. Deno stays the primary host and
   the spoof profile rotation absorbs the rest.
4. Run the app pipeline once so `update.json` gains the privacy entries. The CI
   job in the app repository pushes OTA metadata here, not into the app repo.

The standalone proxy project (entrypoint `proxy/deno/main.ts`) stays deployed
for the Android client and the CLI, which call an absolute URL and are not
subject to CORS.

The proxy URL in the Android client and the CLI still points at the current
deployment and is updated separately, before a stable release.

## Tests

`npm test` (`node --test`) covers the parts that silently corrupt output when
wrong: `.conf` assembly and AWG ordering, the download matrix key mapping
(`release`/`debug` vs `privacyRelease`/`privacyDebug`), the Java `hashCode`
reimplementation and the device profile rotation used to dodge captchas.

## Security notes

- The WireGuard private key is generated in the browser with WebCrypto and never
  leaves the page; only the derived public key is sent to Proton.
- The generated `.conf` contains that private key, so the UI warns before
  download and never stores the key in `localStorage`.
- Guest sessions are short-lived and kept in memory only.
