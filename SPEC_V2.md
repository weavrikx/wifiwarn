# WifiWarn V2 需求规格书 (SPEC)

> 交付对象: codex 研发。验收: Hermes 项目总监。
> 基础: 现有工程 /root/wifiwarn (Kotlin, AGP 8.2.2, compileSdk 34, minSdk 26)。
> 构建命令: `ANDROID_HOME=/opt/android-sdk /opt/gradle-8.6/bin/gradle assembleDebug`。构建必须 SUCCESSFUL。

## 一、页面结构 (两页)

### 1. 主页 MainActivity (现有文件改)
从上到下:
1. 标题「WiFi 警告」
2. **总开关** (Switch, 默认关): 控制整个警告功能是否启用。开 = 无障碍服务检测生效; 关 = 全部不警告。
3. 附属开关区 (总开关下方, 两个并排或竖排):
   - **通知开关** (Switch, 默认关): 只有已授予 POST_NOTIFICATIONS 权限才能打开; 无权时点击不打开且弹 Toast 提示去授权(跳系统通知权限设置页)。
   - **悬浮窗开关** (Switch, 默认关): 只有已授予 SYSTEM_ALERT_WINDOW 权限才能打开; 无权时点击不打开且弹 Toast 提示去授权(跳系统悬浮窗设置页 ACTION_MANAGE_OVERLAY_PERMISSION)。
4. 无障碍服务状态行 + 「开启无障碍服务」按钮 (保留现有逻辑)
5. 「进入设置」按钮 → 打开 SettingsActivity
6. 现有 SSID/包名编辑框全部删除 (移到设置页)

### 2. 设置页 SettingsActivity (新)
- 顶部: 标题「设置」+ 返回
- **SSID 列表**: 展示已保存的所有 SSID (每行: SSID 名 + 删除按钮 + 展开箭头)
- 底部/顶部: 「添加 SSID」按钮 → 弹出对话框: EditText 输入 SSID + 「使用当前 WiFi」按钮自动填入 + 确定保存
- 展开某个 SSID 后: 显示该 SSID 下的**应用列表** (每行: 应用名 + 包名 + 删除) + 「添加应用」按钮
- 「添加应用」→ 弹出**应用选择对话框** (滚动列表, 显示所有已安装应用: 图标+应用名+包名, 可搜索过滤) → 选中后加入该 SSID 的应用列表 (去重)

## 二、数据模型

现有 Config 只有 ssid + targetPkg 字段。改为:

```kotlin
data class WifiRule(val ssid: String, val packages: MutableSet<String>)
```

存储: SharedPreferences "wifi_warn_config", 用 `rules` 键存 JSON 字符串:
```json
[
  {"ssid": "ZTE-x96R2G-5G", "packages": ["com.termux", "com.tencent.mm"]},
  {"ssid": "Office_WiFi", "packages": ["com.example.app"]}
]
```

另存三个开关键 (Boolean): `master_enabled`, `notif_enabled`, `overlay_enabled` (默认全 false)。

Config 类提供: `loadRules()`, `saveRules()`, `addPackage(ssid, pkg)`, `removePackage(ssid, pkg)`, `addSsid(ssid)`, `removeSsid(ssid)`, `findRulesForSsid(ssid)`, 以及三个开关的 get/set。

## 三、WarnService 行为变更

现有逻辑: 单包名 + 单 SSID 匹配。
新逻辑:
1. 读 `master_enabled`; 为 false 直接 return (不警告)。
2. 读当前 SSID (现有 WifiHelper.currentSsid, 定位权限硬依赖, 保留 getConnectionInfo 分支即可)。
3. 用当前 SSID 查 rules: 找到对应 WifiRule; 找不到 → return。
4. 前台包名 (AccessibilityEvent.packageName) 不在该 rule 的 packages 里 → return。
5. 命中 → 30s 冷却 (保留, 按 SSID+包名 分别冷却可以, 简单起见全局 30s 也行) → 触发警告:
   - **提示音** (无条件, 只要总开关开): 用 RingtoneManager 默认通知音播放一次 (或 ToneGenerator, 选通知音更明显)。
   - **通知**: 仅当 `notif_enabled` 为 true (且通知权限在) 才发 (现有 sendNotification 逻辑)。
   - **悬浮窗**: 仅当 `overlay_enabled` 为 true (且悬浮窗权限在) 才弹。悬浮窗为 TYPE_APPLICATION_OVERLAY, 显示: 红色警告卡片 「⚠️ 你在 WiFi「XX」下打开了「应用名」」+ 一个关闭按钮, 3 秒后自动消失, 可点关闭立即消失。
   - Toast: 保留 (现有逻辑), 无条件。

悬浮窗实现注意: 悬浮窗必须持有 Context, 用 WindowManager.addView; 在服务里创建即可; 关闭时 removeView; 用 handler.postDelayed 3 秒自动移除。

## 四、权限

Manifest 现有权限保留 (ACCESS_WIFI_STATE/ACCESS_NETWORK_STATE/ACCESS_FINE_LOCATION maxSdkVersion=34/NEARBY_WIFI_DEVICES/POST_NOTIFICATIONS/BIND_ACCESSIBILITY_SERVICE)。
新增:
1. `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` (悬浮窗, 特殊权限不走运行时弹窗, 跳设置页)
2. `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` (Android 11+ 获取已安装应用列表)

### 首次启动权限申请 (MainActivity.onCreate)
依次申请 (可用 ActivityResultContracts.RequestMultiplePermissions 一次申请运行时权限):
- NEARBY_WIFI_DEVICES + ACCESS_FINE_LOCATION (WiFi SSID 获取, 含其附属定位)
- POST_NOTIFICATIONS (通知)
- SYSTEM_ALERT_WINDOW 不是运行时权限 → 用 intent 跳 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` (uri: package:包名)

申请完成后:
- 通知权限 → 有则通知开关可开 (状态由用户决定, 但若权限无则强制关并显示灰)
- 悬浮窗权限 → 有则悬浮窗开关可开

### 添加应用时的应用列表权限
QUERY_ALL_PACKAGES 声明即可 (安装时自动有); 无需运行时申请。应用列表用 `packageManager.getInstalledApplications(PackageManager.GET_META_DATA)` 过滤 `applicationInfo.enabled && flags & FLAG_SYSTEM` 可选 (建议不过滤, 全列 + 可搜索)。

## 五、验收标准 (Hermes 将逐条验证)

1. 构建成功, APK 可安装
2. 主页: 总开关/通知开关/悬浮窗开关/无障碍状态/进入设置, 无旧编辑框
3. 首次打开弹权限申请 (定位/附近设备/通知), 悬浮窗走设置页
4. 通知权限未授予时通知开关点不开; 授予后可开
5. 设置页: 添加 SSID (含"使用当前 WiFi"), 添加应用 (弹应用列表), 删除 SSID/应用
6. 全链路: 总开关开 + 无障碍开 → 启动 rule 内的应用 → 听到提示音 + Toast + (通知开关开则通知, 悬浮窗开关开则悬浮窗) 全部按开关生效
7. 悬浮窗 3 秒自动消失, 可手动关闭
8. 总开关关: 无任何警告
9. 非 rule 内 SSID 或应用: 不警告

## 六、已知约束

- 定位权限是 SSID 读取硬依赖 (华为 ROM), 保持 maxSdkVersion="34" 声明 + 运行时申请
- 30s 冷却防刷屏
- 不改 WarnService 外的无障碍声明
- 中文文案