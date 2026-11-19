package l1j.server.server.model.network.thread;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jctools.queues.MpmcArrayQueue;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.action.CCreatureController;
import l1j.server.server.model.npc.ai.NpcServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback.CIOThread;
import l1j.server.server.model.network.thread.CIOThreadCallback.TaskHandle;
import l1j.server.server.model.network.thread.CIOThreadCallback.WheelTask;

public final class CThreadLocalTimer {
    private static final Logger LOGGER = Logger.getLogger(CIOThreadCallback.class.getName());
    private static final MpmcArrayQueue<Node> NODE_POOL = new MpmcArrayQueue<>(32768);
    private static final MpmcArrayQueue<CSlot> SLOT_POOL = new MpmcArrayQueue<>(4096);

	private static final byte KIND_GENERIC = 0;
	private static final byte KIND_ACTION = 1;
	private static final byte KIND_ITEM = 2;
	private static final byte KIND_NPC_MAKER = 3;
	private static final byte KIND_CHARACTER_SKILL = 5;
	private static final byte KIND_PC_ITEM_DELAY = 6;
	private static final byte KIND_NPC_TIMER = 7;
    private static final byte KIND_PC_TELEPORT = 8;

    final int tickMs;
    final CSlot[] buckets;
    final CIOThread owner;
    private final int size;
    private int cursor;
    private long tickAtMs;
    
    private boolean closed;
    
    private Node[] distant = new Node[32];
    private int distantSize;

    CThreadLocalTimer(int tickMs, int size, CIOThread owner) {
        if (tickMs <= 0 || size < 2) throw new IllegalArgumentException();
        this.tickMs = tickMs;
        this.size = size;
        this.owner = owner;
        this.buckets = new CSlot[size];
        this.tickAtMs = CIOThread.nowMs();
    }

    /** No publication until kind, identity, generation and payload are set. */
    private Node obtainNode(long delayMs) {
        Node node = NODE_POOL.poll();
        if (node == null) node = new Node();
        final long now = CIOThread.nowMs();
        final long delay = Math.max(1L, delayMs);
        node.dueMs = delay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + delay;
        return node;
    }

    /** Must be called with wheel monitor held after full node initialization. */
    private void publish(Node node) {
        if (closed || !owner.running ||
                (node.handle != null && node.handle.isCancelled())) {
            if (node.handle != null) node.handle.cancel();
            recycleNode(node);
            return;
        }
        long remaining = node.dueMs - tickAtMs;
        if (remaining <= 0L) remaining = 1L;
        long ticks = 1L + (remaining - 1L) / tickMs;
        if (ticks >= size) {
            pushDistant(node);
            return;
        }
        final int slot = (cursor + (int) ticks) % size;
        CSlot head = buckets[slot];
        if (head == null || head.count == 1000) {
            CSlot next = SLOT_POOL.poll();
            if (next == null) next = new CSlot();
            next.next = head;
            buckets[slot] = next;
            head = next;
        }
        head.add(node);
    }

    private void pushDistant(Node node) {
        int i = distantSize;
        if (i == distant.length) {
            Node[] extended = new Node[i << 1];
            System.arraycopy(distant, 0, extended, 0, i);
            distant = extended;
        }
        distantSize = i + 1;
        while (i != 0) {
            final int parent = (i - 1) >>> 1;
            Node other = distant[parent];
            if (other.dueMs <= node.dueMs) break;
            distant[i] = other;
            i = parent;
        }
        distant[i] = node;
    }

    private Node popDistant() {
        final Node first = distant[0];
        final Node last = distant[--distantSize];
        distant[distantSize] = null;
        if (distantSize == 0) return first;
        int i = 0;
        while (true) {
            final int left = (i << 1) + 1;
            if (left >= distantSize) break;
            final int right = left + 1;
            final int child = right < distantSize && distant[right].dueMs < distant[left].dueMs
                    ? right : left;
            if (last.dueMs <= distant[child].dueMs) break;
            distant[i] = distant[child];
            i = child;
        }
        distant[i] = last;
        return first;
    }

    /** Long-timer -> near wheel promotion during this shard's tick. */
    private void promoteDistant() {
        final long window = (long) (size - 1) * tickMs;
        final long threshold = tickAtMs > Long.MAX_VALUE - window
                ? Long.MAX_VALUE : tickAtMs + window;
        while (distantSize != 0 && distant[0].dueMs <= threshold) {
            Node node = popDistant();
            if (node.handle != null && node.handle.isCancelled()) {
                recycleNode(node);
            } else {
                publish(node);
            }
        }
    }

	synchronized void schedule(
			long delayMs,
			WheelTask task,
			TaskHandle handle) {

		if (task == null || delayMs <= 0L) {
			return;
		}

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_GENERIC;
		node.payload = task;
		node.handle = handle;
        publish(node);
    }

