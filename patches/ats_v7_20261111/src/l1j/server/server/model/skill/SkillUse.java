
package l1j.server.server.model.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.CharacterAction.Teleport.Teleport;
import l1j.server.Templates.L1BookMark;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.clientpackets.C_CommonClick;
import l1j.server.server.clientpackets.C_NewCharSelect;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.MapRingTelTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.Magic;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1AuctionBoardInstance;
import l1j.server.server.model.Instance.BoardInstance;
import l1j.server.server.model.Instance.CrownInstance;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.DoorInstance;
import l1j.server.server.model.Instance.DwarfInstance;
import l1j.server.server.model.Instance.EffectInstance;
import l1j.server.server.model.Instance.FieldObjectInstance;
import l1j.server.server.model.Instance.FurnitureInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.Instance.TeleporterInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.poison.CurseParalysis;
import l1j.server.server.model.poison.DamagePoison;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.serverpackets.S_ChangeHeading;
import l1j.server.server.serverpackets.S_ChangeName;
import l1j.server.server.serverpackets.S_ChangeShape;
import l1j.server.server.serverpackets.S_CharAmount;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_Coma;
import l1j.server.server.serverpackets.S_CurseBlind;
import l1j.server.server.serverpackets.S_Dexup;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_Invis;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_NpcChatPacket;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_Poison;
import l1j.server.server.serverpackets.S_RangeSkill;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_ShowPolyList;
import l1j.server.server.serverpackets.S_ShowSummonList;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconAura;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillIconShield;
import l1j.server.server.serverpackets.S_SkillIconWindShackle;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_Strup;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_TrueTarget;
import l1j.server.server.serverpackets.S_UseAttackSkill;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;

public class SkillUse {

	public static final int TYPE_NORMAL = 0;
	public static final int TYPE_LOGIN = 1;
	public static final int TYPE_SPELLSC = 2;
	public static final int TYPE_NPCBUFF = 3;
	public static final int TYPE_GMBUFF = 4;
	private static final int PC_PC = 1;
	private static final int PC_NPC = 2;
	private static final int NPC_PC = 3;
	private static final int NPC_NPC = 4;
	private L1Character _user = null;        // 유저 나 자신 ?
	private L1Character _target = null;      // 타겟 
	private L1PcInstance _pc = null;         // 피시 
	private L1NpcInstance _npc = null;       // 엔피시
	private L1NpcInstance _targetNpc = null; // 타겟 엔피시
	private boolean _isGlanceCheckFail = false;
	private L1Skills _skill;
	private int _skillId;                    // 스킬 아이디 
	private int _getBuffDuration;            // 스킬 시간
	private int _shockStunDuration;          // 쇼크 스턴 시간
	private int _getBuffIconDuration;        // 버프 아이콘 시간
	private int _targetID;                   // 타겟 아이디
	private int _mpConsume = 0;              // 엠피 소모량
	private int _hpConsume = 0;              // 피 소모량
	private int _targetX = 0;                // 타겟 좌표 X
	private int _targetY = 0;                // 타겟 좌표 Y
	private String _message = null;          // 메세지
	private int _skillTime = 0;              // 스킬 타이머
	private boolean _isPK = false;           // 피케이 인지 여부
	private int _bookmarkId = 0;             // 북마크 아이디
	private int _itemobjid = 0;              // 아이템 오브젝트 
	private boolean _checkedUseSkill = false; // 사전 체크가 끝난 상태인가
	private int _leverage = 10;              // 1/10배이므로 10으로 1배
	private boolean _isFreeze = false;       // 동결 상태 
	private boolean _isCounterMagic = true;  // 카운터 매직 처리 
	private int _calcType;                   // 캐릭터 타입 종류
	private Random random = new Random(System.nanoTime());	
	private ArrayList<TargetStatus> _targetList;	
	public static Map<Integer, L1Object> _truetargetList = new HashMap<Integer, L1Object>();
	private static Logger _log = Logger.getLogger(SkillUse.class.getName());

	public SkillUse() {}

	private static class TargetStatus {
		private L1Character _target = null;
		private boolean _isCalc = true;
		public TargetStatus(L1Character _cha) {
			_target = _cha;
		}
		public TargetStatus(L1Character _cha, boolean _flg) {
			_isCalc = _flg;
		}
		public L1Character getTarget() {
			return _target;
		}
		public boolean isCalc() {
			return _isCalc;
		}
	}
	public void setLeverage(int i) {
		_leverage = i;
	}
	public int getLeverage() {
		return _leverage;
	}
	private boolean isCheckedUseSkill() {
		return _checkedUseSkill;
	}
	private void setCheckedUseSkill(boolean flg) {
		_checkedUseSkill = flg;
	}

