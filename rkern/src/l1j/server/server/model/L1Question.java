package l1j.server.server.model;

import l1j.server.server.model.world.CWorldWork;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;

public class L1Question implements Runnable{
	private static final Logger LOG = Logger.getLogger(L1Question.class.getName());

	private static volatile L1Question _instance;
	public static String maintext;
	public static int good;
	public static int bad;
	public static boolean mainstart;

	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_PREPARE = 1;
	public static final int EXECUTE_STATUS_PROGRESS = 3;
	public static final int EXECUTE_STATUS_FINALIZE = 4;
	
	private int _executeStatus = EXECUTE_STATUS_NONE;

	public static synchronized L1Question getInstance(String text) {
		if (_instance != null) return _instance;
		final L1Question survey = new L1Question(text);
		_instance = survey;
		try {
			CWorldWork.execute(survey);
		} catch (java.util.concurrent.RejectedExecutionException rejected) {
			survey.resetRejected(rejected);
		}
		return _instance;
	}

	private L1Question(String text) {
		good = 0;
		bad = 0;
		maintext = text;
	}

	private void resetRejected(RuntimeException rejected) {
		synchronized (L1Question.class) {
			if (_instance == this) {
				_instance = null;
				mainstart = false;
				maintext = "";
			}
		}
		LOG.warning("Survey job not admitted: " + rejected);
	}

	private void scheduleNext(long delayMs) {
		if (CWorldWork.schedule(this, delayMs) == null) {
			resetRejected(new java.util.concurrent.RejectedExecutionException("survey timer queue full"));
		}
	}

	@Override
	public void run(){
		try{
			switch(_executeStatus)
			{
				case EXECUTE_STATUS_NONE:
				{
					mainstart = true;
					CWorld.getInstance().broadcastServerMessage(" \\fY잠시 후 설문조사가 시작됩니다. (제한시간 30초)");
					CWorld.getInstance().broadcastServerMessage(" YES = 찬성, NO = 반대, 그외 무효~!");

					_executeStatus = EXECUTE_STATUS_PREPARE;
					scheduleNext(3000L);
				}
				break;

				case EXECUTE_STATUS_PREPARE:
				{
					SC_MessageYN.broadcast(622, maintext);
					
					_executeStatus = EXECUTE_STATUS_PROGRESS;					
					scheduleNext(30000L);
				}
				break;

				case EXECUTE_STATUS_PROGRESS:
				{
					CWorld.getInstance().broadcastServerMessage(" 잠시 후 설문조사 결과가 발표됩니다.");

					_executeStatus = EXECUTE_STATUS_FINALIZE;
					scheduleNext(3000L);
				}
				break;

				case EXECUTE_STATUS_FINALIZE:
				{
					CWorld.getInstance().broadcastServerMessage(" \\fW[결과] 찬성 : " + good + "표, 반대 : " + bad + "표");
					_instance = null;		
					mainstart = false;
					maintext = "";			
				}
				break;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
