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

/** CItemConstInfo-equivalent base item attribute assembly (L1 template source). */
public final class CItemConstInfo {
    private CItemConstInfo() {}

    static void AssembleAttribute(L1ItemInstance item, ItemPresentOutStream os) {
        switch (item.getItem().getType()) {
				case 2: // light
					os.writeLightRange(item.getItem().getLightRange());
					break;
				case 7: // food
					os.writeFoodVolume(item.getItem().getFoodVolume());
					break;
				case 0: // arrow
				case 15: // sting
					os.writeDMG(item.getItem().getDmgSmall(), item.getItem().getDmgLarge());
					break;
				default:
					os.writeC(23);
					break;
				}
				os.writeC(item.getItem().getMaterial());
				os.writeD(item.getWeight());
    }

    static int GetRuneAc(L1ItemInstance item) {
		int ac = item.getItem().get_ac();

		if ((item.getItemId() >= 900135 && item.getItemId() <= 900149) || (item.getItemId() >= 222295 && item.getItemId() <= 222316)) {
			if (item._cha != null && item._cha.getType() == 4) {
				ac += 3;
			}
		}

		return ac;
	
    }
}
