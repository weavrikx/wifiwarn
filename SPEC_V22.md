# WifiWarn V2.2 返修规格书 (SPEC) — 用户机器"整页无法点击"

## 问题描述 (用户真机验收反馈)
用户在**自己的手机——vivo (OriginOS 定制 ROM)** 上安装 V2.1 APK 后:
> "整个页面无法进行任何点击"

**用户补充关键细节**: 首启权限弹窗**逐个全部接受后** (定位/附近设备/通知全允许), 然后就点不了了。即: 卡死发生在权限全部授予之后, 不是拒绝时。

华为测试机上 UI 一切正常可点(该 ROM 权限流程不同)。请复现并修复。设备: 可通过 adb 连测试机 (序列号 4ZQ9K25B19013196)。

## 关键根因 (vivo 场景: 权限全接受后卡死)

### 0. 权限回调→onResume→再次launch 循环 (vivo 头号根因)
vivo/OriginOS 权限弹窗接受后, 权限入库有延迟。代码时序:
1. onCreate → onResume → `permissionCheckedThisEntry=false→true` → `requestMissingPermissions()` → `permLauncher.launch([FINE_LOCATION, NEARBY, NOTIFICATION])`
2. 系统弹窗 → App `onPause()` → `permissionCheckedThisEntry=false` (!!)
3. 用户逐个点允许 → 权限回调 → **onResume() 再次进入** → `permissionCheckedThisEntry` 已是 false → 又 `requestMissingPermissions()` → 此时部分权限(尤其 POST_NOTIFICATIONS, vivo 上常需二次开启)仍未入库 → needed 非空 → **再次 launch** → 又弹窗/又 pause → **死循环**
4. 死循环中系统可能不再显示可见弹窗, 但 GrantPermissionsActivity 透明窗口残留/堆积 → **全屏拦截触摸, 页面完全点不动**

**修复方案 (必须)**: 
- **权限请求只在 Activity 首次创建时发起一次**: 加 `private var permissionRequestedOnce = false` 类级标志, `onCreate` 里发起, 之后永不自动 launch
- 删除 onResume 里的 `requestMissingPermissions()` 调用 (或改为只展示引导条, 绝不 launch)
- `permLauncher` 回调里: 结果处理后只 `refreshSwitches()`, 不再触发任何 launch
- launch 前 `shouldShowRequestPermissionRationale()` 检查: false 且未授予 = 永久拒绝 → 不 launch, 弹自定义 AlertDialog 引导去设置页
- launch 后加防重入: `if (permissionRequestedOnce) return`

## 其他根因 (低于 #0, 仍要排查)

### 1. 权限请求卡死/透明权限窗口残留 (最高嫌疑)
`MainActivity.kt` 的 `requestMissingPermissions()`:
- onResume 每次进入都 `permLauncher.launch()` 硬弹权限
- 若用户**之前点过"禁止"且勾选"不再询问"**, 部分 ROM 上 launch 会闪一个透明 GrantPermissionsActivity 窗口, 窗口不释放 → **全屏拦截触摸, 页面完全点不动**
- 另外 requestMissingPermissions 有 `permissionCheckedThisEntry` 防重, 但权限回调后 onResume 再次触发, 逻辑绕圈

**修复方案**:
- launch 前用 `shouldShowRequestPermissionRationale()` 判断: 
  - 若为 false 且权限未授予(用户永久拒绝) → **不 launch**, 弹自定义对话框引导去设置页手动授权
- 权限请求改为只在首启申请一次, 之后缺失权限只显示引导而非强弹
- 权限回调 `permLauncher` 里: 无论结果, 确保没有任何系统窗口残留(正常 dismiss)

### 2. 主线程 ANR (第二嫌疑)
- 若用户手机应用多, `loadInstalledApps()` 若在主线程执行会卡 UI → ANR → 系统弹"未响应"对话框 → 看似"无法点击"
- **修复**: 确认 loadInstalledApps / 应用列表查询在后台线程(Intent查询已是异步? 需要检查), ItemApp 加载 icon 用后台。加防御: 用 StrictMode 查主线程 I/O

### 3. 悬浮窗 overlay 残留
- `WarnService.kt` 的 overlay: FLAG_NOT_FOCUSABLE 但没加 `FLAG_NOT_TOUCHABLE` → 遮条区域会拦截点击, 若用户机器上悬浮窗权限异常, overlay 可能未被 3 秒移除
- **修复**: overlay LayoutParams 加 `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`, 确保警告条绝不挡点击

### 4. BottomNavigation / 布局遮挡
- `activity_main.xml` contentArea FrameLayout `layout_weight=1` + bottomNav 正常; 检查是否有 View 全屏覆盖。确保 homeView/settingsView 切换时另一个用 GONE 而非 INVISIBLE

## 验收标准
1. 修复后华为测试机: 全新安装 → 首启权限 → 点任何按钮/Tab/开关 全部响应
2. 模拟"永久拒绝权限": adb shell pm revoke + 设置不再询问 → App 正常显示引导框而非卡死
3. 悬浮窗警告条出现期间, 警告条**不拦截**对下层页面的点击
4. 构建成功 BUILD SUCCESSFUL
5. 报告: 每项根因的排查结论(是/否命中) + 修复代码片段 + 复现/验证结果

构建命令: `cd /root/wifiwarn && ANDROID_HOME=/opt/android-sdk /opt/gradle-8.6/bin/gradle assembleDebug`