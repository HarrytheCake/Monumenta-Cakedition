# One update channel at two addresses: GitHub, then GitLab

Installs read the update channel from GitHub and fall back to an identical GitLab copy when GitHub fails or times out (a config key can prefer GitLab), because GitHub is slow or blocked for some of our Chinese-speaking players. It is one channel at two addresses, not two channels: `push_all` updates both in the same run and refuses if either fails, so they can never drift apart.

## Consequences

- The fallback only ships once an update can finish without GitHub at all: our own mods download from Modrinth, and the pack file carries `packwiz-installer.jar` as well as the bootstrap.
