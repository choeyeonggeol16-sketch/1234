package l1j.server.server.storage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.ToIntFunction;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.HpMpGrowthHistory;
import l1j.server.server.model.L1ReturnStatTemp;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;

/** Persist only the character fields changed by a confirmed stat operation. */
public final class CharacterStatReceipt {
    private CharacterStatReceipt() {}

    // Same fixed order as the reset packet and its detached allocation.
    private enum Stat {
        STR("Str", L1PcInstance::GetStatStr, L1PcInstance::GetOriginalStatStr),
        INT("Intel", L1PcInstance::GetStatInt, L1PcInstance::GetOriginalStatInt),
        WIS("Wis", L1PcInstance::GetStatWis, L1PcInstance::GetOriginalStatWis),
        DEX("Dex", L1PcInstance::GetStatDex, L1PcInstance::GetOriginalStatDex),
        CON("Con", L1PcInstance::GetStatCon, L1PcInstance::GetOriginalStatCon),
        CHA("Cha", L1PcInstance::GetStatCha, L1PcInstance::GetOriginalStatCha);

        final ToIntFunction<L1PcInstance> pure, original;
        final String whereStat, invest, readOlderProgression, investWithProgression;

        Stat(String column, ToIntFunction<L1PcInstance> pure, ToIntFunction<L1PcInstance> original) {
            this.pure = pure;
            this.original = original;
            // Legacy rows can have a recorded pure value below their original.
            // Character loading applies the same lower bounds.
            String base = "Base" + column;
            whereStat = " AND GREATEST(0," + base + ")=? AND GREATEST(0," + base + "," + column + ")=?";
            String guard = " WHERE objid=? AND account_name=? AND char_name=? AND Type=?"
                    + " AND COALESCE(ReturnStat,0)=0 AND ElixirStatus=? AND BonusStatus=?" + whereStat;
            invest = "UPDATE characters SET " + column + "=?,BonusStatus=?" + ("Dex".equals(column) ? ",Ac=?" : "")
                    + guard + " AND Exp>=?";
            readOlderProgression = "SELECT Exp,HighLevel,MaxHp,MaxMp,hp_mp_growth FROM characters"
                    + guard + " AND Exp<? FOR UPDATE";
            investWithProgression = "UPDATE characters SET " + column + "=?,BonusStatus=?,"
                    + "Exp=?,level=?,HighLevel=GREATEST(HighLevel,?),MaxHp=?,MaxMp=?,hp_mp_growth=?,Ac=? WHERE objid=?";
        }
    }
    private static final Stat[] STATS = Stat.values();

    /** Caller holds the character's state lock and has reserved its receipt. */
    public static void stageInvestment(L1InventoryTransaction tx, L1PcInstance pc, int packetStat) {
        Stat stat = switch (packetStat) {
            case SC_CHAR_STAT.Stat_Str -> Stat.STR;
            case SC_CHAR_STAT.Stat_Int -> Stat.INT;
            case SC_CHAR_STAT.Stat_Wis -> Stat.WIS;
            case SC_CHAR_STAT.Stat_Dex -> Stat.DEX;
            case SC_CHAR_STAT.Stat_Con -> Stat.CON;
            case SC_CHAR_STAT.Stat_Cha -> Stat.CHA;
            default -> throw new IllegalArgumentException("Unknown investment stat");
        };
        int value = stat.pure.applyAsInt(pc), base = stat.original.applyAsInt(pc);
        int bonus = pc.getBonusStats(), elixirs = pc.getElixirStats();
        int minLevel = Math.max(51 + bonus, value >= 45 ? 90 : 51);
        if (pc.getReturnStat() != 0 || bonus < 0 || bonus >= ExpTable.MAX_LEVEL
                || pc.getLevel() < minLevel || value >= 50)
            throw new IllegalArgumentException("Invalid character stat investment");
        Investment intent = new Investment(stat, pc.getId(), pc.getAccountName(), pc.getName(), pc.getType(),
                value, base, bonus, elixirs, ExpTable.getExpByLevel(minLevel),
                pc.getExp(), pc.getLevel(), Math.max(pc.getHighLevel(), pc.getLevel()),
                pc.getBaseMaxHp(), pc.getBaseMaxMp(), pc.encodeHpMpGrowth(),
                stat == Stat.DEX ? pc.projectEffectiveAc(value + 1, pc.getLevel()) : pc.getAc());
        if (ExpTable.getLevelByExp(intent.exp) != intent.level)
            throw new IllegalArgumentException("Character level is changing");
        tx.requireTables("characters");
        tx.sql(intent::write);
    }

