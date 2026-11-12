package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanMatching;
import l1j.server.server.model.L1ClanMatching.ClanMatchingList;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import java.nio.charset.Charset;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;


public final class SC_ClanMatching {

	private static final Logger _log = Logger.getLogger(SC_ClanMatching.class.getName());

	/**
	 * type
	 * 0: 등록, 수정
	 * 1: 등록취소 (군주)
	 * 2: 추천혈맹, 새로고침
	 * 3: 신청목록, 새로고침
	 * 4: 요청목록, 새로고침
	 * 5: 신청하기
	 * 6: 신청취소
	 */
	public static final int TYPE_RECOMMEND    = 2;
	public static final int TYPE_APPLIED_LIST = 3;
	public static final int TYPE_REQUEST_LIST = 4;
	public static final int TYPE_APPLY        = 5;
	public static final int TYPE_CANCEL       = 6;

	// DTO: 추천/신청 혈맹 노드
	private static class ClanNode {
		final int clanId;
		final String clanName;
		final String leaderName;
		final int onlineMaxUser;
		final int type2;
		final boolean hasHouse;
		final boolean isWar;
		final String text;

		ClanNode(int clanId, String clanName, String leaderName, int onlineMaxUser, int type2, boolean hasHouse, boolean isWar, String text) {
			this.clanId = clanId;
			this.clanName = clanName != null ? clanName : "";
			this.leaderName = leaderName != null ? leaderName : "";
			this.onlineMaxUser = onlineMaxUser;
			this.type2 = type2;
			this.hasHouse = hasHouse;
			this.isWar = isWar;
			this.text = text != null ? text : "";
		}
	}

	// DTO: 가입 신청 유저 노드
	private static class ApplicantNode {
		final int userId;
		final int onlineStatus;
		final String username;
		final int classType;
		final int lawful;
		final int level;

		ApplicantNode(int userId, int onlineStatus, String username, int classType, int lawful, int level) {
			this.userId = userId;
			this.onlineStatus = onlineStatus;
			this.username = username != null ? username : "";
			this.classType = classType;
			this.lawful = lawful;
			this.level = level;
		}
	}

	
	
	
	
	
	

	// --- 정적 팩토리 메서드 ---

	/**
	 * 추천 혈맹 목록 생성 (type == 2)
	 */
	public static Data createRecommended(L1PcInstance pc) {
		if (pc == null) {
			return new Data(TYPE_RECOMMEND, 0, 0, false, null, null);
		}

		L1ClanMatching cml = L1ClanMatching.getInstance();
		ArrayList<ClanMatchingList> matchingList = cml.getMatchingList();
		List<ClanNode> nodes = new ArrayList<>();

		if (matchingList != null) {
			synchronized (matchingList) {
				for (int i = 0; i < matchingList.size(); i++) {
					ClanMatchingList cmlItem = matchingList.get(i);
					if (cmlItem == null || cmlItem._clanname == null) {
						continue;
					}

					// 이미 신청한 혈맹은 추천에서 제외
					if (pc.getCMAList() != null && pc.getCMAList().contains(cmlItem._clanname)) {
						continue;
					}

					L1Clan clan = L1World.getInstance().findClan(cmlItem._clanname);
					if (clan == null) {
						continue;
					}

					nodes.add(new ClanNode(
						clan.getClanId(),
						clan.getClanName(),
						clan.getLeaderName(),
						clan.getOnlineMaxUser(),
						cmlItem._type,
						clan.getHouseId() != 0,
						clan.getCurrentWar() != null,
						cmlItem._text
					));
				}
			}
		}

		return new Data(TYPE_RECOMMEND, 0, 0, false, nodes, null);
	}

