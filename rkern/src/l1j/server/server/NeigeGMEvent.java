package l1j.server.server;




import java.util.ArrayList;

import l1j.server.Config;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardA;
import l1j.server.server.model.item.Card.L1CardAlchemy2;
import l1j.server.server.model.item.Card.L1CardB;
import l1j.server.server.model.item.Card.L1CardC;
import l1j.server.server.model.item.Card.L1CardHPast;
import l1j.server.server.model.item.Card.L1CardHeroList;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardStep2;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;



public class NeigeGMEvent{

	boolean spawnTF = false;  


	private static NeigeGMEvent _instance; 
	
	private NeigeGMEvent() {
	}

	public static NeigeGMEvent getInstance() {
		if (_instance == null) {
			_instance = new NeigeGMEvent(); 
		}
		return _instance;
	}
	public static void settingpage(L1PcInstance pc) {
		ArrayList<String> setting = new ArrayList<String>();
	    if ((pc == null) || (pc.isDead())) {
		      return;
		}
	    setting.add(Config.흑단무빙 == 1 ? "5208" : "5209");
	    setting.add(Config.Neige_Count_Pass == 2 ? "5208" : "5209");
	    setting.add(Config.펑몹이벤트 == 2 ? "5208" : "5209");
	    setting.add(Config.Neige_Reward == 2 ? "5208" : "5209");
	    setting.add(Config.Neige_Myth_Skill == 2 ? "5208" : "5209");
	    setting.add(Config.myth_possible == 0 ? "5208" : "5209");
	    setting.add(Config.sole_possible == 0 ? "5208" : "5209");
	    
	    SC_NPCTalkReturn.send(pc, pc.getId(), "inshop20", (String[]) setting.toArray(new String[setting.size()]));
	    
	}

	public static void eventpage(L1PcInstance pc) {
		ArrayList<String> event = new ArrayList<String>();
	    if ((pc == null) || (pc.isDead())) {
		      return;
		}
	    event.add(Config.할파스변수+"");
	    event.add(Config.카드변수+"");
	    event.add(Config.인형변수+"");
	    event.add(Config.오림변수+"");
	    event.add(Config.수호성변수+"");
	    event.add("5209");
	    event.add(Config.할파스갯수+"");
	    event.add(Config.카드갯수+"");
	    event.add(Config.인형갯수+"");
	    event.add(Config.오림갯수+"");
	    event.add(Config.수호성갯수+"");
	    SC_NPCTalkReturn.send(pc, pc.getId(), "inshop21", (String[]) event.toArray(new String[event.size()]));
	    
	}
	public static void total(L1PcInstance pc) {
		ArrayList<String> event = new ArrayList<String>();
	    if ((pc == null) || (pc.isDead())) {
		      return;
		}
		if(Config.할파스변수 != 1 && Config.할파스변수 != Config.할파스갯수){
			Config.할파스갯수 = Config.할파스변수;
			SC_SystemMessage.broadcast("할파스의 집념 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.할파스갯수+"배]");
			SC_PacketBox.broadcastGreen("할파스의 집념 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.할파스갯수+"배]");
		}
		if(Config.카드변수 != 1 && Config.카드변수 != Config.카드갯수){
			Config.카드갯수 = Config.카드변수;
			SC_SystemMessage.broadcast("영웅 변신 카드상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.카드갯수+"배]");
			SC_PacketBox.broadcastGreen("영웅 변신 카드상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.카드갯수+"배]");
		}
		if(Config.오림변수 != 1 && Config.오림변수 != Config.오림갯수){
			Config.오림갯수 = Config.오림변수;
			SC_SystemMessage.broadcast("오림의 장신구 마법 주문서 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.오림갯수+"배]");
			SC_PacketBox.broadcastGreen("오림의 장신구 마법 주문서 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.오림갯수+"배]");
		}
		if(Config.인형변수 != 1 && Config.인형변수 != Config.인형갯수){
			Config.인형갯수 = Config.인형변수;
			SC_SystemMessage.broadcast("금빛 마법인형 주머니 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.인형갯수+"배]");
			SC_PacketBox.broadcastGreen("금빛 마법인형 주머니 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.인형갯수+"배]");
		}
		if(Config.수호성변수 != 1 && Config.수호성변수 != Config.수호성갯수){
			Config.수호성갯수 = Config.수호성변수;
			SC_SystemMessage.broadcast("수호성 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.수호성갯수+"배]");
			SC_PacketBox.broadcastGreen("수호성 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+Config.수호성갯수+"배]");
		}
		if(Config.할파스변수 == 1 && Config.할파스갯수 != 1){
			Config.할파스갯수 = Config.할파스변수;
			SC_SystemMessage.broadcast("할파스의 집념 드랍율 이벤트가 종료되었습니다.");
			SC_PacketBox.broadcastGreen("할파스의 집념 드랍율 이벤트가 종료되었습니다.");
		}
		if(Config.카드변수 == 1 && Config.카드갯수 != 1){
			Config.카드갯수 = Config.카드변수;
			SC_SystemMessage.broadcast("영웅 변신 카드상자 드랍율 이벤트가 종료되었습니다.");
			SC_PacketBox.broadcastGreen("영웅 변신 카드상자 드랍율 이벤트가 종료되었습니다.");
		}
		if(Config.오림변수 == 1 && Config.오림갯수 != 1){
			Config.오림갯수 = Config.오림변수;
			SC_SystemMessage.broadcast("오림의 장신구 마법 주문서 상자 드랍율 이벤트가 종료되었습니다.");
			SC_PacketBox.broadcastGreen("오림의 장신구 마법 주문서 상자 드랍율 이벤트가 종료되었습니다.");
		}
		if(Config.인형변수 == 1 && Config.인형갯수 != 1){
			Config.인형갯수 = Config.인형변수;
			SC_SystemMessage.broadcast("금빛 마법인형 주머니 드랍율 이벤트가 종료되었습니다.");
			SC_PacketBox.broadcastGreen("금빛 마법인형 주머니 드랍율 이벤트가 종료되었습니다.");
		}
		if(Config.수호성변수 == 1 && Config.수호성갯수 != 1){
			Config.수호성갯수 = Config.수호성변수;
			SC_SystemMessage.broadcast("수호성 상자 드랍율 이벤트가 종료되었습니다.");
			SC_PacketBox.broadcastGreen("수호성 상자 드랍율 이벤트가 종료되었습니다.");
		}
	    event.add(Config.할파스변수+"");
	    event.add(Config.카드변수+"");
	    event.add(Config.인형변수+"");
	    event.add(Config.오림변수+"");
	    event.add(Config.수호성변수+"");
	    event.add("5209");
	    event.add(Config.할파스갯수+"");
	    event.add(Config.카드갯수+"");
	    event.add(Config.인형갯수+"");
	    event.add(Config.오림갯수+"");
	    event.add(Config.수호성갯수+"");
	    SC_NPCTalkReturn.send(pc, pc.getId(), "inshop21", (String[]) event.toArray(new String[event.size()]));
}
	
	
}
