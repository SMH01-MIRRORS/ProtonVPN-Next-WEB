/**
 * Server list, live load and account tier, ported from `pvpn_cli/vpn.py`.
 */

import { apiCall } from "./api.js"

// The CLI splits this literal so the protocol name is not sitting in plain sight
// inside the binary; the same is done here for the bundled JavaScript.
const PROTOCOL = "wire" + "guard"

/** Proton tier numbers; guests are always tier 0 (free). */
export const TIER_FREE = 0

export async function fetchServers({ profile, session, signal }) {
	const payload = await apiCall(`/vpn/v2/logicals?WithEntriesForProtocols=${PROTOCOL}&WithState=true`, {
		profile,
		session,
		signal,
	})
	return payload.LogicalServers ?? []
}

/** Live load percentage per logical server id. */
export async function fetchLoads({ profile, session, signal }) {
	const payload = await apiCall("/vpn/v1/loads", { profile, session, signal })
	const byId = new Map()
	for (const entry of payload.LogicalServers ?? []) {
		byId.set(entry.ID, { load: entry.Load ?? null, score: entry.Score ?? null, status: entry.Status ?? null })
	}
	return byId
}

/** The highest tier the session may connect to. Guests get 0. */
export async function fetchMaxTier({ profile, session, signal }) {
	const payload = await apiCall("/vpn/v2", { profile, session, signal })
	return payload.VPN?.MaxTier ?? TIER_FREE
}

/**
 * Merges the logical list with the live loads and keeps only servers the
 * session can actually use.
 */
export function prepareServers(logicals, loads, maxTier) {
	return logicals
		.filter((server) => (server.Tier ?? 0) <= maxTier)
		.filter((server) => (server.Status ?? 1) === 1)
		.map((server) => {
			const live = loads.get(server.ID)
			const entry = (server.Servers ?? []).find((candidate) => candidate.Status === 1 && candidate.X25519PublicKey)

			return {
				id: server.ID,
				name: server.Name,
				exitCountry: server.ExitCountry,
				entryCountry: server.EntryCountry,
				city: server.City ?? null,
				tier: server.Tier ?? 0,
				features: server.Features ?? 0,
				load: live?.load ?? server.Load ?? null,
				score: live?.score ?? server.Score ?? null,
				entryIp: entry?.EntryIP ?? null,
				publicKey: entry?.X25519PublicKey ?? null,
				label: entry?.Label ?? null,
			}
		})
		.filter((server) => server.entryIp && server.publicKey)
		.sort((first, second) => {
			const byCountry = first.exitCountry.localeCompare(second.exitCountry)
			return byCountry !== 0 ? byCountry : (first.load ?? 100) - (second.load ?? 100)
		})
}

/** Country codes present in a prepared server list. */
export function countriesOf(servers) {
	return [...new Set(servers.map((server) => server.exitCountry))].sort()
}

/** The id the picker uses for the "fastest server" entry. */
export const FASTEST_ID = "__fastest__"

/**
 * Picks the server the app would connect to for "fastest", which is the lowest
 * Proton score rather than the lowest load: the score already folds in latency
 * and capacity, and load alone would happily pick an idle server on the far
 * side of the planet. Load breaks ties so the choice is stable.
 */
export function fastestServer(servers) {
	if (servers.length === 0) return null

	return servers.reduce((best, candidate) => {
		const bestScore = best.score ?? Number.POSITIVE_INFINITY
		const candidateScore = candidate.score ?? Number.POSITIVE_INFINITY
		if (candidateScore !== bestScore) return candidateScore < bestScore ? candidate : best
		return (candidate.load ?? 100) < (best.load ?? 100) ? candidate : best
	})
}

/**
 * Groups servers by country for the picker, ordered the way the app orders its
 * country list: the country whose fastest server is best comes first.
 */
export function serversByCountry(servers) {
	const groups = new Map()
	for (const server of servers) {
		const existing = groups.get(server.exitCountry)
		if (existing) existing.push(server)
		else groups.set(server.exitCountry, [server])
	}

	return [...groups.entries()]
		.map(([country, list]) => ({
			country,
			servers: list,
			fastest: fastestServer(list),
			load: averageLoad(list),
		}))
		.sort((first, second) => {
			const byScore = (first.fastest?.score ?? Number.POSITIVE_INFINITY) - (second.fastest?.score ?? Number.POSITIVE_INFINITY)
			return byScore !== 0 ? byScore : first.country.localeCompare(second.country)
		})
}

/** Mean load across a country's servers, or null when nothing reported one. */
export function averageLoad(servers) {
	const reported = servers.map((server) => server.load).filter((load) => typeof load === "number")
	if (reported.length === 0) return null
	return Math.round(reported.reduce((sum, load) => sum + load, 0) / reported.length)
}

/**
 * City names present in a prepared list, sorted alphabetically.
 *
 * Proton leaves `City` empty on a few servers; those cannot be offered as a
 * choice, so they are skipped rather than shown as a blank entry.
 */
export function citiesOf(servers) {
	return [...new Set(servers.map((server) => server.city).filter(Boolean))].sort((first, second) =>
		first.localeCompare(second),
	)
}
