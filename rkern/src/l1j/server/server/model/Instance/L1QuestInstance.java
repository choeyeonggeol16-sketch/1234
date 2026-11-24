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

import l1j.server.server.model.character.action.CAttackAction;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.quest.CQuestSystem;

import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.templates.L1Npc;

import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;
public class L1QuestInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
    private boolean followerClaimed;

    private synchronized boolean claimFollower() {
        if (followerClaimed || isDestroyed() || isDead()) return false;
        followerClaimed = true;
        return true;
    }

    private synchronized void releaseFollowerClaim() {
        if (!isDestroyed()) followerClaimed = false;
    }

	public L1QuestInstance(L1Npc template) {
		super(template);
		_restCallCount = new AtomicInteger(0);
	}

	@Override
	public void onNpcAI() {
		int npcId = getNpcTemplate().get_npcId();
		if (isAiRunning()) {
			return;
		}
		if (npcId == 71075 || npcId == 70957 || npcId == 81209) {
			return;
		} else {
			setActived(false);
			startAI();
		}
	}

	@Override
	public void onAction(L1PcInstance pc) {
		CAttackAction.attackPcNpc(pc, this, true);
	}

	@Override
	public void onTalkAction(L1PcInstance pc) {
		int pcX = pc.getX();
		int pcY = pc.getY();
		int npcX = getX();
		int npcY = getY();

		if (pcX == npcX && pcY < npcY) {
			setHeading(0);
		} else if (pcX > npcX && pcY < npcY) {
			setHeading(1);
		} else if (pcX > npcX && pcY == npcY) {
			setHeading(2);
		} else if (pcX > npcX && pcY > npcY) {
			setHeading(3);
		} else if (pcX == npcX && pcY > npcY) {
			setHeading(4);
		} else if (pcX < npcX && pcY > npcY) {
			setHeading(5);
		} else if (pcX < npcX && pcY == npcY) {
			setHeading(6);
		} else if (pcX < npcX && pcY < npcY) {
			setHeading(7);
		}
		SC_ChangeHeading.broadcast(this);

		int npcId = getNpcTemplate().get_npcId();
		if (npcId == 71092 || npcId == 71093) {
			if (pc.isKnight() && pc.getQuest().get_step(3) == 4) {
				SC_NPCTalkReturn.send(pc, getId(), "searcherk1");
			} else {
				SC_NPCTalkReturn.send(pc, getId(), "searcherk4");
			}
		} else if (npcId == 71094) {
			if (pc.isDarkelf() && pc.getQuest().get_step(4) == 1) {
				SC_NPCTalkReturn.send(pc, getId(), "endiaq1");
			} else {
				SC_NPCTalkReturn.send(pc, getId(), "endiaq4");
			}
		} else if (npcId == 71062) {
			if (pc.getQuest().get_step(CQuestSystem.QUEST_CADMUS) == 2) {
				SC_NPCTalkReturn.send(pc, getId(), "kamit1b");
			} else {
				SC_NPCTalkReturn.send(pc, getId(), "kamit1");
			}
		} else if (npcId == 71075) {
			if (pc.getQuest().get_step(CQuestSystem.QUEST_LIZARD) == 1) {
				SC_NPCTalkReturn.send(pc, getId(), "llizard1b");
			} else {
				SC_NPCTalkReturn.send(pc, getId(), "llizard1a");
			}
		} else if (npcId == 70957 || npcId == 81209) {
			if (pc.getQuest().get_step(CQuestSystem.QUEST_ROI) != 1) {
				SC_NPCTalkReturn.send(pc, getId(), "roi1");
			} else {
				SC_NPCTalkReturn.send(pc, getId(), "roi2");
			}
		}

		// 얘 좀 구린듯. interlockedIncrement하는데 compare&Swap이라니. 어차피 intel 기준 lock
		// xadd 한번만 콜해주면 되는데.-_-
		if (_restCallCount.getAndIncrement() == 0) {
			setRest(true);
		}

		ThreadPool.getInstance().scheduleRequired(new RestMonitor(), REST_MILLISEC);
	}

    @Override
    public void onFinalAction(L1PcInstance pc, String action) {
        if (!"start".equalsIgnoreCase(action) || pc == null || isDestroyed() || isDead()) return;
        final int npcId = getNpcTemplate().get_npcId();
        final int followerId;
        if ((npcId == 71092 || npcId == 71093) && pc.isKnight() && pc.getQuest().get_step(3) == 4) {
            followerId = 71093;
        } else if (npcId == 71094 && pc.isDarkelf() && pc.getQuest().get_step(4) == 1) {
            followerId = 71094;
        } else if (npcId == 71062 && pc.getQuest().get_step(CQuestSystem.QUEST_CADMUS) == 2) {
            if (!pc.getFollowerList().isEmpty()) {
                SC_NPCTalkReturn.send(pc, getId(), "");
                return;
            }
            followerId = 71062;
        } else if (npcId == 71075 && pc.getQuest().get_step(CQuestSystem.QUEST_LIZARD) == 1) {
            followerId = 71075;
        } else if (npcId == 70957 || npcId == 81209) {
            followerId = 70957;
        } else return;

        // The PC owner serializes start commands. Do not replace a live
        // escort with another copy of the same quest NPC.
        for (L1FollowerInstance existing : pc.getFollowerList().values()) {
            if (existing != null && !existing.isDestroyed() && existing.getNpcId() == followerId) {
                SC_NPCTalkReturn.send(pc, getId(), "");
                return;
            }
        }
        final L1Npc template = NpcTable.getInstance().getTemplate(followerId);
        if (template == null || !claimFollower()) return;
        try {
            new L1FollowerInstance(template, this, pc);
            SC_NPCTalkReturn.send(pc, getId(), "");
        } finally {
            releaseFollowerClaim();
        }
    }

	private static final long REST_MILLISEC = 10000;

	private AtomicInteger _restCallCount;

	public class RestMonitor implements Runnable {
		@Override
		public void run() {
			if (_restCallCount.decrementAndGet() == 0) {
				setRest(false);
			}
		}
	}
}
