# NiuDash 原厂仪表资源与通信验证（D0.1）

适配基准：2026款小牛 FX 风速；仪表 MTC85V14；显示 MTQ02V06；蓝牙 C22QQD06；一加15 Android 16；小牛 APP com.niu.cloud 5.19.0。

## 原则
1. **手机 APK 资源不等于原厂仪表固件资源**。即使手机 APK 包含壁纸和动画，也不能证明 MTQ02V06 可以直接执行或加载。
2. **HCI 通信突发不等于视频流**。必须定位到对端服务、消息类型与传输格式，并排除蓝牙音频等干扰。
3. 全部操作必须是**只读、驻车、用户授权**；不发送未知控制帧、不刷仪表固件、不绕过数字签名。
4. 原始 APK、bugreport、HCI 日志有敏感或专有信息。只存本地，**绝不上传公开 GitHub**。工具自身不会联网。
5. 原厂素材的提取、使用与再分发需遵守相应授权；未确认授权时用自绘组件。

## 一、离线检查官方手机 APP

Windows 电脑安装 Android Platform-Tools、Python 3.10+。手机开启 USB 调试，并通过 adb devices 授权。

在仓库根目录 PowerShell 中执行：

~~~powershell
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode status
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode apk
python .\tools\niudash\probe.py apk .\niudash-private\phone-app-apks\01-base.apk --out .\niudash-private\app-resources.json
~~~

注：安装包可能拆分为多个 APK，请分别检查生成的 01-base.apk、02-*.apk 等文件。只列 ZIP 目录的文件名、后缀和大小，**不会提取或分享素材**。

结论只可表述为“手机 APK 存在某种资源”；不能等同于原厂仪表资源。

## 二、蓝牙通信基线 / 壁纸 / 导航对比

手机开发者选项开启“蓝牙 HCI 信息收集日志”，然后关闭并开启蓝牙使其生效。车辆驻车，停止音乐，尽量只保留小牛相关蓝牙连接。Android 官方认可 HCI snoop 与 Bug Report 用于排查通信，但具体 ROM 的日志是否完整需实测。

实验按下表依次进行，**每个阶段都要手动进行对应动作，标记命令不能一次性全部执行**。

| 阶段 | 操作 | 目的 |
|---|---|---|
| idle-start | 小牛官方 APP 连车、不进行操作，静置30秒 | 记录基线 |
| wallpaper-start | 官方 APP 开始一项原厂支持的壁纸切换或上传 | 寻找资源传输变化 |
| wallpaper-end | 完成壁纸切换，静置30秒 | 识别上传结束 |
| nav-start | 原厂 APP 启动导航并让仪表显示路线 | 分辨导航消息 |
| nav-end | 停止导航、静置30秒 | 观察回到基线 |

每个阶段开始/结束时执行对应标记（下列只是示例，按实际操作时机分别运行）：

~~~powershell
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "idle-start"
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "wallpaper-start"
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "wallpaper-end"
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "nav-start"
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "nav-end"
~~~

全部完成后（HCI 仍开启时）：

~~~powershell
powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode bugreport
python .\tools\niudash\probe.py hci ".\niudash-private\fx-bugreport-实际文件名.zip" --out .\niudash-private\hci-summary.json
~~~

Bug Report 在采集前需要输入 YES；可能包含个人账号、蓝牙设备标识、定位和通信内容。若 ZIP 没有二进制 snoop，参见 [AOSP Bluetooth 调试说明](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging)，使用官方 btsnooz.py 转换，或检查是否属于设备不允许导出的情况。

## 三、证据等级 / 结果门槛

| 等级 | 验证证据 | 后续决定 |
|---|---|---|
| E0 | 实车照片、版本、官方导航正常 | 仅确认屏幕已有功能 |
| E1 | APK 发现疑似图片、动画或主题资源 | 仍不能判定属于仪表固件 |
| E2 | 待机与壁纸事件的 HCI 流量可重复区分 | 继续确认目标服务及数据类型 |
| E3 | 合法方式解析出明确的资源传输格式 | 设计只读资源适配器 |
| E4 | 通过官方支持的壁纸入口可重复显示自制静态画面 | 可实现静态 DIY 壁纸 |
| E5 | 动态数据源与显示通道都经受控验证，关键警告不受影响，能回退 | 才能研究完整动态 DIY 仪表 |

**E2 不等于图像流，E4 不等于动态主题，E5 前不得声称完全支持 DIY 原厂仪表。**

## 四、混合组件规范

每个组件分别记录三项：render_source（self_drawn / official_asset_local_only / official_theme_supported）、telemetry_source（simulated / verified_vehicle_read_only / unavailable）、display_target（phone_preview / official_wallpaper / verified_dynamic_instrument）。

没有真实车速、电量来源时显示“模拟”；组件编辑器先在手机端实现。原厂关键警告、转向灯、挡位不能被自制绘图遮挡，任何异常必须恢复原厂显示。

本地输出：niudash-private/app-resources.json、markers.tsv、hci-summary.json。
在共享任何摘要前也应检查其中是否包含身份信息或专有文件名。
