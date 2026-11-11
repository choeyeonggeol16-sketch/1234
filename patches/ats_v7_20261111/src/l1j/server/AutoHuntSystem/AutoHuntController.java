package l1j.server.AutoHuntSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.monitor.L1PcMonitor;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.serverpackets.S_AttackStatus;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_MoveCharPacket;
import l1j.server.server.serverpackets.S_Sound;

public class AutoHuntController extends L1PcMonitor {
	public AutoHuntController(int oId) {
		super(oId);
	}

	@Override
	public void execTask(L1PcInstance pc) {
		try {
			if (owner != pc)
				owner = pc;

			getSource();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Random _rnd = new Random(System.nanoTime());

	public final int AUTO_STATUS_NONE = -1;
	public final int AUTO_STATUS_WALK = 0;
	public final int AUTO_STATUS_ATTACK = 1;
	public final int AUTO_STATUS_PICKUP_ITEM = 2;

	private L1PcInstance owner;
    private boolean randomWalkPlanned;
    private int movementPathErrorCount;
    // The old AI recalculated A* on each tick after a step. Keep the
    // selected path in the owner's existing 300-entry primitive array and
    // advance its cursor ONLY after the walk executor reports application.
    private boolean routeReserved;
    private boolean routeAwaitingApply;
    private int routeGoalX, routeGoalY, routeMap, routeMode, routeTargetId;
    private int routeIndex = -1;
    private long routeAppliedAtOffer;

	private void getSource() {
		if (owner == null) {
			removeAuto("자동 사냥을 종료 합니다.");
			return;
		}
		
        if (!owner.getAutoHunt()) return;
        String atsBlocked = owner.getAtsBlockReason();
        if (atsBlocked != null) {
            owner.sendPackets(new l1j.server.server.serverpackets.S_SystemMessage(atsBlocked));
            owner.EndAutoController();
            return;
        }

        // Refresh the already authorized heading independently of pathfinding.
        // toMoving() is sprite-gated and cannot provide a steady 75ms hold.
        if (owner.getAutoHunt() && owner.getNativeStepSession().takeResyncRequest()) {
            // Local client prediction outran validated server movement.
            final int px = owner.getX(), py = owner.getY();
            final short pm = (short) owner.getMapId();
            final int ph = owner.getHeading();
            owner.EndAutoController();
            owner.teleport(px, py, pm, ph, 169, false, false);
            System.out.println("[ClientInput] auto stopped and client position resync requested");
            return;
        }
        if (owner.getAutoHunt())
            owner.getNativeStepSession().refreshPending(System.nanoTime());

		int percent = (int) Math.round(((double) owner.getCurrentHp() / (double) owner.getMaxHp()) * 100);
		if (percent < 30) {
			if (owner.isAlarmHp()) {
				if (owner.getHpArlamTime() == 0) {
					owner.setHpArlamTime(System.currentTimeMillis());
				} else {
					if (System.currentTimeMillis() >= owner.getHpArlamTime() + 2000) {
						owner.setHpArlamTime(0);
						int sound_num = owner.get_sex() == 0 ? 21234:21233;
						owner.sendPackets(new S_Sound(sound_num));
					}
				}
			}
		}

		if (owner.getInventory().getWeight100() > 82) {
			if (owner.isAlarmWeight()) {
				if (owner.getWeightArlamTime() == 0) {
					owner.setWeightArlamTime(System.currentTimeMillis());
				} else {
					if (System.currentTimeMillis() >= owner.getWeightArlamTime() + 2000) {
						owner.setWeightArlamTime(0);
						int sound_num = owner.get_sex() == 0 ? 21235:21236;
						owner.sendPackets(new S_Sound(sound_num));
					}
				}
			}
			return;
		}
		
		if (owner.getAutoDropTime() != 0) {
			if (System.currentTimeMillis() < owner.getAutoDropTime() + 1000) {
				return;
			} else {
				owner.setAutoDropTime(0);
			}
		}

		if ((owner.getAutoStatus() != AUTO_STATUS_WALK && owner.getAutoStatus() != AUTO_STATUS_ATTACK
				&& owner.getAutoStatus() != AUTO_STATUS_PICKUP_ITEM)) {
			owner.setAutoStatus(AUTO_STATUS_WALK);
		}

		if (owner.isDead()) {
			owner.resetAuto();
			owner.sendPackets(new S_ChatPacket(owner, String.format("%s", "캐릭터가 사망하여 자동사냥을 종료 합니다."), 1));
			owner.EndAutoController();
			return;
		} else {
			AutoHuntItemUse itemuse = new AutoHuntItemUse(owner);
			itemuse.toUseItem();
			itemuse.toPolyScroll();

			AutoHuntSkillUse skilluse = new AutoHuntSkillUse(owner);
			skilluse.toUseSkills();
			skilluse.toUseHealingMagic();
			
			toPickupItem();
		}

		switch (owner.getAutoStatus()) {
		case AUTO_STATUS_WALK:
			searchTarget();
			
			if (owner.getAutoTarget() == null)
				toRandomWalk();
			
			if (owner.getAutoTargetList().toTargetArrayList().size() > 0) {
				owner.setAutoStatus(AUTO_STATUS_ATTACK);
			}
			break;
		case AUTO_STATUS_ATTACK:
            randomWalkPlanned = false;
			if (owner.getAutoTargetList().toTargetArrayList().size() == 0) {
				owner.getAutoTargetList().clear();
				owner.setAutoTarget(null);
				owner.setAutoStatus(AUTO_STATUS_WALK);
			}
			toAttackMonster();
			break;
		case AUTO_STATUS_PICKUP_ITEM:
            randomWalkPlanned = false;
			PickUpItem();
			break;
		default:
			break;
		}
	}

	private void toAttackMonster() {
		try {
			if (!isAutoAttackTime()) {
				return;
			}
			L1Character target = owner.getAutoTarget();

			if (target != null && target.isDead()) {
				owner.removeAutoTargetList(target);
				owner.setAutoTarget(null);
				searchTarget();
				return;
			}

			if (owner.getAutoTarget() == null) {
				owner.setAutoTarget(getTarget());
			}

			if (!isAttack(target)) {
				owner.removeAutoTargetList(target);
				owner.setAutoTarget(null);
			}

			if (owner.getAutoTarget() == null) {
				owner.setAutoStatus(AUTO_STATUS_WALK);
				return;
			}

			if (owner.getLocation().getTileLineDistance(getTarget().getLocation()) < owner.getLocation()
					.getTileLineDistance(target.getLocation())) {
				owner.removeAutoTargetList(target);
				owner.setAutoTarget(getTarget());
			}

			if (isDistance(owner.getX(), owner.getY(), owner.getMapId(), target.getX(), target.getY(),
					target.getMapId(), owner.getAttackRang())) {
				if (owner.glanceCheck(target.getX(), target.getY())) {
					toAttack();
				} else {
					toMoving(target.getX(), target.getY(), 0, true);
				}
			} else {
				if (owner.getAutoAiTime() == 0) {
					owner.setAutoAiTime(System.currentTimeMillis());
				} else {
					if (System.currentTimeMillis() >= owner.getAutoAiTime() + 10000) {
						owner.setAutoAiTime(0);
						owner.removeAutoTargetList(target);
						owner.setAutoTarget(null);
					}
				}
				toMoving(target.getX(), target.getY(), 0, true);
			}
		} catch (Exception e) {
			owner.removeAutoTargetList(owner.getAutoTarget());
			owner.setAutoTarget(null);
		}
	}

	private boolean isAttack(L1Character cha) {
		try {
			if (cha == null) {
				return false;
			}
			if (cha.isDead()) {
				return false;
			}
			if (cha.hasSkillEffect(SkillId.어스_바인드) || cha.hasSkillEffect(SkillId.아이스_랜스)
					|| cha.hasSkillEffect(SkillId.쇼크_스턴) || cha.hasSkillEffect(SkillId.상태_동결)) {
				return false;
			}
			if (cha.getMap().isSafetyZone(cha.getLocation())) {
				return false;
			}
	
			if (cha.isInvisble()) {
				return false;
			}
	
			if (!isDistance(owner.getX(), owner.getY(), owner.getMapId(), cha.getX(), cha.getY(), cha.getMapId(), 16)) {
				return false;
			}
			if (!owner.glanceCheck(cha.getX(), cha.getY())) {
				return false;
			}
				

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private void toAttack() {
        if (owner.getNativeStepSession().hasOutstandingMove(System.nanoTime())) return;
		try {
			L1Character target = owner.getAutoTarget();
			if (target == null) {
				owner.getAutoTargetList().clear();
				owner.setAutoStatus(AUTO_STATUS_WALK);
				return;
			}

			if(owner.hasSkillEffect(SkillId.메디테이션)) {
				owner.removeSkillEffect(SkillId.메디테이션);
			}
			owner.delInvis();
			if (owner.isElf() && owner.getWeapon().getItem().getType1() == 20) {
				if (owner.getInventory().getArrow() == null) {
					// 화살이 없다
					// returnScroll();
					return;
				}
				int chance = _rnd.nextInt(100) + 1;
				if (chance <= 30) {
					AutoHuntSkillUse skilluse = new AutoHuntSkillUse(owner);
					skilluse.toTripleArrow(target);
				} else {
					target.onAction(owner);
				}
			} else {
				target.onAction(owner);
			}
		} catch (Exception e) {
			owner.setAutoTarget(null);
			owner.getAutoTargetList().clear();
		}
	}

	private L1Character getTarget() {
		L1Character realTarget = null;
		try {
			for (int i = 0; i < owner.getAutoTargetList().toTargetArrayList().size(); i++) {
				L1Character target = owner.getAutoTargetList().toTargetArrayList().get(i);
				if (target.isDead()) {
					owner.removeAutoTargetList(target);
					owner.setAutoTarget(null);
					continue;
				}
				if (!owner.glanceCheck(target.getX(), target.getY())) {
					owner.removeAutoTargetList(target);
					owner.setAutoTarget(null);
					continue;
				}

				if (realTarget == null) {
					realTarget = target;
				} else if (!target.isDead()
						&& getDistance(owner.getX(), owner.getY(), target.getX(), target.getY()) < getDistance(
								owner.getX(), owner.getY(), realTarget.getX(), realTarget.getY())) {
					realTarget = target;
				}
			}
			return realTarget;
		} catch (Exception e) {
			e.printStackTrace();
			owner.getAutoTargetList().clear();
			owner.setAutoTarget(null);
			return realTarget;
		}
	}

	private boolean isDistance(int x, int y, int m, int tx, int ty, int tm, int loc) {
		int distance = getDistance(x, y, tx, ty);
		if (loc < distance)
			return false;
		if (m != tm)
			return false;
		return true;
	}

	private int getDistance(int x, int y, int tx, int ty) {
		long dx = tx - x;
		long dy = ty - y;
		return (int) Math.sqrt(dx * dx + dy * dy);
	}

	private void searchTarget() {
		checkTarget();
		for (L1Object obj : L1World.getInstance().getVisibleObjects(owner)) {
			if (obj == null) {
				continue;
			}
			if (obj instanceof MonsterInstance) {
				MonsterInstance mon = (MonsterInstance) obj;
				if (mon.isDead()) {
					continue;
				}
				if (mon.getHiddenStatus() >= 1) {
					continue;
				}

				if (!owner.glanceCheck(mon.getX(), mon.getY())) {
					continue;
				}
				owner.addAutoTargetList(mon);
			}
		}
	}

	private void checkTarget() {
		try {
			L1Character target = owner.getAutoTarget();
			if (target == null || target.getMapId() != owner.getMapId() || target.isDead() || target.getCurrentHp() <= 0
					|| (target.isInvisble() && !owner.getAutoTargetList().containsKey(target))) {
				if (target != null) {
					tagertClear();
				}

				if (!owner.getAutoTargetList().isEmpty()) {
					owner.setAutoTarget(owner.getAutoTargetList().getMaxHateCharacter());
					checkTarget();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void tagertClear() {
		L1Character target = owner.getAutoTarget();
		if (target == null) {
			return;
		}
		owner.getAutoTargetList().remove(target);
		owner.setAutoTarget(null);
	}

	private void toRandomWalk() {
        // NativeStepSession owns walk cadence. Consuming another sprite gate
        // before offerMove could miss its deadline and skip an entire cycle.
		
        // Old direct-movement code incremented AutoMoveCount immediately.
        // Now the count changes only after an approved C_MoveChar is applied.
        // Without a persistent plan, the 10ms AI tick would reroll the target
        // before the first walking packet arrives and keep changing direction.
        if (!randomWalkPlanned) {
			int randomLocX = (int) ((Math.random() * 20) - 10);
			int randomLocY = (int) ((Math.random() * 20) - 10);
			int _locX = owner.getX() + randomLocX;
			int _locY = owner.getY() + randomLocY;	
			owner.setAutoLocX(_locX);
			owner.setAutoLocY(_locY);
            randomWalkPlanned = true;
		}
		int dir = owner.targetDirection(owner.getAutoLocX(), owner.getAutoLocY());		
		toMoving(owner.getAutoLocX(), owner.getAutoLocY(), dir, true);
	}

    private void clearReservedRoute() {
        routeReserved = false;
        routeAwaitingApply = false;
        routeIndex = -1;
    }

    private void toMoving(int x, int y, int h, boolean astar) {
        if (owner == null || !owner.getAutoHunt() || owner.getTeleport() || owner.isDead()) return;
        if (!astar) {
            clearReservedRoute();
            toMoving(x, y, h);
            return;
        }
        final NativeStepSession session = owner.getNativeStepSession();
        final long now = System.nanoTime();
        final int map = owner.getMapId();
        final int mode = owner.getAutoStatus();
        final L1Character target = owner.getAutoTarget();
        final int targetId = (mode == AUTO_STATUS_ATTACK && target != null) ? target.getId() : 0;

        // A reservation can survive multiple AI ticks, but an already
        // authorized move must be allowed to finish before switching routes.
        if (routeAwaitingApply) {
            if (session.hasOutstandingMove(now)) return;
            final int[][] path = owner.getAutoPath();
            if (session.appliedStepSequence() == routeAppliedAtOffer) {
                // Silent short clicks are not proof of client/server desync.
                // Retry the SAME authorized origin/heading at most twice,
                // and never retry after any movement packet was observed.
                if (session.retrySilentMove(owner.getX(), owner.getY(), map, now))
                    return;
                clearReservedRoute();
                session.markMoveNotApplied();
                return;
            }
            if (routeIndex < 0 || routeIndex >= path.length ||
                    owner.getX() != path[routeIndex][0] ||
                    owner.getY() != path[routeIndex][1] || map != routeMap) {
                clearReservedRoute();
                session.markMoveNotApplied();
                return;
            }
            routeAwaitingApply = false;
            --routeIndex;
            if (routeIndex < 0) {
                // L1Astar intentionally stops next to the requested target.
                // Keep the same random target if the path ended early.
                if (mode == AUTO_STATUS_WALK && target == null &&
                        Math.max(Math.abs(x - owner.getX()), Math.abs(y - owner.getY())) <= 1)
                    randomWalkPlanned = false;
                clearReservedRoute();
                return;
            }
        }
        if (session.hasOutstandingMove(now)) return;
        if (routeReserved && (routeGoalX != x || routeGoalY != y ||
                routeMap != map || routeMode != mode || routeTargetId != targetId))
            clearReservedRoute();

        if (!routeReserved) {
            if (x == owner.getX() && y == owner.getY()) {
                if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
                return;
            }
            try {
                owner.getAutoAstar().ResetPath();
                owner.setAutoTail(owner.getAutoAstar().FindPath(owner, x, y, map, null));
                final int[][] path = owner.getAutoPath();
                int n = 0;
                for (l1j.server.server.Astar.L1Node node = owner.getAutoTail();
                        node != null; node = node.prev) {
                    if (node.x == owner.getX() && node.y == owner.getY()) break;
                    if (n >= path.length) {
                        if (movementPathErrorCount++ < 4)
                            System.out.println("[ClientInput] reserved route exceeds path capacity");
                        if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
                        return;
                    }
                    path[n][0] = node.x;
                    path[n][1] = node.y;
                    ++n;
                }
                if (n == 0) {
                    if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
                    return;
                }
                routeIndex = n - 1;  // L1Astar returns destination -> ... -> start
                routeGoalX = x;
                routeGoalY = y;
                routeMap = map;
                routeMode = mode;
                routeTargetId = targetId;
                routeReserved = true;
            } catch (RuntimeException ex) {
                clearReservedRoute();
                if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
                if (movementPathErrorCount++ < 4)
                    System.out.println("[ClientInput] route reservation failed: " + ex.getClass().getSimpleName());
                return;
            }
        }

        final int[][] path = owner.getAutoPath();
        final int stepX = path[routeIndex][0];
        final int stepY = path[routeIndex][1];
        final int dx = stepX - owner.getX();
        final int dy = stepY - owner.getY();
        // A reserved tile may become occupied before we reach it. Abandon
        // this route rather than authorize a direction based on stale tiles.
        if (Math.max(Math.abs(dx), Math.abs(dy)) != 1) {
            clearReservedRoute();
            if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
            return;
        }
        final int dir = commons.calcheading(owner.getX(), owner.getY(), stepX, stepY);
        if (dir < 0 || dir > 7 || dx != commons.HEADING_TABLE_X[dir] ||
                dy != commons.HEADING_TABLE_Y[dir] ||
                !owner.getMap().isUserPassable(owner.getX(), owner.getY(), dir)) {
            clearReservedRoute();
            if (mode == AUTO_STATUS_WALK) randomWalkPlanned = false;
            return;
        }
        if (!l1j.server.server.network.NativeStepControl.isBound(owner)) {
            owner.EndAutoController();
            return;
        }
        // Record the prior completion counter so a pending offer, timeout,
        // or queued packet cannot be mistaken for a confirmed movement.
        final long before = session.appliedStepSequence();
        if (session.offerMove(owner.getX(), owner.getY(), map, dir, now)) {
            routeAppliedAtOffer = before;
            routeAwaitingApply = true;
        }
    }

    private void toMoving(final int x, final int y, final int h) {
        if (owner == null || !owner.getAutoHunt() || owner.getTeleport() || owner.isDead()) return;
        int dx = x - owner.getX();
        int dy = y - owner.getY();
        if (h < 0 || h > 7 || dx != commons.HEADING_TABLE_X[h]
                || dy != commons.HEADING_TABLE_Y[h]) return;
        if (!owner.getMap().isUserPassable(owner.getX(), owner.getY(), h)) return;
        if (!l1j.server.server.network.NativeStepControl.isBound(owner)) {
            owner.EndAutoController();
            return;
        }
        owner.getNativeStepSession().offerMove(owner.getX(), owner.getY(),
                owner.getMapId(), h, System.nanoTime());
    }

	private boolean isAutoAttackTime() {
		long temp = System.currentTimeMillis() - owner.getAutoTimeAttack();
  
		if (commons.isNonAction(owner))
			return false;
		
		
		long interval = owner.getCurrentSpriteInterval(EActionCodes.fromInt(owner.getCurrentWeapon() + 1));
		if (temp < interval) {
			return false;
		}
		if (temp >= interval) {
			owner.setAutoTimeAttack(System.currentTimeMillis());
			return true;
		}
		return false;
	}

	private boolean isAutoMoveTime() {
		long temp = System.currentTimeMillis() - owner.getAutoTimeMove();
		if (commons.isNonAction(owner))
			return false;
		long interval = owner.getCurrentSpriteInterval(EActionCodes.fromInt(owner.getCurrentWeapon()));

		if (temp < interval) {
			return false;
		}
		if (temp >= interval) {
			owner.setAutoTimeMove(System.currentTimeMillis());
			return true;
		}
		return false;
	}

	protected ItemInstance _targetItem = null;
	protected List<ItemInstance> _targetItemList = new ArrayList<ItemInstance>();

	private void PickUpItem() {
		if (_targetItem != null) {
			L1Inventory groundInventory = L1World.getInstance().getInventory(_targetItem.getX(), _targetItem.getY(),
					_targetItem.getMapId());
			if (groundInventory.checkItem(_targetItem.getItemId())) {
				onTargetItem();
			} else {
				_targetItemList.remove(_targetItem);
				_targetItem = null;
				return;
			}
		} else {
			owner.setAutoStatus(0);
		}
	}

	private void onTargetItem() {
        if (owner.getNativeStepSession().hasOutstandingMove(System.nanoTime())) return;
		try {
			if (owner.getLocation().getTileLineDistance(_targetItem.getLocation()) <= 1) {
                // Keep item pickup paced; walking to it uses the session clock.
                if (!isAutoMoveTime()) return;
				L1Inventory inv = L1World.getInstance().getInventory(_targetItem.getX(), _targetItem.getY(),
						_targetItem.getMapId());
				ItemInstance item = inv.getItem(_targetItem.getId());
				if (item != null) {
					inv.tradeItem(item.getId(), item.getCount(), owner.getInventory());
					S_AttackStatus pck = S_AttackStatus.get(owner, _targetItem.getId(), ActionCodes.ACTION_Pickup);
					owner.sendPackets(pck);
					owner.broadcastPacket(pck);
					_targetItemList.remove(_targetItem);
					_targetItem = null;
					inv = null;
					owner.setAutoDropTime(System.currentTimeMillis());
				}
			} else {
				toMoving(_targetItem.getX(), _targetItem.getY(), 0, true);
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	private void toPickupItem() {
		checkTargetItem();
		if (_targetItem == null) {
			searchTargetItem();
		}

		if (_targetItem != null) {
			owner.setAutoStatus(AUTO_STATUS_PICKUP_ITEM);
		}
	}

	private void checkTargetItem() {
		if (_targetItem == null || _targetItem.getMapId() != owner.getMapId()
				|| owner.getLocation().getTileDistance(_targetItem.getLocation()) > 15) {
			if (!_targetItemList.isEmpty()) {
				_targetItem = _targetItemList.get(0);
				_targetItemList.remove(0);
				checkTargetItem();
			} else {
				_targetItem = null;
			}
		}
	}

	private void searchTargetItem() {
		ArrayList<L1GroundInventory> gInventorys = new ArrayList<L1GroundInventory>();
		for (L1Object obj : L1World.getInstance().getVisibleObjects(owner)) {
			if (obj == null)
				continue;
			if (obj != null && obj instanceof L1GroundInventory) {
				gInventorys.add((L1GroundInventory) obj);
			}
		}
		if (gInventorys.size() == 0) {
			return;
		}

		int pickupIndex = (int) (Math.random() * gInventorys.size());
		L1GroundInventory inventory = gInventorys.get(pickupIndex);
		for (ItemInstance item : inventory.getItems()) {
			if (item == null)
				continue;
			if (owner.getInventory().checkAddItem(item, item.getCount()) == L1Inventory.OK) {
				_targetItem = item;
				_targetItemList.add(_targetItem);
			}
		}
	}

	public void removeAuto(String ment) {
		owner.toCharacterRefresh();
		if (owner != null) {
			owner.resetAuto();
			owner.sendPackets(new S_ChatPacket(owner, String.format("%s", ment), 1));
		}
		owner.EndAutoController();
	}

}
