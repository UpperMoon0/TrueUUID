# NsTut Forge 1.20.1 fork: 1.3.1

Based on YuWan-030/TrueUUID main `73fd511` (LGPL-3.0). Its Forge 1.20.1
controller is unchanged from upstream v1.3.0, matching the installed controller
whose generic cleanup sets `authFinalized=true` even after rejection.

Rejected/disconnected listeners now remain terminal. Cleanup cannot reopen the
acceptance gate, delayed hello/answer/verifier/migration callbacks cannot revive
the session, and migration timeout cancels the same tick. Login answers are
decoded before their Netty buffer is released and processed on the server thread
alongside vanilla acceptance. Disconnect cleanup and callback state effects are
serialized. Policy-approved offline fallback and premium verification retain
their existing policy and protocol.

An already-confirmed migration may finish its atomic file transaction after a
connection closes. Cancelling a dependent future cannot interrupt that IO; its
existing migration lock is released on actual worker completion rather than on
listener cleanup. A closed listener never receives the resulting premium profile.
The existing lock's bounded expiry policy is unchanged.

## Validation

`RejectedLoginRegressionTest` drives the production controller with mocked native
connection/server/services and immutable synthetic account data. The transport
deliberately remains connected after disconnect to exercise asynchronous close.
The listener-continuation assertion tests the boundary before player creation and
save; it is not a full client or filesystem-write integration test.

The CI workflow first runs the identical regressions against the unmodified
upstream controller and requires the specific assertion failures, then restores
the patch and runs the Forge and shared suites. It verifies the reobfuscated JAR
and repeats packaging to compare exact bytes. Local Windows shared-store tests
have OS filesystem limitations; Linux CI is authoritative for those cases.

Only Forge 1.20.1 is the intended fork artifact. Other loader releases remain
unapproved. No live server deployment, account migration or configuration change
is part of this PR. A future release must be built from the reviewed merged head,
published with its source and LGPL notices, and pinned by exact artifact metadata
in the TFG integration before that draft can become deployable.
