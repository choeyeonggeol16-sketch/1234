package l1j.server.server.model.Instance;
  
import java.util.Random;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_MoveCharPacket;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillSound;

public class FakePcInstance extends L1PcInstance {
	private static Random _random = new Random(System.nanoTime());
	public static final int DEFAULT_INTERVAL = 1000;

	enum ACTION_TYPE {
		ACTION_IDLE, ACTION_WALK, ACTION_TELEPORT, ACTION_CHAT, ACTION_USE_ITEM
	}

	private static final String[] _chatList1 = { "님", "ㅋㅋㅋ", "ㅋ", "하이요", "?", "ㅇㅇ", "ㅎㅇ", "ㅇㄷ?", "ㄱㄱ", "ㅇㅋ", "잠시", "기달",
			"ㄱㄷ", "ㅈㅅ", "ㄴㄴ" };

	private static final ACTION_TYPE[] _actionList = { ACTION_TYPE.ACTION_IDLE, ACTION_TYPE.ACTION_WALK,
			ACTION_TYPE.ACTION_TELEPORT, ACTION_TYPE.ACTION_CHAT, ACTION_TYPE.ACTION_USE_ITEM };
	// private static final int[] _actionRatio = {35, 32, 11, 11, 11};
	private static final int[] _actionRatio = { 35, 43, 11, 0, 11 };

	private ACTION_TYPE _actionType;
	private int _actionCount;

	private int _spawnX;
	private int _spawnY;
	private int _spawnHeading;
	private int _spawnMapId;
	private int _actionInterval;
	private int _lastDollTime;
	private boolean _isPolymorph = false;

	public void setPolymorph() {
		_isPolymorph = true;
	}

	private static final long serialVersionUID = 1L;

	public FakePcInstance() {
		super();
		_actionType = ACTION_TYPE.ACTION_IDLE;
		_actionCount = 0;
		_actionInterval = DEFAULT_INTERVAL;
		setFake();
	}

	public FakePcInstance(int x, int y, int heading, int mapId) {
		super();
		_actionType = ACTION_TYPE.ACTION_IDLE;
		_actionCount = 0;
		_actionInterval = DEFAULT_INTERVAL;
		setFake();

		_spawnX = x;
		_spawnY = y;
		_spawnHeading = heading;
		_spawnMapId = mapId;

	}

	public void SetSpawnInfo(int x, int y, int heading, int mapId) {
		_spawnX = x;
		_spawnY = y;
		_spawnHeading = heading;
		_spawnMapId = mapId;
	}

	class ActionTimer implements Runnable {
		boolean _active;

		public ActionTimer() {
			_active = true;
		}

		@Override
		public void run() {
			if (!_active) {
				return;
			}

			FakePcInstance.this.DoAction();

			if (_active) {
				GeneralThreadPool.getInstance().schedule(this, _actionInterval);
			}
		}

		public void cancel() {
			_active = false;
		}
	}

	private void OnIdle() {
		if ((int) (System.currentTimeMillis() / 1000) - _lastDollTime > 2000) {
			// UseDoll();
		}

		if (++_actionCount > 20) {
			changeAction();
			_actionCount = 0;
		}
	}

	private void OnWalk() {
		int heading = getHeading();
		int ori_Heading = heading;

		int headingFindCount = 0;
		boolean clockwise = _random.nextBoolean();

	//	long _actionInterval = this.getCurrentSpriteInterval(EActionCodes.fromInt(this.getCurrentWeapon() + 1));
		//_actionInterval = getAcceleratorChecker().getRightInterval(ACT_TYPE.MOVE);

		while (true) {
			if (getMap().isPassable(getX(), getY(), heading) == true) {
				int locx = getX();
				int locy = getY();

				switch (heading) {
				case 1:
					locx++;
					locy--;
					break;
				case 2:
					locx++;
					break;
				case 3:
					locx++;
					locy++;
					break;
				case 4:
					locy++;
					break;
				case 5:
					locx--;
					locy++;
					break;
				case 6:
					locx--;
					break;
				case 7:
					locx--;
					locy--;
					break;
				case 0:
					locy--;
					break;
				}
				if (getMap().isSafetyZone(locx, locy)) {
					getMap().setPassable(getLocation(), true);
					getLocation().set(locx, locy);
					getMap().setPassable(getLocation(), false);

					setHeading(heading);

					L1WorldTraps.getInstance().onPlayerMoved(this);

					broadcastPacket(S_MoveCharPacket.get(this, this.getHeading()));

					if (ori_Heading == heading && _random.nextInt(3) == 0) {
						setHeading(_random.nextInt(8));
					}

					break;
				}
			}

			if (++headingFindCount >= 8) {
				break;
			}

			if (clockwise) {
				++heading;
				if (heading > 7) {
					heading = 0;
				}
			} else {
				--heading;
				if (heading < 0) {
					heading = 7;
				}
			}
		}

		if (++_actionCount > 20) {
			changeAction();
			_actionCount = 0;
		}
	}

