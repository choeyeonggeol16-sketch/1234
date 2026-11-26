/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.npc.spawn.group;

import l1j.server.server.model.L1World;
import l1j.server.server.datatables.CObjectDB;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;

// Referenced classes of package l1j.server.server.model:
// L1MobGroupSpawn

public class L1MobGroupSpawn {

	private static final L1MobGroupSpawn INSTANCE = new L1MobGroupSpawn();

	private L1MobGroupSpawn() {
	}

	public static L1MobGroupSpawn getInstance() {
		return INSTANCE;
	}

	public void doSpawn(L1NpcInstance leader, int groupId,
			boolean isRespawnScreen, boolean isInitSpawn) {

		L1MobGroup mobGroup = MobGroupTable.getInstance().getTemplate(groupId);
		if (mobGroup == null) {
			return;
		}

		final L1MobGroupInfo mobGroupInfo = new L1MobGroupInfo();
		mobGroupInfo.setRemoveGroup(mobGroup.isRemoveGroupIfLeaderDie());
		mobGroupInfo.addMember(leader);

		for (int slot = 0; slot < L1MobGroup.MINION_SLOTS; slot++) {
			final int npcId = mobGroup.getMinionId(slot);
			final int count = mobGroup.getMinionCount(slot);
			if (npcId <= 0 || count <= 0) continue;
			for (int i = 0; i < count; i++) {
				spawn(leader, npcId, mobGroupInfo, isRespawnScreen, isInitSpawn);
			}
		}
	}

	private void spawn(final L1NpcInstance leader, final int npcId,
			final L1MobGroupInfo group, final boolean isRespawnScreen,
			final boolean isInitSpawn) {
		L1NpcInstance mob = null;
		try {
			L1Npc l1npc = CObjectDB.GetNPC(npcId);
			if (l1npc == null) {
				return;
			}
			mob = CObjectDB.CreateNPC(l1npc);		
			mob.setId(IdFactory.getInstance().nextId());
			mob.setHeading(leader.getHeading());
			mob.setMap(leader.getMapId());
			mob.setMovementDistance(leader.getMovementDistance());
			mob.setRest(leader.isRest());

			final ThreadLocalRandom random = ThreadLocalRandom.current();
			mob.setX(leader.getX() + random.nextInt(5) - 2);
			mob.setY(leader.getY() + random.nextInt(5) - 2);
			if (!isDoSpawn(mob, isRespawnScreen)) {
				mob.setX(leader.getX());
				mob.setY(leader.getY());
			}
			mob.setHomeX(mob.getX());
			mob.setHomeY(mob.getY());

			if (mob instanceof L1MonsterInstance) {
				((L1MonsterInstance) mob).initHideForMinion(leader);
			}

			mob.setSpawn(leader.getSpawn());
			mob.setRespawn(leader.isReSpawn());
			mob.setSpawnNumber(leader.getSpawnNumber());

			if (mob instanceof L1MonsterInstance) {
				if (mob.getMapId() == 666) {
					((L1MonsterInstance) mob).set_storeDroped(true);
				}
			}

			// Publish membership before perception and the first AI tick.
			group.addMember(mob);
			L1World.getInstance().storeAndEnterWorld(mob);

			if (mob instanceof L1MonsterInstance) {
				if (!isInitSpawn && mob.getHiddenStatus() == 0) {
					mob.onNpcAI();
				}
			}
			mob.refreshLight();
			mob.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
		} catch (Exception e) {
			if (mob != null && mob.getMobGroupInfo() == group
					&& L1World.getInstance().findObject(mob.getId()) != mob) {
				group.removeMember(mob);
			}
			e.printStackTrace();
		}
	}

	private boolean isDoSpawn(L1NpcInstance mob, boolean isRespawnScreen) {
		if (mob.getMap().isInMap(mob.getLocation())
				&& mob.getMap().isPassable(mob.getLocation())) {
			if (isRespawnScreen) {
				return true;
			}
			if (L1World.getInstance().getVisiblePlayer(mob).size() == 0) {
				return true;
			}
		}
		return false;
	}

}
