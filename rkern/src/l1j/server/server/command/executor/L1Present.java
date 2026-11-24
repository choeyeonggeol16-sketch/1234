package l1j.server.server.command.executor;

import l1j.server.server.datatables.CObjectDB;
import java.util.StringTokenizer;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.logging.Logger;

import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

public class L1Present implements L1CommandExecutor {

	private L1Present() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Present();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			String nameid = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(name);

			if (target == null) {
				target = CharacterTable.getInstance().restoreCharacter(name);

				if (target == null) {
					SC_SystemMessage.send(pc, "존재하지 않는 캐릭터입니다.");
					return;
				}

				CharacterTable.getInstance().restoreInventory(target);
			}

			int count = 1;
			if (st.hasMoreTokens()) {
				count = Integer.parseInt(st.nextToken());
			}
			int enchant = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			int itemid = 0;
			int Attrenchant = 0;
			if (st.hasMoreTokens()) {
				Attrenchant = Integer.parseInt(st.nextToken());
			}
			int bless = 0;
			if (st.hasMoreTokens()) {
				bless = Integer.parseInt(st.nextToken());
			}
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = CObjectDB.LoadItem().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					SC_SystemMessage.send(pc, "해당 아이템이 발견되지 않았습니다.");
					return;
				}
			}
			L1Item temp = CObjectDB.GetItem(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					L1ItemInstance item = CObjectDB.CreateItem(itemid);
					item.setEnchantLevel(0);
					item.setCount(count);
					if (target.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
						target.getInventory().storeItem(item);
					//	target.sendPackets(new S_GMHtml("선물: "+target.getName()+"" , ""+item.getLogName()+" 를(을) 획득함 인벤토리 확인."));
						target.sendPackets("\\f:선물: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요");
						SC_PacketBox.send(target, SC_PacketBox.GREEN_MESSAGE, "\\f=메티스로부터 "+target.getName()+"님께 선물이 도착했습니다! ▶▶ "+ temp.getNameId()+"("+count+")");
						SC_SkillSound.send(target, pc.getId(), 4856);
						SC_SystemMessage.send(pc, "\\aD["+target.getName()+"] "+ item.getLogName() + " (ID:" + itemid + ") 보냄");
					}
				} else {
					L1ItemInstance item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = CObjectDB.CreateItem(itemid);
						item.setEnchantLevel(enchant);
						item.setAttrEnchantLevel(Attrenchant);
						if (bless == 129) {
							item.setBless(bless);
						}
						//if (target.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
						target.getInventory().storeItem(item);
						if (bless == 129) {
							item.setBless(bless);
							target.getInventory().updateItem(item, L1PcInventory.COL_BLESS);
							target.getInventory().saveItem(item, L1PcInventory.COL_BLESS);
						}
						//	} else {
						//		break;
						//	}
					}
					if (createCount > 0) {
						//target.sendPackets(new S_GMHtml("선물:"+target.getName()+"" , ""+item.getLogName()+"("+count+") 를(을) 획득함.　인벤토리 확인하세요"));
						SC_SkillSound.send(target, pc.getId(), 4856);
						SC_PacketBox.send(target, SC_PacketBox.GREEN_MESSAGE, "\\f=메티스로부터 "+target.getName()+"님께 선물이 도착했습니다! ▶▶ "+ temp.getNameId()+"("+count+")");
						
//						target.sendPackets(new SC_SystemMessage("\\aD[선물] +" + enchant + " " + item.getLogName() + " (" + count + "개) 획득"));
						//pc.sendPackets(new SC_SystemMessage("\\aD["+target.getName()+"] +" + enchant + " "+ temp.getNameId()+"(ID:" + itemid + ") "+ count +"개 보냄", true));
						SC_SystemMessage.send(pc, "\\f:케릭명: \\aH"+target.getName()+"\\f:님에게 아래 아이템을 보냈습니다");
						SC_SystemMessage.send(pc, "\\f:아이템: [+"+ enchant +" "+ item.getLogName()+"] (ID:" + itemid + ") ("+count+"개) ");
					}
				}
			} else {
				SC_SystemMessage.send(pc, "지정 ID의 아이템은 존재하지 않습니다.");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".[선물] [캐릭] [아이템ID] [갯수] [인챈] [속성] [봉인129]");
		}
	}
}