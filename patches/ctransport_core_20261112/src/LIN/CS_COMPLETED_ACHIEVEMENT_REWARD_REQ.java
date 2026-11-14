package LIN;

import LIN.CompletedAchievement;
import LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSRewardsLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterCriteriaProgress;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;



/**
 * CS_COMPLETED_ACHIEVEMENT_REWARD_REQ
 *
 * 도감 업적 보상 수령 요청
 *
 * wire:
 * [1] achievement_id uint32
 * [2] optional_reward_indexes repeated uint32
 *
 * 중요:
 * 클라이언트가 보내온 achievement_id만 믿고 보상 단계를 결정하지 않는다.
 *
 * 실제 현재 단계는:
 *
 *     BQSCharacterCriteriaProgress.current_achievement_level
 *
 * 을 기준으로 판단한다.
 *
 * currentLevel:
 * 0 = 1단계
 * 1 = 2단계
 * 2 = 3단계
 *
 * 실제 수령 가능한 achievement id:
 *
 *     bqsInfo.getAchievementId() + currentLevel
 *
 */
public final class CS_COMPLETED_ACHIEVEMENT_REWARD_REQ {

    private CS_COMPLETED_ACHIEVEMENT_REWARD_REQ() {
    }

    public static void handle(CIOClient client, ProtoReader r) {

        // =========================================================
        // 패킷 파싱
        // =========================================================

        int achievementId = 0;
        boolean hasAchievementId = false;

        List<Integer> optionalRewardIndexes = null;

        try {

            while (!r.isAtEnd()) {

                final int tag = r.tag();

                switch (tag) {

                // field 1 : achievement_id
                case (1 << 3) | 0:
                    achievementId = r.varint32();
                    hasAchievementId = true;
                    break;

                // field 2 : repeated uint32 - unpacked
                case (2 << 3) | 0:
                    if (optionalRewardIndexes == null) {
                        optionalRewardIndexes = new ArrayList<>();
                    }

                    optionalRewardIndexes.add(r.varint32());
                    break;

                // field 2 : repeated uint32 - packed
                case (2 << 3) | 2: {

                    ProtoReader sub = r.subReader();

                    while (!sub.isAtEnd()) {

                        if (optionalRewardIndexes == null) {
                            optionalRewardIndexes = new ArrayList<>();
                        }

                        optionalRewardIndexes.add(
                                sub.varint32()
                        );
                    }

                    break;
                }

                default:
                    r.skip(tag & 7);
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }


        // =========================================================
        // required 검사
        // =========================================================

        if (!hasAchievementId) {
            return;
        }


        // =========================================================
        // 캐릭터
        // =========================================================

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null) {
            return;
        }


        // =========================================================
        // 보상 처리
        // =========================================================

        try {

            // -----------------------------------------------------
            // 도감 시스템 정지 상태
            // -----------------------------------------------------

            if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL_ALREADY_GET_REWARD
                );

                pc.sendPackets(
                        "현재 도감 시스템이 일시 중지 상태입니다."
                );

                return;
            }


            // -----------------------------------------------------
            // 요청 achievement id -> criteria id
            //
            // 여기서는 어느 몬스터 도감인지 찾는 용도로만 사용한다.
            // 실제 보상 단계는 뒤에서 currentLevel로 결정한다.
            // -----------------------------------------------------

            final int criteriaId =
                    BQSLoadManager.achievementIdToCriteriaId(
                            achievementId
                    );


            // -----------------------------------------------------
            // 캐릭터 도감 데이터
            // -----------------------------------------------------

            final BQSCharacterData bqs = pc.getBqs();

            if (bqs == null) {
                return;
            }


            // -----------------------------------------------------
            // 해당 도감 진행 데이터
            // -----------------------------------------------------

            final BQSCharacterCriteriaProgress progress =
                    bqs.get_progress(criteriaId);

            final BQSInformation bqsInfo =
                    BQSInformationLoader
                            .getInstance()
                            .getInformation(criteriaId);


            if (progress == null || bqsInfo == null) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL
                );

                System.out.println(
                        String.format(
                                "[도감] 할당되지 않은 도감에 대해 보상을 시도했습니다. "
                                + "캐릭터이름 : %s, 보상아이디 : %d, 북아이디 : %d",
                                pc.getName(),
                                achievementId,
                                criteriaId
                        )
                );

