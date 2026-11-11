package l1j.server.TowerOfDominance.BossController;

import java.util.ArrayList;
import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class DominanceFloorLv11 implements Runnable {

	private boolean _event = false;
	private boolean _event2 = false;
	private boolean _event3 = false;

	private int _npcid;
	private int _mapx;
	private int _mapy;
	private int _mapid;
	private boolean _isment;
	private String _ment;
	private boolean _effectuse;
	private int _effid;

	private boolean _EVENT1 = false;
	private boolean _END = false;

	private int stage = 1;

	private static final int EVENT = 1;
	private static final int EVENT2 = 2;
	private static final int END = 3;

	private volatile boolean Running = false;
    private java.util.concurrent.Future<?> eventTask;
    private boolean eventRunning;
    private long eventGeneration;

	private int Time = 3600;

	public DominanceFloorLv11(int bossid, int x, int y, int mapid, boolean mentuse, String ment, boolean effectuse,
			int effectid) {
		_npcid = bossid;
		_mapx = x;
		_mapy = y;
		_mapid = mapid;
		_isment = mentuse;
		_ment = ment;
		_effectuse = effectuse;
		_effid = effectid;
	}

	public synchronized void setReady(boolean flag) {
        Running = flag;
        if (!flag) {
            eventGeneration++;
            if (eventTask != null) eventTask.cancel(true);
        }
    }

	public boolean isReady() {
		return Running;
	}

	@Override
	public void run() {
        final long generation;
        synchronized (this) { generation = eventGeneration; }
        runEvent(generation);
    }

    private void runEvent(long generation) {
        synchronized (this) {
            if (generation != eventGeneration || !Running || eventRunning) return;
            eventRunning = true;
        }
        try {
		while (Running && !Thread.currentThread().isInterrupted()) {
			try {
				TimeCheck();
				switch (stage) {
				case EVENT:
					for (L1PcInstance pc : PcCK()) {
						if ((pc.getX() >= 32721 && pc.getX() <= 32753) && (pc.getY() >= 32874 && pc.getY() <= 32904)) {
							pc.start_teleport(32713, 32913, _mapid, pc.getHeading(), 169, true, false);
						}
					}
					spawn(32723, 32904, (short) _mapid, 0, 8500132, ActionCodes.ACTION_Hide);
					spawn(32723, 32903, (short) _mapid, 0, 8500133, ActionCodes.ACTION_Hide);
					spawn(32722, 32902, (short) _mapid, 0, 8500134, ActionCodes.ACTION_Hide);
					spawn(32721, 32902, (short) _mapid, 0, 8500135, ActionCodes.ACTION_Hide);
					spawn(32722, 32903, (short) _mapid, 0, 8500301, ActionCodes.ACTION_Hide);
					for (L1PcInstance pc : PcCK()) {
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fV$27679");
					}
					spawn(32713, 32902, (short) _mapid, 0, 8500130, ActionCodes.ACTION_SwordWalk);
					spawn(32722, 32913, (short) _mapid, 0, 8500131, ActionCodes.ACTION_SwordWalk);
					Thread.sleep(7000);
					for (L1PcInstance pc : PcCK()) {
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY$27689");
					}
					stage = 2;
				case EVENT2:
					if (_EVENT1 == true) {
						Thread.sleep(15000);
						for (L1PcInstance pc : PcCK()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY$27699");
						}
						Thread.sleep(3000);
						spawn(_mapx, _mapy, (short) _mapid, 0, 8500137, ActionCodes.ACTION_Appear);
						L1Object effect = L1World.getInstance().findNpc(8500137);
						if (effect != null && effect instanceof L1NpcInstance && !(effect instanceof L1DollInstance)) {
							L1NpcInstance npc = (L1NpcInstance) effect;
							deleteNpc(npc);
						}
						Thread.sleep(10000);
						spawn(_mapx, _mapy, (short) _mapid, 0, _npcid, ActionCodes.ACTION_Walk);
						stage = 3;
						break;
					}
					Object_Check();
					break;
				case END:
					if (_END == true) {
						for (L1PcInstance pc : PcCK()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY$27707");
						}
						Thread.sleep(7000);
						for (L1PcInstance pc : PcCK()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY$27708");
						}
						Running = false;
						break;
					}
					Object_Check();
					break;
				default:
					break;
				}
			} catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                Running = false;
            } catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    Running = false;
                }
			}
		}
        } finally {
            try { reset(); }
            finally {
                synchronized (this) { Running = false; eventRunning = false; }
            }
        }
	}

	public synchronized void Start() {
        if (Running || eventRunning) return;
        reset();
        Running = true;
        final long generation = ++eventGeneration;
        try { eventTask = ThreadPool.getInstance().scheduleBlocking(() -> runEvent(generation), 5000); }
        catch (RuntimeException failure) { Running = false; throw failure; }
		if (!Config.STANDBY_SERVER) {
			if (_isment) {
				SC_SystemMessage.broadcast(_ment);
				SC_PacketBox.broadcastGreen(_ment);
			}

			int effect = 0;
			if (_effectuse) {
				if (_effid > 0)
					effect = _effid;
				for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
					if (pc.isPrivateShop() || !pc.is_world())
						continue;
					if (effect > 0)
						SC_DisplayEffect.send(pc, effect);
				}
//				if (effect > 0)
//					effect.clear();
			}
		}
	}

	private void TimeCheck() {
		if (Time > 0) {
			Time--;
		}
		if (Time == 0) {
			Running = false;
		}
	}

	private void Object_Check() {
		L1NpcInstance mob = null;
		for (L1Object object : L1World.getInstance().getVisibleObjects(_mapid).values()) {
			if (object instanceof L1MonsterInstance) {
				mob = (L1NpcInstance) object;
				int npc = mob.getNpcTemplate().get_npcId();
				switch (npc) {
				case 8500130:
					if (mob != null && mob.isDead() && _event == false) {
						for (L1PcInstance pc : PcCK()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fV$27688");
						}
						_event = true;
					}
					break;
				case 8500131:
					if (mob != null && mob.isDead() && _event2 == false) {
						for (L1PcInstance pc : PcCK()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY$27698");
						}
						_event2 = true;
					}
					break;
				default:
					break;
				}
				if (npc == _npcid) {
					if (mob != null && mob.isDead() && _event3 == false) {
						_event3 = true;
					}
				}
			}
		}
		if (_event == true && _event2 == true) {
			L1Object target = L1World.getInstance().findNpc(8500301);
			if (target != null && target instanceof L1NpcInstance && !(target instanceof L1DollInstance)) {
				L1NpcInstance npc = (L1NpcInstance) target;
				deleteNpc(npc);
			}
			L1Object passlock = L1World.getInstance().findNpc(8500132);
			if (passlock != null && passlock instanceof L1NpcInstance && !(passlock instanceof L1DollInstance)) {
				L1NpcInstance npc = (L1NpcInstance) passlock;
				deleteNpc(npc);
			}
			L1Object passlock2 = L1World.getInstance().findNpc(8500133);
			if (passlock2 != null && passlock2 instanceof L1NpcInstance && !(passlock2 instanceof L1DollInstance)) {
				L1NpcInstance npc = (L1NpcInstance) passlock2;
				deleteNpc(npc);
			}
			L1Object passlock3 = L1World.getInstance().findNpc(8500134);
			if (passlock3 != null && passlock3 instanceof L1NpcInstance && !(passlock3 instanceof L1DollInstance)) {
				L1NpcInstance npc = (L1NpcInstance) passlock3;
				deleteNpc(npc);
			}
			L1Object passlock4 = L1World.getInstance().findNpc(8500135);
			if (passlock4 != null && passlock4 instanceof L1NpcInstance && !(passlock4 instanceof L1DollInstance)) {
				L1NpcInstance npc = (L1NpcInstance) passlock4;
				deleteNpc(npc);
			}
			_EVENT1 = true;
		}
		if (_event3 == true) {
			_event3 = false;
			_END = true;
		}
	}

	private void reset() {
		L1Object dominance = L1World.getInstance().findNpc(8500301);
		if (dominance != null && dominance instanceof L1NpcInstance && !(dominance instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) dominance;
			deleteNpc(npc);
		}
		L1Object guarder = L1World.getInstance().findNpc(8500130);
		if (guarder != null && guarder instanceof L1NpcInstance && !(guarder instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) guarder;
			deleteNpc(npc);
		}
		L1Object guarder2 = L1World.getInstance().findNpc(8500131);
		if (guarder2 != null && guarder2 instanceof L1NpcInstance && !(guarder2 instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) guarder2;
			deleteNpc(npc);
		}
		L1Object passlock = L1World.getInstance().findNpc(8500132);
		if (passlock != null && passlock instanceof L1NpcInstance && !(passlock instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) passlock;
			deleteNpc(npc);
		}
		L1Object passlock2 = L1World.getInstance().findNpc(8500133);
		if (passlock2 != null && passlock2 instanceof L1NpcInstance && !(passlock2 instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) passlock2;
			deleteNpc(npc);
		}
		L1Object passlock3 = L1World.getInstance().findNpc(8500134);
		if (passlock3 != null && passlock3 instanceof L1NpcInstance && !(passlock3 instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) passlock3;
			deleteNpc(npc);
		}
		L1Object passlock4 = L1World.getInstance().findNpc(8500135);
		if (passlock4 != null && passlock4 instanceof L1NpcInstance && !(passlock4 instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) passlock4;
			deleteNpc(npc);
		}
		L1Object boss = L1World.getInstance().findNpc(_npcid);
		if (boss != null && boss instanceof L1NpcInstance && !(boss instanceof L1DollInstance)) {
			L1NpcInstance npc = (L1NpcInstance) boss;
			deleteNpc(npc);
		}
	}

	public ArrayList<L1PcInstance> PcCK() {
		ArrayList<L1PcInstance> _pc = new ArrayList<L1PcInstance>();
		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getMapId() == _mapid)
				_pc.add(pc);
		}
		return _pc;
	}

	private static void spawn(int x, int y, short MapId, int Heading, int npcId, int actioncode) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			int tryCount = 0;
			do {
				tryCount++;
				npc.setX(x);
				npc.setY(y);
				if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
					break;
				}
				Thread.sleep(1);
			} while (tryCount < 50);
			if (tryCount >= 50) {
				npc.getLocation().forward(Heading);
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(Heading);
			if (npcId == 8500301 || npcId >= 8500132 && npcId <= 8500135) {
				npc.setPassObject(false);
			}
			for (L1PcInstance pc
			        : L1World.getInstance()
			                .getVisiblePlayer(npc)) {

			    npc.onPerceive(pc);

			    SC_DoActionGFX.send(
			            pc.getNetConnection(),
			            npc.getId(),
			            actioncode);
			}

			L1World.getInstance().storeAndEnterWorld(npc);

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);

		} catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void deleteNpc(L1NpcInstance npc) {
		if (npc.getNpcId() == 8500301) {
			
			for (L1PcInstance pc
			        : L1World.getInstance()
			                .getVisiblePlayer(npc)) {

			    npc.onPerceive(pc);

			    SC_DoActionGFX.send(
			            pc.getNetConnection(),
			            npc.getId(),
			            ActionCodes.ACTION_On);
			}
		}
		npc.deleteMe();
	}
}