	synchronized void scheduleAction(
			long delayMs,
			CCreatureController controller,
			int timerId,
			int generation,
			int expectedSession) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_ACTION;
		node.payload = controller;
		node.p0 = timerId;
		node.p1 = generation;
		node.l0 = expectedSession;
        publish(node);
    }

    synchronized void scheduleTeleport(long delayMs, long smartId,
            int expectedSession, int expectedEpoch) {
        final Node node = obtainNode(delayMs);
        node.kind = KIND_PC_TELEPORT;
        node.l0 = smartId;
        node.p0 = expectedSession;
        node.p1 = expectedEpoch;
        publish(node);
    }

	synchronized void schedulePcItemDelay(
			long delayMs,
			long smartId,
			int delayId,
			int generation) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_PC_ITEM_DELAY;
		node.l0 = smartId;
		node.p0 = delayId;
		node.p1 = generation;
        publish(node);
    }

	synchronized void scheduleNpcTimer(
			long delayMs,
			long smartId,
			int timerId,
			int generation) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_NPC_TIMER;
		node.l0 = smartId;
		node.p0 = timerId;
		node.p1 = generation;
        publish(node);
    }

	synchronized void scheduleItem(
			long delayMs,
			long smartId,
			int timerId,
			int generation) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_ITEM;
		node.l0 = smartId;
		node.p0 = timerId;
		node.p1 = generation;
        publish(node);
    }

	synchronized void scheduleNpcMaker(
			long delayMs,
			L1Spawn maker,
			int timerId,
			int generation) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_NPC_MAKER;
		node.payload = maker;
		node.p0 = timerId;
		node.p1 = generation;
        publish(node);
    }


	synchronized void scheduleCharacterSkill(
			long delayMs,
			L1Character actor,
			int generation) {

		final Node node =
				obtainNode(delayMs);

		node.kind = KIND_CHARACTER_SKILL;
		node.payload = actor;
		node.p0 = generation;
        publish(node);
    }

    void tick(CIOThread owner) {
        final CSlot first;
        synchronized (this) {
            cursor = cursor + 1 == size ? 0 : cursor + 1;
            tickAtMs = owner.lastWheelAtMs;
            first = buckets[cursor];
            buckets[cursor] = null;
            promoteDistant();
        }
        CSlot slot = first;
        while (slot != null) {
            final CSlot nextSlot = slot.next;
            for (int i = 0, count = slot.count; i < count; i++) {
                final Node cur = slot.entries[i];
                slot.entries[i] = null;
            if (!owner.running) {
                if (cur.handle != null) cur.handle.cancel();
                recycleNode(cur);
                continue;
            }
			if (cur.kind == KIND_GENERIC
					&& cur.handle != null
					&& cur.handle.isCancelled()) {

				recycleNode(cur);
                continue;
			}

            if (cur.dueMs > owner.lastWheelAtMs) {
                synchronized (this) {
                    publish(cur);
                }
                continue;
            }

			boolean retryAction = false; // serializer capacity: retry without losing timer
			CUserSocket.beginSendBurst();
			try {
				if (cur.kind == KIND_ACTION) {
					final CCreatureController controller = (CCreatureController) cur.payload;
					final L1PcInstance pc = controller == null ? null : controller.actor();
					if (pc != null && pc.getSessionStamp() == (int) cur.l0
							&& controller.actor() == pc
							&& controller.ownerShardIndex() == owner.idx) {
						retryAction = !controller.AddExecutableTimer(cur.p0, cur.p1);
					}
                } else if (cur.kind == KIND_ITEM) {
                    final Object item = L1World.getInstance().findObjectBySmartId(cur.l0);
                    if (item instanceof L1ItemInstance) {
                        // Saturation: keep the same timer node for next tick.
                        // CWorld rechecks the registered object's identity.
                        retryAction = !L1World.getInstance().AsyncItemTimer(
                                (L1ItemInstance) item, cur.p0, cur.p1);
                    }
				} else {
                    retryAction = dispatch(owner, cur);
				}
			} catch (Throwable t) {
				LOGGER.log(
						Level.SEVERE,
						"[CIOThread] wheel task failure; owner survives"
								+ " kind=" + cur.kind,
						t);
			} finally {
				CUserSocket.endSendBurst();
			}

            if (retryAction) {
                synchronized (this) {
                    final long base = Math.max(tickAtMs, CIOThread.nowMs());
                    cur.dueMs = base > Long.MAX_VALUE - tickMs
                            ? Long.MAX_VALUE : base + tickMs;
                    publish(cur);
                }
            } else {
                recycleNode(cur);
            }
            }
            slot.clear();
            SLOT_POOL.offer(slot);
            slot = nextSlot;
        }
    }

	private boolean dispatch(
			CIOThread owner,
			Node node) {

		switch (node.kind) {
		case KIND_GENERIC: {
			final WheelTask task =
					(WheelTask) node.payload;

			if (task != null && !task.isCancelled()) {
				task.run();
			}
			return false;
		}

		case KIND_ACTION: {
			final CCreatureController controller =
					(CCreatureController) node.payload;

			if (controller != null) {
				controller.TimerExpired(
						node.p0,
						node.p1);
			}
			return false;
		}

        case KIND_PC_TELEPORT: {
            final Object obj = L1World.getInstance().findObjectBySmartId(node.l0);
            if (obj instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) obj;
                if (pc.getSessionStamp() == node.p0
                        && pc.teleportTimerEpoch() == node.p1
                        && CIOThreadCallback.getInstance().shardOf(pc) == owner) {
                    final CCreatureController controller = pc.getCreatureController();
                    if (controller != null && controller.actor() == pc) {
                        controller.AddExecutableTeleport(node.p1);
                    }
                }
            }
            return false;
        }

		case KIND_PC_ITEM_DELAY: {
            final Object obj = L1World.getInstance().findObjectBySmartId(node.l0);
            if (obj instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) obj;
                final CCreatureController controller = pc.getCreatureController();
                if (controller != null && controller.actor() == pc) {
                    controller.AddExecutableItemDelay(node.p0, node.p1);
                }
            }
            return false;
        }

		case KIND_NPC_TIMER: {
			final Object obj =
					L1World.getInstance()
							.findObjectBySmartId(
									node.l0);

            if (obj instanceof L1NpcInstance) {
                final NpcServer npcServer = NpcServer.getInstance();
                return npcServer.isRunning() && !npcServer.postNpcTimer((L1NpcInstance) obj,
                        node.l0, node.p0, node.p1);
            }
            return false;
		}


		case KIND_NPC_MAKER: {
			final L1Spawn maker =
					(L1Spawn) node.payload;

            if (maker != null) {
                final NpcServer npcServer = NpcServer.getInstance();
                return npcServer.isRunning() && !npcServer.postMakerTimer(maker, node.p0, node.p1);
            }
            return false;
		}


		case KIND_CHARACTER_SKILL: {
			final L1Character actor =
					(L1Character) node.payload;

			final int generation =
					node.p0;

            if (actor == null || owner == null
                    || !actor.skillEffectHeartbeatValid(generation)) return false;
            if (actor instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) actor;
                final CCreatureController controller = pc.getCreatureController();
                if (controller != null && controller.actor() == pc) {
                    controller.AddExecutableSkillHeartbeat(generation);
                }
                return false;
            }

            if (actor instanceof L1NpcInstance) {
                // Do not release the heartbeat generation merely because
                // the NPC event pool is saturated: retry this SAME wheel node.
                final NpcServer npcServer = NpcServer.getInstance();
                if (npcServer.postNpcSkillHeartbeat((L1NpcInstance) actor, generation)) return false;
                if (npcServer.isRunning()) return true;
                actor.skillEffectHeartbeatRelease(generation);
                return false;
            }

			long nextDelay = 0L;

			try {
				nextDelay =
						actor.skillEffectHeartbeatTick(
								generation);
			} catch (Throwable t) {
				LOGGER.log(
						Level.SEVERE,
						"Uncaught exception in character skill heartbeat",
						t);
			}

			if (nextDelay <= 0L
					|| !actor.skillEffectHeartbeatValid(generation)) {

				actor.skillEffectHeartbeatRelease(
						generation);
				return false;
			}

			owner.onCharacterSkillHeartbeatSchedule(
					nextDelay,
					actor,
					generation);
			return false;
		}

		default:
			return false;
		}
	}

    // On stop, a scheduled callback must not retain PCs/NPCs/Spawn and
    // active timer handles. Reuse the same pooled nodes, no new executor.
    synchronized void discardOnShutdown() {
        // Set first: all subsequent schedules are discarded even if an
        // external thread races the shutdown drain or arrives afterwards.
        closed = true;
        for (int i = 0; i < buckets.length; i++) {
            CSlot slot = buckets[i];
            buckets[i] = null;
            while (slot != null) {
                final CSlot next = slot.next;
                for (int j = 0; j < slot.count; j++) {
                    final Node node = slot.entries[j];
                    slot.entries[j] = null;
                    if (node.handle != null) node.handle.cancel();
                    recycleNode(node);
                }
                slot.clear();
                SLOT_POOL.offer(slot);
                slot = next;
            }
        }
        while (distantSize != 0) {
            Node node = popDistant();
            if (node.handle != null) node.handle.cancel();
            recycleNode(node);
        }
    }

	private void recycleNode(Node node) {
		node.clear();
		NODE_POOL.offer(node);
	}

	static final class Node {
		byte kind;
		Object payload;
		TaskHandle handle;
		long l0;
		int p0;
		int p1;
		long dueMs;

		void clear() {
			kind = KIND_GENERIC;
			payload = null;
			handle = null;
			l0 = 0L;
			p0 = 0;
			p1 = 0;
			dueMs = 0L;
		}
	}
}