	public boolean checkUseSkill(L1PcInstance player, int skillid, int target_id, int x, int y, String message,
			int time, int type,
			L1Character attacker) {
		// 유저 체크 스킬 
		if (player instanceof L1PcInstance) {
			L1Object l1object = L1World.getInstance().findObject(target_id);
			if (l1object instanceof ItemInstance) {
				ItemInstance item = (ItemInstance) l1object;
				if (item.getX() != 0 && item.getY() != 0 || player.isDead()) { // 지면상의 아이템은 아니고, 누군가의 소유물
					if (skillid == SkillId.텔레포트 || skillid == SkillId.매스_텔레포트 || skillid == SkillId.텔레포트_투_마더) {
						player.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false), true);
					}
					return false;
				}
			}
		}
		// 존재버그 관련 추가
		if (player instanceof L1PcInstance) {
			L1PcInstance jonje = L1World.getInstance().getPlayer(player.getName());
			if (jonje == null && player.getAccessLevel() != 200) {
				player.sendPackets(new S_SystemMessage("존재버그 강제종료! 재접속하세요"), true);
				player.sendPackets(new S_Disconnect(), true);
				return false;
			}
		}
		// 초기설정
		setCheckedUseSkill(true);
		_targetList = new ArrayList<TargetStatus>(); // 타겟 리스트의 초기화
		_skill = SkillsTable.getInstance().getTemplate(skillid);

		if (_skill == null) {
			System.out.println(String.format("스킬 테이블에 없는 스킬 번호가 사용되었습니다. 스킬아이디 : %d", skillid));
			return false;
		}
		_skillId = skillid;
		_targetX = x;
		_targetY = y;
		_message = message;
		_skillTime = time;
		boolean checkedResult = true;

		if (attacker == null) {
			_pc = player;
			_user = _pc;
		} else {
			_npc = (L1NpcInstance) attacker;
			_user = _npc;
		}

		if (_skill.getTarget().equals("none")) {
			_targetID = _user.getId();
			_targetX = _user.getX();
			_targetY = _user.getY();
		} else {
			_targetID = target_id;
		}

		if (type == TYPE_NORMAL) { // 통상의 마법 사용시
			checkedResult = isNormalSkillUsable();
		} else if (type == TYPE_SPELLSC) { // 스펠 스크롤 사용시
			checkedResult = isSpellScrollUsable();
		} else if (type == TYPE_NPCBUFF) {
			checkedResult = true;
		}
		if (!checkedResult) {
			return false;
		} 
		if (_skillId == SkillId.파이어_월 || _skillId == SkillId.라이프_스트림) {
			return true;
		} 

		L1Object l1object = L1World.getInstance().findObject(_targetID);
		if (l1object instanceof ItemInstance) {
			_log.fine("skill target item name: " + ((ItemInstance) l1object).getViewName());
			return false;
		}
		if (_user instanceof L1PcInstance) {
			if (l1object instanceof L1PcInstance) {
				_calcType = PC_PC;
			} else {
				_calcType = PC_NPC;
				_targetNpc = (L1NpcInstance) l1object;
			}
		} else if (_user instanceof L1NpcInstance) {
			if (l1object instanceof L1PcInstance) {
				_calcType = NPC_PC;
			} else if (_skill.getTarget().equals("none")) {
				_calcType = NPC_PC;
			} else {
				_calcType = NPC_NPC;
				_targetNpc = (L1NpcInstance) l1object;
			}
		}

		// 텔레포트, 매스 텔레포트는 대상이 북마크 ID
		if (_skillId == SkillId.텔레포트 || _skillId == SkillId.매스_텔레포트  || _skillId == SkillId.트루_타겟) {
			_bookmarkId = target_id; 
		}
		// 대상이 아이템인 스킬
		if (_skillId == SkillId.크리에이트_매지컬_웨폰 || _skillId == SkillId.브링_스톤 || _skillId == SkillId.블레스드_아머 
				|| _skillId == SkillId.인챈트_웨폰 || _skillId == SkillId.쉐도우_팽) {
			_itemobjid = target_id;
		}
		_target = (L1Character) l1object;

		if (!(_target instanceof MonsterInstance)
				&& _skill.getTarget().equals("attack")
				&& _user.getId() != target_id) {
			_isPK = true; // 타겟이 monster 이외로 공격계 스킬로, 자신 이외의 경우 PK모드로 한다.
		}
		// 사전 체크
		if (!(l1object instanceof L1Character)) { // 타겟이 캐릭터 이외의 경우 아무것도 하지 않는다.
			checkedResult = false;
		}
		makeTargetList(); // 타겟의 일람을 작성
		if (_targetList.size() == 0 && (_user instanceof L1NpcInstance)) {
			checkedResult = false;
		}
		return checkedResult;
	}
	/**
	 * 확률계 스킬, 힐 계열 스킬 보라돌이 처리
	 */
	private void pinkName() {
		
		if(_calcType == PC_PC && _user.getZoneType() == 0 && _target.getZoneType() != 1) {
			// 힐계
			if(_skill.getType() == L1Skills.TYPE_HEAL || _skillId == SkillId.이뮨_투함) {
				if (_target instanceof L1PcInstance) {
					L1PcInstance target = (L1PcInstance) _target;
					// 타겟이 보라돌이 일때에만 
					if(target.isPinkName()) {
						L1PinkName.onAction(target, _user);
					}				
				}			
			}
			// 확률, 저주계
			if(_skill.getTarget().equals("buff"))  {
				if(_skill.getType() == L1Skills.TYPE_PROBABILITY || _skill.getType() == L1Skills.TYPE_CURSE) {
					if (_target instanceof L1PcInstance) {
						L1PcInstance target = (L1PcInstance) _target;
						L1PinkName.onAction(target, _user);
					}				
				}
			}	
		}
	}
	/**
	 * 통상의 스킬 사용시에 사용자 상태로부터 스킬이 사용 가능한가 판단한다
	 *
	 * @return false 스킬이 사용 불가능한 상태인 경우
	 */
	private boolean isNormalSkillUsable() { 
		// 스킬 사용자가 PC의 경우의 체크
		if (_user instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _user;

			// 스킬 지연중 사용 불가
			if (pc.isSkillDelay()) {
				return false;
			} 
			// 아이템 재료 체크 
			if (!isItemConsume()) {
				_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_29), true);
				return false;
			}	
			// 피시가 커스 패럴라이즈 상태 일 경우 
			if (pc.isParalyzed()) {
				return false;
			}  
			// 인비지 상태일 때 체크 
			if (!SkillUtil.isInvisUsableSkill(_skillId) && pc.isInvisble() || pc.isInvisDelay()) { // 인비지중에 사용 불가의 스킬
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_44), true);
				return false;
			}
			// 침묵 상태에서는 사용 불가
			if (!SkillUtil.isSilenceUsableSkill(_skillId) && pc.hasSkillEffect(SkillId.사일런스) 
					|| pc.hasSkillEffect(SkillId.에이리어_오브_사일런스) || pc.hasSkillEffect(SkillId.상태_침묵)) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_30), true);
				return false;	
			}
			// 무게 체크
			if (pc.getInventory().getWeight100() > 82) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_31), true);
				return false;
			}
			// 카운터 배리어, 쇼크 스턴 무기타입  체크 
			if (_skillId == SkillId.카운터_배리어 || _skillId == SkillId.쇼크_스턴) {
				if (pc.getWeapon().getItem().getType() != L1Item.TYPE_WEAPON_TOHANDSWORD) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_32), true);
					return false;
				}
			}		
			// 정령 마법 속성 체크
			if (!isAttrAgrees()) {
				return false;

			}
			/////// 공통  ///////
			// 변신 체크 
			int polyId = pc.getCurrentSpriteId();
			L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);
			if (poly != null && !poly.canUseSkill()) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_30), true);
				return false;
			}	
			// 이벤트 엔피시 스폰 체크
			L1EventNpcSpawn event_map_check = EventNpcSpawnTable.getInstance().getEventMapCheck(pc.getMapId());
			if ((event_map_check != null) && (!pc.isGm())) {
				if (!EventNpcSpawnCotroller.isSpawn(event_map_check)) {
					if (_skillId != 0) {
						pc.sendPackets(new S_ChatPacket(pc, "사냥시간이 아니므로 스킬을 사용 할 수 없습니다."), true);
						return false; 
					}
				}
			}
		}
		// 스킬 사용자가 NPC의 경우의 체크
		else if (_user instanceof L1NpcInstance) {
			// 침묵 상태에서는 사용 불가
			if (_user.hasSkillEffect(SkillId.사일런스)) {
				_user.removeSkillEffect(SkillId.사일런스);
				return false;
			}
		}
		// 스킬 사용시 hp, mp 감소 체크
		if (!isHPMPConsume()) { 
			return false;
		} 
		return true;
	}

	/**
	 * 스펠 스크롤 사용시에 사용자 상태로부터 스킬이 사용 가능한가 판단한다
	 *
	 * @return false 스킬이 사용 불가능한 상태인 경우
	 */
	private boolean isSpellScrollUsable() {
		L1PcInstance pc = (L1PcInstance) _user;

		if(pc.isParalyzed()) {
			return false;
		}
		if(!SkillUtil.isInvisUsableSkill(_skillId) && pc.isInvisble() || pc.isInvisDelay()) { // 인비지중에 사용 불가의 스킬
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_44), true);
			return false;
		}
		return true;
	}
	public void handleCommands(L1PcInstance player, int skillId, int targetId,
			int x, int y, String message, int timeSecs, int type) {
		L1Character attacker = null;
		handleCommands(player, skillId, targetId, x, y, message, timeSecs, type, attacker);
	}

	public void handleCommands(L1PcInstance player, int skillId, int targetId,
			int x, int y, String message, int timeSecs, int type, L1Character attacker) {

		try {
			if (!isCheckedUseSkill()) {
				boolean isUseSkill = checkUseSkill(player, skillId, targetId, x, y, message, timeSecs, type, attacker);
				if (!isUseSkill) {
					failSkill();   
					return;
				}
			} 	 
			if (type == TYPE_NORMAL) {
				if (!_isGlanceCheckFail || _skill.getArea() > 0 || _skill.getTarget().equals("none")) {
					runSkill();
					useConsume();
					sendGrfx(true);
					sendFailMessageHandle();
					setDelay();
					pinkName();
				}
			} else if (type == TYPE_LOGIN) { // 로그인시(HPMP 재료 소비 이루어, 그래픽 없음)
				runSkill();
			} else if (type == TYPE_SPELLSC) { // 스펠 스크롤 사용시(HPMP 재료 소비 없음) 
				runSkill();
				sendGrfx(true);
				setDelay();
			} else if (type == TYPE_GMBUFF) { // GMBUFF 사용시(HPMP 재료 소비 이루어, 마법 모션 없음)
				runSkill();
				sendGrfx(false);
			} else if (type == TYPE_NPCBUFF) { // NPCBUFF 사용시(HPMP 재료 소비 없음)
				runSkill();
				sendGrfx(true);
			}
			setCheckedUseSkill(false); 
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	/**
	 * 스킬의 실패 처리(PC만)
	 */
	private void failSkill() {
		setCheckedUseSkill(false);
		// 텔레포트 스킬
		if (_skillId == SkillId.텔레포트 || _skillId == SkillId.매스_텔레포트 || _skillId == SkillId.텔레포트_투_마더) {
			_pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
		}
	}

	private boolean isTarget(L1Character cha) throws Exception {
		boolean _flg = false;

		// pc가 고스트, 운영자투명일때 return
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			if (pc.isGhost() || pc.isGmInvis()) {
				return false;
			}
		}
		// 캐릭터가 엔피시일경우 피시, 서먼, 펫을 타겟팅 한다.
		if (_calcType == NPC_PC
				&& (cha instanceof L1PcInstance || cha instanceof PetInstance || cha instanceof SummonInstance)) {
			_flg = true;
		}
		// 타겟이 문일 경우 return
		if (cha instanceof DoorInstance) {
			if (cha.getMaxHp() == 0 || cha.getMaxHp() == 1) {
				return false;
			}
		}
		// 캐릭터가 서먼일경우 주인이 pc일때 return 마을일 경우 return
		if ((_skill.getTarget().equals("attack") || _skill.getType() == L1Skills.TYPE_ATTACK)
				&& _calcType == NPC_PC && cha instanceof L1PcInstance && _user instanceof SummonInstance) {
			SummonInstance summon = (SummonInstance) _user;
			if (cha.getId() == summon.getMaster().getId()) {
				return false;
			}
			if (cha.getZoneType() == 1) {
				return false;
			}
		}
		// 캐릭터가 펫일경우 주인이 pc일때 return 마을일 경우 return
		if ((_skill.getTarget().equals("attack") || _skill.getType() == L1Skills.TYPE_ATTACK)
				&& _calcType == NPC_PC && cha instanceof L1PcInstance && _user instanceof PetInstance) {
			PetInstance pet = (PetInstance) _user;
			if (cha.getId() == pet.getMaster().getId()) {
				return false;
			}
			if (cha.getZoneType() == 1) {
				return false;
			}
		}
		// 마법인형이 헤이스트 상태가 아니거나 같을때
		if (cha instanceof DollInstance && _skillId != SkillId.헤이스트) {
			return false;
		}
		// 타겟이 Pet, Summon 이외의 NPC의 경우, PC, Pet, Summon는 대상외
		if (_calcType == PC_NPC && _target instanceof L1NpcInstance && !(_target instanceof PetInstance)
				&& !(_target instanceof SummonInstance) && (cha instanceof PetInstance
						|| cha instanceof SummonInstance || cha instanceof L1PcInstance)) {
			return false;
		}
		// NPC대  PC로 타겟이 monster의 경우 타겟은 아니다.
		if ((_skill.getTarget().equals("attack") || _skill.getType() == L1Skills.TYPE_ATTACK)
				&& _calcType == NPC_PC && !(cha instanceof PetInstance)
				&& !(cha instanceof SummonInstance) && !(cha instanceof L1PcInstance)) {
			return false;
		}
		// NPC대  NPC로 사용자가 monster로, 타겟이 monster의 경우 타겟은 아니다.
		if ((_skill.getTarget().equals("attack")
				|| _skill.getType() == L1Skills.TYPE_ATTACK) && _calcType == NPC_NPC && _user instanceof MonsterInstance && cha instanceof MonsterInstance) {
			return false;
		}
		if (_skill.getTarget().equals("none")
				&& _skill.getType() == L1Skills.TYPE_ATTACK && (cha instanceof L1AuctionBoardInstance || cha instanceof BoardInstance || cha instanceof CrownInstance
						|| cha instanceof DwarfInstance || cha instanceof EffectInstance || cha instanceof FieldObjectInstance 
						|| cha instanceof FurnitureInstance || cha instanceof L1HousekeeperInstance || cha instanceof L1MerchantInstance 
						|| cha instanceof TeleporterInstance)) {
			return false;
		}
		if (_skill.getType() == L1Skills.TYPE_ATTACK && cha.getId() == _user.getId()) {
			return false;
		}
		// 타겟이 나일때 힐올은 효과를 발동하지 않는다.
		if (cha.getId() == _user.getId() && _skillId == SkillId.힐올) {
			return false;
		}
		// 타겟이 파티나 클랜원일 겨우 자신에게 효과가 있다.(다만, 힐 올은 제외)
		if (((_skill.getTargetTo() & L1Skills.TARGET_TO_PC) == L1Skills.TARGET_TO_PC
				|| (_skill.getTargetTo() & L1Skills.TARGET_TO_CLAN) == L1Skills.TARGET_TO_CLAN 
				|| (_skill.getTargetTo() & L1Skills.TARGET_TO_PARTY) == L1Skills.TARGET_TO_PARTY)
				&& cha.getId() == _user.getId() && _skillId != SkillId.힐올) {
			return true;
		}
		// 스킬 사용자가 PC로, PK모드가 아닌 경우, 자신의 서먼,펫 대상 외
		if (_user instanceof L1PcInstance
				&& (_skill.getTarget().equals("attack") || _skill.getType() == L1Skills.TYPE_ATTACK)
				&& !_isPK) {
			if (cha instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) cha;
				if (_pc.getId() == summon.getMaster().getId()) {
					return false;
				}
			} else if (cha instanceof PetInstance) {
				PetInstance pet = (PetInstance) cha;
				if (_pc.getId() == pet.getMaster().getId()) {
					return false;
				}
			}
		}
		if ((_skill.getTarget().equals("attack")
				|| _skill.getType() == L1Skills.TYPE_ATTACK)
				&& !(cha instanceof MonsterInstance) && !_isPK 
				&& _target instanceof L1PcInstance) {
			L1PcInstance enemy = (L1PcInstance) cha;
			// 카운터 디텍션
			if (_skillId == SkillId.카운터_디텍션 && enemy.getZoneType() != 1
					&& (cha.hasSkillEffect(SkillId.인비지블리티)
							|| cha.hasSkillEffect(SkillId.블라인드_하이딩))) {
				return true; // 인비지나 블라인드 하이딩중
			}
			return false; // 공격 스킬로 PK모드가 아닌 경우
		}
		if (!_user.glanceCheck(cha.getX(), cha.getY()) && !_skill.getIsThrough()) {
			if (!(_skill.getType() == L1Skills.TYPE_CHANGE || _skill.getType() == L1Skills.TYPE_RESTORE)) {
				_isGlanceCheckFail = true;
				return false; 
			}
		}
		// 몬스터가 아닐때 테이밍 몬스터 , 크리에이터 좀비 return
		if (!(cha instanceof MonsterInstance)
				&& (_skillId == SkillId.테이밍_몬스터 || _skillId == SkillId.크리에이터_좀비)) {
			return false; 
		}
		// 타겟이 죽엇을 경우 
		if (cha.isDead() && (_skillId != SkillId.크리에이터_좀비 && _skillId != SkillId.리절렉션
				&& _skillId != SkillId.그레이터_리절렉션 && _skillId != SkillId.콜_오브_네이쳐)) {
			return false; 
		}
		// 타겟이 죽지 않았을 경우 
		if (!cha.isDead() && (_skillId == SkillId.크리에이터_좀비 || _skillId == SkillId.리절렉션
				|| _skillId == SkillId.그레이터_리절렉션 || _skillId == SkillId.콜_오브_네이쳐)) {
			return false; 
		}
		// 타겟이 타워 일경우 
		if (cha instanceof L1TowerInstance && (_skillId == SkillId.크리에이터_좀비 || _skillId == SkillId.리절렉션
				|| _skillId == SkillId.그레이터_리절렉션 || _skillId == SkillId.콜_오브_네이쳐)) {
			return false; 
		}
		// 몬스터가 땅에 숨어 있을 경우 마법 효과 처리
		if (cha instanceof L1NpcInstance) {
			int hiddenStatus = ((L1NpcInstance) cha).getHiddenStatus();
			if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_SINK) {
				if (_skillId == SkillId.디텍션 || _skillId == SkillId.카운터_디텍션) {
					return true;
				} else {
					return false;
				}
			} else if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_FLY) {
				return false;
			}
		}
		// 타겟이 pc일때
		if ((_skill.getTargetTo() & L1Skills.TARGET_TO_PC) == L1Skills.TARGET_TO_PC && cha instanceof L1PcInstance) {
			_flg = true;
			// 타겟이 npc일때 
		} else if ((_skill.getTargetTo() & L1Skills.TARGET_TO_NPC) == L1Skills.TARGET_TO_NPC 
				&& (cha instanceof MonsterInstance || cha instanceof L1NpcInstance
						|| cha instanceof SummonInstance || cha instanceof PetInstance)) {
			_flg = true;
			// 타겟이 펫이고 서먼일때 
		} else if ((_skill.getTargetTo() & L1Skills.TARGET_TO_PET) == L1Skills.TARGET_TO_PET
				&& (cha instanceof SummonInstance || cha instanceof PetInstance)) {
			_flg = true;
		}
		if (_calcType == PC_PC && cha instanceof L1PcInstance) {
			// 타겟이 클랜원일때
			if ((_skill.getTargetTo() & L1Skills.TARGET_TO_CLAN) == L1Skills.TARGET_TO_CLAN
					&& ((_pc.getClanid() != 0 
					&& _pc.getClanid() == ((L1PcInstance) cha).getClanid()) || _pc.isGm())) {
				return true;
			}
			// 타겟이 파티일때
			if ((_skill.getTargetTo() & L1Skills.TARGET_TO_PARTY) == L1Skills.TARGET_TO_PARTY
					&& (_pc.getParty().isMember((L1PcInstance) cha) || _pc.isGm())) {
				return true;
			}
		}
		return _flg;
	}

	// 타겟의 일람을 작성
	private void makeTargetList() {
		try {
			if (_skill.getTargetTo() == L1Skills.TARGET_TO_ME
					&& (_skill.getType() & L1Skills.TYPE_ATTACK) != L1Skills.TYPE_ATTACK) {
				_targetList.add(new TargetStatus(_user)); // 타겟은 사용자만
				return;
			}
			if (_skill.getRanged() != -1) {// 사정거리 -1 화면내 오브젝트만
				if (_user.getLocation().getTileLineDistance(_target.getLocation()) > _skill.getRanged()) {
					return;
				}
			} else {
				if (!_user.getLocation().isInScreen(_target.getLocation())) {
					return;
				}
			}
			if (!isTarget(_target) && !(_skill.getTarget().equals("none"))) {
				// 대상이 다르므로 스킬이 발동하지 않는다.
				return;
			}

			if (_skillId == SkillId.라이트닝) {
				// 라이트닝 직선적으로 범위를 결정한다.
				ArrayList<L1Object> al1object = L1World.getInstance().getVisibleLineObjects(_user, _target);

				for (L1Object tgobj : al1object) {
					if (tgobj == null) {
						continue;
					}
					if (!(tgobj instanceof L1Character)) { // 타겟이 캐릭터 이외의 경우 아무것도 하지 않는다.
						continue;
					}
					L1Character cha = (L1Character) tgobj;
					if (!isTarget(cha)) {
						continue;
					}
					_targetList.add(new TargetStatus(cha));
				}
				return;
			}

			if (_skill.getArea() == 0) { // 단체의 경우

				if (!_user.glanceCheck(_target.getX(), _target.getY())) {
					if ((_skill.getType() & L1Skills.TYPE_ATTACK) == L1Skills.TYPE_ATTACK) {
						_targetList.add(new TargetStatus(_target, false)); // 데미지도 발생하지 않고, 데미지 모션도 발생하지 않지만, 스킬은 발동
						return;
					}
				}
				_targetList.add(new TargetStatus(_target));
			} else { // 범위의 경우
				if (!_skill.getTarget().equals("none")) {
					_targetList.add(new TargetStatus(_target));
				}

				if (_skillId != 49 && !(_skill.getTarget().equals("attack") || _skill.getType() == L1Skills.TYPE_ATTACK)) {
					// 공격계 이외의 스킬과 이외는 타겟 자신을 포함한다
					_targetList.add(new TargetStatus(_user));
				}

				List<L1Object> objects;
				if (_skill.getArea() == -1) {
					objects = L1World.getInstance().getVisibleObjects(_user);
				} else {
					objects = L1World.getInstance().getVisibleObjects(_target, _skill.getArea());
				}
				for (L1Object tgobj : objects) {
					if (tgobj == null) {
						continue;
					}
					if (!(tgobj instanceof L1Character)) { // 타겟이 캐릭터 이외의 경우 아무것도 하지 않는다.
						continue;
					}
					L1Character cha = (L1Character) tgobj;
					if (!isTarget(cha)) {
						continue;
					}
					if(_skillId == SkillId.미티어_스트라이크) {
						if (cha instanceof L1PcInstance && _user instanceof L1PcInstance) {
							boolean isNowWar = false;
							int castleId = L1CastleLocation.getCastleIdByArea((L1PcInstance) _user);
							if (castleId != 0) {
								isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
							}
							if (!isNowWar) {
								continue;
							}
						}
					}
					_targetList.add(new TargetStatus(cha));
				}
				return;
			}

		} catch (Exception e) {
			_log.finest("exception in L1Skilluse makeTargetList" + e);
		}
	}

	// 메세지의 표시(무엇인가 일어났을 때)
	private void sendHappenMessage(L1PcInstance pc) {
		int msgID = _skill.getSysmsgIdHappen();
		if (msgID > 0) {
			pc.sendPackets(new S_ServerMessage(msgID), true);
		}	
	}
	// 실패 메세지 표시의 핸들
	private void sendFailMessageHandle() {
		if (_skill.getType() != L1Skills.TYPE_ATTACK && !_skill.getTarget().equals("none") && _targetList.size() == 0) {
			sendFailMessage();
		}
	}

	// 메세지의 표시(실패했을 때)
	private void sendFailMessage() {
		int msgID = _skill.getSysmsgIdFail();
		if (msgID > 0 && (_user instanceof L1PcInstance)) {
			_pc.sendPackets(new S_ServerMessage(msgID), true);
		}
	}

	// 메세지의 표시(실패했을 때) 일부 스킬만 
	private void sendSkillFailMessage() {
		int msgID = _skill.getSysmsgIdFail();
		if (msgID > 0 && (_user instanceof L1PcInstance)) {
			_user.sendPackets(new S_ServerMessage(msgID));
		}
	}
	// 정령 속성 마법 체크 
	private boolean isAttrAgrees() {
		int magicattr = _skill.getAttr();
		if (_user instanceof L1NpcInstance) { // NPC가 사용했을 경우 뭐든지 OK
			return true;
		}
		if ((_skill.getSkillLevel() >= 17 && magicattr != 0) // 정령 마법으로, 무속성 마법은 아니고,
				&& (magicattr != _pc.getElfAttr() // 사용자와 마법의 속성이 일치하지 않는다.
				&& !_pc.isGm())) { // 다만 GM은 예외 
			return false;
		}
		if (_skillId == SkillId.프로텍션_프롬_엘리멘탈 && _pc.getElfAttr() == 0) {
			_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_33), true);
			return false;
		} 
		return true;
	}

	// 마법 사용으로 인해 hp, mp 감소 처리
	private boolean isHPMPConsume() {
		_mpConsume = _skill.getMpConsume();
		_hpConsume = _skill.getHpConsume();
		int currentMp = 0;
		int currentHp = 0;

		if (_user instanceof L1NpcInstance) {
			currentMp = _npc.getCurrentMp();
			currentHp = _npc.getCurrentHp();
		} else {
			currentMp = _pc.getCurrentMp();
			currentHp = _pc.getCurrentHp();

			// 엠피 소모 감소 구현 기사 스킬은 제외 
			if(!(_skillId >= SkillId.쇼크_스턴 && _skillId <= SkillId.카운터_배리어)) {
				double intPre = CalcUtil.getMpConsumptionDecrease(_pc.getAbility().getTotalInt()) * 0.01;
				int minMp = (int) (_mpConsume * intPre);
				if (minMp > 0) {
					_mpConsume -= minMp;
				}
			}
			// 장비에 대한 mp 소모 구현 
			if (_skillId == SkillId.인챈트_덱스터리티 || _skillId == SkillId.헤이스트
					&& _pc.toArmorEquipped().isMagicHelmFast()) {
				_mpConsume >>= 1;
			} 
			else if(_skillId == SkillId.힐 || _skillId == SkillId.익스트라_힐
					&& _pc.toArmorEquipped().isMagicHelmCure()) {
				_mpConsume >>= 1;
			}
			else if(_skillId == SkillId.인챈트_웨폰 || _skillId == SkillId.디텍션
					|| _skillId == SkillId.인챈트_마이티 
					&& _pc.toArmorEquipped().isMagicHelmStr()) {
				_mpConsume >>= 1;
			}
			else if(_skillId == SkillId.헤이스트 && _pc.toArmorEquipped().isOldWindHelm()) {
				_mpConsume >>= 1;
			}
			else if(_skillId == SkillId.그레이터_헤이스트 && _pc.toArmorEquipped().isWindHelm()) {
				_mpConsume >>= 1;
			}	
			if (0 < _skill.getMpConsume()) {
				_mpConsume = Math.max(_mpConsume, 0);
			}
		}
		// 마법 사용 hp, mp 실패 액션 처리 
		int actionId = _skill.getActionId();
		if (currentHp < _hpConsume + 1) {
			if (_user instanceof L1PcInstance) {
				_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_34));
				if(_skillId != SkillId.쇼크_스턴 && _skillId != SkillId.트리플_애로우) {
					_pc.sendActionAll(actionId);
				}		
			}
			return false;
		} else if (currentMp < _mpConsume) {
			if (_user instanceof L1PcInstance) {
				_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_35));			
				if(_skillId != SkillId.쇼크_스턴 && _skillId != SkillId.트리플_애로우) {
					_pc.sendActionAll(actionId);
				}
			}
			return false;
		}
		return true;
	}

	/**
	 * 아이템 소모 처리 
	 * @return
	 */
	private boolean isItemConsume() {
		int itemConsume = _skill.getItemConsumeId();
		int itemConsumeCount = _skill.getItemConsumeCount();
		int actionId = _skill.getActionId();

		// 필요한 아이템이 없는 경우 
		if (itemConsume == 0) {			
			return true;
		}
		// 재료가 없다면 
		if (!_pc.getInventory().checkItem(itemConsume, itemConsumeCount)) {
			_pc.sendActionAll(actionId);
			return false; 
		}	
		return true;
	}

	/**
	 * 마법 아이템 사용 재료 및 hp, mp 감소 처리
	 */
	private void useConsume() {
		if (_user instanceof L1NpcInstance) {
			// NPC의 경우, HP, MP만 마이너스
			int current_hp = _npc.getCurrentHp() - _hpConsume;
			_npc.setCurrentHp(current_hp);

			int current_mp = _npc.getCurrentMp() - _mpConsume;
			_npc.setCurrentMp(current_mp);
			return;
		}
		// HP·MP 감소
		if (isHPMPConsume()) {
			int current_hp = _pc.getCurrentHp() - _hpConsume;
			_pc.setCurrentHp(current_hp);
			int current_mp = _pc.getCurrentMp() - _mpConsume;
			_pc.setCurrentMp(current_mp);
		}
		// Lawful를 마이너스
		int lawful = _pc.getLawful() + _skill.getLawful();
		if (lawful > 32767) {
			lawful = 32767;
		}
		if (lawful < -32767) {
			lawful = -32767;
		}
		_pc.setLawful(lawful);

		int itemConsume = _skill.getItemConsumeId();
		int itemConsumeCount = _skill.getItemConsumeCount();

		if (itemConsume == 0) {
			return; // 재료를 필요로 하지 않는 마법
		}
		// 사용 재료를 마이너스
		_pc.getInventory().consumeItem(itemConsume, itemConsumeCount);
	}

	/**
	 * 마법리스트에 추가한다.
	 * @param cha
	 * @param repetition
	 */
	private void addMagicList(L1Character cha, boolean repetition) {
		if (_skillTime == 0) {
			_getBuffDuration = _skill.getBuffDuration() * 1000; // 효과 시간
			if (_skill.getBuffDuration() == 0) {
				if (_skillId == SkillId.인비지블리티) { // 인비지비리티
					cha.setSkillEffect(SkillId.인비지블리티, 0);
				}
				return;
			}
		} else {
			_getBuffDuration = _skillTime * 1000; // 파라미터의 time가  0이외라면, 효과 시간으로서 설정한다
		}
		if (_skillId == SkillId.쇼크_스턴 || _skillId == SkillId.어스_바인드) {
			_getBuffDuration = _shockStunDuration;  
		}
		if (_skillId == SkillId.아이스_랜스 && !_isFreeze) { // 아이스 랜스 상태에서 동결 처리 
			return;
		}
		if(_skillId == SkillId.블레스드_아머) {
			return;		
		}	
		cha.setSkillEffect(_skillId, _getBuffDuration);

		if (cha instanceof L1PcInstance && repetition) { // 대상이 PC로 이미 스킬이 중복 하고 있는 경우
			L1PcInstance pc = (L1PcInstance) cha;
			sendIcon(pc);
		}
	}

	/**
	 * 마법 아이콘을 송신한다.
	 * @param player
	 */
	private void sendIcon(L1PcInstance pc) {
		if (_skillTime == 0) {
			_getBuffIconDuration = _skill.getBuffDuration(); // 효과 시간
		} else {
			_getBuffIconDuration = _skillTime; // 파라미터의 time가  0이외라면, 효과 시간으로서 설정한다
		}
		switch (_skillId) {
		case SkillId.실드: pc.sendPackets(new S_SkillIconShield(5, _getBuffIconDuration)); break;
		case SkillId.쉐도우_아머: pc.sendPackets(new S_SkillIconShield(4, _getBuffIconDuration)); break;
		case SkillId.드레스_덱스터리티: pc.sendPackets(new S_Dexup(pc, 2, _getBuffIconDuration)); break;
		case SkillId.드레스_마이티: pc.sendPackets(new S_Strup(pc, 2, _getBuffIconDuration)); break;
		case SkillId.글루잉_오라: pc.sendPackets(new S_SkillIconAura(113, _getBuffIconDuration)); break;
		case SkillId.샤이닝_오라: pc.sendPackets(new S_SkillIconAura(114, _getBuffIconDuration)); break;
		case SkillId.브레이브_오라: pc.sendPackets(new S_SkillIconAura(116, _getBuffIconDuration)); break;
		case SkillId.파이어_웨폰: pc.sendPackets(new S_SkillIconAura(147, _getBuffIconDuration)); break;
		case SkillId.윈드_샷: pc.sendPackets(new S_SkillIconAura(148, _getBuffIconDuration)); break;
		case SkillId.블레스_오브_파이어: pc.sendPackets(new S_SkillIconAura(154, _getBuffIconDuration)); break;
		case SkillId.아이_오브_스톰: pc.sendPackets(new S_SkillIconAura(155, _getBuffIconDuration)); break;
		case SkillId.블레스_오브_어스: pc.sendPackets(new S_SkillIconShield(7, _getBuffIconDuration)); break;
		case SkillId.버닝_웨폰: pc.sendPackets(new S_SkillIconAura(162, _getBuffIconDuration)); break;
		case SkillId.스톰_샷: pc.sendPackets(new S_SkillIconAura(165, _getBuffIconDuration)); break;
		case SkillId.아이언_스킨: pc.sendPackets(new S_SkillIconShield(10, _getBuffIconDuration)); break;
		case SkillId.어스_스킨: pc.sendPackets(new S_SkillIconShield(6, _getBuffIconDuration)); break;
		
		case SkillId.인챈트_마이티: pc.sendPackets(new S_Strup(pc, 5, _getBuffIconDuration)); break;
		case SkillId.인챈트_덱스터리티: pc.sendPackets(new S_Dexup(pc, 5, _getBuffIconDuration)); break;
		case SkillId.헤이스트: case SkillId.그레이터_헤이스트:
			pc.sendPackets(S_SkillHaste.get(pc.getId(), 1,_getBuffIconDuration)); break;
		case SkillId.홀리_워크: case SkillId.무빙_악셀레이션: case SkillId.윈드_워크:
			pc.sendPackets(S_SkillBrave.get(pc.getId(), 4, _getBuffIconDuration)); break;		
		case SkillId.슬로우: case SkillId.매스_슬로우: case SkillId.인탱글:
			pc.sendPackets(S_SkillHaste.get(pc.getId(), 2, 64)); break;
		case SkillId.이뮨_투함: pc.sendPackets(new S_SkillIconGFX(40, _getBuffIconDuration)); break; default: break;
	
		}
		pc.sendPackets(new S_OwnCharStatus(pc));
	}

	/**
	 * 마법 이팩트를 송신한다.
	 * @param isSkillAction
	 */
	private void sendGrfx(boolean isSkillAction) {
		int actionId = _skill.getActionId();
		int castgfx = _skill.getCastGfx();
		int castgfx1 = _skill.getCastGfx2();
		if (castgfx == 0) {
			return; // 표시하는 그래픽이 없다
		}
		if (_user instanceof L1PcInstance) {
			if (_skillId == SkillId.파이어_월 || _skillId == SkillId.라이프_스트림) {
				L1PcInstance pc = (L1PcInstance) _user;
				if (_skillId == SkillId.파이어_월) {
					pc.setHeading(pc.targetDirection(_targetX, _targetY));
					S_ChangeHeading s = S_ChangeHeading.get(pc);
					pc.sendPackets(s);
					pc.broadcastPacket(s);			
				}
				pc.sendActionAll(actionId);
				return;
			}

			int targetid = _target.getId();
			if (_skillId == SkillId.쇼크_스턴) {  
				if (_targetList.size() == 0) { // 실패
					if (_target instanceof L1PcInstance) {
						_target.onAction(_pc);
						_target.sendAll(EffectUtil.쇼크스턴_실패_이팩트);
					} else if (_target instanceof L1NpcInstance) {
						_target.onAction(_pc);
						_target.broadcastPacket(S_SkillSound.get(_target.getId(), EffectUtil.쇼크스턴_실패_이팩트));
					}
					return;
				} else {	
					if (_target instanceof L1PcInstance) {
						L1PcInstance targetPc = (L1PcInstance) _target;
						targetPc.onAction(_pc);
						targetPc.sendAll(EffectUtil.쇼크스턴_실패_이팩트);
					} else if (_target instanceof L1NpcInstance) {
						_target.onAction(_pc);
						_target.broadcastPacket(S_SkillSound.get(_target.getId(), EffectUtil.쇼크스턴_실패_이팩트));
					}
					return;
				}
			} 	
			if (_skillId == SkillId.마나_드레인) {
				if (_targetList.size() == 0) { // 실패
					_user.sendAll(19);
				} else {
					if (_user instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) _user;
						pc.sendAll(castgfx1);
					}		
				}
			}		
			if (_targetList.size() == 0 && !(_skill.getTarget().equals("none"))) {
				// 타겟수가 0으로 대상을 지정하는 스킬의 경우, 마법 사용 effect만 표시해 종료
				int tempchargfx = _pc.getCurrentSpriteId();
				if (tempchargfx == 5727 || tempchargfx == 5730) { // 그림자계 변신의 모션 대응
					actionId = ActionCodes.ACTION_SkillBuff;
				} else if (tempchargfx == 5733 || tempchargfx == 5736) {
					actionId = ActionCodes.ACTION_Attack;
				}
				if (isSkillAction) {
					_pc.sendActionAll(actionId);
				}
				return;
			}
			// 현재 스킬 상태중 액션만 처리 
			if(_skill.getType() == L1Skills.TYPE_HEAL || _skillId == SkillId.이레이즈_매직) {
				if(_target.hasSkillEffect(SkillId.앱솔루트_배리어) || _target.hasSkillEffect(SkillId.어스_바인드)
						|| _target.hasSkillEffect(SkillId.상태_동결) || _target.hasSkillEffect(SkillId.아이스_랜스)) {				
					_pc.sendActionAll(actionId);	
					return;
				}
			}	
			if (_skill.getTarget().equals("attack") && _skillId != SkillId.턴_언데드) {
				if (_calcType == PC_PC || (_calcType == PC_NPC && // 대상이 애완동물
						_target instanceof PetInstance) || (_calcType == PC_NPC && _target instanceof SummonInstance)) {
					if (_pc.getZoneType() == 1 || _target.getZoneType() == 1 // 공격하는 측 또는 공격받는 측이 세이프티 존
							|| _pc.checkNonPvP(_pc, _target) || _target.hasSkillEffect(SkillId.앱솔루트_배리어)
							|| _target.hasSkillEffect(SkillId.상태_동결) || _target.hasSkillEffect(SkillId.아이스_랜스)) { // Non-PvP 설정
						S_UseAttackSkill s = S_UseAttackSkill.get(_pc, targetid, castgfx, _targetX, _targetY, actionId, true);
						_pc.sendPackets(s);
						_pc.broadcastPacket(s);
						return;
					}
				} 		
				if (_skill.getArea() == 0) { // 단체 공격 마법
					S_UseAttackSkill s = S_UseAttackSkill.gett(_pc, targetid, castgfx, _targetX, _targetY, actionId, 6, true);
					_pc.sendPackets(s);
					_pc.broadcastPacket(s);
					_target.broadcastPacketExceptTargetSight(S_DoActionGFX.get(targetid, ActionCodes.ACTION_Damage), _pc);
				} else { // 유방향 범위 공격 마법
					L1Character[] cha = new L1Character[_targetList.size()];
					int i = 0;
					for (TargetStatus ts : _targetList) {
						cha[i] = ts.getTarget();
						i++;
					}
					S_RangeSkill s = S_RangeSkill.get(_pc, cha, castgfx, actionId, S_RangeSkill.TYPE_DIR);
					_pc.sendPackets(s);
					_pc.broadcastPacket(s);
				}
			} else if (_skill.getTarget().equals("none") && _skill.getType() == L1Skills.TYPE_ATTACK) { // 무방향 범위 공격 마법
				L1Character[] cha = new L1Character[_targetList.size()];
				int i = 0;
				for (TargetStatus ts : _targetList) {
					cha[i] = ts.getTarget();
					cha[i].broadcastPacketExceptTargetSight(S_DoActionGFX.get(cha[i].getId(), ActionCodes.ACTION_Damage), _pc);
					i++;
				}
				S_RangeSkill s = S_RangeSkill.get(_pc, cha, castgfx, actionId, S_RangeSkill.TYPE_NODIR);
				_pc.sendPackets(s);
				_pc.broadcastPacket(s);

			} else { // 보조 마법
				if (_skillId != SkillId.텔레포트 && _skillId != SkillId.매스_텔레포트 && _skillId != SkillId.텔레포트_투_마더) {
					// 마법을 사용하는 동작의 effect는 사용자만
					if (isSkillAction) {
						_pc.sendActionAll(actionId);
					}
					if (_skillId == SkillId.카운터_매직 || _skillId == SkillId.카운터_배리어) {
						S_SkillSound ss = S_SkillSound.get(targetid, castgfx);
						_pc.sendPackets(ss);
						_pc.broadcastPacket(ss);
					} else {
						S_SkillSound ss = S_SkillSound.get(targetid, _skill.getCastGfx());
						_pc.sendPackets(ss);
						_pc.broadcastPacket(ss);

					}
				}

				// 스킬의 effect 표시는 타겟 전원이지만, 그다지 필요성이 없기 때문에, 스테이터스만 송신
				for (TargetStatus ts : _targetList) {
					L1Character cha = ts.getTarget();
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_OwnCharStatus(pc));
					}
				}
			}
		} else if (_user instanceof L1NpcInstance) { // NPC가 스킬을 사용했을 경우
			int targetid = _target.getId();

			if (_user instanceof L1MerchantInstance) {
				_user.broadcastPacket(S_SkillSound.get(targetid, castgfx));
				return;
			}
			if (_targetList.size() == 0 && !(_skill.getTarget().equals("none"))) {
				// 타겟수가 0으로 대상을 지정하는 스킬의 경우, 마법 사용 effect만 표시해 종료
				_user.broadcastPacket(S_DoActionGFX.get(_user.getId(), _skill.getActionId()));
				return;
			}

			if (_skill.getTarget().equals("attack") && _skillId != 18) {
				if (_skill.getArea() == 0) { // 단체 공격 마법
					_user.broadcastPacket(S_UseAttackSkill.gett(_user, targetid, castgfx, _targetX, _targetY, actionId, 6, true));
					_target.broadcastPacketExceptTargetSight(S_DoActionGFX.get(targetid, ActionCodes.ACTION_Damage), _user);
				} else { // 유방향 범위 공격 마법
					L1Character[] cha = new L1Character[_targetList.size()];
					int i = 0;
					for (TargetStatus ts : _targetList) {
						cha[i] = ts.getTarget();
						cha[i].broadcastPacketExceptTargetSight(S_DoActionGFX.get(cha[i].getId(), ActionCodes.ACTION_Damage), _user);
						i++;
					}
					_user.broadcastPacket(S_RangeSkill.get(_user, cha, castgfx, actionId, S_RangeSkill.TYPE_DIR));
				}
			} else if (_skill.getTarget().equals("none") && _skill.getType() == L1Skills.TYPE_ATTACK) { // 무방향 범위 공격 마법
				L1Character[] cha = new L1Character[_targetList.size()];
				int i = 0;
				for (TargetStatus ts : _targetList) {
					cha[i] = ts.getTarget();
					i++;
				}
				_user.broadcastPacket(S_RangeSkill.get(_user, cha, castgfx,actionId, S_RangeSkill.TYPE_NODIR));
			} else { // 보조 마법
				if (_skillId != SkillId.텔레포트 && _skillId != SkillId.매스_텔레포트 && _skillId != SkillId.텔레포트_투_마더) {
					// 마법을 사용하는 동작의 effect는 사용자만
					_user.broadcastPacket(S_DoActionGFX.get(_user.getId(), _skill.getActionId()));
					_user.broadcastPacket(S_SkillSound.get(targetid, castgfx));
				}
			}
		}
	}
	/**
	 * 중복 되는 스킬은 삭제처리한다. (라인 줄에 따라  스킬 삭제)
	 * @param cha
	 */
	private void deleteRepeatedSkills(L1Character cha) {
		final int[][] repeatedSkills = {
				{ SkillId.파이어_웨폰, SkillId.윈드_샷, SkillId.블레스_오브_파이어, SkillId.아이_오브_스톰,
					SkillId.버닝_웨폰, SkillId.스톰_샷 },
					{ SkillId.실드, SkillId.쉐도우_아머, SkillId.어스_스킨, SkillId.블레스_오브_어스, SkillId.아이언_스킨 },
					{ SkillId.홀리_워크, SkillId.무빙_악셀레이션, SkillId.윈드_워크, SkillId.상태_2단_가속, SkillId.상태_요정_2단_가속},
					{ SkillId.헤이스트, SkillId.그레이터_헤이스트, SkillId.상태_1단_가속},
					{ SkillId.인챈트_덱스터리티, SkillId.드레스_덱스터리티 },
					{ SkillId.수룡_마안, SkillId.지룡_마안 },
					{ SkillId.상태_체력_증강, SkillId.상태_마나_증강, SkillId.상태_전투_강화 },
					{ SkillId.인챈트_마이티, SkillId.드레스_마이티 },
					{ SkillId.홀리_웨폰, SkillId.인챈트_웨폰, SkillId.블레스_웨폰},
					{ SkillId.글루잉_오라, SkillId.샤이닝_오라},
					{ SkillId.운세_버프_A, SkillId.운세_버프_B, SkillId.운세_버프_C, SkillId.운세_버프_D }};
		for (int[] skills : repeatedSkills) {
			for (int id : skills) {
				if (id == _skillId) {
					stopSkillList(cha, skills);
				}
			}
		}
	}
	private void stopSkillList(L1Character cha, int[] repeat_skill) {
		for (int skillId : repeat_skill) {
			if (skillId != _skillId) {
				cha.removeSkillEffect(skillId);
			}
		}
	}
	/**
	 * 스킬 딜레이 처리 
	 */
	private void setDelay() {
		int delay = _skill.getReuseDelay();
		if (delay < _skill.getReuseDelay()) {
			delay = _skill.getReuseDelay();
		}
		_user.setSkillDelay(delay);
	}
	/**
	 * 마법 사용 시작
	 */
	private void runSkill() {

		if (_skillId == SkillId.라이프_스트림) {
			L1EffectSpawn.getInstance().spawnEffect(81169, _skill.getBuffDuration() * 1000, _targetX, _targetY, _user.getMapId());
			return;
		} 
		if (_skillId == SkillId.파이어_월) { // 파이어월
			for (L1Object object : L1World.getInstance().getVisibleObjects(_user, 8)) { // 주위에 파이어월이 있으면 사용 불가
				if (object instanceof EffectInstance) {
					if (((L1NpcInstance) object).getNpcTemplate().get_npcId() == 81157) {
						return;
					}
				}
			}
			L1EffectSpawn.getInstance().doSpawnFireWall(_user, _targetX, _targetY);
			return;
		}

		// 카운터 매직 처리 
		if (SkillUtil.isNotConterMagicSkill(_skillId)) {
			_isCounterMagic = false;
		}

		if (_skill.getTarget().equals("attack") && _skillId != SkillId.턴_언데드) { // 공격 마법
			if (_calcType == PC_PC || // 대상이 PC
					(_calcType == PC_NPC && _target instanceof PetInstance) || // 대상이 애완동물
					(_calcType == PC_NPC && _target instanceof SummonInstance)) { // 대상이 사몬
				if (_target.getZoneType() == 1) { // 공격받는 측이 세이프티 존
					return;
				}
				if (_pc.checkNonPvP(_pc, _target)) { // NonPvP 설정
					return;
				}
			}
		}

		try {
			TargetStatus ts = null;
			L1Character cha = null;
			int dmg = 0;
			int drainMana = 0;
			int heal = 0;
			boolean isSuccess = false;
			int undeadType = 0; 

			for (Iterator<TargetStatus> iter = _targetList.iterator(); iter.hasNext();) {
				ts = null;
				cha = null;
				dmg = 0;
				heal = 0;
				isSuccess = false;
				undeadType = 0;
				ts = iter.next();
				cha = ts.getTarget();

				if (!ts.isCalc() || !isTargetCalc(cha)) {
					continue; // 계산할 필요가 없다.
				}
				Magic _magic = new Magic(_user, cha);
				_magic.setLeverage(getLeverage());
				if (cha instanceof MonsterInstance) { // 몬스터 턴 언데드 처리
					undeadType = ((MonsterInstance) cha).getNpcTemplate().getUndead();
				}
				// 확률계 스킬로 실패가 확정되어 있는 경우
				if ((_skill.getType() == L1Skills.TYPE_CURSE || _skill.getType() == L1Skills.TYPE_PROBABILITY) && isTargetFailure(cha)) {
					iter.remove();
					continue;
				}
				if (cha instanceof L1PcInstance) { // 타겟이 PC의 경우만 아이콘은 송신한다.
					if (_skillTime == 0) {
						_getBuffIconDuration = _skill.getBuffDuration(); // 효과 시간
					} else {
						_getBuffIconDuration = _skillTime; // 파라미터의 time가  0이외라면, 효과 시간으로서 설정한다
					}
				}
				deleteRepeatedSkills(cha); // 중복되는 스킬 삭제 처리 

				if (_skill.getType() == L1Skills.TYPE_ATTACK && _user.getId() != cha.getId()) {
					if (isUseCounterMagic(cha)) {  
						iter.remove(); 
						continue;
					}
					// 공격계 스킬＆타겟이 사용자 이외인 것.
					dmg = _magic.calcMagicDamage(_skillId);
					if (_skillId != SkillId.쇼크_스턴 && _skillId != SkillId.트리플_애로우) {
						SkillUtil.cancelEraseMagic(cha);
					}
				} else if (_skill.getType() == L1Skills.TYPE_CURSE || _skill.getType() == L1Skills.TYPE_PROBABILITY) { // 확률계 스킬
					isSuccess = _magic.calcSkillProbability(_skillId);
					if (_skillId != SkillId.어스_바인드 &&_skillId != SkillId.이레이즈_매직 && _skillId != SkillId.인탱글
							&& _skillId != SkillId.쇼크_스턴 && _skillId != SkillId.트리플_애로우) {
						SkillUtil.cancelEraseMagic(cha);
					}		
					if (isSuccess) {
						if (isUseCounterMagic(cha)) {
							iter.remove(); 
							continue;
						}
					} else {
						if (_skillId == SkillId.포그_오브_슬리핑 && cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_2));
						}
						iter.remove();
						continue;
					}
				} else if (_skill.getType() == L1Skills.TYPE_HEAL) { // 회복계 스킬
					// 회복량은 마이너스 데미지로 표현
					dmg = -1 * _magic.calcHealing(_skillId);
					if (cha.hasSkillEffect(SkillId.워터_라이프)) {
						dmg <<= 1;
					}
					if (cha.hasSkillEffect(SkillId.플루트_워터)) {
						dmg >>= 1;
					}
				}
				///////////////////////// 개벌 스킬 처리  ////////////////////////
				if (cha.hasSkillEffect(_skillId)  && _skillId != SkillId.쇼크_스턴  && _skillId != SkillId.아머_브레이크 && _skillId != SkillId.어스_바인드) {				
					addMagicList(cha, true); // 타겟으로 마법의 효과 시간을 덧쓰기					 
					if (_skillId != SkillId.셰이프_체인지) { // 셰이프 체인지는 변신을 덧쓰기 할 수 있기 때문에 예외
						continue;
					}
				}
				///////////////////////// PC, NPC 양쪽 모두 효과가 있는 스킬  ////////////////////////
				switch (_skillId) {
				case SkillId.헤이스트:
					if (cha.getMoveSpeed() != 2) { // 슬로우중 이외
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							if (pc.getHasteItemEquipped() > 0) {
								continue;
							}
							pc.setDrink(false);
							pc.sendPackets(S_SkillHaste.get(pc.getId(), 1, _getBuffIconDuration));
						}
						cha.broadcastPacket(S_SkillHaste.get(cha.getId(), 1, 0));
						cha.setMoveSpeed(1);
					} else { // 슬로우중
						int skillNum = 0;
						if (cha.hasSkillEffect(SkillId.슬로우)) {
							skillNum = SkillId.슬로우;
						} else if (cha.hasSkillEffect(SkillId.매스_슬로우)) {
							skillNum = SkillId.매스_슬로우;
						} else if (cha.hasSkillEffect(SkillId.인탱글)) {
							skillNum = SkillId.인탱글;
						}
						if (skillNum != 0) {
							cha.removeSkillEffect(skillNum);
							cha.removeSkillEffect(SkillId.헤이스트);
							cha.setMoveSpeed(0);
							continue;
						}
					}
					break;
				case SkillId.커스_블라인드: case SkillId.다크니스:			
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.hasSkillEffect(SkillId.상태_괴물눈)) {
							pc.sendPackets(new S_CurseBlind(2));
						} else {
							pc.sendPackets(new S_CurseBlind(1));
						}
					}
					break;
				case SkillId.엘리멘탈_폴다운:
					if (_user instanceof L1PcInstance) {
						int playerAttr = _pc.getElfAttr();
						int i = -50;
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							switch (playerAttr) {
							case 0: _pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true); break;
							case 1: pc.getResistance().addEarth(i); pc.setAddAttrKind(1); break;
							case 2: pc.getResistance().addFire(i); pc.setAddAttrKind(2); break;
							case 4: pc.getResistance().addWater(i); pc.setAddAttrKind(4); break;
							case 8: pc.getResistance().addWind(i); pc.setAddAttrKind(8); break; default: break;
							}
						} else if (cha instanceof MonsterInstance) {
							MonsterInstance mob = (MonsterInstance) cha;
							switch (playerAttr) {
							case 0: _pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true); break;
							case 1: mob.getResistance().addEarth(i); mob.setAddAttrKind(1); break;
							case 2: mob.getResistance().addFire(i); mob.setAddAttrKind(2); break;
							case 4: mob.getResistance().addWater(i); mob.setAddAttrKind(4); break;
							case 8: mob.getResistance().addWind(i); mob.setAddAttrKind(8); break; default: break;
							}
						}
					}
					break;
				case SkillId.큐어_포이즌: {
					cha.curePoison();
				}
				break;
				case SkillId.리무브_커스: {
					cha.curePoison();
					cha.cureParalaysis();			
					if (cha.hasSkillEffect(SkillId.커스_블라인드) || cha.hasSkillEffect(SkillId.다크니스)) {
						cha.removeSkillEffect(SkillId.커스_블라인드);					
						cha.removeSkillEffect(SkillId.다크니스);
					}
				}
				break;
				case SkillId.커스_패럴라이즈: {
					CurseParalysis.curse(cha, 20000, 45000);
				}
				break;
				case SkillId.그레이터_리절렉션:
				case SkillId.리절렉션:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						if (_pc.getId() != pc.getId()) {
							if (L1World.getInstance().getVisiblePlayer(pc, 0).size() > 0) {
								// \f1그 자리에 다른 사람이 서 있어서 부활을 시킬 수 없습니다.
								_pc.sendPackets(new S_ServerMessage(592));
								continue;
							}
							/** 공성장에서는 부활불가능하도록 **/
							int castle_id = L1CastleLocation.getCastleIdByArea(pc);
							if (castle_id != 0) {
								pc.sendPackets(new S_SystemMessage("사용할 수 없는 지역입니다."));
								return;
							}
							if (pc.getCurrentHp() == 0 && pc.isDead()) {
								if (pc.getMap().isUseResurrection()) {
									if (_skillId == SkillId.리절렉션) {
										pc.setGres(false);
									} else if (_skillId == SkillId.그레이터_리절렉션) {
										pc.setGres(true);
									}
									pc.setTempID(_pc.getId());
									pc.sendPackets(new S_Message_YN(322, "")); // 또 restore하고 싶습니까? (Y/N)
								}
							}
						}
					}
					if (cha instanceof L1NpcInstance) {
						if (!(cha instanceof L1TowerInstance)) {
							L1NpcInstance npc = (L1NpcInstance) cha;
							if (npc instanceof PetInstance
									&& L1World.getInstance().getVisiblePlayer(npc, 0).size() > 0) {
								for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) {
									if (!visiblePc.isDead()) {
										// \f1그 자리에 다른 사람이 서 있어서 부활을 시킬 수 없습니다.
										_pc.sendPackets(new S_ServerMessage(592));
										return;
									}
								}
							}
							if (npc.getCurrentHp() == 0 && npc.isDead()) {
								npc.resurrect(npc.getMaxHp() / 4);
								npc.setResurrect(true);
							}
						}
					}
					break;
				case SkillId.콜_오브_네이쳐: {
					L1PcInstance pc = (L1PcInstance) cha;
					if (_pc.getId() != pc.getId()) {
						if (L1World.getInstance().getVisiblePlayer(pc,
								0).size() > 0) {
							for (L1PcInstance visiblePc : L1World
									.getInstance().getVisiblePlayer(pc,
											0)) {
								if (!visiblePc.isDead()) {
									// \f1그 자리에 다른 사람이 서 있어서 부활을 시킬 수 없습니다.
									_pc.sendPackets(new S_ServerMessage(592));
									return;
								}
							}
						}
						if (pc.getCurrentHp() == 0 && pc.isDead()) {
							pc.setTempID(_pc.getId());
							pc.sendPackets(new S_Message_YN(322, "")); // 또 restore하고 싶습니까? (Y/N)
						}
					}
				}
				if (cha instanceof L1NpcInstance) {
					if (!(cha instanceof L1TowerInstance)) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						if (npc instanceof PetInstance && L1World.getInstance().getVisiblePlayer(npc, 0).size() > 0) {
							for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc,0)) {
								if (!visiblePc.isDead()) {
									// \f1그 자리에 다른 사람이 서 있어서 부활을 시킬 수 없습니다.
									_pc.sendPackets(new S_ServerMessage(592));
									return;
								}
							}
						}
						if (npc.getCurrentHp() == 0 && npc.isDead()) {
							npc.resurrect(cha.getMaxHp());// HP를 전회복한다
							npc.resurrect(cha.getMaxMp() / 100);// MP를 0으로 한다
							npc.setResurrect(true);
						}
					}
				}
				break;
				case SkillId.디텍션:
					if (cha instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						int hiddenStatus = npc.getHiddenStatus();
						if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_SINK) {
							npc.appearOnGround(_pc);
						}
					}
					break;
				case SkillId.카운터_디텍션:
					if (cha instanceof L1PcInstance) {
						dmg = _magic.calcMagicDamage(_skillId);
					} else if (cha instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						int hiddenStatus = npc.getHiddenStatus();
						if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_SINK) {
							npc.appearOnGround(_pc);
						} else {
							dmg = 0;
						}
					} else {
						dmg = 0;
					}
					break;
				case SkillId.앱솔루트_배리어: // 이레이즈매직을 피격 당한후 앱솔루트 상태일때 처리.
					_pc.killSkillEffectTimer(SkillId.이레이즈_매직);
					break;
				case SkillId.트루_타겟: {
					if (_user instanceof L1PcInstance) {
						L1PcInstance pri = (L1PcInstance) _user;
						pri.sendPackets(new S_TrueTarget(_targetID, pri.getId(), _message)); 
						for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(_target)) {
							if (pri.getClanid() == pc.getClanid()) {
								pc.sendPackets(new S_TrueTarget(_targetID, pc.getId(), _message));
							}
						}
					}
				}
				break;
				case SkillId.힐: case SkillId.익스트라_힐: case SkillId.그레이터_힐:
				case SkillId.풀_힐: case SkillId.힐올: case SkillId.네이쳐스_블레싱: {	
					if (cha.hasSkillEffect(SkillId.워터_라이프)) {
						cha.removeSkillEffect(SkillId.워터_라이프);
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							pc.sendPackets(new S_PacketBox(S_PacketBox.DEL_ICON), true);
						}
					}	
				}
				break;
				case SkillId.칠_터치: case SkillId.뱀파이어릭_터치:
					heal = dmg;						
					break;
				case SkillId.트리플_애로우:
					int playerGFX = _pc.getCurrentSpriteId(), weaponType = _pc.getWeapon().getItem().getType1();
					if (_pc.getWeapon() == null && weaponType != 20 || playerGFX == 3784) {
						return;
					}	
					
					// 트리플 수정
					GeneralThreadPool.getInstance().execute(new Runnable() {
						@Override
						public void run() {
						
							for (int i = 3; i > 0; i--) {
								_target.onAction(_pc);			 
							}
						}
					});
					_pc.sendAll(EffectUtil.트리플_이팩트_넘버_1);
					break;
				case SkillId.린드비오르_안식: case SkillId.안타라스_안식: case SkillId.발라카스_안식: case SkillId.파푸리온_안식:
					if (_user instanceof L1NpcInstance) {
						_user.broadcastPacket(new S_NpcChatPacket(_npc, "$3717", 0)); // 자, 너에게 안식을 주자.
					} else {
						_pc.broadcastPacket(new S_ChatPacket(_pc, "$3717", 0, 0)); // 자, 너에게 안식을 주자.
					}
					dmg = cha.getCurrentHp();
					break;			
				case SkillId.몬스터_리콜:
					Teleport.teleportToTargetFront(cha, _user, 1, true);
					break;
				case SkillId.슬로우: case SkillId.매스_슬로우: case SkillId.인탱글:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.getHasteItemEquipped() > 0) {
							continue;
						}
					}
					if (cha.getMoveSpeed() == 0) {
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							pc.sendPackets(S_SkillHaste.get(pc.getId(), 2, 64));
						}
						cha.broadcastPacket(S_SkillHaste.get(cha.getId(), 2, 64));
						cha.setMoveSpeed(2);
					} else if (cha.getMoveSpeed() == 1) {
						int skillNum = 0;
						if (cha.hasSkillEffect(SkillId.헤이스트)) {
							skillNum = SkillId.헤이스트;
						} else if (cha.hasSkillEffect(SkillId.그레이터_헤이스트)) {
							skillNum = SkillId.그레이터_헤이스트;
						} else if (cha.hasSkillEffect(SkillId.상태_1단_가속)) {
							skillNum = SkillId.상태_1단_가속;
						}
						if (skillNum != 0) {
							cha.removeSkillEffect(skillNum);
							cha.removeSkillEffect(_skillId);
							cha.setMoveSpeed(0);
							continue;
						}
					}
					break;
				case SkillId.커스_포이즌:
					DamagePoison.doInfection(_user, cha, 3000, 5);
					break;
				case SkillId.바운스_어택: {
					L1PcInstance pc = (L1PcInstance) cha;
					pc.addMeleeHit(6);
				}
				break;
				case SkillId.위크니스:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(-5);
						pc.addMeleeHit(-1);
					}
					break;
				case SkillId.디지즈:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeHit(-6);
						pc.getAC().addAc(12);
					}
					break;
				case SkillId.아머_브레이크:
					boolean probability = _magic.calcSkillProbability(_skillId);
					if (probability) {
						_target.sendAll(EffectUtil.아머브레이크_이팩트); 
					} else {
						_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_33));
					}
					break;
				case SkillId.아이스_랜스:
					_isFreeze = _magic.calcSkillProbability(_skillId);
					if (_isFreeze) {
						L1EffectSpawn.getInstance().spawnEffect(81168, 16000, cha.getX(), cha.getY(), cha.getMapId());
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							S_Poison poison = new S_Poison(pc.getId(), 2);
							pc.sendPackets(poison);
							pc.broadcastPacket(poison);
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_FREEZE, true));
						} else if (cha instanceof MonsterInstance || cha instanceof SummonInstance
								|| cha instanceof PetInstance) {
							L1NpcInstance npc = (L1NpcInstance) cha;
							npc.broadcastPacket(new S_Poison(npc.getId(), 2));
							npc.setParalyzed(true);
							npc.setParalysisTime(16000);
						}
					}
					break;
				case SkillId.어스_바인드:{
					String[] stunTimeArray = null;
					stunTimeArray = Config.EARTH_TIME.split(",");
					_shockStunDuration = Integer.valueOf(stunTimeArray[random.nextInt(stunTimeArray.length)]).intValue();

					if (cha instanceof L1PcInstance && cha.hasSkillEffect(SkillId.어스_바인드)) {
						_shockStunDuration += cha.getSkillEffectTimeSec(SkillId.어스_바인드) * 1000;
					}  
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						S_Poison poison1 = new S_Poison(pc.getId(), 2);
						pc.sendPackets(poison1);
						pc.broadcastPacket(poison1);
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_FREEZE, true), true);
					} else if (cha instanceof MonsterInstance || cha instanceof SummonInstance || cha instanceof PetInstance) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						npc.setParalyzed(true);
						npc.setParalysisTime(_shockStunDuration);
					}
				} 
				break;
				case SkillId.쇼크_스턴: {
					String[] stunTimeArray = null;
					stunTimeArray = Config.STUN_SKILL_RND_TIME.split(",");
					_shockStunDuration = Integer.valueOf(stunTimeArray[random.nextInt(stunTimeArray.length)]) .intValue();

					if (cha instanceof L1PcInstance && cha.hasSkillEffect(SkillId.쇼크_스턴)) {
						_shockStunDuration += cha.getSkillEffectTimeSec(SkillId.쇼크_스턴) * 1000;
					}  			
					L1EffectSpawn.getInstance().spawnEffect(81162, _shockStunDuration, cha.getX(), cha.getY(), cha.getMapId());
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;		
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_STUN, true), true);
					} else if (cha instanceof MonsterInstance || cha instanceof SummonInstance || cha instanceof PetInstance) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						npc.setParalyzed(true);
						npc.setParalysisTime(_shockStunDuration);
					}
				}
				break;
				case SkillId.윈드_셰클:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_SkillIconWindShackle(pc.getId(), _getBuffIconDuration));
					}
					break;
				case SkillId.캔슬레이션:
					if (cha instanceof L1PcInstance) { 
						L1PcInstance targetPc = (L1PcInstance) cha;
						if (targetPc != null && targetPc.isInvisble()) { // 타겟이 인비지 상태라면
							targetPc.delInvis();
						}
					}
					if(_pc.isInvisble()) { // 내 자신이 인비지 상태라면 
						_pc.delInvis();
					}	
					
					if (cha instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) cha;
						int npcId = npc.getNpcTemplate().get_npcId();
						if (npcId == 45640) {

							if (npc.equalsCurrentSprite(2755)) {
								npc.setCurrentHp(npc.getMaxHp());
								npc.setCurrentSprite(2332);
								npc.sendShape(2332);
								npc.setName("$2103");
								npc.setNameId("$2103");
								Broadcaster.broadcastPacket(npc, new S_ChangeName(npc.getId(), "$2103"));
							} else {
								npc.setCurrentHp(npc.getMaxHp());
								npc.setCurrentSprite(2755);
								npc.sendShape(2755);
								npc.setName("$2488");
								npc.setNameId("$2488");
								Broadcaster.broadcastPacket(npc, new S_ChangeName(npc.getId(), "$2488"));
							}
						}
					}
					
					// 엔피시 상태 해제
					if (!(cha instanceof L1PcInstance)) {
						L1NpcInstance npc = (L1NpcInstance) cha;				
						npc.setMoveSpeed(0);
						npc.setBraveSpeed(0);	
						npc.broadcastPacket(S_SkillHaste.get(cha.getId(), 0, 0));
						npc.broadcastPacket(S_SkillBrave.get(cha.getId(), 0, 0));
						npc.broadcastPacket(new S_ChangeShape(cha.getId(), cha.getCurrentSpriteId()));
						npc.setParalyzed(false);
						npc.setParalysisTime(0);
					}
					// 독 관련 해제
					cha.curePoison();
					cha.cureParalaysis();
					// 스킬의 해제
					for(int skillNum = SkillId.스킬_시작; skillNum <= SkillId.스킬_끝; skillNum++) {
						if (SkillUtil.isNotCancelable(skillNum)) {
							continue;
						}
						cha.removeSkillEffect(skillNum);
					}
					for(int skillNum = SkillId.상태_시작; skillNum <= SkillId.상태_끝; skillNum++) {
						if (skillNum == SkillId.상태_채팅_금지) { // 채팅 금지는 해제하지 않는다
							continue;
						}
						cha.removeSkillEffect(skillNum);
					}
					// 요리 효과  해제
					for(int skillNum = SkillId.요리_시작; skillNum <= SkillId.요리_끝; skillNum++) {
						cha.removeSkillEffect(skillNum);
					}
					break;
				case SkillId.턴_언데드: {
					if (undeadType == 1) {
						dmg = cha.getCurrentHp();
						if(_target instanceof MonsterInstance){
							MonsterInstance m = (MonsterInstance)_target;
							if(_user instanceof L1PcInstance)
								m.setHate(_user, 1);
						}
					}
				}
				break;
				case SkillId.마나_드레인: {		
					int 피해량 = CommonUtil.randomRange(24, 42), 상대엠알 = cha.getResistance().getMr();	
					// 상대 엠알이 100미만이라면     
					if(상대엠알 < 100) {
						drainMana = 피해량;
					} else {
						drainMana = 피해량 / 2;
					}
					if(cha.getCurrentMp() < drainMana) {
						drainMana = cha.getCurrentMp();
					}
				}
				break;
				case SkillId.웨폰_브레이크: {
					if (_calcType == PC_PC || _calcType == NPC_PC) {
						if (cha instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) cha;
							ItemInstance weapon = pc.getWeapon();
							if (weapon != null) {
								int weaponDamage = random.nextInt(_user.getAbility().getTotalInt() / 3) + 1;
								pc.sendPackets(new S_ServerMessage(268, weapon.getLogName()));
								pc.getInventory().receiveDamage(weapon, weaponDamage);
							}
						}
					} else {
						((L1NpcInstance) cha).setWeaponBreaked(true);
					}
				}
				break;
				case SkillId.포그_오브_슬리핑:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_SLEEP, true));
					}
					cha.setSleeped(true);
					break;
				case SkillId.상태_동결:
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;
						S_Poison poison2 = new S_Poison(pc.getId(), 2);
						pc.sendPackets(poison2);
						pc.broadcastPacket(poison2);
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_BIND, true));
					}
					break;
				default:
					break;
				}
				// ●●●● PC밖에 효과가 없는 스킬 ●●●●
				if (_calcType == PC_PC || _calcType == NPC_PC) {
					// ★★★ 특수계 스킬★★★
					switch (_skillId) {
					case SkillId.텔레포트: case SkillId.매스_텔레포트:{
						L1PcInstance pc = (L1PcInstance) cha;
						L1BookMark bookm = pc.getBookMark(_bookmarkId);
						if (bookm != null) { // 북마크를 취득 할 수 있으면  텔레포트
							if (pc.getMap().isEscapable() || pc.isGm()) {
								int newX = bookm.getLocX();
								int newY = bookm.getLocY();
								short mapId = bookm.getMapId();

								if (_skillId == SkillId.매스_텔레포트) { // 매스 텔레포트
									List<L1PcInstance> clanMember = L1World.getInstance().getVisiblePlayer(pc);
									for (L1PcInstance member : clanMember) {
										if (pc.getLocation().getTileLineDistance(member.getLocation()) <= 3
												&& member.getClanid() == pc.getClanid()
												&& member.getId() != pc.getId()) {
											member.teleport(newX, newY, mapId, member.getHeading(), 169, true, true);
										}
									}
								}
								pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, true);
							} else { // 텔레포트 불가 MAP에의 이동 제한
								pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
							}

						} else if (MapRingTelTable.getInstance().isMapZoneChange(pc.getMapId())) {
							ItemInstance findItem = pc.getInventory().findItemId(202811);
							if (findItem != null || pc.isGm()) {
								Teleport.randomTeleport(pc, true);
							} else { 
								pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
								pc.sendPackets(new S_ServerMessage(276));
							}
						} else { // 북마크를 취득 할 수 없었다, 혹은 「임의의 장소」를 선택했을 경우의 처리
							if (pc.getMap().isTeleportable() || pc.isGm()) {
								L1Location newLocation = pc.getLocation().randomLocation(200, true);
								int newX = newLocation.getX();
								int newY = newLocation.getY();
								short mapId = (short) newLocation.getMapId();

								if (_skillId == SkillId.매스_텔레포트) { // 매스 텔레포트
									List<L1PcInstance> clanMember = L1World.getInstance().getVisiblePlayer(pc);
									for (L1PcInstance member : clanMember) {
										if (pc.getLocation().getTileLineDistance(member.getLocation()) <= 3
												&& member.getClanid() == pc.getClanid()
												&& member.getId() != pc.getId()) {
											member.teleport(newX, newY, mapId, member.getHeading(), 169, true, true);

										}
									}
								}
								pc.sendActionAll(19);
								pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, true);
							} else {
								pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
								pc.sendPackets(new S_ServerMessage(276));
							}
						}
					}
					break;
					case SkillId.상태_체력_증강: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMaxHp(50);
						pc.addHpr(4);
					}
					break;
					case SkillId.상태_마나_증강: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMaxMp(40);
						pc.addMpr(4);
					}
					break;
					case SkillId.상태_전투_강화: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeHit(3);
						pc.addMeleeDmg(3);
						pc.addRangedHit(3);
						pc.addRangedDmg(3);
						pc.addSp(3);
					}
					break;
					case SkillId.라이트:
						break;
					case SkillId.텔레포트_투_마더:{
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.getMap().isEscapable() || pc.isGm()) {	
							pc.teleport(33051, 32337, (short) 4, pc.getHeading(), 169, true, true);
						} else {
							pc.sendPackets(new S_ServerMessage(647));
							pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
						} 
					}
					break;
					case SkillId.콜_클랜:{
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.getMapId() == 4
								&& ((pc.getX() >= 33332 && pc.getX() <= 33338
								&& pc.getY() >= 32433 && pc.getY() <= 32439)
								|| (pc.getX() >= 33259 && pc.getX() <= 33265
								&& pc.getY() >= 32399 && pc.getY() <= 32405) 
								|| (pc.getX() >= 33389 && pc.getX() <= 33395
								&& pc.getY() >= 32341 && pc.getY() <= 32347))) {
							pc.sendPackets(new S_ServerMessage(647), true);
							return;
						}
						if (pc.getMapId() == 4
								&& ((pc.getX() >= 33328 && pc.getX() <= 33344
								&& pc.getY() >= 32427 && pc.getY() <= 32444)
								|| (pc.getX() >= 33255 && pc.getX() <= 33272
								&& pc.getY() >= 32393 && pc .getY() <= 32412)
								|| (pc.getX() >= 34194 && pc.getX() <= 34305
								&& pc.getY() >= 33324 && pc.getY() <= 33535)
								|| (pc.getX() >= 33450 && pc.getX() <= 33470
								&& pc.getY() >= 32328 && pc.getY() <= 32344)
								|| (pc.getX() >= 33385 && pc.getX() <= 33400
								&& pc.getY() >= 32336 && pc.getY() <= 32353) 
								|| (pc.getX() >= 33461 && pc.getX() <= 33534
								&& pc.getY() >= 33165 && pc.getY() <= 33253))) {
							pc.sendPackets(new S_ServerMessage(647), true);
						}	
						L1PcInstance clanPc = (L1PcInstance) L1World.getInstance().findObject(_targetID);
						if (clanPc != null) {
							clanPc.setTempID(pc.getId()); // 상대의 오브젝트 ID를 보존해 둔다
							clanPc.sendPackets(new S_Message_YN(748, ""), true); // 혈맹원이 당신을 텔레포트 시키려고 하고 있습니다.응합니까? (Y/N)
						}
					}
					break;
					case SkillId.런_클랜: {
						L1PcInstance pc = (L1PcInstance) cha;
						L1PcInstance clanPc = (L1PcInstance) L1World.getInstance().findObject(_targetID);
						if (clanPc != null) {
							if (pc.getMap().isEscapable() || pc.isGm()) {
								boolean castle_area = L1CastleLocation.checkInAllWarArea(clanPc.getX(), clanPc.getY(), clanPc.getMapId());
								if ((clanPc.getMapId() == 0 || clanPc.getMapId() == 4 || clanPc.getMapId() == 304) && !castle_area) {
									pc.teleport(clanPc.getX(), clanPc.getY(), clanPc.getMapId(),pc.getHeading(), 2236, true, true);
								} else {
									pc.sendPackets(new S_ServerMessage(547));
								}
							} else {
								pc.sendPackets(new S_ServerMessage(647));
								pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false));
							}
						}
					}
					break;
					case SkillId.크리에이트_매지컬_웨폰:{
						// 매지컬 웨폰
						L1PcInstance pc = (L1PcInstance) cha;
						ItemInstance item = pc.getInventory().getItem(_itemobjid);
						if (item != null && item.getItem().getType2() == 1) {
							int item_type = item.getItem().getType2();
							int safe_enchant = item.getItem().get_safeenchant();
							int enchant_level = item.getEnchantLevel();
							String item_name = item.getName();
							if (safe_enchant < 0) { // 강화 불가
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
							} else if (safe_enchant == 0) { // 안전권+0
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
							} else if (item_type == 1 && enchant_level == 0) {
								if (!item.isIdentified()) {// 미감정
									pc.sendPackets(new S_ServerMessage(161, item_name,"$245", "$247"));
								} else {
									item_name = "+0 " + item_name;
									pc.sendPackets(new S_ServerMessage(161, "+0 " + item_name, "$245", "$247"));
								}
								item.setEnchantLevel(1);
								pc.getInventory().updateItem(item,L1PcInventory.COL_ENCHANTLVL);
							} else {
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
							}
						} else {
							pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
						}
					}
					break;
					case SkillId.지룡_마안: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getResistance().addregist_spirit(15);
					}
					break;
					case SkillId.네이쳐스_터치: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addHpr(15);
					}
					break;
					case SkillId.메디테이션: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMpr(5);
					}
					break;
					case SkillId.브링_스톤:{
						L1PcInstance pc = (L1PcInstance) cha;
						Random random = new Random();
						ItemInstance item = pc.getInventory().getItem(_itemobjid);
						if (item != null) {
							int dark = (int) (10 + (pc.getLevel() * 0.8) + (pc.getAbility().getTotalWis() - 6) * 1.2);
							int brave = (int) (dark / 2.1);
							int wise = (int) (brave / 2.0);
							int kayser = (int) (wise / 1.9);
							int chance = random.nextInt(100) + 1;
							if (item.getItem().getItemId() == 40320) {
								pc.getInventory().removeItem(item, 1);
								if (dark >= chance) {
									pc.getInventory().storeItem(40321, 1);
									pc.sendPackets(new S_ServerMessage(403, "$2475")); // %0를 손에 넣었습니다.
								} else {
									pc.sendPackets(new S_ServerMessage(280)); // \f1마법이 실패했습니다.
								}
							} else if (item.getItem().getItemId() == 40321) {
								pc.getInventory().removeItem(item, 1);
								if (brave >= chance) {
									pc.getInventory().storeItem(40322, 1);
									pc.sendPackets(new S_ServerMessage(403, "$2476")); // %0를 손에 넣었습니다.
								} else {
									pc.sendPackets(new S_ServerMessage(280)); // \f1마법이 실패했습니다.
								}
							} else if (item.getItem().getItemId() == 40322) {
								pc.getInventory().removeItem(item, 1);
								if (wise >= chance) {
									pc.getInventory().storeItem(40323, 1);
									pc.sendPackets(new S_ServerMessage(403, "$2477")); // %0를 손에 넣었습니다.
								} else {
									pc.sendPackets(new S_ServerMessage(280)); // \f1마법이 실패했습니다.
								}
							} else if (item.getItem().getItemId() == 40323) {
								pc.getInventory().removeItem(item, 1);
								if (kayser >= chance) {
									pc.getInventory().storeItem(40324, 1);
									pc.sendPackets(new S_ServerMessage(403, "$2478")); // %0를 손에 넣었습니다.
								} else {
									pc.sendPackets(new S_ServerMessage(280)); // \f1마법이 실패했습니다.
								}
							}
						}
					}
					break;
					case SkillId.서먼_몬스터:{
						L1PcInstance pc = (L1PcInstance) cha;
						int level = pc.getLevel();
						int[] summons; // 서먼 가능한맵인지, 공성장이 아닌지, 지엠인지
						if(pc.getMap().isRecallPets() && !pc.isInWarArea() || pc.isGm()) {
							if(pc.toArmorEquipped().isSummonControlRing()) { // 소환 조종 반지를 착용햇을 경우 
								pc.sendPackets(new S_ShowSummonList(pc.getId()));
							} else { // 그게 아니면 			
								summons = new int[] { 45140, 45098, 45163, 81083, 81086, 81089, 81092 };
								int summonid = 0;
								int summoncost = 6;
								int levelRange = 32;
								for (int i = 0; i < summons.length; i++) { // 해당 LV범위 검색
									if (level < levelRange || i == summons.length - 1) {
										summonid = summons[i];
										break;
									}
									levelRange += 4;
								}

								int petcost = 0;
								Object[] petlist = pc.getPetList().values().toArray();
								for (Object pet : petlist) {
									petcost += ((L1NpcInstance) pet).getPetcost();
								}
								int charisma = pc.getAbility().getTotalCha() + 6 - petcost;
								int summoncount = charisma / summoncost;
								L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonid);
								for (int i = 0; i < summoncount; i++) {
									SummonInstance summon = new SummonInstance(npcTemp, pc);
									summon.setPetcost(summoncost);
								}
							}
						} else {
							pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
						}
					}
					break;
					case SkillId.서먼_레서_엘리멘탈: case SkillId.서먼_그레이트_엘리멘탈:{
						L1PcInstance pc = (L1PcInstance) cha;
						int attr = pc.getElfAttr();
						if (attr != 0) { // 무속성이 아니면 실행
							if (pc.getMap().isRecallPets() || pc.isGm()) {
								int petcost = 0;
								Object[] petlist = pc.getPetList().values().toArray();
								for (Object pet : petlist) {
									// 현재의 애완동물 코스트
									petcost += ((L1NpcInstance) pet).getPetcost();
								}
								if (petcost == 0) { // 1마리나 소속 NPC가 없으면 실행
									int summonid = 0;
									int summons[];
									if (_skillId == SkillId.서먼_레서_엘리멘탈) { // 렛서에레멘탈[지, 화, 수, 바람]
										summons = new int[] { 45306, 45303, 45304, 45305 };
									} else {
										// 그레이터 일렉트로닉 멘탈[지, 화, 수, 바람]
										summons = new int[] { 81053, 81050, 81051, 81052 };
									}
									int npcattr = 1;
									for (int i = 0; i < summons.length; i++) {
										if (npcattr == attr) {
											summonid = summons[i];
											i = summons.length;
										}
										npcattr *= 2;
									}
									// 특수 설정의 경우 랜덤으로 출현
									if (summonid == 0) {
										Random random = new Random();
										int k3 = random.nextInt(4);
										summonid = summons[k3];
									}

									L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonid);
									SummonInstance summon = new SummonInstance(npcTemp, pc);
									summon.setPetcost(pc.getAbility().getTotalCha() + 7);
									// 정령 외에는 NPC를 소속 시킬 수 없다
								}
							} else {
								// \f1 아무것도 일어나지 않았습니다.
								pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
							}
						}
					}
					break;
					case SkillId.흑사_코인: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-2);
						pc.addMeleeHit(3);
						pc.addRangedHit(3);
						pc.addMaxHp(20);
						pc.addMaxMp(13);
						pc.getResistance().addregist_spirit(10);
					}
					break;
					case SkillId.코마_축복_코인_8:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_Coma(41, 0));
						pc.getAbility().addAddedStr(5);
						pc.getAbility().addAddedDex(5);
						pc.getAbility().addAddedCon(1);
						pc.addMeleeHit(5);
						pc.addRangedHit(5);
						pc.getAC().addAc(-8);	
						pc.addSp(1);
					}
					break;
					case SkillId.글루잉_오라:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeHit(5);
						pc.addRangedHit(5);
						pc.getResistance().addMr(20);
						pc.sendPackets(new S_SkillIconAura(113, _getBuffIconDuration));	
					}
					break;
					case SkillId.샤이닝_오라:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-8);
						pc.sendPackets(new S_SkillIconAura(114, _getBuffIconDuration));
					}
					break;
					case SkillId.브레이브_오라:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(5);
						pc.addRangedDmg(5);
						pc.sendPackets(new S_SkillIconAura(116, _getBuffIconDuration));
					}
					break;
					case SkillId.실드:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-2);
						pc.sendPackets(new S_SkillIconShield(5, _getBuffIconDuration));
					}
					break;
					case SkillId.언케니_닷지: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addDg(Config.DODGEHIT);
					}
					break;
					case SkillId.쉐도우_아머:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-3);
						pc.sendPackets(new S_SkillIconShield(3, _getBuffIconDuration));
					}
					break;
					case SkillId.드레스_덱스터리티:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAbility().addAddedDex((byte) 3);
						pc.sendPackets(new S_Dexup(pc, 2, _getBuffIconDuration));
					}
					break;
					case SkillId.드레스_마이티:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAbility().addAddedStr((byte) 3);
						pc.sendPackets(new S_Strup(pc, 2, _getBuffIconDuration));
					}
					break;
					case SkillId.쉐도우_팽: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(5);
					}
					break;
					case SkillId.홀리_웨폰:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(1);
						pc.addMeleeHit(1);
					}
					break;
					case SkillId.인챈트_웨폰: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(2);
					}
					break;
					case SkillId.안타라스_레이드_버프_크레이: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(1);
						pc.addMeleeHit(5);
						pc.addRangedDmg(1);
						pc.addRangedHit(5);
						pc.addMaxHp(100);
						pc.addMaxMp(50);
						pc.addHpr(3);
						pc.addMpr(3);
						pc.getResistance().addEarth(30);	
					}
					break;
					case SkillId.블레스_웨폰: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(2);
						pc.addMeleeHit(2);
					}
					break;
					case SkillId.블레스드_아머: {
						L1PcInstance pc = (L1PcInstance) cha;		
						ItemInstance item = pc.getInventory().getItem(_itemobjid);
						if (item != null && item.getItem().getType2() == L1Item.CATEGORY_ARMOR && item.getItem().getType() == L1Item.TYPE_ARMOR_ARMOR) {	
							pc.getAC().addAc(-3);
							pc.sendPackets(new S_ServerMessage(161, item.getLogName(),"$245", "$247"), true);
							pc.setSkillEffect(SkillId.블레스드_아머, _skill.getBuffDuration() * 1000);
						} else {
							pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
						}
					}
					break;
					case SkillId.블레스_오브_어스: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-7);
						pc.sendPackets(new S_SkillIconShield(7, _getBuffIconDuration));
					}
					break;
					case SkillId.레지스트_매직:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getResistance().addMr(10);
					}
					break;
					case SkillId.클리어_마인드:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAbility().addAddedWis((byte) 3);
						pc.resetBaseMr();
					}
					break;
					case SkillId.레지스트_엘리멘트:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getResistance().addWind(10);
						pc.getResistance().addWater(10);
						pc.getResistance().addFire(10);
						pc.getResistance().addEarth(10);
						pc.sendPackets(new S_OwnCharAttrDef(pc));
					}
					break;
					case SkillId.프로텍션_프롬_엘리멘탈:{
						L1PcInstance pc = (L1PcInstance) cha;
						int attr = pc.getElfAttr();
						if (attr == 1) {
							pc.getResistance().addEarth(50);
						} else if (attr == 2) {
							pc.getResistance().addFire(50);
						} else if (attr == 4) {
							pc.getResistance().addWater(50);
						} else if (attr == 8) {
							pc.getResistance().addWind(50);
						}
					}
					break;
					case SkillId.바디_투_마인드: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.setCurrentMp(pc.getCurrentMp() + 2);
					} 
					break;

					case SkillId.블러드_투_소울: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.setCurrentMp(pc.getCurrentMp() + 15);
					}
					break;
					case SkillId.인비지블리티: case SkillId.블라인드_하이딩: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_Invis(pc.getId(), 1));
						L1World.getInstance().broadcastPacketToAll(new S_Invis(pc.getId(), 1)); 
					}
					break;
					case SkillId.아이언_스킨: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-10);
						pc.sendPackets(new S_SkillIconShield(10, _getBuffIconDuration));
					}
					break;
					case SkillId.어스_스킨: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-4);
						pc.sendPackets(new S_SkillIconShield(6, _getBuffIconDuration));
					}
					break;
					case SkillId.인챈트_마이티: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAbility().addAddedStr((byte) 5);
						pc.sendPackets(new S_Strup(pc, 5, _getBuffIconDuration));
					}
					break;
					case SkillId.인챈트_덱스터리티: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAbility().addAddedDex((byte) 5);
						pc.sendPackets(new S_Dexup(pc, 5, _getBuffIconDuration));
					}
					break;
					case SkillId.파이어_웨폰: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(4);
						pc.sendPackets(new S_SkillIconAura(147, _getBuffIconDuration));
					}
					break;
					case SkillId.블레스_오브_파이어: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(4);
						pc.sendPackets(new S_SkillIconAura(154, _getBuffIconDuration));
					}
					break;
					case SkillId.버닝_웨폰: {
						L1PcInstance pc = (L1PcInstance) cha; 
						pc.addMeleeDmg(6);
						pc.addMeleeHit(3);
						pc.sendPackets(new S_SkillIconAura(162, _getBuffIconDuration));
					}
					break;
					case SkillId.윈드_샷: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addRangedHit(6);
						pc.sendPackets(new S_SkillIconAura(148, _getBuffIconDuration));
					}
					break;
					case SkillId.아이_오브_스톰: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addRangedDmg(3);
						pc.addRangedHit(2);
						pc.sendPackets(new S_SkillIconAura(155, _getBuffIconDuration));
					}
					break;
					case SkillId.스톰_샷: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addRangedDmg(6);
						pc.addRangedHit(3);
						pc.sendPackets(new S_SkillIconAura(165, _getBuffIconDuration));
					}
					break;
					case SkillId.버서커스: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(10);
						pc.addMeleeDmg(2);
						pc.addMeleeHit(8);
					}
					break;
					case SkillId.셰이프_체인지:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.sendPackets(new S_ShowPolyList(pc.getId()));
					}
					break;
					case SkillId.어드밴스_스피릿:{
						L1PcInstance pc = (L1PcInstance) cha;
						pc.setAdvenHp(pc.getBaseMaxHp() / 5);
						pc.setAdvenMp(pc.getBaseMaxMp() / 5);
						pc.addMaxHp(pc.getAdvenHp());
						pc.addMaxMp(pc.getAdvenMp());
					}
					break;
					case SkillId.그레이터_헤이스트:{
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.getHasteItemEquipped() > 0) {
							continue;
						}
						if (pc.getMoveSpeed() != 2) { // 슬로우중 이외
							pc.setDrink(false);
							pc.setMoveSpeed(1);
							pc.sendPackets(S_SkillHaste.get(pc.getId(), 1, _getBuffIconDuration));
						} else { // 슬로우중
							int skillNum = 0;
							if (pc.hasSkillEffect(SkillId.슬로우)) {
								skillNum = SkillId.슬로우;
							} else if (pc.hasSkillEffect(SkillId.매스_슬로우)) {
								skillNum = SkillId.매스_슬로우;
							} else if (pc.hasSkillEffect(SkillId.인탱글)) {
								skillNum = SkillId.인탱글;
							}
							if (skillNum != 0) {
								pc.removeSkillEffect(skillNum);
								pc.removeSkillEffect(SkillId.그레이터_헤이스트);
								pc.setMoveSpeed(0);
								continue;
							}
						}
					}
					break;
					case SkillId.홀리_워크: case SkillId.무빙_악셀레이션: case SkillId.윈드_워크: {
						L1PcInstance pc = (L1PcInstance) cha;
						if (pc.hasSkillEffect(SkillId.홀리_워크)/* || pc.hasSkillEffect(SkillId.무빙_악셀레이션) || pc.hasSkillEffect(SkillId.윈드_워크)*/) {
							pc.setSkillEffect(SkillId.홀리_워크, _getBuffIconDuration * 1000);
						}
						S_SkillBrave s = S_SkillBrave.get(pc.getId(), 4,_getBuffIconDuration);
						pc.sendPackets(s);
						pc.broadcastPacket(s);
					}
					break;
					

					case SkillId.운세_버프_A: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeDmg(2);    // 근거리 대미지 +2
						pc.addMeleeHit(2);   // 근거리 명중+2
						pc.addRangedHit(2);  // 원거리 명중 +2
						pc.addRangedDmg(2);  // 원거리 대지+2 
						pc.addDamageReduction(3); //대미지 감소 +3
						pc.addSp(2);
						pc.sendPackets(new S_SPMR(pc));
						pc.addMaxHp(50);
						pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
						if (pc.isInParty())
							pc.getParty().updateMiniHP(pc);
						pc.addHpr(3);
						pc.addMaxMp(30);
						pc.addMpr(3);
						pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
					}
						break;
					case SkillId.운세_버프_B: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMeleeHit(2);   // 근거리 명중+2
						pc.addRangedHit(2);  // 원거리 명중 +2
						pc.addDamageReduction(2); //대미지 감소 +3
						pc.addSp(1);
						pc.sendPackets(new S_SPMR(pc));
						pc.addMaxHp(50);
						pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
						if (pc.isInParty())
							pc.getParty().updateMiniHP(pc);
						pc.addMaxMp(30);
						pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
					}
						break;
					case SkillId.운세_버프_C: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.addMaxHp(50);
						pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
						if (pc.isInParty())
							pc.getParty().updateMiniHP(pc);
						pc.addMaxMp(30);
						pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
						pc.getAC().addAc(-2);
						pc.sendPackets(new S_OwnCharAttrDef(pc));
					}
						break;
					case SkillId.운세_버프_D: {
						L1PcInstance pc = (L1PcInstance) cha;
						pc.getAC().addAc(-1);
						pc.sendPackets(new S_OwnCharAttrDef(pc));
					}
						break;
					default:
						break;
					}
				}
				// ●●●● NPC 밖에 효과가 없는 스킬 ●●●●
				if (_calcType == PC_NPC || _calcType == NPC_NPC) {
					// ★★★ 애완동물계 스킬 ★★★
					if (_skillId == SkillId.테이밍_몬스터 && ((MonsterInstance) cha).getNpcTemplate().isTamable()) { // 테이밍몬스타
						int petcost = 0;
						Object[] petlist = _user.getPetList().values().toArray();
						for (Object pet : petlist) {
							// 현재의 애완동물 코스트
							petcost += ((L1NpcInstance) pet).getPetcost();
						}
						int charisma = _user.getAbility().getTotalCha();
						if (_pc.isElf()) { // 에르프
							charisma += 12;
						} else if (_pc.isWizard()) { // 위저드
							charisma += 6;
						}
						charisma -= petcost;
						if (charisma >= 6) { // 펫 코스트의 확인
							SummonInstance summon = new SummonInstance(_targetNpc, _user, false);
							_target = summon; // 타겟 교체
						} else {
							_pc.sendPackets(new S_ServerMessage(319)); // \f1 더 이상의 monster를 조종할 수 없습니다.
						}
					} else if (_skillId == SkillId.크리에이터_좀비) { // 클리에 실 좀비
						int petcost = 0;
						Object[] petlist = _user.getPetList().values().toArray();
						for (Object pet : petlist) {
							// 현재의 애완동물 코스트
							petcost += ((L1NpcInstance) pet).getPetcost();
						}
						int charisma = _user.getAbility().getTotalCha();
						if (_pc.isElf()) { // 에르프
							charisma += 12;
						} else if (_pc.isWizard()) { // 위저드
							charisma += 6;
						}
						charisma -= petcost;
						if (charisma >= 6) { // 펫 코스트의 확인
							SummonInstance summon = new SummonInstance(_targetNpc, _user, true);
							_target = summon; // 타겟 교체
						} else {
							_pc.sendPackets(new S_ServerMessage(319)); // \f1 더 이상의 monster를 조종할 수 없습니다.
						}
					} else if (_skillId == SkillId.위크_엘리멘탈) { // 위크에레멘탈
						if (cha instanceof MonsterInstance) {
							L1Npc npcTemp = ((MonsterInstance) cha).getNpcTemplate();
							int weakAttr = npcTemp.get_weakAttr();
							if ((weakAttr & 1) == 1) {
								cha.broadcastPacket(S_SkillSound.get(cha.getId(), 2169));
							}
							if ((weakAttr & 2) == 2) {
								cha.broadcastPacket(S_SkillSound.get(cha.getId(), 2167));
							}
							if ((weakAttr & 4) == 4) {
								cha.broadcastPacket(S_SkillSound.get(cha.getId(), 2166));
							}
							if ((weakAttr & 8) == 8) {
								cha.broadcastPacket(S_SkillSound.get(cha.getId(), 2168));
							}
						}
					} else if (_skillId == SkillId.리턴_투_네이쳐) {
						if (Config.RETURN_TO_NATURE && cha instanceof SummonInstance) {
							SummonInstance summon = (SummonInstance) cha;
							summon.broadcastPacket(S_SkillSound.get(summon.getId(), 2245));
							summon.returnToNature();
						} else {
							if (_user instanceof L1PcInstance) {
								_pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
							}
						}
					}
				}

				// ■■■■ 개별 처리 여기까지 ■■■■	
				if (_skill.getType() == L1Skills.TYPE_HEAL && _calcType == PC_NPC && undeadType == 1) {
					dmg *= -1; // 만약, 안 뎁트로 회복계 스킬이라면 데미지가 된다.
				}
				if (_skill.getType() == L1Skills.TYPE_HEAL && _calcType == PC_NPC && undeadType == 3) {
					dmg = 0; // 만약, 안 뎁트계 보스로 회복계 스킬이라면 무효
				}
				if (cha instanceof L1TowerInstance && dmg < 0) { // 타워한테 힐을 했을 경우 무효
					dmg = 0;
				}
				if (dmg != 0 || drainMana != 0) {
					_magic.commit(dmg, drainMana); // 데미지계, 회복계의 값을 타겟으로 위탁한다.	
				}
				if (heal > 0) {
					if ((heal + _user.getCurrentHp()) > _user.getMaxHp()) {
						_user.setCurrentHp(_user.getMaxHp());
					} else {
						_user.setCurrentHp(heal + _user.getCurrentHp());
					}
				}
				if (cha instanceof L1PcInstance) { // 타겟이 PC라면, AC와 스테이터스를 송신
					L1PcInstance pc = (L1PcInstance) cha;
					pc.getLight().turnOnOffLight();
					pc.sendPackets(new S_OwnCharAttrDef(pc));
					pc.sendPackets(new S_OwnCharStatus(pc));
					// 현재 스킬 상태중 메세지 실패 처리
					if(pc.hasSkillEffect(SkillId.어스_바인드) || pc.hasSkillEffect(SkillId.앱솔루트_배리어) 
							|| pc.hasSkillEffect(SkillId.상태_동결) || pc.hasSkillEffect(SkillId.아이스_랜스)) {		
						if(!SkillUtil.isSkilldAbol(_skillId)) {
							sendSkillFailMessage();			
						}	
					} else {
						sendHappenMessage(pc); // 타겟으로 메세지를 송신	
					}
				}
				addMagicList(cha, false); // 타겟으로 마법의 효과 시간을 설정
				if (cha instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) cha;
					pc.getLight().turnOnOffLight();
				}
			}
			if (_skillId == SkillId.디텍션 || _skillId == SkillId.카운터_디텍션) {
				detection(_pc);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	private void detection(L1PcInstance pc) {
		if ( _skillId == SkillId.카운터_디텍션) {
			if(pc.getZoneType() == 1 || _pc.getZoneType() == 1) { // 세이프티 존
				return;
			}
		}
		if (!pc.isGmInvis() && pc.isInvisble()) { // 자신
			pc.delInvis();
			pc.beginInvisTimer();
		}
		for (L1PcInstance tgt : L1World.getInstance().getVisiblePlayer(pc)) {
			if (!tgt.isGmInvis() && tgt.isInvisble()) {
				tgt.delInvis();
			}
		}
		L1WorldTraps.getInstance().onDetection(pc);
	}

	// 타겟으로 붙어 계산할 필요가 있을까 돌려준다
	private boolean isTargetCalc(L1Character cha) {
		// 공격 마법의 Non－PvP 판정
		if (_skill.getTarget().equals("attack") && _skillId != 18) { // 공격 마법
			if (_calcType == PC_PC || // 대상이 PC
					(_calcType == PC_NPC && cha instanceof PetInstance) || // 대상이 애완동물
					(_calcType == PC_NPC && cha instanceof SummonInstance)) { // 대상이 사몬
				if (_pc.getZoneType() == 1 || cha.getZoneType() == 1 // 공격하는 측 또는 공격받는 측이 세이프티 존
						|| _pc.checkNonPvP(_pc, cha)) { // Non-PvP 설정
					return false;
				}
			}
		}
		// fog 오브 슬리핑은 자기 자신은 대상외
		if (_skillId == SkillId.포그_오브_슬리핑 && _user.getId() == cha.getId()) {
			return false;
		}
		// 매스 슬로우는 자기 자신과 자신의 애완동물은 대상외
		if (_skillId == SkillId.매스_슬로우) {
			if (_user.getId() == cha.getId()) {
				return false;
			}
			if (cha instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) cha;
				if (_user.getId() == summon.getMaster().getId()) {
					return false;
				}
			} else if (cha instanceof PetInstance) {
				PetInstance pet = (PetInstance) cha;
				if (_user.getId() == pet.getMaster().getId()) {
					return false;
				}
			}
		}
		// 매스 텔레포트는 자기 자신만 대상(동시에 크란원도 텔레포트 시킨다)
		if (_skillId == SkillId.매스_텔레포트) {
			if (_user.getId() != cha.getId()) {
				return false;
			}
		}

		return true;
	}

	private boolean isUseCounterMagic(L1Character cha) {
		if (_isCounterMagic && cha.hasSkillEffect(SkillId.카운터_매직)) {
			cha.removeSkillEffect(SkillId.카운터_매직);
			S_SkillSound ss = S_SkillSound.get(cha.getId(), EffectUtil.카운터_매직);
			cha.broadcastPacket(ss);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(ss); 
			} 
			return true;
		}
		return false;
	}
	// 타겟으로 대해 반드시 실패가 될까 돌려준다
	private boolean isTargetFailure(L1Character cha) {
		boolean isTU = false;
		boolean isErase = false;
		boolean isManaDrain = false;
		int undeadType = 0;

		if (cha instanceof L1TowerInstance) { // 타워 인경우 확률계 실패 처리 
			return true;
		}
		if (cha instanceof L1PcInstance) { // 대  PC의 경우
			if (_calcType == PC_PC && _pc.checkNonPvP(_pc, cha)) { // Non-PvP 설정
				L1PcInstance pc = (L1PcInstance) cha;
				if (_pc.getId() == pc.getId() || (pc.getClanid() != 0 && _pc.getClanid() == pc.getClanid())) {
					return false;
				}
				return true;
			}
			return false;
		}
		if (cha instanceof MonsterInstance) { // 턴 언데드 가능 여부 
			isTU = ((MonsterInstance) cha).getNpcTemplate().get_IsTU();
		}
		if (cha instanceof MonsterInstance) { // 이레이즈매 매직 가능 여부
			isErase = ((MonsterInstance) cha).getNpcTemplate().get_IsErase();
		}
		if (cha instanceof MonsterInstance) { // 언데드 판정 1 언데드
			undeadType = ((MonsterInstance) cha).getNpcTemplate().getUndead();
		}
		if (cha instanceof MonsterInstance) {
			isManaDrain = true;
		}
		if ((_skillId == SkillId.턴_언데드 && (undeadType == 0 || undeadType == 2))
				|| (_skillId == SkillId.턴_언데드 && !isTU) || ((_skillId == SkillId.이레이즈_매직 
				|| _skillId == SkillId.슬로우 || _skillId == SkillId.마나_드레인 || _skillId == SkillId.매스_슬로우 || _skillId == SkillId.인탱글) && !isErase)
				|| (_skillId == SkillId.마나_드레인 && !isManaDrain)) {

			return true;
		}
		return false;
	}
}