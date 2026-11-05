# 업로드에서 가져온 배회 코어

`mjlin_배회기능_이식준비자료.zip`의 `RoamCore.cpp`와 `RoamTests.cpp`는 원본 그대로다. CPP SHA256은 `51d2ead64f5f9ade8fe48df50c9a1f3d88f014849616015ccc8f9ec794579d8c`이다. 원본 헤더는 `RoamCore.original.h`에 보존했다.

상위 DLL이 C로 작성되어 `RoamCore.h`에 C 호환 typedef와 extern 선언을 추가했다. 구조체 필드·순서와 함수 호출 규약은 유지한다. 비 Windows 테스트용 `__cdecl` 정의와 조건부 export 선언도 추가했다. 배회 알고리즘 CPP는 변경하지 않았다.

상위 `build.cmd`는 코어를 C++로 컴파일하고 `Pss2019.dll`에 정적 링크한다. `PssRoaming.c`가 `sr_create/sr_tick/sr_destroy`를 실제 호출하며 `PssNavigation.c`의 경로 간선 캐시와 네이티브 이동 함수를 호스트로 제공한다. 독립 `RoamCore32.dll`은 필요 없다. 여기의 `build-roam.cmd`는 원래 제공된 독립 빌드용이다.

제공 자료만으로 이 코어가 NC 원본 배회 본체라는 출처는 확립되지 않았다. 이를 현재 클라이언트에 연결한 테스트 빌드다.
