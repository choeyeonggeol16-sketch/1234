package l1j.server.server.command.executor;

import java.util.HashMap;

import gnu.trove.map.TIntObjectMap;
import l1j.server.Config;
import l1j.server.server.model.character.pss.PssConfig;
import l1j.server.server.model.character.Attack.Helper.AttackSkillChanceConfig;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatConfig;
import l1j.server.server.model.character.pss.PlaySupport2019;
import l1j.server.DogFight.History.MJDogFightTicketIdManager;
import l1j.server.MJ3SEx.MJNpcSpeedData;
import l1j.server.MJ3SEx.MJSprBoundary;
import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.MJActionListener.ActionListenerLinkageLoader;
import l1j.server.MJActionListener.ActionListenerLoader;
import l1j.server.MJDTSSystem.MJDTSLoader;
import l1j.server.MJEffectSystem.Loader.MJEffectModelLoader;
import l1j.server.MJExpAmpSystem.MJExpAmplifierLoader;
import l1j.server.MJExpRevision.MJFishingExpInfo;


import l1j.server.MJItemSkillSystem.MJItemSkillModelLoader;
import l1j.server.MJPassiveSkill.MJPassiveLoader;
import l1j.server.MJRaidSystem.Loader.MJRaidCompensatorLoader;
import l1j.server.MJServerMacroSystem.MJServerMacroLoader;
import l1j.server.MJTemplate.DateSchedulerModel.MinuteScheduler;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.Announcecycle;
import l1j.server.server.Controller.BossController;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.AdenShopTable;
import l1j.server.server.datatables.AlchemyProbability;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantList;
import l1j.server.server.datatables.ArmorSetTable;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.CharacterQuestTable;
import l1j.server.server.datatables.CharactersGiftItemTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.DropItemTable;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.IncreaseEinhasadMap;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.MJNpcMarkTable;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.datatables.NpcCashShopSpawnTable;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.datatables.NpcTable;

import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.QuestInfoTable;
import l1j.server.server.datatables.QuestMonsterTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.WeaponEnchantList;
import l1j.server.server.datatables.WeaponSkillTable;
import l1j.server.server.model.Beginner;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.item.function.L1HealingPotion;
import l1j.server.server.model.npc.shop.NpcShopManager;
import LIN.SC_ALCHEMY_DESIGN_ACK;
import LIN.SC_CRAFT_LIST_ALL_ACK;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftLoader;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
//import l1j.server.server.model.item.function.L1OrimScrollEnchant;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1RaceTicket;

public class L1Reload implements L1CommandExecutor {

	private L1Reload() {  }

	public static L1CommandExecutor getInstance() {
		return new L1Reload();
	}

