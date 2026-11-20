# OSRS PvP 강화학습 엔진 분석 및 RKern 자율 플레이어 AI 이식 설계

- 작성일: 2026-11-21 KST
- 조사 대상: [Naton1/osrs-pvp-reinforcement-learning](https://github.com/Naton1/osrs-pvp-reinforcement-learning), 분석 리비전 `6914425d018ae36ef8cbaebaf1a3ba06dcc89921`
- RKern 확인 기준: `2aefad5dc7e570bfb05cbdc8bd5cdc2d7f908632`, 브랜치 `rkern/unified-jproring-game-20261120`
- 범위: 실제 공개 소스의 정적 분석 및 향후 구현 요구사항. 이번 커밋은 보고서만 추가한다.
- 목표: 단순 자동사냥이 아닌 학습·판단·개인 장기 기억·공유 전술·파티 전투·영구 복원 가능한 인공 플레이어.

## 1. 결론 및 원본의 범위

원본은 Java 17 Elvarg RSPS의 실제 전투 엔진을 개조한 시뮬레이션과 Python/PyTorch PPO 학습 및 추론 API를 분리한다. 학습 모델과 다수의 과거 체크포인트도 공개한다. README의 실제 게임 최종 모델 약 99% 승률은 개발자 자신의 평가이며 독립 재현을 하지 않았다. 실게임 외부 클라이언트 플러그인은 공개되지 않았고 RKern 이식 대상도 아니다.

분석한 소스에서는 실제 역전파와 optimizer 업데이트, 행동 제약, 과거 모델 상대 선택, 체크포인트 save/load가 확인된다. 반면 MMORPG 전역 자율 사냥, 파티 다중 에이전트 학습, 캐릭터별 장기 대인 기억, 운영 서버에서 무중단 온라인 학습은 원본에서 완성된 것으로 간주하지 않는다. 필요한 확장 항목이다.

## 2. Java 전투 환경과 실행 순서

[AgentAdapter.java](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/simulation-rsps/ElvargServer/src/main/java/com/github/naton1/rl/AgentAdapter.java#L65-L197):

1. processPlayer: onTickStart → checkAttacker → checkFightComplete → processAction.
2. 첫 전투 시작 시 EnvironmentDescriptor로 전투별 AgentEnvironment 생성 및 frame history 초기화.
3. 이전 비동기 추론 응답이 완료됐으면 환경 processAction으로 적용. 미완료 응답은 취소 후 해당 tick을 놓쳤다고 기록.
4. onFlush에서 onTickProcessed → 예약된 requestNewAction → onTickEnd.
5. requestNewAction: getObs, frame stack, getActionMasks, model 이름, deterministic 설정을 묶어 PvpClient로 전달.
6. onHitCalculated / onHitApplied 이벤트로 피격 관측과 공격 유형 통계를 반영.
7. 전투 종료 조건(사망, 월드 이탈, 전투 구역 이탈) 충족 시 환경·프레임 및 진행 중 추론 초기화. 로그아웃 시 executor 종료.

[RemoteEnvironmentServer.java](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/simulation-rsps/ElvargServer/src/main/java/com/github/naton1/rl/RemoteEnvironmentServer.java#L38-L117)는 학습용 login/logout/reset/step/debug 소켓 API를 열고 메시지를 해당 봇의 실행 경로에 전달한다. 실제 운영 Java 서버에 원본의 봇별 executor, LinkedList, boxed Number, JSON RPC, 외부 추론 대기 구조를 그대로 복제하지 않는다.

## 3. 관측 계약과 행동 선택

[NhEnv.json](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/contracts/environments/NhEnv.json)은 176개 관측값과 12개 행동 그룹의 순서 및 의존성을 정의한다. [NhEnvironment.java](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/simulation-rsps/ElvargServer/src/main/java/com/github/naton1/rl/env/nh/NhEnvironment.java#L169-L523)의 실제 구현과 연결된다.

관측: 자기 HP·자원·장비·공격 재사용 시간·빙결/면역·공격 거리·현재 이동·예정 피격, 상대 마지막 관측 HP·공격 유형·방어 선택·장비 통계, 최근 및 누적 공격/방어 경향 등. 일부 입력은 서버 내부 정보를 사용하므로 원본 전체가 인간과 완전히 동일한 관측이라고 주장하지 않는다. RKern은 공정한 관측 범위를 별도 정의한다.

| 그룹 | 선택지 수 |
|---|---:|
| attack | 4 |
| melee_attack_type | 3 |
| ranged_attack_type | 3 |
| mage_attack_type | 4 |
| potion | 5 |
| food | 2 |
| karambwan | 2 |
| veng | 2 |
| gear | 2 |
| movement | 5 |
| farcast_distance | 7 |
| prayer | 6 |

processAction은 길이 12를 검사한 후 overheadPrayer → primaryFood → potion → karambwan → gear → vengeance → attack → movement 순으로 기존 게임 기능 호출.

getActionMasks는 사거리·이동 가능 여부·물약/스킬 준비 상태와 같은 제약을 반영한다. 모델의 [policy.py](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/ppo/policy.py#L87-L182)는 환경 마스크와 이전 행동에서 도출한 dependency mask를 AND하고, 유효한 행동에 대해 softmax 후 argmax 또는 sampling을 수행한다. 선택 불가능 시 action 0 fallback을 사용하지만 RKern에서는 모든 행동 그룹에 명시적인 안전한 no-op을 정의하고 실행 직전 재검증해야 한다.

## 4. PPO와 기억의 실제 구조

[core.yml](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/config/nh/core.yml) 기준:

- 관측 176개, stack-frames=1(기본 설정은 프레임 한 개이며 다중 프레임은 옵션).
- actor MLP 512→512, 행동 head마다 128→128, 12개 그룹 총 45개 logits.
- critic MLP 512→512, 기본 학습에서는 상대 관측값도 critic에 포함.
- Autoregressive action heads: 앞서 선택한 행동의 one-hot을 후속 head에 입력해 연계 행동을 판단.
- 100개 환경, 환경당 1024 rollout steps, batch 2048, gamma 0.995, epochs 1, 체크포인트 주기 1.
- 승리 +1, 패배 -1, 피해량 보상 스케일 +0.25, 피격 스케일 -0.25, 상대 빙결 tick 보상 +0.004→+0.003, 자기 빙결 -0.004→-0.003, 올바른/잘못된 방어 보상 ±0.025. 이는 원본 설정값이지 리니지1의 확정 보상값이 아니다.

[PPO 실제 학습](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/ppo/ppo.py#L206-L311): 관측 정규화 → 기존/새 action log-prob 비율 → clipped surrogate → entropy/value loss 합산 → loss.backward → gradient clipping → optimizer.step. 단순한 TD 오차 계산만 하고 학습을 하지 않는 가짜 구현과 구별된다.

[rollout buffer](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/ppo/buffer.py): 관측, action, action mask, log prob, value, reward, episode start, truncate, advantage, return을 배열로 축적한다. PPO는 on-policy이므로 오래된 임의의 경험을 최신 rollout에 무조건 섞어 학습하지 않는다. 영구 보관한 경험은 평가, 분석, 적절한 재학습/모방학습 등에 구분하여 사용한다.

[save_buffer_callback.py](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/callback/save_buffer_callback.py)는 선택적으로 최근 raw rollout을 압축 pickle로 저장한다. 모든 전투를 영구 저장하는 개인 기억 DB라는 의미는 아니다.

[PPO save/load](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/ppo/ppo.py#L486-L543): policy state_dict, optimizer state_dict, policy params, meta, extension state를 저장하고 복원한다. 배포용 모델과 optimizer를 포함한 학습 재개용 체크포인트를 구분할 수 있다.

원본의 핵심은 feed-forward MLP, 명시적 전투 통계 및 선택적 frame stack이다. GRU/LSTM을 사용 중이라고 주장하지 않는다. GRU는 RKern의 향후 장기 전투 문맥 확장을 위한 후보이다.

## 5. Self-Play League

[league.py](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/util/league.py#L12-L49), [past_self_play_callback.py](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/pvp_ml/callback/past_self_play_callback.py):

- 과거 모델들을 상대 후보군으로 등록.
- 신규 상대의 quality는 기존 최고값(없으면 1).
- quality의 softmax를 이용해 대결 상대를 샘플링.
- 승리 시 해당 상대 quality에서 learning_rate/(상대 수 × 선택확률)을 차감.
- 어려운 상대를 상대적으로 자주 만나게 하는 prioritized self-play.
- [human-like.yml](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/config/nh/human-like.yml): self-play 10%, latest self-play 10%, past self-play 80% 설정.
- [past-self-play-with-adversary.yml](https://github.com/Naton1/osrs-pvp-reinforcement-learning/blob/6914425d018ae36ef8cbaebaf1a3ba06dcc89921/pvp-ml/config/nh/past-self-play-with-adversary.yml): main exploiter 2개, league exploiter 1개 학습 설정. 주 모델의 약점을 공략하는 상대를 별도 학습.

RKern은 기존 최강 모델만 계속 상대하는 단순 self-play가 아니라 과거 모델, 규칙 기반 기준선, 상성별 강한 상대를 유지해 망각과 전술 퇴화를 줄인다. 신규 정책은 독립 평가를 통과할 때만 승격한다.

## 6. RKern 구현 요구사항

다음은 원본 사실이 아닌 RKern 설계 제안이다. 코드를 작성하기 전 최신 소스의 실제 소유권, 전투·스킬·이동 호출 및 DB 계약을 점검한다.

### R01. 실제 게임 엔진 기반 학습 환경

- 기존 PC 공격, 마법, 아이템 사용, 이동, HP/MP, 사망, 2D 8방향 타일 및 점유 규칙 재사용.
- 클라이언트 없이 독립 훈련 환경에서 reset/step/observation/action mask/reward/done 제공.
- 기존 게임 시간·공격 딜레이·스킬 쿨다운을 정확히 반영. 훈련용 가속은 게임 규칙을 바꾸지 않음.
- 운영 캐릭터, CacheD, 재화·아이템·DB와 훈련 데이터 격리. 실시간 운영 게임 서버에 무리한 학습 부하를 넣지 않음.

### R02. 정책 행동 및 공정한 관측

- 자기 스탯, HP/MP, 시야 내 대상, 거리, 점유, 아군 HP, 최근 공격/피격, 스킬 상태, 자원, 관측된 상대 행동.
- 상대 비공개 HP/MP·좌표·장비/쿨다운처럼 플레이어가 알 수 없는 정보는 운영 actor에 직접 주입하지 말고 관측 가능한 증거로 추정. 학습 critic의 전역 상태와 운영 actor 입력 구분.
- 타겟 선택, 일반 공격, 스킬, 아이템, 8방향 이동, 대기, 후퇴, 회복, 파티 지원의 action 계약 정의.
- 유효성 마스크 및 실행 직전 기존 정책 검증. 스킬/속도/시야/점유 규칙 우회 금지.

### R03. 저비용 Java 추론

- 기존 CIO 소유 실행 구조와 타이밍 휠에 결합. 운영 경로에 새 봇별 thread/executor, 중복 queue, 외부 Python 소켓 호출을 추가하지 않는다.
- 직업 또는 역할별 immutable 모델 가중치 공유. 캐릭터별 primitive 관측·행동 배열과 필요한 개별 기억 상태만 유지.
- 순전파는 재사용 scratch와 flat float 배열을 우선 검토. 실제 측정 전 off-heap/Vector API/양자화의 우위를 단정하지 않는다.
- 학습 서버는 Python/PyTorch 원본을 참고하고, 검증된 정책만 운영용 Java 추론 포맷으로 내보낸다.
- 모델 교체 시 스키마·크기·유효값·해시 검증, 세대 단위 안전한 공개, 이전 모델 롤백. Python 참조 출력과 Java 출력의 수치 및 행동 마스크 동등성 검증.

### R04. 개인 기억과 공유 지식 (필수)

| 데이터 | 소유 단위 | 저장 / 복원 |
|---|---|---|
| 단기 전투 상황 | 캐릭터 | bounded primitive ring 또는 GRU hidden state |
| 상대·사냥터·전투 결과 | 캐릭터 ID | bounded 영구 기억 레코드; TTL/중요도 정책 |
| 파티 구성·협동 결과 | 파티 유형 | 공유 통계 및 전략 기록 |
| 훈련 관측/행동/보상/종료 | 모델 세대·episode | 별도 bounded rollout 및 디스크 archive |
| 학습 모델·정규화·optimizer | 직업/역할·모델 버전 | immutable checkpoint |
| 과거 모델 상대군 | league | 정책 hash, 승패, quality, 버전, 시드 |

운영 판단 tick에서 DB 조회 및 파일 I/O를 하지 않는다. RAM에서 읽고 기존 비동기 저장 경로 또는 별도 훈련 프로세스에서 batch flush한다. 운영 저장 경로는 CacheD 등 실제 계약을 먼저 확인한다. 바이너리 고정 레코드, memory-mapped store, 기존 DB 중 적합한 방법을 실제 요구사항으로 선택한다. Java 21 FFM은 preview 및 수명 관리 비용을 고려하며 필수 의존성으로 강제하지 않는다.

디스크에는 magic/schema version, 레코드 길이, ID/세대, checksum, 갱신 시각을 둔다. 메모리 상한과 보존 개수, stale data eviction, 재접속·재시작 복원, 손상 복구를 명시한다. 스냅샷은 임시 파일 기록·동기화·원자적 교체 등 파일시스템 조건을 고려한 내구성 정책으로 저장한다. 장기 기억은 모델 가중치나 GRU 상태 하나로 대체할 수 없다.

### R05. 자율 사냥 및 파티 학습

- 1대1, 사냥, 다수 몬스터, 파티 PvE, 파티 PvP의 환경을 단계적으로 구축.
- 우선 공개 OSRS PPO 구조로 1대1 및 개인 전투 학습을 검증.
- 파티는 [MARL-BattleGrounds](https://github.com/arm-2-lab/MARL-BattleGrounds)의 recurrent MAPPO 및 QMIX를 참고. 팀 보상, 역할별 policy, 중앙 critic/분산 actor, 팀 목표와 개인 보호의 trade-off 설계.
- 목표 행동: 집중 공격, 위험 아군 회복, 타겟 분배, 길목 차단, 카이팅, 파티 진형, 후퇴, 자원 절약. 단순 규칙 나열만으로 완료 처리 금지.
- 개인 장기 기억을 실제 입력으로 사용하고 새 경험을 저장하여 같은 대상/사냥터 재방문 시 의사결정에 영향을 주는지 검증.
- 현재 모델과 과거 모델 및 기준선의 self-play에서 훈련. 라이브 데이터는 권한과 관측 계약에 맞는 범위에서 별도 평가/학습에 활용.

### R06. 검증 및 성능 기준

- 기존 전투 엔진과 동일한 action 결과, 불법 행동 0, 공격/이동/스킬/아이템 쿨다운·점유·사망 회귀.
- 관측 누출, 학습/운영 정규화 불일치, 마스크 fallback, 버전 호환성, NaN/Inf, 잘못된 모델/파일, 재시작 복원, 저장 실패 복구 검증.
- 여러 seed와 holdout 조건, 고정 상대군 및 기존 규칙 AI 대결. 승률·생존·사냥 효율·물약/MP 소비·파티원 보호/사망을 함께 측정.
- 학습 전후 비교와 개인 기억 활성/비활성 A/B. 저장 후 재시작 결과의 동등성 확인.
- 운영 Java에서 실제 로봇 100/1000/5000 단계별 활성 수, 정책 호출 빈도, CPU/decision, p99 tick, allocation, retained heap, GC, 디스크 I/O 측정. 전체 서버 성능 목표를 실측 없이 보장하지 않는다.
- 경량 정책과 원본 규모 MLP 비교. 필요 시 모델 크기/결정 빈도 축소, 실제 게임 행동 경계에서만 재판단. 기존 네트워크 및 다른 게임 로직은 범위 밖.

## 7. 우선순위와 금지 사항

먼저 훈련 환경 및 계약(R01/R02) → PPO/league/체크포인트 재현 → Java 추론(R03) → 개인/공유 영구 기억(R04) → 파티 확장(R05) → 반복 검증(R06). 개인 기억을 이후 삭제할 임시 기능으로 취급하지 않는다.

원본 코드를 그대로 복제하기 전에 저장소 및 의존 코드의 라이선스와 배포 권한을 확인할 것. 가중치를 직접 리니지1에 재사용할 수 있다고 가정하지 말 것: 관측·행동·보상 및 전투 규칙이 달라 새 학습이 필요하다. 현 단계에서 네트워크, PSS, DB 거래 확정성, NPC 전투 경로를 임의 변경하지 말 것. 이번 보고서는 구현 완료 보고가 아니다.
