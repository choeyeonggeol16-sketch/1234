package l1j.server.server.model;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.templates.L1Npc;

/** 스폰 행의 몬스터 원형, 수량, 위치 및 재생성 설정. */
public abstract class CNpcSpawnDefine {
    protected final L1Npc _template;

    protected int _id;
    private String _location;
    protected int _maximumCount;
    protected int _npcid;
    private int _groupId;
    private int _locx;
    private int _locy;
    private int _randomx;
    private int _randomy;
    private int _locx1;
    private int _locy1;
    private int _locx2;
    private int _locy2;
    protected int _heading;
    protected int _minRespawnDelay;
    protected int _maxRespawnDelay;

    protected short _mapid;
    private boolean _respaenScreen;
    private int _movementDistance;
    private boolean _rest;
    protected int _delayInterval;

    private String _name;


    protected CNpcSpawnDefine(L1Npc template) {
        _template = template;
        _npcid = template != null ? template.get_npcId() : 0;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public short getMapId() {
        return _mapid;
    }

    public void setMapId(short _mapid) {
        this._mapid = _mapid;
    }

    public boolean isRespawnScreen() {
        return _respaenScreen;
    }

    public void setRespawnScreen(boolean flag) {
        _respaenScreen = flag;
    }

    public int getMovementDistance() {
        return _movementDistance;
    }

    public void setMovementDistance(int i) {
        _movementDistance = i;
    }

    public int getAmount() {
        return _maximumCount;
    }

    public int getGroupId() {
        return _groupId;
    }

    public int getId() {
        return _id;
    }

    public String getLocation() {
        return _location;
    }

    public int getLocX() {
        return _locx;
    }

    public int getLocY() {
        return _locy;
    }

    public int getNpcId() {
        return _npcid;
    }

    public int getHeading() {
        return _heading;
    }

    public int getRandomx() {
        return _randomx;
    }

    public int getRandomy() {
        return _randomy;
    }

    public int getLocX1() {
        return _locx1;
    }

    public int getLocY1() {
        return _locy1;
    }

    public int getLocX2() {
        return _locx2;
    }

    public int getLocY2() {
        return _locy2;
    }

    public int getMinRespawnDelay() {
        return _minRespawnDelay;
    }

    public int getMaxRespawnDelay() {
        return _maxRespawnDelay;
    }

    public void setAmount(int amount) {
        _maximumCount = amount;
    }

    public void setId(int id) {
        _id = id;
    }

    public void setGroupId(int i) {
        _groupId = i;
    }

    public void setLocation(String location) {
        _location = location;
    }

    public void setLocX(int locx) {
        _locx = locx;
    }

    public void setLocY(int locy) {
        _locy = locy;
    }

    public void setNpcid(int npcid) {
        _npcid = npcid;
    }

    public void setHeading(int heading) {
        _heading = heading;
    }

    public void setRandomx(int randomx) {
        _randomx = randomx;
    }

    public void setRandomy(int randomy) {
        _randomy = randomy;
    }

    public void setLocX1(int locx1) {
        _locx1 = locx1;
    }

    public void setLocY1(int locy1) {
        _locy1 = locy1;
    }

    public void setLocX2(int locx2) {
        _locx2 = locx2;
    }

    public void setLocY2(int locy2) {
        _locy2 = locy2;
    }

    public void setMinRespawnDelay(int i) {
        _minRespawnDelay = i;
    }

    public void setMaxRespawnDelay(int i) {
        _maxRespawnDelay = i;
    }

    protected final long calcRespawnDelay() {
        long respawnDelay = (long) _minRespawnDelay * 1000L;
        if (_delayInterval > 0) {
            respawnDelay += (long) ThreadLocalRandom.current().nextInt(_delayInterval) * 1000L;
        }
        return respawnDelay;
    }

    public void setRest(boolean flag) {
        _rest = flag;
    }

    public boolean isRest() {
        return _rest;
    }

    protected final boolean isAreaSpawn() {
        return getLocX1() != 0 && getLocY1() != 0 && getLocX2() != 0 && getLocY2() != 0;
    }

    protected final boolean isRandomSpawn() {
        return getRandomx() != 0 || getRandomy() != 0;
    }
  
    public long calcRespawnDelayPublic() {
        return calcRespawnDelay();
    }
}
