/**
 * Bulk export.
 *
 * One server at a time is fine until you want every Dutch server in your
 * client. This module turns a scope — one server, a city, a country or the
 * whole list — into a single download: an archive of `.conf` files, or one
 * Clash document holding every proxy.
 *
 * All configurations in a batch share one key pair and one certificate. Proton
 * issues a certificate for a public key, not for a server, so the same one is
 * valid everywhere; asking for eighty certificates would only earn a rate
 * limit.
 */

import { configFileName, safeServerName } from "./conf.js"
import { buildClashDocument, formatById, renderConfig } from "./formats.js"
import { createZip } from "./zip.js"

/** Export scopes offered in the UI. */
export const BULK_SCOPES = [
	{ id: "server", labelKey: "gen_bulk_scope_server" },
	{ id: "city", labelKey: "gen_bulk_scope_city" },
	{ id: "country", labelKey: "gen_bulk_scope_country" },
	{ id: "all", labelKey: "gen_bulk_scope_all" },
]

export function scopeById(id) {
	return BULK_SCOPES.find((scope) => scope.id === id) ?? BULK_SCOPES[0]
}

/**
 * The servers a scope covers.
 *
 * @param options.scope   one of `BULK_SCOPES`
 * @param options.server  the currently selected server, for the `server` scope
 * @param options.country exit country code, for the `country` and `city` scopes
 * @param options.city    city name, for the `city` scope
 */
export function scopeServers({ servers = [], scope = "all", server = null, country = null, city = null }) {
	if (scope === "server") return server ? [server] : []
	if (scope === "country") return servers.filter((candidate) => candidate.exitCountry === country)
	if (scope === "city") {
		return servers.filter(
			(candidate) => candidate.city === city && (!country || candidate.exitCountry === country),
		)
	}
	return [...servers]
}

/** A filesystem-safe fragment describing the scope, such as `NL-Amsterdam`. */
function scopeSlug({ scope, server, country, city }) {
	if (scope === "server") return safeServerName(server)
	if (scope === "city") return `${country ?? ""}-${city ?? ""}`.replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-|-$/g, "")
	if (scope === "country") return String(country ?? "").replace(/[^A-Za-z0-9_-]+/g, "-")
	return "all"
}

/** Name of the file the browser receives. */
export function bundleFileName({ scope, format, server, country, city }) {
	const descriptor = formatById(format)
	const slug = scopeSlug({ scope, server, country, city }) || "all"

	if (descriptor.bundle === "single") return `pvpn-next-${slug}-${descriptor.id}.${descriptor.extension}`
	return `pvpn-next-${slug}-${descriptor.id}.zip`
}

/**
 * Builds the download for a set of servers.
 *
 * @param options.servers  servers to export
 * @param options.format   client format id
 * @param options.settings everything a single configuration needs, minus the server
 * @returns `{ kind, fileName, text, bytes, count }`; `kind` is `text` for a
 *          single document and `zip` for an archive of per-server files.
 */
export function buildBundle({ servers, format = "amneziawg", settings = {}, scope = "all", country = null, city = null }) {
	const descriptor = formatById(format)
	const fileName = bundleFileName({
		scope,
		format: descriptor.id,
		server: servers[0],
		country,
		city,
	})

	if (descriptor.bundle === "single") {
		return {
			kind: "text",
			fileName,
			mime: "text/yaml;charset=utf-8",
			text: buildClashDocument(servers.map((server) => ({ ...settings, server }))),
			count: servers.length,
		}
	}

	// A single server needs no archive: hand over the plain file.
	if (servers.length === 1) {
		return {
			kind: "text",
			fileName: configFileName(servers[0], descriptor.extension),
			mime: "text/plain;charset=utf-8",
			text: renderConfig({ ...settings, format: descriptor.id, server: servers[0] }),
			count: 1,
		}
	}

	const used = new Set()
	const files = servers.map((server) => {
		let name = `${safeServerName(server)}.${descriptor.extension}`
		if (used.has(name)) {
			let suffix = 2
			while (used.has(`${safeServerName(server)}-${suffix}.${descriptor.extension}`)) suffix += 1
			name = `${safeServerName(server)}-${suffix}.${descriptor.extension}`
		}
		used.add(name)
		return { name, text: renderConfig({ ...settings, format: descriptor.id, server }) }
	})

	return {
		kind: "zip",
		fileName,
		mime: "application/zip",
		bytes: createZip(files),
		count: files.length,
	}
}
