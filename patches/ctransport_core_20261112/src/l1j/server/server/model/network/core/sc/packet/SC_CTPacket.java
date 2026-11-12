package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l1j.server.MJCTSystem.MJCTCharInfo;
import l1j.server.MJCTSystem.MJCTItem;
import l1j.server.MJCTSystem.MJCTSpell;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_CTPacket {

	private static final int MODE_CHAR_INFO  = 1;
	private static final int MODE_SPELL_LIST = 2;
	private static final int MODE_INV_LIST   = 3;

	

	// 캐릭터 정보 필드
	
	

	// 스펠 목록 노드 DTO
	private static class SpellNode {
		final int xicon;
		final String name;

		SpellNode(int xicon, String name) {
			this.xicon = xicon;
			this.name = name != null ? name : "";
		}
	}

	
	
	

	// 인벤토리 목록 노드 DTO
	private static class InvItemNode {
		final int id;
		final int type2;
		final int gfxId;
		final int bless;
		final int count;
		final int iden;
		final String desc;

		InvItemNode(int id, int type2, int gfxId, int bless, int count, int iden, String desc) {
			this.id = id;
			this.type2 = type2;
			this.gfxId = gfxId;
			this.bless = bless;
			this.count = count;
			this.iden = iden;
			this.desc = desc != null ? desc : "";
		}
	}

	

	/**
	 * 캐릭터 정보 패킷 생성 정적 팩토리
	 */
	public static Data getCharacterInfo(MJCTCharInfo info) {
		String name = (info != null && info.name != null) ? info.name : "";
		String infoStr = (info != null) ? info.toString() : "";
		return new Data(MODE_CHAR_INFO, name, infoStr, null, 0, 0, null);
	}

	/**
	 * 스펠 목록 패킷 생성 정적 팩토리
	 */
	public static Data getSpellList(ArrayDeque<MJCTSpell> spQ) {
		List<SpellNode> spells = new ArrayList<>();
		if (spQ != null) {
			synchronized (spQ) {
				for (MJCTSpell sp : spQ) {
					if (sp != null) {
						spells.add(new SpellNode(sp.xicon, sp.name));
					}
				}
			}
		}

		int dummyId = 0;
		int dummyType2 = 0;
		L1ItemInstance item = ItemTable.getInstance().createItem(40005);
		if (item != null) {
			dummyId = item.getId();
			if (item.getItem() != null) {
				dummyType2 = item.getItem().getType2();
			}
		}

		return new Data(MODE_SPELL_LIST, "", "", spells, dummyId, dummyType2, null);
	}

	/**
	 * 인벤토리 목록 패킷 생성 정적 팩토리
	 */
	public static Data getInvList(ArrayDeque<MJCTItem> itemQ) {
		List<InvItemNode> items = new ArrayList<>();
		if (itemQ != null) {
			synchronized (itemQ) {
				for (MJCTItem item : itemQ) {
					if (item == null || item.item == null) {
						continue;
					}
					items.add(new InvItemNode(
						item.id,
						item.item.getType2(),
						item.item.getGfxId(),
						item.bless,
						item.count,
						item.iden,
						item.toString()
					));
				}
			}
		}

		return new Data(MODE_INV_LIST, "", "", null, 0, 0, items);
	}

	

	

	

    public static final class Data {
private final int _mode;

private final String _charName;

private final String _charInfoStr;

private final List<SpellNode> _spellList;

private final int _dummyItemId;

private final int _dummyItemType2;

private final List<InvItemNode> _invList;

private Data(int mode, String charName, String charInfoStr,
			List<SpellNode> spellList, int dummyItemId, int dummyItemType2,
			List<InvItemNode> invList) {
		_mode = mode;
		_charName = charName;
		_charInfoStr = charInfoStr;
		_spellList = spellList != null ? spellList : Collections.<SpellNode>emptyList();
		_dummyItemId = dummyItemId;
		_dummyItemType2 = dummyItemType2;
		_invList = invList != null ? invList : Collections.<InvItemNode>emptyList();
	}

public boolean send(CIOClient client) {
		if (client == null || !client.isConnected()) return false;
		final CUserSocket conn = client.getConnection();
		if (conn == null || !conn.isClientConnected()) return false;
		long capacity;
		switch (_mode) {
		case MODE_CHAR_INFO:
			capacity = 2L + 1 + 4 + 4;
			capacity += ((long) _charName.length() + "캐릭터 정보".length()
					+ (_charInfoStr == null ? 0 : _charInfoStr.length())) * 2;
			break;
		case MODE_SPELL_LIST:
			capacity = 2L + 1 + 4 + 2 + 1 + 10;
			for (int i = 0, n = _spellList.size(); i < n; ++i)
				capacity += 15L + ((long) _spellList.get(i).name.length() << 1);
			break;
		case MODE_INV_LIST:
			capacity = 2L + 1 + 4 + 2 + 1 + 10;
			for (int i = 0, n = _invList.size(); i < n; ++i)
				capacity += 15L + ((long) _invList.get(i).desc.length() << 1);
			break;
		default:
			return false;
		}
		if (capacity > Integer.MAX_VALUE) return false;
		final CIOFrame frame = conn.beginDirectFrame((int) capacity);
		if (frame == null) return false;
		boolean committed = false;
		try {
			writeImpl(client, frame.buffer());
			committed = conn.commitDirectFrame(frame);
			return committed;
		} finally {
			if (!committed) conn.abortDirectFrame(frame);
		}
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		switch (_mode) {
		case MODE_CHAR_INFO:
			buffer.writeC(Opcodes.S_BOARD_READ);
			buffer.writeD(1);
			buffer.writeS(_charName);
			buffer.writeS("캐릭터 정보");
			buffer.writeS("");
			buffer.writeS(_charInfoStr);
			break;

		case MODE_SPELL_LIST:
			buffer.writeC(Opcodes.S_RETRIEVE_LIST);
			buffer.writeD(1);
			buffer.writeH(_spellList.size());
			buffer.writeC(0x03);
			for (SpellNode sp : _spellList) {
				buffer.writeD(_dummyItemId);
				buffer.writeC(_dummyItemType2);
				buffer.writeH(sp.xicon);
				buffer.writeC(1);
				buffer.writeD(1);
				buffer.writeC(1);
				buffer.writeS(sp.name);
				buffer.writeC(0);
			}
			buffer.writeD(100);
			buffer.writeD(0x0);
			buffer.writeH(0x00);
			break;

		case MODE_INV_LIST:
			buffer.writeC(Opcodes.S_RETRIEVE_LIST);
			buffer.writeD(1);
			buffer.writeH(_invList.size());
			buffer.writeC(0x03);
			for (InvItemNode item : _invList) {
				buffer.writeD(item.id);
				buffer.writeC(item.type2);
				buffer.writeH(item.gfxId);
				buffer.writeC(item.bless);
				buffer.writeD(item.count);
				buffer.writeC(item.iden);
				buffer.writeS(item.desc);
				buffer.writeC(0);
			}
			buffer.writeD(100);
			buffer.writeD(0x0);
			buffer.writeH(0x00);
			break;

		default:
			break;
		}
	}
    }
}