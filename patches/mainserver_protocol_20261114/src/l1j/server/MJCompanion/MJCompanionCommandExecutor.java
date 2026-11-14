package l1j.server.MJCompanion;

import java.util.ArrayList;

import l1j.server.MJCompanion.Basic.Buff.MJCompanionBuffInfo;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJCompanion.Basic.Exp.MJCompanionExpPenalty;
import l1j.server.MJCompanion.Basic.FriendShip.MJCompanionFriendShipBonus;
import l1j.server.MJCompanion.Basic.HuntingGround.MJCompanionHuntingGround;
import l1j.server.MJCompanion.Basic.Joke.MJCompanionJokeInfo;
import l1j.server.MJCompanion.Basic.Potion.MJCompanionPotionInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillEnchantInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillTierOpenInfo;
import l1j.server.MJCompanion.Basic.Stat.MJCompanionStatEffect;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.MJCompanion.Instance.MJCompanionRegenerator;
import l1j.server.MJCompanion.Instance.MJCompanionUpdateFlag;
import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.Exceptions.MJCommandArgsIndexException;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_SKILL_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_SKILL_NOTI.Skill;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_STATUS_NOTI;
import l1j.server.server.utils.IntRange;

public class MJCompanionCommandExecutor implements MJCommand{
	public static void exec(MJCommandArgs args){
		new MJCompanionCommandExecutor().execute(args);
	}
	
	@Override
	public void execute(MJCommandArgs args) {
		
		try{
			switch(args.nextInt()){
			case 1:
				do_reload_commands(args);
				break;
			case 2:
				do_levelup_companion(args);
				break;
			case 3:
				do_give_friend_ship(args);
				break;
			case 4:
				do_oblivion(args);
				break;
			case 5:
				do_stats_initialized(args);
				break;
			case 6:
				do_restore_exp(args);
				break;
			case 7:
				do_traning_initialized(args);
				break;
			case 8:
				do_enchant_skill(args);
				break;
			}
			
		}catch(Exception e){
			args.notify(".펫  [1.리로드][2.레벨업][3.우정포인트 지급]");
			args.notify(".펫  [4.망각][5.스초][6.경험치복구]");
			args.notify(".펫  [7.훈련초기화][8.스킬인챈트]");
			
		}
	}
	
	private L1CompanionInstance find_companion(MJCommandArgs args, String name) throws MJCommandArgsIndexException{
		L1PcInstance pc = L1World.getInstance().findpc(name);
		if(pc == null){
			args.notify(String.format("%s님을 찾을 수 없습니다.", name));
			return null;
		}
		L1CompanionInstance companion = pc.get_companion();
		if(companion == null){
			args.notify(String.format("%s님이 펫을 소환하고 있지 않습니다.", name));
			return null;
		}
		return companion;
	}
	
	private void do_levelup_companion(MJCommandArgs args){
		try{
			String name = args.nextString();
			int level = args.nextInt();
			if (!IntRange.includes(level, 1, CompanionSettings.COMPANION_MAX_LEVEL)) {
				args.notify(String.format("레벨 범위가 벗어났습니다. 1~%d", CompanionSettings.COMPANION_MAX_LEVEL));
				return;
			}
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			int exp = (ExpTable.getExpByLevel(level) + 1);
			companion.update_exp(exp, MJCompanionUpdateFlag.UPDATE_ALL);
			args.notify(String.format("%s의 펫 %s(%d) 레벨을 %d로 조정하였습니다.", name, companion.getName(), companion.getId(), companion.getLevel()));
		}catch(Exception e){
			args.notify(".펫 2(레벨업) [펫주인이름] [레벨]");
		}
	}
	
	private void do_give_friend_ship(MJCommandArgs args){
		try{
			String name = args.nextString();
			int count = args.nextInt();
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			companion.set_friend_ship_marble(companion.get_friend_ship_marble() + count);
			companion.update_instance();
			SC_COMPANION_STATUS_NOTI.send(companion.get_master(), companion, MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP);
			args.notify(String.format("%s의 펫 %s(%d)에게 우정포인트 %d을 지급했습니다.", name, companion.getName(), companion.getId(), count));
		}catch(Exception e){
			args.notify(".펫 3(우정포인트지급) [펫주인이름] [갯수]");
		}
	}
	
	private void do_oblivion(MJCommandArgs args){
		try{
			String name = args.nextString();
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			companion.do_oblivion(true);
			args.notify(String.format("%s의 펫 %s(%d)을 망각시켰습니다.", name, companion.getName(), companion.getId()));
		}catch(Exception e){
			args.notify(".펫 4(망각) [펫주인이름]");
		}
	}
	
	private void do_stats_initialized(MJCommandArgs args){
		try{
			String name = args.nextString();
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			companion.do_stats_initialized();
			args.notify(String.format("%s의 펫 %s(%d)의 스탯을 초기화하였습니다.", name, companion.getName(), companion.getId()));
		}catch(Exception e){
			args.notify(".펫 5(스탯초기화) [펫주인이름]");
		}
	}
	
