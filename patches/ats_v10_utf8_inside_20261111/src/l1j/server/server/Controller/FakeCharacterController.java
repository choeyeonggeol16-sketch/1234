package l1j.server.server.Controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.RepeatTask;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Beginner;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.FakePcInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

class L1FakeCharacterInfo
{

	public L1FakeCharacterInfo() {
		_x = 0;
		_y = 0;
		_heading = 0;
		_mapId = 0;
		_staticBot = false;
	}
	
	public L1FakeCharacterInfo(String name, int x, int y, int heading, int mapId, boolean staticBot) {
		_name = name;
		_x = x;
		_y = y;
		_heading = heading;
		_mapId = mapId;
		_staticBot = staticBot;
	}

	public String	_name;
	public int		_x;
	public int		_y;
	public int		_heading;
	public int		_mapId;
	public boolean	_staticBot;
}

public class FakeCharacterController extends RepeatTask {
	private static final int[] MALE_LIST = new int[] { 61/*기사*/, 138/*요정*/, 20071/*법사*/, 2786/*다엘*/, 6658/*용기사*/, 6671/*환술사*/ };
	private static final int[] FEMALE_LIST = new int[] { 48, 37, 20072, 2796, 6661, 6650 };
	private static final int[] WEAPON_LIST = new int[] { 41, 172, 125, 80, 502, 503 };
	private static final int[] TYPE_RATIO = new int[] { 27, 20, 23, 20, 8, 2 };
	
	private static final int STATUS_NONE = 0;
	private static final int STATUS_SPAWN_NORMAL = 1;
	private static final int STATUS_SPAWN_POLYMORPH = 2;
	

	private static Random _random =  new Random(System.nanoTime());

	private static FakeCharacterController _instance;

	private List<L1FakeCharacterInfo> _fakeCharacterList = new ArrayList<L1FakeCharacterInfo>();
	
	private int _status = STATUS_NONE;

	public static FakeCharacterController getInstance() {
		if (_instance == null) {
			_instance = new FakeCharacterController();
		}
		
		return _instance;
	}
	
	public FakeCharacterController() {
		super(2000);

		_status = STATUS_NONE;
	}

	@Override
	public void execute() {
		if (_status == STATUS_NONE) {
			return;
		}

		if (!_fakeCharacterList.isEmpty()) {
			L1FakeCharacterInfo info = _fakeCharacterList.get(0);
			_fakeCharacterList.remove(0);

			if (_status == STATUS_SPAWN_NORMAL) {
				loadBot(info._name, info._x, info._y, info._heading, info._mapId, info._staticBot, false);
			} else {
				loadBot(info._name, info._x, info._y, info._heading, info._mapId, info._staticBot, true);
			}
		}
	}
	
	public void start(int status) {
		if (_status != STATUS_NONE) {
			return;
		}

		if (status < STATUS_NONE || status > STATUS_SPAWN_POLYMORPH) {
			status = STATUS_NONE;
		}
		_status = status;
	}
	
	public void loadBot(String name, int x, int y, int heading, int mapId, boolean staticBot, boolean doPoly) {
		FakePcInstance pc = FakePcInstance.load(name);

		if (pc == null) {
			return;
		}

		CharacterTable.getInstance().restoreInventory(pc);

		pc.SetSpawnInfo(x, y, heading, mapId);

		pc.getLocation().set(x, y);
		pc.setHeading(heading);
		pc.setMap((short) mapId);

		if (doPoly) {
			pc.setPolymorph();
		} else {
			pc.setLawful(0);
		}

		pc.getMap().setPassable(pc.getLocation(), false);

		L1World.getInstance().storeObject(pc);
		L1World.getInstance().addVisibleObject(pc);

		if (!staticBot) {
			pc.startAction();
		}
	}
	
	private void addBot(String name, int x, int y, int heading, int mapId, boolean staticBot) {
		L1FakeCharacterInfo info = new L1FakeCharacterInfo(name, x, y, heading, mapId, staticBot);

		_fakeCharacterList.add(info);
	}

