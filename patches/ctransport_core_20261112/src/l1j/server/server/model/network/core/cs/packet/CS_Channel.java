package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_CharPass;
import l1j.server.server.appcenter.AppCenterService;

import l1j.server.server.model.network.core.sc.packet.SC_CharCraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.Account;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ReportTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.templates.L1ItemBookMark;

import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
public final class CS_Channel {

    public static final int DragonMenu = 0x06;
    public static final int MINI_MAP_SEND = 0x0b;
    public static final int MonsterKill = 0x2c;
    public static final int BOOKMARK_SAVE = 0x22;
    public static final int BOOKMARK_COLOR = 0x27;
    public static final int BOOKMARK_LOADING_SAVE = 0x28;
    public static final int EMBLEM = 0x2e;
    public static final int TELPORT = 0x30;
    public static final int 케릭터생성 = 0x2b;
    public static final int 파워북검색 = 0x13;
    public static final int 상인찾기 = 0x31;
    public static final int 상점개설횟수 = 0x39;
    public static final int 자동신고 = 0x00;
    public static final int 시세검색 = 0xff;
    public static final int 케릭터비번생성 = 0x0e;
    public static final int 케릭터비번변경 = 0x10;
    public static final int 케릭터비번인증 = 0x11;
    public static final int CHARNAME_CHANGED = 0x1A;

    public static final class BookmarkColorPayload {
        final int[] numIds;
        final String[] names;

        public BookmarkColorPayload(int[] numIds, String[] names) {
            this.numIds = numIds;
            this.names = names;
        }

        int size() {
            return numIds != null ? numIds.length : 0;
        }
    }

    private CS_Channel() {
    }