	private void do_restore_exp(MJCommandArgs args){
		try{
			String name = args.nextString();
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			companion.do_restore_keep_exp(true);
			args.notify(String.format("%s의 펫 %s(%d)의 경험치를 복구하였습니다.", name, companion.getName(), companion.getId()));
		}catch(Exception e){
			args.notify(".펫 6(경험치 복구) [펫주인이름]");
		}
	}
	
	private void do_traning_initialized(MJCommandArgs args){
		MJCompanionRegenerator.getInstance().do_traning_initialized();
		args.notify("모든 펫의 훈련을 초기화하였습니다.");
	}
	
	private void do_enchant_skill(MJCommandArgs args){
		try{
			String name = args.nextString();
			L1CompanionInstance companion = find_companion(args, name);
			if(companion == null)
				return;
			
			ArrayList<Skill> skills = companion.get_all_skills();
			for(Skill sk : skills)
				sk.set_enchant(10);
			companion.update_skills(skills);
			SC_COMPANION_SKILL_NOTI.send(companion.get_master(), companion);
			companion.update_skill_effect();
			SC_COMPANION_STATUS_NOTI.send(companion.get_master(), companion, MJCompanionUpdateFlag.UPDATE_ALL);
			
			args.notify(String.format("%s의 펫 %s(%d)의 스킬들을 10으로 인챈트 시켰습니다.", name, companion.getName(), companion.getId()));
		}catch(Exception e){
			args.notify(".펫 8(스킬인챈트) [펫주인이름]");
		}
	}
	
	private void do_reload_commands(MJCommandArgs args){
		try{
			switch(args.nextInt()){
			case 1:{
				MJCompanionClassInfo.do_load();
				for(L1CompanionInstance companion : L1World.getInstance().getAllCompanions()){
					if(companion == null)
						continue;
						
					MJCompanionInstanceCache.update_cache(companion, true);
					SC_COMPANION_STATUS_NOTI.send(companion.get_master(), companion, MJCompanionUpdateFlag.UPDATE_CLASS_ID);
				}
				args.notify("companion_class 테이블이 리로드 되었습니다.");
				break;
			}
			case 2:{
				CompanionSettings.do_load();
				args.notify("companion_settings 테이블이 리로드 되었습니다.");
				break;
			}
			case 3:{
				MJCompanionHuntingGround.do_load();
				args.notify("companion_hunting_ground 테이블이 리로드 되었습니다.");
				break;
			}
			case 4:{
				MJCompanionJokeInfo.do_load();
				args.notify("companion_joke_effect 테이블이 리로드 되었습니다.");
				break;
			}
			case 5:{
				MJCompanionSkillInfo.do_load();
				for(L1CompanionInstance companion : L1World.getInstance().getAllCompanions()){
					if(companion == null)
						continue;
					
					companion.update_skill_effect();
					SC_COMPANION_STATUS_NOTI.send(companion.get_master(), companion, MJCompanionUpdateFlag.UPDATE_ALL);
				}
				args.notify("companion_skills 테이블이 리로드 되었습니다.");
				break;
			}
			case 6:{
				MJCompanionSkillEnchantInfo.do_load();
				args.notify("companion_skills_enchant 테이블이 리로드 되었습니다.");
				break;
			}
			case 7:{
				MJCompanionSkillTierOpenInfo.do_load();
				args.notify("companion_skills_open 테이블이 리로드 되었습니다.");
				break;
			}
			case 8:{
				MJCompanionStatEffect.do_load();
				for(L1CompanionInstance companion : L1World.getInstance().getAllCompanions()){
					if(companion == null)
						continue;
					
					companion.update_stats();
					SC_COMPANION_STATUS_NOTI.send(companion.get_master(), companion, MJCompanionUpdateFlag.UPDATE_ALL);
				}
				args.notify("companion_stat_effects 테이블이 리로드 되었습니다.");
				break;
			}
			case 9:{
				MJCompanionPotionInfo.do_load();
				args.notify("companion_potion_info 테이블이 리로드 되었습니다.");
				break;
			}
			case 10:{
				MJCompanionBuffInfo.do_load();
				args.notify("companion_buff 테이블이 리로드 되었습니다.");				
				break;
			}
			case 11:{
				MJCompanionFriendShipBonus.do_load();
				args.notify("companion_friend_ship_bonus 테이블이 리로드 되었습니다.");								
				break;
			}
			case 12:{
				MJCompanionExpPenalty.do_load();
				args.notify("companion_penalty 테이블이 리로드 되었습니다.");	
				
			}
			default:
				throw new Exception();
			}
			
		}catch(Exception e){
			args.notify(".펫 1(리로드) [1.클래스정보][2.셋팅][3.사냥터]");
			args.notify(".펫 1(리로드) [4.조크][5.스킬효과][6.스킬인챈트]");
			args.notify(".펫 1(리로드) [7.스킬단계][8.스탯효과][9.포션효과]");
			args.notify(".펫 1(리로드) [10.버프정보][11.프랜드쉽][12.패널티]");
		}
	}
}
