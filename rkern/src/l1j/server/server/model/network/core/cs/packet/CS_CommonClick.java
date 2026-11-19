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
import l1j.server.server.model.network.core.sc.packet.SC_CharPass;


import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
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
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.network.core.sc.packet.SC_CharPacks;

import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;

import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
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

    private static final class DeletedCharacter {
        final String name;
        final String clanName;

        DeletedCharacter(String name, String clanName) {
            this.name = name;
            this.clanName = clanName;
        }
    }

    private static boolean canSendCharacterList(CIOClient client, Account account) {
        return client.isConnected() && client.getAccount() == account
                && client.getActiveChar() == null
                && client.getConnectionState() == ConnectionState.AUTHENTICATED;
    }

    public CS_CommonClick(CIOClient client) {
        if (client == null || client.getAccount() == null || !client.isConnected()) return;
        final Account account = client.getAccount();
        final String accountName = account.getName();
        final int ownerShard = client.getConnection().affinityShard();
        try {
            CacheD.getInstance().submitDbJobOrdered(accountName,
                    () -> loadCommonClick(client, account, accountName, ownerShard));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "CacheD common click submit failed: " + accountName, e);
            client.close();
        }
    }

    private static void loadCommonClick(CIOClient client, Account account,
                                        String accountName, int ownerShard) {
        if (!canSendCharacterList(client, account)) return;
        final ArrayList<DeletedCharacter> deleted = deleteExpired(accountName);
        try {
            account.tamUpdate(account);
            account.getTamHandler().refreshCacheOnDbWorker();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "TAM update failed: " + accountName, e);
        }

        final List<CharRow> rows;
        try {
            rows = loadCharRows(accountName);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Character list load failed: " + accountName, e);
            CIOThreadCallback.getInstance().completeSession(client, () -> {
                removeDeletedClanMembers(deleted);
                if (client.getAccount() == account && client.isConnected()) client.close();
            });
            return;
        }

        if (!CIOThreadCallback.getInstance().completeSession(client,
                () -> completeCommonClick(client, account, deleted, rows))) {
            LOG.warning("Common click completion rejected: " + accountName);
        }
    }

    private static void completeCommonClick(CIOClient client, Account account, List<DeletedCharacter> deleted, List<CharRow> rows) {
        removeDeletedClanMembers(deleted);
        if (!canSendCharacterList(client, account)) return;
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

    private static void removeDeletedClanMembers(List<DeletedCharacter> deleted) {
        for (int i = 0, size = deleted.size(); i < size; i++) {
            DeletedCharacter entry = deleted.get(i);
            if (entry.clanName != null && !entry.clanName.isEmpty()) {
                L1Clan clan = L1World.getInstance().findClan(entry.clanName);
                if (clan != null) clan.removeClanMember(entry.name);
            }
        }
    }

    private static ArrayList<DeletedCharacter> deleteExpired(String accountName) {
        ArrayList<DeletedCharacter> expired = new ArrayList<>();
        final long now = System.currentTimeMillis();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT char_name,Clanname,DeleteTime FROM characters WHERE account_name=? ORDER BY objid")) {
            ps.setString(1, accountName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp deleteTime = rs.getTimestamp("DeleteTime");
                    if (deleteTime != null && now - deleteTime.getTime() >= DELETE_AFTER_MILLIS) {
                        expired.add(new DeletedCharacter(rs.getString("char_name"), rs.getString("Clanname")));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Expired character scan failed: " + accountName, e);
            return new ArrayList<>();
        }
        ArrayList<DeletedCharacter> deleted = new ArrayList<>(expired.size());
        for (int i = 0, size = expired.size(); i < size; i++) {
            DeletedCharacter entry = expired.get(i);
            try {
                CharacterTable.getInstance().deleteCharacter(accountName, entry.name);
                deleted.add(entry);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Expired character delete failed: " + entry.name, e);
            }
        }
        return deleted;
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

    private static void sendCharPack(CIOClient client, CharRow row) {
        SC_CharPacks.send(client, 
                row.name, row.clanName, row.type, row.sex, row.lawful,
                row.hp, row.mp, row.ac, row.level, row.str, row.dex,
                row.con, row.wis, row.cha, row.intel, row.accessLevel, row.birth);
    }

    public static void sendCharPacks(CIOClient client) {
        submitCharList(client, false);
    }

    /** Account-ordered DB read replaces a synchronous countCharacters() on the
     * CIO owner. Count and packets use the SAME snapshot on the SAME session.
     */
    public static void sendCharAmountAndPacks(CIOClient client) {
        submitCharList(client, true);
    }

    private static void submitCharList(CIOClient client, boolean includeAmount) {
        if (client == null || client.getAccount() == null || !client.isConnected()) return;
        final Account account = client.getAccount();
        if (!canSendCharacterList(client, account)) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account.getName(), () -> {
                try {
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
