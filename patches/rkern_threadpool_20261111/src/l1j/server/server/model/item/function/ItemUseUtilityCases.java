package l1j.server.server.model.item.function;

import java.util.Iterator;
import l1j.server.Config;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_IdentifyDesc;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.ItemUseCreature.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemUseDurable.UseDurableTwoInput;
import static l1j.server.server.model.item.function.ItemUsePotions.useFruit;
import static l1j.server.server.model.item.function.ItemUseSpecialEnchant.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.ItemUseWorldHelpers.withdrawPet;

/** Item-specific Utility effects; selected once by ItemUseDispatcher. */
final class ItemUseUtilityCases {
    private ItemUseUtilityCases() {}

    static ItemUseResult handleRequestAnnouncement(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getLevel() >= 82) {
            pc.setTemporaryItemObjectId(item.getId());
            pc.sendPackets("확성할 메시지를 입력해주세요.");
        } else
            pc.sendPackets("레벨 82이하의 용사님은 사용이 불가능 합니다.");
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleStoreClassChange(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int storedItemObjectId = use.storedItemObjectId();
        if (pc.getId() == 0) return ItemUseResult.STOP;

         int day = (itemId == 600226) ? 3 : (itemId == 3000235) ? 7 : 30;

        TamFruitUseService.useFruit(pc, storedItemObjectId, item, day);
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleIdentifyItem(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            int add_mpr = targetItem.getItem().get_addmpr();
            int add_hpr = targetItem.getItem().get_addhpr();
            int safe_enchant = targetItem.getItem().get_safeenchant();
            if (!targetItem.isIdentified()) {
                targetItem.setIdentified(true);
                pc.getInventory().updateItem(targetItem, L1PcInventory.COL_IS_ID);
            }
            SC_IdentifyDesc.send(pc, targetItem);

            pc.getInventory().removeItem(item, 1);
            StringBuffer sb = new StringBuffer();
            if (targetItem.getItem().getType2() == 1 || targetItem.getItem().getType2() == 2) {
                if (safe_enchant == -1) {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: 불가능");
                } else if (safe_enchant == 0) {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: 0");
                } else {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: " + safe_enchant + "");
                }
            }
            SC_SystemMessage.send(pc, sb.toString());
            sb = null;
        }
            return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleWriteBlankScroll(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int blankScrollSkillId = use.blankScrollSkillId();
        ItemUseBlankScroll.write(pc, item, blankScrollSkillId);
        return ItemUseResult.STOP;
    }

    static ItemUseResult handlePlayRaceFlute(ItemUseContext use) {
        L1PcInstance pc = use.player();
        int itemObjectId = use.itemObjectId();
        if (pc.getInventory().checkItem(41160)) {

            if (withdrawPet(pc, itemObjectId)) {
                pc.getInventory().consumeItem(41160, 1);
            }
        } else {
            SC_ServerMessage.send(pc, 79);

        }
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleSpecialFlute(ItemUseContext use) {
        L1PcInstance pc = use.player();
        SC_Sound.send(pc, 437);
        SC_Sound.broadcast(pc, 437);
        Object[] petList = pc.getPetList().values().toArray();
        for (Object petObject : petList) {
            if (petObject instanceof L1PetInstance) {
                L1PetInstance pet = (L1PetInstance) petObject;
                pet.call();
            }
        }
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleEngagementRing(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            L1PcInstance partner = null;
            boolean partner_stat = false;

            int chargeCount = item.getChargeCount();
            if (pc.getPartnerId() != 0) {
                partner = (L1PcInstance) L1World.getInstance().findObject(pc.getPartnerId());
                if (chargeCount > 0) {
                    if (partner != null && partner.getPartnerId() != 0 && pc.getPartnerId() == partner.getId()
                            && partner.getPartnerId() == pc.getId()) {
                        partner_stat = true;
                        item.setChargeCount(item.getChargeCount() - 1);
                        pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
                    }
                }
            } else {
                SC_ServerMessage.send(pc, 662);

                return ItemUseResult.STOP;
            }

            if (partner_stat) {
                boolean castle_area = L1CastleLocation.checkInAllWarArea(

                        partner.getX(), partner.getY(), partner.getMapId());
                if (castle_area == true || partner.isDead() || partner.getMapId() == 603
                        || partner.getMapId() == 255 || partner.getMapId() == 777 || partner.getMapId() == 778
                        || partner.getMapId() == 39 || partner.getMapId() == 5167 || partner.getMapId() == 5153
                        || partner.getMapId() == 5001 || (partner.getMapId() > 190 && partner.getMapId() < 201)
                        || (partner.getMapId() > 255 && partner.getMapId() < 260) || partner.getMapId() == 23
                        || partner.getMapId() == 5153 || partner.getMapId() == 5001 || partner.getMapId() == 24
                        || (partner.getMapId() > 239 && partner.getMapId() < 244)
                        || (partner.getMapId() > 247 && partner.getMapId() < 252)
                        || (partner.getMapId() > 280 && partner.getMapId() < 289)
                        || (partner.getMapId() > 1 && partner.getMapId() < 2)
                        || (partner.getMapId() > 1700 && partner.getMapId() < 1712)) {
                    SC_SystemMessage.send(pc, "당신의 파트너는 죽어있거나 갈 수 없는 곳에 있습니다.");
                }
            } else if (item.getChargeCount() > 0) {
                SC_ServerMessage.send(pc, 546);

            } else {
                SC_ServerMessage.send(pc, 79);

            }
        }
            return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleRequestNameChange(ItemUseContext use) {
        L1PcInstance pc = use.player();
        if (pc.is_combat_field() || !pc.getSafetyZone() || StadiumManager.getInstance().is_on_stadium(pc.getMapId())) {
            pc.sendPackets("해당 지역에서는 사용할 수 없습니다.");
            return ItemUseResult.STOP;
        }
        if (!pc.getInventory().checkItem(408991)) {
            return ItemUseResult.STOP;
        }
        if (MJInstanceSpace.isInInstance(pc)) {
            if (pc.getInstStatus() != InstStatus.INST_USERSTATUS_NONE) {
                pc.sendPackets("해당 지역에서는 사용할 수 없습니다.");
                return ItemUseResult.STOP;
            }
        }

        pc.setChangechaname(true);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "잠시후 캐릭터 이름 변경이 진행됩니다.");
        pc.sendPackets("잠시후 캐릭터 이름 변경이 진행됩니다.");

        ThreadPool.getInstance().scheduleRequired(new Runnable() {
            @Override
            public void run() {
                SC_ChangeCharName.sendStart(pc);
            }
        }, 3000L);
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleStartFishing(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int fishingX = use.fishingX();
        int fishingY = use.fishingY();
        if (Config.ALT_FISHEVENT) {
            FishitemService.clickItem(pc, itemId, fishingX, fishingY, item, itemObjectId);
        } else {
            FishitemService.clickItem1(pc, itemId, fishingX, fishingY, item, itemObjectId);
        }
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleFishingRod(ItemUseContext use) {
        L1PcInstance pc = use.player();
        {
            long curtime = System.currentTimeMillis() / 1000;
            if (pc.getQuizTime() + 5 > curtime) {
                long time = (pc.getQuizTime() + 5) - curtime;
                SC_ChatPacket.sendDirect(pc, pc, time + " 초 후 사용 하시길 바랍니다.");
                return ItemUseResult.STOP;
            }
            int i = 1;
            if (pc.문장주시) {
                i = 3;
                pc.문장주시 = false;
            } else
                pc.문장주시 = true;
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "모든 혈맹의 마크를 표시하거나 종료하였습니다.");

            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                if (clan != null) {
                    SC_War.send(pc, i, pc.getClanname(), clan.getClanName());
                }
                pc.setQuizTime(curtime);
            }
        }
            return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleResetStats(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            StatReset.useCandle(pc, item);
        }
        return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleSummonMonster(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            if (!pc.getMap().isRecallPets() || pc.isInWarArea()) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }
            int pet_size = pc.getPetList().values().size();
            if (pet_size >= 1) {
                SC_SystemMessage.send(pc, "더 이상 소환이 불가능 합니다.");
                return ItemUseResult.STOP;
            }

            if (pet_size == 0) {
                pet_size = 1;
            }

            int[] summon_id = { 7320072, 7320073, 7320074, 7320075, 7320076, 7320077, 7320078, 7320079, 7320080,
                    7320081, 7320000 };
            int random_id = CommonUtil.random(3);
            int pet_cost = 0;

            Iterator<L1NpcInstance> iter = pc.getPetList().values().iterator();
            L1NpcInstance npc = null;

            while (iter.hasNext()) {
                npc = iter.next();
                if (npc == null) {
                    continue;
                }
                pet_cost = npc.getPetcost();
            }

            L1Npc npc_temp = NpcTable.getInstance().getTemplate(summon_id[random_id]);

            if (npc_temp == null) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }

            L1SummonInstance summon = null;

            for (int i = 0; i < pet_size; i++) {
                summon = new L1SummonInstance(npc_temp, pc);
                summon.setPetcost(pet_cost);
            }
            pc.getInventory().removeItem(item, 1);
        }
            return ItemUseResult.COMPLETE;
    }

