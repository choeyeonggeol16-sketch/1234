package l1j.server.MJEffectSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.MJEffectSystem.MJEffectModel;
import l1j.server.MJEffectSystem.Realize.OnEffectThread;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class MJEffectModelLoader {
    private static final Logger LOG = Logger.getLogger(MJEffectModelLoader.class.getName());
    private static final MJEffectModelLoader INSTANCE = new MJEffectModelLoader();
    private static final int RELOAD_ORDER_KEY = 0x7EF00E11;

    private volatile Map<Integer, MJEffectModel> models = Collections.emptyMap();

    private MJEffectModelLoader() {
    }

    public static MJEffectModelLoader getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        if (CIOThreadCallback.currentShardIndex() >= 0) {
            reload();
        } else {
            INSTANCE.loadSnapshot();
        }
    }

    public static void reload() {
        CacheD.getInstance().submitDbJobOrdered(RELOAD_ORDER_KEY, INSTANCE::loadSnapshot);
    }

    public static void release() {
        OnEffectThread.cancelAll();
        INSTANCE.clear();
    }

    public void clear() {
        models = Collections.emptyMap();
    }

    private void loadSnapshot() {
        HashMap<Integer, MJEffectModel> next = new HashMap<>(32);
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("select * from tb_mjeffects");
             ResultSet rs = pstm.executeQuery()) {
            while (rs.next()) {
                MJEffectModel model = new MJEffectModel();
                model.setType(rs.getString("type"));
                model.setEffectId(rs.getInt("effectId"));
                model.setActionId(rs.getInt("actId"));
                model.setImpact(rs.getInt("impact"));
                model.setRange(rs.getInt("range"));
                model.setDelay(rs.getInt("delay"));
                model.setDamageMin(rs.getInt("dmgMin"));
                model.setDamageMax(rs.getInt("dmgMax"));
                model.setZPos(rs.getInt("zpos"));
                model.setRatio(rs.getInt("ratio"));
                if (!model.isSafe()) {
                    LOG.warning("Invalid MJEffect row skipped: effectId=" + model.getEffectId()
                            + " type=" + rs.getString("type")
                            + " range=" + model.getRange() + " impact=" + model.getImpact()
                            + " zpos=" + model.getZPos() + " delay=" + model.getDelay());
                    continue;
                }
                String ownerIds = rs.getString("owner_ids");
                if (ownerIds == null || ownerIds.isEmpty()) continue;
                String[] keys = ownerIds.split(",");
                for (int i = 0; i < keys.length; i++) {
                    try {
                        next.put(Integer.parseInt(keys[i].trim()), model);
                    } catch (NumberFormatException ex) {
                        LOG.log(Level.WARNING, "Invalid MJEffect owner_id: " + keys[i], ex);
                    }
                }
            }
            models = next;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "MJEffect snapshot load failed; previous snapshot retained", ex);
        }
    }

    public MJEffectModel get(int id) {
        return models.get(id);
    }
}
