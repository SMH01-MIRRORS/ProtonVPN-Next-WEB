/**
 * Deno Deploy entrypoint: serves the built site and the Proton API proxy from a
 * single deployment.
 *
 * Keeping both in one origin is what makes the generator robust: the browser
 * calls `/api/...` on the page's own origin, so there is no preflight, no
 * allow-list to keep in sync, and no way for a stale proxy deployment to break
 * the site while the site itself is up to date.
 *
 * Cloudflare serves the same repository without this file: it only hosts the
 * static build, and the generator falls back to the absolute proxy URL there.
 */

import { serveDir } from "jsr:@std/http@^1.0.0/file-server"
import { handleProxyRequest } from "./proxy/deno/main.ts"

/** Everything below this path is proxied to Proton instead of the file system. */
const PROXY_PREFIX = "/api"

/** Vite's build output, committed so a deployment never depends on a build step. */
const STATIC_ROOT = "dist"

/**
 * Maps an incoming path to the path the proxy should forward, or `null` when
 * the request belongs to the site.
 *
 * `/__proxy/health` stays at the root as well: the documented verification
 * command has to work the same way against this deployment and the standalone
 * proxy one.
 */
function proxyPathname(pathname: string): string | null {
	if (pathname === "/__proxy/health") return pathname
	if (pathname === PROXY_PREFIX) return "/"
	if (pathname.startsWith(`${PROXY_PREFIX}/`)) return pathname.slice(PROXY_PREFIX.length)
	return null
}

/**
 * True for navigation requests, which should fall back to the app shell.
 *
 * A missing asset (`.js`, `.png`, `update.json`) must stay a real 404: serving
 * HTML in its place turns a broken path into a confusing parse error further
 * down the line.
 */
function wantsAppShell(request: Request, pathname: string): boolean {
	if (request.method !== "GET" && request.method !== "HEAD") return false
	if (pathname.includes(".")) return false
	return (request.headers.get("accept") ?? "").includes("text/html")
}

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
