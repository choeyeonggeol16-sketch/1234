package l1j.server.server.model.network.core.sc.packet;

import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.MJKDASystem.MJKDALoader;
import l1j.server.MJRaidSystem.MJRaidSpace;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.server.BadNamesList;
import l1j.server.server.Opcodes;
import l1j.server.server.UserCommands;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.utils.CommonUtil;

public final class SC_ChangeCharName {

	private static final Logger _log = Logger.getLogger(SC_ChangeCharName.class.getName());

	private static final int RESULT_NO_PACKET = -1;
	private static final int RESULT_FAILURE = 0;
	private static final int RESULT_SUCCESS = 1;

	private SC_ChangeCharName() { }

	public static boolean sendStart(L1PcInstance pc) {
		return pc != null && send(pc.getNetConnection(), 2, false);
	}

	public static boolean sendResult(CIOClient client, boolean success) {
		return send(client, 1, success);
	}

	private static boolean send(CIOClient client, int mode, boolean success) {
		if (client == null || !client.isConnected()) return false;
		final CUserSocket conn = client.getConnection();
		if (conn == null || !conn.isClientConnected()) return false;
		final CIOFrame frame = conn.beginDirectFrame(8);
		if (frame == null) return false;
		boolean committed = false;
		try {
			final WriteBuffer buffer = frame.buffer();
			buffer.writeC(Opcodes.S_VOICE_CHAT);
			if (mode == 1) {
				buffer.writeC(0x1E);
				buffer.writeH(success ? 0x02 : 0x06);
				buffer.writeH(0x00);
			} else {
				buffer.writeC(0x1D);
				buffer.writeD(0x00);
			}
			committed = conn.commitDirectFrame(frame);
			return committed;
		} finally {
			if (!committed) conn.abortDirectFrame(frame);
		}
	}

	/**
	 * 캐릭터 이름 변경 비즈니스 로직 수행
	 */
	public static int doChangeCharName(L1PcInstance pc, String sourceName, String destinationName) {
		if (pc == null || sourceName == null || destinationName == null) {
			return RESULT_FAILURE;
		}

		try {
			if (!isValidNewName(destinationName)) {
				return RESULT_FAILURE;
			}

			if (!pc.getInventory().checkItem(408991, 1)) {
				if (pc.getNetConnection() != null) {
					pc.getNetConnection().close();
				}
				return RESULT_NO_PACKET;
			}

			// 3. DB 캐릭터 이름 업데이트
			Updator.exec("UPDATE characters SET char_name=? WHERE char_name=?", new Handler() {
				@Override
				public void handle(PreparedStatement pstm) throws Exception {
					pstm.setString(1, destinationName);
					pstm.setString(2, pc.getName());
				}
			});

			// 4. DB KDA 정보 업데이트
			Updator.exec("UPDATE tb_kda SET name=? WHERE name=? OR objid=?", new Handler() {
				@Override
				public void handle(PreparedStatement pstm) throws Exception {
					pstm.setString(1, destinationName);
					pstm.setString(2, sourceName);
					pstm.setInt(3, pc.getId());
				}
			});

			try {
				MJKDALoader.getInstance().getChangeName(pc.getId(), destinationName);
			} catch (Exception e) {
				_log.log(Level.SEVERE, "KDA 이름 변경 업데이트 실패", e);
			}

			// 5. 캐릭 정보 저장 및 상태 갱신
			pc.save();
			pc.setChangechaname(false);

			if (pc.getNetConnection() != null) {
				System.out.println(String.format("[%s][%s] %s에서 %s로 캐릭명 변경.\r\n",
						CommonUtil.getLocalTime(),
						pc.getNetConnection().getIp(),
						sourceName,
						destinationName));
			}

			pc.getInventory().consumeItem(408991, 1);
//			if (pc.is_shift_transfer()) {
//				pc.set_shift_type(MJEShiftObjectType.NONE);
//			} else {
//				pc.getInventory().consumeItem(408991, 1);
//			}

			// 7. 후속 연동 데이터 정리 (친구, 편지, 맵 오프셋 등)
			UserCommands.buddys(pc);
			UserCommands.편지삭제(pc);
			MJCopyMapObservable.getInstance().resetPosition(pc);
			MJRaidSpace.getInstance().getBackPc(pc);

			// 8. 캐릭터 선택창 리스타트 처리
			CS_CharSelect.restartProcess(pc);
			return RESULT_SUCCESS;

		} catch (Exception e) {
			_log.log(Level.SEVERE, "캐릭터 이름 변경 로직 처리 중 예외 발생 - PC: " + sourceName, e);
		}

		return RESULT_FAILURE;
	}

	/**
	 * 변경할 캐릭터 이름의 유효성 검사 (중복 코드 및 연산 낭비 최적화)
	 */
	private static boolean isValidNewName(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}

		try {
			byte[] buff = name.getBytes("MS949");
			if (buff.length <= 0) {
				return false;
			}
		} catch (Exception e) {
			return false;
		}

		// DB 및 시스템 상의 이름 중복 체크
		if (CharacterTable.getInstance().isContainNameList(name)
				|| BadNamesList.getInstance().isBadName(name)) {
			return false;
		}

		// 한글 단독 자음/모음, 특정 비속어, 공백 문자(U+3164) 검사
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);

			// 완성되지 않은 한글 자음/모음 범위 (ㄱ~ㅣ: 0x3131~0x3163) 및 특수 공백 (0x3164)
			if ((ch >= 'ㄱ' && ch <= 'ㅣ') || ch == 'ㅤ') {
				return false;
			}

			// 금지어 단일 문자 체크
			if (ch == '씹' || ch == '좃' || ch == '좆') {
				return false;
			}

			// 문자 및 숫자가 아닌 특수문자 차단
			if (!Character.isLetterOrDigit(ch)) {
				return false;
			}
		}

		// 특수문자/알파벳 혼용 규칙 검증
		return UserCommands.isAlphaNumeric(name);
	}

}
