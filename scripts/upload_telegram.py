import os
import asyncio
import glob
from telethon import TelegramClient
from telethon.sessions import StringSession

# Environment variables from Woodpecker secrets
API_ID = int(os.environ.get('TG_API_ID', 0))
API_HASH = os.environ.get('TG_API_HASH')
# Use Bot Token instead of User Session
BOT_TOKEN = os.environ.get('TG_BOT_TOKEN')
TG_CHAT_ID = os.environ.get('TG_CHAT_ID', '0')

# Try to parse CHAT_ID as int, otherwise keep as string (for usernames)
try:
    CHAT_ID = int(TG_CHAT_ID)
except ValueError:
    CHAT_ID = TG_CHAT_ID

# Woodpecker default environment variables
COMMIT_MESSAGE = os.environ.get('CI_COMMIT_MESSAGE', 'No message')
COMMIT_AUTHOR = os.environ.get('CI_COMMIT_AUTHOR', 'Unknown')
COMMIT_SHA = os.environ.get('CI_COMMIT_SHA', 'none')[:8]
REPO_NAME = os.environ.get('CI_REPO', 'ProtonVPN-Next')
BRANCH = os.environ.get('CI_COMMIT_BRANCH', 'unknown')
EVENT = os.environ.get('CI_PIPELINE_EVENT', 'manual')
TAG = os.environ.get('CI_COMMIT_TAG')

async def main():
    if not all([API_ID, API_HASH, BOT_TOKEN, CHAT_ID]):
        print("Error: Missing Telegram configuration secrets (API_ID, API_HASH, BOT_TOKEN, TG_CHAT_ID)!")
        return

    # Find all APK files (release and debug)
    apk_files = glob.glob('app/build/outputs/apk/**/*.apk', recursive=True)
    if not apk_files:
        print("Error: No APK files found!")
        return

    print(f"Connecting to Telegram via Bot and uploading {len(apk_files)} APK(s)...")

    # Use an empty StringSession to keep the session strictly in-memory
    client = TelegramClient(StringSession(''), API_ID, API_HASH)

    # Authenticate using the Bot Token
    await client.start(bot_token=BOT_TOKEN)

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
            print(f"Uploading {file_name} to {CHAT_ID}...")
            # We try to send directly using the CHAT_ID (int or str)
            await client.send_file(
                CHAT_ID,
                apk_path,
                caption=caption,
                parse_mode='md'
            )
            print(f"Upload successful: {file_name}")
        except Exception as e:
            print(f"Error during upload of {file_name}: {e}")
            if isinstance(CHAT_ID, int) and CHAT_ID > 0:
                print(f"💡 IMPORTANT: Your Chat ID {CHAT_ID} looks like a Channel ID.")
                print(f"Try changing TG_CHAT_ID to -100{CHAT_ID} in your Woodpecker CI secrets.")
            print("\n💡 Troubleshooting:")
            print("1. If CHAT_ID is a user, they MUST send /start to the bot first.")
            print("2. If CHAT_ID is a channel/group, ensure it starts with '-' or '-100'.")
            print("3. Ensure the Bot is an administrator in the channel/group with 'Post Messages' permission.")

    await client.disconnect()

if __name__ == '__main__':
    asyncio.run(main())