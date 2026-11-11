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

import l1j.server.server.GameClient;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import manager.SJManager;

public class C_ChatWhisper extends ClientBasePacket {

	private static final String C_CHAT_WHISPER = "[C] C_ChatWhisper";
	public C_ChatWhisper(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		String targetName = readS();
		String text = readS();
		L1PcInstance whisperFrom = client.getActiveChar();
		if (whisperFrom == null) {
			return;
		}
		// 채팅 금지중의 경우
		if (whisperFrom.hasSkillEffect(1005)) {
			whisperFrom.sendPackets(new S_ServerMessage(242)); // 현재 채팅 금지중입니다.
			return;
		}

		L1PcInstance whisperTo = L1World.getInstance().getPlayer(targetName);
		// 영자상점 귓속말
		if (L1World.getInstance().getShopNpc(targetName) != null) {
			whisperFrom.sendPackets(new S_SystemMessage("-> ("+targetName+") " + text));
			return;
		// 월드에 없는 경우
		}else if (whisperTo == null) {
			whisperFrom.sendPackets(new S_ServerMessage(73, targetName)); // \f1%0은 게임을 하고 있지 않습니다.
			return;
		}
		// 자기 자신에 대한 wis의 경우
		if (whisperTo.equals(whisperFrom)) {
			return;
		}
		// 차단되고 있는 경우
		if (whisperTo.excludes(whisperFrom.getName())) {
			whisperFrom.sendPackets(new S_ServerMessage(117, whisperTo.getName())); // %0가 당신을 차단했습니다.
			return;
		}
		
		if (!whisperTo.isCanWhisper()) {
			whisperFrom.sendPackets(new S_ServerMessage(205, whisperTo.getName()));
			return;
		}
		whisperFrom.sendPackets(new S_ChatPacket(whisperTo, text, Opcodes.S_OPCODE_GLOBALCHAT, 9));
		whisperTo.sendPackets(new S_ChatPacket(whisperFrom, text, Opcodes.S_OPCODE_WHISPERCHAT, 16));
		SJManager.getInstance().WisperChatAppend(whisperFrom.getName(), whisperTo.getName(), text);
		 
	}

	@Override
	public String getType() {
		return C_CHAT_WHISPER;
	}
}
