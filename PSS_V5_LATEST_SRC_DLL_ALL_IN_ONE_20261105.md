# 최신 src 기준 PSS V5 통합본

[최신 서버 전체 src + DLL 한 번에 받기](downloads/PSS_V5_Latest_SRC_DLL_ALL_IN_ONE_20261105.zip)

이번에 업로드한 src.zip(소스 1,688개)을 원본으로 사용했다. 원본 파일 1,689개 중 1,685개는 바이트 그대로 유지했고 Java 파일 4개만 수정했다. 이전 팩에서 삭제된 클래스나 제거한 일반 로그를 다시 넣지 않았다. 아이템 직접 창 연결이 이미 들어 있는 User.java와 ItemUseExtensions.java도 그대로 유지했다.

수정한 파일은 ItemTable.java, CAttackAction.java, PlaySupportWindow.java, PssWindowTransport.java다. PSS 아이템 즉시 사용 형식, 실제 PSS 세션의 원거리 시야 검사, 타겟을 유지하는 접근 알림과 v1/v2 동반 연결을 합쳤다. DLL은 앞서 검증한 V5와 동일하다. 구역 배회와 실행 중 문구/회전 표시를 포함하며 ON 버튼은 없다.

압축 루트의 src 폴더는 최신 서버 전체 소스다. 기존 프로젝트 src를 백업한 뒤 교체하고 기존 build.xml/lib로 clean build 후 서버를 재시작한다. src/META-INF/MANIFEST.MF도 포함했다. Pss2019.dll은 게임을 완전히 종료하고 기존 파일을 백업한 뒤 실제 게임 실행파일 옆에 복사한다. 기존 Pss2019.ini는 유지하며, 이미 자동 로딩을 적용했으면 Apply를 다시 실행하지 않는다.

서버와 DLL을 함께 적용해야 활 시야 알림과 아이템 사용 형식 변경이 반영된다. 새 DB 테이블은 필요 없다. ItemTable이 아이템 900000901의 사용 형식을 30으로 적용한다. DB도 맞추려면 동봉한 sql/PSS_V5_Immediate_Settings_Item.sql을 선택적으로 실행할 수 있다.

표시 위치는 기존 INI의 [PssIndicator] OffsetX/OffsetY로 조정한다. 기본값은 0/20이며 Enabled=0으로 표시만 끌 수 있다. [PssRoaming] CommandIntervalMs=0, StrideTiles=3을 사용한다. 실행 중 표시는 DLL이 만든 창모드용 표시로 원본 리마스터 UI 이식본은 아니다. 실제 Windows 표시·위치·폰트와 사냥터 동작은 아직 확인이 필요하다. 서버 시야 알림 전에 최초 클라이언트 활 모션이 보일 수 있으며 서버는 막힌 공격을 명중/효과 처리 전에 거절한다.

이번 최신본의 전체 Java 1,688개 컴파일, NIO2 전송 검사와 아이템/시야 알림 경계 검사를 통과했다. 최신본의 일일 시간/DB 정산 코드는 바이트 그대로 유지했으며 실제 DB 통합 시험은 수행하지 않았다. 결과는 validation에 있다. DLL은 이전 V5의 실제 32비트 어댑터/전송/로드 검사를 통과한 동일 파일이며 체크섬으로 확인했다. client-source에는 해당 DLL 소스를 넣었다. changes.patch와 source_manifest.json으로 업로드 원본 대비 변경 범위를 확인할 수 있다.
