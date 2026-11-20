package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.RepeatTask;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.character.skill.CSkillInfo;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;


import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public class HelpBySupport extends RepeatTask {
	private static Logger _log = Logger.getLogger(HelpBySupport.class
			.getName());
	private static Random _random = new Random(System.nanoTime());
	
	private final L1PcInstance _pc;
	
	private L1SupportInstance _support;

	public HelpBySupport(L1PcInstance pc, long interval) {
		super(interval);
		_pc = pc;
		  Object[] supportList = _pc.getSupportList().values().toArray();
		  L1SupportInstance support = null;
		  for (Object supportObject : supportList) {
			  support = (L1SupportInstance) supportObject;
				_support = support;
		  }
	}

	@Override
	public void execute() {
		try {
			if (_pc.isDead()) {
				return;
			}
			if (_support.hasSkillEffect(L1SkillId.HASTE)) {
			} else if (_support.hasSkillEffect(L1SkillId.GREATER_HASTE)) {
			} else if (_support.hasSkillEffect(L1SkillId.STATUS_HASTE)) {
			}else{
				SupportHaste();
			}
			if (_pc.hasSkillEffect(L1SkillId.HASTE)) {
			} else if (_pc.hasSkillEffect(L1SkillId.GREATER_HASTE)) {
			} else if (_pc.hasSkillEffect(L1SkillId.STATUS_HASTE)) {
			}else{
				Haste();
			}
			
			if (_support.hasSkillEffect(L1SkillId.HOLY_WALK)) {
			}else{
				Brave();
			}
			
			if(_pc.hasSkillEffect(L1SkillId.PHYSICAL_ENCHANT_STR)){
			}else{
				Strup();
			}
			
			if(_pc.hasSkillEffect(L1SkillId.PHYSICAL_ENCHANT_DEX)){
			}else{
				Dexup();
			}
			
			if(_pc.hasSkillEffect(L1SkillId.ADVANCE_SPIRIT)){
			}else{
				Advanceup();
			}
			if(_pc.hasSkillEffect(L1SkillId.STATUS_POISON)
					||_pc.hasSkillEffect(L1SkillId.STATUS_POISON_PARALYZING) 
					||_pc.hasSkillEffect(L1SkillId.STATUS_POISON_SILENCE)){
				cure();
			}
			heal();
			heal3();
					
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void Haste() {
		if(_support.getCurrentMp() > 40){
			SC_SkillHaste.send(_pc, _pc.getId(), 1, 1200);
			SC_SkillHaste.broadcast(_pc, _pc.getId(), 1, 1200);
		_pc.setSkillEffect(L1SkillId.HASTE, 1200 * 1000);
		SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 755); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 755);
		_pc.setMoveSpeed(1);
		_support.setCurrentMp(_support.getCurrentMp() - 40);
		}else{
			npctalk(0);
		}
	}
	
	public void SupportHaste() {
		if(_support.getCurrentMp() > 40){
			_support.setCurrentMp(_support.getCurrentMp() - 40);
			SC_SkillHaste.broadcast(_support, _support.getId(), 1, 1200);
			_support.setSkillEffect(L1SkillId.HASTE, 1200 * 1000);
			SC_DoActionGFX.broadcast(_support, 19);
			SC_SkillSound.broadcast(_support, _support.getId(), 755);
			_support.setMoveSpeed(1);
			_support.setCurrentMp(_support.getCurrentMp() - 40);
		}else{
			npctalk(0);
		}
	}
	
	public void Brave() {
		if(_support.getCurrentMp() > 15){
			_support.setBraveSpeed(4);
			SC_SkillBrave.broadcast(_support, _support.getId(), 4, 32);
			_support.setCurrentMp(_support.getCurrentMp() - 15);
			_support.setSkillEffect(L1SkillId.HOLY_WALK, 32 * 1000);
			SC_DoActionGFX.broadcast(_support, 19);
			SC_SkillSound.broadcast(_support, _support.getId(), 3936);
		}else{
			npctalk(0);
		}
	}
	
	public void Strup() {
		if(_support.getCurrentMp() > 50){
		if (!_pc.submitSkillPumpStart(L1SkillId.PHYSICAL_ENCHANT_STR, 1200)) return;
		SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 751); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 751);
		_support.setCurrentMp(_support.getCurrentMp() - 50);
		}else{
			npctalk(0);
		}
	}
	
	public void Dexup() {
		if(_support.getCurrentMp() > 50){
		if (!_pc.submitSkillPumpStart(L1SkillId.PHYSICAL_ENCHANT_DEX, 1200)) return;
		SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 750); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 750);
		_support.setCurrentMp(_support.getCurrentMp() - 50);
		}else{
			npctalk(0);
		}
	}
	
	public void heal() {
		if(_support.getCurrentMp() > 20){
		if(_pc.getCurrentHp() < _pc.getMaxHp()*60 / 100){
			SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 830); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 830);
		_pc.healHp(150);
		_support.setCurrentMp(_support.getCurrentMp() - 20);
		}
	}else{
			npctalk(0);
		}
	}
	
	public void heal2() {
		if(_support.getCurrentMp() > 20){
			SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 830); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 830);
		_pc.healHp(150);
		_support.setCurrentMp(_support.getCurrentMp() - 20);
	}else{
			npctalk(0);
		}
	}
	
	public void heal3() {
		if(_support.getCurrentMp() > 20){
			if(_support.getCurrentHp() < _support.getMaxHp()*60 / 100){
			SC_DoActionGFX.broadcast(_support, 19);
			SC_SkillSound.broadcast(_support, _support.getId(), 830);
		_support.healHp(150);
		_support.setCurrentMp(_support.getCurrentMp() - 20);
			}
	}else{
			npctalk(0);
		}
	}
	
	public void cure() {
		if(_support.getCurrentMp() > 15){
			SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 871); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 871);
		_pc.curePoison();
		_support.setCurrentMp(_support.getCurrentMp() - 15);
	}else{
			npctalk(0);
		}
	}
	
	public void Advanceup() {
		if(_support.getCurrentMp() > 20){
		if (!CSkillInfo.grantSupportAdvance(_pc, 1200)) return;
		_support.setCurrentMp(_support.getCurrentMp() - 20);
		SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 3935); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 3935);
		}else{
			npctalk(0);
		}
	}
	
	public void Berserkers() {
		if(_support.getCurrentMp() > 40){
			if(_pc.hasSkillEffect(L1SkillId.BERSERKERS)){
				_pc.removeSkillEffect(L1SkillId.BERSERKERS);
			}
		_pc.setSkillEffect(L1SkillId.BERSERKERS, 320 * 1000);
		_pc.getAC().addAc(10);
		_pc.addDmgup(5);
		_pc.addHitup(2);
		SC_OwnCharAttrDef.send(_pc);
		_support.setCurrentMp(_support.getCurrentMp() - 20);
		SC_DoActionGFX.broadcast(_support, 19);
		SC_SkillSound.send(_pc, _pc.getId(), 3943); 
		SC_SkillSound.broadcast(_pc, _pc.getId(), 3943);
		}else{
			npctalk(0);
		}
	}
	
	public void Immune() {
		if(_pc.getInventory().checkItem(40318, 2)){
			if(_support.getCurrentMp() > 30){
				_pc.getInventory().consumeItem(40318, 2);
				_pc.setSkillEffect(L1SkillId.IMMUNE_TO_HARM, 32 * 1000);
				_pc.setLastImmuneLevel(65); // 임시입니다.
				SC_SkillIconGFX.send(_pc, 40, 32);
				SC_DoActionGFX.broadcast(_support, 19);
			SC_SkillSound.send(_pc, _pc.getId(), 228); 
			SC_SkillSound.broadcast(_pc, _pc.getId(), 228);
			_support.setCurrentMp(_support.getCurrentMp() - 30);
			}else{
				npctalk(0);
			}
			}else{
				npctalk(2);
			}
		/*
		if(_pc.getInventory().checkItem(40318, 2)){
		if(_support.getCurrentMp() > 30){
			_pc.getInventory().consumeItem(40318, 2);
			_pc.setSkillEffect(L1SkillId.IMMUNE_TO_HARM, 32 * 1000);
			SC_SkillIconGFX.send(_pc, 40, 32);
			SC_DoActionGFX.broadcast(_support, 19);
		l1j.server.server.model.network.core.sc.packet.SC_SkillSound.sendAndBroadcast(_pc, _pc.getId(), 228);
		_support.setCurrentMp(_support.getCurrentMp() - 30);
		}else{
			npctalk(0);
		}
		}else{
			npctalk(2);
		}*/
	}
	
	public void npctalk(int i) {
		int rnd = _random.nextInt(100)+1;
		String chat = null;
		if(rnd > 99){
		if(i == 0){
    	chat = "주인님! 엠이오링났어요..";
    	SC_NpcChatPacket.broadcastNpc(_support, _support, chat, 0);
		}else if(i == 1){
	    	chat = "주인님! 엠이 반절남았어요..";
	    	SC_NpcChatPacket.broadcastNpc(_support, _support, chat, 0);
		}
		}
		if(i == 2){
	    	 chat = "주인님! 마돌이 없습니다..";
	    	SC_NpcChatPacket.broadcastNpc(_support, _support, chat, 0);
		}
		if(i == 3){
	    	 chat = "엠["+ _support.getCurrentMp() +"]남았습니다.";
	    	SC_NpcChatPacket.broadcastNpc(_support, _support, chat, 0);
		}
	}
	
	public void npctalk2(String chatText) {
		String chat = chatText;
	    	SC_NpcChatPacket.broadcastNpc(_support, _support, chat, 0);
	}
	
	public void npctalk3(String chatText) {
		if (chatText.startsWith("힐줘gm")) {
			heal2();
			return;
		}else if(chatText.startsWith("버서줘gm")){
			Berserkers();
			return;
		}else if(chatText.startsWith("힘업줘gm")){
			Strup();
			return;
		}else if(chatText.startsWith("덱업줘gm")){
			Dexup();
			return;
		}else if(chatText.startsWith("어벤줘gm")){
			Advanceup();
			return;
		}else if(chatText.startsWith("뮨줘gm") && _pc.isGm()){
			Immune();
			return;
		}else if(chatText.startsWith("엠몇gm")){
			npctalk(3);
			return;
		}else if(chatText.startsWith("속이기gm") && _pc.isGm()){
			chatText = chatText.replace("속이기gm","");
			npctalk2(chatText);
			return;
		}else if(chatText.startsWith("공격해gm") && _pc.isGm()){
			chatText = chatText.replace("공격해gm","");
			hate(chatText);
			return;
		}else if(chatText.startsWith("그만해gm") && _pc.isGm()){
			cleartarget();
			return;
		}
	}
	
	public void hate(String chatText) {
		String chat = chatText;
		L1Character target = (L1Character) L1World.getInstance().findObject(chat);
		if (_support != null && target != null) {
			if (target instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) target;
				if (pc.checkNonPvP(pc, _support)) {
					return;
				}
			}
			_support.setMasterTarget(target);
		}
	}
	
	public void cleartarget(){
		_support.allTargetClear();
	}
}
