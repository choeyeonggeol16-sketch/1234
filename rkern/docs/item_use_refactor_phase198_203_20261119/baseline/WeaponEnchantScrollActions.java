package l1j.server.server.model.item.function;

/** Weapon enchant: target validation -> level caps -> scroll consumption/effects. */
final class WeaponEnchantScrollActions {
    private WeaponEnchantScrollActions() {}

    static ItemActionResult use(ItemActionContext use) {
        // Both the consumable and the enchant target must still belong to
        // this inventory. Never resolve a stale or foreign target object.
        if (!SpecialEnchantScrolls.isOwnedScrollTarget(
                use.player(), use.item(), use.targetItem())) {
            return ItemActionResult.STOP;
        }
        ItemActionResult result = WeaponEnchantTargetRules.validate(use);
        if (result == ItemActionResult.STOP) return result;
        result = WeaponEnchantLevelLimits.validate(use);
        if (result == ItemActionResult.STOP) return result;
        return WeaponEnchantScrollResolution.apply(use);
    }
}
