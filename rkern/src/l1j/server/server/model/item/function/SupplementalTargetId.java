package l1j.server.server.model.item.function;

/** Supplemental target ID mapping. No object allocation. */
final class SupplementalTargetId {
    private SupplementalTargetId() {}

    static int select(int itemId, int I) {
		int l = 0;

		// TODO 아이템번호 추가해야한다 아니면 사용이안됨
		switch (itemId) {
		case 300001:
		case 3000393:
		case 40964: // 흑마법가루
		case 41036: // 일기 제작 대상 아이템....
		case 60035: // 룬 마력제거제
		case 3000148:
		case 410094: // 마력의 숨결: target is the sealed rune
		case 210082: // 균열의 핵: target is the item to transform
		case 40925: // target earring
		case 40926:
		case 40927:
		case 40928:
		case 40929:

		case 719:
		case 707:
		case 706:
		case 705:
		case 704:
		case 703:
		case 698:
		case 776:
		case 474747:
		case 400001:
		case 500001:
		case 600001:
		case 700001:
			l = I;
			break;
		default:
			break;
		}

        return l;
    }
}
