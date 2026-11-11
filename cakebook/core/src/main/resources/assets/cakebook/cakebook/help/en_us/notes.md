---
order: 1
---
# Your notes

Your notes are listed under **My notes** at the bottom of the contents, newest first. Search finds them too.

## Writing a note

1. Click **New note** (or type `/cakebook note <title>`).
2. Give it a title and write. **Save** or Escape keeps it; **Cancel** throws the changes away.
3. **Edit** and **Delete** appear under a note while you read it.

## Handy buttons in the editor

- **Insert location** adds where you stand: the area and shard when Unofficial Monumenta Mod is installed, and your coordinates.
- **Insert link…** picks a book page or another note and adds a link to it. Click the link later to jump there.

## Formatting

Notes use the same simple Markdown as the books:

- `**bold**`, `*italic*`, `~~struck out~~`, `` `code` ``
- A line starting with `- ` or `1. ` is a list item; `# ` starts a heading; `> ` a tip box
- `[text](book:help/notes)` links to a book page, `[text](note:my-note)` to a note, `[text](https://…)` to a website
- `{key:key.inventory}` shows your current key for that control: {key:key.inventory}

## Where they are kept

Each note is a plain text file in `config/cakebook/notes/` in your game folder. Copy that folder to back them up or to move them to another install.
