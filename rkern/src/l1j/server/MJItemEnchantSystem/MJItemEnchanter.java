package l1j.server.MJItemEnchantSystem;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.packet.IPacketWriter;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

public class MJItemEnchanter {
	public static MJItemEnchanter newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.set_enchanter_id(rs.getInt("enchanter_id"))
			.set_item_id(rs.getInt("item_id"))
			.set_item_amount(rs.getInt("item_amount"))
			.set_vaporization_probability_by_millions(rs.getInt("vaporization_probability_by_millions"))
			.set_success_format_string(rs.getString("success_format_string"))
			.set_failure_format_string(rs.getString("failure_format_string"));
	}
	
	public static MJItemEnchanter newInstance(){
		return new MJItemEnchanter();
	}
	
	private int _enchanter_id;
	private int _item_id;
	private int _item_amount;
	private int _vaporization_probability_by_millions;
	private String _success_format_string;
	private String _failure_format_string;
	
	private MJItemEnchanter(){}
	
	public MJItemEnchanter set_enchanter_id(int enchanter_id){
		_enchanter_id = enchanter_id;
		return this;
	}
	
	public int get_enchanter_id(){
		return _enchanter_id;
	}
	
	public MJItemEnchanter set_item_id(int item_id){
		_item_id = item_id;
		return this;
	}
	
	public int get_item_id(){
		return _item_id;
	}
	
	public MJItemEnchanter set_item_amount(int item_amount){
		_item_amount = item_amount;
		return this;
	}
	
	public int get_item_amount(){
		return _item_amount;
	}
	
	public MJItemEnchanter set_vaporization_probability_by_millions(int vaporization_probability_by_millions){
		_vaporization_probability_by_millions = vaporization_probability_by_millions;
		return this;
	}
	
	public int get_vaporization_probability(){
		return _vaporization_probability_by_millions;
	}
	
	public MJItemEnchanter set_success_format_string(String success_format_string){
		_success_format_string = success_format_string;
		return this;
	}
	
	public String get_success_format_string(){
		return _success_format_string;
	}
	
	public MJItemEnchanter set_failure_format_string(String failure_format_string){
		_failure_format_string = failure_format_string;
		return this;
	}
	
	public String get_failure_format_string(){
		return _failure_format_string;
	}
	
    public void do_enchant(L1PcInstance pc, L1ItemInstance enchanter_item,
            L1ItemInstance enchantee_item, MJItemEnchantee enchantee) {
        if (pc == null || enchantee == null || enchanter_item == null || enchantee_item == null
                || pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
        final L1Item sourceTemplate = enchantee.to_item_temaplte();
        final L1Item rewardTemplate = enchantee.to_reward_item_template();
        if (sourceTemplate == null || rewardTemplate == null) {
            pc.sendPackets(String.format("%s은(는) 운영자에 의해 인챈트가 일시 중지되어 있는 상태입니다.", enchantee_item.getName()));
            return;
        }
        if (enchanter_item.getItemId() != get_item_id()
                || enchantee_item.getItemId() != enchantee.get_item_id()
                || enchanter_item.isEquipped() || enchantee_item.isEquipped()
                || get_item_amount() <= 0 || enchantee.get_item_amount() <= 0
                || enchantee.get_reward_item_amount() <= 0) return;
        final boolean success = pc._Blessleaf
                || MJRnd.isWinning(1000000, enchantee.get_enchant_probability_by_millions());
        final int vaporization = get_vaporization_probability();
        final boolean consumeTarget = success || vaporization >= 1000000
                || (vaporization > 0 && MJRnd.isWinning(1000000, vaporization));
        final String itemName = enchantee_item.getName();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("enchant:" + pc.getId()))) {
            if (!tx.debit(enchanter_item, get_item_amount())
                    || (consumeTarget && !tx.debit(enchantee_item, enchantee.get_item_amount()))) return;
            if (success && tx.grant(enchantee.get_reward_item_id(),
                    enchantee.get_reward_item_amount()) == null) return;
            tx.commit(() -> {
                if (success) {
                    // Effects, guarantee consumption and broadcast describe the
                    // durable outcome, never an admitted but uncommitted write.
                    pc._Blessleaf = false;
                    if (get_success_format_string() != null) {
                        String message = String.format(get_success_format_string(), itemName);
                        if (enchantee.is_broadcast()) {
                            String broadcast = String.format("어느 아덴 용사 님께서 %s", message);
                            SC_PacketBox.broadcastGreen(broadcast);
                            SC_SystemMessage.broadcast(broadcast);
                        }
                        pc.sendPackets(message);
                    }
                    pc.send_effect(enchantee.get_success_effect_id());
                } else {
                    if (get_failure_format_string() != null)
                        pc.sendPackets(String.format(get_failure_format_string(), itemName));
                    pc.send_effect(enchantee.get_failure_effect_id());
                }
            });
        } catch (RuntimeException failure) {
            pc.sendPackets("아이템 변경을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            java.util.logging.Logger.getLogger(MJItemEnchanter.class.getName())
                    .log(java.util.logging.Level.WARNING, "Enchant preparation failed", failure);
        }
    }
}
