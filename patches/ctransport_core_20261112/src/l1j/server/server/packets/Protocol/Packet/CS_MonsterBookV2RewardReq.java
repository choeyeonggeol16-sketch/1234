package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQRewardsLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Packet.SC_MonsterBookV2RewardAck.eResultCode;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.MonsterBookV2Info;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.eMonsterBookV2DeckState;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.eMonsterBookV2RewardGrade;

/**
 * CS_MONSTER_BOOK_V2_REWARD_REQ (0x032B) 
 * 와이어: deck_index(field 1, varint), grade(field 2, enum)
 */
public final class CS_MonsterBookV2RewardReq {
    private CS_MonsterBookV2RewardReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        int deckIndex = 0;
        eMonsterBookV2RewardGrade grade = null;
        boolean hasDeck = false, hasGrade = false;

        try {
            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x08: deckIndex = r.varint32(); hasDeck = true; break;
                case 0x10: grade = eMonsterBookV2RewardGrade.fromInt(r.varint32()); hasGrade = true; break;
                default: r.skip(tag & 7); break;
                }
            }

            // ---------- pc 확보 ----------
            final L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            // ---------- 필수 필드 검증 ----------
            if (!hasDeck || !hasGrade) return;

            if (grade == null) {
                SC_MonsterBookV2RewardAck.send(pc, deckIndex, null, eResultCode.REWARD_FAIL);
                return;
            }

            // ---------- 비즈니스 로직 ----------
            if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
                SC_MonsterBookV2RewardAck.send(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
                pc.sendPackets("현재 도감 시스템이 일시 중지 상태입니다.");
                return;
            }

            final BQSCharacterData bqs = pc.getBqs();
            if (bqs == null) return;

            final MonsterBookV2Info.DeckT deck = bqs.get_deck(deckIndex);
            if (deck == null) {
                SC_MonsterBookV2RewardAck.send(pc, deckIndex, grade, eResultCode.REWARD_FAIL_INVALID_INDEX);
                System.out.println(String.format("[도감] 할당되지 않은 주간퀘스트 보상을 시도했습니다. 캐릭터이름 : %s, 보상라인 : %d, 보상종류 : %s",
                        pc.getName(), deckIndex, grade.name()));
                return;
            }

            final eMonsterBookV2DeckState state = deck.get_state();
            if (!state.equals(eMonsterBookV2DeckState.DS_COMPLETED)) {
                SC_MonsterBookV2RewardAck.send(pc, deckIndex, grade,
                        state.equals(eMonsterBookV2DeckState.DS_IN_PROGRESS) ? eResultCode.REWARD_FAIL_NOT_ENOUGH_AMOUNT : eResultCode.REWARD_FAIL);
                System.out.println(String.format("[도감] 완료상태가 아닌 상태에서 주간퀘스트 보상을 시도했습니다. 캐릭터이름 : %s, 보상라인 : %d, 보상종류 : %s, 상태 : %s",
                        pc.getName(), deckIndex, grade.name(), state.name()));
                return;
            }

            SC_MonsterBookV2RewardAck.send(pc, deckIndex, grade, BQSWQRewardsLoader.getInstance().doReward(pc, grade));
            deck.set_state(eMonsterBookV2DeckState.DS_REWARDED);
            SC_MonsterBookV2UpdateStateNoti.send(pc, deck);
            BQSCharacterDataLoader.storeCharacterBqs(bqs, false);

        } catch (Throwable t) {
            System.out.println(String.format("[도감] 주간퀘스트 보상 예외. %s", client.getIp()));
            t.printStackTrace();
            final L1PcInstance pc = client.getActiveChar();
            if (pc != null)
                SC_MonsterBookV2RewardAck.send(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
        }
    }
}