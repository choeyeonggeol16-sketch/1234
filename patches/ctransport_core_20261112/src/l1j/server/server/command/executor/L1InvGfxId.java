/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.command.executor;

import java.util.StringTokenizer;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import LIN.SC_WAREHOUSE_ITEM_LIST_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1InvGfxId implements L1CommandExecutor {

	private L1InvGfxId() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1InvGfxId();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
	    try {
	        StringTokenizer st = new StringTokenizer(arg);
	        int gfxid = Integer.parseInt(st.nextToken(), 10);
	        int count = Integer.parseInt(st.nextToken(), 10);
	        if(count <= 200){
	            Proto.send(pc, LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.newDisplayInstance(pc.getId(), 0, gfxid, count, true));
	        }else{
	            int page = (count / 200) + ((count % 200) > 0 ? 1 : 0);
	            for(int i=0; i<page; ++i){
	                int real_index = i * 200;
	                Proto.send(pc, LIN.SC_WAREHOUSE_ITEM_LIST_NOTI.newDisplayInstance(pc.getId(), real_index, gfxid + real_index, 200, (i + 1 == page)));
	            }
	        }

	        SC_SystemMessage.send(pc, "\\aHInvGfxId " + gfxid + "~"
	                + String.valueOf(count) + " 생성 되었습니다.");
	    } catch (Exception exception) {
	        int count = 0;
	        for (L1ItemInstance item : pc.getInventory().getItems()) {
	            if (item.getItemId() == 40005) {
	                pc.getInventory().deleteItem(item);
	                count++;
	            }
	        }
	        if (count > 0) {
	            SC_SystemMessage.send(pc, "\\aAInvGfxId 확인용 아이템("
	                    + count + ")이 삭제되었습니다.");
	        } else {
	            SC_SystemMessage.send(pc, "\\aH" + cmdName
	                    + " [InvGfxId] [갯수]로 입력해 주세요.");
	        }
	    }
	}
}
