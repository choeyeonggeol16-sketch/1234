package l1j.server.server.model.item.nc;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.Config;
import l1j.server.MJCTSystem.MJCTObject;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.Loader.MJCTSystemLoader;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.ItemPresentator.ItemPresentatorFactory;
import l1j.server.PrideSystem.PrideInfo;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.ThreadPool; //CrockController
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1EquipmentSlot;
import l1j.server.server.model.L1EquipmentSlot.ClassArmorBonus;
import l1j.server.server.model.L1EquipmentTimer;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.doll.DollOption;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.MJBytesOutputStream;
import l1j.server.server.utils.eKind;
import l1j.server.server.model.Instance.L1ItemInstance;

/** NC CItem assembly responsibility adapted to L1 option wire format.
 * The existing item-ID option order is preserved. */
public final class CItem {
    // Origin of the L1 legacy time field; evaluated once instead of per item.
    @SuppressWarnings("deprecation")
    private static final long LEGACY_TIME_ORIGIN_MS = Date.parse("January 1, 2017 0:00:00");

    private CItem() {}

    static byte[] AssembleItem(L1ItemInstance item) {
		int itemType2 = item.getItem().getType2();
		int itemId = item.getItemId();
		int itemgrade = item.getItem().getGrade();

		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			if (itemType2 == 0) {
                CItemConstInfo.AssembleEtcItem(item, os, itemType2, itemId, itemgrade);
            } else if (itemType2 == 1 || itemType2 == 2) {
                CItemEnchantOption.AssembleEquipmentItem(item, os, itemType2, itemId, itemgrade);
            }

			// 원거리 치명타
			if (itemId == 900082) {
				switch (item.getEnchantLevel()) {
				case 6:
					os.writeLongCritical(1);
					break;
				case 7:
					os.writeLongCritical(3);
					break;
				case 8:
					os.writeLongCritical(5);
					break;
				case 9:
					os.writeLongCritical(7);
					break;
				}
			}

			// 근거리 치명타
			if (itemId == 203019) { /** 포르세의 검 **/
				if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 15)
					os.writeShortCritical(10);
			} else if (itemId == 900081) {
				switch (item.getEnchantLevel()) {
				case 6:
					os.writeShortCritical(1);
					break;
				case 7:
					os.writeShortCritical(3);
					break;
				case 8:
					os.writeShortCritical(5);
					break;
				case 9:
					os.writeShortCritical(7);
					break;
				}
			}
		
			// TODO 라이브형식 자동삭제 표기
			/*
			 * if (itemId == 40312 && getEndTime() != null) { os.writeC(112);
			 * os.writeD((getEndTime().getTime() / 1000)); }
			 */
			if (item.getEndTime() != null) {
				if (item.isIdentified()) {
					long temp = LEGACY_TIME_ORIGIN_MS;
					os.writeC(61);
					os.writeD((item.getEndTime().getTime() - temp) / 1000 * 6);
				}

				// long temp = Date.parse("January 1, 2017 19:00:00");

				// os.writeC(112);
				// os.writeD((getEndTime().getTime() / 1000));
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return os.getBytes();
	
    }

