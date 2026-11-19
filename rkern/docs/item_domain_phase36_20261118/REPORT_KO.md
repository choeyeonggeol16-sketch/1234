# RKern 아이템 36차 — 콜로세움 바닥 보상 객체 누수 방지

`L1UltimateBattle`·`L1NewUltimateBattle`의 `spawnGroundItem`에서 바닥 인벤토리가 보상을 받지 못하거나 스택 병합하면 `ItemTable.createItem`이 사전 등록한 월드 객체가 남을 수 있었다. 생성·강화값·보상 수량·위치·중첩 여부·반복 순서를 바꾸지 않고, 신규 객체가 실제 바닥 인벤토리를 소유하지 않을 때만 월드 인덱스에서 정리하도록 했다.

`bash docs/item_domain_phase36_20261118/run_tests.sh`로 Java 21 생산 메서드 추출 원본/수정 비교를 수행한다. 기존 보상 결과와 실패 예외를 동등하게 유지하면서 고아 객체가 줄어드는지 확인한다. 서버 전체 컴파일 및 실DB·인게임 확인은 별도로 필요하다.
