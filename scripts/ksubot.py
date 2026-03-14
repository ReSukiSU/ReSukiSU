import asyncio
import os
import sys
from telegram import Bot,InputMediaDocument
from telegram.constants import ParseMode
from telegram.error import BadRequest

API_ID = 611335
API_HASH = "d524b414d21f4d37f08684c1df41ac9c"


BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
COMMIT_URL = os.environ.get("COMMIT_URL")
COMMIT_MESSAGE = os.environ.get("COMMIT_MESSAGE")
RUN_URL = os.environ.get("RUN_URL")
TITLE = os.environ.get("TITLE")
VERSION = os.environ.get("VERSION")
BRANCH = os.environ.get("BRANCH")
MSG_TEMPLATE = """
**{title}**
Branch: {branch}
#ci_{version}
```
{commit_message}
```
[Commit]({commit_url})
[Workflow run]({run_url})
""".strip()


def get_caption():
    msg = MSG_TEMPLATE.format(
        title=TITLE,
        branch=BRANCH,
        version=VERSION,
        commit_message=COMMIT_MESSAGE,
        commit_url=COMMIT_URL,
        run_url=RUN_URL,
    )
    if len(msg) > 1024:
        return COMMIT_URL
    return msg

def get_caption_for_debug():
    msg = MSG_TEMPLATE.format(
        title=f"{TITLE}-Debug",
        branch=BRANCH,
        version=VERSION,
        commit_message=COMMIT_MESSAGE,
        commit_url=COMMIT_URL,
        run_url=RUN_URL,
    )
    if len(msg) > 1024:
        return COMMIT_URL
    return msg

def check_environ():
    global CHAT_ID, MESSAGE_THREAD_ID
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)
    else:
        try:
            CHAT_ID = int(CHAT_ID)
        except:
            pass
    if COMMIT_URL is None:
        print("[-] Invalid COMMIT_URL")
        exit(1)
    if COMMIT_MESSAGE is None:
        print("[-] Invalid COMMIT_MESSAGE")
        exit(1)
    if RUN_URL is None:
        print("[-] Invalid RUN_URL")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if VERSION is None:
        print("[-] Invalid VERSION")
        exit(1)
    if BRANCH is None:
        print("[-] Invalid BRANCH")
        exit(1)
    if MESSAGE_THREAD_ID and MESSAGE_THREAD_ID != "":
        try:
            MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID)
        except:
            print("[-] Invalid MESSAGE_THREAD_ID")
            exit(1)
    else:
        MESSAGE_THREAD_ID = None


async def main():
    print("[+] Uploading to telegram")
    check_environ()
    files = sys.argv[1:]
    print("[+] Files:", files)
    if len(files) <= 0:
        print("[-] No files to upload")
        exit(1)
    print("[+] Logging in Telegram with bot")
    bot = Bot(token=BOT_TOKEN)
    caption = get_caption()
    upload_release_files = []
    upload_debug_files = []
    for index, file in enumerate(files):
        if os.path.basename(file).find("debug") != -1:
            # If the filename contains "debug", treat it as a debug file and add caption to it
            upload_debug_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file), caption=get_caption_for_debug(), parse_mode=ParseMode.MARKDOWN))
            continue
        if index == len(files) - 1:
            # Only add caption to the last file
            upload_release_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file), caption=caption, parse_mode=ParseMode.MARKDOWN))
            continue
        upload_release_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file)))
    print("[+] Caption: ")
    print("---")
    print(caption)
    print("---")
    print("[+] Sending")
    await bot.send_media_group(chat_id=CHAT_ID, media=upload_release_files, message_thread_id=MESSAGE_THREAD_ID)
    print("[+] Release files uploaded, now uploading debug files (if any)")
    if len(upload_debug_files) > 0:
        await bot.send_media_group(chat_id=CHAT_ID, media=upload_debug_files, message_thread_id=MESSAGE_THREAD_ID)
    print("[+] Done!")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
