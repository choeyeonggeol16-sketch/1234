package l1j.server.server.model.item.doll;

import java.util.ArrayList;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.Config;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemScript;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.eKind;

/** Applies and retires the potential linked to a summoned doll's item. */
public final class DollPotentialEffects {
    private enum Option {
        MELEE_CRITICAL, BOW_CRITICAL, MAGIC_CRITICAL, MELEE_DAMAGE, BOW_DAMAGE,
        MELEE_HIT, BOW_HIT, DAMAGE_REDUCTION, AC, MR,
        MAX_HP, MAX_MP, PVP_DAMAGE, PVP_REDUCTION, FIRE_RESIST,
        EARTH_RESIST, WATER_RESIST, WIND_RESIST, WEIGHT, DG,
        ER, RESIST_ABILITY, RESIST_SPIRIT, RESIST_DRAGON_SPELL, RESIST_FEAR,
        STR, DEX, CON, WIS, INT,
        SP, MAGIC_HIT, PIERCE_ABILITY, PIERCE_SPIRIT, PIERCE_DRAGON_SPELL,
        PIERCE_FEAR, ELEMENT_RESIST, RESIST_ALL, PIERCE_ALL,
        HP_TICK, MP_TICK, SKILL, PEARL
    }

    private record Effect(Option option, int value) {}

    private record Definition(Effect[] effects, String[] tooltip, int hpTick, int mpTick) {}

    private static final TIntObjectHashMap<Definition> DEFINITIONS = ItemScript.load(
            "doll-potential.txt", "rkern.doll.potential", (source, reader) -> {
                final TIntObjectHashMap<Definition> result = new TIntObjectHashMap<>();
                ItemScript.readRows(source, reader, fields -> readDefinition(result, fields));
                if (result.isEmpty()) throw new IllegalStateException(source + " has no doll potentials");
                return result;
            });

    private DollPotentialEffects() {}

    private static void readDefinition(TIntObjectHashMap<Definition> result, String[] fields) {
        if (fields.length < 2) throw new IllegalArgumentException("Potential without effects");
        final int level = Integer.parseInt(fields[0]);
        if (level <= 0) throw new IllegalArgumentException("Invalid potential " + level);
        final var effects = new ArrayList<Effect>();
        final var tooltip = new ArrayList<String>();
        int hpTick = -1;
        int mpTick = -1;
        for (int i = 1; i < fields.length; i++) {
            final int equals = fields[i].indexOf('=');
            if (equals < 1) throw new IllegalArgumentException("Invalid potential option " + fields[i]);
            final String key = fields[i].substring(0, equals);
            final String raw = fields[i].substring(equals + 1);
            if (key.equals("text")) {
                tooltip.add(raw);
                continue;
            }
            final Option option = Option.valueOf(key);
            final int value = Integer.parseInt(raw);
            if ((option == Option.SKILL || option == Option.PEARL) && value <= 0) {
                throw new IllegalArgumentException("Invalid potential skill " + value);
            }
            if (option == Option.HP_TICK) {
                if (hpTick >= 0 || value < 0) throw new IllegalArgumentException("Invalid or duplicate HP tick");
                hpTick = value;
            } else if (option == Option.MP_TICK) {
                if (mpTick >= 0 || value < 0) throw new IllegalArgumentException("Invalid or duplicate MP tick");
                mpTick = value;
            }
            effects.add(new Effect(option, value));
        }
        if (effects.isEmpty()) throw new IllegalArgumentException("Potential without effects " + level);
        tooltip.replaceAll(line -> formatTooltip(line, effects));
        final Definition definition = new Definition(effects.toArray(Effect[]::new), tooltip.toArray(String[]::new),
                Math.max(0, hpTick), Math.max(0, mpTick));
        if (result.putIfAbsent(level, definition) != null) {
            throw new IllegalArgumentException("Duplicate doll potential " + level);
        }
    }

    private static String formatTooltip(String line, ArrayList<Effect> effects) {
        final Effect first = effects.getFirst();
        final long shown = first.option() == Option.AC ? -(long) first.value() : first.value();
        final String signed = (shown >= 0 ? "+" : "") + shown;
        if (line.contains("{ALL_STATS}")) {
            for (Effect effect : effects) {
                if (effect.value() != first.value()) {
                    throw new IllegalArgumentException("{ALL_STATS} requires matching stat values");
                }
            }
            return line.replace("{ALL_STATS}", signed);
        }
        return line.replace("{VALUE}", signed);
    }

    public static int hpTick(int level) {
        final Definition definition = DEFINITIONS.get(level);
        return definition == null ? 0 : definition.hpTick();
    }

