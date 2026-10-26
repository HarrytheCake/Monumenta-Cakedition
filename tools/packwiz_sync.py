"""Keep the packwiz pack in pack/ in sync with this Prism instance.

    python tools/packwiz_sync.py from-instance   # pull mods + yosbr defaults from .minecraft, then refresh
    python tools/packwiz_sync.py refresh         # rebuild pack/index.toml and the index hash in pack/pack.toml
    python tools/packwiz_sync.py modrinth <folder> <version-id>   # add/replace a Modrinth file (e.g. a resource pack)
    python tools/packwiz_sync.py github <folder> <owner/repo> <tag> [jar-prefix] [--optional="<description>"]
        # point at a GitHub release jar (our own mods); the prefix picks one jar from a multi-jar release

`refresh` does the same job as `packwiz refresh`, so either can be used.
"""
import hashlib
import io
import json
import pathlib
import re
import shutil
import sys
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
MC = ROOT / ".minecraft"

# Installed only when missing, so pack updates never wipe a player's server list.
PRESERVE = {"servers.dat"}
SKIP = {"pack.toml", "index.toml", ".packwizignore"}
# Per-player state and caches in .minecraft/config/yosbr that must not ship (paths relative to yosbr/).
YOSBR_EXCLUDE = ("config/mid", "config/spark/activity.json", "config/monumenta/persistent.json")


def parse_toml(text):
    """Minimal parser for the flat TOML that Prism and packwiz write."""
    root = {}
    cur = root
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("["):
            cur = root
            for part in line.strip("[]").split("."):
                cur = cur.setdefault(part, {})
            continue
        key, _, val = line.partition("=")
        key, val = key.strip(), val.strip()
        if val.startswith("'"):
            val = val[1:-1]
        elif val.startswith('"'):
            val = json.loads(val)
        elif val in ("true", "false"):
            val = val == "true"
        cur[key] = val
    return root


def q(s):
    return json.dumps(s, ensure_ascii=False)


def write_metafile(path, name, filename, side, url, hash_format, hash_, mod_id=None, version=None, option=None):
    lines = [
        f"name = {q(name)}",
        f"filename = {q(filename)}",
        f"side = {q(side)}",
        "",
        "[download]",
        f"url = {q(url)}",
        f"hash-format = {q(hash_format)}",
        f"hash = {q(hash_)}",
    ]
    if option:
        lines += ["", "[option]", f"optional = {str(option['optional']).lower()}",
                  f"default = {str(option.get('default', False)).lower()}"]
        if option.get("description"):
            lines.append(f"description = {q(option['description'])}")
    if mod_id:
        lines += ["", "[update]", "[update.modrinth]", f"mod-id = {q(mod_id)}", f"version = {q(version)}"]
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes("\n".join(lines).encode("utf-8"))


def sync_mods():
    src, dst = MC / "mods" / ".index", PACK / "mods"
    dst.mkdir(parents=True, exist_ok=True)
    seen = set()
    for meta_path in sorted(src.glob("*.pw.toml")):
        m = parse_toml(meta_path.read_text(encoding="utf-8"))
        jar = MC / "mods" / m["filename"]
        disabled = not jar.exists() and jar.with_name(jar.name + ".disabled").exists()
        if not jar.exists() and not disabled:
            print(f"WARN {meta_path.name}: jar {m['filename']} not found, skipping")
            continue
        real = jar if jar.exists() else jar.with_name(jar.name + ".disabled")
        if hashlib.new(m["download"]["hash-format"], real.read_bytes()).hexdigest() != m["download"]["hash"]:
            print(f"WARN {meta_path.name}: jar hash does not match metadata")
        out = dst / meta_path.name
        option = None
        if disabled:
            # A mod disabled in Prism ships as an optional mod that is off by default.
            old = parse_toml(out.read_text(encoding="utf-8")).get("option", {}) if out.exists() else {}
            option = {"optional": True, "default": False,
                      "description": old.get("description", "Off by default.")}
        mr = m["update"]["modrinth"]
        write_metafile(out, m["name"], m["filename"], m["side"], m["download"]["url"],
                       m["download"]["hash-format"], m["download"]["hash"], mr["mod-id"], mr["version"], option)
        seen.add(meta_path.name)
    for stale in dst.glob("*.pw.toml"):
        # Only Modrinth metafiles come from Prism; others (e.g. our GitHub release jars) are kept.
        modrinth_meta = "modrinth" in parse_toml(stale.read_text(encoding="utf-8")).get("update", {})
        if stale.name not in seen and modrinth_meta:
            print(f"removed mods/{stale.name}")
            stale.unlink()


def sync_yosbr():
    """Mirror .minecraft/config/yosbr into pack/config/yosbr file by file."""
    src, dst = MC / "config" / "yosbr", PACK / "config" / "yosbr"

    def wanted(rel):
        return not any(rel == x or rel.startswith(x + "/") for x in YOSBR_EXCLUDE)

    keep = {p.relative_to(src).as_posix() for p in src.rglob("*") if p.is_file()}
    keep = {rel for rel in keep if wanted(rel)}
    for p in sorted(dst.rglob("*"), reverse=True) if dst.exists() else []:
        rel = p.relative_to(dst).as_posix()
        if p.is_file() and rel not in keep:
            p.unlink()
        elif p.is_dir() and not any(p.iterdir()):
            p.rmdir()
    for rel in keep:
        (dst / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / rel, dst / rel)


