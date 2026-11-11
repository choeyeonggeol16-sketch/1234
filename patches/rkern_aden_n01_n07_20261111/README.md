## N01/N02 follow-up fix

- Public kick/close/disconnect now request transport shutdown. The terminal callback starts logout exactly once after pending packets finish, independently of the disconnect-request CAS.
- One shutdown coordinator closes connections and native transport with a shared deadline. Callers have independent timeouts even if a connection/native call blocks. Expired attempts retain native resources and stop before the next phase; explicit retry after completion is supported.
- Apply all three files together: CIOClient.java, CIOSocketFactory.java, CIOServer.java.
- [Three-file src-only ZIP](../../downloads/RKern_Aden_N01_N02_FOLLOWUP_SRC_20261111.zip) requires the earlier N01-N07 source baseline.
- Regression tests passed against actual client/factory/server classes with fixture native/game dependencies. Run `python3 tests/test_lifecycle.py` and `python3 tests/test_shutdown.py` on JDK 21. Logs are in `logs/`.
- Windows IOCP/live game and full server dependency linking remain unverified. DLLs are unchanged.

---

# RKern / Aden IOCP network fixes (2026-11-11)

This branch contains only the **11 modified Java files** relative to `RKern_AllWarehouses_AtomicMultiWithdrawal_FULL_SRC_20261109.zip`, plus a unified patch and checksums. Paths under `src/` match the original source layout. The two supplied native DLLs are **not modified** and are not included here.

## Scope

- N01: packet execution and logout lifecycle synchronization
- N02: Java-side shutdown/timeout protections around native Stop/Wait
- N03: RECV stash data preservation
- N04: CIO ingress capacity setting
- N05: connection table tombstone/churn handling
- N06: ingress and shutdown race handling
- N07: known supplied codec DLL version and hash checks

## Validation caveat

This is **not** a Windows IOCP integration or 5,000-user soak-tested release. Native DLL internal Stop behavior is not changed. ABI checks cover the provided DLL version/binary identity, not full dynamic ABI compatibility. Review and test in a staging environment before deploying to production.

## Applying

Use the full source baseline named above, then replace the eleven `src/...` files in this directory or apply `RKern_Aden_N01_N07_20261111.patch`. Do not apply these changes to the unrelated Netty pack.
