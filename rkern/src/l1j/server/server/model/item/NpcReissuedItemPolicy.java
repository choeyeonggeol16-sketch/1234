package l1j.server.server.model.item;

/** Protects dedicated NPC replacement and support items from ordinary conversion. */
public final class NpcReissuedItemPolicy {
    private NpcReissuedItemPolicy() {}

    public static boolean isProtected(int itemId) {
        return switch (itemId) {
            // The two tutorial armor sets supplied by the replacement NPC.
            case 22300, 22301, 22302, 22303, 22304,
                 22306, 22307, 22308, 22309, 22310,
                 // Veteran support, stat reset, Tikal calendar and Marbin's quest pouch.
                 30070, 30071, 200000, 500211, 700012,
                 // Spy contracts, Orc village credentials and quest documents.
                 41121, 41130, 41060, 41061, 41062, 41063, 41064, 41065,
                 41007, 41009, 41209, 41225, 41356, 49082, 49083 -> true;
            default -> false;
        };
    }
}
