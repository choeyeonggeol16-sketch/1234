/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.datatables;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.utils.SQLUtil;

public class CharBuffTable {
	private CharBuffTable() {}

	private static Logger _log = Logger.getLogger(CharBuffTable.class.getName());

	private static final int[] buffSkill = {
		// 패킷 순서대로 버프 실행
		SkillId.라이트, SkillId.실드, SkillId.쉐도우_아머, SkillId.어스_스킨, SkillId.블레스_오브_어스, SkillId.아이언_스킨,
		SkillId.헤이스트, SkillId.그레이터_헤이스트, SkillId.무빙_악셀레이션, SkillId.윈드_워크,
		SkillId.상태_요정_2단_가속, SkillId.상태_2단_가속, SkillId.상태_1단_가속, SkillId.상태_파란물약, SkillId.홀리_워크,
		SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.드레스_마이티, SkillId.드레스_덱스터리티,
		SkillId.글루잉_오라, SkillId.샤이닝_오라, SkillId.브레이브_오라,
		SkillId.파이어_웨폰, SkillId.블레스_오브_파이어, SkillId.버닝_웨폰,
		SkillId.윈드_샷, SkillId.아이_오브_스톰, SkillId.스톰_샷,
		SkillId.운세_버프_A, SkillId.운세_버프_B, SkillId.운세_버프_C, SkillId.운세_버프_D,
		
		SkillId.디크리즈_웨이트, SkillId.디케이_포션, SkillId.사일런스, SkillId.베놈_레지스트, SkillId.위크니스, SkillId.디지즈,
		SkillId.드레스_이베이젼, SkillId.버서커스, SkillId.네이쳐스_터치, SkillId.윈드_셰클, SkillId.이레이즈_매직, SkillId.어디셔널_파이어,
		SkillId.엘리멘탈_폴다운, SkillId.엘리멘탈_파이어, SkillId.스트라이커_게일, SkillId.소울_오브_프레임, SkillId.천상_물약,
		SkillId.상태_체력_증강, SkillId.상태_마나_증강, SkillId.상태_전투_강화,	SkillId.코마_축복_코인_8, SkillId.BUYER_COOLTIME,
		SkillId.셰이프_체인지, SkillId.상태_채팅_금지,  SkillId.상태_드래곤_진주_3단계, SkillId.지룡_마안, SkillId.수룡_마안,
		
		SkillId.괴물눈_스테이크, SkillId.환상_괴물눈_스테이크, SkillId.시호떡, SkillId.환상_시호떡, SkillId.과일_샐러드, SkillId.환상_과일_샐러드,
		SkillId.과일_탕수육, SkillId.환상_과일_탕수육, SkillId.크러스트_시안_집게발, SkillId.환상_크러스트_시안_집게발, SkillId.코카트리스_스테이크,
		SkillId.환상_코카트리스_스테이크, SkillId.심해어_스튜, SkillId.환상_심해어_스튜, SkillId.버섯스프, SkillId.환상_버섯스프,
		SkillId.메티스_정성스런_스프, SkillId.메티스_정성스런_요리, SkillId.바실리스크_알_스프, SkillId.환상_바실리스크_알_스프,
		SkillId.환상_드레이크_구이, SkillId.환상_대왕거북_구이, SkillId.환상_레서드래곤_날개꼬치
	};

	private static void StoreBuff(int objId, int skillId, int time, int polyId) {
		java.sql.Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO character_buff SET char_obj_id=?, skill_id=?, remaining_time=?, poly_id=?");
			pstm.setInt(1, objId);
			pstm.setInt(2, skillId);
			pstm.setInt(3, time);
			pstm.setInt(4, polyId);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		
		}
	}

	public static void DeleteBuff(L1PcInstance pc) {
		java.sql.Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_buff WHERE char_obj_id=?");
			pstm.setInt(1, pc.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public static void SaveBuff(L1PcInstance pc) {
		for (int skillId : buffSkill) {
			int timeSec = pc.getSkillEffectTimeSec(skillId);
			if (0 < timeSec) {
				int polyId = 0;
				if (skillId == SkillId.셰이프_체인지) {
					polyId = pc.getCurrentSpriteId();
				}
				StoreBuff(pc.getId(), skillId, timeSec, polyId);
			}
		}
	}

}
