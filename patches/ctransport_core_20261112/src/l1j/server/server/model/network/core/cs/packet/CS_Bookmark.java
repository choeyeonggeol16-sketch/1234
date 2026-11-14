package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1BookMark;

public final class CS_Bookmark {

    private CS_Bookmark() {
    }

    public static void handle(
            CIOClient client,
            String bookmarkName) {

        if (client == null
                || bookmarkName == null
                || bookmarkName.isEmpty()) {

            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        /*
         * 맵 자체가 기억 불가.
         */
        if (!pc.isGm()
                && !pc.getMap().isMarkable()) {

            SC_ServerMessage.send(pc, 214);

            return;
        }

        /*
         * GM은 아래 모든 지역 제한 bypass.
         */
        if (!pc.isGm()
                && isRestrictedArea(pc)) {

            SC_ServerMessage.send(pc, 214);

            return;
        }

        L1BookMark.addBookmark(
                pc,
                bookmarkName);
    }

    private static boolean isRestrictedArea(
            L1PcInstance pc) {

        final int x =
                pc.getX();

        final int y =
                pc.getY();

        final int mapId =
                pc.getMapId();

        if (L1CastleLocation.checkInAllWarArea(
                x,
                y,
                (short) mapId)) {

            return true;
        }

        if (L1HouseLocation.isInHouse(
                x,
                y,
                (short) mapId)) {

            return true;
        }

        /*
         * map 4 전용 지역.
         * mapId 비교를 매 조건마다 반복하지 않는다.
         */
        if (mapId == 4) {

            if (inRect(
                    x, y,
                    33514, 33809,
                    32216, 32457)) {

                return true;
            }

            if (inRect(
                    x, y,
                    34280, 34287,
                    33103, 33492)) {

                return true;
            }

            if (inRect(
                    x, y,
                    33464, 33532,
                    32839, 32878)) {

                return true;
            }

            if (inRect(
                    x, y,
                    34207, 34287,
                    33097, 33515)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33261, 33266,
                    32397, 32405)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33331, 33339,
                    32432, 32440)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33388, 33396,
                    32340, 32348)) {

                return true;
            }

            // 한국민
            if (inRect(
                    x, y,
                    33449, 33473,
                    32324, 32347)) {

                return true;
            }

            // 악어섬
            if (inRect(
                    x, y,
                    33470, 33530,
                    33177, 33231)) {

                return true;
            }

            // 샌드웜
            if (inRect(
                    x, y,
                    32707, 32826,
                    33117, 33229)) {

                return true;
            }
        }

        // 정무
        return mapId == 430
                || mapId == 15430;
    }

    private static boolean inRect(
            int x,
            int y,
            int minX,
            int maxX,
            int minY,
            int maxY) {

        return x >= minX
                && x <= maxX
                && y >= minY
                && y <= maxY;
    }
}