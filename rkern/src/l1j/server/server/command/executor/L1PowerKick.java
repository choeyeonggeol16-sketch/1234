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
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.rank.loader.RankUserLoader;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1PowerKick implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1PowerKick.class.getName());

	private L1PowerKick() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1PowerKick();
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
			IpTable iptable = IpTable.getInstance();
			if (target != null) {
				if (target.isGm()) {
					SC_SystemMessage.send(pc, "운영자는 벤처리 되지않습니다.");
					return;
				}
				Account.ban(target.getAccountName(), reason); // 계정을 BAN시킨다.
				
				if(!iptable.isBannedIp(target.getNetConnection().getIp()))
					iptable.banIp(target.getNetConnection().getIp()); // BAN 리스트에IP를 추가한다.
				else
					SC_SystemMessage.send(pc, " 이미등록된 아이피 : " + target.getNetConnection().getIp());
				SC_SystemMessage.send(pc, (new StringBuilder()).append(target.getName()).append(" 를 영구 추방 했습니다. ").toString());
				
				duplicateKick(target.getNetConnection().getIp(), target, reason);
			} else {
				String name = loadCharacter(sname);
				if (name != null) {
					Account.ban(name, reason);
					String nc = Account.checkIP(name);
					if (nc != null){
						duplicateKick(nc, null, reason);
						if(!iptable.isBannedIp(nc))
							iptable.banIp(nc);
						else
							SC_SystemMessage.send(pc, name + " 이미등록된 아이피 : " + nc);
					}
					
					SC_SystemMessage.send(pc, name + " 를 계정압류 하였습니다.");
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [압류사유번호] 으로 입력해 주세요. ");
			SC_SystemMessage.send(pc, "사유1: 불법프로그램 사용/ 사유2: 공공의 안녕, 질서/ 사유3: 상업적인 목적의 광고");
		}
	}

	public static void duplicateKick(String addr, L1PcInstance target, int reason){
		try{
//			Collection<GameClient> cList = MJNSHandler.getClients();
//			if(target != null){
//				MJRankUserLoader.getInstance().banUser(target);
//				target.getNetConnection().kick();
//				target.logout();	
//			}
//			if(cList != null){
//				for(GameClient clnt : cList){
//					if(clnt == null)
//						continue;
//					if(clnt.getIp().equalsIgnoreCase(addr)){
//						L1PcInstance tt = clnt.getActiveChar();
//						if(tt != null){
//							MJRankUserLoader.getInstance().banUser(tt);
//						}
//						if(clnt.getAccountName() != null && !clnt.getAccountName().equalsIgnoreCase(""))
//							Account.ban(clnt.getAccountName(), reason);
//						clnt.kick();
//					}
//				}
//			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private String loadCharacter(String charName) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		String name = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM characters WHERE char_name=?");
			pstm.setString(1, charName);

			rs = pstm.executeQuery();

			if (rs.next()) {
				name = rs.getString("account_name");
			}

		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return name;
	}
}
