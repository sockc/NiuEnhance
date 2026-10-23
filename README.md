# NiuEnhance

面向 **2026款小牛 FX 风速** 的安卓增强工具与 LSPosed 模块（非小牛官方产品）。

**适配基准**：小牛电动 `com.niu.cloud` **5.19.0**；仪表 MTC85V14、显示 MTQ02V06、蓝牙 C22QQD06；测试目标一加15（Android 16）。

## V0.1 实现范围

- 一份 APK 同时包含独立管理界面及 legacy LSPosed 模块入口
- 蓝牙诊断：只读已配对的 NIU Link 设备与全局 A2DP 状态（不冒充精确的每设备连接状态）
- 音乐：经系统通知使用权授权后读取媒体会话、上一首/下一首/播放暂停
- 车辆兼容性：小牛官方 APP 版本识别、LSPosed 模块启动心跳
- LSPosed **仅做版本白名单兼容检查**，暂不隐藏原厂控件，不改车辆控制
- GitHub Actions 自动构建 Debug APK；打 `v*` 标签后仅在已有固定签名 Secrets 时发布 Release

## 构建

需要 Android SDK 36、JDK 17、Gradle 8.13：

```bash
gradle :app:assembleDebug
```

可直接在 GitHub Actions 的 `Android CI` 下载 Debug APK。仓库未包含 Gradle Wrapper 二进制文件，CI 使用固定版本 Gradle。

## 安装与权限

1. 安装 Debug APK，打开并允许「附近设备」以检测 NIU 蓝牙设备。
2. 如需音乐控制，系统设置中为 NiuEnhance 开启「通知使用权」；本模块不读取通知内容。
3. 如使用 LSPosed，在模块管理中启用 NiuEnhance，并且**只勾选** `com.niu.cloud`，然后重启官方 APP。
4. 再打开 NiuEnhance 查看「上次捕获小牛启动」。如果无心跳，不代表原车异常；先检查 Hook 作用域、LSPosed 兼容性及官方 APP 版本。

## 固定签名

在仓库 Settings → Secrets and variables → Actions 设置：
- `SIGNING_KEY_BASE64`：固定 JKS 的 Base64
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

签名材料不完整时，Tag 的 Release Job 会失败，**不会上传无签名 Release APK**。同一 JKS 必须长期保留，丢失后无法覆盖升级。

## 重要说明

本项目尚需真机和 CI 验证；不要将 APK 当作已测试可用的正式版。不要把 Debug APK 当作将来固定签名 Release APK 的直接升级包。界面精简将在拿到官方页面的实际 View 结构后启用，避免误伤车控按钮。

详见 [开发路线](docs/ROADMAP.md) 与 [安全边界](SECURITY.md)。
