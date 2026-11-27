/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.model.network.core.cs.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
import l1j.server.server.model.network.core.sc.packet.SC_CharPacks;
import l1j.server.server.model.network.core.sc.packet.SC_CharPass;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.storage.CacheD;

public class CS_CommonClick {
	
    private static final Logger LOG = Logger.getLogger(CS_CommonClick.class.getName());
    private static final String C_COMMON_CLICK = "[C] C_CommonClick";
    private static final long DELETE_AFTER_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String CHAR_LIST_SQL =
            "SELECT objid,char_name,Clanname,Type,Sex,Lawful,CurHp,CurMp,level,Ac," +
            "Str,Dex,Con,Wis,Cha,Intel,AccessLevel,BirthDay FROM characters " +
            "WHERE account_name=? ORDER BY objid";

    private static final class CharRow {
        int objectId;
        String name;
        String clanName;
        int type;
        byte sex;
        int lawful;
        int hp;
        int mp;
        int level;
        int ac;
        int str;
        int dex;
        int con;
        int wis;
        int cha;
        int intel;
        int accessLevel;
        int birth;
    }

    private static final class ExpiredCharacter {
        final int objectId;
        final String name;
        final String clanName;

        ExpiredCharacter(int objectId, String name, String clanName) {
            this.objectId = objectId;
            this.name = name;
            this.clanName = clanName;
        }
    }

    private static boolean canSendCharacterList(CUserSocket client, Account account) {
        return client.isConnected() && client.getAccount() == account
                && client.getActiveChar() == null
                && client.getConnectionState() == CConnectionState.AUTHENTICATED;
    }

