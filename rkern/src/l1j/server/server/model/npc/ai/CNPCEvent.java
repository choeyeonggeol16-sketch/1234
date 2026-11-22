package l1j.server.server.model.npc.ai;

import org.jctools.queues.MpmcArrayQueue;
import l1j.server.server.model.L1Character;

/** Compact NPC input event. One lease is the payload AND the inbox link. */
public final class CNPCEvent {
    // The free pool is bounded; the backlog must remain lossless.
    private static final MpmcArrayQueue<CNPCEvent> POOL = new MpmcArrayQueue<>(4096);
    public static final CNPCEvent CLOSED = new CNPCEvent();

    public L1Character cha;
    public int value;
    public int epoch;
    public CNPCEvent next;

    private CNPCEvent() {}

    static CNPCEvent Create(L1Character cha, int value, int epoch) {
        CNPCEvent event = POOL.poll();
        if (event == null) event = new CNPCEvent();
        event.cha = cha;
        event.value = value;
        event.epoch = epoch;
        return event;
    }

    /** An atomic detach owns the entire chain. Reverse once for FIFO order. */
    public static CNPCEvent Reverse(CNPCEvent head) {
        CNPCEvent ordered = null;
        while (head != null && head != CLOSED) {
            final CNPCEvent next = head.next;
            head.next = ordered;
            ordered = head;
            head = next;
        }
        return ordered;
    }

    public static void Release(CNPCEvent event) {
        if (event == null || event == CLOSED) return;
        event.cha = null;
        event.value = 0;
        event.epoch = 0;
        event.next = null;
        POOL.offer(event);
    }

    public static void ReleaseChain(CNPCEvent head) {
        while (head != null && head != CLOSED) {
            final CNPCEvent next = head.next;
            Release(head);
            head = next;
        }
    }
}
