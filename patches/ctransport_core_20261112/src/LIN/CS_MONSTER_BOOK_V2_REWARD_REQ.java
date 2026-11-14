package LIN;

import LIN.MonsterBookV2Info;
import LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode;
import LIN.eMonsterBookV2DeckState;
import LIN.eMonsterBookV2RewardGrade;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQRewardsLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;


/**
 * CS_MONSTER_BOOK_V2_REWARD_REQ (0x032B) 
 * 와이어: deck_index(field 1, varint), grade(field 2, enum)
 */
public final class CS_MONSTER_BOOK_V2_REWARD_REQ {
    private CS_MONSTER_BOOK_V2_REWARD_REQ() {}

    public static void handle(CIOClient client, ProtoReader r) {
        int deckIndex = 0;
        LIN.eMonsterBookV2RewardGrade grade = null;
        boolean hasDeck = false, hasGrade = false;

        try {
            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x08: deckIndex = r.varint32(); hasDeck = true; break;
                case 0x10: grade = LIN.eMonsterBookV2RewardGrade.fromInt(r.varint32()); hasGrade = true; break;
                default: r.skip(tag & 7); break;
                }
            }

            // ---------- pc 확보 ----------
            final L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            // ---------- 필수 필드 검증 ----------
            if (!hasDeck || !hasGrade) return;

            if (grade == null) {
                LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, null, LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL);
                return;
            }

            // ---------- 비즈니스 로직 ----------
            if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
                LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL);
                pc.sendPackets("현재 도감 시스템이 일시 중지 상태입니다.");
                return;
            }

            final BQSCharacterData bqs = pc.getBqs();
            if (bqs == null) return;

            final LIN.MonsterBookV2Info.DeckT deck = bqs.get_deck(deckIndex);
            if (deck == null) {
                LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL_INVALID_INDEX);
                System.out.println(String.format("[도감] 할당되지 않은 주간퀘스트 보상을 시도했습니다. 캐릭터이름 : %s, 보상라인 : %d, 보상종류 : %s",
                        pc.getName(), deckIndex, grade.name()));
                return;
            }

            final LIN.eMonsterBookV2DeckState state = deck.state();
            if (!state.equals(LIN.eMonsterBookV2DeckState.DS_COMPLETED)) {
                LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade,
                        state.equals(LIN.eMonsterBookV2DeckState.DS_IN_PROGRESS) ? LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL_NOT_ENOUGH_AMOUNT : LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL);
                System.out.println(String.format("[도감] 완료상태가 아닌 상태에서 주간퀘스트 보상을 시도했습니다. 캐릭터이름 : %s, 보상라인 : %d, 보상종류 : %s, 상태 : %s",
                        pc.getName(), deckIndex, grade.name(), state.name()));
                return;
            }

            LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, BQSWQRewardsLoader.getInstance().doReward(pc, grade));
            deck.set_state(LIN.eMonsterBookV2DeckState.DS_REWARDED);
            LIN.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI.send(pc, deck);
            BQSCharacterDataLoader.storeCharacterBqs(bqs, false);

        } catch (Throwable t) {
            System.out.println(String.format("[도감] 주간퀘스트 보상 예외. %s", client.getIp()));
            t.printStackTrace();
            final L1PcInstance pc = client.getActiveChar();
            if (pc != null)
                LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, LIN.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL);
        }
    }
}