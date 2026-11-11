package l1j.server.server.Controller;


import java.util.TimerTask;

import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;

public class HpMpRegenController extends TimerTask {
	private final L1PcInstance _pc;
	private final int attackSec = 6;
	private final int moveSec = 2;
	private int oldLevel = 0;
	private int oldLoc = 0;
	private int regenTimeCount = 0;
	private int levelBaseSec = 0;

	public HpMpRegenController(L1PcInstance pc) { //처음 스레드 실행시킬때 한번 그이후에는 바로 run 스레드만 실행하는듯.
		_pc = pc;
		levelBaseSecRefresh(pc.getLevel());
	}

	private void levelBaseSecRefresh(int lv) {
		if (lv < 55) { //55이하거나
			levelBaseSec = 12;
		} else if (lv >= 55 && lv <= 65) { //55~65 사이거나.
			levelBaseSec = 8;
		} else if (lv > 65) { //65 이상일때
			levelBaseSec = 4;
		}
		oldLevel = lv;
	}
	//리젠스테이터스를 갱신할때는 가장먼저 공격중인지를 우선시한다. 그다음 움직임 다음 제자리 순으로 갱신한다.
	private void regenStatusRefresh() { //1초마다 리프레쉬.
		if (oldLevel != _pc.getLevel()) {
			//System.out.println("레벨 변화감지");
			levelBaseSecRefresh(_pc.getLevel()); //올드 레벨과 다를경우 갱신
		}
		if (_pc.isPinkName()) { //현재 전투중인지.
			//System.out.println("전투감지");
			regenTimeCount = levelBaseSec + attackSec;
		} else {
			int newLoc = _pc.getX() + _pc.getY();		
			//System.out.println("oldLoc : " + oldLoc + " / newLoc : " + newLoc);
			if (oldLoc != newLoc) { //움직였을경우 리젠타임카운트 변동.
				//System.out.println("무빙감지 : " + newLoc);
				regenTimeCount = levelBaseSec + moveSec;
				oldLoc = newLoc;
			} else { //움직이지않고 자리에 가만히 있을때
				regenTimeCount = levelBaseSec;
			}
		}
		//System.out.println("regenTimeCount : " + regenTimeCount + " / levelBaseSec : " + levelBaseSec);
	}
	private void hpregen() {
		if (_pc.getCurrentHp() == _pc.getMaxHp() && !isUnderwater(_pc)) 
			return;
		_regenHpPoint += 1;
		synchronized (this) {
			if (regenTimeCount <= _regenHpPoint) {
				_regenHpPoint = 0;
				regenHp();
			}
		}
	}

	private int _regenHpPoint = 0;

	private int _regenMpPoint = 0;

	private int _curMpPoint = 4;

	
	private boolean isPcCk(L1PcInstance pc) {
		if (pc == null || pc.isDead() || pc.getNetConnection() == null || pc.getCurrentHp() == 0 || pc.noPlayerck2)
			return true;
		return false;
	}
	@Override
	public void run() {
		try { 
			regenStatusRefresh(); //이건 신형 hp리젠 위하여
			if (!isPcCk(_pc)) {
				hpregen();
			}
			if (!isPcCk(_pc)) {
				mpregen();
			} 
		} catch (Exception e) {
			_pc.setHpMpRegenActive(false);
			//e.printStackTrace();
		}
	}

	private void mpregen() {
		int nowMaxMp = _pc.getMaxMp();
		if (_pc.isDead()) {
			return;
		}
		if (_pc.getCurrentMp() == nowMaxMp) {
			return;
		}
		_regenMpPoint += _curMpPoint;
		_curMpPoint = 4;
		if (64 <= _regenMpPoint) {
			_regenMpPoint = 0;
			regenMp();
		}
	}

