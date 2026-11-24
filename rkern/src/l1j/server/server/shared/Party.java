package l1j.server.server.shared;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CParty;

public class Party extends CParty{
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
