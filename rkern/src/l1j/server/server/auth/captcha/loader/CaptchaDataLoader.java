package l1j.server.server.auth.captcha.loader;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.server.auth.captcha.CaptchaData;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CaptchaDataLoader{
	private static CaptchaDataLoader _instance;
	public static CaptchaDataLoader getInstance(){
		if(_instance == null)
			_instance = new CaptchaDataLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		CaptchaDataLoader old = _instance;
		_instance = new CaptchaDataLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	
	private ArrayList<CaptchaData> _captcha_datas;
	private CaptchaDataLoader(){
		_captcha_datas = load();
		
	}
	
	private ArrayList<CaptchaData> load(){
		ArrayList<CaptchaData> captcha_datas = new ArrayList<CaptchaData>(300);
		Selector.exec("select * from tb_captcha", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					captcha_datas.add(CaptchaData.newInstance(rs));
			}
		});
		
		return captcha_datas;
	}
	
	public CaptchaData to_rand_captcha(){
		return _captcha_datas.get(GameRandom.next(_captcha_datas.size()));
	}

	public void dispose(){
		if(_captcha_datas != null){
			for(CaptchaData c_data : _captcha_datas)
				c_data.dispose();
			_captcha_datas.clear();
			_captcha_datas = null;
		}
	}
}
