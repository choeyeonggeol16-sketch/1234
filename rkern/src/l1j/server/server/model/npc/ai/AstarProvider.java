package l1j.server.server.model.npc.ai;

import l1j.server.server.model.ai.core.PathFinder;

public class AstarProvider {
    
    private static final ThreadLocal<PathFinder> ASTAR_THREAD_LOCAL = ThreadLocal.withInitial(() -> new PathFinder());

    public static PathFinder get() {
        return ASTAR_THREAD_LOCAL.get();
    }
}