package l1j.server.server.utils;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class MJFullStater implements Runnable{
	public static void running(L1PcInstance pc, String s, int count){
		int i = calcBonus(pc);
		if(i <= 0 || count <= 0 || i < count){
			SC_SystemMessage.send(pc, String.format("스텟이 충분하지 않습니다. [남은 스텟 : %d]", i));
			return;
		}
		
		MJFullStater stater = new MJFullStater(pc, s, count);
		if(count <= 1){
			try{
				stater.work(0L);
				SC_SystemMessage.send(pc, String.format("%s(이)가 증가되었습니다. [남은 스텟 : %d]", s, calcBonus(pc)));
				SC_MessageYN.send(pc, 479, Integer.toString(calcBonus(pc)));
			}catch(Exception e){
				SC_SystemMessage.send(pc, String.format("스텟이 충분하지 않습니다. [남은 스텟 : %d]",calcBonus(pc)));
			}
		}else
			ThreadPool.getInstance().execute(stater);
	}
	
	private L1PcInstance 	_pc;
	private String				_s;
	private int					_count;
	private MJFullStater(L1PcInstance pc, String s, int count){
		_pc 		= pc;
		_s			= s;
		_count	= count;
	}
	
	@Override
	public void run() {
		try{
			for(int i = _count - 1; i>= 0; --i){
				work(100L);
			}
			SC_SystemMessage.send(_pc, String.format("%s(이)가 증가되었습니다. [남은 스텟 : %d]", _s, calcBonus(_pc)));
		}catch(Exception e){
			if(_pc != null)
				SC_SystemMessage.send(_pc, String.format("스텟이 충분하지 않습니다. [남은 스텟 : %d]", calcBonus(_pc)));
		}finally{
			SC_MessageYN.send(_pc, 479, Integer.toString(calcBonus(_pc)));
		}
	}
	
	private void work(long sleeping) throws Exception{
		if(!_pc.onStat(_s))
			throw new Exception();
		if(sleeping > 0)
			Thread.sleep(sleeping);
	}
	
	private static int calcBonus(L1PcInstance pc){
		return pc.getLevel() - 50 - pc.getBonusStats();
	}
}
