package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.config.CIOConfig;

public abstract class IPacketWriter<T extends CUserSocket> {

    /**
     * Opt in only when the encoder honors Assemble's frame-relative region and
     * can finish in one call within a strict wire-size bound, including the header.
     * The bound must be side-effect free. An overflow is an encoding error.
     */
    public interface DirectTail {
        int maximumWireBytes();
    }

    protected IPacketWriter() {
    }

    /**
     * 재사용 Assemble용.
     *
     * CUserSocket.prepareWriteBuffer()가 packet class metadata를 선택하고
     * registered SEND region까지 arm한 뒤 이 메서드가 호출된다.
     *
     * Assemble.reset(getClass())는 이미 preparePacket()으로 캐시된
     * packet class라면 ClassValue/AtomicInteger 재조회 없이
     * arm된 registered region을 그대로 소비한다.
     */
    public Assemble writeData(T client, Assemble buffer) throws Exception {
        if (buffer == null) {
            throw new IllegalArgumentException("Assemble is null");
        }

        buffer.reset(getClass());
        buffer.position(CIOConfig.HEADER_SIZE);

        try {
            if (write(client, buffer)) {
                buffer.mark();
                return buffer;
            }

            buffer.releaseResources();
            throw new Exception(
                    "IPacketWriter.write() returned false for "
                            + getClass().getSimpleName());

        } catch (RuntimeException e) {
            buffer.releaseResources();
            throw e;
        }
    }

    public void writeHeader(Assemble buffer, int header) {
        buffer.writeShort(0, (short) header);
    }

    public boolean canBeDropped(T client) {
        return false;
    }

    protected abstract boolean write(T client, Assemble buffer);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
