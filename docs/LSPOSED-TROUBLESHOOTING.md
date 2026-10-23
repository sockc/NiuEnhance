# LSPosed 状态诊断（2026款小牛 FX / 小牛官方 APP 5.19.0）

## V0.1.3 的修复
- 实机日志已证明 `entry`、`hook-installed`、`attach version=5.19.0` 成功。
- V0.1.2 的 `heartbeat-dispatched` 只是发送成功，并不能证明收到。
- Android 14+ 的广播身份共享 **默认为关闭**。接收端 `getSentFromUid()` 可能获得 `Process.INVALID_UID`，进而拒绝所有心跳。
- V0.1.3 在发送时调用 `BroadcastOptions.makeBasic().setShareIdentityEnabled(true)`，保持 UID 校验，不允许无验证来源伪造“成功”。
- NiuEnhance APP 显示广播收到时的校验结果和时间。如果没有广播到达，也会明确显示接收端无记录。

## 测试
1. 安装最新构建，LSPosed 作用域保持只勾选 **小牛电动**。
2. 系统设置中强行停止小牛官方 APP，再进入车况页。
3. LSPosed 模块日志应该看到 `[NiuEnhance] heartbeat-dispatched identity-shared`。
4. 回到 NiuEnhance，点 **刷新全部状态**：
   - **已验证**：广播抵达且 Android 系统发送方 UID 与 `com.niu.cloud` 相符。
   - **已收到广播，但系统未提供发送方 UID**：身份仍未共享或设备 ROM 限制。
   - **拒绝：UID 未关联小牛官方 APP**：发起方身份无法可靠匹配。
   - **接收端尚无记录**：广播可能未送达，检查包安装与显式广播限制。

项目不改变蓝牙钥匙、车辆控制、动力或 OTA。请勿公开原始车架号、设备地址和身份令牌。
