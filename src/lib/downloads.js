/**
 * Download matrix built from `public/update.json`.
 *
 * The Android OTA client reads the same file and expects
 * `{ stable | nightly }.{ release | debug }` to stay exactly where it is. Its
 * parser ignores unknown keys, so the privacy flavour is published alongside it
 * under `privacyRelease` / `privacyDebug` instead of reshaping the document.
 */

export const CHANNELS = [
	{ id: "stable", labelKey: "dl_channel_stable", descriptionKey: "dl_channel_stable_desc" },
	{ id: "nightly", labelKey: "dl_channel_nightly", descriptionKey: "dl_channel_nightly_desc" },
]

export const FLAVORS = [
	{ id: "standard", labelKey: "dl_flavor_standard", descriptionKey: "dl_flavor_standard_desc" },
	{ id: "privacy", labelKey: "dl_flavor_privacy", descriptionKey: "dl_flavor_privacy_desc" },
]

export const BUILD_TYPES = [
	{ id: "release", labelKey: "dl_build_release", descriptionKey: "dl_build_release_desc" },
	{ id: "debug", labelKey: "dl_build_debug", descriptionKey: "dl_build_debug_desc" },
]

/** Key inside a channel object for a given flavour and build type. */
export function metadataKey(flavor, buildType) {
	if (flavor === "standard") return buildType
	return `privacy${buildType.charAt(0).toUpperCase()}${buildType.slice(1)}`
}

export async function fetchUpdateMetadata(signal) {
	const response = await fetch("/update.json", { cache: "no-cache", signal })
	if (!response.ok) throw new Error(`update.json returned HTTP ${response.status}`)
	return response.json()
}

/** Looks up one build; returns null when that variant was never published. */
export function buildFor(metadata, channel, flavor, buildType) {
	const entry = metadata?.[channel]?.[metadataKey(flavor, buildType)]
	if (!entry?.url) return null

	return {
		channel,
		flavor,
		buildType,
		versionCode: entry.versionCode ?? null,
		versionName: entry.versionName ?? null,
		url: entry.url,
		changelog: entry.changelog ?? "",
		sha256Url: `${entry.url}.sha256`,
	}
}

/** Every published variant, for rendering the full matrix at once. */
export function allBuilds(metadata) {
	const builds = []
	for (const channel of CHANNELS) {
		for (const flavor of FLAVORS) {
			for (const buildType of BUILD_TYPES) {
				builds.push({
					channel: channel.id,
					flavor: flavor.id,
					buildType: buildType.id,
					build: buildFor(metadata, channel.id, flavor.id, buildType.id),
				})
			}
		}
	}
	return builds
}
