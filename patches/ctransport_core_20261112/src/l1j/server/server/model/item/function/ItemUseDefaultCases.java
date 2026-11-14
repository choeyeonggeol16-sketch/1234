package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.templates.L1EtcItem;
import static l1j.server.server.model.item.function.ItemUseCreature.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemUseDurable.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemUseMiscActions.전사스킬;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.SpellBook1;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.SpellBook3;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.SpellBook4;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.SpellBook5;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.SpellBook6;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.useElfSpellBook;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.useSpellBook;
import static l1j.server.server.model.item.function.ItemUseWorldHelpers.useFurnitureItem;

/** Item-specific Default effects; selected once by ItemUseDispatcher. */
final class ItemUseDefaultCases {
    private ItemUseDefaultCases() {}

    static ItemUseResult use(ItemUseContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();
        if (itemId >= 40136 && itemId <= 40161 || itemId == 410027) {
            int soundid = 3198;
            if (itemId == 40154) {
                soundid = 3198;
            } else if (itemId == 40152) {
                soundid = 2031;
            } else if (itemId == 40141) {
                soundid = 2028;
            } else if (itemId == 40160) {
                soundid = 2030;
            } else if (itemId == 40145) {
                soundid = 2029;
            } else if (itemId == 40159) {
                soundid = 2033;
            } else if (itemId == 40151) {
                soundid = 2032;
            } else if (itemId == 40161) {
                soundid = 2037;
            } else if (itemId == 40142) {
                soundid = 2036;
            } else if (itemId == 40146) {
                soundid = 2039;
            } else if (itemId == 40148) {
                soundid = 2043;
            } else if (itemId == 40143) {
                soundid = 2041;
            } else if (itemId == 40156) {
                soundid = 2042;
            } else if (itemId == 40139) {
                soundid = 2040;
            } else if (itemId == 40137) {
                soundid = 2047;
            } else if (itemId == 40136) {
                soundid = 2046;
            } else if (itemId == 40138) {
                soundid = 2048;
            } else if (itemId == 40140) {
                soundid = 2051;
            } else if (itemId == 40144) {
                soundid = 2053;
            } else if (itemId == 40147) {
                soundid = 2045;
            } else if (itemId == 40149) {
                soundid = 2034;
            } else if (itemId == 40150) {
                soundid = 2055;
            } else if (itemId == 40153) {
                soundid = 2038;
            } else if (itemId == 40155) {
                soundid = 2044;
            } else if (itemId == 40157) {
                soundid = 2035;
            } else if (itemId == 40158) {
                soundid = 2049;
            } else {
                soundid = 3198;
            }

            final long fireworkSound = SC_SkillSound.pack(pc.getId(), soundid);
            UseDurableConsume(pc, item, () -> {
                SC_SkillSound.sendPacked(pc, fireworkSound);
                SC_SkillSound.broadcastPacked(pc, fireworkSound);
            });

        } else if ((itemId >= 40859 && itemId <= 40898) && itemId != 40863) {

            int skillid = itemId - 40858;

            if(pc.isSkillDelay()) {
                SC_ServerMessage.send(pc, 281);
                pc.teleportunLock(skillId);
                return ItemUseResult.STOP;
            }

            int useType = item.getItem().getUseType();
            if (useType == 30 || useType == 0) {
                spellTargetId = pc.getId();
            } else {
                if (spellTargetId == pc.getId() || spellTargetId <= 0) {
                    SC_ServerMessage.send(pc, 281);
                    return ItemUseResult.STOP;
                }
            }
            final int spellTarget = spellTargetId;
            final int spellX = spellTargetX;
            final int spellY = spellTargetY;
            UseDurableConsume(pc, item, () -> {
                cancelAbsoluteBarrier(pc);
                L1SkillUse.cast(pc, skillid, spellTarget, spellX, spellY, null, 0, L1SkillUse.TYPE_SPELLSC);
            });

        } else if (itemId >= 41357 && itemId <= 41382) {

            int soundid = itemId - 34946;
            final long alphabetSound = SC_SkillSound.pack(pc.getId(), soundid);
            UseDurableConsume(pc, item, () -> {
                SC_SkillSound.sendPacked(pc, alphabetSound);
                SC_SkillSound.broadcastPacked(pc, alphabetSound);
            });
        } else if ((itemId >= 41277 && itemId <= 41292) || (itemId >= 49049 && itemId <= 49064) || (itemId >= 210048 && itemId <= 210063)
                || (itemId >= 30051 && itemId <= 30054) || (itemId >= 4100156 && itemId <= 4100159) || (itemId >= 3000129 && itemId <= 3000130)
                || (itemId >= 42650 && itemId <= 42653)) {
            L1Cooking.useCookingItem(pc, item);
        } else if (itemId >= 41383 && itemId <= 41400) {
            useFurnitureItem(pc, itemId, itemObjectId);
        } else if (itemId > 40169 && itemId <= 40223 || itemId >= 45000 && itemId <= 45022 || itemId == 40225
                || itemId == 3000095) {
            useSpellBook(pc, item, itemId);

        } else if (itemId == 40226  ||itemId == 40227  ||itemId == 40229  ||itemId == 40230
                || itemId == 5560 || itemId == 3000090 || itemId == 3000089) {
            if (pc.isCrown() || pc.isGm()) {
                if (itemId == 40226 && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);

                } else if (itemId == 40227 && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);

                } else if ((itemId == 40232) && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);
                } else if (itemId == 40230 && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);
                } else if (itemId == 40229 && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);
                } else if (itemId == 5560 ||itemId == 3000090 || itemId == 3000089 && pc.getLevel() >= 1) {
                    SpellBook4(pc, item, client);

                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }

        } else if ((itemId >= 40232 && itemId <= 40264) || (itemId >= 41149 && itemId <= 41153)
                || itemId == 3000091 || (itemId >= 3000511 && itemId <= 3000513) || itemId == 4100295) {
            useElfSpellBook(pc, item, itemId);
        } else if (itemId > 40264 && itemId < 40280 || itemId == 5559 || itemId == 4100103
                || itemId == 4100104 || itemId == 4100105) {
            if (pc.isDarkelf() || pc.isGm()) {
                if (itemId >= 40265 && itemId <= 40269 && pc.getLevel() >= 1) {
                    SpellBook1(pc, item, client);
                } else if (((itemId == 40270) || (itemId >= 40272 && itemId <= 40274) && pc.getLevel() >= 1)) {
                    SpellBook1(pc, item, client);
                } else if (itemId >= 40275 && itemId <= 40278 && pc.getLevel() >= 1) {
                    SpellBook1(pc, item, client);
                } else if (itemId == 5559 && pc.getLevel() >= 1) {
                    SpellBook1(pc, item, client);
                } else if ((itemId == 4100103 || itemId == 4100104) && pc.getLevel() >= 1) {
                    SpellBook1(pc, item, client);
                } else if ((itemId == 4100105) && pc.getLevel() >= 1) {
                    SpellBook1(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);

            }

        } else if (itemId >= 40164 && itemId <= 40166 || itemId == 41148
                || itemId == 3000092 || itemId == 4100101 || itemId == 4100102) {
            if (pc.isKnight() || pc.isGm()) {
                if (itemId == 40165 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);

                } else if (itemId == 40164 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if ((itemId == 40166) && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);

                } else if (itemId == 4100101 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if (itemId == 41148 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if (itemId == 4100102 || itemId == 3000092 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
        } else if ((itemId >= 210020 && itemId <= 210034) || itemId == 4100106) {
            if (pc.isDragonknight() || pc.isGm()) {
                if (itemId >= 210020 && itemId <= 210023 && pc.getLevel() >= 1) {
                    SpellBook5(pc, item, client);
                } else if (itemId >= 210024 && itemId <= 210031 && pc.getLevel() >= 1) {
                    SpellBook5(pc, item, client);
                } else if (itemId >= 210032 && itemId <= 210034 && pc.getLevel() >= 1) {
                    SpellBook5(pc, item, client);
                } else if (itemId == 4100106 && pc.getLevel() >= 1) {
                    SpellBook5(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
        } else if ((itemId >= 210000 && itemId <= 210019) || itemId == 3000096 || itemId == 4100109 || itemId == 5618 || itemId == 5617) {
            if (pc.isBlackwizard() || pc.isGm()) {
                if (itemId >= 210000 && itemId <= 210004 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (((itemId >= 210005 && itemId <= 210007) ||  (itemId == 210009)) && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (((itemId >= 210010 && itemId <= 210012) ||  (itemId == 210014))&& pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId >= 210015 && itemId <= 210019 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 4100109 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 3000096 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 5618 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 5617 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
        } else if (itemId >= 210123 && itemId <= 210132 || itemId == 3000094 || itemId == 210121 ) {

            if (pc.isWarrior()) {

                if (itemId == 210121 && pc.getLevel() >= 1) {
                    전사스킬(pc, item, false);
                } else if ( itemId == 210129 && pc.getLevel() >= 1) {
                    전사스킬(pc, item, false);
                } else if (itemId == 210130 || itemId == 210123 && pc.getLevel() >= 1) {
                    전사스킬(pc, item, false);
                } else if (itemId == 210131 || itemId == 210125 && pc.getLevel() >= 1) {
                    전사스킬(pc, item, false);
                } else if (itemId == 3000094 && pc.getLevel() >= 1) {
                    전사스킬(pc, item, false);
                } else {
                    전사스킬(pc, item, true);
                }

            }
        } else {
            int locX = ((L1EtcItem) item.getItem()).get_locx();
            int locY = ((L1EtcItem) item.getItem()).get_locy();
            short mapId = ((L1EtcItem) item.getItem()).get_mapid();
            if (locX != 0 && locY != 0) {

                if (pc.hasSkillEffect(L1SkillId.이터디스) || pc.hasSkillEffect(L1SkillId.버닝샷)  ) {
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
                    return ItemUseResult.STOP;
                }

                if (pc.getMap().isEscapable() || pc.isGm()) {
                    UseDurableConsume(pc, item, () -> {
                        pc.start_teleport(locX, locY, mapId, pc.getHeading(), 169, true, pc.getAutoHunt());
                        cancelAbsoluteBarrier(pc);
                    });
                } else {
                    SC_ServerMessage.send(pc, 647);
                    cancelAbsoluteBarrier(pc);
                }
            } else {
                if (item.getCount() < 1) {
                    SC_ServerMessage.send(pc, 329, item.getLogName());
                }
            }
        }
        return ItemUseResult.COMPLETE;
    }
}
