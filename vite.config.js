import { defineConfig } from "vite"
import tailwindcss from "@tailwindcss/vite"

// The Cloudflare Workers asset directory points at `dist`, so the build output
// stays there. Everything under `public/` (update.json, the WAP page, images)
// is copied verbatim and must keep its paths.
export default defineConfig({
	plugins: [tailwindcss()],
	publicDir: "public",
	build: {
		outDir: "dist",
		emptyOutDir: true,
		target: "es2022",
	},
})
