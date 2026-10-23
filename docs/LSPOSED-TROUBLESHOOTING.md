# LSPosed 注入诊断（小牛电动 5.19.0）

## 实机发现：V0.1.1
- 已经出现 `entry`、`hook-installed`、`attach version=5.19.0`，**证明模块在小牛主进程注入成功**。
- `heartbeat-error type=IllegalArgumentException` 发生在原 ContentProvider 回传路径，不是注入失败。
- V0.1.2 改用**显式广播**；接收端通过 Android 14+ 的发送 UID 交叉验证 `com.niu.cloud`，不接受无法验证来源的数据。
- 仅对 `com.niu.cloud` 主进程注册 Hook，不再处理 `com.niu.cloud:pushcore`。

## 安装后测试
1. 在 LSPosed 中启用 **NiuEnhance**，作用域只选 **小牛电动（com.niu.cloud）**。
2. 强行停止小牛电动，再打开官方 APP 的车况页。
3. 在 LSPosed **模块日志**搜索 `[NiuEnhance]`：
   - `entry process=com.niu.cloud`：注入入口成功。
   - `hook-installed`：钩子安装成功。
   - `attach version=5.19.0`：已匹配官方版本。
   - `heartbeat-dispatched`：Android 已接受广播发送调用，但**不能代表回传成功**。
   - `heartbeat-error type=...`：发送失败。
4. 打开 NiuEnhance，按「刷新全部状态」；只有**实际收到并验证**广播才会显示心跳时间。
5. 如果出现 `heartbeat-dispatched` 但界面仍无时间，则检查广播投递或接收端 UID 校验，暂不重复调整 LSPosed 作用域。

## 说明
- 双蓝牙状态显示「已配对」不等于当前实时已连接。
- 本项目目前只做诊断，不改变蓝牙钥匙、车控、动力、OTA。
- 日志发出前请遮盖设备地址、VIN 和位置等敏感信息。
