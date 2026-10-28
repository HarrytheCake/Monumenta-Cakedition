# Monumenta Cakedition

A Fabric 1.20.4 client modpack for the [Monumenta](https://www.playmonumenta.com/) MMORPG server, made for English- and Chinese-speaking players alike.

**[简体中文](#简体中文)**

- **Modrinth:** [modrinth.com/modpack/monucake](https://modrinth.com/modpack/monucake)
- **Newest pack file:** [GitHub Releases](https://github.com/HarrytheCake/Monumenta-Cakedition/releases/latest)
- **Install guide:** [Wiki](https://github.com/HarrytheCake/Monumenta-Cakedition/wiki/Install-Guide)
- **Bug reports and suggestions:** [Issues](https://github.com/HarrytheCake/Monumenta-Cakedition/issues/new/choose), in English or Chinese
- **Questions and chat:** [Monumenta Discord](https://discord.gg/monumenta), or the [Unofficial Monumenta QQ group](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=HuISJO6q-9qxvbFKs-zh6_cL8E4ESf8p&authKey=v%2B8i%2Be%2BtXWfa4gB%2BjS1q0UeG7TmroP9hMEiwbW7xCLV3VkIwGeOFAzUyrKi3TMNu&noverify=0&group_code=588414395)

## What's inside

About 70 mods, chosen to work on Monumenta:
- **Monumenta:** Unofficial Monumenta Mod, Monumenta Addons, Monumenta Item Dictionary, the Monumenta resource pack
- **Performance:** Sodium, Lithium, ModernFix, ImmediatelyFast, More Culling, Enhanced Block Entities and more
- **Visuals:** CIT Resewn and Entity Model/Texture Features for Monumenta's custom items and mobs, dynamic lights, a dark loading screen with Paperbox artwork
- **HUD and chat:** AppleSkin, BetterF3, Xaero's World Map, ChatPlus, longer chat history
- **Quality of life:** Mouse Tweaks, Shulker Box Tooltip, Searchables, Zoomify and more
- **Optional, off by default:** Iris Shaders with Complementary Reimagined, Xaero's Minimap (Fair-Play), CakeText Translator

## Install

Download the `.mrpack` and import it:
- **Prism Launcher:** Add Instance → Import → choose the file.
- **Modrinth App:** Add instance → Import from file.

Mods are downloaded from Modrinth and from our GitHub releases while the pack installs.

**Alternative (Prism Launcher, for updates on every launch):** create a Minecraft 1.20.4 + Fabric Loader 0.18.6 instance, put [`packwiz-installer-bootstrap.jar`](https://github.com/packwiz/packwiz-installer-bootstrap/releases) in its `.minecraft` folder, and set Edit instance → Settings → Custom commands → Pre-launch command to:

```
"$INST_JAVA" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition/packwiz/pack/pack.toml
```

## Updates

**Cakedition Updater** tells you in chat when a newer version of the pack is out, and updates it after you close the game. Before an update it backs up your server list and configs, and offers to restore them afterwards.

- Your optional-mod choices stay as you set them.
- The pack never deletes or changes a file it didn't ship: mods you added yourself and your server list are left alone.

## Our own mods

- **[Cakedition Updater](https://github.com/HarrytheCake/CakeUpdater)** (MIT): pack and resource-pack updates, backups. Works with any packwiz pack.
- **[CakeText Translator](https://github.com/HarrytheCake/CakeText)** (Apache-2.0, optional): shows the game's text in Chinese, from human translations or a translation model you install yourself.

Most of the code of these mods and of the pack's tooling was written with the help of an AI assistant (Claude).

## For developers

The pack itself lives on the [`packwiz` branch](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz) (a [packwiz](https://packwiz.infra.link/) pack, plus its tooling and our mods' source). This `main` branch is the front page; a weekly job keeps Monumenta's change logs and announcements in it.

## Credits

- Loading-screen artwork by Paperbox, used with permission. The Silver Destiny artwork belongs to Monumenta.
- [packwiz](https://github.com/packwiz) by its contributors (MIT).
- Every mod belongs to its authors; see each mod's page on Modrinth.

Monumenta Cakedition is a fan-made modpack, not an official Monumenta product.

---

## 简体中文

为 [Monumenta](https://www.playmonumenta.com/) MMORPG 服务器制作的 Fabric 1.20.4 客户端整合包，同样面向英文和中文玩家。

- **Modrinth：** [modrinth.com/modpack/monucake](https://modrinth.com/modpack/monucake)
- **最新整合包文件：** [GitHub Releases](https://github.com/HarrytheCake/Monumenta-Cakedition/releases/latest)
- **安装指南：** [Wiki](https://github.com/HarrytheCake/Monumenta-Cakedition/wiki/安装指南)（[繁體中文](https://github.com/HarrytheCake/Monumenta-Cakedition/wiki/安裝指南)）
- **反馈问题与建议：** [Issues](https://github.com/HarrytheCake/Monumenta-Cakedition/issues/new/choose)，中文或英文均可
- **提问与交流：** [Monumenta 非官方 QQ 群](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=HuISJO6q-9qxvbFKs-zh6_cL8E4ESf8p&authKey=v%2B8i%2Be%2BtXWfa4gB%2BjS1q0UeG7TmroP9hMEiwbW7xCLV3VkIwGeOFAzUyrKi3TMNu&noverify=0&group_code=588414395)，或 [Monumenta Discord](https://discord.gg/monumenta)

### 包含内容

约 70 个模组，均为适配 Monumenta 挑选：
- **Monumenta：** Unofficial Monumenta Mod、Monumenta Addons、Monumenta Item Dictionary、Monumenta 资源包
- **性能：** Sodium、Lithium、ModernFix、ImmediatelyFast、More Culling、Enhanced Block Entities 等
- **画面：** CIT Resewn 与 Entity Model/Texture Features（用于 Monumenta 的自定义物品和怪物）、动态光源、带 Paperbox 插画的深色加载界面
- **HUD 与聊天：** AppleSkin、BetterF3、Xaero 世界地图、ChatPlus、更长的聊天记录
- **便利性：** Mouse Tweaks、Shulker Box Tooltip、Searchables、Zoomify 等
- **可选（默认关闭）：** Iris 光影与 Complementary Reimagined 光影包、Xaero 小地图（Fair-Play 版）、CakeText Translator

### 安装

下载 `.mrpack` 文件并导入：
- **Prism Launcher：** 添加实例 → 导入 → 选择该文件。
- **Modrinth App：** 添加实例 → 从文件导入。

安装时，模组会从 Modrinth 和我们的 GitHub 发布页下载。

**另一种方式（Prism Launcher，每次启动时自动更新）：** 新建一个 Minecraft 1.20.4 + Fabric Loader 0.18.6 实例，把 [`packwiz-installer-bootstrap.jar`](https://github.com/packwiz/packwiz-installer-bootstrap/releases) 放进实例的 `.minecraft` 文件夹，然后在 编辑实例 → 设置 → 自定义命令 → 启动前命令 中填入：

```
"$INST_JAVA" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition/packwiz/pack/pack.toml
```

### 更新

整合包有新版本时，**Cakedition Updater** 会在聊天栏提示你，并在你关闭游戏后完成更新。更新前它会备份你的服务器列表和配置文件，更新后可以一键恢复。

- 你对可选模组的选择会保持不变。
- 整合包不会删除或修改任何不是它安装的文件：你自己添加的模组和服务器列表都不会被动到。

### 我们自己的模组

- **[Cakedition Updater](https://github.com/HarrytheCake/CakeUpdater)**（MIT）：整合包与资源包更新、备份。适用于任何 packwiz 整合包。
- **[CakeText Translator](https://github.com/HarrytheCake/CakeText)**（Apache-2.0，可选）：把游戏文本显示为中文，来源是人工翻译，或你自行安装的翻译模型。

这些模组和整合包工具的大部分代码是在 AI 助手（Claude）的协助下编写的。

### 开发者

整合包本体位于 [`packwiz` 分支](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz)（一个 [packwiz](https://packwiz.infra.link/) 整合包，以及相关工具和我们模组的源码）。`main` 分支是项目首页，每周会自动同步 Monumenta 的更新日志和公告。

### 致谢

- 加载界面插画由 Paperbox 创作，已获授权使用。Silver Destiny 插画版权归 Monumenta 所有。
- [packwiz](https://github.com/packwiz) 由其贡献者开发（MIT）。
- 所有模组归其作者所有，详见各模组的 Modrinth 页面。

Monumenta Cakedition 是玩家自制整合包，并非 Monumenta 官方产品。
