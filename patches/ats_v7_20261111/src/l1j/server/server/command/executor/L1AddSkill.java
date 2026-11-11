package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Skills;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SQLUtil;

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
				gm.sendPackets(new S_SystemMessage("접속중 캐릭터가 아닙니다."));
			int object_id = pc.getId(); // 캐릭터의 objectid를 취득
			S_SkillSound ss = S_SkillSound.get(object_id, 227);
			pc.sendPackets(ss); // 마법 습득의 효과음을 울린다
			pc.broadcastPacket(ss);
			if (pc.isCrown()) {
				L1Skills l1skills = null;
				for (cnt = 1; cnt <= 16; cnt++) { // LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;		
					skill_name = l1skills.getName();
					if (skill_name.equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 113; cnt <= 123; cnt++) {// 프리 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_name = l1skills.getName();
					if (skill_name.equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(241); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(33); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

			} else if (pc.isKnight()) {
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 8; cnt++) {// LV1 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 87; cnt <= 94; cnt++) {// 나이트 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(242); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
			} else if (pc.isElf()) {
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 48; cnt++) {// LV1~6 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 129; cnt <= 179; cnt++) {// 에르프 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(5113); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(5114); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isWizard()) {
				L1Skills l1skills = null;
				for (cnt = 1; cnt <= 80; cnt++) {// LV1~10 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(243); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				l1skills = SkillsTable.getInstance().getTemplate(5115); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(5152); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
			} else if (pc.isDarkelf()) {
				L1Skills l1skills = null;
				for (cnt = 1; cnt <= 16; cnt++) {// LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 97; cnt <= 112; cnt++) { // DE마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					if(l1skills.getName().equalsIgnoreCase("none"))
						continue;
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(234); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(244); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				l1skills = SkillsTable.getInstance().getTemplate(199); // 스킬 정보를 취득
				if(l1skills != null){
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			}
			skills(pc);
			gm.sendPackets(new S_SystemMessage("해당 캐릭터 스킬마스터 완료"));
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".스킬마스터 [캐릭명] 명령하세요."));
		}
	}
	
	private void skills(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_skills WHERE char_obj_id=?");
			pstm.setInt(1, pc.getId());
			rs = pstm.executeQuery();
			int i = 0;
			int lv1 = 0;
			int lv2 = 0;
			int lv3 = 0;
			int lv4 = 0;
			int lv5 = 0;
			int lv6 = 0;
			int lv7 = 0;
			int lv8 = 0;
			int lv9 = 0;
			int lv10 = 0;
			int lv11 = 0;
			int lv12 = 0;
			int lv13 = 0;
			int lv14 = 0;
			int lv15 = 0;
			int lv16 = 0;
			int lv17 = 0;
			int lv18 = 0;
			int lv19 = 0;
			int lv20 = 0;
			int lv21 = 0;
			int lv22 = 0;
			int lv23 = 0;
			int lv24 = 0;
			while (rs.next()) {
				int skillId = rs.getInt("skill_id");
				L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
				if (l1skills.getSkillLevel() == 1) {
					lv1 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 2) {
					lv2 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 3) {
					lv3 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 4) {
					lv4 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 5) {
					lv5 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 6) {
					lv6 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 7) {
					lv7 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 8) {
					lv8 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 9) {
					lv9 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 10) {
					lv10 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 11) {
					lv11 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 12) {
					lv12 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 13) {
					lv13 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 14) {
					lv14 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 15) {
					lv15 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 16) {
					lv16 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 17) {
					lv17 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 18) {
					lv18 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 19) {
					lv19 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 20) {
					lv20 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 21) {
					lv21 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 22) {
					lv22 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 23) {
					lv23 |= l1skills.getId();
				}
				if (l1skills.getSkillLevel() == 24) {
					lv24 |= l1skills.getId();
				}
				i = lv1 + lv2 + lv3 + lv4 + lv5 + lv6 + lv7 + lv8 + lv9 + lv10 + lv11 + lv12 + lv13 + lv14 + lv15 + lv16
						+ lv17 + lv18 + lv19 + lv20 + lv21 + lv22 + lv23 + lv24;

			}
			if (i > 0) {
				pc.sendPackets(new S_AddSkill(lv1, lv2, lv3, lv4, lv5, lv6, lv7, lv8, lv9, lv10, lv11, lv12, lv13, lv14,
						lv15, lv16, lv17, lv18, lv19, lv20, lv21, lv22, lv23, lv24));
				// _log.warning("여기 끊어 오군요＠직역");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
