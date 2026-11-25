#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发版：把编译好的 APK 变成一份 update.json，配合 GitHub Release 使用。

用法（在 cleanlink 目录下）：

    python tools/release.py --apk app/build/outputs/apk/debug/app-debug.apk \
        --notes "更新检查不再联网展开短链"

产物：
    dist/update.json
    dist/cleanlink-<版本>.apk        （ASCII 文件名，避免 URL 编码问题）

之后：
    gh release create v<版本> dist/cleanlink-<版本>.apk \
        --title "净链 <版本>" --notes "<说明>"
    git add update.json && git commit -m "release <版本>" && git push

update.json 在仓库根目录，App 从 raw / 镜像拉它。
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

OWNER = "lueyoyo1-afk"
REPO = "cleanlink"

SDK = os.environ.get("ANDROID_HOME") or r"D:\android-dev\sdk"
BUILD_TOOLS = os.environ.get("BUILD_TOOLS_VERSION", "36.0.0")
JAVA_HOME = os.environ.get("JAVA_HOME") or r"D:\android-dev\jdk-17.0.21.1+2"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")


def build_tools() -> str:
    return os.path.join(SDK, "build-tools", BUILD_TOOLS)


def exe(name: str) -> str:
    return name + (".exe" if os.name == "nt" else "")


def run(cmd, **kw):
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + env.get("PATH", "")
    out = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                         errors="replace", env=env, **kw)
    if out.returncode != 0:
        sys.exit(f"命令失败：{' '.join(cmd)}\n{out.stdout}\n{out.stderr}")
    return (out.stdout or "") + (out.stderr or "")


def badging(apk: str):
    # 用 aapt2.exe 而不是 bat：CreateProcess 起不了 .bat 包装
    text = run([os.path.join(build_tools(), exe("aapt2")), "dump", "badging", apk])
    line = next((l for l in text.splitlines() if l.startswith("package:")), None)
    if not line:
        sys.exit("读不到 APK 的 package 信息")
    code = int(re.search(r"versionCode='(\d+)'", line).group(1))
    name = re.search(r"versionName='([^']*)'", line).group(1)
    return code, name


def signer_digest(apk: str) -> str:
    jar = os.path.join(build_tools(), "lib", "apksigner.jar")
    text = run([os.path.join(JAVA_HOME, "bin", exe("java")), "-jar", jar,
                "verify", "--print-certs", apk])
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", text)
    if not m:
        sys.exit("读不到签名证书摘要，确认 APK 已签名")
    return m.group(1).lower()


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apk", required=True, help="编译产物路径")
    p.add_argument("--notes", default="", help="一句话更新说明，显示在卡片上")
    p.add_argument("--tag", default="", help="发布标签，默认 v<版本号>")
    p.add_argument("--out", default="", help="update.json 输出位置，默认仓库根目录")
    args = p.parse_args()

    apk = args.apk if os.path.isabs(args.apk) else os.path.join(ROOT, args.apk)
    if not os.path.exists(apk):
        sys.exit(f"找不到 {apk}")

    code, name = badging(apk)
    tag = args.tag or f"v{name}"
    asset = f"cleanlink-{name}.apk"

    os.makedirs(DIST, exist_ok=True)
    target = os.path.join(DIST, asset)
    if os.path.abspath(apk) != os.path.abspath(target):
        shutil.copyfile(apk, target)

    base = f"https://github.com/{OWNER}/{REPO}/releases/download/{tag}/{asset}"
    manifest = {
        "code": code,
        "name": name,
        "notes": args.notes.strip(),
        "size": os.path.getsize(target),
        "sha256": sha256_file(target),
        "signer": signer_digest(target),
        "urls": [base, f"https://ghfast.top/{base}"],
    }

    out = args.out or os.path.join(ROOT, "update.json")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(f"\n清单：{out}")
    print(f"资产：{target}")
    print(f"\n下一步：\n  gh release create {tag} \"{target}\" --title \"净链 {name}\" --notes \"{args.notes}\"")


if __name__ == "__main__":
    main()
