package l1j.server.server.model.network.core.cs.packet;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.appcenter.AppCenterService;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.MapUserTeleportTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.CParty;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.npc.spawn.boss.BossTeleportInvites;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SOCIAL_ACTION_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_TAM_WINDOW;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.utils.TYUtil;

public final class CS_ActionUi {


    private static final int ACCOUNT_TAM = 0x01CC;
    private static final int ACCOUNT_TAM_CANCEL = 0x01E0;
    private static final int ACCOUNT_TAM_UPDATE = 0x013D;

    private static final int ACTION_SOCIAL = 0x013F;
    private static final int SKY_GARDEN_TELEPORT = 0x84;
    private static final int CASTLE_WAR_PROCLAIM = 0x45;
    private static final int PARTY_FLAG_SET = 0x0152;
    private static final int EVENT_TELEPORT = 143;
    private static final int NEWSHOP = 0x0331;
    private static final int BLESSED_TELEPORT = 0x033D;

    private static final int CLAN_JOIN_SETTING = 0x0146;
    private static final int CLAN_JOIN_INFO = 0x014C;
    private static final int CLAN_JOIN = 322;

    private static final int ENVIRONMENT_SETTING = 1002;

    public static final int[] EMPTY_SHOP_ENTRIES =
            new int[0];

    private static final Charset MS949 =
            Charset.forName("MS949");


    private static final int[] SKY_GARDEN_COORDS = {
            32779, 32825,
            32761, 32819,
            32756, 32837,
            32770, 32839
    };

    private CS_ActionUi() {
    }

    public static final class ShopPayload {

        final int[] entries;
        final int count;
        final byte[] chat;
        final String polyNum;

        public ShopPayload(
                int[] entries,
                int count,
                byte[] chat,
                String polyNum) {

            this.entries = entries;
            this.count = count;
            this.chat = chat;
            this.polyNum = polyNum;
        }
    }

