# AI Changelog Integration Plan

I will implement a Python script to automatically generate release changelogs using Google's Gemini AI. The script will be integrated into the existing Woodpecker CI pipeline.

## Proposed Changes

### [Component: CI/CD Scripts]

#### [NEW] [generate_ai_changelog.py](file:///home/smh01/StudioProjects/ProtonVPN-Next/scripts/generate_ai_changelog.py)
A new Python script that will:
1.  Identify the current tag and the previous tag using `git`.
2.  Extract the commit history between these tags.
3.  Format a prompt for the Gemini AI using the template provided.
4.  Call the Gemini API (using `google-generativeai` library).
5.  Save the generated changelog to `changelog.txt` for use in the Woodpecker release step.

## Verification Plan

### Manual Verification
1.  I will verify the Python script's logic by mocking the environment variables and checking the generated `git log` commands.
2.  I will provide the user with the script so they can test it in their CI environment.

> [!NOTE]
> The script assumes that `GEMINI_API_KEY` is set in Woodpecker secrets.
