package l1j.server.server.model.Instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.BonusDropSystem.BonusDropSystemInfo;
import l1j.server.BonusDropSystem.BonusDropSystemLoader;
import l1j.server.BonusMaps.BonusMapInfo;
import l1j.server.BonusMaps.BonusMapTable;
import l1j.server.MagicDollEventMonster.MagicDollEventMonsterLoader;
import l1j.server.Template.LORnd;
import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Templates.L1Npc;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_Karma;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.utils.CalcExp;

public class MonsterInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(MonsterInstance.class
			.getName());

	private static Random _random = new Random();

	private boolean _storeDroped; // 드롭 아이템의 독입이 완료했는지

	// 아이템 사용 처리
	@Override
	public void onItemUse() {
		if (!isActived() && _target != null) {
			useItem(USEITEM_HASTE, 40); // 40%의 확률로 헤이 파업 일부 사용
			// 아이템이 아니지만 돕펠 처리
			if (getNpcTemplate().is_doppel() && _target instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) _target;
				setName(_target.getName());
				setNameId(_target.getName());
				setTitle(_target.getTitle());
				setTempLawful(_target.getLawful());
				setCurrentSprite(targetPc.getClassId());
				for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
					pc.sendPackets(S_RemoveObject.get(this));
					pc.removeKnownObject(this);
					pc.updateObject();
				}
			}
		}
		if (getCurrentHp() * 100 / getMaxHp() < 40) { // HP가 40%
			useItem(USEITEM_HEAL, 30); // 30%의 확률로 회복 일부 사용
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		if (0 < getCurrentHp()) {
			if (getHiddenStatus() == HIDDEN_STATUS_SINK) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Hide));
			} else if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Moveup));
			}
			perceivedFrom.sendPackets(S_WorldObject.npc(this));
			onNpcAI(); // monster의 AI를 개시
			if (getBraveSpeed() == 1) { // 제대로 된 방법을 모른다
				S_SkillBrave s =  S_SkillBrave.get(getId(), 1, 600000);		
				perceivedFrom.sendPackets(s);
				perceivedFrom.broadcastPacket(s);
			}
		}
	}

	// 타겟을 찾는다
	public static int[][] _classGfxId = { { 0, 1 }, { 48, 61 }, { 37, 138 }, { 734, 1186 }, { 2786, 2796 } };

	@Override
	public void searchTarget() {
		L1PcInstance targetPlayer = null;
		MonsterInstance targetMonster = null;

		Collection<L1PcInstance> col = L1World.getInstance().getVisiblePlayer(this);

		if(col.size() > 0){
			PriorityQueue<L1PcInstance> target_q = new PriorityQueue<L1PcInstance>(col.size(), new TargetSorter());
			for (L1PcInstance pc : col) {
				if (pc == null || pc.getCurrentHp() <= 0 || pc.isDead() || pc.isGm() || pc.isMonitor() || pc.isGhost()) {
					continue;
				}

				int mapId = getMapId();
				if (mapId == 88 || mapId == 98 || mapId == 92 || mapId == 91 || mapId == 95) {
					if (!pc.isInvisble() || getNpcTemplate().is_agrocoi()) {
						target_q.offer(pc);
						continue;
					}
				}

				if ((getNpcTemplate().getKarma() < 0 && pc.getKarmaLevel() >= 1)
						|| (getNpcTemplate().getKarma() > 0 && pc.getKarmaLevel() <= -1)) {
					continue;
				}

				int spriteId = pc.getCurrentSpriteId();
				// 버릴 수 있었던 사람들의 땅업 퀘스트의 변신중은, 각 진영의 monster로부터 선제 공격받지 않는다
				if (spriteId == 6034 && getNpcTemplate().getKarma() < 0
						|| spriteId == 6035 && getNpcTemplate().getKarma() > 0
						|| spriteId == 6035 && getNpcTemplate().get_npcId() == 46070
						|| spriteId == 6035 && getNpcTemplate().get_npcId() == 46072) {
					continue;
				}

				if (!getNpcTemplate().is_agro() && !getNpcTemplate().is_agrososc() && getNpcTemplate().is_agrogfxid1() < 0
						&& getNpcTemplate().is_agrogfxid2() < 0) {
					if (pc.getLawful() < -1000) {
						target_q.offer(pc);
						continue;
					}
					continue;
				}

				if (!pc.isInvisble() || getNpcTemplate().is_agrocoi()) { // 인비지체크
					if (pc.hasSkillEffect(SkillId.셰이프_체인지)) { // 변신하고 있다
						if (getNpcTemplate().is_agrososc()) { // 변신에 대해서 액티브
							target_q.offer(pc);
							continue;
						}
					} else if (getNpcTemplate().is_agro()) { // 액티브 monster
						target_q.offer(pc);
						continue;
					}

					if (getNpcTemplate().is_agrogfxid1() >= 0 && getNpcTemplate().is_agrogfxid1() <= 4) {
						if (_classGfxId[getNpcTemplate().is_agrogfxid1()][0] == pc.getCurrentSpriteId()
								|| _classGfxId[getNpcTemplate().is_agrogfxid1()][1] == pc.getCurrentSpriteId()) {
							target_q.offer(pc);
							continue;
						}
					} else if (pc.getCurrentSpriteId() == getNpcTemplate().is_agrogfxid1()) {
						target_q.offer(pc);
						continue;
					}

					if (getNpcTemplate().is_agrogfxid2() >= 0 && getNpcTemplate().is_agrogfxid2() <= 4) {
						if (_classGfxId[getNpcTemplate().is_agrogfxid2()][0] == pc.getCurrentSpriteId()
								|| _classGfxId[getNpcTemplate().is_agrogfxid2()][1] == pc.getCurrentSpriteId()) {
							target_q.offer(pc);
							continue;
						}
					} else if (pc.getCurrentSpriteId() == getNpcTemplate().is_agrogfxid2()) {
						target_q.offer(pc);
						continue;
					}
				}
			}
			target_q.comparator();
			while (!target_q.isEmpty()) {
				L1PcInstance target = target_q.poll();
				if(target != null && !target.isDead())
					targetPlayer = target;
					break;
			}
		}
		
		/**
		 * @설명글// 추가 이후에있을지도모를 1.Monster vs Monster 2.Monster vs Guard 3.Monster vs Guardian 4.Monster vs Npc 위와같은 상황을 위해 오브젝트를 불러오도록 추가 현재는 1번만을위한 소스임 간단하게 오브젝트를 인스턴스of로 선언만해주면되게끔 설정
		 * 
		 */
		for (L1Object obj : L1World.getInstance().getVisibleObjects(this)) {
			if (obj instanceof MonsterInstance) {
				MonsterInstance mon = (MonsterInstance) obj;
				if (mon.getHiddenStatus() != 0 || mon.isDead()) {
					continue;
				}
				if (this.getNpcTemplate().get_npcId() == 45570) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45571) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45582) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45587) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될 몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45605) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될 몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45685) { // 적을 인식할 몬스터(사제)
					if (mon.getNpcTemplate().get_npcId() == 45391 || mon.getNpcTemplate().get_npcId() == 45450
							|| mon.getNpcTemplate().get_npcId() == 45482 || mon.getNpcTemplate().get_npcId() == 45569
							|| mon.getNpcTemplate().get_npcId() == 45579 || mon.getNpcTemplate().get_npcId() == 45315
							|| mon.getNpcTemplate().get_npcId() == 45647) { // 적으로 인식될 몬스터 (발록의)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45391) { // 적을 인식할 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로 인식될 몬스터 (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45450) { // 적을 인식할 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로 인식 될몬스터 (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45482) { // 적을 인식할
																	// 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로
																			// 인식될몬스터
																			// (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45569) { // 적을 인식할
																	// 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로
																			// 인식될몬스터
																			// (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45579) { // 적을 인식할 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로 인식될 몬스터 (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45315) { // 적을 인식할 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로 인식 될몬스터 (사제)
						targetMonster = mon;
						break;
					}
				}

				if (this.getNpcTemplate().get_npcId() == 45647) { // 적을 인식할 몬스터(발록)
					if (mon.getNpcTemplate().get_npcId() == 45570 || mon.getNpcTemplate().get_npcId() == 45571
							|| mon.getNpcTemplate().get_npcId() == 45582 || mon.getNpcTemplate().get_npcId() == 45587
							|| mon.getNpcTemplate().get_npcId() == 45605) { // 적으로 인식될 몬스터 (사제)
						targetMonster = mon;
						break;
					}
				}
			}
		}
		
		if (getMap().getBaseMapId() == 1936 && targetPlayer != null) {
			targetPlayer = null;
		}
		
		if (targetPlayer != null) {
			_hateList.add(targetPlayer, 0);
			_target = targetPlayer;
		}
		if (targetMonster != null) {
			_hateList.add(targetMonster, 0);
			_target = targetMonster;
		}
	}

	// 링크의 설정
	@Override
	public void setLink(L1Character cha) {
		if (cha != null && _hateList.isEmpty()) { // 타겟이 없는 경우만 추가
			_hateList.add(cha, 0);
			checkTarget();
		}
	}

	public MonsterInstance(L1Npc template) {
		super(template);
		_storeDroped = false;
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		if (!_storeDroped) // 쓸데없는 오브젝트 ID를 발행하지 않게 여기서 세트
		{
			DropTable.getInstance().setDrop(this, getInventory());
			getInventory().shuffle();
			_storeDroped = true;
		}
		setActived(false);
		startAI();
		startChat(CHAT_TIMING_APPEARANCE); // 채팅 시작
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (pc == null)
			return;
		if (getCurrentHp() > 0 && !isDead()) {
			Attack attack = new Attack(pc, this);
			if (attack.calcHit()) {
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		}
	}

	@Override
	public void ReceiveManaDamage(L1Character attacker, int mpDamage) { 
		if (mpDamage > 0 && !isDead()) {
			setHate(attacker, mpDamage);
			onNpcAI();

			if (attacker instanceof L1PcInstance) { // 동료의식을 가지는 monster의 타겟으로  설정
				serchLink((L1PcInstance) attacker, getNpcTemplate().get_family());
			}
			int newMp = getCurrentMp() - mpDamage;
			if (newMp < 0) {
				newMp = 0;
			}
			setCurrentMp(newMp);
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { // 공격으로 HP를 줄일

		// 때는 여기를 사용
		if (getCurrentHp() > 0 && !isDead()) {
			if (getHiddenStatus() != HIDDEN_STATUS_NONE) {
				return;
			}
			if (damage > 0) {
				setHate(attacker, damage);
				removeSkillEffect(SkillId.포그_오브_슬리핑);
			}
			onNpcAI();

			if (attacker instanceof L1PcInstance) { // 동료의식을 가지는 monster의 타겟으로 설정
				serchLink((L1PcInstance) attacker, getNpcTemplate().get_family());
			}
			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.setPetTarget(this);
				
				BonusDropItem(player);
				BonusMapDropItems(player);

			}
			int newHp = getCurrentHp() - damage;
			if (newHp <= 0 && !isDead()) {
				int transformId = getNpcTemplate().getTransformId();
				// 변신하지 않는 monster
				if (transformId == -1) {
					if (attacker instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) attacker;
						if (MagicDollEventMonsterLoader.getInstance().ChangeMonster(pc, this)) {
							return;
						}
					}
					setCurrentHpDirect(0);
					setDead(true);
					Death death = new Death(attacker);
					GeneralThreadPool.getInstance().execute(death);
				} else {
					transform(transformId);
				}
			}
			if (newHp > 0) {
				setCurrentHp(newHp);
				hide();
			}
		} else if (!isDead()) { // 만약을 위해
			setDead(true);
			_log.warning("경고：NPC의 HP감소 처리가 올바르게 행해지지 않은 개소가 있습니다.※혹은 최초부터 HP0");
			Death death = new Death(attacker);
			GeneralThreadPool.getInstance().execute(death);
		}
	}

	@Override
	public void setCurrentHp(int i) {
		int currentHp = i;
		if (currentHp >= getMaxHp()) {
			currentHp = getMaxHp();
		}
		setCurrentHpDirect(currentHp);

		if (getMaxHp() > getCurrentHp()) {
			startHpRegeneration();
		}
	}

	@Override
	public void setCurrentMp(int i) {
		int currentMp = i;
		if (currentMp >= getMaxMp()) {
			currentMp = getMaxMp();
		}
		setCurrentMpDirect(currentMp);

		if (getMaxMp() > getCurrentMp()) {
			startMpRegeneration();
		}
	}

	class Death implements Runnable {
		L1Character _lastAttacker;

		public Death(L1Character lastAttacker) {
			_lastAttacker = lastAttacker;
		}

		@Override
		public void run() {
			setDeathProcessing(true);
			setCurrentHpDirect(0);
			setDead(true);
			int targetobjid = getId();
			getMap().setPassable(getLocation(), true);
			broadcastPacket(S_DoActionGFX.get(targetobjid, ActionCodes.ACTION_Die));
			startChat(CHAT_TIMING_DEAD);
			if (_lastAttacker != null) {
				distributeExpDropKarma(_lastAttacker);
			}
			setDeathProcessing(false);
			setExp(0);
			setKarma(0);
			allTargetClear();
			startDeleteTimer();
			if (_lastAttacker instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) _lastAttacker;
				BonusDropItem(pc);
				BonusMapDropItems(pc);
			}

		}
	}

	private void distributeExpDropKarma(L1Character lastAttacker) {
		try {
			if (lastAttacker == null) {
				return;
			}
			L1PcInstance pc = null;
			if (lastAttacker instanceof L1PcInstance) {
				pc = (L1PcInstance) lastAttacker;
			} else if (lastAttacker instanceof PetInstance) {
				pc = (L1PcInstance) ((PetInstance) lastAttacker).getMaster();
			} else if (lastAttacker instanceof SummonInstance) {
				pc = (L1PcInstance) ((SummonInstance) lastAttacker)
						.getMaster();
			}

			if (pc != null && !pc.isDead()) {
				ArrayList<L1Character> targetList = _hateList
						.toTargetArrayList();
				ArrayList<Integer> hateList = _hateList.toHateArrayList();
				int exp = getExp();

				if (isDead()) {
					distributeDrop(pc);
					giveKarma(pc);
				}

				CalcExp.calcExp(pc, getId(), targetList, hateList, exp); // 원래는
				// 토탈
				// exp

			//} else if (lastAttacker instanceof EffectInstance) {
			} else if (lastAttacker instanceof EffectInstance) {
				ArrayList<L1Character> targetList = _hateList
						.toTargetArrayList();
				ArrayList<Integer> hateList = _hateList.toHateArrayList();
				if (hateList.size() != 0) {
					int maxHate = 0;
					for (int i = hateList.size() - 1; i >= 0; i--) {
						if (maxHate < ((Integer) hateList.get(i))) {
							maxHate = (hateList.get(i));
							lastAttacker = targetList.get(i);
						}
					}
					if (lastAttacker instanceof L1PcInstance) {
						pc = (L1PcInstance) lastAttacker;
					} else if (lastAttacker instanceof PetInstance) {
						pc = (L1PcInstance) ((PetInstance) lastAttacker)
								.getMaster();
					} else if (lastAttacker instanceof SummonInstance) {
						pc = (L1PcInstance) ((SummonInstance) lastAttacker)
								.getMaster();
					}
					int exp = getExp();
					if (isDead()) {
						distributeDrop(pc);
						giveKarma(pc);
					}
					CalcExp.calcExp(pc, getId(), targetList, hateList, exp); // 원래는
					// 토탈
					// exp
				}
			}
		} catch (Exception e) {
		}
	}

	private void distributeDrop(L1PcInstance pc) {
		ArrayList<L1Character> dropTargetList = _dropHateList
				.toTargetArrayList();
		ArrayList<Integer> dropHateList = _dropHateList.toHateArrayList();
		try {
			if (isResurrect()) {
				return;
			}
			int npcId = getNpcTemplate().get_npcId();
			if (npcId != 45640 || (npcId == 45640 && getCurrentSpriteId() == 2332)) {
				DropTable.getInstance().dropShare(MonsterInstance.this,
						dropTargetList, dropHateList, pc);
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	private void giveKarma(L1PcInstance pc) {
		int karma = getKarma();
		if (karma != 0) {
			int karmaSign = Integer.signum(karma);
			int pcKarmaLevel = pc.getKarmaLevel();
			int pcKarmaLevelSign = Integer.signum(pcKarmaLevel);
			if (pcKarmaLevelSign != 0 && karmaSign != pcKarmaLevelSign) {
				karma *= 5;
			}
			pc.addKarma((int) (karma * Config.RATE_KARMA));
			pc.sendPackets(new S_Karma(pc));
		}
	}

	public boolean is_storeDroped() {
		return _storeDroped;
	}

	public void set_storeDroped(boolean flag) {
		_storeDroped = flag;
	}

	private int _ubSealCount = 0; // UB에서 몬스터가 쓰러졌을때, 참가자에게 주어지는 용자의 표식 개수

	public int getUbSealCount() {
		return _ubSealCount;
	}

	public void setUbSealCount(int i) {
		_ubSealCount = i;
	}

	private int _ubId = 0; // UBID

	public int getUbId() {
		return _ubId;
	}

	public void setUbId(int i) {
		_ubId = i;
	}

	private void hide() {
		int npcid = getNpcTemplate().get_npcId();
		if (npcid == 45061 // 카즈드스파르트이
				|| npcid == 45161 // 스파르트이
				|| npcid == 45181 // 스파르트이
				|| npcid == 45455) { // 데드 리스 펄 토이
			if (getMaxHp() / 3 > getCurrentHp()) {
				int rnd = _random.nextInt(10);
				if (1 > rnd) {
					allTargetClear();
					setHiddenStatus(HIDDEN_STATUS_SINK);
					broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Hide));
					setStatus(13);
					broadcastPacket(S_WorldObject.npc(this));
				}
			}
		} else if (npcid == 45067 // 바레이하피
				|| npcid == 45264 // 하피
				|| npcid == 45452 // 하피
				|| npcid == 45090 // 바레이그리폰
				|| npcid == 45321 // 그리폰
				|| npcid == 45445) { // 그리폰
			if (getMaxHp() / 3 > getCurrentHp()) {
				int rnd = _random.nextInt(10);
				if (1 > rnd) {
					allTargetClear();
					setHiddenStatus(HIDDEN_STATUS_FLY);
					broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Moveup));
					setStatus(4);
					broadcastPacket(S_WorldObject.npc(this));
				}
			}
		}
	}

	public void initHide() {
		// 출현 직후가 숨는 동작
		// 움츠러드는 MOB은 일정한 확률로 음츠러든 상태에,
		// 나는 MOB은 난 상태로 해 둔다
		int npcid = getNpcTemplate().get_npcId();
		if (npcid == 45061 // 썩어가는 해골
				|| npcid == 45161 // 스파토이
				|| npcid == 45181 // 스파토이
				|| npcid == 45455) { // 난폭한 스파토이
			int rnd = _random.nextInt(3);
			if (1 > rnd) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(13);
			}
		} else if (npcid == 45067 // 계곡 하피
				|| npcid == 45264 // 하피
				|| npcid == 45452 // 하피
				|| npcid == 45090 // 계곡 그리폰
				|| npcid == 45321 // 그리폰
				|| npcid == 45445) { // 그리폰
			setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
			setStatus(4);
		}
	}

	public void initHideForMinion(L1NpcInstance leader) {
		// 그룹에 속하는 괴물의 출현 직후 숨기는 동작 (지도자와 동일하게 동작한다)
		int npcid = getNpcTemplate().get_npcId();
		if (leader.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
			if (npcid == 45061 // 카즈도스빠루토이
					|| npcid == 45161 // 스빠루토이
					|| npcid == 45181 // 스빠루토이
					|| npcid == 45455) { // 뎃도리스빠루토이
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(13);
			} else if (npcid == 45045 // 쿠레이고레무
					|| npcid == 45126 // 스토ㄴ고레무
					|| npcid == 45134 // 스토ㄴ고레무
					|| npcid == 45281) { // 기란스토ㄴ고레무
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(4);
			}
		} else if (leader.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_FLY) {
			if (npcid == 45067 // 바레하삐
					|| npcid == 45264 // 하피
					|| npcid == 45452 // 하피
					|| npcid == 45090 // 바레구리호ㄴ
					|| npcid == 45321 // Griffon
					|| npcid == 45445) { // Griffon
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
				setStatus(4);
			}
		}
	}

	public void re() {
		setDeathProcessing(true);
		setCurrentHp(0);
		setDead(true);
		getMap().setPassable(getLocation(), true);
		setDeathProcessing(false);
		setExp(0);
		setKarma(0);
		setLawful(0);
		allTargetClear();
		deleteMe();
	}

	@Override
	public void transform(int transformId) {
		super.transform(transformId);
		// DROP의 재설정
		getInventory().clearItems();
		DropTable.getInstance().setDrop(this, getInventory());
		getInventory().shuffle();
	}

	private void BonusDropItem(L1PcInstance player) {
		BonusDropSystemInfo bInfo = BonusDropSystemLoader.getInstance()
				.getBonusDropSystemInfo(this.getNpcId());
		if (bInfo != null) {
			if (player.getBonusDropNpc() != this.getId())
				player.setBonusDropNpc(this.getId());

			if (this.isDead()) {
				for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(
						this, 30)) {
					if (pc.isDead()) {
						continue;
					}
					if (pc.getBonusDropNpc() != this.getId())
						continue;
					if (LORnd.isWinning(1000000, bInfo.calc_probability(pc))) {
						ItemInstance bonusitem = ItemTable.getInstance()
								.createItem(bInfo.get_item_id());
						bonusitem.setCount(bInfo.calc_count(pc));
						bonusitem.setEnchantLevel(bInfo.get_item_enchant());
						bonusitem.setIdentified(true);//
						pc.sendPackets(new S_ServerMessage(143, this.getName(),
								bonusitem.getLogName()));
						pc.getInventory().storeTradeItem(bonusitem);
					}
					pc.setBonusDropNpc(0);
				}
			}
		}
	}

	private void BonusMapDropItems(L1PcInstance pc) {
		try {
			int mapId = getMapId();
			BonusMapInfo bInfo = BonusMapTable.getInstance().getBonusMapInfo(
					mapId);
			if (bInfo != null) {
				if (this.isDead()) {
					String[] itemid = (String[]) MJArrangeParser.parsing(
							bInfo.get_item_id(), ",",
							MJArrangeParseeFactory.createStringArrange())
							.result();
					for (int i = 0; i < itemid.length; i++) {
						if (pc.isDead()) {
							continue;
						}
						if (LORnd.isWinning(1000000, bInfo.calc_probability(i))) {
							ItemInstance dropitem = ItemTable.getInstance()
									.createItem(Integer.parseInt(itemid[i]));
							if (dropitem != null) {
								dropitem.setCount(bInfo.calc_count(i));
								pc.sendPackets(new S_ServerMessage(143, this
										.getName(), dropitem.getLogName()));

								ItemInstance inventoryItem = pc.getInventory()
										.findItemId(40308);
								int inventoryItemCount = 0;
								if (inventoryItem != null) {
									inventoryItemCount = inventoryItem
											.getCount();
								}
								if (inventoryItemCount > 1800000000) {
									if (pc.getInventory().consumeItem(40308,
											1500000000)) {
										pc.getInventory().storeItem(46010, 15);
									}
								}
								pc.getInventory().storeTradeItem(dropitem);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	class TargetSorter implements Comparator<L1PcInstance> {
		@Override
		public int compare(L1PcInstance o1, L1PcInstance o2) {
			int law1 = o1.getLawful();
			int law2 = o2.getLawful();
			if (law1 < 0 || law2 < 0) {
				if (law1 != law2)
					return law1 > law2 ? 1 : -1;
			}

			int dir1 = getLocation().getTileLineDistance(o1.getLocation());
			int dir2 = getLocation().getTileLineDistance(o2.getLocation());
			if (dir1 == dir2) {
				return LORnd.isBoolean() ? 1 : -1;
			}
			return dir1 > dir2 ? 1 : -1;
		}
	}
}
