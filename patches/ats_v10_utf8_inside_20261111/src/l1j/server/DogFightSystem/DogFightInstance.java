package l1j.server.DogFightSystem;

import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_WorldObject;

public class DogFightInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;
	
	public static DogFightInstance newInstance(int gfx, String name, String nameid, int max_hp, int num, int x, int y, int mapid, int min_dmg, int max_dmg,
			int skill_per, int skill_min_dmg, int skill_max_dmg, int skill_effect, int win_effect, int heading) {
		DogFightInstance dogfight = newInstance();
		dogfight.setId(IdFactory.getInstance().nextId());	
		dogfight.set_class_info();
		dogfight.setCurrentSprite(gfx);
		dogfight.setName(name);
		dogfight.setNameId(nameid);
		dogfight.setMaxHp(max_hp);
		dogfight.setCurrentHp(max_hp);
		dogfight.set_num(num);
		dogfight.setX(x);
		dogfight.setY(y);
		dogfight.setMap((short) mapid);
		dogfight.setMoveSpeed(0);
		
		// 인포
		dogfight.setMinDmg(min_dmg);
		dogfight.setMaxDmg(max_dmg);
		dogfight.setSkillPercent(skill_per);
		dogfight.setSkillMinDmg(skill_min_dmg);
		dogfight.setSkillMaxDmg(skill_max_dmg);
		dogfight.setSkillEffect(skill_effect);
		dogfight.setWinEffect(win_effect);
		dogfight.setHeading(heading);
		dogfight.do_store_object();
		return dogfight;
	}
	
	public DogFightInstance set_class_info() {
		L1Npc npc = new L1Npc();
		npc.set_family(0);
		npc.set_agrofamily(0);
		npc.set_picupitem(false);
		
		setting_template(npc);
		return this;
	}

	private static DogFightInstance newInstance() {
		return new DogFightInstance();
	}
	
	private Object m_death_lock;
	private int m_x;
	private int m_y;
	private int m_mapid;
	private int m_min_dmg;
	private int m_max_dmg;
	private int m_skill_percent;
	private int m_skill_min_dmg;
	private int m_skill_max_dmg;
	private int m_skill_effect;
	private int m_win_effect;
	private boolean m_skill_use;
	
	public int get_X() {
		return m_x;
	}
	
	public void set_X(int value) {
		m_x = value;
	}
	
	public int get_Y() {
		return m_y;
	}
	
	public void set_Y(int value) {
		m_y = value;
	}
	
	public int get_Map_Id() {
		return m_mapid;
	}
	
	public void set_Map_Id(int value) {
		m_mapid = value;
	}
	
	public int getMinDmg() {
		return m_min_dmg;
	}
	
	public void setMinDmg(int value) {
		m_min_dmg = value;
	}
	
	public int getMaxDmg() {
		return m_max_dmg;
	}
	
	public void setMaxDmg(int value) {
		m_max_dmg = value;
	}
	
	public int getSkillPercent() {
		return m_skill_percent;
	}
	
	public void setSkillPercent(int value) {
		m_skill_percent = value;
	}
	
	public void addSkillPercent(int value) {
		m_skill_percent += value;
	}
	
	public int getSkillMinDmg() {
		return m_skill_min_dmg;
	}
	
	public void setSkillMinDmg(int value) {
		m_skill_min_dmg = value;
	}
	
	public int getSkillMaxDmg() {
		return m_skill_max_dmg;
	}
	
	public void setSkillMaxDmg(int value) {
		m_skill_max_dmg = value;
	}
	
	public int getSkillEffect() {
		return m_skill_effect;
	}
	
	public void setSkillEffect(int value) {
		m_skill_effect = value; 
	}
	
	public int getWinEffect() {
		return m_win_effect;
	}
	
	public void setWinEffect(int value) {
		m_win_effect = value; 
	}
	
	public boolean getSkillUse() {
		return m_skill_use;
	}
	
	public void setSkillUse(boolean value) {
		m_skill_use = value; 
	}

	public DogFightInstance() {
		super(null);
		m_death_lock = new Object();
	}

	public void do_store_object() {
		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
	}

	public void death(DogFightInstance last_attacker) {
		synchronized (m_death_lock) {
			if (isDead())
				return;

			setDead(true);
		}

		setStatus(ActionCodes.ACTION_Die);
		setCurrentHp(0);
		getMap().setPassable(getLocation(), true);
		sendActionAll(ActionCodes.ACTION_Die);
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
		broadcastPacket(new S_HPMeter(this));
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.npc(this));
		perceivedFrom.sendPackets(new S_HPMeter(this));
		if (isDead()) {
			perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
		}
	}

	@Override
	public boolean noTarget() {
		return false;
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (!(attacker instanceof DogFightInstance)) {
			return;
		}
		DogFightInstance attacker_dog = (DogFightInstance) attacker;
		if (getCurrentHp() <= 0) {
			if (!isDead())
				death(attacker_dog);
			return;
		}
		int new_hp = getCurrentHp() - damage;
		if (new_hp <= 0)
			death(attacker_dog);
		else
			setCurrentHp(new_hp);
	}

	@Override
	public void onAction(L1PcInstance player) {
	}
}
