---
order: 2
---
# 撰寫一本書

寫給模組包和資源包作者。一本書就是一個裝著 Markdown 檔案的資料夾，每頁一個檔案，所以用任何文字編輯器都能寫，也能在 GitHub 上預覽。

## 書放在哪裡

- **模組包中：**遊戲資料夾下的 `cakebook/books/<書>/`。模組包更新時會替換這些頁面，玩家不需要開啟任何東西。
- **資源包或模組中：**`assets/<命名空間>/cakebook/<書>/`。只有已啟用的資源包有效；清單中位置較高的資源包會替換同名檔案。

兩處有同一個檔案時，以 books 資料夾為準。`<書>` 是書的 ID：小寫字母、數字、`-`、`_` 和 `.`。

## 書的結構

```
<書>/book.json             標題、順序、圖示
<書>/en_us/index.md        第一頁
<書>/en_us/keys.md         頁面 "keys"
<書>/en_us/mods/index.md   "mods" 資料夾的標題頁
<書>/en_us/mods/sodium.md  頁面 "mods/sodium"
<書>/zh_tw/...             其他語言的同一批頁面
<書>/images/map.png        圖片
```

`book.json`：

```
{
  "title": { "en_us": "Pack guide", "zh_tw": "模組包指南" },
  "order": 0,
  "icon": "minecraft:cake"
}
```

書按 `order` 由小到大排列，第一本會最先開啟。缺少的頁面或標題會依序退回簡體中文（對繁體中文而言）和英文，所以翻譯可以只完成一部分。

## 頁面

頁面標題是它的第一個 `# ` 標題。選用設定寫在檔案最開頭：

```
---
title: Sodium
order: 5
hidden: true
---
```

`order` 決定頁面和資料夾在目錄中的順序（相同時按標題排序）。`hidden: true` 讓頁面不出現在目錄中，但連結和搜尋仍能找到它。

## 支援的 Markdown

- 標題 `#` 到 `######`、段落、`-` 和 `1.` 清單（縮排兩個空格表示巢狀）、`>` 提示框、`---` 分隔線、用三個反引號圍起來的程式碼區塊、簡單的 `|` 表格
- `**粗體**`、`*斜體*`、`~~刪除線~~`、`` `程式碼` ``
- 連結：`[文字](keys.md)` 和 `[文字](../index.md)` 與 GitHub 上的用法相同（相對於頁面所在的資料夾）；`/keys` 從書的根目錄開始；`book:<書>/<頁面>` 跳到另一本書；`note:<ID>` 跳到玩家的筆記；`https://…` 跳到網站
- `{key:<操作 ID>}` 顯示玩家自己設定的按鍵，例如 `{key:key.inventory}` → {key:key.inventory}
- 單獨一行的圖片：`![地圖](../images/map.png)`，或指定寬度：`![地圖|160](../images/map.png)`。路徑規則與連結相同，`minecraft:textures/...` 可從任何資源包載入。僅支援 PNG；不會載入網路圖片。

## 測試

修改檔案後執行 `/cakebook reload`；`/cakebook folder` 會開啟 books 資料夾。書的問題（錯誤的 `book.json`、命名不正確的資料夾）會寫入遊戲記錄檔。