                return;
            }


            // =====================================================
            // ★ 실제 서버 현재 단계
            //
            // 0 = 1단계
            // 1 = 2단계
            // 2 = 3단계
            // =====================================================

            final int currentLevel =
                    progress.get_current_ahcievement_level();


            if (currentLevel < 0 || currentLevel >= 3) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL
                );

                System.out.println(
                        String.format(
                                "[도감] 유효하지 않은 현재 단계입니다. "
                                + "캐릭터이름 : %s, 북아이디 : %d, currentLevel : %d",
                                pc.getName(),
                                criteriaId,
                                currentLevel
                        )
                );

                return;
            }


            // =====================================================
            // ★ 현재 단계에서 서버가 기대하는 achievement id
            //
            // 예:
            //
            // criteria 677
            // base = 2060
            //
            // currentLevel 0 -> 2060
            // currentLevel 1 -> 2061
            // currentLevel 2 -> 2062
            // =====================================================

            final int expectedAchievementId =
                    bqsInfo.getAchievementId()
                    + currentLevel;


            // =====================================================
            // ★ 핵심 검증
            //
            // 클라이언트가 요청한 achievement id가
            // 현재 서버 단계의 achievement id와 다르면
            // 절대로 단계 진행시키지 않는다.
            // =====================================================

            if (achievementId != expectedAchievementId) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL_NOT_COMPLETED
                );

                System.out.println(
                        String.format(
                                "[도감] 현재 단계와 다른 보상을 요청했습니다. "
                                + "캐릭터이름 : %s, 북아이디 : %d, "
                                + "요청ID : %d, 서버기대ID : %d, currentLevel : %d",
                                pc.getName(),
                                criteriaId,
                                achievementId,
                                expectedAchievementId,
                                currentLevel
                        )
                );

                return;
            }


            // =====================================================
            // 완료 업적 확인
            // =====================================================

            final LIN.CompletedAchievement achievement =
                    bqs.get_achievement(
                            expectedAchievementId
                    );


            if (achievement == null) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL_NOT_COMPLETED
                );

                System.out.println(
                        String.format(
                                "[도감] 완료되지 않은 보상을 요청했습니다. "
                                + "캐릭터이름 : %s, 보상아이디 : %d, 북아이디 : %d",
                                pc.getName(),
                                achievementId,
                                criteriaId
                        )
                );

                return;
            }


            // =====================================================
            // 이미 받은 보상
            // =====================================================

            if (achievement.get_reward()) {

                LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                        pc,
                        achievementId,
                        LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL_ALREADY_GET_REWARD
                );

                System.out.println(
                        String.format(
                                "[도감] 이미 받은 보상에 대해 재 보상을 시도했습니다. "
                                + "캐릭터이름 : %s, 보상아이디 : %d, 북아이디 : %d",
                                pc.getName(),
                                achievementId,
                                criteriaId
                        )
                );

                return;
            }


            // =====================================================
            // ★ 실제 보상 단계
            //
            // currentLevel은 0-base
            // rewardIndex는 1-base
            //
            // currentLevel 0 -> rewardIndex 1
            // currentLevel 1 -> rewardIndex 2
            // currentLevel 2 -> rewardIndex 3
            // =====================================================

            final int rewardIndex =
                    currentLevel + 1;


            // =====================================================
            // 보상 처리
            // =====================================================

            achievement.set_get_reward(true);


            // -----------------------------------------------------
            // 도감 단계별 부가 효과
            // -----------------------------------------------------

            applyMonsterBookBuff(
                    pc,
                    rewardIndex
            );


            // -----------------------------------------------------
            // 실제 도감 보상
            // -----------------------------------------------------

            BQSRewardsLoader
                    .getInstance()
                    .doReward(
                            pc,
                            rewardIndex
                    );


            // =====================================================
            // ★ 보상 처리가 끝난 뒤 다음 단계로 이동
            // =====================================================

            progress.increase_current_achievement_level();


            // =====================================================
            // 성공 ACK
            // =====================================================

            LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                    pc,
                    achievementId,
                    LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_SUCCESS
            );


            // =====================================================
            // DB 저장
            // =====================================================

            BQSCharacterDataLoader.storeCharacterBqs(
                    bqs,
                    false
            );


        } catch (Exception e) {

            System.out.println(
                    String.format(
                            "[도감] 몬스터북 보상 예외 %s",
                            client.getIp()
                    )
            );

            e.printStackTrace();

            LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.send(
                    pc,
                    achievementId,
                    LIN.SC_COMPLETED_ACHIEVEMENT_REWARD_ACK.eResultCode.REQUEST_REWARD_FAIL
            );
        }
    }


    /**
     * 도감 1단계 보상 시 천하장사 버프 부여.
     */
    private static void applyMonsterBookBuff(
            L1PcInstance pc,
            int rewardIndex) {

        try {

            if (rewardIndex != 1) {
                return;
            }

            final int buffId =
                    L1SkillId.천하장사버프;

            final int durationSeconds =
                    21600; // 6시간


            if (pc.hasSkillEffect(buffId)) {
                pc.removeSkillEffect(buffId);
            }


            SC_ServerMessage.send(pc, 1426);


            L1Cooking.eatCooking(
                    pc,
                    buffId,
                    durationSeconds
            );


            pc.setDessertId(
                    buffId
            );


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}