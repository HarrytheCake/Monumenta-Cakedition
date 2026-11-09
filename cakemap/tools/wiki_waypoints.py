"""Builds CakeMap's built-in Monumenta waypoints from monumenta.wiki.gg (CC BY-SA 4.0).

    python tools/wiki_waypoints.py [<cakemap data folder of the monumenta server>]

Sources: the "Point of Interest List" page (name, region, coordinates), each POI's infobox
("dungeon" field: the dungeon a POI leads to, often with the entrance button's coordinates),
pages with {{Infobox location}} (towns and cities), {{Infobox NPC}} (traders, charm traders) and
{{Quest}} (each quest's starting NPC, region and coordinates: quest givers; "quest_main" when one of
their quests is in MAIN_QUESTS, the Quest Flowchart's story / wool / major side quests). Region
names map to shards: King's Valley = monumenta:valley, Celsian Isles = monumenta:isles, Architect's
Ring = monumenta:ring. Towns: the cities in each region page's "Cities" section, with the
coordinates given there or on the city's page.

Other towns and NPCs have no region on the wiki. With a CakeMap data folder (e.g. one filled by the Xaero
import: <game>/cakemap/monumenta), each of them is placed in the one shard whose explored terrain
fits its coordinates; points that fit none or several shards are left out. Without it, only POIs
and dungeon entrances are written.

Writes core/src/main/resources/assets/cakemap/cakemap/waypoints/monumenta.json.
"""
import json
import os
import re
import struct
import sys
import time
import urllib.parse
import urllib.request
import zlib

UA = "CakeMap waypoint builder (github.com/HarrytheCake/CakeMap)"
API = "https://monumenta.wiki.gg/api.php"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "core", "src", "main", "resources", "assets", "cakemap", "cakemap", "waypoints", "monumenta.json")
REGIONS = {"King's Valley": "monumenta:valley", "Celsian Isles": "monumenta:isles", "Architect's Ring": "monumenta:ring"}
SHARDS = ["valley", "isles", "ring"]
BOSSES = {"Azacor", "Kaul", "Eldrask", "Hekawt", "Sirius"}
COORD = re.compile(r"[\(\[]?\s*(-?\d{1,5})\s*[, ]\s*(-?\d{1,3})\s*[, ]\s*(-?\d{1,5})\s*[\)\]]?")


