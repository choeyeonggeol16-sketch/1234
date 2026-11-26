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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.w3c.dom.Element;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.L1NpcHtml;

public class L1NpcListedAction extends L1NpcXmlAction {
	private static final Logger LOG = Logger.getLogger(L1NpcListedAction.class.getName());
	private final List<L1NpcAction> _actions;
    private final boolean containsQuest;
    private final boolean containsCraft;
    private final boolean hasSplitCraftQuest;

	public L1NpcListedAction(Element element) {
		super(element);
		_actions = L1NpcXmlParser.listActions(element);
        boolean quest = false;
        boolean craft = false;
        boolean localSplit = false;
        for (L1NpcAction action : _actions) {
            final L1NpcListedAction nested = action instanceof L1NpcListedAction list ? list : null;
            final boolean actionHasQuest = action instanceof L1NpcSetQuestAction
                    || (nested != null && nested.containsQuest);
            localSplit |= craft && actionHasQuest;
            quest |= actionHasQuest;
            craft |= action instanceof L1NpcMakeItemAction || (nested != null && nested.containsCraft);
        }
        containsQuest = quest;
        containsCraft = craft;
        // A nested list rejects its own split sequence when reached. Its
        // presence alone must not disable other independently selected actions.
        hasSplitCraftQuest = localSplit;
        if (localSplit) LOG.warning("NPC XML quest reward needs SetQuest in the MakeItem Succeed branch: "
                + sourceDescription(element));
	}

    @Override
    public L1NpcHtml execute(String name, L1PcInstance pc, L1Object obj, byte[] args) {
        throw new IllegalStateException("XML list requires continuation entry");
    }

    @Override
    public L1NpcHtml executeWithAmount(String name, L1PcInstance pc, L1Object obj, int amount) {
        throw new IllegalStateException("XML list requires continuation entry");
    }

    @Override
    public L1NpcHtml executeDeferred(String name, L1PcInstance pc, L1Object obj,
            byte[] args, Consumer<L1NpcHtml> completion) {
        return run(name, pc, obj, args, 0, 0, null, List.of(), completion);
    }

    @Override
    public L1NpcHtml executeAmountDeferred(String name, L1PcInstance pc, L1Object obj,
            int amount, Consumer<L1NpcHtml> completion) {
        return run(name, pc, obj, null, amount, 0, null, List.of(), completion);
    }

    private enum QuestStageOrder { OPEN, CLOSED, UNSUPPORTED }

    /** A passive HTML node and unconditional nesting preserve the quest order. */
    final List<L1NpcSetQuestAction> successQuestStages() {
        if (!containsQuest) return List.of();
        final ArrayList<L1NpcSetQuestAction> quests = new ArrayList<>();
        if (collectQuestStages(quests, QuestStageOrder.OPEN) == QuestStageOrder.UNSUPPORTED) return null;
        return List.copyOf(quests);
    }

    private QuestStageOrder collectQuestStages(List<L1NpcSetQuestAction> quests, QuestStageOrder order) {
        for (L1NpcAction action : _actions) {
            if (action instanceof L1NpcSetQuestAction quest) {
                if (order != QuestStageOrder.OPEN || !quest.hasDefaultConditions())
                    return QuestStageOrder.UNSUPPORTED;
                quests.add(quest);
            } else if (action instanceof L1NpcListedAction nested) {
                if (nested.hasDefaultConditions()) {
                    order = nested.collectQuestStages(quests, order);
                    if (order == QuestStageOrder.UNSUPPORTED) return order;
                } else {
                    if (nested.containsQuest) return QuestStageOrder.UNSUPPORTED;
                    order = QuestStageOrder.CLOSED;
                }
            } else if (!(action instanceof L1NpcShowHtmlAction html) || !html.hasDefaultConditions()) {
                order = QuestStageOrder.CLOSED;
            }
        }
        return order;
    }

    final L1NpcHtml resumeAfterCraftQuests(String name, L1PcInstance pc, L1Object obj,
            byte[] args, List<L1NpcSetQuestAction> stagedQuests, Consumer<L1NpcHtml> completion) {
        return run(name, pc, obj, args, 0, 0, null, stagedQuests, completion);
    }

    /** Keep original order, acceptance checks and last non-null HTML. Only
     * suspend at a receipt; no new worker, queue or blocking wait. */
    private L1NpcHtml run(String name, L1PcInstance pc, L1Object obj, byte[] args,
            int amount, int start, L1NpcHtml result, List<L1NpcSetQuestAction> stagedQuests,
            Consumer<L1NpcHtml> completion) {
        if (hasSplitCraftQuest) return rejectUnsafeQuestReward(pc);
        for (int i = start; i < _actions.size(); i++) {
            L1NpcAction action = _actions.get(i);
            if (action instanceof L1NpcSetQuestAction && stagedQuests.contains(action)) continue;
            if (!action.acceptsRequest(name, pc, obj)) continue;
            L1NpcHtml r;
            if (action instanceof L1NpcMakeItemAction || action instanceof L1NpcListedAction
                    || action instanceof L1NpcSetQuestAction) {
                final int next = i + 1;
                final L1NpcHtml previous = result;
                Consumer<L1NpcHtml> resume = html -> {
                    if (html == ABORTED) {
                        completion.accept(ABORTED);
                        return;
                    }
                    L1NpcHtml tail = run(name, pc, obj, args, amount, next,
                            html == null ? previous : html, stagedQuests, completion);
                    if (tail != DEFERRED) completion.accept(tail);
                };
                if (action instanceof L1NpcListedAction nested && !stagedQuests.isEmpty()) {
                    r = nested.run(name, pc, obj, args, amount, 0, null, stagedQuests, resume);
                } else {
                    r = amount == 0 ? action.executeDeferred(name, pc, obj, args, resume)
                            : action.executeAmountDeferred(name, pc, obj, amount, resume);
                }
            } else {
                r = amount == 0 ? action.execute(name, pc, obj, args)
                        : action.executeWithAmount(name, pc, obj, amount);
            }
            if (r == DEFERRED || r == ABORTED) return r;
            if (r != null) result = r;
        }
        return result;
    }
}
