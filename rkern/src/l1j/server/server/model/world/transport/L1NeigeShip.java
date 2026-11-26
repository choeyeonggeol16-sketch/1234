package l1j.server.server.model.world.transport;


import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTimeClock;


//해적섬 배 스케쥴러 Neige
public class L1NeigeShip {
	
	public static void join(L1PcInstance pc) {
		int servertime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
		int nowtime = servertime % 86400;
		if(!pc.getInventory().HaveItemByClassId(40303, 1)){return;}
		if (nowtime >= -62370 && nowtime < -60870 //13시 8분 ~ 13시 33분 배
				|| nowtime >= -56982 && nowtime < -55482
				|| nowtime >= -51576 && nowtime < -50076
				|| nowtime >= -46176 && nowtime < -44676
				|| nowtime >= -40776 && nowtime < -39276
				|| nowtime >= -35376 && nowtime < -33876
				|| nowtime >= -29976 && nowtime < -28476
				|| nowtime >= -24576 && nowtime < -23076
				|| nowtime >= -19176 && nowtime < -17676
				|| nowtime >= -13776 && nowtime < -12276
				|| nowtime >= -8376 && nowtime < -6876
				|| nowtime >= -2976 && nowtime < -1476
				|| nowtime >= -83970 && nowtime < -82470
				|| nowtime >= -78570 && nowtime < -77070
				|| nowtime >= -73170 && nowtime < -71670
				|| nowtime >= -67770 && nowtime < -66270
				
				) {
			pc.start_teleport(32731, 32797, 447, 4, 169, false, false);
		}
		 }
	public static void out(L1PcInstance pc) {
		int servertime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
		int nowtime = servertime % 86400;	
		if (nowtime >= -62370 && nowtime < -60870 //13시 8분 ~ 13시 33분 배
				|| nowtime >= -56982 && nowtime < -55482
				|| nowtime >= -51576 && nowtime < -50076
				|| nowtime >= -46176 && nowtime < -44676
				|| nowtime >= -40776 && nowtime < -39276
				|| nowtime >= -35376 && nowtime < -33876
				|| nowtime >= -29976 && nowtime < -28476
				|| nowtime >= -24576 && nowtime < -23076
				|| nowtime >= -19176 && nowtime < -17676
				|| nowtime >= -13776 && nowtime < -12276
				|| nowtime >= -8376 && nowtime < -6876
				|| nowtime >= -2976 && nowtime < -1476
				|| nowtime >= -83970 && nowtime < -82470
				|| nowtime >= -78570 && nowtime < -77070
				|| nowtime >= -73170 && nowtime < -71670
				|| nowtime >= -67770 && nowtime < -66270
				) {
			pc.start_teleport(32751, 32873, 445, 7, 169, false, false);
		}
	}
}
