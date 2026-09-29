"""Real Android UI + HTTP smoke. Run only on a disposable emulator with --reset-app.

Requires Python 3.10+, adb, installed debug APK, and local demo server. No pip packages.
Saves real device screenshots and a machine-readable result; never prints auth tokens.
"""
import argparse
import json
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.lxy.nearcircle"
parser = argparse.ArgumentParser()
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--server", default="http://127.0.0.1:8080")
parser.add_argument("--device-server", default="http://10.0.2.2:8080")
parser.add_argument("--reset-app", action="store_true", required=True, help="Clear ONLY NearCircle test app data on this emulator")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("This destructive test fixture reset is restricted to emulators.")

def adb(*parts, binary=False):
    result = subprocess.run([args.adb, "-s", args.serial, *parts], capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

def api(path, token=None, body=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(args.server + "/api" + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response)

def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/near-e2e.xml")
    return list(ET.fromstring(adb("exec-out", "cat", "/sdcard/near-e2e.xml")).iter("node"))

def find(value, attr="text", timeout=25):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        for node in nodes():
            actual=node.get(attr,"")
            matches=actual==value or (attr=="text" and actual.removesuffix("  ›")==value)
            if matches and node.get("enabled")=="true":
                bounds=list(map(int,re.findall(r"\d+",node.get("bounds",""))))
                if len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]:return node
        time.sleep(.4)
    raise AssertionError(f"UI element missing: {attr}={value}")

def tap(value, attr="text"):
    node=find(value,attr);x1,y1,x2,y2=map(int,re.findall(r"\d+",node.get("bounds")))
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))

def fill(resource, text):
    tap(PACKAGE+":id/"+resource,"resource-id")
    adb("shell","input","keyevent","KEYCODE_MOVE_END")
    adb("shell","input","keyevent","--longpress","KEYCODE_DEL")
    adb("shell","input","text",text.replace(" ","%s"))
    adb("shell","input","keyevent","4")

def shot(name):
    folder=ROOT/"docs"/"screenshots";folder.mkdir(parents=True,exist_ok=True)
    (folder/(name+".png")).write_bytes(adb("exec-out","screencap","-p",binary=True))

checks=[]
def passed(name):
    checks.append(name);print("PASS:",name,flush=True)

def wait_sent(peer,token,marker,timeout=65):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        items=api(f"/messages/{peer}",token)["items"]
        found=[x for x in items if x["body"]==marker]
        if found:
            assert len(found)==1
            return found[0]
        time.sleep(1)
    raise AssertionError("Message did not reach peer: "+marker)

def network(enabled):
    value="enable" if enabled else "disable"
    adb("shell","svc","wifi",value);adb("shell","svc","data",value)

marker="Android_"+str(int(time.time()))
lin=api("/auth/login",body={"username":"lin","password":"NearCircle2026!"})
zhou=api("/auth/login",body={"username":"zhou","password":"NearCircle2026!"})
lin_id,zhou_id=lin["user"]["id"],zhou["user"]["id"]
post_id=None
try:
    network(True)
    if PACKAGE not in adb("shell","pm","list","packages",PACKAGE):
        adb("install","-r",str(ROOT/"android/app/build/outputs/apk/debug/app-debug.apk"))
    adb("shell","pm","clear",PACKAGE)
    adb("shell","am","start","-n",PACKAGE+"/.ui.LoginActivity")
    find("登录近友");shot("login")
    fill("login_username","lin");fill("login_password","NearCircle2026!")
    if args.device_server!="http://10.0.2.2:8080":raise ValueError("Non-default device server requires manual login setup for this smoke script")
    tap("登录近友");find("最近联系");passed("native login and conversation list");shot("messages")
    tap("好友","content-desc");find("周予");shot("friends")
    tap("周予");find(PACKAGE+":id/chat_input","resource-id")
    fill("chat_input",marker);tap("发送")
    wait_sent(lin_id,zhou["token"],marker);passed("Room outbox -> HTTP -> peer exactly once")
    api(f"/messages/{lin_id}",zhou["token"],{"client_id":marker+"-reply","body":"收到，周末见！"})
    find("收到，周末见！");shot("chat");passed("WebSocket refresh and incoming HTTP catch-up")
    network(False)
    offline=marker+"_offline";fill("chat_input",offline);tap("发送")
    find("等待发送 · 联网后自动重试");shot("offline")
    adb("shell","am","force-stop",PACKAGE)
    adb("shell","am","start","-n",PACKAGE+"/.ui.LoginActivity")
    # The conversation list is remote; return via the same non-exported activity is forbidden.
    # Restore network and let startup WorkManager recover the durable queue.
    network(True)
    find("最近联系",timeout=45)
    wait_sent(lin_id,zhou["token"],offline);passed("offline queue survives process stop and resumes after reopening")
    tap("朋友圈","content-desc");find("朋友的日常");shot("moments")
    tap("添加好友或发布朋友圈","content-desc");find("记录此刻")
    fill("post_body",marker+"_moment");tap("发布")
    find(marker+"_moment");passed("native publish -> friend-visible feed")
    post=next(p for p in api("/posts",zhou["token"])["items"] if p["body"]==marker+"_moment");post_id=post["id"]
    tap("点赞","content-desc");tap("评论")
    tap("写一条评论");adb("shell","input","text","Nice_day");adb("shell","input","keyevent","4");tap("发送")
    find("林间：Nice_day");passed("like and comment persist")
    tap("我","content-desc");find("编辑个人资料");shot("profile")
    adb("shell","settings","put","system","accelerometer_rotation","0")
    adb("shell","settings","put","system","user_rotation","1")
    # The edit row is below the fold in landscape; assert the profile itself, not its scroll position.
    find("林");find("我");adb("shell","settings","put","system","user_rotation","0")
    find("编辑个人资料");passed("activity recreation preserves selected tab")
    result={"status":"passed","checks":checks,"device":args.serial,"api":adb("shell","getprop","ro.build.version.sdk").strip()}
    (ROOT/"docs"/"e2e-result.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
finally:
    network(True)
    adb("shell","settings","put","system","user_rotation","0")
    if post_id is not None:api(f"/posts/{post_id}",lin["token"],method="DELETE")