	@Override
	public void execute(L1PcInstance gm, String cmdName, String arg) {		
        if (arg != null && arg.trim().equalsIgnoreCase("pss")) {
            try {
                PssConfig.Settings updated = PssConfig.reload();
                PlaySupport2019.refreshForReload();
                SC_SystemMessage.send(gm, "PSS 설정 적용: 하루 " + (updated.dailySeconds() / 60)
                        + "분, 매일 " + updated.resetTimeText()
                        + "(한국 시간) 초기화. 실행 중인 PSS는 계속 유지됩니다.");
            } catch (java.io.IOException | IllegalArgumentException e) {
                SC_SystemMessage.send(gm, "PSS 설정 로딩 실패: " + e.getMessage());
            }
            return;
        }
        if (arg != null && (arg.trim().equalsIgnoreCase("인형발동")
                || arg.trim().equalsIgnoreCase("인형확률"))) {
            try {
                MagicDollCombatConfig.reload();
                SC_SystemMessage.send(gm, "마법인형 발동 설정 적용 완료 (MagicDollProc.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "마법인형 발동 설정 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
        if (arg != null && (arg.trim().equalsIgnoreCase("키링크밸런스")
                || arg.trim().equalsIgnoreCase("키링크"))) {
            try {
                CAttackAction.reloadKeylinkBalance();
                SC_SystemMessage.send(gm, "키링크 방어자 직업 계수 8종 적용 완료 (KeylinkBalance.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "키링크 밸런스 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
        if (arg != null && arg.trim().equalsIgnoreCase("스킬확률")) {
            try {
                AttackSkillChanceConfig.reload();
                SC_SystemMessage.send(gm, "스킬 발동 확률 설정 적용 완료 (SkillChance.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "스킬 발동 확률 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
		if (arg.equalsIgnoreCase("몹드랍")) {
			DropTable.reload();
			gm.sendPackets("droplist 테이블이 리로드 되었습니다.");	
	//	} else if (arg.equalsIgnoreCase("오림주문서")) {	
		//	L1OrimScrollEnchant.reload();
		//	gm.sendPackets("오림 장신구 강화주문서 정보가 리로드 되었습니다.");
 
		} else if (arg.equalsIgnoreCase("창고이용")) {
			NoShopAndWare.getInstance().reload();;
			gm.sendPackets("NoShopAndWare 테이블이 리로드 되었습니다.");	
		}else if (arg.equalsIgnoreCase("노드랍")) {
				NoDropItem.getInstance().reload();;
				gm.sendPackets("nodropitem 테이블이 리로드 되었습니다.");	
		}else if(arg.equalsIgnoreCase("낚시경험치")){
			MJFishingExpInfo.do_load();
			gm.sendPackets("fishing_exp_info 테이블이 리로드 되었습니다.");
		}else if(arg.equalsIgnoreCase("변신레벨")){
			MJSprBoundary.do_load();
			gm.sendPackets("spr_boundary 테이블이 리로드 되었습니다.");
		}else if(arg.equalsIgnoreCase("엔피씨속도")){
			MJNpcSpeedData.do_load();
			for(L1NpcInstance npc : L1World.getInstance().getAllNpc())
				MJNpcSpeedData.install_npc(npc);
			gm.sendPackets("npc_speed_data  테이블이 리로드 되었습니다.");

		} else if (arg.equalsIgnoreCase("무한대전")) {
			 UBTable.reload();
			gm.sendPackets("ub_settings/ub_manager_npc_id/ub_times/spawnlist_ub/ub_rank 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("겟백")) {
			 Getback.loadGetBack();
			gm.sendPackets("getback 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("겟백리스타트")) {
			 GetBackRestartTable.reload();
				gm.sendPackets("getback_restart 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("용드랍")) {
			 MJRaidCompensatorLoader.reload();
			gm.sendPackets("tb_mjraid_compensators 테이블이 리로드 되었습니다.");	
		 } else if(arg.equalsIgnoreCase("버경")){
			BugRaceController.load_config();
			gm.sendPackets("bug_race.properties가 리로드 되었습니다.");	
		} else if(arg.equalsIgnoreCase("커스시간")){
			MonsterParalyzeDelay.reload();
			gm.sendPackets("monster_paralyze 테이블이 리로드 되었습니다.");				
		}else if(arg.equalsIgnoreCase("자존심")){
			PrideLoadManager.reload();
			gm.sendPackets("pride_items 테이블/price_system.properties이 리로드 되었습니다.");			
		} else if(arg.equalsIgnoreCase("맵아인")){
			IncreaseEinhasadMap.reload();
			gm.sendPackets("tb_increase_einhasad_map 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("패시브스킬")){
			MJPassiveLoader.reload();
			gm.sendPackets("passive_book_mapped 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("텔주문서")){
			MJDTSLoader.reload();
			gm.sendPackets("tb_designate_teleport_scroll 테이블이 리로드되었습니다.");
		} else if(arg.equalsIgnoreCase("액션리스너")){
			MinuteScheduler.getInstance().clear_action_listener();
			ActionListenerLoader.getInstance().updateNpcActionListener();
			ActionListenerLinkageLoader.reload();
			gm.sendPackets("액션 리스너가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("자동공지")){
			Announcecycle.getInstance().reloadAnnouncecycle();
			gm.sendPackets("Announcecycle이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("제작확률")){
			CraftProbability.reload();
			gm.sendPackets("craft_probability 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("인형확률")){
			AlchemyProbability.reload();
			gm.sendPackets("tb_alchemy_probability 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("제작인형컨픽")){
			Config.loadCraftConfig();
			gm.sendPackets("제작/인형 컨픽이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("크레프트인포")){
		    CraftLoader.load();
		    LIN.SC_CRAFT_LIST_ALL_ACK.buildCaches(); 
			gm.sendPackets("craftinfo.dat가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("인형인포")){
			LIN.SC_ALCHEMY_DESIGN_ACK.load(); 
			gm.sendPackets("alchemyinfo.dat가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("제작엔피씨")){
			LIN.SC_CRAFT_LIST_ALL_ACK.buildNpcCaches();
			gm.sendPackets("craftlist 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("아이템스킬")){
			MJItemSkillModelLoader.reload();
			gm.sendPackets("tb_itemskill_model 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("매크로")){
			MJServerMacroLoader.reload();			
			gm.sendPackets("tb_ServerMacro 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("아인증폭")){
			MJExpAmplifierLoader.reload();
			gm.sendPackets("MJExpAmplifierLoader 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("확")){
			MJExpAmplifierLoader.reload();
			gm.sendPackets("MJExpAmplifierLoader 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("엔피씨마크")){
			MJNpcMarkTable.reload();
			gm.sendPackets("NpcMark 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨액션")) { 
			NPCTalkDataTable.reload();
			gm.sendPackets("NpcAction 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨")) {
			NpcTable.reload();
			gm.sendPackets("NpcTable/npc_born 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("템드랍아이템")) {
			DropItemTable.reload();
			gm.sendPackets("drop_item 테이블이 리로드 되었습니다.");
//		} else if (arg.equalsIgnoreCase("용해제2")) {
//			ResolventTable1.reload();
//			gm.sendPackets("resolvent1 테이블이 리로드 최신화되었습니다.");
		} else if (arg.equalsIgnoreCase("변신")) {
			PolyTable.reload();
			gm.sendPackets("polymorphs 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("용해제1")) {
			ResolventTable.reload();
			gm.sendPackets("resolvent 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("박스")) {
			if(L1TreasureBox.load())
				gm.sendPackets("TreasureBox.xml 파일이 리로드 되었습니다.");
			else{
				String s = "TreasureBox에 오류가 발견되었습니다.";
				gm.sendPackets(s);
				System.out.println(s);
			}
		} else if (arg.equalsIgnoreCase("물약회복")) {
			L1HealingPotion.reload();
			gm.sendPackets("HealingPotion.xml 파일이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("컨피그")) {			
			Config.load();
			gm.sendPackets("config 폴더에 파일이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("스킬")) {
			SkillsTable.reload();
			gm.sendPackets("Skill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("몹스킬")) {
			MobSkillTable.reload();
			gm.sendPackets("mobskill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨액션")) { 
			NPCTalkDataTable.reload();
			gm.sendPackets("NpcAction 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("무기스킬")) {
			WeaponSkillTable.reload();
			gm.sendPackets("WeaponSkill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("영자상점")) {
			NpcShopManager.getInstance().reloadAndApply();
			gm.sendPackets("영자상점 테이블이 리로드 되었습니다.");	
		}else if (arg.equalsIgnoreCase("레벨퀘스트")) {
			CharactersGiftItemTable.reload();
			gm.sendPackets("characters_levelup_item 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("밴아이피")) {
			IpTable.getInstance();
			IpTable.reload();
			gm.sendPackets("banIp 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("아이템")){
			ItemTable.reload();
			gm.sendPackets("아이템 정보가 리로드 되었습니다.");
			TIntObjectMap<L1Item> items = ItemTable.getInstance().getAllTemplates();
			HashMap<Integer, L1RaceTicket> map = BugRaceController.getInstance().getAllTemplates();
			for(L1RaceTicket t : map.values()){
				if(t != null)
					items.put(t.getItemId(), t);
			}
			MJDogFightTicketIdManager.getInstance().update_item_template();
		} else if (arg.equalsIgnoreCase("상점")){
			ShopTable.reload();
			gm.sendPackets("shop 테이블이 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("웨폰데미지")
                || arg.equalsIgnoreCase("웨폰대미지")) {
            if (WeaponAddDamage.reload()) {
                final WeaponAddDamage loaded = WeaponAddDamage.getInstance();
                gm.sendPackets("weapon_damage 전체 리로드 완료: " + loaded.getEntryCount()
                        + "개 / 명중 보정 " + loaded.getNonZeroHitCount()
                        + "개 / 데미지 보정 " + loaded.getNonZeroDamageCount() + "개");
            } else {
                gm.sendPackets("weapon_damage 리로드 실패: 기존 설정 유지 (서버 로그 확인)");
            }
		} else if (arg.equalsIgnoreCase("클랜데이터")){
			ClanTable.reload();
			MJCastleWarBusiness.getInstance().reload();
			gm.sendPackets("리로드: clan_data 테이블이 리로드 되었습니다.");		
		} else if (arg.equalsIgnoreCase("공성")){
			MJCastleWarBusiness.getInstance().reload();
			gm.sendPackets("리로드: castle 테이블이 리로드 되었습니다.");		
		} else if (arg.equalsIgnoreCase("스폰리스트")){
			
			gm.sendPackets("[.엔피씨다운 몬스터 배치or삭제]를 이용하시길 바랍니다.");	
//		} else if (arg.equalsIgnoreCase("엔스폰리스트")){
//			NpcSpawnTable.getInstance().reload();
//			gm.sendPackets("리로드: spawnlist_npc 테이블 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("엔스폰리스트")){
			
			gm.sendPackets("[.엔피씨다운 엔피씨 배치or삭제]를 이용하시길 바랍니다.");	
		} else if (arg.equalsIgnoreCase("엔캐샵스폰리스트")){
			NpcCashShopSpawnTable.reload();
			gm.sendPackets("리로드: spawnlist_npc_cash_shop 테이블이 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("엔피씨채팅")){
			NpcChatTable.reload();
			gm.sendPackets("리로드: npcchat 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("던전")){
			Dungeon.reload();
			gm.sendPackets("리로드: dungeon 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("문")){
			DoorSpawnTable.reload();
			gm.sendPackets("리로드: spawnlist_door 테이블이 리로드 되었습니다.");
//		} else if (arg.equalsIgnoreCase("맵")){
//			MapsTable.reload();
//			gm.sendPackets("리로드: mapids 테이블이 리로드 되었습니다.");
//		} else if (arg.equalsIgnoreCase("벨런스")){
//			gm.sendPackets("리로드: character_balance 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("비기너")){
		//} else if (arg.equalsIgnoreCase("신규지급템")){
			Beginner.reload();
			gm.sendPackets("리로드: beginner 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("오토드랍")){
			AutoLoot.reload();
			gm.sendPackets("리로드: autoloot 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("아머인챈트")){
			ArmorEnchantList.reload();
			gm.sendPackets("리로드: armor_enchant_list 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("웨폰인챈트")){
			WeaponEnchantList.reload();
			gm.sendPackets("리로드: weapon_enchant_list 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("엔샵")){
			AdenShopTable.reload();
			gm.sendPackets("리로드: shop_aden 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("방어구인챈정보")) {
			ArmorEnchantInformationTable.reload();
			gm.sendPackets("리로드: armor_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("무기인챈정보")) {
			WeaponEnchantInformationTable.reload();
			gm.sendPackets("리로드: weapon_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("악세인챈정보")) {
			AccessoryEnchantInformationTable.reload();
			gm.sendPackets("리로드: accessory_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("세트아이템")) {
			ArmorSetTable.reload();
			gm.sendPackets("리로드: armor_set 테이블이 리로드 되었습니다");
//		} else if (arg.equalsIgnoreCase("이벤트알람")) {
//			try {
//				L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f:보스알람시간이 재정비되었습니다." ));
//				L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f:리스 후 보스알람시간표기가 리로드됩니다." ));
//				
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
		} else if (arg.equalsIgnoreCase("보스스폰")) {
			if (BossController.reload()) {
				gm.sendPackets("리로드: spawnlist_boss_hot 테이블이 리로드 되었습니다");
			} else {
				gm.sendPackets("리로드 실패: 보스 스폰 DB 오류. 기존 예약을 유지합니다.");
			}
		} else if (arg.equalsIgnoreCase("클라퀘스트")) {
			QuestInfoTable.reload();
			gm.sendPackets("리로드: QuestInfoTable 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("클라퀘스트캐릭터")) {
			CharacterQuestTable.reload();
			gm.sendPackets("리로드: CharacterQuestTable 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("답장")) {
			L1LetterCommand.reload();
			gm.sendPackets("리로드: letter_command 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("마법딜")){			
			SpriteInformationLoader.getInstance().reloadSpellDelayInformation();
			gm.sendPackets("리로드: MagicDelay 테이블이 리로드 되었습니다");	
		} else if(arg.equalsIgnoreCase("광역마법")){
			MJEffectModelLoader.reload();
			gm.sendPackets("리로드: EffectModel 테이블이 리로드 되었습니다.");	
			// 퀘스트 몬스터 시스템
		} else if (arg.equalsIgnoreCase("퀘스트몬스터")) {
			QuestMonsterTable.reload();
			gm.sendPackets("리로드: quest_monster 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("신규보호몬스터")){
			UserProtectMonsterTable.getInstance().reload();
			gm.sendPackets("신규보호몬스터 테이블이 리로드 되었습니다.");
		} else {
			gm.sendPackets("---------------리로드 목록을 보여드립니다-------------");
			gm.sendPackets("몹드랍.템드랍아이템.변신.상점.박스.스킬.몹스킬");
			gm.sendPackets("아이템.용해제1~2.무기스킬.레벨퀘스트");
			gm.sendPackets("컨피그.물약회복.밴아이피.용해제아덴.이벤트알람");
			gm.sendPackets("엔피씨.엔피씨액션.클랜데이터.공성.보스스폰");
			gm.sendPackets("스폰리스트.엔스폰리스트.엔피씨채팅.무기인챈트");
			gm.sendPackets("엔캐샵스폰리스트.던전.맵.신규지급템.답장");
			gm.sendPackets("오토드랍.엔샵 .매크로 .악세인챈정보");
			gm.sendPackets("세트아이템.로봇.제작엔피씨.자동공지.북시스템");
			gm.sendPackets("클라퀘스트.클라퀘스트캐릭터.맵아인");
			gm.sendPackets("마법딜 .광역마법 .엔피씨마크 .아인증폭 .노드랍");
			gm.sendPackets("아이템스킬 .크레프트인포 .인형인포 .제작인형컨픽");
			gm.sendPackets("액션리스너 .제작확률 .인형확률 .엔피씨다운");
			gm.sendPackets("텔주문서 .푸쉬시스템 .퀘스트몬스터 .참시스템");
			gm.sendPackets("신규보호몬스터 .자존심 .커스시간 .버경 .회복률");
			gm.sendPackets("아머클래스 .엔피씨속도 .낚시경험치");
			gm.sendPackets("보너스아이템");
			gm.sendPackets("영자상점 .영자상점물품 .무기인챈정보 .방어구인챈정보");
			gm.sendPackets("악세인챈정보 .웨폰데미지 .스킬확률 .키링크밸런스");
		}		
	}	
}
