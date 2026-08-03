/**
 * Country flags.
 *
 * The SVGs in `public/flags` are converted straight from the Android client's
 * vector drawables by `scripts/convert_flags.py`, so the site shows exactly the
 * same artwork as the app, including the dedicated "fastest" badge.
 *
 * Proton reports a few exit codes that are not ISO country codes; those have no
 * drawable in the app either, so they fall back to the neutral badge rather
 * than rendering a broken image.
 */

/** Identifier used for the "fastest server" artwork (`flag_fastest.xml`). */
export const FASTEST_FLAG = "fastest"

const FLAG_DIRECTORY = "/flags"

/** Path to a flag asset. Codes are lower-cased the way the drawables are named. */
export function flagUrl(code) {
	const normalised = String(code ?? "").trim().toLowerCase()
	if (!normalised) return `${FLAG_DIRECTORY}/${FASTEST_FLAG}.svg`
	return `${FLAG_DIRECTORY}/${normalised}.svg`
}

/**
 * Flag `<img>` sized like `FlagIcon` in the app: a 30x20 rounded rectangle with
 * the artwork centre-cropped, which keeps every flag the same size regardless
 * of its own aspect ratio.
 */
export function flagImage(code, alt = "", className = "flag") {
	const image = document.createElement("img")
	image.className = className
	image.src = flagUrl(code)
	image.alt = alt
	image.loading = "lazy"
	image.decoding = "async"
	image.width = 30
	image.height = 20

	// Unknown or non-ISO exit codes must not leave a broken image behind.
	image.addEventListener(
		"error",
		() => {
			image.src = flagUrl(FASTEST_FLAG)
		},
		{ once: true },
	)

	if (!alt) image.setAttribute("aria-hidden", "true")
	return image
}