    public CS_CommonClick(CUserSocket client) {
        if (client == null) return;
        final Account account = client.getAccount();
        if (account == null || !canSendCharacterList(client, account)) return;
        final CUserSocket.LoginDbLoad request = client.tryBeginLoginDbLoad(CConnectionState.AUTHENTICATED, account);
        if (request == null) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account.getName(),
                    () -> loadCommonClick(client, account, request));
        } catch (RuntimeException failure) {
            finishCommonClick(client, request, account.getName(), failure);
        }
    }

    private static void loadCommonClick(CUserSocket client, Account account,
                                        CUserSocket.LoginDbLoad request) {
        if (!client.isCurrentLoginDbLoad(request)) {
            client.finishLoginDbLoad(request);
            return;
        }
        try {
            final List<ExpiredCharacter> expired = findExpired(account.getName());
            if (expired.isEmpty()) {
                readCommonClick(client, account, request);
                return;
            }
            final CacheD cache = CacheD.getInstance();
            final int accountKey = CacheD.accountOrderKey(account.getName());
            final CWorld world = CWorld.getInstance();
            final int ownerShard = client.affinityShard();
            // The scan owns only the account. Deletion must reserve the character too,
            // so it cannot wait for an older job that this account lane is blocking.
            for (ExpiredCharacter entry : expired) {
                cache.submitDbJobOrdered(entry.objectId, accountKey, () -> {
                    if (client.isCurrentLoginDbLoad(request))
                        deleteExpiredCharacter(account.getName(), entry, world, ownerShard);
                });
            }
            cache.submitDbJobOrdered(accountKey, () -> readCommonClick(client, account, request));
        } catch (SQLException | RuntimeException failure) {
            finishCommonClick(client, request, account.getName(), failure);
        }
    }

    private static void readCommonClick(CUserSocket client, Account account, CUserSocket.LoginDbLoad request) {
        if (!client.isCurrentLoginDbLoad(request)) {
            client.finishLoginDbLoad(request);
            return;
        }
        final String accountName = account.getName();
        try {
            final List<CharRow> rows = loadCharRows(accountName);
            try {
                account.tamUpdate(account);
                account.getTamHandler().refreshCacheOnDbWorker();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "TAM update failed: " + accountName, e);
            }
            CIOThreadCallback.getInstance().completeSession(client, () -> {
                if (client.isCurrentLoginDbLoad(request)) completeCommonClick(client, account, rows);
            }, failure -> finishCommonClick(client, request, accountName, failure));
        } catch (SQLException | RuntimeException failure) {
            finishCommonClick(client, request, accountName, failure);
        }
    }

    private static void finishCommonClick(
            CUserSocket client, CUserSocket.LoginDbLoad request, String account, Throwable failure) {
        try {
            if (failure != null && client.isCurrentLoginDbLoad(request)) {
                LOG.log(Level.WARNING, "Character list load failed: " + account, failure);
                client.close();
            }
        } finally {
            client.finishLoginDbLoad(request);
        }
    }

    private static void completeCommonClick(CUserSocket client, Account account, List<CharRow> rows) {
        SC_NewCreateItem.sendTamPoint(client);
        if (Config.CHAR_PASSWORD && (account.getCPW() == null || account.getCPW().isEmpty())) {
            SC_CharPass.send(client, SC_CharPass._케릭선택창진입3);
        }
        SC_CharAmount.send(client, rows.size(), account.getCharSlot());
        SC_CharPass.send(client, SC_CharPass._케릭선택창진입2);
        for (int i = 0, size = rows.size(); i < size; i++) {
            CharRow row = rows.get(i);
            RankBusiness.getInstance().noti(client, row.objectId);
            sendCharPack(client, row);
        }
        SC_CharPass.send(client, SC_CharPass._케릭선택창진입);
        SC_Unknown2.send(client, 0);
    }

    private static void deleteExpiredCharacter(
            String account, ExpiredCharacter entry, CWorld world, int ownerShard) {
        try {
            // Another queued account operation may have changed this row since the scan.
            if (!isStillExpired(account, entry)) return;
            CharacterTable.getInstance().deleteCharacter(account, entry.name);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Expired character delete failed: " + entry.name, e);
            return;
        }
        if (entry.clanName == null || entry.clanName.isEmpty()) return;
        CIOThreadCallback.getInstance().completeOnShard(ownerShard, () -> {
            if (CWorld.getInstance() != world) return;
            final L1Clan clan = world.findClan(entry.clanName);
            if (clan != null) clan.removeClanMember(entry.objectId);
        }, failure -> {
            if (failure != null) LOG.log(Level.WARNING, "Expired character clan cleanup failed: " + entry.name, failure);
        });
    }

    private static List<ExpiredCharacter> findExpired(String accountName) throws SQLException {
        final ArrayList<ExpiredCharacter> expired = new ArrayList<>();
        final long now = System.currentTimeMillis();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT objid,char_name,Clanname,DeleteTime FROM characters WHERE account_name=? ORDER BY objid")) {
            ps.setString(1, accountName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (hasExpired(rs.getTimestamp("DeleteTime"), now)) {
                        expired.add(new ExpiredCharacter(rs.getInt("objid"), rs.getString("char_name"),
                                rs.getString("Clanname")));
                    }
                }
            }
        }
        return expired;
    }

    private static boolean isStillExpired(String account, ExpiredCharacter entry) throws SQLException {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT DeleteTime FROM characters WHERE objid=? AND account_name=? AND char_name=?")) {
            ps.setInt(1, entry.objectId);
            ps.setString(2, account);
            ps.setString(3, entry.name);
            try (ResultSet rows = ps.executeQuery()) {
                return rows.next() && hasExpired(rows.getTimestamp(1), System.currentTimeMillis());
            }
        }
    }

    private static boolean hasExpired(Timestamp deleteTime, long now) {
        return deleteTime != null && now - deleteTime.getTime() >= DELETE_AFTER_MILLIS;
    }

    private static List<CharRow> loadCharRows(String accountName) throws SQLException {
        final ArrayList<CharRow> rows = new ArrayList<>(8);
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(CHAR_LIST_SQL)) {
            ps.setString(1, accountName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CharRow row = new CharRow();
                    row.objectId = rs.getInt("objid");
                    row.name = rs.getString("char_name");
                    row.clanName = rs.getString("Clanname");
                    row.type = rs.getInt("Type");
                    row.sex = rs.getByte("Sex");
                    row.lawful = rs.getInt("Lawful");
                    row.hp = Math.max(1, Math.min(32767, rs.getInt("CurHp")));
                    row.mp = Math.max(1, Math.min(32767, rs.getInt("CurMp")));
                    row.level = Config.CHARACTER_CONFIG_IN_SERVER_SIDE
                            ? Math.max(1, Math.min(127, rs.getInt("level"))) : 1;
                    row.ac = Math.max(-128, rs.getInt("Ac"));
                    row.str = rs.getByte("Str");
                    row.dex = rs.getByte("Dex");
                    row.con = rs.getByte("Con");
                    row.wis = rs.getByte("Wis");
                    row.cha = rs.getByte("Cha");
                    row.intel = rs.getByte("Intel");
                    row.accessLevel = rs.getShort("AccessLevel");
                    row.birth = rs.getInt("BirthDay");
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static void sendCharPack(CUserSocket client, CharRow row) {
        SC_CharPacks.send(client, 
                row.name, row.clanName, row.type, row.sex, row.lawful,
                row.hp, row.mp, row.ac, row.level, row.str, row.dex,
                row.con, row.wis, row.cha, row.intel, row.accessLevel, row.birth);
    }

    public static void sendCharPacks(CUserSocket client) {
        submitCharList(client, false);
    }

    /** Account-ordered DB read replaces a synchronous countCharacters() on the
     * CIO owner. Count and packets use the SAME snapshot on the SAME session.
     */
    public static void sendCharAmountAndPacks(CUserSocket client) {
        submitCharList(client, true);
    }

    private static void submitCharList(CUserSocket client, boolean includeAmount) {
        if (client == null || client.getAccount() == null || !client.isConnected()) return;
        final Account account = client.getAccount();
        if (!canSendCharacterList(client, account)) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account.getName(), () -> {
                try {
                    if (!canSendCharacterList(client, account)) return;
                    List<CharRow> rows = loadCharRows(account.getName());
                    if (!canSendCharacterList(client, account)) return;
                    if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (!canSendCharacterList(client, account)) return;
                        if (includeAmount) SC_CharAmount.send(client, rows.size(), account.getCharSlot());
                        for (int i = 0; i < rows.size(); i++) {
                            CharRow row = rows.get(i);
                            RankBusiness.getInstance().noti(client, row.objectId);
                            sendCharPack(client, row);
                        }
                    })) LOG.warning("Char packs completion rejected: " + account.getName());
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Char packs async load failed", e);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Char packs request rejected", e);
        }
    }

    public String getType() {
        return C_COMMON_CLICK;
    }
}
