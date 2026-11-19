package l1j.server.MJTemplate.Builder;

import java.util.ArrayDeque;
import java.util.Random;

import l1j.server.MJTemplate.Interface.MJMonsterDeathHandler;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.templates.L1Npc;

public class MJMonsterSpawnBuilder {
	private static final Random 	_rnd = new Random(System.nanoTime());
	private L1Npc[] 		_templates;
	
	public void dispose(){
		_templates = null;
	}
	
	public MJMonsterSpawnBuilder setNpc(ArrayDeque<Integer> npcIds){
		_templates = new L1Npc[npcIds.size()];
		int idx = 0;
		while(!npcIds.isEmpty())
			_templates[idx++] = NpcTable.getInstance().getTemplate(npcIds.poll());
		return this;
	}
	
	public MJMonsterSpawnBuilder setNpc(int[] npc_ids){
		_templates = new L1Npc[npc_ids.length];
		for(int i=npc_ids.length - 1; i>=0; --i)
			_templates[i] = NpcTable.getInstance().getTemplate(npc_ids[i]);
		return this;
	}
	
	public L1Npc getNpc(){
		return _templates[_rnd.nextInt(_templates.length)];
	}
	
	private L1MonsterInstance create(int x, int y, int h, short mapid){
		if(_templates == null || _templates.length <= 0)
			return null;
		
		return create(x, y, h, mapid, _templates[_rnd.nextInt(_templates.length)]);
	}
	
	private L1MonsterInstance create(int x, int y, int h, short mapid, L1Npc npc){
		if(npc == null)
			return null;
		
		L1MonsterInstance m = new L1MonsterInstance(npc);
		m.setId(IdFactory.getInstance().nextId());
		m.setMap(mapid);
		m.getLocation().forward(0);
		m.setX(x); m.setHomeX(x);
		m.setY(y); m.setHomeY(y);
		m.setHeading(_rnd.nextInt(8));
		m.setLightSize(npc.getLightSize());
		m.getLight().turnOnOffLight();
		L1World.getInstance().storeAndEnterWorld(m);
		return m;
	}
	
	public L1MonsterInstance build(int left, int top, int right, int bottom, short mapid, MJMonsterDeathHandler handler){
		int cx = _rnd.nextInt(right - left) + left;
		int cy = _rnd.nextInt(bottom - top) + top;
		return create(cx, cy, _rnd.nextInt(8), mapid).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int left, int top, int right, int bottom, short mapid, MJMonsterDeathHandler handler, L1Npc npc){
		int cx = _rnd.nextInt(right - left) + left;
		int cy = _rnd.nextInt(bottom - top) + top;
		return create(cx, cy, _rnd.nextInt(8), mapid, npc).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid, MJMonsterDeathHandler handler){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
			cy += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
		}
		return create(cx, cy, _rnd.nextInt(8), mapid).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid, MJMonsterDeathHandler handler, L1Npc npc){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
			cy += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
		}
		return create(cx, cy, _rnd.nextInt(8), mapid, npc).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
			cy += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
		}
		return create(cx, cy, _rnd.nextInt(8), mapid);
	}
}
