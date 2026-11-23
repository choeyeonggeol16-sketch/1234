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
package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1NpcInstance;

// Referenced classes of package l1j.server.server.model:
// L1MobGroupInfo

public class L1MobGroupInfo {

	private final List<L1NpcInstance> _membersList =
			new ArrayList<L1NpcInstance>();

	// The NPC owner observes leader changes from respawn/delete threads.
	private volatile L1NpcInstance _leader;

	public L1MobGroupInfo() {
	}

	/**
	 * The group identity is the CURRENT leader's object ID. When the
	 * leader changes, all members must see the same identity; otherwise
	 * the existing group-help event compares stale IDs and ignores allies.
	 *
	 * Membership mutation is rare (spawn/death), never on the NPC hot path.
	 */
	public synchronized void setLeader(final L1NpcInstance npc) {
		_leader = npc;
		final int groupId = npc != null ? npc.getId() : 0;
		for (int i = 0; i < _membersList.size(); i++) {
			_membersList.get(i).setMobGroupId(groupId);
		}
	}

	public L1NpcInstance getLeader() {
		return _leader;
	}

	public boolean isLeader(final L1NpcInstance npc) {
		return npc != null && npc == _leader;
	}

	private L1Spawn _spawn;

	public synchronized void setSpawn(L1Spawn spawn) {
		_spawn = spawn;
	}

	public synchronized L1Spawn getSpawn() {
		return _spawn;
	}

	public synchronized void addMember(final L1NpcInstance npc) {
		if (npc == null) {
			throw new NullPointerException();
		}

		if (_membersList.contains(npc)) {
			return;
		}

		if (_membersList.isEmpty()) {
			setLeader(npc);
			if (npc.isReSpawn()) {
				setSpawn(npc.getSpawn());
			}
		}

		_membersList.add(npc);
		npc.setMobGroupInfo(this);
		npc.setMobGroupId(_leader != null ? _leader.getId() : 0);
	}

	public synchronized int removeMember(final L1NpcInstance npc) {
		if (npc == null) {
			throw new NullPointerException();
		}

		// A late duplicate delete must not detach a newly rejoined NPC or
		// disturb a different leader's group.
		if (!_membersList.remove(npc)) {
			return _membersList.size();
		}

		npc.setMobGroupInfo(null);
		npc.setMobGroupId(0);

		if (npc != _leader) {
			return _membersList.size();
		}

		if (_isRemoveGroup && !_membersList.isEmpty()) {
			// The original L1 remove-group policy disables minion respawn.
			// Release member IDs and the retained list as well, otherwise a
			// group object can retain removed NPCs until its own GC.
			for (int i = 0; i < _membersList.size(); i++) {
				final L1NpcInstance minion = _membersList.get(i);
				minion.setMobGroupInfo(null);
				minion.setMobGroupId(0);
				minion.setSpawn(null);
				minion.setRespawn(false);
			}
			_membersList.clear();
			_leader = null;
			return 0;
		}

		// L1 leader succession is the counterpart of NC private-group
		// identity updates. The rest of the group follows this new leader.
		setLeader(_membersList.isEmpty() ? null : _membersList.get(0));
		return _membersList.size();
	}

	public synchronized int getNumOfMembers() {
		return _membersList.size();
	}


	private volatile boolean _isRemoveGroup;

	public boolean isRemoveGroup() {	return _isRemoveGroup;	}

	public void setRemoveGroup(boolean flag) {	_isRemoveGroup = flag;	}
}
