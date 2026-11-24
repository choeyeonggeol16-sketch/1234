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

import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.L1NpcHtml;

public class L1NpcListedAction extends L1NpcXmlAction {
	private final List<L1NpcAction> _actions;

	public L1NpcListedAction(Element element) {
		super(element);
		_actions = L1NpcXmlParser.listActions(element);
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
        return run(name, pc, obj, args, 0, 0, null, completion);
    }

    @Override
    public L1NpcHtml executeAmountDeferred(String name, L1PcInstance pc, L1Object obj,
            int amount, Consumer<L1NpcHtml> completion) {
        return run(name, pc, obj, null, amount, 0, null, completion);
    }

    /** Do not reorder past any action or fold a conditional/nested child. */
    final L1NpcSetQuestAction leadingPlainQuest(L1PcInstance pc) {
        if (!_actions.isEmpty() && _actions.get(0) instanceof L1NpcSetQuestAction quest
                && quest.isPlainQuestAction(pc)) return quest;
        return null;
    }

    final L1NpcHtml resumeAfterCraftQuest(String name, L1PcInstance pc, L1Object obj,
            byte[] args, Consumer<L1NpcHtml> completion) {
        return run(name, pc, obj, args, 0, 1, null, completion);
    }

    /** Keep original order, acceptance checks and last non-null HTML. Only
     * suspend at a receipt; no new worker, queue or blocking wait. */
    private L1NpcHtml run(String name, L1PcInstance pc, L1Object obj, byte[] args,
            int amount, int start, L1NpcHtml result, Consumer<L1NpcHtml> completion) {
        for (int i = start; i < _actions.size(); i++) {
            L1NpcAction action = _actions.get(i);
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
                            html == null ? previous : html, completion);
                    if (tail != DEFERRED) completion.accept(tail);
                };
                r = amount == 0 ? action.executeDeferred(name, pc, obj, args, resume)
                        : action.executeAmountDeferred(name, pc, obj, amount, resume);
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
