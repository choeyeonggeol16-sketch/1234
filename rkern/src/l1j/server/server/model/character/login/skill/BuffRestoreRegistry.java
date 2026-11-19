package l1j.server.server.model.character.login.skill;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.character.skill.L1SkillUse;

/**
 * 스킬 로그인 복구 핸들러 레지스트리.
 */
public final class BuffRestoreRegistry {

    private static final Map<Integer, BuffRestoreHandler> _handlers = new ConcurrentHashMap<>(512);

    private static final BuffRestoreHandler DEFAULT_HANDLER =
            (pc, skillId, polyId, remainingTime, icon) ->
    
    L1SkillUse.cast(
            pc,
            skillId,
            pc.getId(),
            pc.getX(),
            pc.getY(),
            null,
            remainingTime,
            L1SkillUse.TYPE_LOGIN);

    private BuffRestoreRegistry() {}

    public static void register(int skillId, BuffRestoreHandler handler) {
        if (_handlers.putIfAbsent(skillId, handler) != null) {
            throw new IllegalStateException("[BuffRegistry] 중복 등록 skillId=" + skillId);
        }
      // System.out.println("  등록: " + skillId);  
    }

    public static BuffRestoreHandler find(int skillId) {
        return _handlers.getOrDefault(skillId, DEFAULT_HANDLER);
    }

    public static int size() {
        return _handlers.size();
    }
}