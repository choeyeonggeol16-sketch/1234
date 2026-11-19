package l1j.server.MJCaptchaSystem.Loader;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.MJCaptchaSystem.MJCaptchaData;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCaptchaDataLoader{
	private static MJCaptchaDataLoader _instance;
	public static MJCaptchaDataLoader getInstance(){
		if(_instance == null)
			_instance = new MJCaptchaDataLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJCaptchaDataLoader old = _instance;
		_instance = new MJCaptchaDataLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	
	private ArrayList<MJCaptchaData> _captcha_datas;
	private MJCaptchaDataLoader(){
		_captcha_datas = load();
		
	}
	
	private ArrayList<MJCaptchaData> load(){
		ArrayList<MJCaptchaData> captcha_datas = new ArrayList<MJCaptchaData>(300);
		Selector.exec("select * from tb_captcha", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					captcha_datas.add(MJCaptchaData.newInstance(rs));
			}
		});
		
		return captcha_datas;
	}
	
	public MJCaptchaData to_rand_captcha(){
		return _captcha_datas.get(MJRnd.next(_captcha_datas.size()));
	}

	public void dispose(){
		if(_captcha_datas != null){
			for(MJCaptchaData c_data : _captcha_datas)
				c_data.dispose();
			_captcha_datas.clear();
			_captcha_datas = null;
		}
	}
}
