package l1j.server.server.model.character.transform;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;

/** Immutable lookup snapshot of the existing polymorph table. */
public final class CTransformDB {
    private static final Logger LOG = Logger.getLogger(CTransformDB.class.getName());
    private static volatile boolean polyEvent;

    private static final String[] RANKING = {
        "rangking prince male", "rangking prince female", "rangking knight male", "rangking knight female",
        "rangking elf male", "rangking elf female", "rangking wizard male", "rangking wizard female",
        "rangking darkelf male", "rangking darkelf female", "rangking dragonknight male", "rangking dragonknight female",
        "rangking illusionist male", "rangking illusionist female", "rangking warrior male", "rangking warrior female"
    };
    private static final String[] RANKING_ALTERNATE = {
        "rangking prince male1", "rangking prince female1", "rangking knight male1", "rangking knight female1",
        "rangking elf male1", "rangking elf female1", "rangking wizard male1", "rangking wizard female1",
        "rangking darkelf male1", "rangking darkelf female1", "rangking dragonknight male1", "rangking dragonknight male1",
        "rangking illusionist male1", "rangking illusionist male1", "rangking warrior male1", "rangking warrior female1"
    };
    private static final String[] MYTHIC = {
        "royal male r", "royal female r", "knight male r", "knight female r",
        "elf male r", "elf female r", "wizard male r", "wizard female r",
        "darkelf male r", "darkelf female r", "dragon r", "dragon r",
        "dopel male", "dopel female", "my warrior", "my warrior"
    };

    private final Map<String, CTransform> byName;
    private final TIntObjectHashMap<CTransform> bySprite;
    private final TIntHashSet rankingSprites;

    private CTransformDB(Connection con) throws SQLException {
        Map<String, CTransform> names = new HashMap<>();
        var sprites = new TIntObjectHashMap<CTransform>(512);
        var ranks = new TIntHashSet(64);
        try (var query = con.prepareStatement("SELECT * FROM polymorphs"); var rows = query.executeQuery()) {
            while (rows.next()) {
                int id = rows.getInt("id");
                int sprite = rows.getInt("polyid");
                if (id <= 0 || sprite <= 0) throw new SQLException("Invalid transform definition " + id + "/" + sprite);
                String name = rows.getString("name");
                var common = new CTransformCommonData(id, name, sprite, rows.getInt("minlevel"),
                        rows.getInt("cause"), parseOption(rows.getString("option")));
                var combat = new CTransformCombatData(rows.getInt("weaponequip"), rows.getInt("armorequip"),
                        rows.getBoolean("isSkillUse"));
                var definition = new CTransform(common, combat);
                // Multiple names and rows may refer to the same model. Keep the original lookup precedence.
                if (name != null) {
                    names.put(name, definition);
                    if (name.startsWith("rangking")) ranks.add(sprite);
                }
                sprites.put(sprite, definition);
            }
        }
        if (sprites.isEmpty()) throw new SQLException("Empty transform catalog");
        byName = Map.copyOf(names);
        bySprite = sprites;
        rankingSprites = ranks;
    }

    private static final class Holder {
        private static volatile CTransformDB current = load();
    }

    private static CTransformDB load() {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            return new CTransformDB(con);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load transform definitions", failure);
        }
    }

    public static CTransformDB getInstance() {
        return Holder.current;
    }

    public static synchronized boolean reload() {
        try {
            Holder.current = load();
            return true;
        } catch (IllegalStateException failure) {
            LOG.log(Level.SEVERE, "Transform reload rejected; retained previous snapshot", failure);
            return false;
        }
    }

    public CTransform find(int sprite) { return bySprite.get(sprite); }
    public CTransform find(String name) { return name == null ? null : byName.get(name); }
    public boolean isRankingPoly(int sprite) { return rankingSprites.contains(sprite); }
    public boolean isPolyEvent() { return polyEvent; }
    public void setPolyEvent(boolean enabled) { polyEvent = enabled; }

    public static String resolveClassName(L1PcInstance pc, String name) {
        if (name == null || pc.getType() < 0 || pc.getType() > 7) return name;
        boolean male = pc.get_sex() == 0;
        int index = pc.getType() * 2 + (male ? 0 : 1);
        if (name.equalsIgnoreCase("ranking class polymorph")) return RANKING[index];
        if (name.equalsIgnoreCase("ranking class polymorph1")) return RANKING_ALTERNATE[index];
        if (!name.equalsIgnoreCase("myth class polymorph")) return name;
        if (pc.getType() == 2 && pc.getCurrentSpriteId() == (male ? 21070 : 21066))
            return male ? "elf old male" : "elf old female";
        return MYTHIC[index];
    }

    private static int parseOption(String option) {
        if (option == null || option.isEmpty()) return 0;
        return switch (option.toLowerCase(java.util.Locale.ROOT)) {
            case "poly_scroll" -> 1;
            case "poly_ringmaster" -> 2;
            case "poly_event" -> 3;
            case "poly_class" -> 4;
            case "poly_ev" -> 5;
            default -> 0;
        };
    }
}
