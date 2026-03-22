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

    # Find the APK file
    apk_files = glob.glob('app/build/outputs/apk/release/*.apk')
    if not apk_files:
        print("Error: APK file not found!")
        return

    # Use the first APK found (usually only one if using standard assembleRelease)
    apk_path = apk_files[0]
    file_name = os.path.basename(apk_path)

    print(f"Connecting to Telegram and uploading {file_name}...")

    client = TelegramClient(StringSession(SESSION), API_ID, API_HASH)
    await client.connect()

    tag_str = f"\n🏷️ **Tag:** `{TAG}`" if TAG else ""

    caption = (
        f"🚀 **New Build: {REPO_NAME}**\n\n"
        f"📝 **Commit:** {COMMIT_MESSAGE}\n"
        f"👤 **Author:** {COMMIT_AUTHOR}\n"
        f"🌿 **Branch:** `{BRANCH}`{tag_str}\n"
        f"🔢 **Hash:** `{COMMIT_SHA}`\n"
        f"⚡ **Event:** `{EVENT.upper()}`"
    )

    try:
        await client.send_file(
            CHAT_ID,
            apk_path,
            caption=caption,
            parse_mode='md'
        )
        print("Upload successful!")
    except Exception as e:
        print(f"Error during upload: {e}")
    finally:
        await client.disconnect()

if __name__ == '__main__':
    asyncio.run(main())