	/**
	 * 본인 가입 신청 목록 생성 (type == 3)
	 */
	public static Data createAppliedList(L1PcInstance pc) {
		if (pc == null || pc.getCMAList() == null) {
			return new Data(TYPE_APPLIED_LIST, 0, 0, false, null, null);
		}

		L1ClanMatching cml = L1ClanMatching.getInstance();
		List<String> cmaListSnapshot = new ArrayList<>();
		synchronized (pc.getCMAList()) {
			cmaListSnapshot.addAll(pc.getCMAList());
		}

		List<ClanNode> nodes = new ArrayList<>();
		for (String clanName : cmaListSnapshot) {
			if (clanName == null) {
				continue;
			}

			ClanMatchingList cmlItem = cml.getClanMatchingList(clanName);
			L1Clan clan = L1World.getInstance().findClan(clanName);
			if (clan == null) {
				continue;
			}

			int type2 = (cmlItem != null) ? cmlItem._type : 0;
			String text = (cmlItem != null) ? cmlItem._text : "";

			nodes.add(new ClanNode(
				clan.getClanId(),
				clan.getClanName(),
				clan.getLeaderName(),
				clan.getOnlineMaxUser(),
				type2,
				clan.getHouseId() != 0,
				clan.getCurrentWar() != null,
				text
			));
		}

		return new Data(TYPE_APPLIED_LIST, 0, 0, false, nodes, null);
	}

	/**
	 * 혈맹 가입 요청 목록 생성 - 군주용 (type == 4)
	 * (DB 캐릭터 로드를 선행 스냅샷 단계로 격리)
	 */
	public static Data createRequestList(L1PcInstance pc) {
		if (pc == null || pc.getClanname() == null) {
			return new Data(TYPE_REQUEST_LIST, 0, 0, true, null, null);
		}

		L1ClanMatching cml = L1ClanMatching.getInstance();
		if (!cml.isClanMatchingList(pc.getClanname())) {
			return new Data(TYPE_REQUEST_LIST, 0, 0, true, null, null);
		}

		List<String> cmaListSnapshot = new ArrayList<>();
		if (pc.getCMAList() != null) {
			synchronized (pc.getCMAList()) {
				cmaListSnapshot.addAll(pc.getCMAList());
			}
		}

		List<ApplicantNode> nodes = new ArrayList<>();
		for (String username : cmaListSnapshot) {
			if (username == null) {
				continue;
			}

			L1PcInstance user = L1World.getInstance().getPlayer(username);
			if (user == null) {
				try {
					user = CharacterTable.getInstance().restoreCharacter(username);
				} catch (Exception e) {
					_log.log(Level.SEVERE, "SC_ClanMatching 신청자 데이터 DB 복원 실패 - name: " + username, e);
				}
			}

			if (user == null) {
				continue;
			}

			nodes.add(new ApplicantNode(
				user.getId(),
				user.getOnlineStatus(),
				username,
				user.getType(),
				user.getLawful(),
				user.getLevel()
			));
		}

		return new Data(TYPE_REQUEST_LIST, 0, 0, false, null, nodes);
	}

	/**
	 * 단범위 응답/신청/취소 패킷 생성 (type == 5, 6 등)
	 */
	public static Data createSimple(int type, int objId, int hType) {
		return new Data(type, objId, hType, false, null, null);
	}

	/**
	 * 종합 분기 정적 팩토리
	 */
	public static Data create(L1PcInstance pc, int type, int objid, String text1, int htype) {
		switch (type) {
		case TYPE_RECOMMEND:
			return createRecommended(pc);
		case TYPE_APPLIED_LIST:
			return createAppliedList(pc);
		case TYPE_REQUEST_LIST:
			return createRequestList(pc);
		case TYPE_APPLY:
		case TYPE_CANCEL:
			return createSimple(type, objid, htype);
		default:
			return createSimple(type, objid, htype);
		}
	}

	

	

    private static final Charset MS949 = Charset.forName("MS949");

