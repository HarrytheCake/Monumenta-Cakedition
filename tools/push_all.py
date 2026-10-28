"""Release in one go: push main to the packwiz branch at both update-channel addresses (GitHub, GitLab)
and mirror updater/ and caketext/ to their own repos.

    python tools/push_all.py           # check, show what goes out, ask, then push everything
    python tools/push_all.py --check   # only run the checks, push nothing
    python tools/push_all.py --yes     # don't ask before pushing

Nothing is pushed unless every check passes:
  - the working tree is clean (commit first),
  - pack/index.toml and the hash in pack/pack.toml are up to date (`refresh` changes nothing),
  - no remote has commits that main (or the split folder) doesn't,
  - outgoing commits use a no-reply identity and add no personal data (emails, local paths,
    your username, player UUIDs, `lastServer`, profiler links),
  - a dry-run push to every remote succeeds (so a login problem on one address stops all of them).

The update channel is one channel at two addresses (docs/adr/0003): GitHub and GitLab always get the
same packwiz branch in the same run, so they never drift apart.
"""
import getpass
import pathlib
import re
import subprocess
import sys

import packwiz_sync

ROOT = packwiz_sync.ROOT
PACK_BRANCH = "packwiz"
PACK_REMOTES = ["github", "gitlab"]  # update channel addresses, primary first
MIRRORS = [("updater", "updater", "CakeUpdater source"),  # (remote, prefix, label); pushed to <remote> main
           ("caketext", "caketext", "CakeText source")]
MIRROR_BRANCH = "main"
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


def has_ref(ref):
    return subprocess.run(["git", "rev-parse", "-q", "--verify", ref], cwd=ROOT, capture_output=True).returncode == 0


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

    remotes = git("remote").split()
    missing = [r for r in PACK_REMOTES + [m[0] for m in MIRRORS] if r not in remotes]
    if missing:
        fail(f"remote(s) not set up: {', '.join(missing)}. For GitLab:\n"
             f"  git remote add gitlab https://gitlab.com/<user>/Monumenta-Cakedition.git\n"
             f"  git config remote.gitlab.push refs/heads/main:refs/heads/{PACK_BRANCH}")

    print("Checking working tree...")
    if git("status", "--porcelain"):
        fail("uncommitted changes. Commit (or stash) them first:\n" + git("status", "--short"))

    print("Checking pack index...")
    packwiz_sync.refresh()
    if git("status", "--porcelain", "--", "pack"):
        fail("pack/index.toml was out of date. `refresh` has fixed it; review and commit, then run this again.")

    # (label, local commit, remote, remote ref it updates, remote-tracking ref)
    targets = []
    print("Fetching remotes...")
    for remote in PACK_REMOTES:
        git("fetch", "-q", remote)  # a missing packwiz branch (first push to a new address) is fine
        targets.append((f"{remote} {PACK_BRANCH} (update channel: players get this on next launch)",
                        "main", remote, PACK_BRANCH, f"{remote}/{PACK_BRANCH}"))
    for remote, prefix, label in MIRRORS:
        git("fetch", "-q", remote)
        print(f"Splitting {prefix}/...")
        split = git("subtree", "split", f"--prefix={prefix}")
        targets.append((f"{remote} {MIRROR_BRANCH} ({label})", split, remote, MIRROR_BRANCH, f"{remote}/{MIRROR_BRANCH}"))

    # The base for a first push to a new pack address is what the primary address already has public.
    primary = f"{PACK_REMOTES[0]}/{PACK_BRANCH}"
    outgoing = []
    for label, local, remote, branch, tracking in targets:
        if has_ref(tracking):
            if not is_ancestor(tracking, local):
                fail(f"{tracking} has commits that aren't here. It must only be written from this repo.")
            base = tracking
        elif branch == PACK_BRANCH and has_ref(primary):
            base = primary
        else:
            fail(f"{tracking} doesn't exist. Make the first push of that repo by hand, after a personal-data check.")
        log = git("log", "--format=%h %s", f"{base}..{local}")
        if log or base != tracking:
            outgoing.append((label, local, remote, branch, base, log or f"(new branch, same commits as {base})"))
    if not outgoing:
        print("\nNothing to push: every remote is up to date.")
        return

    print("Scanning outgoing commits for personal data...")
    pats = personal_patterns()
    hits = [h for _, local, _, _, base, _ in outgoing for h in scan(base, local, pats)]
    if hits:
        fail("possible personal data in outgoing commits:\n  " + "\n  ".join(dict.fromkeys(hits)))

    version = re.search(r'^version = "([^"]*)"', (ROOT / "pack" / "pack.toml").read_text(encoding="utf-8"), re.M).group(1)
    remote_version = re.search(r'^version = "([^"]*)"', git("show", f"{primary}:pack/pack.toml"), re.M).group(1)
    print(f"\nPack version: {remote_version} -> {version}" if version != remote_version
          else f"\nPack version: {version} (unchanged)")
    for label, *_, log in outgoing:
        print(f"\n{label}:\n  " + log.replace("\n", "\n  "))

    print("\nChecking every remote accepts the push (dry run)...")
    for label, local, remote, branch, *_ in outgoing:
        r = subprocess.run(["git", "push", "--dry-run", remote, f"{local}:refs/heads/{branch}"], cwd=ROOT,
                           capture_output=True)
        if r.returncode:
            fail(f"{remote} refused a dry-run push:\n{r.stderr.decode(errors='replace')}")

    if check_only:
        print("\nAll checks passed (--check: nothing pushed).")
        return
    if not yes and input("\nPush? [y/N] ").strip().lower() != "y":
        print("Cancelled, nothing pushed.")
        return

    for label, local, remote, branch, *_ in outgoing:
        print(f"Pushing {remote} {branch}...")
        r = subprocess.run(["git", "push", remote, f"{local}:refs/heads/{branch}"], cwd=ROOT, capture_output=True)
        if r.returncode:
            fail(f"push to {remote} failed (any pushes listed above it did go out); fix it and run this "
                 f"again so every address matches:\n{r.stderr.decode(errors='replace')}")
    print("Done.")


if __name__ == "__main__":
    main()
