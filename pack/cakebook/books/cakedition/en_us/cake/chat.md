---
order: 2
---
# CakeChat

The pack's chat, made for Monumenta.

## Windows

You can have more than one chat window. While the chat is open, drag a window by its tab bar and resize it from its top-right corner. Add windows and tabs in the settings.

## Tabs and channels

The main window has tabs for Monumenta's channels: **All**, **Global**, **LFG**, **Trade**, **中文**, **Guild** and **Whisper**. They only show on Monumenta.

- Clicking a channel tab joins that channel, so what you type goes there.
- **Guild** joins your guild's channel. CakeChat reads your guild's tag from the Guild part of the tab list ({key:key.playerlist}) when you click it. `/cakechat tablist` shows what it read.
- **All** takes you back to the channel you picked yourself.
- CakeChat never leaves a channel for you, and sends `/join` only when you click a tab.

## History

The history has no limit and stays when you switch shards or reconnect (it isn't saved when you quit). Repeated lines are merged into one with a counter. Timestamps can be turned on in the settings.

## Find, copy, peek, hide

- **Ctrl+F** in the open chat searches every message; the matches are highlighted.
- **Right-click** a message to copy its text, with colour codes, or as JSON.
- Hold {key:key.cakechat.peek} to see the whole chat while playing.
- {key:key.cakechat.hide} hides or shows the chat (no key by default: set one in Controls).

## Settings

`/cakechat` or Mod Menu. Each tab has a pattern (which messages it shows), a prefix (put in front of what you send from it), a channel and the server it shows on.

## Coming from ChatPlus

Cakedition Updater can bring your ChatPlus windows and tabs over when it imports your old pack: see [Moving in from another pack](updater.md#moving-in-from-another-pack).
