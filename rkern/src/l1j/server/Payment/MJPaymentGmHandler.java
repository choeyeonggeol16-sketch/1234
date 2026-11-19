package l1j.server.Payment;

import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.server.utils.MJCommons;

public class MJPaymentGmHandler implements MJCommand{
	public static void do_execute(MJCommandArgs args){
		new MJPaymentGmHandler().execute(args);
	}
	
	private MJPaymentGmHandler(){}
	@Override
	public void execute(MJCommandArgs args) {
		try{
			String name = args.nextString();
			int adena = args.nextInt();
			String character_name = args.nextString();
			if(MJCommons.isNullOrEmpty(name)){
				args.notify("입금자명은 공백일 수 없습니다.");
				throw new Exception();
			}
			if(adena <= 0){
				args.notify("아데나는 최소 1원 이상 입력 바랍니다.");
				throw new Exception();
			}
			if(MJCommons.isNullOrEmpty(character_name)){
				args.notify("캐릭터명을 입력해주세요.");
			}
			MJPaymentInfo pInfo = MJPaymentInfo.newInstance(name, adena, character_name);
			args.notify(String.format("[발급됨]코드 : %s, 입금자명 : %s, 아데나 : %s, 캐릭명 : %s", pInfo.get_code(), pInfo.get_name(), pInfo.get_adena(), pInfo.get_issue_character_name()));
		}catch(Exception e){
			args.notify(".코드 [입금자명] [아데나] [캐릭명]");
		}
	}
	
}
