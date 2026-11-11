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

package l1j.server.server.clientpackets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Templates.L1Skills;
import l1j.server.server.BadNamesList;
import l1j.server.server.GameClient;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.CreateCharTeleportTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Beginner;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_CharCreateStatus;
import l1j.server.server.serverpackets.S_CommonNews;
import l1j.server.server.serverpackets.S_NewCharPacket;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket

public class C_CreateChar extends ClientBasePacket {

	private static Logger _log = Logger.getLogger(C_CreateChar.class.getName());
	private static final String C_CREATE_CHAR = "[C] C_CreateChar";

	public C_CreateChar(byte[] abyte0, GameClient client) throws Exception {
		super(abyte0);
		L1PcInstance pc = new L1PcInstance();
		String name = readS();
		Calendar cal = Calendar.getInstance();
		int 시간 = Calendar.HOUR;
		int 분 = Calendar.MINUTE;
		/** 0 오전 , 1 오후 * */
		String 오전오후 = "오후";
		if (cal.get(Calendar.AM_PM) == 0) {
			오전오후 = "오전";
		}
		if (name.length() > 50) {
			System.out.println("■ 패킷공격 ID ■ :" + name);
			System.out.println("■ 패킷공격 IP ■ :" + client.getIp());
			client.kick();
			return;
		}
		name = name.replaceAll("\\s", "");
		name = name.replaceAll("@", "");
		if (name.length() <= 0) { // 한글자 아이디 안되게 픽스
			// 영어는 한글자당 1의 길이, 한글은 한글자당 2의 길이
			S_CharCreateStatus s_charcreatestatus = new S_CharCreateStatus(S_CharCreateStatus.REASON_INVALID_NAME);
			client.sendPacket(s_charcreatestatus);
			return;
		}
		//## A93 단일 자음, 모음의 캐릭터 생성 못하게 
		for (int i = 0;i<name.length();i++) {  
			if (name.charAt(i) == 'ㄱ' || name.charAt(i) == 'ㄲ' || name.charAt(i) == 'ㄴ' || name.charAt(i) == 'ㄷ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㄸ' || name.charAt(i) == 'ㄹ' || name.charAt(i) == 'ㅁ' || name.charAt(i) == 'ㅂ' ||    //한문자(char)단위로 비교 
					name.charAt(i) == 'ㅃ' || name.charAt(i) == 'ㅅ' || name.charAt(i) == 'ㅆ' || name.charAt(i) == 'ㅇ' ||    //한문자(char)단위로 비교
					name.charAt(i) == 'ㅈ' || name.charAt(i) == 'ㅉ' || name.charAt(i) == 'ㅊ' || name.charAt(i) == 'ㅋ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅌ' || name.charAt(i) == 'ㅍ' || name.charAt(i) == 'ㅎ' || name.charAt(i) == 'ㅛ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅕ' || name.charAt(i) == 'ㅑ' || name.charAt(i) == 'ㅐ' || name.charAt(i) == 'ㅔ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅗ' || name.charAt(i) == 'ㅓ' || name.charAt(i) == 'ㅏ' || name.charAt(i) == 'ㅣ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅠ' || name.charAt(i) == 'ㅜ' || name.charAt(i) == 'ㅡ' || name.charAt(i) == 'ㅒ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅖ' || name.charAt(i) == 'ㅢ' || name.charAt(i) == 'ㅟ' || name.charAt(i) == 'ㅝ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅞ' || name.charAt(i) == 'ㅙ' || name.charAt(i) == 'ㅚ' || name.charAt(i) == 'ㅘ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == '씹' || name.charAt(i) == '좃' || name.charAt(i) == '좆' || name.charAt(i) == 'ㅤ'){
				S_CharCreateStatus s_charcreatestatus = new S_CharCreateStatus(S_CharCreateStatus.REASON_INVALID_NAME);
				client.sendPacket(s_charcreatestatus);
				return; 
			}
		}
//## A93 단일 자음, 모음의 캐릭터 생성 못하게 

		if (isInvalidName(name)) {
			S_CharCreateStatus s_charcreatestatus = new S_CharCreateStatus(S_CharCreateStatus.REASON_INVALID_NAME);
			client.sendPacket(s_charcreatestatus);
			return;
		}

//## A93 단일 자음, 모음의 캐릭터 생성 못하게 
		if (BadNamesList.getInstance().isBadName(name)) {
			S_CharCreateStatus s_charcreatestatus = new S_CharCreateStatus(S_CharCreateStatus.REASON_INVALID_NAME);
			_log.info("생성 금지된 캐릭터 이름, 생성실패" + "아이디 : " + name);
			client.sendPacket(s_charcreatestatus);
			return;
		}
//## A93 단일 자음, 모음의 캐릭터 생성 못하게 

		if (CharacterTable.doesCharNameExist(name) || L1World.getInstance().getPlayer(name) != null) {
			_log.fine("charname: " + pc.getName() + " already exists. creation failed.");
			S_CharCreateStatus s_charcreatestatus1 = new S_CharCreateStatus(S_CharCreateStatus.REASON_ALREADY_EXSISTS);
			client.sendPacket(s_charcreatestatus1);
			return;
		}
		
		if (client.getAccount().countCharacters() >= 8) {
			_log.fine("account: " + client.getAccountName() + " 4를 넘는 캐릭터 작성 요구.");
			S_CharCreateStatus s_charcreatestatus1 = new S_CharCreateStatus(S_CharCreateStatus.REASON_WRONG_AMOUNT);
			client.sendPacket(s_charcreatestatus1);
			return;
		}


		pc.setName(name);
		pc.setType(readC());
		pc.set_sex(readC());
		pc.getAbility().setBaseStr((byte) readC());
		pc.getAbility().setBaseDex((byte) readC());
		pc.getAbility().setBaseCon((byte) readC());
		pc.getAbility().setBaseWis((byte) readC());
		pc.getAbility().setBaseCha((byte) readC());
		pc.getAbility().setBaseInt((byte) readC());

		int statusAmount = pc.getAbility().getAmount();

		if (pc.getAbility().getBaseStr() > 20 
				|| pc.getAbility().getBaseDex() > 18
				|| pc.getAbility().getBaseCon() > 18
				|| pc.getAbility().getBaseWis() > 18
				|| pc.getAbility().getBaseCha() > 18
				|| pc.getAbility().getBaseInt() > 18
				|| statusAmount != 75) {
			_log.finest("Character have wrong value");
			S_CharCreateStatus s_charcreatestatus3 = new S_CharCreateStatus(S_CharCreateStatus.REASON_WRONG_AMOUNT);
			client.sendPacket(s_charcreatestatus3); 
			return;
		}
		
		if (pc.getType() >= 5 && pc.getType() <= 6) {
			client.sendPacket(new S_CommonNews("생성이 불가능한 클래스입니다."));
			client.kick();
			client.close();
			return;
		} 

		_log.fine("charname: " + pc.getName() + " classId: " + pc.getClassId());
		S_CharCreateStatus s_charcreatestatus2 = new S_CharCreateStatus(S_CharCreateStatus.REASON_OK);
		client.sendPacket(s_charcreatestatus2);
		initNewChar(client, pc);
		System.out.println("" + 오전오후 + " " + cal.get(시간) + "시" + cal.get(분) + "분" + "   ■ 신규 캐릭: [" + pc.getName() + "] 생성완료 ■");
	}

