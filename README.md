# WiFi Beacon · wifiwarn

> Warns you when a **specific app comes to the foreground while you're connected to a specific WiFi network**. No root, no blocking — just a sound, a notification, and a full-screen overlay telling you to get back on task.

Use cases: a parent keeping an eye on games on the home WiFi; self-discipline on the office WiFi.

[English](#english) · [中文](#中文)

---

## English

### What it does
- You configure a **target WiFi SSID** + a **target app package name**
- When both match, it fires: sound + notification + full-screen overlay
- 30-second cooldown so it never spams you
- 100% local. No cloud, no analytics, no data collection

### License & how this project makes money
**The source code is MIT — use it, modify it, ship it, sell it. Free, forever.** No bait, no "open core" asterisk.

What costs money is *time*:

| | What you get | Price |
|---|---|---|
| **Free** | This repo. Build it yourself. | ¥0 |
| **Ready APK** | Install-and-go build, setup guide | ¥9.9 |
| **Branded build** | Renamed / re-iconed / custom rules, delivered in 2–5 days | ¥99+ |

If you'd rather not fight Gradle, the paid tiers exist. If you're happy to build it, the repo is genuinely complete — that's the point.

### Build

```bash
# needs JDK 17 + Android SDK (platform 34, build-tools 34)
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

No Android Studio required. See `SPEC_V22.md` for the full feature spec.

### Requirements
- Android 8.0+ (minSdk 26), tested on Android 14 real hardware
- Android 13+: `NEARBY_WIFI_DEVICES` + `POST_NOTIFICATIONS`
- Android 12/12L: `ACCESS_FINE_LOCATION` (SSID reads are permission-gated)
- Accessibility service must be enabled manually by the user (system restriction)

---

## 中文

### 它做什么
设定「目标 WiFi SSID」+「目标应用包名」，两者同时命中就弹警告：提示音 + 通知 + 全屏浮窗。冷却 30 秒防刷屏。纯本地运行，不联网、不采集数据。

### 授权与商业模式
**源码 MIT，随便用、随便改、可以商用，永久免费。**
付钱买的是**省时间**：① 开箱即用的成品 APK（¥9.9）② 品牌定制版（¥99 起，换名/换图标/换警告音/加规则，2–5 天交付）。
不想折腾构建就买成品；愿意自己编译，这个仓库是完整可用的。

### 构建
```bash
# 需要 JDK 17 + Android SDK (platform 34 / build-tools 34)
./gradlew assembleDebug
```

### 文件
- `WarnService.kt` — 无障碍服务：前台应用检测 + 警告触发
- `WifiHelper.kt` — SSID 读取（含 Android 12+ 权限坑处理）
- `Config.kt` — SharedPreferences 配置
- `MainActivity.kt` — 配置页 UI

### 已知限制
- Android 12+ 读 SSID 需「位置 / 附近设备」权限，拒绝则无法判断 WiFi
- 无障碍服务必须用户手动开启（系统限制，无 API 可自动开）
- 只提醒、不拦截：无 Root 无法真正阻止应用启动

---

*Built by [Weavri](https://dev.to/weavrikx). Cloud builds, real-device testing, reproducible artifacts.*