    public static void handle(
            CIOClient client,
            int type,
            int readD1,
            int readD2,
            int readH1,
            int readH2,
            int mid,
            String readString,
            String readString2,
            Object extra) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null) {
            switch (type) {
            case 케릭터생성:
                if (Config.CHAR_PASSWORD
                        && (client.getAccount().getCPW() == null
                        || client.getAccount().getCPW().isEmpty())) {

                    SC_CharPass.send(client, SC_CharPass._비번생성창);
                    return;
                }
                break;

            case 케릭터비번생성:
            case 케릭터비번변경:
            case 케릭터비번인증:
                break;

            default:
                return;
            }
        }

        switch (type) {
        case 케릭터비번생성:
            handleCharPasswordCreate(client, readString);
            return;

        case 케릭터비번변경:
            handleCharPasswordChange(client, readString, readString2);
            return;

        case 케릭터비번인증:
            handleCharPasswordAuth(client, readString);
            return;

        case 케릭터생성:
            SC_CharCraft.send(client);
            return;

        case 자동신고:
            handleAutoReport(pc, readD1);
            return;

        case 시세검색:
            handleMarketSearch(pc, readString);
            return;

        case 상인찾기:
            handleFindMerchant(pc, readString);
            return;

        case 상점개설횟수:
            handleShopOpenCount(pc);
            return;

        case BOOKMARK_COLOR:
            handleBookmarkColor(pc, (BookmarkColorPayload) extra);
            return;

        case BOOKMARK_SAVE:
            handleBookmarkSave(pc, (byte[]) extra);
            return;

        case BOOKMARK_LOADING_SAVE:
            handleBookmarkLoadingSave(pc, readD1);
            return;

        case MonsterKill:
            if (pc != null) {
                pc.setMonsterkill(0);
            }
            return;

        case EMBLEM:
            handleEmblem(pc, readH1);
            return;

        case TELPORT:
            handleTownTeleport(pc, readH1, readH2);
            return;

        case MINI_MAP_SEND:
            handleMiniMapSend(pc, readString, readH1, readD1, readD2, mid);
            return;

        case CHARNAME_CHANGED:
            handleCharNameChanged(client, pc, readString, readString2);
            return;

        default:
            return;
        }
    }

    private static void handleCharPasswordCreate(CIOClient client, String password) {
        if (!Config.CHAR_PASSWORD || client == null) return;
        final Account account = client.getAccount();
        if (account == null || password == null
                || (account.getCPW() != null && !account.getCPW().isEmpty())) {
            client.kick();
            return;
        }
        persistCharPassword(client, account, null, password, true);
    }

    private static void handleCharPasswordChange(CIOClient client, String password,
                                                 String modPassword) {
        if (!Config.CHAR_PASSWORD || client == null) return;
        final Account account = client.getAccount();
        if (account == null || password == null || modPassword == null) {
            client.kick();
            return;
        }
        final String current = account.getCPW();
        if (current == null || current.isEmpty()) return;
        if (!current.equals(password)) {
            SC_CharPass.send(client, SC_CharPass._비번변경답변, false);
            return;
        }
        persistCharPassword(client, account, current, modPassword, false);
    }

    /** Account-order DB worker: commit BEFORE updating session memory/ACK.
     * Compare the previous database value so overlapping requests cannot
     * silently overwrite a password already committed by another session.
     */
    private static void persistCharPassword(CIOClient client, Account account,
                                            String expected, String replacement, boolean create) {
        final String login = account.getName();
        final var connection = client.getConnection();
        try {
            CacheD.getInstance().submitDbJobOrdered(login, () -> {
                boolean committed = false;
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement ps = con.prepareStatement(create
                             ? "UPDATE accounts SET CharPassword=? WHERE login=? AND (CharPassword IS NULL OR CharPassword='')"
                             : "UPDATE accounts SET CharPassword=? WHERE login=? AND BINARY CharPassword=BINARY ?")) {
                    ps.setString(1, replacement);
                    ps.setString(2, login);
                    if (!create) ps.setString(3, expected);
                    committed = ps.executeUpdate() == 1;
                } catch (SQLException e) {
                    java.util.logging.Logger.getLogger(CS_Channel.class.getName())
                            .log(java.util.logging.Level.SEVERE, "Character password DB update failed: " + login, e);
                }
                final boolean success = committed;
                CIOThreadCallback.getInstance().completeSession(client, () -> {
                    if (client.getAccount() != account) return;
                    // A session changed while the DB worker was running.
                    // The successful DB write is authoritative, but an obsolete
                    // completion must not replace a newer in-memory password.
                    final String observed = account.getCPW();
                    final boolean stillExpected = create
                            ? observed == null || observed.isEmpty()
                            : expected.equals(observed);
                    if (success && stillExpected) {
                        account.setCPW(replacement);
                        if (create) SC_CharPass.send(client, SC_CharPass._비번생성완료창);
                        else SC_CharPass.send(client, SC_CharPass._비번변경답변, true);
                    } else if (create) {
                        client.close();
                    } else {
                        SC_CharPass.send(client, SC_CharPass._비번변경답변, false);
                    }
                }, failure -> {
                    if (failure == null) return;
                    java.util.logging.Logger.getLogger(CS_Channel.class.getName())
                            .log(java.util.logging.Level.WARNING,
                                    "Character password completion unavailable: " + login, failure);
                    // The DB may already be committed. A session whose owner
                    // callback was lost must not keep authenticating against
                    // an obsolete in-memory password until reconnect.
                    if (success && client.isConnected() && client.getAccount() == account
                            && client.getConnection() == connection) client.close();
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            if (create) client.close();
            else SC_CharPass.send(client, SC_CharPass._비번변경답변, false);
        }
    }

    private static void handleCharPasswordAuth(CIOClient client, String password) {
        if (!Config.CHAR_PASSWORD || client == null) {
            return;
        }

        final Account account = client.getAccount();
        final String storedPassword = account != null ? account.getCPW() : null;

        if (password == null || storedPassword == null || storedPassword.isEmpty()) {
            client.close();
            return;
        }

        if (!storedPassword.equals(password)) {
            final int failureCount = client.incSecondPasswordFailureCount();

            if (failureCount >= Config.CHAR_PASSWORD_MAXIMUM_FAILCOUNT) {
                client.close();
            } else {
                SC_CharPass.sendFailedPassword(client, 
                        failureCount,
                        Config.CHAR_PASSWORD_MAXIMUM_FAILCOUNT);
            }

            return;
        }

        client.resetSecondPasswordFailureCount();

        final String targetChar = client.getLastSelectedCharName();
        if (targetChar != null && !targetChar.isEmpty()) {
            client.setLastSelectedCharName(null);
            CS_EnterWorld.doEnterWorld(targetChar, client);
        }
    }

    private static void handleAutoReport(L1PcInstance pc, int objid) {
        if (pc == null || pc.getNetConnection() == null) return;
        final L1Object obj = L1World.getInstance().findObject(objid);
        if (!(obj instanceof L1PcInstance)) return;
        final L1PcInstance target = (L1PcInstance) obj;
        if (target.isGm()) {
            SC_SystemMessage.send(pc, "신고 대상이 존재하지 않습니다.");
            return;
        }
        if (!pc.isReport()) {
            SC_ServerMessage.send(pc, 1021);
            return;
        }
        final String targetName = target.getName();
        final String reporterName = pc.getName();
        final CIOClient socket = pc.getNetConnection();
        final int ownerId = pc.getId();
        try {
            CacheD.getInstance().submitDbJobOrdered(ownerId, () -> {
                boolean inserted = false;
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement ps = con.prepareStatement(
                         "SELECT object_name FROM report WHERE object_name=? LIMIT 1")) {
                    ps.setString(1, targetName);
                    boolean already;
                    try (ResultSet rs = ps.executeQuery()) {
                        already = rs.next();
                    }
                    if (!already) {
                        ReportTable.getInstance().reportUpdate(targetName, reporterName,
                                new Timestamp(System.currentTimeMillis()));
                        inserted = true;
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("Report DB save failed: " + targetName, ex);
                }
                final boolean notifyNew = inserted;
                CIOThreadCallback.getInstance().completeSession(socket, () -> {
                    if (socket.getActiveChar() != pc) return;
                    if (notifyNew) SC_ServerMessage.send(pc, 1019);
                    for (L1PcInstance gm : L1World.getInstance().getAllPlayers()) {
                        if (gm.isGm()) {
                            SC_SystemMessage.send(gm, "\\fY 신 고 자 : " + reporterName);
                            SC_SystemMessage.send(gm, "\\fY 피신고자 : " + targetName);
                            SC_SystemMessage.send(gm, "\\fY해당 유저를 소환하여 취조하길 바랍니다.");
                        }
                    }
                    pc.startReportDeley();
                });
            });
        } catch (RuntimeException ex) {
            SC_SystemMessage.send(pc, "신고 처리가 지연되고 있습니다.");
        }
    }

    private static void handleMarketSearch(L1PcInstance pc, String itemname) {
        if (pc == null || itemname == null) return;
        // /시세 searches substrings across actual open shops, not item templates.
        AppCenterService.registerMarketSearch(pc,itemname);
    }

    private static final int[] MERCHANT_DX = {0,1,1,1,0,-1,-1,-1};
    private static final int[] MERCHANT_DY = {-1,-1,0,1,1,1,0,-1};

    private static void handleFindMerchant(L1PcInstance pc, String name) {
        if (pc == null || name == null || pc.getMapId()!=800 || pc.isDead()
                || pc.isGhost() || pc.isItemTransferBusy() || pc.isTradeResolving()) return;
        String seller=name.strip();
        if (seller.isEmpty() || seller.length()>32) return;
        final L1PcInstance merchant=L1World.getInstance().getPlayer(seller);
        final L1Object target;
        if (merchant!=null && merchant!=pc && merchant.getMapId()==800
                && merchant.isPrivateShop()) {
            target=merchant;
        } else {
            // Preserve the existing NPC merchant-search behavior as well.
            L1NpcShopInstance npc=L1World.getInstance().getShopNpc(seller);
            if (npc==null || npc.getMapId()!=800 || npc.getState()!=1) {
                SC_SystemMessage.send(pc,"상인찾기 : 현재 판매 중인 상인이 없습니다.");
                return;
            }
            target=npc;
        }
        // Do not use a random +/-1 tile: that can hit a blocked wall,
        // an occupied merchant tile, or a monster reservation.
        final int x=target.getX(), y=target.getY();
        final l1j.server.server.model.map.L1Map map=target.getMap();
        final l1j.server.server.model.character.TileOccupancy.TileOccupancy occ=
                l1j.server.server.model.character.TileOccupancy.TileOccupancy.getInstance();
        for(int n=0;n<8;n++){
            int nx=x+MERCHANT_DX[n],ny=y+MERCHANT_DY[n];
            if(!map.isInMap(nx,ny) || !map.isPassable(nx,ny))continue;
            if(occ.getOccupier(800,nx,ny)!=0 || occ.getReservedBy(800,nx,ny)!=0)continue;
            pc.setFindMerchantId(target.getId());
            pc.start_teleport(nx,ny,800,pc.getHeading(),169,false,false);
            return;
        }
        SC_SystemMessage.send(pc,"상인찾기 : 상인 주변에 빈자리가 없습니다.");
    }

    private static void handleShopOpenCount(L1PcInstance pc) {
        if (pc == null
                || pc.getNetConnection() == null
                || pc.getNetConnection().getAccount() == null) {

            return;
        }

        SC_PacketBox.send(pc, 
                        SC_PacketBox.상점개설횟수,
                        pc.getNetConnection().getAccount().Shop_open_count);
    }

    private static void handleBookmarkColor(L1PcInstance pc, BookmarkColorPayload payload) {
        if (pc == null || payload == null || payload.size() == 0) return;
        final CIOClient client = pc.getNetConnection();
        if (client == null) return;
        final int ownerId = pc.getId();
        final int[] ids = new int[payload.size()];
        final String[] names = new String[ids.length];
        final L1BookMark[] books = new L1BookMark[ids.length];
        int count = 0;
        for (int i = 0; i < ids.length; i++) {
            final int numId = payload.numIds[i];
            for (L1BookMark book : pc.getBookMarkArray()) {
                if (book.getNumId() == numId) {
                    ids[count] = book.getId();
                    names[count] = payload.names[i];
                    books[count] = book;
                    count++;
                    break;
                }
            }
        }
        if (count == 0) return;
        final int size = count;
        final var connection = client.getConnection();
        try {
            CacheD.getInstance().submitDbJobOrdered(ownerId, () -> {
                try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                    con.setAutoCommit(false);
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE character_teleport SET name=? WHERE id=? AND char_id=?")) {
                        for (int i = 0; i < size; i++) {
                            ps.setString(1, names[i]);
                            ps.setInt(2, ids[i]);
                            ps.setInt(3, ownerId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        con.commit();
                    } catch (SQLException ex) {
                        try { con.rollback(); } catch (SQLException roll) { ex.addSuppressed(roll); }
                        throw ex;
                    }
                } catch (SQLException ex) {
                    java.util.logging.Logger.getLogger(CS_Channel.class.getName())
                            .log(java.util.logging.Level.SEVERE,
                                    "Bookmark name update failed: " + ownerId, ex);
                    CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (client.getActiveChar() == pc)
                            SC_SystemMessage.send(pc, "북마크 이름 저장에 실패했습니다.");
                    });
                    return;
                }
                // A successful queue admission is NOT a successful commit.
                // Apply only on the socket's CIO owner after JDBC commit.
                CIOThreadCallback.getInstance().completeSession(client, () -> {
                    if (client.getActiveChar() != pc) return;
                    final L1BookMark[] current = pc.getBookMarkArray();
                    for (int i = 0; i < size; i++) {
                        // A bookmark removed while SQL was in flight must not
                        // be resurrected through its old Java object reference.
                        for (L1BookMark book : current) {
                            if (book == books[i]) {
                                book.setName(names[i]);
                                break;
                            }
                        }
                    }
                }, failure -> {
                    if (failure != null && client.isConnected()
                            && client.getConnection() == connection
                            && client.getActiveChar() == pc) client.close();
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            SC_SystemMessage.send(pc, "북마크 이름 저장이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static void handleBookmarkSave(L1PcInstance pc, byte[] data) {
        if (pc == null || data == null || data.length <= 0) {
            return;
        }

        int pos = 0;

        // 기존 runImpl()의 첫 readC() 버림.
        if (pos >= data.length) {
            return;
        }
        pos++;

        final int size = pc._bookmarks.size();

        for (int i = 0; i < size; i++) {
            if (pos >= data.length) {
                return;
            }

            final int num = data[pos++] & 0xFF;
            pc._bookmarks.get(i).setTemp_id(num);
        }

        pc._speedbookmarks.clear();

        for (int i = 0; i < 5; i++) {
            if (pos >= data.length) {
                return;
            }

            final int num = data[pos++] & 0xFF;

            if (num == 255) {
                return;
            }

            if (pc._bookmarks.size() - 1 < num) {
                return;
            }

            pc._bookmarks.get(num).setSpeed_id(i);
            pc._speedbookmarks.add(pc._bookmarks.get(num));
        }
    }

    private static void handleBookmarkLoadingSave(L1PcInstance pc, int itemId) {
        if (pc == null) {
            return;
        }

        if (pc.getBookMarkSize() <= 0) {
            SC_ServerMessage.send(pc, 2963);
            return;
        }

        final int totalCount = pc.getInventory().getSize();

        if (pc.getInventory().getWeight100() > 82 || totalCount > 180) {
            SC_SystemMessage.send(pc, "인벤이 가득차서 기억구슬을 생성할 수 없습니다.");
            return;
        }

        final L1ItemInstance useItem = pc.getInventory().getItem(itemId);

        if (useItem != null) {
            기억저장구슬(pc, useItem);
        }
    }

    private static void handleEmblem(L1PcInstance pc, int emblemStatus) {
        if (pc == null) {
            return;
        }

        if (pc.getClanRank() != 4 && pc.getClanRank() != 10) {
            return;
        }

        final L1Clan clan = pc.getClan();

        if (clan != null) {
            clan.setEmblemStatus(emblemStatus);
            ClanTable.getInstance().updateClan(clan);

            for (L1PcInstance member : clan.getOnlineClanMember()) {
                if (member != null) {
                    SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS, emblemStatus);
                }
            }
        }
    }

    private static void handleTownTeleport(
            L1PcInstance pc,
            int mapIndex,
            int point) {

        if (pc == null) {
            return;
        }

        int locx = pc.getX();
        int locy = pc.getY();

        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (mapIndex == 1) {
            if (point == 0) {
                locx = 34079 + rnd.nextInt(12);
                locy = 33136 + rnd.nextInt(15);
            } else if (point == 1) {
                locx = 33970 + rnd.nextInt(10);
                locy = 33243 + rnd.nextInt(14);
            } else if (point == 2) {
                locx = 33925 + rnd.nextInt(14);
                locy = 33351 + rnd.nextInt(9);
            }

        } else if (mapIndex == 2) {
            if (point == 0) {
                locx = 32615 + rnd.nextInt(11);
                locy = 32719 + rnd.nextInt(7);
            } else if (point == 1) {
                locx = 32621 + rnd.nextInt(9);
                locy = 32788 + rnd.nextInt(13);
            }

        } else if (mapIndex == 3) {
            if (point == 0) {
                locx = 33501 + rnd.nextInt(11);
                locy = 32765 + rnd.nextInt(9);
            } else if (point == 1) {
                locx = 33440 + rnd.nextInt(11);
                locy = 32784 + rnd.nextInt(11);
            }

        } else if (mapIndex == 4) {
            final int[][] marketCoords = {
                    {32844, 32883}, {32801, 32882}, {32756, 32882},
                    {32743, 32927}, {32740, 32972}, {32800, 32971},
                    {32844, 32971}, {32846, 32928}, {32797, 32927}
            };

            if (point >= 0 && point < marketCoords.length) {
                locx = marketCoords[point][0] + rnd.nextInt(2);
                locy = marketCoords[point][1] + rnd.nextInt(2);
            }
        }

        pc.start_teleport(
                locx, locy,
                pc.getMapId(),
                pc.getHeading(),
                169,
                true,
                false);

        SC_PacketBox.send(pc, SC_PacketBox.TOWN_TELEPORT, pc);
    }

    private static void handleMiniMapSend(
            L1PcInstance pc,
            String targetName,
            int mapid,
            int x,
            int y,
            int mid) {

        if (pc == null) {
            return;
        }

        final L1PcInstance target = L1World.getInstance().getPlayer(targetName);

        if (target == null) {
            SC_ServerMessage.send(pc, 1782);

        } else if (pc == target) {
            SC_ServerMessage.send(pc, 1785);

        } else {
            SC_ServerMessage.send(target, 1784, pc.getName());
            SC_PacketBox.send(target, 
                    SC_PacketBox.MINI_MAP_SEND,
                    pc.getName(),
                    mapid,
                    x,
                    y,
                    mid);

            SC_ServerMessage.send(pc, 1783, target.getName());
        }
    }

    private static void handleCharNameChanged(
            CIOClient client,
            L1PcInstance pc,
            String sourceName,
            String destinationName) {

        if (pc == null) {
            return;
        }

        final int result = SC_ChangeCharName.doChangeCharName(
                pc,
                sourceName,
                destinationName);

        if (result >= 0) {
            SC_ChangeCharName.sendResult(client, result == 1);
        }

        final Account acc = client.getAccount();

        if (acc != null) {
            CS_CommonClick.sendCharAmountAndPacks(client);
        }
    }

    private static void 기억저장구슬(
            L1PcInstance pc,
            L1ItemInstance useItem) {

        final ArrayList<L1BookMark> books = pc._bookmarks;
        final L1ItemInstance item = ItemTable.getInstance().createItem(700023);

        if (item != null) {
            for (int i = 0; i < books.size(); i++) {
                L1ItemBookMark.addBookmark(pc, item, books.get(i));
            }

            pc.getInventory().storeItem(item);
            pc.getInventory().removeItem(useItem, 1);
        }
    }
}
