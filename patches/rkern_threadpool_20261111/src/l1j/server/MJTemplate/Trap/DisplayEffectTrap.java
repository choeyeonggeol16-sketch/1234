package l1j.server.MJTemplate.Trap;

import java.util.Collection;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;

public class DisplayEffectTrap extends AbstractTrap{	
	public static DisplayEffectTrap newInstance(){
		return new DisplayEffectTrap();
	}
	
	private DisplayEffectTrap(){
	}

	@Override
	public void do_trap(L1PcInstance pc) {
		SC_DisplayEffect.send(pc, get_asset_id());
	}

	@Override
	public void do_traps(L1PcInstance[] pc_array) {
		int asset_id = get_asset_id();
		
		
		for(L1PcInstance pc : pc_array){
			if(pc == null)
				continue;
			
			SC_DisplayEffect.send(pc, asset_id);
		}
		
		if(asset_id == SC_DisplayEffect.QUAKE_DISPLAY){
			ThreadPool.getInstance().scheduleRequired(new Runnable(){
				@Override
				public void run(){
					for(L1PcInstance pc : pc_array){
						if(pc == null)
							continue;
						
						SC_DisplayEffect.send(pc, asset_id);
					}
				//	eff.clear();
				}
			}, 1000L);
		}else{
			//eff.clear();
		}
		
	}

	@Override
	public void do_traps(Collection<L1PcInstance> pc_collections) {
		do_traps(pc_collections.toArray(new L1PcInstance[pc_collections.size()]));
	}
}
