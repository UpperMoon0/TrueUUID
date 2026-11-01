## English

TrueUUID 1.3.2 is a **development candidate, not a published release**. It
improves authentication failure messages after a Minecraft/Microsoft launcher
session expires. The server continues to reject unauthenticated login attempts
for names already linked to verified premium UUIDs.

### Changes

- Premium-account login rejection now instructs players to sign out of their
  launcher account, sign in again, and reconnect. The message explains why an
  offline login cannot replace an existing verified premium identity.
- Both the Forge 1.20.1-specific English locale and the shared locale used by
  other loader targets receive the updated guidance.
- Automated tests check both localization sources, while the Forge regression
  workflow validates the new JAR independently of the immutable published 1.3.1
  checksum and verifies reproducible packaging.
- The 1.3.1 release manifest, published artifact, tag and SHA-256 are unchanged.

### Publication status

All 52 mod targets and both 1.20.1 server plugins have their release approvals
reset for version 1.3.2. No new download may be published until the separate
release review, installed-JAR acceptance and approval gates have passed.
This change does not refresh a player's Microsoft credentials automatically and
does not migrate or rewrite account, inventory or world data.

## 中文

TrueUUID 1.3.2 是**开发候选版本，尚未发布**。本次更新改善 Minecraft/Microsoft
启动器登录会话过期时的认证失败提示。对于已绑定正版 UUID 的玩家名称，
服务器仍然拒绝未经验证的离线登录，以保护原有身份和存档。

### 更新内容

- 正版账户认证失败时，提示玩家在启动器中退出账户、重新登录，再尝试连接。
  同时说明离线登录不能替换已验证的正版身份。
- 同步更新 Forge 1.20.1 专用英文语言文件及其他加载器共用的语言文件。
- 测试覆盖两份语言资源。Forge 回归工作流独立验证新构建并检查可复现打包，
  不再将候选 JAR 与已发布 1.3.1 的哈希错误地进行相等比较。
- 已发布的 1.3.1 版本、标签、文件和 SHA-256 校验值保持不变。

### 发布状态

1.3.2 的 52 个 Mod 目标及两个 1.20.1 服务端插件均未获得发布许可。
必须完成独立验收和审批后才能发布。此更新不会自动刷新玩家的
Microsoft 登录凭证，也不会迁移或修改账户、背包和世界数据。
