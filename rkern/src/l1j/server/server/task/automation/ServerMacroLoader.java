package l1j.server.server.task.automation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_ChatMessageNoti;
import l1j.server.server.model.network.core.sc.packet.SC_IconMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class ServerMacroLoader implements Runnable{
	private static volatile ServerMacroLoader _instance;
    private static final Logger LOG = Logger.getLogger(ServerMacroLoader.class.getName());
	public static ServerMacroLoader getInstance(){
		if(_instance == null)
			_instance = new ServerMacroLoader();
		return _instance;
	}
	
	public static void reload(){
		ServerMacroLoader tmp = _instance;
        // Invalidate the previous generation before scheduling the next one.
        if (tmp != null) tmp.clear();
        ServerMacroLoader replacement = new ServerMacroLoader();
        _instance = replacement;
        replacement.start();
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
    private volatile boolean _isrun;
    private volatile ArrayList<ServerMacroObject> _workList;
    private volatile CServerTimer heartbeat;
    private final AtomicBoolean tickPending = new AtomicBoolean();
	private ServerMacroLoader(){
		_isrun	= false;
	}
	
	public void start(){
        // Enable only AFTER the complete DB snapshot is published.
        _isrun = false;
        Connection 			con 	= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs		= null;
		ServerMacroObject obj		= null;
		HashMap<Integer, ServerMacroObject> maps = new HashMap<Integer, ServerMacroObject>(128);
		try{
			con 		= DatabaseFactory.getInstance().getConnection();
			pstm 		= con.prepareStatement("SELECT * FROM tb_servermacro");
			rs			= pstm.executeQuery();
			int rows	= SQLUtil.calcRows(rs);
			if(rows <= 0)
				return;
			_workList	= new ArrayList<ServerMacroObject>(SQLUtil.calcRows(rs));
			long cur	= System.currentTimeMillis();
			while(rs.next()){
				int 	id	= rs.getInt("id");
				long 	d	= rs.getInt("delay") * 1000;
				obj			= maps.get(id);
				if(obj == null){
					obj = new ServerMacroObject(id, d, cur);
					maps.put(id, obj);
				}
				
				byte[] b = rs.getBytes("message");
				obj.add(new String(b, "MS949"), rs.getString("type"));
			}
			
			for(ServerMacroObject o : maps.values())
				_workList.add(o);

			_isrun = true;
			Collections.sort(_workList);
            heartbeat = CServerTimer.fixedDelaySignal(
                    this::signalTick, 0L, 1000L);
            if (heartbeat == null) {
                _isrun = false;
                throw new IllegalStateException("Server macro scheduler admission failed");
            }
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
    public void clear() {
        _isrun = false;
        CServerTimer previous = heartbeat;
        heartbeat = null;
        if (previous != null) previous.cancel();
        // The running owner may still hold the old list; do not mutate it
        // concurrently. Retired lists become collectible when run() exits.
        _workList = null;
    }

    private void signalTick() {
        if (!_isrun || _workList == null || !tickPending.compareAndSet(false, true)) return;
        try {
            CWorldWork.execute(this);
        } catch (RejectedExecutionException refused) {
            tickPending.set(false);
            LOG.log(Level.WARNING, "Server macro tick admission refused; retry next second", refused);
        }
    }
	
	@Override
	public void run() {
		long 				cur = System.currentTimeMillis();
		ServerMacroObject obj = null;
		
        final ArrayList<ServerMacroObject> list = _workList;
        if (!_isrun || list == null) {
            tickPending.set(false);
            return;
        }
        try {
            Collection<L1PcInstance> col = CWorld.getInstance().getAllPlayers();
            int size = list.size();			
			for(int i=0; i<size; i++){
                if (!_isrun) break;
                obj = list.get(i);
				if(obj.nextMs > cur)
					break;
				
final String packet=obj.get();
                final int selectedType=obj.getSelectedType();
                if(selectedType==0){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(col,SC_SystemMessage.nativeWriter(packet));
                } else if(selectedType==2){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(col,SC_PacketBox.nativeGreenWriter(packet));
                } else if(selectedType==1){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(col,SC_IconMessage.nativeGmWriter(packet));
                } else if(selectedType==3){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(col,SC_ChatMessageNoti.nativeMacroWriter(
                            SC_ChatMessageNoti.CHAT_PLEDGE_NOTICE,
                            packet,Config.GAME_SERVER_NAME));
                } else if(selectedType==4){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(col,SC_ChatMessageNoti.nativeMacroWriter(
                            SC_ChatMessageNoti.CHAT_WHISPER,
                            packet,"오늘의 검색어"));
                }

                obj.nextMs = cur + obj.delay;
			}
		}catch(Exception e){
			if(_isrun)	e.printStackTrace();
        } finally {
            if (_isrun && _workList == list && !list.isEmpty()) {
                Collections.sort(list);
            }
            tickPending.set(false);
        }
    }
}
