package l1j.server.ItemInvisibleOptions;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.configure.Config;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class ItemInvisibleOptionsInfo {
	static ItemInvisibleOptionsInfo newInstance(ResultSet rs) throws SQLException {
		ItemInvisibleOptionsInfo pInfo = newInstance();
		pInfo._item_id = rs.getInt("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._pc_add_dmg = rs.getString("pc_add_dmg");
		pInfo._pc_add_hit = rs.getString("pc_add_hit");
		pInfo._pc_add_reduc = rs.getString("pc_add_reduc");
		pInfo._pc_add_magic_dmg = rs.getString("pc_add_magic_dmg");
		pInfo._npc_add_dmg = rs.getString("npc_add_dmg");
		pInfo._npc_add_hit = rs.getString("npc_add_hit");
		pInfo._npc_add_reduc = rs.getString("npc_add_reduc");
		pInfo._npc_add_magic_dmg = rs.getString("npc_add_magic_dmg");
		return pInfo;
	}

	private static ItemInvisibleOptionsInfo newInstance() {
		return new ItemInvisibleOptionsInfo();
	}

	private int _item_id;
	private String _item_name;
	private String _pc_add_dmg;
	private String _pc_add_hit;
	private String _pc_add_reduc;
	private String _pc_add_magic_dmg;
	private String _npc_add_dmg;
	private String _npc_add_hit;
	private String _npc_add_reduc;
	private String _npc_add_magic_dmg;

	private ItemInvisibleOptionsInfo() {
	}

	public int get_item_id() {
		return _item_id;
	}

	public String get_item_name() {
		return _item_name;
	}

	public String get_pc_dmg() {
		return _pc_add_dmg;
	}

	public String get_pc_hit() {
		return _pc_add_hit;
	}

	public String get_pc_reduc() {
		return _pc_add_reduc;
	}

	public String get_pc_magic_dmg() {
		return _pc_add_magic_dmg;
	}

	public String get_npc_dmg() {
		return _npc_add_dmg;
	}

	public String get_npc_hit() {
		return _npc_add_hit;
	}

	public String get_npc_reduc() {
		return _npc_add_reduc;
	}

	public String get_npc_magic_dmg() {
		return _npc_add_magic_dmg;
	}

	void print() {
	}

	public int add_pc_dmg(L1PcInstance pc, ItemInstance item) {
		int dmg = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_pc_dmg(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			dmg = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			dmg = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(
						new S_SystemMessage(String.format("%s PC -> PC 아이템의 추가 대미지 : %d", item.getLogName(), dmg)));
			}
		}
		return dmg;
	}

	public int add_pc_hit(L1PcInstance pc, ItemInstance item) {
		int hit = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_pc_hit(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			hit = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			hit = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(
						new S_SystemMessage(String.format("%s PC -> PC 아이템의 추가 명중 : %d", item.getLogName(), hit)));
			}
		}

		return hit;
	}

	public int add_pc_reduc(L1PcInstance pc, ItemInstance item) {
		int reduc = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_pc_reduc(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			reduc = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			reduc = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(new S_SystemMessage(
						String.format("%s PC -> PC 아이템의 추가 대미지 감소 : %d", item.getLogName(), reduc)));
			}
		}

		return reduc;
	}

	public int add_pc_magic_dmg(L1PcInstance pc, ItemInstance item) {
		int m_dmg = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_pc_magic_dmg(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			m_dmg = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			m_dmg = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(
						new S_SystemMessage(String.format("%s PC -> PC 아이템의 추가 마법대미지 : %d", item.getLogName(), m_dmg)));
			}
		}

		return m_dmg;
	}

	public int add_npc_dmg(L1PcInstance pc, ItemInstance item) {
		int dmg = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_npc_dmg(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			dmg = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			dmg = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(
						new S_SystemMessage(String.format("%s PC -> NPC 아이템의 추가 대미지 : %d", item.getLogName(), dmg)));
			}
		}

		return dmg;
	}

	public int add_npc_hit(L1PcInstance pc, ItemInstance item) {
		int hit = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_npc_hit(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			hit = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			hit = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(
						new S_SystemMessage(String.format("%s PC -> NPC 아이템의 추가 명중 : %d", item.getLogName(), hit)));
			}
		}

		return hit;
	}

	public int add_npc_reduc(L1PcInstance pc, ItemInstance item) {
		int reduc = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_npc_reduc(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			reduc = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			reduc = Integer.parseInt(bonus_info[item.getEnchantLevel()]);

		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(new S_SystemMessage(
						String.format("%s PC -> NPC 아이템의 추가 대미지 감소 : %d", item.getLogName(), reduc)));
			}
		}

		return reduc;
	}

	public int add_npc_magic_dmg(L1PcInstance pc, ItemInstance item) {
		int m_dmg = 0;
		String[] bonus_info = (String[]) MJArrangeParser
				.parsing(get_npc_magic_dmg(), ",", MJArrangeParseeFactory.createStringArrange()).result();

		if (item.getEnchantLevel() >= bonus_info.length)
			m_dmg = Integer.parseInt(bonus_info[bonus_info.length - 1]);
		else
			m_dmg = Integer.parseInt(bonus_info[item.getEnchantLevel()]);
		if (Config.ServerInfo.ITEMOPTIONMSG) {
			if (pc.isGm()) {
				pc.sendPackets(new S_SystemMessage(
						String.format("%s PC -> NPC 아이템의 추가 마법대미지 : %d", item.getLogName(), m_dmg)));
			}
		}

		return m_dmg;
	}

}
