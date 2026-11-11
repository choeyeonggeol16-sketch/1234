
package l1j.server.server.model.Instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import I1j.server.server.model.Thread.NpcChatTimer;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1NpcChat;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.Astar.NpcAStar;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1MobSkillUse;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ChangeShape;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_MoveCharPacket;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CommonUtil;

public class L1NpcInstance extends L1Character {
	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(L1NpcInstance.class.getName());
	public static final int HIDDEN_STATUS_NONE = 0;                  
	public static final int HIDDEN_STATUS_SINK = 1;                   
	public static final int HIDDEN_STATUS_FLY = 2;

	public static final int CHAT_TIMING_APPEARANCE = 0;
	public static final int CHAT_TIMING_DEAD = 1;
	public static final int CHAT_TIMING_HIDE = 2;

	private L1Npc _npcTemplate;
	private L1Spawn _spawn;
	private int _spawnNumber; // L1Spawn로 관리되고 있는 넘버
	private int _petcost;
	protected L1Inventory _inventory = new L1Inventory();
	private L1MobSkillUse mobSkill;
	private static Random _random = new Random(System.nanoTime());
	private boolean firstFound = true; 	// 대상을 처음으로 발견했을 때.(텔레포트용)
	protected static int courceRange = 30;
	private int _drainedMana = 0;
	private int _drainedHealth = 0; // 피 흡수를 위해 추가
	private boolean _rest = false;
	private boolean _isResurrect;
	private int _randomMoveDistance = 0;
	private int _randomMoveDirection = 0;
	private boolean _isTradingInPrivateShop = false;
	public boolean isTradingInPrivateShop() { return _isTradingInPrivateShop; }
	public void setTradingInPrivateShop(boolean flag) { _isTradingInPrivateShop = flag; }
	private boolean _aiRunning = false; // AI가 실행중인가
	private boolean _actived = false; // NPC가 액티브한가
	private boolean _firstAttack = false; // 파스트앗타크 되었는지
	private long _sleep_time; // AI를 정지하는 시간(ms) ※행동을 일으켰을 경우에 소요하는 시간을 세트
	protected L1HateList _hateList = new L1HateList();
	protected L1HateList _dropHateList = new L1HateList(); 
	protected List<ItemInstance> _targetItemList = new ArrayList<ItemInstance>();
	protected L1Character _target = null; // 현재의 타겟
	protected ItemInstance _targetItem = null; // 현재의 타겟 아이템
	protected L1Character _master = null; // 주인 or그룹리더-
	private L1MobGroupInfo _mobGroupInfo = null;
	private int _mobGroupId = 0;
	private DeleteTimer _deleteTask;	// 죽고 나서 사라질 때까지의 시간 계측용
	private ScheduledFuture<?> _future = null;
	private Map<Integer, Integer> _digestItems;
	public boolean _digestItemRunning = false;
	private boolean _발묶임상태 = false;

	public boolean 발묶임상태() {
		return _발묶임상태;
	}
	public void set발묶임상태(boolean flag) {
		_발묶임상태 = flag;
	}
	public static final int MOVE_TYPE_NORMAL = 0;
	public static final int MOVE_TYPE_ASTAR = 1;
	public static final int MOVE_TYPE_ASTAR_FAIL = 2;

	protected int _moveType = MOVE_TYPE_NORMAL;

	public NpcAStar _aStar;

	public L1Character ActiveTarget = null;
	public final HashSet<Integer> backingTargets = new HashSet<>();

	public L1NpcInstance(L1Npc template) {
		setStatus(0);
		setMoveSpeed(0);
		setDead(false);
		setreSpawn(false);
		_aStar = new NpcAStar(getMap(), new Point(0, 0));
		if (template != null) {
			setting_template(template);
		}
	}

	private double calcRandomVal(int seed, int ranval, double rate) {
		return rate * (ranval - seed);
	}	


	// 지정의 템플릿으로 각종치를 초기화
	protected void setting_template(L1Npc template) {
		_npcTemplate = template;
		int randomlevel = 0;
		double rate = 0;
		double diff = 0;
		setName(template.get_name());
		setNameId(template.get_nameid());

		int level = template.get_level();
		if (template.get_randomlevel() != 0) {
			randomlevel = _random.nextInt(template.get_randomlevel() - level + 1);
			diff = template.get_randomlevel() - level;
			rate = randomlevel / diff;
			randomlevel += template.get_level();
			level = randomlevel;
		}
		setLevel(level);

		int ac = template.get_ac();
		if (template.get_randomac() != 0) {
			ac = (int) (ac + calcRandomVal(ac, template.get_randomac(), rate));
		}
		this.ac.setAc(ac);
		if (template.get_randomlevel() == 0) {
			ability.setStr(template.get_str());
			ability.setCon(template.get_con());
			ability.setDex(template.get_dex());
			ability.setInt(template.get_int());
			ability.setWis(template.get_wis());
			resistance.setBaseMr(template.get_mr());
		} else {
			ability.setStr((byte) Math.min(template.get_str() + diff, 127));
			ability.setCon((byte) Math.min(template.get_con() + diff, 127));
			ability.setDex((byte) Math.min(template.get_dex() + diff, 127));
			ability.setInt((byte) Math.min(template.get_int() + diff, 127));
			ability.setWis((byte) Math.min(template.get_wis() + diff, 127));
			resistance.setBaseMr((byte) Math.min(template.get_mr() + diff, 127));

			addMeleeHit((int) diff * 2);
			addMeleeDmg((int) diff * 2);
		}

		int hp = template.get_hp();
		if (template.get_randomhp() != 0) {
			hp = (int) (hp + calcRandomVal(hp, template.get_randomhp(), rate));
		}
		setMaxHp(hp);
		setCurrentHp(getMaxHp());

		int mp = template.get_mp();
		if (template.get_randommp() != 0) {
			mp = (int) (mp + calcRandomVal(mp, template.get_randommp(), rate));
		}
		setMaxMp(mp);
		setCurrentMp(mp);

		setAgro(template.is_agro());
		setAgrocoi(template.is_agrocoi());
		setAgrososc(template.is_agrososc());
		setCurrentSprite(template.get_gfxid());
		if (template.get_randomexp() == 0) {
			setExp(template.get_exp());
		} else {
			setExp(template.get_randomexp() + randomlevel);
		}

		int lawful = template.get_lawful();
		if (template.get_randomlawful() != 0) {
			lawful = (int) (lawful + calcRandomVal(lawful, template.get_randomlawful(), rate));
		}
		setLawful(lawful);
		setTempLawful(lawful);
		setPickupItem(template.is_picupitem());
		if (template.is_bravespeed()) {
			setBraveSpeed(1);
		} else {
			setBraveSpeed(0);
		}
		if (template.get_digestitem() > 0) {
			_digestItems = new HashMap<Integer, Integer>();
		}
		setKarma(template.getKarma());
		setLightSize(template.getLightSize());

		mobSkill = new L1MobSkillUse(this);
	}
	class NpcAI implements Runnable {
		public void start() {
			setAiRunning(true);
			GeneralThreadPool.getInstance().schedule(NpcAI.this, 1000);
		}

		private void stop() {
			if (mobSkill != null) {
				try {
					mobSkill.resetAllSkillUseCount();
				} catch (Exception e) {}
			}
			GeneralThreadPool.getInstance().schedule(new DeathSyncTimer(), 0);
		}

		private void schedule(long delay) {
			GeneralThreadPool.getInstance().schedule(NpcAI.this, delay);
		}
		@Override
		public void run() {
			try {
				synchronized (synchObject) {
					if (notContinued()) {
						stop();
						return;
					}
					if (0 < _paralysisTime) {
						schedule(_paralysisTime);
						_paralysisTime = 0;
						setParalyzed(false);
						return;
					} else if (isParalyzed() || isSleeped() || hasSkillEffect(SkillId.상태_동결)) {
						schedule(200);
						return;
					}
					if (!AIProcess()) { // AI를 계속해야 하는 것이면, 다음의 실행을 스케줄 해, 종료
						schedule(getSleepTime());
						return;
					}
				}
				stop();
			} catch (Exception e) {
				System.out.println("NPC ID : " + getNpcTemplate().get_npcId());
				_log.log(Level.WARNING, "NpcAI로 예외가 발생했습니다.", e);
			}
		}

		private boolean notContinued() {
			return _destroyed || isDead() || getCurrentHp() <= 0 || getHiddenStatus() != HIDDEN_STATUS_NONE;
		}
	}

	protected void startAI() {
		new NpcAI().start();
	}

	class DeathSyncTimer implements Runnable {
		private void schedule(long delay) {
			GeneralThreadPool.getInstance().schedule(DeathSyncTimer.this, delay);
		}

		@Override
		public void run() {
			if (isDeathProcessing()) {
				schedule(getSleepTime());
				return;
			}
			allTargetClear();
			setAiRunning(false);
		}
	}

	public void 몬스터Teleport() {// 제자리 텔레포트
		int lvl = this.getLevel();
		if (lvl >= Config.NPCMAX) {
			if (this instanceof MonsterInstance) {
				if (getLocation().getTileLineDistance(new Point(getHomeX(), getHomeY())) > Config.NPCLOCATION) {
					teleport(getHomeX(), getHomeY(), getHeading());
				}
			}
		}
	}

