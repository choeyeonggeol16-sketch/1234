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

import l1j.server.configure.Config;
import l1j.server.server.BadNamesList;
import l1j.server.server.GMCommands;
import l1j.server.server.GameClient;
import l1j.server.server.UserCommands;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_NpcChatPacket;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.utils.MessageUtil;
import manager.SJManager;

public class C_Chat extends ClientBasePacket {
	
	private static final int 통상_채팅 = 0;
	private static final int 절규_채팅 = 2;
	private static final int 전체_채팅 = 3;
	private static final int 혈맹_채팅 = 4;
	private static final int 파티_채팅 = 11;
	private static final int 장사_채팅 = 12;
	private static final int 연합_채팅 = 13;
	private static final int 채팅_파티 = 14;
	
	private static final String C_CHAT = "[C] C_Chat";

	public C_Chat(byte abyte0[], GameClient clientthread) {
		super(abyte0);

		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null) {
			return;
		} 
		int chatType = readC();
		String chatText = readS();
		if (pc.hasSkillEffect(SkillId.사일런스) || pc.hasSkillEffect(SkillId.에이리어_오브_사일런스) || pc.hasSkillEffect(SkillId.상태_침묵)) {
			return;
		}
		if (pc.hasSkillEffect(SkillId.상태_채팅_금지)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_42), true);
			return;
		}
		 
		switch (chatType) {
		case 통상_채팅:
			if (pc.getAccessLevel() == Config.GMCODE && chatText.startsWith(".") || pc.isMonitor() && chatText.startsWith(".")) {
				String cmd = chatText.substring(1);
				GMCommands.getInstance().handleCommands(pc, cmd);
				return;
			} else if (chatText.startsWith(".")) {
				String cmd = chatText.substring(1);
				UserCommands.getInstance().handleCommands(pc, cmd);
				return;
			}		
			if (BadNamesList.getInstance().isBadText(chatText)) {
				int time = 1; // 채금시간
				pc.setSkillEffect(SkillId.상태_채팅_금지, time * 60 * 1000);
				pc.sendPackets(new S_SkillIconGFX(36, time * 60));
				pc.sendPackets(new S_ServerMessage(286, String.valueOf(time)));
				SJManager.getInstance().ChatBug(pc.getName(), " 욕설 > " + chatText);
				return;
			}
			S_ChatPacket chat = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_NORMALCHAT, 0);
			if (!pc.excludes(pc.getName())) {
				pc.sendPackets(chat);
			}
			for (L1PcInstance listner : L1World.getInstance().getRecognizePlayer(pc)) {
				if (!listner.excludes(pc.getName())) {
					listner.sendPackets(chat);
				}
			}
			for (L1Object obj : pc.getKnownObjects()) {
				if (obj instanceof MonsterInstance) {
					MonsterInstance mob = (MonsterInstance) obj;
					if (mob.getNpcTemplate().is_doppel() && mob.getName().equals(pc.getName())) {
						mob.broadcastPacket(new S_NpcChatPacket(mob, chatText, 0));
					}
				}
			} 
			SJManager.getInstance().NomalchatAppend(pc.getName(), chatText);
			break;
		case 절규_채팅:	
			if (pc.isGhost()) {
				return;
			}
			S_ChatPacket chat2 = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_NORMALCHAT, 2);;
			if (!pc.excludes(pc.getName())) {
				pc.sendPackets(chat2);
			}
			for (L1PcInstance listner : L1World.getInstance().getVisiblePlayer(pc, 50)) {
				if (!listner.excludes(pc.getName())) {
					listner.sendPackets(chat2);
				}
			}
			break;
		case 전체_채팅:
			if (BadNamesList.getInstance().isBadText(chatText)) {
				int time = 1; // 채금시간
				pc.setSkillEffect(SkillId.상태_채팅_금지, time * 60 * 1000);
				pc.sendPackets(new S_SkillIconGFX(36, time * 60));
				pc.sendPackets(new S_ServerMessage(286, String.valueOf(time)));
				SJManager.getInstance().ChatBug(pc.getName(), " 욕설 > " + chatText);
				return;
			}
			chatWorld(pc, chatText, chatType);
			break;
		case 혈맹_채팅:
			if (pc.getClanid() != 0) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
				if (clan != null) {
					int rank = pc.getClanRank();
					if (clan != null && (rank == L1Clan.CLAN_RANK_PUBLIC || rank == L1Clan.CLAN_RANK_GUARDIAN
							|| rank == L1Clan.CLAN_RANK_PRINCE)) {
						S_ChatPacket s_chatpacket = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, 4);
						for (L1PcInstance listner : clan.getOnlineClanMember()) {
							if (!listner.excludes(pc.getName())) {
								listner.sendPackets(s_chatpacket);
							}
						}
					}
				}
				SJManager.getInstance().ClanChatAppend(pc.getClanname(), pc.getName(), chatText);
			} 
			break;
		case 파티_채팅:
			String list ="[";
			if (pc.isInParty()) {
				S_ChatPacket chat3 = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, 11);
				L1PcInstance[] partyMembers = pc.getParty().getMembers();
				for (L1PcInstance listner : partyMembers) {
					list += listner.getName()+",";
					if (!listner.excludes(pc.getName())) {
						listner.sendPackets(chat3);
					}
				} 
				list += "]";
				SJManager.getInstance().PartyChatAppend(list, pc.getName(), chatText);
			}
			break;
		case 장사_채팅:
			if (BadNamesList.getInstance().isBadText(chatText)) {
				int time = 1; // 채금시간
				pc.setSkillEffect(SkillId.상태_채팅_금지, time * 60 * 1000);
				pc.sendPackets(new S_SkillIconGFX(36, time * 60));
				pc.sendPackets(new S_ServerMessage(286, String.valueOf(time)));
				SJManager.getInstance().ChatBug(pc.getName(), " 욕설 > " + chatText);
				return;
			}
			chatWorld(pc, chatText, chatType);
			break;
		case 연합_채팅:
			if (pc.getClanid() != 0) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
				if (clan != null) {
					S_ChatPacket chat4 = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, 13);
					L1PcInstance[] clanMembers = clan.getOnlineClanMember();
					for (L1PcInstance listner : clanMembers) {
						if (!listner.excludes(pc.getName())) {
							listner.sendPackets(chat4);
						}
					}
				} 
				SJManager.getInstance().ClanChatAppend(pc.getClanname(), pc.getName(), chatText);
			}
			break;
		case 채팅_파티:
			if (pc.isInChatParty() || pc.isGhost()) { // 채팅 파티 중
				S_ChatPacket chat5 = new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_NORMALCHAT, 14);
				L1PcInstance[] partyMembers = pc.getChatParty().getMembers();
				for (L1PcInstance listner : partyMembers) {
					if (!listner.excludes(pc.getName())) {
						listner.sendPackets(chat5);
					}
				}
				SJManager.getInstance().PartyChatAppend(pc.getName(), chatText);
			} 
			break;
		}
	}
	
	
	private void chatWorld(L1PcInstance pc, String chatText, int chatType) {
		if (pc.isGm() || pc.getAccessLevel() == 1) {
			if (chatType == 전체_채팅) {
				L1World.getInstance().broadcastPacketToAll(new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, chatType));
				L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, chatText));
				SJManager.getInstance().AllChatAppend(pc.getName(), chatText);
			} else if (chatType == 장사_채팅) { 
				L1World.getInstance().broadcastPacketToAll(new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, chatType));
			}

		} else if (pc.getLevel() >= Config.GLOBAL_CHAT_LEVEL) {
			if (L1World.getInstance().isWorldChatElabled()) {
				if (pc.getFood() >= 12) { // 5%겟지?
					pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));
					if (chatType == 전체_채팅) {
						pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));	
						SJManager.getInstance().AllChatAppend(pc.getName(), chatText);
					} else if (chatType == 장사_채팅) {
						pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood())); 
					}
					pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));

					for (L1PcInstance listner : L1World.getInstance().getAllPlayers()) {
						if (!listner.excludes(pc.getName())) {
							if (listner.isShowTradeChat() && chatType == 장사_채팅) {
								listner.sendPackets(new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, chatType));
							} else if (listner.isShowWorldChat() && chatType == 전체_채팅) {
								listner.sendPackets(new S_ChatPacket(pc, chatText, Opcodes.S_OPCODE_GLOBALCHAT, chatType));	
							}
						}
					}
				} else {
					pc.sendPackets(new S_ServerMessage(462));
				}
			} else {
				pc.sendPackets(new S_ServerMessage(510)); 
			}
		} else {
			pc.sendPackets(new S_ServerMessage(195, String.valueOf(Config.GLOBAL_CHAT_LEVEL)));
		}
	}

	@Override
	public String getType() {
		return C_CHAT;
	}
}
