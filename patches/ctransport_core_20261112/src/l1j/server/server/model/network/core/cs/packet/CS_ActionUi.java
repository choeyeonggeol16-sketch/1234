package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.MJDShopSystem.MJDShopItem;
import l1j.server.MJDShopSystem.MJDShopStorage;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.Lineage2D.MJPoint;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.appcenter.AppCenterService;
import l1j.server.server.datatables.MapUserTeleportTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.tam.L1Tam;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import LIN.SC_SOCIAL_ACTION_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_TamWindow;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Ping;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Boss;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.utils.TYUtil;

public final class CS_ActionUi {

    private static final int CS_PING_ACK = 0x03E9;

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

    private static final int[] RUNE_ARMOR_IDS = {
            22232, 22233, 22234, 22235,
            22236, 22237, 22238, 22239,
            22240, 22241, 22242, 22243,
            22244, 22245, 22246, 22247,
            22248, 22249
    };

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
            CIOClient client,
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

            case CS_PING_ACK:

                if (pc.isGm()) {
                    SC_Ping.reqForGM(pc);
                }

                return;

            case NEWSHOP:

                handleNewShop(
                        pc,
                        p1,
                        o0 instanceof ShopPayload
                                ? (ShopPayload) o0
                                : null);

                return;

            case EVENT_TELEPORT:
                handleEventTeleport(pc);
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

                SC_TamWindow.sendAsync(pc);

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

    private static void handleNewShop(
            L1PcInstance pc,
            int newShopType,
            ShopPayload payload) {

        try {

            if (!pc.getInventory()
                    .checkItem(
                            40308,
                            1000)) {

                SC_ChatPacket.sendDirect(pc, pc, "\\aG상점 설치는 1,000원이 필요합니다.");

                return;
            }

            if (pc.isInvisble()) {

                SC_ServerMessage.send(pc, 755);

                return;
            }

            if (pc.getMapId() != 800) {

                if (pc.isFishing()) {

                    pc.setFishing(false);
                    pc.setFishingTime(0);
                    pc.setFishingReady(false);

                    SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));

                    SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));

                    FishingTimeController
                            .getInstance()
                            .removeMember(pc);

                    SC_ServerMessage.send(pc, 2120);

