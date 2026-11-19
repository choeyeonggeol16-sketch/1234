package l1j.server.server;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [19fix-17] 마법 사용불가 알림(멘트) 음소거 상태 보관.
 * .마법멘트 토글 명령으로 켜고 끄며, L1SkillUse 의 사용불가 멘트 송신부에서
 * isMuted() 로 확인하여 멘트만 생략한다(기능 차단 로직은 그대로 동작).
 */
public class SkillMsgMute {

	// [19fix-17] objectId 기반 음소거 집합 (동시접속 안전)
	private static final Set<Integer> _muted = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

	private SkillMsgMute() {
	}

	/**
	 * 음소거 상태 토글.
	 *
	 * @param objId 캐릭터 objectId
	 * @return 토글 후 음소거(멘트 끔) 상태가 되면 true
	 */
	public static boolean toggle(int objId) {
		Integer key = Integer.valueOf(objId);
		if (_muted.remove(key)) {
			return false; // 켜짐(멘트 표시) 상태로 복귀
		}
		_muted.add(key);
		return true; // 꺼짐(멘트 생략) 상태
	}

	/**
	 * @param objId 캐릭터 objectId
	 * @return 멘트 끔 상태이면 true
	 */
	public static boolean isMuted(int objId) {
		return _muted.contains(Integer.valueOf(objId));
	}
}
