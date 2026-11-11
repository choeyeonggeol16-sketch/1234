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
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.FaceToFace;

public class C_JoinClan extends ClientBasePacket {

	private static final String C_JOIN_CLAN = "[C] C_JoinClan";

	public C_JoinClan(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);

		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null || pc.isGhost() ) {
			return;
		}

		L1PcInstance target = FaceToFace.faceToFace(pc);
		if (target != null) {
			JoinClan(pc, target);
		}
	}

	private void JoinClan(L1PcInstance player, L1PcInstance target) {
		if (!target.isCrown() && (target.getClanRank() != L1Clan.CLAN_RANK_GUARDIAN)) {
			player.sendPackets(new S_SystemMessage(target.getName() + "는 왕자나 공주 수호기사가 아닙니다."));
			return;
		}

		int clan_id = target.getClanid();
		String clan_name = target.getClanname();
		if (clan_id == 0) { // 상대 크란이 없다
			player.sendPackets(new S_ServerMessage(90, target.getName())); // \f1%0은 혈맹을 창설하고 있지 않는 상태입니다.
			return;
		}

		L1Clan clan = L1World.getInstance().getClan(clan_name);
		if (clan == null) {
			return;
		}

		if (target.getClanRank() != L1Clan.CLAN_RANK_PRINCE && target.getClanRank() != L1Clan.CLAN_RANK_GUARDIAN) { 
			player.sendPackets(new S_SystemMessage(target.getName() + "는 왕자나 공주 수호기사가 아닙니다."));
			return;
		}
		
		

		if (player.getClanid() != 0) { // 이미 크란에 가입이 끝난 상태
			if (player.isCrown()) { // 자신이 군주
				String player_clan_name = player.getClanname();
				L1Clan player_clan = L1World.getInstance().getClan(player_clan_name);
				if (player_clan == null) {
					return;
				}

				if (player.getId() != player_clan.getLeaderId()) { // 자신이 혈맹주 이외
					player.sendPackets(new S_ServerMessage(89)); // \f1당신은 벌써 혈맹에 가입하고 있습니다.
					return;
				}

				if (player_clan.getCastleId() != 0 || // 자신이 성주·아지트 보유
						player_clan.getHouseId() != 0) {
					player.sendPackets(new S_ServerMessage(665)); // \f1성이나 아지트를 소유한 상태로 혈맹을 해산할 수 없습니다.
					return;
				}
				 
				
			} else {
				player.sendPackets(new S_ServerMessage(89)); // \f1당신은 벌써 혈맹에 가입하고 있습니다.
				return;
			}
		}

		target.setTempID(player.getId()); // 상대의 오브젝트 ID를 보존해 둔다
		target.sendPackets(new S_Message_YN(97, player.getName())); // %0가 혈맹에 가입했지만은 있습니다.승낙합니까? (Y/N)
	}
	
	


	@Override
	public String getType() {
		return C_JOIN_CLAN;
	}
}