                    return;
                }

                SC_SystemMessage.send(pc, "개인상점은 시장에서만 열 수 있습니다.");

                return;
            }

            for (int runeId :
                    RUNE_ARMOR_IDS) {

                if (pc.getInventory()
                        .checkEquipped(runeId)) {

                    SC_ChatPacket.sendDirect(pc, pc, "룬을 착용하셨다면 해제하시기 바랍니다.");

                    return;
                }
            }

            L1PolyMorph.undoPoly(pc);

            pc.killSkillEffectTimer(
                    L1SkillId.SHAPE_CHANGE);

            if (newShopType == 0) {

                if (pc.getCurrentSpriteId()
                        != pc.getClassId()) {

                    SC_SystemMessage.send(pc, "변신이 해제됩니다.");
                }

                if (pc.hasSkillEffect(
                        L1SkillId.SHAPE_CHANGE)) {

                    pc.removeSkillEffect(
                            L1SkillId.SHAPE_CHANGE);

                } else if (pc.hasSkillEffect(
                        L1SkillId.POLY_RING_MASTER)) {

                    pc.removeSkillEffect(
                            L1SkillId.POLY_RING_MASTER);

                } else {

                    L1PolyMorph.undoPoly(pc);
                }

                for (L1PcInstance target :
                        L1World
                                .getInstance()
                                .getAllPlayers3()) {

                    if (target.getId() != pc.getId()
                            && target
                                .getAccountName()
                                .equalsIgnoreCase(
                                        pc.getAccountName())
                            && target.isPrivateShop()) {

                        pc.sendPackets(
                                "\\f3이미 당신의 보조 캐릭터가 무인상점 상태입니다.");

                        pc.setPrivateShop(false);

                        pc.상점변신 = 0;

                        SC_DoActionGFX.send(
                                pc.getNetConnection(),
                                pc.getId(),
                                ActionCodes.ACTION_Idle);

                        SC_DoActionGFX.broadcast(
                                pc,
                                ActionCodes.ACTION_Idle);

                        SC_WorldPutObject.broadcastPc(pc);

                        return;
                    }
                }

                boolean tradable = true;

                final int[] entries =
                        payload != null
                                ? payload.entries
                                : EMPTY_SHOP_ENTRIES;

                final int count =
                        payload != null
                                ? payload.count
                                : 0;

                /*
                 * 원본과 동일하게 SELL 먼저.
                 */
                for (int i = 0;
                        i < count;
                        i++) {

                    final int base =
                            i << 2;

                    if (entries[base] != 0x12) {
                        continue;
                    }

                    final int objectId =
                            entries[base + 1];

                    final int price =
                            entries[base + 2];

                    final int itemCount =
                            entries[base + 3];

                    final L1ItemInstance checkItem =
                            pc.getInventory()
                                    .getItem(objectId);

                    if (checkItem == null
                            || objectId
                                != checkItem.getId()) {

                        SC_Disconnect.send(pc);

                        return;
                    }

                    if (!checkItem.isStackable()
                            && itemCount != 1) {

                        SC_Disconnect.send(pc);

                        return;
                    }

                    if (checkItem.getCount() <= 0
                            || itemCount <= 0) {

                        pc.disposeShopInfo();

                        return;
                    }

                    if (checkItem.getBless() >= 128) {

                        SC_ServerMessage.send(pc, 210, checkItem
                                            .getItem()
                                            .getName());

                        pc.disposeShopInfo();

                        return;
                    }

                    if (!checkItem
                            .getItem()
                            .isTradable()) {

                        tradable = false;

                        SC_ServerMessage.send(pc, 166, checkItem
                                            .getItem()
                                            .getName(), "거래 불가능합니다.");

                        break;
                    }

                    if (!MJCompanionInstanceCache
                            .is_companion_oblivion(
                                    checkItem.getId())) {

                        tradable = false;

                        SC_ServerMessage.send(pc, 166, checkItem
                                            .getItem()
                                            .getName(), "거래 불가능합니다.");
                    }

                    for (Object petObject :
                            pc.getPetList().values()) {

                        if (petObject instanceof L1PetInstance
                                && checkItem.getId()
                                    == ((L1PetInstance) petObject)
                                        .getItemObjId()) {

                            tradable = false;

                            SC_ServerMessage.send(pc, 166, checkItem
                                                .getItem()
                                                .getName(), "거래 불가능합니다.");

                            break;
                        }
                    }

                    for (Object dollObject :
                            pc.getDollList().values()) {

                        if (dollObject instanceof L1DollInstance
                                && checkItem.getId()
                                    == ((L1DollInstance) dollObject)
                                        .getItemObjId()) {

                            tradable = false;

                            SC_ServerMessage.send(pc, 166, checkItem
                                                .getItem()
                                                .getName(), "거래 불가능합니다.");

                            break;
                        }
                    }

                    pc.addSellings(
                            MJDShopItem.create(
                                    checkItem,
                                    itemCount,
                                    price,
                                    false));
                }

                /*
                 * 원본과 동일하게 BUY second.
                 */
                for (int i = 0;
                        i < count;
                        i++) {

                    final int base =
                            i << 2;

                    if (entries[base] != 0x1A) {
                        continue;
                    }

                    final int objectId =
                            entries[base + 1];

                    final int price =
                            entries[base + 2];

                    final int itemCount =
                            entries[base + 3];

                    final L1ItemInstance checkItem =
                            pc.getInventory()
                                    .getItem(objectId);

                    if (checkItem == null
                            || objectId
                                != checkItem.getId()) {

                        SC_Disconnect.send(pc);

                        return;
                    }

                    if (!checkItem.isStackable()
                            && itemCount != 1) {

                        SC_Disconnect.send(pc);

                        return;
                    }

                    if (checkItem.getCount() <= 0
                            || itemCount <= 0) {

                        pc.disposeShopInfo();

                        return;
                    }

                    if (checkItem.getBless() >= 128) {

                        SC_ServerMessage.send(pc, 210, checkItem
                                            .getItem()
                                            .getName());

                        pc.disposeShopInfo();

                        return;
                    }

                    if (!checkItem
                            .getItem()
                            .isTradable()) {

                        tradable = false;

                        SC_ServerMessage.send(pc, 166, checkItem
                                            .getItem()
                                            .getName(), "거래 불가능합니다.");

                        break;
                    }

                    if (!MJCompanionInstanceCache
                            .is_companion_oblivion(
                                    checkItem.getId())) {

                        tradable = false;

                        SC_ServerMessage.send(pc, 166, checkItem
                                            .getItem()
                                            .getName(), "거래 불가능합니다.");
                    }

                    for (Object petObject :
                            pc.getPetList().values()) {

                        if (petObject instanceof L1PetInstance
                                && checkItem.getId()
                                    == ((L1PetInstance) petObject)
                                        .getItemObjId()) {

                            tradable = false;

                            SC_ServerMessage.send(pc, 166, checkItem
                                                .getItem()
                                                .getName(), "거래 불가능합니다.");

                            break;
                        }
                    }

                    for (Object dollObject :
                            pc.getDollList().values()) {

                        if (dollObject instanceof L1DollInstance
                                && checkItem.getId()
                                    == ((L1DollInstance) dollObject)
                                        .getItemObjId()) {

                            tradable = false;

                            SC_ServerMessage.send(pc, 166, checkItem
                                                .getItem()
                                                .getName(), "거래 불가능합니다.");

                            break;
                        }
                    }

                    pc.addPurchasings(
                            MJDShopItem.create(
                                    checkItem,
                                    itemCount,
                                    price,
                                    true));
                }

                if (!tradable) {

                    pc.setPrivateShop(false);

                    SC_DoActionGFX.send(
                            pc.getNetConnection(),
                            pc.getId(),
                            ActionCodes.ACTION_Idle);

                    SC_DoActionGFX.broadcast(
                            pc,
                            ActionCodes.ACTION_Idle);

                    pc.disposeShopInfo();

                    return;
                }

                final byte[] chat =
                        payload != null
                                ? payload.chat
                                : new byte[0];

                final String polyNum =
                        payload != null
                                ? payload.polyNum
                                : null;

                if (pc.getNetConnection() != null
                        && pc.getNetConnection()
                            .getAccount() != null) {

                    pc.getNetConnection()
                            .getAccount()
                            .updateShopOpenCount();

                    SC_PacketBox.send(pc, 
                                    SC_PacketBox.상점개설횟수,
                                    pc.getNetConnection()
                                        .getAccount()
                                        .Shop_open_count);
                }

                pc.setShopChat(chat);

                pc.setPrivateShop(true);

                SC_DoActionShop.send(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);

                SC_DoActionShop.broadcast(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);

                int poly = 0;

                if (polyNum != null) {

                    if (polyNum.equalsIgnoreCase("tradezone1")) {
                        poly = 11479;
                    } else if (polyNum.equalsIgnoreCase("tradezone2")) {
                        poly = 11483;
                    } else if (polyNum.equalsIgnoreCase("tradezone3")) {
                        poly = 11480;
                    } else if (polyNum.equalsIgnoreCase("tradezone4")) {
                        poly = 11485;
                    } else if (polyNum.equalsIgnoreCase("tradezone5")) {
                        poly = 11482;
                    } else if (polyNum.equalsIgnoreCase("tradezone6")) {
                        poly = 11486;
                    } else if (polyNum.equalsIgnoreCase("tradezone7")) {
                        poly = 11481;
                    } else if (polyNum.equalsIgnoreCase("tradezone8")) {
                        poly = 11484;
                    }
                }

                pc.상점변신 = poly;

                pc.setCurrentSprite(poly);

                SC_ChangeShape.send(pc, pc.getId(), poly, 70);

                SC_ChangeShape.broadcast(pc, pc.getId(), poly, 70);

                SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));

                SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));

                SC_WorldPutObject.broadcastPc(pc);

                pc.curePoison();

                ThreadPool
                        .getInstance()
                        .execute(
                                new MJDShopStorage(
                                        pc,
                                        false));

            } else if (newShopType == 1) {

                if (pc.isPrivateReady()
                        && pc.isPrivateShop()) {

                    return;
                }

                pc.setPrivateShop(false);

                pc.상점변신 = 0;

                SC_DoActionGFX.send(
                        pc.getNetConnection(),
                        pc.getId(),
                        ActionCodes.ACTION_Idle);

                SC_DoActionGFX.broadcast(
                        pc,
                        ActionCodes.ACTION_Idle);

                L1PolyMorph
                        .undoPolyPrivateShop(pc);

                ThreadPool
                        .getInstance()
                        .execute(
                                new MJDShopStorage(
                                        pc,
                                        true));
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static void handleEventTeleport(
            L1PcInstance pc) {

        if (pc.getLevel()
                < Config.YN_pclevel) {

            SC_SystemMessage.send(pc, "레벨 "
                                    + Config.YN_pclevel
                                    + " 부터 이용가능 합니다.");

            return;
        }

        final L1Boss boss =
                BossMonsterSpawnList.find(
                        pc.getBossYN());

        if (boss == null) {

            SC_SystemMessage.send(pc, "선택 딜레이가 지나 이용이 불가능 합니다(10초)");

            return;
        }

        final L1Map map =
                L1WorldMap
                        .getInstance()
                        .getMap(
                                (short) boss.getMap());

        final int x =
                boss.getX();

        final int y =
                boss.getY();

        int cx = 0;
        int cy = 0;

        int currentTry = 0;

        final int limitTry =
                100;

        do {

            cx =
                    x + (MJRnd.isBoolean()
                            ? MJRnd.next(5)
                            : -MJRnd.next(5));

            cy =
                    y + (MJRnd.isBoolean()
                            ? MJRnd.next(5)
                            : -MJRnd.next(5));

        } while (++currentTry < limitTry
                && !MJPoint.isValidPosition(
                        map,
                        cx,
                        cy));

        if (currentTry >= limitTry) {

            cx = x;
            cy = y;
        }

        pc.start_teleport(
                cx,
                cy,
                (short) boss.getMap(),
                pc.getHeading(),
                169,
                true,
                pc.getAutoHunt());
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
                        .checkItem(
                                needItem,
                                1)) {

                    SC_SystemMessage.send(pc, "인벤토리에 아이템이 존재하지 않습니다.");

                    return;
                }

                if (!pc.getInventory()
                        .consumeItem(
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
                    L1World
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

        final L1Party party =
                pc.getParty();

        if (party == null) {
            return;
        }

        for (L1PcInstance member :
                party.getMembers()) {

            if (member != null) {

                SC_ActionUI.send(member, bytes);
            }
        }
    }

    private static void handleTamCancel(
            L1PcInstance pc,
            String encoded) {

        if (encoded == null) {
            return;
        }

        if (pc.getNetConnection() == null
                || pc.getNetConnection()
                    .getAccount() == null) {

            SC_SystemMessage.send(pc, "계정 정보를 찾을 수 없습니다.");

            return;
        }

        final Account account =
                pc.getNetConnection()
                        .getAccount();

        if (account.getTamHandler() == null) {

            SC_SystemMessage.send(pc, "탐 정보를 찾을 수 없습니다.");

            return;
        }

        final L1Tam tam =
                account.getTamHandler();

        final int day =
                tam.Nexttam(
                        encoded);

        final int charObjId =
                tam.TamCharid(
                        encoded);

        if (day <= 0
                || charObjId <= 0) {

            SC_SystemMessage.send(pc, "예약 정보를 찾을 수 없습니다.");

            return;
        }

        if (!tam.isMyAccountChar(
                charObjId)) {

            SC_SystemMessage.send(pc, "같은 계정의 캐릭터 예약만 취소할 수 있습니다.");

            return;
        }

        final int itemId;

        if (day == 3) {
            itemId = 600226;
        } else if (day == 7) {
            itemId = 3000235;
        } else if (day == 30) {
            itemId = 600227;
        } else {
            return;
        }

        final L1ItemInstance item =
                pc.getInventory()
                        .storeItem(
                                itemId,
                                1);

        if (item == null) {
            return;
        }

        SC_ServerMessage.send(pc, 403, item.getName()
                                + " (1)");

        final boolean ok =
                tam.tamcancle(
                        encoded);

        tam.invalidateCache();

        if (!ok) {

            SC_SystemMessage.send(pc, "예약 취소 처리에 실패했습니다.");
        }

        SC_TamWindow.sendAsync(pc);
    }

    private static void handleSocialAction(
            L1PcInstance pc,
            int actionType,
            int code) {

        final ServerPacket packet =
                LIN.SC_SOCIAL_ACTION_NOTI.get(
                        pc.getId(),
                        actionType,
                        code);

        if (packet != null) {

            pc.sendPackets(
                    packet);

            pc.broadcastPacket(
                    packet);
        }
    }

    private static void handleCastleWarProclaim(
            L1PcInstance pc,
            int castleId) {

        try {

            MJCastleWarBusiness
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