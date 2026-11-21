package l1j.server.server.model.character.survey;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;


public class SurveySystemLoader {
	private static SurveySystemLoader _instance;
	public static SurveySystemLoader getInstance(){
		if(_instance == null)
			_instance = new SurveySystemLoader();
		return _instance;
	}
	
	private ConcurrentHashMap<Integer, InterfaceSurvey> _surveys;
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
		InterfaceSurvey old = _surveys.get(num);
		if(old != null)
			return false;
		
		_surveys.put(num, survey);
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				_surveys.remove(num);
			}
		}, remainMillis);
		SC_MessageYN.send(receiver, num, 622, message);
        return true;
	}
}
