package l1j.server.server.model.network.core.cs.packet;

import static l1j.server.server.model.network.core.cs.packet.HtmlCmdHandler._random;
import l1j.server.IndunSystem.Hadin.HadinSystem;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ApplyAuction;
import l1j.server.server.model.network.core.sc.packet.SC_AuctionBoardRead;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Deposit;
import l1j.server.server.model.network.core.sc.packet.SC_Drawal;
import l1j.server.server.model.network.core.sc.packet.SC_HouseMap;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SelectTarget;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_TaxRate;
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Town;
import l1j.server.server.utils.CommonUtil;

/** General HTML commands and NPC services. */
final class NpcServiceHtmlCommands {
    private NpcServiceHtmlCommands() {}

    static void handle(CUserSession client, L1PcInstance pc, L1Object obj, int objid,
            String s, String s2, String htmlid, int templateId) throws Exception {
        int[] materials = null;
        int[] counts = null;
        int[] createitem = null;
        int[] createcount = null;
        String[] htmldata = null;
        int beginnerMorphId;

        if (s.equalsIgnoreCase("buy")) {
            if (NpcMarketCommands.buy(pc, obj, objid)) return;
        } else if (s.equalsIgnoreCase("pssellps") || s.equalsIgnoreCase("sell")) {
            NpcMarketCommands.sell(pc, obj, objid);
            return;
        } else if (NpcMarketCommands.isWarehouseCommand(s)) {
            NpcMarketCommands.warehouse(pc, objid, s);
            return;
        } else if (templateId == 200007) {//오류
            if (s.equalsIgnoreCase("1")) {
                htmlid = "camus5";
            } else if (s.equalsIgnoreCase("2")) {
                htmlid = NpcExchangeCommands.prism(pc, objid, _random);
            }
            // 결혼반지 충전
        } else if (templateId == 70702) {
            if (s.equalsIgnoreCase("chg")) NpcExchangeCommands.rechargeWeddingRing(pc);
        } else if (templateId == 900106) {// 달나라
            if (s.equalsIgnoreCase("0")) {
                htmlid = NpcExchangeCommands.moonItem(pc, objid);
            }

            // 호슈
        } else if (templateId == 1000004) {
            htmlid = NpcTravelCommands.hyosue(pc, s, htmlid);
        } else if (obj instanceof L1HousekeeperInstance && s.equalsIgnoreCase("g")) {
            if (s.equalsIgnoreCase("g")) {
                L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
                if (clan == null) return;
                int houseId = clan.getHouseId();
                if (houseId != 0) {
                    L1House house = HouseTable.getInstance().getHouseTable(houseId);
                    if (house == null) return;
                    int keeperId = house.getKeeperId();
                    int rank = pc.getClanRank();
                    if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == keeperId && house.isPurchaseBasement()) {
                        if (clan.getUnderDungeon() == 0) {
                            if (clan.getBlessCount() >= 99000000
                                    && (rank == 3 || rank == 4 || rank == 6 || rank == 9 || rank == 10)) {
                                pc.setTempID(pc.getId());
                                // SC_MessageYN.send(pc, 4703, "");//
                                // 통로를 열기 위해 "축복의 기운" 9,900이 필요합니다. 사용
                                // 하시겠습니까?//주석
                                SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");// 추가
                                return;// 추가
                            } else {
                                htmlid = "agit2";
                            }
                        } else if (clan.getUnderDungeon() == 1) {
                            if (clan.getUnderMapid() != 0) {
                                // pc.start_teleport(32922, 32860,
                                // clan.getUnderMapid(), 5, 169, true, false);
                                SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");// 추가
                                return;// 추가
                            }
                        } else {
                            SC_SystemMessage.send(pc, "지하통로 를 클리어 하였습니다. 내일 이용하세요");
                        }
                    } else {
                        htmlid = "agit2";
                    }
                }
            }
        } else if (s.equalsIgnoreCase("get")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70099
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70796) {
                final int step = pc.getQuest().get_step(L1Quest.QUEST_OILSKINMANT);
                if (step != L1Quest.QUEST_END && pc.getLevel() > 13)
                    NpcQuestTransactions.questGrantOnce(pc, npc, L1Quest.QUEST_OILSKINMANT,
                            step, L1Quest.QUEST_END, 20081, 1, "");
                else SC_NPCTalkReturn.send(pc, objid, "");
                return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70528
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70546
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70567
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70594
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70654
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70748
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70774
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70799
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70815
                    || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70860) {

                if (pc.getHomeTownId() > 0) {

                } else {

                }
            }
        } else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 8500147 && s.equalsIgnoreCase("B")){
            L1CompanionInstance companion = pc.get_companion();
            if(companion == null){
                pc.sendPackets("펫을 소환한 후 시도해주세요.");
                return;
            }
            companion.do_traning(false);
            return;
        } else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 8500150){
            L1CompanionInstance companion = pc.get_companion();
            if(companion == null){
                pc.sendPackets("펫을 소환한 후 시도해주세요.");
                return;
            }
            if(s.equalsIgnoreCase("a")){
                companion.do_restore_keep_exp(false);
                return;
            }else if(s.equalsIgnoreCase("b")){
                companion.do_oblivion(false);
            }
        } else if (s.equalsIgnoreCase("fix")) {

        } else if (templateId == 70012
                || templateId == 70019
                || templateId == 70031
                || templateId == 70054
                || templateId == 70065
                || templateId == 70070
                || templateId == 70075
                || templateId == 70084
                || templateId == 70096) {
            SC_SkillSound.send(pc, pc.getId(), 612);
            if (s.equalsIgnoreCase("room")) {
                if (pc.getInventory().checkItem(40312))

                    htmlid = "inn5";
                else {
                    L1ItemInstance item = pc.getInventory().findItemId(40308);
                    if(item != null && item.getCount() >= 300){
                        materials = Recipes.RECIPE_VALUES_1;
                        counts = Recipes.RECIPE_VALUES_2;
                        createitem = Recipes.RECIPE_VALUES_3;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        htmlid = "inn4";
                    }else
                        htmlid = "inn3";
                }
            } else if (s.equalsIgnoreCase("hall")) {
                int k1 = 0;
                int c1 = 0;
                try {
                    k1 = pc.getLawful();
                    c1 = pc.getClassId();
                } catch (Exception exception1) {
                }
                if (k1 >= 0) {
                    if (c1 == 0 || c1 == 1) {
                        htmlid = "inn4";
                        htmldata = DialogData.INN_HALL_PRICE;
                    } else {
                        htmlid = "inn10";
                    }
                } else {
                    htmlid = "inn11";
                }
            } else if (s.equalsIgnoreCase("return")) {
                if (pc.getInventory().checkItem(40312)) {
                    int ct = pc.getInventory().findItemId(40312).getCount();
                    int cash = Math.multiplyExact(ct, 60);
                    materials = Recipes.RECIPE_VALUES_3;
                    counts = (new int[] { ct });
                    createitem = Recipes.RECIPE_VALUES_1;
                    createcount = (new int[] { cash });
                    htmlid = "inn20";
                    String count = Integer.toString(cash);
                    htmldata = (new String[] { "여관주인", count });

                } else {
                    // htmlid = "inn7";
                    SC_SystemMessage.send(pc, "대여한 방 또는 홀이 없습니다.");
                }
            } else if (s.equalsIgnoreCase("enter")) {
                int nowX = pc.getX();
                int nowY = pc.getY();
                short map = pc.getMapId();
                if (pc.getInventory().checkItem(40312)) {
                    if (map == 0)
                        pc.start_teleport(32746, 32803, 16384, 5, 169, false, false);
                    else if (map > 0)
                        if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
                            pc.start_teleport(32744, 32803, 17408, 5, 169, false, false);
                        else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
                            pc.start_teleport(32745, 32803, 20480, 5, 169, false, false);
                        else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
                            pc.start_teleport(32745, 32803, 24576, 5, 169, false, false);
                        else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
                            pc.start_teleport(32740, 32803, 18432, 5, 169, false, false);
                        else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
                            pc.start_teleport(32745, 32803, 22528, 5, 169, false, false);
                        else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
                            pc.start_teleport(32745, 32803, 21504, 5, 169, false, false);
                        else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
                            pc.start_teleport(32745, 32803, 19456, 5, 169, false, false);
                } else if (pc.getInventory().checkItem(40312)) {
                    if (map == 0)
                        pc.start_teleport(32744, 32808, 16896, 5, 169, false, false);
                    else if (map > 0)
                        if (nowX < 32641 && nowX > 32621 && nowY < 32760 && nowY > 32740)
                            pc.start_teleport(32745, 32807, 18944, 5, 169, false, false);
                        else if (nowX < 32638 && nowX > 32618 && nowY < 33177 && nowY > 33157)
                            pc.start_teleport(32745, 32807, 19968, 5, 169, false, false);
                        else if (nowX < 33995 && nowX > 33975 && nowY < 33322 && nowY > 33302)
                            pc.start_teleport(32745, 32807, 20992, 5, 169, false, false);
                        else if (nowX < 33447 && nowX > 33427 && nowY < 32799 && nowY > 32779)
                            pc.start_teleport(32745, 32807, 22016, 5, 169, false, false);
                        else if (nowX < 33615 && nowX > 33595 && nowY < 33285 && nowY > 33265)
                            pc.start_teleport(32745, 32807, 23040, 5, 169, false, false);
                        else if (nowX < 33126 && nowX > 33106 && nowY < 33389 && nowY > 33369)
                            pc.start_teleport(32745, 32807, 24064, 5, 169, false, false);
                        else if (nowX < 34078 && nowX > 34058 && nowY < 32264 && nowY > 32244)
                            pc.start_teleport(32745, 32807, 25088, 5, 169, false, false);
                } else {
                    SC_SystemMessage.send(pc, "대여한 방 또는 홀이 없습니다.");
                }
            }
        } else if (s.equalsIgnoreCase("hall") && obj instanceof L1MerchantInstance) {

        } else if (s.equalsIgnoreCase("return")) {

        } else if (templateId == 900136) {// 말섬 인던 유키
            if (s.equalsIgnoreCase("enter")) {
                if (pc.isInParty()) {
                    if (pc.getParty().isLeader(pc)) {
                        if (pc.getParty().getNumOfMembers() >= 1) {
                            boolean ck = true;
                            for (L1PcInstance Ppc : pc.getParty().getMembers()) {
                                if (pc.getMapId() != Ppc.getMapId()) {
                                    SC_SystemMessage.send(pc, "파티원이 다 모이지 않았습니다.");
                                    ck = false;
                                    break;
                                }
                            }
                            if (ck) {
                                if (pc.getMapId() == 9100)
                                    htmlid = "";
                                else
                                    HadinSystem.getInstance().startHadin(pc);
                                L1PolyMorph.undoPoly(pc);
                                L1World.getInstance().broadcastServerMessage("\\aD" + pc.getName() + " 님이 동료들과 함께 과거로 여행을 떠났습니다.");
                            }
                            htmlid = "";
                        } else if (pc.getMapId() == 9100) {
                            htmlid = "id1_1";
                        } else {
                            htmlid = "id0_1";
                        }
                    } else if (pc.getMapId() == 9100)
                        htmlid = "id1_2";
                    else {
                        htmlid = "id0_2";
                    }
                } else if (pc.getMapId() == 9100)
                    htmlid = "id1_2";
                else {
                    htmlid = "id0_2";
                }
            }
        } else if (s.equalsIgnoreCase("openigate")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            NpcCastleActions.openCloseGate(pc, npc.getNpcTemplate().get_npcId(), true);
            htmlid = "";
        } else if (s.equalsIgnoreCase("closeigate")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            NpcCastleActions.openCloseGate(pc, npc.getNpcTemplate().get_npcId(), false);
            htmlid = "";
        } else if (s.equalsIgnoreCase("askwartime")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            if (npc.getNpcTemplate().get_npcId() == 60514) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.KENT_CASTLE_ID);
                htmlid = "ktguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 60560) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.OT_CASTLE_ID);
                htmlid = "orcguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 60552 || npc.getNpcTemplate().get_npcId() == 5155) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.WW_CASTLE_ID);
                htmlid = "wdguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 60524 || npc.getNpcTemplate().get_npcId() == 60525
                    || npc.getNpcTemplate().get_npcId() == 60529 || npc.getNpcTemplate().get_npcId() == 7320232) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.GIRAN_CASTLE_ID);
                htmlid = "grguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 70857) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.HEINE_CASTLE_ID);
                htmlid = "heguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 60530 || npc.getNpcTemplate().get_npcId() == 60531) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.DOWA_CASTLE_ID);
                htmlid = "dcguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 60533 || npc.getNpcTemplate().get_npcId() == 60534) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.ADEN_CASTLE_ID);
                htmlid = "adguard7";
            } else if (npc.getNpcTemplate().get_npcId() == 81156) {
                htmldata = NpcCastleActions.makeWarTimeStrings(L1CastleLocation.DIAD_CASTLE_ID);
                htmlid = "dfguard3";
            }
        } else if (s.equalsIgnoreCase("inex")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int castle_id = clan.getCastleId();
                if (castle_id != 0) {
                    MJCastleWar war = MJCastleWarBusiness.getInstance().get(castle_id);
                    SC_ServerMessage.send(pc, 309, war.getCastleName(), String.valueOf(war.getPublicMoney()));
                    htmlid = "";
                }
            }
        } else if (s.equalsIgnoreCase("tax")) {
            L1Clan clan = pc.getClan();
            if (clan != null && clan.getCastleId() != 0)
                SC_TaxRate.send(pc, pc.getId(), MJCastleWarBusiness.getInstance().getTaxRate(clan.getCastleId()));
        } else if (s.equalsIgnoreCase("withdrawal")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int castle_id = clan.getCastleId();
                if (castle_id != 0) {
                    SC_Drawal.send(pc, pc.getId(), MJCastleWarBusiness.getInstance().getPublicMoney(castle_id));
                }
            }
        } else if (s.equalsIgnoreCase("cdeposit")) {
            SC_Deposit.send(pc, pc.getId());
        } else if (s.equalsIgnoreCase("employ")) {
            if (!NpcCastleActions.hireMercenary(pc, (L1NpcInstance) obj, false)) return;
        } else if (s.equalsIgnoreCase("arrange")) {
            SC_SystemMessage.send(pc, "현재 리뉴얼 중입니다. 조만간 이용해보실수 있습니다.");
        } else if (s.equalsIgnoreCase("castlegate")) {
            NpcCastleActions.repairGate(pc);
            htmlid = "";
        } else if (s.equalsIgnoreCase("encw")) {
            NpcBuffCommands.paidEquipmentBuff(pc, true);
            htmlid = "";
        } else if (s.equalsIgnoreCase("demand")) {
            if (!NpcCastleActions.hireMercenary(pc, (L1NpcInstance) obj, true)) return;
        } else if (s.equalsIgnoreCase("enca")) {
            NpcBuffCommands.paidEquipmentBuff(pc, false);
            htmlid = "";
        } else if (s.equalsIgnoreCase("depositnpc")) {
            Object[] petList = pc.getPetList().values().toArray();
            L1PetInstance pet = null;
            for (Object petObject : petList) {
                if (petObject instanceof L1PetInstance) {
                    pet = (L1PetInstance) petObject;
                    if (pet.getArmor() != null) {
                        pet.removePetArmor(pet.getArmor());
                    }
                    if (pet.getWeapon() != null) {
                        pet.removePetWeapon(pet.getWeapon());
                    }
                    /** ui6 관련 펫파티,컨트롤 **/
                    l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, pet, false);

                    pet.collect();
                    pet.deleteMe();
                    pc.getPetList().remove(Integer.valueOf(pet.getId()));
                }
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("dismissnpc")) {
            SC_SystemMessage.send(pc, "당분간 양도는 불가능 합니다.");
        } else if (s.equalsIgnoreCase("withdrawnpc")) {
            NpcPetCommands.withdrawPets(pc, objid);
        } else if (s.equalsIgnoreCase("changename")) {
            pc.setTempID(objid);
            SC_MessageYN.send(pc, 325, "");
        } else if (s.equalsIgnoreCase("attackchr")) {
            if (pc.getZoneType() != 1) {
                if (obj instanceof L1Character) {
                    L1Character cha = (L1Character) obj;
                    SC_SelectTarget.send(pc, cha.getId());
                }
            } else {
                if (obj instanceof L1Character) {
                    L1Character cha = (L1Character) obj;
                    SC_SelectTarget.send(pc, cha.getId());
                }
            }
        } else if (s.equalsIgnoreCase("select")) {
            SC_AuctionBoardRead.sendAsync(pc, objid, s2);
        } else if (s.equalsIgnoreCase("map")) {
            SC_HouseMap.send(pc, objid, s2);
        } else if (s.equalsIgnoreCase("apply")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                if (pc.isCrown() && pc.getId() == clan.getLeaderId()) {
                    if (pc.getLevel() >= 15) {
                        if (clan.getHouseId() == 0) {
                            SC_ApplyAuction.send(pc, objid, s2);
                        } else {
                            SC_ServerMessage.send(pc, 521);
                            htmlid = "";
                        }
                    } else {
                        SC_ServerMessage.send(pc, 519);
                        htmlid = "";
                    }
                } else {
                    SC_ServerMessage.send(pc, 518);
                    htmlid = "";
                }
            } else {
                SC_ServerMessage.send(pc, 518);
                htmlid = "";
            }
        } else if (s.equalsIgnoreCase("open") || s.equalsIgnoreCase("close")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            NpcCastleActions.openCloseDoor(pc, npc, s);
            htmlid = "";
        } else if (s.equalsIgnoreCase("expel")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            NpcCastleActions.expelOtherClan(pc, npc.getNpcTemplate().get_npcId());
            htmlid = "";
        } else if (s.equalsIgnoreCase("pay")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            htmldata = NpcCastleActions.makeHouseTaxStrings(pc, npc);
            htmlid = "agpay";
        } else if (s.equalsIgnoreCase("payfee")) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            NpcCastleActions.payFee(pc, npc);
            htmlid = "";
        } else if (s.equalsIgnoreCase("name")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int houseId = clan.getHouseId();
                if (houseId != 0 && pc.isCrown() && clan.getLeaderId() == pc.getId()) {
                    L1House house = HouseTable.getInstance().getHouseTable(houseId);
                    if (house != null && !house.isOnSale() && obj instanceof L1NpcInstance npc
                            && npc.getNpcTemplate().get_npcId() == house.getKeeperId()) {
                        pc.setTempID(houseId);
                        SC_MessageYN.send(pc, 512, "");
                    }
                }
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("rem")) {
        } else if (s.equalsIgnoreCase("tel0") || s.equalsIgnoreCase("tel1") || s.equalsIgnoreCase("tel2")
                || s.equalsIgnoreCase("tel3")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int houseId = clan.getHouseId();
                if (houseId != 0) {
                    L1House house = HouseTable.getInstance().getHouseTable(houseId);
                    if (house == null) return;
                    int keeperId = house.getKeeperId();
                    L1NpcInstance npc = (L1NpcInstance) obj;
                    if (npc.getNpcTemplate().get_npcId() == keeperId) {
                        int[] loc = L1HouseLocation.getHouseTeleportLoc(houseId, s.charAt(3) - '0');
                        if (loc != null && loc.length >= 3 && loc[0] > 0 && loc[1] > 0)
                            pc.start_teleport(loc[0], loc[1], loc[2], 5, 169, true, false);
                    }
                }
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("upgrade")) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int houseId = clan.getHouseId();
                if (houseId != 0) {
                    L1House house = HouseTable.getInstance().getHouseTable(houseId);
                    if (house == null) return;
                    int keeperId = house.getKeeperId();
                    L1NpcInstance npc = (L1NpcInstance) obj;
                    if (npc.getNpcTemplate().get_npcId() == keeperId) {
                        if (pc.isCrown() && pc.getId() == clan.getLeaderId()) {
                            if (house.isPurchaseBasement()) {
                                SC_ServerMessage.send(pc, 1135);
                            } else {
                                if (l1j.server.server.model.L1AuctionTransaction.upgradeBasement(pc, house)) {
                                    SC_ServerMessage.send(pc, 1099);
                                } else {
                                    SC_SystemMessage.send(pc, "지하실 확장을 완료하지 못했습니다. 아데나와 소유권을 확인해 주세요.");
                                }
                            }
                        } else {
                            SC_ServerMessage.send(pc, 518);
                        }
                    }
                }
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("hall") && obj instanceof L1HousekeeperInstance) {
            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                int houseId = clan.getHouseId();
                if (houseId != 0) {
                    L1House house = HouseTable.getInstance().getHouseTable(houseId);
                    if (house == null) return;
                    int keeperId = house.getKeeperId();
                    L1NpcInstance npc = (L1NpcInstance) obj;
                    if (npc.getNpcTemplate().get_npcId() == keeperId) {
                        if (house.isPurchaseBasement()) {
                            int[] loc = L1HouseLocation.getBasementLoc(houseId);
                            if (loc != null && loc.length >= 3 && loc[0] > 0 && loc[1] > 0)
                                pc.start_teleport(loc[0], loc[1], loc[2], 5, 169, true, false);
                        } else {
                            SC_ServerMessage.send(pc, 1098);
                        }
                    }
                }
            }
            htmlid = "";
        }

        else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 9291) {
            NpcLocalCommands.elementalAttribute(pc, objid, s, htmlid);
            return;
        } else if (s.equalsIgnoreCase("exp")) {// 신녀 아가타
            if (pc.getExpRes() == 1) {
                int cost = 0;
                int level = pc.getLevel();
                int lawful = pc.getLawful();
                if (level < 45) {
                    cost = level * level * 50;
                } else {
                    cost = level * level * 150;
                }
                if (lawful >= 0) {
                    cost = (int) (cost * 0.7);
                }
                SC_MessageYN.send(pc, 738, String.valueOf(cost));
            } else {
                SC_ServerMessage.send(pc, 739);
                htmlid = "";
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("material")) { // 구호의 증서
            if (pc.getInventory().checkItem(3000049, 1)) {
                if (pc.getExpRes() == 1) {
                    SC_MessageYN.send(pc, 2551, "");
                } else {
                    SC_ServerMessage.send(pc, 739);
                    htmlid = "";
                }
            } else {
                pc.sendPackets("구호 증서가 부족 합니다.");
//					pc.sendPackets(new SC_NpcChatPacket(npc, "구호 증서가 필요합니다.", 0));
                htmlid = "";
            }
        } else if (s.equalsIgnoreCase("pk")) {
            if (pc.getLawful() < 30000) {
                SC_ServerMessage.send(pc, 559);
            } else if (pc.get_PKcount() < 5) {
                SC_ServerMessage.send(pc, 560);
            } else {
                if (pc.getInventory().consumeItem(L1ItemId.ADENA, 700000)) {
                    pc.set_PKcount(pc.get_PKcount() - 5);
                    SC_ServerMessage.send(pc, 561, String.valueOf(pc.get_PKcount()));
                } else {
                    SC_ServerMessage.send(pc, 189);
                }
            }
            htmlid = "";
        } else if (s.equalsIgnoreCase("ent")) {
            int npcId = ((L1NpcInstance) obj).getNpcId();
            if (npcId == 80085) {
                htmlid = NpcGameEventActions.enterHauntedHouse(pc);
            } else if (npcId == 80086 || npcId == 80087) {
                htmlid = NpcGameEventActions.enterDeathMatch(pc);
            } else if (npcId == 50037) {
                htmlid = NpcGameEventActions.enterUb(pc, npcId);
            } else if (npcId == 80088) {
                htmlid = NpcGameEventActions.enterPetMatch(pc, s2);
            } else if (npcId == 300000) { // 펫 레이싱
                htmlid = NpcGameEventActions.enterPe(pc, npcId);
            } else if (npcId == 200014) {
                if (pc.getLevel() != pc.getHighLevel()) {
                    SC_SystemMessage.send(pc, "레벨이 다운된 캐릭입니다. 레벨업 후 이용하세요.");
                    return;
                }
                if (pc.getLevel() > 54) {
                    if (pc.getInventory().checkItem(200000)
                            && pc.getInventory().consumeItem(200000, 1)) {
                        pc.start_teleport(32723 + CommonUtil.random(-10, 10), 32815 + CommonUtil.random(-10, 10), 5166,
                                5, 169, true, false);
                        NpcLocalCommands.StatInitialize(pc);
                        htmlid = "";
                    } else {
                        SC_ServerMessage.send(pc, 1290);
                    }
                } else
                    SC_SystemMessage.send(pc, "스텟초기화는 55레벨 이상만 가능합니다.");

            } else if (npcId == 50038 || npcId == 50042 || npcId == 50029 || npcId == 50019 || npcId == 50062) {
                SC_ChatPacket.sendDirect(pc, pc, "무한대전 관람모드 비활성화 상태.");
            } else {
                htmlid = NpcGameEventActions.enterUb(pc, npcId);
            }
        } else if (s.equalsIgnoreCase("par")) {
            htmlid = NpcGameEventActions.enterUb(pc, ((L1NpcInstance) obj).getNpcId());
            //pc.sendPackets("이용이 불가능 합니다.");
        } else if (s.equalsIgnoreCase("info")) {
            int npcId = ((L1NpcInstance) obj).getNpcId();
            if (npcId == 80085 || npcId == 80086 || npcId == 80087) {
            } else {
                htmlid = "colos2";
            }
        } else if (s.equalsIgnoreCase("sco")) {
            htmldata = DialogData.EMPTY_SCOREBOARD;
            htmlid = "colos3";
                //TODO 헤이스트 사
        } else if (s.equalsIgnoreCase("haste")) {
            if (pc.hasSkillEffect(L1SkillId.HASTE))
                pc.removeSkillEffect(L1SkillId.HASTE);

            L1SkillUse.cast(pc, L1SkillId.HASTE, pc.getId(), pc.getX(), pc.getY(), null, 0,L1SkillUse.TYPE_GMBUFF);

                //TODO 변신술사
        } else if ((beginnerMorphId = NpcLocalCommands.beginnerMorphId(s)) != 0) {
            if (!NpcLocalCommands.beginnerMorph(pc, beginnerMorphId)) return;
            htmlid = "";
        } else if (s.equalsIgnoreCase("contract1")) {
            pc.getQuest().set_step(L1Quest.QUEST_LYRA, 1);
            htmlid = "lyraev2";
        } else if (s.equalsIgnoreCase("contract1yes") || s.equalsIgnoreCase("contract1no")) {

            if (s.equalsIgnoreCase("contract1yes")) {
                htmlid = "lyraev5";
            } else if (s.equalsIgnoreCase("contract1no")) {
                pc.getQuest().set_step(L1Quest.QUEST_LYRA, 0);
                htmlid = "lyraev4";
            }
            NpcCraftCommands.exchangeTotems(pc, obj, objid, htmlid);
            return;
        } else if (s.equalsIgnoreCase("pandora6") || s.equalsIgnoreCase("cold6") || s.equalsIgnoreCase("balsim3")
                || s.equalsIgnoreCase("mellin3") || s.equalsIgnoreCase("glen3")) {
            htmlid = s;
            int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
            int taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(npcid);
            htmldata = new String[] { String.valueOf(taxRatesCastle) };
        } else if (s.equalsIgnoreCase("set")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);

                if (town_id >= 1 && town_id <= 10) {
                    if (pc.getHomeTownId() == -1) {
                        SC_ServerMessage.send(pc, 759);
                        htmlid = "";
                    } else if (pc.getHomeTownId() > 0) {
                        if (pc.getHomeTownId() != town_id) {
                            L1Town town = TownTable.getInstance().getTownTable(pc.getHomeTownId());
                            if (town != null) {
                                SC_ServerMessage.send(pc, 758, town.get_name());
                            }
                            htmlid = "";
                        } else {
                            htmlid = "";
                        }
                    } else if (pc.getHomeTownId() == 0) {
                        if (pc.getLevel() < 10) {
                            SC_ServerMessage.send(pc, 757);
                            htmlid = "";
                        } else {
                            int level = pc.getLevel();
                            int cost = level * level * 10;
                            if (pc.getInventory().consumeItem(L1ItemId.ADENA, cost)) {
                                pc.setHomeTownId(town_id);
                                pc.setContribution(0);
                                pc.save();
                            } else {
                                SC_ServerMessage.send(pc, 337, "$4");
                            }
                            htmlid = "";
                        }
                    }
                }
            }
        } else if (s.equalsIgnoreCase("Tgiran")) {
            pc.start_teleport(32683, 32895, 608, 5, 169, true, false);

        } else if (s.equalsIgnoreCase("clear")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);
                if (town_id > 0) {
                    if (pc.getHomeTownId() > 0) {
                        if (pc.getHomeTownId() == town_id) {
                            pc.setHomeTownId(-1);
                            pc.setContribution(0);
                        } else {
                            SC_ServerMessage.send(pc, 756);
                        }
                    }
                    htmlid = "";
                }
            }
        } else if (s.equalsIgnoreCase("ask")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);

                if (town_id >= 1 && town_id <= 10) {
                    L1Town town = TownTable.getInstance().getTownTable(town_id);
                    String leader = town.get_leader_name();
                    if (leader != null && leader.length() != 0) {
                        htmlid = "owner";
                        htmldata = new String[] { leader };
                    } else {
                        htmlid = "noowner";
                    }
                }
            }
        } else {
            NpcQuestHtmlCommands.handle(client, pc, obj, objid, s, s2, htmlid, templateId);
            return;
        }
        HtmlCmdHandler.finish(pc, obj, objid, htmlid, materials, counts, createitem, createcount,
                null, null, htmldata);
    }

    private static final class DialogData {
        private static final String[] INN_HALL_PRICE = {"여관주인", "600"};
        private static final String[] EMPTY_SCOREBOARD = new String[10];
    }

    private static final class Recipes {
        private static final int[] RECIPE_VALUES_1 = {40308};
        private static final int[] RECIPE_VALUES_2 = {300};
        private static final int[] RECIPE_VALUES_3 = {40312};
    }
}
