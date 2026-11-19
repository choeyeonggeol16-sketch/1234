package l1j.server.MJCTSystem;

import l1j.server.server.templates.L1Item;
/** 
 * MJCTCharInfo
 * MJSoft Character TradeSystem - Item
 * made by mjsoft, 2016.
 **/
public class MJCTItem {
	public int 		id;
	public int 		bless;
	public int 		count;
	public int 		iden;
	public int		enchant;
	public int		attr;
	public L1Item	item;
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(128);
		if(item.getType2() == 1 || item.getType2() == 2)
			sb.append(getEnchantString());
		sb.append(item.getNameId());
		if(count > 1)
			sb.append(" (").append(count).append("");
		return sb.toString();
	}
	
	private String getEnchantString(){
		StringBuilder sb = new StringBuilder(64);
		if(item.getType2() == 1){
			switch(attr){
			case 1:		sb.append("$6115"); 	break;
			case 2: 	sb.append("$6116"); 	break;
			case 3: 	sb.append("$6117"); 	break;
			case 4: 	sb.append("$14361"); 	break;
			case 5: 	sb.append("$14365"); 	break;
			case 6:	 	sb.append("$6118"); 	break;
			case 7: 	sb.append("$6119"); 	break;
			case 8: 	sb.append("$6120"); 	break;
			case 9: 	sb.append("$14362"); 	break;
			case 10: 	sb.append("$14366"); 	break;
			case 11: 	sb.append("$6121"); 	break;
			case 12: 	sb.append("$6122"); 	break;
			case 13: 	sb.append("$6123"); 	break;
			case 14: 	sb.append("$14363"); 	break;
			case 15: 	sb.append("$14367"); 	break;
			case 16: 	sb.append("$6124"); 	break;
			case 17: 	sb.append("$6125"); 	break;
			case 18: 	sb.append("$6126"); 	break;
			case 19: 	sb.append("$14364"); 	break;
			case 20: 	sb.append("$14368");	break;
			default: break;
			}
		}
		
		if(enchant >= 0)
			sb.append("+").append(enchant);
		else 
			sb.append(String.valueOf(enchant));
		sb.append(" ");
		
		return sb.toString();
	}
}
