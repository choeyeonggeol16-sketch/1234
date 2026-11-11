package l1j.server.CharacterAction.Walk;
import java.util.ArrayList;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.CharacterAction.Executor.CharacterActionExecutor;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.GMCommands;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.DungeonRandom;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.serverpackets.S_ChangeHeading;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_MoveCharPacket;
import l1j.server.server.serverpackets.S_Party;
import l1j.server.server.serverpackets.S_PinkName;
import l1j.server.server.serverpackets.S_SystemMessage;
import manager.SJManager;

public class WalkActionHandler extends AbstractActionHandler implements PacketParser {
	protected int x;
	protected int y;
	protected int h;
	protected int nextX;
	protected int nextY;
    // Authorization must be consumed while the inbound packet is decoded,
    // BEFORE CharacterActionExecutor queues/sleeps for sprite intervals.
    private boolean nativeStepObserved;
    private boolean nativeStepAuthorized;
    private long nativeStepAcceptedSerial;
    private boolean nativeStepDiscarded;
    private static final java.util.concurrent.atomic.AtomicInteger NATIVE_STEP_TRACE =
            new java.util.concurrent.atomic.AtomicInteger();

	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
        // A pooled/reused handler must never inherit an earlier authorization.
        nativeStepObserved = nativeStepAuthorized = nativeStepDiscarded = false;
        nativeStepAcceptedSerial = 0L;
		this.owner = owner;
		x = pck.readH();
		y = pck.readH();
		h = pck.readC() % 8;
		nextX = x + commons.HEADING_TABLE_X[h];
		nextY = y + commons.HEADING_TABLE_Y[h];
        if (owner != null && owner.getAutoHunt()) {
            nativeStepObserved = true;
            long now = System.nanoTime();
            l1j.server.AutoHuntSystem.NativeStepSession session = owner.getNativeStepSession();
            int status = session.consumeMoveStatus(owner.getX(), owner.getY(),
                    owner.getMapId(), x, y, h, now);
            nativeStepAuthorized = status == l1j.server.AutoHuntSystem.NativeStepSession.MOVE_APPROVED;
            nativeStepAcceptedSerial = nativeStepAuthorized ? session.currentApprovedSerial() : 0L;
            nativeStepDiscarded = !nativeStepAuthorized && session.discardUnapproved(status, now);
            // Explain the rejection without exposing address, player identity,
            // or any network endpoint. Limit log volume in packet hot paths.
            int n = NATIVE_STEP_TRACE.getAndIncrement();
            if (n < 40) {
                System.out.println("[ClientInput] C_MoveChar " +
                        l1j.server.AutoHuntSystem.NativeStepSession.moveReason(status) +
                        (nativeStepAuthorized ? " accepted" :
                         nativeStepDiscarded ? " ignored" : " manual") +
                        " heading=" + h);
            }
        }
	}

	@Override
	public void doWork() {
        if (nativeStepDiscarded) return; // never enqueue unauthorized synthetic movement

		owner.setCallClanId(0);
		if(owner.hasSkillEffect(SkillId.메디테이션)) {
			owner.removeSkillEffect(SkillId.메디테이션);
		}
		owner.setRegenState(L1PcInstance.STATE_MOVE);
		register();
	}

	@Override
	public void handle() {
        final boolean nativeStepMove = nativeStepAuthorized;
        boolean nativeStepApplied = false;
        try {
            if (owner != null && nativeStepObserved && !nativeStepMove && owner.getAutoHunt()) {
                owner.EndAutoController(); // normal manual input still proceeds
            }

        if (nativeStepMove && !owner.getNativeStepSession().isCurrentApprovedSerial(nativeStepAcceptedSerial))
            return; // queued packet from an earlier OFF/ON or timed-out offer
        if (nativeStepMove && (x != owner.getX() || y != owner.getY())) {
            owner.setRegenState(L1PcInstance.STATE_NONE);
            return;
        }
		if (!validation()) {
            if (nativeStepMove && NATIVE_STEP_TRACE.getAndIncrement() < 12)
                System.out.println("[ClientInput] C_MoveChar validation rejected");
			if (nativeStepMove) owner.getNativeStepSession().markMoveNotApplied();
			owner.setRegenState(L1PcInstance.STATE_NONE);
			return;
		}
		/** 낚시 중일 경우 취소 안되고 이동 가능시 * */
		if (owner.isFishing()) {
			try {
				FishingTimeController.getInstance().endFishing(owner);
			} catch (Exception e) {
			}
		}
		try {

            if (nativeStepMove && !owner.getNativeStepSession().isCurrentApprovedSerial(nativeStepAcceptedSerial))
                return; // do not apply old movement after a restart
			owner.setHeading(h);
			owner.getLocation().set(nextX, nextY);
            nativeStepApplied = true;
			if (nativeStepMove) {
                if (NATIVE_STEP_TRACE.getAndIncrement() < 12)
                    System.out.println("[ClientInput] C_MoveChar server move applied");
				int count = owner.getAutoMoveCount() + 1;
				owner.setAutoMoveCount(count >= 5 ? 0 : count);
			}
			owner.setLastMoveActionMillis(System.currentTimeMillis());
			owner.broadcastPacket(S_MoveCharPacket.get(owner, owner.getHeading()));
			CastleWarBusiness.move(owner);
			L1WorldTraps.getInstance().onPlayerMoved(owner);
			owner.getMap().setPassable(nextX, nextY, false);

			/** 파티중 지도표시 **/
			if (owner.isInParty()) {
				L1PcInstance member[] = owner.getParty().getMembers();
				if (owner.getParty() != null) {
					for (int i = 0, a = member.length; i < a; i++) {
						member[i].sendPackets(new S_Party(0x6e, member[i]));
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
        } finally {
            if (nativeStepMove && owner != null) {
                owner.getNativeStepSession().completeMove(nativeStepApplied, nativeStepAcceptedSerial, getInterval());
                if (!nativeStepApplied) owner.getNativeStepSession().markMoveNotApplied(nativeStepAcceptedSerial);
            }
        }
	}

	@Override
	public boolean validation() {

		if (h < 0 || h > 7) {
			return false;
		}
		if (owner == null) {
			return false;
		} 

		if (owner.isDead() || owner.getTeleport() || commons.isNonAction(owner)) {
			return false;
		}	
		int mid = owner.getMap().getId();
		if (Config.길팅체크) {
			
			ArrayList<L1Object> allList = L1World.getInstance().getVisiblePoint(new L1Location(nextX, nextY, owner.getMapId()), 0);

			if (!allList.isEmpty()) {
				for (L1Object obj : allList) {

					if (obj instanceof L1PcInstance) {
						L1PcInstance pcs = (L1PcInstance) obj;
						if (!pcs.isDead() && !pcs.isGmInvis()) {
							owner.setHeading(h);
							
							owner.teleport(owner.getX(), owner.getY(), (short) owner.getMapId(), owner.getHeading(), 169, false, false);
						//	L1Teleport.teleport(pc, pc.getX(), pc.getY(), (short)  pc.getMapId(), pc.getMoveState().getHeading(), false, L1Teleport.DUNGEON_TELEPORT);
							owner.broadcastPacket(S_ChangeHeading.get(owner));
							return false;
						}
					}
					else if (obj instanceof MonsterInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (!npc.isDead()) {
							owner.setHeading(h);
							owner.teleport(owner.getX(), owner.getY(), (short) owner.getMapId(), owner.getHeading(), 169, false, false);
						//	L1Teleport.teleport(pc, pc.getX(), pc.getY(), (short)  pc.getMapId(), pc.getMoveState().getHeading(), false, L1Teleport.DUNGEON_TELEPORT);
							owner.broadcastPacket(S_ChangeHeading.get(owner));
							return false;
						}
					}
					obj = null;
				}
				allList.clear();
				allList = null;
			}
			
			
			

			
//			boolean 길팅 = falㅇse;
//			for (L1PcInstance objs : L1World.getInstance().getVisiblePlayer(owner)) {
//				if (objs.isDead()) {
//					continue;
//				}
//				if (objs.getX() == nextX && objs.getY() == nextY && objs.getMapId() == owner.getMapId()) {
//					if (!objs.isGm() && !objs.isGmInvis()) {
//						길팅 = true;
//						break;
//					}
//				}
//			}		
//			if (길팅) {
//				owner.teleport(x, y, (short) mid, h, 169, false, false);
//			}
		}		

		if (Config.ServerInfo.WALK_SPEED_CHECK) {

			long lastMillis = owner.getLastMoveActionMillis();
			long currentMillis = System.currentTimeMillis() + 20;
			long interval = getInterval();

			if (lastMillis > 0) {
				if ((currentMillis - lastMillis) < interval) {
					owner.addSpeedOverCount();
				} else {
					owner.delSpeedOverCount();
				}
			} 

			if (owner.getSpeedOverCount() == 1) {
				owner._Insert_speed_over_loc[0] = owner.getX();
				owner._Insert_speed_over_loc[1] = owner.getY();
				owner._Insert_speed_over_loc[2] = owner.getMapId();
			}

			if (owner.getSpeedOverCount() > Config.ServerInfo.WALK_SPEED_OVERCOUNT) {

				if (Config.ServerInfo.WALK_SPEED_CHECKTYPE == 1) { // 팅기게 
					owner.sendPackets(new S_Disconnect()); 
				} else if (Config.ServerInfo.WALK_SPEED_CHECKTYPE == 2) { // 제자리 텔
					owner.teleport(owner._Insert_speed_over_loc[0], owner._Insert_speed_over_loc[1], (short) owner._Insert_speed_over_loc[2], owner.getHeading(), 169, false, false);
				} 
				SJManager.getInstance().TimeSpeed(owner.getName(), owner);
				return false;
			}
		}

		L1EventNpcSpawn event_map_check = EventNpcSpawnTable.getInstance().getEventMapCheck(this.owner.getMapId());
		if ((event_map_check != null) && (!this.owner.isGm())) {
			if ((event_map_check.getJoinMaxLevel() != 0)
					&& (owner.getLevel() > event_map_check.getJoinMaxLevel())) {
				owner.sendPackets(
						new S_SystemMessage("\\fY[!] : 레벨" + event_map_check.getJoinMaxLevel() + " 이하만 입장할 수 있습니다."));

				owner.teleport(event_map_check.getExitLoc()[0], event_map_check.getExitLoc()[1], (short) event_map_check.getExitLoc()[2], owner.getHeading(), 169, false, false);

				return false;
			}
			if (!EventNpcSpawnCotroller.isSpawn(event_map_check)) {
				owner.teleport(event_map_check.getExitLoc()[0], event_map_check.getExitLoc()[1], (short) event_map_check.getExitLoc()[2], owner.getHeading(), 169, false, false);

				owner.sendPackets(new S_ChatPacket(owner, String.format("%s", "[사냥터 시간이 아닙니다]"), 1));
				return false;
			}
		}

		owner.getMap().setPassable(x, y, true);
		try {
			if (Dungeon.getInstance().dg(nextX, nextY, mid, owner)) {
				return false;
			}
			if (DungeonRandom.getInstance().dg(nextX, nextY, mid, owner)) {
				return false;
			}
			if (!owner.isGm()) {
				if (!owner.getMap().isUserPassable(x, y, h)) { 
					owner.teleport(x, y, (short) mid, h, 169, false, false);
					return false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public int getRegistIndex() {
		return CharacterActionExecutor.ACTION_IDX_WALK;
	}

	@Override
	public long getInterval() {
		return owner == null ? 1000L : owner.getCurrentSpriteInterval(EActionCodes.fromInt(owner.getCurrentWeapon()));
	}
}
