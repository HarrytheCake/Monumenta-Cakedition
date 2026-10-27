"""Build a Modrinth .mrpack from the packwiz pack in pack/.

    python tools/mrpack.py            # writes mrpack/Monumenta-Cakedition-<version>.mrpack (git-ignored)

- Every *.pw.toml becomes a download entry (Modrinth CDN or our GitHub releases), never a bundled jar.
  sha1 and the size come from Modrinth's API, or from downloading the file when it isn't on Modrinth.
- Every other file in the pack becomes an override, like packwiz-installer would install it, except preserve=true
  files (servers.dat): they go to config/cakeupdater/defaults/, so a launcher's update never replaces them.
- packwiz-installer-bootstrap.jar (MIT, pinned v0.0.3) is added as an override with its license, so
  Cakedition Updater can update a .mrpack install without downloading anything itself.
"""
import hashlib
import json
import pathlib
import sys
import urllib.request
import zipfile

import packwiz_sync

PACK = packwiz_sync.PACK
OUT = packwiz_sync.ROOT / "mrpack"
UA = {"User-Agent": "Monumenta-Cakedition mrpack"}
BOOTSTRAP = "packwiz-installer-bootstrap.jar"
BOOTSTRAP_URL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/" + BOOTSTRAP
BOOTSTRAP_SHA256 = "a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c"
BOOTSTRAP_LICENSE_URL = "https://raw.githubusercontent.com/packwiz/packwiz-installer-bootstrap/v0.0.3/LICENSE"


def fetch(url, data=None):
    req = urllib.request.Request(url, data=data, headers={**UA, **({"Content-Type": "application/json"} if data else {})})
    with urllib.request.urlopen(req) as r:
        return r.read()


def env(meta):
    side = meta.get("side", "both")
    optional = meta.get("option", {}).get("optional", False)
    client = "unsupported" if side == "server" else ("optional" if optional else "required")
    server = "unsupported" if side == "client" else ("optional" if optional else "required")
    return {"client": client, "server": server}


def main():
    pack = packwiz_sync.parse_toml((PACK / "pack.toml").read_text(encoding="utf-8"))
    version = pack["version"]
    # index.toml lists every file in the pack (`file = "..."` once per [[files]] entry)
    rels = [line.split("=", 1)[1].strip().strip('"') for line in (PACK / "index.toml").read_text(encoding="utf-8").splitlines()
            if line.startswith("file = ")]

    metas = {}
    overrides = []
    for rel in rels:
        if rel.endswith(".pw.toml"):
            metas[rel] = packwiz_sync.parse_toml((PACK / rel).read_text(encoding="utf-8"))
        else:
            overrides.append(rel)

    by_sha512 = {m["download"]["hash"]: rel for rel, m in metas.items() if m["download"]["hash-format"] == "sha512"}
    known = json.loads(fetch("https://api.modrinth.com/v2/version_files",
                             json.dumps({"hashes": list(by_sha512), "algorithm": "sha512"}).encode()))
    files = []
    for rel, m in sorted(metas.items()):
        dl = m["download"]
        info = None
        if dl["hash"] in known:
            info = next(f for f in known[dl["hash"]]["files"] if f["hashes"]["sha512"] == dl["hash"])
            sha1, sha512, size = info["hashes"]["sha1"], dl["hash"], info["size"]
        else:
            data = fetch(dl["url"])
            if hashlib.new(dl["hash-format"], data).hexdigest() != dl["hash"]:
                sys.exit(f"{rel}: downloaded file doesn't match its metafile hash")
            sha1, sha512, size = hashlib.sha1(data).hexdigest(), hashlib.sha512(data).hexdigest(), len(data)
        folder = rel.rsplit("/", 1)[0] if "/" in rel else ""
        files.append({
            "path": f"{folder}/{m['filename']}" if folder else m["filename"],
            "hashes": {"sha1": sha1, "sha512": sha512},
            "env": env(m),
            "downloads": [dl["url"]],
            "fileSize": size,
        })

    bootstrap = fetch(BOOTSTRAP_URL)
    if hashlib.sha256(bootstrap).hexdigest() != BOOTSTRAP_SHA256:
        sys.exit(f"{BOOTSTRAP}: unexpected hash")
    bootstrap_license = fetch(BOOTSTRAP_LICENSE_URL)

    manifest = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": f"{pack['name']} {version}",
        "summary": "A Fabric client modpack for the Monumenta MMORPG server.",
        "files": files,
        "dependencies": {"minecraft": pack["versions"]["minecraft"], "fabric-loader": pack["versions"]["fabric"]},
    }
    OUT.mkdir(exist_ok=True)
    out = OUT / f"{pack['name'].replace(' ', '-')}-{version}.mrpack"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(manifest, indent=1, ensure_ascii=False))
        for rel in overrides:
            # A launcher's update rewrites overrides; preserve=true files (servers.dat) go to Cakedition Updater's
            # defaults instead, which it copies into place only when missing.
            dst = f"config/cakeupdater/defaults/{rel}" if rel in packwiz_sync.PRESERVE else rel
            z.write(PACK / rel, f"overrides/{dst}")
        z.writestr(f"overrides/{BOOTSTRAP}", bootstrap)
        z.writestr("overrides/packwiz-installer-bootstrap-LICENSE.txt", bootstrap_license)
    print(f"{out.relative_to(packwiz_sync.ROOT).as_posix()}: {len(files)} downloads, {len(overrides) + 2} overrides, "
          f"{out.stat().st_size >> 10} KB")


if __name__ == "__main__":
    main()
