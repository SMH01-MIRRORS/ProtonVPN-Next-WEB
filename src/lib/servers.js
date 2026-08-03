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
