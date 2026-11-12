# 실제 바이너리 근거와 새 구현의 구분

이 코어는 실행 파일의 원본 C++ 텍스트를 복구한 것이 아니다. 실제 명령/RTTI/vtable에서 확인한 계약을 해석하여 새 C++로 작성했다. 모든 부분이 NC와 동일하거나 본섭을 완전히 복제했다고 주장하지 않는다.

| 부분 | 실제 확인 근거 | 새 구현 |
|---|---|---|
| 객체 수명/가상 완료 | NC x64 retain0x7d6d50, socket completion0x45f028, worker dispatch0x7058d8; x86 completion dispatch0x53cbe3 | CIOObject retain/release와 owner virtual completed |
| 8KB 버퍼/송신 FIFO | NC x64 read0x45e138, Write0x45e480, WriteCallback0x45e8a0; x860x2000 공간 계산 | 8192-byte pool, 활성 head 보존과 대기 tail 합치기 |
| 최대128개 배치 | **기존 CIOWinTransport.dll** VA0x180049646에서 r8d=0x80,0x180049659에서 GetQueuedCompletionStatusEx | worker별 stack ENTRY[128]과 Ex 호출 |
| 배치 종료 신호 | 같은 DLL0x180049738~0x180049768에서 종료 개수 확인 후 남은 PostQueuedCompletionStatus 재전달 | 실완료를 모두 처리한 뒤 종료 신호1개 소비,나머지 재전달 |
| 개별 I/O 오류 | 같은 DLL은 ENTRY 상태를 보고 필요 시 WSAGetOverlappedResult | 새 코어는 ENTRY.Internal snapshot의 NTSTATUS를 RtlNtStatusToDosError로 변환. 현재 OVERLAPPED 상태를 다시 읽지 않음 |
| AcceptEx/연결 ID/상한/FFM | 기존 RKern 계약에 맞춘 설계 | 이 부분은 새 어댑터이며 NC 원본 그대로라는 주장을 하지 않음 |

NC 두 EXE에서 확인한 worker는 단일 GQCS다. **128개 배치가 NC EXE에 있었다는 뜻은 아니다.** 사용자 제공 기존 전송 DLL의 실제 배치 루프를 참고하여 새 코어에 확장한 것이다. 원본 명령 바이트를 새 DLL에 복사하지 않았다.

기존 전송 DLL SHA256: `9d8106886be0e26d14bd8eb7093714ae3b44d655ccae869ffc98148db8807ecc`. 선택된 실제 명령은 hp-batch-worker-excerpt.txt에 있다. NC EXE별 해시와 vtable은 nc-rtti.json 및 NC_IO_CORE_RECOVERED.md에 있다.

항목별 NTSTATUS 변환, 새 API adapter, 부분 송신 재제출, 제한값은 새 구현의 선택이다. Windows API fixture 기반 검사와 실제 Windows DLL/FFM 검증은 구별한다.
