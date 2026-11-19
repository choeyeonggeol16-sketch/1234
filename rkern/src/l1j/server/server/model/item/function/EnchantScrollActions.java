package l1j.server.server.model.item.function;

/** Shared scroll entrypoint; target checks precede class-specific restrictions. */
final class EnchantScrollActions {
    private EnchantScrollActions() {}

    private static boolean owned(ItemActionContext use) {
        return SpecialEnchantScrolls.isOwnedScrollTarget(
                use.player(), use.item(), use.targetItem());
    }

    static ItemActionResult useWeapon(ItemActionContext use) {
        if (!owned(use)) return ItemActionResult.STOP;
        ItemActionResult result = WeaponEnchantTargetRules.validate(use);
        if (result == ItemActionResult.STOP) return result;
        result = WeaponEnchantLevelLimits.validate(use);
        if (result == ItemActionResult.STOP) return result;
        return WeaponEnchantScrollResolution.apply(use);
    }

    static ItemActionResult useArmor(ItemActionContext use) {
        if (!owned(use)) return ItemActionResult.STOP;
        ItemActionResult result = ArmorEnchantTargetRules.validate(use);
        if (result == ItemActionResult.STOP) return result;
        result = ArmorEnchantLevelLimits.validate(use);
        if (result == ItemActionResult.STOP) return result;
        return ArmorEnchantScrollResolution.apply(use);
    }
}
