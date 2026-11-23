package l1j.server.server;





import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;


//GM 전용
public class NeigeGM{

	boolean spawnTF = false;  


	private static NeigeGM _instance; 

	private NeigeGM() {
	}

	public static NeigeGM getInstance() {
		if (_instance == null) {
			_instance = new NeigeGM(); 
		}
		return _instance;
	}



	public boolean gm(L1PcInstance pc, String s) {

		String s2 = s;
		int a = 0;
		switch (s2) {
		case "neigma00":
			NeigeGMEvent.settingpage(pc);
			return true;
		case "neigma01":
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma02":
			if(Config.흑단무빙 != 1){
				Config.흑단무빙 = 1;
			}else{Config.흑단무빙 = 0;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma03":
			if(Config.Neige_Count_Pass != 2){
				Config.Neige_Count_Pass = 2;
			}else{Config.Neige_Count_Pass = 0;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma04":
			if(Config.펑몹이벤트 != 2){
				Config.펑몹이벤트 = 2;
			}else{Config.펑몹이벤트 = 1;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma05":
			if(Config.Neige_Reward != 2){
				Config.Neige_Reward = 2;
			}else{Config.Neige_Reward = 1;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma06":
			if(Config.Neige_Myth_Skill != 2){
				Config.Neige_Myth_Skill = 2;
			}else{Config.Neige_Myth_Skill = 1;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma07":
			if(Config.myth_possible != 0){
				Config.myth_possible = 0;
			}else{Config.myth_possible = 1;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma08":
			if(Config.sole_possible != 0){
				Config.sole_possible = 0;
			}else{Config.sole_possible = 1;}
			NeigeGMEvent.settingpage(pc);
			return false;
		case "neigma49":
			NeigeGMEvent.total(pc);
			return false;
		case "neigma50":
			if(Config.할파스변수 > 1){
				Config.할파스변수 -= 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma51":
			if(Config.할파스변수 < 10){
				Config.할파스변수 += 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma52":
			if(Config.카드변수 > 1){
				Config.카드변수 -= 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma53":
			if(Config.카드변수 < 10){
				Config.카드변수 += 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma54":
			if(Config.인형변수 > 1){
				Config.인형변수 -= 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma55":
			if(Config.인형변수 < 10){
				Config.인형변수 += 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma56":
			if(Config.오림변수 > 1){
				Config.오림변수 -= 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma57":
			if(Config.오림변수 < 10){
				Config.오림변수 += 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma58":
			if(Config.수호성변수 > 1){
				Config.수호성변수 -= 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		case "neigma59":
			if(Config.수호성변수 < 10){
				Config.수호성변수 += 1;
			}
			NeigeGMEvent.eventpage(pc);
			return false;
		default: {

			return false;
		}

		}

	}


}
