# Modrinth: page text and version notes

Source text for the pack's Modrinth project (`monucake`), pasted in by hand. Keep it in step with the README on `main` and the wiki's install guide. English first, then 简体中文 (Q12, Q18).

## Project settings

- **Summary:** `A Fabric 1.20.4 client modpack for Monumenta, in English and Chinese. 为 Monumenta 制作的 Fabric 1.20.4 客户端整合包。`
- **Links:** Issues `https://github.com/HarrytheCake/Monumenta-Cakedition/issues` · Source `https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz` · Wiki `https://github.com/HarrytheCake/Monumenta-Cakedition/wiki` · Discord `https://discord.gg/monumenta`
- **Contains AI-generated content:** on (Q19).
- **Old versions:** keep them all listed (Q23).

## Description

````markdown
![Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition/blob/main/1.19.4/resource/titlemonumenta.png?raw=true)

A Fabric 1.20.4 client modpack for the [Monumenta](https://www.playmonumenta.com/) MMORPG server, made for English- and Chinese-speaking players alike. **[简体中文在下方](#简体中文)**

## What's inside

About 70 mods, chosen to work on Monumenta:
- **Monumenta:** [Unofficial Monumenta Mod](https://modrinth.com/mod/unofficial-monumenta-mod), Monumenta Addons, [Monumenta Item Dictionary](https://modrinth.com/mod/monumenta-item-dictionary), and the Monumenta resource pack, installed and turned on.
- **Performance:** [Sodium](https://modrinth.com/mod/sodium), Lithium, ModernFix, ImmediatelyFast, [More Culling](https://modrinth.com/mod/moreculling), Enhanced Block Entities and more.
- **Visuals:** CIT Resewn and Entity Model/Texture Features for Monumenta's custom items and mobs, dynamic lights, a dark loading screen with Paperbox artwork.
- **HUD and chat:** AppleSkin, BetterF3, Xaero's World Map, ChatPlus, longer chat history.
- **Quality of life:** Mouse Tweaks, Shulker Box Tooltip, Searchables, Zoomify and more.
- **Optional, off by default:** [Iris](https://modrinth.com/mod/iris) with [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined), [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap-fair) (Fair-Play), CakeText Translator.

The full list is on each version's page.

## Updates inside the game

**Cakedition Updater** (our own mod) tells you in chat when a new version of the pack is out, and updates it after you close the game, with no new download. It backs up your server list and configs first and offers to restore them. Your optional-mod choices are kept, and the pack never deletes or changes a file it didn't ship.

**[Install guide](https://github.com/HarrytheCake/Monumenta-Cakedition/wiki/Install-Guide)** (memory settings, optional mods, updating from an older version).

## What happens outside the game

- Mods download from Modrinth and from our GitHub releases while the pack installs.
- When you choose to update, Cakedition Updater opens a terminal window after the game closes and runs [packwiz-installer](https://github.com/packwiz/packwiz-installer) (shipped in the pack as `packwiz-installer-bootstrap.jar`), which downloads the new version's files. It never restarts the game by itself.
- CakeText Translator is optional and off. If you turn it on, it can send text to a translation server on your own PC, and only if you set one up.

## Links

- **Bug reports and suggestions:** [GitHub Issues](https://github.com/HarrytheCake/Monumenta-Cakedition/issues/new/choose), in English or Chinese. Please report pack problems to us, not to the mod authors or to Monumenta.
- **Questions:** [Monumenta Discord](https://discord.gg/monumenta) or the [Unofficial Monumenta QQ group](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=HuISJO6q-9qxvbFKs-zh6_cL8E4ESf8p&authKey=v%2B8i%2Be%2BtXWfa4gB%2BjS1q0UeG7TmroP9hMEiwbW7xCLV3VkIwGeOFAzUyrKi3TMNu&noverify=0&group_code=588414395).
- **Source:** [GitHub](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz).

## Credits

- [Paperbox](https://www.pixiv.net/en/users/21257681) for the loading-screen artwork, used with permission. The Silver Destiny artwork belongs to Monumenta.
- [gensokyo-reimagined-qol](https://modrinth.com/modpack/gensokyo-reimagined-qol) for many mod configurations.
- Moe_Muyo and ProbsWinter for texture support.
- [Monumenta Wiki](https://monumenta.wiki.gg/wiki/Monumenta_Wiki) for quest data.
- Every mod belongs to its authors.

Most of the code of our own mods and of the pack's tools was written with the help of an AI assistant (Claude).

**Disclaimer:** this is a fan-made modpack, not affiliated with Monumenta, the Unofficial Monumenta Mod or any included mod.

---

## 简体中文

为 [Monumenta](https://www.playmonumenta.com/) MMORPG 服务器制作的 Fabric 1.20.4 客户端整合包，同样面向英文和中文玩家。

### 包含内容

约 70 个模组，均为适配 Monumenta 挑选：
- **Monumenta：** [Unofficial Monumenta Mod](https://modrinth.com/mod/unofficial-monumenta-mod)、Monumenta Addons、[Monumenta Item Dictionary](https://modrinth.com/mod/monumenta-item-dictionary)，以及已安装并启用的 Monumenta 资源包。
- **性能：** [Sodium](https://modrinth.com/mod/sodium)、Lithium、ModernFix、ImmediatelyFast、[More Culling](https://modrinth.com/mod/moreculling)、Enhanced Block Entities 等。
- **画面：** CIT Resewn 与 Entity Model/Texture Features（用于 Monumenta 的自定义物品和怪物）、动态光源、带 Paperbox 插画的深色加载界面。
- **HUD 与聊天：** AppleSkin、BetterF3、Xaero 世界地图、ChatPlus、更长的聊天记录。
- **便利性：** Mouse Tweaks、Shulker Box Tooltip、Searchables、Zoomify 等。
- **可选（默认关闭）：** [Iris](https://modrinth.com/mod/iris) 与 [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) 光影、[Xaero 小地图](https://modrinth.com/mod/xaeros-minimap-fair)（Fair-Play 版）、CakeText Translator。

完整模组列表见各版本页面。

### 游戏内更新

整合包有新版本时，**Cakedition Updater**（我们自己的模组）会在聊天栏提示你，并在你关闭游戏后完成更新，无需重新下载。更新前它会备份服务器列表和配置文件，之后可以一键恢复。你对可选模组的选择会保持不变；整合包也不会删除或修改任何不是它安装的文件。

**[安装指南](https://github.com/HarrytheCake/Monumenta-Cakedition/wiki/安装指南)**（内存设置、可选模组、从旧版本升级）。

### 游戏之外会发生什么

- 安装时，模组会从 Modrinth 和我们的 GitHub 发布页下载。
- 选择更新后，Cakedition Updater 会在游戏关闭后打开一个终端窗口，运行 [packwiz-installer](https://github.com/packwiz/packwiz-installer)（整合包内附带 `packwiz-installer-bootstrap.jar`）下载新版本的文件。它不会自行重启游戏。
- CakeText Translator 是可选模组，默认关闭。开启后，只有在你自己设置了翻译服务器的情况下，它才会把文本发送到你电脑上的这个服务器。

### 链接

- **反馈问题与建议：** [GitHub Issues](https://github.com/HarrytheCake/Monumenta-Cakedition/issues/new/choose)，中文或英文均可。整合包的问题请反馈给我们，不要报告给模组作者或 Monumenta。
- **提问：** [Monumenta 非官方 QQ 群](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=HuISJO6q-9qxvbFKs-zh6_cL8E4ESf8p&authKey=v%2B8i%2Be%2BtXWfa4gB%2BjS1q0UeG7TmroP9hMEiwbW7xCLV3VkIwGeOFAzUyrKi3TMNu&noverify=0&group_code=588414395) 或 [Monumenta Discord](https://discord.gg/monumenta)。
- **源码：** [GitHub](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz)。

### 致谢

- [Paperbox](https://www.pixiv.net/en/users/21257681) 创作的加载界面插画，已获授权使用。Silver Destiny 插画版权归 Monumenta 所有。
- [gensokyo-reimagined-qol](https://modrinth.com/modpack/gensokyo-reimagined-qol) 提供的大量模组配置。
- Moe_Muyo 与 ProbsWinter 提供的材质支持。
- [Monumenta Wiki](https://monumenta.wiki.gg/wiki/Monumenta_Wiki) 提供的任务数据。
- 所有模组归其作者所有。

我们自己的模组和整合包工具的大部分代码是在 AI 助手（Claude）的协助下编写的。

**声明：** 这是玩家自制整合包，与 Monumenta、Unofficial Monumenta Mod 及包内任何模组均无关联。
````

## Version 2.2.2

- **Version number:** `2.2.2` · **Name:** `Monumenta Cakedition 2.2.2` · **Channel:** Release · **Game version:** 1.20.4 · **Loader:** Fabric
- **File:** `Monumenta-Cakedition-2.2.2.mrpack` from [GitHub release v2.2.2](https://github.com/HarrytheCake/Monumenta-Cakedition/releases/tag/v2.2.2) (check its SHA-256 against the release notes).

Changelog, compared with 2.2.0 on Modrinth (2025-07-19):

````markdown
**Updates now happen inside the game.** Cakedition Updater tells you in chat when a new version is out and installs it after you close the game. It backs up your server list and configs first. From this version on, you don't need to download the pack again for each update.

**Added**
- Cakedition Updater (our own): pack and resource-pack updates, backups of the server list and configs.
- Monumenta Addons (replaces Flowey Monumenta Mod).
- Monumenta Item Dictionary.
- Remove Reloading Screen.
- Translations for Sodium (resource pack, on for new installs).
- CakeText Translator (our own, optional and off): server text in Simplified or Traditional Chinese.

**Removed**
- Flowey Monumenta Mod (replaced by Monumenta Addons), Very Many Players, Show Me Your Skin!

**Changed**
- **Xaero's Minimap is now optional and off by default.** If your minimap is gone after updating, turn it on in your launcher's mod list.
- Complementary Reimagined shaders are optional, together with Iris.
- Monumenta resource pack 5.7.0 → 6.3.0, now turned on for new installs (2.2.0's setting named an older file, so it stayed off).
- Paperbox loading-screen artwork 1.1.0.
- Fabric Loader 0.16.9 → 0.18.6, and most mods updated.
- Monumenta is in the server list for new installs.

---

**游戏内更新。** 有新版本时，Cakedition Updater 会在聊天栏提示你，并在你关闭游戏后安装。安装前会先备份服务器列表和配置文件。从这个版本开始，每次更新都不需要重新下载整合包。

**新增**
- Cakedition Updater（我们自己的模组）：整合包与资源包更新、服务器列表和配置备份。
- Monumenta Addons（取代 Flowey Monumenta Mod）。
- Monumenta Item Dictionary。
- Remove Reloading Screen。
- Translations for Sodium（资源包，新安装默认开启）。
- CakeText Translator（我们自己的模组，可选，默认关闭）：把服务器文本显示为简体或繁体中文。

**移除**
- Flowey Monumenta Mod（由 Monumenta Addons 取代）、Very Many Players、Show Me Your Skin!

**变更**
- **Xaero 小地图现在是可选模组，默认关闭。** 如果更新后小地图不见了，请在启动器的模组列表中开启。
- Complementary Reimagined 光影与 Iris 一样改为可选。
- Monumenta 资源包 5.7.0 → 6.3.0，新安装现在会默认启用（2.2.0 的设置指向了旧文件名，所以没有生效）。
- Paperbox 加载界面插画更新至 1.1.0。
- Fabric Loader 0.16.9 → 0.18.6，大部分模组已更新。
- 新安装的服务器列表中已包含 Monumenta。
````
