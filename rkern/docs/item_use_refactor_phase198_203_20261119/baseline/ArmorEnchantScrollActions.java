package l1j.server.server.model.item.function;

/** Armor scroll pipeline: target rules, level caps, then consumption/effects. */
final class ArmorEnchantScrollActions {
    private ArmorEnchantScrollActions() {}

    static ItemActionResult use(ItemActionContext use) {
        // Both the consumable and the enchant target must still belong to
        // this inventory. Never resolve a stale or foreign target object.
        if (!SpecialEnchantScrolls.isOwnedScrollTarget(
                use.player(), use.item(), use.targetItem())) {
            return ItemActionResult.STOP;
        }
        ItemActionResult result = ArmorEnchantTargetRules.validate(use);
        if (result == ItemActionResult.STOP) return result;
        result = ArmorEnchantLevelLimits.validate(use);
        if (result == ItemActionResult.STOP) return result;
        return ArmorEnchantScrollResolution.apply(use);
    }
}
