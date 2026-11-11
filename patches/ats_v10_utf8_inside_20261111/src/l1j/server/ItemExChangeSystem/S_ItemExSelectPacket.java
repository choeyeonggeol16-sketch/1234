package l1j.server.ItemExChangeSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.Templates.L1Item;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.ServerBasePacket;

public class S_ItemExSelectPacket extends ServerBasePacket{
	private static AtomicInteger _select_id = new AtomicInteger(0);
	
	public static S_ItemExSelectPacket newInstance(){
		return new S_ItemExSelectPacket().set_id(_select_id.getAndIncrement());
	}
	
	private int _id;
	private int _key_item_id;
	private boolean _is_copy_level;
	private boolean _is_copy_bless;
	private boolean _is_copy_enchant;
	private boolean _is_copy_elemental;
	private ArrayList<Integer> _rewards;
	private ItemInstance _key;
	private ItemInstance _target;
	private S_ItemExSelectPacket(){
		writeC(Opcodes.S_OPCODE_SHOWRETRIEVELIST);
	}
	
	public S_ItemExSelectPacket set_id(int val){
		_id = val;
		return this;
	}
	public int get_id(){
		return _id;
	}
	
	public S_ItemExSelectPacket set_key_item_id(int val){
		_key_item_id = val;
		return this;
	}
	public int get_key_item_id(){
		return _key_item_id;
	}

	public S_ItemExSelectPacket set_is_copy_bless(boolean val){
		_is_copy_bless = val;
		return this;
	}
	public boolean get_is_copy_bless(){
		return _is_copy_bless;
	}
	
	/** 단계 교환 시스템 **/
	public S_ItemExSelectPacket set_is_copy_level(boolean val){
		_is_copy_level = val;
		return this;
	}
	public boolean get_is_copy_level(){
		return _is_copy_level;
	}

	public S_ItemExSelectPacket set_is_copy_enchant(boolean val){
		_is_copy_enchant = val;
		return this;
	}
	public boolean get_is_copy_enchant(){
		return _is_copy_enchant;
	}

	public S_ItemExSelectPacket set_is_copy_elemental(boolean val){
		_is_copy_elemental = val;
		return this;
	}
	public boolean get_is_copy_elemental(){
		return _is_copy_elemental;
	}

	public S_ItemExSelectPacket set_rewards(ArrayList<Integer> val){
		_rewards = val;
		return this;
	}
	
	public S_ItemExSelectPacket add_rewards(Integer val){
		_rewards.add(val);
		return this;
	}
	
	public ArrayList<Integer> get_rewards(){
		return _rewards;
	}
	
	public S_ItemExSelectPacket set_key(ItemInstance val){
		_key = val;
		return this;
	}
	public ItemInstance get_key(){
		return _key;
	}

	public S_ItemExSelectPacket set_target(ItemInstance val){
		_target = val;
		return this;
	}
	public ItemInstance get_target(){
		return _target;
	}
	
	public void do_select(L1PcInstance pc, int index){
		if(index < 0 || index >= _rewards.size()){
			pc.sendPackets("알 수 없는 선택입니다.");
			return;
		}
		
		if(_target.isEquipped()){
			pc.sendPackets("착용중인 아이템에는 사용할 수 없습니다.");
			return;
		}
		
		int reward_id = _rewards.get(index);
		ItemInstance reward = ItemTable.getInstance().createItem(reward_id);
		if(reward == null){
			pc.sendPackets("현재 변환은 운영자에 의해 일시적으루 중지되어 있는 상태입니다.");
			return;
		}
		
		
		if(get_is_copy_enchant())
			reward.setEnchantLevel(_target.getEnchantLevel());
		if(get_is_copy_elemental())
			reward.setAttrEnchantLevel(_target.getAttrEnchantLevel());
		reward.setBlessType(_target.getBlessType());
		reward.setBlessTypeValue(_target.getBlessTypeValue());
		reward.setIdentified(true);
		
		if(!pc.getInventory().consumeItem(new ItemInstance[]{_key, _target}, new int[]{1, 1})){
			pc.sendPackets("재료 아이템을 찾을 수 없습니다.");
			L1World.getInstance().removeObject(reward);
			return;
		}
		
		pc.getInventory().storeItem(reward);
		pc.sendPackets(String.format("%s이(가) 교환 되었습니다.", reward.getLogName()));
		if(get_is_copy_bless()){
			reward.setBless(_target.getBless());
			pc.getInventory().updateItem(reward, L1PcInventory.COL_BLESS);
			pc.getInventory().saveItem(reward, L1PcInventory.COL_BLESS);
		}
 
	}
	
	public S_ItemExSelectPacket serialize(L1PcInstance pc){
		writeD(pc.getId());
		if(_rewards == null || _rewards.size() <= 0){
			writeH(0x00);
			writeC(0x09);
			writeC(0x00);
			return this;
		}
		
		int size = _rewards.size();
		writeH(size);
		writeC(0x09);
		for(int i=0; i<size; ++i){
			int reward = _rewards.get(i);
			L1Item item_template = ItemTable.getInstance().getTemplate(reward);
			if(item_template == null){
				writeD(i);
				writeC(0x00);
				writeH(0x00);
				writeC(0x01);
				writeD(0x01);
				writeC(0x01);
				String s = String.format("[error item : %d]", reward);
				writeS(s);
				System.out.println(String.format("%s %s", pc.getName(), s));
				writeC(0x00);
				continue;
			}
			writeD(i);
			writeC(item_template.getType2());
			writeH(item_template.getGfxId());
			writeC(0x01);
			writeD(0x01);
			writeC(0x01);
			writeS(ItemInstance.to_simple_description(item_template.getName(), get_is_copy_bless() ? _target.getBless() : 1, get_is_copy_enchant() ? _target.getEnchantLevel() : 0, get_is_copy_elemental() ? _target.getAttrEnchantLevel() : 0, get_is_copy_level() ? 0 : 0));
			//writeC(0x00);
		} 
		return this;
	}
	
	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
	
	public void dispose(){
		if(_rewards != null){
			_rewards.clear();
			_rewards = null;
		}
		_key = null;
		_target = null;
	}

}
