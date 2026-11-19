package l1j.server.server.command.executor;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Recall implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1Recall.class.getName());

	private L1Recall() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Recall();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			Collection<L1PcInstance> targets = null;
			
			
			
			
			if (arg.equalsIgnoreCase("소환")) {
				targets = L1World.getInstance().getAllPlayers();
			} else {
				targets = new ArrayList<L1PcInstance>();
				L1PcInstance tg = L1World.getInstance().getPlayer(arg);
				if (tg == null) {
					SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다. ");
					return;
				}
				targets.add(tg);
			}

			try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter("\\f:운영자님께서 당신을 소환 하였습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance target : targets) {
				

				
				
				if (target.isPrivateShop() || target.isAutoClanjoin() /*|| target.isAutoFish()*/) {
					SC_SystemMessage.send(pc, target.getName() + " 님은 상점모드 or 무인가입 중 입니다.");
					return;
				}
				if (pc.getMapId() == 5554) {
					SC_Ability.send(target, 3, true);
				}
				
				L1Location loc = L1Teleport.getInstance().소환텔레포트(pc, 1);
				target.start_teleportForGM(loc.getX(), loc.getY(), loc.getMapId(), target.getHeading(), 169, true, true);
				SC_SystemMessage.send(pc, "\\f:캐릭터: ["+target.getName()+"]님을 소환하였습니다. ");
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,target);
			}
}
			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + ".소환 캐릭터명 으로 입력해 주세요.]");
		}
	}
}
