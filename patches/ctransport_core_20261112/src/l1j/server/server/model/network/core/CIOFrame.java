package l1j.server.server.model.network.core;

import l1j.server.server.model.network.buffer.WriteBuffer;

/** Reusable direct SEND lease handle; owned by CIOBufferPool. */
public final class CIOFrame {
    private CIOUserSocket owner;
    private CIOBufferPool.Lease lease;
    private WriteBuffer buffer;
    private boolean active;
    private int reservedBytes;
    private int reservedTicket = -1;
    private long reservedTicketId = -1L;

    CIOFrame() {}
    public WriteBuffer buffer() { return buffer; }
    void beginIO(CIOUserSocket owner, CIOBufferPool.Lease lease) {
        if (active) throw new IllegalStateException("outbound frame still reserved");
        this.owner = owner;
        this.lease = lease;
        this.buffer = lease.writer();
        active = true;
    }
    void setReservation(int bytes, int ticket, long ticketId) {
        if (!active) throw new IllegalStateException("frame not active");
        reservedBytes = bytes;
        reservedTicket = ticket;
        reservedTicketId = ticketId;
    }
    int reservedBytes() { return reservedBytes; }
    int reservedTicket() { return reservedTicket; }
    long reservedTicketId() { return reservedTicketId; }
    CIOBufferPool.Lease ioLease(CIOUserSocket owner) {
        if (!active || this.owner != owner) throw new IllegalArgumentException("foreign frame");
        return lease;
    }
    void finishIO() {
        active = false;
        reservedBytes = 0;
        reservedTicket = -1;
        reservedTicketId = -1L;
        owner = null;
        lease = null;
        buffer = null;
    }
}
