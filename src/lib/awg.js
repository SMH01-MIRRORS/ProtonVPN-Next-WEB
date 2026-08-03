/**
 * AmneziaWG obfuscation parameters, ported from `pvpn_cli/awg.py` and the
 * Android obfuscation screen (`ObfuscationSettingsScreen.kt`).
 *
 * Two levels are exposed, exactly as in the app: presets for people who just
 * want a working file, and an advanced mode where every field can be typed in.
 */

import { DEFAULT_I1, I1_PRESETS, nextI1 } from "./i1.js"

export { DEFAULT_I1, I1_PRESETS, nextI1 }

/** The parameter order used when the values are written into a `.conf`. */
export const AWG_KEY_ORDER = ["Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "I2", "I3", "I4", "I5"]

/**
 * The AmneziaWG 2.0 fields the Android engine accepts
 * (`.artifacts/amnezia-box/option/awg.go`). They are written in `.conf` spelling
 * here, following the WireGuard convention where an ini key such as
 * `PersistentKeepalive` maps to the `persistent_keepalive_interval` the engine
 * reads. Older clients ignore keys they do not know, and every one of these is
 * omitted unless the user fills it in, so a default file stays compatible.
 */
export const ADVANCED_KEY_ORDER = [
	"HeaderProtectionKey",
	"ContentPaddingAddition",
	"RekeyAfterTime",
	"RekeyTimeout",
	"RejectAfterTime",
	"KeepaliveTimeout",
	"MaxHandshakeAttempts",
]

/**
 * Field descriptors that drive the advanced editor, so the form and the file
 * writer can never drift apart. `labelKey` and `hintKey` resolve through the
 * translation files.
 */
export const ADVANCED_GROUPS = [
	{
		id: "junk",
		titleKey: "gen_adv_group_junk",
		fields: [
			{ key: "Jc", labelKey: "gen_adv_jc", type: "number" },
			{ key: "Jmin", labelKey: "gen_adv_jmin", type: "number" },
			{ key: "Jmax", labelKey: "gen_adv_jmax", type: "number" },
		],
	},
	{
		id: "magic",
		titleKey: "gen_adv_group_magic",
		fields: [
			{ key: "S1", labelKey: "gen_adv_s1", type: "number" },
			{ key: "S2", labelKey: "gen_adv_s2", type: "number" },
			{ key: "S3", labelKey: "gen_adv_s3", type: "number" },
			{ key: "S4", labelKey: "gen_adv_s4", type: "number" },
		],
	},
	{
		id: "headers",
		titleKey: "gen_adv_group_headers",
		fields: [
			{ key: "H1", labelKey: "gen_adv_h1", type: "number" },
			{ key: "H2", labelKey: "gen_adv_h2", type: "number" },
			{ key: "H3", labelKey: "gen_adv_h3", type: "number" },
			{ key: "H4", labelKey: "gen_adv_h4", type: "number" },
		],
	},
	{
		id: "signatures",
		titleKey: "gen_adv_group_signatures",
		fields: [
			{ key: "I1", labelKey: "gen_adv_i1", type: "long-text" },
			{ key: "I2", labelKey: "gen_adv_i2", type: "long-text" },
			{ key: "I3", labelKey: "gen_adv_i3", type: "long-text" },
			{ key: "I4", labelKey: "gen_adv_i4", type: "long-text" },
			{ key: "I5", labelKey: "gen_adv_i5", type: "long-text" },
		],
	},
	{
		id: "mimicry",
		titleKey: "gen_adv_group_mimicry",
		hintKey: "gen_adv_mimicry_hint",
		fields: [
			{ key: "HeaderProtectionKey", labelKey: "gen_adv_hpk", type: "text", hintKey: "gen_adv_hpk_desc", generator: "hpk" },
			{ key: "ContentPaddingAddition", labelKey: "gen_adv_cpa", type: "text", hintKey: "gen_adv_cpa_desc" },
		],
	},
	{
		id: "timings",
		titleKey: "gen_adv_group_timings",
		hintKey: "gen_adv_timings_hint",
		fields: [
			{ key: "RekeyAfterTime", labelKey: "gen_adv_rekey_after", type: "text" },
			{ key: "RekeyTimeout", labelKey: "gen_adv_rekey_timeout", type: "text" },
			{ key: "RejectAfterTime", labelKey: "gen_adv_reject_after", type: "text" },
			{ key: "KeepaliveTimeout", labelKey: "gen_adv_keepalive_timeout", type: "text" },
			{ key: "MaxHandshakeAttempts", labelKey: "gen_adv_max_handshake", type: "text" },
		],
	},
]

export function vpnNextDefault() {
	return {
		Jc: "3",
		Jmin: "1",
		Jmax: "3",
		S1: "0",
		S2: "0",
		S3: "0",
		S4: "0",
		H1: "1",
		H2: "2",
		H3: "3",
		H4: "4",
		I1: DEFAULT_I1,
		I2: "",
		I3: "",
		I4: "",
		I5: "",
	}
}

/**
 * Presets offered in the UI. `labelKey` points at the translation entry, so no
 * user-facing text lives here. The junk figures match `applyJunkPreset` in the
 * Android view model.
 */
export const OBFUSCATION_PRESETS = [
	{ id: "none", labelKey: "gen_obf_none", descriptionKey: "gen_obf_none_desc", params: () => ({}) },
	{
		id: "preset-off",
		labelKey: "gen_obf_off",
		descriptionKey: "gen_obf_off_desc",
		params: () => ({ ...vpnNextDefault(), Jc: "0", Jmin: "0", Jmax: "0" }),
	},
	{
		id: "vpn-next-default",
		labelKey: "gen_obf_low",
		descriptionKey: "gen_obf_low_desc",
		params: () => vpnNextDefault(),
	},
	{
		id: "preset-medium",
		labelKey: "gen_obf_medium",
		descriptionKey: "gen_obf_medium_desc",
		params: () => ({ ...vpnNextDefault(), Jc: "10", Jmin: "50", Jmax: "100" }),
	},
	{
		id: "preset-high",
		labelKey: "gen_obf_high",
		descriptionKey: "gen_obf_high_desc",
		params: () => ({ ...vpnNextDefault(), Jc: "20", Jmin: "400", Jmax: "800" }),
	},
]

export function presetById(id) {
	return OBFUSCATION_PRESETS.find((preset) => preset.id === id) ?? OBFUSCATION_PRESETS[0]
}

/** Every field the advanced editor can write, in file order. */
export function advancedFieldKeys() {
	return ADVANCED_GROUPS.flatMap((group) => group.fields.map((field) => field.key))
}

/**
 * Starting point for the advanced editor: the chosen preset's values, with the
 * AmneziaWG 2.0 fields left blank so nothing unexpected lands in the file.
 */
export function advancedFromPreset(presetId) {
	const base = presetById(presetId).params()
	const params = { ...vpnNextDefault(), ...base }
	for (const key of ADVANCED_KEY_ORDER) params[key] = ""
	return params
}

/**
 * A base64 32-byte key for header protection, matching
 * `generateHeaderProtectionKey` in the Android view model.
 */
export function generateHeaderProtectionKey(randomSource = (buffer) => globalThis.crypto.getRandomValues(buffer)) {
	const bytes = randomSource(new Uint8Array(32))
	let binary = ""
	for (const byte of bytes) binary += String.fromCharCode(byte)
	return btoa(binary)
}

/** Parses `"jc=1, jmax=10"` or a preset name, mirroring `parse_awg_string`. */
export function parseAwgString(value) {
	const trimmed = (value ?? "").trim()
	if (!trimmed) return {}

	const known = OBFUSCATION_PRESETS.find((preset) => preset.id === trimmed.toLowerCase())
	if (known) return known.params()
	if (trimmed.toLowerCase() === "preset-low") return vpnNextDefault()

	const params = {}
	for (const part of trimmed.split(",")) {
		if (!part.includes("=")) continue
		const [key, ...rest] = part.split("=")
		const name = key.trim()
		if (!name) continue
		params[name.charAt(0).toUpperCase() + name.slice(1).toLowerCase()] = rest.join("=").trim()
	}
	return params
}

/** Drops empty values and returns the parameters in `.conf` order. */
export function orderedAwgEntries(params) {
	const entries = []
	const ordered = [...AWG_KEY_ORDER, ...ADVANCED_KEY_ORDER]

	for (const key of ordered) {
		const value = params[key]
		if (value === undefined || value === null || value === "") continue
		entries.push([key, String(value)])
	}
	for (const [key, value] of Object.entries(params)) {
		if (ordered.includes(key)) continue
		if (value === undefined || value === null || value === "") continue
		entries.push([key, String(value)])
	}
	return entries
}
