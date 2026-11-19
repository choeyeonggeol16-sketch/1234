package l1j.server.server.model.network.core.cs.packet;

import java.util.Random;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.TreasureChest.MJL1TreasureChest;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1House;

/**
 * C_OPEN / 기존 C_Door의 네트워크 이관본.
 *
 * packet wrapper만 제거하고 기존 게임 로직은 그대로 유지한다.
 */
public final class CS_Door {

    /*
     * 기존 C_Door와 동일하게 static Random 하나를 유지한다.
     * C_OPEN은 cold path라 여기서 Random contention을 건드릴 이유가 없다.
     */
    private static final Random RANDOM =
            new Random(System.nanoTime());

    private CS_Door() {
    }

    public static void handle(
            CIOClient client,
            int locX,
            int locY,
            int objectId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Object obj =
                L1World.getInstance()
                        .findObject(objectId);

        if (obj == null) {
            return;
        }

  
        if (obj.instanceOf(MJL1Type.L1TYPE_TREASURE_CHEST)) {
            ((MJL1TreasureChest) obj).open(pc);
            return;
        }

        if (!(obj instanceof L1DoorInstance)) {
            return;
        }

        final L1DoorInstance door =
                (L1DoorInstance) obj;

        /*
         * 클라이언트가 클릭한 문 좌표가 PC 인접 1칸 밖이면 무시.
         */
        if (locX > pc.getX() + 1
                || locX < pc.getX() - 1
                || locY > pc.getY() + 1
                || locY < pc.getY() - 1) {

            return;
        }

        final int npcId =
                door.getNpcId();

        if ((npcId >= 5147 && npcId <= 5151)
                || (npcId >= 14911 && npcId <= 14912)) {

            return;
        }

  
        if (isExistKeeper(
                pc,
                door.getKeeperId())) {

            return;
        }

        final int doorId =
                door.getDoorId();

        if (doorId == 113) {
            if (pc.getInventory()
                    .checkItem(40163)) {

                pc.getInventory()
                        .consumeItem(40163, 1);

            } else {
                return;
            }
        }

        /*
         * 기란 감옥 상자.
         */
        if (doorId >= 8001
                && doorId <= 8010) {

            if (pc.getInventory()
                    .checkItem(
                            L1ItemId.GIRANCAVE_BOXKEY,
                            1)) {

                giranCaveBox(
                        pc,
                        door);

            }

            return;
        }

        /*
         * 하딘 문.
         */
        if (doorId >= 900151
                && doorId <= 900154) {

            return;
        }

        /*
         * 화룡의 안식처.
         */
        if (npcId >= 7210013
                && npcId <= 7210015) {

            return;
        }

        if (door.getOpenStatus()
                == ActionCodes.ACTION_Open) {

            if (!(doorId >= 7210016
                    && doorId <= 7210019)) {

                door.close();
            }

            return;
        }

        if (door.getOpenStatus()
                == ActionCodes.ACTION_Close) {

            door.open();

            if (doorId >= 7210016
                    && doorId <= 7210019) {

                phoenixEgg(
                        pc,
                        door);
            }
        }
    }

    // ------------------------------------------------------------------
    // 기란 감옥 상자
    // ------------------------------------------------------------------

