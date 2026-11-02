---
order: 4
---
# Cakedition 自製模組

模組包裡有七個模組是專門為 Cakedition 編寫的，每個都有自己的頁面：

- [Cakedition Updater](updater.md)：讓模組包和 Monumenta 資源包保持最新，備份你的設定，並把設定從別的模組包搬過來。
- [CakeChat](chat.md)：聊天視窗，以及幫你切換 Monumenta 頻道的分頁，聊天記錄不限筆數。
- [CakeWiki](wiki.md)：在遊戲裡看 Monumenta Wiki（{key:key.cakewiki.open}）。
- [CakeBook](book.md)：這本書，以及你自己的筆記。
- [CakeFixes](fixes.md)：修正 Minecraft 和其他模組造成的卡頓與變慢。
- *選用：* [CakeText Translator](translator.md)：把遊戲裡的英文顯示成中文。
- *選用：* [CakeText Collector](collector.md)：收集遊戲裡的英文文字，供譯者使用。

## 它們的共同點

- **你的資料留在你的電腦上。** 它們都不會上傳任何東西。CakeWiki 只讀取 Wiki，Cakedition Updater 只下載模組包自己的檔案；只有你用自己的金鑰設定了線上翻譯服務，CakeText 才會使用它。
- **它們不會替你在伺服器上做事。** 唯一會傳送的指令是 CakeChat 的 `/join`，而且只在你點擊頻道分頁時傳送。
- **設定：** Mod Menu（**模組**按鈕）→ 該模組 → 設定按鈕，或使用該模組頁面上寫的指令。
- **開放原始碼**，MIT 授權（CakeText Translator 為 Apache-2.0）：https://github.com/HarrytheCake
