/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.npc.action;

import l1j.server.server.model.network.core.sc.packet.SC_HowManyMake;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1ObjectAmount;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.IterableElementList;

public class L1NpcMakeItemAction extends L1NpcXmlAction {
	private static final Logger LOG = Logger.getLogger(L1NpcMakeItemAction.class.getName());
	private static final byte[] EMPTY_ARGS = new byte[0];
	private final List<L1ObjectAmount<Integer>> _materials = new ArrayList<L1ObjectAmount<Integer>>();
	private final List<L1ObjectAmount<Integer>> _items = new ArrayList<L1ObjectAmount<Integer>>();
	private final boolean _isAmountInputable;
	private final L1NpcAction _actionOnSucceed;
	private final L1NpcAction _actionOnFail;

	public L1NpcMakeItemAction(Element element) {
		super(element);

		_isAmountInputable = L1NpcXmlParser.getBoolAttribute(element, "AmountInputable", true);
		NodeList list = element.getChildNodes();
		for (Element elem : new IterableElementList(list)) {
			if (elem.getNodeName().equalsIgnoreCase("Material")) {
				int id = Integer.valueOf(elem.getAttribute("ItemId"));
				int amount = Integer.valueOf(elem.getAttribute("Amount"));
				int enchant = 0;
				try {
					enchant = Integer.valueOf(elem.getAttribute("Enchant"));
				} catch (Exception e) { }
				_materials.add(new L1ObjectAmount<Integer>(id, amount, enchant));
				continue;
			}
			if (elem.getNodeName().equalsIgnoreCase("Item")) {
				int id = Integer.valueOf(elem.getAttribute("ItemId"));
				int amount = Integer.valueOf(elem.getAttribute("Amount"));
				int enchant = 0;
				try {
					enchant = Integer.valueOf(elem.getAttribute("Enchant"));
				} catch (Exception e) { }
				_items.add(new L1ObjectAmount<Integer>(id, amount, enchant));
				continue;
			}
		}

		if (_items.isEmpty() || _materials.isEmpty()) {
			throw new IllegalArgumentException();
		}

		Element elem = L1NpcXmlParser.getFirstChildElementByTagName(element, "Succeed");
		_actionOnSucceed = elem == null ? null : new L1NpcListedAction(elem);
		elem = L1NpcXmlParser.getFirstChildElementByTagName(element, "Fail");
		_actionOnFail = elem == null ? null : new L1NpcListedAction(elem);
	}

