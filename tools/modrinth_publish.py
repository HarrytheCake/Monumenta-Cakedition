"""Publish a released pack file as a new version of the pack's Modrinth project (run by the mrpack CI).

    python tools/modrinth_publish.py <version> <file.mrpack>             # dry run: build the request, check the token
    python tools/modrinth_publish.py <version> <file.mrpack> --publish   # create the Modrinth version

The token comes from the MODRINTH_TOKEN environment variable (a personal access token with "Create versions").
A version that is already on Modrinth is never uploaded again (docs/adr/0002). The changelog is this version's
English + Simplified Chinese changelog files, the same text players see in game.

The dry run checks the token with a version-create request for a project that doesn't exist and has no files:
Modrinth parses the body, then checks the token (401 = rejected), then fails on the project, so nothing can be
created. (A body it can't parse is refused before the token is looked at, which proves nothing.)
Results are also printed as GitHub annotations (::notice:: / ::error::), which show on the workflow run.
"""
import hashlib
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com/v2"
PROJECT = "Yx4XUCFJ"  # modrinth.com/modpack/monucake
PROBE_PROJECT = "AAAAAAAA"  # doesn't exist (HTTP 404); the dry run's token check aims at it
USER_AGENT = "HarrytheCake/Monumenta-Cakedition-ci (github.com/HarrytheCake/Monumenta-Cakedition)"
RELEASES = "https://github.com/HarrytheCake/Monumenta-Cakedition/releases/tag/v"


def note(level, msg):
    print(msg)
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::{level} title=Modrinth::{msg}".replace("\n", "%0A"))


def request(method, url, body=None, headers=None):
    req = urllib.request.Request(url, data=body, method=method, headers={"User-Agent": USER_AGENT, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def multipart(data, file=None):
    boundary = uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
             f"Content-Type: application/json\r\n\r\n{json.dumps(data)}\r\n".encode()]
    if file:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file.name}"\r\n'
                     f"Content-Type: application/x-modrinth-modpack+zip\r\n\r\n".encode() + file.read_bytes() + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def changelog(version):
    folder = ROOT / "pack" / "config" / "cakeupdater" / "changelog" / version
    text = []
    for lang, heading in (("en_us", None), ("zh_cn", "简体中文")):
        f = folder / f"{lang}.md"
        if f.is_file():
            body = f.read_text(encoding="utf-8").strip()
            text.append(f"## {heading}\n\n{body}" if heading else body)
    text.append(f"Install info, credits and the file's SHA-256: [GitHub release v{version}]({RELEASES}{version})")
    return "\n\n---\n\n".join(text)


def version_data(version):
    pack = (ROOT / "pack" / "pack.toml").read_text(encoding="utf-8")
    minecraft = re.search(r'^minecraft\s*=\s*"([^"]+)"', pack, re.M).group(1)
    return {
        "project_id": PROJECT,
        "name": f"Monumenta Cakedition {version}",
        "version_number": version,
        "changelog": changelog(version),
        "dependencies": [],
        "game_versions": [minecraft],
        "version_type": "release",
        "loaders": ["fabric"],
        "featured": False,
        "file_parts": ["file"],
        "primary_file": "file",
    }


def probe_data(data):
    """A version-create body that Modrinth parses (so it reaches the token check) but can't create: no files, and a
    project ID that doesn't exist. A bad token gets 401; a good one gets past it and fails on the project."""
    probe = {**data, "project_id": PROBE_PROJECT, "version_number": "0.0.0-token-check", "file_parts": []}
    probe.pop("primary_file", None)
    return probe


def on_modrinth(version):
    status, body = request("GET", f"{API}/project/{PROJECT}/version")
    if status != 200:
        sys.exit(f"Could not list the Modrinth versions: HTTP {status} {body[:200]}")
    return any(v["version_number"] == version for v in json.loads(body))


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    publish = "--publish" in sys.argv
    if len(args) != 2:
        sys.exit(__doc__)
    version, file = args[0], pathlib.Path(args[1])
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    data = version_data(version)

    print(f"File: {file.name}, {file.stat().st_size:,} bytes, sha512 {hashlib.sha512(file.read_bytes()).hexdigest()[:16]}...")
    print("Version data:\n" + json.dumps({**data, "changelog": data["changelog"][:300] + "..."}, ensure_ascii=False, indent=2))
    exists = on_modrinth(version)

    if not publish:
        if not token:
            note("warning", "dry run: MODRINTH_TOKEN is not set")
            return
        body, content_type = multipart(probe_data(data))
        status, reply = request("POST", f"{API}/version", body, {"Authorization": token, "Content-Type": content_type})
        if status == 401:
            note("error", f"dry run: Modrinth rejected the token (HTTP 401): {reply[:200]}")
            sys.exit(1)
        verdict = f"accepted (the probe got HTTP {status}: {reply[:120]})"
        note("notice", f"dry run for {version}: token {verdict}; version is "
                       f"{'already on Modrinth (would be skipped)' if exists else 'not on Modrinth yet (would be created)'}; "
                       f"file {file.name} ({file.stat().st_size:,} bytes); changelog {len(data['changelog'])} characters")
        return

    if exists:
        note("notice", f"{version} is already on Modrinth; not uploaded again")
        return
    if not token:
        note("warning", f"MODRINTH_TOKEN is not set; {version} was not published to Modrinth")
        return
    body, content_type = multipart(data, file)
    status, reply = request("POST", f"{API}/version", body, {"Authorization": token, "Content-Type": content_type})
    if status != 200:
        note("error", f"Modrinth refused {version}: HTTP {status} {reply[:300]}")
        sys.exit(1)
    note("notice", f"Published {version} to Modrinth: https://modrinth.com/modpack/monucake/version/{json.loads(reply)['id']}")


if __name__ == "__main__":
    main()