    public static void handle(
            CUserSocket client,
            int type,
            int p1,
            int p2,
            int p3,
            long l0,
            long l1,
            String s0,
            String s1,
            Object o0) {

        if (client == null) {
            return;
        }

        try {

            final L1PcInstance pc =
                    client.getActiveChar();

            if (pc == null
                    || pc.isGhost()) {

                return;
            }

            switch (type) {

            case ENVIRONMENT_SETTING:
                handleEnvironmentSetting(pc);
                return;

            case NEWSHOP:

                User.OnPrivateStorePacket(
                        client,
                        p1,
                        o0 instanceof ShopPayload
                                ? (ShopPayload) o0
                                : null);

                return;

            case EVENT_TELEPORT:
                BossTeleportInvites.teleportFromActionUi(pc, client);
                return;

            case BLESSED_TELEPORT:

                handleBlessedTeleport(
                        pc,
                        o0 instanceof byte[]
                                ? (byte[]) o0
                                : null);

                return;

            case SKY_GARDEN_TELEPORT:
                handleSkyGardenTeleport(pc);
                return;

            case CLAN_JOIN_SETTING:

                handleClanJoinSetting(
                        pc,
                        p1,
                        p2,
                        s0);

                return;

            case CLAN_JOIN_INFO:
                handleClanJoinInfo(pc);
                return;

            case CLAN_JOIN:

                handleClanJoin(
                        pc,
                        p1,
                        o0 instanceof byte[]
                                ? (byte[]) o0
                                : null,
                        s0);

                return;

            case PARTY_FLAG_SET:

                handlePartyFlag(
                        pc,
                        o0 instanceof byte[]
                                ? (byte[]) o0
                                : null);

                return;

            case ACCOUNT_TAM_UPDATE:
                return;

            case ACCOUNT_TAM:

                SC_TAM_WINDOW.sendAsync(pc);

                return;

            case ACCOUNT_TAM_CANCEL:

                handleTamCancel(
                        pc,
                        s0);

                return;

            case ACTION_SOCIAL:

                handleSocialAction(
                        pc,
                        p1,
                        p2);

                return;

            case CASTLE_WAR_PROCLAIM:

                handleCastleWarProclaim(
                        pc,
                        p1);

                return;

            default:
                return;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static void handleEnvironmentSetting(
            L1PcInstance pc) {

        Calendar rTime =
                getRealTime();

        if (pc.isOneMinuteStart()) {

            if (rTime.after(
                    pc.getEnviroMentLocalTime())) {

                pcBuffPremiumTime(pc);

                pc.setEnviroMentCount(0);

                rTime.add(
                        Calendar.SECOND,
                        55);

                pc.setEnviroMentLocalTime(
                        rTime);

            } else {

                pc.addEnviroMentCount(1);
            }

        } else {

            pc.setOneMinuteStart(true);

            pc.setEnviroMentLocalTime(
                    rTime);
        }
    }



    private static void handleBlessedTeleport(
            L1PcInstance pc,
            byte[] bytes) {

        try {

            if (bytes == null) {
                return;
            }

            final String code =
                    new String(bytes);

            if (code.isEmpty()) {

                SC_SystemMessage.send(pc, "이동 코드가 비어있습니다.");

                return;
            }

            final MapUserTeleportTable.TeleportLoc data =
                    MapUserTeleportTable
                            .getInstance()
                            .getTeleportLoc(code);

            if (data == null) {
                return;
            }

            final int needItem =
                    data.getNeedItem();

            if (needItem > 0) {

                if (!pc.getInventory()
                        .HaveItemByClassId(
                                needItem,
                                1)) {

                    SC_SystemMessage.send(pc, "인벤토리에 아이템이 존재하지 않습니다.");

                    return;
                }

                if (!pc.getInventory()
                        .DeleteItemInInventory(
                                needItem,
                                1)) {

                    SC_SystemMessage.send(pc, "아이템 소모에 실패했습니다.");

                    return;
                }
            }

            pc.start_teleport(
                    data.getX(),
                    data.getY(),
                    (short) data.getMapId(),
                    pc.getHeading(),
                    169,
                    true,
                    pc.getAutoHunt());

            SC_PacketBox.send(pc, 
                            SC_PacketBox.TOWN_TELEPORT,
                            pc);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static void handleSkyGardenTeleport(
            L1PcInstance pc) {

        if (!pc.PC방_버프) {

            SC_SystemMessage.send(pc, "PC방 이용권을 사용중에만 사용 가능한 행동입니다.");

            return;
        }

        final int mapId =
                pc.getMapId();

        if (mapId == 13005
                || mapId == 2236) {

            SC_SystemMessage.send(pc, "이곳에서 사용할 수 없습니다.");

            return;
        }

        final int[] restrictedMaps =
                TYUtil.GetIntTokenizeIntArray(
                        Config.맵번호);

        if (restrictedMaps != null) {

            for (int map :
                    restrictedMaps) {

                if (mapId == map) {

                    SC_SystemMessage.send(pc, "주위의 마력에 의해 순간이동을 사용할 수 없습니다.");

                    return;
                }
            }
        }

        if (pc.hasSkillEffect(
                L1SkillId.DESPERADO)) {

            return;
        }

        if (pc.hasSkillEffect(
                    L1SkillId.쉐도우제약)
                || pc.hasSkillEffect(
                    L1SkillId.이터디스)) {

            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);

            return;
        }

        final int index =
                ThreadLocalRandom
                        .current()
                        .nextInt(4)
                        << 1;

        pc.start_teleport(
                SKY_GARDEN_COORDS[index],
                SKY_GARDEN_COORDS[index + 1],
                622,
                pc.getHeading(),
                169,
                true,
                pc.getAutoHunt());
    }

    private static void handleClanJoinSetting(
            L1PcInstance pc,
            int setting1,
            int setting2,
            String passwordHex) {

        if (pc.getClanid() == 0
                || (!pc.isCrown()
                && pc.getClanRank()
                    != L1Clan.수호)) {

            return;
        }

        if (pc.getClan()==null || setting1<0 || setting1>1 || setting2<0 || setting2>2) return;

        if (passwordHex != null) {

            pc.getClan()
                    .setJoinPassword(
                            passwordHex);

            ClanTable
                    .getInstance()
                    .updateClanPassword(
                            pc.getClan());
        }

        pc.getClan()
                .setJoinSetting(
                        setting1);

        pc.getClan()
                .setJoinType(
                        setting2);

        SC_NewCreateItem.sendSubtypeObjid(pc, SC_NewCreateItem.CLAN_JOIN_SETTING, setting1, setting2);

        final L1Clan updatedClan=pc.getClan();
        final int clanId=updatedClan.getClanId();
        final String clanName=updatedClan.getClanName();
        ClanTable.getInstance().updateClan(updatedClan,
                () -> AppCenterService.publishClanSettings(clanId,clanName,setting1,setting2));

        SC_ServerMessage.send(pc, 3980);
    }

    private static void handleClanJoinInfo(
            L1PcInstance pc) {

        if (pc.getClanid() == 0) {
            return;
        }

        SC_NewCreateItem.sendSubtypeObjid(pc, SC_NewCreateItem.CLAN_JOIN_SETTING, pc.getClan()
                                .getJoinSetting(), pc.getClan()
                                .getJoinType());
    }

    private static void handleClanJoin(
            L1PcInstance pc,
            int nameLength,
            byte[] nameBytes,
            String passwordHex) {

        try {

            if (nameBytes == null) {
                return;
            }

            if (pc.isCrown()) {

                SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 4);

                return;
            }

            if (pc.getClanid() != 0) {

                final L1Clan oldClan =
                        pc.getClan();

                if (oldClan == null || oldClan.getLeaderId()
                        != pc.getId()) {

                    SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 9);

                    return;
                }

                if (oldClan.getCastleId() > 0
                        || oldClan.getCurrentWar() != null
                        || oldClan.getAlliance() > 0) {

                    SC_ServerMessage.send(pc, ServerMessage.CANNOT_BREAK_CLAN);

                    return;
                }
            }

            final String clanName =
                    new String(
                            nameBytes,
                            0,
                            nameLength,
                            MS949);

            final L1Clan clan =
                    CWorld
                            .getInstance()
                            .findClan(
                                    clanName);

            if (clan == null) {

                SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 13);

                return;
            }

            final L1PcInstance crown =
                    clan.getonline간부();

            switch (clan.getJoinType()) {

            case 1:

                if (crown == null) {

                    SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 11);

                    return;
                }

                crown.setTempID(
                        pc.getId());

                SC_MessageYN.send(crown, 97,
                                pc.getName());

                SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 1);

                break;

            case 2:

                if (clan.getJoinPassword() == null
                        || !clan
                            .getJoinPassword()
                            .equalsIgnoreCase(
                                    passwordHex)) {

                    SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 3);

                    return;
                }

                /*
                 * 원본 fall-through 유지.
                 */

            case 0:

                // Admission is asynchronous. Only a durable COMMIT may send
                // success; an accepted request is not a successful join.
                if (!L1ClanJoin.getInstance().ClanJoin(clan, pc,
                        committed -> SC_NewCreateItem.sendSubtype(pc,
                                SC_NewCreateItem.CLAN_JOIN_MESSAGE, committed ? 0 : 1))) {
                    SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 1);
                }

