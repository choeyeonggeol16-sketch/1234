package l1j.server.server.payment;

import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.utils.GameUtils;

public class PaymentGmHandler implements Command{
	public static void do_execute(CommandArgs args){
		new PaymentGmHandler().execute(args);
	}
	
	private PaymentGmHandler(){}
	@Override
	public void execute(CommandArgs args) {
		try{
			String name = args.nextString();
			int adena = args.nextInt();
			String character_name = args.nextString();
			if(GameUtils.isNullOrEmpty(name)){
				args.notify("입금자명은 공백일 수 없습니다.");
				throw new Exception();
			}
			if(adena <= 0){
				args.notify("아데나는 최소 1원 이상 입력 바랍니다.");
				throw new Exception();
			}
			if(GameUtils.isNullOrEmpty(character_name)){
				args.notify("캐릭터명을 입력해주세요.");
			}
			PaymentInfo pInfo = PaymentInfo.newInstance(name, adena, character_name);
			args.notify(String.format("[발급됨]코드 : %s, 입금자명 : %s, 아데나 : %s, 캐릭명 : %s", pInfo.get_code(), pInfo.get_name(), pInfo.get_adena(), pInfo.get_issue_character_name()));
		}catch(Exception e){
			args.notify(".코드 [입금자명] [아데나] [캐릭명]");
		}
	}
	
}
