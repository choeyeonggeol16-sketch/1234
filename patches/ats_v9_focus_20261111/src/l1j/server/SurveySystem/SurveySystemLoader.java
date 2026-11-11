package l1j.server.SurveySystem;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.ServerBasePacket;

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
	
	public ServerBasePacket registerSurvey(String message, int num, InterfaceSurvey survey, long remainMillis){
		InterfaceSurvey old = _surveys.get(num);
		if(old != null)
			return null;
		
		_surveys.put(num, survey);
		GeneralThreadPool.getInstance().schedule(new Runnable(){
			@Override
			public void run(){
				_surveys.remove(num);
			}
		}, remainMillis);
		
		return new S_Message_YN(num, 6008, message);
	}
}