	public void regenMp() {
		int wisStatMpr = _pc.getAbility().getTotalWis() / 5, 
				baseMpr = CalcUtil.calcBaseMpr(_pc.getType(), _pc.getAbility().getBaseWis()), 
				equipMpr = _pc.getMpr(), newMpr = 0,
				bonus = CommonUtil.randomRange(1, wisStatMpr);
		
		newMpr += bonus + equipMpr + baseMpr;
		
		if (L1HouseLocation.isInHouse(_pc.getX(), _pc.getY(), _pc.getMapId())) {
			newMpr += 3;
		}
		if (isInn(_pc)) {
			newMpr += 15;
		}
		if (isPlayerInLifeStream(_pc)) {
			newMpr += 20;
		}
		if (_pc.getFood() < 24 || isOverWeight(_pc)) {
			newMpr = 0;
			if (equipMpr > 0) {
				equipMpr = 0;
			}
		}
		
		int newMp = _pc.getCurrentMp() + newMpr;

		_pc.setCurrentMp(newMp);
	}
	public void regenHp() {
		try {
			if (_pc.isDead()) {
				return;
			}
			if (_pc.getCurrentHp() == _pc.getMaxHp() && !isUnderwater(_pc)) {
				return;
			}

			// 베이스 CON 보너스
			int conStatHpr = _pc.getAbility().getTotalCon() / 2, 
					baseHpr = CalcUtil.calcBaseHpr(_pc.getType(), _pc.getAbility().getBaseCon()), 
					equipHpr = _pc.getHpr(), newHpr = 0,
					bonus = CommonUtil.randomRange(1, conStatHpr);
			
			newHpr += bonus + equipHpr + baseHpr;
			
			if (isInn(_pc)) {
				newHpr += 15;
			}
			if (L1HouseLocation.isInHouse(_pc.getX(), _pc.getY(), _pc.getMapId())) {
				newHpr += 15;
			}

			boolean inLifeStream = false;
			if (isPlayerInLifeStream(_pc)) {
				inLifeStream = true;
				// 고대의 공간, 마족의 신전에서는 HPR+3은 없어져?
				newHpr += 20;
			}

			// 공복과 중량의 체크
			if (_pc.getFood() < 40 || isOverWeight(_pc) || _pc.hasSkillEffect(SkillId.버서커스)) {
				newHpr = 0;
				baseHpr = 0;
				// 장비에 의한 HPR 증가는 만복도, 중량에 의해 없어지지만, 감소인 경우는 만복도, 중량에 관계없이 효과가
				// 남는다
				if (equipHpr > 0) {
					equipHpr = 0;
				}
			}

			int newHp = _pc.getCurrentHp() + newHpr;

			if (newHp < 1) {
				newHp = 1; // HPR 감소 장비에 의해 사망은 하지 않는다
			}
			// 수중에서의 감소 처리
			// 라이프 시냇물로 감소를 없앨 수 있을까 불명
			if (isUnderwater(_pc)) {
				newHp -= 20;
				if (newHp < 1) {
					if (_pc.isGm()) {
						newHp = 1;
					} else {
						_pc.death(null); // HP가 0이 되었을 경우는 사망한다.
					}
				}
			}
			// Lv50 퀘스트의 고대의 공간 1 F2F에서의 감소 처리
			if (isLv50Quest(_pc) && !inLifeStream) {
				newHp -= 10;
				if (newHp < 1) {
					if (_pc.isGm()) {
						newHp = 1;
					} else {
						_pc.death(null); // HP가 0이 되었을 경우는 사망한다.
					}
				}
			}

			if (!_pc.isDead()) {
				_pc.setCurrentHp(Math.min(newHp, _pc.getMaxHp()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isUnderwater(L1PcInstance pc) {
		// 워터 부츠 장비시인가, 에바의 축복 상태이면, 수중은 아니면 간주한다.
		if (pc.toArmorEquipped().isUnderwaterBreathArmor()) {
			return false;
		}
		if (pc.hasSkillEffect(SkillId.상태_호흡)) {
			return false;
		}
		return pc.getMap().isUnderwater();
	}

	private boolean isOverWeight(L1PcInstance pc) {
		if (pc.hasSkillEffect(SkillId.엑조틱_바이탈라이즈) || pc.hasSkillEffect(SkillId.어디셔널_파이어)) {
			return false;
		} 
		if (isInn(pc)) {
			return false;
		}
		return (50 <= pc.getInventory().getWeight100()) ? true : false;
	}

	private boolean isLv50Quest(L1PcInstance pc) {
		int mapId = pc.getMapId();
		return (mapId == 2000 || mapId == 2001) ? true : false;
	}
	
	/**
	 * 지정한 PC가 라이프 시냇물의 범위내에 있는지 체크한다
	 * 
	 * @param pc
	 *            PC
	 * @return true PC가 라이프 시냇물의 범위내에 있는 경우
	 */
	private static boolean isPlayerInLifeStream(L1PcInstance pc) {
		EffectInstance effect = null;
		for (L1Object object : pc.getKnownObjects()) {
			if (!(object instanceof EffectInstance)) {
				continue;
			}
			effect = (EffectInstance) object;
			if (effect.getNpcId() == 81169 && effect.getLocation().getTileLineDistance(pc.getLocation()) < 4) {
				return true;
			}
		}
		return false;
	}
	private boolean isInn(L1PcInstance pc) {
		int mapId = pc.getMapId();
		return (mapId == 16384 || mapId == 16896 || mapId == 17408 || mapId == 17492 || mapId == 17820
				|| mapId == 17920 || mapId == 18432 || mapId == 18944 || mapId == 19456 || mapId == 19968
				|| mapId == 20480 || mapId == 20992 || mapId == 21504 || mapId == 22016 || mapId == 22528
				|| mapId == 23040 || mapId == 23552 || mapId == 24064 || mapId == 24576 || mapId == 25088) ? true
						: false;
	}

}
