package l1j.server.Stadium;

import java.util.Collection;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.Packet.SC_EVENT_COUNTDOWN_NOTI_PACKET;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class StadiumObject {
    private int m_map_id;
    private int m_ready_seconds;
    private int m_play_seconds;
    private boolean m_is_on;
    private String m_name;
    public StadiumObject(int map_id, int ready_seconds, int play_seconds, String name){
        m_map_id = map_id;
        m_ready_seconds = ready_seconds;
        m_play_seconds = play_seconds;
        m_is_on = false;
        m_name = name;
    }
    
    public void execute(){
        StadiumManager.getInstance().regist(this);
        ThreadPool.getInstance().execute(new Runnable(){
            @Override
            public void run(){
                try{
                    for(int i=m_ready_seconds; i>=0; --i){
                        Collection<L1PcInstance> col = L1World.getInstance().getVisiblePlayers(m_map_id);
                        String message = null;
                        if(i > 0){
                            message = String.format("%d초 후 경기가 시작됩니다.", i);
                        }else{
                            message = "Go!!";
                        }
                        byte[] countDownData = null;
                        if(i == 0){
                            // 경기 시작 — 카운트다운 패킷으로 경기 시간/이름 표시
                            countDownData = SC_EVENT_COUNTDOWN_NOTI_PACKET.create(m_play_seconds, String.format("\\f3%s", m_name)).encode();
                        }
                        for(L1PcInstance pc : col){
                            if(!check_pc(pc))
                                continue;

                            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
                            if(countDownData != null)
                                l1j.server.server.packets.Protocol.Proto.sendRaw(pc, countDownData);
                        }
                        message = null;
                        Thread.sleep(1000);
                    }
                    m_is_on = true;
                    do_wait();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void do_wait(){
        try{
            Thread.sleep(m_play_seconds * 1000);
            if(m_is_on)
                do_ended();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void do_ended() throws Exception{
        m_is_on = false;
        StadiumManager.getInstance().remove(this);
        byte[] countDownData = SC_EVENT_COUNTDOWN_NOTI_PACKET.create(0, String.format("\\f3%s", m_name)).encode();
        String message = String.format("%s 경기가 종료되었습니다.", m_name);
        Collection<L1PcInstance> col = L1World.getInstance().getVisiblePlayers(m_map_id);
        byte[] countDown = countDownData;
        for(L1PcInstance pc : col){
            if(!check_pc(pc))
                continue;

            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, countDown);
            SC_SystemMessage.send(pc, message);
        }
    }
    
    public boolean is_on(){
        return m_is_on;
    }
    
    public int get_current_play_map_id(){
        return m_map_id;
    }

    private boolean check_pc(L1PcInstance pc){
        return !(pc == null || pc.getNetConnection() == null || pc.getNetConnection().isClosed());
    }
    
}