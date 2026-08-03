/**
 * Guest (credential-less) sessions, ported from `pvpn_cli/auth.py`.
 *
 * The login is two phases: an anonymous session first, then the credential-less
 * upgrade that yields a usable VPN session. If Proton asks for human
 * verification the whole flow is retried with the next Android device profile,
 * which is why the site keeps a pool of them instead of a single hardcoded one.
 */

import { apiCall, ApiError, ProxyUnreachableError } from "./api.js"
import { buildChallengePayload, profileRotation } from "./spoof.js"

export class VerificationExhaustedError extends Error {
	constructor(attempts) {
		super("Every device profile was challenged")
		this.name = "VerificationExhaustedError"
		this.attempts = attempts
	}
}

async function createAnonymousSession(profile, signal) {
	const payload = await apiCall("/auth/v4/sessions", {
		method: "POST",
		profile,
		body: buildChallengePayload(profile),
		signal,
	})

	return { accessToken: payload.AccessToken, uid: payload.UID, refreshToken: payload.RefreshToken ?? null }
}

async function upgradeToCredentialless(profile, session, signal) {
	const payload = await apiCall("/auth/v4/credentialless", {
		method: "POST",
		profile,
		session,
		body: buildChallengePayload(profile),
		signal,
	})

	return {
		accessToken: payload.AccessToken ?? session.accessToken,
		uid: payload.UID ?? session.uid,
		refreshToken: payload.RefreshToken ?? session.refreshToken,
	}
}

/**
 * Logs in as a guest.
 *
 * @param onProgress called with `{ stage, profile, attempt, total }` so the UI
 *   can show which device profile is being used, including silent retries.
 * @returns {Promise<{accessToken: string, uid: string, profile: object}>}
 */
export async function loginAsGuest({ onProgress = () => {}, signal } = {}) {
	const profiles = profileRotation()
	const attempts = []

	for (const [index, profile] of profiles.entries()) {
		const progress = { profile, attempt: index + 1, total: profiles.length }

		try {
			onProgress({ ...progress, stage: "session" })
			const anonymous = await createAnonymousSession(profile, signal)

			onProgress({ ...progress, stage: "credentialless" })
			const session = await upgradeToCredentialless(profile, anonymous, signal)

			onProgress({ ...progress, stage: "done" })
			return { ...session, profile }
		} catch (error) {
			if (error instanceof ProxyUnreachableError) throw error
			if (error?.name === "AbortError") throw error

			if (error instanceof ApiError && error.needsVerification) {
				// A captcha for this fingerprint. Swap the device profile and retry
				// without bothering the user.
				attempts.push({ profile: profile.id, code: error.code })
				onProgress({ ...progress, stage: "rotating" })
				continue
			}

			throw error
		}
	}

	throw new VerificationExhaustedError(attempts)
}