	//노변신 상태의 이미지들
	private static final int[] MALE_LIST = new int[] { 0, 61, 138, 734, 2786 };
	private static final int[] FEMALE_LIST = new int[] { 1, 48, 37, 1186, 2796 }; 

	private static void initNewChar(GameClient client, L1PcInstance pc) throws IOException, Exception {

		short init_hp = 0;
		short init_mp = 0;

		pc.setId(IdFactory.getInstance().nextId());

		if (pc.get_sex() == 0) {
			pc.setClassId(MALE_LIST[pc.getType()]);
		} else {
			pc.setClassId(FEMALE_LIST[pc.getType()]);
		}
		String setName = "시작좌표";
		CreateCharTeleportTable mbt = CreateCharTeleportTable.getInstance();
		CreateCharTeleportTable.BooksTeleportLoc bl = mbt.getTeleportLoc(setName);
		if (bl == null){
			return;
		}
		
		L1Location loc = new L1Location(bl._getX, bl._getY, bl._getMapId).randomLocation(3, true);
		pc.setX(loc.getX());
		pc.setY(loc.getY());
		pc.setMap((short) loc.getMapId());
		
		
		
		pc.setHeading(5);
		pc.setLawful(0);
		if (pc.isCrown()) { // 군주
			init_hp = 14;
			switch (pc.getAbility().getBaseWis()) {
			case 11:
				init_mp = 2;
				break;
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 3;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 4;
				break;
			default:
				init_mp = 2;
				break;
			}
		} else if (pc.isKnight()) { // 나이트
			init_hp = 16;
			switch (pc.getAbility().getBaseWis()) {
			case 9:
			case 10:
			case 11:
				init_mp = 1;
				break;
			case 12:
			case 13:
				init_mp = 2;
				break;
			default:
				init_mp = 1;
				break;
			}
		} else if (pc.isElf()) { // 에르프
			init_hp = 15;
			switch (pc.getAbility().getBaseWis()) {
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 4;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 6;
				break;
			default:
				init_mp = 4;
				break;
			}
		} else if (pc.isWizard()) { // WIZ
			init_hp = 12;
			switch (pc.getAbility().getBaseWis()) {
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 6;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 8;
				break;
			default:
				init_mp = 6;
				break;
			}
		} else if (pc.isDarkelf()) { // DE
			init_hp = 12;
			switch (pc.getAbility().getBaseWis()) {
			case 10:
			case 11:
				init_mp = 3;
				break;
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 4;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 6;
				break;
			default:
				init_mp = 3;
				break;
			}
		}
		pc.addBaseMaxHp(init_hp);
		pc.setCurrentHp(init_hp);
		pc.addBaseMaxMp(init_mp);
		pc.setCurrentMp(init_mp);
		pc.resetBaseAc();
		pc.setTitle("");
		pc.setClanid(0);
		pc.setClanRank(0);
		pc.setFood(39);
		pc.setAccessLevel((short) 0);
		pc.setGm(false);
		pc.setMonitor(false);
		pc.setGmInvis(false);
		pc.setExp(0);
		pc.setHighLevel(0);
		pc.setStatus(0);
		pc.setAccessLevel((short) 0);
		pc.setClanname("");
		pc.setBonusStats(0);
		pc.setElixirStats(0);
		pc.resetBaseMr();
		pc.setElfAttr(0);
		pc.set_PKcount(0);
		pc.setExpRes(0);
		pc.setPartnerId(0);
		pc.setOnlineStatus(0);
		pc.setHomeTownId(0);
		pc.setContribution(0);
		pc.setBanned(false);
		pc.setKarma(0);
		pc.setReturnStat(0); 

		
		// 310
		Calendar local_c = Calendar.getInstance();
		SimpleDateFormat local_sdf = new SimpleDateFormat("yyyyMMdd");
		local_c.setTimeInMillis(System.currentTimeMillis());
		pc.setBirthDay(Integer.parseInt(local_sdf.format(local_c.getTime())));
		
		if (pc.isWizard()) { // WIZ
			pc.sendPackets(new S_AddSkill(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
			int object_id = pc.getId();
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(4); // EB
			String skill_name = l1skills.getName();
			int skill_id = l1skills.getSkillId();
			SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
		}
		

		Beginner.getInstance().GiveItem(pc);
		Beginner.getInstance().writeBookmark(pc);
		pc.setAccountName(client.getAccountName());
		CharacterTable.getInstance().storeNewCharacter(pc);
		S_NewCharPacket s_newcharpacket = new S_NewCharPacket(pc);
		client.sendPacket(s_newcharpacket);
		pc.save();
		pc.refresh();
	}

	private static boolean isAlphaNumeric(String s) {
        if (s == null) {
            return false;
        }
		boolean flag = true;
		char ac[] = s.toCharArray();
		int i = 0;
		do {
			if (i >= ac.length) {
				break;
			}
			if (!Character.isLetterOrDigit(ac[i])) {
				flag = false;
				break;
			}
			i++;
		} while (true);
		return flag;
	}

	private static boolean isInvalidName(String name) {
		int numOfNameBytes = 0;
		try {
			numOfNameBytes = name.getBytes("EUC-KR").length;
		} catch (UnsupportedEncodingException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			return false;
		}

		if (isAlphaNumeric(name)) {
			return false;
		}

		// XXX - 본청의 사양과 동등한가 미확인
		// 전각 캐릭터가 5 캐릭터를 넘는지, 전체로 12바이트를 넘으면(자) 무효인 이름으로 한다
		if (5 < (numOfNameBytes - name.length()) || 12 < numOfNameBytes) {
			return false;
		}

		if (BadNamesList.getInstance().isBadName(name)) {
			return false;
		}
		return true;
	}

	@Override
	public String getType() {
		return C_CREATE_CHAR;
	}
}
