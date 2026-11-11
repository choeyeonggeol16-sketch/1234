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
package l1j.server.server.serverpackets;

import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 스킬 아이콘이나 차단 리스트의 표시 등 복수의 용도에 사용되는 패킷의 클래스
 */
public class S_PacketBox extends ServerBasePacket {
	private static final String S_PACKETBOX = "[S] S_PacketBox";

	private byte[] _byte = null;

	// *** S_107 sub code list ***

	// 1:Kent 2:Orc 3:WW 4:Giran 5:Heine 6:Dwarf 7:Aden 8:Diad 9:성명 9 ...
	/** C(id) H(?): %s의 공성전이 시작되었습니다. */
	public static final int MSG_WAR_BEGIN = 0;

	/** C(id) H(?): %s의 공성전이 종료했습니다. */
	public static final int MSG_WAR_END = 1;

	/** C(id) H(?): %s의 공성전이 진행중입니다. */
	public static final int MSG_WAR_GOING = 2;
	/** -: 성의 주도권을 잡았습니다. (음악이 바뀐다) */
	public static final int MSG_WAR_INITIATIVE = 3;

	/** -: 성을 점거했습니다. */
	public static final int MSG_WAR_OCCUPY = 4;

	/** ?: 결투가 끝났습니다. (음악이 바뀐다) */
	public static final int MSG_DUEL = 5;

	/** C(count): SMS의 송신에 실패했습니다. / 전부%d건송신되었습니다. */
	public static final int MSG_SMS_SENT = 6;

	/** -: 축복안, 2명은 부부로서 연결되었습니다. (음악이 바뀐다) */
	public static final int MSG_MARRIED = 9;

	/** C(weight): 중량(30 단계) */
	public static final int WEIGHT = 10;

	/** C(food): 만복도(30 단계) */
	public static final int FOOD = 11;

	/** C(0) C(level): 이 아이템은%d레벨 이하만 사용할 수 있습니다. (0~49이외는 표시되지 않는다) */
	public static final int MSG_LEVEL_OVER = 12;

	/** UB정보 HTML */
	public static final int HTML_UB = 14;

	/**
	 * C(id)<br>
	 * 1:몸에 담겨져 있던 정령의 힘이 공기안에 녹아 가는 것을 느꼈습니다.<br>
	 * 2:몸의 구석구석에 화의 정령력이 스며들어 옵니다.<br>
	 * 3:몸의 구석구석에 물의 정령력이 스며들어 옵니다.<br>
	 * 4:몸의 구석구석에 바람의 정령력이 스며들어 옵니다.<br>
	 * 5:몸의 구석구석에 땅의 정령력이 스며들어 옵니다.<br>
	 */
	public static final int MSG_ELF = 15;

	/** C(count) S(name)...: 차단 리스트 복수 추가 */
	public static final int ADD_EXCLUDE2 = 17;

	/** S(name): 차단 리스트 추가 */
	public static final int ADD_EXCLUDE = 18;

	/** S(name): 차단 해제 */
	public static final int REM_EXCLUDE = 19;

	/** 스킬 아이콘 */
	public static final int ICONS1 = 20;

	/** 스킬 아이콘 */
	public static final int ICONS2 = 21;

	/** 아우라계의 스킬 아이콘 */
	public static final int ICON_AURA = 22;

	/** S(name): 타운 리더에게%s가 선택되었습니다. */
	public static final int MSG_TOWN_LEADER = 23;
	
	/** 
	 * D(혈맹원수) (S(혈원이름) C(혈원계급)) 혈맹원 갱신이 된 상태에서의 /혈맹.
	 */
	public static final int PLEDGE_TWO = 24;//추가

	/** 
	 * D(혈맹원이름) C(랭크) 혈맹에 추가된 인원이 있을때 보내주는 패킷
	 */
	public static final int PLEDGE_REFRESH_PLUS = 25;//추가

	/** 
	 * D(혈맹원이름) C(랭크) 혈맹에 삭제된 인원이 있을때 보내주는 패킷
	 */
	public static final int PLEDGE_REFRESH_MINUS = 26;//추가
	/**
	 * C(id): 당신의 랭크가%s로 변경되었습니다.<br>
	 * id - 1:견습 2:일반 3:가디안
	 */
	public static final int MSG_RANK_CHANGED = 27;

