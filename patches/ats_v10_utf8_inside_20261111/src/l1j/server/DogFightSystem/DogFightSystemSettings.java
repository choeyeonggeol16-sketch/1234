package l1j.server.DogFightSystem;

import l1j.server.Template.JsonUtil;

public class DogFightSystemSettings {
	public static DogFightSystem DogfightInfo = new DogFightSystem();
	public static class DogFightSystem{
		public boolean isRun;
		public double Rate;
		public boolean Auto_Controll;
		public int Auto_Controll_Plus_Skill_Percent;
		public int Auto_Controll_Minus_Skill_Percent;
		public int Left_X;
		public int Left_Y;
		public int Left_H;
		public int Right_X;
		public int Right_Y;
		public int Right_H;
		public int MapId;
		public int Ticket_Shop_Npcid;
		public int DogFight_Helper_Npcid;
		public int DogFight_Chart_Npcid;
		public int DogFight_Time;
		public DogFightSystem() {
			isRun									= false;
			Rate									= 1.95;
			Left_X									= 32767;
			Left_Y									= 32767;
			Left_H									= 3;
			Right_X									= 32767;
			Right_Y									= 32767;
			Right_H									= 6;
			MapId									= 4;
			Auto_Controll							= false;
			Auto_Controll_Plus_Skill_Percent		= 10;
			Auto_Controll_Minus_Skill_Percent 		= -10;
			Ticket_Shop_Npcid						= 0;
			DogFight_Helper_Npcid					= 0;
			DogFight_Chart_Npcid					= 0;
			DogFight_Time							= 60;
		}
	}

	public static void DogfightInfoLoad() {
		try {
			DogfightInfo = JsonUtil.fromFile("./config/DogFightSystem.json", DogFightSystem.class);
		} catch (Exception e) {
			System.out.println("Config->DogFightSystem.json 에서 에러가 발생했습니다.");
			e.printStackTrace();
			throw new Error("Failed to Load ./config/DogFightSystem.json File.");
		}
	}
}
