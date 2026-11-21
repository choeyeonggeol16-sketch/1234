package l1j.server.server.model;

import static l1j.server.server.model.item.ItemStat.*;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.templates.L1Item;

/** Lineage 1 equipment options shared by equip, unequip, and enchant changes. */
final class EquipmentEnchantOptions {
    private static final ItemStat[] STATS = ItemStat.values();

    private EquipmentEnchantOptions() {}

    static int[] enchantDelta(L1PcInstance pc, L1Item item, int previousEnchant, int enchant) {
        final int[] changes = new int[STATS.length];
        collect(pc, item, previousEnchant, -1, changes);
        collect(pc, item, enchant, 1, changes);
        return changes;
    }

    private static void collect(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        if (item.getType2() == 1) {
            applyMeditationStaff(pc, item, enchant, sign, changes);
            applyNamedWeaponOptions(pc, item, enchant, sign, changes);
            applyDemonAxe(pc, item, enchant, sign, changes);
            applyWeaponAccuracy(pc, item, enchant, sign, changes);
            applyCourtshipWeaponPvp(pc, item, enchant, sign, changes);
        } else if (item.getType2() == 2) {
            applyEnhancedWard(pc, item, enchant, sign, changes);
            applyWards(pc, item, enchant, sign, changes);
            applyMagicClothing(pc, item, enchant, sign, changes);
            applyArmorDefenseOptions(pc, item, enchant, sign, changes);
            applyGuardersAndSanctuaryArmor(pc, item, enchant, sign, changes);
            applyVitalityGreaves(pc, item, enchant, sign, changes);
            applyUnicornGreaves(pc, item, enchant, sign, changes);
            applyMamboHat(pc, item, enchant, sign, changes);
            applyShouldersAndCloak(pc, item, enchant, sign, changes);
            applyBadges(pc, item, enchant, sign, changes);
            applyWingsAndAccessory(pc, item, enchant, sign, changes);
            applyCrimsonArmor(pc, item, enchant, sign, changes);
            applyVampireCloak(pc, item, enchant, sign, changes);
            applyIrisBoots(pc, item, enchant, sign, changes);
            applyMagicAndRareArmor(pc, item, enchant, sign, changes);
            applyHelmsAndDragonArmor(pc, item, enchant, sign, changes);
            applyAccessoryOptions(pc, item, enchant, sign, changes);
        }
    }

    static void applyStat(L1PcInstance pc, int[] changes, ItemStat stat, int value) {
        if (changes == null) stat.apply(pc, value);
        else changes[stat.ordinal()] += value;
    }

    static boolean applyDelta(L1PcInstance pc, int[] changes) {
        for (int i = 0; i < changes.length; i++) {
            if (changes[i] != 0) STATS[i].apply(pc, changes[i]);
        }
        if (changes[EVASION.ordinal()] != 0) {
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
        }
        return changes[MR.ordinal()] != 0 || changes[SP.ordinal()] != 0
                || changes[CHARACTER_SP.ordinal()] != 0;
    }

    static int accessoryHeal(L1Item item, int enchant) {
        final int type = item.getType();
        if (type != 8 && type != 12) return 0;
        final int grade = item.getGrade();
        if (grade < 0 || grade > 2) return 0;
        final int amount = switch (enchant) {
            case 5 -> 3;
            case 6 -> 5;
            case 7 -> 7;
            case 8 -> 9;
            case 9 -> 10;
            case 10 -> 11;
            default -> 0;
        };
        return type == 12 && amount > 0 ? amount - 1 : amount;
    }

