package l1j.server.server.model.Instance;

import java.util.ArrayList;
import l1j.server.Templates.L1Npc;
import l1j.server.server.model.Attack;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ChangeHeading;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CalcExp;

public class ScarecrowInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	public ScarecrowInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance player) {
		Attack attack = new Attack(player, this);
		if (player.isInParty() || player.isDead()) {  
			player.sendPackets(new S_SystemMessage("파티중에는 허수아비를 칠수 없습니다."));
			return;
		}
		if (attack.calcHit()) {
			if (player.getLevel() < 99) { // LV제한 버는 경우는 여기를 변경
				ArrayList<L1PcInstance> targetList = new ArrayList<L1PcInstance>();

				targetList.add(player);
				ArrayList<Integer> hateList = new ArrayList<Integer>();
				hateList.add(1);
				CalcExp.calcExp(player, getId(), targetList, hateList, getExp());
			}
			if (this.getNpcId() == 45003 || this.getNpcId() == 10345796) {
				int dmgList = attack.calcDamage();
				if (!player.hasSkillEffect(SkillId.SPR_TIME)) {
					player.setSkillEffect(SkillId.SPR_TIME, 300 * 1000);
				}
				player.setPcDmg(dmgList);
				if (player.getMinDmg() == 0) {
					player.setMinDmg(player.getPcDmg());
				}
				if (player.getMinDmg() > player.getPcDmg()) {
					player.setMinDmg(player.getPcDmg());
				}
				if (player.getMaxDmg() < player.getPcDmg()) {
					player.setMaxDmg(player.getPcDmg());
				}
				player.setDmgPesent(player.getDmgPesent() + player.getPcDmg());
				player.setDmgCount(player.getDmgCount() +1);
				int dmg = 0;
				dmg = player.getDmgPesent() / player.getDmgCount();
				int sec = 0;
				sec = 300 - player.getSkillEffectTimeSec(SkillId.SPR_TIME);
				long start = System.nanoTime();
				player.setStartTime(start);
				long check = player.getStartTime() - player.getEndTime();
				double sec2 = check / 1000000000.0;
				String speed = "";
				if (sec2 > 1.2) {
					speed = "시작!";
					player.setMinTime(0);
				} else {
					speed = String.format("%.3f", sec2)+"초";
				}
				
				if (player.getMinTime() == 0 || player.getMinTime() < sec2 - 0.1) {
					player.setMinTime(sec2);
				}
				if (player.getMinTime() > sec2) {
					player.setMinTime(sec2);
				}
				
				if (player.getMaxTime() > player.getMinTime() + 0.15) {
					player.setMaxTime(sec2);
				}
				if (player.getMaxTime() < sec2) {
					player.setMaxTime(sec2);
				}
				
				double minsec = player.getMinTime();
				double maxsec = player.getMaxTime();
				String minSpeed = "";
				if (minsec > 1.2) {
					minSpeed = "시작!";
				} else {
					minSpeed = String.format("%.3f", minsec);
				}
				String maxSpeed = "";
				if (maxsec > 1.2) {
					maxSpeed = "시작!";
				} else {
					maxSpeed = String.format("%.3f", maxsec);
				}
				
				if (player.isGm()) {
					player.sendPackets(new S_SystemMessage("\\fRDMG[\\fY"+player.getPcDmg()+"\\fR]최소[\\fY"+player.getMinDmg()+"\\fR]최대[\\fY"+player.getMaxDmg()+"\\fR]"));
					player.sendPackets(new S_SystemMessage("\\fR횟수[\\fT"+player.getDmgCount()+"\\fR]평타[\\fY"+dmg+"\\fR]시간[\\fV"+sec+"\\fR]"));
					player.sendPackets(new S_SystemMessage("\\fR공속[\\fU"+speed+"\\fR]최소[\\fU"+minSpeed+"\\fR]최대[\\fU"+maxSpeed+"\\fR]"));
				} else {
					player.sendPackets(new S_SystemMessage("\\fR무기[\\fT+"+player.getEquipSlot().getWeapon().getEnchantLevel()+player.getEquipSlot().getWeapon().getName()+"\\fR]"));
					player.sendPackets(new S_SystemMessage("\\fRDMG[\\fY"+player.getPcDmg()+"\\fR]최소[\\fY"+player.getMinDmg()+"\\fR]최대[\\fY"+player.getMaxDmg()+"\\fR]"));
					player.sendPackets(new S_SystemMessage("\\fR공속[\\fU"+speed+"\\fR]최소[\\fU"+minSpeed+"\\fR]최대[\\fU"+maxSpeed+"\\fR]"));
				}
				//if (player.isDmgViewer) { attack.DmgViewer(); }
			}
			long end = System.nanoTime();
			player.setEndTime(end);
			/*if (this.getNpcId() == 10345796) {
				int dmg = attack.calcDamage();
				// String chatId = "현재 데미지는 " + dmg + " 입니다!";
				// Broadcaster.broadcastPacket(this, new S_NpcChatPacket(this, chatId, 0));
				// 방송하기 싫으면 위에꺼 대신 아래꺼 사용
				player.sendPackets(new S_SystemMessage("damaged : " + dmg + "."));
			}*/
			if (getHeading() < 7) { // 지금의 방향을 취득
				setHeading(getHeading() + 1); // 지금의 방향을 설정
			} else {
				setHeading(0); // 지금의 방향이 7 이상이 되면(자) 지금의 방향을 0에 되돌린다
			}
			broadcastPacket(S_ChangeHeading.get(this)); // 방향의 변경
		}
		attack.action();
	}

	@Override
	public void onTalkAction(L1PcInstance l1pcinstance) {

	}

	public void onFinalAction() {

	}

	public void doFinalAction() {
	}
}
