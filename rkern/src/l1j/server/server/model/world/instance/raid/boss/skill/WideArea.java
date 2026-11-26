package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;

import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

public class WideArea extends RaidBossSkill{

    @Override
    public String toString(){
        return WideArea.class.getName();
    }

    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        try{
            own.setParalyzed(true);
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());

            CInstanceEvents.start(new tightenEffectThread(own, pcs, _effectId, _range));
            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.setParalyzed(false);
            own.endCast();
        }
    }

    @Override
    public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0)
            return;

        if(!own.tryBeginCast())
            return;

        try{
            if(_actid >= 0)
                SC_DoActionGFX.broadcast(own, getSafeActId());
            CInstanceEvents.start(new tightenEffectThread(own, pcs, _effectId, _range));
            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.endCast();
        }
    }

    /** 원형으로 조여오는 이펙트 **/
    public class tightenEffectThread implements Runnable{
        private L1NpcInstance 			_own;
        private ArrayList<L1PcInstance> _pcs;
        private int						_range;
        private int						_effect;
        // [TEST] 생성자 파라미터 순서: (own, pcs, effectId, range) 순서로 호출되지만 생성자는 (own, pcs, range, effect) 순 → 순서 바뀐 버그
        public tightenEffectThread(L1NpcInstance own, ArrayList<L1PcInstance> pcs, int range, int effect){
            _own 	= own;
            _pcs	= pcs;
            _range	= range;
            _effect	= effect;
        }

        @Override
        public void run(){
            double radX = Math.max(1, _range / 2);
            double radY = radX;
            double cx	= _own.getX();
            double cy	= _own.getY();
            int rx		= 0;
            int ry		= 0;
            // [TEST] S_EffectLocation 임시 주석 처리 - 3.8 미지원 effectId 크래시 원인 확인용
            long eff = 0L;
            for(int z=_range; z>=1 && !CInstanceEvents.isStopping(); z-=3){
                for(int i=1; i<=360; i += radX){
                    rx		= (int)(cx+Math.cos(i) * radX);
                    ry		= (int)(cy+Math.sin(i) * radY);
                    //eff		= new S_EffectLocation(rx, ry, _effect);
                    for(L1PcInstance pc : _pcs){
                        if(pc == null || pc.getMapId() != _own.getMapId())
                            continue;
                        // [TEST] eff 전송 주석 - S_EffectLocation 미생성으로 NPE 방지
                        //pc.sendPackets(eff, false);
                        if(!pc.isDead() && pc.getX() == rx && pc.getY() == ry){
                            if(isSkillSet(_own, pc))
                                pc.receiveDamage(_own, 500);
                        }
                    }
                    //eff.clear();
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException stopping) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

}
