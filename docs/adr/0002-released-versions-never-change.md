# Released pack versions never change; mistakes are fixed forward

Once a pack version is released, its contents are fixed: any change, including undoing a bad release, ships as a new pack version, and the `packwiz` branch and the mirrors are never rewound or force-pushed (branch protection enforces it). Pack file installs only notice a new version number, and installs that already took a bad version can only be moved off it by a newer one, so reverting would strand them.

## Consequences

- A version with a critical bug or exploit is **withdrawn**: taken off the platforms, its GitHub release kept but marked withdrawn, and an **urgent update** released.
- The single exception is a **purge** (leaked personal data or attack material), in this order: revoke or rotate what leaked, delete the pack files and releases everywhere, rewrite history and force-push with protection lifted, ask GitHub Support to drop cached views, release a fix-forward version.
