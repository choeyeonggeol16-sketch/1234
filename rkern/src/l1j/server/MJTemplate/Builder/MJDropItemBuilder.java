package l1j.server.MJTemplate.Builder;

import java.util.Random;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class MJDropItemBuilder {
	private static final Random _rnd = new Random(System.nanoTime());
	private int _itemId;
	private int	_min_enchant;
	private int _max_enchant;
	private int	_min;
	private int	_max;
	private int	_dice;
	
	public MJDropItemBuilder setItemId(int itemId){
		_itemId = itemId;
		return this;
	}
	
	public MJDropItemBuilder setMinCount(int minCount){
		_min = minCount;
		return this;
	}
	
	public MJDropItemBuilder setMaxCount(int maxCount){
		_max = maxCount;
		return this;
	}
	
	public MJDropItemBuilder setMinimumEnchant(int enchant){
		_min_enchant = enchant;
		return this;
	}
	
	public MJDropItemBuilder setMaximumEnchant(int enchant){
		_max_enchant = enchant;
		return this;
	}
	
	public MJDropItemBuilder setDice(int dice){
		_dice = dice;
		return this;
	}
	
	public boolean isWinning(int division){
		if(_dice <= 0)
			return true;
		
		if(division <= 0)
			division = 1;
		return _rnd.nextInt(100000 / division) <= (_dice);
	}
	
	public L1ItemInstance build(int x, int y, L1Map m){
		L1ItemInstance item = null;
		
		try{
			item = ItemTable.getInstance().createItem(_itemId);
			if(item == null || _min == 0 || _max == 0)
				return null;
			
			if(_min_enchant != _max_enchant)
				item.setEnchantLevel(_rnd.nextInt(_max_enchant + 1 - _min_enchant) + _min_enchant);
			item.setCount(_min == _max ? _min : _rnd.nextInt(_max - _min + 1) + _min);
			item.setIdentified(true);
			L1Inventory inv = L1World.getInstance().getInventory(x, y, (short)m.getId());
			inv.storeItem(item);
		}catch(Exception e){
			e.printStackTrace();
		}
		return item;
	}
	
	public L1ItemInstance build(int x, int y, L1Map m, int range){
		int cx 	= 0;
		int cy 	= 0;
		int idx	= 0;
		if(range <= 0)
			return build(x, y, m);
		
		do{
			if(idx++ > 10){
				cx = x;
				cy = y;
				break;
			}
			cx = x + (_rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range));
			cy = y + (_rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range));
		}while(!m.isPassable(cx, cy));
		return build(cx, cy, m);
	}
	
	public L1ItemInstance build(int x, int y, short mapId, int range){
		return build(x, y, L1WorldMap.getInstance().getMap(mapId), range);
	}
	
	public L1ItemInstance build(L1Object obj, int range){
		return build(obj.getX(), obj.getY(), obj.getMapId(), range);
	}
	
	public L1ItemInstance build(int left, int top, int right, int bottom, short mapId){
		int xw = (right - left) / 2;
		int yh = (bottom - top) / 2;
		return build(xw + left, yh + top, mapId, (xw + yh) / 2);
	}	
}
