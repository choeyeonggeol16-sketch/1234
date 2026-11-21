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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.ObjectWrapper;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.utils.GameUtils;


public class L1RangeKick implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1RangeKick.class.getName());

	private L1RangeKick() {}

	public static L1CommandExecutor getInstance() {
		return new L1RangeKick();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String				sname	= st.nextToken();
			if(sname == null || sname.equalsIgnoreCase(""))
				throw new Exception("");
			
			Integer reason = SC_LoginResult.banServerCodes.get(Integer.parseInt(st.nextToken()));
			if(reason == null)
				throw new Exception("");
			
			L1PcInstance target = L1World.getInstance().getPlayer(sname);
//			if (target == null) {
//				target = CharacterTable.getInstance().restoreCharacter(arg);
//			}

			if (target != null) {
				IpTable ip = IpTable.getInstance();

				Account.ban(target.getAccountName(), reason); // 계정을 BAN시킨다.
				ip.rangeBanIp(target.getNetConnection().getHostname());
				SC_SystemMessage.send(pc, target.getName() + "[" + pc.getNetConnection() + "] 를 광역추방 했습니다.");						
				L1World.getInstance().removeObject(target);
				L1PowerKick.duplicateKick(target.getNetConnection().getIp(), target, reason);
				
				/*target.getNetConnection().kick();
				target.getNetConnection().close();
				target.logout();	
				SC_Disconnect.send(target);*/
//				if (target.getOnlineStatus() == 1) {
//					SC_Disconnect.send(target);
//				}
			} else {
				final ObjectWrapper<String> wrapper = new ObjectWrapper<String>();
				wrapper.value = "";
				Selector.exec("select account_name from characters where char_name=?", new SelectorHandler(){
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						pstm.setString(1, sname);
					}

					@Override
					public void result(ResultSet rs) throws Exception {
						while(rs.next()){
							wrapper.value = rs.getString("account_name");
						}
					}
					
				});
				if(GameUtils.isNullOrEmpty(wrapper.value)){
					SC_SystemMessage.send(pc, String.format("%s은(는) 존재하지 않는 캐릭터명입니다.", sname));					
				}else{
					IpTable ip = IpTable.getInstance();
					Account.ban(wrapper.value, reason); // 계정을 BAN시킨다.
					Integer[] octet = Account.loadAccountAddress(wrapper.value);
					StringBuilder sb = new StringBuilder(256);
					sb.append(sname).append("을 광역 추방했습니다.(").append(wrapper.value);
					if(octet != null){
						sb.append(", ").append(octet[0]).append(".").append(octet[1]).append(".").append(octet[2]).append(".").append("*");
						ip.rangeBanIp(octet);
					}
					sb.append(")");
					pc.sendPackets(sb.toString());
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [정지사유번호]으로 입력해 주세요. ");
		}
	}
}
