package l1j.server.server.model.world.trap;

import java.util.Collection;

import l1j.server.server.task.TaskLane;
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
        java.util.Objects.requireNonNull(pc_array);
		
		
		l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(pc_array,SC_DisplayEffect.nativeWriter(asset_id));
		
		if(asset_id == SC_DisplayEffect.QUAKE_DISPLAY){
			TaskLane.WORLD.scheduleRequired(new Runnable(){
				@Override
				public void run(){
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(pc_array,SC_DisplayEffect.nativeWriter(asset_id));
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
