package l1j.server.server.Controller;

import java.util.List;

import javolution.util.FastTable;
import l1j.server.ExpRevision.EFishingType;
import l1j.server.ExpRevision.FishingExpInfo;
import l1j.server.FishingRodInfo.FishingRodInfoLoader;
import l1j.server.configure.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_Fishing;
import l1j.server.server.serverpackets.S_SystemMessage;

public class FishingTimeController implements Runnable {
	public static final int SLEEP_TIME = 1000;

	private final int _mapid = 5124;
    private final List<L1PcInstance> _fishingList = new FastTable<L1PcInstance>();

	private static class LazyHolder {
		private static final FishingTimeController instance = new FishingTimeController();
	}
	public static FishingTimeController getInstance() {
		return LazyHolder.instance;
	}
	@Override
	public void run() {
		try {
			fishing();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	public void addMember(L1PcInstance pc) {
		if (pc == null || _fishingList.contains(pc)) {
			return;
		}
		_fishingList.add(pc);

	}
	public void removeMember(L1PcInstance pc) {
		if (pc == null || !_fishingList.contains(pc)) {
			return;
		}
		_fishingList.remove(pc);
	}
	
	public boolean 성장낚시 = false;
	public boolean 고급성장낚시 = false;

	private void fishing() {
		long currentTime = System.currentTimeMillis();
		for (Object obj : L1World.getInstance().getVisibleObjects(_mapid).values()) {
			L1PcInstance pc = null;
			if (obj instanceof L1PcInstance)
				pc = (L1PcInstance) obj;

			if (pc == null) {
				continue;
			}
			if (pc.getMapId() != _mapid || pc.isDead() || pc.getNetConnection() == null || pc.getCurrentHp() == 0 || !pc.isFishing()) {
				continue;
			}	
			if (pc.isFishing()) {
				long time = pc.getFishingTime();
				if (currentTime > time) {
					if (pc._fishingRod.getItemId() == 41408 || pc.getInventory().consumeItem(41295, 1)) {
						//TODO 릴 장착 고탄력 낚싯대
						
						if (pc._fishingRod.getItemId() == 41408) {
							ItemInstance item = pc._fishingRod;
							if (item != null) {
								if (item.getChargeCount() <= 0) {
									ItemInstance newfishingRod = null;
									pc.getInventory().removeItem(item, 1);
									newfishingRod = pc.getInventory().storeItem(41293, 1);
									pc._fishingRod = newfishingRod;
									endFishing(pc);
								} else {
									item.setChargeCount(item.getChargeCount() - 1);
									pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
									pc.setFishingTime(System.currentTimeMillis() + 30 * 1000);
									pc.sendPackets(new S_Fishing());
									성장낚시 = true;
									FishingRodInfoLoader.getInstance().SuccessFishing(pc, pc.getFishingRod().getItemId());
									if (성장낚시) {
										calculate_exp(pc, EFishingType.GROWN_UP);
										성장낚시 = false; 
									}
								}
							}
						} else if (pc._fishingRod.getItemId() == 41409) {
							pc.setFishingTime(System.currentTimeMillis() + 30 * 1000); 
							pc.sendPackets(new S_Fishing()); 
							고급성장낚시 = true;
							FishingRodInfoLoader.getInstance().SuccessFishing(pc, pc.getFishingRod().getItemId());
							if (고급성장낚시) {
								calculate_exp(pc, EFishingType.GROWN_UP);
								고급성장낚시 = false;
							}
						} else if (pc._fishingRod.getItemId() == 41293) {
							pc.setFishingTime(System.currentTimeMillis() + 60 * 1000); 
							pc.sendPackets(new S_Fishing());
							FishingRodInfoLoader.getInstance().SuccessFishing(pc, pc.getFishingRod().getItemId());
						}
					} else {
						// 미끼가 없어서 종료 처리 구간.
						endFishing(pc);
					}
				}
			}
		}
	}

	public void endFishing(L1PcInstance pc) {
		pc.setFishingTime(0);
		pc.setFishing(false);
		pc.setFishingRod(null);
		if(성장낚시){
			성장낚시 = false;
		} else if (고급성장낚시) {
			고급성장낚시 = false;
		}
		S_CharVisualUpdate ss = S_CharVisualUpdate.get(pc);
		pc.sendPackets(ss);
		pc.broadcastPacket(ss);
		removeMember(pc);
	}

	
	private void calculate_exp(L1PcInstance pc, EFishingType f_type){
		try {
			int level = Math.max(pc.getLevel(), 2);
			FishingExpInfo eInfo = FishingExpInfo.find_fishing_exp_info(f_type, level);
			if(eInfo == null)
				return;
			if (Config.STANDBY_SERVER) {
				pc.sendPackets(new S_SystemMessage("오픈대기중에는 경험치를 획득할 수 없으며 물품은 낚입니다"));
				return;
			}
			if (pc.getLevel() >= Config.LIMITLEVEL) {
				pc.sendPackets(new S_SystemMessage("더 이상 경험치를 획득 할 수 없습니다."));
				return;
			}
			
			int need_exp = ExpTable.getNeedExpNextLevel(52);
			double exp = need_exp * eInfo.get_default_exp();
			exp = exp * eInfo.get_addition_exp();
			exp = Math.max(1, exp);
			
			if (pc.getLevel() >= Config.LIMITLEVEL) {
				int maxexp = ExpTable.getExpByLevel(Config.LIMITLEVEL + 1);
				if (pc.getExp() + exp >= maxexp)
					return;
			}
			if ((exp + pc.getExp()) > ExpTable.getExpByLevel((pc.getLevel() + 1))) {
				exp = (ExpTable.getExpByLevel((pc.getLevel() + 1)) - pc.getExp());
			} 
			pc.addExp((int)exp);
			pc.save();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
