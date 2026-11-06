# Bug 登记

## BUG-001 · 播放组件静音无效，且按静音后进度归零

- 现象：点播放卡里的静音键，电视声音没有变化；紧接着进度条回到 0:00。
- 根因（真机日志确认）：
  - `_mcc` SetVolume 0 改的是 Apple TV 上报的“音量”值（日志 level=0.5 → after=0.0），但当前音频路由没有真的静音；它并不是 Siri Remote 的静音键。
  - 这条音量命令还会触发 NowPlayingInfo 刷新，导致进度被重置。
- 正确做法：模拟物理遥控器的静音键，发 HID 命令 18（down + up），让 Apple TV 自己走 CEC/IR 静音。
- 参考：pyatv issue #2325 的协议抓包记录（Power=19，Mute=18）。
- 修复：`toggleMute()` 改为 `pressButton(HidCommand.Mute)`，不再发任何 `_mcc` 音量命令；图标先本地切换。
- 状态：已改，待真机验证

## BUG-002 · 触控板下方播放/暂停键无效，暂停时图标不变成播放

- 现象：点触控板下方的播放/暂停键，电视没有暂停/播放；电视已经暂停时，按钮图标仍是暂停（没有变成播放）。
- 影响：无法从遥控页控制播放状态，图标与电视状态不一致。
- 初步定位：
  - 当前用 HID `PlayPause(14)`（`pressButton`）发送切换；该路径在部分 tvOS/App 上不生效。
  - 图标完全依赖 `_iMC` / `MediaControlStatus` 推送；若电视没有推送或推送的 flags 不明确，`playbackState` 不会更新。
- 修复方向：
  - 改用 `_mcc` 的 Play / Pause 显式命令，失败时回退 HID PlayPause。
  - 点击时先乐观更新图标，命令失败再回滚。
  - 用 `NowPlayingInfo.playbackRate` 作为状态补充（仅完整快照，避免音量局部更新误判）。
- 状态：已修复，待真机验证（`_mcc` Play/Pause + 乐观图标 + rate 兜底）

## BUG-003 · 锁屏媒体卡只对触发时的那个 App 生效

- 现象：在某个视频 App 里能看到锁屏卡；切到另一个视频/音频 App 后，即使电视在播放，锁屏卡也不再出现。
- 需求：只要 Apple TV 处于播放/暂停状态，不管当前是视频还是音频、有没有上报标题，都应该出现。
- 初步定位：
  - 之前锁屏状态要求 `NowPlaying.title != null`；不报元数据的 App 会被直接过滤掉。
  - 切换 App 时播放状态会短暂经过 Stopped，服务立刻停止，新 App 起播时可能因后台启动 FGS 受限而无法重新拉起。
- 修复：
  - 有播放状态但没有标题时，用「设备名 / 正在播放」的通用卡兜底。
  - 前台服务从连接到断开一直保持：播放时显示媒体卡，空闲时切换为低优先级「已连接」通知（锁屏隐藏），这样在别的手机 App / 锁屏状态下切换电视 App 也不会遇到后台启动 FGS 的限制。
  - App 内播放条也使用同一份兜底状态。
- 状态：已修复，待真机验证


## BUG-004 · B 站等第三方 App 只显示 Apple TV 名称，不显示视频标题

- 现象：B 站播放时媒体卡标题是设备名「Fei's Apple TV」/「正在播放」，不是视频标题。
- 影响：锁屏/播放条拿不到当前内容标题。
- 初步定位：当前标题只来自 Companion 的 `NowPlayingInfo`；B 站可能没有上报，或 archive 里用了我们没解析的 key。已加 `NowPlayingInfo keys=...` 和解析结果日志。
- 下一步：
  - 抓 B 站播放时的 `NowPlayingInfo keys`；若有 title 类 key，补 `NowPlayingParser`。
  - 若完全没有 `NowPlayingInfo`，Companion 层拿不到，需要评估 MRP（Media Remote Protocol）或放弃第三方 App 标题。
- 状态：已确认 Companion 限制。真机日志里 B 站播放时 root 有 `playerIdentifier`/`identifier` 等 key，但值全部是 `$null`；`NowPlayingInfo keys=[]`，没有标题、bundleID 或应用名。无标题时固定显示「正在播放」，手动兜底设置已按用户要求删除；自动按 App 显示名称需要 MRP，暂未实现。
