"""Release in one go: push main to the packwiz branch and mirror updater/ to its own repo.

    python tools/push_all.py           # check, show what goes out, ask, then push both
    python tools/push_all.py --check   # only run the checks, push nothing
    python tools/push_all.py --yes     # don't ask before pushing

Nothing is pushed unless every check passes:
  - the working tree is clean (commit first),
  - pack/index.toml and the hash in pack/pack.toml are up to date (`refresh` changes nothing),
  - neither remote has commits that main doesn't,
  - outgoing commits use a no-reply identity and add no personal data (emails, local paths,
    your username, player UUIDs, `lastServer`, profiler links).
"""
import getpass
import pathlib
import re
import subprocess
import sys

import packwiz_sync

ROOT = packwiz_sync.ROOT
PACK_REMOTE, PACK_BRANCH = "github", "packwiz"  # main -> packwiz via the remote's push refspec
UPDATER_REMOTE, UPDATER_BRANCH = "updater", "main"
UPDATER_PREFIX = "updater"
NOREPLY = re.compile(r"@(users\.noreply\.github\.com|anthropic\.com)$")


def git(*args, check=True):
    r = subprocess.run(["git", *args], cwd=ROOT, capture_output=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(args)} failed:\n{r.stderr.decode(errors='replace')}")
    return r.stdout.decode("utf-8", errors="replace").strip()


def fail(msg):
    sys.exit(f"\nNOT PUSHED: {msg}")


def is_ancestor(old, new):
    return subprocess.run(["git", "merge-base", "--is-ancestor", old, new], cwd=ROOT).returncode == 0


def personal_patterns():
    """Patterns for data that must not leave this machine. Your own email/username are read at run time
    so they never get written into this (tracked) file."""
    pats = [re.compile(rx.pattern.decode()) for rx in packwiz_sync.PERSONAL]
    pats += [
        re.compile(r"[\w.+-]+@(?!users\.noreply\.github\.com|anthropic\.com)[\w-]+\.[\w.-]*[a-z]", re.I),
        re.compile(r"[A-Za-z]:[\\/]+Users[\\/]+(?!Public)", re.I),
        re.compile(r"/(home|Users)/[a-z]", re.I),
    ]
    for word in {getpass.getuser(), git("config", "--global", "user.email", check=False)}:
        if len(word) >= 4:
            pats.append(re.compile(re.escape(word), re.I))
    return pats


def scan(base, head, pats):
    """Personal-data hits in lines added by base..head and in its commit messages."""
    hits = []
    file = None
    for line in git("diff", "-U0", f"{base}..{head}").splitlines():
        if line.startswith("+++ "):
            file = line[6:]
        elif line.startswith("+"):
            hits += [f"{file}: {m.group()}" for p in pats for m in [p.search(line)] if m]
    hits += [f"commit message: {m.group()}" for p in pats
             for m in [p.search(git("log", "--format=%B", f"{base}..{head}"))] if m]
    for who in git("log", "--format=%h %an <%ae> / %cn <%ce>", f"{base}..{head}").splitlines():
        if not all(NOREPLY.search(e) for e in re.findall(r"<([^>]*)>", who)):
            hits.append(f"identity: {who}")
    return hits


def main():
    sys.stdout.reconfigure(errors="replace")
    check_only = "--check" in sys.argv
    yes = "--yes" in sys.argv

    print("Checking working tree...")
    if git("status", "--porcelain"):
        fail("uncommitted changes. Commit (or stash) them first:\n" + git("status", "--short"))

    print("Checking pack index...")
    packwiz_sync.refresh()
    if git("status", "--porcelain", "--", "pack"):
        fail("pack/index.toml was out of date. `refresh` has fixed it; review and commit, then run this again.")

    print("Fetching remotes...")
    git("fetch", "-q", PACK_REMOTE, PACK_BRANCH)
    git("fetch", "-q", UPDATER_REMOTE, UPDATER_BRANCH)
    pack_remote = f"{PACK_REMOTE}/{PACK_BRANCH}"
    upd_remote = f"{UPDATER_REMOTE}/{UPDATER_BRANCH}"

    print("Splitting updater/...")
    split = git("subtree", "split", f"--prefix={UPDATER_PREFIX}")

    if not is_ancestor(pack_remote, "main"):
        fail(f"{pack_remote} has commits that main doesn't. Look at them before pushing.")
    if not is_ancestor(upd_remote, split):
        fail(f"{upd_remote} has commits that aren't in updater/ here. It must only be written from this repo.")

    pack_log = git("log", "--format=  %h %s", f"{pack_remote}..main")
    upd_log = git("log", "--format=  %h %s", f"{upd_remote}..{split}")
    if not pack_log and not upd_log:
        print("\nNothing to push: both remotes are up to date.")
        return

    print("Scanning outgoing commits for personal data...")
    pats = personal_patterns()
    hits = scan(pack_remote, "main", pats) + scan(upd_remote, split, pats)
    if hits:
        fail("possible personal data in outgoing commits:\n  " + "\n  ".join(dict.fromkeys(hits)))

    version = re.search(r'^version = "([^"]*)"', (ROOT / "pack" / "pack.toml").read_text(encoding="utf-8"), re.M).group(1)
    remote_version = re.search(r'^version = "([^"]*)"', git("show", f"{pack_remote}:pack/pack.toml"), re.M).group(1)
    print(f"\nPack version: {remote_version} -> {version}" if version != remote_version
          else f"\nPack version: {version} (unchanged)")
    print(f"\n{PACK_REMOTE} {PACK_BRANCH} (players get this on next launch):\n{pack_log or '  up to date'}")
    print(f"\n{UPDATER_REMOTE} {UPDATER_BRANCH} (CakeUpdater source):\n{upd_log or '  up to date'}")

    if check_only:
        print("\nAll checks passed (--check: nothing pushed).")
        return
    if not yes and input("\nPush? [y/N] ").strip().lower() != "y":
        print("Cancelled, nothing pushed.")
        return

    if pack_log:
        print(f"\nPushing {PACK_REMOTE}...")
        git("push", PACK_REMOTE)  # configured refspec: main:packwiz
    if upd_log:
        print(f"Pushing {UPDATER_REMOTE}...")
        git("push", UPDATER_REMOTE, f"{split}:refs/heads/{UPDATER_BRANCH}")
    print("Done.")


if __name__ == "__main__":
    main()
