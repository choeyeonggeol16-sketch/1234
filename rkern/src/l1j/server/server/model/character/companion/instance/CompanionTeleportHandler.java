package l1j.server.server.model.character.companion.instance;

import java.util.ArrayList;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.world.instance.shared.EffectTriggerInstance;
import l1j.server.server.shared.geometry.GamePoint;

public final class CompanionTeleportHandler {
	
	public static boolean on_teleported(L1PcInstance owner, int next_x, int next_y, int map_id) {
	    final L1CompanionInstance companion = owner.get_companion();
	    if (companion == null) {
	        return false;
	    }

	    final short mid = owner.getMapId();
	    final L1Map map = owner.getMap();

	    if (owner.isGhost() || !map.isTakePets() || mid == 5153 || mid == 5140 || mid == 781 || mid == 782) {
	        owner.remove_companion();
	        return false;
	    }

	    companion.set_is_teleporting(true);
	    try {
	        final ArrayList<EffectTriggerInstance> triggers = companion.get_triggers();

	        /*
	         * OLD VIEW
	         * 기존 위치에서 보고 있던 플레이어들에게 companion / trigger 제거.
	         */
	        final ArrayList<L1PcInstance> oldViewers =
	                L1World.getInstance().getVisiblePlayer(companion);

	        hideCompanionFromViewer(owner, companion, triggers);

	        for (L1PcInstance viewer : oldViewers) {
	            if (viewer == null || viewer == owner) {
	                continue;
	            }
	            hideCompanionFromViewer(viewer, companion, triggers);
	        }

	        /*
	         * 실제 companion 좌표 이동
	         */
	        final GamePoint pt = GamePoint.newInstance(next_x, next_y, 2, mid);

	        L1World.getInstance().relocateVisibleObject(
	                companion, pt.x, pt.y, mid);

	        if (triggers != null) {
	            for (EffectTriggerInstance trigger : triggers) {
	                trigger.setX(pt.x);
	                trigger.setY(pt.y);
	                trigger.setMap(mid);
	                L1World.getInstance().getClusterGrid().relocate(trigger);
	            }
	        }

	        /*
	         * NEW VIEW
	         * 새 위치에서 보이는 플레이어들에게 companion / trigger 즉시 생성.
	         */
	        showCompanionToViewer(owner, companion, triggers);

	        for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayerFast(companion)) {
	            if (viewer == null || viewer == owner) {
	                continue;
	            }
	            showCompanionToViewer(viewer, companion, triggers);
	        }

	    } finally {
	        companion.set_is_teleporting(false);
	    }

	    return false;
	}

	private static void hideCompanionFromViewer(
	        L1PcInstance viewer,
	        L1CompanionInstance companion,
	        ArrayList<EffectTriggerInstance> triggers) {

	    if (viewer == null) {
	        return;
	    }

	    viewer.outOfSight(companion);

	    if (triggers != null) {
	        for (EffectTriggerInstance trigger : triggers) {
	            if (trigger != null) {
	                viewer.outOfSight(trigger);
	            }
	        }
	    }
	}
	private static void showCompanionToViewer(
	        L1PcInstance viewer,
	        L1CompanionInstance companion,
	        ArrayList<EffectTriggerInstance> triggers) {

	    if (viewer == null) {
	        return;
	    }

	    if (viewer.canSeeObject(companion)) {
	        viewer.inSight(companion);
	    }

	    if (triggers != null) {
	        for (EffectTriggerInstance trigger : triggers) {
	            if (trigger != null && viewer.canSeeObject(trigger)) {
	                viewer.inSight(trigger);
	            }
	        }
	    }
	}

}