	public void load() {
		Connection con = null;
		PreparedStatement pstm = null;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM bots");
			ResultSet rs = pstm.executeQuery();

			while (rs.next()) {
				String name = rs.getString("name");
				int x = rs.getInt("x");
				int y = rs.getInt("y");
				int heading = rs.getInt("heading");
				int mapId = rs.getInt("mapId");
				boolean staticBot = rs.getBoolean("type");

				addBot(name, x, y, heading, mapId, staticBot);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public void deleteBot(String name) {
		L1PcInstance target = L1World.getInstance().getPlayer(name);

		target.logout();

		Connection con = null;
		PreparedStatement pstm = null;

		try {
			if (target != null) {
				L1Clan clan = L1World.getInstance().getClan(target.getClanname());
				if (clan != null) { 
					clan.delMemberName(target.getName());
					// clan.delMemberName(name);
				}
			}
			CharacterTable.getInstance().deleteCharacter("", name);

			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM bots WHERE name = ?");
			pstm.setString(1, name);
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}


	public boolean createNewBot(String name, int x, int y, int heading, int mapId, boolean staticBot) {
		if (CharacterTable.doesCharNameExist(name) || L1World.getInstance().getPlayer(name) != null) {
			return false;
		}

		int sex = _random.nextInt(2);

		int type = 0;
		int typeRatio = _random.nextInt(100);
		for (int i = 0; i < TYPE_RATIO.length; ++i) {
			if (TYPE_RATIO[i] > typeRatio) {
				type = i;
				break;
			}

			typeRatio -= TYPE_RATIO[i];
		}

		L1PcInstance targetPc = createBot(name, sex, type, x, y, heading, mapId);
		targetPc.logout();

		Connection con = null;
		PreparedStatement pstm = null;

		try {
			Beginner.getInstance().GiveItem(targetPc);
			CharacterTable.getInstance().storeNewCharacter(targetPc);

			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO bots SET name = ?, x = ?, y = ?, heading = ?, mapId = ?, type = ?");
			pstm.setString(1, name);
			pstm.setInt(2, x);
			pstm.setInt(3, y);
			pstm.setInt(4, heading);
			pstm.setInt(5, mapId);
			pstm.setBoolean(6, staticBot);

			pstm.execute();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		if (staticBot) {
			loadBot(name, x, y, heading, mapId, staticBot, false);
			return true;
		} else if (_status == STATUS_SPAWN_NORMAL) {
			loadBot(name, x, y, heading, mapId, staticBot, false);
		} else {
			loadBot(name, x, y, heading, mapId, staticBot, true);
		}

		return true;
	}

	public L1PcInstance createBot(String name, int sex, int type, int x, int y, int heading, int mapId) {
		FakePcInstance newPc = new FakePcInstance(x, y, heading, mapId);
		newPc.setAccountName("");
		newPc.setId(IdFactory.getInstance().nextId());
		newPc.setName(name);
		newPc.setHighLevel(1);
		newPc.setExp(0);
		newPc.addBaseMaxHp((short) 5000);
		newPc.setCurrentHp(5000);
		newPc.setDead(false);
		newPc.setStatus(0);
		newPc.addBaseMaxMp((short) 2);
		newPc.setCurrentMp(2);

		newPc.getAbility().setBaseStr(16);
		newPc.getAbility().setStr(16);
		newPc.getAbility().setBaseCon(16);
		newPc.getAbility().setCon(16);
		newPc.getAbility().setBaseDex(11);
		newPc.getAbility().setDex(11);
		newPc.getAbility().setBaseCha(13);
		newPc.getAbility().setCha(13);
		newPc.getAbility().setBaseInt(12);
		newPc.getAbility().setInt(12);
		newPc.getAbility().setBaseWis(11);
		newPc.getAbility().setWis(11);

		int klass = 0;

		if (sex == 0) {
			klass = MALE_LIST[type];
		} else {
			klass = FEMALE_LIST[type];

		}

		newPc.setCurrentWeapon(0);
		newPc.setClassId(klass);
		newPc.setCurrentSprite(klass);
		newPc.set_sex(sex);
		newPc.setType(type + 1);

		newPc.setHeading(heading);
		newPc.setX(x);
		newPc.setY(y);
		newPc.setMap((short) mapId);

		newPc.setFood(39);
		newPc.setLawful(_random.nextInt(16000));
		newPc.setTitle("");
		newPc.setClanid(0);
		newPc.setClanname("");
		newPc.setClanRank(0);
		newPc.setBonusStats(0); 
		newPc.setElfAttr(0);
		newPc.set_PKcount(0);
		newPc.setExpRes(0);
		newPc.setPartnerId(0);
		newPc.setAccessLevel((short) 0);
		newPc.setGm(false);
		newPc.setMonitor(false);
		newPc.setOnlineStatus(1);
		newPc.setHomeTownId(0);
		newPc.setContribution(0);
		newPc.setHellTime(0);
		newPc.setBanned(false);
		newPc.setKarma(0);
		newPc.setReturnStat(0);  

		newPc.refresh();
		newPc.setMoveSpeed(0);
		newPc.setBraveSpeed(0);
		newPc.setGmInvis(false);

		ItemInstance item = ItemTable.getInstance().createItem(WEAPON_LIST[type]);
		newPc.getInventory().storeItem(item);
		newPc.getInventory().setEquipped(item, true);

		newPc.getMap().setPassable(newPc.getLocation(), false);

		L1World.getInstance().storeObject(newPc);
		L1World.getInstance().addVisibleObject(newPc);

		newPc.setNetConnection(null);
		newPc.startObjectAutoUpdate();

		return newPc;
	}

}
