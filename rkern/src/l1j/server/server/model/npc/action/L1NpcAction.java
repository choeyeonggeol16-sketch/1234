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

import java.util.function.Consumer;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.dialog.L1NpcHtml;

public interface L1NpcAction {

	public boolean acceptsRequest(String actionName, L1PcInstance pc, L1Object obj);

	public L1NpcHtml execute(String actionName, L1PcInstance pc, L1Object obj, byte args[]);

	public L1NpcHtml executeWithAmount(String actionName, L1PcInstance pc, L1Object obj, int amount);

    /** Internal identity marker, never sent as a packet. A deferred action
     * calls completion exactly once on confirmed success or rejection, never
     * on an unknown receipt outcome. Immediate actions return their result. */
    L1NpcHtml DEFERRED = new L1NpcHtml("");
    /** Failed prerequisite; propagate through nested lists without executing their tail. */
    L1NpcHtml ABORTED = new L1NpcHtml("");

    default L1NpcHtml executeDeferred(String name, L1PcInstance pc, L1Object obj,
            byte[] args, Consumer<L1NpcHtml> completion) {
        return execute(name, pc, obj, args);
    }

    default L1NpcHtml executeAmountDeferred(String name, L1PcInstance pc, L1Object obj,
            int amount, Consumer<L1NpcHtml> completion) {
        return executeWithAmount(name, pc, obj, amount);
    }

    /** Shared entry for talk, HTML command and amount packets. */
    default void dispatch(String name, L1PcInstance pc, L1Object obj, byte[] args, int amount) {
        final int objectId = obj.getId();
        final var client = pc.getNetConnection();
        final int dialogueVersion = client == null ? 0 : client.npcDialogueVersion();
        final long dialogueTarget = client == null ? 0 : L1World.getInstance().smartIdOf(obj);
        Consumer<L1NpcHtml> sendDeferred = html -> {
            if (html != null && html != DEFERRED
                    && client != null && client.canReplyToNpcDialogue(pc, dialogueTarget, dialogueVersion))
                SC_NPCTalkReturn.send(pc, objectId, html == ABORTED ? L1NpcHtml.HTML_CLOSE : html);
        };
        L1NpcHtml result = amount == 0 ? executeDeferred(name, pc, obj, args, sendDeferred)
                : executeAmountDeferred(name, pc, obj, amount, sendDeferred);
        if (result != null && result != DEFERRED)
            SC_NPCTalkReturn.send(pc, objectId, result == ABORTED ? L1NpcHtml.HTML_CLOSE : result);
    }
}