	private boolean AIProcess() {

		if (checkCondition()) {
			return false;
		}
		setSleepTime(300);

		checkTarget();
		몬스터Teleport();
		if (_target == null && _master == null) {
			searchTarget();
		}

		onItemUse();

		if (_target == null) {


			// 타겟이 없는 경우
			backingTargets.clear();
			checkTargetItem();
			if (isPickupItem() && _targetItem == null) {
				// 아이템 줍는 아이의 경우는 아이템을 찾아 본다
				searchTargetItem();
			}

			if (_targetItem == null) {
				if (noTarget()) {
					return true;
				}
			} else {
				L1Inventory groundInventory = L1World.getInstance().getInventory(_targetItem.getX(), _targetItem.getY(),
						_targetItem.getMapId());
				if (groundInventory.checkItem(_targetItem.getItemId())) {
					onTargetItem();
				} else {
					_targetItemList.remove(_targetItem);
					_targetItem = null;
					setSleepTime(1000);
					return false;
				}
			}
			ActiveTarget = null;
		} else { // 타겟이 있는 경우
			if (getHiddenStatus() == HIDDEN_STATUS_NONE) {
				onTarget();
			} else {
				ActiveTarget = null;
				allTargetClear();
				return true;
			}
		}

		return false; // AI처리 속행
	}

	// 아이템 사용 처리(Type에 의해 상당히 다르므로 오바라이드로 실장)
	public void onItemUse() {
	}

	// 타겟을 찾는다(Type에 의해 상당히 다르므로 오바라이드로 실장)
	public void searchTarget() {
	}

	public boolean checkCondition() {
		return false;
	}
	public void checkTarget() {
		if (_target == null || _target.getMapId() != getMapId() || _target.getCurrentHp() <= 0 || _target.isDead()
				|| (_target.isInvisble() && !getNpcTemplate().is_agrocoi() && !_hateList.containsKey(_target))) {
			if (_target != null) {
				tagertClear();
			}
			if (!_hateList.isEmpty()) {
				_target = _hateList.getMaxHateCharacter();
				checkTarget();
			}
		}
	}

	// 유효한 타겟 아이템이나 확인 및 다음의 타겟 아이템을 설정
	public void checkTargetItem() {
		if (_targetItem == null || _targetItem.getMapId() != getMapId()
				|| getLocation().getTileDistance(_targetItem.getLocation()) > 15) {
			if (!_targetItemList.isEmpty()) {
				_targetItem = _targetItemList.get(0);
				_targetItemList.remove(0);
				checkTargetItem();
			} else {
				_targetItem = null;
			}
		}
	}

	/**
	 * 엔피시가 인식한 타겟팅 
	 */
	public void onTarget() {
		try {
			setActived(true);
			_targetItemList.clear();
			_targetItem = null;
			L1Character target = _target;
			if (_target == null || target == null) {
				return;
			}
			if (hasSkillEffect(SkillId.다크니스) || hasSkillEffect(SkillId.커스_블라인드) 
					&& getLocation().getTileLineDistance(target.getLocation()) > 1) {
				return;
			}
			/**
			 * 공격은 없고 움직이기만 하는 엔피시
			 */
			if (!isActionable() && isMovable()) {
				int escapeDistance = 15;
				if (getLocation().getTileLineDistance(target.getLocation()) > escapeDistance) {
					tagertClear();
				} else {
					int dir = targetReverseDirection(target.getX(), target.getY());
					dir = checkObject(getX(), getY(), getMapId(), dir);
					setSleepTime(setDirectionMove(dir));
				}
			} else {
				boolean isSkillUse = false;
				if (mobSkill != null) {
					try {
						if (target != null) {
							isSkillUse = mobSkill.skillUse(target);
							if (isSkillUse == true) {
								setSleepTime(mobSkill.getSleepTime());
								return;
							}
						}
					} catch (Exception e) {
						System.out.println("[isSkillUse]: " + isSkillUse);
						System.out.println("[mobSkill]: " + mobSkill);
						System.out.println("[mobSkill.skillUse]: " + mobSkill.skillUse(target));
						System.out.println("[mobSkill.getSleepTime()]: " + mobSkill.getSleepTime());
						e.printStackTrace();
					}
				}

				/**
				 * 어택 거리
				 */
				if (isAttackPosition(target, getNpcTemplate().get_ranged())) {
					setHeading(targetDirection(target.getX(), target.getY()));
					attackTarget(target);
				} else {
					if (isMovable()) {
						// 타겟과의 거리가 너무 먼경우  같은 맵이 아닐때 타겟을 잃는다.
						int distance = getLocation().getTileDistance(target.getLocation());				
						if (target.getMapId() != getMapId() && distance > 18) {
							tagertClear();
							return;
						}
						// 몬스터가 타겟을 처음 발견했을때 텔레포트를 할것인지 거리는 3이상 13칸 이내에서 텔레포트를 함 
						if (firstFound == true && getNpcTemplate().isTeleport() && distance > 3 && distance < 13) {
							if (nearTeleport(target.getX(), target.getY()) == true) {
								firstFound = false;
								return;
							} 
						}
						// 도망 텔레포트 
						if (getNpcTemplate().isTeleport() && 20 > _random.nextInt(100) && getCurrentMp() >= 30
								&& distance > 6 && distance < 15) {
							if (nearTeleport(target.getX(), target.getY()) == true) {
								return;
							}
						}
						if (발묶임상태()) {
							return;
						}
						switch (_moveType) {
						case MOVE_TYPE_NORMAL:
							doNormalMove();
							break;
						case MOVE_TYPE_ASTAR:
							doAStarMove();
							break;
						case MOVE_TYPE_ASTAR_FAIL:
							doAStarFailMove();
							break;
						}
					} else {
						tagertClear();
					}
				}
			}

		} catch (Exception e) {
			// System.out.println("엔피씨에러3 : " + this.getNpcId());
		}
	}

	public void setHate(L1Character cha, int hate) {
		if (cha != null && cha.getId() != getId()) {
			if (!isFirstAttack() && hate != 0) {
				// hate += 20; // FA헤이트
				hate += getMaxHp() / 10; // FA헤이트
				setFirstAttack(true);
			}

			_hateList.add(cha, hate);
			_dropHateList.add(cha, hate);
			_target = _hateList.getMaxHateCharacter();
			_moveType = MOVE_TYPE_NORMAL;
			checkTarget();
		}
	}

	// 링크의 설정
	public void setLink(L1Character cha) {
	}

	// 동료의식에 의해 액티브하게 되는 NPC의 검색(공격자가 플레이어만 유효)
	public void serchLink(L1PcInstance targetPlayer, int family) {
		List<L1Object> targetKnownObjects = targetPlayer.getKnownObjects();
		L1NpcInstance npc = null;
		L1MobGroupInfo mobGroupInfo = null;
		for (Object knownObject : targetKnownObjects) {
			if (knownObject == null) {
				continue;
			}
			if (knownObject instanceof L1NpcInstance) {
				npc = (L1NpcInstance) knownObject;
				if (npc.getNpcTemplate() != null && npc.getNpcTemplate().get_agrofamily() > 0) {
					if (npc.getNpcTemplate().get_agrofamily() == 1) {
						if (npc.getNpcTemplate().get_family() == family) {
							npc.setLink(targetPlayer);
						}
					} else {
						npc.setLink(targetPlayer);
					}
				}
				mobGroupInfo = getMobGroupInfo();
				if (mobGroupInfo != null) {
					if (getMobGroupId() != 0 && getMobGroupId() == npc.getMobGroupId()) {
						npc.setLink(targetPlayer);
					}
				}
			}
		}
	}