    /** Prepare exact unequipped material rows and detached reward images. */
    private boolean submit(L1PcInstance pc, L1NpcInstance npc, int amount,
            Runnable success, Runnable failure) {
        if (amount <= 0 || amount >= 1000 || (!_isAmountInputable && amount != 1)) return false;
        final L1PcInventory inv = pc.getInventory();
        final String npcName = npc.getNpcTemplate().get_name();
        final ItemTable templates = ItemTable.getInstance();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcTemplate().get_npcId())) {
            for (int i = 0; i < _materials.size(); i++) {
                L1ObjectAmount<Integer> material = _materials.get(i);
                final int id = material.getObject(), enchant = material.getEnchant();
                if (!validExpandedAmount(material.getAmount(), amount)) return false;
                L1Item definition = templates.getTemplate(id);
                if (definition == null) return false;
                boolean first = true;
                for (int j = 0; j < i; j++) {
                    L1ObjectAmount<Integer> earlier = _materials.get(j);
                    if (earlier.getObject().intValue() == id && earlier.getEnchant() == enchant) {
                        first = false; break;
                    }
                }
                if (!first) continue;
                long required = 0;
                for (int j = i; j < _materials.size(); j++) {
                    L1ObjectAmount<Integer> row = _materials.get(j);
                    if (row.getObject().intValue() != id || row.getEnchant() != enchant) continue;
                    if (!validExpandedAmount(row.getAmount(), amount)) return false;
                    required += (long) row.getAmount() * amount;
                    if (required > L1Inventory.MAX_AMOUNT) return false;
                }
                long available = 0;
                for (L1ItemInstance item : inv.getItems()) {
                    if (!item.isEquipped() && item.getItemId() == id && item.getEnchantLevel() == enchant)
                        available += item.getCount();
                }
                if (available < required) {
                    String materialName = rewardName(definition, enchant, 1);
                    SC_ServerMessage.send(pc, 337, materialName + "(" + (required - available) + ")");
                    return false;
                }
                int left = (int) required;
                for (L1ItemInstance item : inv.getItems()) {
                    if (left == 0) break;
                    if (item.isEquipped() || item.getItemId() != id || item.getEnchantLevel() != enchant) continue;
                    int take = Math.min(left, item.getCount());
                    if (take <= 0) continue;
                    if (!tx.debit(item, take)) return false;
                    left -= take;
                }
                if (left != 0) return false;
            }
            // Preserve the existing conservative preflight; the receipt also
            // validates projected stack quantities, capacity and weight.
            long slots = 0, weight = 0;
            for (L1ObjectAmount<Integer> row : _items) {
                if (!validExpandedAmount(row.getAmount(), amount)) return false;
                L1Item definition = templates.getTemplate(row.getObject());
                if (definition == null) return false;
                int count = row.getAmount() * amount;
                slots += definition.isStackable() ? (inv.checkItem(row.getObject()) ? 0 : 1) : count;
                weight = Math.addExact(weight, (long) definition.getWeight() * count / 1000);
                if (slots > 1000) return false;
            }
            if (inv.getSize() + slots > 180) { SC_ServerMessage.send(pc, 263); return false; }
            if (pc.getMaxWeight() < Math.addExact((long) inv.getWeight(), weight)) {
                SC_ServerMessage.send(pc, 82); return false;
            }
            final String[] rewardNames = new String[_items.size()];
            for (int i = 0; i < _items.size(); i++) {
                L1ObjectAmount<Integer> row = _items.get(i);
                int count = row.getAmount() * amount;
                L1Item definition = templates.getTemplate(row.getObject());
                // Enchant BEFORE staging: a stack must never be merged first
                // and then have the enchant of the existing stack overwritten.
                L1ItemInstance image = new L1ItemInstance(definition, count);
                image.setId(IdFactory.getInstance().nextId());
                image.setBless(definition.getBless());
                image.setIdentified(true);
                image.setEnchantLevel(row.getEnchant());
                if (tx.grant(image) == null) return false;
                rewardNames[i] = rewardName(definition, row.getEnchant(), count);
            }
            tx.onFailure(failure);
            tx.commitAfterInventoryRelease(() -> {
                for (String rewardName : rewardNames) {
                    SC_ServerMessage.send(pc, 143, npcName, rewardName);
                }
                success.run();
            });
            return true;
        } catch (RuntimeException rejected) {
            LOG.log(Level.FINE, "XML NPC craft rejected", rejected);
            return false;
        }
    }

    private static String rewardName(L1Item definition, int enchant, int count) {
        String name = definition.getName();
        if (count > 1) name += " (" + count + ")";
        if (enchant != 0) name = (enchant < 0 ? Integer.toString(enchant) : "+" + enchant) + " " + name;
        return name;
    }

    private static boolean validExpandedAmount(int unit, int amount) {
        return unit > 0 && amount > 0 && (long) unit * amount <= L1Inventory.MAX_AMOUNT;
    }

    /** Bound the quantity prompt and direct packet requests using the same exact
     * enchant/unequipped requirements, including repeated material entries. */
    private int countNumOfMaterials(L1PcInventory inv) {
        int count = 999;
        for (int i = 0; i < _materials.size(); i++) {
            L1ObjectAmount<Integer> material = _materials.get(i);
            int id = material.getObject();
            int enchant = material.getEnchant();
            if (material.getAmount() <= 0 || ItemTable.getInstance().getTemplate(id) == null) return 0;
            long required = 0;
            for (int j = 0; j < _materials.size(); j++) {
                L1ObjectAmount<Integer> other = _materials.get(j);
                if (other.getObject().intValue() == id && other.getEnchant() == enchant) {
                    if (other.getAmount() <= 0) return 0;
                    required += other.getAmount();
                }
            }
            if (required > L1Inventory.MAX_AMOUNT) return 0;
            long available = 0;
            for (L1ItemInstance item : inv.getItems()) {
                if (!item.isEquipped() && item.getItemId() == id && item.getEnchantLevel() == enchant)
                    available += item.getCount();
            }
            count = (int) Math.min(count, Math.min(available, L1Inventory.MAX_AMOUNT) / required);
        }
        for (L1ObjectAmount<Integer> reward : _items) {
            if (reward.getAmount() <= 0 || ItemTable.getInstance().getTemplate(reward.getObject()) == null) return 0;
            count = Math.min(count, L1Inventory.MAX_AMOUNT / reward.getAmount());
        }
        return count;
    }

    @Override
    public L1NpcHtml execute(String name, L1PcInstance pc, L1Object obj, byte[] args) {
        throw new IllegalStateException("XML craft requires continuation entry");
    }

    @Override
    public L1NpcHtml executeWithAmount(String name, L1PcInstance pc, L1Object obj, int amount) {
        throw new IllegalStateException("XML craft requires continuation entry");
    }

    @Override
    public L1NpcHtml executeDeferred(String name, L1PcInstance pc, L1Object obj,
            byte[] args, Consumer<L1NpcHtml> completion) {
        if (!(obj instanceof L1NpcInstance)) return L1NpcHtml.HTML_CLOSE;
        int available = countNumOfMaterials(pc.getInventory());
        if (available > 1 && _isAmountInputable) {
            SC_HowManyMake.send(pc, obj.getId(), available, name);
            return null;
        }
        return executeAmountDeferred(name, pc, obj, 1, completion);
    }

    @Override
    public L1NpcHtml executeAmountDeferred(String name, L1PcInstance pc, L1Object obj,
            int amount, Consumer<L1NpcHtml> completion) {
        if (!(obj instanceof L1NpcInstance npc)) return L1NpcHtml.HTML_CLOSE;
        if (submit(pc, npc, amount,
                () -> completeOutcome(_actionOnSucceed, name, pc, obj, completion),
                () -> completeOutcome(_actionOnFail, name, pc, obj, completion))) return DEFERRED;
        return outcome(_actionOnFail, name, pc, obj, completion);
    }

    private static L1NpcHtml outcome(L1NpcAction branch, String name, L1PcInstance pc,
            L1Object obj, Consumer<L1NpcHtml> completion) {
        if (branch == null) return L1NpcHtml.HTML_CLOSE;
        L1NpcHtml result = branch.executeDeferred(name, pc, obj, EMPTY_ARGS,
                html -> completion.accept(html == null ? L1NpcHtml.HTML_CLOSE : html));
        return result == null ? L1NpcHtml.HTML_CLOSE : result;
    }

    private static void completeOutcome(L1NpcAction branch, String name, L1PcInstance pc,
            L1Object obj, Consumer<L1NpcHtml> completion) {
        L1NpcHtml result = outcome(branch, name, pc, obj, completion);
        if (result != DEFERRED) completion.accept(result);
    }
}
