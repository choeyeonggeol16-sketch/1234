package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ShopSellList2;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

public final class L1Search1 implements L1CommandExecutor {
    private static final Logger LOG = Logger.getLogger(L1Search1.class.getName());
    private static final int SHOP_ORDER_KEY = 0x3fffff08;
    private static final String[] QUERY = {
            "SELECT item_id, name, name_id FROM etcitem WHERE name LIKE ?",
            "SELECT item_id, name, name_id FROM weapon WHERE name LIKE ?",
            "SELECT item_id, name, name_id FROM armor WHERE name LIKE ?",
            "SELECT npcid, name, note FROM npc WHERE name LIKE ?",
            "SELECT polyid, name, id FROM polymorphs WHERE name LIKE ?",
            "SELECT gfxid, name, note FROM npc WHERE name LIKE ?"
    };

    private L1Search1() {}

    public static L1CommandExecutor getInstance() {
        return new L1Search1();
    }

    @Override
    public void execute(L1PcInstance pc, String cmdName, String arg) {
        if (pc == null || arg == null) return;
        final int type;
        final String pattern;
        try {
            StringTokenizer tok = new StringTokenizer(arg);
            type = Integer.parseInt(tok.nextToken());
            pattern = "%" + tok.nextToken() + "%";
            if (type < 0 || type >= QUERY.length) {
                throw new IllegalArgumentException("Invalid item search category");
            }
        } catch (RuntimeException e) {
            SC_SystemMessage.send(pc, ".검색 [0~5] [name]을 입력 해주세요.");
            SC_SystemMessage.send(pc, "0=잡템, 1=무기, 2=갑옷, 3=엔피시, 4=변신, 5=엔피시(gfxid)");
            return;
        }

        final CIOClient owner = pc.getNetConnection();
        if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(SHOP_ORDER_KEY, pc.getId(), () -> {
                final ArrayList<String> messages = new ArrayList<>();
                boolean success = false;
                try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                    boolean oldAutoCommit = con.getAutoCommit();
                    try {
                        con.setAutoCommit(false);
                        try (PreparedStatement clear = con.prepareStatement(
                                "DELETE FROM shop WHERE npc_id=8")) {
                            clear.executeUpdate();
                        }
                        try (PreparedStatement query = con.prepareStatement(QUERY[type]);
                             PreparedStatement insert = con.prepareStatement(
                                    "INSERT INTO shop SET npc_id=?,item_id=?,order_id=?,selling_price=?,pack_count=?,purchasing_price=?,enchant=?,time_limit=?")) {
                            query.setString(1, pattern);
                            try (ResultSet rs = query.executeQuery()) {
                                while (rs.next()) {
                                    String itemId = rs.getString(1);
                                    String itemName = rs.getString(2);
                                    String description = rs.getString(3);
                                    messages.add("[" + itemId + "]--[" + itemName + "]--" + description);
                                    insert.setInt(1, 8);
                                    insert.setString(2, itemId);
                                    insert.setInt(3, 0);
                                    insert.setInt(4, 1);
                                    insert.setInt(5, 0);
                                    insert.setInt(6, -1);
                                    insert.setInt(7, 0);
                                    insert.setString(8, "false");
                                    insert.addBatch();
                                }
                            }
                            insert.executeBatch();
                        }
                        con.commit();
                        success = true;
                    } catch (Exception e) {
                        try { con.rollback(); } catch (Exception rollbackFailure) {
                            e.addSuppressed(rollbackFailure);
                        }
                        throw e;
                    } finally {
                        try { con.setAutoCommit(oldAutoCommit); }
                        catch (Exception e) { LOG.log(Level.WARNING, "Search transaction restore failed", e); }
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "GM item search failed", e);
                }
                if (success) {
                    try {
                        ShopTable.reload();
                    } catch (RuntimeException e) {
                        success = false;
                        LOG.log(Level.WARNING, "GM item search shop reload failed", e);
                    }
                }
                final boolean completed = success;
                if (!CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
                    if (!owner.isConnected() || owner.getActiveChar() != pc
                            || pc.getNetConnection() != owner) return;
                    if (!completed) {
                        SC_SystemMessage.send(pc, "검색 상점 갱신에 실패했습니다.");
                        return;
                    }
                    for (int i = 0, size = messages.size(); i < size; i++) {
                        SC_SystemMessage.send(pc, messages.get(i));
                    }
                    SC_SystemMessage.send(pc, "총 [" + messages.size() + "]개 검색이 되었습니다.");
                    SC_ShopSellList2.send(pc);
                })) {
                    LOG.fine("GM item search response discarded: player disconnected");
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "GM item search CacheD submission failed", e);
            SC_SystemMessage.send(pc, "검색 요청을 처리할 수 없습니다.");
        }
    }
}
