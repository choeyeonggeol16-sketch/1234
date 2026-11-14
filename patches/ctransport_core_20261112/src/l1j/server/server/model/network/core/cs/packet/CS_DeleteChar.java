package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_DeleteCharOK;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_CommonNews;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.templates.L1CharName;

public final class CS_DeleteChar {
    private static final Logger LOG = Logger.getLogger(CS_DeleteChar.class.getName());
    private static final int ERROR_MISSING = -1;
    private static final int ERROR_ONLINE = -2;
    private static final int ERROR_SEALED = -3;
    private static final int ERROR_FAILED = -4;

    private CS_DeleteChar() {
    }

    public static void handle(CIOClient client, String name) {
        if (client == null || name == null || name.isEmpty() ||
                !client.isConnected() || client.getAccount() == null) return;
        final String account = client.getAccountName();
        if (account == null || account.isEmpty()) return;
        final L1CharName known = CharacterTable.getInstance().getCharName(name);
        if (known == null) {
            SC_CommonNews.send(client, "존재하지 않는 캐릭터 입니다.");
            return;
        }
        final int id = known.getId();
        try {
            CacheD.getInstance().submitDbJobOrdered(id, CacheD.accountOrderKey(account), () -> {
                int result = ERROR_FAILED;
                try {
                    if (client.isConnected() && account.equals(client.getAccountName())) {
                        result = performDelete(account, name);
                    }
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Character deletion failed: " + name, e);
                }
                final int status = result;
                if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                    if (!account.equals(client.getAccountName())) return;
                    switch (status) {
                        case ERROR_MISSING:
                            SC_CommonNews.send(client, "존재하지 않는 캐릭터 입니다.");
                            break;
                        case ERROR_ONLINE:
                            SC_CommonNews.send(client, "접속 중인 캐릭터는 삭제할 수 없습니다.");
                            break;
                        case ERROR_SEALED:
                            SC_CommonNews.send(client, "봉인된 아이템을 가진 캐릭터는 삭제할 수 없습니다.");
                            break;
                        case ERROR_FAILED:
                            SC_CommonNews.send(client, "캐릭터 삭제 처리에 실패했습니다.");
                            break;
                        default:
                            SC_DeleteCharOK.send(client, status);
                            break;
                    }
                })) {
                    LOG.warning("Character delete completion could not be delivered: " + name);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Character delete submission failed: " + name, e);
            SC_CommonNews.send(client, "캐릭터 삭제 처리가 지연되고 있습니다.");
        }
    }

    private static int performDelete(String account, String name) throws Exception {
        final L1PcInstance pc = CharacterTable.getInstance().restoreCharacter(name);
        if (pc == null || !account.equals(pc.getAccountName())) return ERROR_MISSING;
        for (L1PcInstance player : L1World.getInstance().getAllPlayers3()) {
            if (player != null && player.getId() == pc.getId()) return ERROR_ONLINE;
        }
        if (pc.getLevel() >= 1 && Config.DELETE_CHARACTER_AFTER_7DAYS) {
            int type = pc.getType();
            if (type < 32) {
                if (pc.isCrown()) pc.setType(32);
                else if (pc.isKnight()) pc.setType(33);
                else if (pc.isElf()) pc.setType(34);
                else if (pc.isWizard()) pc.setType(35);
                else if (pc.isDarkelf()) pc.setType(36);
                else if (pc.isDragonknight()) pc.setType(37);
                else if (pc.isBlackwizard()) pc.setType(38);
                else if (pc.isWarrior()) pc.setType(39);
                pc.setDeleteTime(new Timestamp(System.currentTimeMillis() + 86400000L));
            } else {
                if (pc.isCrown()) pc.setType(0);
                else if (pc.isKnight()) pc.setType(1);
                else if (pc.isElf()) pc.setType(2);
                else if (pc.isWizard()) pc.setType(3);
                else if (pc.isDarkelf()) pc.setType(4);
                else if (pc.isDragonknight()) pc.setType(5);
                else if (pc.isBlackwizard()) pc.setType(6);
                else if (pc.isWarrior()) pc.setType(7);
                pc.setDeleteTime(null);
            }
            new CharacterSnapshot(pc).write();
            return SC_DeleteCharOK.DELETE_CHAR_AFTER_7DAYS;
        }
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM character_items WHERE char_id=? AND bless>=128 LIMIT 1")) {
            ps.setInt(1, pc.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return ERROR_SEALED;
            }
        }
        CharacterTable.getInstance().deleteCharacter(account, name);
        BQSCharacterDataLoader.deleteCharacterBps(pc, true);
        return SC_DeleteCharOK.DELETE_CHAR_NOW;
    }
}
