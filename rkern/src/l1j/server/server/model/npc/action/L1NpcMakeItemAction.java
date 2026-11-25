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

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_HowManyMake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import l1j.server.server.IdFactory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1ObjectAmount;
import l1j.server.server.model.CUserInventory;
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
	private record Material(int itemId, int enchant, long amount) {}
	private final Material[] _materials;
	private final List<L1ObjectAmount<Integer>> _items = new ArrayList<L1ObjectAmount<Integer>>();
	private final boolean _isAmountInputable;
	private final L1NpcListedAction _actionOnSucceed;
	private final L1NpcAction _actionOnFail;

	public L1NpcMakeItemAction(Element element) {
		super(element);

		_isAmountInputable = L1NpcXmlParser.getBoolAttribute(element, "AmountInputable", true);
		Map<Long, Material> materials = new LinkedHashMap<>();
		NodeList list = element.getChildNodes();
		for (Element elem : new IterableElementList(list)) {
			if (elem.getNodeName().equalsIgnoreCase("Material")) {
				int id = Integer.parseInt(elem.getAttribute("ItemId"));
				int amount = Integer.parseInt(elem.getAttribute("Amount"));
				int enchant = L1NpcXmlParser.getIntAttribute(elem, "Enchant", 0);
				long key = ((long) id << 32) | Integer.toUnsignedLong(enchant);
				Material previous = materials.get(key);
				long prior = previous == null ? 0L : previous.amount();
				long total = amount <= 0 || prior < 0 ? -1L : prior + amount;
				materials.put(key, new Material(id, enchant, total));
				continue;
			}
			if (elem.getNodeName().equalsIgnoreCase("Item")) {
				int id = Integer.parseInt(elem.getAttribute("ItemId"));
				int amount = Integer.parseInt(elem.getAttribute("Amount"));
				int enchant = L1NpcXmlParser.getIntAttribute(elem, "Enchant", 0);
				_items.add(new L1ObjectAmount<Integer>(id, amount, enchant));
				continue;
			}
		}

		if (_items.isEmpty() || materials.isEmpty()) {
			throw new IllegalArgumentException();
		}
		_materials = materials.values().toArray(Material[]::new);

		Element elem = L1NpcXmlParser.getFirstChildElementByTagName(element, "Succeed");
		_actionOnSucceed = elem == null ? null : new L1NpcListedAction(elem);
		elem = L1NpcXmlParser.getFirstChildElementByTagName(element, "Fail");
		_actionOnFail = elem == null ? null : new L1NpcListedAction(elem);
	}

    /** Prepare exact unequipped material rows and detached reward images. */
    private boolean submit(L1PcInstance pc, L1NpcInstance npc, int amount,
            String name, Consumer<L1NpcHtml> completion, Runnable failure) {
        if (amount <= 0 || amount >= 1000 || (!_isAmountInputable && amount != 1)) return false;
        final CUserInventory inv = pc.getInventory();
        final String npcName = npc.getNpcTemplate().get_name();
        final CObjectDB.ItemData templates = CObjectDB.LoadItem();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcTemplate().get_npcId())) {
            for (Material material : _materials) {
                final int id = material.itemId(), enchant = material.enchant();
                if (material.amount() <= 0 || material.amount() > CInventory.MAX_AMOUNT / amount) return false;
                final long required = material.amount() * amount;
                L1Item definition = templates.getTemplate(id);
                if (definition == null) return false;
                final long available = inv.GetRecipeMaterialAmount(id, enchant);
                if (available < required) {
                    String materialName = rewardName(definition, enchant, 1);
                    SC_ServerMessage.send(pc, 337, materialName + "(" + (required - available) + ")");
                    return false;
                }
                if (!tx.DebitRecipeMaterial(id, enchant, (int) required)) return false;
            }
            // Preserve the existing conservative preflight; the receipt also
            // validates projected stack quantities, capacity and weight.
            long slots = 0, weight = 0;
            for (L1ObjectAmount<Integer> row : _items) {
                if (!validExpandedAmount(row.getAmount(), amount)) return false;
                L1Item definition = templates.getTemplate(row.getObject());
                if (definition == null) return false;
                int count = row.getAmount() * amount;
                slots += definition.isStackable() ? (inv.HaveItemByClassId(row.getObject()) ? 0 : 1) : count;
                weight = Math.addExact(weight, (long) definition.getWeight() * count / 1000);
                if (slots > 1000) return false;
            }
            if (inv.getSize() + slots > 180) { SC_ServerMessage.send(pc, 263); return false; }
            if (pc.getMaxWeight() < Math.addExact((long) inv.GetCarryingWeight(), weight)) {
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
            final L1NpcSetQuestAction quest = _actionOnSucceed == null
                    ? null : _actionOnSucceed.leadingPlainQuest(pc);
            if (quest != null) quest.stageInCraft(tx, pc);
            tx.onFailure(failure);
            Runnable effects = () -> {
                for (String rewardName : rewardNames) {
                    SC_ServerMessage.send(pc, 143, npcName, rewardName);
                }
                if (quest == null) {
                    completeOutcome(_actionOnSucceed, name, pc, npc, completion);
                } else {
                    L1NpcHtml result = _actionOnSucceed.resumeAfterCraftQuest(name, pc, npc,
                            EMPTY_ARGS, html -> completion.accept(html == null ? L1NpcHtml.HTML_CLOSE : html));
                    if (result != DEFERRED)
                        completion.accept(result == null ? L1NpcHtml.HTML_CLOSE : result);
                }
            };
            if (quest == null) tx.commitAfterInventoryRelease(effects);
            else tx.commitAfterInventoryRelease(() -> quest.publishCraftStep(pc), effects);
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
        return unit > 0 && amount > 0 && (long) unit * amount <= CInventory.MAX_AMOUNT;
    }

    /** Bound the quantity prompt and direct packet requests using the same exact
     * enchant/unequipped requirements, including repeated material entries. */
    private int countNumOfMaterials(CUserInventory inv) {
        int count = 999;
        for (Material material : _materials) {
            int id = material.itemId();
            int enchant = material.enchant();
            long required = material.amount();
            if (required <= 0 || required > CInventory.MAX_AMOUNT
                    || CObjectDB.GetItem(id) == null) return 0;
            final long available = inv.GetRecipeMaterialAmount(id, enchant);
            count = (int) Math.min(count, Math.min(available, CInventory.MAX_AMOUNT) / required);
        }
        for (L1ObjectAmount<Integer> reward : _items) {
            if (reward.getAmount() <= 0 || CObjectDB.GetItem(reward.getObject()) == null) return 0;
            count = Math.min(count, CInventory.MAX_AMOUNT / reward.getAmount());
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
        if (submit(pc, npc, amount, name, completion,
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