    private static void giranCaveBox(
            L1PcInstance pc,
            L1DoorInstance door) {

        final int ran =
                RANDOM.nextInt(100) + 1;

        if (door.getOpenStatus()
                != ActionCodes.ACTION_Close) {

            return;
        }

        pc.getInventory()
                .consumeItem(
                        L1ItemId.GIRANCAVE_BOXKEY,
                        1);

        door.open();

        if (ran <= 15) {
            pc.getInventory().storeItem(40308, 10000);
            SC_SystemMessage.send(pc, "아데나 (10000)을 얻었습니다.");

        } else if (ran <= 29) {
            pc.getInventory().storeItem(40308, 20000);
            SC_SystemMessage.send(pc, "아데나 (20000)을 얻었습니다.");

        } else if (ran <= 49) {
            pc.getInventory().storeItem(40308, 30000);
            SC_SystemMessage.send(pc, "아데나 (30000)을 얻었습니다.");

        } else if (ran <= 59) {
            pc.getInventory().storeItem(40308, 50000);
            SC_SystemMessage.send(pc, "아데나 (50000)을 얻었습니다.");

        } else if (ran <= 64) {
            pc.getInventory().storeItem(40308, 100000);
            SC_SystemMessage.send(pc, "아데나 (100000)을 얻었습니다.");

        } else if (ran <= 69) {
            pc.getInventory().storeItem(40308, 200000);
            SC_SystemMessage.send(pc, "아데나 (200000)을 얻었습니다.");

        } else if (ran <= 72) {
            pc.getInventory().storeItem(40308, 300000);
            SC_SystemMessage.send(pc, "아데나 (300000)을 얻었습니다.");

        } else if (ran <= 75) {
            pc.getInventory().storeItem(40308, 400000);
            SC_SystemMessage.send(pc, "아데나 (400000)을 얻었습니다.");

        } else if (ran <= 78) {
            pc.getInventory().storeItem(40308, 500000);
            SC_SystemMessage.send(pc, "아데나 (500000)을 얻었습니다.");

        } else if (ran <= 80) {
            pc.getInventory().storeItem(40308, 1000000);
            SC_SystemMessage.send(pc, "아데나 (1000000)을 얻었습니다.");

        } else if (ran <= 90) {
            pc.getInventory().storeItem(40074, 3);
            SC_SystemMessage.send(pc, "갑옷 마법 주문서 (3)을 얻었습니다.");

        } else {
            pc.getInventory().storeItem(40087, 3);
            SC_SystemMessage.send(pc, "무기 마법 주문서 (3)을 얻었습니다.");
        }
    }

    // ------------------------------------------------------------------
    // 화룡 알
    // ------------------------------------------------------------------

    private static void phoenixEgg(
            L1PcInstance pc,
            L1DoorInstance door) {

        final int ran =
                RANDOM.nextInt(100) + 1;

        final L1ItemInstance item;

        if (ran <= 10) {
            item = pc.getInventory()
                    .storeItem(40010, 5);

            SC_ServerMessage.send(pc, 403, item.getName() + " (5)");

        } else if (ran <= 20) {
            item = pc.getInventory()
                    .storeItem(40010, 10);

            SC_ServerMessage.send(pc, 403, item.getName() + " (10)");

        } else if (ran <= 30) {
            item = pc.getInventory()
                    .storeItem(40010, 15);

            SC_ServerMessage.send(pc, 403, item.getName() + " (15)");

        } else if (ran <= 40) {
            item = pc.getInventory()
                    .storeItem(40010, 30);

            SC_ServerMessage.send(pc, 403, item.getName() + " (30)");

        } else if (ran <= 50) {
            item = pc.getInventory()
                    .storeItem(40010, 50);

            SC_ServerMessage.send(pc, 403, item.getName() + " (50)");

        } else if (ran <= 60) {
            item = pc.getInventory()
                    .storeItem(40012, 5);

            SC_ServerMessage.send(pc, 403, item.getName() + " (5)");

        } else if (ran <= 70) {
            item = pc.getInventory()
                    .storeItem(40012, 10);

            SC_ServerMessage.send(pc, 403, item.getName() + " (10)");

        } else if (ran <= 80) {
            item = pc.getInventory()
                    .storeItem(40012, 15);

            SC_ServerMessage.send(pc, 403, item.getName() + " (15)");

        } else if (ran <= 90) {
            item = pc.getInventory()
                    .storeItem(40012, 30);

            SC_ServerMessage.send(pc, 403, item.getName() + " (30)");

        } else {
            item = pc.getInventory()
                    .storeItem(40012, 50);

            SC_ServerMessage.send(pc, 403, item.getName() + " (50)");
        }
    }

    private static boolean isExistKeeper(
            L1PcInstance pc,
            int keeperId) {

        if (keeperId == 0) {
            return false;
        }

        final L1Clan clan =
                L1World.getInstance()
                        .getClan(pc.getClanid());

        if (clan != null) {
            final int houseId =
                    clan.getHouseId();

            if (houseId != 0) {
                final L1House house =
                        HouseTable.getInstance()
                                .getHouseTable(houseId);

                if (house != null
                        && keeperId == house.getKeeperId()) {

                    return false;
                }
            }
        }

        return true;
    }
}
