package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ApplyAuction;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_NoSell;
import l1j.server.server.model.network.core.sc.packet.SC_SellHouse;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_Drawal;

import l1j.server.server.model.network.core.sc.packet.SC_Deposit;

import l1j.server.server.model.network.core.sc.packet.SC_TaxRate;

import l1j.server.server.model.network.core.sc.packet.SC_HouseMap;

import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.network.core.sc.packet.SC_SelectTarget;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.DogFight.MJDogFightSettings;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.IndunSystem.Hadin.HadinSystem;
import l1j.server.IndunSystem.Ice.Demon.DemonSystem;
import l1j.server.IndunSystem.Ice.Queen.QueenSystem;
import l1j.server.MJActionListener.ActionListener;
import l1j.server.MJActionListener.ActionListenerLoader;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.Builder.MJServerPacketBuilder;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.NeigeCard;
import l1j.server.server.NeigeCard2;
import l1j.server.server.NeigeCard3;
import l1j.server.server.NeigeGM;
import l1j.server.server.NeigeLow;
import l1j.server.server.NeigeNormal;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1DeathMatch;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PetMatch;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.npc.action.ActionControlHandler;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.shop.L1AssessedItem;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_WAREHOUSE_ITEM_LIST_NOTI;

import l1j.server.server.model.network.core.sc.packet.SC_AuctionBoardRead;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ClanWareHouseHistory;
import l1j.server.server.model.network.core.sc.packet.SC_ClassShop;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_PetList;
import l1j.server.server.model.network.core.sc.packet.SC_PremiumShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_RaceBoard;
import l1j.server.server.model.network.core.sc.packet.SC_RetrieveList;


import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShopBuyList;
import l1j.server.server.model.network.core.sc.packet.SC_ShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1PetType;
import l1j.server.server.templates.L1Town;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.L1SpawnUtil;

import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
final class HtmlCmdHandler {

    // Immutable recipe vectors; never mutate after class initialization.
    private static final int[] RECIPE_VALUES_1 = {40308};
    private static final int[] RECIPE_VALUES_2 = {300};
    private static final int[] RECIPE_VALUES_3 = {40312};
    private static final int[] RECIPE_VALUES_4 = {1};
    private static final int[] RUMTIS_SEAL_MATERIALS = {40959,40960,40961,40962,40046,40618,40643,40645,40651,40676};
    private static final int[] RUMTIS_BADGE_MATERIALS = {40642,40635,40638,40667,40046,40618,40643,40645,40651,40676};
    private static final int[] RUMTIS_SEAL_COUNTS = {1,1,1,1,1,5,5,5,5,5};
    private static final int[] RUMTIS_BADGE_COUNTS = {10,10,10,10,1,5,5,5,5,5};
    private static final int[] RECIPE_VALUES_6 = {40995, 40718, 40991};
    private static final int[] RECIPE_VALUES_7 = {100, 100, 1};
    private static final int[] RECIPE_VALUES_8 = {196};
    private static final int[] RECIPE_VALUES_9 = {40997, 40718, 196};
    private static final int[] RECIPE_VALUES_10 = {197};
    private static final int[] RECIPE_VALUES_11 = {40990, 40718, 197};
    private static final int[] RECIPE_VALUES_12 = {198};
    private static final int[] RECIPE_VALUES_13 = {40994, 40718, 198};
    private static final int[] RECIPE_VALUES_14 = {50, 100, 1};
    private static final int[] RECIPE_VALUES_15 = {199};
    private static final int[] RECIPE_VALUES_16 = {40993, 40718, 199};
    private static final int[] RECIPE_VALUES_17 = {200};
    private static final int[] RECIPE_VALUES_18 = {40998, 40718, 200};
    private static final int[] RECIPE_VALUES_19 = {201};
    private static final int[] RECIPE_VALUES_20 = {40996, 40718, 201};
    private static final int[] RECIPE_VALUES_21 = {10, 100, 1};
    private static final int[] RECIPE_VALUES_22 = {202};
    private static final int[] RECIPE_VALUES_23 = {40992, 40718, 202};
    private static final int[] RECIPE_VALUES_24 = {203};
    private static final int[] RECIPE_VALUES_25 = {20158, 40669, 40678};
    private static final int[] RECIPE_VALUES_26 = {1, 50, 100};
    private static final int[] RECIPE_VALUES_27 = {20083};
    private static final int[] RECIPE_VALUES_28 = {20144, 40672, 40678};
    private static final int[] RECIPE_VALUES_29 = {20131};
    private static final int[] RECIPE_VALUES_30 = {20075, 40671, 40678};
    private static final int[] RECIPE_VALUES_31 = {20069};
    private static final int[] RECIPE_VALUES_32 = {20183, 40674, 40678};
    private static final int[] RECIPE_VALUES_33 = {1, 20, 100};
    private static final int[] RECIPE_VALUES_34 = {20179};
    private static final int[] RECIPE_VALUES_35 = {20190, 40674, 40678};
    private static final int[] RECIPE_VALUES_36 = {1, 40, 100};
    private static final int[] RECIPE_VALUES_37 = {20209};
    private static final int[] RECIPE_VALUES_38 = {20078, 40674, 40678};
    private static final int[] RECIPE_VALUES_39 = {1, 5, 100};
    private static final int[] RECIPE_VALUES_40 = {20290};
    private static final int[] RECIPE_VALUES_41 = {20078, 40670, 40678};
    private static final int[] RECIPE_VALUES_42 = {1, 1, 100};
    private static final int[] RECIPE_VALUES_43 = {20261};
    private static final int[] RECIPE_VALUES_44 = {40719, 40673, 40678};
    private static final int[] RECIPE_VALUES_45 = {20031};
    private static final int[] RECIPE_VALUES_46 = {41212};
    private static final int[] RECIPE_VALUES_47 = {41213};
    private static final int[] RECIPE_VALUES_48 = {40969, 41007};
    private static final int[] RECIPE_VALUES_49 = {20, 1};
    private static final int[] RECIPE_VALUES_50 = {41008};
    private static final int[] RECIPE_VALUES_51 = {40959, 41009};
    private static final int[] RECIPE_VALUES_52 = {1, 1};
    private static final int[] RECIPE_VALUES_53 = {41010};
    private static final int[] RECIPE_VALUES_54 = {40960, 41009};
    private static final int[] RECIPE_VALUES_55 = {40961, 41009};
    private static final int[] RECIPE_VALUES_56 = {40962, 41009};
    private static final int[] RECIPE_VALUES_57 = {40635, 41009};
    private static final int[] RECIPE_VALUES_58 = {10, 1};
    private static final int[] RECIPE_VALUES_59 = {40638, 41009};
    private static final int[] RECIPE_VALUES_60 = {40642, 41009};
    private static final int[] RECIPE_VALUES_61 = {40667, 41009};
    private static final int[] RECIPE_VALUES_62 = {40651, 40643, 40618, 40645, 40676, 40442, 40051};
    private static final int[] RECIPE_VALUES_63 = {10, 10, 10, 10, 20, 5, 1};
    private static final int[] RECIPE_VALUES_64 = {40925};
    private static final int[] RECIPE_VALUES_65 = {40651, 40643, 40618, 40645, 40676, 40675, 40049, 40051};
    private static final int[] RECIPE_VALUES_66 = {5, 5, 5, 5, 10, 10, 3, 1};
    private static final int[] RECIPE_VALUES_67 = {40926};
    private static final int[] RECIPE_VALUES_68 = {40651, 40643, 40618, 40645, 40676, 40675, 40048, 40051};
    private static final int[] RECIPE_VALUES_69 = {10, 10, 10, 10, 20, 10, 3, 1};
    private static final int[] RECIPE_VALUES_70 = {40927};
    private static final int[] RECIPE_VALUES_71 = {40651, 40643, 40618, 40645, 40676, 40675, 40050, 40051};
    private static final int[] RECIPE_VALUES_72 = {20, 20, 20, 20, 30, 10, 3, 1};
    private static final int[] RECIPE_VALUES_73 = {40928};
    private static final int[] RECIPE_VALUES_74 = {40651, 40643, 40618, 40645, 40676, 40675, 40052, 40051};
    private static final int[] RECIPE_VALUES_75 = {30, 30, 30, 30, 30, 20, 1, 1};
    private static final int[] RECIPE_VALUES_76 = {21015};
    private static final int[] RECIPE_VALUES_77 = {41179};
    private static final int[] RECIPE_VALUES_78 = {21016};
    private static final int[] RECIPE_VALUES_79 = {41182};
    private static final int[] RECIPE_VALUES_80 = {21009};
    private static final int[] RECIPE_VALUES_81 = {41180};
    private static final int[] RECIPE_VALUES_82 = {21012};
    private static final int[] RECIPE_VALUES_83 = {41183};
    private static final int[] RECIPE_VALUES_84 = {21008};
    private static final int[] RECIPE_VALUES_85 = {41181};
    private static final int[] RECIPE_VALUES_86 = {21010};
    private static final int[] RECIPE_VALUES_87 = {41184};
    private static final int[] RECIPE_VALUES_88 = {41019, 41020, 41021, 41022, 41023, 41024, 41025, 41026};
    private static final int[] RECIPE_VALUES_89 = {1, 1, 1, 1, 1, 1, 1, 1};
    private static final int[] RECIPE_VALUES_90 = {41027};
    private static final int[] RECIPE_VALUES_91 = {40965};
    private static final int[] RECIPE_VALUES_92 = {49028, 49029, 49030, 41139};
    private static final int[] RECIPE_VALUES_93 = {1, 1, 1, 1};
    private static final int[] RECIPE_VALUES_94 = {41140};
    private static final int[] RECIPE_VALUES_95 = {410027};
    private static final int[] RECIPE_VALUES_96 = {5};
    private static final int[] RECIPE_VALUES_97 = {49027, 41140};
    private static final int[] RECIPE_VALUES_98 = {20422};
    private static final int[] ALICE_MATERIAL_IDS = {40991, 196, 197, 198, 199, 200, 201, 202, 203};


	private static final byte[] EMPTY_BYTES = new byte[0];
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(HtmlCmdHandler.class.getName());

	/*
	 * 원본도 static Random 하나를 공유했다.
	 * packet wrapper만 제거하고 random semantics는 그대로 유지.
	 */
	static final Random _random = new Random(System.nanoTime());

	private HtmlCmdHandler() {
	}

