"""Build a Modrinth .mrpack from the packwiz pack in pack/.

    python tools/mrpack.py            # writes mrpack/Monumenta-Cakedition-<version>.mrpack (git-ignored)

- Every *.pw.toml becomes a download entry (Modrinth CDN or our GitHub releases), never a bundled jar.
  sha1 and the size come from Modrinth's API, or from downloading the file when it isn't on Modrinth.
- Every other file in the pack becomes an override, like packwiz-installer would install it, except preserve=true
  files (servers.dat): they go to config/cakeupdater/defaults/, so a launcher's update never replaces them.
- packwiz-installer-bootstrap.jar (MIT, pinned v0.0.3) is added as an override with its license, so
  Cakedition Updater can update a .mrpack install without downloading anything itself.
- config/cakeupdater/adoption.json lists every file the .mrpack installs, in packwiz.json's form. Cakedition
  Updater 1.4.1+ turns it into packwiz.json before the install's first update, so packwiz-installer replaces renamed
  jars instead of adding a second copy, and keeps the player's optional-mod choices.
- Pack files over LARGE bytes (the Paperbox art zips) are not bundled: they become downloads from
  raw.githubusercontent.com pinned to the commit being built (the build refuses if the file differs from that commit),
  so the .mrpack, which Cakedition Updater 1.6.0+ downloads for every update, stays small.
- config/cakeupdater/packwiz/ holds the packwiz pack itself (pack.toml, index.toml, every metafile) and files.tsv,
  where each other pack file is: `<path>\t<sha256>\tmrpack:<entry>` or `\turl:<url>`. Cakedition Updater rebuilds the
  pack from it and runs packwiz-installer on that, so an update from Modrinth works exactly like one from GitHub.
"""
import os
import subprocess
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
ADOPTION = "config/cakeupdater/adoption.json"
PACKWIZ = "config/cakeupdater/packwiz"
LARGE = 1 << 20
RAW = "https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition"


def commit_of(rel):
    """The last commit that changed pack/<rel> (already on GitHub when CI builds), after checking that the file on disk
    is what that commit has. Its raw URL then always serves exactly these bytes."""
    root = packwiz_sync.ROOT
    head = os.environ.get("GITHUB_SHA") or "HEAD"
    commit = subprocess.run(["git", "log", "-1", "--format=%H", head, "--", f"pack/{rel}"], cwd=root,
                            capture_output=True, text=True, check=True).stdout.strip()
    committed = subprocess.run(["git", "rev-parse", f"{commit}:pack/{rel}"], cwd=root, capture_output=True, text=True)
    on_disk = subprocess.run(["git", "hash-object", str(PACK / rel)], cwd=root, capture_output=True, text=True, check=True)
    if committed.returncode or committed.stdout.strip() != on_disk.stdout.strip():
        sys.exit(f"pack/{rel} differs from commit {commit[:7]}: commit it first (the .mrpack links to it there)")
    return commit


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
    # index.toml lists every file in the pack: `file = "..."` then its sha256 `hash = "..."`, once per [[files]] entry
    index_hash = {}
    for line in (PACK / "index.toml").read_text(encoding="utf-8").splitlines():
        if line.startswith("file = "):
            rel = line.split("=", 1)[1].strip().strip('"')
        elif line.startswith("hash = "):
            index_hash[rel] = line.split("=", 1)[1].strip().strip('"')
    rels = list(index_hash)

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
    adoption = {}
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
        if m.get("side", "both") != "server":
            adoption[rel] = {
                "hash": {"type": "sha256", "value": index_hash[rel]},
                "linkedFileHash": {"type": dl["hash-format"], "value": dl["hash"]},
                "cachedLocation": files[-1]["path"],
                **({"isOptional": True} if m.get("option", {}).get("optional", False) else {}),
            }
    for rel in overrides:
        if rel not in packwiz_sync.PRESERVE:  # installed elsewhere (defaults), so packwiz-installer must not own it
            adoption[rel] = {"hash": {"type": "sha256", "value": index_hash[rel]}, "cachedLocation": rel}

    # large files become downloads (see the module docstring); the rest stay overrides
    linked = {}
    for rel in overrides:
        if rel not in packwiz_sync.PRESERVE and (PACK / rel).stat().st_size > LARGE:
            data = (PACK / rel).read_bytes()
            linked[rel] = f"{RAW}/{commit_of(rel)}/pack/{rel}"
            files.append({"path": rel, "hashes": {"sha1": hashlib.sha1(data).hexdigest(),
                                                   "sha512": hashlib.sha512(data).hexdigest()},
                          "env": {"client": "required", "server": "required"}, "downloads": [linked[rel]],
                          "fileSize": len(data)})
    bundled = [rel for rel in overrides if rel not in linked]
    tsv = []
    for rel in overrides:
        where = f"url:{linked[rel]}" if rel in linked else \
            "mrpack:overrides/" + (f"config/cakeupdater/defaults/{rel}" if rel in packwiz_sync.PRESERVE else rel)
        tsv.append(f"{rel}\t{index_hash[rel]}\t{where}")

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
        for rel in bundled:
            # A launcher's update rewrites overrides; preserve=true files (servers.dat) go to Cakedition Updater's
            # defaults instead, which it copies into place only when missing.
            dst = f"config/cakeupdater/defaults/{rel}" if rel in packwiz_sync.PRESERVE else rel
            z.write(PACK / rel, f"overrides/{dst}")
        z.writestr(f"overrides/{BOOTSTRAP}", bootstrap)
        z.writestr("overrides/packwiz-installer-bootstrap-LICENSE.txt", bootstrap_license)
        z.writestr(f"overrides/{ADOPTION}", json.dumps({"files": adoption}, indent=1, ensure_ascii=False))
        # the packwiz pack itself, for Cakedition Updater's updates from Modrinth
        z.write(PACK / "pack.toml", f"overrides/{PACKWIZ}/pack.toml")
        z.write(PACK / "index.toml", f"overrides/{PACKWIZ}/index.toml")
        for rel in metas:
            z.write(PACK / rel, f"overrides/{PACKWIZ}/{rel}")
        z.writestr(f"overrides/{PACKWIZ}/files.tsv", "\n".join(tsv) + "\n")
    print(f"{out.relative_to(packwiz_sync.ROOT).as_posix()}: {len(files)} downloads ({len(linked)} linked pack files), "
          f"{len(bundled) + 3} overrides + the packwiz pack, "
          f"{out.stat().st_size >> 10} KB")


if __name__ == "__main__":
    main()
