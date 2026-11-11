package l1j.server.server.clientpackets;
import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.WarSystem.War;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SystemUtil;


public class C_War extends ClientBasePacket {

	private static final String C_WAR = "[C] C_War";

	public C_War(byte abyte0[], GameClient clientthread) throws Exception {		
		super(abyte0);
		try {
		int type = readC();
		String s = readS();
		L1PcInstance player = clientthread.getActiveChar();
		if ( player == null)
			return;
		
		if(type == 0){
			if(s.equalsIgnoreCase("붉은 기사단")){
				int castleId = L1CastleLocation.getCastleIdByArea(player);
				if(castleId == 0){
					player.sendPackets("공성전이 진행중이지 않습니다.");
					return;
				}
				CastleWar war = CastleWarBusiness.getInstance().get(castleId);
				if(war == null){
					player.sendPackets("공성전이 진행중이지 않습니다.");
					return;
				}
				CastleWarBusiness.getInstance().proclaim(player, castleId);
				return;
			}
			
			CastleWar war = CastleWarBusiness.getInstance().findWar(s);
			L1Clan c = L1World.getInstance().findClan(s);
			if(war != null){
				CastleWarBusiness.getInstance().proclaim(player, war.getCastleId());
			}else if(c != null){				
				CastleWarBusiness.getInstance().proclaim(player, c.getCastleId());
			}else{
				player.sendPackets(new S_SystemMessage(String.format("대상 혈맹을 찾을 수 없습니다. [%s]", s)));
			}
			return;
		}
		
		String playerName = player.getName();
		String clanName = player.getClanname();
		int clanId = player.getClanid();
		
		if (!player.isCrown()) { // 군주 이외
			player.sendPackets(new S_ServerMessage(478));// \f1프린스와 프린세스만 전쟁을 포고할 수 있습니다.
			return;
		}
		if (clanId == 0) { // 크란미소속
			player.sendPackets(new S_ServerMessage(272)); // \f1전쟁하기 위해서는 우선 혈맹을 창설하지 않으면 안됩니다.
			return;
		}
		
		L1Clan clan = L1World.getInstance().getClan(clanId);
		if (clan == null) {
			S_SystemMessage sm = new S_SystemMessage("대상 혈맹이 발견되지 않았습니다.");
			player.sendPackets(sm);
			sm = null;
			return;
		}

		if (player.getId() != clan.getLeaderId()) { // 혈맹주
			player.sendPackets(new S_ServerMessage(478)); // \f1프린스와 프린세스만 전쟁을 포고할 수 있습니다.
			return;
		}

		if (clanName.toLowerCase().equals(s.toLowerCase())) { // 자크란을 지정
			return;
		}

		L1Clan enemyClan = null;
		String enemyClanName = null;
		for (L1Clan checkClan : L1World.getInstance().getAllClans()) { // 크란명을 체크
			if (checkClan.getClanName().toLowerCase().equals(s.toLowerCase())) {
				enemyClan = checkClan;
				enemyClanName = checkClan.getClanName();
				break;
			}
		}
		if (enemyClan == null) {
			S_SystemMessage sm = new S_SystemMessage("대상 혈맹이 발견되지 않았습니다.");
			player.sendPackets(sm);
			sm = null;
			return;
		}

		boolean inWar = false;
		War war = clan.getCurrentWar();
		if(war != null){
			if (type == 0) { // 선전포고
				player.sendPackets(new S_ServerMessage(234)); // \f1당신의 혈맹은 벌써 전쟁중입니다.
				return;
			}
			inWar = true;
		}
		
		if (!inWar && (type == 2 || type == 3)) { // 자크란이 전쟁중 이외로, 항복 또는 종결
			return;
		}

		if (clan.getCastleId() != 0) { // 자크란이 성주
			if (type == 0) { // 선전포고
				player.sendPackets(new S_ServerMessage(474)); // 당신은 벌써 성을 소유하고 있으므로, 다른 시로를 잡을 수 없습니다.
				return;
			} else if (type == 2 || type == 3) { // 항복, 종결
				return;
			}
		}

		// 상대 크란이 성주는 아니고, 자캐릭터가 Lv15 이하
		if (enemyClan.getCastleId() == 0 && player.getLevel() < 52) {
			player.sendPackets(new S_ServerMessage(232)); // \f1레벨 15 이하의 군주는 선전포고할 수 없습니다.
			return;
		}

		//엔피씨 클릭해서 선포 할시
		if (enemyClan.getCastleId() != 0 && player.getLevel() <  Config.ALT_WAR_LEVEL) {
			player.sendPackets(new S_SystemMessage("\\aA공성: 군주/공주 레벨\\aG[" + Config.ALT_WAR_LEVEL + "]\\aA 부터 선포할 수 있습니다."));
			return;
		}

		War enemyWar = enemyClan.getCurrentWar();
		if (enemyClan.getCastleId() != 0) { // 상대 크란이 성주
		} else { // 상대 크란이 성주는 아니다
			boolean enemyInWar = false;
			if(enemyClan.getCurrentWar() != null){
				if (type == 0) { // 선전포고
					player.sendPackets(new S_ServerMessage(236,enemyClanName)); // %0혈맹이 당신의 혈맹과의 전쟁을 거절했습니다.
					return;
				} else if (type == 2 || type == 3) { // 항복 또는 종결
					if (war.getId() != enemyWar.getId()) { // 자크란과 상대 크란이 다른 전쟁
						return;
					}
				}
				enemyInWar = true;
			}

			if (!enemyInWar && (type == 2 || type == 3)) { // 상대 크란이 전쟁중 이외로, 항복 또는 종결
				return;
			}

			// 공성전이 아닌 경우, 상대의 혈맹주의 승인이 필요
			L1PcInstance enemyLeader = L1World.getInstance().getPlayer(enemyClan.getLeaderName());

			if (enemyLeader == null) { // 상대의 혈맹주가 발견되지 않았다
				player.sendPackets(new S_ServerMessage(218, enemyClanName)); // \f1%0 혈맹의 군주는 현재 월드에 없습니다.
				return;
			}

			if (type == 0) { // 선전포고
				enemyLeader.setTempID(player.getId()); // 상대의 오브젝트 ID를 보존해 둔다
				enemyLeader.sendPackets(new S_Message_YN(217, clanName,playerName)); // %0혈맹의%1가 당신의 혈맹과의 전쟁을 바라고 있습니다. 전쟁에 응합니까? (Y/N)
			} else if (type == 2) { // 항복
				enemyLeader.setTempID(player.getId()); // 상대의 오브젝트 ID를 보존해 둔다
				enemyLeader.sendPackets(new S_Message_YN(221, clanName)); // %0혈맹이 항복을 바라고 있습니다. 받아들입니까? (Y/N)
			} else if (type == 3) { // 종결
				enemyLeader.setTempID(player.getId()); // 상대의 오브젝트 ID를 보존해 둔다
				enemyLeader.sendPackets(new S_Message_YN(222, clanName)); // %0혈맹이 전쟁의 종결을 바라고 있습니다. 종결합니까? (Y/N)
			}
		}
		}catch(Exception e) {
			
		}finally{
			clear();
		}
	}

	@Override
	public String getType() {
		return C_WAR;
	}

}
