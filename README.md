# 净链 CleanLink

只在 Android 分享菜单里出现的工具：把分享文案里的链接捞出来，按平台规则洗掉跟踪参数，
弹一张卡片让你看一眼，复制或者直接发回去。全程不需要「打开 App」。

## 现在能跑的东西

- 分享菜单入口（`ACTION_SEND` / `text/plain`），透明浮层，不跳页面
- 规则库 `app/src/main/assets/rules.json`：15 组域名 + 通用兜底，**保留清单优先于删除清单**
- 卡片模式 / 静默复制模式（桌面图标里切换）
- 短链手动展开（点了才联网，只发一个请求）
- 口令识别（直说「这是口令，不是链接」，不假装能处理）
- 最近 100 条记录，只存在本机 DataStore
- 应用内更新：启动时静默查一次（12 小时限频），有新版本时页脚展开成一张卡，
  下载 → 校验 SHA256 → 交给系统安装器

## 构建

需要 JDK 17 + Android SDK（platform 36 / build-tools 36.0.0）。

```bash
./gradlew :app:testDebugUnitTest     # 规则 20 条 + 更新清单 10 条
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease       # 已用 app/keystore/cleanlink.jks 签名
```

**debug 与 release 用同一张证书**（`app/keystore/cleanlink.jks`），否则两个包互相
覆盖不了，应用内更新下载完也装不上。改签名等于让所有人卸载重装一次。

装机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 应用内更新

更新源是 GitHub 仓库根目录的 `update.json`，App 依次尝试直连和镜像
（见 `UpdateConfig.MANIFEST_URLS`）。清单结构：

```json
{
  "code": 3,                       // versionCode，必须大于手机里装的
  "name": "1.0.2",
  "notes": "一句话说明，显示在卡片上",
  "size": 1468006,
  "sha256": "…",                   // 下完必须对上，否则换下一个地址重试
  "signer": "…",                   // 证书摘要，防止装到别的渠道签的包
  "urls": ["…", "…"]
}
```

发一版：

```bash
./gradlew :app:assembleDebug
python tools/release.py --apk app/build/outputs/apk/debug/app-debug.apk --notes "改了什么"
gh release create v1.0.2 dist/cleanlink-1.0.2.apk --title "净链 1.0.2" --notes "改了什么"
git add update.json && git commit -m "release 1.0.2" && git push
```

`release.py` 会自动读版本号、大小、SHA256 和签名摘要，不用手填。

**能做到的静默极限**：不 root 的情况下系统不允许应用自己装 APK
（`INSTALL_PACKAGES` 是 signature 级权限），所以流程是「应用内点更新 →
系统安装页点更新」，两步。真静默需要 Shizuku 或 root。

## 规则库怎么改

判定顺序不能反：

1. 按 host 后缀匹配 `domains`，最长 host 优先（`item.taobao.com` → `taobao.com` 那条）
2. 命中规则里 `keep` 的参数一律保留，哪怕它也在 `globalDrop` 里
3. 剩下的参数命中 `globalDrop` / `globalDropPrefix` → 删
4. 剩下的参数**默认保留**（宁可多留一个跟踪参数，也不能删掉内容定位）
5. host 命中 `shortHosts` → 不动手，标「短链，未展开」

改完跑一次 `testDebugUnitTest`，`CleanerTest` 里放的都是从手机分享菜单里原样拷出来的文案。

## 明确不做

- 口令解析（不是链接，是平台私有协议，离线解不出）
- 短链自动展开（会引入常驻联网口）
- 账号 / 云同步 / 统计 / 自定义规则编辑器
- 浏览器插件

## 环境

这台机器上工具链在 `D:\android-dev`（JDK 17 / SDK / Gradle / 便携版 Android Studio），
工程里的 `local.properties` 已指向 `D:/android-dev/sdk`。
