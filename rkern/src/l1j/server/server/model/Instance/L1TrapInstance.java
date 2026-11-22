/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.Instance;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.trap.L1Trap;
import l1j.server.server.model.network.core.sc.packet.SC_Trap;
import l1j.server.server.types.Point;

public class L1TrapInstance extends L1Object {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final L1Trap _trap;
	private final Point _baseLoc = new Point();
	private final Point _rndPt = new Point();
	private final int _span;
	private boolean _isEnable = true;
	private final String _nameForView;

	// A persistent trap must not retain a disconnected character or its inventory.
	private final Set<L1PcInstance> _knownPlayers =
			Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private static final Random _random = new Random(System.nanoTime());

	public L1TrapInstance(int id, L1Trap trap, L1Location loc, Point rndPt,
			int span) {
		setId(id);
		_trap = trap;
		getLocation().set(loc);
		_baseLoc.set(loc);
		_rndPt.set(rndPt);
		_span = span;
		_nameForView = "trap";

		resetLocation();
	}

	public L1TrapInstance(int id, L1Location loc) {
		setId(id);
		_trap = L1Trap.newNull();
		getLocation().set(loc);
		_span = 0;
		_nameForView = "trap base";
	}

	public void resetLocation() {
		if (_rndPt.getX() == 0 && _rndPt.getY() == 0) {
			return;
		}
		L1Map map = null;
		for (int i = 0; i < 50; i++) {
			int rndX = _random.nextInt(_rndPt.getX() + 1)
					* (_random.nextInt(2) == 1 ? 1 : -1);
			int rndY = _random.nextInt(_rndPt.getY() + 1)
					* (_random.nextInt(2) == 1 ? 1 : -1);

			rndX += _baseLoc.getX();
			rndY += _baseLoc.getY();

			map = getLocation().getMap();
			if (map.isInMap(rndX, rndY) && map.isPassable(rndX, rndY)) {
				getLocation().set(rndX, rndY);
				break;
			}
		}
	}

	public void enableTrap() {
		_isEnable = true;
	}

	public void disableTrap() {
		_isEnable = false;

		final L1PcInstance[] viewers;
		synchronized (_knownPlayers) {
			viewers = _knownPlayers.toArray(new L1PcInstance[0]);
			_knownPlayers.clear();
		}
		try (var sharedRemove = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
			        SC_RemoveObject.nativeWriter(getId()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
			for (L1PcInstance pc : viewers) {
				if (pc == null) continue;
				pc.removeKnownObject(this);
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedRemove, pc);
			}
		}
	}

	public boolean isEnable() {
		return _isEnable;
	}

	public int getSpan() {
		return _span;
	}

	public void onTrod(L1PcInstance trodFrom) {
		_trap.onTrod(trodFrom, this);
	}

	public void onDetection(L1PcInstance caster) {
		_trap.onDetection(caster, this);
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null)
			return;
		if (perceivedFrom.hasSkillEffect(L1SkillId.GMSTATUS_SHOWTRAPS)) {
			perceivedFrom.addKnownObject(this);
			SC_Trap.send(perceivedFrom, getX(), getY(), getId(), _nameForView);
			_knownPlayers.add(perceivedFrom);
		}
	}
}