    static void applyMeditationStaff(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 1134 || itemId == 101134) { // 명상의 지팡이
            if (enchant > 0) {
                applyStat(pc, changes, MPR, sign * enchant);
            }
        }
    }

    static void applyNamedWeaponOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 61 || itemId == 14061) {
            if (enchant > 0) {
                applyStat(pc, changes, ABILITY_PLERCE, sign * (8 + enchant));
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * (1 + enchant));
            } else {
                applyStat(pc, changes, ABILITY_PLERCE, sign * 8);
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 1);
            }
        }

        if (itemId == 12 || itemId == 14012) {
            if (enchant > 0) {
                applyStat(pc, changes, ALL_PLERCE, sign * enchant);
            }
        }

        if (itemId == 134 || itemId == 14134) {
            if (enchant > 0) {
                applyStat(pc, changes, SP, sign * (7 + enchant));
                applyStat(pc, changes, MAGIC_HIT, sign * (2 + enchant));
            } else {
                applyStat(pc, changes, SP, sign * 7);
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
            }
            if (changes == null) SC_SPMR.send(pc);
        }

        if (itemId == 86 || itemId == 14086) {
            if (enchant > 0) {
                applyStat(pc, changes, SPIRIT_PLERCE, sign * (5 + enchant));
            } else {
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
            }
        }
        if (itemId == 202011 || itemId == 2202011) {
            if (enchant > 0) {
                applyStat(pc, changes, SPIRIT_PLERCE, sign * (5 + enchant));
                applyStat(pc, changes, DMG_RED_IGNORE, sign * (9  + enchant));
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * (1 + enchant));
                applyStat(pc, changes, DMG_RED, sign * 2);
            } else {
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 1);
                applyStat(pc, changes, DMG_RED_IGNORE, sign * 9);
                applyStat(pc, changes, DMG_RED, sign * 2);

            }
        }

        if (itemId == 202013 || itemId == 2202013) {
            if (enchant > 0) {
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * (1 + enchant));
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * (7  + enchant));
            } else {
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 1);
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 7);
            }
        }

        if (itemId == 202012 || itemId == 2202012) {
            if (enchant > 0) {
                applyStat(pc, changes, SP, sign * (2 + enchant));
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * (7  + enchant));
                applyStat(pc, changes, MAGIC_CRITICAL_RATE, sign * (1 + enchant));
            } else {
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 7);
                applyStat(pc, changes, MAGIC_CRITICAL_RATE, sign * 1);
            }
            if (changes == null) SC_SPMR.send(pc);
        }

        if (itemId == 202014 || itemId == 2202014) {
            if (enchant > 0) {
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * (1 + enchant));
                applyStat(pc, changes, FEAR_PLERCE, sign * (3 + enchant));

            } else {
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 1);
                applyStat(pc, changes, FEAR_PLERCE, sign * 3);

            }
        }

        /** 칠흑의 수정구 **/
        if (itemId == 118) {
            switch (enchant) {
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, CHARISMA, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 축복받은 제로스의 지팡이 **/
        if (itemId == 620) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);// 마법 치명타
                break;
            }
        }
        /** 살기의 키링크 **/
        if (itemId == 7000223 || itemId == 505015) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 1);// 마법 치명타
                break;
            case 7:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 1);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 2);// 마법 치명타
                break;
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 2);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 3);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 4);// 마법 치명타
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 4);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);// 마법 치명타
                break;
            }
        }

        if (itemId == 123001) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, FEAR_PLERCE, sign * 2);
                applyStat(pc, changes, ABILITY_PLERCE, sign * 2);
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, FEAR_PLERCE, sign * 3);
                applyStat(pc, changes, ABILITY_PLERCE, sign * 3);
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 3);
                break;
            case 10:
                applyStat(pc, changes, FEAR_PLERCE, sign * 4);
                applyStat(pc, changes, ABILITY_PLERCE, sign * 4);
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 4);
                break;
            case 11:
                applyStat(pc, changes, FEAR_PLERCE, sign * 5);
                applyStat(pc, changes, ABILITY_PLERCE, sign * 5);
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, FEAR_PLERCE, sign * 5);
                    applyStat(pc, changes, ABILITY_PLERCE, sign * 5);
                    applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
                }
                break;
            }
        }
        if (itemId == 123002) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                applyStat(pc, changes, ABILITY_PLERCE, sign * 5);
                break;
            case 8:
                applyStat(pc, changes, ABILITY_PLERCE, sign * 6);
                break;
            case 9:
                applyStat(pc, changes, ABILITY_PLERCE, sign * 7);
                break;
            case 10:
                applyStat(pc, changes, ABILITY_PLERCE, sign * 8);
                break;
            default:
                if (enchant > 10) {
                    applyStat(pc, changes, ABILITY_PLERCE, sign * 8);
                }
                break;
            }
        }
        if (itemId == 123003) {
            switch (enchant) {
            case 9:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 1);
                break;
            case 10:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 2);
                break;
            case 11:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 3);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, SPIRIT_PLERCE, sign * 3);
                }
                break;
            }
        }
        if (itemId == 123004) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 3);
                break;
            case 10:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 4);
                break;
            case 11:
                applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, SPIRIT_PLERCE, sign * 5);
                }
                break;
            }
        }
        if (itemId == 123005) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, MAGIC_HIT, sign * 1);
                break;
            case 9:
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
                break;
            case 10:
                applyStat(pc, changes, MAGIC_HIT, sign * 3);
                break;
            case 11:
                applyStat(pc, changes, MAGIC_HIT, sign * 4);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, MAGIC_HIT, sign * 4);
                }
                break;
            }
        }
        if (itemId == 123006) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, FEAR_PLERCE, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, FEAR_PLERCE, sign * 3);
                break;
            case 10:
                applyStat(pc, changes, FEAR_PLERCE, sign * 4);
                break;
            case 11:
                applyStat(pc, changes, FEAR_PLERCE, sign * 5);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, FEAR_PLERCE, sign * 5);
                }
                break;
            }
        }
        if (itemId == 123007) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 7);
                break;
            case 11:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 8);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 8);
                }
                break;
            }
        }
        if (itemId == 123008) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                applyStat(pc, changes, CHARACTER_SP, sign * 1);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                applyStat(pc, changes, CHARACTER_SP, sign * 2);
                break;
            case 10:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                applyStat(pc, changes, CHARACTER_SP, sign * 3);
                break;
            case 11:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                applyStat(pc, changes, CHARACTER_SP, sign * 4);
                break;
            default:
                if (enchant > 11) {
                    applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 5);
                    applyStat(pc, changes, CHARACTER_SP, sign * 4);
                }
                break;
            }
        }
        /** 바포메트의 지팡이 SP **/
        if (itemId == 124) {
            switch (enchant) {
            case 7:
                applyStat(pc, changes, SP, sign * 1);
                if (changes == null) SC_SPMR.send(pc);
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 2);
                if (changes == null) SC_SPMR.send(pc);
                break;
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SP, sign * 3);
                if (changes == null) SC_SPMR.send(pc);
                break;
            }
        }
        if (itemId == 541) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;
            }
        }
        if (itemId == 542) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, DMG_RED, sign * 2);
                break;
            }
        }
        if (itemId == 543) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, HIT, sign * 2);
                break;
            }
        }
        if (itemId == 544) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, HIT, sign * 10);
                break;
            }
        }
        if (itemId == 545) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, HIT, sign * 17);
                break;
            }
        }
        if (itemId == 547) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, HIT, sign * 11);
                break;
            }
        }
        /** 드래곤슬레이어 **/
        if (itemId == 66) {
            applyStat(pc, changes, MELEE_DAMAGE_UP, sign * (enchant * 2));
        }
    }

    static void applyDemonAxe(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 147) {
            applyStat(pc, changes, DMG_RATE, sign * enchant);
        }
    }

    static void applyWeaponAccuracy(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 203006 || itemId == 616) {
            switch (enchant) {
            case 8:
                applyStat(pc, changes, FEAR_PLERCE, sign * 1);
                break;
            case 9:
                applyStat(pc, changes, FEAR_PLERCE, sign * 2);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, FEAR_PLERCE, sign * 3);
                break;
            }
        }
        /** 섬멸자의 체인소드 적중 **/
        if (itemId == 203017 || itemId == 618) {
            switch (enchant) {
            case 7:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 3);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, DRAGON_SPELL_PLERCE, sign * 4);
                break;
            }
        }
    }

    static void applyCourtshipWeaponPvp(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId >= 7000214 && itemId <= 7000221) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                applyStat(pc, changes, PVP_DMG, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, PVP_DMG, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, PVP_DMG, sign * 7);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, PVP_DMG, sign * 10);
                break;
            }
        }
    }

    static void applyEnhancedWard(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 808011) {

            switch (enchant) {
            case 0:
                applyStat(pc, changes, MR, sign * 12);
                break;
            case 1:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MR, sign * 26);
                break;

            case 2:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_MP, sign * 10);
                applyStat(pc, changes, MAX_HP, sign * 20);
                applyStat(pc, changes, MR, sign * 28);
                break;
            case 3:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_MP, sign * 20);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, MR, sign * 30);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_MP, sign * 30);
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, MR, sign * 32);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_MP, sign * 40);
                applyStat(pc, changes, MAX_HP, sign * 80);
                applyStat(pc, changes, MR, sign * 34);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                applyStat(pc, changes, EVASION, sign * 4);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                break;

        default:
            if (enchant > 5 ){
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_MP, sign * 40);
                applyStat(pc, changes, MAX_HP, sign * 80);
                applyStat(pc, changes, MR, sign * 34);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                applyStat(pc, changes, EVASION, sign * 4);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
            }
                break;
            }
        }
    }

    static void applyWards(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if ( itemId == 565611) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 1);
                applyStat(pc, changes, SPIRIT_RESIS, sign * 1);
                applyStat(pc, changes, FEAR_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                break;
        default:
            if (enchant > 5) {
                applyStat(pc, changes, MR, sign * (enchant - 5 + 12));
                applyStat(pc, changes, DMG_RED, sign * (enchant - 5 + 1));
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * ((enchant - 5 + 1)));
                applyStat(pc, changes, SPIRIT_RESIS, sign * ((enchant - 5 + 1)));
                applyStat(pc, changes, FEAR_RESIS, sign * ((enchant - 5 + 1)));
                applyStat(pc, changes, ABILITY_RESIS, sign * ((enchant - 5 + 12)));
                applyStat(pc, changes, PVP_DMG, sign * (enchant - 5));
            }
                break;
            }
        }

        if (itemId == 900116) {
            if (enchant <= 5) {
                applyStat(pc, changes, PVP_DMG, sign * 3);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                applyStat(pc, changes, MR, sign * 10);
                applyStat(pc, changes, MAX_HP, sign * 100);
            } else if (enchant == 6) {
                applyStat(pc, changes, PVP_DMG, sign * 4);
                applyStat(pc, changes, PVP_DMG_RED, sign * 2);
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, MAX_HP, sign * 120);
            } else if (enchant == 7) {
                applyStat(pc, changes, PVP_DMG, sign * 5);
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);
                applyStat(pc, changes, MR, sign * 13);
                applyStat(pc, changes, MAX_HP, sign * 130);
            } else if (enchant == 8) {
                applyStat(pc, changes, PVP_DMG, sign * 6);
                applyStat(pc, changes, PVP_DMG_RED, sign * 4);
                applyStat(pc, changes, MR, sign * 15);
                applyStat(pc, changes, MAX_HP, sign * 150);
            }

        }
    }

    static void applyMagicClothing(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if(itemId == 700117){

            applyStat(pc, changes, SP, sign * (2+ enchant));
            applyStat(pc, changes, PVP_DMG, sign * (2 + enchant));
            applyStat(pc, changes, PVP_DMG_RED, sign * (2 + enchant));
            applyStat(pc, changes, MR, sign * (3 + enchant));
            applyStat(pc, changes, MAGIC_HIT, sign * (1 + enchant));
            if (changes == null) SC_OwnCharAttrDef.send(pc);
            if (changes == null) SC_SPMR.send(pc);
            if (enchant >= 5) {
                applyStat(pc, changes, ALL_RESIS, sign * (enchant - 4));
                applyStat(pc, changes, ALL_PLERCE, sign * (enchant - 4));
            }

        }
    }

    static void applyArmorDefenseOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 20050) {

            switch (enchant) {
            case 5:
                applyStat(pc, changes, EVASION, sign * 1);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

                break;
            case 6:
                applyStat(pc, changes, EVASION, sign * 2);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                break;
            case 7:
                applyStat(pc, changes, EVASION, sign * 3);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                break;
            case 8:
                applyStat(pc, changes, EVASION, sign * 4);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                break;
            case 9:
                applyStat(pc, changes, EVASION, sign * 5);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                break;

        default:
            if (enchant > 9 ){
                applyStat(pc, changes, EVASION, sign * 5);
                if (changes == null) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
            }
                break;
            }
        }
        if (itemId == 474700) {

            switch (enchant) {
            case 0:
                applyStat(pc, changes, MR, sign * 1);
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MR, sign * 3);
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                break;
            case 2:
                applyStat(pc, changes, MR, sign * 5);
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                break;
            case 3:
                applyStat(pc, changes, MR, sign * 7);
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                break;
            case 4:
                applyStat(pc, changes, MR, sign * 9);
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                break;
            case 5:
                applyStat(pc, changes, MR, sign * 11);
                applyStat(pc, changes, FEAR_RESIS, sign * 6);
                applyStat(pc, changes, ABILITY_RESIS, sign * 6);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 13);
                applyStat(pc, changes, FEAR_RESIS, sign * 7);
                applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                break;
            case 7:
                applyStat(pc, changes, MR, sign * 15);
                applyStat(pc, changes, FEAR_RESIS, sign * 8);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 17);
                applyStat(pc, changes, FEAR_RESIS, sign * 9);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 19);
                applyStat(pc, changes, FEAR_RESIS, sign * 10);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;
            case 10:
                applyStat(pc, changes, MR, sign * 21);
                applyStat(pc, changes, FEAR_RESIS, sign * 10);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;

