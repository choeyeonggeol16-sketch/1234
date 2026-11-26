
package l1j.server.server.model.Instance;

import java.util.ArrayList;
import java.util.Random;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.item.drop.CContributeData;
import l1j.server.server.model.npc.dialog.NPCTalkDataTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.npc.dialog.L1NpcTalkData;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.Exp.CalcExp;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.templates.L1Npc;
public class L1GuardianInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Random _random = new Random(System.nanoTime());
	private L1GuardianInstance _npc = this;

	/**
	 * @param template
	 */
	public L1GuardianInstance(L1Npc template) {
		super(template);
	}

	/**
	 * AOI가 이미 넘겨준 PC 한 명만 SEE_CREATURE로 전달한다.
	 * 주변 player scan 없음.
	 */
	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		super.onPerceive(perceivedFrom);

		if (perceivedFrom != null) {
			NpcEventFactory.seeCreature(this, perceivedFrom);
		}
	}

	/**
	 * Guardian SEE_CREATURE 단일 후보 판정.
	 *
	 * 기존 searchTarget() 규칙:
	 * - target이 없을 때만
	 * - 살아있는 PC
	 * - GM / Ghost 제외
	 * - invis는 agrocoi일 때만
	 * - Elf는 공격하지 않음
	 * - 실제 최초 target 채택 시 $804 경고
	 */
	public boolean isSightTargetCandidate(L1Character target) {
		if (_target != null
				|| !(target instanceof L1PcInstance)) {
			return false;
		}

		final L1PcInstance pc = (L1PcInstance) target;

		if (pc.getMapId() != getMapId()
				|| pc.getCurrentHp() <= 0
				|| pc.isDead()
				|| pc.isGm()
				|| pc.isGhost()) {
			return false;
		}

		if (pc.isInvisble()
				&& !getNpcTemplate().is_agrocoi()) {
			return false;
		}

		if (pc.isElf()) {
			return false;
		}

		SC_NpcChatPacket.broadcastNpcWide(this, this, "$804", 2);

		return true;
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		setActived(false);
		startAI();
	}

	@Override
	public void onAction(L1PcInstance player) {
		if (player.getType() == 2 && player.getCurrentWeapon() == 0 && player.isElf()) {
			CAttackAction.attackGuardianBarehanded(player, this);
		} else if (getCurrentHp() > 0 && !isDead()) {
			CAttackAction.attackPcNpc(player, this, true);
		}
	}

	/** Called only after the reference design executor confirms a barehanded elf hit. */
	public boolean onGuardianBarehandedHit(L1PcInstance player) {
		if (getNpcTemplate().get_npcId() == 70848) { // tree
			int chance = _random.nextInt(100) + 1;
			if (chance <= 10) {
				player.getInventory().CreateOrAddItem(40506, 1);
				SC_ServerMessage.send(player, 143, "$755", "$794");
			} else if (chance <= 20) {
				player.getInventory().CreateOrAddItem(40507, 1);
				SC_ServerMessage.send(player, 143, "$755", "$763");
			} else if (chance <= 30) {
				player.getInventory().CreateOrAddItem(40505, 1);
				SC_ServerMessage.send(player, 143, "$755", "$770");
			} else {
				return false; // original path skips swing and commit when no tree reward
			}
		}
		if (getNpcTemplate().get_npcId() == 70850) {
			int chance = _random.nextInt(100) + 1;
			if (chance <= 30) {
				player.getInventory().CreateOrAddItem(40519, 5);
				SC_ServerMessage.send(player, 143, "$753", "$760" + " (" + 5 + ")");
			}
		}
		if (getNpcTemplate().get_npcId() == 70846) {
			int chance = _random.nextInt(100) + 1;
			if (chance <= 30) {
				player.getInventory().CreateOrAddItem(40503, 1);
				SC_ServerMessage.send(player, 143, "$752", "$769");
			}
		}
		return true;
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (player == null || this == null)
			return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		L1Object object = L1World.getInstance().findObject(getId());
		if (object == null)
			return;
		L1NpcInstance target = (L1NpcInstance) object;

		if (talking != null) {
			int pcx = player.getX(); // PC의 X좌표
			int pcy = player.getY(); // PC의 Y좌표
			int npcx = target.getX(); // NPC의 X좌표
			int npcy = target.getY(); // NPC의 Y좌표

			if (pcx == npcx && pcy < npcy) {
				setHeading(0);
			} else if (pcx > npcx && pcy < npcy) {
				setHeading(1);
			} else if (pcx > npcx && pcy == npcy) {
				setHeading(2);
			} else if (pcx > npcx && pcy > npcy) {
				setHeading(3);
			} else if (pcx == npcx && pcy > npcy) {
				setHeading(4);
			} else if (pcx < npcx && pcy > npcy) {
				setHeading(5);
			} else if (pcx < npcx && pcy == npcy) {
				setHeading(6);
			} else if (pcx < npcx && pcy < npcy) {
				setHeading(7);
			}
			SC_ChangeHeading.broadcast(this);

			if (player.getLawful() < -1000) {
				SC_NPCTalkReturn.send(player, talking, objid, 2);
			} else {
				SC_NPCTalkReturn.send(player, talking, objid, 1);
			}
			
			pauseForDialog();
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { 
		
		if(attacker == null) return;
		 

		if (attacker instanceof L1PcInstance && damage > 0) {
			L1PcInstance pc = (L1PcInstance) attacker;
			if (pc.getType() == 2 && 
					pc.getCurrentWeapon() == 0) {
			} else {
				if (getCurrentHp() > 0 && !isDead()) {
					if (damage >= 0) {
						setHate(attacker, damage);
					}
					if (damage > 0) {
						if(hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)){
							removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
						}else if (hasSkillEffect(L1SkillId.PHANTASM)){
							removeSkillEffect(L1SkillId.PHANTASM);
						}
					}
					onNpcAI();
					NpcEventFactory.OnClanAwareness(this, pc);
					if (damage > 0) {
						pc.set_pet_target(this);
					}

					int newHp = applyNpcHpDelta(-(long) damage);
					if (newHp == 0 && !isDead()) {
						onDeath(attacker);
					}

                }
			}
		}
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
	}

	@Override
	public void setCurrentMp(int i) {
		super.setCurrentMp(i);
	}

	@Override
	protected void onDeath(final L1Character lastAttacker) {
        final int deathLifeEpoch;
        synchronized (this) {
            if (!Die()) return;
            deathLifeEpoch = npcEventEpoch();
        }

        final Runnable deathContinuation = () -> {
            try {
                if (npcEventEpoch() == deathLifeEpoch && isDead()
                        && !isDestroyed() && isDeathProcessing()) {
                    processDeathRewards(lastAttacker);
                }
            } finally {
                synchronized (this) {
                    if (npcEventEpoch() == deathLifeEpoch && isDead() && !isDestroyed()) {
                        setKarma(0);
                        setLawful(0);
                        setExp(0);
                        try {
                            allTargetClear();
                        } finally {
                            DeathFinished(true);
                        }
                    }
                }
            }
        };
        try {
            setStatus(ActionCodes.ACTION_Die);
            releaseNpcTileNow();
            SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Die);
        } finally {
            SubmitDeathSettlement(deathContinuation);
        }
    }

	private void processDeathRewards(L1Character lastAttacker) {
		L1PcInstance player = null;
		if (lastAttacker instanceof L1PcInstance) {
			player = (L1PcInstance) lastAttacker;
		} else if (lastAttacker instanceof L1CompanionInstance) {
			player = ((L1CompanionInstance) lastAttacker).get_master();
		} else if (lastAttacker instanceof L1SummonInstance) {
			player = (L1PcInstance) ((L1SummonInstance) lastAttacker).getMaster();
		}
		if (player == null) {
			return;
		}

		ArrayList<L1Character> targetList = _hateList.toTargetArrayList();
		ArrayList<Integer> hateList = _hateList.toHateArrayList();
		CalcExp.calcExp(player, getId(), targetList, hateList, getExp());

		ArrayList<L1Character> dropTargetList = new ArrayList<>();
		ArrayList<Integer> dropHateList = new ArrayList<>();
		_dropHateList.copySnapshotTo(dropTargetList, dropHateList);
		try {
			// Guardian still uses the legacy untagged hate input; do not reinterpret it as HP damage.
			DropItemOnDying(CContributeData.Distribute(this, dropTargetList, dropHateList, null),
					player, null, null, getLocation());
		} catch (Exception e) {
			e.printStackTrace();
		}

		// 펫/소환수로 죽인 경우에도 해당 플레이어에게 카르마를 지급한다.
		player.addKarma((int) (getKarma() * Config.RATE_KARMA));
		SC_Karma.send(player);
	}

	@Override
	public void onFinalAction(L1PcInstance player, String action) {
	}

	public void doFinalAction(L1PcInstance player) {
	}

}
