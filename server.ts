/**
 * Deno Deploy entrypoint: serves the built site and the Proton API proxy from a
 * single deployment.
 *
 * Keeping both in one origin is what makes the generator robust: the browser
 * calls `/api/...` on the page's own origin, so there is no preflight, no
 * allow-list to keep in sync, and no way for a stale proxy deployment to break
 * the site while the site itself is up to date.
 *
 * Cloudflare runs the mirror image of this file in `worker/index.ts`, sharing
 * the same proxy and routing modules, so the two hosts stay independent.
 */

import { serveDir } from "jsr:@std/http@^1.0.0/file-server"
import { handleProxyRequest } from "./proxy/core.ts"
import { proxyPathname, wantsAppShell } from "./proxy/routing.ts"

/** Vite's build output, committed so a deployment never depends on a build step. */
const STATIC_ROOT = "dist"

Deno.serve(async (request: Request): Promise<Response> => {
	const url = new URL(request.url)

	const proxied = proxyPathname(url.pathname)
	if (proxied !== null) {
		return await handleProxyRequest(request, proxied)
	}

	const response = await serveDir(request, { fsRoot: STATIC_ROOT, quiet: true })
	if (response.status !== 404 || !wantsAppShell(request, url.pathname)) {
		return response
	}

	const shellRequest = new Request(new URL("/", url), { headers: request.headers })
	return await serveDir(shellRequest, { fsRoot: STATIC_ROOT, quiet: true })
})