    static ItemUseResult handleJoinProtectedClan(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            if (pc.getLevel() >= Config.신규혈맹보호레벨) {
                SC_SystemMessage.send(pc, Config.신규혈맹보호레벨 + "레벨 이상은 "+Config.신규혈맹이름+"혈맹에 가입할 수 없습니다.");
                return ItemUseResult.STOP;
            }

            if (pc.getClanid() != 0) {
                SC_SystemMessage.send(pc, "당신은 이미 혈맹에 가입하였습니다.");
                return ItemUseResult.COMPLETE;
            }

            String[] clan_id = { "중립" };
            int random_id = CommonUtil.random(1);

            L1Clan clan = L1World.getInstance().findClan(clan_id[random_id]);
            L1ClanJoin.getInstance().tutorialJoin(clan, pc);
            pc.getInventory().removeItem(item, 1);
            pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
            return ItemUseResult.COMPLETE;
        }
    }

    static ItemUseResult handleUpgradeFishingReel(ItemUseContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)) return ItemUseResult.COMPLETE;
            final int reelTargetId = targetItem.getItemId();
            if (reelTargetId == 41293) {
                UseDurableTwoInput(pc, item, targetItem, 600229, null);
            } else if (reelTargetId == 600229) {
                final int charge = targetItem.getChargeCount();
                if (charge >= 10000) {
                    SC_ServerMessage.send(pc, 3457);
                    return ItemUseResult.COMPLETE;
                }
                final int newCharge = Math.min(10000, charge + 100);
                try (l1j.server.server.model.L1InventoryTransaction tx =
                        User.CheckAndSetPendingUseETCItem(pc,
                                CacheD.accountOrderKey("item-use:", pc.getId()))) {
                    if (!tx.debit(item, 1)) return ItemUseResult.COMPLETE;
                    tx.changeChargeCount(targetItem, newCharge);
                    tx.onFailure(() -> SC_SystemMessage.send(pc, "성장의 릴 충전 저장에 실패했습니다."));
                    CDB.RequestUseItem(pc, tx, null);
                } catch (RuntimeException failure) {
                    ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Growth reel charge prepare failed: " + pc.getId(), failure);
                    SC_SystemMessage.send(pc, "성장의 릴 충전을 처리하지 못했습니다.");
                }
            }
            return ItemUseResult.COMPLETE;
        }
    }
}
