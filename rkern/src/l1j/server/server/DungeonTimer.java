//import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

//package l1j.server.server;
//
//import java.util.Calendar;
//
//import l1j.server.server.Controller.DungeonQuitController;
//import l1j.server.server.model.L1Teleport;
//import l1j.server.server.model.L1World;
//import l1j.server.server.model.Instance.L1PcInstance;
//import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
//import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
//
//public class DungeonTimer implements Runnable {
//
//	private static DungeonTimer instance;
//
//	public static final int SleepTime = 1 * 60 * 1000; //1분 마다 체크
//	
//	public static DungeonTimer getInstance(){
//		if (instance == null){
//			instance = new DungeonTimer();
//		}
//		return instance;
//	}
//
//	@Override
//	public void run() {
//		while (true) {
//			for (L1PcInstance use : L1World.getInstance().getAllPlayers()){
//				if (use == null || use.getNetConnection() == null || use.noPlayerCK || use.noPlayerck2){
//					continue;
//				} else {
//					try {
////						if (use.getMapId() >= 53 && use.getMapId() <= 56 || use.getMapId() >= 807
////								&& use.getMapId() <= 813) { // 기란
////							GiranTimeCheck(use);
////						}
//						if (use.getMapId() >= 78 && use.getMapId() <= 82){ //오렌
//							OrenTimeCheck(use);
//						}
//						if (use.getMapId() >= 30 && use.getMapId() <= 33
//						 || use.getMapId() >= 35 && use.getMapId() <= 37
//						 || use.getMapId() == 814){ //용던
//							DrageonTimeCheck(use);
//						}
//						if (use.getMapId() >= 451 && use.getMapId() <= 456
//								|| use.getMapId() >= 460 && use.getMapId() <= 466
//								|| use.getMapId() >= 470 && use.getMapId() <= 478
//								|| use.getMapId() >= 490 && use.getMapId() <= 496
//								|| use.getMapId() >= 530 && use.getMapId() <= 534
//								|| use.getMapId() == 479){
//							RadungeonTimeCheck(use);
//						}
//						if (use.getMapId() == 303) { // 몽섬
//							SomeTimeCheck(use);
//						}
//						if (use.getMapId() == 430 || use.getMapId() == 400) { // 정령의무덤 , 고대의무덤 
//							SoulTimeCheck(use);
//						}
//						if (use.getMapId() == 280 || use.getMapId() == 281 || use.getMapId() == 282
//								 || use.getMapId() == 283 || use.getMapId() == 284) { //발록진영
//							newdodungeonTimeCheck(use);
//						}
//						if (use.getMapId() == 285 || use.getMapId() == 286 || use.getMapId() == 287 || use.getMapId() == 288
//								 || use.getMapId() == 289) { //야히진영
//							OrenTimeCheck(use);
//						}
//						if (use.getMapId() == 5555 || use.getMapId() == 5556) { //얼던PC
//							icedungeonTimeCheck(use);
//						}
//						if (use.getMapId() == 1 || use.getMapId() == 2) { //말던
//							islanddungeonTimeCheck(use);
//						}
//						초기화();
//						
//
//					} catch (Exception e) {
//						e.printStackTrace();
//					} finally {
//						try {
//							Thread.sleep(60000); // 전체 던전 타이머 1분마다 체크 
//						} catch (InterruptedException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
//					}
//
//				 }
//
//			 }
//		}
//	}
//	
//	private void 초기화(){
//		try {
//			Calendar cal = Calendar.getInstance();
//			int 시간 = Calendar.HOUR;
//			int 분 = Calendar.MINUTE;
//			/** 0 오전 , 1 오후 * */
//			String 오전오후 = "오후";
//			if (cal.get(Calendar.AM_PM) == 0) {
//				오전오후 = "오전";
//			}
//			if (DungeonQuitController.getInstance().isgameStart == false) {
//				if ((오전오후.equals("오전") && cal.get(시간) == 8 && cal.get(분) == 59)) {//매일 오전 8시59분초기화
//					DungeonQuitController.getInstance().isgameStart = true;
//					System.out.println("■CMD MSG■: " + 오전오후 + " " + cal.get(시간) + "시" + cal.get(분) + "분 초기화되었습니다.");
//				}
//			}
//		} catch (Exception e) {
//			System.out.println("시간초기화에러" + e);
//		}
//	}
//
////	private void GiranTimeCheck(L1PcInstance pc) {
////		if (pc.getGirandungeonTime() == 179){
//////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
////			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
////			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[기란]\\aA 던전 시간이 만료되었습니다."));
////		}
////		pc.setGirandungeonTime(pc.getGirandungeonTime() + 1);
////	}
//	
//	private void OrenTimeCheck(L1PcInstance pc){
//		if (pc.getOrendungeonTime() == 59){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("계정내 야히진영 던전 시간이 만료되었습니다."));
//		}
//		pc.setOrendungeonTime(pc.getOrendungeonTime() + 1);
//	}
//	
//	private void DrageonTimeCheck(L1PcInstance pc){
//		if (pc.getDrageonTime() == 119){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[용의]\\aA 던전 시간이 만료되었습니다."));
//		}
//		pc.setDrageonTime(pc.getDrageonTime() + 1);
//	}
//	
//	private void SomeTimeCheck(L1PcInstance pc){
//		if (pc.getSomeTime() == 29){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[몽환의 섬]\\aA 던전 시간이 만료되었습니다."));
//		}
//		pc.setSomeTime(pc.getSomeTime() + 1);
//	}
//	private void SoulTimeCheck(L1PcInstance pc){
//		if (pc.getSoulTime() == 29){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			(new SC_ChatPacket(pc, "무덤 체류 시간이 만료 되었습니다.")).send(pc);
//		}
//		pc.setSoulTime(pc.getSoulTime() + 1);
//	}
//	
//	private void RadungeonTimeCheck(L1PcInstance pc){
//		if (pc.getRadungeonTime() == 119){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			(new SC_ChatPacket(pc, "라스타바드 던전 시간이 만료 되었습니다.")).send(pc);
//		}
//		pc.setRadungeonTime(pc.getRadungeonTime() + 1);
//	}
//	private void newdodungeonTimeCheck(L1PcInstance pc){
//		if (pc.getnewdodungeonTime() == 59){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[발록진영]\\aA 던전 시간이 만료되었습니다."));
//		}
//		pc.setnewdodungeonTime(pc.getnewdodungeonTime() + 1);
//	}
//	private void icedungeonTimeCheck(L1PcInstance pc){
//		if (pc.geticedungeonTime() == 29){
////			L1Teleport.teleport(pc, 33419, 32810, (short) 4, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[얼음PC]\\aA 던전 시간이 만료되었습니다."));
//		}
//		pc.seticedungeonTime(pc.geticedungeonTime() + 1);
//	}
//	private void islanddungeonTimeCheck(L1PcInstance pc){
//		if (pc.getislandTime() == 119){
////			L1Teleport.teleport(pc, 32585, 32929, (short) 0, 0, true);
//			pc.start_teleport(33419, 32810, 4, 5, 169, true, false);
//			pc.sendPackets(new SC_SystemMessage("\\aA경고: \\aG[말하는섬]\\aA 던전 시간이 만료되었습니다."));
//		}
//		pc.setislandTime(pc.getislandTime() + 1);
//	}
//}