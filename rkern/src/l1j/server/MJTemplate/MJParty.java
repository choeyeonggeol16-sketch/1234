package l1j.server.MJTemplate;

import l1j.server.server.model.L1Party;
import l1j.server.server.model.Instance.L1PcInstance;

public class MJParty extends L1Party{
	@Override
	public void leaveMember(L1PcInstance pc) {
		try{
			if (getNumOfMembers() == 2) {
				breakup();
			} else {
				removeMember(pc);
				for (L1PcInstance member : getMembers()) {
					sendLeftMessage(member, pc);
				}
				sendLeftMessage(pc, pc);
			}
			
			if(isLeader(pc) && _membersList.size() > 1 ){
				setLeader(_membersList.get(0));
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
