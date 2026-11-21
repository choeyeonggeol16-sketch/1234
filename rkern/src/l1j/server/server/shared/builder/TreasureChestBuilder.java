package l1j.server.server.shared.builder;

import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.treasure.L1TreasureChest;
import l1j.server.server.model.item.treasure.TreasureChestAttackFilter;
import l1j.server.server.model.item.treasure.TreasureChestCompensator;
import l1j.server.server.model.item.treasure.TreasureChestOpenFilter;

public class TreasureChestBuilder {
	private String 						_name;
	private int							_gfx;
	private int							_key;
	private TreasureChestOpenFilter 	_ofilter;
	private TreasureChestAttackFilter	_afilter;
	private TreasureChestCompensator	_compensator;
	public TreasureChestBuilder setName(String s){
		_name = s;
		return this;
	}
	
	public TreasureChestBuilder setGfx(int i){
		_gfx = i;
		return this;
	}
	
	public TreasureChestBuilder setKey(int i){
		_key = i;
		return this;
	}
	
	public int getKey(){
		return _key;
	}
	
	public TreasureChestBuilder setOpenFilter(TreasureChestOpenFilter filter){
		_ofilter = filter;
		return this;
	}
	
	public TreasureChestBuilder setAttackFilter(TreasureChestAttackFilter afilter){
		_afilter = afilter;
		return this;
	}
	
	public TreasureChestBuilder setCompensator(TreasureChestCompensator compensator){
		_compensator = compensator;
		return this;
	}
	
	public L1TreasureChest build(int x, int y, int h, short mapId){
		L1TreasureChest chest = new L1TreasureChest(
				IdFactory.getInstance().nextId(), _name, _gfx, x, y, h, mapId, _ofilter, _afilter, _compensator);
		L1World.getInstance().storeAndEnterWorld(chest);
		_ofilter 		= null;
		_afilter 		= null;
		_compensator 	= null;
		return chest;
	}
}
