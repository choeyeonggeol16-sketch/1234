package l1j.server.RaidSystem.Objects;

import java.util.ArrayList;
import l1j.server.RaidSystem.Dragon_Raid_Object;
import l1j.server.RaidSystem.Dragon_Raid_Skill_Combo;
import l1j.server.RaidSystem.Dragon_Raid_Spawn;
import l1j.server.RaidSystem.Dragon_Raid_Type;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class Dragon_Raid_Antaras extends Dragon_Raid_Object {
	private static final ServerBasePacket[] s_startMessages = new ServerBasePacket[]{
		new S_ServerMessage(1570),
		new S_ServerMessage(1571),
		new S_ServerMessage(1572),
		new S_ServerMessage(1573),
		new S_ServerMessage(1574),
		new S_SystemMessage("시스템 메시지 : 안타라스를 공략할 수 있습니다."),
	};

	public Dragon_Raid_Antaras(L1PcInstance owner, Dragon_Raid_Type type){
		super(owner, type);
	}

	@Override
	protected void setNormalSpawn(Dragon_Raid_Spawn spawn) {
		_npcList.add(spawn.spawn(getCopyMapId()));
	}

	@Override
	protected void setRectSpawn(Dragon_Raid_Spawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnRectangle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setCircleSpawn(Dragon_Raid_Spawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnCircle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setBossSpawn(Dragon_Raid_Spawn spawn) {
		_bossSpawn = spawn;
	}

	@Override
	public void dispose() {
		super.dispose();

		if(_npcList != null){
			_npcList.clear();
			_npcList = null;
		}
	}

	@Override
	protected void readyRaid() {
		L1PcInstance 	pc 		= null;
		int 			size 	= 0;
		int 			cpMap	= 0;
		try{
			cpMap				= getCopyMapId();
			L1PcInstance[] pcs	= null;
			boolean isInArea 	= true;

			while(isInArea) {
				Thread.sleep(500);
				pcs = _users.toArray(new L1PcInstance[_users.size()]);
				for(int i=0; i<pcs.length; i++) {
					pc = pcs[i];
					if(pc == null) {
						continue;
					}
					if(pc.getMapId() != cpMap) {
						delUser(pc);						
						continue;
					}

					if(_bossSpawn.isInArea(pc)) {
						isInArea = false;
						break;
					}
				}
			}

			size = _users.size();
			for(int i=0; i<s_startMessages.length; i++) {
				Thread.sleep(Dragon_Raid_Load_Manager.MRS_MESSAGE_DELAY);
				for(int j=0; j<size; j++){
					pc = _users.get(j);
					if(pc != null && pc.getMapId() == cpMap) {
						pc.sendPackets(s_startMessages[i], false);

						if(i == 0) {
							pc.sendPackets(s_quakeDisplay, false);
						}					
					}
				}
			}
			if(_bossSpawn != null)
				_boss = _bossSpawn.spawnBoss(getCopyMapId());

			_state = RS_START;
			GeneralThreadPool.getInstance().schedule(this, Dragon_Raid_Load_Manager.MRS_MESSAGE_DELAY);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	@Override
	protected void runRaid() {
		try{
			int csize				= 0;
			int count				= 0;
			int phase				= 0;
			int hpRatio             = 0;
			Dragon_Raid_Skill_Combo combo	= null;
			if(_combos != null) {
				csize = _combos.size();
			}
			while((_state & RS_START) > 0) {
				Thread.sleep(Dragon_Raid_Load_Manager.MRS_THREAD_CLOCK);

				// 한번 더 검사.
				if((_state & RS_START) == 0) {
					return;
				}
				if(!isInRaid()) {
					resetRaid();
					return;
				}	
				if(_boss == null) {
					continue;
				}
				if(_boss.isDead()) {
					successRaid();
					waitClose();
					closeRaid();
					return;
				}
				if(csize != 0 && (++count % Dragon_Raid_Load_Manager.MRS_CB_COUNT) == 0) {
					combo = _combos.get(_rnd.nextInt(csize));
					phase = combo.getPhase();	
					hpRatio = (_boss.getCurrentHp() * 100) / _boss.getMaxHp();
					if (hpRatio <= phase) {	
						combo.action(_boss, _users);
						continue;
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