                break;

            default:

                SC_NewCreateItem.sendSubtype(pc, SC_NewCreateItem.CLAN_JOIN_MESSAGE, 11);

                break;
            }

        } catch (Exception e) {
        }
    }

    private static void handlePartyFlag(
            L1PcInstance pc,
            byte[] bytes) {

        if (bytes == null) {
            return;
        }

        final CParty party =
                pc.getParty();

        if (party == null) {
            return;
        }

        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(party.getMembers(),SC_ActionUI.nativeFlagWriter(bytes));
    }

    private static void handleTamCancel(
            L1PcInstance pc,
            String encoded) {

        TamFruitUseService.cancelReservation(pc, encoded);
    }

    private static void handleSocialAction(
            L1PcInstance pc,
            int actionType,
            int code) {
        final byte[] encoded=SC_SOCIAL_ACTION_NOTI.encodeFields(
                pc.getId(),actionType,code);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendRawSelfAndVisible(pc,encoded);
    }

    private static void handleCastleWarProclaim(
            L1PcInstance pc,
            int castleId) {

        try {

            CastleWarBusiness
                    .getInstance()
                    .proclaim(
                            pc,
                            castleId);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private static Calendar getRealTime() {

        final TimeZone tz =
                TimeZone.getTimeZone(
                        Config.TIME_ZONE);

        return Calendar.getInstance(
                tz);
    }

    private static void pcBuffPremiumTime(
            L1PcInstance pc) {

        if (pc.PC방_버프삭제중) {

            pc.PC방_버프삭제중 =
                    false;

            SC_ActionUI.send(pc, SC_ActionUI.PCBANG_SET, false);

            SC_PacketBox.send(pc, 
                            SC_PacketBox.UNLIMITED_ICON1,
                            293,
                            false);

            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "[PC방 상품 종료 안내] PC방 이용 시간이 종료되어 효과가 사라집니다.");
        }

        final long sysTime =
                System.currentTimeMillis();

        if (pc.PC방_버프
                && pc.getAccount()
                    .getBuff_PC방() != null) {

            if (sysTime
                    <= pc.getAccount()
                        .getBuff_PC방()
                        .getTime()) {

                final long pcTime =
                        pc.getAccount()
                            .getBuff_PC방()
                            .getTime()
                        - sysTime;

                final Calendar calendar =
                        Calendar.getInstance(
                                TimeZone.getTimeZone(
                                        Config.TIME_ZONE));

                calendar.setTimeInMillis(
                        pcTime);

                final int d =
                        calendar.get(
                                Calendar.DATE)
                        - 1;

                final int h =
                        calendar.get(
                                Calendar.HOUR_OF_DAY);

                final int m =
                        calendar.get(
                                Calendar.MINUTE);

                final int sc =
                        calendar.get(
                                Calendar.SECOND);

                if (d == 0
                        && h < 0
                        && m == 1) {

                    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "[PC방 이용 시간] "
                                            + m
                                            + "분 "
                                            + sc
                                            + "초 남았습니다.");

                    SC_SystemMessage.send(pc, "[PC방 상품 종료 안내] 이용 시간 소진시 1분 뒤에 아이콘이 사라집니다.");
                }

            } else {

                pc.setPC방_버프(
                        false);

                pc.PC방_버프삭제중 =
                        true;
            }
        }
    }
}
