package l1j.server.MJTemplate.Builder;

import l1j.server.MJTemplate.TreasureChest.MJL1TreasureChest;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestAttackFilter;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestCompensator;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestOpenFilter;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;

public class MJTreasureChestBuilder {
	private String 						_name;
	private int							_gfx;
	private int							_key;
	private MJTreasureChestOpenFilter 	_ofilter;
	private MJTreasureChestAttackFilter	_afilter;
	private MJTreasureChestCompensator	_compensator;
	public MJTreasureChestBuilder setName(String s){
		_name = s;
		return this;
	}
	
	public MJTreasureChestBuilder setGfx(int i){
		_gfx = i;
		return this;
	}
	
	public MJTreasureChestBuilder setKey(int i){
		_key = i;
		return this;
	}
	
	public int getKey(){
		return _key;
	}
	
	public MJTreasureChestBuilder setOpenFilter(MJTreasureChestOpenFilter filter){
		_ofilter = filter;
		return this;
	}
	
	public MJTreasureChestBuilder setAttackFilter(MJTreasureChestAttackFilter afilter){
		_afilter = afilter;
		return this;
	}
	
	public MJTreasureChestBuilder setCompensator(MJTreasureChestCompensator compensator){
		_compensator = compensator;
		return this;
	}
	
	public MJL1TreasureChest build(int x, int y, int h, short mapId){
		MJL1TreasureChest chest = new MJL1TreasureChest(
				IdFactory.getInstance().nextId(), _name, _gfx, x, y, h, mapId, _ofilter, _afilter, _compensator);
		L1World.getInstance().storeAndEnterWorld(chest);
		_ofilter 		= null;
		_afilter 		= null;
		_compensator 	= null;
		return chest;
	}
}
