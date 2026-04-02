# Website Implementation Plan

The goal is to create a landing page for ProtonVPN-Next in a separate branch, matching the app's design.

## User Review Required

- Confirm if the branch should only contain the website or if it's okay to keep the hidden project folders (e.g., .idea, .gradle). I've currently cleared the visible source files in the `website` branch.
- Any specific images/screenshots to include? (I'll use placeholders for now or try to extract from the project).

## Proposed Changes

### Website Assets

I will add the following files to the `public/` directory of the `website` branch:

#### public/index.html
- Main structure of the landing page.
- Hero section with "Download" button.
- Feature highlights.
- Footer with links to Telegram and Codeberg.

#### public/style.css
- Styling following the Proton dark theme colors:
    - Primary: `#6D4AFF` (CornflowerBlue)
    - Background: `#1C1B24` (BalticSea)
    - Deep Background: `#0C0C14` (Cinder)
    - Success: `#007B58` (Apple)
- Glassmorphism effects for cards.
- Responsive design for mobile and desktop.

#### public/assets/
- Includes the app icon and hero image.

### Cloudflare Pages Deployment
- Add a `wrangler.toml` file for CLI-based deployment.
- Provide instructions for manual and automated (GitHub/GitLab) deployment.

## Verification Plan

### Manual Verification
- I will use the `browser_use` tool to preview the generated `index.html` and ensure it looks correct and matches the app's style.
- I'll verify the links and buttons (even if "Download" is currently a placeholder).
