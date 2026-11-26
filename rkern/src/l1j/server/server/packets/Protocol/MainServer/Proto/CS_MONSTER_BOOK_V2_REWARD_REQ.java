package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.BookQuest.WeeklyMonsterBookRewards;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.eMonsterBookV2RewardGrade;

/**
 * CS_MONSTER_BOOK_V2_REWARD_REQ (0x032B) 
 * 와이어: deck_index(field 1, varint), grade(field 2, enum)
 */
public final class CS_MONSTER_BOOK_V2_REWARD_REQ {
    private CS_MONSTER_BOOK_V2_REWARD_REQ() {}

    public static void handleRequest(CUserSocket client, ProtoReader r) {
        if (client == null) return;
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

            if (hasDeck && hasGrade) WeeklyMonsterBookRewards.claim(client.getActiveChar(), deckIndex, grade);

        } catch (RuntimeException t) {
            System.out.println(String.format("[도감] 주간퀘스트 보상 예외. %s", client.getIp()));
            t.printStackTrace();
            final L1PcInstance pc = client.getActiveChar();
            if (pc != null)
                SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
        }
    }
}