//		default:
//			if (enchant > 9 ){
//			}
//				break;
            }
        }

        if (itemId == 20051) {

            switch (enchant) {
            case 0:
                applyStat(pc, changes, MR, sign * 4);

                break;
            default:
                if (enchant > 0) {
                    applyStat(pc, changes, MR, sign * (enchant * 2 + 4));
                }

                break;

            }
        }

        if (itemId == 22263) {
            int aa = enchant - 4;
                if (enchant > 4) {
                        applyStat(pc, changes, MAX_HP, sign * (aa * 20));
                        applyStat(pc, changes, ABILITY_PLERCE, sign * (enchant - 4));
                    }
            }
        if (itemId == 22360) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, MR, sign * 1);

                break;
            default:

                if (enchant > 4) {
                    applyStat(pc, changes, ABILITY_RESIS, sign * (enchant - 4));
                    applyStat(pc, changes, MR, sign * (((enchant - 4 ) * 2) + 1));
                }

                break;

            }
        }
    }

    static void applyGuardersAndSanctuaryArmor(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900117) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, HIT, sign * 2);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                applyStat(pc, changes, HIT, sign * 3);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, DMG_RATE, sign * 3);
                applyStat(pc, changes, HIT, sign * 4);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, DMG_RATE, sign * 4);
                applyStat(pc, changes, HIT, sign * 5);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, DMG_RATE, sign * 5);
                applyStat(pc, changes, HIT, sign * 6);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 풍령의 가더 **/
        if (itemId == 900118) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);
                applyStat(pc, changes, BOW_HIT, sign * 2);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 2);
                applyStat(pc, changes, BOW_HIT, sign * 3);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 3);
                applyStat(pc, changes, BOW_HIT, sign * 4);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 4);
                applyStat(pc, changes, BOW_HIT, sign * 5);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 5);
                applyStat(pc, changes, BOW_HIT, sign * 6);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 수령의 가더 **/
        if (itemId == 900119) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, MAGIC_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, MAGIC_HIT, sign * 3);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 3);
                applyStat(pc, changes, MAGIC_HIT, sign * 4);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, SP, sign * 4);
                applyStat(pc, changes, MAGIC_HIT, sign * 5);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, SP, sign * 5);
                applyStat(pc, changes, MAGIC_HIT, sign * 6);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 에바의 방패 **/
        if (itemId == 20235) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 시어의 심안 **/
        if (itemId == 22214) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 3);
                break;
            case 6:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 4);
                break;
            case 7:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 5);
                break;
            case 8:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 6);
                break;
            case 9:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 7);
                break;
            case 10:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 8);
                break;
            default:
                break;
            }
        }
        /** 지령의 가더 **/
        if (itemId == 900120) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                applyStat(pc, changes, MR, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                applyStat(pc, changes, MR, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, PVP_DMG_RED, sign * 2);
                applyStat(pc, changes, MR, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);
                applyStat(pc, changes, MR, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, PVP_DMG_RED, sign * 4);
                applyStat(pc, changes, MR, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, PVP_DMG_RED, sign * 5);
                applyStat(pc, changes, MR, sign * 7);
                break;
            default:
                break;
            }
        }

        /** 마법사의가더 **/
        if (itemId == 22255) {
            switch (enchant) {
            case 5:
            case 6:
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 7:
            case 8:
                applyStat(pc, changes, SP, sign * 2);
                break;
            case 9:
            case 10:
                applyStat(pc, changes, SP, sign * 3);
                break;
            default:
                break;
            }
        }
        /** 체력의 가더 **/
        if (itemId == 22256) {
            switch (enchant) {
            case 5:
            case 6:
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 7:
            case 8:
                applyStat(pc, changes, MAX_HP, sign * 50);
                break;
            case 9:
            case 10:
                applyStat(pc, changes, MAX_HP, sign * 75);
                break;
            default:
                break;
            }
        }
        // TODO 투사의 휘장
        if (itemId == 900152) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -2);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, DMG_RATE, sign * 1);// 근거리 대미지
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, DMG_RATE, sign * 2);// 근거리 대미지
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 1);// 근거리 치명타
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, DMG_RATE, sign * 3);// 근거리 대미지
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 3);// 근거리 치명타
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, DMG_RATE, sign * 4);// 근거리 대미지
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);// 근거리 치명타
                break;
            default:
                break;
            }
        }
        // TODO 명궁의 휘장
        if (itemId == 900153) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -2);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);//원거리 대미지
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 2);//원거리 대미지
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 1);// 원거리 치명타
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 3);//원거리 대미지
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 3);// 원거리 치명타
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 4);//원거리 대미지
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);// 원거리 치명타
                break;
            default:
                break;
            }
        }
        if (itemId == 123111) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 7);
                break;
            case 1:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 9);
                break;
            case 2:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 11);
                break;
            case 3:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 13);
                break;
            case 4:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 15);
                break;
            case 5:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 17);
                break;
            case 6:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 19);
                break;
            case 7:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 21);
                break;
            case 8:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 23);
                break;
            case 9:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 25);
                break;
            case 10:
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                applyStat(pc, changes, DMG_RED, sign * 27);
                break;
            default:
                if (enchant > 10) {
                    applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 5);
                    applyStat(pc, changes, DMG_RED, sign * (enchant * 2 + 7));
                }
                break;
            }
        }
            //할파스의 예지력
                    if (itemId == 123112) {
                    switch (enchant) {
                    case 0:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 7);
                        break;
                    case 1:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 9);
                        break;
                    case 2:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 11);
                        break;
                    case 3:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 13);
                        break;
                    case 4:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 15);
                        break;
                    case 5:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 17);
                        break;
                    case 6:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 19);
                        break;
                    case 7:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 21);
                        break;
                    case 8:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 23);
                        break;
                    case 9:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 25);
                        break;
                    case 10:
                        applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 27);
                        break;
                    default:
                        if (enchant > 10) {
                            applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                            applyStat(pc, changes, DMG_RED, sign * (enchant * 2 + 7));
                        }
                        break;
                    }
                }
                    //할파스의 완력
                    if (itemId == 123113) {
                    switch (enchant) {
                    case 0:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 7);
                        break;
                    case 1:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 9);
                        break;
                    case 2:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 11);
                        break;
                    case 3:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 13);
                        break;
                    case 4:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 15);
                        break;
                    case 5:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 17);
                        break;
                    case 6:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 19);
                        break;
                    case 7:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 21);
                        break;
                    case 8:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 23);
                        break;
                    case 9:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 25);
                        break;
                    case 10:
                        applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                        applyStat(pc, changes, DMG_RED, sign * 27);
                        break;
                    default:
                        if (enchant > 10) {
                            applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                            applyStat(pc, changes, DMG_RED, sign * (enchant * 2 + 7));
                        }
                        break;
                    }
                }
        // TODO 현자의 휘장
        if (itemId == 900154) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -2);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, HIT, sign * 1);//근거리 명중
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, HIT, sign * 2);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 1);// 마법 치명타
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, HIT, sign * 3);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 2);// 마법 치명타
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, HIT, sign * 4);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 4);// 마법 치명타
                break;
            default:
                break;
            }
        }

        /** 완력의 문장 효과 **/
        if (itemId == 900049 || itemId == 900093 || itemId == 900096) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, HIT, sign * 1);
                applyStat(pc, changes, DMG_RATE, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, HIT, sign * 2);
                applyStat(pc, changes, DMG_RATE, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, HIT, sign * 3);
                applyStat(pc, changes, DMG_RATE, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, HIT, sign * 4);
                applyStat(pc, changes, DMG_RATE, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, HIT, sign * 5);
                applyStat(pc, changes, DMG_RATE, sign * 5);
                break;
            default:
                break;
            }
        }
        //고대 마물 시리즈
