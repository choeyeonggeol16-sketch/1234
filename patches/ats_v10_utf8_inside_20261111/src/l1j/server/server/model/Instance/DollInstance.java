package l1j.server.server.model.Instance;

import java.util.Arrays;
import java.util.Timer;
import I1j.server.server.model.Thread.HpRegenerationDollTimer;
import I1j.server.server.model.Thread.MakeItemByDollTimer;
import I1j.server.server.model.Thread.MpRegenerationDollTimer;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.Templates.L1Npc;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.utils.EffectUtil;

public class DollInstance extends L1NpcInstance {
	private static final int[] DollAction = { 66, 67, 68, 69 };
	private static final long serialVersionUID = 1L;
	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();
	private MagicDoll _magicDoll;
	private static Timer _timer = new Timer();
	private HpRegenerationDollTimer _hprTimer;
	private MpRegenerationDollTimer _mprTimer;
	private MakeItemByDollTimer _makeTimer;
	private int sleeptime_PT = 64;
	private int _itemId;
	private int _itemObjId;
	private int _itemBless;
	
	public boolean noTarget() {
		if (_master == null) {
			deleteDoll();
			return true;
		}
		if (_master.isDead()) {
			deleteDoll();
			return true;
		}
		if(_master.isInvisble()) {
			deleteDoll();
			return true;
		}
		if (_master != null && _master.getMapId() == getMapId()) {
			int distance = getLocation().getTileLineDistance(_master.getLocation());	
			if (getLocation().getTileLineDistance(_master.getLocation()) > 40) {
				teleport(_master.getX(), _master.getY(), getHeading());
			} else if (distance > 2) {
				int dir = moveDirection(_master.getX(), _master.getY());
				setDirectionMove(dir);
				setSleepTime(getCurrentSpriteInterval(EActionCodes.walk));
			} else if (this.sleeptime_PT == 0) {
				broadcastPacket(S_DoActionGFX.get(getId(), DollAction[_random.nextInt(2)]));
				sleeptime_PT = 64;
			} else {
				sleeptime_PT -= 1;
				setSleepTime(500);
			}
		} else {
			deleteDoll();
			return true;
		}
		return false;
	}
	public DollInstance(L1Npc template, L1PcInstance master, ItemInstance item) {
		super(template);
		setId(IdFactory.getInstance().nextId());
		setItemId(item.getItemId());
		setItemObjId(item.getId());

		_magicDoll = MagicDoll.get(item.getItemId());

		int summonTime = getDoll().getInfo().getSummonTime() * 1000;
		GeneralThreadPool.getInstance().schedule(new DollTimer(), summonTime);

		setMaster(master);
		setX(_random.nextInt(5) + master.getX() - 2);
		setY(_random.nextInt(5) + master.getY() - 2);
		setMap(master.getMapId());
		setHeading(_random.nextInt(8));
		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
		master.setMagicDoll(this);

		broadcastPacket(S_SkillSound.get(getId(), EffectUtil.인형_소환_이펙트));

		if (!isAiRunning()) {
			startAI();
		}
		startHprTimer();
		startMprTimer();
		startMakeTimer();

		if (_master != null) {
			effectToMaster(1);
		}
	}

	public void deleteDoll() {
	    try {
	      broadcastPacket(S_SkillSound.get(getId(), EffectUtil.인형_해제_이펙트));
	      _master.sendPackets(new S_SkillIconGFX(56, 0)); 
	      stopHprTimer();
	      stopMprTimer();
	      stopMakeTimer();
	      getMap().setPassable(getLocation(), true);  // 이거추가
	      if (_master != null) {
	        effectToMaster(-1);
	        _master.setMagicDoll(null);
	      }
	    } catch (Exception e) {
	      e.printStackTrace();
	    } finally {
	      deleteMe();
	    }
	  }

