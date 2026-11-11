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
import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_Party;


public class C_BanChatParty extends ClientBasePacket {

	private static final String C_BAN_CHAT_PARTY = "[C] C_BanChatParty";

	public C_BanChatParty(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);
		int c = readC();
		L1PcInstance player = client.getActiveChar();
		if (player == null) {
			return;
		}
		if (c == 0) { // 파티에서 추방 당하는 경우
			if (!player.getChatParty().isLeader(player)) { // 파티 리더가 아닌 경우
				player.sendPackets(new S_ServerMessage(427)); // 파티의 리더만을 추방할 수 있습니다.
				return;
			}
			String s = readS();
			for (L1PcInstance member : player.getChatParty().getMembers()) {
				if (member.getName().toLowerCase().equals(s.toLowerCase())) {
					player.getChatParty().kickMember(member);
					return;
				}
			}
			// 발견되지 않았다
			player.sendPackets(new S_ServerMessage(426, player.getName())); // %0는 파티 멤버가 아닙니다.
			return;
		} else if (c == 1) { // /파티탈퇴의 경우
			if (player.isInChatParty()) // 채팅 파티중
			{
				player.getChatParty().leaveMember(player);
				return;
			} else {
				player.sendPackets(new S_ServerMessage(426, player.getName())); // %0는 파티 멤버가 아닙니다.
				return;
			}
		} else if (c == 2) { // 채팅 파티 목록
			if (player.isGhost()) {
				return;
			}
			L1ChatParty party = player.getChatParty();
			if (player.isInChatParty()) {
				player.sendPackets(
						new S_Party("party", player.getId(), party.getLeader().getName(), party.getMembersNameList()));
			} else {
				player.sendPackets(new S_Party("party", player.getId()));
			}
		}
	}

	@Override
	public String getType() {
		return C_BAN_CHAT_PARTY;
	}

}