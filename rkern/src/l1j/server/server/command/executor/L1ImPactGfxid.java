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

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.SQLUtil;

public class L1ImPactGfxid implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1InvGfxId.class.getName());
	public static ArrayList<ImPact> _ImPactInfo = new ArrayList<ImPact>();

	private L1ImPactGfxid() {}

	public static L1CommandExecutor getInstance() {
		return new L1ImPactGfxid();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int GfxidStart = Integer.parseInt(st.nextToken());
			int GfxidEnd = Integer.parseInt(st.nextToken());

			int HomeXY = 4;
			int HomeH = 10;
			int HomeX = pc.getX();
			int HomeY = pc.getY();
			int LocX = HomeX;
			int LocY = HomeY;
			L1Npc l1npc = null;
			Constructor<?> constructor = null;
			L1NpcInstance npc = null;

			l1npc = NpcTable.getInstance().getTemplate(45001);
			constructor = Class.forName("l1j.server.server.model.Instance." + l1npc.getImpl() + "Instance").getConstructors()[0];
			Object aobj[] = { l1npc };

			ArrayList<ImPact> ImPactList = isImPact(GfxidStart, GfxidEnd);
			for (int i = 0; i < ImPactList.size(); i++) {
				ImPact Pact = ImPactList.get(i);
				npc = (L1NpcInstance) constructor.newInstance(aobj);
				npc.setId(IdFactory.getInstance().nextId());
				npc.setCurrentSprite(Pact._ImPact);
				npc.setNameId(Pact._ImPact+"");
				npc.setMap(pc.getMapId());
				npc.setX(LocX);
				npc.setY(LocY);
				npc.setHomeX(npc.getX());
				npc.setHomeY(npc.getY());
				npc.setHeading(4);
				L1World.getInstance().storeAndEnterWorld(npc);
				ThreadPool.getInstance().scheduleRequired(new Pact(npc, Pact), 1000);

				/** 1시간만 유지되도록 따로 처리 */
				npc.startTimedDelete(60 * 60 * 1000);	
				if(i == HomeH){
					LocX = HomeX - (HomeXY * (HomeH / 10));
					LocY = HomeY + (HomeXY * (HomeH / 10));
					HomeH += 10;
				}else{
					LocX += HomeXY;
					LocY += HomeXY;
				}

			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, ".셀이펙트 (시작번호) (나올갯수)");
			SC_SystemMessage.send(pc, "번호끝 : 18367");

		}
	}

	class Pact implements Runnable {
		L1NpcInstance _NpcInstance;
		int _PactId;

		public Pact(L1NpcInstance Npc, ImPact Pact) {
			_PactId = Pact.getImPactId();
			_NpcInstance = Npc;
		}

		@Override
		public void run() {
			try{
				if(_NpcInstance == null || _NpcInstance._destroyed) return;
				SC_DoActionGFX.broadcast(_NpcInstance, _PactId);
				ThreadPool.getInstance().scheduleRequired(this, 3000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}


	public ArrayList<ImPact> isImPact(int GfxidStart, int GfxidEnd) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM spr_action WHERE act_name = ?");
			pstm.setString(1, "effect");
			rs = pstm.executeQuery();

			 int Gfxid;
			 _ImPactInfo.clear();
			 while (rs.next()) {
				Gfxid = rs.getInt("spr_id");
				if(Gfxid >= GfxidStart && Gfxid <= GfxidEnd) {
					ImPact ImPactList = new ImPact();
					ImPactList._ImPact = Gfxid;
					ImPactList._ImPactId = rs.getInt("act_id");
					_ImPactInfo.add(ImPactList);
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return _ImPactInfo;
	}

	public static class ImPact {
		public int	_ImPact;
		public int	_ImPactId;

		public int getImPact() {
			return _ImPact;
		}

		public int getImPactId() {
			return _ImPactId;
		}
	}
}
