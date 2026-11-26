package l1j.server.server.model.Instance;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.character.action.CAttackAction;

import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

public class L1FollowerInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

    private static final int HANDOFF_IDLE = 0, HANDOFF_PENDING = 1,
            HANDOFF_COMMITTED = 2, HANDOFF_UNCERTAIN = 3, HANDOFF_DURABLE = 4;
    // NPC owner admits; CIO owner/receipt publishes only the terminal state.
    private volatile int questHandoff;
    private long nextHandoffAt;

	@Override
	public boolean noTarget() {
        if (isDestroyed()) return true;
        if (questHandoff == HANDOFF_COMMITTED) {
            deleteMe();
            return true;
        }
        final L1PcInstance owner = _master;
        if (owner == null) {
            deleteMe();
            return true;
        }
        if (questHandoff != HANDOFF_IDLE) {
            setSleepTime(1000L);
            return false;
        }
        if (!ownerStillNear(owner)) {
            onOwnerLogout();
            return true;
        }

        final int followerId = getNpcId();
        for (L1Object object : CWorld.getInstance().getVisibleObjects(this)) {
            if (!(object instanceof L1NpcInstance recipient) || !validRecipient(recipient)) continue;
            switch (followerId) {
                case 71093:
                    requestHandoff(owner, recipient, 40593, 0, 0, 0, true);
                    return false;
                case 71094:
                    requestHandoff(owner, recipient, 40582, 0, 0, 0, true);
                    return false;
                case 71062:
                    if (handoffPositionStillValid(owner, CQuestSystem.QUEST_CADMUS)) {
                        requestHandoff(owner, recipient, 40711, CQuestSystem.QUEST_CADMUS, 2, 3, true);
                        return false;
                    }
                    break;
                case 71075:
                    if (handoffPositionStillValid(owner, CQuestSystem.QUEST_LIZARD)) {
                        requestHandoff(owner, recipient, 40633, CQuestSystem.QUEST_LIZARD, 1, 2, true);
                        return false;
                    }
                    break;
                case 70957:
                    if (handoffPositionStillValid(owner, CQuestSystem.QUEST_ROI)) {
                        requestHandoff(owner, recipient, 41003, CQuestSystem.QUEST_ROI, -1, 0, false);
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }

        if (getLocation().getTileLineDistance(owner.getLocation()) > 2)
            setSleepTime(setDirectionMove(moveDirection(owner.getX(), owner.getY())));
		return false;
	}
	
	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}

	public L1FollowerInstance(L1Npc template, L1NpcInstance target, L1PcInstance master) {
		super(template);

		_master = master;
		setId(IdFactory.getInstance().nextId());

		setMaster(master);
		setX(target.getX());
		setY(target.getY());
		setMap(target.getMapId());
		setHeading(target.getHeading());
		setLightSize(target.getLightSize());
		setMaxHp(500);
		setCurrentHp(500);

		target.setParalyzed(true);
		target.deleteMe();

		CWorld.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : CWorld.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}

		master.addFollower(this);
		startAI();
	}

	@Override
	public synchronized void deleteMe() {
		if (_master != null) _master.removeFollower(this);
		super.deleteMe();
	}

	@Override
	public void onAction(L1PcInstance pc) {
		CAttackAction.attackPcNpc(pc, this, true);
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (isDead()) {
			return;
		}
		switch(getNpcTemplate(). get_npcId()){
		case 71093:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "searcherk2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "searcherk4");
			}
			break;
		case 71094:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "endiaq2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "endiaq4");
			}
			break;
		case 71062:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "kamit2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "kamit1");
			}
			break;
		case 71075:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "llizard2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "llizard1a");
			}
			break;
		case 70957:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "roi2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "roi2");
			}
			break;
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		
		if (l1j.server.server.model.network.core.sc.packet.SC_FollowerPack.send(perceivedFrom, this)) {
			perceivedFrom.addKnownObject(this);
		}
	}

    /** Arrival is detected on the NPC owner. Submit on the player's CIO
     * owner; only the NPC owner consumes success and deletes this actor. */
    private void requestHandoff(L1PcInstance pc, L1NpcInstance recipient, int itemId, int questId,
            int requiredStep, int nextStep, boolean once) {
        final long now = System.currentTimeMillis();
        if (now < nextHandoffAt || questHandoff != HANDOFF_IDLE) {
            setSleepTime(1000L);
            return;
        }
        final var client = pc.getNetConnection();
        if (client == null || !client.isConnected() || client.getActiveChar() != pc) {
            setSleepTime(1000L);
            return;
        }
        final long recipientId = CWorld.getInstance().smartIdOf(recipient);
        if (recipientId == 0L) return;
        nextHandoffAt = now + 1000L;
        questHandoff = HANDOFF_PENDING;
        setSleepTime(1000L);
        CIOThreadCallback.getInstance().completeSession(client, () -> {
            if (isDestroyed() || isDead() || getMaster() != pc || !ownerStillNear(pc)
                    || CWorld.getInstance().findObjectBySmartId(recipientId) != recipient
                    || !validRecipient(recipient) || !handoffPositionStillValid(pc, questId)) {
                questHandoff = HANDOFF_IDLE;
                return;
            }
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, getNpcId())) {
                final int followerId = getNpcId();
                if ((followerId == 71093 && (!pc.isKnight()
                        || pc.getQuest().get_step(CQuestSystem.QUEST_LEVEL45) != 4))
                        || (followerId == 71094 && (!pc.isDarkelf()
                        || pc.getQuest().get_step(CQuestSystem.QUEST_LEVEL50) != 1))) {
                    questHandoff = HANDOFF_COMMITTED;
                    return;
                }
                if (once && pc.getInventory().HaveItemByClassId(itemId)) {
                    questHandoff = HANDOFF_COMMITTED;
                    return;
                }
                final int expected = questId == 0 ? 0 : pc.getQuest().get_step(questId);
                if (requiredStep >= 0 && questId != 0 && expected != requiredStep) {
                    questHandoff = HANDOFF_COMMITTED;
                    return;
                }
                final L1ItemInstance reward = tx.grant(itemId, 1);
                if (reward == null) {
                    questHandoff = HANDOFF_IDLE;
                    return;
                }
                if (questId != 0)
                    CQuestSystem.StageQuestStep(tx, pc.getId(), questId, expected, nextStep, 0);
                // Unknown or committed-but-not-replayed outcomes must not retry.
                // A confirmed rollback invokes onFailure and may retry next tick.
                tx.onDurableCommit(() -> questHandoff = HANDOFF_DURABLE);
                tx.onTerminalFailure(() -> {
                    if (questHandoff == HANDOFF_PENDING)
                        questHandoff = HANDOFF_UNCERTAIN;
                });
                tx.onFailure(() -> questHandoff = HANDOFF_IDLE);
                tx.commitAfterInventoryRelease(() -> {
                    if (questId != 0) pc.getQuest().applyCommittedStepIfLoaded(questId, nextStep);
                }, () -> {
                    questHandoff = HANDOFF_COMMITTED;
                    SC_ServerMessage.send(pc, 403, reward.getLogName());
                });
            } catch (RuntimeException rejected) {
                questHandoff = HANDOFF_IDLE;
            }
        }, failure -> {
            if (failure != null && questHandoff == HANDOFF_PENDING)
                questHandoff = HANDOFF_IDLE;
        });
    }

    private boolean ownerStillNear(L1PcInstance pc) {
        return !pc.isDead() && getMapId() == pc.getMapId() && getMap() == pc.getMap()
                && getLocation().getTileLineDistance(pc.getLocation()) <= 10;
    }

    /** Keep the original recipient IDs, but bind them to the correct escort.
     * A different player's follower is never a valid delivery NPC. */
    private static boolean matchesHandoffNpc(int followerId, int recipientId) {
        return switch (followerId) {
            case 71093 -> recipientId == 70740 || recipientId == 71093;
            case 71094 -> recipientId == 71094;
            case 71062 -> recipientId == 71061 || recipientId == 71062;
            case 71075 -> recipientId == 71074 || recipientId == 71075;
            case 70957 -> recipientId == 70964 || recipientId == 70957;
            default -> false;
        };
    }

    private boolean validRecipient(L1NpcInstance recipient) {
        return recipient != this && !(recipient instanceof L1FollowerInstance)
                && !recipient.isDestroyed() && !recipient.isDead()
                && recipient.getMapId() == getMapId() && recipient.getMap() == getMap()
                && matchesHandoffNpc(getNpcId(), recipient.getNpcId())
                && getLocation().isInScreen(recipient.getLocation());
    }

    /** Recheck the existing arrival coordinates on the receiving PC owner. */
    private boolean handoffPositionStillValid(L1PcInstance pc, int questId) {
        if (questId == 0) return true;
        if (getMapId() != pc.getMapId()
                || getLocation().getTileLineDistance(pc.getLocation()) >= 3) return false;
        final int x = pc.getX(), y = pc.getY(), map = pc.getMapId();
        return switch (questId) {
            case CQuestSystem.QUEST_CADMUS -> map == 440 && x >= 32448 && x <= 32452 && y >= 33048 && y <= 33052;
            case CQuestSystem.QUEST_LIZARD -> map == 480 && x >= 32731 && x <= 32735 && y >= 32854 && y <= 32858;
            case CQuestSystem.QUEST_ROI -> map == 410 && x >= 32917 && x <= 32921 && y >= 32974 && y <= 32978;
            default -> false;
        };
    }

    /** Preserve logout respawn except for a confirmed handoff. */
    public synchronized void onOwnerLogout() {
        if (isDestroyed()) return;
        setParalyzed(true);
        if (questHandoff != HANDOFF_COMMITTED && questHandoff != HANDOFF_DURABLE)
            spawn(getNpcTemplate().get_npcId(), getX(), getY(), getHeading(), getMapId());
        deleteMe();
    }

	public void spawn(int npcId, int X, int Y, int H, short Map) {
		L1Npc l1npc = CObjectDB.GetNPC(npcId);
		if (l1npc != null) {
			try {
				L1NpcInstance mob = CObjectDB.CreateNPC(l1npc);
				if (mob == null) return;

				mob.setId(IdFactory.getInstance().nextId());
				mob.setX(X);
				mob.setY(Y);
				mob.setHomeX(X);
				mob.setHomeY(Y);
				mob.setMap(Map);
				mob.setHeading(H);
				
				CWorld.getInstance().storeAndEnterWorld(mob);
				
				if (mob instanceof L1QuestInstance newnpc) {
					newnpc.onNpcAI();
					newnpc.refreshLight();
					newnpc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
