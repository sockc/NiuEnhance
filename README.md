# NiuEnhance

## V0.2.3 — 自动发现与自定义菜单精简
- 官方 APP 5.19.0 的「我的」页面扫描后，自动列出匹配固定白名单的菜单；没有识别到的项不盲目创建开关。
- 9 个可识别菜单逐项配置，默认保留；沿用已经实机验证的 NIU ZOO 安全隐藏规则，扩展到独立网格和列表行。
- 「我的设备」「消息通知」「我的订单」等重要入口默认保持显示；不满足唯一独立容器、安全尺寸或页面指纹条件时直接跳过。
- 每项明确区分「待验证 / 找到独立容器 / 已执行隐藏 / 复查通过 / 已跳过」，切换设置后不会沿用过期扫描状态。
- 诊断区域默认简洁，点击展开节点与隐藏详情；从小牛官方 APP 返回时自动刷新。
- 仅与小牛官方进程通过 UID 核验的显式广播交换固定菜单标签和配置，不保存任意文字、VIN 或位置；不修改车辆控制、蓝牙钥匙、导航或 OTA。
- **本版本尚需 GitHub Actions 编译与实机回归验证**，尤其是新增加的列表行隐藏。

## V0.2.1 — 安全精简开关（实验版）
- 实机确认 5.19.0「我的」页面有原生 View 节点（250 个节点、90 个原生 TextView）。
- 独立 APK 提供「隐藏 NIU ZOO」「隐藏我的小队」两个单独开关，**初始均关闭**。
- 通过官方小牛进程请求 + UID 校验 + 随机 nonce 的广播往返读取设置，避免应用私有数据被跨包直接读取。
- 只有同时匹配「主题工坊 / 消息通知 / 我的设备」等页面特征，且找到**唯一、点击式、独立的小尺寸标签容器**时才尝试隐藏；无法确认则跳过。
- 隐藏的 View 保留原始 visibility，并在 Activity 暂停/退出时恢复，避免影响其他页面。
- APP 显示隐藏是否成功，未匹配安全容器不谎称已生效。
- 车况、导航、蓝牙钥匙、车控和 OTA 保留原厂。


## V0.2.0 — 官方页面识别（只读）
- 在上一版的 LSPosed 心跳验证基础上，只观察 `com.niu.cloud` 主进程的原生 View 树。
- 只统计节点数、WebView/Flutter/Compose/React 容器数量，并且只匹配固定的公开菜单名称，如 `NIU ZOO`、`我的小队`、`主题工坊`；**不保存自由文本、VIN 或位置信息**。
- NiuEnhance 独立 APP 增加「官方 APP 页面识别」模块，进入小牛的「我的」页面后切回即可查看最新统计。
- 此版**不会隐藏任何控件**。页面结构确认后才加入默认关闭的 UI 精简开关，避免影响原厂车辆功能。
- 原厂 APP 版本不为 5.19.0 或非主进程时自动跳过，失败时不影响小牛启动。

面向 **2026款小牛 FX 风速** 的 Android 独立增强 APK + LSPosed 模块（非小牛官方产品）。

**实测基准**：小牛 `com.niu.cloud` 5.19.0、FX 仪表 MTC85V14、显示 MTQ02V06、蓝牙 C22QQD06；一加15（Android 16）。

## V0.1.2（诊断修复）
- 双蓝牙已配对设备只读识别，显示全局 A2DP 状态（不冒充逐设备连接状态）
- 通知使用权授权后，支持音乐标题/媒体会话和播放控制
- LSPosed 注入入口已实测，上一版心跳 `IllegalArgumentException` 已定位为回传链路异常
- 移除旧 ContentProvider IPC，改为显式广播；接收端验证官方小牛 UID 后记录心跳
- Hook 仅作用于 `com.niu.cloud` 主进程，不处理 `com.niu.cloud:pushcore`
- 不修改官方车辆管理页面、车控、蓝牙钥匙或 OTA

## 安装测试
1. [Actions](https://github.com/sockc/NiuEnhance/actions) 下载最新成功的 `NiuEnhance-debug`，解压 ZIP 安装。
2. 在 LSPosed 管理器启用 NiuEnhance，作用域**只勾选** `com.niu.cloud`。
3. 强行停止小牛官方 APP 后再启动，在 LSPosed 模块日志检查 `[NiuEnhance]`。
4. 回到 NiuEnhance 点击「刷新全部状态」，确认心跳时间。
5. 详细结果解释见 [诊断说明](docs/LSPOSED-TROUBLESHOOTING.md)。

## 构建
Android SDK 36、JDK 17、Gradle 8.13，运行 `gradle :app:assembleDebug`。
GitHub Actions 自动构建 Debug。正式发布需要在 Settings → Secrets and variables → Actions 设置固定签名的 `SIGNING_KEY_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。CI Debug 签名可能随 Runner 变化，不要认为可直接覆盖旧版本；需长期固定 JKS 才能保障升级。

## 安全边界
不读取车辆密钥，不向车辆发送指令；不上传诊断数据；外部心跳来源经过 UID 校验。
