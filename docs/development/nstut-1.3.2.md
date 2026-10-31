# NsTut Forge 1.20.1 fork: 1.3.2 development candidate

This candidate retains the Forge 1.20.1 rejected-login lifecycle fix from
[TrueUUID 1.3.1](nstut-1.3.1.md), while improving authentication failure
messages. Names already bound to verified premium identities still cannot be
admitted as offline profiles after a failed session check.

## Scope

- Forge-specific and shared English localization now give actionable
  Minecraft/Microsoft launcher sign-out and sign-in guidance.
- Regression tests check both locale sources and preserve the clear offline
  fallback prohibition.
- The Forge regression workflow verifies the new version's packaged metadata
  and byte-for-byte rebuild reproducibility independently from the immutable
  already-published 1.3.1 JAR and digest.
- The historical release manifest `release/forge-fork.json` remains unchanged;
  the workflow compares it to the manifest stored at the `v1.3.1` tag.
- Version 1.3.2 resets all mod and plugin publication approvals, so builds
  remain unapproved until separate release testing and authorization.

This change does not bypass account verification, alter stored UUIDs or player
data, publish any artifact, or deploy to a live server. An expired launcher
session still has to be refreshed by the player.
