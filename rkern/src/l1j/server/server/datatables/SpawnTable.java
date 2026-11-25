package l1j.server.server.datatables;

/** 기존 호출부에서 사용하는 스폰 DB 진입점. 정의 로딩 및 등록은 상위 클래스에서 수행한다. */
public final class SpawnTable extends CNpcMakerDB {
    private static class SingletonHolder {
        private static volatile SpawnTable INSTANCE = new SpawnTable();
    }

    private SpawnTable() {}

    public static SpawnTable getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static void reload() {
        SingletonHolder.INSTANCE = new SpawnTable();
    }
}
