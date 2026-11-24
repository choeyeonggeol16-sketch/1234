package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.BookmarkSlot;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;

public final class TeleportUseService {

    private TeleportUseService() {}

    /** Ordinary GM teleport remains available; native PSS follows player map rules. */


    /** Ordinary GM teleport; keeps the original client/PSS checks. */
    public static boolean canBypassMapRestrictions(L1PcInstance pc) {
        return TeleportItemRules.canBypassMapRestrictions(pc);
    }

    // --- allocations 제거용 상수 ---
    private static final int EQUIP_PRECISE_TELEPORT[] = { 20288, 900111 };

    // =========================================================
    // Public API (아이템용)
    // =========================================================
    public static void clickItem(L1PcInstance pc, int itemId,
                                 int bookmarkX, int bookmarkY, short bookmarkMapId,
                                 L1ItemInstance consumeItem) {
        if (pc == null) return;

        try {
            if (!TeleportItemRules.preCheckTeleportState(pc)) return;

            final boolean hasBookmark = (bookmarkX != 0);

            if (hasBookmark) {
                if (!TeleportItemRules.isValidBookmark(pc, bookmarkX, bookmarkY, bookmarkMapId)) {
                    if (itemId == 40100) {
                        if (!TeleportItemRules.canRandomTeleportHere(pc)) {
                            return;
                        }
                        TeleportRandomTravel.doRandomTeleport(pc, 0, consumeItem);
                    } else {
                        SC_ServerMessage.send(pc, 79);
                        pc.tpEndFinish();
                    }
                    return;
                }

                if (!(pc.getMap().isEscapable() || TeleportUseService.canBypassMapRestrictions(pc))) {
                    SC_ServerMessage.send(pc, 79);
                    pc.tpEndFinish();
                    return;
                }
                if (consumeItem != null) {
                    ItemReceiptOperations.UseDurableConsume(pc, consumeItem, () -> {
                        try {
                            pc.start_teleport(bookmarkX, bookmarkY, bookmarkMapId, pc.getHeading(), 169, true, false);
                        } finally {
                            try {
                                pc.cancelAbsoluteBarrier();
                                pc.cancelMoebius();
                            } finally {
                                ItemActionCompletion.finishCommittedConsumable(pc, consumeItem);
                            }
                        }
                    });
                } else {
                    pc.start_teleport(bookmarkX, bookmarkY, bookmarkMapId, pc.getHeading(), 169, true, false);
                }
                return;
            }

            if (!TeleportItemRules.canRandomTeleportHere(pc)) {
                            return;
                        }
            TeleportRandomTravel.doRandomTeleport(pc, 0, consumeItem);

        } finally {
            if (consumeItem == null) {
                pc.cancelAbsoluteBarrier();
                pc.cancelMoebius();
            }
        }
    }

    // =========================================================
    // Public API (스킬 TELEPORT/MASS_TELEPORT용)
    // =========================================================
    public static void handleSkillTeleport(L1PcInstance pc,
                                           int skillId,
                                           int bookmarkX, int bookmarkY, short bookmarkMapId) {
        if (pc == null) return;

        try {
            if (!TeleportItemRules.preCheckTeleportState(pc)) 
            	return;

            final boolean hasBookmark = (bookmarkX != 0);

            if (hasBookmark) {
                if (!TeleportItemRules.isValidBookmark(pc, bookmarkX, bookmarkY, bookmarkMapId)
                        || !(pc.getMap().isEscapable() || TeleportUseService.canBypassMapRestrictions(pc))) {
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
                    SC_ServerMessage.send(pc, 79);
                    pc.tpEndFinish();
                    return;
                }

                final L1Map map = L1WorldMap.getInstance().getMap(bookmarkMapId);

                if (skillId == L1SkillId.MASS_TELEPORT) {
                    TeleportClanBookmarkTravel.teleportClanMembersAroundBookmarkMass(pc, bookmarkX, bookmarkY, bookmarkMapId, map);
                }
                final boolean precise = pc.getInventory().checkEquippedAtOnce(EQUIP_PRECISE_TELEPORT);
                if (precise) {
                    pc.start_teleport(bookmarkX, bookmarkY, bookmarkMapId, pc.getHeading(), 169, true, true);
                } else {
                    int nx = bookmarkX + ThreadLocalRandom.current().nextInt(15);
                    int ny = bookmarkY + ThreadLocalRandom.current().nextInt(15);
                    if (map.isInMap(nx, ny) && map.isPassable(nx, ny)) {
                        pc.start_teleport(nx, ny, bookmarkMapId, pc.getHeading(), 169, true, true);
                    } else {
                        pc.start_teleport(bookmarkX, bookmarkY, bookmarkMapId, pc.getHeading(), 169, true, true);
                    }
                }
                return;
            }

            if (!TeleportItemRules.canRandomTeleportHere(pc)) {
                            return;
                        }
            TeleportRandomTravel.doRandomTeleport(pc, skillId, null);

        } finally {
        	pc.cancelAbsoluteBarrier();
        	pc.cancelMoebius();
        }
    }

    // =========================================================
    // pre-check
    // =========================================================


    // =========================================================
    // Bookmark validate
    // =========================================================


    // =========================================================
    // Map restrictions for RANDOM teleport
    // =========================================================


    // =========================================================
    // Random teleport execution
    // =========================================================




  
    // =========================================================
    // 스킬 북마크 MASS: 주변 분산
    // =========================================================


    // =========================================================
    // Packets / consume
    // =========================================================


}
