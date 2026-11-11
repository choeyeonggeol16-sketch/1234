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
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_ServerMessage;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket

public class C_CreateParty extends ClientBasePacket {

	private static final String C_CREATE_PARTY = "[C] C_CreateParty";

	public C_CreateParty(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);

		L1PcInstance pc = client.getActiveChar();

		if (pc == null) {
			return;
		}

		int type = readC();
		if (type == 0 || type == 1) {
			int targetId = readD();
			L1Object temp = L1World.getInstance().findObject(targetId);
			if (temp instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) temp;
				if (pc.getId() == targetPc.getId()) {
					return;
				}
				if (targetPc.isInParty()) {
					// 벌써 다른 파티에 소속해 있기 (위해)때문에 초대할 수 없습니다
					pc.sendPackets(new S_ServerMessage(415));
					return;
				}

				if (pc.isInParty()) {
					if (pc.getParty().isLeader(pc)) {
						targetPc.setPartyID(pc.getId());
						// \f2%0\f>%s로부터 \fU파티 \f> 에 초대되었습니다. 응합니까? (Y/N)
						if (!pc.getParty().isShareParty()) {
							targetPc.sendPackets(new S_Message_YN(953, pc.getName()));
						} else {
							targetPc.sendPackets(new S_Message_YN(954, pc.getName()));
						}
					} else {
						// 파티의 리더만을 초대할 수 있습니다.
						pc.sendPackets(new S_ServerMessage(416));
					}
				} else {
					pc.setPartyType(1);
					targetPc.setPartyID(pc.getId());
					// \f2%0\f>%s로부터 \fU파티 \f> 에 초대되었습니다. 응합니까? (Y/N)
					if (type == 0) {
						targetPc.sendPackets(new S_Message_YN(953, pc.getName()));
					} else {
						targetPc.sendPackets(new S_Message_YN(954, pc.getName()));
					}
				}
			}
		} else if (type == 2) { // 채팅 파티
			String name = readS();
			L1PcInstance targetPc = L1World.getInstance().getPlayer(name);
			if (targetPc == null) {
				// %0라는 이름의 사람은 없습니다.
				pc.sendPackets(new S_ServerMessage(109));
				return;
			}
			if (pc.getId() == targetPc.getId()) {
				return;
			}
			if (targetPc.isInChatParty()) {
				// 벌써 다른 파티에 소속해 있기 (위해)때문에 초대할 수 없습니다
				pc.sendPackets(new S_ServerMessage(415));
				return;
			}

			if (pc.isInChatParty()) {
				if (pc.getChatParty().isLeader(pc)) {
					targetPc.setPartyID(pc.getId());
					// \f2%0\f>%s로부터\fU채팅 파티 \f>에 초대되었습니다. 응합니까? (Y/N)
					targetPc.sendPackets(new S_Message_YN(951, pc.getName()));
				} else {
					// 파티의 리더만을 초대할 수 있습니다.
					pc.sendPackets(new S_ServerMessage(416));
				}
			} else {
				targetPc.setPartyID(pc.getId());
				// \f2%0\f>%s로부터\fU채팅 파티 \f>에 초대되었습니다. 응합니까? (Y/N)
				targetPc.sendPackets(new S_Message_YN(951, pc.getName()));
			}
		} else if (type == 3) { // 파장 위임
			int targetId = readD();
			L1Object temp = L1World.getInstance().findObject(targetId);
			if (temp instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) temp;
				if (pc.getId() == targetPc.getId()) {
					return;
				}

				if (!pc.isInParty()) {
					return;
				}

				if (targetPc.getParty() != pc.getParty()) {
					return;
				}

				if (!pc.getParty().isLeader(pc)) {
					return;
				}

				pc.getParty().promoteLeader(targetPc);
			}

		}

	}

	@Override
	public String getType() {
		return C_CREATE_PARTY;
	}

}