def get(params):
    q = urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(API + "?" + q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def wikitext(page):
    return get({"action": "parse", "page": page, "prop": "wikitext", "redirects": "1"})["parse"]["wikitext"]["*"]


def texts(titles):
    out = {}
    titles = sorted(set(titles))
    for i in range(0, len(titles), 40):
        d = get({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "redirects": "1", "titles": "|".join(titles[i:i + 40])})
        redirect = {r["from"]: r["to"] for r in d["query"].get("redirects", [])}
        norm = {n["from"]: n["to"] for n in d["query"].get("normalized", [])}
        by = {}
        for p in d["query"]["pages"].values():
            try:
                by[p["title"]] = p["revisions"][0]["slots"]["main"]["*"]
            except (KeyError, IndexError):
                pass
        for t in titles[i:i + 40]:
            k = norm.get(t, t)
            k = redirect.get(k, k)
            if k in by:
                out[t] = by[k]
        time.sleep(0.5)
    return out


def embedded(template):
    titles, cont = [], {}
    while True:
        d = get({"action": "query", "list": "embeddedin", "eititle": template, "eilimit": "500", **cont})
        titles += [p["title"] for p in d["query"]["embeddedin"] if ":" not in p["title"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def unlink(s):
    return re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", s).strip()


def field(text, name):
    """An infobox field, up to the next field or the end of the infobox (may span lines)."""
    m = re.search(r"\|\s*" + name + r"\s*=(.*?)(?=\n\s*\||\n\}\}|\|\s*\w+\s*=|\Z)", text, re.S)
    return m.group(1).strip() if m else ""


# ---- explored terrain (CakeMap region files) ----

class Terrain:
    def __init__(self, root):
        self.root = root
        self.cache = {}

    def column(self, shard, x, z):
        rx, rz = x >> 9, z >> 9
        key = (shard, rx, rz)
        if key not in self.cache:
            f = os.path.join(self.root, "monumenta", shard, f"{rx}_{rz}.cmr")
            self.cache[key] = self._read(f) if os.path.exists(f) else None
        r = self.cache[key]
        if r is None:
            return None
        i = (z & 511) * 512 + (x & 511)
        if struct.unpack_from(">h", r[0], 2 * i)[0] == 0:
            return None
        return struct.unpack_from(">h", r[1], 2 * i)[0]

    @staticmethod
    def _read(f):
        area = 512 * 512
        body = zlib.decompress(open(f, "rb").read()[16:])
        p = 0
        for _ in range(2):  # state palette, biome palette
            n = struct.unpack_from(">i", body, p)[0]
            p += 4
            for _ in range(n):
                p += 2 + struct.unpack_from(">H", body, p)[0]
        state = body[p:p + 2 * area]
        p += 2 * area + area
        return state, body[p:p + 2 * area]

    def fits(self, shard, x, y, z):
        hs = [h for dx in range(-6, 7, 3) for dz in range(-6, 7, 3) if (h := self.column(shard, x + dx, z + dz)) is not None]
        return len(hs) >= 5 and min(hs) - 70 <= y <= max(hs) + 4

    def shard(self, x, y, z, hint=None):
        """The one shard whose terrain fits; with several, the one the page's own text names."""
        fit = [s for s in SHARDS if self.fits(s, x, y, z)]
        if len(fit) == 1:
            return "monumenta:" + fit[0]
        if hint and hint.split(":")[1] in fit:
            return hint
        return None


# ---- main quests: the coloured boxes of the Monumenta Quest Flowchart (updated June 2026) ----
# Story quests (green), wool quests (blue) and major side quests (orange) get the "quest_main" icon
# (light red triangle, three "!"); quest givers with only side quests (white boxes) keep "quest".
# Specialization trials (orange, "all over the Isles") are found by the {{Quest}} field "spec".
# Edit this list when the flowchart changes; -v prints names that matched no quest page.
MAIN_QUESTS = [
    # Region 1: King's Valley
    "A Little Spark", "A Crew to Remember", "Tutorial", "Tutorial: The Forgotten Halls", "Lost in My Mind", "Memories of Battle", "Mage's Legacy",
    "The Staff", "The Staff?", "Of Monks and Magic", "Sons of the Forest", "Winds of War", "Beasts of Men",
    "Roots of the Plague", "Siege of Highwatch", "Scars of Magic", "Words Upon Hushed Lips", "Soul of the Forest",
    "A Study in Crimson I", "A Study in Crimson II", "Unveiled", "The Elder's Scrolls", "Crystal Visions",
    "Voyage of Discovery", "Starry Night", "Starrier Night", "Starriest Night", "Into Thin Air", "All That Remains",
    "Tlaxan Troubles", "The Crystal Connection", "Shurima's Big Day Off", "Outer Demons",
    # Region 2: Celsian Isles
    "Raiding Party", "An Alcoholic Accord", "The Wizard's Apprentice", "The Lost Empire", "Xenophobia",
    "The Gem of Ishnir", "Harmonic Convergence", "The Foreign Soldier", "Secrets Best Left Forgotten", "Mirrored Egos",
    "An Unlikely Partnership", "Rapacity", "Silver Skinned", "Bleeding Proof", "Kingdom of Lies", "R is for Reprisal",
    "Unmasked", "The Olive Branch", "Here There Be Dragons", "Borrowed Time", "The Tomb of Time", "Ring Around the Rose",
    "Master of One", "Fred's Bizarre Adventure", "A Glich in the System", "The Stone Ocean", "The End Chimes",
    "Depths Charge", "Primeval Creations I", "Primeval Creations II", "Primeval Creations III", "Primeval Creations IV",
    "Primeval Creations V", "Primeval Creations VI",
    # Region 3: Architect's Ring
    "Into the Woods", "Of Mushrooms and Magic", "Wolf in Witch's Clothing", "A Wolf in Witch's Clothing", "Sporetal Combat", "Unraveled",
    "Bad Moon Rising", "Unconventional Methods", "Behind the Starlit Shroud", "A Sirius Problem", "As Below, So Above",
    "Starless Night", "Holding Space", "Defying Gravity", "Cure for Insomnia", "Praetorer Strike", "Keeper Agents",
    "Mechs Machina", "Assisted Dreaming", "External Corruption", "A Union Cross", "Sleeping Shadows",
    "Scientists of Steelmeld III", "For Whom the Bell Tolls", "Remnants of the Empire",
]


def quest_key(name):
    """Compare quest names loosely: case, punctuation, a "(Quest)" suffix and "Part One" vs "I" don't matter."""
    n = re.sub(r"\(quest\)", "", name.lower())
    n = re.sub(r"\bpart one\b", "i", n)
    n = re.sub(r"\bpart two\b", "ii", n)
    return re.sub(r"[^a-z0-9?]", "", n)


def region_cities():
    """{city: (dim, (x, y, z) or None)} from each region page's "Cities" section: its deepest headings
    are the cities (shallower ones are sub-regions), with the coordinates the page gives under them."""
    out = {}
    for name, dim in REGIONS.items():
        t = wikitext(name.replace(" ", "_"))
        m = re.search(r"(?im)^==\s*(cities|towns|settlements)[^=]*==\s*$", t)
        if not m:
            continue
        sec = t[m.end():]
        n = re.search(r"(?m)^==[^=]", sec)
        sec = sec[:n.start()] if n else sec
        heads = list(re.finditer(r"(?m)^(={3,5})\s*([^=]+?)\s*\1\s*$", sec))
        if not heads:
            continue
        deepest = max(len(h.group(1)) for h in heads)
        for i, h in enumerate(heads):
            if len(h.group(1)) != deepest:
                continue
            body = sec[h.end():heads[i + 1].start() if i + 1 < len(heads) else len(sec)]
            c = re.search(r"Coordinates?:?\s*'*\s*\(?(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)", body)
            out[unlink(h.group(2))] = (dim, (int(c[1]), int(c[2]), int(c[3])) if c else None)
    return out


def region_hint(text):
    """The region a page names most often (King's Valley, Celsian Isles, Architect's Ring), or None."""
    counts = {dim: len(re.findall(re.escape(name), text)) for name, dim in REGIONS.items()}
    best = max(counts, key=counts.get)
    return best if counts[best] > 0 else None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    terrain = Terrain(args[0]) if args else None
    out = {}

    def add(dim, kind, name, x, y, z, ident):
        out.setdefault(dim, []).append({"id": "wiki:" + ident, "name": name, "kind": kind, "x": x, "y": y, "z": z})

    # POIs
    poi_text = wikitext("Point_of_Interest_List")
    pois = []
    for row in poi_text.split("|-"):
        cells = [c.strip() for c in re.split(r"\n\|", row) if c.strip()]
        if len(cells) < 4:
            continue
        link = re.search(r"\[\[([^|\]]*)(?:\|([^\]]*))?\]\]", cells[0])
        m = re.search(r"\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)", cells[3])
        dim = REGIONS.get(unlink(cells[1]))
        if link and m and dim:
            pois.append((link.group(1), link.group(2) or link.group(1), dim, int(m[1]), int(m[2]), int(m[3])))
    pages = texts([p[0] for p in pois])
    for page, name, dim, x, y, z in pois:
        add(dim, "poi", name, x, y, z, "poi/" + page)
        d = field(pages.get(page, ""), "dungeon")
        if not d or d.startswith("|"):
            continue
        for target in re.findall(r"\[\[([^|\]]*)(?:\|[^\]]*)?\]\]", d)[:1]:
            c = COORD.search(d)
            ex, ey, ez = (int(c[1]), int(c[2]), int(c[3])) if c else (x, y, z)
            kind = "boss" if target in BOSSES else "dungeon"
            add(dim, kind, target, ex, ey, ez, kind + "/" + target)

    # quest givers: {{Quest}} pages name the starting NPC, region and coordinates; one waypoint per NPC place
    givers = {}
    main_keys = {quest_key(q) for q in MAIN_QUESTS}
    matched = set()
    for title, t in texts(embedded("Template:Quest")).items():
        m = re.search(r"\{\{\s*Quest\b(.*?)\}\}\s*\n", t, re.S)
        if not m:
            continue
        box = m.group(1)
        dim = REGIONS.get(unlink(field(box, "region")))
        c = COORD.search(field(box, "starting_npc_coordinates"))
        npc = unlink(field(box, "starting_npc_name"))
        if not dim or not c or not npc:
            continue
        key = (dim, int(c[1]), int(c[2]), int(c[3]))
        quest = unlink(field(box, "title1")) or title
        main = quest_key(quest) in main_keys or quest_key(title) in main_keys or bool(field(box, "spec").strip("| \n"))
        matched.update(k for k in (quest_key(quest), quest_key(title)) if k in main_keys)
        entry = givers.setdefault(key, [npc, [], False])
        entry[1].append(quest)
        entry[2] = entry[2] or main
    for (dim, x, y, z), (npc, quests, main) in givers.items():
        add(dim, "quest_main" if main else "quest", npc + ": " + ", ".join(sorted(quests)), x, y, z, "quest/" + npc + "/" + str(x) + "," + str(z))
    if "-v" in sys.argv:
        print("main quests on no quest page:", [q for q in MAIN_QUESTS if quest_key(q) not in matched])

    # towns: the cities each region page lists (its shard), at the coordinates it gives or the city page's
    skipped = []
    cities = region_cities()
    city_pages = texts([c for c, (_, xyz) in cities.items() if xyz is None])
    for city, (dim, xyz) in cities.items():
        if xyz is None:
            page = city_pages.get(city, "")
            loc = field(page, "location").replace("Y", "64")
            # the infobox's location, else the first x, y, z in the page, else an infobox "x z" (y = 64)
            c = COORD.search(loc) or re.search(r"(-?\d{2,5}),\s*(-?\d{1,3}),\s*(-?\d{2,5})", page)
            xz = re.fullmatch(r"\s*(-?\d{1,5})[ ,]+(-?\d{1,5})\s*", loc)
            xyz = (int(c[1]), int(c[2]), int(c[3])) if c else (int(xz[1]), 64, int(xz[2])) if xz else None
        if xyz is None:
            skipped.append(city)
            continue
        add(dim, "town", city, *xyz, "town/" + city)

    # other towns and NPCs: only with explored terrain to place them
    if terrain:
        loc = texts(embedded("Template:Infobox_location"))
        for title, t in loc.items():
            if title in cities:
                continue
            kind = unlink(field(t, "type")).lower()
            if kind not in ("town", "city"):
                continue
            c = COORD.search(field(t, "location").replace("Y", "64"))
            if not c:
                skipped.append(title)
                continue
            x, y, z = int(c[1]), int(c[2]), int(c[3])
            dim = terrain.shard(x, y, z, region_hint(t))
            if dim:
                add(dim, "town", title, x, y, z, "town/" + title)
            else:
                skipped.append(title)
        npcs = texts(embedded("Template:Infobox_NPC"))
        for title, t in npcs.items():
            m = re.search(r"\{\{\s*Infobox[ _]NPC(.*?)\}\}", t, re.S | re.I)
            if not m:
                continue
            c = COORD.search(field(m.group(1), "coordinates"))
            if not c:
                continue
            low = t.lower()
            if "charm" in title.lower() or ("charm" in low and ("trade" in low or "sells" in low)):
                kind = "charm_trader"
            elif "quest" in low and "quest giver" in low:
                kind = "quest"
            elif "trade" in low or "sells" in low or "shop" in low:
                kind = "trader"
            else:
                continue
            x, y, z = int(c[1]), int(c[2]), int(c[3])
            dim = terrain.shard(x, y, z, region_hint(t))
            if dim:
                add(dim, kind, title, x, y, z, "npc/" + title)
            else:
                skipped.append(title)
    if "-v" in sys.argv:
        print("skipped:", skipped)

    result = {
        "_source": "monumenta.wiki.gg, CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). Built by tools/wiki_waypoints.py on "
                   + time.strftime("%Y-%m-%d") + ".",
        "_license": "CC BY-SA 4.0: this file only; the rest of CakeMap is MIT.",
    }
    for dim in sorted(out):
        seen = set()
        uniq = []
        for w in sorted(out[dim], key=lambda w: (w["kind"], w["name"])):
            if w["id"] not in seen:
                seen.add(w["id"])
                uniq.append(w)
        result[dim] = uniq
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(result, f, ensure_ascii=False, indent="\t")
        f.write("\n")
    counts = {}
    for dim, ws in out.items():
        for w in ws:
            counts[w["kind"]] = counts.get(w["kind"], 0) + 1
    print("written", os.path.normpath(OUT), counts, "skipped", len(skipped))


if __name__ == "__main__":
    main()
