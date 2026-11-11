---
order: 2
---
# 编写一本书

写给整合包和资源包作者。一本书就是一个装着 Markdown 文件的文件夹，每页一个文件，所以用任何文本编辑器都能写，也能在 GitHub 上预览。

## 书放在哪里

- **整合包中：**游戏目录下的 `cakebook/books/<书>/`。整合包更新时会替换这些页面，玩家不需要开启任何东西。
- **资源包或模组中：**`assets/<命名空间>/cakebook/<书>/`。只有已启用的资源包有效；列表中位置更高的资源包会替换同名文件。

两处有同一个文件时，以 books 文件夹为准。`<书>` 是书的 ID：小写字母、数字、`-`、`_` 和 `.`。

## 书的结构

```
<书>/book.json             标题、顺序、图标
<书>/en_us/index.md        第一页
<书>/en_us/keys.md         页面 "keys"
<书>/en_us/mods/index.md   "mods" 文件夹的标题页
<书>/en_us/mods/sodium.md  页面 "mods/sodium"
<书>/zh_cn/...             其他语言的同一批页面
<书>/images/map.png        图片
```

`book.json`：

```
{
  "title": { "en_us": "Pack guide", "zh_cn": "整合包指南" },
  "order": 0,
  "icon": "minecraft:cake"
}
```

书按 `order` 从小到大排列，第一本会最先打开。缺少的页面或标题会依次回退到简体中文（对繁体中文而言）和英文，所以翻译可以只完成一部分。

## 页面

页面标题是它的第一个 `# ` 标题。可选设置写在文件最开头：

```
---
title: Sodium
order: 5
hidden: true
---
```

`order` 决定页面和文件夹在目录中的顺序（相同时按标题排序）。`hidden: true` 让页面不出现在目录中，但链接和搜索仍能找到它。

## 支持的 Markdown

- 标题 `#` 到 `######`、段落、`-` 和 `1.` 列表（缩进两个空格表示嵌套）、`>` 提示框、`---` 分隔线、用三个反引号围起来的代码块、简单的 `|` 表格
- `**粗体**`、`*斜体*`、`~~删除线~~`、`` `代码` ``
- 链接：`[文字](keys.md)` 和 `[文字](../index.md)` 与 GitHub 上的用法相同（相对于页面所在的文件夹）；`/keys` 从书的根目录开始；`book:<书>/<页面>` 跳到另一本书；`note:<ID>` 跳到玩家的笔记；`https://…` 跳到网站
- `{key:<操作 ID>}` 显示玩家自己设置的按键，例如 `{key:key.inventory}` → {key:key.inventory}
- 单独一行的图片：`![地图](../images/map.png)`，或指定宽度：`![地图|160](../images/map.png)`。路径规则与链接相同，`minecraft:textures/...` 可从任意资源包加载。仅支持 PNG；不会加载网络图片。

## 测试

修改文件后运行 `/cakebook reload`；`/cakebook folder` 会打开 books 文件夹。书的问题（错误的 `book.json`、命名不正确的文件夹）会写入游戏日志。