	/** 
	 * D(혈맹원수) (S(혈원이름) C(혈원계급)) 혈맹원 갱신이 안된 상태에서의 /혈맹.
	 */
	public static final int PLEDGE_ONE = 29;//추가
 

	/** D(?) S(name) S(clanname): %s혈맹의%s가 라스타바드군을 치웠습니다. */
	public static final int MSG_WIN_LASTAVARD = 30;

	/** -: \f1기분이 좋아졌습니다. */
	public static final int MSG_FEEL_GOOD = 31;

	/** 불명.C_30 패킷이 난다 */
	public static final int SOMETHING1 = 33;

	/** H(time): 블루 일부의 아이콘이 표시된다. */
	public static final int ICON_BLUEPOTION = 34;

	/** H(time): 변신의 아이콘이 표시된다. */
	public static final int ICON_POLYMORPH = 35;

	/** H(time): 채팅 금지의 아이콘이 표시된다. */
	public static final int ICON_CHATBAN = 36;

	/** 불명.C_7 패킷이 난다.C_7은 애완동물의 메뉴를 열었을 때에도 난다. */
	public static final int SOMETHING2 = 37;

	/** 혈맹 정보의 HTML가 표시된다 */
	public static final int HTML_CLAN1 = 38;

	/** H(time): 이뮤의 아이콘이 표시된다 */
	public static final int ICON_I2H = 40;

	/** 캐릭터의 게임 옵션, 쇼트 컷 정보등을 보낸다 */
	public static final int CHARACTER_CONFIG = 41;

	/** 캐릭터 선택 화면으로 돌아간다 */
	public static final int LOGOUT = 42;

	/** 전투중에 재시 동요할 수 없습니다. */
	public static final int MSG_CANT_LOGOUT = 43;

	/**
	 * C(count) D(time) S(name) S(info):<br>
	 * [CALL] 버튼이 붙은 윈도우가 표시된다.이것은 BOT등의 부정자 체크에
	 * 사용되는 기능한 것같다.이름을 더블 클릭 하면(자) C_RequestWho가 날아, 클라이언트의
	 * 폴더에 bot_list.txt가 생성된다.이름을 선택해+키를 누르면(자) 새로운 윈도우가 열린다.
	 */
	public static final int CALL_SOMETHING = 45;

	/**
	 * C(id): 배틀 콜롯세움, 카오스 대전이―<br>
	 * id - 1:개시합니다 2:삭제되었던 3:종료합니다
	 */
	public static final int MSG_COLOSSEUM = 49;

	// 혈맹 정보의 HTML
	public static final int HTML_CLAN2 = 51;

	// 요리 윈도우를 연다
	public static final int COOK_WINDOW = 52;

	/** C(type) H(time): 요리 아이콘이 표시된다 */
	public static final int ICON_COOKING = 53;
	
	/** 아이콘 삭제 */
	public static final int DEL_ICON = 59;
	
	public static final int DRAGONPERL = 60; // 드래곤진주
	
	/** 미니게임 : 타임 */
	public static final int MINIGAME_TIME = 71;
	
	/** 미니게임 : 타임삭제 */
	public static final int MINIGAME_END = 72;
	
	/** 메세지 */
	public static final int GREEN_MESSAGE = 84;
	
	/** 아인하사드 버프 */
	public static final int EINHASAD = 82;
	
	public static final int EMERALD_EVA = 86;//드래곤에메랄드
	public static final int INIT_DG = 88;
	public static final int BAPHOMET_SYSTEM = 114;
	
	public static final int UPDATE_DG = 101;

    /** 보안 버프 **/
    public static final int ICON_SECURITY_SERVICES = 125;
    
	/** 아이콘 표시 **/
	public static final int UNLIMITED_ICON = 147;
	
	public static final int TIME_COUNT = 153;
	
	public static final int BUFFICON = 154;
	
	public static final int TOWN_TELEPORT = 176;
	
	public static final int UNLIMITED_ICON1 = 180;
	public static final int 유저빽스탭 = 193;
	
