package l1j.server.server.auth.captcha.loader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.server.auth.captcha.Captcha;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.PropertyReader;
import l1j.server.server.shared.SimpleRgb;
import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.shared.command.CommandTree;

public class CaptchaLoadManager implements Command{
	private static CaptchaLoadManager _instance;
	public static CaptchaLoadManager getInstance(){
		if(_instance == null)
			_instance = new CaptchaLoadManager();
		return _instance;
	}
	public Calendar getRealTime() {
		TimeZone _tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(_tz);
		return cal;
	}
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		CaptchaLoadManager old = _instance;
		_instance = new CaptchaLoadManager();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	public static boolean CAPTCHA_IS_RUNNING;
	public static int CAPTCHA_SHOW_PROBABILITY_BYMILLIMON;
	public static int CAPTCHA_INPUT_DURATION;
	public static int CAPTCHA_RELAY_COUNT;
	public static int CAPTCHA_FAIL_COUNT;
	public static int[] CAPTCHA_TELEPORT_INFO;
	public static SimpleRgb CAPTCHA_MESSAGE_COLOR;
	public static long CAPTCHA_DELAY_MILLIS;
	private CommandTree _commands;
	private CaptchaLoadManager(){
		_commands = createCommand();
	}
	
	public void load(){
		loadConfig();
		CaptchaDataLoader.getInstance();
	}
	
	private void loadConfig(){
		PropertyReader reader = null;
		try{
			
			reader = new PropertyReader("./config/mj_captcha.properties");
			CAPTCHA_IS_RUNNING = reader.readBoolean("CaptchaIsRunning", "true");
			CAPTCHA_SHOW_PROBABILITY_BYMILLIMON = reader.readInt("ShowProbability", "200");
			CAPTCHA_INPUT_DURATION = reader.readInt("InputDurationSecond", "10");
			CAPTCHA_RELAY_COUNT = reader.readInt("MonsterKillIgnoreCount", "5");
			CAPTCHA_FAIL_COUNT = reader.readInt("TotalFailCount", "5");
			CAPTCHA_DELAY_MILLIS = reader.readInt("DelayMillisecond", "10000");
			CAPTCHA_TELEPORT_INFO = new int[]{
					reader.readInt("Assert_X", "32736"),
					reader.readInt("Assert_Y", "32799"),
					reader.readInt("Assert_mapId", "38")
			};
			CAPTCHA_MESSAGE_COLOR = SimpleRgb.fromRgb(
					reader.readInt("MessageColor_R", "255"), 
					reader.readInt("MessageColor_G", "50"), 
					reader.readInt("MessageColor_B", "50"));
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(reader != null)
				reader.dispose();			
		}
	}
	
	public boolean is_in_assert(L1PcInstance pc){
		return pc.getMapId() == CAPTCHA_TELEPORT_INFO[2];
	}

	public void do_assert(L1PcInstance pc){
		if(pc == null)
			return;
//		Calendar rightNow = Calendar.getInstance();
//		int year = rightNow.get(Calendar.YEAR);
//		int month = rightNow.get(Calendar.MONTH) + 1;
//		int date = rightNow.get(Calendar.DATE);
//		int hour = rightNow.get(Calendar.HOUR);
//		int min = rightNow.get(Calendar.MINUTE);
//		String stryyyy = "";
//		String strmmmm = "";
//		String strDate = "";
//		String strhour = "";
//		String strmin = "";
//		stryyyy = Integer.toString(year);
//		strmmmm = Integer.toString(month);
//		strDate = Integer.toString(date);
//		strhour = Integer.toString(hour);
//		strmin = Integer.toString(min);
	SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
		
		Calendar Rtime = getRealTime();
		String nowToday = sdf.format(Rtime.getTime());
		String str = "";
		
		
		str = new String("[" +nowToday+ "] 감옥 간 유저 : "+pc.getName());
		
		StringBuffer FileName = new StringBuffer("감옥이동유저목록/오토방지실패.txt");
		PrintWriter out = null;
		
		try {
			out = new PrintWriter(new FileWriter(FileName.toString(), true));
			out.println(str);
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SC_Ability.send(pc, 3, false);
		SC_SystemMessage.send(pc, "해당 맵에서는 라이트가 실행 되지 않습니다.");
		pc.do_simple_teleport(CAPTCHA_TELEPORT_INFO[0], CAPTCHA_TELEPORT_INFO[1], CAPTCHA_TELEPORT_INFO[2]);
	}
	
	public boolean do_auth_captcha(L1PcInstance pc, String text){
		Captcha captcha = pc.get_captcha();
		if(captcha == null)
			captcha = pc.create_captcha();
		
		if(!captcha.is_keep_captcha())
			return false;
		
		try{
			Integer answer = Integer.parseInt(text);
			if(answer >= 0 && answer <= 9){
				captcha.auth_captcha(pc, answer);
				return true;
			}
		}catch(Exception e){}
		return false;
	}
	
	private CommandTree createCommand(){
		return new CommandTree(".캡챠", "캡챠 관련 명령어를 실행합니다.", null)
			.add_command(createReloadCommand())
			.add_command(createUserInitializeCommand())
			.add_command(createAllInitializeCommand());
	}
	
	private CommandTree createUserInitializeCommand(){
		return new CommandTree("케릭터초기화", "선택된 케릭터의 캡차 정보를 초기화합니다.", new String[]{"케릭터이름"}){
			@Override
			protected void to_handle_command(CommandArgs args) throws Exception{
				String character_name = args.nextString();
				L1PcInstance pc = L1World.getInstance().getPlayer(character_name);
				if(pc == null){
					args.notify("케릭터 이름 %s을(를) 찾을 수 없습니다.");
					return;
				}
				
				Captcha captcha = pc.get_captcha();
				if(captcha != null){
					captcha
					.set_c_data(null)
					.set_fail_count(0)
					.set_relay_count(0)
					.set_is_answer(true);
				}
				args.notify(String.format("%s의 캡챠 정보를 초기화 했습니다.", character_name));
			}
		};
	}
	
	private CommandTree createAllInitializeCommand(){
		return new CommandTree("전체초기화", "전체 유저의 캡차 정보를 초기화합니다.", null){
			@Override
			protected void to_handle_command(CommandArgs args) throws Exception{
				L1World.getInstance().getAllPlayerStream()
				.filter((L1PcInstance pc) ->{
					return pc != null;
				})
				.forEach((L1PcInstance pc) ->{
					Captcha captcha = pc.get_captcha();
					if(captcha != null){
						captcha
						.set_c_data(null)
						.set_fail_count(0)
						.set_relay_count(0)
						.set_is_answer(true);
					}
				});
			}
		};
	}
	
	private CommandTree createReloadCommand(){
		return new CommandTree("리로드", "리로드 관련 명령을 수행합니다.", null)
		.add_command(new CommandTree("정보", "", null){
			@Override
			protected void to_handle_command(CommandArgs args) throws Exception{
				CaptchaDataLoader.reload();
				args.notify("tb_captcha 테이블을 리로드 하였습니다.");
			}
		})
		.add_command(new CommandTree("컨피그", "", null){
			@Override
			protected void to_handle_command(CommandArgs args) throws Exception{
				loadConfig();
				args.notify("mj_captcha.properties를 리로드 하였습니다.");
			}
		});
	}
	
	public void dispose(){
	}

	@Override
	public void execute(CommandArgs args) {
		_commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
	}
}
