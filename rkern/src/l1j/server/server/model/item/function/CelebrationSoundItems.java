package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.templates.L1EtcItem;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.전사스킬;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook1;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook3;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook4;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook5;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook6;
import static l1j.server.server.model.item.function.SpellBookItems.useElfSpellBook;
import static l1j.server.server.model.item.function.SpellBookItems.useSpellBook;
import static l1j.server.server.model.item.function.FurniturePlacementItems.useFurnitureItem;

/** Default-path item domain, extracted without changing action branch bodies. */
final class CelebrationSoundItems {
    private CelebrationSoundItems() {}

    static ItemActionResult fireworks(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();

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
                try {
                    try {
                        SC_SkillSound.sendPacked(pc, fireworkSound);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed fireworkSound send failed: " + pc.getId(), outputFailure);
                    }
                    try {
                        SC_SkillSound.broadcastPacked(pc, fireworkSound);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed fireworkSound broadcast failed: " + pc.getId(), outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });

                return ItemActionResult.STOP;
    }

    static ItemActionResult alphabetSound(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();


            int soundid = itemId - 34946;
            final long alphabetSound = SC_SkillSound.pack(pc.getId(), soundid);
            UseDurableConsume(pc, item, () -> {
                try {
                    try {
                        SC_SkillSound.sendPacked(pc, alphabetSound);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed alphabetSound send failed: " + pc.getId(), outputFailure);
                    }
                    try {
                        SC_SkillSound.broadcastPacked(pc, alphabetSound);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed alphabetSound broadcast failed: " + pc.getId(), outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });

                return ItemActionResult.STOP;
    }
}