    static byte[] AssembleBriefItem(L1ItemInstance item) {
		int itemType2 = item.getItem().getType2();

		ItemPresentOutStream os = new ItemPresentOutStream();

		try {
			if (itemType2 == 0) { 
			switch (item.getItem().getItemId()) {
			case 875640331:
			case 875640332:
			case 875640333:
			case 875640334:
			case 875640335:
				os.writeC(39);
				os.writeS("구분: \\fB조우");
				break;
			case 875640336:
			case 875640337:
			case 875640338:
				os.writeC(39);
				os.writeS("구분: \\fF크리스터");
				break;
			case 875640339:
			case 875640340:
			case 875640341:
				os.writeC(39);
				os.writeS("구분: \\fD이실로테");
				break;
			case 875640446:
			case 875640447:
			case 875640448:
				os.writeC(39);
				os.writeS("구분: \\aG아툰");
				break;
				
			}
			}
			if (item.getEndTime() != null) {
				if (item.isIdentified()) {
					long temp = LEGACY_TIME_ORIGIN_MS;
					os.writeC(61);
					os.writeD((item.getEndTime().getTime() - temp) / 1000 * 6);
				}

				// long temp = Date.parse("January 1, 2017 19:00:00");

				// os.writeC(112);
				// os.writeD((getEndTime().getTime() / 1000));
			}

		} catch (

		Exception e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return os.getBytes();
	
    }

    static byte[] AssembleItemForPc(L1ItemInstance item, L1PcInstance pc) {
		int itemType2 = item.getItem().getType2();
		int itemId = item.getItemId();
		ItemPresentOutStream os = new ItemPresentOutStream();

		if (itemType2 == 0) { // etcitem
			switch (item.getItem().getType()) {
			case 2: // light
				os.writeC(22);
				os.writeH(item.getItem().getLightRange());
				break;
			case 7: // food
				os.writeC(21);
				os.writeH(item.getItem().getFoodVolume());
				break;
			case 0: // arrow
			case 15: // sting
				os.writeC(1);
				os.writeC(item.getItem().getDmgSmall());
				os.writeC(item.getItem().getDmgLarge());
				break;
			default:
				os.writeC(23);
				break;
			}
			os.writeC(item.getItem().getMaterial());
			os.writeD(item.getWeight());

		} else if (itemType2 == 1 || itemType2 == 2) { // weapon | armor
			if (itemType2 == 1) { // weapon 무기 타격치
				os.writeC(1);
				os.writeC(item.getItem().getDmgSmall());
				os.writeC(item.getItem().getDmgLarge());
				os.writeC(item.getItem().getMaterial());
				os.writeD(item.getWeight());
			} else if (itemType2 == 2) { // armor
				os.writeC(19);
				int ac = ((L1Armor) item.getItem()).get_ac();
				int Grade = ((L1Armor) item.getItem()).getGrade();
				if (ac < 0) {
					ac = ac - ac - ac;
				}
				os.writeC(ac - item.get_durability());
				os.writeC(item.getItem().getMaterial());
				os.writeH(-1);
				os.writeD(item.getWeight());
			}

			if (item.getEnchantLevel() != 0 && !(itemType2 == 2 && item.getItem().getGrade() >= 0)) {
				os.writeC(2);
				os.writeC(item.getEnchantLevel());
			}
			if (item.getItem().isTwohandedWeapon()) { // 양손무기
				os.writeC(4);
			}
			if (item.get_durability() != 0) { // 손상도
				os.writeC(3);
				os.writeC(item.get_durability());
			}

			/** 클래스 착용 부분 **/
			int bit = 0;
			bit |= item.getItem().isUseRoyal() ? 1 : 0;
			bit |= item.getItem().isUseKnight() ? 2 : 0;
			bit |= item.getItem().isUseElf() ? 4 : 0;
			bit |= item.getItem().isUseMage() ? 8 : 0;
			bit |= item.getItem().isUseDarkelf() ? 16 : 0;
			bit |= item.getItem().isUseDragonKnight() ? 32 : 0;
			bit |= item.getItem().isUseBlackwizard() ? 64 : 0;
			bit |= item.getItem().isUse전사() ? 128 : 0;
			os.writeC(7);
			os.writeC(bit);

			os.writeC(130);
			if (!item.getItem().isTradable()) {
				os.writeD(6);
			} else {
				os.writeD(7);
			}

			if (item.isCanbeDmg()) {
				os.writeC(131);
				os.writeD(1); // 비손상
			}

			os.writeC(132);
			os.writeD(3); // 성별 구분 1.남자 2.여자 3.전체

			if (itemType2 == 1 && item.isUndeadDmg()) {
				os.writeC(114);
				os.writeD(1); // 언데드
			}


			/** 70레벨 엘릭서 룬 옵션 표시 **/
			if (itemId == 222312) { // 민첩의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(5);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(32);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222313) { // 체력의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222314) { // 지식의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222315) { // 지혜의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222316) { // 힘의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeAddAc(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			// TODO 80레벨 엘릭서 룬 옵션 표시
			if (itemId == 900135) {
				// System.out.println(pc.getType());
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:// 기사
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:// 다엘
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(5);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(32);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900136) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900137) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900138) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900139) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
		}

		CItemEnchantOption.AssembleMythicWeaponOption(item, os);
		return os.getBytes();
	
    }
}
