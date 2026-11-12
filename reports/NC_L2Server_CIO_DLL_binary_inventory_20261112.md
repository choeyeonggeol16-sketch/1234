# NC L2Server / RKern CIO DLL 바이너리 확인 목록 (2026-11-12)

> **상태:** 실제 바이너리의 로컬 조사·중복 제거·검증용 ZIP 생성 완료. **GitHub에는 이 목록만 업로드됨. EXE/DLL 바이너리는 업로드되지 않았음.** 공개 저장소 재배포에 관한 권리·라이선스도 검증되지 않음.

## 확인된 원본 파일 및 SHA-256

| 파일 | 바이트 | SHA-256 | 분류 |
|---|---:|---|---|
| `L2Server.exe` | 3,395,584 | `8eecd317fb152b76f32a0a6a65fbf916e39d20511e203b4a0ea602fe041a22f5` | NC L2Server |
| `L2Server(10).exe` | 13,215,232 | `9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588` | NC L2Server (별도 계열) |
| `CIOPacketCodec.dll` | 119,808 | `60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9` | RKern의 JNI/네이티브 DLL |
| `CIOWinTransport.dll` | 4,573,184 | `9d8106886be0e26d14bd8eb7093714ae3b44d655ccae869ffc98148db8807ecc` | RKern의 Windows transport DLL |

### L2Server 사본 중복

- 동일 SHA-256 그룹 A: `L2Server.exe`, `L2Server(1).exe` ~ `L2Server(6).exe` — 7개 파일이 한 바이너리.
- 동일 SHA-256 그룹 B: `L2Server(7).exe` ~ `L2Server(10).exe` — 4개 파일이 다른 한 바이너리.
- 즉, 총 11개 이름을 검사했으며 **실제 중복 없는 EXE는 2종**.

### 정확한 DLL 이름

- `PacketCodec.dll`과 비슷한 실제 발견물은 `CIOPacketCodec.dll`임.
- 요청한 **`WinSocket.dll`은 정확한 파일명으로 발견되지 않았음.** 대신 `CIOWinTransport.dll`이 있음.
- 최신 RKern 소스에는 `src/l1j/server/server/model/network/core/CIOWinSocket.java`도 있지만 이는 DLL이 아닌 Java 소스.
- 라이브러리의 `NCSOFT_sources.rar`, `L2Tools_1.4.rar`에서도 요청 명칭을 찾지 못함.

## 검증용 아카이브

모델 작업 환경에서 아래 4개 중복 없는 파일과 `MANIFEST.txt`를 하나의 ZIP으로 묶어 **ZIP CRC 및 각 엔트리 SHA-256 검증 완료**.

- ZIP 파일명: `RKern_NC_Server_and_CIO_DLL_Inventory_20261112.zip`
- ZIP 크기: **7,461,505바이트**
- ZIP SHA-256: `f226ea4ca7438f20b07b257924865c8d788d21a61f441e9140f4a1cb1cbfbfc1`
- ZIP 내부: `NC/L2Server.exe`, `NC/L2Server(10).exe`, `RKern/CIOPacketCodec.dll`, `RKern/CIOWinTransport.dll`, `MANIFEST.txt`.

**중요:** 위 ZIP은 채팅 첨부 산출물이며 **GitHub 저장소에 존재하지 않음**. GitHub 연결 도구의 쓰기 기능은 UTF-8 텍스트·Git blob에 입력한 명시적 문자열을 처리하지만, 작업 컨테이너의 바이너리 파일을 `source_file_ref` 형태로 직접 전달하는 기능이 없어 바이너리 GitHub 게시를 완료할 수 없었음. NC 상용 실행 파일의 공개 재배포 권한도 확인되지 않았으므로 게시 전 별도 확인이 필요함.
