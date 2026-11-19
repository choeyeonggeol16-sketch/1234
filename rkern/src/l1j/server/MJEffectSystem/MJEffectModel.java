package l1j.server.MJEffectSystem;

import java.util.Random;

import l1j.server.MJEffectSystem.Realize.OnChaseThread;
import l1j.server.MJEffectSystem.Realize.OnEffectThread;
import l1j.server.MJEffectSystem.Realize.OnRadiationThread;
import l1j.server.MJEffectSystem.Realize.OnRotationThread;
import l1j.server.server.model.Instance.L1NpcInstance;

public class MJEffectModel{
	protected static Random _rnd = new Random(System.nanoTime());
	private static final int ET_RANDOM 		= 0;
	private static final int ET_TIGHTEN 	= 1;
	private static final int ET_ROTATE		= 2;
	private static final int ET_RADIATION	= 3;
	private static final int ET_CHASE		= 4;
	private static final int[] ETS_RANDOM = new int[]{
		ET_TIGHTEN, ET_ROTATE, ET_RADIATION, ET_CHASE
	};
	private int				_type = -1;
	private int 			_effectId;		// 이펙트 id
	private int				_actId;
	private int 			_impact;		// 대미지가 미칠 반경
	private int 			_range;			// 범위
	private int 			_dmgMin;		// 최소 대미지
	private int				_dmgMax;		// 최대 대미지
	private int				_delay;			// delay
	private int				_zpos;			// 좁혀 들어오는 크기
	private int				_ratio;			// 확률
	
	public MJEffectModel(){
		
	}
	
	public void setType(String s){
        _type = -1;
        if (s == null) return;
		if(s.equalsIgnoreCase("Random"))
			_type = ET_RANDOM;
		else if(s.equalsIgnoreCase("Tighten"))
			_type = ET_TIGHTEN;
		else if(s.equalsIgnoreCase("Rotate"))
			_type = ET_ROTATE;
		else if(s.equalsIgnoreCase("Radiation"))
			_type = ET_RADIATION;
		else if(s.equalsIgnoreCase("Chase"))
			_type = ET_CHASE;
	}
	
	public int getEffectId(){
		return _effectId;
	}
	
	public void setEffectId(int i){
		_effectId = i;
	}
	
	public int getActionId(){
		return _actId;
	}
	
	public void setActionId(int i){
		_actId = i;
	}
	
	public int getImpact(){
		return _impact;
	}
	
	public void setImpact(int i){
		_impact = i;
	}
	
	public int getRange(){
		return _range;
	}
	
	public void setRange(int i){
		_range = i;
	}
	
	public int getDamageMin(){
		return _dmgMin;
	}
	
	public void setDamageMin(int i){
		_dmgMin = i;
	}
	
	public int getDamageMax(){
		return _dmgMax;
	}
	
	public void setDamageMax(int i){
		_dmgMax = i;
	}
	
	public int getDelay(){
		return _delay;
	}
	
	public void setDelay(int i){
		_delay = i;
	}
	
	public int getZPos(){
		return _zpos;
	}
	
	public void setZPos(int i){
		_zpos = i;
	}
	
	public void setRatio(int i){
		_ratio = i;
	}
	
	public int getRatio(){
		return _ratio;
	}
	
	public boolean isEffectSet(){
		return _rnd.nextInt(100) + 1 <= _ratio;
	}
	
	/** Bound the full cast before admitting any task to the shared scheduler. */
    public boolean isSafe() {
        if (_range < 1 || _range > 32 || _impact < 1 || _impact > 32
                || _delay < 1 || _delay > 5000 || _ratio < 0 || _ratio > 100
                || _dmgMin > _dmgMax) return false;
        final int type = _type;
        if (type < ET_RANDOM || type > ET_CHASE) return false;
        if (type == ET_TIGHTEN || type == ET_RANDOM) {
            if (_zpos < 1 || _zpos > 32) return false;
        }
        int steps = 0;
        if (type == ET_TIGHTEN || type == ET_RANDOM) {
            for (int r = _range; r > 0; r -= _zpos) steps += (r * 2 + _impact - 1) / _impact;
        }
        if (type == ET_ROTATE || type == ET_RANDOM) {
            final int n = (_range + 1) / 2;
            steps = Math.max(steps, n * n);
        }
        if (type == ET_RADIATION || type == ET_RANDOM)
            steps = Math.max(steps, (_range + _impact - 1) / _impact);
        if (type == ET_CHASE || type == ET_RANDOM) steps = Math.max(steps, _range);
        return steps > 0 && steps <= 512 && (long) steps * _delay <= 60000L;
    }

    public boolean runEffect(L1NpcInstance npc){
        if (npc == null || !isSafe()) return false;
		int tmpType = _type;
		if(_type == ET_RANDOM)
			tmpType = ETS_RANDOM[_rnd.nextInt(ETS_RANDOM.length)];
		
		switch(tmpType){
		case ET_TIGHTEN:
            return new OnEffectThread(this, npc).start();
		case ET_ROTATE:
            return new OnRotationThread(this, npc).start();
		case ET_RADIATION:
            return new OnRadiationThread(this, npc).start();
		case ET_CHASE:
            return new OnChaseThread(this, npc).start();
        }
        return false;
    }
}