def modrinth(folder, version_id):
    def get(url):
        req = urllib.request.Request(url, headers={"User-Agent": "Monumenta-Cakedition packwiz_sync"})
        with urllib.request.urlopen(req) as r:
            return json.load(r)
    v = get(f"https://api.modrinth.com/v2/version/{version_id}")
    p = get(f"https://api.modrinth.com/v2/project/{v['project_id']}")
    f = next((f for f in v["files"] if f["primary"]), v["files"][0])
    out = PACK / folder / f"{p['slug']}.pw.toml"
    option = None
    if out.exists():
        option = parse_toml(out.read_text(encoding="utf-8")).get("option")
    write_metafile(out, p["title"], f["filename"], "client", f["url"], "sha512", f["hashes"]["sha512"],
                   p["id"], v["id"], option)
    print(f"wrote {out.relative_to(PACK).as_posix()} -> {f['filename']}")


def github(folder, repo, tag, prefix=None, optional=None):
    """Point a metafile at a jar attached to a GitHub release (our own mods, built by CI).
    `prefix` picks one jar from a release with several; `optional` (a description) makes a new metafile
    optional and off by default. An existing metafile keeps its [option] block."""
    req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/tags/{tag}",
                                 headers={"User-Agent": "Monumenta-Cakedition packwiz_sync"})
    with urllib.request.urlopen(req) as r:
        release = json.load(r)
    jars = [a for a in release["assets"]
            if a["name"].endswith(".jar") and (not prefix or a["name"].startswith(prefix + "-"))]
    if len(jars) != 1:
        sys.exit(f"{repo} {tag}: expected one matching jar, found {[a['name'] for a in jars]}; pass a jar prefix")
    asset = jars[0]
    with urllib.request.urlopen(asset["browser_download_url"]) as r:
        data = r.read()
    if len(data) != asset["size"]:
        sys.exit(f"{asset['name']}: got {len(data)} bytes, release says {asset['size']}")
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        mod = json.loads(jar.read("fabric.mod.json"))
    slug = mod["id"]
    out = PACK / folder / f"{slug}.pw.toml"
    option = parse_toml(out.read_text(encoding="utf-8")).get("option") if out.exists() else None
    if option is None and optional:
        option = {"optional": True, "default": False, "description": optional}
    write_metafile(out, mod["name"], asset["name"], "client", asset["browser_download_url"],
                   "sha512", hashlib.sha512(data).hexdigest(), option=option)
    for old in (PACK / folder).glob(f"{slug}-*.jar"):  # a bundled jar this metafile replaces
        old.unlink()
        print(f"removed {folder}/{old.name}")
    print(f"wrote {out.relative_to(PACK).as_posix()} -> {asset['name']}")


# Things that identify a player and must not ship: cached avatars (UUIDs), spark activity, last joined server.
PERSONAL = [
    re.compile(rb"mc-heads\.net/avatar/[0-9a-f]{32}"),
    re.compile(rb"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
    re.compile(rb"^lastServer:\S", re.M),
    re.compile(rb"spark\.lucko\.me/[A-Za-z0-9]{6,}"),
]


def check_personal():
    hits = []
    for path in PACK.rglob("*"):
        if path.is_file() and path.suffix not in (".zip", ".png", ".dat"):
            data = path.read_bytes()
            hits += [f"{path.relative_to(PACK).as_posix()}: {m.group().decode(errors='replace')}"
                     for rx in PERSONAL for m in [rx.search(data)] if m]
    for h in hits:
        print(f"PERSONAL DATA? {h}")
    return not hits


def write_pack_version():
    """Cakedition Updater compares this with the published pack.toml on installs without packwiz.json."""
    version = re.search(r'^version = "([^"]*)"', (PACK / "pack.toml").read_text(encoding="utf-8"), re.M).group(1)
    out = PACK / "config" / "cakeupdater" / "pack-version.txt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes((version + "\n").encode("utf-8"))


def refresh():
    write_pack_version()
    entries = []
    for path in sorted(PACK.rglob("*"), key=lambda p: p.relative_to(PACK).as_posix()):
        rel = path.relative_to(PACK).as_posix()
        if not path.is_file() or rel in SKIP:
            continue
        lines = ["[[files]]", f"file = {q(rel)}", f"hash = {q(hashlib.sha256(path.read_bytes()).hexdigest())}"]
        if rel.endswith(".pw.toml"):
            lines.append("metafile = true")
        if rel in PRESERVE:
            lines.append("preserve = true")
        entries.append("\n".join(lines))
    index = 'hash-format = "sha256"\n\n' + "\n\n".join(entries) + "\n"
    (PACK / "index.toml").write_bytes(index.encode("utf-8"))
    pack_toml = PACK / "pack.toml"
    text = pack_toml.read_text(encoding="utf-8")
    new_hash = hashlib.sha256(index.encode("utf-8")).hexdigest()
    text = re.sub(r'(\[index\][^\[]*?hash = )"[0-9a-f]*"', rf'\1"{new_hash}"', text, flags=re.S)
    pack_toml.write_bytes(text.encode("utf-8"))
    print(f"index.toml: {len(entries)} files")
    if not check_personal():
        print("Fix the files above (at their source in .minecraft/config/yosbr) before committing.")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "from-instance":
        sync_mods()
        sync_yosbr()
        refresh()
    elif cmd == "refresh":
        refresh()
    elif cmd == "modrinth" and len(sys.argv) == 4:
        modrinth(sys.argv[2], sys.argv[3])
        refresh()
    elif cmd == "github" and 5 <= len(sys.argv) <= 7:
        args = [a for a in sys.argv[2:] if not a.startswith("--optional=")]
        opt = next((a.split("=", 1)[1] for a in sys.argv[2:] if a.startswith("--optional=")), None)
        github(*args, optional=opt)
        refresh()
    else:
        sys.exit(__doc__)
