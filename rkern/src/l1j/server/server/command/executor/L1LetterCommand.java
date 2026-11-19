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

import java.sql.Connection;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_LetterList;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1LetterCommand implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1LetterCommand.class.getName());

	private static L1LetterCommand _instance;
	
	private L1LetterCommand() {}

	public static L1CommandExecutor getInstance() {
		return new L1LetterCommand();
	}
	
	@SuppressWarnings("unused")
	public static void reload() {
		L1LetterCommand oldInstance = _instance;
		_instance = new L1LetterCommand();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try{
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int letter_id = Integer.parseInt(st.nextToken());

		/*	if (!pc.getName().equalsIgnoreCase("메티스") && !pc.getName().equalsIgnoreCase("카시오페아") && !pc.getName().equalsIgnoreCase("미소피아") && !pc.getName().equalsIgnoreCase("운영자")) {
				SC_SystemMessage.send(pc, "운영자가 성립되지 않았습니다.");
				return;
			}*/
			
			if(name != null){
				WritePrivateMail(pc, name, letter_id);
			}
		}catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [번호] 입력해 주세요.");
		}
	}
	/** 개인편지 자동으로 보내기 **/
	private void WritePrivateMail(L1PcInstance sender, String receiverName, int letterId) {
        final l1j.server.server.model.network.core.session.CIOClient session=sender.getNetConnection();
        final int stamp=sender.getSessionStamp();
        final String senderName=sender.getName();
        l1j.server.server.storage.CacheD.getInstance().submitDbJobOrdered(sender.getId(),()->{
            if(session==null || !session.isConnected() || session.getActiveChar()!=sender
                    || sender.getSessionStamp()!=stamp)return;
            try(Connection con=DatabaseFactory.getInstance().getConnection();
                PreparedStatement ps=con.prepareStatement("SELECT subject,content FROM letter_command WHERE id=?")) {
                ps.setInt(1,letterId);
                try(ResultSet rows=ps.executeQuery()) {
                    if(!rows.next())return;
                    String subject=rows.getString(1),content=rows.getString(2);
                    if(subject==null || content==null)return;
                    LetterTable.getInstance().writeLetter(949,new Timestamp(System.currentTimeMillis()),
                        senderName,receiverName,0,subject,content,sender,()->{
                            SC_SystemMessage.send(sender,receiverName+"님께 편지를 보냈습니다.");
                            L1PcInstance target=L1World.getInstance().getPlayer(receiverName);
                            if(target!=null)l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                                .completeWarehouseMove(target,target.getSessionStamp(),
                                    ()->sendMessageToReceiver(target,sender,0,50),failure->{});
                        });
                }
            }catch(Exception failure){throw new IllegalStateException("Letter command failed",failure);}
        });
    }

	private void LetterList(L1PcInstance pc, int type, int count) {
		SC_LetterList.sendListAsync(pc, type, count);
	}

	private void sendMessageToReceiver(L1PcInstance receiver, L1PcInstance sender, final int type, final int MAILBOX_SIZE) {
		if (receiver != null && receiver.getOnlineStatus() != 0) {
			LetterList(receiver, type, MAILBOX_SIZE);
			SC_SkillSound.send(receiver, receiver.getId(), 1091);
			SC_ServerMessage.send(receiver, 428); // 편지가 도착했습니다.
			SC_LetterList.sendListAsync(sender, type, MAILBOX_SIZE);
			return;
		}
	}

}
