package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.util.HashMap;

import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

public final class CraftLoader {
    private CraftLoader() {}

    public static synchronized void load() {
        try {
            final var loaded = new HashMap<Integer, Craft>();
            final ProtoInputStream in = ProtoInputStream.newInstance("./data/craftinfo.dat");
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                switch (tag >>> 3) {
                case 1 -> in.readEnum();
                case 2 -> {
                    Craft craft = Craft.parse(in.readSlice());
                    loaded.put(craft.get_id(), craft);
                }
                default -> in.skipField(tag);
                }
            }
            Craft.publish(loaded);
        } catch (Exception failure) {
            java.util.logging.Logger.getLogger(CraftLoader.class.getName()).log(java.util.logging.Level.SEVERE,
                    "Craft data reload failed; previous recipes retained", failure);
        }
    }
}
