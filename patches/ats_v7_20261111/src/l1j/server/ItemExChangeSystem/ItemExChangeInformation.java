package l1j.server.ItemExChangeSystem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class ItemExChangeInformation {
	public static ItemExChangeInformation newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.set_key_item_id(rs.getInt("key_item_id"))
			.set_is_copy_bless(rs.getBoolean("is_copy_bless"))
			.set_is_copy_enchant(rs.getBoolean("is_copy_enchant"))
			.set_is_copy_elemental(rs.getBoolean("is_copy_elemental"))
			.set_is_copy_level(rs.getBoolean("is_copy_level"));
	}

	public static ItemExChangeInformation newInstance() {
		return new ItemExChangeInformation();
	}

	private int _key_item_id;
	private boolean _is_copy_level;
	private boolean _is_copy_bless;
	private boolean _is_copy_enchant;
	private boolean _is_copy_elemental;
	private ArrayList<Integer> _rewards;

	private ItemExChangeInformation() {
		_rewards = new ArrayList<Integer>(8);
	}

	public ItemExChangeInformation set_key_item_id(int val) {
		_key_item_id = val;
		return this;
	}

	public int get_key_item_id() {
		return _key_item_id;
	}

	public ItemExChangeInformation set_is_copy_bless(boolean val) {
		_is_copy_bless = val;
		return this;
	}

	public boolean get_is_copy_bless() {
		return _is_copy_bless;
	}

	public ItemExChangeInformation set_is_copy_enchant(boolean val) {
		_is_copy_enchant = val;
		return this;
	}

	public boolean get_is_copy_enchant() {
		return _is_copy_enchant;
	}

	public ItemExChangeInformation set_is_copy_elemental(boolean val) {
		_is_copy_elemental = val;
		return this;
	}

	public boolean get_is_copy_elemental() {
		return _is_copy_elemental;
	}

	/** 단계 교환 시스템 **/
	public ItemExChangeInformation set_is_copy_level(boolean val) {
		_is_copy_level = val;
		return this;
	}

	public boolean get_is_copy_level() {
		return _is_copy_level;
	}

	public ItemExChangeInformation set_rewards(ArrayList<Integer> val) {
		_rewards = val;
		return this;
	}

	public ItemExChangeInformation add_rewards(Integer val) {
		_rewards.add(val);
		return this;
	}

	public ArrayList<Integer> get_rewards() {
		return _rewards;
	}

	public ItemExChangeInformation load_rewards() {
		Selector.exec("select key_item_id, reward_item_id from tb_item_exchange_rewards where key_item_id=?",
				new SelectorHandler() {
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						pstm.setInt(1, get_key_item_id());
					}

					@Override
					public void result(ResultSet rs) throws Exception {
						while (rs.next())
							add_rewards(rs.getInt("reward_item_id"));
					}
				});
		return this;
	}

	public void use_item(L1PcInstance pc, ItemInstance key, ItemInstance target) {
		if (target.isEquipped()) {
			pc.sendPackets("착용중인 아이템에는 사용할 수 없습니다.");
			return;
		}
		if (pc.getTradeReady()) {
			pc.sendPackets(new S_SystemMessage("교환중엔 불가능한 행동입니다."));
			return;
		}
		/** 시간제아이템 창고불가 */
		if (target.getEndTime() != null){
			pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 사용할 수 없습니다."));
			return;
		}
		/** 시간제아이템 창고불가 */
		
		DollInstance doll = pc.getMagicDoll();
		if ((doll != null) && (target.getId() == doll.getItemObjId())) {
			pc.sendPackets(new S_SystemMessage("\\fW소환된 인형은 사용할 수 없습니다."));
			return;
		}

		int size = _rewards.size();
		int t_id = target.getItemId();
		boolean is_possible_target = false;
		ArrayList<Integer> rewards = new ArrayList<Integer>(size - 1);
		for (int i = size - 1; i >= 0; --i) {
			int r_id = _rewards.get(i);
			if (r_id == t_id) {
				is_possible_target = true;
			} else {
				rewards.add(r_id);
			}
		}

		if (!is_possible_target) {
			if (rewards != null)
				rewards.clear();
			pc.sendPackets("사용할 수 없는 대상입니다.");
			return;
		}

		pc.on_select_item(S_ItemExSelectPacket.newInstance().set_key_item_id(key.getItemId())
				.set_is_copy_bless(get_is_copy_bless())
				.set_is_copy_enchant(get_is_copy_enchant())
				.set_is_copy_elemental(get_is_copy_elemental())
				.set_is_copy_level(get_is_copy_level())
				.set_key(key)
				.set_target(target).set_rewards(rewards).serialize(pc));
	}
}
