/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
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

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_ChatPacket extends ServerBasePacket {

	private static final String _S__1F_NORMALCHATPACK = "[S] S_ChatPacket";
	
	public S_ChatPacket(L1PcInstance pc, String chat, int test) {
		writeC(Opcodes.S_OPCODE_NORMALCHAT);
		writeC(15);
		writeD(pc.getId());
		writeS(chat);
	}

	public S_ChatPacket(String chat, int test) {
		writeC(Opcodes.S_OPCODE_NORMALCHAT);
		writeC(15);
		writeS(chat);
	}

	public S_ChatPacket(L1PcInstance pc, String chat) {
		writeC(Opcodes.S_OPCODE_NORMALCHAT);
		writeC(11);// 10
		writeS(chat);
	}

	public S_ChatPacket(L1PcInstance pc, String chat, int opcode, int type) {

		if (type == 0) // 통상 채팅
		{
			writeC(opcode);
			writeC(type);
			writeD(pc.getId()); // ## 투명시 채팅 메시지 보이기 본인만
			/*
			 * if (pc.isInvisble()) { //## 원본 writeD(0); } else { writeD(pc.getId()); }
			 */
			writeS(pc.getName() + ": " + chat); 
		} else if (type == 2) // 절규
		{
			writeC(opcode);
			writeC(type);
			if (pc.isInvisble()) {
				writeD(0);
			} else {
				writeD(pc.getId());
			}
			writeS("<" + pc.getName() + "> " + chat);
			writeH(pc.getX());
			writeH(pc.getY());
		} else if (type == 3) // 전체 채팅
		{
			writeC(opcode);
			writeC(type);
			if (pc.isGm() == true) {
				writeS("[******] " + chat);
			} else {
				writeS("[" + pc.getName() + "] " + chat);
			}
		} else if (type == 4) // 혈맹 채팅
		{
			writeC(opcode);
			writeC(type);
			writeS("{" + pc.getName() + "} " + chat); 
		} else if (type == 9) // 위스파
		{
			writeC(opcode);
			writeC(type);
			writeS("-> (" + pc.getName() + ") " + chat);
		} else if (type == 11) // 파티 채팅
		{
			writeC(opcode);
			writeC(type);
			writeS("(" + pc.getName() + ") " + chat); 
		} else if (type == 12) // 연합 채팅
		{
			writeC(opcode);
			writeC(type);
			writeS("[" + pc.getName() + "] " + chat);
		} else if (type == 13) // 연합 채팅
		{
			writeC(opcode);
			writeC(type);
			writeS("{{" + pc.getName() + "}} " + chat);
		} else if (type == 14) // 채팅 파티
		{
			writeC(opcode);
			writeC(type);
//			writeS("(" + pc.getName() + ") " + chat); //## A140 원본 
			writeD(pc.getId()); // ## A140 채팅 파티 추가
			writeS("\\fU(" + pc.getName() + ") " + chat); // #
		} else if (type == 16) // 위스파
		{
			writeC(opcode);
			writeS(pc.getName());
			writeS(chat);
		}
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	@Override
	public String getType() {
		return _S__1F_NORMALCHATPACK;
	}

}