	// 목표를 지정의 스킬로 공격
	public void attackTarget(L1Character target) {
		if (target == null) {
			return;
		}
		if (target instanceof L1PcInstance) {
			L1PcInstance player = (L1PcInstance) target;
			if (player.getTeleport()) { // 텔레포트 처리중
				return;
			}
		} else if (target instanceof PetInstance) {
			PetInstance pet = (PetInstance) target;
			L1Character cha = pet.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance player = (L1PcInstance) cha;
				if (player.getTeleport()) { // 텔레포트 처리중
					return;
				}
			}
		} else if (target instanceof SummonInstance) {
			SummonInstance summon = (SummonInstance) target;
			L1Character cha = summon.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance player = (L1PcInstance) cha;
				if (player.getTeleport()) { // 텔레포트 처리중
					return;
				}
			}
		}
		if (this instanceof PetInstance) {
			PetInstance pet = (PetInstance) this;
			L1Character cha = pet.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance player = (L1PcInstance) cha;
				if (player.getTeleport()) { // 텔레포트 처리중
					return;
				}
			}
		} else if (this instanceof SummonInstance) {
			SummonInstance summon = (SummonInstance) this;
			L1Character cha = summon.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance player = (L1PcInstance) cha;
				if (player.getTeleport()) { // 텔레포트 처리중
					return;
				}
			}
		}

		if (target instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) target;
			if (npc.getHiddenStatus() != HIDDEN_STATUS_NONE) { // 지중에 기어들고 있는지, 날고 있다
				allTargetClear();
				return;
			}
		}

		boolean isCounterBarrier = false;
		Attack attack = new Attack(this, target);
		if (attack.calcHit()) {
			if (target.hasSkillEffect(SkillId.카운터_배리어)) {			
				boolean isProbability = false;
				if (CommonUtil.randomRange(1, 100) <= Config.카운터_배리어_발동_확률) {
					isProbability = true;
				}
				boolean isShortDistance = attack.isShortDistance();
				if (isProbability && isShortDistance) {
					isCounterBarrier = true;
				}
			}		
			if (!isCounterBarrier) {
				attack.calcDamage();
			}
		}
		if (isCounterBarrier) {
			attack.actionCounterBarrier();
			attack.commitCounterBarrier(); 
		} else {
			attack.action();
			attack.commit();
		}
		// 칠흑의 해골 궁수 활질이 비약적으로 빠르므로 수정.
		if(getNpcId() == 5000104) {
			setSleepTime(getCurrentSpriteInterval(EActionCodes.alt_attack));
		} else {
			setSleepTime(getCurrentSpriteInterval(EActionCodes.attack));
		}

	}

	// 타겟 아이템을 찾는다
	public void searchTargetItem() {
		ArrayList<L1GroundInventory> gInventorys = new ArrayList<L1GroundInventory>();

		for (L1Object obj : L1World.getInstance().getVisibleObjects(this)) {
			if (obj == null)
				continue;
			if (obj != null && obj instanceof L1GroundInventory) {
				gInventorys.add((L1GroundInventory) obj);
			}
		}
		if (gInventorys.size() == 0) {
			return;
		}
		// 줍는 아이템(의 목록)을 랜덤으로 선정
		int pickupIndex = (int) (Math.random() * gInventorys.size());
		L1GroundInventory inventory = gInventorys.get(pickupIndex);
		for (ItemInstance item : inventory.getItems()) {
			if (item == null || item.getItemId() == 40308) {
				continue;
			}
			if (getInventory().checkAddItem(item, item.getCount()) == L1Inventory.OK) { // 있을 수있을 경우 대상 항목 이외에
				_targetItem = item;
				_targetItemList.add(_targetItem);
			}
		}
	}

	public void searchItemFromAir() {
		ArrayList<L1GroundInventory> gInventorys = new ArrayList<L1GroundInventory>();

		for (L1Object obj : L1World.getInstance().getVisibleObjects(this, 4)) {
			if (obj == null)
				continue;
			if (obj != null && obj instanceof L1GroundInventory && ((L1GroundInventory) obj).getSize() > 0) {
				gInventorys.add((L1GroundInventory) obj);
			}
		}
		if (gInventorys.size() == 0) {
			return;
		}

		int pickupIndex = (int) (Math.random() * gInventorys.size());
		L1GroundInventory inventory = gInventorys.get(pickupIndex);
		for (ItemInstance item : inventory.getItems()) {
			if (item == null) {
				continue;
			}
			if (item.getItem().getType() == L1Item.TYPE_ETCITEM_POTION
					|| item.getItem().getType() == L1Item.TYPE_ETCITEM_FOOD) { 
				if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
					setHiddenStatus(HIDDEN_STATUS_NONE);
					broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Movedown));
					setStatus(0); 
					broadcastPacket(S_WorldObject.npc(this));
					onNpcAI();
					startChat(CHAT_TIMING_HIDE);
					_targetItem = item;
					_targetItemList.add(_targetItem);
				}
			}
		}
	}
	public static void shuffle(L1Object[] arr) {
		for (int i = arr.length - 1; i > 0; i--) {
			int t = (int) (Math.random() * i);

			// 선택된 값과 교환한다
			L1Object tmp = arr[i];
			arr[i] = arr[t];
			arr[t] = tmp;
		}
	}
	// 타겟 아이템이 있는 경우의 처리
	public void onTargetItem() {
		if (getLocation().getTileLineDistance(_targetItem.getLocation()) == 0) {
			pickupTargetItem(_targetItem);
		} else {
			int dir = moveDirection(_targetItem.getX(), _targetItem.getY());
			if (dir == -1) {
				_targetItemList.remove(_targetItem);
				_targetItem = null;
			} else {
				setSleepTime(setDirectionMove(dir));
			}
		}
	}

	// 아이템을 줍는다
	public void pickupTargetItem(ItemInstance targetItem) {
		if (targetItem == null) {
			return;
		}
		targetItem.setGiveItem(true);
		L1Inventory groundInventory = L1World.getInstance().getInventory(targetItem.getX(), targetItem.getY(),
				targetItem.getMapId());
		ItemInstance item = groundInventory.tradeItem(targetItem, targetItem.getCount(), getInventory());
		if (item == null) {
			return; 
		}
		light.turnOnOffLight();
		onGetItem(item);
		_targetItemList.remove(_targetItem);
		_targetItem = null;
		setSleepTime(1000);
	}
	// 타겟이 없는 경우의 처리 (돌아가 값은 AI처리를 종료할지 어떨지)
	public boolean noTarget() {
		if (hasSkillEffect(SkillId.다크니스) || hasSkillEffect(SkillId.커스_블라인드) && getLocation().getTileLineDistance(_target.getLocation()) > 1) {
			return false;
		}
		if (_master != null && _master.getMapId() == getMapId()
				&& getLocation().getTileLineDistance(_master.getLocation()) > 2) {
			switch (_moveType) {
			case MOVE_TYPE_NORMAL:
				doNormalFollow();
				break;
			case MOVE_TYPE_ASTAR:
				doAStarFollow();
				break;
			case MOVE_TYPE_ASTAR_FAIL:
				doAStarFailFollow();
				break;
			}
		} else {
			if (L1World.getInstance().getRecognizePlayer(this).size() == 0) {
				return true;
			}
			if (_master == null && isMovable() && !isRest()) {
				L1MobGroupInfo mobGroupInfo = getMobGroupInfo();
				if (mobGroupInfo == null || mobGroupInfo != null && mobGroupInfo.isLeader(this)) {
					if (_randomMoveDistance == 0) {
						_randomMoveDistance = _random.nextInt(5) + 1;
						_randomMoveDirection = _random.nextInt(20);
						if (getHomeX() != 0 && getHomeY() != 0 && _randomMoveDirection < 8 && _random.nextInt(3) == 0) {
							_randomMoveDirection = moveDirection(getHomeX(), getHomeY());
						}
					} else {
						_randomMoveDistance--;

					}
					if (_randomMoveDirection < 8 && getHomeX() != 0 && getHomeY() != 0) {
						byte dis = 0;
						if (getNpcId() == 70009)// 게렝
							dis = 3;
						else if (getNpcId() == 70027)// 디오
							dis = 2;
						if (dis != 0 && getLocation().getLineDistance(new Point(getHomeX(), getHomeY())) > dis)
							_randomMoveDirection = moveDirection(getHomeX(), getHomeY());
					}
					int dir = checkObject(getX(), getY(), getMapId(), _randomMoveDirection);
					if (dir != -1) {
						setSleepTime(setDirectionMove(dir));
					}
				} else {
					L1NpcInstance leader = mobGroupInfo.getLeader();
					if (getLocation().getTileLineDistance(leader.getLocation()) > 2) {
						int dir = moveDirection(leader.getX(), leader.getY());
						if (dir == -1) {
							return true;
						} else {
							setSleepTime(setDirectionMove(dir));
						}
					}
				}
			}
		}
		return false;
	}

	public void onFinalAction(L1PcInstance pc, String s) {
	}

	// 현재의 타겟을 삭제
	public void tagertClear() {
		if (_target != null) {
			_hateList.remove(_target);
		}  
		_target = null;
		_moveType = MOVE_TYPE_NORMAL;
	}

	// 지정된 타겟을 삭제
	public void targetRemove(L1Character target) {
		_hateList.remove(target);
		if (_target != null && _target.equals(target)) {
			_target = null;
			_moveType = MOVE_TYPE_NORMAL;
		}
	}

	// 모든 타겟을 삭제
	public void allTargetClear() {
		_hateList.clear();
		_dropHateList.clear();
		_target = null;
		_targetItemList.clear();
		_targetItem = null;
		_moveType = MOVE_TYPE_NORMAL;
	}

	// 마스터의 설정
	public void setMaster(L1Character cha) {
		_master = cha;
	}

	// 마스터의 취득
	public L1Character getMaster() {
		return _master;
	}

	// AI방아쇠
	public void onNpcAI() {
		startChat(CHAT_TIMING_APPEARANCE); // 채팅 시작
	}

	// 아이템 정제
	public void refineItem() {

		int[] materials = null;
		int[] counts = null;
		int[] createitem = null;
		int[] createcount = null;

		if (_npcTemplate.get_npcId() == 45032) { // 브롭
			// 오리하루콘 검신
			if (getExp() != 0 && !_inventory.checkItem(20)) {
				materials = new int[] { 40508, 40521, 40045 };
				counts = new int[] { 150, 3, 3 };
				createitem = new int[] { 20 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
			// 장검신
			if (getExp() != 0 && !_inventory.checkItem(19)) {
				materials = new int[] { 40494, 40521 };
				counts = new int[] { 150, 3 };
				createitem = new int[] { 19 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
			// 단검신
			if (getExp() != 0 && !_inventory.checkItem(3)) {
				materials = new int[] { 40494, 40521 };
				counts = new int[] { 50, 1 };
				createitem = new int[] { 3 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
			// 오리하루콘 도금 뿔
			if (getExp() != 0 && !_inventory.checkItem(100)) {
				materials = new int[] { 88, 40508, 40045 };
				counts = new int[] { 4, 80, 3 };
				createitem = new int[] { 100 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
			// 미스릴 도금 뿔
			if (getExp() != 0 && !_inventory.checkItem(89)) {
				materials = new int[] { 88, 40494 };
				counts = new int[] { 2, 80 };
				createitem = new int[] { 89 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						ItemInstance item = _inventory.storeItem(createitem[j], createcount[j]);
						if (getNpcTemplate().get_digestitem() > 0) {
							setDigestItem(item);
						}
					}
				}
			}
		} else if (_npcTemplate.get_npcId() == 81069) { // 돕페르겐가(퀘스트)
			// 돕페르겐가의 체액
			if (getExp() != 0 && !_inventory.checkItem(40542)) {
				materials = new int[] { 40032 };
				counts = new int[] { 1 };
				createitem = new int[] { 40542 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
		} else if (_npcTemplate.get_npcId() == 45166 // 잔크오란탄
				|| _npcTemplate.get_npcId() == 45167) {
			// 펌프킨의 종
			if (getExp() != 0 && !_inventory.checkItem(40726)) {
				materials = new int[] { 40725 };
				counts = new int[] { 1 };
				createitem = new int[] { 40726 };
				createcount = new int[] { 1 };
				if (_inventory.checkItem(materials, counts)) {
					for (int i = 0; i < materials.length; i++) {
						_inventory.consumeItem(materials[i], counts[i]);
					}
					for (int j = 0; j < createitem.length; j++) {
						_inventory.storeItem(createitem[j], createcount[j]);
					}
				}
			}
		}
	}

	private boolean _deathProcessing = false; // 사망 처리중인가
	// EXP, Drop 분배중은 타겟 리스트, 헤이 새 파업을 클리어 하지 않는다

	private int _paralysisTime = 0; // Paralysis RestTime

	public void setParalysisTime(int ptime) {
		_paralysisTime = ptime;
	}

	public L1HateList getHateList() {
		return _hateList;
	}
	public int getParalysisTime() {
		return _paralysisTime;
	}

	// HP자연 회복
	public final void startHpRegeneration() {
		int hprInterval = getNpcTemplate().get_hprinterval();
		int hpr = getNpcTemplate().get_hpr();
		if (!_hprRunning && hprInterval > 0 && hpr > 0) {
			_hprTimer = new HprTimer(hpr, hprInterval);
			GeneralThreadPool.getInstance().schedule(_hprTimer, hprInterval);
			_hprRunning = true;
		}
	}

	public final void stopHpRegeneration() {
		if (_hprRunning) {
			_hprTimer.cancel();
			_hprRunning = false;
		}
	}

	// MP자연 회복
	public final void startMpRegeneration() {
		int mprInterval = getNpcTemplate().get_mprinterval();
		int mpr = getNpcTemplate().get_mpr();
		if (!_mprRunning && mprInterval > 0 && mpr > 0) {
			_mprTimer = new MprTimer(mpr, mprInterval);
			GeneralThreadPool.getInstance().schedule(_mprTimer, mprInterval);
			_mprRunning = true;
		}
	}

	public final void stopMpRegeneration() {
		if (_mprRunning) {
			_mprTimer.cancel();
			_mprRunning = false;
		}
	}

	// ■■■■■■■■■■■■ 타이머 관련 ■■■■■■■■■■

	// HP자연 회복
	private boolean _hprRunning = false;

	private HprTimer _hprTimer;

	class HprTimer implements Runnable {
		private boolean _active;
		private long _interval;
		private int _point;

		@Override
		public void run() {
			try {
				if (!_active) {
					return;
				}
				if ((!_destroyed && !isDead()) && (getCurrentHp() > 0 && getCurrentHp() < getMaxHp())) {
					setCurrentHp(getCurrentHp() + _point);

					GeneralThreadPool.getInstance().schedule(this, _interval);
				} else {
					_hprRunning = false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public HprTimer(int point, long interval) {
			if (point < 1) {
				point = 1;
			}
			_point = point;
			_active = true;
			_interval = interval;
		}

		public void cancel() {
			_active = false;
		}
	}
	// MP자연 회복
	private boolean _mprRunning = false;

	private MprTimer _mprTimer;

	class MprTimer implements Runnable {
		private boolean _active;
		private long _interval;
		private int _point;

		@Override
		public void run() {
			try {
				if (!_active) {
					return;
				}

				if ((!_destroyed && !isDead()) && (getCurrentHp() > 0 && getCurrentMp() < getMaxMp())) {
					setCurrentMp(getCurrentMp() + _point);

					GeneralThreadPool.getInstance().schedule(this, _interval);
				} else {
					_mprRunning = false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public MprTimer(int point, long interval) {
			if (point < 1) {
				point = 1;
			}
			_point = point;
			_active = true;
			_interval = interval;
		}

		public void cancel() {
			_active = false;
		}
	}


	class DigestItemTimer implements Runnable {
		@Override
		public void run() {
			if (!_digestItemRunning) {
				_digestItemRunning = true;
			}

			Object[] keys = null;
			ItemInstance digestItem = null;

			if (!_destroyed && _digestItems.size() > 0) {
				keys = _digestItems.keySet().toArray();
				Integer key = null;
				Integer digestCounter = null;
				for (int i = 0; i < keys.length; i++) {
					key = (Integer) keys[i];
					digestCounter = _digestItems.get(key);
					digestCounter -= 1;
					if (digestCounter <= 0) {
						_digestItems.remove(key);
						digestItem = getInventory().getItem(key);
						if (digestItem != null) {
							getInventory().removeItem(digestItem, digestItem.getCount());
						}
					} else {
						_digestItems.put(key, digestCounter);
					}
				}
				GeneralThreadPool.getInstance().schedule(this, 1000);
			} else {
				_digestItemRunning = false;
			}
		}
	}

	private boolean _pickupItem;

	public boolean isPickupItem() {
		return _pickupItem;
	}

	public void setPickupItem(boolean flag) {
		_pickupItem = flag;
	}

	private int _passispeed;
	private int _atkspeed;
	public int getPassispeed() {
		return _passispeed;
	}

	public void setPassispeed(int i) {
		_passispeed = i;
	}

	public int getAtkspeed() {
		return _atkspeed;
	}

	public void setAtkspeed(int i) {
		_atkspeed = i;
	}

	@Override
	public L1Inventory getInventory() {
		return _inventory;
	}

	public void setInventory(L1Inventory inventory) {
		_inventory = inventory;
	}

	public L1Npc getNpcTemplate() {
		return _npcTemplate;
	}

	public int getNpcId() {
		return _npcTemplate.get_npcId();
	}

	public void setPetcost(int i) {
		_petcost = i;
	}

	public int getPetcost() {
		return _petcost;
	}

	public void setSpawn(L1Spawn spawn) {
		_spawn = spawn;
	}

	public L1Spawn getSpawn() {
		return _spawn;
	}

	public void setSpawnNumber(int number) {
		_spawnNumber = number;
	}

	public int getSpawnNumber() {
		return _spawnNumber;
	}

	public void onDecay(boolean isReuseId) {
		int id = 0;
		if (isReuseId) {
			id = getId();
		} else {
			id = 0;
		}
		_spawn.executeSpawnTask(_spawnNumber, id);
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if ((this == null) || (perceivedFrom == null)) {
			return;
		}
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.npc(this));
		onNpcAI();
	}


	public boolean isPassableNpc() {
		return this instanceof DollInstance;
	}
	@Override
	public void dispose() {
		_mobGroupInfo = null;
		_inventory = null;

		if (_digestItems != null) {
			_digestItems.clear();
			_digestItems = null;
		}

		if (getNpcTemplate().get_npcId() != 8500138) {
			if (mobSkill != null) {
				mobSkill.dispose();
				mobSkill = null;
			}
		}

		_spawn = null;
		super.dispose();
	}
	public void deleteMe() {
		try {
			_destroyed = true;
			if (!isDead() && !isPassableNpc()) {
				getMap().setPassable(getLocation(), true);
			}
			if (getInventory() != null) {
				getInventory().clearItems();
			}
			allTargetClear();
			_master = null;
			L1World.getInstance().removeVisibleObject(this);
			L1World.getInstance().removeObject(this);
			List<L1PcInstance> players = L1World.getInstance().getRecognizePlayer(this);
			if (players.size() > 0) {
				S_RemoveObject remove = S_RemoveObject.get(this);
				for (L1PcInstance pc : players) {
					if (pc != null) {
						pc.removeKnownObject(this);
						pc.sendPackets(remove);
					}
				}
			}
			removeAllKnownObjects();
			clearSkillEffectTimer();
			L1MobGroupInfo mobGroupInfo = getMobGroupInfo();
			// 리스폰 설정
			if (mobGroupInfo == null) {
				if (isReSpawn()) {
					onDecay(true);
				}
			} else {
				if (mobGroupInfo.removeMember(this) == 0) {
					setMobGroupInfo(null);
					if (isReSpawn()) {
						onDecay(false);
					}
				}
			}
			if (this instanceof MonsterInstance) {
				dispose();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void ReceiveManaDamage(L1Character attacker, int damageMp) {}

	public void receiveDamage(L1Character attacker, int damage) {

	}

	public void setDigestItem(ItemInstance item) {
		if (item == null) {
			return;
		}
		_digestItems.put(Integer.valueOf(item.getId()), Integer.valueOf(getNpcTemplate().get_digestitem()));
		if (!_digestItemRunning) {
			DigestItemTimer digestItemTimer = new DigestItemTimer();
			GeneralThreadPool.getInstance().execute(digestItemTimer);
		}
	}

	public void onGetItem(ItemInstance item) {
		refineItem();
		getInventory().shuffle();
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
	}

	public void approachPlayer(L1PcInstance pc) {
		if (pc == null) {
			return;
		}
		if (pc.hasSkillEffect(SkillId.인비지블리티) || pc.hasSkillEffect(SkillId.블라인드_하이딩)) {
			return;
		}
		if (getHiddenStatus() == HIDDEN_STATUS_SINK) {
			if (getCurrentHp() == getMaxHp()) {
				if (pc.getLocation().getTileLineDistance(this.getLocation()) <= 2) {
					appearOnGround(pc);
				}
			}
		} else if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
			if (getCurrentHp() == getMaxHp()) {
				if (pc.getLocation().getTileLineDistance(this.getLocation()) <= 1) {
					appearOnGround(pc);
				}
			} else {
				searchItemFromAir();
			}
		}

	}

	public void appearOnGround(L1PcInstance pc) {
		if (pc == null) {
			return;
		}
		if (getHiddenStatus() == HIDDEN_STATUS_SINK) {
			setHiddenStatus(HIDDEN_STATUS_NONE);
			broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Appear));
			setStatus(0);
			broadcastPacket(S_WorldObject.npc(this));
			if (!pc.hasSkillEffect(SkillId.인비지블리티) && !pc.hasSkillEffect(SkillId.블라인드_하이딩) && !pc.isGm()) {
				_hateList.add(pc, 0);
				_target = pc;
				_moveType = MOVE_TYPE_NORMAL;
			}
			onNpcAI();
		} else if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
			setHiddenStatus(HIDDEN_STATUS_NONE);
			broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Movedown));
			setStatus(0);
			broadcastPacket(S_WorldObject.npc(this));
			if (!pc.hasSkillEffect(SkillId.인비지블리티) && !pc.hasSkillEffect(SkillId.블라인드_하이딩) && !pc.isGm()) {
				_hateList.add(pc, 0);
				_target = pc;
				_moveType = MOVE_TYPE_NORMAL;
			}
			onNpcAI();
			startChat(CHAT_TIMING_HIDE);
		}
	}

	// 지정된 방향으로 이동시킨다
	public long setDirectionMove(int dir) {
		if (dir >= 0) {
			int nx = 0;
			int ny = 0;
			switch (dir) {
			case 1: nx = 1; ny = -1; setHeading(1); break;
			case 2: nx = 1; ny = 0; setHeading(2); break;
			case 3: nx = 1; ny = 1; setHeading(3); break;
			case 4: nx = 0; ny = 1; setHeading(4); break;
			case 5: nx = -1; ny = 1; setHeading(5); break;
			case 6: nx = -1; ny = 0; setHeading(6); break;
			case 7: nx = -1; ny = -1; setHeading(7); break;
			case 0: nx = 0; ny = -1; setHeading(0); break; default: break;
			}

			if (!isPassableNpc()) {
				getMap().setPassable(getLocation(), true);
			}

			int nnx = getX() + nx;
			int nny = getY() + ny;
			setX(nnx);
			setY(nny);

			if (!isPassableNpc()) {
				getMap().setPassable(getLocation(), false);
			}
			broadcastPacket(S_MoveCharPacket.get(this, dir));

			if (getMovementDistance() > 0) {
				if (this instanceof GuardInstance
						|| this instanceof CastleGuardInstance
						|| this instanceof L1GuardianInstance 
						|| this instanceof L1MerchantInstance
						|| this instanceof MonsterInstance) {
					if (getLocation().getLineDistance(
							new Point(getHomeX(), getHomeY())) > getMovementDistance()) {
						teleport(getHomeX(), getHomeY(), getHeading());
					}
				}
				// 필드 몬스터 40칸 이상 갈시에 제자리 텔 이론적으로 캐릭터 화면 3개 정도의 분량에서 텔레포트 
			} else if (this instanceof MonsterInstance && (getMapId() == 4)) {
				if (getLocation().getLineDistance(new Point(getHomeX(), getHomeY())) > 40) {
					teleport(getHomeX(), getHomeY(), getHeading());
				}
			}
		}
		return getCurrentSpriteInterval(EActionCodes.walk);
	}

	public int moveDirection(int x, int y) {
		return moveDirection(x, y, getLocation().getLineDistance(new Point(x, y)));
	}

	protected boolean isExsistCharacterBetweenTarget(int dir) {
		if (!(this instanceof MonsterInstance)) {
			return false;
		}
		if (_target == null) {
			return false;
		}

		L1Character cha = null;
		L1PcInstance pc = null;

		for (L1Object object : L1World.getInstance().getVisibleObjects(this, 1)) {
			if (object instanceof L1PcInstance || object instanceof SummonInstance || object instanceof PetInstance) {
				cha = (L1Character) object;
				if (!cha.isDead()) {

					boolean matched = false;
					for (int i = 0; i < 4; ++i) {
						if (!cha.getMap().isUserPassable(cha.getX(), cha.getY(), i)
								&& !cha.getMap().isUserPassable(cha.getX(), cha.getY(), i + 4)) {
							matched = true;
							break;
						}
					}

					if (!matched) {
						continue;
					}
					if (object instanceof L1PcInstance) {
						pc = (L1PcInstance) object;
						if (pc.isGhost()) {
							continue;
						}
					}
					_hateList.add(cha, 0);
					_target = cha;
					_moveType = MOVE_TYPE_NORMAL;
					_aStar.resetTryCount();
					return true;
				}
			}
		}
		return false;
	}
	// 목표의 역방향을 돌려준다
	public int targetReverseDirection(int tx, int ty) { // 목표점X 목표점Y
		int dir = targetDirection(tx, ty);
		dir += 4;
		if (dir > 7) {
			dir -= 8;
		}
		return dir;
	}

	public static int checkObject(int x, int y, short m, int d) {
		L1Map map = L1WorldMap.getInstance().getMap(m);
		switch (d) {
		case 1:
			if (map.isPassable(x, y, 1)) {
				return 1;
			} else if (map.isPassable(x, y, 0)) {
				return 0;
			} else if (map.isPassable(x, y, 2)) {
				return 2;
			}
			break;
		case 2:
			if (map.isPassable(x, y, 2)) {
				return 2;
			} else if (map.isPassable(x, y, 1)) {
				return 1;
			} else if (map.isPassable(x, y, 3)) {
				return 3;
			}
			break;
		case 3:
			if (map.isPassable(x, y, 3)) {
				return 3;
			} else if (map.isPassable(x, y, 2)) {
				return 2;
			} else if (map.isPassable(x, y, 4)) {
				return 4;
			}
			break;
		case 4:
			if (map.isPassable(x, y, 4)) {
				return 4;
			} else if (map.isPassable(x, y, 3)) {
				return 3;
			} else if (map.isPassable(x, y, 5)) {
				return 5;
			}
			break;
		case 5:
			if (map.isPassable(x, y, 5)) {
				return 5;
			} else if (map.isPassable(x, y, 4)) {
				return 4;
			} else if (map.isPassable(x, y, 6)) {
				return 6;
			}
			break;
		case 6:
			if (map.isPassable(x, y, 6)) {
				return 6;
			} else if (map.isPassable(x, y, 5)) {
				return 5;
			} else if (map.isPassable(x, y, 7)) {
				return 7;
			}
			break;
		case 7:
			if (map.isPassable(x, y, 7)) {
				return 7;
			} else if (map.isPassable(x, y, 6)) {
				return 6;
			} else if (map.isPassable(x, y, 0)) {
				return 0;
			}
			break;
		case 0:
			if (map.isPassable(x, y, 0)) {
				return 0;
			} else if (map.isPassable(x, y, 7)) {
				return 7;
			} else if (map.isPassable(x, y, 1)) {
				return 1;
			}
			break;
		default:
			break;
		}
		return -1;
	}

	protected int _serchCource(int x, int y) {
		int i;
		int locCenter = courceRange + 1;
		int diff_x = x - locCenter;
		int diff_y = y - locCenter;
		int[] locBace = { getX() - diff_x, getY() - diff_y, 0, 0 };
		int[] locNext = new int[4];
		int[] locCopy;
		int[] dirFront = new int[5];
		boolean serchMap[][] = new boolean[locCenter * 2 + 1][locCenter * 2 + 1];
		LinkedList<int[]> queueSerch = new LinkedList<int[]>();

		for (int j = courceRange * 2 + 1; j > 0; j--) {
			for (i = courceRange - Math.abs(locCenter - j); i >= 0; i--) {
				serchMap[j][locCenter + i] = true;
				serchMap[j][locCenter - i] = true;
			}
		}

		int[] firstCource = { 2, 4, 6, 0, 1, 3, 5, 7 };
		for (i = 0; i < 8; i++) {
			System.arraycopy(locBace, 0, locNext, 0, 4);
			_moveLocation(locNext, firstCource[i]);
			if (locNext[0] - locCenter == 0 && locNext[1] - locCenter == 0) {
				return firstCource[i];
			}
			if (serchMap[locNext[0]][locNext[1]]) {
				int tmpX = locNext[0] + diff_x;
				int tmpY = locNext[1] + diff_y;
				boolean found = false;
				switch (i) {
				case 0:
					found = getMap().isPassable(tmpX, tmpY + 1, i);
					break;
				case 1:
					found = getMap().isPassable(tmpX - 1, tmpY + 1, i);
					break;
				case 2:
					found = getMap().isPassable(tmpX - 1, tmpY, i);
					break;
				case 3:
					found = getMap().isPassable(tmpX - 1, tmpY - 1, i);
					break;
				case 4:
					found = getMap().isPassable(tmpX, tmpY - 1, i);
					break;
				case 5:
					found = getMap().isPassable(tmpX + 1, tmpY - 1, i);
					break;
				case 6:
					found = getMap().isPassable(tmpX + 1, tmpY, i);
					break;
				case 7:
					found = getMap().isPassable(tmpX + 1, tmpY + 1, i);
					break;
				default:
					break;
				}
				if (found) {
					locCopy = new int[4];
					System.arraycopy(locNext, 0, locCopy, 0, 4);
					locCopy[2] = firstCource[i];
					locCopy[3] = firstCource[i];
					queueSerch.add(locCopy);
				}
				serchMap[locNext[0]][locNext[1]] = false;
			}
		}
		locBace = null;

		while (queueSerch.size() > 0) {
			locBace = queueSerch.removeFirst();
			_getFront(dirFront, locBace[2]);
			for (i = 4; i >= 0; i--) {
				System.arraycopy(locBace, 0, locNext, 0, 4);
				_moveLocation(locNext, dirFront[i]);
				if (locNext[0] - locCenter == 0 && locNext[1] - locCenter == 0) {
					return locNext[3];
				}
				if (serchMap[locNext[0]][locNext[1]]) {
					int tmpX = locNext[0] + diff_x;
					int tmpY = locNext[1] + diff_y;
					boolean found = false;
					if (i == 0) {
						found = getMap().isPassable(tmpX, tmpY + 1, i);
					} else if (i == 1) {
						found = getMap().isPassable(tmpX - 1, tmpY + 1, i);
					} else if (i == 2) {
						found = getMap().isPassable(tmpX - 1, tmpY, i);
					} else if (i == 3) {
						found = getMap().isPassable(tmpX - 1, tmpY - 1, i);
					} else if (i == 4) {
						found = getMap().isPassable(tmpX, tmpY - 1, i);
					}
					if (found) {
						locCopy = new int[4];
						System.arraycopy(locNext, 0, locCopy, 0, 4);
						locCopy[2] = dirFront[i];
						queueSerch.add(locCopy);
					}
					serchMap[locNext[0]][locNext[1]] = false;
				}
			}
			locBace = null;
		}
		return -1;
	}

	private void _moveLocation(int[] ary, int d) {
		switch (d) {
		case 1:
			ary[0] = ary[0] + 1;
			ary[1] = ary[1] - 1;
			break;
		case 2:
			ary[0] = ary[0] + 1;
			break;
		case 3:
			ary[0] = ary[0] + 1;
			ary[1] = ary[1] + 1;
			break;
		case 4:
			ary[1] = ary[1] + 1;
			break;
		case 5:
			ary[0] = ary[0] - 1;
			ary[1] = ary[1] + 1;
			break;
		case 6:
			ary[0] = ary[0] - 1;
			break;
		case 7:
			ary[0] = ary[0] - 1;
			ary[1] = ary[1] - 1;
			break;
		case 0:
			ary[1] = ary[1] - 1;
			break;
		default:
			break;
		}
		ary[2] = d;
	}

	private void _getFront(int[] ary, int d) {
		switch (d) {
		case 1:
			ary[4] = 2;
			ary[3] = 0;
			ary[2] = 1;
			ary[1] = 3;
			ary[0] = 7;
			break;
		case 2:
			ary[4] = 2;
			ary[3] = 4;
			ary[2] = 0;
			ary[1] = 1;
			ary[0] = 3;
			break;
		case 3:
			ary[4] = 2;
			ary[3] = 4;
			ary[2] = 1;
			ary[1] = 3;
			ary[0] = 5;
			break;
		case 4:
			ary[4] = 2;
			ary[3] = 4;
			ary[2] = 6;
			ary[1] = 3;
			ary[0] = 5;
			break;
		case 5:
			ary[4] = 4;
			ary[3] = 6;
			ary[2] = 3;
			ary[1] = 5;
			ary[0] = 7;
			break;
		case 6:
			ary[4] = 4;
			ary[3] = 6;
			ary[2] = 0;
			ary[1] = 5;
			ary[0] = 7;
			break;
		case 7:
			ary[4] = 6;
			ary[3] = 0;
			ary[2] = 1;
			ary[1] = 5;
			ary[0] = 7;
			break;
		case 0:
			ary[4] = 2;
			ary[3] = 6;
			ary[2] = 0;
			ary[1] = 1;
			ary[0] = 7;
			break;
		default:
			break;
		}
	}

	// ■■■■■■■■■■■■ 아이템 관련 ■■■■■■■■■■

	private void useHealPotion(int healHp, int effectId) {
		broadcastPacket(S_SkillSound.get(getId(), effectId));
		if (this.hasSkillEffect(SkillId.플루트_워터)) { // 포르트워타중은 회복량1/2배
			healHp /= 2;
		}
		if (this instanceof PetInstance) {
			((PetInstance) this).setCurrentHp(getCurrentHp() + healHp);
		} else if (this instanceof SummonInstance) {
			((SummonInstance) this).setCurrentHp(getCurrentHp() + healHp);
		} else {
			setCurrentHpDirect(getCurrentHp() + healHp);
		}
	}

	public void useHastePotion(int time) {
		broadcastPacket(S_SkillHaste.get(getId(), 1, time));
		broadcastPacket(S_SkillSound.get(getId(), 191));
		setMoveSpeed(1);
		setSkillEffect(SkillId.상태_1단_가속, time * 1000);
	}

	// 아이템의 사용 판정 및 사용
	public static final int USEITEM_HEAL = 0;
	public static final int USEITEM_HASTE = 1;
	public static int[] healPotions = { ItemId.강력_체력_회복제, ItemId.고급_체력_회복제, ItemId.체력_회복제 };
	public static int[] haestPotions = { ItemId.축복_강화_초록_물약, ItemId.강화_초록_물약, ItemId.축복_속도_향상_물약, ItemId.속도_향상_물약 };

	public void useItem(int type, int chance) { // 사용하는 종류 사용할 가능성(%)
		if (hasSkillEffect(SkillId.디케이_포션)) {
			return; 
		}
		if (CommonUtil.randomRange(1, 100) > chance) {
			return; // 사용할 가능성
		}

		if (type == USEITEM_HEAL) { // 회복계 일부
			// 회복량의 큰 순서
			if (getInventory().consumeItem(ItemId.강력_체력_회복제, 1)) {
				useHealPotion(75, 197);
			} else if (getInventory().consumeItem(ItemId.고급_체력_회복제, 1)) {
				useHealPotion(45, 194);
			} else if (getInventory().consumeItem(ItemId.체력_회복제, 1)) {
				useHealPotion(15, 189);
			}
		} else if (type == USEITEM_HASTE) { // 헤이 파업계 일부
			if (hasSkillEffect(1001)) {
				return; // 헤이 파업 상태 체크
			}

			// 효과의 긴 순서
			if (getInventory().consumeItem(ItemId.축복_강화_초록_물약, 1)) {
				useHastePotion(2100);
			} else if (getInventory().consumeItem(ItemId.강화_초록_물약, 1)) {
				useHastePotion(1800);
			} else if (getInventory().consumeItem(ItemId.축복_속도_향상_물약, 1)) {
				useHastePotion(350);
			} else if (getInventory().consumeItem(ItemId.속도_향상_물약, 1)) {
				useHastePotion(300);
			}
		}
	}

	/**
	 * 몬스터가 캐릭터의 근처에 텔레포트를 한다.
	 * @param nx
	 * @param ny
	 * @return
	 */
	public boolean nearTeleport(int nx, int ny) {
		int rdir = _random.nextInt(8);
		int dir;
		for (int i = 0; i < 8; i++) {
			dir = rdir + i;
			if (dir > 7) {
				dir -= 8;
			}
			switch (dir) {
			case 1:
				nx++;
				ny--;
				break;
			case 2:
				nx++;
				break;
			case 3:
				nx++;
				ny++;
				break;
			case 4:
				ny++;
				break;
			case 5:
				nx--;
				ny++;
				break;
			case 6:
				nx--;
				break;
			case 7:
				nx--;
				ny--;
				break;
			case 0:
				ny--;
				break;
			default:
				break;
			}
			if (getMap().isPassable(nx, ny)) {
				dir += 4;
				if (dir > 7) {
					dir -= 8;
				}
				teleport(nx, ny, dir);
				setCurrentMp(getCurrentMp() - 120);
				return true;
			} 
			// 텔레포트를 하는 몬스터는 약 두어번 정도 텔레포트를 한다. 
			if(getCurrentMp() <= 100) {
				return false;
			}
		}
		return false;
	}

	// 목표에 텔레포트
	public void teleport(int nx, int ny, int dir) {
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if (pc == null) {
				continue;
			}
			pc.sendPackets(S_SkillSound.get(getId(), 169));
			pc.sendPackets(S_RemoveObject.get(this));
			pc.removeKnownObject(this);
		}
		if (!isPassableNpc()) {
			getMap().setPassable(getLocation(), true);
		}
		setX(nx);
		setY(ny);
		setHeading(dir);
		if (!isPassableNpc()) {
			getMap().setPassable(getLocation(), false);
		}
	}

	// ----------From L1Character-------------
	private String _nameId; // ● 네임 ID

	public String getNameId() {
		return _nameId;
	}

	public void setNameId(String s) {
		_nameId = s;
	}

	private boolean _Agro; // ● 액티브한가

	public boolean isAgro() {
		return _Agro;
	}

	public void setAgro(boolean flag) {
		_Agro = flag;
	}

	private boolean _Agrocoi; // ● 인비지아크티브인가

	public boolean isAgrocoi() {
		return _Agrocoi;
	}

	public void setAgrocoi(boolean flag) {
		_Agrocoi = flag;
	}

	private boolean _Agrososc; // ● 변신 액티브한가

	public boolean isAgrososc() {
		return _Agrososc;
	}

	public void setAgrososc(boolean flag) {
		_Agrososc = flag;
	}

	private int _homeX; // ● 홈 포인트 X(monster가 돌아오는 위치라든지 애완동물의 경계 위치)

	public int getHomeX() {
		return _homeX;
	}

	public void setHomeX(int i) {
		_homeX = i;
	}

	private int _homeY; // ● 홈 포인트 Y(monster가 돌아오는 위치라든지 애완동물의 경계 위치)

	public int getHomeY() {
		return _homeY;
	}

	public void setHomeY(int i) {
		_homeY = i;
	}

	private boolean _reSpawn; // ● 재팝 할지 어떨지

	public boolean isReSpawn() {
		return _reSpawn;
	}

	public void setRespawn(boolean flag) {
		_reSpawn = flag;
	}

	public void setreSpawn(boolean flag) {
		_reSpawn = flag;
	}

	private int _lightSize; // 라이트 0. 없음 1~14. 크기

	public int getLightSize() {
		return _lightSize;
	}

	public void setLightSize(int i) {
		_lightSize = i;
	}

	private boolean _weaponBreaked; // ● 웨폰브레이크중인가 어떤가

	public boolean isWeaponBreaked() {
		return _weaponBreaked;
	}

	public void setWeaponBreaked(boolean flag) {
		_weaponBreaked = flag;
	}

	private int _hiddenStatus; // ● 지중에 기어들거나 하늘을 날고 있는 상태

	public int getHiddenStatus() {
		return _hiddenStatus;
	}

	public void setHiddenStatus(int i) {
		_hiddenStatus = i;
	}

	// 행동 거리
	private int _movementDistance = 0;

	public int getMovementDistance() {
		return _movementDistance;
	}

	public void setMovementDistance(int i) {
		_movementDistance = i;
	}

	// 표시용 로우훌
	private int _tempLawful = 0;

	public int getTempLawful() {
		return _tempLawful;
	}

	public void setTempLawful(int i) {
		_tempLawful = i;
	}

	protected void setAiRunning(boolean aiRunning) {
		_aiRunning = aiRunning;
	}

	protected boolean isAiRunning() {
		return _aiRunning;
	}

	protected void setActived(boolean actived) {
		_actived = actived;
	}

	protected boolean isActived() {
		return _actived;
	}

	protected void setFirstAttack(boolean firstAttack) {
		_firstAttack = firstAttack;
	}

	protected boolean isFirstAttack() {
		return _firstAttack;
	}

	protected void setSleepTime(long sleep_time) {
		_sleep_time = sleep_time;
	}

	protected long getSleepTime() {
		return _sleep_time;
	}

	public void setDeathProcessing(boolean deathProcessing) {
		_deathProcessing = deathProcessing;
	}

	protected boolean isDeathProcessing() {
		return _deathProcessing;
	}

	public int drainMana(int drain) {
		if (_drainedMana >= Config.MANA_DRAIN_LIMIT_PER_NPC) {
			return 0;
		}
		int result = Math.min(drain, getCurrentMp());
		if (_drainedMana + result > Config.MANA_DRAIN_LIMIT_PER_NPC) {
			result = Config.MANA_DRAIN_LIMIT_PER_NPC - _drainedMana;
		}
		_drainedMana += result;
		return result;
	}

	public int drainHealth(int drain) { // 피흡수를 위해 추가
		if (_drainedHealth >= Config.HEALTH_DRAIN_LIMIT_PER_NPC) {
			return 0;
		}
		int result = Math.min(drain, getCurrentHp());
		if (_drainedHealth + result > Config.HEALTH_DRAIN_LIMIT_PER_NPC) {
			result = Config.HEALTH_DRAIN_LIMIT_PER_NPC - _drainedHealth;
		}
		_drainedHealth += result;
		return result;
	}

	public boolean _destroyed = false; // 이 인스턴스가 파기되고 있을까

	// ※파기 후에 움직이지 않게 강제적으로 AI등의 thread 처리 중지(만약을 위해)

	// NPC가 다른 NPC로 바뀌는 경우의 처리
	public void transform(int transformId) {
		stopHpRegeneration();
		stopMpRegeneration();
		int transformGfxId = getNpcTemplate().getTransformGfxId();
		if (transformGfxId != 0) {
			broadcastPacket(S_SkillSound.get(getId(), transformGfxId), true);
		}
		L1Npc npcTemplate = NpcTable.getInstance().getTemplate(transformId);
		setting_template(npcTemplate);

		broadcastPacket(new S_ChangeShape(getId(), getCurrentSpriteId()), true);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if (pc != null) {
				onPerceive(pc);
			}
		}
	}

	public void setRest(boolean _rest) {
		this._rest = _rest;
	}

	public boolean isRest() {
		return _rest;
	}


	public boolean isResurrect() {
		return _isResurrect;
	}

	public void setResurrect(boolean flag) {
		_isResurrect = flag;
	}

	@Override
	public synchronized void resurrect(int hp) {
		if (_destroyed) {
			return;
		}
		if (_deleteTask != null) {
			if (!_future.cancel(false)) { // 캔슬할 수 없다
				return;
			}
			_deleteTask = null;
			_future = null;
		}
		super.resurrect(hp);
	}

	private static final long DELETE_TIME = 10000L;

	protected synchronized void startDeleteTimer() {
		if (_deleteTask != null) {
			return;
		}
		_deleteTask = new DeleteTimer(getId());
		_future = GeneralThreadPool.getInstance().schedule(_deleteTask, DELETE_TIME);
	}

	protected static class DeleteTimer implements Runnable {
		private int _id;

		protected DeleteTimer(int oId) {
			try {
				_id = oId;
				if (L1World.getInstance().findObject(_id) == null) {
					return;
				}
				if (!(L1World.getInstance().findObject(_id) instanceof L1NpcInstance)) {
					throw new IllegalArgumentException("allowed only L1NpcInstance [신경 쓰지 말 것.]");
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("DeleteTimer 에러 : " + L1World.getInstance().findObject(_id));
			}
		}

		@Override
		public void run() {
			L1NpcInstance npc = (L1NpcInstance) L1World.getInstance().findObject(_id);
			if (npc == null || !npc.isDead() || npc._destroyed) {
				return; // 부활하고 있어, 이미 파기가 끝난 상태라면 뽑아라
			}
			try {
				npc.deleteMe();
			} catch (Exception e) { // 절대 예외를 던지지 않게
				e.printStackTrace();
			}
		}
	}



	public boolean isInMobGroup() {
		return getMobGroupInfo() != null;
	}

	public L1MobGroupInfo getMobGroupInfo() {
		return _mobGroupInfo;
	}

	public void setMobGroupInfo(L1MobGroupInfo m) {
		_mobGroupInfo = m;
	}



	public int getMobGroupId() {
		return _mobGroupId;
	}

	public void setMobGroupId(int i) {
		_mobGroupId = i;
	}

	public void startChat(int chatTiming) {
		// 외관 시간의 대화에도 불구하고 사망 중 사망시 채팅에도 불구하고 살기 중
		if (chatTiming == CHAT_TIMING_APPEARANCE && this.isDead()) {
			return;
		}
		if (chatTiming == CHAT_TIMING_DEAD && !this.isDead()) {
			return;
		}
		if (chatTiming == CHAT_TIMING_HIDE && this.isDead()) {
			return;
		}

		int npcId = this.getNpcTemplate().get_npcId();
		L1NpcChat npcChat = null;
		switch (chatTiming) {
		case CHAT_TIMING_APPEARANCE:
			npcChat = NpcChatTable.getInstance().getTemplateAppearance(npcId);
			break;
		case CHAT_TIMING_DEAD:
			npcChat = NpcChatTable.getInstance().getTemplateDead(npcId);
			break;
		case CHAT_TIMING_HIDE:
			npcChat = NpcChatTable.getInstance().getTemplateHide(npcId);
			break; 
		default:
			break;
		}
		if (npcChat == null) {
			return;
		}

		NpcChatTimer npcChatTimer;
		if (!npcChat.isRepeat()) {
			npcChatTimer = new NpcChatTimer(this, npcChat);
		} else {
			npcChatTimer = new NpcChatTimer(this, npcChat, npcChat.getRepeatInterval());

		}
		npcChatTimer.startChat(npcChat.getStartDelayTime());
	}

	public boolean isMovable() {
		return _npcTemplate.isMovable();
	}

	public boolean isActionable() {
		return _npcTemplate.isActionable();
	}

	public boolean isDynamic() {
		return _npcTemplate.isDynamic();
	}

	public void NpcDie() {
		try {
			setDeathProcessing(true);
			setCurrentHp(0);
			setDead(true);
			getMap().setPassable(getLocation(), true);
			setDeathProcessing(false);
			setExp(0);;
			setKarma(0);
			setLawful(0);
			allTargetClear();
			deleteMe();
		} catch (Exception e) {
		}
	}

	private int num;

	public void set_num(int num) {
		this.num = num;
	}

	public int get_num() {
		return num;
	}

	private long m_total_price;

	public long getTotalPrice() {
		return m_total_price;

	}
	public Object synchObject = new Object();

	public void addTotalPrice(long i) {
		m_total_price += i;

	}

	public int AStar_Try_Count = 0;

	protected boolean aStarMove() {
		Point current = new Point(getX(), getY());
		while (current.isSamePoint(_aStar.getFinalPath().get(0))) {
			_aStar.eraseOneNodeFromFinalPath();
		}
		int dir = current.getHeading(_aStar.getFinalPath().get(0));
		if (dir == -1) { // 길을 잃엇을때 여기 들어옴 
			tagertClear();
		} else if (getMap().isPassable(current, dir)
				&& !isObjectExist(current.getX(), current.getY(), (short) getMapId(), dir)) { // 맵지형 검색 
			_aStar.eraseOneNodeFromFinalPath();
			setDirectionMove(dir);
			setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			return true;
		} else {
			return false;
		}
		return true;
	}

	private void doNormalMove() {
		try {
			Point current = new Point(getX(), getY());

			int dir = moveDirection2(_target.getX(), _target.getY(),
					getLocation().getLineDistance(new Point(_target.getX(), _target.getY())));
			if (dir != -1 && getMap().isPassable(current, dir)
					&& !isObjectExist(current.getX(), current.getY(), (short) getMapId(), dir)) {
				setDirectionMove(dir);
				setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			} else {  
				Point targetPoint = new Point(_target.getX(), _target.getY());
				_aStar.setTarget(getMap(), targetPoint);
				if (_aStar.compute(current) == null || targetPoint == null) {
					_moveType = MOVE_TYPE_ASTAR_FAIL;
					doAStarFailMove();
					return;
				} else {
					_moveType = MOVE_TYPE_ASTAR;
					doAStarMove();
					return;
				}  
			}
		} catch (Exception e) {
			// System.out.println("엔피씨에러 : " + this.getNpcId());
		}

	}


	private void doAStarMove() {
		try {
			Point current = new Point(getX(), getY());
			Point targetPoint = new Point(_target.getX(), _target.getY());
			if (_aStar.getTarget().getX() != targetPoint.getX() || _aStar.getTarget().getY() != targetPoint.getY()) { 
				_aStar.setTarget(getMap(), targetPoint);
				if (_aStar.compute(current) == null) { 
					_moveType = MOVE_TYPE_ASTAR_FAIL;
					doAStarFailMove();
					return;
				}
			} else if (_aStar.getTryCount() >= 50) {
				_moveType = MOVE_TYPE_ASTAR_FAIL;
				doAStarFailMove();
				return;
			}
			if (!aStarMove()) {
				if (AStar_Try_Count > 10) {
					backingTargets.add(_target.getId());
					tagertClear();
					// _moveType = MOVE_TYPE_ASTAR_FAIL;
					// doAStarFailMove();
					AStar_Try_Count = 0;
					return;
				}

				if (this instanceof MonsterInstance) {
					if (_aStar.compute(current) == null) {
						_moveType = MOVE_TYPE_ASTAR_FAIL;
						doAStarFailMove();
						return;
					}
					if (!aStarMove()) {
						setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
					}
				} else if (this instanceof SummonInstance) {
					if (_aStar.compute(current) == null) {
						_moveType = MOVE_TYPE_ASTAR_FAIL;
						doAStarFailMove();
						return;
					}
					if (!aStarMove()) {
						setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
					}
				}
				AStar_Try_Count++;
			} else {
				setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			}
		} catch (Exception e) {

		}
	}

	private void doAStarFailMove() {
		try {
			Point current = new Point(getX(), getY());
			Point targetPoint = new Point(_target.getX(), _target.getY());

			if (_aStar.getTarget().getX() != targetPoint.getX() || _aStar.getTarget().getY() != targetPoint.getY()) {
				_aStar.setTarget(getMap(), targetPoint);
			}

			if (this instanceof MonsterInstance) {
				if (findNextTarget()) {
					setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
					return;
				}
			}

			int dir = targetDirection(targetPoint.getX(), targetPoint.getY());

			if (dir != -1 && isExsistCharacterBetweenTarget(dir)) {
				setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
				return;
			}

			dir = moveDirection3(_target.getX(), _target.getY());

			if (dir != -1 && getMap().isPassable(current, dir) && !isExsistCharacterBetweenTarget(dir)) {
				setDirectionMove(dir);
			}

			if (dir == -1) {
				backingTargets.add(_target.getId());
				_hateList.remove(_target);
				if (_target != null && _target.equals(_target)) {
					_target = null;
					_moveType = MOVE_TYPE_ASTAR;
					doAStarMove();
					return;
				}
			}

			setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
		} catch (Exception e) {
			// System.out.println("엔피씨에러2 : " + this.getNpcId());
		}
	}

	protected boolean aStarFollow() {
		Point current = new Point(getX(), getY());
		while (current.isSamePoint(_aStar.getFinalPath().get(0))) {
			_aStar.eraseOneNodeFromFinalPath();
		}
		int dir = current.getHeading(_aStar.getFinalPath().get(0));
		if (dir == -1) {
			return false;
		} else if (getMap().isPassable(current, dir)
				&& !isObjectExist(current.getX(), current.getY(), (short) getMapId(), dir)) {
			_aStar.eraseOneNodeFromFinalPath();
			setDirectionMove(dir);
			setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			return true;
		}

		return false;
	}

	private void doNormalFollow() {
		Point current = new Point(getX(), getY());

		int dir = moveDirection2(_master.getX(), _master.getY(),
				getLocation().getLineDistance(new Point(_master.getX(), _master.getY())));
		if (dir != -1 && getMap().isPassable(current, dir)
				&& !isObjectExist(current.getX(), current.getY(), (short) getMapId(), dir)) {
			setDirectionMove(dir);
			setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
		} else {
			Point targetPoint = new Point(_master.getX(), _master.getY());
			_aStar.setTarget(getMap(), targetPoint);
			if (_aStar.compute(current) == null) {
				_moveType = MOVE_TYPE_ASTAR_FAIL;
				doAStarFailFollow();
				return;
			} else {
				_moveType = MOVE_TYPE_ASTAR;
				doAStarFollow();
				return;
			}
		}

	}

	private void doAStarFollow() {
		Point current = new Point(getX(), getY());
		Point targetPoint = new Point(_master.getX(), _master.getY());

		if (_aStar.getTarget().getX() != targetPoint.getX() || _aStar.getTarget().getY() != targetPoint.getY()) {
			_aStar.setTarget(getMap(), targetPoint);

			if (_aStar.compute(current) == null) {
				_moveType = MOVE_TYPE_ASTAR_FAIL;
				doAStarFailFollow();
				return;
			}
		} else if (_aStar.getTryCount() >= 10) {
			_moveType = MOVE_TYPE_ASTAR_FAIL;
			doAStarFailFollow();
			return;
		}

		if (!aStarFollow()) {
			if (_aStar.compute(current) == null) {
				_moveType = MOVE_TYPE_ASTAR_FAIL;
				doAStarFailFollow();
				return;
			}
			if (!aStarMove()) {
				setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			}
		} else {
			setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
		}
	}

	private void doAStarFailFollow() {
		Point current = new Point(getX(), getY());
		Point targetPoint = new Point(_master.getX(), _master.getY());

		if (_aStar.getTarget().getX() != targetPoint.getX() || _aStar.getTarget().getY() != targetPoint.getY()) {
			_aStar.setTarget(getMap(), targetPoint);
		}

		if (_aStar.getTryCount() < 10 && _aStar.compute(current) != null) {
			_moveType = MOVE_TYPE_ASTAR;
			doAStarFollow();
			return;
		}

		int dir = moveDirection3(_target.getX(), _target.getY());

		if (dir != -1 && getMap().isPassable(current, dir) && !isObjectExist(current.getX(), current.getY(), (short) getMapId(), dir)) {
			setDirectionMove(dir);
		}

		setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
	}
	
	private static boolean isObjectExist(int x, int y, short m, int d) {
		L1Location loc = new L1Location();
		loc.setX(x);
		loc.setY(y);
		loc.setMap(m);

		loc.forward(d);

		for (L1Object object : L1World.getInstance().getVisiblePoint(loc, 0)) {
			if (object instanceof L1Character && !(object instanceof DollInstance)) {
				if (m >= 732 && m <= 776)
					return false;

				if (!((L1Character) object).isDead()) {
					return true;
				}
			}
		}

		return false;

	}
	
	protected boolean findNextTarget() {
		for (L1Object object : L1World.getInstance().getVisibleObjects(this, 10)) {
			if (object instanceof L1PcInstance) {
				L1Character cha = (L1Character) object;
				if (cha != _target) {
					if (object instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) object;
						if (pc.isGhost()) {
							continue;
						}
						if (pc.isGm()) {
							continue;
						}
					}
					_hateList.add(cha, 0);
					_target = cha;
					_moveType = MOVE_TYPE_NORMAL;
					_aStar.resetTryCount();
					return true;
				}
			}
		}
		return false;
	}

	public int moveDirection(int x, int y, double d) {
		int dir = 0;
		if (hasSkillEffect(SkillId.다크니스) || hasSkillEffect(SkillId.커스_블라인드) && d >= 2D) {
			return -1;
		} else if (d > 30D) {
			return -1;
		} else if (d > courceRange) {
			dir = targetDirection(x, y);
			dir = checkObject(getX(), getY(), getMapId(), dir);
		} else {
			dir = _serchCource(x, y);
			if (dir == -1) {
				dir = targetDirection(x, y);
				if (!isExsistCharacterBetweenTarget(dir)) {
					dir = checkObject(getX(), getY(), getMapId(), dir);
				}
			}
		}
		return dir;
	}

	public int moveDirection2(int x, int y, double d) {
		if (hasSkillEffect(SkillId.다크니스) || hasSkillEffect(SkillId.커스_블라인드) && d >= 2D) {
			return -1;
		} else if (d > 30D) {
			return -1;
		} else {
			int newX = getX();
			int newY = getY();

			int firstDir = directionFromCurrent(newX, newY, x, y);
			int dir = firstDir;
			while (true) {
				if (dir == 8) {
					if (firstDir == 8) {
						firstDir = 0;
					}
					return firstDir;
				}
				if (!getMap().isPassable(newX, newY, dir)) {
					return -1;
				}
				switch (dir) {
				case 0:
					newY -= 1;
					break;
				case 1:
					newX += 1;
					newY -= 1;
					break;
				case 2:
					newX += 1;
					break;
				case 3:
					newX += 1;
					newY += 1;
					break;
				case 4:
					newY += 1;
					break;
				case 5:
					newX -= 1;
					newY += 1;
					break;
				case 6:
					newX -= 1;
					break;
				case 7:
					newX -= 1;
					newY -= 1;
					break;
				}
				dir = directionFromCurrent(newX, newY, x, y);
			}
		}
	}

	public int moveDirection3(int x, int y) {
		int dir = targetDirection(x, y);
		dir = checkObject(getX(), getY(), getMapId(), dir);

		return dir;
	}

	public int directionFromCurrent(int cx, int cy, int tx, int ty) {
		float dis_x = Math.abs(cx - tx); // X방향의 타겟까지의 거리
		float dis_y = Math.abs(cy - ty); // Y방향의 타겟까지의 거리
		float dis = Math.max(dis_x, dis_y); // 타겟까지의 거리

		if (dis == 0)
			return 8;

		int avg_x = (int) Math.floor((dis_x / dis) + 0.59f); // 상하 좌우가 조금 우선인 둥근
		int avg_y = (int) Math.floor((dis_y / dis) + 0.59f); // 상하 좌우가 조금 우선인 둥근

		int dir_x = 0;
		int dir_y = 0;

		if (cx < tx)
			dir_x = 1;
		if (cx > tx)
			dir_x = -1;

		if (cy < ty)
			dir_y = 1;
		if (cy > ty)
			dir_y = -1;

		if (avg_x == 0)
			dir_x = 0;
		if (avg_y == 0)
			dir_y = 0;

		if (dir_x == 1 && dir_y == -1)
			return 1; // 상
		if (dir_x == 1 && dir_y == 0)
			return 2; // 우상
		if (dir_x == 1 && dir_y == 1)
			return 3; // 오른쪽
		if (dir_x == 0 && dir_y == 1)
			return 4; // 우하
		if (dir_x == -1 && dir_y == 1)
			return 5; // 하
		if (dir_x == -1 && dir_y == 0)
			return 6; // 좌하
		if (dir_x == -1 && dir_y == -1)
			return 7; // 왼쪽
		if (dir_x == 0 && dir_y == -1)
			return 0; // 좌상
		return -1;
	}
	
	private boolean _isPassObject = true;
	public boolean isPassObject() { return _isPassObject; }
	public void setPassObject(boolean b) { _isPassObject = b; }
}