	private void effectToMaster(int type) {
		if (_master instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _master;
			if (getDoll().getEffect().getHp() != 0) {
				pc.addMaxHp(type * getDoll().getEffect().getHp());
			}
			if (getDoll().getEffect().getHpr() != 0 && getDoll().getEffect().getHprTime() == 0) {
				pc.addHpr(type * getDoll().getEffect().getHpr());
			}
			if (getDoll().getEffect().getMp() != 0) {
				pc.addMaxMp(type * getDoll().getEffect().getMp());
			}
			if (getDoll().getEffect().getMpr() != 0 && getDoll().getEffect().getMprTime() == 0) {
				pc.addMpr(type * getDoll().getEffect().getMpr());
			}
			int add_ac = getDoll().getEffect().getAc();
			if (add_ac != 0) {
				pc.getAC().addAc(type * add_ac);
			}
			if (getDoll().getEffect().getStr() != 0) {
				pc.getAbility().addAddedStr(type * getDoll().getEffect().getStr());
			}
			if (getDoll().getEffect().getCon() != 0) {
				pc.getAbility().addAddedCon(type * getDoll().getEffect().getCon());
			}
			if (getDoll().getEffect().getDex() != 0) {
				pc.getAbility().addAddedDex(type * getDoll().getEffect().getDex());
			}
			if (getDoll().getEffect().getCha() != 0)
				pc.getAbility().addAddedCha(type * getDoll().getEffect().getCha());
			if (getDoll().getEffect().getInt() != 0) {
				pc.getAbility().addAddedInt(type * getDoll().getEffect().getInt()); 
    		}
			if (getDoll().getEffect().getWis() != 0) {
				pc.getAbility().addAddedWis(type * getDoll().getEffect().getWis()); 
			}
			if (getDoll().getEffect().getDmg() != 0) {
				pc.addMeleeDmg(type * getDoll().getEffect().getDmg());
			}		
			if (getDoll().getEffect().getBowDmg() != 0) {
				pc.addRangedDmg(type * getDoll().getEffect().getBowDmg());
			}
			if (getDoll().getEffect().getHit() != 0) {
				pc.addMeleeHit(type * getDoll().getEffect().getHit());
			}
			if (getDoll().getEffect().getBowHit() != 0) {
				pc.addRangedHit(type * getDoll().getEffect().getBowHit());
			}
			if (getDoll().getEffect().getMr() != 0) {
				pc.getResistance().addMr(type * getDoll().getEffect().getMr());
			}
			if (getDoll().getEffect().getSp() != 0) {
				pc.addSp(type * getDoll().getEffect().getSp());
			}
			if (getDoll().getEffect().getDmgReduction() != 0) {
				((L1PcInstance) pc).addDamageReduction(type * getDoll().getEffect().getDmgReduction());
			}
			if (getDoll().getEffect().getWeightReduction() != 0) {
				pc.addWeightReduction(type * getDoll().getEffect().getWeightReduction());
			}
			if (getDoll().getEffect().getPvPDamage() != 0) {
				pc.addDmgupPvP(type * getDoll().getEffect().getPvPDamage());
			}
			if (getDoll().getEffect().getPvpReduction() != 0) {
				pc.addDmgDownPvP(type * getDoll().getEffect().getPvpReduction());
			}	
			if (getDoll().getEffect().getAdenaBonus() != 0) {
				pc.addAdenaBonus(type * getDoll().getEffect().getAdenaBonus());
			}	
			if (getDoll().getEffect().get_regist_technology() != 0) {
				pc.getResistance().addregist_technology(type * getDoll().getEffect().get_regist_technology());
			}	
			if (getDoll().getEffect().get_regist_All() != 0) {
				pc.getResistance().regist_all(type * getDoll().getEffect().get_regist_All());
			}	
			if (getDoll().getEffect().get_hit_All() != 0) {
				pc.getResistance().hit_all(type * getDoll().getEffect().get_hit_All());
			}
			if (getDoll().getEffect().get_technique_hit() != 0) {
				pc.getResistance().addtechnique_hit(type * getDoll().getEffect().get_technique_hit());
			}	
			if (getDoll().getEffect().get_regist_spirit() != 0) {
				pc.getResistance().addregist_spirit(type * getDoll().getEffect().get_regist_spirit());
			}		
			if (getDoll().getEffect().get_spirit_hit() != 0) {
				pc.getResistance().addspirit_hit(type * getDoll().getEffect().get_spirit_hit());
			}	
			if (getDoll().getEffect().get_magic_hit() != 0) {
				pc.addMagicHit(type * getDoll().getEffect().get_magic_hit());
			}	
			if (getDoll().getEffect().isHaste()) {
				if (type == 1) {
					pc.addHasteItemEquipped(1);
					pc.removeHasteSkillEffect();
					pc.setMoveSpeed(1);
					S_SkillHaste dd = S_SkillHaste.get(pc.getId(), 1, -1);
					pc.sendPackets(dd); 
					pc.broadcastPacket(dd);
				} else {
					pc.addHasteItemEquipped(-1);
					if (pc.getHasteItemEquipped() == 0) {
						pc.setMoveSpeed(0);
						S_SkillHaste aa = S_SkillHaste.get(pc.getId(), 0, 0);
						pc.sendPackets(aa); 
						pc.broadcastPacket(aa);
					}
				}
			}
			if (getDoll().getEffect().getSkillId() == SkillId.오토루팅) {
				if (type == -1) {
					if (pc.hasSkillEffect(SkillId.오토루팅)) {
						pc.removeSkillEffect(SkillId.오토루팅);
					}
				} else {
					pc.setSkillEffect(SkillId.오토루팅, 0);
				}
			}
            pc.sendPackets(new S_OwnCharStatus(pc), true);
            pc.sendPackets(new S_OwnCharAttrDef(pc), true);
		}
	}

	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.doll(this, perceivedFrom));
	}
	public void onItemUse() {
		if (!isActived()) {
			useItem(1, 100);
		}
	}
	public void onGetItem(ItemInstance item, int count) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		if (Arrays.binarySearch(haestPotions, item.getItem().getItemId()) >= 0) {
			useItem(1, 100);
		}
	}
	public void startMakeTimer() {
		if (getDoll().getEffect().getMakeTime() > 0 && getDoll().getEffect().getMakeItemId() > 0) {
			int interval = getDoll().getEffect().getMakeTime() * 1000;

			if (_makeTimer == null) {
				_makeTimer = new MakeItemByDollTimer((L1PcInstance) _master, this);
				_timer.scheduleAtFixedRate(_makeTimer, interval, interval);
			}
		}
	}
	public void stopMakeTimer() {
		if (this._makeTimer != null) {
			this._makeTimer.cancel();
			this._makeTimer = null;
		}
	}
	public void startHprTimer() {
		if (getDoll().getEffect().getHprTime() > 0 && getDoll().getEffect().getHpr() > 0) {
			int regen_hpr = getDoll().getEffect().getHpr();
			int interval = getDoll().getEffect().getHprTime() * 1000;
 
			if (_hprTimer != null) {
				_hprTimer.cancel();
				_hprTimer = null;
			}		
			if (_hprTimer == null) { 
				_hprTimer = new HpRegenerationDollTimer((L1PcInstance) this._master, this, regen_hpr);
				_timer.scheduleAtFixedRate(_hprTimer, interval, interval);
			}
		}
	}
	public void stopHprTimer() {
		if (_hprTimer != null) {
			_hprTimer.cancel();
			_hprTimer = null;
		}
	}
	public void startMprTimer() {
		if (getDoll().getEffect().getMprTime() > 0 && getDoll().getEffect().getMpr() > 0) {
			int regen_hpr = getDoll().getEffect().getMpr();
			int interval = getDoll().getEffect().getMprTime() * 1000;

			if (_mprTimer != null) {
				_mprTimer.cancel();
				_mprTimer = null;
			}
			
			if (_mprTimer == null) {
				_mprTimer = new MpRegenerationDollTimer((L1PcInstance) this._master, this, regen_hpr);
				_timer.scheduleAtFixedRate(_mprTimer, interval, interval);
			}
		}
	}
	public void stopMprTimer() {
		if (this._mprTimer != null) {
			this._mprTimer.cancel();
			this._mprTimer = null;
		}
	}
	public int getItemBless() {
		return _itemBless;
	}	
	public void setItemBless(int i) {
		_itemBless = i;
	}
	public int getItemObjId() {
		return this._itemObjId;
	}
	public void setItemObjId(int i) {
		this._itemObjId = i;
	}
	public int getItemId() {
		return this._itemId;
	}
	public void setItemId(int i) {
		this._itemId = i;
	}
	public MagicDoll getDoll() {
		return this._magicDoll;
	}
	class DollTimer implements Runnable {
		DollTimer() {
		}
		public void run() {
			if (_destroyed) {
				return;
			}
			deleteDoll();
		}
	}
}