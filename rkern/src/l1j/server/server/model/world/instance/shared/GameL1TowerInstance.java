package l1j.server.server.model.world.instance.shared;

import java.util.LinkedList;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.shared.L1Type;
import l1j.server.server.shared.listener.DamageListener;
import l1j.server.server.templates.L1Npc;
@SuppressWarnings("serial")
public class GameL1TowerInstance extends L1TowerInstance{
	protected static final int[] CRACK_STATUS = new int[]{ActionCodes.ACTION_TowerCrack1, ActionCodes.ACTION_TowerCrack2, ActionCodes.ACTION_TowerCrack3};

	private LinkedList<DamageListener<GameL1TowerInstance>> _damage_listeners;
	public GameL1TowerInstance(L1Npc template) {
		super(template);
		_damage_listeners = new LinkedList<DamageListener<GameL1TowerInstance>>();
	}

	public void add_damage_listeners(DamageListener<GameL1TowerInstance> listener){
		if(_damage_listeners == null)
			_damage_listeners = new LinkedList<DamageListener<GameL1TowerInstance>>();
		_damage_listeners.add(listener);
	}

	private void on_death(){
		if(_damage_listeners != null){
			for(DamageListener<GameL1TowerInstance> listener : _damage_listeners)
				listener.on_daed(_lastattacker, this);
			_damage_listeners.clear();
			_damage_listeners = null;
		}
	}

	protected void do_crack_status(int status){
		int act = CRACK_STATUS[status - 1];
		SC_DoActionGFX.broadcast(this, act);
		setStatus(act);
		_crackStatus = status;
	}

	protected void do_death(L1PcInstance pc){
		_crackStatus = 0;
		set_death(pc);
	}

	protected void set_death(L1PcInstance pc){
		setDead(true);
		setStatus(ActionCodes.ACTION_TowerDie);
		_lastattacker = pc;
		SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_TowerDie);
		on_death();
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage){
		if(!attacker.instanceOf(L1Type.L1TYPE_PC) || attacker == null)
			return;

		if(_damage_listeners != null){
			for(DamageListener<GameL1TowerInstance> listener : _damage_listeners){
				if(!listener.is_filter(attacker, this, damage))
					return;
			}
		}

		L1PcInstance pc = (L1PcInstance)attacker;
		int currentHp = getCurrentHp();
		if(currentHp > 0 && !isDead()){
			int newHp = currentHp - damage;
			setCurrentHp(newHp);
			if(newHp <= 0){
				if(!isDead()){
					do_death(pc);
				}
				return;
			}
			int p = (int) (((double) newHp / (double) getMaxHp()) * 100D);
			if(p <= 25){
				if(_crackStatus != 3){
					do_crack_status(3);
				}
			}else if(p <= 50){
				if(_crackStatus != 2){
					do_crack_status(2);
				}
			}else if(p <= 75){
				if(_crackStatus != 1){
					do_crack_status(1);
				}				
			}
			if(_damage_listeners != null){
				for(DamageListener<GameL1TowerInstance> listener : _damage_listeners){
					listener.on_damage(attacker, this, damage);
				}
			}
		}else if(!isDead()){
			do_death(pc);
		}
	}

}
