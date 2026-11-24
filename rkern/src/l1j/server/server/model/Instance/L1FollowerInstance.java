package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

public class L1FollowerInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

    private static final int HANDOFF_IDLE = 0, HANDOFF_PENDING = 1,
            HANDOFF_COMMITTED = 2, HANDOFF_UNCERTAIN = 3;
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
        if (!(_master instanceof L1PcInstance owner)) {
            deleteMe();
            return true;
        }
        if (questHandoff != HANDOFF_IDLE) {
            setSleepTime(1000L);
            return false;
        }
		L1NpcInstance npc = null;
		L1PcInstance pc = null;
		for (L1Object object : L1World.getInstance().getVisibleObjects(this)) {
			if (object == null)
				continue;
			if (object instanceof L1NpcInstance) {
				npc = (L1NpcInstance) object;
				switch(npc.getNpcTemplate(). get_npcId()){
				case 70740: //디가르뎅의 병사
				case 71093: //조사원
					requestHandoff(owner, 40593, 0, 0, 0, true);
					return false;
				case 71094: //엔디아
					requestHandoff(owner, 40582, 0, 0, 0, true);
					return false;
				case 71061:
				case 71062:
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32448 && pc.getX() <= 32452) // 모퉁이 모스 주변 좌표
								&& (pc.getY() >= 33048 && pc.getY() <= 33052) && (pc.getMapId() == 440)) {
							requestHandoff(owner, 40711, CQuestSystem.QUEST_CADMUS, 2, 3, true);
							return false;
						}
					}
					break;
				case 71074:
				case 71075:
					// 완전히 지쳐 버린 리자드만파이타
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32731 && pc.getX() <= 32735) // 리자드만장로 주변
			
								&& (pc.getY() >= 32854 && pc.getY() <= 32858) && (pc.getMapId() == 480)) {
							requestHandoff(owner, 40633, CQuestSystem.QUEST_LIZARD, 1, 2, true);
							return false;
						}
					}
					break;
				case 70964:
				case 70957:
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32917 && pc.getX() <= 32921) // 밧슈 주변
																		// 좌표
								&& (pc.getY() >= 32974 && pc.getY() <= 32978) && (pc.getMapId() == 410)) {
							requestHandoff(owner, 41003, CQuestSystem.QUEST_ROI, -1, 0, false);
							return false;
						}
					}
					break;
				}
			}
		}

		if (_master.isDead() || getLocation().getTileLineDistance(_master.getLocation()) > 10) {
			setParalyzed(true);
			spawn(getNpcTemplate().get_npcId(), getX(), getY(), getHeading(), getMapId());
			deleteMe();
			return true;
		} else if (_master != null && _master.getMapId() == getMapId()) {
			if (getLocation().getTileLineDistance(_master.getLocation()) > 2) {
				setSleepTime(setDirectionMove(moveDirection(_master.getX(), _master.getY())));
			}
		}
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

		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}

		startAI();
		master.addFollower(this);
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
    private void requestHandoff(L1PcInstance pc, int itemId, int questId,
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
        nextHandoffAt = now + 1000L;
        questHandoff = HANDOFF_PENDING;
        setSleepTime(1000L);
        CIOThreadCallback.getInstance().completeSession(client, () -> {
            if (isDestroyed() || isDead() || getMaster() != pc || pc.isDead()) {
                questHandoff = HANDOFF_IDLE;
                return;
            }
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, getNpcId())) {
                if (once && pc.getInventory().checkItem(itemId)) {
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
                tx.onTerminalFailure(() -> questHandoff = HANDOFF_UNCERTAIN);
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

    /** Preserve logout respawn except for a confirmed handoff. */
    public synchronized void onOwnerLogout() {
        if (isDestroyed()) return;
        setParalyzed(true);
        if (questHandoff != HANDOFF_COMMITTED)
            spawn(getNpcTemplate().get_npcId(), getX(), getY(), getHeading(), getMapId());
        deleteMe();
    }

	public void spawn(int npcId, int X, int Y, int H, short Map) {
		L1Npc l1npc = NpcTable.getInstance().getTemplate(npcId);
		if (l1npc != null) {
			try {
				L1NpcInstance mob = NpcTable.getInstance().newNpcInstance(l1npc);
				if (mob == null) return;

				mob.setId(IdFactory.getInstance().nextId());
				mob.setX(X);
				mob.setY(Y);
				mob.setHomeX(X);
				mob.setHomeY(Y);
				mob.setMap(Map);
				mob.setHeading(H);
				
				L1World.getInstance().storeAndEnterWorld(mob);
				
				if (mob instanceof L1QuestInstance newnpc) {
					newnpc.onNpcAI();
					newnpc.getLight().turnOnOffLight();
					newnpc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
