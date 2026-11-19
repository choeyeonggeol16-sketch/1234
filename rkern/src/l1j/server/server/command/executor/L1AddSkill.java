package l1j.server.server.command.executor;

import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.StringTokenizer;

import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJPassiveSkill.MJPassiveInfo;
import l1j.server.MJPassiveSkill.MJPassiveLoader;
import l1j.server.MJPassiveSkill.MJPassiveUserLoader;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;

public class L1AddSkill implements L1CommandExecutor {

	private L1AddSkill() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1AddSkill();
	}

	@Override
	public void execute(L1PcInstance gm, String cmdName, String arg) {
		try {
			int cnt = 0; // 루프 카운터
			String skill_name = ""; // 스킬명
			int skill_id = 0; // 스킬 ID
			
			StringTokenizer st = new StringTokenizer(arg);
			String charname = st.nextToken();
			L1PcInstance pc = L1World.getInstance().getPlayer(charname);
			
			if(pc == null)
				SC_SystemMessage.send(gm, "경고: 접속중 캐릭터가 아닙니다.");
			int object_id = pc.getId(); // 캐릭터의 objectid를 취득
			SC_SkillSound.send(pc, object_id, '\343'); // 마법 습득의 효과음을 울린다
			SC_SkillSound.broadcast(pc, object_id, '\343');
			ArrayList<MJPassiveInfo> passives = null;
			if (pc.isCrown()) {
				passives = MJPassiveLoader.getInstance().fromClassType(0);
				SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 16; cnt++) { // LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 113; cnt <= 122; cnt++) {// 프리 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isKnight()) {
				passives = MJPassiveLoader.getInstance().fromClassType(1);
				SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 8; cnt++) {// LV1 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 87; cnt <= 94; cnt++) {// 나이트 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				if (!pc.hasSkillEffect(L1SkillId.프라이드)) {
					pc.프라이드(true);
					pc.setSkillEffect(L1SkillId.프라이드, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.솔리드캐리지)) {
					pc.솔리드캐리지(true);
					pc.setSkillEffect(L1SkillId.솔리드캐리지, -1);
				
				}
			} else if (pc.isElf()) {
				passives = MJPassiveLoader.getInstance().fromClassType(2);
				SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 127, 3, 255, 255, 255, 255, 0, 0, 0, 0, 0,
						0, 0, 0, pc.getElfAttr());
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 48; cnt++) {// LV1~6 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 129; cnt <= 176; cnt++) {// 에르프 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isWizard()) {
				passives = MJPassiveLoader.getInstance().fromClassType(3);
				SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 1; cnt <= 80; cnt++) {// LV1~10 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isDarkelf()) {
				passives = MJPassiveLoader.getInstance().fromClassType(4);
				SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 127, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 16; cnt++) {// LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 97; cnt <= 112; cnt++) { // DE마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				{
					l1skills = SkillsTable.getInstance().getTemplate(234); // 스킬 정보를 취득
					if(l1skills != null){
						skill_name = l1skills.getName();
						skill_id = l1skills.getSkillId();
						SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
					}
				}
				if (!pc.hasSkillEffect(L1SkillId.버닝)) {
					pc.버닝(true);
					pc.setSkillEffect(L1SkillId.버닝, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.이베이젼)) {
					pc.이베이젼(true);
					pc.setSkillEffect(L1SkillId.이베이젼, -1);
				
				}
				
			} else if (pc.isDragonknight()) {
				passives = MJPassiveLoader.getInstance().fromClassType(5);
				SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 177; cnt <= 200; cnt++) { // 용기사스킬
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isBlackwizard()) {
				passives = MJPassiveLoader.getInstance().fromClassType(6);
				SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 201; cnt <= 224; cnt++) { // 환술사스킬
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				if (!pc.hasSkillEffect(L1SkillId.일루션리치)) {
					pc.일루션리치(true);
					pc.setSkillEffect(L1SkillId.일루션리치, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.일루션골렘)) {
					pc.일루션골렘(true);
					pc.setSkillEffect(L1SkillId.일루션골렘, -1);
				
				}
				
			} else if (pc.isWarrior()) {
				passives = MJPassiveLoader.getInstance().fromClassType(7);
				SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); // 전사꺼
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 8; cnt++) {// LV1 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 225; cnt <= 240; cnt++) {// 전사 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			}
			if(passives != null){
				for(MJPassiveInfo pInfo : passives){
					if(pc.isPassive(pInfo.getPassiveId()))
						continue;
					
					int passiveId = pInfo.getPassiveId();
					if(passiveId == MJPassiveID.DOUBLE_BREAK_DESTINY.toInt()){
						if(pc.hasSkillEffect(L1SkillId.DOUBLE_BRAKE))
							pc.removeSkillEffect(L1SkillId.DOUBLE_BRAKE);
					}
					pc.addPassive(pInfo);
					MJPassiveUserLoader.store(pc, pInfo);
				}
			}
			
			SC_SystemMessage.send(gm, "해당 캐릭터 스킬마스터 완료");
		} catch (Exception e) {
			SC_SystemMessage.send(gm, ".스킬마스터 [캐릭명] 명령하세요.");
		}
	}
}