    private static int stringBytes(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    public static boolean send(L1PcInstance pc, int type, int objid, String text, int htype) {
        return pc != null && create(pc, type, objid, text, htype).writeTo(pc.getNetConnection());
    }

    


    public static final class Data {
private final int _type;

private final int _objId;

private final int _hType;

private final boolean _noRequest;

private final List<ClanNode> _clanList;

private final List<ApplicantNode> _applicantList;

private Data(int type, int objId, int hType, boolean noRequest, List<ClanNode> clanList, List<ApplicantNode> applicantList) {
		_type = type;
		_objId = objId;
		_hType = hType;
		_noRequest = noRequest;
		_clanList = clanList != null ? clanList : Collections.<ClanNode>emptyList();
		_applicantList = applicantList != null ? applicantList : Collections.<ApplicantNode>emptyList();
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_MATCH_MAKING);
		buffer.writeC(_type);

		switch (_type) {
		case TYPE_RECOMMEND:
			buffer.writeC(0x00);
			buffer.writeC(_clanList.size());
			for (ClanNode node : _clanList) {
				buffer.writeD(node.clanId);       // 혈마크
				buffer.writeS(node.clanName);      // 혈맹 이름
				buffer.writeS(node.leaderName);    // 군주 이름
				buffer.writeD(node.onlineMaxUser);// 주간 최대 접속자 수
				buffer.writeC(node.type2);         // 0: 사냥, 1: 전투, 2: 친목
				buffer.writeC(node.hasHouse ? 0x01 : 0x00); // 아지트
				buffer.writeB(node.isWar);         // 전쟁 여부
				buffer.writeC(0x00);               // 고정값
				buffer.writeS(node.text);          // 소개 멘트
				buffer.writeD(node.clanId);        // 혈맹 objid
			}
			break;

		case TYPE_APPLIED_LIST:
			buffer.writeC(0x00);
			buffer.writeC(_clanList.size());
			for (ClanNode node : _clanList) {
				buffer.writeD(node.clanId);       // 삭제 시 띄우는 obj값
				buffer.writeC(0x00);
				buffer.writeD(node.clanId);       // 혈마크
				buffer.writeS(node.clanName);      // 혈맹 이름
				buffer.writeS(node.leaderName);    // 군주 이름
				buffer.writeD(node.onlineMaxUser);// 주간 최대 접속자 수
				buffer.writeC(node.type2);         // 0: 사냥, 1: 전투, 2: 친목
				buffer.writeC(node.hasHouse ? 0x01 : 0x00); // 아지트
				buffer.writeB(node.isWar);         // 전쟁 여부
				buffer.writeC(0x00);               // 고정값
				buffer.writeS(node.text);          // 소개 멘트
				buffer.writeD(node.clanId);        // 혈맹 objid
			}
			break;

		case TYPE_REQUEST_LIST:
			if (_noRequest) {
				buffer.writeC(0x82); // 요청 목록이 없을 때
			} else {
				buffer.writeC(0x00);
				buffer.writeC(0x02);
				buffer.writeC(0x00); // 고정값
				buffer.writeC(_applicantList.size());
				for (ApplicantNode user : _applicantList) {
					buffer.writeD(user.userId);        // 신청자 objid
					buffer.writeC(0x00);
					buffer.writeC(user.onlineStatus); // 0x01: 접속, 0x00: 비접속
					buffer.writeS(user.username);      // 신청자 이름
					buffer.writeC(user.classType);     // 클래스
					buffer.writeH(user.lawful);        // 라우풀
					buffer.writeC(user.level);         // 레벨
					buffer.writeC(0x01);               // 이름 앞 풀잎 표식
				}
			}
			break;

		case TYPE_APPLY:
		case TYPE_CANCEL:
			buffer.writeC(0x00);
			buffer.writeD(_objId);
			buffer.writeC(_hType);
			break;

		default:
			break;
		}
	}

private boolean writeTo(CIOClient client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int required = frameLength();
        if (required < 3) return false;
        final CIOFrame frame = connection.beginDirectFrame(required);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeImpl(client, frame.buffer());
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

private int frameLength() {
        long len = 2L + 1 + 1;
        switch (_type) {
        case TYPE_RECOMMEND:
            len += 2;
            for (ClanNode n : _clanList) {
                len += 4 + stringBytes(n.clanName) + stringBytes(n.leaderName)
                     + 4 + 1 + 1 + 1 + 1 + stringBytes(n.text) + 4;
            }
            break;
        case TYPE_APPLIED_LIST:
            len += 2;
            for (ClanNode n : _clanList) {
                len += 4 + 1 + 4 + stringBytes(n.clanName) + stringBytes(n.leaderName)
                     + 4 + 1 + 1 + 1 + 1 + stringBytes(n.text) + 4;
            }
            break;
        case TYPE_REQUEST_LIST:
            if (_noRequest) {
                len += 1;
            } else {
                len += 4;
                for (ApplicantNode n : _applicantList) {
                    len += 4 + 1 + 1 + stringBytes(n.username) + 1 + 2 + 1 + 1;
                }
            }
            break;
        case TYPE_APPLY:
        case TYPE_CANCEL:
            len += 1 + 4 + 1;
            break;
        default:
            break;
        }
        return len <= Integer.MAX_VALUE ? (int) len : 0;
    }
    }
}