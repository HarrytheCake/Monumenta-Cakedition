"""Draws CakeMap's player arrow and mod icon (PNG).

The waypoint icons are SVG files (core/src/main/resources/assets/cakemap/textures/waypoint/*.svg),
edited by hand and drawn by the mod at the exact pixel size they cover (Icons.java).

    python tools/make_icons.py            (from the cakemap folder)
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "core", "src", "main", "resources", "assets", "cakemap", "textures")
INK = (16, 17, 20, 255)  # the dark pixel outline every icon gets


def canvas(size):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def outline(img, colour, width):
    """Adds `width` pixels of `colour` around every opaque pixel (8-neighbourhood)."""
    w, h = img.size
    src = img.load()
    for _ in range(width):
        solid = [[src[x, y][3] > 0 for x in range(w)] for y in range(h)]
        for y in range(h):
            for x in range(w):
                if solid[y][x]:
                    continue
                near = False
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < w and 0 <= ny < h and solid[ny][nx]:
                            near = True
                if near:
                    src[x, y] = colour
    return img


def arrow():
    img = canvas(16)
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (14, 14), (8, 10), (2, 14)], fill=(255, 255, 255, 255))
    d.polygon([(8, 1), (8, 10), (2, 14)], fill=(214, 220, 232, 255))
    return outline(img, INK, 1)


def mod_icon():
    """128 px: a folded map with a pin."""
    s = 128
    img = canvas(s)
    d = ImageDraw.Draw(img)
    paper, paper2, fold, land, water, path = (238, 226, 190, 255), (214, 198, 156, 255), (180, 160, 118, 255), (126, 178, 92, 255), (88, 150, 210, 255), (176, 92, 60, 255)
    panels = [(14, 24, 50, 112), (50, 16, 86, 104), (86, 24, 116, 112)]
    for i, (x0, y0, x1, y1) in enumerate(panels):
        d.polygon([(x0, y0), (x1, y0 + (8 if i == 0 else -8 if i == 1 else 8)), (x1, y1 + (-8 if i == 1 else 0)), (x0, y1)], fill=paper if i != 1 else paper2)
    d.ellipse([22, 40, 62, 80], fill=land)
    d.ellipse([60, 60, 108, 100], fill=water)
    d.line([(26, 96), (48, 70), (70, 74), (96, 46)], fill=path, width=4)
    for x in (50, 86):
        d.line([(x, 16 if x == 50 else 24), (x, 104 if x == 50 else 112)], fill=fold, width=2)
    # the pin
    d.ellipse([78, 14, 108, 44], fill=(58, 141, 255, 255))
    d.polygon([(82, 36), (104, 36), (93, 58)], fill=(58, 141, 255, 255))
    d.ellipse([87, 23, 99, 35], fill=(255, 255, 255, 255))
    return outline(img, INK, 2)


def main():
    gui = os.path.join(OUT, "gui")
    os.makedirs(gui, exist_ok=True)
    arrow().save(os.path.join(gui, "arrow.png"))
    mod_icon().save(os.path.join(OUT, "..", "icon.png"))
    print("icons written to", os.path.normpath(OUT))


if __name__ == "__main__":
    sys.exit(main())
