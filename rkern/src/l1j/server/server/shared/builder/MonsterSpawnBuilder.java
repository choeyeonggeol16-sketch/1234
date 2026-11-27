package l1j.server.server.shared.builder;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayDeque;

import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.listener.MonsterDeathHandler;
import l1j.server.server.templates.L1Npc;

public class MonsterSpawnBuilder {
	private L1Npc[] 		_templates;
	
	public void dispose(){
		_templates = null;
	}
	
	public MonsterSpawnBuilder setNpc(ArrayDeque<Integer> npcIds){
		_templates = new L1Npc[npcIds.size()];
		int idx = 0;
		while(!npcIds.isEmpty())
            _templates[idx++] = CObjectDB.LoadNPC().getTemplate(npcIds.poll());
		return this;
	}
	
	public MonsterSpawnBuilder setNpc(int[] npc_ids){
		_templates = new L1Npc[npc_ids.length];
		for(int i=npc_ids.length - 1; i>=0; --i)
            _templates[i] = CObjectDB.LoadNPC().getTemplate(npc_ids[i]);
		return this;
	}
	
	public L1Npc getNpc(){
		return _templates[GameRandom.nextInt(_templates.length)];
	}
	
	private L1MonsterInstance create(int x, int y, int h, short mapid){
		if(_templates == null || _templates.length <= 0)
			return null;
		
		return create(x, y, h, mapid, _templates[GameRandom.nextInt(_templates.length)]);
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
		m.setHeading(GameRandom.nextInt(8));
		m.setLightSize(npc.getLightSize());
		m.refreshLight();
		CWorld.getInstance().storeAndEnterWorld(m);
		return m;
	}
	
	public L1MonsterInstance build(int left, int top, int right, int bottom, short mapid, MonsterDeathHandler handler){
		int cx = GameRandom.nextIntOrFixed(left, right);
		int cy = GameRandom.nextIntOrFixed(top, bottom);
		return create(cx, cy, GameRandom.nextInt(8), mapid).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int left, int top, int right, int bottom, short mapid, MonsterDeathHandler handler, L1Npc npc){
		int cx = GameRandom.nextIntOrFixed(left, right);
		int cy = GameRandom.nextIntOrFixed(top, bottom);
		return create(cx, cy, GameRandom.nextInt(8), mapid, npc).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid, MonsterDeathHandler handler){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
			cy += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
		}
		return create(cx, cy, GameRandom.nextInt(8), mapid).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid, MonsterDeathHandler handler, L1Npc npc){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
			cy += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
		}
		return create(cx, cy, GameRandom.nextInt(8), mapid, npc).setDeathHandler(handler);
	}
	
	public L1MonsterInstance build(int x, int y, int range, short mapid){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
			cy += GameRandom.nextBoolean() ? -GameRandom.nextInt(range) : GameRandom.nextInt(range);
		}
		return create(cx, cy, GameRandom.nextInt(8), mapid);
	}
}
