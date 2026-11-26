package l1j.server.server.model.Instance;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import java.util.Random;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.TimeMapController;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.CParty;
import l1j.server.server.model.character.Item.Drop.CContributeData;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.Exp.CalcExp;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_Liquor;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.action.dMoveTo;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.npc.ai.CNpcItemUse;
import l1j.server.server.model.npc.ai.CNpcSpecialAction;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.L1Type;
import l1j.server.server.shared.listener.MonsterDeathHandler;
import l1j.server.server.shared.listener.MonsterTransformHandler;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1TimeMap;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.TYUtil;
public class L1MonsterInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private static Random _random = new Random(System.nanoTime());
    private volatile int _deathEpoch;
    private static final java.util.logging.Logger DEATH_DROP_LOG =
            java.util.logging.Logger.getLogger(L1MonsterInstance.class.getName());
    private static final byte DROP_DELETE = 1, DROP_DELETE_RE = 2, DROP_RE = 3,
            DROP_NPC_DIE = 4, DROP_DISPOSE = 5;
    private DeathSettlement _pendingPartyDrop; // guarded by this
    private volatile byte _dropRemovalRequest;

	private boolean _storeDroped;

    /** Retail CDesirePQ runtime. Lazily allocated on the first real desire. */
    public CDesirePQ _desirePQ;

    /** GetLifeTime() origin for the retail CLAN_ATTACKED lifetime gate. */
    public final long _desireCreatedAtMs = System.currentTimeMillis();

    /** Retail dMoveAround internal 0..9 gate. */
    public int _desireMoveAroundCounter = -1;

    public long _desireMoveAroundDecisionAtMs = 0L;

    public int _desireBlockedAttackTargetId = 0;

    /** Packed unsigned-16 X/Y of the dynamic tile that forced body-block MOVE_AROUND. */
    public int _desireBlockedLanePos = 0;

    /** 0=none, 1=single blocked lane, 2=target melee ring fully occupied. */
    public byte _desireBlockedAttackMode = 0;

    /** Exact target-adjacent slot reserved when a full melee ring opens. */
    public int _desireResumeAttackTargetId = 0;
    /** Packed target coordinates at melee-slot claim time. */
    public int _desireResumeAttackTargetPos = 0;
    public int _desireResumeAttackPos = 0;
    /** Map that owns _desireResumeAttackPos; the NPC may move maps before cleanup. */
    public short _desireResumeAttackMapId = 0;
    // Consecutive no-progress ticks for a claimed melee destination.
    public byte _desireResumeAttackBlockedRetries = 0;

    /** CMoveAroundAction internal wait kind: 0=none, 1=normal 6s rest, 2=short retry. */
    public byte _desireMoveAroundWaitKind = 0;

	private MonsterDeathHandler _deathHandler;
	private MonsterTransformHandler _transformHandler;
	
	public L1MonsterInstance setDeathHandler(MonsterDeathHandler handler){
		_deathHandler = handler;
		return this;
	}
	
	public MonsterDeathHandler getDeathHandler(){
		return _deathHandler;
	}
	
	public L1MonsterInstance setTransformHandler(MonsterTransformHandler handler){
		_transformHandler = handler;
		return this;
	}
	    
	@Override
	public void onItemUse() {
	    if (!isActived() && _target != null) {
	        if (getLevel() <= 45) {
	            useItem(CNpcItemUse.USEITEM_HASTE, 40);
	        }

	        if (getNpcTemplate().is_doppel() && _target instanceof L1PcInstance) {

	            final L1PcInstance targetPc =
	                    (L1PcInstance) _target;

	            setName(targetPc.getName());
	            setNameId(targetPc.getName());
	            setTitle(targetPc.getTitle());
	            setTempLawful(targetPc.getLawful());
	            setCurrentSprite(targetPc.getClassId());

	            for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
	                if (pc == null) {
	                    continue;
	                }
	                pc.outOfSight(this);
	                if (pc.canSeeObject(this)) {
	                    pc.inSight(this);
	                }
	            }
	        }
	    }

	    if (getCurrentHp() * 100 / getMaxHp() < 40) {
	        useItem(CNpcItemUse.USEITEM_HEAL, 50);
	    }
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
	
			
		final boolean appearanceAdmitted = SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this);
		if (appearanceAdmitted) perceivedFrom.addKnownObject(this);
		if (appearanceAdmitted && getHiddenStatus() == HIDDEN_STATUS_SINK) {
			SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_Hide);
		} else if (appearanceAdmitted && getHiddenStatus() == HIDDEN_STATUS_FLY) {
			SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_Moveup);
		}
		onNpcAI();
		
	    NpcEventFactory.onSeePlayer(this, perceivedFrom);
	    
		if (appearanceAdmitted && getBraveSpeed() == 1) {
			SC_SkillBrave.send(perceivedFrom, getId(), 1, 600000);
		}
	}

	public L1MonsterInstance(L1Npc template) {
		super(template);
		_storeDroped = false;
		synchronized (this) {
			CNpcSpecialAction.OnCreated(this, template.get_npcId());

			if (this.getNpcTemplate().get_gfxid() == 7684 || this.getNpcTemplate().get_gfxid() == 7805
					|| this.getNpcTemplate().get_gfxid() == 8063) {
				_PapPearlMonster = new PapPearlMonitor(this);
				_PapPearlMonster.begin();
			}
		}
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		if (!_storeDroped) {
			DropTable.getInstance().setDrop(this, getInventory());			
			if(getInventory() == null)
				return;
			getInventory().shuffle();
			_storeDroped = true;
		}
		setActived(false);
		startAI();
	}

    /** The committed pickup amount excludes pre-existing merged loot. */
    @Override
    public void onGetItem(L1ItemInstance item, int acquiredCount) {
        if (item == null || isDestroyed() || isDead()) return;
        final int before = item.getCount();
        RefineItem();
        final CInventory inventory = getInventory();
        if (inventory == null) return;
        inventory.shuffle();
        if (inventory.GetByServerID(item.getId()) == item) {
            final int remaining = Math.max(0,
                    acquiredCount - Math.max(0, before - item.getCount()));
            setDigestItem(item, remaining);
        }
    }

	@SuppressWarnings("unused")
	@Override
	public void onTalkAction(L1PcInstance pc) {
		if (pc == null)
			return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(getNpcTemplate().get_npcId());
		if(talking == null){
			System.out.println(String.format("NPC Action을 찾을 수 없습니다. NPC ID : %d", getNpcTemplate().get_npcId()));
			return;
		}
		
		String htmlid = null;
		String[] htmldata = null;

		// html 표시 패킷 송신
		if (htmlid != null) { // htmlid가 지정되고 있는 경우
			if (htmldata != null) { // html 지정이 있는 경우는 표시
				SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
			} else {
				SC_NPCTalkReturn.send(pc, objid, htmlid);
			}
		} else {
			if (pc.getLawful() < -1000) { // 플레이어가 카오틱
				SC_NPCTalkReturn.send(pc, talking, objid, 2);
			} else {
				SC_NPCTalkReturn.send(pc, talking, objid, 1);
			}
		}
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (pc == null)
			return;
		if (getCurrentHp() > 0 && !isDead()) {
			// 여기다.
			CAttackAction.attackPcNpc(pc, this, true);
		}
	}

    @Override
    public void ReceiveManaDamage(L1Character attacker, int mpDamage) {
        applyManaDamage(attacker, mpDamage, false);
    }

    /** Credit only MP actually available at the same commit as the debit. */
    public int ReceiveManaDrain(L1Character attacker, int mpDamage) {
        return applyManaDamage(attacker, mpDamage, true);
    }

    private int applyManaDamage(L1Character attacker, int mpDamage, boolean creditDrain) {
        if (attacker == null || mpDamage <= 0) return 0;
        final int credit;
        synchronized (this) {
            if (isDead() || isDestroyed() || dMoveTo.isReturningHome(this)) return 0;
            credit = creditDrain ? drainMana(mpDamage) : 0;
            changeNpcMp(-(long) mpDamage);
        }
        setHate(attacker, mpDamage);
        onNpcAI();
        if (attacker instanceof L1PcInstance) {
            NpcEventFactory.OnClanAwareness(this, attacker);
        }
        return credit;
    }

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		int _npcId = getNpcTemplate().get_npcId();
		if (attacker == null || dMoveTo.isReturningHome(this))
			return;
		if (getCurrentHp() > 0 && !isDead()) {
			if (getHiddenStatus() != HIDDEN_STATUS_NONE) {
				return;
			}
			if (damage >= 0) {
				if (!(attacker instanceof L1EffectInstance)) {
					if(attacker instanceof L1MonsterInstance){
						L1MonsterInstance mon = (L1MonsterInstance) attacker;
							if(mon.getNpcTemplate().get_npcId() != 8500138){
						setAttackHate(attacker, damage);
						}
					} else {
						setAttackHate(attacker, damage);
					}
				}
			}
			if (damage > 0) {
				if (hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)) {
					removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
				} else if (hasSkillEffect(L1SkillId.PHANTASM)) {
					removeSkillEffect(L1SkillId.PHANTASM);
				}
			}

			onNpcAI();

			if (attacker instanceof L1PcInstance
                    && damage > 0) {

                NpcEventFactory.onClanAttacked(
                        this,
                        attacker,
                        damage);

                NpcEventFactory.onPartyAttacked(
                        this,
                        attacker);
			}

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.set_pet_target(this);
				 
				if (getNpcTemplate().get_npcId() == 45681 || getNpcTemplate().get_npcId() == 45682
						|| getNpcTemplate().get_npcId() == 45683 || getNpcTemplate().get_npcId() == 45684
						|| getNpcTemplate().get_npcId() == 45653 || getNpcTemplate().get_npcId() == 900011
						|| getNpcTemplate().get_npcId() == 900012 || getNpcTemplate().get_npcId() == 900013 // 안타라스
						|| getNpcTemplate().get_npcId() == 900038 || getNpcTemplate().get_npcId() == 900039
						|| getNpcTemplate().get_npcId() == 900040// 파푸리온 1차 ~ 3차
						|| getNpcTemplate().get_npcId() == 5096 || getNpcTemplate().get_npcId() == 5097
						|| getNpcTemplate().get_npcId() == 5098 || getNpcTemplate().get_npcId() == 5099
						|| getNpcTemplate().get_npcId() == 5100)// 린드비오르 1차 ~ 3차
				{
					recall(player);
				}
				
				//네쥬 보스
				if(getNpcTemplate().get_npcId() == 5136 || getNpcTemplate().get_npcId() == 45601
						|| getNpcTemplate().get_npcId() == 45609 || getNpcTemplate().get_npcId() == 45617
						|| getNpcTemplate().get_npcId() == 45752 || getNpcTemplate().get_npcId() == 45753
						|| getNpcTemplate().get_npcId() == 45863 || getNpcTemplate().get_npcId() == 8500044
						|| getNpcTemplate().get_npcId() == 8500049 || getNpcTemplate().get_npcId() == 8500060
						|| getNpcTemplate().get_npcId() == 8500063 || getNpcTemplate().get_npcId() == 8500071
						|| getNpcTemplate().get_npcId() == 8500081 || getNpcTemplate().get_npcId() == 8500090
						|| getNpcTemplate().get_npcId() == 8500095 || getNpcTemplate().get_npcId() == 8500111
						|| getNpcTemplate().get_npcId() == 8500129 || getNpcTemplate().get_npcId() == 460000362
						){
					recall(player);
				}
				
				/** 1대라도 치면 해당 아이템드랍 **/
				if (getNpcTemplate().get_npcId() == 707017 || getNpcTemplate().get_npcId() == 45617
						|| getNpcTemplate().get_npcId() == 707022 || getNpcTemplate().get_npcId() == 707023
						|| getNpcTemplate().get_npcId() == 707024 || getNpcTemplate().get_npcId() == 707025
						|| getNpcTemplate().get_npcId() == 5134 || getNpcTemplate().get_npcId() == 5047) {
					if (!player.isBosMon()) {
						player.setBosMon(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 707026) { // 에이션트가디언
					if (!player.isBosMon1()) {
						player.setBosMon1(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 5136) { // 에르자베
					if (!player.isElrzabe()) {
						player.setElrzabe(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 5135) { // 샌드웜
					if (!player.isSandWarm()) {
						player.setSandWarm(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 45529) { // 드레이크
					if (!player.is드레이크()) {
						player.set드레이크(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 7000093) { // 제로스
					if (!player.is제로스()) {
						player.set제로스(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 45684) { // 발라카스
					if (!player.is발라카스()) {
						player.set발라카스(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 900040) { // 파푸리온
					if (!player.is파푸리온()) {
						player.set파푸리온(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 5100) { // 린드비오르
					if (!player.is린드비오르()) {
						player.set린드비오르(true);
					}
				}
				if (getNpcTemplate().get_npcId() == 900013) { // 안타라스
					if (!player.is안타라스()) {
						player.set안타라스(true);
					}
				}
				 int npcId = getNpcTemplate().get_npcId();
			        int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds);
			        for (int i = 0; i < bossIds.length; i++) {
			          if ((npcId == bossIds[i]) && 
			            (!player.is특정보스())) {
			            player.set특정보스(true);
			          }
			        }
			        int[] bossIdss = TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds1);
			        for (int i = 0; i < bossIdss.length; i++) {
			          if ((npcId == bossIdss[i]) && 
			            (!player.is특정보스투())) {
			            player.set특정보스투(true);
			          }
			        }


			}
            final int newHp = applyNpcHpDelta(-(long) damage);
            if (newHp < 0) return;
            if (newHp == 0 && !isDead()) {
				if (attacker instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) attacker;
					
					
					//도감 등록
					
					if (!pc.noPlayerCK && BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
					    ArrayList<BQSInformation> bqsInfos = BQSInformationLoader.getInstance().getInformationsFromNpcId(getNpcId());
					    BQSCharacterData bqs = pc.getBqs();
					    if (bqsInfos != null && !bqsInfos.isEmpty() && bqs != null) {
					        for (BQSInformation bqsInfo : bqsInfos) {
					            bqs.onUpdate(bqsInfo);
					        }
					    }
					}

					if (getNpcTemplate().get_npcId() == 7320012) {
						pc.setBuffnoch(1);
						pc.setCurrentMp(pc.getCurrentMp() + 100);
						pc.setBuffnoch(0);
					}
					if (getNpcTemplate().get_npcId() == 7320013) {
						pc.setBuffnoch(1);
						L1SkillUse.cast(
								pc,
								L1SkillId.GREATER_HEAL,
								pc.getId(), pc.getX(), pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
						pc.setBuffnoch(0);
					}
					if (getNpcTemplate().get_npcId() == 7320014) {
						pc.setBuffnoch(1);
						L1SkillUse.cast(
								pc,
								L1SkillId.BLESS_WEAPON,
								pc.getId(), pc.getX(), pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
						pc.setBuffnoch(0);
					}
					if (getNpcTemplate().get_npcId() == 7320015) {
						pc.setBuffnoch(1);
						L1SkillUse.cast(
								pc,
								L1SkillId.PATIENCE,
								pc.getId(), pc.getX(), pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
						pc.setBuffnoch(0);
					}
					if (getNpcTemplate().get_npcId() == 7320016) {
						pc.setBuffnoch(1);
						L1SkillUse.cast(
								pc,
								L1SkillId.ADVANCE_SPIRIT,
								pc.getId(), pc.getX(), pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
						pc.setBuffnoch(0);
					}
					if (getNpcTemplate().get_npcId() == 7320017) {
						if (pc.hasSkillEffect(L1SkillId.STATUS_DRAGON_PEARL)) {
							pc.killSkillEffectTimer(L1SkillId.STATUS_DRAGON_PEARL);
							SC_Liquor.send(pc, pc.getId(), 0);
							SC_PacketBox.send(pc, SC_PacketBox.DRAGON_PEARL, 8, 0);
							pc.setPearl(0);
						}
					}
				}

				/** 본던 리뉴얼 용아병의 혼령 **/
				if (getNpcTemplate().get_npcId() == 7000075) {// 파란색 동일층
					if (attacker instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) attacker;
						if (pc != null && pc.getMapId() >= 807 && pc.getMapId() <= 813) {
							L1Location newLocation = pc.getLocation().randomLocation(200, true);
							int x = newLocation.getX();
							int y = newLocation.getY();
							short mapid = (short) newLocation.getMapId();
							int heading = pc.getHeading();
							pc.start_teleport(x, y, mapid, heading, 169, true, false);
						}
					}
				}
				if (getNpcTemplate().get_npcId() == 7000074) {// 노란색 아래층
					if (attacker instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) attacker;
						if (pc != null && pc.getMapId() >= 807 && pc.getMapId() <= 813) {
							L1Location newLocation = pc.getLocation().randomLocation(200, true);
							int x = newLocation.getX();
							int y = newLocation.getY();
							short mapid = (short) newLocation.getMapId();
							int heading = pc.getHeading();
							if (pc.getMapId() == 807) {
								pc.start_teleport(x, y, mapid, heading, 169, true, false);
							} else {
								pc.start_teleport(x, y, (mapid - 1), heading, 169, true, false);
							}
						}
					}
				}
				int transformId = getNpcTemplate().getTransformId(); 

				if (transformId == -1) {
					if ((getNpcTemplate().get_gfxid() == 7684 || getNpcTemplate().get_gfxid() == 7805
							|| getNpcTemplate().get_gfxid() == 8063) && _PapPearlMonster != null) {
						_PapPearlMonster.begin();
						_PapPearlMonster = null;
					}
					onDeath(attacker);
				} else {
					transform(transformId);
				}
			}
            if (newHp > 0) {
                hide();
            }
        }
    }

	private void recall(L1PcInstance pc) {
		if (pc == null || getMapId() != pc.getMapId() || pc.get_teleport()) {
			return;
		}
		if (getLocation().getTileLineDistance(pc.getLocation()) > 4) {
			int loc_x = this.getX() + CommonUtil.random(-3, 3);
			int loc_y = this.getY() + CommonUtil.random(-3, 3);

			if (glanceCheck(loc_x, loc_y)) {
				pc.start_teleport(loc_x, loc_y, this.getMapId(), CommonUtil.random(7), 169, true, false);
			}
		}
	}
	 private void BossCoinGet(L1Character lastAttacker)
	  {
	    if (Config.보스아이템지급여부)
	    {
	      int npcId = getNpcTemplate().get_npcId();
	      int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds);
	      for (int i = 0; i < bossIds.length; i++) {
	        if (npcId == bossIds[i]) {
	          l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer greenShared=null;
try{
for (L1PcInstance pc : L1World.getInstance().getAllPlayers())
	          {
	            if ((lastAttacker.getMapId() == pc.getMapId()) && (pc.is특정보스()) && (!pc.isDead()) && (!pc.isInvisble()) && 
	              (getLocation().getTileLineDistance(new Point(pc.getLocation())) < 20))
	            {
	              createNewItem(pc, Config.BossAreaDropItemId, Config.BossAreaDropItemCount, 0);
	              
	              SC_SkillSound.send(pc, pc.getId(), 7783);
	              SC_SkillSound.broadcast(pc, pc.getId(), 7783);
	              if(greenShared==null)
 greenShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
 SC_PacketBox.nativeGreenWriter(Config.한입만멘트 + TYUtil.GetItemInstByID(Config.BossAreaDropItemId).getName() + " (" + Config.BossAreaDropItemCount + "개)"),Integer.MIN_VALUE,Integer.MAX_VALUE);
l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(greenShared,pc);
	              TYUtil.SetSystemMessage(pc, Config.한입만멘트 + 
	              //TYUtil.SetSystemMessage(pc, "\\f=[선물] \\ff보스공략 참여보상 : " + 
	              
	                TYUtil.GetItemInstByID(Config.BossAreaDropItemId).getName()  + " (" + Config.BossAreaDropItemCount + "개)");
	            }
	            pc.set특정보스(false);
	          }
}finally{if(greenShared!=null)greenShared.close();}
	        }
	      }
	    }
	  }
	  
	  private void BossCoinGet1(L1Character lastAttacker)
	  {
	    if (Config.보스아이템지급여부1)
	    {
	      int npcId = getNpcTemplate().get_npcId();
	      int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds1);
	      for (int i = 0; i < bossIds.length; i++) {
	        if (npcId == bossIds[i]) {
	          l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer greenShared=null;
try{
for (L1PcInstance pc : L1World.getInstance().getAllPlayers())
	          {
	            if ((lastAttacker.getMapId() == pc.getMapId()) && (pc.is특정보스투()) && (!pc.isDead()) && (!pc.isInvisble()) && 
	              (getLocation().getTileLineDistance(new Point(pc.getLocation())) < 20))
	            {
	              createNewItem(pc, Config.BossAreaDropItemId1, Config.BossAreaDropItemCount1, 0);
	              
	              SC_SkillSound.send(pc, pc.getId(), 7783);
	              SC_SkillSound.broadcast(pc, pc.getId(), 7783);
	              if(greenShared==null)
 greenShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
 SC_PacketBox.nativeGreenWriter(Config.한입만멘트1 + TYUtil.GetItemInstByID(Config.BossAreaDropItemId1).getName() + " (" + Config.BossAreaDropItemCount1 + "개)"),Integer.MIN_VALUE,Integer.MAX_VALUE);
l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(greenShared,pc);
	              TYUtil.SetSystemMessage(pc, Config.한입만멘트1 + 
	              //TYUtil.SetSystemMessage(pc, "\\f=[선물] \\ff보스공략 참여보상 : " + 
	              
	                TYUtil.GetItemInstByID(Config.BossAreaDropItemId1).getName()  + " (" + Config.BossAreaDropItemCount1 + "개)");
	            }
	            pc.set특정보스투(false);
	          }
}finally{if(greenShared!=null)greenShared.close();}
	        }
	      }
	    }
	  }
	  
    @Override
    public void setCurrentHp(int i) {
        super.setCurrentHp(i);
        onNpcAtomicHpChanged(getCurrentHp());
    }

    @Override
    protected void onNpcAtomicHpChanged(int i) {
        CNpcSpecialAction.OnHpChanged(this, i);
    }

	@Override
	public void setCurrentMp(int i) {
		super.setCurrentMp(i);
	}

    private synchronized boolean DeferDropRemoval(byte mode) {
        if (_dropRemovalRequest == 0) _dropRemovalRequest = mode;
        return _pendingPartyDrop != null;
    }

    @Override
    public void deleteMe() {
        if (DeferDropRemoval(DROP_DELETE)) return;
        _PapPearlMonster = null;
        super.deleteMe();
    }

    @Override
    public void deleteRe() {
        if (DeferDropRemoval(DROP_DELETE_RE)) return;
        super.deleteRe();
    }

    @Override
    public void NpcDie() {
        if (DeferDropRemoval(DROP_NPC_DIE)) return;
        super.NpcDie();
    }

    @Override
    public void dispose() {
        if (DeferDropRemoval(DROP_DISPOSE)) return;
        super.dispose();
    }

    @Override
    public synchronized void setInventory(CInventory inventory) {
        if (_pendingPartyDrop != null && inventory != getInventory()) {
            throw new IllegalStateException("Party drop owns the NPC inventory");
        }
        super.setInventory(inventory);
    }

    @Override
    public synchronized void setDead(boolean dead) {
        if (!dead && _pendingPartyDrop != null) return;
        super.setDead(dead);
    }

    private void ApplyDeferredDropRemoval(byte mode) {
        switch (mode) {
            case DROP_DELETE: deleteMe(); break;
            case DROP_DELETE_RE: deleteRe(); break;
            case DROP_RE: re(); break;
            case DROP_NPC_DIE: NpcDie(); break;
            case DROP_DISPOSE: dispose(); break;
            default: throw new IllegalArgumentException("Unknown drop removal " + mode);
        }
    }

    private void Sahel(L1NpcInstance Sahel) { // 사엘
        L1NpcInstance Pearl = null;
        L1PcInstance PearlBuff = null;
        Sahel.receiveDamage(Sahel, 1);
        for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(Sahel, 5)) {
            if (obj instanceof L1MonsterInstance) {
                L1MonsterInstance mon = (L1MonsterInstance) obj;
                if (mon.getNpcTemplate().get_gfxid() == 7684 || mon.getNpcTemplate().get_gfxid() == 7805)
                    Pearl = mon;
            }
            if (obj instanceof L1PcInstance) {
                L1PcInstance Buff = (L1PcInstance) obj;
                if (!(Buff.hasSkillEffect(L1SkillId.PAP_FIVEPEARLBUFF)
                        || Buff.hasSkillEffect(L1SkillId.PAP_MAGICALPEARLBUFF)))
                    PearlBuff = Buff;
            }
        }
        if (Pearl == null) return; 
        if (Pearl.getNpcTemplate().get_gfxid() == 7684 && PearlBuff != null && Pearl.getCurrentHp() > 0
                && PearlBuff.getCurrentHp() > 0) {
            SC_SkillSound.send(PearlBuff, PearlBuff.getId(), 7836);
            PearlBuff.setSkillEffect(L1SkillId.PAP_FIVEPEARLBUFF, 60 * 1000);
            SC_SkillSound.broadcast(PearlBuff, PearlBuff.getId(), 7836);
            SC_NpcChatPacket.broadcastNpc(Sahel, Sahel, "$8468", 0); // 힐을 줍니다
        }
        if (Pearl.getNpcTemplate().get_gfxid() == 7805 && PearlBuff != null && Pearl.getCurrentHp() > 0
                && PearlBuff.getCurrentHp() > 0) {
            SC_SkillSound.send(PearlBuff, PearlBuff.getId(), 7834);
            PearlBuff.setSkillEffect(L1SkillId.PAP_MAGICALPEARLBUFF, 60 * 1000);
            SC_SkillSound.broadcast(PearlBuff, PearlBuff.getId(), 7834);
            if (PearlBuff.isKnight() || PearlBuff.isCrown() || PearlBuff.isDarkelf() || PearlBuff.isDragonknight()
                    || PearlBuff.isWarrior()) {
                SC_NpcChatPacket.broadcastNpc(Sahel, Sahel, "$8471", 0); // 근거리 물리력에
            } else if (PearlBuff.isElf()) {
                SC_NpcChatPacket.broadcastNpc(Sahel, Sahel, "$8472", 0); // 원거리 물리력에
            } else if (PearlBuff.isWizard() || PearlBuff.isBlackwizard()) {
                SC_NpcChatPacket.broadcastNpc(Sahel, Sahel, "$8470", 0); // 마법에
            } else {
                SC_NpcChatPacket.broadcastNpc(Sahel, Sahel, "$8469", 0); // 헤이스트
            }
        }
    }

    private void PapPearl(L1NpcInstance Pearl) { // 진주
        L1NpcInstance Pap = null;
        L1PcInstance PearlBuff = null;
        Random random = new Random();
        // ── 클러스터 기반: 반경 10 조회 ──
        for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(Pearl, 10)) {
            if (obj instanceof L1MonsterInstance) {
                L1MonsterInstance mon = (L1MonsterInstance) obj;
                if (mon.getNpcTemplate().get_gfxid() == 7864 || mon.getNpcTemplate().get_gfxid() == 7869
                        || mon.getNpcTemplate().get_gfxid() == 7870)
                    Pap = mon;
            }
            if (obj instanceof L1PcInstance) {
                L1PcInstance Buff = (L1PcInstance) obj;
                if (!(Buff.hasSkillEffect(L1SkillId.PAP_FIVEPEARLBUFF)
                        || Buff.hasSkillEffect(L1SkillId.PAP_MAGICALPEARLBUFF)))
                    PearlBuff = Buff;
            }
        }
        int PearlBuffRandom = random.nextInt(10) + 1;
        if (Pap != null && Pap.getCurrentHp() > 0 && Pearl.getNpcTemplate().get_gfxid() == 7684
                && Pearl.getCurrentHp() > 0) {
            Pap.applyNpcHpDelta(3000);
            SC_SkillSound.broadcast(Pearl, Pearl.getId(), 233);
            L1EffectSpawn.getInstance().spawnEffect(900055, 1 * 1000, Pap.getX(), Pap.getY(), Pap.getMapId());
        } else if (Pap != null && Pap.getCurrentHp() > 0 && Pearl.getNpcTemplate().get_gfxid() == 7805
                && Pearl.getCurrentHp() > 0) {
            Pap.setMoveSpeed(1);
            Pap.setSkillEffect(L1SkillId.STATUS_HASTE, 30 * 1000);
            SC_SkillSound.broadcast(Pearl, Pearl.getId(), 224);
        }
        if (PearlBuff != null && PearlBuffRandom == 3 && Pearl.getNpcTemplate().get_gfxid() == 7684) { // 오색
            SC_SkillSound.send(PearlBuff, PearlBuff.getId(), 7836);
            PearlBuff.setSkillEffect(L1SkillId.PAP_FIVEPEARLBUFF, 60 * 1000);
            SC_SkillSound.broadcast(PearlBuff, PearlBuff.getId(), 7836);
        } else if (PearlBuff != null && PearlBuffRandom == 5 && Pearl.getNpcTemplate().get_gfxid() == 7805) { // 신비
            SC_SkillSound.send(PearlBuff, PearlBuff.getId(), 7834);
            PearlBuff.setSkillEffect(L1SkillId.PAP_MAGICALPEARLBUFF, 60 * 1000);
            SC_SkillSound.broadcast(PearlBuff, PearlBuff.getId(), 7834);
        }
    }
    
	@Override
	public void onDeath(L1Character lastAttacker) {
        final int deathEpoch;
        synchronized (this) {
            if (!Die()) return;
            deathEpoch = ++_deathEpoch;
        }

        final DeathSettlement mandatoryDeath = new DeathSettlement(lastAttacker, deathEpoch);

        try {
            setStatus(ActionCodes.ACTION_Die);
            CDesirePQ.releaseBlockedAttack(this);
            releaseNpcTileNow();
            final int dieGfx = getNpcId() >= 8500142 && getNpcId() <= 8500144
                    ? 11 : ActionCodes.ACTION_Die;
            SC_DoActionGFX.broadcast(this, dieGfx);
        } finally {
            SubmitDeathSettlement(mandatoryDeath);
        }
    }

    private boolean isCurrentDeath(int deathEpoch) {
        return _deathEpoch == deathEpoch && isDead() && !_destroyed
                && isDeathProcessing() && _dropRemovalRequest == 0;
    }

    /** Reuse the existing per-death Runnable as the post-pickup continuation. */
    private final class DeathSettlement implements Runnable {
        final L1Character lastAttacker;
        final int epoch;
        L1PcInstance rewardPc;
        CContributeData snapshot;
        CParty party;
        L1PcInstance[] members;
        L1Location origin;
        CInventory source;
        boolean claimed; // guarded by NPC monitor
        boolean routed; // only the claimed dispatcher may run the initial drop
        boolean retryArmed; // guarded by NPC monitor
        int retryAttempts; // guarded by NPC monitor
        boolean handedOff; // initial worker only
        volatile boolean afterPickup;
        byte removalMode;

        DeathSettlement(L1Character lastAttacker, int epoch) {
            this.lastAttacker = lastAttacker;
            this.epoch = epoch;
        }

        @Override
        public void run() {
            if (afterPickup) {
                try {
                    if (removalMode != 0) {
                        ApplyDeferredDropRemoval(removalMode);
                        return;
                    }
                    if (!isCurrentDeath(epoch)) return;
                    giveKarma(rewardPc);
                    processDeathAfterDrop(lastAttacker, epoch);
                } catch (Exception e) {
                    DEATH_DROP_LOG.log(java.util.logging.Level.WARNING, "Party death continuation failed", e);
                } finally {
                    FinishMonsterDeath(epoch);
                }
                return;
            }

            try {
                if (!isCurrentDeath(epoch)) return;
                handleExplosionDamage();
                if (!isCurrentDeath(epoch)) return;
                if (lastAttacker != null) rewardPc = distributeExp(lastAttacker);
                if (!isCurrentDeath(epoch)) return;
                if (rewardPc != null) {
                    if (distributeDrop(this, rewardPc)) return;
                    if (!isCurrentDeath(epoch)) return;
                    giveKarma(rewardPc);
                }
                processDeathAfterDrop(lastAttacker, epoch);
            } catch (Exception e) {
                DEATH_DROP_LOG.log(java.util.logging.Level.WARNING, "Monster death settlement failed", e);
            } finally {
                if (!handedOff) FinishMonsterDeath(epoch);
            }
        }
    }

    private void FinishMonsterDeath(int epoch) {
        synchronized (this) {
            if (isCurrentDeath(epoch)) {
                setExp(0);
                setLawful(0);
                setKarma(0);
                try {
                    allTargetClear();
                } finally {
                    DeathFinished(true);
                }
            }
        }
    }

    private boolean TryQueuePartyDrop(DeathSettlement job, L1PcInstance pc, CContributeData snap) {
        if (!Config.PARTY_LOOT_ENABLE || Config.STANDBY_SERVER || (snap == null || !snap.HasRouteCandidates())
                || (Config.BOSS_DROP_GROUND_ENABLE && BossMonsterSpawnList.find(getNpcId()) != null)) return false;
        final L1PcInstance owner = snap.GetPrimaryOwner(pc);
        final CParty party = owner == null ? null : owner.getParty();
        if (party == null) return false;
        final L1PcInstance[] members = party.getMembers();
        synchronized (this) {
            if (!isCurrentDeath(job.epoch) || _pendingPartyDrop != null || getInventory() == null
                    || owner.getParty() != party) return false;
            job.snapshot = snap;
            job.party = party;
            job.members = members;
            job.origin = new L1Location(getLocation());
            job.source = getInventory();
            job.handedOff = true;
            _pendingPartyDrop = job;
        }
        // Never publish while holding the NPC monitor: the existing serializer
        // may execute immediately on this producer or drain on another thread.
        try {
            if (!L1World.getInstance().AsyncPickItem(this, job.epoch)) DispatchPartyDrop(job.epoch);
        } catch (RuntimeException failure) {
            DEATH_DROP_LOG.log(java.util.logging.Level.WARNING, "Party pickup admission failed", failure);
            DispatchPartyDrop(job.epoch);
        }
        return true;
    }

    /** Called by the existing CWorld serializer, or the same job on admission rejection. */
    public final void DispatchPartyDrop(int epoch) {
        final DeathSettlement job;
        synchronized (this) {
            job = _pendingPartyDrop;
            if (job == null || job.epoch != epoch || job.claimed) return;
            job.claimed = true;
        }

        boolean complete = false;
        try {
            if (getInventory() != job.source || _destroyed || !isDead()) {
                throw new IllegalStateException("Admitted NPC drop source changed");
            }
            if (!job.routed) {
                // Set before routing. A partial failure must never regenerate
                // adena, select recipients again or repeat a successful share.
                job.routed = true;
                try {
                    DropItemOnDying(job.snapshot, job.rewardPc, job.party, job.members, job.origin);
                } finally {
                    // Recovery needs only the remaining inventory and origin.
                    job.snapshot = null;
                    job.party = null;
                    job.members = null;
                }
            }
            complete = RecoverPartyDrop(job);
        } catch (RuntimeException failure) {
            if (job.retryAttempts == 0) {
                DEATH_DROP_LOG.log(java.util.logging.Level.SEVERE, "Party pickup failed", failure);
            }
        } finally {
            if (complete) CompletePartyDrop(job);
            else SchedulePartyDropRetry(job);
        }
    }

    /** Only transfer exact items still linked to the retained corpse inventory. */
    private boolean RecoverPartyDrop(DeathSettlement job) {
        final CInventory source = job.source;
        if (source.getItems().isEmpty()) return true;
        final L1World world = L1World.getInstance();
        for (L1ItemInstance item : source.getItems()) {
            if (item == null || source.GetByServerID(item.getId()) != item || item.getCount() <= 0) continue;
            try {
                world.RecoverNpcDrop(job.origin, source, item);
            } catch (RuntimeException failure) {
                if (job.retryAttempts == 0) {
                    DEATH_DROP_LOG.log(java.util.logging.Level.WARNING,
                            "Party drop remainder recovery failed: " + item.getId(), failure);
                }
            }
        }
        return source.getItems().isEmpty();
    }

    /** Exceptional path only. Retain the existing job and source until drained. */
    private void SchedulePartyDropRetry(DeathSettlement job) {
        // Keep claimed=true until the retry callback admits a new attempt.
        // Late/duplicate world tickets cannot bypass the delay.
        final int attempt;
        synchronized (this) {
            if (_pendingPartyDrop != job || !job.claimed || job.retryArmed) return;
            if (job.retryAttempts < Integer.MAX_VALUE) ++job.retryAttempts;
            attempt = job.retryAttempts;
            job.retryArmed = true;
        }
        if (attempt == 1) {
            DEATH_DROP_LOG.warning("Retaining unpaid party drop: npc=" + getId()
                    + ", epoch=" + job.epoch + ", remaining=" + job.source.getItems().size());
        }
        // At most one delayed task per failed settlement, using the existing
        // scheduler. Never block the world serializer or spin on a full tile.
        final long delayMs = Math.min(30_000L, 250L << Math.min(7, attempt - 1));
        try {
            ThreadPool.getInstance().scheduleRequired(() -> RetryPartyDrop(job, attempt), delayMs);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            // Typically shutdown: do not release the source merely because the
            // retry was rejected. No durable crash guarantee is implied.
            synchronized (this) {
                if (_pendingPartyDrop == job && job.retryAttempts == attempt) job.retryArmed = false;
            }
            DEATH_DROP_LOG.log(java.util.logging.Level.SEVERE,
                    "Party drop retry not admitted; NPC inventory retained: npc=" + getId(), rejected);
        }
    }

    private void RetryPartyDrop(DeathSettlement job, int attempt) {
        synchronized (this) {
            if (_pendingPartyDrop != job || !job.claimed || !job.retryArmed
                    || job.retryAttempts != attempt) return;
            job.retryArmed = false;
            job.claimed = false;
        }
        try {
            if (!L1World.getInstance().AsyncPickItem(this, job.epoch)) DispatchPartyDrop(job.epoch);
        } catch (RuntimeException failure) {
            DEATH_DROP_LOG.log(java.util.logging.Level.WARNING, "Party pickup retry admission failed", failure);
            DispatchPartyDrop(job.epoch);
        }
    }

    private void CompletePartyDrop(DeathSettlement job) {
        synchronized (this) {
            if (_pendingPartyDrop != job) return;
            job.removalMode = _dropRemovalRequest;
            _pendingPartyDrop = null;
        }
        job.snapshot = null;
        job.party = null;
        job.members = null;
        job.origin = null;
        job.source = null;
        job.afterPickup = true;
        // Keep scripts and death completion out of the normal CWorld drain.
        try {
            ThreadPool.getInstance().execute(job);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            job.run();
        }
    }

    private void processDeathAfterDrop(L1Character lastAttacker, int deathEpoch) {
        if (!isCurrentDeath(deathEpoch)) return;
        startChat(CHAT_TIMING_DEAD);
        if (!isCurrentDeath(deathEpoch)) return;
        allTargetClear();
        if (!isCurrentDeath(deathEpoch)) return;
        혈맹포인트(lastAttacker);
        if (!isCurrentDeath(deathEpoch)) return;
        BossCoinGet(lastAttacker);
        if (!isCurrentDeath(deathEpoch)) return;
        BossCoinGet1(lastAttacker);
        if (!isCurrentDeath(deathEpoch)) return;
        handleWorldEvents(lastAttacker);
    }

	// ---------------------------------------------------------
	// 보조 메소드들 
	// ---------------------------------------------------------
    private void handleExplosionDamage() {
        int npcId = getNpcId();
        if (npcId == 45263 || npcId == 7320176 || npcId == 7320180 || npcId == 7320182) {
            for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(this, 2)) {
                if (obj instanceof L1PcInstance) {
                    L1PcInstance pc = (L1PcInstance) obj;
                    if (!pc.isDead() && !GameUtils.isCounterMagic(pc)) {
                        pc.receiveDamage(this, GameRandom.next(200) + 200, 1);
                    }
                }
            }
        }
    }

	private void handleWorldEvents(L1Character lastAttacker) {
	    // 문 열기 및 타임맵 처리
	    if (getNpcTemplate().getDoor() > 0) {
	        int doorId = getNpcTemplate().getDoor();
	        if (getNpcTemplate().getCountId() > 0) {
	            TimeMapController.getInstance().add(new L1TimeMap(getNpcTemplate().getCountId(), 7200, doorId));
	        }
	        L1DoorInstance door = DoorSpawnTable.getInstance().getDoor(doorId);
	        if (door != null) door.open(); 
	    }

	    if (lastAttacker instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) lastAttacker;
	        handleTimeRiftItems(pc);
	    }
	    // 보스 컨트롤러 (Crock/Tikal)
	 //   handleBossCounters();
	}

	private void handleTimeRiftItems(L1PcInstance pc) {
	    int mapId = getMap().getId();
	    if (mapId == 781 || mapId == 783) {
	        if (GameRandom.next(100) < 15) { // 15% 확률 (기존 rnd >= 85와 동일)
	            int itemId = (mapId == 781) ? 100036 : 500210;
	            if (!pc.getInventory().HaveItemByClassId(itemId, 1)) {
	                L1ItemInstance item = pc.getInventory().CreateOrAddItem(itemId, 1);
	                SC_ServerMessage.send(pc, 143, getNpcTemplate().get_name(), item.getItem().getName());
	            }
	        }
	    }
	}

	private L1PcInstance distributeExp(L1Character lastAttacker) {
		if (lastAttacker == null) {
			return null;
		}
		L1PcInstance pc = null;
		if (lastAttacker instanceof L1PcInstance) {
			pc = (L1PcInstance) lastAttacker;
		} else if(lastAttacker instanceof L1CompanionInstance){
			pc = ((L1CompanionInstance) lastAttacker).get_master();
		} else if (lastAttacker instanceof L1SummonInstance) {
			pc = (L1PcInstance) ((L1SummonInstance) lastAttacker).getMaster();
		}
		if (pc != null && !pc.noPlayerCK && !pc.noPlayerck2 && !pc.isDead()) {
			ArrayList<L1Character> targetList = new ArrayList<>();
			ArrayList<Integer> hateList = new ArrayList<>();
			_hateList.copySnapshotTo(targetList, hateList);
			
			int exp = getExp();
			CalcExp.calcExp(pc, getId(), targetList, hateList, exp);
					
			if (isDead()) return pc;

		} else if (lastAttacker instanceof L1EffectInstance)

		{
			ArrayList<L1Character> targetList = new ArrayList<>();
			ArrayList<Integer> hateList = new ArrayList<>();
			_hateList.copySnapshotTo(targetList, hateList);
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
				} else if(lastAttacker instanceof L1CompanionInstance){
					pc = ((L1CompanionInstance) lastAttacker).get_master();
				} else if (lastAttacker instanceof L1SummonInstance) {
					pc = (L1PcInstance) ((L1SummonInstance) lastAttacker).getMaster();
				}
				int exp = getExp();
				CalcExp.calcExp(pc, getId(), targetList, hateList, exp);
				if (isDead()) return pc;
			}
		}
		return null;
	}

    /** Returns true only when the mandatory continuation belongs to the world job. */
    private boolean distributeDrop(DeathSettlement job, L1PcInstance pc) {
        try {
            int npcId = getNpcTemplate().get_npcId();
            if (npcId == 45640 && getCurrentSpriteId() != 2332) return false;
            ArrayList<L1Character> dropTargetList = new ArrayList<>();
            ArrayList<Integer> dropHateList = new ArrayList<>();
            ArrayList<Integer> damage = new ArrayList<>();
            _dropHateList.copySnapshotTo(dropTargetList, dropHateList, damage);
            final CContributeData snap = CContributeData.Distribute(this, dropTargetList, dropHateList, damage);
            if (TryQueuePartyDrop(job, pc, snap)) return true;
            if (isCurrentDeath(job.epoch)) DropItemOnDying(snap, pc, null, null, getLocation());
        } catch (Exception failure) {
            DEATH_DROP_LOG.log(java.util.logging.Level.WARNING, "Monster drop preparation failed", failure);
        }
        return job.handedOff;
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
			SC_Karma.send(pc);
		}
	}

	public boolean is_storeDroped() {
		return _storeDroped;
	}

	public void set_storeDroped(boolean flag) {
		_storeDroped = flag;
	}

	private int _ubSealCount = 0;

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
        if (getHiddenStatus() != HIDDEN_STATUS_NONE
                || isDead() || isDestroyed() || getCurrentHp() <= 0
                || getMaxHp() / 3 <= getCurrentHp()) {
            return;
        }

        // The existing L1 monster-specific HP/probability policy stays here.
        // Only the state/action mutation is handed to the NPC owner.
        final int event;
        final int odds;
        switch (getNpcTemplate().get_npcId()) {
        case 45061: case 45161: case 45181: case 45455:
            event = HATE_EVENT_HIDE_SINK;
            odds = 10;
            break;
        case 45682:
            event = HATE_EVENT_HIDE_ANTHARAS;
            odds = 50;
            break;
        case 45067: case 45264: case 45452: case 45090:
        case 45321: case 45445: case 75000:
            event = HATE_EVENT_HIDE_FLY;
            odds = 10;
            break;
        case 45681:
            event = HATE_EVENT_HIDE_DRAGON_FLY;
            odds = 50;
            break;
        default:
            return;
        }
        if (_random.nextInt(odds) == 0) {
            NpcEventFactory.requestHide(this, event);
        }
    }


	public void initHide() {
		int npcid = getNpcTemplate().get_npcId();
		if (npcid == 45061 || npcid == 45161 || npcid == 45181 || npcid == 45455 || npcid == 400000
				|| npcid == 400001) {
			int rnd = _random.nextInt(3);
			if (1 > rnd) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(13);
			}
		} else if (npcid == 45045 || npcid == 45126 || npcid == 45134 || npcid == 45281 || npcid == 75003) {
			int rnd = _random.nextInt(3);
			if (1 > rnd) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(4);
			}
		} else if (npcid == 217) {
			setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
			setStatus(6);
		} else if (npcid == 45067 || npcid == 45264 || npcid == 45452 || npcid == 45090 || npcid == 45321
				|| npcid == 45445 || npcid == 75000) {
			setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
			setStatus(4);
		} else if (npcid == 45681) {
			setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
			setStatus(11);
		}
	}

	public void initHideForMinion(L1NpcInstance leader) {
		int npcid = getNpcTemplate().get_npcId();
		if (leader.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
			if (npcid == 45061 || npcid == 45161 || npcid == 45181 || npcid == 45455 || npcid == 400000
					|| npcid == 400001) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(13);
			} else if (npcid == 45045 || npcid == 45126 || npcid == 45134 || npcid == 45281 || npcid == 75003) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_SINK);
				setStatus(4);
			}
		} else if (leader.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_FLY) {
			if (npcid == 45067 || npcid == 45264 || npcid == 45452 || npcid == 45090 || npcid == 45321 || npcid == 45445
					|| npcid == 75000) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
				setStatus(4);
			} else if (npcid == 45681) {
				setHiddenStatus(L1NpcInstance.HIDDEN_STATUS_FLY);
				setStatus(11);
			}
		}
	}

	@Override
	protected synchronized void transform(int transformId) {
        if (_destroyed || isDead() || isDeathProcessing() || _dropRemovalRequest != 0) return;
        cancelDigestItems();
		if(_transformHandler != null)
			_transformHandler.onTransFormNotify(this);
		super.transform(transformId);
		getInventory().clearItems();
		DropTable.getInstance().setDrop(this, getInventory());
		getInventory().shuffle();
	}

	private PapPearlMonitor _PapPearlMonster;// 행동

	public class PapPearlMonitor implements Runnable {
		private final L1MonsterInstance _Pearl;

		public PapPearlMonitor(L1MonsterInstance npc) {
			_Pearl = npc;
		}

		public void begin() {
			ThreadPool.getInstance().scheduleRequired(this, 3000);
		}

		@Override
		public void run() {
			try {
				if (_Pearl.getNpcTemplate().get_gfxid() == 7684 || _Pearl.getNpcTemplate().get_gfxid() == 7805)
					PapPearl(_Pearl);
				else if (_Pearl.getNpcTemplate().get_gfxid() == 8063)
					Sahel(_Pearl);
			} catch (Exception exception) {
			}
		}
	}

	private boolean createNewItem(L1PcInstance pc, int item_id, int count, int Bless) {
		L1ItemInstance item = CObjectDB.LoadItem().createItem(item_id);

		if (item != null) {
			item.setCount(count);
			item.setBless(Bless);
			if (pc.getInventory().CheckAddable(item, count) == CInventory.OK) {
				pc.getInventory().CreateOrAddItem(item);
				pc.getInventory().updateItem(item, CUserInventory.COL_BLESS);
				pc.getInventory().saveItem(item, CUserInventory.COL_BLESS);
			} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
				L1World.getInstance().getInventory(pc.getX(), pc.getY(), pc.getMapId()).CreateOrAddItem(item);
			}
			if (pc.isInParty()) { // 파티 인경우
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                        pc.getParty().getMembers(),
                        SC_ServerMessage.nativeWriter(813,
                                getNpcTemplate().get_name(),item.getLogName(),pc.getName()));
            } else { // 파티가 아닌 경우
				SC_ServerMessage.send(pc, 143, getNpcTemplate().get_name(), item.getLogName());
			}
			return true;
		} else {
			return false;
		}
	}


	private void 혈맹포인트(L1Character lastAttacker) {
		if ((lastAttacker instanceof L1PcInstance)) {
		L1PcInstance pc = (L1PcInstance) lastAttacker;
		if (Config.혈맹포인트여부) {
		      int 혈맹포인트맵 = getMapId();
		      int[] 혈맹포인트맵번호 = TYUtil.GetIntTokenizeIntArray(Config.혈맹포인트맵번호);
		      for (int i = 0; i < 혈맹포인트맵번호.length; i++) {
		        if (혈맹포인트맵 == 혈맹포인트맵번호[i]) {
//		        	 if (Config.STANDBY_SERVER) {
//							(new SC_ChatPacket(pc, "오픈대기중에는 혈맹포인트 적립이 되지 않습니다.")).send(pc);
//							
//		        	 } 딘ㄷ 
		        	int npcId = getNpcTemplate().get_npcId();
		        	 if (!(npcId == 4037000 || npcId == 4037100 || npcId == 4037101 || npcId == 4037102 || npcId == 4037200 || npcId == 4037201 || npcId == 4037202)) {
		        		 pc.set_monCount(pc.get_monCount()+1);
				        	 }
		        	
			if (pc.get_monCount() >= Config.혈맹포인트카운트) {
				pc.set_monCount(0); //몬스터킬수 +1 
				if (pc.getClanid() != 0) {
				ClanTable.changeWarPoint(pc.getClan(), 1, null);
				
				
				}
				
			}
		        }
		      }
			}
		
		}
		
	}
	
    @Override
    public synchronized void resurrect(int hp) {
        if (_destroyed || isDeathProcessing()) return;
        final boolean wasDead = isDead();
        super.resurrect(hp);
        if (wasDead && !isDead() && getCurrentHp() > 0) ++_deathEpoch;
    }

    public synchronized void re() {
        // Explicit script removal invalidates the old reward generation.
        if (_destroyed || DeferDropRemoval(DROP_RE)) return;
        if (!isDead() && !Die()) return;
        ++_deathEpoch;
        setExp(0);
        setKarma(0);
        setLawful(0);
        allTargetClear();
        DeathFinished(false);
        deleteRe();
    }

	// 오림 인던 관련
	private boolean _isCurseMimic;

	public void setCurseMimic(boolean curseMimic) {
		_isCurseMimic = curseMimic;
	}

	public boolean isCurseMimic() {
		return _isCurseMimic;
	}

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_MONSTER : _instanceType;
	}
}