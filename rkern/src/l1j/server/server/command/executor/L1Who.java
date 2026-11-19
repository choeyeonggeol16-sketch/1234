package l1j.server.server.command.executor;

import l1j.server.server.model.network.core.sc.packet.SC_WhoAmount;
import java.util.Collection;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

public class L1Who implements L1CommandExecutor {

	private L1Who() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Who();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			int CalcUser = L1UserCalc.getClacUser();
			Collection<L1PcInstance> players = L1World.getInstance().getAllPlayers();
			int robotcount = 0; // 무인
			int playercount = 0; 
			int AutoShopUser = 0;
			for (L1PcInstance each : players) {
				if(each.noPlayerCK || each.noPlayerck2)
					robotcount++;
				else if (each.isPrivateShop() && each.getNetConnection() == null) {
					AutoShopUser++;
				} else {
					playercount++;
				}
			}
			String amount = String.valueOf(playercount);
			SC_WhoAmount.send(pc, amount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aD=========== 접속중인 플레이어 수 ===========");
			SC_ChatPacket.sendDirect(pc, pc, "\\aL[로봇] : " + robotcount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aH[유저] 총인원수: " + playercount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aJ[무인상점] : " + AutoShopUser);
			SC_ChatPacket.sendDirect(pc, pc, "\\aE[뻥튀기] : " + CalcUser);

			
			// 온라인의 플레이어 리스트를 표시
			if (arg.equalsIgnoreCase("전체")) {
				StringBuffer gmList = new StringBuffer();
				StringBuffer playList = new StringBuffer();
				StringBuffer shopList = new StringBuffer();
				StringBuffer robotList = new StringBuffer();

				int countGM = 0, countPlayer = 0, countShop = 0, countRobot = 0;

				for (L1PcInstance each : players) {
					if (each.isGm()) {
						gmList.append(each.getName() + ", ");
						countGM++;
						continue;
					}
					if (each.noPlayerCK || each.noPlayerck2) {
						robotList.append(each.getName() + ", ");
						countRobot++;
						continue;
					}
					if (!each.isPrivateShop()) {
						playList.append(each.getName() + ", ");
						countPlayer++;
						continue;
					}
					if (each.isPrivateShop()) {
						shopList.append(each.getName() + ", ");
						countShop++;
					}
				}
				if (gmList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 운영자 (" + countGM + "명)");
					SC_ChatPacket.sendDirect(pc, pc, gmList.toString());
				}

				if (playList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 플레이어 (" + countPlayer + "명)");
					SC_ChatPacket.sendDirect(pc, pc, playList.toString());
				}
				if (robotList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 로봇유저 (" + countRobot + "명)");
					SC_ChatPacket.sendDirect(pc, pc, robotList.toString());
				}
				if (shopList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 개인상점 (" + countShop + "명)");
					SC_ChatPacket.sendDirect(pc, pc, shopList.toString());
				}
			}
			players = null;
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".누구 [전체] 라고 입력해 주세요. ");
		}
	}
}
