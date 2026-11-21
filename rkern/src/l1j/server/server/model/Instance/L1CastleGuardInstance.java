package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.templates.L1Npc;

public class L1CastleGuardInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
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
	 * CastleGuard SEE_CREATURE 단일 후보 판정.
	 * 기존 searchTarget()의 실제 선공 규칙만 보존한다.
	 */
	public boolean isSightTargetCandidate(L1Character target) {
		if (!(target instanceof L1PcInstance)) {
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

		return War.isOffenseClan(pc.getClan());
	}

	public void setTarget(L1PcInstance targetPlayer) {
		if (targetPlayer != null) {
			_hateList.add(targetPlayer, 0);
			_target = targetPlayer;
		}
	}
	
	@Override
	public boolean noTarget() {
		final int homeX = getHomeX();
		final int homeY = getHomeY();

		// 노타겟 경비는 랜덤워크하지 않고 원래 자리로 복귀한다.
		if (getX() != homeX || getY() != homeY) {
			final int dir = moveDirection(homeX, homeY);

			if (dir >= 0 && dir < 8) {
				setSleepTime(setDirectionMove(dir));
			} else {
				teleport(homeX, homeY, 1);
			}

			return false;
		}

		// 원래 자리에 있고 주변 유저도 없으면 AI 종료.
		return !L1World.getInstance().hasRecognizePlayerFast(this);
	}
	
	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}

	public L1CastleGuardInstance(L1Npc template) {
		super(template);
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
	public void onAction(L1PcInstance pc) {
		if (!isDead()) {
			if (getCurrentHp() > 0) {
				CAttackAction.attackPcNpc(pc, this, true);
			} else {
				CAttackAction.hitAndSwing(pc, this);
			}
		}
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		String htmlid = null;
		String[] htmldata = null;
		boolean hascastle = false;
		String clan_name = "";
		String pri_name = "";

		if (talking != null) {
			if (npcid == 70549 || npcid == 70985 || npcid == 70656) { 
				hascastle = checkHasCastle(player,
						L1CastleLocation.KENT_CASTLE_ID);
				if (hascastle) {
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70600 || 
					npcid == 70986) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.OT_CASTLE_ID);
				if (hascastle) {
					htmlid = "orckeeper";
				} else {
					htmlid = "orckeeperop";
				}
			} else if (npcid == 70687 || npcid == 70987 || npcid == 70778) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.WW_CASTLE_ID);
				if (hascastle) { 
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			}else if (npcid == 70800
					|| 
					npcid == 70988 || npcid == 70989 || npcid == 70990
					|| npcid == 70991 || npcid == 70817) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.GIRAN_CASTLE_ID);
				if (hascastle) { 
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70862 || 
					npcid == 70992 || npcid == 70863) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.HEINE_CASTLE_ID);
				if (hascastle) { 
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70993 || 
					npcid == 70994 || npcid == 70995) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.DOWA_CASTLE_ID);
				if (hascastle) { 
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70996) { 
				hascastle = checkHasCastle(player,
						L1CastleLocation.ADEN_CASTLE_ID);
				if (hascastle) { 
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			}

			else if (npcid == 60514) { 
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() 
					== L1CastleLocation.KENT_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "ktguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60560) { 
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() 
					== L1CastleLocation.OT_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "orcguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60552 || npcid == 5155) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId()
					== L1CastleLocation.WW_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "wdguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60524 || // 공성 경비병 클릭시
					npcid == 60525 || npcid == 60529 || npcid == 7320232) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() == L1CastleLocation.GIRAN_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "grguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 70857) { 
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() 
					== L1CastleLocation.HEINE_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "heguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60530 || 
					npcid == 60531) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() 
					== L1CastleLocation.DOWA_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "dcguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60533 || 
					npcid == 60534) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() 
					== L1CastleLocation.ADEN_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "adguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 81156) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId()
					== L1CastleLocation.DIAD_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "ktguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			}

			
			if (htmlid != null) { 
				if (htmldata != null) {
					SC_NPCTalkReturn.send(player, objid, htmlid, htmldata);
				} else {
					SC_NPCTalkReturn.send(player, objid, htmlid);
				}
			} else {
				if (player.getLawful() < -1000) {
					SC_NPCTalkReturn.send(player, talking, objid, 2);
				} else {
					SC_NPCTalkReturn.send(player, talking, objid, 1);
				}
			}
		}
	}

	public void onFinalAction() {

	}

	public void doFinalAction() {

	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (getCurrentHp() > 0 && !isDead()) {
			if (damage >= 0) {
				if (!(attacker instanceof L1EffectInstance)) { 
					setHate(attacker, damage);
					
				    if (damage > 0) {
				        NpcEventFactory.onGuardAttacked(this, attacker);
				    }
				}
			}
			if (damage > 0) {
				if(hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)){
					removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
				}else if (hasSkillEffect(L1SkillId.PHANTASM)){
					removeSkillEffect(L1SkillId.PHANTASM);
				}
			}

			onNpcAI();

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance pc = (L1PcInstance) attacker;
				pc.set_pet_target(this);
			}

			int newHp = applyNpcHpDelta(-(long) damage);
			if (newHp == 0 && !isDead()) {
				onDeath(attacker);
			}

        }
	}
	@Override
	public void checkTarget(){
		//System.out.println("오버라이드 했는데 여기 진입할까?");
		if (_target == null
				|| 	(Math.abs(this.getX() - this.getHomeX())) > 20 
				||  (Math.abs(this.getY() - this.getHomeY())) > 20
				|| _target.getMapId() != getMapId()
				|| _target.getCurrentHp() <= 0
				|| _target.isDead()
				|| (_target.isInvisble() && !getNpcTemplate().is_agrocoi() && !_hateList.containsKey(_target))
				|| (_target instanceof L1SummonInstance && ((L1SummonInstance)_target).isDestroyed())
				|| (_target instanceof L1PetInstance && ((L1PetInstance)_target).isDestroyed())
				|| (_target instanceof L1CompanionInstance && ((L1CompanionInstance)_target).isDestroyed())
				) {
			if (_target != null) {
				tagertClear();
				teleport(getHomeX(), getHomeY(),getSpawn().getHeading());
				
			}
			if (!_hateList.isEmpty()) {
				_target = _hateList.getMaxHateCharacter();
				checkTarget();
			}
			
		}
	}
	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
	}

	@Override
	protected void onDeath(L1Character lastAttacker) {
		if (isDead()) {
			return;
		}

		setDeathProcessing(true);
		setCurrentHp(0);
		setDead(true);
		setStatus(ActionCodes.ACTION_Die);
		releaseNpcTileNow();
		SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Die);
		startChat(CHAT_TIMING_DEAD);
		allTargetClear();
		setDeathProcessing(false);
		startDeleteTimer();
	}

	private boolean checkHasCastle(L1PcInstance pc, int castleId) {
		boolean isExistDefenseClan = false;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (castleId == clan.getCastleId()) {
				isExistDefenseClan = true;
				break;
			}
		}
		if (!isExistDefenseClan) {
			return true;
		}

		if (pc.getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
			if (clan != null) {
				if (clan.getCastleId() == castleId) {
					return true;
				}
			}
		}
		return false;
	}

}