# LSPosed 状态诊断：小牛电动 5.19.0

当前版本只做只读心跳，不 Hook 车控或隐藏 UI。

1. 在 **LSPosed 管理器 → 模块 → NiuEnhance** 启用模块，**作用域**仅选「小牛电动（com.niu.cloud）」，不要勾系统框架。
2. 在系统设置中对小牛电动「强行停止」，重新打开车况页。
3. 返回 NiuEnhance，点击「刷新全部状态」。
4. 若仍未出现心跳，在 **LSPosed 模块日志**搜索 **[NiuEnhance]**；不要只搜索 `niue`，也不要仅凭 Zygisk 中 `pre specialize [com.sockc.niuenhance]` 判断官方 APP 已被注入。

标记含义：
- `entry`：LSPosed 调用了官方 APP 的注入入口。
- `hook-installed`：监听已注册。
- `attach version=5.19.0`：已获取官方 APP 上下文。
- `heartbeat-ok`：回传已被 NiuEnhance 接收。
- `heartbeat-no`：未收到接收方确认。
- `heartbeat-error type=...`：回传出现异常。
- `hook-error type=...`：注册监听失败。
- `version-skip`：官方 APP 版本未列入白名单。

如果没有 `entry`，优先确认作用域、LSPosed 版本与模块启用状态；如果有 `attach` 但无 `heartbeat-ok`，再排查跨应用状态回传。请注意：LSPosed 内部日志和 Zygisk 日志不是同一种记录。

只提供上述标记的截图；不要公开上传未经脱敏的完整 LSPosed/系统日志（可能包含设备地址、令牌等）。
