# 업로드에서 가져온 배회 코어

`mjlin_배회기능_이식준비자료.zip`의 C++ 소스와 헤더·테스트·빌드 스크립트를 변경 없이 복사했다. 파일별 SHA256은 PROVENANCE.json에 있다.

이 코어의 `sr_create/sr_tick/sr_reset/sr_destroy`와 BFS 배회 로직은 존재한다. 실제 게임의 통행 판정 `can_step`, 이동 `request_step`, 상태/시간/좌표 변환을 제공하는 호스트 어댑터는 아직 연결되지 않았다.

현재 상위 Pss2019.dll 빌드는 이 코어를 링크하지 않는다. `build-roam.cmd`는 원래 제공된 독립 코어 빌드용이며 상위 PSS 통합 빌드가 아니다. 원본 NC 배회 함수와의 대응 관계는 아직 검증되지 않았다.
