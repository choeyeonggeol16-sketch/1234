**마법중첩·마법데미지 포함 최신 통합본:** [전체 src](../../downloads/RKern_Aden_IOCP_THREADPOOL_MAGIC_FULL_SRC_20261111.zip) · [구현·검증](../rkern_magic_20261111/README.md)

**후속 스레드풀 수정 포함 최신본:** [전체 src ZIP](../../downloads/RKern_Aden_IOCP_THREADPOOL_FIXED_FULL_SRC_20261111.zip) · [적용·검증 안내](../rkern_threadpool_20261111/README.md)

## Aden 네트워크 최신 통합 소스 — 2026-11-11

[**전체 src 통합본 다운로드**](../../downloads/RKern_Aden_IOCP_FIXED_FULL_SRC_20261111.zip) · [SHA256](../../downloads/RKern_Aden_IOCP_FIXED_FULL_SRC_20261111.zip.sha256) · [구성 기록](FULL_SOURCE_MANIFEST.json)

원본 aden.zip의 전체 src에 N01–N07 수정과 후속 두 가지 수정을 모두 반영했습니다. 소스 파일 1,695개(Java 1,694개) 중 최신 수정 11개를 합친 통합본입니다. 기존 패치를 차례로 적용할 필요 없이 이 ZIP의 전체 src를 사용하면 됩니다. 기존 커스텀 수정이 있으면 백업 후 비교해 반영하세요.

최신 수정에는 kick/close/disconnect의 transport 종료 장벽 통일, Java 연결 종료부터 적용하는 전체 시간 제한이 포함됩니다. 기존 native DLL 두 개·설정·의존 라이브러리·DB는 원래 구성으로 사용합니다. ZIP에는 src 폴더만 들어 있습니다. 소스 인코딩은 각 원본 파일 그대로 유지했습니다.

Java 21 FFM preview 실행 구성을 유지하세요. 실제 Java 클래스 기반 회귀 검사는 통과했으며 전체 서버 의존성 연결 빌드와 Windows 실게임 검증은 아직 하지 않았습니다.

나중에 찾을 파일 이름: `RKern_Aden_IOCP_FIXED_FULL_SRC_20261111.zip`

---

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
