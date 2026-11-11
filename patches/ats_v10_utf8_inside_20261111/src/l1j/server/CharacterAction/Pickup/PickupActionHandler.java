package l1j.server.CharacterAction.Pickup;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.CharacterAction.Executor.CharacterActionExecutor;
import l1j.server.Template.LORnd;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameServer;
import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.datatables.ItemMessageTable;
import l1j.server.server.datatables.ItemMessageTable.L1ItemMessage;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.serverpackets.S_AttackStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.utils.SkillUtil;
import manager.SJManager;

public class PickupActionHandler extends AbstractActionHandler implements PacketParser {
	private static final long MAX_STACKABLE = 2000000000L;

	protected int x;
	protected int y;
	protected int tId;
	protected long amount;
	protected ItemInstance item;
	protected L1PcInventory inven;
	protected L1Inventory gInven;

	@Override
	public void dispose() {
		item = null;
		inven = null;
		gInven = null;
		super.dispose();
	}

	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
		this.owner = owner;
		x = pck.readH();
		y = pck.readH();
		tId = pck.readD();
		amount = pck.readD();
	}

	@Override
	public void doWork() {
		
		if(owner.hasSkillEffect(SkillId.메디테이션)) {
			owner.removeSkillEffect(SkillId.메디테이션);
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);
		
		register();
	}

	@Override
	public void handle() {
		if (!validation()) {
			return;
		}
		L1Party party = owner.getParty();
		if (party != null) {
			handleParty(party);
		} else {
			handleNonParty();
		}
		
		owner.getLight().turnOnOffLight();
		S_AttackStatus pck = S_AttackStatus.get(owner, tId, ActionCodes.ACTION_Pickup);
		owner.sendPackets(pck, false);
		pck.clear();
		if (!owner.isGmInvis() && !owner.isGhost() && !owner.isInvisble()) {
			owner.broadcastPacket(pck, false);
		}		
		/** 해당 아이템 드랍 체크및 설정 즉 밑 아이템은 위 조건문을 통해 갯수설정 **/ 
		if (((item.getCount() >= 100) && ((item.getItemId() == 41159)
				|| (item.getItemId() == 40087) || (item.getItemId() == 40074)))
				|| (item.getEnchantLevel() > 0)
				|| ((item.getCount() >= 1000000) && (item.getItemId() == 40308))
				|| (item.getItemId() != 7136)
				|| ((item.getCount() >= 1000) && (item.getItemId() != 40308))) {
			SJManager.getInstance().PicupAppend(item.getLogName(), owner.getName(), item.getCount(), 0);
		}
		if (!item.isGiveItem()) {
			if (ItemMessageTable.getInstance().isPickUpMessage(item.getItemId())) {
				L1ItemMessage temp = ItemMessageTable.getInstance().getPickUpMessage(item.getItemId());
				String men = "";
				if (temp != null) {
					if (temp.getType() == 1) {
						if (temp.isMentuse()) {
							if (temp.getOption() == 1)
								men = "" + owner.getName() + " 님께서";
							else
								men = "누군가가";

							if (temp.getMent() == null || temp.getMent().equals("")) {
								String locationName = MapsTable.getInstance().getMapName(owner.getMapId());
								String itemName = item.getViewName();
								if (itemName == null)
									itemName = item.getName();
								String message = String.format("" + men + " %s \\fH을(를) " + locationName + "에서 획득하였습니다.", itemName);
								L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(message)); 
							} else {
								L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(temp.getMent()));
								L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, temp.getMent()));
								
							}
						}
					}
				}
			}  
		}		
		item.setGiveItem(false);
		LoggerInstance.getInstance().addItemAction(ItemActionType.Pickup, owner, item, (int) amount);
		  
	}

	private void handleNonParty() {
		gInven.tradeItem(item, (int) amount, inven);

	}

	private void handleParty(L1Party party) {
		L1Npc npc = NpcTable.getInstance().getTemplate(item.isDropMobId());
		if (party.getLeader().getPartyType() == 1 && item.isDropMobId() != 0 && party.getNumOfMembers() > 1) {
			try {
				item.setDropMobId(0);
				Stream<L1PcInstance> stream = party.createVisibleMembersStream(owner);
				ArrayList<L1PcInstance> members = stream.collect(Collectors.toCollection(ArrayList::new));
				long len = members.size();
				if (item.getItemId() == ItemId.아데나 && amount > len) {
					int div = (int) (amount / len);
					int adder = (int) (amount % len);
					for (L1PcInstance pc : members) {
						int amt = div;
						if (pc.getId() == owner.getId())
							amt += adder;
						gInven.tradeItem(item, amt, pc.getInventory());
						ServerBasePacket pck = new S_ServerMessage(813, npc.get_name(), item.getLogName(),pc.getName());
						party.broadcastRootMent(pck);
					}
				} else {
					L1PcInstance luck = members.get(LORnd.next(members.size()));
					gInven.tradeItem(item, (int) amount, luck.getInventory());
					ServerBasePacket pck = new S_ServerMessage(813, npc.get_name(), item.getLogName(), luck.getName());
					party.broadcastRootMent(pck);
					members.clear();
				}
			} catch (Exception e) {
				gInven.tradeItem(item, (int) amount, inven);
				if (npc != null) {
					ServerBasePacket pck = new S_ServerMessage(813, npc.get_name(), item.getLogName(), owner.getName());
					party.broadcastRootMent(pck);
				}
			}
		} else {
			gInven.tradeItem(item, (int) amount, inven);
			
			if (npc != null) {
				ServerBasePacket pck = new S_ServerMessage(813, npc.get_name(), item.getLogName(), owner.getName());
				party.broadcastRootMent(pck);

			}
		}
	}

	@Override
	public boolean validation() {
		if(owner == null) {
			return false;
		}
		if (owner.isDead()) {
			return false;
		}
		if (commons.isNonAction(owner)) {
			return false;
		}
		
		return validationInventory() && validationState() && validationAmount() && validationPosition();
	}

	@Override
	public int getRegistIndex() {
		return CharacterActionExecutor.ACTION_IDX_PICKUP;
	}

	@Override
	public long getInterval() {
		return owner.hasSkillEffect(SkillId.셰이프_체인지) ? 10L : owner.getCurrentSpriteInterval(EActionCodes.get);
	}

	private boolean validationInventory() {
		gInven = L1World.getInstance().getInventory(x, y, owner.getMapId());
		L1Object obj = gInven.getItem(tId);
		
		if (obj != null) {
			item = (ItemInstance) obj;
			inven = owner.getInventory();
			
			if (inven.getWeight100() > 90) {
				owner.sendPackets("소지품이 너무 무겁습니다.");
				return false;
			} 
			
			if (inven.getWeight100() > 90 || owner.getMaxWeight() <= inven.getWeight()) {
				owner.sendPackets("소지품이 너무 무겁습니다.");
				return false;
			}
		}
		else {
			boolean ck = false;
			for (L1PcInstance temp : L1World.getInstance().getAllPlayers()) {
				if (temp.getInventory().getItem(tId) != null) {
					System.out.println("루팅 체크 > 바닥 허상 - 아이템 소유자 : " + temp.getName());
					ck = true;
					break;
				}
			}
			if (!ck) {
				for (L1Object object : L1World.getInstance().getAllItem()) {
					if (!(object instanceof ItemInstance)) {
						continue;
					}
					ItemInstance item = (ItemInstance) object;
					if (item.getId() == tId) {
						System.out.println("루팅 체크 > 바닥 허상 - 아이템 바닥 : x>"
										+ item.getX() + " y>"
										+ item.getY() + " map>"
										+ item.getMapId());
						return false; 
					}
				}
				System.out.println("루팅 체크 > 바닥 허상 - 존재하지 않는 아이템 objectId :" + tId);
			}
		}
		
		return true;
	}

	private boolean validationState() {
		L1PcInstance pc = item.getItemOwner();
		if (pc != null && pc.getId() != owner.getId()) {
			L1Party party = pc.getParty();
			if (party == null || !party.isMember(owner)) {
				owner.sendPackets(new S_ServerMessage(623));
				return false;
			}
		}
		return true;
	}

	private boolean validationAmount() {
		if (amount > item.getCount())
			amount = item.getCount();

		int itemAmount = item.getCount();
		if (amount <= 0 || itemAmount <= 0 || itemAmount > MAX_STACKABLE) {
			GameServer.disconnectChar(owner);
			gInven.deleteItem(item);
			return false;
		}

		if (item.isStackable()) {
			ItemInstance invItem = inven.findItemId(item.getItemId());
			long sum = invItem == null ? 0L : invItem.getCount();
			if (amount + sum > MAX_STACKABLE) {
				owner.sendPackets("아이템(아데나) 획득 초과로 드랍이 불가합니다.(창고이용)");
				return false;
			} else if (item.getItemId() != ItemId.아데나 && amount > 1000000) {
				owner.sendPackets("너무많은 드랍은 불가능합니다.");
				return false;
			}
		} else if (amount > 1) {
			GameServer.disconnectChar(owner);
			return false;
		}

		return inven.checkAddItem(item, (int) amount) == L1Inventory.OK;
	}

	private boolean validationPosition() {
		int cx = Math.abs(x - owner.getX());
		int cy = Math.abs(y - owner.getY());
		return (cx < 2 && cy < 2) && (item.getX() != 0 && item.getY() != 0);
	}
 

}