	public static void handle(
			CIOClient client,
			int objid,
			String s,
			String s2,
			byte[] actionData) {

		if (client == null) return;

		// 게임중이 아니면
		if (client.getConnectionState() != ConnectionState.IN_GAME) {
			return;
		}

		try {
			L1PcInstance pc = client.getActiveChar();
			if (pc == null) return;
			
			if (s == null) return;

			if (NpcLocalCommands.dispatchLocalCommand(pc, s)) return;

			int[] materials = null;
			int[] counts = null;
			int[] createitem = null;
			int[] createcount = null;

			String htmlid = null;
			String success_htmlid = null;
			String failure_htmlid = null;
			String[] htmldata = null;
            int beginnerMorphId;

			
			if (!canInteractWithNpc(client, pc))
				return;

		//	System.out.println("Action : " + s);
			L1Object obj = L1World.getInstance().findObject(objid);
			if (!dispatchTarget(pc, obj, objid, s)) return;

			if (dispatchRegisteredAction(pc, obj, s, actionData)) return;
            if (!(obj instanceof L1NpcInstance)) return;

            NpcCastleActions.sendTaxScreens(pc, obj, s);

			if (s.equalsIgnoreCase("mayer3")) {
				htmlid = "mayer3";
			}
			if (s.equalsIgnoreCase("buy")) {
				if (NpcMarketCommands.buy(pc, obj, objid)) return;
			} else if (s.equalsIgnoreCase("pssellps") || s.equalsIgnoreCase("sell")) {
                NpcMarketCommands.sell(pc, obj, objid);
                return;
            } else if (NpcMarketCommands.isWarehouseCommand(s)) {
                NpcMarketCommands.warehouse(pc, objid, s);
                return;
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200007) {//오류
				if (s.equalsIgnoreCase("1")) {
					htmlid = "camus5";
				} else if (s.equalsIgnoreCase("2")) {
                    htmlid = NpcExchangeCommands.prism(pc, objid, _random);
				}
				// 결혼반지 충전
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70702) {
                if (s.equalsIgnoreCase("chg")) NpcExchangeCommands.rechargeWeddingRing(pc);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900106) {// 달나라
                if (s.equalsIgnoreCase("0")) {
                    htmlid = NpcExchangeCommands.moonItem(pc, objid);
                }

				// 호슈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 1000004) {
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

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70012
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70019
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70031
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70054
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70065
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70070
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70075
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70084
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70096) {
				SC_SkillSound.send(pc, pc.getId(), 612);
				if (s.equalsIgnoreCase("room")) {
					if (pc.getInventory().checkItem(40312))
						
						htmlid = "inn5";
					else {
						L1ItemInstance item = pc.getInventory().findItemId(40308);
						if(item != null && item.getCount() >= 300){
							materials = RECIPE_VALUES_1;
							counts = RECIPE_VALUES_2;
							createitem = RECIPE_VALUES_3;
							createcount = RECIPE_VALUES_4;
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
							htmldata = (new String[] { "여관주인", "600" });
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
						materials = RECIPE_VALUES_3;
						counts = (new int[] { ct });
						createitem = RECIPE_VALUES_1;
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

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900136) {// 말섬 인던 유키
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
				htmldata = new String[10];
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
				int totem = 0;
				if (pc.getInventory().checkItem(40131)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40132)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40133)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40134)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40135)) {
					totem++;
				}
				if (totem != 0) {
					materials = new int[totem];
					counts = new int[totem];
					createitem = new int[totem];
					createcount = new int[totem];

					totem = 0;
					if (pc.getInventory().checkItem(40131)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40131);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40131;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 50);
						totem++;
					}
					if (pc.getInventory().checkItem(40132)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40132);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40132;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 100);
						totem++;
					}
					if (pc.getInventory().checkItem(40133)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40133);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40133;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 50);
						totem++;
					}
					if (pc.getInventory().checkItem(40134)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40134);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40134;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 30);
						totem++;
					}
					if (pc.getInventory().checkItem(40135)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40135);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40135;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 200);
						totem++;
					}
				}
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 71038
                    && ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 71044) {
                L1NpcInstance npc = (L1NpcInstance) obj;
                htmlid = NpcQuestTransactions.orcQuestToken(pc, npc, npc.getNpcTemplate().get_npcId(), s, htmlid);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71078) {
				if (s.equalsIgnoreCase("teleportURL")) {
					htmlid = "usender2";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71080) {
				if (s.equalsIgnoreCase("teleportURL")) {
					// htmlid = "amisoo2";
					SC_SystemMessage.send(pc, "하이네성 던전은 폐쇠 되었습니다.");
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80049) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getKarma() <= -10000000) {
						pc.setKarma(1000000);
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "betray13";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80050) {
				if (s.equalsIgnoreCase("1")) {
					htmlid = "meet105";
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getInventory().checkItem(40718)) {
						htmlid = "meet106";
					} else {
						htmlid = "meet110";
					}
				} else if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().consumeItem(40718, 1)) {
						pc.addKarma((int) (-100 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet107";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().consumeItem(40718, 10)) {
						pc.addKarma((int) (-1000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet108";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().consumeItem(40718, 100)) {
						pc.addKarma((int) (-10000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "meet109";
					} else {
						htmlid = "meet104";
					}
				} else if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(40066)) {
						htmlid = "";
					} else {
						if (pc.getKarmaLevel() < -1) {
							pc.start_teleport(32683, 32895, 608, 5, 169, true, false);
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80052) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
						SC_ServerMessage.send(pc, 79);
					} else {
						pc.setSkillEffect(STATUS_CURSE_BARLOG, 1020 * 1000); // 1020
						SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 1, 1020);
						SC_SkillSound.send(pc, pc.getId(), 750);
						SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
						 SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "야히의 군사들은 이곳 저곳에 배치가 되어있습니다.");
						pc.sendPackets("권한 부여: 거대한 마족 공격 가능(발록)");
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80053) {
				int karmaLevel = pc.getKarmaLevel();
				if (s.equalsIgnoreCase("a")) {
					int aliceMaterialId = 0;
					
					for (int id : ALICE_MATERIAL_IDS) {
						if (pc.getInventory().checkItem(id)) {
							aliceMaterialId = id;
							break;
						}
					}
					if (aliceMaterialId == 0) {
						htmlid = "alice_no";
					} else if (aliceMaterialId == 40991) {
						if (karmaLevel <= -1) {
							materials = RECIPE_VALUES_6;
							counts = RECIPE_VALUES_7;
							createitem = RECIPE_VALUES_8;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_1";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "aliceyet";
						}
					} else if (aliceMaterialId == 196) {
						if (karmaLevel <= -2) {
							materials = RECIPE_VALUES_9;
							counts = RECIPE_VALUES_7;
							createitem = RECIPE_VALUES_10;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_2";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_1";
						}
					} else if (aliceMaterialId == 197) {
						if (karmaLevel <= -3) {
							materials = RECIPE_VALUES_11;
							counts = RECIPE_VALUES_7;
							createitem = RECIPE_VALUES_12;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_3";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_2";
						}
					} else if (aliceMaterialId == 198) {
						if (karmaLevel <= -4) {
							materials = RECIPE_VALUES_13;
							counts = RECIPE_VALUES_14;
							createitem = RECIPE_VALUES_15;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_4";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_3";
						}
					} else if (aliceMaterialId == 199) {
						if (karmaLevel <= -5) {
							materials = RECIPE_VALUES_16;
							counts = RECIPE_VALUES_14;
							createitem = RECIPE_VALUES_17;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_5";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_4";
						}
					} else if (aliceMaterialId == 200) {
						if (karmaLevel <= -6) {
							materials = RECIPE_VALUES_18;
							counts = RECIPE_VALUES_14;
							createitem = RECIPE_VALUES_19;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_6";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_5";
						}
					} else if (aliceMaterialId == 201) {
						if (karmaLevel <= -7) {
							materials = RECIPE_VALUES_20;
							counts = RECIPE_VALUES_21;
							createitem = RECIPE_VALUES_22;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_7";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_6";
						}
					} else if (aliceMaterialId == 202) {
						if (karmaLevel <= -8) {
							materials = RECIPE_VALUES_23;
							counts = RECIPE_VALUES_21;
							createitem = RECIPE_VALUES_24;
							createcount = RECIPE_VALUES_4;
							success_htmlid = "alice_8";
							failure_htmlid = "alice_no";
						} else {
							htmlid = "alice_7";
						}
					} else if (aliceMaterialId == 203) {
						htmlid = "alice_8";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71168) {
				// 지.상.의.타.락.을.막.기.위.해.서.라.도.
				// 그.대.와.나.더.이.상.적.일.수.만.은.없.으.리.
				// 어.서.나.를.그.곳.으.로
				if (s.equalsIgnoreCase("a")) {
					// L1Teleport.teleport(pc, 32648, 32921, (short) 535, 5, true);
					if (pc.getInventory().consumeItem(41028, 1))
                        pc.start_teleport(32648, 32921, 535, 5, 169, true, false);
				} else {
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80056) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.getKarma() <= -10000000) {
					NpcQuestTransactions.getBloodCrystalByKarma(pc, npc, s);
				}
				htmlid = "";
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80063) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().consumeItem(40921, 1)) {
						pc.start_teleport(32674, 32832, 603, 2, 169, true, false);
					} else {
						htmlid = "gpass02";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80064) {
				if (s.equalsIgnoreCase("1")) {
					htmlid = "meet005";
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getInventory().checkItem(40678)) {
						htmlid = "meet006";
					} else {
						htmlid = "meet010";
					}
				} else if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().consumeItem(40678, 1)) {
						pc.addKarma((int) (100 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet007";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().consumeItem(40678, 10)) {
						pc.addKarma((int) (1000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet008";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getInventory().consumeItem(40678, 100)) {
						pc.addKarma((int) (10000 * Config.RATE_KARMA));
						SC_ServerMessage.send(pc, 1078);
						SC_Karma.send(pc);
						htmlid = "meet009";
					} else {
						htmlid = "meet004";
					}
				} else if (s.equalsIgnoreCase("d")) {
					if (pc.getInventory().checkItem(40066) // 잠시 송편으로 대체하자! 송편이있을경우
															// 안날라가지게~
					/*
					 * || pc.getInventory().checkItem(40910) ||
					 * pc.getInventory().checkItem(40911) ||
					 * pc.getInventory().checkItem(40912) ||
					 * pc.getInventory().checkItem(40913) ||
					 * pc.getInventory().checkItem(40914) ||
					 * pc.getInventory().checkItem(40915) ||
					 * pc.getInventory().checkItem(40916) ||
					 * pc.getInventory().checkItem(40917) ||
					 * pc.getInventory().checkItem(40918) ||
					 * pc.getInventory().checkItem(40919) ||
					 * pc.getInventory().checkItem(40920) ||
					 * pc.getInventory().checkItem(40921)
					 */) {
						htmlid = "";
					} else {
						if (pc.getKarmaLevel() > 1) {
							// L1Teleport.teleport(pc, 32674, 32832, (short) 602, 2,
							// true);
							pc.start_teleport(32674, 32832, 602, 2, 169, true, false);
						}
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80066) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getKarma() >= 10000000) {
						pc.setKarma(-1000000);
						SC_ServerMessage.send(pc, 1079);
						SC_Karma.send(pc);
						htmlid = "betray03";
					}
				}
			} 

			else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80073) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
						SC_ServerMessage.send(pc, 79);
					} else {
						pc.setSkillEffect(STATUS_CURSE_YAHEE, 1020 * 1000);
						SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 2, 1020);
						SC_SkillSound.send(pc, pc.getId(), 750);
						SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
						pc.sendPackets("권한 부여: 거대한 마족 공격 가능(혼돈-죽음-타락-야히)");
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80072) {
				int karmaLevel = pc.getKarmaLevel();
				if (s.equalsIgnoreCase("0")) {
					htmlid = "lsmitha";
				} else if (s.equalsIgnoreCase("1")) {
					htmlid = "lsmithb";
				} else if (s.equalsIgnoreCase("2")) {
					htmlid = "lsmithc";
				} else if (s.equalsIgnoreCase("3")) {
					htmlid = "lsmithd";
				} else if (s.equalsIgnoreCase("4")) {
					htmlid = "lsmithe";
				} else if (s.equalsIgnoreCase("5")) {
					htmlid = "lsmithf";
				} else if (s.equalsIgnoreCase("6")) {
					htmlid = "";
				} else if (s.equalsIgnoreCase("7")) {
					htmlid = "lsmithg";
				} else if (s.equalsIgnoreCase("8")) {
					htmlid = "lsmithh";
				} else if (s.equalsIgnoreCase("a") && karmaLevel >= 1) {
					materials = RECIPE_VALUES_25;
					counts = RECIPE_VALUES_26;
					createitem = RECIPE_VALUES_27;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithaa";
				} else if (s.equalsIgnoreCase("b") && karmaLevel >= 2) {
					materials = RECIPE_VALUES_28;
					counts = RECIPE_VALUES_26;
					createitem = RECIPE_VALUES_29;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithbb";
				} else if (s.equalsIgnoreCase("c") && karmaLevel >= 3) {
					materials = RECIPE_VALUES_30;
					counts = RECIPE_VALUES_26;
					createitem = RECIPE_VALUES_31;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithcc";
				} else if (s.equalsIgnoreCase("d") && karmaLevel >= 4) {
					materials = RECIPE_VALUES_32;
					counts = RECIPE_VALUES_33;
					createitem = RECIPE_VALUES_34;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithdd";
				} else if (s.equalsIgnoreCase("e") && karmaLevel >= 5) {
					materials = RECIPE_VALUES_35;
					counts = RECIPE_VALUES_36;
					createitem = RECIPE_VALUES_37;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithee";
				} else if (s.equalsIgnoreCase("f") && karmaLevel >= 6) {
					materials = RECIPE_VALUES_38;
					counts = RECIPE_VALUES_39;
					createitem = RECIPE_VALUES_40;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithff";
				} else if (s.equalsIgnoreCase("g") && karmaLevel >= 7) {
					materials = RECIPE_VALUES_41;
					counts = RECIPE_VALUES_42;
					createitem = RECIPE_VALUES_43;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithgg";
				} else if (s.equalsIgnoreCase("h") && karmaLevel >= 8) {
					materials = RECIPE_VALUES_44;
					counts = RECIPE_VALUES_42;
					createitem = RECIPE_VALUES_45;
					createcount = RECIPE_VALUES_4;
					success_htmlid = "";
					failure_htmlid = "lsmithhh";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 205) {// 테베문지기
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss()) {
//					if (CrockController.getInstance().add(pc)) {
//						if (pc.getInventory().checkItem(100036, 1)) {
//							pc.getInventory().consumeItem(100036, 1);
//							pc.start_teleport(32735, 32831, 782, 5, 169, true, false);
//						}
//					}
//				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7000021) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.isInvisble()) {
					SC_NpcChatPacket.sendNpc(pc, npc, "투명 상태에서 받을 수 없어요.", 0);
					return;
				}
				if (s.equalsIgnoreCase("1")) {
					NpcGameEventActions.comaCheck(pc, 3, objid);
				} else if (s.equalsIgnoreCase("2")) {
					NpcGameEventActions.comaCheck(pc, 5, objid);
				} else if (s.equalsIgnoreCase("a")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 1);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("b")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 2);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("c")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 3);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("d")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 4);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("e")) {
					pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 5);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("f")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 1);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("g")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 2);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("h")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 3);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("i")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 4);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("j")) {
					pc.setGhostHousePiece(pc.getGhostHousePiece() + 5);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("k")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 1);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("l")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 2);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("m")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 3);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("n")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 4);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("o")) {
					pc.setPetRacePiece(pc.getPetRacePiece() + 5);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("p")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 1);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("q")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 2);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("s")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 3);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("t")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 4);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("u")) {
					pc.setPetMatchPiece(pc.getPetMatchPiece() + 5);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("v")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 1);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("w")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 2);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("x")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 3);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("y")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 4);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("z")) {
					pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 5);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("3")) {
					NpcGameEventActions.resetPiece(pc);
					NpcGameEventActions.selectComa(pc, objid);
				} else if (s.equalsIgnoreCase("4")) {
					NpcGameEventActions.giveComaBuff(pc, objid);
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500062) { // 조우 티칼
                if (s.equalsIgnoreCase("A")) {
                    htmlid = NpcExchangeCommands.tikalExchange(pc, objid, ((L1NpcInstance) obj).getNpcTemplate().get_name());
                } else if (s.equalsIgnoreCase("B")) {
                    pc.start_teleport(34090, 33168, 4, 5, 169, true, false);
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500063) {// 티칼
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss())
//					if (pc.getInventory().checkItem(500210, 1)) {
//						pc.getInventory().consumeItem(500210);
//						CrockController.getInstance().add(pc);
//						pc.start_teleport(32732, 32862, 784, 5, 169, true, false);
//					}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 500064) {// 티칼
                if (s.equalsIgnoreCase("1")) {
                    NpcQuestTransactions.grantMissingNpcItem(pc, (L1NpcInstance) obj, 500211, null, null, true);
                    return;
                }
				// 라폰스시작
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80074) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.getKarma() >= 10000000) {
					NpcQuestTransactions.getSoulCrystalByKarma(pc, npc, s);
				}
				htmlid = "";
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50007) { // 에스메랄다
                NpcTravelCommands.esmeralda(pc, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80057) {
				htmlid = NpcQuestTransactions.karmaLevelToHtmlId(pc.getKarmaLevel());
				htmldata = new String[] { String.valueOf(pc.getKarmaPercent()) };
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80059
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80060
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80061
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80062) {
				htmlid = NpcTravelCommands.talkToDimensionDoor(pc, (L1NpcInstance) obj, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 81124) {
				if (s.equalsIgnoreCase("1")) {
					NpcLocalCommands.poly(client, 4002);
					htmlid = "";
				} else if (s.equalsIgnoreCase("2")) {
					NpcLocalCommands.poly(client, 4004);
					htmlid = "";
				} else if (s.equalsIgnoreCase("3")) {
					NpcLocalCommands.poly(client, 4950);
					htmlid = "";
				}
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
				int totem = 0;
				if (pc.getInventory().checkItem(40131)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40132)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40133)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40134)) {
					totem++;
				}
				if (pc.getInventory().checkItem(40135)) {
					totem++;
				}
				if (totem != 0) {
					materials = new int[totem];
					counts = new int[totem];
					createitem = new int[totem];
					createcount = new int[totem];

					totem = 0;
					if (pc.getInventory().checkItem(40131)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40131);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40131;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 50);
						totem++;
					}
					if (pc.getInventory().checkItem(40132)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40132);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40132;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 100);
						totem++;
					}
					if (pc.getInventory().checkItem(40133)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40133);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40133;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 50);
						totem++;
					}
					if (pc.getInventory().checkItem(40134)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40134);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40134;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 30);
						totem++;
					}
					if (pc.getInventory().checkItem(40135)) {
						L1ItemInstance l1iteminstance = pc.getInventory().findItemId(40135);
						int i1 = l1iteminstance.getCount();
						materials[totem] = 40135;
						counts[totem] = i1;
						createitem[totem] = L1ItemId.ADENA;
						createcount[totem] = Math.multiplyExact(i1, 200);
						totem++;
					}
				}
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
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70534
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70556
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70572
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70631
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70663
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70761
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70788
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70806
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70830
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70876) {
				if (s.equalsIgnoreCase("r")) {
					if (obj instanceof L1NpcInstance) {
					}
				} else if (s.equalsIgnoreCase("t")) {

				} else if (s.equalsIgnoreCase("c")) {

				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71005) {
                if (s.equalsIgnoreCase("0")) {
                    if (pc.getInventory().checkItem(41209)) htmlid = "";
                    else {
                        NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41209, 1, true, "");
                        return;
                    }
                } else if (s.equalsIgnoreCase("1")) {
                    NpcQuestTransactions.jpePotionExchange(pc, (L1NpcInstance) obj);
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71006) {
				if (s.equalsIgnoreCase("0")) {
					if (pc.getLevel() > 25) {
						htmlid = "jpe0057";
					} else if (pc.getInventory().checkItem(41213)) {
						htmlid = "jpe0056";
					} else if (pc.getInventory().checkItem(41210) || pc.getInventory().checkItem(41211)) {
						htmlid = "jpe0055";
					} else if (pc.getInventory().checkItem(41209)) {
						htmlid = "jpe0054";
					} else if (pc.getInventory().checkItem(41212)) {
						htmlid = "jpe0056";
						materials = RECIPE_VALUES_46;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_47;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "jpe0057";
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70512) {
				if (s.equalsIgnoreCase("0") || s.equalsIgnoreCase("fullheal")) {
					int hp = _random.nextInt(21) + 70;
					pc.setCurrentHp(pc.getCurrentHp() + hp);
					SC_ServerMessage.send(pc, 77);
					SC_SkillSound.send(pc, pc.getId(), 830);
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71025) {
                if (s.equalsIgnoreCase("0")) {
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41225, 1, false, "jpe0083");
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454512) {
				
				if (s.equalsIgnoreCase("a")) {
					pc.start_teleport(32734, 32859, 13005, 5, 169, true, false);
					htmlid = "";
				}
				
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454513) {
				if (s.equalsIgnoreCase("a")) {
					pc.setCurrentHp(pc.getMaxHp() / 5);
					pc.setCurrentMp(pc.getMaxMp() / 5);
					pc.start_teleport(33431, 32798, 4, 5, 169, true, false);
					htmlid = "";
				}
					
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 454514) {
				
				if (s.equalsIgnoreCase("a")) { 
					
					int hp = pc.getMaxHp() - pc.getCurrentHp();
					int mp = pc.getMaxMp() - pc.getCurrentMp();
					
					pc.setCurrentHp(pc.getCurrentHp() + hp);
					pc.setCurrentMp(pc.getCurrentMp() + mp);
					SC_SkillSound.send(pc, pc.getId(), 832);
					htmlid = "";
					
				}
				
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71055) {
                if (s.equalsIgnoreCase("0")) {
                    NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, L1Quest.QUEST_LUKEIN1,
                            0, 1, 40701, 1, "lukein8");
                    return;
                }
                if (s.equalsIgnoreCase("1")) {
                    if (pc.getQuest().get_step(L1Quest.QUEST_TBOX3) != L1Quest.QUEST_END
                            || pc.getQuest().get_step(L1Quest.QUEST_LUKEIN1) != 11) {
                        SC_NPCTalkReturn.send(pc, objid, "lukein0");
                        return;
                    }
                    NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj,
                            L1Quest.QUEST_LUKEIN1, 11, L1Quest.QUEST_END,
                            40716, 1, 0, 20269, 0, "lukein0", "lukein0");
                    return;
				} else if (s.equalsIgnoreCase("2")) {
					htmlid = "lukein12";
					pc.getQuest().set_step(L1Quest.QUEST_RESTA, 3);
				}
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71063) {
                if (s.equalsIgnoreCase("0")) {
                    NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                            1, 2, 3, L1Quest.QUEST_TBOX1, 40702, "maptbox1");
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 71064
                    && ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 71066) {
                if (s.equalsIgnoreCase("0")) {
                    final int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                    NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                            npcId - 71062, 5, 6, L1Quest.QUEST_TBOX2, 40702, "maptbox1");
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 71067
                    && ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 71072) {
                if (s.equalsIgnoreCase("0")) {
                    final int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                    NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                            npcId - 71062, 11, 1, L1Quest.QUEST_TBOX3, 40716, "maptboxi");
                    return;
                }

				// 시미즈(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71056) {
				// 아들을 찾는다
				if (s.equalsIgnoreCase("a")) {
					pc.getQuest().set_step(L1Quest.QUEST_SIMIZZ, 1);
					htmlid = "SIMIZZ7";
                } else if (s.equalsIgnoreCase("b")) {
                    if (pc.getInventory().checkItem(40661) && pc.getInventory().checkItem(40662)
                            && pc.getInventory().checkItem(40663)) {
                        NpcQuestTransactions.simizzReceipt(pc, (L1NpcInstance) obj);
                        return;
                    }
                    htmlid = "SIMIZZ9";
                } else if (s.equalsIgnoreCase("d")) {
					htmlid = "SIMIZZ12";
					pc.getQuest().set_step(L1Quest.QUEST_SIMIZZ, L1Quest.QUEST_END);
				}

				// 도일(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71057) {
				// 러쉬에 대해 듣는다
				if (s.equalsIgnoreCase("3")) {
					htmlid = "doil4";
				} else if (s.equalsIgnoreCase("6")) {
					htmlid = "doil6";
                } else if (s.equalsIgnoreCase("1")) {
                    if (pc.getInventory().checkItem(40714)) {
                        NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj, L1Quest.QUEST_DOIL,
                                pc.getQuest().get_step(L1Quest.QUEST_DOIL), L1Quest.QUEST_END,
                                40714, 1, 0, 40647, 0, "doil8", "doil7");
                        return;
                    }
                    htmlid = "doil7";
                }

				// 루디 안(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 83014) {
                if (NpcQuestTransactions.handleBlessingBuyback(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71059) {
				// 루디 안의 부탁을 받아들인다
				if (s.equalsIgnoreCase("A")) {
                    NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, L1Quest.QUEST_RUDIAN,
                            0, 1, 40700, 1, "rudian6");
                    return;
                } else if (s.equalsIgnoreCase("B")) {
                    if (pc.getInventory().checkItem(40710)) {
                        NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj, L1Quest.QUEST_RUDIAN,
                                pc.getQuest().get_step(L1Quest.QUEST_RUDIAN), L1Quest.QUEST_END,
                                40700, 1, 40710, 40647, 0, "rudian8", "rudian9");
                        return;
                    }
                    htmlid = "rudian9";
                }

				// 레스타(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71060) {
				// 동료들에 대해
				if (s.equalsIgnoreCase("A")) {
					if (pc.getQuest().get_step(L1Quest.QUEST_RUDIAN) == L1Quest.QUEST_END) {
						htmlid = "resta6";
					} else {
						htmlid = "resta4";
					}
				} else if (s.equalsIgnoreCase("B")) {
					htmlid = "resta10";
					pc.getQuest().set_step(L1Quest.QUEST_RESTA, 2);
				}

				// 카좀스(해적섬)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71061) {
                if (s.equalsIgnoreCase("A")) {
                    final int step = pc.getQuest().get_step(L1Quest.QUEST_CADMUS);
                    if (step >= 2) {
                        SC_NPCTalkReturn.send(pc, objid, "cadmus6");
                    } else if (pc.getInventory().checkItem(40647, 3)) {
                        NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, L1Quest.QUEST_CADMUS,
                                step, 2, 40647, 3, "cadmus6", "cadmus5");
                    } else {
                        if (step == 0) pc.getQuest().set_step(L1Quest.QUEST_CADMUS, 1);
                        SC_NPCTalkReturn.send(pc, objid, "cadmus5");
                    }
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71036) {
				if (s.equalsIgnoreCase("a")) {
					htmlid = "kamyla7";
					pc.getQuest().set_step(L1Quest.QUEST_KAMYLA, 1);
                } else if (s.equalsIgnoreCase("c")) {
                    NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, L1Quest.QUEST_KAMYLA,
                            2, 3, 40644, 1, "kamyla10", "kamyla10");
                    return;
                } else if (s.equalsIgnoreCase("e")) {
                    NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, L1Quest.QUEST_KAMYLA,
                            3, 4, 40630, 1, "kamyla13", "kamyla13");
                    return;
				} else if (s.equalsIgnoreCase("i")) {
					htmlid = "kamyla25";
				} else if (s.equalsIgnoreCase("b")) { // 카 미라(흐랑코의 미궁)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 1) {
						// L1Teleport.teleport(pc, 32679, 32742, (short) 482, 5,
						// true);
						pc.start_teleport(32679, 32742, 482, 5, 169, true, false);
					}
				} else if (s.equalsIgnoreCase("d")) { // 카 미라(디에고가 닫힌 뇌)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 3) {
						// L1Teleport.teleport(pc, 32736, 32800, (short) 483, 5,
						// true);
						pc.start_teleport(32736, 32800, 483, 5, 169, true, false);
					}
				} else if (s.equalsIgnoreCase("f")) { // 카 미라(호세 지하소굴)
					if (pc.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 4) {
						// L1Teleport.teleport(pc, 32746, 32807, (short) 484, 5,
						// true);
						pc.start_teleport(32746, 32807, 484, 5, 169, true, false);
					}
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71089) {
                if (s.equalsIgnoreCase("a")) {
                    NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, L1Quest.QUEST_KAMYLA,
                            1, 2, 40644, 1, "francu10");
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71090) {
                if (NpcQuestTransactions.handleCrystalKeeper(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71091) {
                if (NpcQuestTransactions.handleCrystalExit(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71074) {
                if (NpcQuestTransactions.handleLizardQuest(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71198) {
                if (NpcQuestTransactions.handleTionQuest(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71199) {
                if (NpcQuestTransactions.handleJeronQuest(pc, (L1NpcInstance) obj, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80079) {
                if (s.equalsIgnoreCase("0")) {
                    if (!pc.getInventory().checkItem(41312)) {
                        // The first clue is a one-time quest award; after it was
                        // consumed, another click must not mint a second clue.
                        if (pc.getQuest().get_step(L1Quest.QUEST_KEPLISHA) == 0)
                            NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, L1Quest.QUEST_KEPLISHA,
                                    0, L1Quest.QUEST_END, 41312, 1, "keplisha7");
                        else
                            SC_NPCTalkReturn.send(pc, objid, "keplisha7");
                        return;
                    }
                } else if (s.equalsIgnoreCase("1")) {
                    NpcQuestTransactions.keplishaHoroscopeReceipt(pc, (L1NpcInstance) obj);
                    return;
				} else if (s.equalsIgnoreCase("2")) {
					if (pc.getCurrentSpriteId() != pc.getClassId()) {
						htmlid = "keplisha9";
					} else {
						if (pc.getInventory().consumeItem(41314, 1)) {
							int html = _random.nextInt(9) + 1;
							int PolyId = 6180 + _random.nextInt(40);
							NpcLocalCommands.polyByKeplisha(client, PolyId);
							switch (html) {
							case 1:
								htmlid = "horomon11";
								break;
							case 2:
								htmlid = "horomon12";
								break;
							case 3:
								htmlid = "horomon13";
								break;
							case 4:
								htmlid = "horomon21";
								break;
							case 5:
								htmlid = "horomon22";
								break;
							case 6:
								htmlid = "horomon23";
								break;
							case 7:
								htmlid = "horomon31";
								break;
							case 8:
								htmlid = "horomon32";
								break;
							case 9:
								htmlid = "horomon33";
								break;
							default:
								break;
							}
						}
					}
				} else if (s.equalsIgnoreCase("3")) {
                    NpcQuestTransactions.discardKeplisha(pc, (L1NpcInstance) obj, htmlid);
                    return;
                }

            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71181) { // 에마이
                final int materialId;
                final int rewardId;
                final String page;
                if (s.equalsIgnoreCase("A")) { materialId = 41093; rewardId = 41097; page = "my5"; }
                else if (s.equalsIgnoreCase("B")) { materialId = 41094; rewardId = 41918; page = "my6"; }
                else if (s.equalsIgnoreCase("C")) { materialId = 41095; rewardId = 41919; page = "my7"; }
                else if (s.equalsIgnoreCase("D")) { materialId = 41096; rewardId = 41920; page = "my8"; }
                else { materialId = 0; rewardId = 0; page = null; }
                if (page != null) {
                    NpcQuestTransactions.npcItemExchange(pc, (L1NpcInstance) obj, materialId, rewardId, page, "my4");
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80084) {
                if (s.equalsIgnoreCase("q")) {
                    if (pc.getInventory().checkItem(41356, 1)) htmlid = "rparum4";
                    else {
                        NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41356, 1, true, "rparum3");
                        return;
                    }
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80105) {
                if (s.equalsIgnoreCase("c")) {
                    NpcExchangeCommands.rechargeCrownRelic(pc, objid);
                    return;
                }

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4202000) { // 용기사
				// 피에나
				if (s.equalsIgnoreCase("teleportURL") && pc.isDragonknight()) {
					htmlid = "feaena3";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4201000) { // 환술사
				// 아샤
				if (s.equalsIgnoreCase("teleportURL") && pc.isBlackwizard()) {
					htmlid = "asha3";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200015) {
                if (s.equalsIgnoreCase("0")) {
                    NpcQuestTransactions.grantMissingNpcItem(pc, (L1NpcInstance) obj, 200000,
                            "candleg2", "candleg3", false);
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200016) {
				if (s.equalsIgnoreCase("B")) {
					if (pc.getInventory().consumeItem(210081, 1)) {
						// L1Teleport.teleport(pc, 32618, 32736, (short) 4, 4,
						// true);
						pc.start_teleport(32618, 32736, 4, 5, 169, true, false);
					} else {
						htmlid = "joegolem20";
					}
				}
			}

            else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3000003) {
                if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                    NpcQuestTransactions.level15ItemReceipt(pc, (L1NpcInstance) obj, false, s.equalsIgnoreCase("a"));
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3100004) {
                if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                    NpcQuestTransactions.level15ItemReceipt(pc, (L1NpcInstance) obj, true, s.equalsIgnoreCase("a"));
                    return;
                }
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3200014) {
				if (s.equalsIgnoreCase("buy 5")) {
					NpcPetCommands.petbuy(client, 46044, 41159, 1000);
					htmlid = "subsusp3";
				} else if (s.equalsIgnoreCase("buy 6")) {
					NpcPetCommands.petbuy(client, 46042, 41159, 1000);
					htmlid = "subsusp4";
				}

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200075) { // 샤르나
                htmlid = NpcExchangeCommands.sharna(pc, objid, s, ((L1NpcInstance) obj).getNpcTemplate().get_name());
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71126) {
				/*int TeleportType = 0, TeleportX = 0, TeleportY = 0, TeleportMap = 0;
				if (s.equals("0")) { // 중앙광장 외각 부근 이동
					TeleportType = 1;
					TeleportX = 32746;
					TeleportY = 32764;
					TeleportMap = 479;
				} else if (s.equals("1")) { // 명법군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32788;
					TeleportY = 32682;
					TeleportMap = 479;
				} else if (s.equals("2")) { // 마령군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32922;
					TeleportY = 32701;
					TeleportMap = 479;
				} else if (s.equals("3")) { // 마수군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32910;
					TeleportY = 32822;
					TeleportMap = 479;
				} else if (s.equals("4")) { // 암살군 입구 부근 이동
					TeleportType = 1;
					TeleportX = 32739;
					TeleportY = 32843;
					TeleportMap = 479;
				} else*/ if (s.equalsIgnoreCase("B")) {
					if (pc.getInventory().checkItem(41007, 1)) {
						htmlid = "eris10";
					} else {
                        NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41007, 1, true, "eris6");
                        return;
					}
				} else if (s.equalsIgnoreCase("C")) {
					if (pc.getInventory().checkItem(41009, 1)) {
						htmlid = "eris10";
					} else {
                        NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41009, 1, true, "eris8");
                        return;
					}
				} else if (s.equalsIgnoreCase("A")) {
					if (pc.getInventory().checkItem(41007, 1)) {
						if (pc.getInventory().checkItem(40969, 20)) {
							htmlid = "eris18";
							materials = RECIPE_VALUES_48;
							counts = RECIPE_VALUES_49;
							createitem = RECIPE_VALUES_50;
							createcount = RECIPE_VALUES_4;
						} else {
							htmlid = "eris5";
						}
					} else {
						htmlid = "eris2";
					}
				} else if (s.equalsIgnoreCase("E")) {
					if (pc.getInventory().checkItem(41010, 1)) {
						htmlid = "eris19";
					} else {
						htmlid = "eris7";
					}
				} else if (s.equalsIgnoreCase("D")) {
					if (pc.getInventory().checkItem(41010, 1)) {
						htmlid = "eris19";
					} else {
						if (pc.getInventory().checkItem(41009, 1)) {
							if (pc.getInventory().checkItem(40959, 1)) {
								htmlid = "eris17";
								materials = RECIPE_VALUES_51;
								counts = RECIPE_VALUES_52;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40960, 1)) {
								htmlid = "eris16";
								materials = RECIPE_VALUES_54;
								counts = RECIPE_VALUES_52;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40961, 1)) {
								htmlid = "eris15";
								materials = RECIPE_VALUES_55;
								counts = RECIPE_VALUES_52;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40962, 1)) {
								htmlid = "eris14";
								materials = RECIPE_VALUES_56;
								counts = RECIPE_VALUES_52;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40635, 10)) {
								htmlid = "eris12";
								materials = RECIPE_VALUES_57;
								counts = RECIPE_VALUES_58;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40638, 10)) {
								htmlid = "eris11";
								materials = RECIPE_VALUES_59;
								counts = RECIPE_VALUES_58;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40642, 10)) {
								htmlid = "eris13";
								materials = RECIPE_VALUES_60;
								counts = RECIPE_VALUES_58;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else if (pc.getInventory().checkItem(40667, 10)) {
								htmlid = "eris13";
								materials = RECIPE_VALUES_61;
								counts = RECIPE_VALUES_58;
								createitem = RECIPE_VALUES_53;
								createcount = RECIPE_VALUES_4;
							} else {
								htmlid = "eris8";
							}
						} else {
							htmlid = "eris7";
						}
					}
				}
				/*if (TeleportType == 1) {
					if (pc.getRadungeonTime() >= 120) {
						SC_ChatPacket.sendDirect(pc, pc, "라스타바드 던전 시간이 만료 되었습니다.");
						return;
					} else {
						// L1Teleport.teleport(pc, TeleportX, TeleportY, (short)
						// TeleportMap, 0, true);
						pc.start_teleport(TeleportX, TeleportY, TeleportMap, 5, 169, true, false);
					}
				}*/

				// 넘어진 항해사
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80076) {
                if (s.equalsIgnoreCase("A")) {
                    final int variant = _random.nextInt(2);
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj,
                            49082 + variant, 1, false, variant == 0 ? "voyager6a" : "voyager6b");
                    return;
                }
				// 연금 술사 페리타
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71128) {
				if (s.equals("A")) {
					if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
						htmlid = "perita2";
					} else {
						htmlid = "perita3";
					}
				} else if (s.equals("p")) {
					// 저주해진 블랙 귀 링 판별
					if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40988, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita43";
					} else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita44";
					} else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
						htmlid = "perita45";
					} else if (pc.getInventory().checkItem(40988, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
						htmlid = "perita47";
					} else if (pc.getInventory().checkItem(40987, 1)) { // 위저드 클래스
						htmlid = "perita46";
					} else if (pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
						htmlid = "perita49";
					} else if (pc.getInventory().checkItem(40987, 1)) { // 워리아크라스
						htmlid = "perita48";
					} else {
						htmlid = "perita50";
					}
				} else if (s.equals("q")) {
					// 블랙 귀 링 판별
					if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41174, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita54";
					} else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita55";
					} else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41174, 1)) { // 나이트 클래스
						htmlid = "perita56";
					} else if (pc.getInventory().checkItem(41174, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
						htmlid = "perita58";
					} else if (pc.getInventory().checkItem(41174, 1)) { // 위저드 클래스
						htmlid = "perita57";
					} else if (pc.getInventory().checkItem(41175, 1)) { // 나이트 클래스
						htmlid = "perita60";
					} else if (pc.getInventory().checkItem(41176, 1)) { // 워리아크라스
						htmlid = "perita59";
					} else {
						htmlid = "perita61";
					}
				} else if (s.equals("s")) {
					// 신비적인 블랙 귀 링 판별
					if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41162, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita62";
					} else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita63";
					} else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
							&& pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
						htmlid = "perita64";
					} else if (pc.getInventory().checkItem(41162, 1) // 나이트 클래스
							&& pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita66";
					} else if (pc.getInventory().checkItem(41161, 1)) { // 위저드 클래스
						htmlid = "perita65";
					} else if (pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
						htmlid = "perita68";
					} else if (pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
						htmlid = "perita67";
					} else {
						htmlid = "perita69";
					}
				} else if (s.equals("B")) {
					// 정화의 일부
					if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 10) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 10) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 10) // 어둠의 숨결
							&& pc.getInventory().checkItem(40442, 5) // 프롭브의 위액
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita7";
						materials = RECIPE_VALUES_62;
						counts = RECIPE_VALUES_63;
						createitem = RECIPE_VALUES_64; // 정화의 일부
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita8";
					}
				} else if (s.equals("G") || s.equals("h") || s.equals("i")) {
					// 신비적인 일부：1 단계
					if (pc.getInventory().checkItem(40651, 5) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 5) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 5) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 5) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 5) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 5) // 어둠의 광석
							&& pc.getInventory().checkItem(40049, 3) // 고급 루비
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita27";
						materials = RECIPE_VALUES_65;
						counts = RECIPE_VALUES_66;
						createitem = RECIPE_VALUES_67; // 신비적인 일부：1 단계
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita28";
					}
				} else if (s.equals("H") || s.equals("j") || s.equals("k")) {
					// 신비적인 일부：2 단계
					if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 10) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 10) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 20) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 10) // 어둠의 광석
							&& pc.getInventory().checkItem(40048, 3) // 고급 다이아몬드
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita29";
						materials = RECIPE_VALUES_68;
						counts = RECIPE_VALUES_69;
						createitem = RECIPE_VALUES_70; // 신비적인 일부：2 단계
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita30";
					}
				} else if (s.equals("I") || s.equals("l") || s.equals("m")) {
					// 신비적인 일부：3 단계
					if (pc.getInventory().checkItem(40651, 20) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 20) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 20) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 20) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 10) // 어둠의 광석
							&& pc.getInventory().checkItem(40050, 3) // 고급 사파이어
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita31";
						materials = RECIPE_VALUES_71;
						counts = RECIPE_VALUES_72;
						createitem = RECIPE_VALUES_73; // 신비적인 일부：3 단계
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita32";
					}
				} else if (s.equals("J") || s.equals("n") || s.equals("o")) {
					// 신비적인 일부：4 단계
					if (pc.getInventory().checkItem(40651, 30) // 불의 숨결
							&& pc.getInventory().checkItem(40643, 30) // 수의 숨결
							&& pc.getInventory().checkItem(40618, 30) // 대지의 숨결
							&& pc.getInventory().checkItem(40645, 30) // 돌풍이 심함 취
							&& pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
							&& pc.getInventory().checkItem(40675, 20) // 어둠의 광석
							&& pc.getInventory().checkItem(40052, 1) // 최고급 다이아몬드
							&& pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
						htmlid = "perita33";
						materials = RECIPE_VALUES_74;
						counts = RECIPE_VALUES_75;
						createitem = RECIPE_VALUES_73; // 신비적인 일부：4 단계
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita34";
					}
				} else if (s.equals("K")) { // 1 단계 귀 링(영혼의 귀 링)
					int earinga = 0;
					int earingb = 0;
					if (pc.getInventory().checkEquipped(21014) || pc.getInventory().checkEquipped(21006)
							|| pc.getInventory().checkEquipped(21007)) {
						htmlid = "perita36";
					} else if (pc.getInventory().checkItem(21014, 1)) { // 위저드 클래스
						earinga = 21014;
						earingb = 41176;
					} else if (pc.getInventory().checkItem(21006, 1)) { // 나이트 클래스
						earinga = 21006;
						earingb = 41177;
					} else if (pc.getInventory().checkItem(21007, 1)) { // 워리아크라스
						earinga = 21007;
						earingb = 41178;
					} else {
						htmlid = "perita36";
					}
					if (earinga > 0) {
						materials = new int[] { earinga };
						counts = RECIPE_VALUES_4;
						createitem = new int[] { earingb };
						createcount = RECIPE_VALUES_4;
					}
				} else if (s.equals("L")) { // 2 단계 귀 링(지혜의 귀 링)
					if (pc.getInventory().checkEquipped(21015)) {
						htmlid = "perita22";
					} else if (pc.getInventory().checkItem(21015, 1)) {
						materials = RECIPE_VALUES_76;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_77;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita22";
					}
				} else if (s.equals("M")) { // 3 단계 귀 링(진실의 귀 링)
					if (pc.getInventory().checkEquipped(21016)) {
						htmlid = "perita26";
					} else if (pc.getInventory().checkItem(21016, 1)) {
						materials = RECIPE_VALUES_78;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_79;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita26";
					}
				} else if (s.equals("b")) { // 2 단계 귀 링(정열의 귀 링)
					if (pc.getInventory().checkEquipped(21009)) {
						htmlid = "perita39";
					} else if (pc.getInventory().checkItem(21009, 1)) {
						materials = RECIPE_VALUES_80;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_81;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita39";
					}
				} else if (s.equals("d")) { // 3 단계 귀 링(명예의 귀 링)
					if (pc.getInventory().checkEquipped(21012)) {
						htmlid = "perita41";
					} else if (pc.getInventory().checkItem(21012, 1)) {
						materials = RECIPE_VALUES_82;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_83;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita41";
					}
				} else if (s.equals("a")) { // 2 단계 귀 링(분노의 귀 링)
					if (pc.getInventory().checkEquipped(21008)) {
						htmlid = "perita38";
					} else if (pc.getInventory().checkItem(21008, 1)) {
						materials = RECIPE_VALUES_84;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_85;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita38";
					}
				} else if (s.equals("c")) { // 3 단계 귀 링(용맹의 귀 링)
					if (pc.getInventory().checkEquipped(21010)) {
						htmlid = "perita40";
					} else if (pc.getInventory().checkItem(21010, 1)) {
						materials = RECIPE_VALUES_86;
						counts = RECIPE_VALUES_4;
						createitem = RECIPE_VALUES_87;
						createcount = RECIPE_VALUES_4;
					} else {
						htmlid = "perita40";
					}
				}

				// 보석 세공인 룸스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71129) {
				if (s.equals("Z")) {
					htmlid = "rumtis2";
				} else if (s.equals("Y")) {
					if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
						htmlid = "rumtis3";
					} else {
						htmlid = "rumtis4";
					}
				} else if (s.equals("q")) {
					htmlid = "rumtis92";
				} else if (s.equals("A")) {
					if (pc.getInventory().checkItem(41161, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis6";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("B")) {
					if (pc.getInventory().checkItem(41164, 1)) {
						// 신비적인 위저드 귀 링
						htmlid = "rumtis7";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("C")) {
					if (pc.getInventory().checkItem(41167, 1)) {
						// 신비적인 회색 위저드 귀 링
						htmlid = "rumtis8";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("T")) {
					if (pc.getInventory().checkItem(41167, 1)) {
						// 신비적인 화이트 위저드 귀 링
						htmlid = "rumtis9";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("w")) {
					if (pc.getInventory().checkItem(41162, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis14";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("x")) {
					if (pc.getInventory().checkItem(41165, 1)) {
						// 신비적인 나이트 귀 링
						htmlid = "rumtis15";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("y")) {
					if (pc.getInventory().checkItem(41168, 1)) {
						// 신비적인 회색 나이트 귀 링
						htmlid = "rumtis16";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("z")) {
					if (pc.getInventory().checkItem(41171, 1)) {
						// 신비적인 화이트 나이트 귀 링
						htmlid = "rumtis17";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("U")) {
					if (pc.getInventory().checkItem(41163, 1)) {
						// 신비적인 블랙 귀 링
						htmlid = "rumtis10";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("V")) {
					if (pc.getInventory().checkItem(41166, 1)) {
						// 미스테리아스워리아이아링
						htmlid = "rumtis11";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("W")) {
					if (pc.getInventory().checkItem(41169, 1)) {
						// 미스테리아스그레이워리아이아링
						htmlid = "rumtis12";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("X")) {
					if (pc.getInventory().checkItem(41172, 1)) {
						// 미스테리아스화이워리아이아링
						htmlid = "rumtis13";
					} else {
						htmlid = "rumtis101";
					}
				} else if (s.equals("D") || s.equals("E") || s.equals("F") || s.equals("G")) {
                    final boolean seals = pc.getInventory().checkItem(40959, 1)
                            && pc.getInventory().checkItem(40960, 1)
                            && pc.getInventory().checkItem(40961, 1)
                            && pc.getInventory().checkItem(40962, 1);
                    final boolean badges = !seals && pc.getInventory().checkItem(40642, 10)
                            && pc.getInventory().checkItem(40635, 10)
                            && pc.getInventory().checkItem(40638, 10)
                            && pc.getInventory().checkItem(40667, 10);
                    if (pc.getInventory().checkItem(40046, 1)
                            && pc.getInventory().checkItem(40618, 5)
                            && pc.getInventory().checkItem(40643, 5)
                            && pc.getInventory().checkItem(40645, 5)
                            && pc.getInventory().checkItem(40651, 5)
                            && pc.getInventory().checkItem(40676, 5)) {
                        if (seals || badges) {
                            htmlid = "rumtis60";
                            materials = seals ? RUMTIS_SEAL_MATERIALS : RUMTIS_BADGE_MATERIALS;
                            counts = seals ? RUMTIS_SEAL_COUNTS : RUMTIS_BADGE_COUNTS;
                            createitem = RECIPE_VALUES_67;
                            createcount = RECIPE_VALUES_4;
                        } else {
                            htmlid = "rumtis18";
                        }
                    }
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70644 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70538
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70560 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70667
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70725 || ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70790
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70884) {
					if (s.equalsIgnoreCase("request blood of evil")) {
						SC_SystemMessage.send(pc, "아직 시기가 아닌 것 같습니다.");
						htmlid = "";
//						return;
					}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71119) {
				if (s.equalsIgnoreCase("request las history book")) {
					materials = RECIPE_VALUES_88;
					counts = RECIPE_VALUES_89;
					createitem = RECIPE_VALUES_90;
					createcount = RECIPE_VALUES_4;
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71170) {
				if (s.equalsIgnoreCase("request las weapon manual")) {
					materials = RECIPE_VALUES_90;
					counts = RECIPE_VALUES_4;
					createitem = RECIPE_VALUES_91;
					createcount = RECIPE_VALUES_4;
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 6000015) {
                if (NpcTravelCommands.towerGate(pc, objid, s)) return;
				// 첩보원(욕망의 동굴측)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80067) {
				if (s.equalsIgnoreCase("n")) {
                    NpcQuestTransactions.spyStart(pc, client, (L1NpcInstance) obj, false);
                    return;
                } else if (s.equalsIgnoreCase("d")) {
					NpcQuestTransactions.spyDiscard(pc, (L1NpcInstance) obj, false);
                    return;
					// 「초기화한다」
				} else if (s.equalsIgnoreCase("k")) {
                    NpcQuestTransactions.spyReset(pc, (L1NpcInstance) obj, false);
                    return;
                } else if (s.equalsIgnoreCase("e")) {
					if (pc.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END || pc.getKarmaLevel() >= 1) {
						htmlid = "";
					} else {
						if (pc.getInventory().checkItem(41138)) {
							htmlid = "";
							pc.addKarma((int) (1600 * Config.RATE_KARMA));
							SC_Karma.send(pc);
							pc.getInventory().consumeItem(41130, 1); // 핏자국의 계약서
							pc.getInventory().consumeItem(41131, 1); // 핏자국의 지령서
							pc.getInventory().consumeItem(41138, 1); // 카헬의 정수
							pc.getQuest().set_step(L1Quest.QUEST_DESIRE, L1Quest.QUEST_END);
						} else {
							htmlid = "minicod04";
						}
					}
					// 선물을 받는다
				} else if (s.equalsIgnoreCase("g")) {
                    NpcQuestTransactions.spyGift(pc, (L1NpcInstance) obj, false);
                    return;
				}
				/** 드루가 리뉴얼 **/
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900005) { // 드루가
                if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")
                        || s.equalsIgnoreCase("c") || s.equalsIgnoreCase("d")) {
                    if (pc.getInventory().checkItem(L1ItemId.DRUGA_POKET)) {
                        SC_NPCTalkReturn.send(pc, objid, "veil3");
                        return;
                    }
                    final int rewardId = s.equalsIgnoreCase("a") ? 14000000
                            : s.equalsIgnoreCase("b") ? 1400001
                            : s.equalsIgnoreCase("c") ? 1400002 : 1400003;
                    NpcQuestTransactions.npcSingleMaterialExchange(pc, (L1NpcInstance) obj,
                            L1ItemId.ADENA, 10000000, rewardId, "veil8", "veil4", null);
                    return;
                }

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900017) {// 반쿠
				if (s.equalsIgnoreCase("buy 7")) {
					NpcPetCommands.petbuy(client, 900043, 410045, 1);// 녹색 해츨링 7
				} else if (s.equalsIgnoreCase("buy 8")) {
					NpcPetCommands.petbuy(client, 900044, 410046, 1);// 황색 해츨링 8
				}
				htmlid = "";
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900048) { // 수상한 요리사
                if (s.equalsIgnoreCase("0")) {
                    NpcQuestTransactions.npcSingleMaterialExchange(pc, (L1NpcInstance) obj,
                            41921, 45, 410055, null, "suschef5", "$8538");
                    return;
				} else if (s.equalsIgnoreCase("1")) {
                    htmlid = NpcBuffCommands.featherBuff(pc);
                }

            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900000) { // 슈에르메
                L1ItemInstance item = null;
                // 60차: 표식 32개 + 100,000 아데나 교환 4종.
                if (s.equalsIgnoreCase("H")) {
                    NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410162, 32, 100000, 410034, "sherme10", false);
                    return;
                } else if (s.equalsIgnoreCase("I")) {
                    NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410163, 32, 100000, 410032, "sherme10", false);
                    return;
                } else if (s.equalsIgnoreCase("J")) {
                    NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410164, 32, 100000, 410035, "sherme10", false);
                    return;
                } else if (s.equalsIgnoreCase("K")) {
                    NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410165, 32, 100000, 410033, "sherme10", false);
                    return;
                // 61차: 봉인 마안 + 100,000 아데나 교환 4종.
                } else if (s.equalsIgnoreCase("A") || s.equalsIgnoreCase("B")
                        || s.equalsIgnoreCase("C") || s.equalsIgnoreCase("D")) {
                    final int materialId = s.equalsIgnoreCase("A") ? 410030
                            : s.equalsIgnoreCase("B") ? 410028
                            : s.equalsIgnoreCase("C") ? 410031 : 410029;
                    final int rewardId = s.equalsIgnoreCase("A") ? 410034
                            : s.equalsIgnoreCase("B") ? 410032
                            : s.equalsIgnoreCase("C") ? 410035 : 410033;
                    if (pc.getInventory().checkItem(rewardId)) {
                        SC_NPCTalkReturn.send(pc, objid, "sherme0");
                        return;
                    }
                    NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, materialId, 1, 100000, rewardId, "sherme1", true);
                    return;
					} else if (s.equalsIgnoreCase("E")) {
                    NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410034, 410032, 410036, 40);
                    return;
                } else if (s.equalsIgnoreCase("F")) {
                    NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410036, 410033, 410037, 30);
                    return;
                } else if (s.equalsIgnoreCase("G")) {
                    NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410037, 410035, 410038, 20);
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900105) { // 마에노브
                // Nine floor tickets, same item IDs/coordinates as the original branches.
                final int floor = s.length() == 1 ? Character.toUpperCase(s.charAt(0)) - 'A' : -1;
                if (floor >= 0 && floor <= 8) {
                    final int x = floor == 0 || floor == 2 || floor == 3 ? 32744
                            : floor == 1 ? 32745 : floor == 5 ? 32737 : 32738;
                    final int y = floor <= 1 ? 32862 : floor <= 3 ? 32863
                            : floor == 5 ? 32797 : 32798;
                    NpcQuestTransactions.npcPaidFloorTeleport(pc, (L1NpcInstance) obj, 40104 + floor,
                            x, y, 116 + floor * 10);
                    return;
                }
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 900090
					&& ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 900098) { // 오만의탑
																						// 리뉴얼
																						// 마에노브의불꽃
																						// 16
																						// ~
																						// 96
                if (s.equalsIgnoreCase("A")) {
                    NpcTravelCommands.towerUpperFloor(pc, objid, ((L1NpcInstance) obj).getNpcId());
                    return;
                }

			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900143) { // 말섬 인던 보조원
				if (s.equalsIgnoreCase("a")) {
					// L1Teleport.teleport(pc, 32583, 32924, (short) 0, 5, true);
					pc.start_teleport(32583, 32924, 0, 5, 169, true, false);
					htmlid = "";
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70009) { // 게렝
				htmlid = NpcExperienceCommands.게렝(pc, s);
				// 클라우디아 수정요청 //엔피시 번호확인하세요
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900146) { // 휴그린트
                if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.hugrint(pc, objid);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70842) { // 마르바
				htmlid = NpcQuestTransactions.마르바(pc, obj, s);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70840) { //로빈후드
				NpcQuestTransactions.moonbow(pc, (L1NpcInstance) obj, s, false);
                return;
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 600005) {// 지브릴
				NpcQuestTransactions.moonbow(pc, (L1NpcInstance) obj, s, true);
                return;
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 71179) { // 디에츠(빛고목제작)
				if (s.equalsIgnoreCase("A")) {// 복원된 고대의 목걸이
					ThreadLocalRandom random = ThreadLocalRandom.current();
					if (pc.getInventory().checkItem(49028, 1) && pc.getInventory().checkItem(49029, 1)
							&& pc.getInventory().checkItem(49030, 1) && pc.getInventory().checkItem(41139, 1)) { // 보석과
																													// 볼품없는
						// 목걸이 확인
						if (random.nextInt(10) > 6) {
							materials = RECIPE_VALUES_92;
							counts = RECIPE_VALUES_93;
							createitem = RECIPE_VALUES_94; // 복원된 고대의 목걸이
							createcount = RECIPE_VALUES_4;
							htmlid = "dh8";
						} else { // 실패의 경우 아이템만 사라짐
							materials = RECIPE_VALUES_92;
							counts = RECIPE_VALUES_93;
							createitem = RECIPE_VALUES_95; // 보석 가루
							createcount = RECIPE_VALUES_96;
							htmlid = "dh7";
						}
					} else { // 재료가 부족한 경우
						htmlid = "dh6";
					}
				} else if (s.equalsIgnoreCase("B")) {// 빛나는 고대의 목걸이 제작을 부탁한다.
					ThreadLocalRandom random = ThreadLocalRandom.current();
					if (pc.getInventory().checkItem(49027, 1) && pc.getInventory().checkItem(41140, 1)) { // 다이아몬드와
						// 복원된 목걸이
						if (random.nextInt(10) > 7) {
							materials = RECIPE_VALUES_97;
							counts = RECIPE_VALUES_52;
							createitem = RECIPE_VALUES_98; // 빛나는 고대 목걸이
							createcount = RECIPE_VALUES_4;
							htmlid = "dh9";
						} else {
							materials = RECIPE_VALUES_97;
							counts = RECIPE_VALUES_52;
							createitem = RECIPE_VALUES_95; // 보석가루
							createcount = RECIPE_VALUES_96;
							htmlid = "dh7";
						}
					} else { // 재료가 부족한 경우
						htmlid = "dh6";
					}
				}
				// 첩보원(그림자의 신전측)
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 81202) {
				// 「화가 나지만 승낙한다」
				if (s.equalsIgnoreCase("n")) {
                    NpcQuestTransactions.spyStart(pc, client, (L1NpcInstance) obj, true);
                    return;
                } else if (s.equalsIgnoreCase("d")) {
					NpcQuestTransactions.spyDiscard(pc, (L1NpcInstance) obj, true);
                    return;
					// 「초기화한다」
				} else if (s.equalsIgnoreCase("k")) {
                    NpcQuestTransactions.spyReset(pc, (L1NpcInstance) obj, true);
                    return;
                } else if (s.equalsIgnoreCase("e")) {
					if (pc.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END || pc.getKarmaLevel() >= 1) {
						htmlid = "";
					} else {
						if (pc.getInventory().checkItem(41129)) {
							htmlid = "";
							pc.addKarma((int) (-1600 * Config.RATE_KARMA));
							SC_Karma.send(pc);
							pc.getInventory().consumeItem(41121, 1); // 카헬의 계약서
							pc.getInventory().consumeItem(41122, 1); // 카헬의 지령서
							pc.getInventory().consumeItem(41129, 1); // 핏자국의 정수
							pc.getQuest().set_step(L1Quest.QUEST_SHADOWS, L1Quest.QUEST_END);
						} else {
							htmlid = "minitos04";
						}
					}
					// 재빠르게 받는다
				} else if (s.equalsIgnoreCase("g")) {
                    NpcQuestTransactions.spyGift(pc, (L1NpcInstance) obj, true);
                    return;
				}
				// 초보자 도우미
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 9274) {
                if (s.equals("A")) {
                    NpcQuestTransactions.tutorialArmor(pc, (L1NpcInstance) obj);
                    return;
                }
			} else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == MJDogFightSettings.CASTER_NPC_ID){
				if (s.equals("status")) {
					MJDogFightHistory.getInstance().on_show_ticket_ration_view(pc, (L1NpcInstance) obj);
				}
				// 버경 승률처리 start
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70035
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70041
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70042) {
				if (s.equals("status")) {
					SC_RaceBoard.send(pc, 1, pc.isGm());
				}
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 777849) { // 킬톤
                if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.contract(pc, objid, true);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 777848) { // 메린
                if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.contract(pc, objid, false);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70000) { // 마빈
                htmlid = NpcExperienceCommands.marbin(pc, objid, s, htmlid);
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70908) {
                htmlid = NpcExchangeCommands.piers(pc, (L1NpcInstance) obj, objid, s, htmlid);
                // 시장 경비병/시장 중심지 이동
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50025
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50032
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50048
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50058
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5067
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5070) {
				if (s.equals("market-giran") || s.equals("teleport Mobjtele2")) {
					int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
					pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
				} else if (s.equals("EnterSeller")) {
		            L1PcInstance targetShop = L1World.getInstance().getPlayer(s2);
		            if (targetShop != null && targetShop.isPrivateShop() && pc.getMapId() == targetShop.getMapId()) {
		               pc.setFindMerchantId(targetShop.getId());
		               pc.start_teleport(targetShop.getX() - 1, targetShop.getY() + 1, targetShop.getMapId(), 1, 169, true, false);
		            }else{
		               L1NpcShopInstance nn = L1World.getInstance().getShopNpc(s2);
		               if (nn != null && nn.getMapId() == 800 && nn.getState() == 1) {
		                  pc.setFindMerchantId(nn.getId());
		                  pc.start_teleport(nn.getX() + CommonUtil.random(3)-1, nn.getY() + CommonUtil.random(3)-1, nn.getMapId(), 0, 169, false, false);
		               }
		            }
				} else if (s.equals("teleport Mobjtele1")) {
					pc.start_teleport(32800, 32927, 800, pc.getHeading(), 169, true, false);
				}
				// 적대적인 얼음 여왕 근위병
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5079) {
				if (s.equalsIgnoreCase("enter")) {
					QueenSystem.getInstance().startQueen(pc);
				}
				// 우호적인 얼음 여왕 근위병
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5078) {
				if (s.equalsIgnoreCase("enter")) {
					DemonSystem.getInstance().startDemon(pc);
				}
				// 상아탑 첩보원
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5086) {
                if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                    NpcQuestTransactions.iceCaveSupplies(pc, (L1NpcInstance) obj, s.equalsIgnoreCase("a"));
                    return;
                }
				// 스빈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5087) {
				if (s.equalsIgnoreCase("a")) {
					// NpcExchangeCommands.createNewItem(pc, npcName, 49031, 1, 0); // 얼음 결정체
					// NpcExchangeCommands.createNewItem(pc, npcName, 21081, 1, 0); // 얼음 귀걸이
					int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_OREN);
					// L1Teleport.teleport(pc, loc[0], loc[1], (short) loc[2],
					// pc.getHeading(), true);
					pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				// 베테르랑
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5111) {
                htmlid = NpcExchangeCommands.veteran(pc, (L1NpcInstance) obj, s, htmlid);
				// 캐릭터 지원단
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5112) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				String npcName = npc.getNpcTemplate().get_name();
				if (s.equals("a")) { // 1.레벨 상승(최대 75레벨)
					if (pc.getLevel() < 51) {
						pc.setExp(ExpTable.getExpByLevel(51));
					} else if (pc.getLevel() >= 51 && pc.getLevel() < 75) {
						pc.setExp(ExpTable.getExpByLevel(pc.getLevel() + 1));
					} else if (pc.getLevel() >= 75) {
						htmlid = "lind_sm1";
					}
                } else if (s.equals("b") || s.equals("c")) {
                    NpcQuestTransactions.veteranSupport(pc, (L1NpcInstance) obj, s.equals("b"));
                    return;
				} else if (s.equals("d")) { // 1.드래곤의 혈흔(안타라스) 받기
					SC_SkillSound.send(pc, pc.getId(), 7783);
					pc.setSkillEffect(L1SkillId.ANTA_BUFF, 7200 * 1000);
					SC_OwnCharAttrDef.send(pc);
					SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 82, 7200 / 60);
					htmlid = "lind_sm8";
					SC_NPCTalkReturn.send(pc, objid, "lind_sm8");
				} else if (s.equals("e")) { // 2.드래곤의 혈흔(파푸리온) 받기.
					SC_SkillSound.send(pc, pc.getId(), 7783);
					pc.setSkillEffect(L1SkillId.FAFU_BUFF, 7200 * 1000);
					SC_OwnCharAttrDef.send(pc);
					SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 85, 7200 / 60);
					htmlid = "lind_sm8";
				}
				// 반지개방
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5162) {
                if (NpcQuestTransactions.unlockEquipmentSlot(pc, s)) return;
				// 안톤
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 70614) {
                htmlid = NpcExchangeCommands.anton(pc, (L1NpcInstance) obj, s, htmlid);

				// 카너스
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5125) {
				htmlid = NpcTravelCommands.카너스(pc, s);
				// 다크엘프 생존자
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5131) {
				if (s.equals("exitghost")) {
					pc.endGhost();
				}
				// 수호병사 OUT
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5128) {
				if (s.equals("a")) {
					// L1Teleport.teleport(pc, 32614, 33195, (short) 4,
					// pc.getHeading(), true);
					pc.start_teleport(32614, 33195, 4, pc.getHeading(), 169, true, false);
					htmlid = "";
				}

				// 수호병사 IN
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5130) {
				if (s.equals("a")) {
					pc.start_teleport(32617, 33215, 4, pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				// 감시자의 눈
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5133) {
				if (s.equals("exitghost")) {
					pc.endGhost();
				}
				// 비자야
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 470000350) {
                if (!NpcGameEventActions.enterClanRaid(pc, s)) return;
            } else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 460000350) {
                if (!NpcGameEventActions.enterBossRaid(pc)) return;
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5161) {
				htmlid = NpcExchangeCommands.비자야(pc, obj, s);
				/** 마을 강화 버프사 통합 **/
			} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 900088
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7310100
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 7132
					|| ((L1NpcInstance) obj).getNpcTemplate().get_npcId() >= 5000
					&& ((L1NpcInstance) obj).getNpcTemplate().get_npcId() <= 5012) {
				NpcSpellScrollCommands.handle(pc, objid, s);
				return;
			} else {
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();

				ActionListener listener = null;
				/** 액션 리스너에서 최대 레벨이 걸릴경우 **/
				if((npcid == 7320204 || npcid == 7320054 || npcid == 7310085) && pc.getLevel() >= Config.New_Cha1){
					pc.sendPackets(String.format("%d레벨 이상은 입장할 수 없습니다.", Config.New_Cha1));
					return;
				}
				
				if(npcid == 70751 && s.equalsIgnoreCase("a")){
					listener = ActionListenerLoader.getInstance().findListener(npcid, String.format("a%d", EClassesType.fromGfx(pc.getClassId()).toInt()));
				}else{
					listener = ActionListenerLoader.getInstance().findListener(npcid, s);
				}
				
				if(listener == null){
					htmlid = NpcTemplateCommands.dispatch(pc, obj, s, htmlid);				
				}else{
					htmlid = listener.to_action(pc, obj);
				}
			}
			if (htmlid != null && htmlid.equalsIgnoreCase("colos2")) {
				htmldata = NpcGameEventActions.makeUbInfoStrings(((L1NpcInstance) obj).getNpcTemplate().get_npcId());
			}
			if (createitem != null) {
                NpcCraftCommands.execute(pc, obj, objid, materials, counts,
                        createitem, createcount, success_htmlid, failure_htmlid, htmlid, htmldata);
                return;
            }

			if (htmlid != null) {
				SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
			}
		} catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "NPC command execution failed", e);
		}
	}


    static boolean canInteractWithNpc(CIOClient client, L1PcInstance pc) {
        return client != null && client.getConnectionState() == ConnectionState.IN_GAME
                && pc != null && !pc.isGhost() && !pc.isPrivateShop()
                && !pc.is무인상점() && !pc.isAutoClanjoin();
    }

    /** Shared live target admission for ordinary HTML and XML amount commands.
     * Shared by talk, HTML, and amount admission; full talk-channel lifecycle is separate. */
    static boolean isNpcTargetInRange(L1PcInstance pc, L1NpcInstance npc) {
        return pc.getMapId() == npc.getMapId()
                && Math.abs((long) pc.getX() - npc.getX()) <= 6
                && Math.abs((long) pc.getY() - npc.getY()) <= 6;
    }

	/** Existing target policy and pre-dispatch side effects; false stops the packet. */
    private static boolean dispatchTarget(L1PcInstance pc, L1Object obj, int objid, String s) {
		if (obj != null) {
			if (obj instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				int nd = Config.네쥬던전초기화;
				
				// 보안 체크: 맵 일치 및 거리 확인
				if (!isNpcTargetInRange(pc, npc)) return false;
				
	//
				if(npc.getNpcId() == 7000020 || npc.getNpcId() == 460000378 || npc.getNpcId() == 460000379){ //기감 시간 초기화 에러 방지 neige
					if(pc.getAccount().gethero21() != nd ){
						if(Config.네쥬던전체킹 == 1){
							DungeonTimeProgressLoader.delete(pc);
							pc.initialize_dungeon_progress();
							System.out.println("던전체킹 1 , 네쥬 기감 초기화 : "+pc.getName());
						}else{
							DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(1);
							DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
							if(progress != null){
								progress.set_remain_seconds(dtInfo.get_amount_seconds());
								DungeonTimeProgressLoader.update(progress);
								System.out.println("던전체킹 2 , 네쥬 기감 초기화 : "+pc.getName());
							}
						}
						pc.getAccount().sethero21(nd);
						pc.getAccount().updateHero21();
					}
				}
				npc.onFinalAction(pc, s);
				if(s.contains("buy")){SC_SkillSound.send(pc, pc.getId(), 21129);}
				if(s.contains("sell")){SC_SkillSound.send(pc, pc.getId(), 21129);}
				if (pc.isGm()) {
					SC_SystemMessage.send(pc, "\\f:[NPC번호]  \\aA" + npc.getNpcId() + "     \\f:[액션]  \\aA" + s);
					SC_SystemMessage.send(pc, "\\f:[obj번호]  \\aA" + objid);
					
				}
			} else if (obj instanceof L1PcInstance target) {
                NpcPlayerMenuHandler.handle(pc, target, s);
                return false;
			}
		} else {
			return false;
		}
		
        return true;
    }

    /** Existing inn and XML handlers have the same precedence; true consumes the packet. */
    private static boolean dispatchRegisteredAction(L1PcInstance pc, L1Object obj, String s, byte[] actionData) {
		if(obj.instanceOf(MJL1Type.L1TYPE_NPC)){
			if(MJINNHelperLoader.getInstance().onAction((L1NpcInstance)obj, pc, s))
				return true;
		}
		
		L1NpcAction action = NpcActionTable.getInstance().get(s, pc, obj);
		if (action != null) {
			action.dispatch(s, pc, obj, actionData != null ? actionData : EMPTY_BYTES, 0);
			return true;
		}

        return false;
    }


}
