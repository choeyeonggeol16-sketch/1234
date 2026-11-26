package l1j.server.server.model.item.function;

public final class EquipmentItemActions {
    private EquipmentItemActions() {}
	public static boolean is_legend_weapon(int itemId) {
		switch (itemId) {
		case 12:
		case 61:
		case 66:
		case 86:
		case 134:
		case 202011:
		case 202012:
		case 202013:
		case 202014:
		case 123777:
		case 123888:
		case 123999:
		case 123666:
			return true;
		}

		return false;
	}
}