	public S_PacketBox(L1PcInstance pc, int subCode) {
		
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case PLEDGE_REFRESH_PLUS:
		case PLEDGE_REFRESH_MINUS:
			writeS(pc.getName());
			writeC(pc.getClanRank());
			writeH(0);
			break; 
		default:
			break;
		}
	}

	public S_PacketBox(int subCode) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
        case ICON_SECURITY_SERVICES: // 보안 버프
		case MSG_WAR_INITIATIVE:
		case MSG_WAR_OCCUPY:
		case MSG_MARRIED:
		case MSG_FEEL_GOOD:
		case MSG_CANT_LOGOUT:
		case LOGOUT:
			break;
		case CALL_SOMETHING:
			callSomething();
		case DEL_ICON:
			writeH(0);
			break;
		case COOK_WINDOW:
			writeC(0xdb); // ?
			writeC(0x31);
			writeC(0xdf);
			writeC(0x02);
			writeC(0x01);
			writeC(0x00);
			break;
		case MINIGAME_END:
			writeC(0);
			writeC(0);
			break;
		case ICON_AURA:
			writeC(0x98);
			writeC(0);
			writeC(0);
			writeC(0);
			writeC(0);
			writeC(0);
			break;
		default:
			break;
		}
	}
	
	public S_PacketBox(int subCode, int value, boolean show) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);
		switch (subCode) {
		case UNLIMITED_ICON: // 무제한 패킷 
			writeC(show ? 0x01 : 0x00); // On Off // true false
			writeC(value); // 
			break;
		case UNLIMITED_ICON1:
			writeC(show ? 0x01 : 0x00); // On Off // true false
			writeD(value);
			writeD(0);
			writeH(0);
			break;
		case BAPHOMET_SYSTEM:
			writeD(value); // 1~7 깃발
			writeD(show ? 0x01 : 0x00); // On Off
			break;
		default:
			break;
		}
	}

	public S_PacketBox(int subCode, int value) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case ICON_BLUEPOTION:
		case ICON_CHATBAN:
		case ICON_I2H:
		case ICON_POLYMORPH:
		case MINIGAME_TIME:
		case INIT_DG:
			writeH(value); // time
			break;
		case MSG_WAR_BEGIN:
		case MSG_WAR_END:
		case MSG_WAR_GOING:
			writeC(value); // castle id
			writeH(0); // ?
			break;
		case MSG_SMS_SENT:
		case WEIGHT:
		case FOOD:
			if (value >= 240) {
				value = 240;
			}
			writeC(value);
			writeH(0);
			break;
		case UPDATE_DG:
			writeH(value);
			writeH(0);
			break;
		case MSG_ELF:
		case MSG_RANK_CHANGED:
		case MSG_COLOSSEUM:
			writeC(value); // msg id
			break;
		case MSG_DUEL: // 인수의 의미가 불명
			writeC(value);
			break;
		case MSG_LEVEL_OVER:
			writeC(0); // ?
			writeC(value); // 0-49이외는 표시되지 않는다
			break; 
		case TIME_COUNT:
			writeH(value);
			writeH(0);
			break;
		case EINHASAD:
			value /= 10000;
			writeD(value);// % 수치 1~200
			break;
		default:
			break;
		}
	}
	
	public S_PacketBox(int subCode, int value, String svalue) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case MSG_RANK_CHANGED:
			writeD(value);
			writeS(svalue);
			break;
		default:
			break;
		}
	}
	

	public S_PacketBox(int subCode, int type, int time) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case DRAGONPERL:// 드래곤진주
			writeC(time);
			writeC(type);
			break; 
		case BUFFICON:
			writeH(time);
			writeH(type);
			writeH(0);
			break;

		case EMERALD_EVA:// 드래곤에메랄드
			writeC(0x70);
			writeC(1);
			writeC(type);// 2: 2배로적용 아니라면:사용불가
			writeH(time);// 초로 적용
			break;
		case ICON_COOKING:
			if (type != 7) {
				writeC(0x0c);
				writeC(0x0c);
				writeC(0x0c);
				writeC(0x12);
				writeC(0x0c);
				writeC(0x09);
				writeC(0x00);
				writeC(0x00);
				writeC(type);
				writeC(0x24);
				writeH(time);
				writeH(0x00);
			} else {
				writeC(0x0c);
				writeC(0x0c);
				writeC(0x0c);
				writeC(0x12);
				writeC(0x0c);
				writeC(0x09);
				writeC(0xc8);
				writeC(0x00);
				writeC(type);
				writeC(0x26);
				writeH(time);
				writeC(0x3e);
				writeC(0x87);
			}
			break;
		case UNLIMITED_ICON1:
			writeC(type);// on/off
			writeD(time);// 166 exp30% 228 시원한얼음조각286 exp40% 343 기르타스지역사망패널티
							// 409아머브레이크 497붉은기사의증표 이벤트공성존 //477~479
			writeD(0x00000D67);
			writeH(0x00);
			break;
		default:
			break;
		}
	}
	
	public S_PacketBox(int subCode, L1PcInstance pc, int value1, int value2) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);
		switch (subCode) { 
		case ICON_COOKING:
			writeC(pc.getAbility().getStr());
			writeC(pc.getAbility().getInt());
			writeC(pc.getAbility().getWis());
			writeC(pc.getAbility().getDex());
			writeC(pc.getAbility().getCon());
			writeC(pc.getAbility().getCha());
			writeH((pc.getFood() - 25) * 10);
			writeC(value1);
			writeC(0x24);
			writeH(value2);
			writeC(pc.getInventory().getWeight100());
			break;
		default:
			break;
		}
	}

	public S_PacketBox(int subCode, String name) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case ADD_EXCLUDE:
		case REM_EXCLUDE:
		case MSG_TOWN_LEADER:
			writeS(name);
			break;
		case GREEN_MESSAGE:
			writeC(2);
			writeS(name);
			break;
		default:
			break;
		}
	}
	 
	public S_PacketBox(int subCode, int id, String name, String clanName) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);

		switch (subCode) {
		case MSG_WIN_LASTAVARD:
			writeD(id); // 크란 ID인가 무엇인가?
			writeS(name);
			writeS(clanName);
			break;
		default:
			break;
		}
	}