    private record Investment(Stat stat, int owner, String account, String name, int type,
            int value, int base, int bonus, int elixirs, int requiredExp,
            int exp, int level, int high, int hp, int mp, String growth, int effectiveAc) {
        private void bindPreimage(PreparedStatement ps, int i) throws SQLException {
            ps.setInt(++i, owner);
            ps.setString(++i, account);
            ps.setString(++i, name);
            ps.setInt(++i, type);
            ps.setInt(++i, elixirs);
            ps.setInt(++i, bonus);
            ps.setInt(++i, base);
            ps.setInt(++i, value);
            ps.setInt(++i, requiredExp);
        }

        void write(Connection con) throws SQLException {
            try (PreparedStatement ps = con.prepareStatement(stat.invest)) {
                ps.setInt(1, value + 1);
                ps.setInt(2, bonus + 1);
                int i = 2;
                if (stat == Stat.DEX) ps.setInt(++i, effectiveAc);
                bindPreimage(ps, i);
                if (ps.executeUpdate() == 1) return;
            }
            // The new level can be eligible before the first ordinary autosave.
            // Lock and verify the old progression, then persist the new level,
            // its growth and its point together without saving unrelated fields.
            try (PreparedStatement ps = con.prepareStatement(stat.readOlderProgression)) {
                bindPreimage(ps, 0);
                try (ResultSet old = ps.executeQuery()) {
                    if (!old.next() || !extendsProgression(old))
                        throw new SQLException("Character investment preimage changed: " + owner);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(stat.investWithProgression)) {
                ps.setInt(1, value + 1);
                ps.setInt(2, bonus + 1);
                ps.setInt(3, exp);
                ps.setInt(4, level);
                ps.setInt(5, high);
                ps.setInt(6, hp);
                ps.setInt(7, mp);
                ps.setString(8, growth);
                ps.setInt(9, effectiveAc);
                ps.setInt(10, owner);
                requireChanged(ps, owner);
            }
        }

        private boolean extendsProgression(ResultSet old) throws SQLException {
            int priorLevel = ExpTable.getLevelByExp(old.getInt(1));
            if (priorLevel >= level || old.getInt(2) > high) return false;
            String durableGrowth = old.getString(5);
            if (durableGrowth != null && !durableGrowth.isEmpty()
                    && (growth == null || !(growth.equals(durableGrowth) || growth.startsWith(durableGrowth + ";"))))
                return false;
            HpMpGrowthHistory history = HpMpGrowthHistory.decode(growth);
            int priorHp = hp, priorMp = mp;
            if (history != null) {
                for (int lv = level; lv > Math.max(priorLevel, history.floorLevel()); lv--) {
                    if (!history.known(lv)) return false;
                    priorHp -= history.hp(lv);
                    priorMp -= history.mp(lv);
                }
            }
            return priorHp == Math.max(1, old.getInt(3)) && priorMp == Math.max(0, old.getInt(4));
        }
    }

    private static final String RESET_SQL = resetSql();

    private static String resetSql() {
        StringBuilder sql = new StringBuilder("UPDATE characters SET BaseStr=?,Str=?,BaseIntel=?,Intel=?,"
                + "BaseWis=?,Wis=?,BaseDex=?,Dex=?,BaseCon=?,Con=?,BaseCha=?,Cha=?,"
                + "BonusStatus=?,MaxHp=?,CurHp=?,MaxMp=?,CurMp=?,Exp=?,level=?,HighLevel=GREATEST(HighLevel,?),hp_mp_growth=?,Ac=?,ReturnStat=0"
                + " WHERE objid=? AND account_name=? AND char_name=? AND Type=? AND ReturnStat=?"
                + " AND BonusStatus=? AND ElixirStatus=? AND HighLevel<=? AND Exp<=?"
                + " AND (Exp<? OR Exp>=? OR (GREATEST(1,MaxHp)=? AND GREATEST(0,MaxMp)=?"
                + " AND (BINARY NULLIF(hp_mp_growth,'') <=> BINARY ?)))");
        for (Stat stat : STATS) sql.append(stat.whereStat);
        return sql.toString();
    }

    /** Freeze all inputs while the allocating CIO still owns the reset stage.
     * No SQL worker reads the stage, character, equipment or a mutable growth history. */
    public static void stageReset(L1InventoryTransaction tx, L1PcInstance pc, L1ReturnStatTemp stage,
            int str, int intel, int wis, int dex, int con, int cha, int currentHp, int currentMp, int finalAc) {
        int marker = pc.getReturnStat(), exp = pc.getExp(), level = pc.getLevel();
        if (stage != pc.rst || marker <= 0 || stage == null || stage.hpMpGrowth == null
                || stage.level != level || ExpTable.getLevelByExp(exp) != level
                || currentHp < 1 || currentMp < 0)
            throw new IllegalArgumentException("Invalid character stat reset");
        int[] after = {stage.basestr, str, stage.baseint, intel, stage.basewis, wis,
                stage.basedex, dex, stage.basecon, con, stage.basecha, cha};
        int[] before = new int[after.length];
        for (Stat stat : STATS) {
            int i = stat.ordinal() * 2;
            before[i] = stat.original.applyAsInt(pc);
            before[i + 1] = stat.pure.applyAsInt(pc);
        }
        int allocated = stage.allocatedLevelPoints();
        int hp = Math.clamp((long) stage.baseHp + stage.upHp, 1, 32767);
        int mp = Math.clamp((long) stage.baseMp + stage.upMp, 0, 32767);
        int owner = pc.getId(), type = pc.getType(), high = Math.max(pc.getHighLevel(), level);
        int oldHp = pc.getBaseMaxHp(), oldMp = pc.getBaseMaxMp();
        int bonus = pc.getBonusStats(), elixirs = pc.getElixirStats();
        String account = pc.getAccountName(), name = pc.getName();
        String oldGrowth = pc.encodeHpMpGrowth(), growth = stage.hpMpGrowth.encode();
        tx.requireTables("characters");
        tx.sql(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(RESET_SQL)) {
                int i = 0;
                for (int stat : after) ps.setInt(++i, stat);
                ps.setInt(++i, allocated);
                ps.setInt(++i, hp);
                ps.setInt(++i, currentHp);
                ps.setInt(++i, mp);
                ps.setInt(++i, currentMp);
                ps.setInt(++i, exp);
                ps.setInt(++i, level);
                ps.setInt(++i, high);
                ps.setString(++i, growth);
                ps.setInt(++i, finalAc);
                ps.setInt(++i, owner);
                ps.setString(++i, account);
                ps.setString(++i, name);
                ps.setInt(++i, type);
                ps.setInt(++i, marker);
                ps.setInt(++i, bonus);
                ps.setInt(++i, elixirs);
                ps.setInt(++i, high);
                ps.setInt(++i, Math.max(marker, exp));
                // While a reset is open, ordinary autosaves can be deferred. A
                // different durable level legitimately has different historical
                // growth/maxima; the new ledger replaces those together.
                ps.setInt(++i, ExpTable.getExpByLevel(level));
                ps.setLong(++i, level < ExpTable.MAX_LEVEL
                        ? ExpTable.getExpByLevel(level + 1) : (long) Integer.MAX_VALUE + 1);
                ps.setInt(++i, oldHp);
                ps.setInt(++i, oldMp);
                ps.setString(++i, oldGrowth);
                for (int stat : before) ps.setInt(++i, stat);
                requireChanged(ps, owner);
            }
        });
    }

    private static void requireChanged(PreparedStatement ps, int owner) throws SQLException {
        if (ps.executeUpdate() != 1) throw new SQLException("Character stat preimage changed: " + owner);
    }
}
