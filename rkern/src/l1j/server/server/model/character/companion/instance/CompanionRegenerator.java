package l1j.server.server.model.character.companion.instance;

import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.companion.CompanionSettings;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.Handler;

public class CompanionRegenerator implements TimeListener, Runnable {
	private static volatile CompanionRegenerator _instance;
    private static final Logger LOG = Logger.getLogger(CompanionRegenerator.class.getName());
	public static CompanionRegenerator getInstance(){
        CompanionRegenerator instance = _instance;
        if (instance == null) {
            synchronized (CompanionRegenerator.class) {
                instance = _instance;
                if (instance == null) _instance = instance = new CompanionRegenerator();
            }
        }
        return instance;
    }

	private final AtomicBoolean m_regenQueued = new AtomicBoolean();
	private volatile boolean m_is_run;
	private int m_accumulcate_seconds;
	private CompanionRegenerator(){
		m_is_run = false;
		m_accumulcate_seconds = 0;
	}
	
	public synchronized void do_run(){
        if (m_is_run) return;
        m_is_run = true;
        try {
            RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
        } catch (RuntimeException e) {
            m_is_run = false;
            throw e;
        }
    }
	
	@Override
	public void onMonthChanged(BaseTime time) {
		
	}

	@Override
	public void onDayChanged(BaseTime time) {
		
	}

	@Override
	public void onHourChanged(BaseTime time) {
		
	}

	@Override
	public void onMinuteChanged(BaseTime time) {
		
	}

	@Override
	public void onSecondChanged(BaseTime time) {
		if (m_is_run && ++m_accumulcate_seconds >= CompanionSettings.REGENERATION_TICK_SECONDS) {
            m_accumulcate_seconds = 0;
            if (m_regenQueued.compareAndSet(false, true)) {
                try {
                    CWorldWork.execute(this);
                } catch (RejectedExecutionException rejected) {
                    m_regenQueued.set(false);
                    LOG.log(Level.WARNING, "Companion regen admission rejected", rejected);
                }
            }
        }
		Calendar cal = time.getCalendar();
		if(cal.get(Calendar.HOUR_OF_DAY) == CompanionSettings.TRANING_INITIALIZE_HOUR && 
				cal.get(Calendar.MINUTE) == 0 &&
				cal.get(Calendar.SECOND) == 0){
			do_traning_initialized();
		}
	}

	public void do_traning_initialized(){
		CWorldWork.execute(new Runnable(){
			@Override
			public void run() {
				try{
					String message = "펫 훈련시간이 초기화 되었습니다.";
					try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_SystemMessage.nativeWriter(message),Integer.MIN_VALUE,Integer.MAX_VALUE)){
for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
						if(pc == null)
							continue;
						
						L1CompanionInstance companion = pc.get_companion();
						if(companion != null)
							companion.set_is_traning(false);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
					}
                }
					Updator.exec("update companion_instance set is_traning=?", new Handler(){
						@Override
						public void handle(PreparedStatement pstm) throws Exception {
							pstm.setInt(1, 0);
						}
					});
				//	message.clear();
				}catch(Exception e){
				//	e.printStackTrace();
				}
			}
		});
	}
    @Override
    public void run() {
        try {
            if (!m_is_run) return;
            for (L1CompanionInstance companion : L1World.getInstance().getAllCompanions()) {
                if (companion == null || companion.isDead() || companion.get_master() == null) continue;
                if (companion.getCurrentHp() >= companion.getMaxHp()) continue;
                companion.do_update_regen_tick();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Companion regen tick failed", e);
        } finally {
            m_regenQueued.set(false);
        }
    }
}
