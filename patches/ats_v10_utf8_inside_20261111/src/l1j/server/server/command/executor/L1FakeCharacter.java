package l1j.server.server.command.executor;

import java.util.Random;
import java.util.StringTokenizer;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1FakeCharacter implements L1CommandExecutor {

	private static Random _random = new Random(System.nanoTime());

	private L1FakeCharacter() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1FakeCharacter();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String name = stringtokenizer.nextToken();

			if (CharacterTable.doesCharNameExist(name) || L1World.getInstance().getPlayer(name) != null) {
				pc.sendPackets(new S_SystemMessage("이미 존재하는 캐릭터 이름입니다"));
				return;
			}
			L1PcInstance newPc = new L1PcInstance();
			newPc.setAccountName("");
			newPc.setId(IdFactory.getInstance().nextId());
			newPc.setName(name);
			newPc.setHighLevel(1);
			newPc.setExp(0);
			newPc.addBaseMaxHp((short) 2000);// 14
			newPc.setCurrentHp(2000);// 14
			newPc.setDead(false);
			newPc.setStatus(0);
			newPc.addBaseMaxMp((short) 2);
			newPc.setCurrentMp(2);
			newPc.getAbility().setBaseStr(16);
			newPc.getAbility().setStr(16);
			newPc.getAbility().setBaseCon(16);
			newPc.getAbility().setCon(16);
			newPc.getAbility().setBaseDex(11);
			newPc.getAbility().setDex(11);
			newPc.getAbility().setBaseCha(13);
			newPc.getAbility().setCha(13);
			newPc.getAbility().setBaseInt(12);
			newPc.getAbility().setInt(12);
			newPc.getAbility().setBaseWis(11);
			newPc.getAbility().setWis(11);
			int ran = _random.nextInt(120);
			if (ran >= 0 && ran < 15) { // 15 남기사가 15프로
				newPc.setClassId(61);
				newPc.setCurrentSprite(61);
				newPc.setType(61);
			} else if (ran >= 15 && ran < 20) { // 5// 여캐릭은 무조건 5프로 왜냐 프리섭은 여캐릭은 거의 안함.
				newPc.setClassId(48);
				newPc.setCurrentSprite(48);
				newPc.setType(48);
			} else if (ran >= 20 && ran < 50) { // 10
				newPc.setClassId(138);
				newPc.setCurrentSprite(138);
				newPc.setType(138);
			} else if (ran >= 50 && ran < 70) { // 5
				newPc.setClassId(37);
				newPc.setCurrentSprite(37);
				newPc.setType(37); 
			} else if (ran >= 70 && ran < 90) {// 10
				newPc.setClassId(2786);
				newPc.setCurrentSprite(2786);
				newPc.setType(2786);
			} else if (ran >= 90 && ran < 120) { // 5
				newPc.setClassId(2796);
				newPc.setCurrentSprite(2796);
				newPc.setType(2796); 
			}

			newPc.setCurrentWeapon(0);
			newPc.setHeading(pc.getHeading());
			newPc.setX(pc.getX());
			newPc.setY(pc.getY());
			newPc.setMap(pc.getMap());

			newPc.setFood(39);
			newPc.setLawful(0);

			newPc.setTitle("");
			newPc.setClanid(0);
			newPc.setClanname("");
			newPc.setClanRank(0);
			newPc.setBonusStats(0); 
			newPc.setElfAttr(0);
			newPc.set_PKcount(0);
			newPc.setExpRes(0);
			newPc.setPartnerId(0);
			newPc.setAccessLevel((short) 0);
			newPc.setGm(false);
			newPc.setMonitor(false);
			newPc.setOnlineStatus(1);
			newPc.setHomeTownId(0);
			newPc.setContribution(0);
			newPc.setHellTime(0);
			newPc.setBanned(false);
			newPc.setKarma(0);
			newPc.setReturnStat(0);

			newPc.refresh();
			newPc.setMoveSpeed(0);
			newPc.setBraveSpeed(0);
			newPc.setGmInvis(false);
			newPc.noPlayerck2 = true;

			ItemInstance item = ItemTable.getInstance().createItem(35);// 수련자의 한손검
			ItemInstance item1 = ItemTable.getInstance().createItem(175);// 수련자의 활
			ItemInstance item2 = ItemTable.getInstance().createItem(120);// 수련자의 지팡이
			ItemInstance item3 = ItemTable.getInstance().createItem(73);// 수련자의 이도류 

			if (newPc.isKnight() || newPc.isCrown()) {// 기사.군주.용기사
				newPc.getInventory().storeItem(item);
				newPc.getInventory().setEquipped(item, true);
			} else if (newPc.isElf()) { // 요정
				newPc.getInventory().storeItem(item1);
				newPc.getInventory().setEquipped(item1, true);
			} else if (newPc.isWizard()) {// 마법사
				newPc.getInventory().storeItem(item2);
				newPc.getInventory().setEquipped(item2, true);
			} else if (newPc.isDarkelf()) { // 다크엘프
				newPc.getInventory().storeItem(item3);
				newPc.getInventory().setEquipped(item3, true);
			}

			L1World.getInstance().storeObject(newPc);
			L1World.getInstance().addVisibleObject(newPc);

			newPc.setNetConnection(null);

			newPc.startObjectAutoUpdate();

		} catch (Exception e) {
			System.out.println(e);
			pc.sendPackets(new S_SystemMessage((new StringBuilder()).append(".무인 [캐릭이름]로 입력해 주세요. ").toString()));
		}
	}

}