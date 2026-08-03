/**
 * Cloudflare Worker entrypoint: static site plus its own Proton API proxy.
 *
 * The Cloudflare deployment used to call the Deno proxy for API traffic, which
 * meant one free-plan quota served both hosts and an outage on Deno took the
 * generator down everywhere. Running the same proxy here makes the two
 * deployments fully independent.
 *
 * Caveat worth remembering: Proton rate-limits Cloudflare egress noticeably
 * harder than other providers, so this path can hit verification challenges
 * where Deno does not. That is what the spoof profile rotation is for, and it is
 * why the Deno deployment stays the primary one.
 */

import { handleProxyRequest } from "../proxy/core.ts"
import { proxyPathname, wantsAppShell } from "../proxy/routing.ts"

interface Env {
	/** Binding for the built site in `dist/`, configured in `wrangler.jsonc`. */
	ASSETS: { fetch: (request: Request) => Promise<Response> }
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url)

		const proxied = proxyPathname(url.pathname)
		if (proxied !== null) {
			return await handleProxyRequest(request, proxied)
		}

		const response = await env.ASSETS.fetch(request)
		if (response.status !== 404 || !wantsAppShell(request, url.pathname)) {
			return response
		}

		return await env.ASSETS.fetch(new Request(new URL("/", url), { headers: request.headers }))
	},
}
