import os
import asyncio
import glob
from telethon import TelegramClient
from telethon.sessions import StringSession

# Environment variables from Woodpecker secrets
API_ID = int(os.environ.get('TG_API_ID', 0))
API_HASH = os.environ.get('TG_API_HASH')
SESSION = os.environ.get('TG_SESSION')
CHAT_ID = int(os.environ.get('TG_CHAT_ID', 0))

# Woodpecker default environment variables
COMMIT_MESSAGE = os.environ.get('CI_COMMIT_MESSAGE', 'No message')
COMMIT_AUTHOR = os.environ.get('CI_COMMIT_AUTHOR', 'Unknown')
COMMIT_SHA = os.environ.get('CI_COMMIT_SHA', 'none')[:8]
REPO_NAME = os.environ.get('CI_REPO', 'ProtonVPN-Next')
BRANCH = os.environ.get('CI_COMMIT_BRANCH', 'unknown')
EVENT = os.environ.get('CI_PIPELINE_EVENT', 'manual')
TAG = os.environ.get('CI_COMMIT_TAG')

async def main():
    if not all([API_ID, API_HASH, SESSION, CHAT_ID]):
        print("Error: Missing Telegram configuration secrets!")
        return

    # Find all APK files (release and debug)
    apk_files = glob.glob('app/build/outputs/apk/**/*.apk', recursive=True)
    if not apk_files:
        print("Error: No APK files found!")
        return

    print(f"Connecting to Telegram and uploading {len(apk_files)} APK(s)...")

    client = TelegramClient(StringSession(SESSION), API_ID, API_HASH)
    await client.connect()

    for apk_path in apk_files:
        file_name = os.path.basename(apk_path)
        is_debug = "debug" in apk_path.lower()
        build_type = "DEBUG" if is_debug else "RELEASE"

        tag_str = f"\n🏷️ **Tag:** `{TAG}`" if TAG else ""

        caption = (
            f"🚀 **New Build ({build_type}): {REPO_NAME}**\n\n"
            f"📝 **Commit:** {COMMIT_MESSAGE}\n"
            f"👤 **Author:** {COMMIT_AUTHOR}\n"
            f"🌿 **Branch:** `{BRANCH}`{tag_str}\n"
            f"🔢 **Hash:** `{COMMIT_SHA}`\n"
            f"⚡ **Event:** `{EVENT.upper()}`"
        )

        try:
            print(f"Uploading {file_name}...")
            await client.send_file(
                CHAT_ID,
                apk_path,
                caption=caption,
                parse_mode='md'
            )
            print(f"Upload successful: {file_name}")
        except Exception as e:
            print(f"Error during upload of {file_name}: {e}")

    await client.disconnect()

if __name__ == '__main__':
    asyncio.run(main())
