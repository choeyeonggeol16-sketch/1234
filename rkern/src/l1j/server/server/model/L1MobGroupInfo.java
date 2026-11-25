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
	private int _nextFormationIndex;

	public L1MobGroupInfo() {
	}

	/** One shared leader publication; no per-member identity rewrite. */
	public synchronized void setLeader(final L1NpcInstance npc) {
		_leader = npc;
	}

	public int getGroupId() {
		final L1NpcInstance leader = _leader;
		return leader == null ? 0 : leader.getId();
	}

	public static void onDelete(final L1NpcInstance npc) {
		final L1MobGroupInfo group = npc.getMobGroupInfo();
		if (group == null) {
			if (npc.isReSpawn()) npc.onDecay(true);
		} else if (group.removeMember(npc) == 0) {
			npc.setMobGroupInfo(null);
			if (npc.isReSpawn()) npc.onDecay(false);
		}
	}

	public static boolean isFollower(final L1NpcInstance npc) {
		final L1MobGroupInfo group = npc.getMobGroupInfo();
		return group != null && group.getActiveLeader() != npc;
	}

	public L1NpcInstance getLeader() {
		return _leader;
	}

	/**
	 * On an ordinary L1 group, a dead leader is replaced as soon as a
	 * follower needs to move, not minutes later when the corpse is deleted.
	 * Existing remove-group-on-leader-death policy is not overridden.
	 *
	 * Live leader: one volatile read, no monitor or allocation on hot path.
	 * Dead leader: one synchronized O(members) election per transition.
	 */
	public L1NpcInstance getActiveLeader() {
		L1NpcInstance leader = _leader;
		if (leader == null || _isRemoveGroup || active(leader)) {
			return leader;
		}
		synchronized (this) {
			leader = _leader;
			if (leader == null || _isRemoveGroup || active(leader)) {
				return leader;
			}
			for (int i = 0; i < _membersList.size(); i++) {
				final L1NpcInstance candidate = _membersList.get(i);
				if (candidate != leader && active(candidate)) {
					setLeader(candidate);
					return candidate;
				}
			}
			return leader;
		}
	}

	private static boolean active(final L1NpcInstance npc) {
		return npc != null
				&& !npc.isDead()
				&& !npc.isDestroyed()
				&& npc.getCurrentHp() > 0;
	}

	public boolean isLeader(final L1NpcInstance npc) {
		return npc != null && npc == _leader;
	}

	private CNpcMaker _spawn;

	public synchronized void setSpawn(CNpcMaker spawn) {
		_spawn = spawn;
	}

	public synchronized CNpcMaker getSpawn() {
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
		npc.setFormationIndex(_nextFormationIndex++);
		npc.setMobGroupId(0);
		npc.setMobGroupInfo(this);
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
