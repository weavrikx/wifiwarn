# WifiWarn V2.1 返修规格书 (SPEC)

> 基于 V2 (已实现) 的返修。构建命令不变: `ANDROID_HOME=/opt/android-sdk /opt/gradle-8.6/bin/gradle assembleDebug` 必须 SUCCESSFUL。

## 一、6 项返修需求

### 1. 首启必申请定位权限 (FAIL: 应用未申请定位权限)
现状: `first_permission_flow_done` 一次性标志, 覆盖安装/重装后不再触发; 且权限弹窗可能被系统合并吞掉。
修改:
- 权限申请改为**每次进入主页 onResume 时检查缺失项并补齐申请** (去掉一次性标志, 改为"缺啥申请啥")。已授予的跳过。
- 申请列表顺序: 先 `ACCESS_FINE_LOCATION` + `NEARBY_WIFI_DEVICES` (SSID 硬依赖), 再 `POST_NOTIFICATIONS`。用 RequestMultiplePermissions 一次提交, 但数组顺序保证定位在前。
- 申请回调里: 依据结果自动打开对应开关 (见第 3 条)。

### 2. 总开关默认打开
- `Config.isMasterEnabled` 默认值从 false 改为 **true** (SharedPreferences getBoolean 默认值 true)。
- 新安装首启: 主页总开关显示为开。

### 3. 通知/悬浮窗开关在权限回调里自动打开
- 运行时权限 (通知) 申请成功回调 → 自动 `Config.setNotifEnabled(true)` + 开关 UI 置开。
- 悬浮窗权限: 从系统设置页返回 (onResume) 检测 `Settings.canDrawOverlays` 为 true → 自动 `Config.setOverlayEnabled(true)` + 开关 UI 置开。
- 用户手动关闭后, 下次权限回调/onResume 不再强制打开? 不——按需求: 拿到权限回调即打开。但用户手动关过的情况, 建议: onResume 时若权限在且开关 off, 保持 off (尊重手动关闭); 仅在"权限从无到有"那次回调里自动打开。实现: onResume 对比上一次权限状态, 从无→有时自动打开; 或简单起见: 权限回调成功即打开 (用户手动关闭后的再次 onResume 不强制)。给出合理实现即可。

### 4. 无障碍服务开启后, 禁用「开启无障碍服务」按钮
- 无障碍已开启: 按钮 `setEnabled(false)` + 文案「无障碍服务已开启」。
- 未开启: 按钮可用, 文案「开启无障碍服务」。

### 5. 设置改为底部 Tab 切换 (不是子页面)
- 删除 SettingsActivity 独立页面 + 主页「进入设置」按钮。
- 主页改为 **单 Activity 双 Tab**: 底部 Tab 栏 (BottomNavigationView 或自定义 TabLayout, 放屏幕底部), 两个 Tab: 「主页」/「设置」。
- Tab 切换用 Fragment 或在 Activity 内 View 切换 (简单起见 View 切换即可, 不必上 Fragment 框架)。
- 「主页」Tab = 现有主页内容 (总开关/通知/悬浮窗/无障碍状态/按钮)。
- 「设置」Tab = 现有设置页内容 (添加 SSID/SSID→应用树/删除/添加应用)。

### 6. 界面美化
- 换 Material3 风格: 主色建议紫色系 (#7C4DFF 或类似), 卡片圆角, 分段标题, 图标。
- 主页三开关用卡片分组 (SwitchMaterial, 右侧开关, 左侧标题+副标题说明文字)。
- 设置页 SSID 行用卡片样式 + 展开动画 (简单展开即可)。
- 应用选择弹窗美化 (圆角, 列表项带图标)。
- 悬浮窗卡片保持红色警告风格但更精致 (圆角+阴影+大图标)。
- 总目标: 不再"太丑", 达到中等偏上的 Material 干净观感。

### 7. 图标重绘 (FAIL: 图标太丑)
- 删除现有 adaptive icon (ic_launcher_foreground/background 是 codex 之前临时画的 WiFi 波形)。
- 重画: **盾牌 + WiFi 信号弧线** 的矢量图标, 主题色紫色渐变, 白底盾牌轮廓, 高识别度。
- foreground 适配 adaptive icon 安全区 (直径 66dp 内), 背景用紫色渐变 (#7C4DFF → #B388FF)。
- 视觉要求: 居中对齐、线条粗细统一、不糊、不挤。

### 8. 应用改名 (FAIL: 名字太丑)
- app_name 从「WiFi 警告」改为 **「WiFi 哨兵」**。
- acc_service_label 改为 **「WiFi 哨兵检测服务」**。
- 通知渠道名「WiFi 警告」→「WiFi 哨兵」。
- 首页标题「WiFi 警告」→「WiFi 哨兵」(带一个盾牌小图标)。
- 悬浮窗/Toast 文案里的「WiFi 警告」措辞同步替换为「WiFi 哨兵」。
- 注意: 包名 applicationId/namespace 保持 dev.weavrik.wifiwarn 不变 (只改显示名)。

## 二、验收标准 (Hermes 逐条验证)

1. 全新安装 (先卸载再装): 首启弹权限 → 定位(含附近设备)出现、通知出现, 顺序定位在前
2. 覆盖安装 (不卸载直接 -r): onResume 也会补齐缺失权限申请 (不再被一次性标志吞掉)
3. 总开关默认 on
4. 通知/悬浮窗权限拿到后开关自动 on
5. 无障碍开启后「开启无障碍服务」按钮 disabled
6. 主页底部有 Tab 栏: 主页/设置可切换, 无「进入设置」按钮, 无独立 SettingsActivity
7. 界面: Material3 紫色系、卡片式, 明显比 V2 好看
8. 全链路警告仍可用 (SSID 匹配 + 应用匹配 + 提示音/通知/悬浮窗)

## 三、约束

- 保留 V2 全部功能: JSON rules 多 SSID→多应用、30s 冷却、悬浮窗 3 秒消失、可搜索应用选择、旧配置迁移。
- 中文文案。不要破坏悬浮窗/通知/提示音链路。
- 如果删 SettingsActivity, 记得 AndroidManifest 里同步移除其声明。