//		if (itemId == 900015 || itemId == 900016 || itemId == 900017 || itemId == 900018) {
//			switch (enchant) {
//			case 0:
//			case 1:
//			case 2:
//			case 3:
//			case 4:
//			case 5:
//			case 6:
//			case 7:
//			case 8:
//			case 9:
//			case 10:
//				break;
//
//			default:
//				break;
//			}
//		}

        /** 민첩의 문장 효과 **/
        if (itemId == 900050 || itemId == 900094 || itemId == 900097) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 지식의 문장 효과 **/
        if (itemId == 900051 || itemId == 900095 || itemId == 900098) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, MAGIC_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, MAGIC_HIT, sign * 1);
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
                applyStat(pc, changes, SP, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, MAGIC_HIT, sign * 3);
                applyStat(pc, changes, SP, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, MAGIC_HIT, sign * 4);
                applyStat(pc, changes, SP, sign * 4);
                break;
            case 10:
                applyStat(pc, changes, MAGIC_HIT, sign * 5);
                applyStat(pc, changes, SP, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 성장/회복의 문장 **/
        if (itemId == 900020 || itemId == 900021 || itemId == 900049 || itemId == 900050 || itemId == 900051 || itemId >= 900093 && itemId <= 900099) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, AC, sign * 0);
                break;
            case 1:
                applyStat(pc, changes, AC, sign * 1);
                break;
            case 2:
                applyStat(pc, changes, AC, sign * 2);
                break;
            case 3:
                applyStat(pc, changes, AC, sign * 3);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * 4);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * 5);
                break;
            case 6:
                applyStat(pc, changes, AC, sign * 6);
                break;
            case 7:
                applyStat(pc, changes, AC, sign * 7);
                break;
            case 8:
                applyStat(pc, changes, AC, sign * 8);
                break;
            case 9:
                applyStat(pc, changes, AC, sign * 9);
                break;
            case 10:
                applyStat(pc, changes, AC, sign * 10);
                break;
            default:
                break;
            }
        }

        /** 실프의티셔츠 내성 인챈별 **/
        if (itemId == 900019) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 1:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 2:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 3:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 4:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 5:
                applyStat(pc, changes, MR, sign * 12);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 13);
                applyStat(pc, changes, DMG_RED, sign * 2);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 7:
                applyStat(pc, changes, MR, sign * 14);
                applyStat(pc, changes, DMG_RED, sign * 3);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 15);
                applyStat(pc, changes, DMG_RED, sign * 4);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 16);
                applyStat(pc, changes, DMG_RED, sign * 5);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, MR, sign * 17);
                applyStat(pc, changes, DMG_RED, sign * 6);//대미지 감소
                applyStat(pc, changes, ALL_RESIS, sign * 6);
                applyStat(pc, changes, ABILITY_RESIS, sign * 11);
                break;
            default:
                break;
            }
        }
    }

    static void applyVitalityGreaves(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900023) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 3:
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 4:
                applyStat(pc, changes, MAX_HP, sign * 30);
                break;
            case 5:
                applyStat(pc, changes, MAX_HP, sign * 35);
                break;
            case 6:
                applyStat(pc, changes, MAX_HP, sign * 40);
                break;
            case 7:
                applyStat(pc, changes, MAX_HP, sign * 45);
                break;
            case 8:
                applyStat(pc, changes, MAX_HP, sign * 50);
                break;
            case 9:
                applyStat(pc, changes, MAX_HP, sign * 55);
                break;
            default:
                break;
            }
        }
    }

    static void applyUnicornGreaves(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900030) {
            switch (enchant) {
            case 9:
            case 10:
            case 11:
            case 12:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 유니콘의 민첩 각반 **/
        if (itemId == 900031) {
            switch (enchant) {
            case 9:
            case 10:
            case 11:
            case 12:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 유니콘의 지식 각반 **/
        if (itemId == 900032) {
            switch (enchant) {
            case 9:
            case 10:
            case 11:
            case 12:
                applyStat(pc, changes, SP, sign * 1);
                break;
            }
        }
    }

    static void applyMamboHat(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 20016 || itemId == 120016 || itemId == 20112 || itemId == 120112) {
            switch (enchant) {
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, CHARISMA, sign * 1);
                break;
            default:
                break;
            }
        }
    }

    static void applyShouldersAndCloak(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900080) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, MAGIC_HIT, sign * 1);// 마법 적중
                break;
            default:
                break;
            }
        }
        /** 칠흑의 망토  **/
        if (itemId == 900076) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                applyStat(pc, changes, CHARISMA, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, CHARISMA, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, CHARISMA, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, CHARISMA, sign * 4);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, CHARISMA, sign * 4);
                break;
            default:
                break;
            }
        }
    }

    static void applyBadges(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900081) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 3:
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, HIT, sign * 1);
                applyStat(pc, changes, DMG_RED, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 5);// 축복 소모 효율
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -5);//AC
                applyStat(pc, changes, MAX_HP, sign * 35);//HP
                applyStat(pc, changes, HIT, sign * 1);//근거리 명중
                applyStat(pc, changes, DMG_RED, sign * 2);//대미지감소
                applyStat(pc, changes, MR, sign * 3);//MR
                applyStat(pc, changes, DMG_RATE, sign * 2);//근거리 대미지
                applyStat(pc, changes, PVP_DMG_RED, sign * 2);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 10);// 축복 소모 효율
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 1);

                break;
            case 7:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, DMG_RED, sign * 3);//대미지감소
                applyStat(pc, changes, MR, sign * 5);//MR
                applyStat(pc, changes, DMG_RATE, sign * 3);//근거리 대미지
                applyStat(pc, changes, HIT, sign * 3);//근거리 명중
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 15);// 축복 소모 효율
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -7);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, DMG_RED, sign * 4);//대미지감소
                applyStat(pc, changes, MR, sign * 7);//MR
                applyStat(pc, changes, DMG_RATE, sign * 4);//근거리 대미지
                applyStat(pc, changes, HIT, sign * 5);//근거리 명중
                applyStat(pc, changes, PVP_DMG_RED, sign * 5);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 20);// 축복 소모 효율
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, AC, sign * -8);
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, DMG_RED, sign * 5);//대미지감소
                applyStat(pc, changes, MR, sign * 9);//MR
                applyStat(pc, changes, DMG_RATE, sign * 5);//근거리 대미지
                applyStat(pc, changes, HIT, sign * 7);//근거리 명중
                applyStat(pc, changes, PVP_DMG_RED, sign * 7);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 25);// 축복 소모 효율
                applyStat(pc, changes, MELEE_CRITICAL_RATE, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 커츠의 명궁 휘장 **/
        if (itemId == 900082) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 3:
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, DMG_RED, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 5);// 축복 소모 효율
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -5);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, DMG_RED, sign * 2);
                applyStat(pc, changes, MR, sign * 3);//MR
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 2);
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 2);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 10);// 축복 소모 효율
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, DMG_RED, sign * 3);
                applyStat(pc, changes, MR, sign * 5);//MR
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 3);
                applyStat(pc, changes, BOW_HIT, sign * 3);
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 15);// 축복 소모 효율
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -7);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, DMG_RED, sign * 4);//대미지 감소
                applyStat(pc, changes, MR, sign * 7);//MR
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 4);
                applyStat(pc, changes, BOW_HIT, sign * 5);
                applyStat(pc, changes, PVP_DMG_RED, sign * 5);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 20);// 축복 소모 효율
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, AC, sign * -8);
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, DMG_RED, sign * 5);//대미지 감소
                applyStat(pc, changes, MR, sign * 9);//MR
                applyStat(pc, changes, BOW_DAMAGE_UP, sign * 5);
                applyStat(pc, changes, BOW_HIT, sign * 7);
                applyStat(pc, changes, PVP_DMG_RED, sign * 7);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 25);// 축복 소모 효율
                applyStat(pc, changes, BOW_CRITICAL_RATE, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 커츠의 현자 휘장 **/
        if (itemId == 900083) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 3:
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, DMG_RED, sign * 1);//대미지 감소
                applyStat(pc, changes, HIT, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 5);// 축복 소모 효율
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -5);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, DMG_RED, sign * 2);//대미지 감소
                applyStat(pc, changes, MR, sign * 3);//MR
                applyStat(pc, changes, HIT, sign * 2);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 1);// 마법 치명타
                applyStat(pc, changes, PVP_DMG_RED, sign * 2);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 10);// 축복 소모 효율
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, DMG_RED, sign * 3);//대미지 감소
                applyStat(pc, changes, MR, sign * 5);//MR
                applyStat(pc, changes, HIT, sign * 3);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 2);// 마법 치명타
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 15);// 축복 소모 효율
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -7);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, DMG_RED, sign * 4);//대미지 감소
                applyStat(pc, changes, MR, sign * 7);//MR
                applyStat(pc, changes, HIT, sign * 4);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                applyStat(pc, changes, PVP_DMG_RED, sign * 5);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 20);// 축복 소모 효율
                break;
            case 9:
                applyStat(pc, changes, AC, sign * -8);
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, DMG_RED, sign * 5);//대미지 감소
                applyStat(pc, changes, MR, sign * 9);//MR
                applyStat(pc, changes, HIT, sign * 5);//근거리 명중
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 4);// 마법 치명타
                applyStat(pc, changes, PVP_DMG_RED, sign * 7);//PVP대미지감소
                applyStat(pc, changes, BLESS_CONSUMO, sign * 25);// 축복 소모 효율
                break;
            default:
                break;
            }
        }
        /** 커츠의 수호 휘장 **/
        if (itemId == 900084) {
            switch (enchant) {
            case 0:
                applyStat(pc, changes, MAX_HP, sign * 5);
                break;
            case 1:
                applyStat(pc, changes, MAX_HP, sign * 10);
                break;
            case 2:
                applyStat(pc, changes, MAX_HP, sign * 15);
                break;
            case 3:
                applyStat(pc, changes, AC, sign * -1);
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 4:
                applyStat(pc, changes, AC, sign * -2);
                applyStat(pc, changes, MAX_HP, sign * 25);
                break;
            case 5:
                applyStat(pc, changes, AC, sign * -3);
                applyStat(pc, changes, MAX_HP, sign * 30);
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, AC, sign * -5);
                applyStat(pc, changes, MAX_HP, sign * 35);
                applyStat(pc, changes, DMG_RED, sign * 2);
                applyStat(pc, changes, MR, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_HP, sign * 40);
                applyStat(pc, changes, DMG_RED, sign * 3);
                applyStat(pc, changes, MR, sign * 5);
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -7);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, DMG_RED, sign * 4);
                applyStat(pc, changes, MR, sign * 7);
                break;
            default:
                break;
            }
        }
    }

    static void applyWingsAndAccessory(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 20049 || itemId == 20050 || itemId == 900056 || itemId == 900057) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 3);
                break;
            case 6:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 4);
                break;
            case 7:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 5);
                break;
            case 8:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 6);
                break;
            case 9:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 7);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 7);
                break;
            }
        }
        /** 리치의 반지 **/
        if (itemId == 900163) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                applyStat(pc, changes, SP, sign * 3);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                break;
            case 7:
                applyStat(pc, changes, SP, sign * 4);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 5);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                break;
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SP, sign * 6);
                applyStat(pc, changes, BASE_MAGIC_CRITICAL, sign * 3);// 마법 치명타
                break;
            }
        }
    }

    static void applyCrimsonArmor(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 988171) {
            switch (enchant) {
            case 0:
                break;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                applyStat(pc, changes, AC, sign * -5);
                applyStat(pc, changes, MAX_HP, sign * 50);
                applyStat(pc, changes, MAX_MP, sign * 20);
                applyStat(pc, changes, MR, sign * 20);
                applyStat(pc, changes, EVASION, sign * 2);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, AC, sign * -6);
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, MAX_MP, sign * 30);
                applyStat(pc, changes, MR, sign * 21);
                applyStat(pc, changes, EVASION, sign * 3);
                applyStat(pc, changes, PVP_DMG, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, AC, sign * -7);
                applyStat(pc, changes, MAX_HP, sign * 70);
                applyStat(pc, changes, MAX_MP, sign * 40);
                applyStat(pc, changes, MR, sign * 22);
                applyStat(pc, changes, EVASION, sign * 4);
                applyStat(pc, changes, PVP_DMG, sign * 3);
                break;
            case 10:
                applyStat(pc, changes, AC, sign * -8);
                applyStat(pc, changes, MAX_HP, sign * 80);
                applyStat(pc, changes, MAX_MP, sign * 50);
                applyStat(pc, changes, MR, sign * 23);
                applyStat(pc, changes, EVASION, sign * 5);
                applyStat(pc, changes, PVP_DMG, sign * 4);
                break;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                break;

            }
        }
    }

    static void applyVampireCloak(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 20079) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, FEAR_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, FEAR_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, FEAR_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, FEAR_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, FEAR_RESIS, sign * 6);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, FEAR_RESIS, sign * 7);
                break;
            }
        }
    }

    static void applyIrisBoots(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900155) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 머미로드의 장갑 **/
        if (itemId == 900156) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 7:
                applyStat(pc, changes, SP, sign * 2);
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 3);
                break;
            case 9:
                applyStat(pc, changes, SP, sign * 3);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SP, sign * 3);
                break;
            default:
                break;
            }
        }
        /** 쿠거의 가더 **/
        if (itemId == 900157) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, HIT, sign * 3);
                break;
            case 5:
                applyStat(pc, changes, HIT, sign * 4);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, HIT, sign * 5);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, HIT, sign * 6);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, HIT, sign * 7);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, HIT, sign * 8);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, HIT, sign * 8);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 우그누스의 가더 **/
        if (itemId == 900158) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                break;
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 6);
                applyStat(pc, changes, ALL_RESIS, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 7);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 8);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BOW_HIT, sign * 8);
                applyStat(pc, changes, ALL_RESIS, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 나이트발드의 각반 **/
        if (itemId == 900159) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 아이리스의 각반 **/
        if (itemId == 900160) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            default:
                break;
            }
        }
        /** 뱀파이어의 각반 **/
        if (itemId == 900161) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, MAGIC_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, MAGIC_HIT, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, MAGIC_HIT, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, MAGIC_HIT, sign * 5);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, MAGIC_HIT, sign * 5);
                break;
            default:
                break;
            }
        }
    }

    static void applyMagicAndRareArmor(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 202022) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 6);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SPIRIT_RESIS, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 지룡의 티셔츠 **/
        if (itemId == 900025) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, MR, sign * 4);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 5);
                break;
            case 7:
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 8);
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 11);
                applyStat(pc, changes, DMG_RED, sign * 2);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, MR, sign * 14);
                applyStat(pc, changes, DMG_RED, sign * 2);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            }
        }
        /** 화룡의 티셔츠 **/
        if (itemId == 900026) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;
            case 8:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, HIT, sign * 2);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                break;
            case 9:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                applyStat(pc, changes, HIT, sign * 4);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                applyStat(pc, changes, HIT, sign * 6);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 풍룡의 티셔츠 **/
        if (itemId == 900027) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, BOW_HIT, sign * 2);// 원거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                applyStat(pc, changes, BOW_HIT, sign * 3);// 원거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                applyStat(pc, changes, BOW_HIT, sign * 6);// 원거리 명중
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                break;
            default:
                break;
            }
        }
        /** 수룡의 티셔츠 **/
        if (itemId == 900028) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, MAGIC_HIT, sign * 1);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                break;
            case 9:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, MAGIC_HIT, sign * 3);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SP, sign * 3);
                applyStat(pc, changes, MAGIC_HIT, sign * 4);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 축복받은 지룡의 티셔츠 **/
        if (itemId == 900184) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, MR, sign * 4);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 5);
                break;
            case 7:
                applyStat(pc, changes, MR, sign * 6);
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 8);
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 11);
                applyStat(pc, changes, DMG_RED, sign * 2);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, MR, sign * 14);
                applyStat(pc, changes, DMG_RED, sign * 2);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            }
        }
        /** 축복받은 화룡의 티셔츠 **/
        if (itemId == 900185) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, HIT, sign * 1);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                applyStat(pc, changes, HIT, sign * 3);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                applyStat(pc, changes, HIT, sign * 5);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                applyStat(pc, changes, HIT, sign * 7);// 근거리 명중
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                applyStat(pc, changes, ALL_RESIS, sign * 6);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 축복받은 풍룡의 티셔츠 **/
        if (itemId == 900186) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, BOW_HIT, sign * 7);
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                applyStat(pc, changes, ALL_RESIS, sign * 6);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 축복받은 수룡의 티셔츠 **/
        if (itemId == 900187) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, SP, sign * 1);
                break;
            case 5:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 8);
                break;
            case 6:
                applyStat(pc, changes, SP, sign * 1);
                applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                break;
            case 7:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, ABILITY_RESIS, sign * 10);
                applyStat(pc, changes, ALL_RESIS, sign * 1);
                break;
            case 8:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, MAGIC_HIT, sign * 2);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 12);
                applyStat(pc, changes, ALL_RESIS, sign * 2);
                break;
            case 9:
                applyStat(pc, changes, SP, sign * 2);
                applyStat(pc, changes, MAGIC_HIT, sign * 4);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 15);
                applyStat(pc, changes, ALL_RESIS, sign * 4);
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                applyStat(pc, changes, SP, sign * 3);
                applyStat(pc, changes, MAGIC_HIT, sign * 5);// 마법 적중
                applyStat(pc, changes, ABILITY_RESIS, sign * 18);
                applyStat(pc, changes, ALL_RESIS, sign * 6);
                applyStat(pc, changes, MAX_HP, sign * 100);
                applyStat(pc, changes, PVP_DMG, sign * 1);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }

        /** 스텟부츠 리뉴얼 **/
        if (itemId == 22359 || itemId == 222308 || itemId ==222309 || itemId ==222307) {
            switch (enchant) {
            case 7:
                applyStat(pc, changes, MAX_HP, sign * 20);
                break;
            case 8:
                applyStat(pc, changes, MAX_HP, sign * 40);
                break;
            case 9:
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            case 10:
                applyStat(pc, changes, MAX_HP, sign * 60);
                applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                break;
            default:
                break;
            }
        }
        /** 고대 투사의 가더 **/
        if (itemId == 22003) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, DMG_RATE, sign * 1);
                break;
            case 5:
            case 6:
                applyStat(pc, changes, DMG_RATE, sign * 2);
                break;
            case 7:
            case 8:
                applyStat(pc, changes, DMG_RATE, sign * 3);
                break;
            case 9:
            case 10:
                applyStat(pc, changes, DMG_RATE, sign * 4);
                break;
            default:
                break;
            }
        }
        /** 고대 명궁의 가더 **/
        if (itemId == 22000) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 5:
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                break;
            case 7:
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                break;
            case 9: case 10:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                break;
            default:
                break;
            }
        }
        /** 호박 목걸이 **/
        if (itemId == 900024) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                applyStat(pc, changes, MAGIC_HIT, sign * 3);// 마법 적중
                break;
            }
        }
        /** 수령의 귀걸이 **/
        if (itemId == 900114) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                applyStat(pc, changes, MAGIC_HIT, sign * 2);
                break;
            }
        }
        /** 수호의 가더 **/
        if (itemId == 22254) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, DMG_RED, sign * 1);
                break;
            case 5:
            case 6:
                applyStat(pc, changes, DMG_RED, sign * 2);
                break;
            case 7:
            case 8:
                applyStat(pc, changes, DMG_RED, sign * 3);
                break;
            case 9:
            case 10:
                applyStat(pc, changes, DMG_RED, sign * 4);
                break;
            }
        }
    }

    static void applyHelmsAndDragonArmor(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        if (itemId == 900038 || itemId == 900054) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, MR, sign * 4);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 8);
                break;
            case 7:
                applyStat(pc, changes, MR, sign * 12);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 16);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 20);
                break;
            case 10:
                applyStat(pc, changes, MR, sign * 24);
                break;
            }
        }
        /** 발라카스의 완력 **/
        if (itemId >= 22208 && itemId <= 22211) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, PVP_DMG_RED, sign * 3);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, PVP_DMG_RED, sign * 4);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, PVP_DMG_RED, sign * 5);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, PVP_DMG_RED, sign * 6);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, PVP_DMG_RED, sign * 7);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 7);
                break;
            }
        }

        /** 신성한 엘름의 축복 **/
        if (itemId == 900035 || itemId == 900072) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, MR, sign * 4);
                break;
            case 6:
                applyStat(pc, changes, MR, sign * 8);
                break;
            case 7:
                applyStat(pc, changes, MR, sign * 12);
                break;
            case 8:
                applyStat(pc, changes, MR, sign * 16);
                break;
            case 9:
                applyStat(pc, changes, MR, sign * 20);
                break;
            case 10:
                applyStat(pc, changes, MR, sign * 24);
                break;
            default:
                break;
            }
        }
        if (itemId == 900039) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                applyStat(pc, changes, HIT, sign * 2);
                break;
            default:
                break;
            }
        }
        if (itemId == 900040) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                break;
            default:
                break;
            }
        }
        if (itemId == 900041) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                applyStat(pc, changes, MAGIC_HIT, sign * 2);// 마법 적중
                break;
            default:
                break;
            }
        }
        if (itemId == 900036 || itemId == 900073) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, HIT, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, HIT, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, HIT, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, HIT, sign * 5);
                break;
            case 10:
                applyStat(pc, changes, HIT, sign * 5);
                break;
            default:
                 if (enchant > 10) {
                     applyStat(pc, changes, HIT, sign * 5);
                    }
                break;
            }
        }
        if (itemId == 900037 || itemId == 900074) {
            switch (enchant) {
            case 5:
                applyStat(pc, changes, BOW_HIT, sign * 1);
                break;
            case 6:
                applyStat(pc, changes, BOW_HIT, sign * 2);
                break;
            case 7:
                applyStat(pc, changes, BOW_HIT, sign * 3);
                break;
            case 8:
                applyStat(pc, changes, BOW_HIT, sign * 4);
                break;
            case 9:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            case 10:
                applyStat(pc, changes, BOW_HIT, sign * 5);
                break;
            default:
                 if (enchant > 10) {
                     applyStat(pc, changes, BOW_HIT, sign * 5);
                    }
                break;
            }
        }
        /** 안타라스의 완력/예지력/인내력/마력 **/
        if (itemId >= 22196 && itemId <= 22199) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, DMG_RED, sign * 3);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, DMG_RED, sign * 4);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, DMG_RED, sign * 5);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, DMG_RED, sign * 6);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, DMG_RED, sign * 7);
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 파푸리온의 완력/예지력/인내력/마력 **/
        if (itemId >= 22200 && itemId <= 22203) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 2);
                break;
            case 6:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 3);
                break;
            case 7:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 4);
                break;
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 5);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 6);
                break;
            case 10:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 7);
                break;
            default:
                break;
            }
        }
        /** 린드비오르의 완력/예지력/인내력/마력 **/
        if (itemId >= 22204 && itemId <= 22207) {
            switch (enchant) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 2);
                break;
            case 5:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 3);
                break;
            case 6:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 4);
                break;
            case 7:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 5);
                break;
            case 8:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 6);
                break;
            case 9:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 7);
                break;
            case 10:
                applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 8);
                break;
            default:
                if (enchant > 10) {
                    applyStat(pc, changes, DRAGON_SPELL_RESIS, sign * 9);
                }
                break;
            }
        }
        /** 빛나는 마력의장갑 **/
        if (itemId == 20274) {
            switch (enchant) {
            case 0:
            case 1:
            case 3:
            case 4:
                applyStat(pc, changes, WEIGHT, sign * 300);
                break;
            default:
                if (enchant > 4) {
                    applyStat(pc, changes, WEIGHT, sign * (300 + ((enchant - 4) * 60)));
                    }
                break;
            }
        }
        /** 격분의 장갑 **/
        if (itemId == 222317) {
            switch (enchant) {
            case 7:

                applyStat(pc, changes, HIT, sign * 4);
                break;
            case 8:

                applyStat(pc, changes, HIT, sign * 5);
                break;
            case 9:

                applyStat(pc, changes, HIT, sign * 6);
                break;
            case 10:

                applyStat(pc, changes, HIT, sign * 7);
                break;
            default:
                if (enchant > 10) {

                    applyStat(pc, changes, HIT, sign * (enchant - 3));
                    }
                break;
            }
        }

        if (itemId == 22214) {
            if (enchant > 6) {
                applyStat(pc, changes, MAGIC_HIT, sign * (enchant - 6));
            }
        }
    }

    static void applyAccessoryOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int itemId = item.getItemId();
        final int type = item.getType();
        final int grade = item.getGrade();
        if (itemId == 20017) {
             int cc = enchant - 4;
             if (enchant > 4) {
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * enchant);
                        applyStat(pc, changes, MR, sign * (cc * 3));
                    }
            }

