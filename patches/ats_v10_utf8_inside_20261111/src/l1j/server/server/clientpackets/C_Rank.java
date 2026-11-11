package l1j.server.server.clientpackets;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.GameClient;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.FaceToFace;

public class C_Rank extends ClientBasePacket {

	private static final String C_RANK = "[C] C_Rank";
	private static Logger _log = Logger.getLogger(C_Rank.class.getName());

	public C_Rank(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);

		int type = readC();
		int rank = readC();

		L1PcInstance pc = clientthread.getActiveChar();

		if (pc == null) {
			return;
		}
		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());

		switch (type) {
		case 0: // 혈맹에 속한 인원수의 변동이 있었을 경우
			pc.sendPackets(new S_PacketBox(pc, S_PacketBox.PLEDGE_TWO)); // 임시
			break;
		case 1:// 계급
			String name = readS();
			L1PcInstance targetPc = L1World.getInstance().getPlayer(name);
			if (rank < 1 && 3 < rank) { // 랭크를 변경하는 사람의 이름과 랭크를 입력해 주세요. [랭크=가디안, 일반, 견습]
				pc.sendPackets(new S_ServerMessage(781));
				return;
			}
			if (pc.isCrown()) { // 군주
				if (pc.getId() != clan.getLeaderId()) { // 혈맹주
					pc.sendPackets(new S_ServerMessage(785)); // 당신은 이제 군주가 아닙니다.
					return;
				}
			} else {
				pc.sendPackets(new S_ServerMessage(518)); // 이 명령은 혈맹의 군주만을 이용할 수 있습니다.
				return;
			}
			if (targetPc != null) { // 온라인중
				if (pc.getClanid() == targetPc.getClanid()) { // 같은 크란
					try {
						targetPc.setClanRank(rank);
						targetPc.save(); // DB에 캐릭터 정보를 기입한다
						String rankString = "$772";
						if (rank == L1Clan.CLAN_RANK_PROBATION) {
							rankString = "$774";
						} else if (rank == L1Clan.CLAN_RANK_PUBLIC) {
							rankString = "$773";
						} else if (rank == L1Clan.CLAN_RANK_GUARDIAN) {
							rankString = "$772";
						}
						targetPc.sendPackets(new S_ServerMessage(784, rankString)); // 당신의 랭크가%s로 변경되었습니다.
					} catch (Exception e) {
						_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				} else {
					pc.sendPackets(new S_ServerMessage(414)); // 같은 혈맹원이 아닙니다.
					return;
				}
			} else { // 오프 라인중
				L1PcInstance restorePc = CharacterTable.getInstance().restoreCharacter(name);
				if (restorePc != null && restorePc.getClanid() == pc.getClanid()) { // 같은 크란
					try {
						restorePc.setClanRank(rank);
						restorePc.save(); // DB에 캐릭터 정보를 기입한다
					} catch (Exception e) {
						_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				} else {
					pc.sendPackets(new S_ServerMessage(109, name)); // %0라는 이름의 사람은 없습니다.
					return;
				}
			}
			break;
		case 2:// 목록
			if (clan.getAlliance() != 0) {
				// pc.sendPackets(new S_PacketBox(S_PacketBox.PLEDGE_UNION,
				// clan.getAlliance()));
			} else {
				pc.sendPackets(new S_ServerMessage(1233)); // 동맹이 없습니다.
				return;
			}
			break;
		case 3:// 가입
			L1PcInstance allianceLeader = FaceToFace.faceToFace(pc);
			if (pc.getLevel() < 25 || !pc.isCrown()) {
				pc.sendPackets(new S_ServerMessage(1206));// 25레벨이상 혈맹 군주만 동맹신청을 할 수 있습니다. 또한 연합 군주는 동맹을 맺을 수 없습니다.
				return;
			}
			if (pc.getClan().getAlliance() != 0) {
				pc.sendPackets(new S_ServerMessage(1202));// 이미 동맹에 가입된 상태입니다.
				return;
			}

			if (allianceLeader != null) {
				if (allianceLeader.getLevel() > 24 && allianceLeader.isCrown()) {
					allianceLeader.setTempID(pc.getId());
					allianceLeader.sendPackets(new S_Message_YN(223, pc.getName()));
				} else {
					pc.sendPackets(new S_ServerMessage(1201));// 동맹에 가입할 수 없습니다.
				}
			}
			break;
		case 4:// 탈퇴
			if (clan.getAlliance() != 0) {
				pc.sendPackets(new S_Message_YN(1210, "")); // 정말로 동맹을 탈퇴하시겠습니까? (Y/N)
			} else {
				pc.sendPackets(new S_ServerMessage(1233)); // 동맹이 없습니다.
			}
			break;
		}
	}


	@Override
	public String getType() {
		return C_RANK;
	}
}
