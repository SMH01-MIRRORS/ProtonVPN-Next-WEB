/**
 * Android device profiles used to make API calls look like the mobile client.
 *
 * Mirrors `pvpn_cli/device_info.py`, with one important addition: the CLI ships a
 * single hardcoded profile, while the site keeps several. When the API answers
 * with a human-verification code the flow is retried with the next profile, so a
 * captcha is worked around transparently instead of being shown to the user.
 */

export const SPOOFED_APP_VERSION = "5.18.75.1"

/**
 * Every entry is a plausible, self-consistent Android device: the model, the OS
 * version, the locale and the timezone offset all match each other, because the
 * challenge payload is checked for internal consistency.
 */
export const DEVICE_PROFILES = [
	{
		id: "pixel6",
		androidVersion: "12",
		manufacturer: "Google",
		model: "Pixel 6",
		language: "en",
		regionCode: "US",
		timezone: "America/New_York",
		timezoneOffset: 240,
		storageCapacity: 128.0,
		keyboards: ["com.google.android.inputmethod.latin"],
	},
	{
		id: "pixel8",
		androidVersion: "14",
		manufacturer: "Google",
		model: "Pixel 8",
		language: "en",
		regionCode: "GB",
		timezone: "Europe/London",
		timezoneOffset: 0,
		storageCapacity: 256.0,
		keyboards: ["com.google.android.inputmethod.latin"],
	},
	{
		id: "galaxys23",
		androidVersion: "13",
		manufacturer: "Samsung",
		model: "SM-S911B",
		language: "de",
		regionCode: "DE",
		timezone: "Europe/Berlin",
		timezoneOffset: -60,
		storageCapacity: 256.0,
		keyboards: ["com.samsung.android.honeyboard"],
	},
	{
		id: "galaxya54",
		androidVersion: "14",
		manufacturer: "Samsung",
		model: "SM-A546E",
		language: "nl",
		regionCode: "NL",
		timezone: "Europe/Amsterdam",
		timezoneOffset: -60,
		storageCapacity: 128.0,
		keyboards: ["com.samsung.android.honeyboard"],
	},
	{
		id: "xiaomi13",
		androidVersion: "13",
		manufacturer: "Xiaomi",
		model: "2211133C",
		language: "fr",
		regionCode: "FR",
		timezone: "Europe/Paris",
		timezoneOffset: -60,
		storageCapacity: 256.0,
		keyboards: ["com.google.android.inputmethod.latin"],
	},
	{
		id: "oneplus11",
		androidVersion: "14",
		manufacturer: "OnePlus",
		model: "CPH2449",
		language: "en",
		regionCode: "CA",
		timezone: "America/Toronto",
		timezoneOffset: 240,
		storageCapacity: 256.0,
		keyboards: ["com.google.android.inputmethod.latin"],
	},
]

/** Java's String.hashCode(), used by the Android client as the device name. */
export function javaStringHashCode(value) {
	let hash = 0
	for (const char of value) {
		hash = (Math.imul(31, hash) + char.codePointAt(0)) | 0
	}
	return hash
}

/**
 * A stable per-browser identifier. The Android client derives it from the device
 * id; here it is generated once and kept in sessionStorage so a retry inside the
 * same visit does not look like a brand new device on every request.
 */
function deviceSeed() {
	const storageKey = "pvpn-device-seed"
	try {
		const existing = sessionStorage.getItem(storageKey)
		if (existing) return existing
		const generated = String(Math.floor(Math.random() * 2 ** 48))
		sessionStorage.setItem(storageKey, generated)
		return generated
	} catch {
		// Private mode without storage access.
		return String(Math.floor(Math.random() * 2 ** 48))
	}
}

export function userAgentFor(profile) {
	const manufacturer = profile.manufacturer.charAt(0).toUpperCase() + profile.manufacturer.slice(1)
	return `PVPN/${SPOOFED_APP_VERSION} (Android ${profile.androidVersion}; ${manufacturer} ${profile.model})`
}

/** Headers shared by every API call, matching the Android NetworkModule. */
export function baseHeaders(profile) {
	return {
		"User-Agent": userAgentFor(profile),
		"x-pm-appversion": `android-vpn@${SPOOFED_APP_VERSION}-dev+play`,
		"x-pm-apiversion": "4",
		Accept: "application/vnd.protonmail.v1+json",
		"Content-Type": "application/json",
	}
}

/** The `vpn-android-v4-challenge-0` payload expected by the auth endpoints. */
export function buildChallengePayload(profile) {
	return {
		Payload: {
			"vpn-android-v4-challenge-0": {
				type: "me.proton.core.challenge.data.frame.ChallengeFrame.Device",
				v: SPOOFED_APP_VERSION,
				appLang: profile.language,
				timezone: profile.timezone,
				deviceName: javaStringHashCode(`${deviceSeed()}:${profile.id}`),
				regionCode: profile.regionCode,
				timezoneOffset: profile.timezoneOffset,
				isJailbreak: false,
				preferredContentSize: "1.0",
				storageCapacity: profile.storageCapacity,
				isDarkmodeOn: false,
				keyboards: profile.keyboards,
			},
		},
	}
}

/**
 * Profiles in the order they should be attempted, starting at a random one so
 * repeated visits do not always hammer the API with the same fingerprint.
 */
export function profileRotation() {
	const offset = Math.floor(Math.random() * DEVICE_PROFILES.length)
	return DEVICE_PROFILES.map((_, index) => DEVICE_PROFILES[(index + offset) % DEVICE_PROFILES.length])
}
