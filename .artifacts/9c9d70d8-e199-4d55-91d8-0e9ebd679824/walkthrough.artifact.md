# AI Changelog Integration Walkthrough

I have implemented the AI-driven changelog generation for the ProtonVPN-Next project.

## Changes Made

### 1. New AI Changelog Script
- Created [generate_ai_changelog.py](file:///home/smh01/StudioProjects/ProtonVPN-Next/scripts/generate_ai_changelog.py).
- This script uses the Google Gemini API to generate a professional changelog based on the commit history between the current tag and the previous one.
- It relies entirely on the `CHANGELOG_PROMPT` environment variable for the instruction set, ensuring that the logic remains in your CI configuration rather than the codebase.
- It automatically replaces the `[TAG_NAME]` placeholder in your prompt with the actual tag being released.

### 2. CI Pipeline Configuration
- Updated [.woodpecker.yml](file:///home/smh01/StudioProjects/ProtonVPN-Next/.woodpecker.yml).
- Added `git fetch --tags` to the `generate-changelog` step to ensure the script can accurately identify previous tags.
- Verified that the `generate-changelog` step runs before the `publish-release` and `publish-pre-release` steps, which consume the generated `changelog.txt`.

## How to use

1.  **Secrets**: Ensure `GEMINI_API_KEY` is added to your Woodpecker repository secrets.
2.  **Optional Secrets**:
    -   `GEMINI_MODEL`: Set this if you want to use a specific model (e.g., `gemini-1.5-pro`). Default is `gemini-1.5-flash`.
    -   `CHANGELOG_PROMPT`: Set this if you want to override the default prompt. You can use `[TAG_NAME]` as a placeholder.

## Verification
- The script logic was verified to correctly identify tags and generate commit logs using standard `git` commands.
- The pipeline configuration ensures that `changelog.txt` is passed to the release plugins as expected.

> [!IMPORTANT]
> Make sure the Gemini API key has sufficient permissions and quota for your release frequency.
