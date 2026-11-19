package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

public class L1FollowerInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	@Override
	public boolean noTarget() {
		L1NpcInstance npc = null;
		L1PcInstance pc = null;
		for (L1Object object : L1World.getInstance().getVisibleObjects(this)) {
			if (object == null)
				continue;
			if (object instanceof L1NpcInstance) {
				npc = (L1NpcInstance) object;
				switch(npc.getNpcTemplate(). get_npcId()){
				case 70740: //디가르뎅의 병사
				case 71093: //조사원
					setParalyzed(true);
					pc = (L1PcInstance) _master;
					if (!pc.getInventory().checkItem(40593)) {
						createNewItem(pc, 40593, 1);
					}
					deleteMe();
					return true;
				case 71094: //엔디아
					setParalyzed(true);
					pc = (L1PcInstance) _master;
					if (!pc.getInventory().checkItem(40582)) {
						createNewItem(pc, 40582, 1);
					}
					deleteMe();
					return true;
				case 71061:
				case 71062:
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32448 && pc.getX() <= 32452) // 모퉁이 모스 주변 좌표
								&& (pc.getY() >= 33048 && pc.getY() <= 33052) && (pc.getMapId() == 440)) {
							setParalyzed(true);
							if (!pc.getInventory().checkItem(40711)) {
								createNewItem(pc, 40711, 1);
								pc.getQuest().set_step(L1Quest.QUEST_CADMUS, 3);
							}
							deleteMe();
							return true;
						}
					}
					break;
				case 71074:
				case 71075:
					// 완전히 지쳐 버린 리자드만파이타
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32731 && pc.getX() <= 32735) // 리자드만장로 주변
			
								&& (pc.getY() >= 32854 && pc.getY() <= 32858) && (pc.getMapId() == 480)) {
							setParalyzed(true);
							if (!pc.getInventory().checkItem(40633)) {
								createNewItem(pc, 40633, 1);
								pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 2);
							}
							deleteMe();
							return true;
						}
					}
					break;
				case 70964:
				case 70957:
					if (getLocation().getTileLineDistance(_master.getLocation()) < 3) {
						pc = (L1PcInstance) _master;
						if ((pc.getX() >= 32917 && pc.getX() <= 32921) // 밧슈 주변
																		// 좌표
								&& (pc.getY() >= 32974 && pc.getY() <= 32978) && (pc.getMapId() == 410)) {
							setParalyzed(true);
							createNewItem(pc, 41003, 1);
							pc.getQuest().set_step(L1Quest.QUEST_ROI, 0);
							deleteMe();
							return true;
						}
					}
					break;
				}
			}
		}

		if (_master.isDead() || getLocation().getTileLineDistance(_master.getLocation()) > 10) {
			setParalyzed(true);
			spawn(getNpcTemplate().get_npcId(), getX(), getY(), getHeading(), getMapId());
			deleteMe();
			return true;
		} else if (_master != null && _master.getMapId() == getMapId()) {
			if (getLocation().getTileLineDistance(_master.getLocation()) > 2) {
				setSleepTime(setDirectionMove(moveDirection(_master.getX(), _master.getY())));
			}
		}
		return false;
	}
	
	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}

	public L1FollowerInstance(L1Npc template, L1NpcInstance target, L1PcInstance master) {
		super(template);

		_master = master;
		setId(IdFactory.getInstance().nextId());

		setMaster(master);
		setX(target.getX());
		setY(target.getY());
		setMap(target.getMapId());
		setHeading(target.getHeading());
		setLightSize(target.getLightSize());
		setMaxHp(500);
		setCurrentHp(500);

		target.setParalyzed(true);
		target.deleteMe();

		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}

		startAI();
		master.addFollower(this);
	}

	@Override
	public synchronized void deleteMe() {
		_master.removeFollower(this);
		super.deleteMe();
	}

	@Override
	public void onAction(L1PcInstance pc) {
		CAttackAction.attackPcNpc(pc, this, true);
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (isDead()) {
			return;
		}
		switch(getNpcTemplate(). get_npcId()){
		case 71093:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "searcherk2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "searcherk4");
			}
			break;
		case 71094:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "endiaq2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "endiaq4");
			}
			break;
		case 71062:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "kamit2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "kamit1");
			}
			break;
		case 71075:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "llizard2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "llizard1a");
			}
			break;
		case 70957:
			if (_master.equals(player)) {
				SC_NPCTalkReturn.send(player, getId(), "roi2");
			} else {
				SC_NPCTalkReturn.send(player, getId(), "roi2");
			}
			break;
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		l1j.server.server.model.network.core.sc.packet.SC_FollowerPack.send(perceivedFrom, this);
	}

	private void createNewItem(L1PcInstance pc, int item_id, int count) {
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		item.setCount(count);
		if (item != null) {
			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				pc.getInventory().storeItem(item);
			} else {
				L1World.getInstance().getInventory(pc.getX(), pc.getY(), pc.getMapId()).storeItem(item);
			}
			SC_ServerMessage.send(pc, 403, item.getLogName());
		}
	}

	public void spawn(int npcId, int X, int Y, int H, short Map) {
		L1Npc l1npc = NpcTable.getInstance().getTemplate(npcId);
		if (l1npc != null) {
			try {
				L1NpcInstance mob = NpcTable.getInstance().newNpcInstance(l1npc);
				if (mob == null) return;

				mob.setId(IdFactory.getInstance().nextId());
				mob.setX(X);
				mob.setY(Y);
				mob.setHomeX(X);
				mob.setHomeY(Y);
				mob.setMap(Map);
				mob.setHeading(H);
				
				L1World.getInstance().storeAndEnterWorld(mob);
				
				if (mob instanceof L1QuestInstance newnpc) {
					newnpc.onNpcAI();
					newnpc.getLight().turnOnOffLight();
					newnpc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
