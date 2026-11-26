package l1j.server.server.model.character.combat.statistics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.server.BuilderCmdHandler;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ShowCmd;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.storage.CacheD;
import l1j.server.server.utils.BytesOutputStream;

public class Kda {
	private static final int KDAM_INIT 			= 0;
	private static final int KDAM_WP_INC 		= 1;
	private static Random _random = new Random(System.nanoTime());
	private static final String[] _kdaMessages = new String[]{
		"당신의 킬데스가 초기화 되었습니다.",
	//	new RPacketBox(RPacketBox.GREEN_MESSAGE, "PK에서 승리하여 혈맹 포인트가 1점이 상승 했습니다."),
		"현재 보호모드로 인해, 페널티가 적용 되지 않습니다. 안심하시고 사냥하셔도 됩니다.",
	};
	
	public int 		objid;
	public String 	name;
	public int 		kill;
	public int 		death;
	public long		lastDeathMs;
	public long		lastKillMs;
	public int		lastKillId;
	public boolean	isBot;
	public boolean	isChartView;
	public Calendar getRealTime() {
		TimeZone _tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(_tz);
		return cal;
	}
	public synchronized void onInit(L1PcInstance owner){
        if (CacheD.getInstance().hasPendingOwnerApply(objid))
            throw new IllegalStateException("K/D row has a pending economy replay");
		kill 	= 0;
		death 	= 0;
		SC_SystemMessage.send(owner, _kdaMessages[KDAM_INIT]);
	}


	public synchronized void 올삭제(L1PcInstance owner){
        if (CacheD.getInstance().hasPendingOwnerApply(objid))
            throw new IllegalStateException("K/D row has a pending economy replay");
		kill 	= 0;
		death 	= 0;
		
		//SC_SystemMessage.send(owner, _kdaMessages[KDAM_INIT]);
	}
	public synchronized void 킬삭제1(L1PcInstance owner){
        if (CacheD.getInstance().hasPendingOwnerApply(objid))
            throw new IllegalStateException("K/D row has a pending economy replay");
		kill 	= 0;
		
		//SC_SystemMessage.send(owner, _kdaMessages[KDAM_INIT]);
	}
	public synchronized void 데스삭제1(L1PcInstance owner){
        if (CacheD.getInstance().hasPendingOwnerApply(objid))
            throw new IllegalStateException("K/D row has a pending economy replay");
		death 	= 0;
		
		//SC_SystemMessage.send(owner, _kdaMessages[KDAM_INIT]);
	}
	public void onProtection(L1PcInstance pc){
		SC_SystemMessage.send(pc, _kdaMessages[KDAM_INIT]);
	}
	
