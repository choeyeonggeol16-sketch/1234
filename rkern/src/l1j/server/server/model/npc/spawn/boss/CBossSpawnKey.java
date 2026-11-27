package l1j.server.server.model.npc.spawn.boss;

/** The existing spawnlist_boss_hot primary key, including the configured anchor. */
record CBossSpawnKey(int npcId, int x, int y, int mapId) {
    static CBossSpawnKey of(CNpcSpawnDefine define) {
        return new CBossSpawnKey(define.getNpcId(), define.getX(), define.getY(), define.getMap());
    }
}
