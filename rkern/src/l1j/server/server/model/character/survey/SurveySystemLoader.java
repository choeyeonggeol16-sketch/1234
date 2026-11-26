package l1j.server.server.model.character.survey;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.thread.CServerTimer;


public class SurveySystemLoader {
	private static final SurveySystemLoader INSTANCE = new SurveySystemLoader();
	public static SurveySystemLoader getInstance(){
		return INSTANCE;
	}
	
	private final ConcurrentHashMap<Integer, InterfaceSurvey> _surveys;
	private SurveySystemLoader(){
		_surveys = new ConcurrentHashMap<Integer, InterfaceSurvey>(256);
	}
	
	public boolean isSurvey(int num){
		return _surveys.containsKey(num);
	}
	
	public boolean submitSurvey(L1PcInstance pc, int num, boolean isYes){
		InterfaceSurvey survey = _surveys.get(num);
		if(survey != null){
			survey.survey(pc, num, isYes);
			return true;
		}
		return false;
	}
	
	public boolean registerSurvey(L1PcInstance receiver, String message, int num, InterfaceSurvey survey, long remainMillis){
		if (_surveys.putIfAbsent(num, survey) != null) return false;
		final Runnable expire = () -> _surveys.remove(num, survey);
		try {
			if (CServerTimer.onceSignal(expire, remainMillis, expire) == null) {
				expire.run();
				return false;
			}
		} catch (RuntimeException | Error failure) {
			expire.run();
			throw failure;
		}
		SC_MessageYN.send(receiver, num, 622, message);
        return true;
	}
}
