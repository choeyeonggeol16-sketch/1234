package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.BuddyTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1CharName;

public final class CS_AddBuddy {

    private CS_AddBuddy() {
    }

    public static void handle(
            CIOClient client,
            String charName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (charName == null
                || charName.isEmpty()) {

            return;
        }

        /*
         * 자기 자신 친구 추가 방지.
         */
        if (charName.equalsIgnoreCase(
                pc.getName())) {

            return;
        }

        final BuddyTable buddyTable =
                BuddyTable.getInstance();

        final L1Buddy buddyList =
                buddyTable.getBuddyTable(
                        pc.getId());

        /*
         * 이미 등록된 친구.
         */
        if (buddyList != null
                && buddyList.containsName(
                        charName)) {

            SC_ServerMessage.send(pc, 1052, charName);

            return;
        }

        /*
         * 캐릭터 이름 조회.
         */
        final L1CharName character =
                CharacterTable
                        .getInstance()
                        .getCharName(
                                charName);

        if (character != null) {

            final int objectId =
                    character.getId();

            final String name =
                    character.getName();

            /*
             * 메모리에 buddy list가 이미 존재하면
             * 즉시 반영.
             */
            if (buddyList != null) {

                buddyList.add(
                        objectId,
                        name);
            }

            /*
             * DB / BuddyTable 등록.
             */
            buddyTable.addBuddy(
                    pc.getId(),
                    objectId,
                    name);

            return;
        }

        /*
         * 존재하지 않는 캐릭터.
         */
        SC_ServerMessage.send(pc, 109, charName);
    }
}