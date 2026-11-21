package l1j.server.server.model.character.combat.arena.dogfight;

import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGameInfo;
import l1j.server.server.model.character.combat.arena.dogfight.skills.DogFightSkill;
import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;

public class DogFightCommandExecutor implements Command{
	public static void do_execute(CommandArgs args){
		new DogFightCommandExecutor().execute(args);
	}
	
	private DogFightCommandExecutor(){}
	
	@Override
	public void execute(CommandArgs args) {
		try{
			switch(args.nextInt()){
			case 1:
				do_start_command(args);
				break;
			case 2:
				do_cancel_command(args);
				break;
			case 3:
				do_reload_command(args);
				break;
			default:
				throw new Exception();
			}
		}catch(Exception e){
			args.notify(".투견 [1.시작][2.취소][3.리로드]");
		}
	}
	
	private void do_start_command(CommandArgs args){
		try{
			int match_count = args.nextInt();
			if(DogFightLoader.getInstance().is_reservation_game()){
				args.notify("이미 진행중인 게임이 있습니다.");
				return;
			}
			DogFightLoader.getInstance().do_reservation_game(DogFightSettings.IS_ON_GAMES, match_count);
			args.notify("게임을 시작합니다.");
		}catch(Exception e){
			args.notify(".투견 1(예약) [연속게임횟수(-1 무한)]");
		}
	}
	private void do_cancel_command(CommandArgs args){
		DogFightLoader.getInstance().close_current_game();
		args.notify("경기를 취소시켰습니다.");
		args.notify("[주의]안전한 저장을 위해 이번 경기까지만 하고 취소가 됩니다.");
		args.notify("[주의]다시 시작할때는 이번 경기가 끝나고 시작해주세요.");
	}
	
	
	private void do_reload_command(CommandArgs args){
		try{
			switch(args.nextInt()){
			case 1:
				DogFightSettings.do_load();
				args.notify("투견 세팅을 리로드 했습니다.");
				break;
			case 2:
				DogFightGameInfo.do_load();
				args.notify("투견 게임정보를 리로드 했습니다.");
				break;
			case 3:
				DogFightSkill.do_load();
				args.notify("투견 스킬정보를 리로드 했습니다.");
				break;
			}
		}catch(Exception e){
			args.notify(".투견 3(리로드) [1.셋팅][2.게임정보][3.스킬]");
		}
	}

}
