package l1j.server.server.model.character.login.skill;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 스킬 로그인 복구 핸들러 
 */
@FunctionalInterface
public interface BuffRestoreHandler {
    /**
     * @param pc 대상 캐릭터
     * @param skillId 스킬 ID
     * @param polyId 저장된 변신 ID (변신류 버프 전용, 그 외 무시 가능)
     * @param remainingTime 남은 시간(초)
     * @param icon S_UnityIcon 통합 아이콘 배열 (필요한 버프만 기록)
     */
    void restore(L1PcInstance pc, int skillId, int polyId, int remainingTime, int[] icon);
}
