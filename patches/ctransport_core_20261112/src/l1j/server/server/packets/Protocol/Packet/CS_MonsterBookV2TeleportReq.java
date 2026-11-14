package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQDecksLoader;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.MonsterBookV2Info;
import l1j.server.server.packets.Protocol.Packet.SC_MonsterBookV2TeleportAck.eResultCode;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_MONSTER_BOOK_V2_TELEPORT_REQ (0x032F) — 도감 주간퀘스트 텔레포트 요청.
 * 와이어: deck_index(f1, varint), card_index(f2, varint)
 */
public final class CS_MonsterBookV2TeleportReq {
    private CS_MonsterBookV2TeleportReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        int deckIndex = 0;
        int cardIndex = 0;
        boolean hasDeck = false, hasCard = false;

        try {
            // ---------- 파싱 ----------
            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x08: deckIndex = r.varint32(); hasDeck = true; break;
                case 0x10: cardIndex = r.varint32(); hasCard = true; break;
                default: r.skip(tag & 7); break;
                }
            }

            // ---------- 필수 필드 검증 ----------
            if (!hasDeck || !hasCard) return;

            // ---------- 처리 로직 ----------
            final L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
                SC_MonsterBookV2TeleportAck.send(pc, deckIndex, cardIndex, eResultCode.TELEPORT_FAIL);
                pc.sendPackets("현재 도감 시스템이 일시 중지 상태입니다.");
                return;
            }

            //        조작 음수값이 findDeck 내부로 들어가는 걸 입구에서 차단
            if (deckIndex < 0 || cardIndex < 0
                    || deckIndex >= BQSLoadManager.BQS_WQ_HEIGHT || cardIndex >= BQSLoadManager.BQS_WQ_WIDTH) {
                SC_MonsterBookV2TeleportAck.send(pc, deckIndex, cardIndex, eResultCode.TELEPORT_FAIL);
                System.out.println(String.format("[도감] 존재하지 않는 주간퀘스트 인덱스로 접근을 시도했습니다.(텔레포트). 캐릭터이름 : %s, 난이도 : %d, 순번 : %d",
                        pc.getName(), deckIndex, cardIndex));
                return;
            }

            final MonsterBookV2Info.DeckT.CardT card = BQSWQDecksLoader.getInstance().findDeck(deckIndex, cardIndex);
            if (card == null) {
                SC_MonsterBookV2TeleportAck.send(pc, deckIndex, cardIndex, eResultCode.TELEPORT_FAIL);
                System.out.println(String.format("[도감] 존재하지 않는 주간퀘스트로 접근을 시도했습니다.(텔레포트). 캐릭터이름 : %s, 난이도 : %d, 순번 : %d",
                        pc.getName(), deckIndex, cardIndex));
                return;
            }

            final BQSInformation bqsInfo = BQSInformationLoader.getInstance().getInformation(card.get_criteria_id());
            SC_MonsterBookV2TeleportAck.send(pc, deckIndex, cardIndex,
                    SC_MonsterBookV2TeleportAck.eResultCode.from(bqsInfo.doTeleport(pc)));

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}