	private void OnUseItem() {
		if (_actionCount == 1) {
			if (_random.nextBoolean()) {
				broadcastPacket(S_SkillSound.get(getId(), 189));
			} else {
				broadcastPacket(S_SkillSound.get(getId(), 191));
				broadcastPacket(S_SkillHaste.get(getId(), 1, 0));
				setMoveSpeed(1);
				setSkillEffect(SkillId.상태_1단_가속, 300 * 1000);
			}
		}

		if (++_actionCount > 5) {
			changeAction();
			_actionCount = 0;
		}
	}

	private void OnTeleport() {
		if (_actionCount == 0) { 
			this.teleport(_spawnX, _spawnY, (short)_spawnMapId, _spawnHeading, 169, true, false);
		}

		if (++_actionCount > 20) {
			changeAction();
			_actionCount = 0;
		}
	}

	private void OnChat() {
		if (_actionCount == 2) {
			String chatText = _chatList1[_random.nextInt(_chatList1.length)];
			S_ChatPacket s_chatpacket = new S_ChatPacket(this, chatText, Opcodes.S_OPCODE_NORMALCHAT, 0);

			for (L1PcInstance listner : L1World.getInstance().getRecognizePlayer(this)) {
				if (!listner.excludes(this.getName())) {
					listner.sendPackets(s_chatpacket);
				}
			}
		}

		if (++_actionCount > 20) {
			changeAction();
			_actionCount = 0;
		}
	}

	private void changeAction() {
		do {
			int ratio = _random.nextInt(100);
			int totalRatio = 0;

			for (int i = 0; i < _actionRatio.length; ++i) {
				totalRatio += _actionRatio[i];

				if (totalRatio > ratio) {
					_actionType = _actionList[i];
					break;
				}
			}
		} while (_actionType == ACTION_TYPE.ACTION_TELEPORT && getX() == _spawnX && getY() == _spawnY
				&& getMapId() == _spawnMapId);

		_actionInterval = DEFAULT_INTERVAL;
	}

	private void DoAction() {
		switch (_actionType) {
		case ACTION_IDLE:
			OnIdle();
			break;

		case ACTION_WALK:
			OnWalk();
			break;

		case ACTION_TELEPORT:
			OnTeleport();
			break;

		case ACTION_CHAT:
			OnChat();
			break;

		case ACTION_USE_ITEM:
			OnUseItem();
			break;
		}
	}

	ActionTimer _actionTimer = null;

	public void startAction() {
		if (_actionTimer == null) { 
			if (_isPolymorph) {
				Polymorph();
			}

			_actionTimer = new ActionTimer();
			GeneralThreadPool.getInstance().execute(_actionTimer);
		}
	}

	private void stopAction() {
		if (_actionTimer != null) {
			_actionTimer.cancel();
			_actionTimer = null;
		}

	}

	void Polymorph() {
		int probability = _random.nextInt(100);
		if (probability < 40) { 
			L1PolyMorph.doPoly(this, 240, 0,
					L1PolyMorph.MORPH_BY_LOGIN);
		}
	}

	 

	@Override
	public void logout() {
		stopAction();
		super.logout();
	}

	public static FakePcInstance load(String charName) {
		FakePcInstance result = null;
		try {
			result = CharacterTable.getInstance().loadFakeCharacter(charName);
		} catch (Exception e) {
		//	_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		return result;
	}
}
