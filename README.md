# WiFi 警告（WifiWarn）

> 检测到「指定应用」在前台 **且** 当前连接「指定 WiFi」时，弹出警告（Toast + 通知 + 悬浮窗）。不阻止启动（无 Root、Android 无开放 API 可真正拦截）。

**☕ 如果这个小工具帮到了你**：推进独立开发，或获取完整源码套件/定制版本，可以到这里支持我：
- [爱发电 · 支持我并获取源码套件](https://afdian.com)（成品 APK / 完整源码 / 定制服务）
- [GitHub Sponsors](https://github.com/sponsors/weavrikx)（一杯咖啡即可）

## 构建

复制到有 Android Studio 的机器：

```
open /root/wifiwarn  (或用 Android Studio Open 该目录)
```

- compileSdk 34 / minSdk 26 / targetSdk 34
- 自带签名 debug 包即可安装：Build → Build APK(s)
- 或命令行：`./gradlew assembleDebug`（需本机有 Android SDK）

## 使用

1. 打开 App，填「目标 WiFi SSID」和「目标应用包名」（如 com.tencent.mm），保存
   - 「使用当前 WiFi」按钮自动填入当前 SSID
2. 点「开启无障碍服务」，在系统设置里允许本 App 的无障碍权限
3. 授予权限：Android 13+ 需要「附近设备」+「通知」；Android 12/12L 需要「位置」

## 行为

- 前台包名 == 目标包名 且 SSID == 目标（忽略大小写；SSID 留空 = 任意 WiFi 都警告）
- 警告冷却 30 秒，避免刷屏
- 仅警告，不强制关闭

## 文件

- `MainActivity.kt` 配置页
- `WarnService.kt` 无障碍服务（前台检测 + 警告）
- `WifiHelper.kt` SSID 读取
- `Config.kt` SharedPreferences 配置

## 已知限制

- Android 12+ 读取 SSID 需要定位/附近设备权限，拒绝权限则无法判断 WiFi
- 无障碍服务需用户手动开启（系统限制，无法代码自动开）
- 通知在 Android 13+ 需运行时授权