//		if (itemId == 22200 || itemId == 22201 || itemId == 22202 || itemId == 22203) {// 파푸리온
//		}else if(itemId >= 22208 && itemId <= 22211 || itemId == 922208)

        //TODO (장신구) 목걸이,귀걸이 인챈트 리뉴얼
        if (type >= 8 && type <= 12) {
            if ((grade >= 0 && grade <= 2) && type == 8 ) { //목걸이
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 5);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    break;
                case 5:

                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, MR, sign * 1);
                    break;
                case 6:

                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, MR, sign * 3);
                    break;
                case 7:

                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, MR, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 2);
                    break;
                case 8:

                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, MR, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 3);
                    break;
                case 9:

                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, MR, sign * 10);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 4);
                    break;
                case 10:

                    applyStat(pc, changes, MAX_HP, sign * 100);
                    applyStat(pc, changes, MR, sign * 12);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 4);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    break;
                }
            } else if ((grade >= 0 && grade <= 2) && type == 12) { //귀걸이
                    switch (enchant) {
                    case 1:
                        applyStat(pc, changes, MAX_HP, sign * 5);
                        break;
                    case 2:
                        applyStat(pc, changes, MAX_HP, sign * 10);
                        break;
                    case 3:
                        applyStat(pc, changes, MAX_HP, sign * 20);
                        break;
                    case 4:
                        applyStat(pc, changes, MAX_HP, sign * 30);
                        break;
                    case 5:

                        applyStat(pc, changes, MAX_HP, sign * 40);

                        break;
                    case 6:

                        applyStat(pc, changes, MAX_HP, sign * 40);

                        break;
                    case 7:

                        applyStat(pc, changes, MAX_HP, sign * 50);
                        applyStat(pc, changes, ABILITY_RESIS, sign * 2);
                        break;
                    case 8:

                        applyStat(pc, changes, MAX_HP, sign * 50);
                        applyStat(pc, changes, ABILITY_RESIS, sign * 3);
                        break;
                    case 9:

                        applyStat(pc, changes, MAX_HP, sign * 60);
                        applyStat(pc, changes, ABILITY_RESIS, sign * 4);
                        break;
                    case 10:

                        applyStat(pc, changes, MAX_HP, sign * 70);
                        applyStat(pc, changes, ABILITY_RESIS, sign * 4);
                        applyStat(pc, changes, FEAR_RESIS, sign * 2);
                        applyStat(pc, changes, ALL_RESIS, sign * 1);
                        break;
                    }
                // TODO (장신구) 반지 인챈트 리뉴얼
            } else

                if (((grade >= 0 && grade <= 2) || grade == 5) && (type == 9 || type == 11)) {
                    switch (enchant) {
                    case 1:
                        applyStat(pc, changes, MAX_HP, sign * 5);
                        break;
                    case 2:
                        applyStat(pc, changes, MAX_HP, sign * 10);
                        break;
                    case 3:
                        applyStat(pc, changes, MAX_HP, sign * 20);
                        break;
                    case 4:
                        applyStat(pc, changes, MAX_HP, sign * 30);
                        break;
                    case 5:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 1);
                        applyStat(pc, changes, MAX_HP, sign * 40);
                        break;
                    case 6:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 2);
                        applyStat(pc, changes, MAX_HP, sign * 40);
                        applyStat(pc, changes, MR, sign * 1);
                        applyStat(pc, changes, PVP_DMG, sign * 1);
                        break;
                    case 7:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 3);
                        applyStat(pc, changes, MAX_HP, sign * 50);
                        if (itemId != 900163) {
                        applyStat(pc, changes, SP, sign * 1);
                        }
                        applyStat(pc, changes, MR, sign * 3);
                        applyStat(pc, changes, PVP_DMG, sign * 2);
                        break;
                    case 8:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 4);
                        applyStat(pc, changes, MAX_HP, sign * 50);
                        if (itemId != 900163) {
                        applyStat(pc, changes, SP, sign * 2);
                        }
                        applyStat(pc, changes, MR, sign * 5);
                        applyStat(pc, changes, PVP_DMG, sign * 3);
                        break;
                    case 9:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 5);
                        applyStat(pc, changes, MAX_HP, sign * 60);
                        if (itemId != 900163) {
                        applyStat(pc, changes, SP, sign * 3);
                        }
                        applyStat(pc, changes, MR, sign * 7);
                        applyStat(pc, changes, PVP_DMG, sign * 5);
                        break;
                    case 10:
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 6);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 6);
                        applyStat(pc, changes, MAX_HP, sign * 70);
                        if (itemId != 900163) {
                        applyStat(pc, changes, SP, sign * 4);
                        }
                        applyStat(pc, changes, MR, sign * 9);
                        applyStat(pc, changes, PVP_DMG, sign * 7);
                        applyStat(pc, changes, ALL_RESIS, sign * 1);
                        break;
                    }
                    // TODO (장신구) 벨트 인챈트 리뉴얼
                } else

                if ((grade >= 0 && grade <= 2) && (type == 10)) {
                    if (itemId == 900007 || itemId == 500012) {
                        applyStat(pc, changes, PVP_DMG_RED, sign * 2);
                    }
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_MP, sign * 5);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_MP, sign * 10);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_MP, sign * 20);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_MP, sign * 30);
                    break;
                case 5:
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    applyStat(pc, changes, MAX_MP, sign * 40);
                    break;
                case 6:
                    applyStat(pc, changes, DMG_RED, sign * 2);
                    applyStat(pc, changes, MAX_MP, sign * 40);
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, PVP_DMG_RED, sign * 1);
                    break;
                case 7:
                    applyStat(pc, changes, DMG_RED, sign * 3);
                    applyStat(pc, changes, MAX_MP, sign * 50);
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, PVP_DMG_RED, sign * 3);
                    break;
                case 8:
                    applyStat(pc, changes, DMG_RED, sign * 4);
                    applyStat(pc, changes, MAX_MP, sign * 50);
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, PVP_DMG_RED, sign * 5);
                    break;
                case 9:
                    applyStat(pc, changes, DMG_RED, sign * 5);
                    applyStat(pc, changes, MAX_MP, sign * 60);
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, PVP_DMG_RED, sign * 7);
                    break;
                case 10:
                    applyStat(pc, changes, DMG_RED, sign * 6);
                    applyStat(pc, changes, MAX_MP, sign * 70);
                    applyStat(pc, changes, MAX_HP, sign * 100);
                    applyStat(pc, changes, PVP_DMG_RED, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    break;
                }

            } else if (grade == 3 && itemId == 22224 || itemId == 22225) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, PVP_DMG, sign * 2);
                    break;
                default:
                    break;
                }
            } else if (grade == 3 && itemId == 22227) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, PVP_DMG, sign * 2);
                    break;
                default:
                    break;
                }
                // 스냅퍼의 마법저항 반지 착용
            } else if (grade == 3 && itemId == 22228) {
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -2);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -6);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 1);
                    applyStat(pc, changes, PVP_DMG, sign * 3);
                    applyStat(pc, changes, MR, sign * 9);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, AC, sign * -8);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 7);
                    applyStat(pc, changes, MR, sign * 12);
                    break;
                default:
                    break;
                }

                //TODO 스냅퍼의 체력 반지 리뉴얼
            } else if (grade == 3 && itemId == 22226) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);

                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    applyStat(pc, changes, DMG_RED, sign * 2);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -6);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 1);
                    applyStat(pc, changes, PVP_DMG, sign * 3);
                    applyStat(pc, changes, DMG_RED, sign * 3);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 55);
                    applyStat(pc, changes, AC, sign * -8);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 7);
                    applyStat(pc, changes, DMG_RED, sign * 5);
                    break;
                default:
                    break;
                }
            } else if (grade == 3 && itemId == 222290) { // 지혜의 반지
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    break;
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 5);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, SP, sign * 1);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, SP, sign * 2);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, SP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, MAX_MP, sign * 30);
                    applyStat(pc, changes, AC, sign * -6);
                    applyStat(pc, changes, SP, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 1);
                    applyStat(pc, changes, PVP_DMG, sign * 3);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, MAX_MP, sign * 70);
                    applyStat(pc, changes, AC, sign * -8);
                    applyStat(pc, changes, SP, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 7);
                    break;
                default:
                    break;
                }
            } else if (grade == 3 && itemId == 222291) { // 용사의 반지
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 2:
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 5);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    applyStat(pc, changes, AC, sign * -4);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 1);
                    applyStat(pc, changes, BOW_HIT, sign * 1);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 2);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    applyStat(pc, changes, BOW_HIT, sign * 2);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 3);
                    applyStat(pc, changes, BOW_HIT, sign * 3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -6);
                    applyStat(pc, changes, HIT, sign * 5);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                    applyStat(pc, changes, BOW_HIT, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 1);
                    applyStat(pc, changes, PVP_DMG, sign * 3);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -8);
                    applyStat(pc, changes, HIT, sign * 7);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, BOW_HIT, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 7);
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 체력 반지 **/
            } else if (grade == 3 && itemId == 222332) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 4);
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 55);
                    applyStat(pc, changes, AC, sign * -5);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    applyStat(pc, changes, DMG_RED, sign * 2);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 65);
                    applyStat(pc, changes, AC, sign * -7);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 5);
                    applyStat(pc, changes, DMG_RED, sign * 5);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 75);
                    applyStat(pc, changes, AC, sign * -10);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 9);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 4);
                    applyStat(pc, changes, PVP_DMG, sign * 9);
                    applyStat(pc, changes, DMG_RED, sign * 7);
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 회복 반지/축복받은 스냅퍼의 집중 반지 **/
            } else if (grade == 3 && itemId == 222330 || itemId == 222331) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, PVP_DMG, sign * 2);
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 마나 반지 **/
            } else if (grade == 3 && itemId == 222333) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 5);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, PVP_DMG, sign * 2);
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 마법 저항 반지 **/
            } else if (grade == 3 && itemId == 222334) {
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -1);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -2);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    applyStat(pc, changes, MR, sign * 7);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 45);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, MR, sign * 8);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -5);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, MR, sign * 9);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -7);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, MR, sign * 12);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 5);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, AC, sign * -10);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 9);
                    applyStat(pc, changes, MR, sign * 15);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 4);
                    applyStat(pc, changes, PVP_DMG, sign * 9);
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 지혜 반지 **/
            } else if (grade == 3 && itemId == 222335) {
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    break;
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 5);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, SP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, SP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 35);
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, AC, sign * -3);
                    applyStat(pc, changes, SP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    applyStat(pc, changes, MAGIC_HIT, sign * 1);// 마법 적중
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, MAX_MP, sign * 30);
                    applyStat(pc, changes, AC, sign * -5);
                    applyStat(pc, changes, SP, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    applyStat(pc, changes, MAGIC_HIT, sign * 2);// 마법 적중
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, MAX_MP, sign * 35);
                    applyStat(pc, changes, AC, sign * -7);
                    applyStat(pc, changes, SP, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 5);
                    applyStat(pc, changes, MAGIC_HIT, sign * 5);// 마법 적중
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, MAX_MP, sign * 80);
                    applyStat(pc, changes, AC, sign * -10);
                    applyStat(pc, changes, SP, sign * 9);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 4);
                    applyStat(pc, changes, PVP_DMG, sign * 9);
                    applyStat(pc, changes, MAGIC_HIT, sign * -7);// 마법 적중
                    break;
                default:
                    break;
                }
                /** 축복받은 스냅퍼의 용사 반지 **/
            } else if (grade == 3 && itemId == 222336) {
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 2:
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 15);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 1);
                    applyStat(pc, changes, BOW_HIT, sign * 1);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 2);
                    applyStat(pc, changes, BOW_HIT, sign * 2);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 25);
                    applyStat(pc, changes, AC, sign * -4);
                    applyStat(pc, changes, HIT, sign * 3);
                    applyStat(pc, changes, BOW_HIT, sign * 3);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 3);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 5);
                    break;
                case 7:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -5);
                    applyStat(pc, changes, HIT, sign * 4);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 4);
                    applyStat(pc, changes, BOW_HIT, sign * 4);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 7);
                    applyStat(pc, changes, PVP_DMG, sign * 1);
                    break;
                case 8:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    applyStat(pc, changes, AC, sign * -7);
                    applyStat(pc, changes, HIT, sign * 7);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                    applyStat(pc, changes, BOW_HIT, sign * 7);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 2);
                    applyStat(pc, changes, PVP_DMG, sign * 5);
                    break;
                case 9:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, AC, sign * -10);
                    applyStat(pc, changes, HIT, sign * 9);
                    applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 9);
                    applyStat(pc, changes, BOW_HIT, sign * 9);
                    applyStat(pc, changes, ABILITY_RESIS, sign * 9);
                    applyStat(pc, changes, FEAR_RESIS, sign * 4);
                    applyStat(pc, changes, PVP_DMG, sign * 9);
                    break;
                default:
                    break;
                }
            } else if(itemId == 22229){
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 30);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, DMG_RED, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_HP, sign * 70);
                    applyStat(pc, changes, DMG_RED, sign * 3);
                    applyStat(pc, changes, AC, sign * -7);
                    break;
                case 7:
                    applyStat(pc, changes, BOW_HIT, sign * 1);
                    applyStat(pc, changes, HIT, sign * 1);
                    applyStat(pc, changes, MAX_HP, sign * 80);
                    applyStat(pc, changes, DMG_RED, sign * 4);
                    applyStat(pc, changes, AC, sign * -8);
                    break;
                case 8:
                    applyStat(pc, changes, BOW_HIT, sign * 3);
                    applyStat(pc, changes, HIT, sign * 3);
                    applyStat(pc, changes, MAX_HP, sign * 90);
                    applyStat(pc, changes, DMG_RED, sign * 7);
                    applyStat(pc, changes, AC, sign * -9);
                    break;
                case 9:
                    applyStat(pc, changes, BOW_HIT, sign * 5);
                    applyStat(pc, changes, HIT, sign * 5);
                    applyStat(pc, changes, MAX_HP, sign * 100);
                    applyStat(pc, changes, DMG_RED, sign * 7);
                    applyStat(pc, changes, AC, sign * -10);
                    break;
                default:
                    break;
                }
            } else if(itemId == 222337){
                switch (enchant) {
                case 1:
                    applyStat(pc, changes, MAX_HP, sign * 10);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_HP, sign * 20);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_HP, sign * 40);
                    applyStat(pc, changes, DMG_RED, sign * 1);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, DMG_RED, sign * 2);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_HP, sign * 60);
                    applyStat(pc, changes, DMG_RED, sign * 3);
                    applyStat(pc, changes, AC, sign * -7);
                    break;
                case 6:
                    applyStat(pc, changes, BOW_HIT, sign * 1);
                    applyStat(pc, changes, HIT, sign * 1);
                    applyStat(pc, changes, MAX_HP, sign * 70);
                    applyStat(pc, changes, DMG_RED, sign * 4);
                    applyStat(pc, changes, AC, sign * -8);
                    break;
                case 7:
                    applyStat(pc, changes, BOW_HIT, sign * 3);
                    applyStat(pc, changes, HIT, sign * 3);
                    applyStat(pc, changes, MAX_HP, sign * 80);
                    applyStat(pc, changes, DMG_RED, sign * 5);
                    applyStat(pc, changes, AC, sign * -9);
                    break;
                case 8:
                    applyStat(pc, changes, BOW_HIT, sign * 5);
                    applyStat(pc, changes, HIT, sign * 5);
                    applyStat(pc, changes, MAX_HP, sign * 140);
                    applyStat(pc, changes, DMG_RED, sign * 7);
                    applyStat(pc, changes, AC, sign * -10);
                    break;
                case 9:
                    applyStat(pc, changes, BOW_HIT, sign * 7);
                    applyStat(pc, changes, HIT, sign * 7);
                    applyStat(pc, changes, MAX_HP, sign * 150);
                    applyStat(pc, changes, DMG_RED, sign * 9);
                    applyStat(pc, changes, AC, sign * -11);
                    break;
                default:
                    break;
                }
            } else if(itemId == 22231){
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MR, sign * 2);
                    break;
                case 1:
                    applyStat(pc, changes, MAX_MP, sign * 10);
                    applyStat(pc, changes, MR, sign * 5);
                    break;
                case 2:
                    applyStat(pc, changes, MAX_MP, sign * 15);
                    applyStat(pc, changes, MR, sign * 6);
                    break;
                case 3:
                    applyStat(pc, changes, MAX_MP, sign * 30);
                    applyStat(pc, changes, MR, sign * 7);
                    applyStat(pc, changes, SP, sign * 1);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_MP, sign * 35);
                    applyStat(pc, changes, MR, sign * 8);
                    applyStat(pc, changes, SP, sign * 1);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_MP, sign * 50);
                    applyStat(pc, changes, MR, sign * 9);
                    applyStat(pc, changes, SP, sign * 2);
                    break;
                case 6:
                    applyStat(pc, changes, MAX_MP, sign * 55);
                    applyStat(pc, changes, MR, sign * 10);
                    applyStat(pc, changes, SP, sign * 2);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 7:
                    applyStat(pc, changes, MAGIC_HIT, sign * 1);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 70);
                    applyStat(pc, changes, MR, sign * 12);
                    applyStat(pc, changes, SP, sign * 3);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 8:
                    applyStat(pc, changes, MAGIC_HIT, sign * 5);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 95);
                    applyStat(pc, changes, MR, sign * 17);
                    applyStat(pc, changes, SP, sign * 5);
                    applyStat(pc, changes, MAX_HP, sign * 50);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 9:
                    applyStat(pc, changes, MAGIC_HIT, sign * 9);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 125);
                    applyStat(pc, changes, MAX_HP, sign * 100);
                    applyStat(pc, changes, MR, sign * 20);
                    applyStat(pc, changes, SP, sign * 7);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                default:
                    break;
                }
            } else if(itemId == 222339){
                switch (enchant) {
                case 0:
                    applyStat(pc, changes, MAX_MP, sign * 10);

                    break;
                case 1:
                    applyStat(pc, changes, MAX_MP, sign * 10);

                    break;
                case 2:
                    applyStat(pc, changes, MAX_MP, sign * 15);

                    break;
                case 3:
                    applyStat(pc, changes, MAX_MP, sign * 35);
                    applyStat(pc, changes, MR, sign * 8);
                    applyStat(pc, changes, SP, sign * 1);
                    break;
                case 4:
                    applyStat(pc, changes, MAX_MP, sign * 50);
                    applyStat(pc, changes, MR, sign * 9);
                    applyStat(pc, changes, SP, sign * 2);
                    break;
                case 5:
                    applyStat(pc, changes, MAX_MP, sign * 55);
                    applyStat(pc, changes, MR, sign * 10);
                    applyStat(pc, changes, SP, sign * 2);
                    applyStat(pc, changes, AC, sign * -1);
                    break;
                case 6:
                    applyStat(pc, changes, MAGIC_HIT, sign * 1);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 70);
                    applyStat(pc, changes, MR, sign * 12);
                    applyStat(pc, changes, SP, sign * 3);
                    applyStat(pc, changes, AC, sign * -2);
                    break;
                case 7:
                    applyStat(pc, changes, MAGIC_HIT, sign * 3);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 95);
                    applyStat(pc, changes, MR, sign * 15);
                    applyStat(pc, changes, SP, sign * 3);
                    applyStat(pc, changes, AC, sign * -3);
                    break;
                case 8:
                    applyStat(pc, changes, MAGIC_HIT, sign * 7);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 125);
                    applyStat(pc, changes, MAX_HP, sign * 100);
                    applyStat(pc, changes, MR, sign * 20);
                    applyStat(pc, changes, SP, sign * 5);
                    applyStat(pc, changes, AC, sign * -4);
                    break;
                case 9:
                    applyStat(pc, changes, MAGIC_HIT, sign * 12);// 마법 적중
                    applyStat(pc, changes, MAX_MP, sign * 155);
                    applyStat(pc, changes, MAX_HP, sign * 150);
                    applyStat(pc, changes, MR, sign * 23);
                    applyStat(pc, changes, SP, sign * 9);
                    applyStat(pc, changes, AC, sign * -4);
                    break;
                default:
                    break;
                }

                /** 검귀 ac처리부분 재수정 **/
            } else if (itemId == 222340 || itemId == 222341) {
                int ac = enchant;

                if (item.getBless() == 0 && enchant >= 3) {
                    ac += 1;
                }
                applyStat(pc, changes, AC, sign * -ac);
                if (enchant >= 4 && enchant <= 7) {
                    if (item.getBless() != 0 ){ // 걍
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * (enchant - 3));
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * (enchant - 3));
                    } else {
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * (enchant - 2));
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * (enchant - 2));
                    }
                } else if (enchant == 8) {
                    if (item.getBless() != 0 ){ // 걍
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 7);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 7);
                    } else {
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 9);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 9);
                    }
                } else if (enchant >= 9) {
                    if (item.getBless() != 0 ){ // 걍
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 9);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 9);
                    } else {
                        applyStat(pc, changes, MELEE_DAMAGE_UP, sign * 12);
                        applyStat(pc, changes, BOW_DAMAGE_UP, sign * 12);
                    }
                }
            }
        }
    }
}