    public static int mpTick(int level) {
        final Definition definition = DEFINITIONS.get(level);
        return definition == null ? 0 : definition.mpTick();
    }

    public static void writeTooltip(ItemPresentOutStream out, int level) {
        final Definition definition = DEFINITIONS.get(level);
        if (definition == null) return;
        for (String line : definition.tooltip()) {
            if (line.contains("{PVP_IGNORE}")) {
                line = line.replace("{PVP_IGNORE}", "+" + Config.인형PVP감소수치);
            } else if (line.contains("{IMMUNE_IGNORE}")) {
                line = line.replace("{IMMUNE_IGNORE}", "+" + (int) (Config.잠재력이뮨 * 100d));
            }
            out.writeC(39);
            out.writeS(line);
        }
    }

    public static void initialize() {
        // Compile all potential definitions before any doll can be summoned.
    }

    public static void apply(L1PcInstance pc, int level, int sign) {
        final Definition definition = DEFINITIONS.get(level);
        if (definition == null) return;
        for (Effect effect : definition.effects()) {
            final int v = effect.value() * sign;
            switch (effect.option()) {
                case MELEE_CRITICAL -> pc.add_melee_critical_rate(v);
                case BOW_CRITICAL -> pc.add_missile_critical_rate(v);
                case MAGIC_CRITICAL -> pc.add_magic_critical_rate(v);
                case MELEE_DAMAGE -> pc.addDmgup(v);
                case BOW_DAMAGE -> pc.addBowDmgup(v);
                case MELEE_HIT -> pc.addHitup(v);
                case BOW_HIT -> pc.addBowHitup(v);
                case DAMAGE_REDUCTION -> pc.addDamageReductionByArmor(v);
                case AC -> pc.addAc(v);
                case MR -> pc.AddMDefend(v);
                case MAX_HP -> pc.addMaxHp(v);
                case MAX_MP -> pc.addMaxMp(v);
                case PVP_DAMAGE -> pc.AddPvpDamage(v);
                case PVP_REDUCTION -> pc.AddPvpDamageReduction(v);
                case FIRE_RESIST -> pc.AddFireResist(v);
                case EARTH_RESIST -> pc.AddEarthResist(v);
                case WATER_RESIST -> pc.AddWaterResist(v);
                case WIND_RESIST -> pc.AddWindResist(v);
                case WEIGHT -> pc.addWeightReduction(v);
                case DG -> pc.addDg(v);
                case ER -> pc.addEffectedER(v);
                case RESIST_ABILITY -> pc.AddSpecialResistance(eKind.ABILITY, v);
                case RESIST_SPIRIT -> pc.AddSpecialResistance(eKind.SPIRIT, v);
                case RESIST_DRAGON_SPELL -> pc.AddSpecialResistance(eKind.DRAGON_SPELL, v);
                case RESIST_FEAR -> pc.AddSpecialResistance(eKind.FEAR, v);
                case STR -> pc.AddStatBonusStr(v);
                case DEX -> pc.AddStatBonusDex(v);
                case CON -> pc.AddStatBonusCon(v);
                case WIS -> pc.AddStatBonusWis(v);
                case INT -> pc.AddStatBonusInt(v);
                case SP -> pc.AddStatSp(v);
                case MAGIC_HIT -> pc.addBaseMagicHitUp(v);
                case PIERCE_ABILITY -> pc.AddSpecialPierce(eKind.ABILITY, v);
                case PIERCE_SPIRIT -> pc.AddSpecialPierce(eKind.SPIRIT, v);
                case PIERCE_DRAGON_SPELL -> pc.AddSpecialPierce(eKind.DRAGON_SPELL, v);
                case PIERCE_FEAR -> pc.AddSpecialPierce(eKind.FEAR, v);
                case ELEMENT_RESIST -> pc.AddAllNaturalResistance(v);
                case RESIST_ALL -> pc.AddSpecialResistance(eKind.ALL, v);
                case PIERCE_ALL -> pc.AddSpecialPierce(eKind.ALL, v);
                case HP_TICK, MP_TICK -> { }
                case SKILL -> updateSkill(pc, effect.value(), sign);
                case PEARL -> {
                    updateSkill(pc, effect.value(), sign);
                    SC_SystemMessage.send(pc, sign > 0 ? "\\f4이미 3단 가속효과가 적용중입니다."
                            : "\\f4마법 인형의 효과가 사라졌습니다.");
                }
            }
        }
    }

    private static void updateSkill(L1PcInstance pc, int skill, int sign) {
        if (sign > 0) pc.setSkillEffect(skill, 3600 * 1000);
        else pc.removeSkillEffect(skill);
    }
}
