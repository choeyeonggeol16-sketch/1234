package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1CheckCharacter implements L1CommandExecutor {
	private L1CheckCharacter() {}
	public static L1CommandExecutor getInstance() {	return new L1CheckCharacter();}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		Connection c = null;
		PreparedStatement p = null;
		PreparedStatement p1 = null;
		ResultSet r = null;
		ResultSet r1 = null;
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String charname = st.nextToken();
			String type = st.nextToken();
			
			c = DatabaseFactory.getInstance().getConnection();
			
//			if (charname.equalsIgnoreCase("\uCF54\uBE0C")) {
//				pc.sendPackets(new SC_SystemMessage("\\fY검사할 수 없는 운영자입니다."));
//				
//				return;
//			}
			
			String itemname;
			int searchCount = 0;
			if (type.equalsIgnoreCase("인벤")){	
				try {
					// 캐릭 오브젝트 ID 검색 1=objid 2=charname
					p = c.prepareStatement("SELECT objid, char_name FROM characters WHERE char_name = ?");
					p.setString(1, charname);
					r = p.executeQuery();
					while(r.next()){
						SC_SystemMessage.send(pc, "\\fW** 검사: "+type+" 캐릭: " + charname + " **");
						L1PcInstance target = L1World.getInstance().getPlayer(charname);			
						if (target != null) target.saveInventory();						
						// 캐릭 아이템 검색 1-itemid 2-인챈 3-착용 4-수량 5-이름 6-축복 7-속성
						p1 = c.prepareStatement("SELECT item_id,enchantlvl,is_equipped,count,item_name,bless,attr_enchantlvl " +
								"FROM character_items WHERE char_id = ? ORDER BY 3 DESC,2 DESC, 1 ASC");
						p1.setInt(1, r.getInt(1));
						r1 = p1.executeQuery();				
						while(r1.next()){
							itemname = getInvenItem(r1.getInt(1),r1.getInt(2),r1.getInt(3),r1.getInt(4),r1.getString(5),r1.getInt(6),r1.getInt(7));
							SC_SystemMessage.send(pc, "\\fU"+ ++searchCount +". " + itemname);
							itemname = "";
						}
						SC_SystemMessage.send(pc, "\\fW** 총 "+searchCount+"건의 아이템이 검색 되었습니다 **");
					}					
				} catch (Exception e) {
					SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
				}
			} else if (type.equalsIgnoreCase("창고")){
				try {
					p = c.prepareStatement("SELECT account_name, char_name FROM characters WHERE char_name = ?");
					p.setString(1, charname);
					r = p.executeQuery();
					while (r.next()){
						SC_SystemMessage.send(pc, "\\fW** 검사: "+type+" 캐릭: " + charname + "(" + r.getString(1) + ") **");
						//캐릭 창고 검색 1-itemid 2-인챈 3-수량 4-이름 5-축복 6-속성
						p1 = c.prepareStatement("SELECT item_id,enchantlvl,count,item_name,bless,attr_enchantlvl FROM character_warehouse " +
								"WHERE account_name = ? ORDER BY 2 DESC, 1 ASC");
						p1.setString(1, r.getString(1));
						r1 = p1.executeQuery();
						while (r1.next()){
							itemname = getInvenItem(r1.getInt(1),r1.getInt(2),0,r1.getInt(3),r1.getString(4),r1.getInt(5),r1.getInt(6));
							SC_SystemMessage.send(pc, "\\fU"+ ++searchCount +". " + itemname);
							itemname = "";
						}
						SC_SystemMessage.send(pc, "\\fW** 총 "+searchCount+"건의 아이템이 검색 되었습니다 **");
					}
				} catch (Exception e) {
					SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
				}
			} else if (type.equalsIgnoreCase("장비")){	
				try {
					// 캐릭 오브젝트 ID 검색 1=objid 2=charname
					p = c.prepareStatement("SELECT objid, char_name FROM characters WHERE char_name = ?");
					p.setString(1, charname);
					r = p.executeQuery();
					while(r.next()){
						SC_SystemMessage.send(pc, "\\fW** 검사: "+type+" 캐릭: " + charname + " **");
						L1PcInstance target = L1World.getInstance().getPlayer(charname);			
						if (target != null) target.saveInventory();						
						// 캐릭 아이템 검색 1-itemid 2-인챈 3-착용 4-수량 5-이름 6-축복 7-속성
						p1 = c.prepareStatement("SELECT item_id,enchantlvl,is_equipped,count,item_name,bless,attr_enchantlvl " +
								"FROM character_items WHERE char_id = ? ORDER BY 3 DESC,2 DESC, 1 ASC");
						p1.setInt(1, r.getInt(1));
						r1 = p1.executeQuery();				
						while(r1.next()){
							L1ItemInstance item = ItemTable.getInstance().createItem(r1.getInt(1));							
							if (item.getItem().getType2() == 1 || item.getItem().getType2() == 2) { // 무기 방어구만 표시
								itemname = getInvenItem(r1.getInt(1),r1.getInt(2),r1.getInt(3),r1.getInt(4),r1.getString(5),r1.getInt(6),r1.getInt(7));
								SC_SystemMessage.send(pc, "\\fU"+ ++searchCount +". " + itemname);
								itemname = "";
							}
						}
						SC_SystemMessage.send(pc, "\\fW** 총 "+searchCount+"건의 아이템이 검색 되었습니다 **");
					}					
				} catch (Exception e) {
					SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
				}						
			} else if (type.equalsIgnoreCase("요정창고")){				
				try {
					p = c.prepareStatement("SELECT account_name, char_name FROM characters WHERE char_name = ?");
					p.setString(1, charname);
					r = p.executeQuery();
					while (r.next()){
						SC_SystemMessage.send(pc, "\\fW** 검사: "+type+" 캐릭: " + charname + "(" + r.getString(1) + ") **");
						//캐릭 요정창고 검색 1-itemid 2-인챈 3-수량 4-이름 5-축복 6-속성
						p1 = c.prepareStatement("SELECT item_id,enchantlvl,count,item_name,bless,attr_enchantlvl FROM character_elf_warehouse " +
								"WHERE account_name = ? ORDER BY 2 DESC, 1 ASC");
						p1.setString(1, r.getString(1));
						r1 = p1.executeQuery();
						while (r1.next()){
							itemname = getInvenItem(r1.getInt(1),r1.getInt(2),0,r1.getInt(3),r1.getString(4),r1.getInt(5),r1.getInt(6));
							SC_SystemMessage.send(pc, "\\fU"+ ++searchCount +". " + itemname);
							itemname = "";
						}
						SC_SystemMessage.send(pc, "\\fW** 총 "+searchCount+"건의 아이템이 검색 되었습니다 **");
					}
				} catch (Exception e) {
					SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
				}
			}			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".검사 [캐릭명] [장비,인벤,창고,요정창고]");
		} finally {
			SQLUtil.close(r1);SQLUtil.close(p1);
			SQLUtil.close(r);SQLUtil.close(p);
			SQLUtil.close(c);
		}
	}
	private String getInvenItem(int itemid, int enchant, int equip, int count, String itemname, int bless, int attr){
		StringBuilder name = new StringBuilder();
		// +9 축복받은 실프의 흑왕도 (착용)		
		// 인챈
		if (enchant > 0) {
			name.append("+" + enchant + " ");
		} else if (enchant == 0) {
			name.append("");
		} else if (enchant < 0) {
			name.append(String.valueOf(enchant) + " ");
		}
		// 축복
		switch (bless) {
		case 0:name.append("축복받은 ");break;
		case 1:name.append("");break;		
		case 2:name.append("저주받은 ");break;
		default: break;
		}
		// 속성
		switch(attr){
		case 1:
			name.append("화령:1단 ");
			break;
		case 2:
			name.append("화령:2단 ");
			break;
		case 3:
			name.append("화령:3단 ");
			break;
		case 4:
			name.append("화령:4단 ");
			break;
		case 5:
			name.append("화령:5단 ");
			break;
		case 6:
			name.append("수령:1단 ");
			break;
		case 7:
			name.append("수령:2단 ");
			break;
		case 8:
			name.append("수령:3단 ");
			break;
		case 9:
			name.append("수령:4단 ");
			break;
		case 10:
			name.append("수령:5단 ");
			break;
		case 11:
			name.append("풍령:1단 ");
			break;
		case 12:
			name.append("풍령:2단 ");
			break;
		case 13:
			name.append("풍령:3단 ");
			break;
		case 14:
			name.append("풍령:4단 ");
			break;
		case 15:
			name.append("풍령:5단 ");
			break;
		case 16:
			name.append("지령:1단 ");
			break;
		case 17:
			name.append("지령:2단 ");
			break;
		case 18:
			name.append("지령:3단 ");
			break;
		case 19:
			name.append("지령:4단 ");
			break;
		case 20:
			name.append("지령:5단 ");
			break;
		default:
			break;
		}
		// 이름
		name.append(itemname + " ");
		// 착용여부
		if (equip == 1){
			name.append("(착용)");
		}
		switch (bless) {
		case 129:name.append("[봉인]");break;
		default: break;
		}
		// 카운트
		if (count > 1){
			name.append("(" + count + ")");
		}
		return name.toString();
	}
}