//	public S_PacketBox(int subCode, Object[] names) {
//		writeC(Opcodes.S_OPCODE_PACKETBOX);
//		writeC(subCode);
//
//		switch (subCode) {
//		case ADD_EXCLUDE2:
//			writeC(names.length);
//			for (Object name : names) {
//				writeS(name.toString());
//			}
//			break;
//		default:
//			break;
//		}
//	}
	
	public S_PacketBox(int subCode, L1PcInstance pc) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);
		switch (subCode) {
		case TOWN_TELEPORT:
			writeC(0x01);
			writeH(pc.getX());
			writeH(pc.getY());
			break;
		case 유저빽스탭:	
			writeH(0x01);
			writeH(pc.getX());
			writeH(pc.getY());
			break;
		}
	}
	public S_PacketBox(int subCode, int time, int gfxid, int type) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(subCode);
		switch (subCode) {
		case BUFFICON:
			writeH(time); // 시간
			writeD(gfxid); // 아이콘
			writeC(type); // 타입
			writeC(0x00);
			break;
		}
	}
	private void callSomething() {
		Iterator<L1PcInstance> itr = L1World.getInstance().getAllPlayers().iterator();

		writeC(L1World.getInstance().getAllPlayers().size());

		while (itr.hasNext()) {
			L1PcInstance pc = itr.next();
			Account acc = Account.load(pc.getAccountName());

			// 시간 정보 우선 로그인 시간을 넣어 본다
			if (acc == null) {
				writeD(0);
			} else {
				Calendar cal = Calendar
						.getInstance(TimeZone.getTimeZone(Config.TIME_ZONE));
				long lastactive = acc.getLastActive().getTime();
				cal.setTimeInMillis(lastactive);
				cal.set(Calendar.YEAR, 1970);
				int time = (int) (cal.getTimeInMillis() / 1000);
				writeD(time); // JST 1970 1/1 09:00 이 기준
			}

			// 캐릭터 정보
			writeS(pc.getName()); // 반각 12자까지
			writeS(pc.getClanname()); // []내에 표시되는 캐릭터 라인.반각 12자까지
		}
	}

	@Override
	public byte[] getContent() {
		if (_byte == null) {
			_byte = getBytes();
		}

		return _byte;
	}

	public String getType() {
		return S_PACKETBOX;
	}
}
