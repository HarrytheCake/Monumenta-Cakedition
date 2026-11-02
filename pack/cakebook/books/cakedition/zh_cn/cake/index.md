---
order: 4
---
# Cakedition 自制模组

整合包里有七个模组是专门为 Cakedition 编写的，每个都有自己的页面：

- [Cakedition Updater](updater.md)：让整合包和 Monumenta 资源包保持最新，备份你的设置，并把设置从别的整合包搬过来。
- [CakeChat](chat.md)：聊天窗口，以及帮你切换 Monumenta 频道的标签页，聊天记录不限条数。
- [CakeWiki](wiki.md)：在游戏里看 Monumenta Wiki（{key:key.cakewiki.open}）。
- [CakeBook](book.md)：这本书，以及你自己的笔记。
- [CakeFixes](fixes.md)：修复 Minecraft 和其他模组造成的卡顿与变慢。
- *可选：* [CakeText Translator](translator.md)：把游戏里的英文显示成中文。
- *可选：* [CakeText Collector](collector.md)：收集游戏里的英文文本，供译者使用。

## 它们的共同点

- **你的数据留在你的电脑上。** 它们都不会上传任何东西。CakeWiki 只读取 Wiki，Cakedition Updater 只下载整合包自己的文件；只有你用自己的密钥设置了在线翻译服务，CakeText 才会使用它。
- **它们不会替你在服务器上做事。** 唯一会发送的命令是 CakeChat 的 `/join`，而且只在你点击频道标签页时发送。
- **设置：** Mod Menu（**模组**按钮）→ 该模组 → 设置按钮，或使用该模组页面上写的命令。
- **开源**，MIT 许可证（CakeText Translator 为 Apache-2.0）：https://github.com/HarrytheCake
