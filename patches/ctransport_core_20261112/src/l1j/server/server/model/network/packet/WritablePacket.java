package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public abstract class WritablePacket<T extends CIOClient> {

    protected WritablePacket() {
    }

    /**
     * 재사용 WriteBuffer용.
     *
     * CUserSocket.prepareWriteBuffer()가 packet class metadata를 선택하고
     * registered SEND region까지 arm한 뒤 이 메서드가 호출된다.
     *
     * WriteBuffer.reset(getClass())는 이미 preparePacket()으로 캐시된
     * packet class라면 ClassValue/AtomicInteger 재조회 없이
     * arm된 registered region을 그대로 소비한다.
     */
    public WriteBuffer writeData(T client, WriteBuffer buffer) throws Exception {
        if (buffer == null) {
            throw new IllegalArgumentException("WriteBuffer is null");
        }

        buffer.reset(getClass());
        buffer.position(ConnectionConfig.HEADER_SIZE);

        try {
            if (write(client, buffer)) {
                buffer.mark();
                return buffer;
            }

            buffer.releaseResources();
            throw new Exception(
                    "WritablePacket.write() returned false for "
                            + getClass().getSimpleName());

        } catch (RuntimeException e) {
            buffer.releaseResources();
            throw e;
        }
    }

    public void writeHeader(WriteBuffer buffer, int header) {
        buffer.writeShort(0, (short) header);
    }

    public boolean canBeDropped(T client) {
        return false;
    }

    protected abstract boolean write(T client, WriteBuffer buffer);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}