import asyncio
import os
import random
import sys,json
from telegram import Bot,InputMediaDocument
from telegram.constants import ParseMode

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
RUN_URL = os.environ.get("RUN_URL")
TITLE = os.environ.get("TITLE")
VERSION = os.environ.get("VERSION")
BRANCH = os.environ.get("BRANCH")

GITHUB_EVENT = json.loads(open(os.environ.get("GITHUB_EVENT_PATH"), "r").read())

commit_message = ''
commit_line = ''
try:
    if 'commits' in GITHUB_EVENT:
        commits = GITHUB_EVENT['commits']
        commit_message = ''
        i = len(commits)
        for commit in commits[::-1]:
            msg_line = commit['message'].split('\n')
            msg = commit['message'].strip()
            msg += ' by ' + commit['author']['username']
            if len(msg) + 1 + len(commit_message) > 3192:
                commit_message = f'(other {i} commits)\n{commit_message}'
                break
            else:
                commit_message = f'{msg}\n{commit_message}'
            i -= 1
        commit_message = f'{commit_message.strip()}'
        last_commit = commits[-1]

    elif 'head_commit' in GITHUB_EVENT:
        msg = GITHUB_EVENT["head_commit"]["msg"]
        if len(msg) > 3192:
            msg = msg[:3189] + '...'
        commit_message = f'{msg.strip()}'
    else:
        commit_message = f'(no commit message)'
except:
    from traceback import print_exc
    print_exc()

if 'compare' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['compare']
    commit_line = '<a href="' + commit_url + '">Compare</a>'
elif 'head_commit' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['head_commit']['url']
    commit_line = '<a href="' + commit_url + '">Commit</a>'
else:
    commit_line = ''

MSG_TEMPLATE = """
<b>{title}</b>
Branch: {branch}
#ci_{version}
<pre>
{commit_message}
</pre>
{commit_line}
<a href="{run_url}">Workflow run</a>
""".strip()

def escape_telegram_html(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&#39;")

def get_caption():
    msg = MSG_TEMPLATE.format(
        title=TITLE,
        branch=BRANCH,
        version=VERSION,
        commit_message=escape_telegram_html(commit_message),
        commit_line=commit_line,
        run_url=RUN_URL,
    )
    return msg

def get_caption_for_debug():
    msg = MSG_TEMPLATE.format(
        title=f"{TITLE}-Debug",
        branch=BRANCH,
        version=VERSION,
        commit_message=escape_telegram_html(commit_message),
        commit_line=commit_line,
        run_url=RUN_URL,
    )
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

async def send_media_group(bot: Bot, chat_id: int, media: list, message_thread_id=None):
    await asyncio.sleep(random.uniform(0.2, 0.8))
    return await bot.send_media_group(chat_id=chat_id, media=media, message_thread_id=message_thread_id,
                                   read_timeout=350,write_timeout=350,connect_timeout=350,pool_timeout=350)

async def main():
    print("[+] Uploading to telegram")
    check_environ()
    files = sys.argv[1:]
    print("[+] Files:", files)
    if len(files) <= 0:
        print("[-] No files to upload")
        exit(1)
    print("[+] Logging in Telegram with bot")
    no_caption=False
    bot = Bot(token=BOT_TOKEN)
    caption = get_caption()
    caption_debug = get_caption_for_debug()
    if len(caption) > 1024 or len(caption_debug) > 1024:
        print("[-] Caption is too long,so it will be sent as a separate message without caption for files")
        no_caption = True
    upload_release_files = []
    upload_debug_files = []

    for index, file in enumerate(files):
        if os.path.basename(file).find("debug") != -1:
            # If the filename contains "debug", treat it as a debug file and add caption to it
            upload_debug_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file), caption=f"{caption_debug if not no_caption else '<b>DEBUG Manager</b>'}", parse_mode=ParseMode.HTML))
            continue
        elif index == len(files) - 1:
            # Only add caption to the last file
            upload_release_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file), caption=f"{caption if not no_caption else '<b>Release Manager</b>'}", parse_mode=ParseMode.HTML))
            continue
        upload_release_files.append(InputMediaDocument(media=open(file, "rb"), filename=os.path.basename(file)))

    print("[+] Caption: ")
    print("---")
    print(caption)
    print("---")
    print("[+] Sending")
    if no_caption:
        await bot.send_message(chat_id=CHAT_ID, text=caption, parse_mode=ParseMode.HTML, message_thread_id=MESSAGE_THREAD_ID, disable_web_page_preview=True)
    if len(upload_debug_files) > 0:
        await send_media_group(bot=bot, chat_id=CHAT_ID, media=upload_debug_files, message_thread_id=MESSAGE_THREAD_ID)
    print("[+] Debug files uploaded,starting to upload release files")
    if len(upload_release_files) > 0:
        await send_media_group(bot=bot, chat_id=CHAT_ID, media=upload_release_files, message_thread_id=MESSAGE_THREAD_ID)
    print("[+] Done!")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
