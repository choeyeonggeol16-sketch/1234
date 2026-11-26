package l1j.server.server.model.character.survey;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_IconMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.lobby.creator.LobbyCreator;
import l1j.server.server.packets.Protocol.MainServer.Proto.Adapter.SC_NOTIFICATION_MESSAGE;
import l1j.server.server.shared.SimpleRgb;
import l1j.server.server.shared.geometry.GamePoint;
import l1j.server.server.templates.L1BoardPost;

public class SurveyFactory {
	private static final String _isOverEvent = "이미 종료된 이벤트입니다.";
	
	public static InterfaceSurvey createLFCSurvey(){
		return new InterfaceSurvey(){
			@Override
			public void survey(L1PcInstance pc, int num, boolean isYes) {
				L1BoardPost bp = L1BoardPost.findByIdLfc(num - 1000);
				if(bp == null){
					pc.sendPackets("잘못된 응답입니다.");
					return;
				}
				String content = bp.getContent();
				String[] content_array = content.split(" ");
				String target_name = content_array[1];
				int type_id = Integer.parseInt(content_array[0]);
				L1PcInstance owner = L1World.getInstance().findpc(target_name);
				if(owner == null){
					pc.sendPackets(String.format("%s님을 찾을 수 없습니다.", target_name));
					LobbyCreator.unregistLfc(pc);
					return;
				}
				
				if(!isYes){
					if(owner != null)
						owner.sendPackets(String.format("%s님이 결투를 취소했습니다.", pc.getName()));
					LobbyCreator.unregistLfc(pc);
					return;
				}
				
				LobbyCreator.create(pc, owner, type_id);
				LobbyCreator.unregistLfc(pc);				
			}
		};
	}
	
	public static InterfaceSurvey createObjectCallSurvey(){
		return new InterfaceSurvey(){
			@Override
			public void survey(L1PcInstance pc, int num, boolean isYes) {
				if(!isYes)
					return;
				
				L1Object obj = L1World.getInstance().findObject(num);
				if(obj == null){
					SC_SystemMessage.send(pc, _isOverEvent);
					return;
				}
				
				if(obj instanceof L1Character){
					L1Character c = (L1Character)obj;
					if(c.isDead()){
						SC_SystemMessage.send(pc, _isOverEvent);
						return;						
					}
				}
				
				GamePoint pt = GamePoint.newInstance(obj.getX(), obj.getY(), 3, obj.getMapId());
				pc.start_teleport(pt.x, pt.y, pt.mapId, pc.getHeading(), 169, true, false);
			}
		};
	}
	
	public static boolean isMegaphoneSpeaking = false;
	public static InterfaceSurvey createMegaphoneSurvey(int megaphoneObjectId, String message, int duration_second){
		return new InterfaceSurvey(){
			@Override
			public void survey(L1PcInstance pc, int num, boolean isYes){
				if(!isYes)
					return;
				
				L1ItemInstance item = pc.getInventory().GetByServerID(num);
				if(item == null){
					pc.sendPackets("확성기를 찾을 수 없습니다.");
					return;
				}
				
				if(isMegaphoneSpeaking){
					pc.sendPackets("이미 확성기 메시지가 재생되고 있습니다. 잠시 후 다시 이용해주세요.");
					return;
				}
				isMegaphoneSpeaking = true;
				final String iconMessage = 
						String.format("[%s]님의 확성알림.", pc.getName());
				final byte[] notificationRgb = SimpleRgb.red().get_bytes();
				L1World.getInstance().getAllPlayerStream()
				.filter((L1PcInstance t) -> t != null)
				.forEach((L1PcInstance t) -> {
					SC_IconMessage.send(t, iconMessage, new SimpleRgb(255, 255, 255), 17, duration_second);
					SC_NOTIFICATION_MESSAGE.send(t, SC_NOTIFICATION_MESSAGE.DISPLAY_POSITION_BOTTOM,
							message, notificationRgb, duration_second);
				});
				TaskLane.SESSION.scheduleRequired(new Runnable(){
					@Override
					public void run(){
						isMegaphoneSpeaking = false;
					}
				}, duration_second * 1000L);
				pc.getInventory().DeleteItemAmount(num, 1);

			}
		};
	}
}
