package l1j.server.server.model.character.transform;

/** Existing L1 cause masks stored in the definition table. */
public enum TransformType {
    LOGIN(0), ITEM_MAGIC(1), GM(2), NPC(4), KEPLISHA(8);

    final int mask;

    TransformType(int mask) {
        this.mask = mask;
    }
}