	public void onKill(L1PcInstance killer, L1PcInstance victim){
		if(killer.getZoneType() != 0)
			return;
		
//		if (killer.isGm() || victim.isGm()) {
//			return;
//		}
		
		if(BuilderCmdHandler.IS_PROTECTION){
			onProtection(victim);
			return;
		}
		
		if (Config.STANDBY_SERVER) {
			
			return;
		}

		if(killer.getClanid() != 0){
			L1Clan clan = killer.getClan();
			if(clan != null){
				final var session = killer.getNetConnection();
				ClanTable.changeWarPoint(clan, 1, committed -> {
					if (committed) CIOThreadCallback.getInstance().completeSession(session,
						() -> SC_SystemMessage.send(killer, _kdaMessages[KDAM_WP_INC]));
				});
			}
		}

		SC_PacketBox.send(killer, SC_PacketBox.배틀샷, victim.getId());
		Kda tKda = victim.getKDA();
		Kda oKda = killer.getKDA();
		if(oKda == null || tKda == null)
			return;

		long cur = System.currentTimeMillis();
		try{
			long diff = cur - tKda.lastDeathMs;
			if(diff < KdaLoadManager.KDA_DEATH_DELAY_MS){
//				killer.sendPackets(new SC_SystemMessage(String.format("[%s] 케릭터는 짧은 시간에 너무 많이 사망하여 포인트를 지급하지 않습니다.", victim.getName())));
				return;
			}else if(tKda.objid == oKda.lastKillId && diff < KdaLoadManager.KDA_KILL_DUPL_DELAY_MS){
//				killer.sendPackets(new SC_SystemMessage(String.format("[%s] 케릭터를 짧은 시간에 너무 많이 죽여 포인트를 지급하지 않습니다.", victim.getName())));
				return;
			}
//			if(killer.getClan() == null) {
//				killer.sendPackets(new SC_SystemMessage("\\f3본인은 무혈맹이므로 킬은 무효화됩니다."));
//				return;
//			}
//			if(killer.getClan().getClanId() == Config.신규혈맹클랜) {
//				killer.sendPackets(new SC_SystemMessage("\\f3본인은 중립혈맹이므로 킬은 무효화됩니다."));
//				return;
//			}
//			if(victim.getClan() == null) {
//				killer.sendPackets(new SC_SystemMessage("\\aA["+victim.getName()+"]\\f3님은 무혈맹이므로 킬은 무효화됩니다."));
//				return;
//			}
//				
//			if(victim.getClan().getClanId() == Config.신규혈맹클랜) {
//				killer.sendPackets(new SC_SystemMessage("\\aA["+victim.getName()+"]\\f3님은 중립혈맹이므로 킬은 무효화됩니다."));
//				return;
//			}	
			PrideLoadManager.getInstance().on_kill(victim);
			
			synchronized (tKda) { tKda.death++; }
			synchronized (oKda) { oKda.kill++; }
			KdaLoader.getInstance().store();
			KdaLoadManager.KDA_TOTAL_PVP++;
			if(killer.getMapId() == 170811 || killer.getMapId() == 170911){
				notifyKillForGotten(killer, victim);
				setLawful(killer, victim);
			}else{
				
				notifyKill(killer, victim);
				onProvideHuntPrice(killer, victim);
				킬데스기록(killer, victim);
				if(!victim.isInWarAreaAndWarTime(victim, killer))
					setLawful(killer, victim);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			tKda.lastDeathMs 	= cur;
			oKda.lastKillMs		= cur;
			oKda.lastKillId 	= tKda.objid;
		}
		
	}
	private void 킬데스기록(L1PcInstance killer, L1PcInstance victim){
		SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
		
		Calendar Rtime = getRealTime();
		String nowToday = sdf.format(Rtime.getTime());
		
		
		String str = "";
		
		String locName = MapsTable.getInstance().getMapName(victim.getMapId());
		str = new String("[" + nowToday+"] [장소 : "+locName+"]  WIN : "+killer.getName()+" / DEATH : "+victim.getName());
		
		StringBuffer FileName = new StringBuffer("킬데스기록/킬데스기록.txt");
		PrintWriter out = null;
		
		try {
			out = new PrintWriter(new FileWriter(FileName.toString(), true));
			out.println(str);
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//TODO 수배 시스템 아데나 돌려주는부분
	private void onProvideHuntPrice(L1PcInstance killer, L1PcInstance victim){
		
	
		
		int aa = _random.nextInt(100) + 1;
		if (Config.공성수배) {
			if (!isInWarAreaAndWarTime(killer)) {
				
				if (victim.hasSkillEffect(L1SkillId.USER_WANTED)) {
					
					 if (killer.hasSkillEffect(L1SkillId.USER_WANTED)) {
						 if (aa <= Config.수배2단확률) {
							 killer.removeSkillEffect(L1SkillId.USER_WANTED);
							 killer.setSkillEffect(L1SkillId.USER_WANTED1, -1);
							 killer.doWanted(false);
							 killer.doWanted1(true);
							
							
						 }
					 }
					killer.getInventory().CreateOrAddItem(L1ItemId.ADENA, Config.수배금액);
					victim.removeSkillEffect(L1SkillId.USER_WANTED);
					victim.doWanted(false);
					SC_InventoryIcon.icoEnd(victim, L1SkillId.수배);
					// killer.sendPackets(new SC_SystemMessage(String.format("\\fT["+killer.getName()
					// + "] "+victim.getName()+"님 수배금 감사합니다")));
					SC_SystemMessage.broadcast("\\f>[" + killer.getName() + "]님이  ["
							+ victim.getName() + "]님의 \\aA수배금(" + Config.수배금액 / 10000 + "만) \\f>획득!");
				} 
				if (victim.hasSkillEffect(L1SkillId.USER_WANTED1)) {
					killer.getInventory().CreateOrAddItem(L1ItemId.ADENA, Config.수배금액);
					victim.removeSkillEffect(L1SkillId.USER_WANTED1);
					victim.doWanted1(false);
					SC_InventoryIcon.icoEnd(victim, L1SkillId.수배1);
					// killer.sendPackets(new SC_SystemMessage(String.format("\\fT["+killer.getName()
					// + "] "+victim.getName()+"님 수배금 감사합니다")));
					SC_SystemMessage.broadcast("\\f>[" + killer.getName() + "]님이  ["
							+ victim.getName() + "]님의 \\aA수배금(" + Config.수배금액 / 10000 + "만) \\f>획득!");
				}
			}
		} else {
			
			if (victim.hasSkillEffect(L1SkillId.USER_WANTED)) {
				
				 if (killer.hasSkillEffect(L1SkillId.USER_WANTED)) {
					 if (aa <= Config.수배2단확률) {
						 killer.removeSkillEffect(L1SkillId.USER_WANTED);
						 killer.setSkillEffect(L1SkillId.USER_WANTED1, -1);
						 killer.doWanted(false);
						 killer.doWanted1(true);
						
						
					 }
				 }
				killer.getInventory().CreateOrAddItem(L1ItemId.ADENA, Config.수배금액);
				victim.removeSkillEffect(L1SkillId.USER_WANTED);
				victim.doWanted(false);
				SC_InventoryIcon.icoEnd(victim, L1SkillId.수배);
				// killer.sendPackets(new SC_SystemMessage(String.format("\\fT["+killer.getName()
				// + "] "+victim.getName()+"님 수배금 감사합니다")));
				SC_SystemMessage.broadcast("\\f>[" + killer.getName() + "]님이  ["
						+ victim.getName() + "]님의 \\aA수배금(" + Config.수배금액 / 10000 + "만) \\f>획득!");
			} 
			if (victim.hasSkillEffect(L1SkillId.USER_WANTED1)) {
				killer.getInventory().CreateOrAddItem(L1ItemId.ADENA, Config.수배금액);
				victim.removeSkillEffect(L1SkillId.USER_WANTED1);
				victim.doWanted1(false);
				SC_InventoryIcon.icoEnd(victim, L1SkillId.수배1);
				// killer.sendPackets(new SC_SystemMessage(String.format("\\fT["+killer.getName()
				// + "] "+victim.getName()+"님 수배금 감사합니다")));
				SC_SystemMessage.broadcast("\\f3[" + killer.getName() + "]님이  ["
						+ victim.getName() + "]님의 \\aA수배금(" + Config.수배금액 / 10000 + "만) \\f3획득!");
			}
		}
		
	}
	public void notifyKill(L1PcInstance killer, L1PcInstance victim){
		String locName = MapsTable.getInstance().getMapName(victim.getMapId());
		//ServerBasePacket[] pcks = new ServerBasePacket[]{//\\da
			//new SC_ChatPacket(String.format("\\aG%s 님이 %s 님을 죽였습니다.", killer.getName(), victim.getName()), Opcodes.S_MESSAGE),
			//CWorld.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f3[ 승리 : \\aA"+killer.getName() + " \\f3]  ■  [ 패배 : \\aA"+victim.getName()+"\\f3]"));
			//CWorld.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f3사망위치 : \\aA"+locName));
			//new SC_ChatPacket(String.format("\\f>사망위치 : %s", MapsTable.getInstance().getMapName(killer.getMapId())), Opcodes.S_MESSAGE),
		//};
		//CWorld.getInstance().broadcastPacketToAll(pcks, true);
			/*
			if (killer.hasSkillEffect(L1SkillId.킬1)) {
				killer.removeSkillEffect(L1SkillId.킬1);
				killer.setSkillEffect(L1SkillId.킬2,20*1000);
				CWorld.getInstance().broadcastPacketToAll(S_IconMessage.getGmMessage(killer.getName()+"님이 더블킬중 입니다."));
			} else if (killer.hasSkillEffect(L1SkillId.킬2)) {
				killer.removeSkillEffect(L1SkillId.킬2);
				killer.setSkillEffect(L1SkillId.킬3,20*1000);
				CWorld.getInstance().broadcastPacketToAll(S_IconMessage.getGmMessage(killer.getName()+"님이 트리플킬중 입니다."));
			} else if (killer.hasSkillEffect(L1SkillId.킬3)) {
				killer.removeSkillEffect(L1SkillId.킬3);
				killer.setSkillEffect(L1SkillId.킬4,20*1000);
				CWorld.getInstance().broadcastPacketToAll(S_IconMessage.getGmMessage(killer.getName()+"님이 쿼드라킬중 입니다."));
			} else if (killer.hasSkillEffect(L1SkillId.킬4)) {
				killer.removeSkillEffect(L1SkillId.킬4);
				killer.setSkillEffect(L1SkillId.킬5,20*1000);
				CWorld.getInstance().broadcastPacketToAll(S_IconMessage.getGmMessage(killer.getName()+"님이 학살중 입니다."));
			} else {
				killer.setSkillEffect(L1SkillId.킬1,20*1000);
			}		
			*/
	}
	private boolean isInWarAreaAndWarTime(L1PcInstance pc) {
		int castleId = L1CastleLocation.getCastleIdByArea(pc);
		
		if (castleId != 0) {
			if (CastleWarBusiness.getInstance().isNowWar(castleId)) {
				return true;
			}
		}
		return false;
	}
	
	public void notifyKillForGotten(L1PcInstance killer, L1PcInstance victim){
        final int cid = victim.getClanid();
        final byte[] plain = SC_ShowCmd.encode(killer.getName());
        final byte[] victimBytes = SC_ShowCmd.encode(victim.getName());
        try(var plainShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                     SC_ShowCmd.nativePkWriter(plain,victimBytes),Integer.MIN_VALUE,Integer.MAX_VALUE)){
        if (cid == 0) {
            for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                if (pc != null) l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(plainShared,pc);
            }
            return;
        }
        final byte[] colored = SC_ShowCmd.encode(String.format("\\aG%s", killer.getName()));
        try(var coloredShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                   SC_ShowCmd.nativePkWriter(colored,victimBytes),Integer.MIN_VALUE,Integer.MAX_VALUE)){
        for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
            if (pc == null) continue;
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(pc.getClanid()==cid?coloredShared:plainShared,pc);
        }
}
    
}}

	private void setLawful(L1PcInstance killer, L1PcInstance victim){
		int kLaw = killer.getLawful();
		int vLaw = victim.getLawful(); 
		if(vLaw >= 0 && !victim.isPinkName()){
			if(kLaw < 30000){
				killer.set_PKcount(killer.get_PKcount() + 1);
				killer.setLastPk();
			}
			
			
			int nLaw = calculate_lawful(killer);
			
			killer.setLawful(nLaw);
			killer.send_lawful();
		}else
			victim.setPinkName(false);
	}
	
	public static int calculate_lawful(L1PcInstance killer){ //라우풀 변화량 Neige
		int nLaw = 0;
		if(killer.getLevel() < 50)
			nLaw = -1 * (int) ((Math.pow(killer.getLevel(), 2) * 4));
		else
			nLaw = -1 * (int) ((Math.pow(killer.getLevel(), 3) * 0.08));
		
		return Math.max(nLaw, -32768);
	}
	
	public byte[] serialize() throws Exception{
		BytesOutputStream bos = new BytesOutputStream(32);
		bos.write(0x0A);
		bos.writeS2(name);
		bos.write(0x10);
		bos.writeBit(kill);
		byte[] data = bos.toArray();
		bos.close();
		bos.dispose();
		bos = null;
		return data;
	}
}
