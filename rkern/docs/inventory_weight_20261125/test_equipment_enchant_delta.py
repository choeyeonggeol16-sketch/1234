"""Run the production equipment definitions and delta handling with isolated packet sinks."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model"
stats_source = (model / "item/ItemStat.java").read_text(encoding="utf-8")
stat_names = re.findall(r"^\s*([A-Z][A-Z0-9_]+)\s*\(\d+,\s*WType\.", stats_source, re.MULTILINE)
assert len(stat_names) > 20
production = [model / "inventory" / f"{name}.java" for name in (
    "EquipmentEnchantOptions", "EquipmentOptionTable", "EquipmentOptionScript", "EquipmentTraits"
)] + [model / "item/ItemScript.java"]

harness = r'''
package l1j.server.server.model.inventory;

import java.util.Arrays;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.templates.L1Item;

public final class EnchantAudit {
    private static void check(boolean passed) {
        if (!passed) throw new AssertionError();
    }

    private static void expectSword(L1PcInstance pc, int enchant) {
        check(pc.stats[ItemStat.MELEE_CRITICAL_RATE.ordinal()] == enchant);
        check(pc.stats[ItemStat.FEAR_PLERCE.ordinal()] == 3 + enchant);
        check(pc.stats[ItemStat.ABILITY_PLERCE.ordinal()] == 3 + enchant);
        check(pc.stats[ItemStat.SPIRIT_PLERCE.ordinal()] == 3 + enchant);
    }

    public static void main(String[] args) {
        final var sword = new L1Item(123999, 1, 1, 0, 1);
        int cases = 0;
        for (int old = -10; old <= 20; old++) {
            for (int next = -10; next <= 20; next++) {
                for (int mask = 0; mask < 4; mask++) {
                    final var pc = new L1PcInstance();
                    pc.mask = mask;
                    EquipmentEnchantOptions.applyWeaponOptions(pc, sword, old, 1, null);
                    expectSword(pc, old);
                    final int[] delta = EquipmentEnchantOptions.enchantDelta(pc, sword, old, next);
                    delta[ItemStat.EVASION.ordinal()] = 2;
                    delta[ItemStat.AC.ordinal()] = -1;
                    delta[ItemStat.MR.ordinal()] = 3;
                    check(EquipmentEnchantOptions.applyDelta(pc, delta));
                    check(pc.failed == Integer.bitCount(mask));
                    expectSword(pc, next);
                    EquipmentEnchantOptions.applyWeaponOptions(pc, sword, next, -1, null);
                    check(pc.stats[ItemStat.MELEE_CRITICAL_RATE.ordinal()] == 0);
                    check(pc.stats[ItemStat.FEAR_PLERCE.ordinal()] == 0);
                    check(pc.stats[ItemStat.ABILITY_PLERCE.ordinal()] == 0);
                    check(pc.stats[ItemStat.SPIRIT_PLERCE.ordinal()] == 0);
                    check(pc.stats[ItemStat.EVASION.ordinal()] == 2);
                    check(pc.stats[ItemStat.AC.ordinal()] == -1);
                    check(pc.stats[ItemStat.MR.ordinal()] == 3);
                    cases++;
                }
            }
        }
        final var pc = new L1PcInstance();
        final var staff = new L1Item(1134, 1, 7, 0, 1);
        final var alias = new L1Item(101134, 1, 7, 0, 1);
        final int[] ordinary = EquipmentEnchantOptions.enchantDelta(pc, staff, 0, 2500);
        check(ordinary[ItemStat.MPR.ordinal()] == 2500);
        check(Arrays.equals(ordinary, EquipmentEnchantOptions.enchantDelta(pc, alias, 0, 2500)));

        final var regularBelt = new L1Item(42, 2, 10, 1, 1);
        final var exceptionalBelt = new L1Item(900007, 2, 10, 1, 1);
        final int[] normal = EquipmentEnchantOptions.accessoryBonusSnapshot(regularBelt, 5);
        final int[] special = EquipmentEnchantOptions.accessoryBonusSnapshot(exceptionalBelt, 5);
        check(normal != special && normal[ItemStat.MAX_MP.ordinal()] == 40);
        check(special[ItemStat.PVP_DMG_RED.ordinal()] == normal[ItemStat.PVP_DMG_RED.ordinal()] + 2);
        EquipmentEnchantOptions.releaseAccessoryBonusSnapshot(special);
        EquipmentEnchantOptions.releaseAccessoryBonusSnapshot(normal);

        final var dodgeArmor = new L1Item(22228, 2, 2, 0, 1);
        final int[] dodge = EquipmentEnchantOptions.enchantDelta(pc, dodgeArmor, 7, 99);
        check(dodge[ItemStat.MAGIC_DODGE.ordinal()] == 6);
        System.out.println("PASS " + cases + " equip/enchant/unequip and packet-fault cases plus definition edge cases");
    }
}
'''

stubs = {
    "l1j.server.server.model.inventory.CUserInventory": '''
package l1j.server.server.model.inventory;
public final class CUserInventory {
    public boolean IsEquippedByClassIDCached(int itemId) { return false; }
}
''',
    "l1j.server.server.model.character.skill.L1SkillId": '''
package l1j.server.server.model.character.skill;
public final class L1SkillId { public static final int TELEPORT_RULER = 1; }
''',
    "l1j.server.server.model.item.ItemStat": f'''
package l1j.server.server.model.item;
import l1j.server.server.model.Instance.L1PcInstance;
public enum ItemStat {{ {", ".join(stat_names)};
    public void apply(L1PcInstance pc, int value) {{ pc.stats[ordinal()] += value; }}
}}
''',
    "l1j.server.server.model.Instance.L1PcInstance": '''
package l1j.server.server.model.Instance;
import l1j.server.server.model.item.ItemStat;
public final class L1PcInstance {
    public final int[] stats = new int[ItemStat.values().length];
    public int failed, mask;
    public int getId() { return 1; }
    public int getTotalER() { return stats[ItemStat.EVASION.ordinal()]; }
}
''',
    "l1j.server.server.templates.L1Item": '''
package l1j.server.server.templates;
public record L1Item(int getItemId, int getType2, int getType, int getGrade, int getBless) {}
''',
    "l1j.server.server.model.network.core.sc.packet.SC_SPMR": '''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SPMR { public static void send(L1PcInstance pc) {} }
''',
    "l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef": '''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_OwnCharAttrDef {
    public static void send(L1PcInstance pc) {
        if ((pc.mask & 2) != 0) {
            pc.failed++;
            throw new IllegalStateException("AC packet failure");
        }
    }
}
''',
    "l1j.server.server.model.network.core.sc.packet.SC_PacketBox": '''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_PacketBox {
    public static final int ER_UpDate = 0;
    public static void send(L1PcInstance pc, int code, int value) {
        if ((pc.mask & 1) != 0) {
            pc.failed++;
            throw new IllegalStateException("ER packet failure");
        }
    }
}
''',
}

with tempfile.TemporaryDirectory() as directory:
    work = Path(directory)
    fixtures = []
    for name, text in {**stubs, "l1j.server.server.model.inventory.EnchantAudit": harness}.items():
        path = work / (name.replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        fixtures.append(path)
    logging = work / "logging.properties"
    logging.write_text(".level=OFF\n", encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", work, *production, *fixtures], check=True)
    subprocess.run(["java", "-ea", f"-Djava.util.logging.config.file={logging}", "-cp", work,
                    f"-Drkern.equipment.options={root / 'src/script/equipment-options.txt'}",
                    "l1j.server.server.model.inventory.EnchantAudit"], check=True, timeout=30)
