package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

import java.nio.charset.Charset;
import l1j.server.server.model.Instance.L1PetInstance;

public final class SC_PetPack {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_PetPack() {}

    public static boolean send(L1PcInstance observer, L1PetInstance pet) {
        if (observer == null) return false;
        final CIOClient client = observer.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final int x = pet == null ? 0 : pet.getX();
        final int y = pet == null ? 0 : pet.getY();
        final int objectId = pet == null ? 0 : pet.getId();
        final int spriteId = pet == null ? 0 : pet.getCurrentSpriteId();
        final int status = pet == null ? 0 : pet.getStatus();
        final int heading = pet == null ? 0 : pet.getHeading();
        final int light = pet == null || pet.getLight() == null ? 0 : pet.getLight().getChaLightSize();
        final int speed = pet == null ? 0 : pet.getMoveSpeed();
        final int exp = pet == null ? 0 : pet.getExp();
        final int lawful = pet == null ? 0 : pet.getTempLawful();
        final int level = pet == null ? 1 : pet.getLevel();
        final String name = pet == null || pet.getName() == null ? "" : pet.getName();
        final String title = pet == null || pet.getTitle() == null ? "" : pet.getTitle();
        final String masterName = pet == null || pet.getMaster() == null || pet.getMaster().getName() == null
                ? "" : pet.getMaster().getName();
        final int poison = pet != null && pet.getPoison() != null && pet.getPoison().getEffectId() == 1 ? 1 : 0;
        final boolean owner = pet != null && pet.getMaster() != null && pet.getMaster().getId() == observer.getId();
        final int maxHp = pet == null ? 0 : pet.getMaxHp();
        final int maxMp = pet == null ? 0 : pet.getMaxMp();
        final int hp = !owner || maxHp <= 0 ? 0xFF : Math.max(0, Math.min(100, 100 * pet.getCurrentHp() / maxHp));
        final int mp = !owner || maxMp <= 0 ? 0xFF : Math.max(0, Math.min(100, 100 * pet.getCurrentMp() / maxMp));
        final int wireSize = 44 + name.getBytes(MS949).length + title.getBytes(MS949).length
                + masterName.getBytes(MS949).length;
        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PUT_OBJECT);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeD(objectId);
            buffer.writeH(spriteId);
            buffer.writeC(status);
            buffer.writeC(heading);
            buffer.writeC(light);
            buffer.writeC(speed);
            buffer.writeD(exp);
            buffer.writeH(lawful);
            buffer.writeS(name);
            buffer.writeS(title);
            buffer.writeC(poison);
            buffer.writeD(0);
            buffer.writeS(null);
            buffer.writeS(masterName);
            buffer.writeC(0);
            buffer.writeC(hp);
            buffer.writeC(0);
            buffer.writeC(level);
            buffer.writeC(0);
            buffer.writeC(0xFF);
            buffer.writeC(0xFF);
            buffer.writeC(0);
            buffer.writeC(0);
            buffer.writeC(mp);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
