package l1j.server.server.command.executor;

import l1j.server.ActionCodes.Loader.SpriteInformationLoader;
import l1j.server.ActionListener.ActionListenerLoader;
import l1j.server.BonusDropSystem.BonusDropSystemLoader;
import l1j.server.BonusItem.BonusItemLoader;
import l1j.server.BonusMaps.BonusMapTable;
import l1j.server.BugRace.BugRaceController;
import l1j.server.DTSSystem.IODTSLoader;
import l1j.server.ExchangeItem.ExchangeItemLoader;
import l1j.server.ExchangeListItem.ExchangeListItemLoader;
import l1j.server.ExpRevision.FishingExpInfo;
import l1j.server.FishingRodInfo.FishingRodInfoLoader;
import l1j.server.InvenBonusItem.InvenBonusItemLoader;
import l1j.server.ItemExChangeSystem.ItemExChangeLoader;
import l1j.server.ItemInvisibleOptions.ItemInvisibleOptionsLoader;
import l1j.server.ItemSkill.Loader.ItemSkillModelLoader;
import l1j.server.MagicDollEventMonster.MagicDollEventMonsterLoader;
import l1j.server.ServerMacroSystem.ServerMacroLoader;
import l1j.server.SetItems.SetItemsLoader;
import l1j.server.Template.DateSchedulerModel.MinuteScheduler;
import l1j.server.Template.SpellProp.SpellProbabilityLoader;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.BadNamesList;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.BuffNpcListTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.CraftListTable;
import l1j.server.server.datatables.CreateCharTeleportTable;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.EtcItemBuffBeadTable;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemMessageBoxTable;
import l1j.server.server.datatables.ItemMessageTable;
import l1j.server.server.datatables.ItemSettingTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.L1DragonJewel;
import l1j.server.server.datatables.L1EnchantBonus;
import l1j.server.server.datatables.MapChangeTable;
import l1j.server.server.datatables.MapReturnTable;
import l1j.server.server.datatables.MapRingTelTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcShopAdenTypeTable;
import l1j.server.server.datatables.NpcShopSpawnTable;
import l1j.server.server.datatables.NpcShopTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PenaltyItemTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.WantedTelTable;
import l1j.server.server.datatables.WeaponBalanceTable;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.wantedadena;
import l1j.server.server.datatables.wcanceladena;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.BlankScroll;
import l1j.server.server.model.item.BlessTypeEnchant;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.item.HealingPotion;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.item.MemorialStone;
import l1j.server.server.model.item.PolyPotion;
import l1j.server.server.model.item.SpellItem;
import l1j.server.server.model.item.SpellWand;
import l1j.server.server.model.item.TreasureBox;
import l1j.server.server.model.item.PolyBuffSystem;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1Reload implements L1CommandExecutor {

	private L1Reload() {  }

	public static L1CommandExecutor getInstance() {
		return new L1Reload();
	}

	@Override
	public void execute(L1PcInstance gm, String cmdName, String arg) {		
		if (arg.equalsIgnoreCase("컨피그")) {
			Config.load();
			gm.sendPackets("Config File Update Completed..");
		} else if (arg.equalsIgnoreCase("밴아이피")) {
			IpTable.getInstance();
			IpTable.reload();
			gm.sendPackets("BanipTable Update Completed..");
		} else if (arg.equalsIgnoreCase("인형이벤")){
			MagicDollEventMonsterLoader.reload();
			gm.sendPackets("MagicdollEventTable Update Completed..");
		} else if (arg.equalsIgnoreCase("변신주문서")) {
			PolyPotion.reload();
			gm.sendPackets("Poly Potion File Update Completed..");
		} else if (arg.equalsIgnoreCase("클랜") || arg.equalsIgnoreCase("혈맹")) {
			ClanTable.reload(); 
			gm.sendPackets("ClanTable Update Completed..");				
		} else if (arg.equalsIgnoreCase("공성")){
			CastleWarBusiness.getInstance().reload();
			gm.sendPackets("CastleWarBusiness Update Completed..");	
		} else if(arg.equalsIgnoreCase("낙시") || arg.equalsIgnoreCase("낚시")) {
			FishingExpInfo.do_load();
			gm.sendPackets("FishingExpInfo File Update Completed..");
		} else if (arg.equalsIgnoreCase("영혼석")) {	
			MemorialStone.reload();
			gm.sendPackets("MemorialStone File Update Completed..");
		} else if (arg.equalsIgnoreCase("던전")) {
			Dungeon.reload();
			gm.sendPackets("Dungeon Update Completed..");
		} else if(arg.equalsIgnoreCase("아이템스킬") || arg.equalsIgnoreCase("아스")){
			ItemSkillModelLoader.reload();
			gm.sendPackets("ItemskillmodelTable Update Completed..");
		} else if(arg.equalsIgnoreCase("장비교환")){
			ItemExChangeLoader.reload();
			gm.sendPackets("ItemExChangeTable Update Completed..");
		} else if (arg.equalsIgnoreCase("수배금액") || arg.equalsIgnoreCase("수배")) {	
			wantedadena.getInstance().reload();
			gm.sendPackets("DB:[wanted_adena] 테이블 리로드 완료!");
		} else if (arg.equalsIgnoreCase("취소아데나")) {	
			wcanceladena.getInstance().reload();
			gm.sendPackets("DB:[w_cancel_adena] 테이블 리로드 완료!"); 
			
		} else if (arg.equalsIgnoreCase("마법막대")) {
			SpellWand.reload(); 
			gm.sendPackets(new S_SystemMessage("SpellWand.xml  리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("빈주문서")) {
			BlankScroll.reload();
			gm.sendPackets(new S_SystemMessage("BlankScroll.xml  리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("변신버프")) {
			PolyBuffSystem.reload();
			gm.sendPackets(new S_SystemMessage("PolyBuff.xml  리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("마법주문서")) {
			SpellItem.reload();
			gm.sendPackets(new S_SystemMessage("SpellItem.xml  리로드 되었습니다."));
		} else if(arg.equalsIgnoreCase("인벤보너스")) {
			InvenBonusItemLoader.reload();
			gm.sendPackets("\\fYDB:[inventory_bonus_items] 테이블 리로드 완료!");	
		} else if (arg.equalsIgnoreCase("물약")) {
			HealingPotion.reload(); 
			gm.sendPackets(new S_SystemMessage("물약.xml 파일이 업데이트 되었습니다. "));
		} else if(arg.equalsIgnoreCase("스킬확률")){
			SpellProbabilityLoader.reload();
			gm.sendPackets("SkillProbTable Update Completed..");
		} else if (arg.equalsIgnoreCase("지배텔")) {
			MapRingTelTable.reload();
			gm.sendPackets(new S_SystemMessage("teleport_ring_map  리로드..."));
		} else if (arg.equalsIgnoreCase("수배사냥터")) {
			WantedTelTable.reload();
			gm.sendPackets(new S_SystemMessage("wanted_map  리로드..."));
		} else if (arg.equalsIgnoreCase("액션리스너")) {
			MinuteScheduler.getInstance().clear_action_listener();
			ActionListenerLoader.getInstance().updateNpcActionListener();
			gm.sendPackets(new S_SystemMessage("Act_listener_npc Reload Complete..."));
		} else if (arg.equalsIgnoreCase("축복부여") || arg.equalsIgnoreCase("축복주문서") || arg.equalsIgnoreCase("축복부여") || arg.equalsIgnoreCase("축복")) {
			BlessTypeEnchant.reload();
			gm.sendPackets("\\fY 축복아이템 리로드 완료!");	
		} else if (arg.equalsIgnoreCase("특화사냥터")) {
			EventNpcSpawnTable.getInstance().loadEventNpc();
			gm.sendPackets("특화사냥터 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("인챈트") || arg.equalsIgnoreCase("디비인챈트")) {
			ItemSettingTable.getInstance().reload();
			WeaponEnchantInformationTable.reload();
			AccessoryEnchantInformationTable.reload();
			ArmorEnchantInformationTable.reload();
			gm.sendPackets(new S_SystemMessage("인챈트정보가 Reload Complete..."));
		} else if (arg.equalsIgnoreCase("버프")) {
			EtcItemBuffBeadTable.reload();
			gm.sendPackets(new S_SystemMessage("etcitem_buff_bead Reload Complete..."));
		} else if (arg.equalsIgnoreCase("용해제")) {
			ResolventTable.reload();
			gm.sendPackets(new S_SystemMessage("resolvent 테이블이 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("인챈보너스") || arg.equalsIgnoreCase("인첸보너스")) {
			L1EnchantBonus.reload();
			gm.sendPackets(new S_SystemMessage("EnchantBonus.xml 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("배드")){
			BadNamesList.reload();
			gm.sendPackets(new S_SystemMessage("리로드: 욕설 테이블이 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("아인")) {
			L1DragonJewel.reload();
			gm.sendPackets(new S_SystemMessage("DragonJewel.xml Reload Complete..."));
		} else if (arg.equalsIgnoreCase("스킬")) {
			SkillsTable.reload();
			MobSkillTable.reload();
			gm.sendPackets("SkillTable, MobSkillTable Update Completed..");
		} else if (arg.equalsIgnoreCase("요리")) {
			Cooking.reload();
			gm.sendPackets(new S_SystemMessage("Cooking.xml 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("맵")){
			MapsTable.reload();
			gm.sendPackets(new S_SystemMessage("리로드: mapids 테이블이 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("영자상점")) {
			NpcShopTable.reloding();
			NpcShopSpawnTable.reloding();
			gm.sendPackets(new S_SystemMessage("NpcShopTable 정보가 최신화 되었습니다."));	
		} else if (arg.equalsIgnoreCase("물약회복")) {
			HealingPotion.load();
			gm.sendPackets(new S_SystemMessage("HealingPotion.xm Reload Complete..."));
		} else if (arg.equalsIgnoreCase("시작좌표")) {
			CreateCharTeleportTable.reload();
			gm.sendPackets(new S_SystemMessage("CreateCharTeleportTable Reload Complete..."));
		} else if (arg.equalsIgnoreCase("박스메세지") || arg.equalsIgnoreCase("아이템메세지")) {
			ItemMessageBoxTable.reload();
			ItemMessageTable.reload();
			gm.sendPackets("리로드: item_message 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("액션파일")) {
			NpcActionTable.reload();
			gm.sendPackets(new S_SystemMessage("NpcActionTable Reload Complete..."));
		} else if(arg.equalsIgnoreCase("매크로")){
			ServerMacroLoader.reload();			
			gm.sendPackets("tb_ServerMacro 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("패널티아이템")) {
			PenaltyItemTable.getInstance().reload();
			gm.sendPackets(new S_SystemMessage("패널티아이템 테이블이 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("맵타입변경")) {
			MapChangeTable.reload();
			gm.sendPackets(new S_SystemMessage("맵타입변경 리로드..."));
		} else if (arg.equalsIgnoreCase("보스")){
			BossMonsterSpawnList.init();
			gm.sendPackets(new S_SystemMessage("리로드: 보스 테이블이 리로드 되었습니다."));
		} else if(arg.equalsIgnoreCase("보상아이템")) {
			ExchangeItemLoader.reload();
			ExchangeListItemLoader.reload();
			gm.sendPackets("\\fYDB:[exchange_items] 테이블 리로드 완료!");
		} else if(arg.equalsIgnoreCase("보너스드랍")) {
			BonusDropSystemLoader.reload();
			gm.sendPackets("\\fYDB:[bonus_drop_system] 테이블 리로드 완료!");
		} else if (arg.equalsIgnoreCase("버프엔피씨") || arg.equalsIgnoreCase("버프엔")) {
			BuffNpcListTable.getInstance().reload();
			gm.sendPackets("DB:[npc_buff_list] 테이블 리로드 완료!");
		} else if(arg.equalsIgnoreCase("보너스맵")) {
			BonusMapTable.reload();
			gm.sendPackets("\\fYDB:[bonus_map] 테이블 리로드 완료!");
		} else if(arg.equalsIgnoreCase("보너스아이템")) {
			BonusItemLoader.reload();
			gm.sendPackets("\\fYDB:[BonusItemLoader] 테이블 리로드 완료!");	
		} else if (arg.equalsIgnoreCase("텔주문서")) {
			IODTSLoader.reload();
			gm.sendPackets("tb_designate_teleport_scroll 가 리로드 되었습니다."); 
		}else if(arg.equalsIgnoreCase("버경")){
			BugRaceController.load_config();
			gm.sendPackets("\\aHConfig:[bug_race.properties] 리로드 완료!");
		} else if(arg.equalsIgnoreCase("세트")) {
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				for(ItemInstance item : pc.getInventory().getItems()) {
					if (item.isEquipped()) {
						if (SetItemsLoader.getInstance().send_view_set_items_check(item)) {
							pc.getInventory().setEquipped(item, false);
							pc.getEquipSetItemList().add(item);
						}
					}
				}
			}
			
			SetItemsLoader.reload();
			
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (pc.getEquipSetItemList() != null) {
					for (ItemInstance set_item : pc.getEquipSetItemList()) {
						pc.getInventory().setEquipped(set_item, true);
					}
					pc.getEquipSetItemList().clear();
				}
				
				for(ItemInstance item : pc.getInventory().getItems()) {
					if (SetItemsLoader.getInstance().send_view_set_items_check(item)) {
						pc.getInventory().updateItem(item, L1PcInventory.COL_IS_ID);
					}
				}
			}
			
			gm.sendPackets("\\fYDB:[SetItemsLoader] 테이블 리로드 완료!");
		} else if (arg.equalsIgnoreCase("드랍")) {
			DropTable.reload();
	    	gm.sendPackets("DropTable Update Completed..");
		} else if (arg.equalsIgnoreCase("리스타트")) {
			GetBackRestartTable.reload();
			gm.sendPackets("GetBackRestartTable Update Completed..");
		} else if (arg.equalsIgnoreCase("맵기억불가")) {
			MapReturnTable.reload();
			gm.sendPackets("MapReturnTable Update Completed..");
		} else if (arg.equalsIgnoreCase("웨폰대미지")) {
	        WeaponBalanceTable.reload();
	    	gm.sendPackets("WeaponBalanceTable Update Completed..");
		} else if(arg.equalsIgnoreCase("낚시보상") || arg.equalsIgnoreCase("낚시")){		
			FishingRodInfoLoader.reload();
			gm.sendPackets("\\fYDB:[fishing_rod_info] 테이블 리로드 완료!");
		} else if(arg.equalsIgnoreCase("아이템옵션")) {
			if (Config.ServerInfo.ITEMOPTION) {
				ItemInvisibleOptionsLoader.reload();
				gm.sendPackets("\\fYDB:[item_invisible_options] 테이블 리로드 완료!");
			} else {
				gm.sendPackets("\\fYDB:컨피그 아이템옵션 시스템이 꺼져있습니다.");
			}
		} else if (arg.equalsIgnoreCase("경험치")) {
			ExpTable.getInstance().loadExp(true);
			gm.sendPackets(new S_SystemMessage("exp 테이블이 리로드 되었습니다."));
		} else if (arg.equalsIgnoreCase("인형")) {
			MagicDoll.reload();
			gm.sendPackets("\\fYDB:[MagicDoll.xml]  리로드 완료!");	
		} else if(arg.equalsIgnoreCase("마법딜") || arg.equalsIgnoreCase("마법딜레이")){		
			SpriteInformationLoader.getInstance().reloadSpellDelayInformation();
			gm.sendPackets("\\fYDB:[MagicDelay] 테이블 리로드 완료!");	
		} else if (arg.equalsIgnoreCase("아이템") || arg.equalsIgnoreCase("템")){
			ItemTable.reload();
			gm.sendPackets("ItemTable Update Completed..");
		} else if (arg.equalsIgnoreCase("엔피씨") || arg.equalsIgnoreCase("엔")) {
			NpcTable.reload();
			gm.sendPackets("NpcTable Update Completed..");
		} else if (arg.equalsIgnoreCase("엔피씨액션")) { 
			NPCTalkDataTable.reload();
			NpcActionTable.reload();
			gm.sendPackets("NpcActionTable Update Completed..");
		} else if (arg.equalsIgnoreCase("변신")) {
			PolyTable.reload();
			gm.sendPackets("\\fYDB:[polymorphs] 테이블 리로드 완료!");
		} else if (arg.equalsIgnoreCase("상점")){
			ShopTable.reload();
			gm.sendPackets("\\fYDB:[shop] 테이블 리로드 완료!");	
		} else if (arg.equalsIgnoreCase("상점타입")){
			NpcShopAdenTypeTable.getInstance().reload();
			gm.sendPackets("\\fYDB:[npc_shop_aden_type] 테이블 리로드 완료!");	 
		} else if (arg.equalsIgnoreCase("박스")) {
			if (TreasureBox.load()) {
				gm.sendPackets("TreasureBox.xml 파일이 리로드 되었습니다.");
			} else {
				String s = "TreasureBox에 오류가 발견되었습니다.";
				gm.sendPackets(s);
				System.out.println(s);
			}
		} else if (arg.equalsIgnoreCase("제작")) {
			CraftListTable.reload();
			gm.sendPackets("\\fYDB:[제작] 테이블 리로드 완료!");	
		} else {
			gm.sendPackets("\\fY[=============== 리로드 목록 ===============]");
			gm.sendPackets("\\fP[컨피그/인형이벤/혈맹/성장/영혼석]");
			gm.sendPackets("\\fP[아이템스킬/장비교환/수배/취소아데나/마법막대]");
			gm.sendPackets("\\fP[변신버프/마법주문서/인벤보너스/스킬확률/지배텔]");
			gm.sendPackets("\\fP[수배사냥터/액션리스너/축복/특화사냥터/인챈트]");
			gm.sendPackets("\\fP[버프/용해제/인챈보너스/배드/아인/스킬/킬데스]");
			gm.sendPackets("\\fP[요리/맵/영자상점/물약회복/시작좌표/아이템메세지]");
			gm.sendPackets("\\fP[액션파일/매크로/패널티아이템/맵타입변경/보스]");
			gm.sendPackets("\\fP[보너스드랍/버프엔피씨/보너스맵/보너스아이템]");
			gm.sendPackets("\\fP[버경/세트/드랍/맵기억불가/웨폰대미지/몹밸런스]");
			gm.sendPackets("\\fP[낚시보상/아이템옵션/경험치/인형/마법딜/아이템]");
			gm.sendPackets("\\fP[엔피씨/엔피씨액션/변신/상점/상점타입/박스/밸런스]");
			gm.sendPackets("\\fP[던전/빈주문서/보상아이템/텔주문서]");
		}	
	}	
}
