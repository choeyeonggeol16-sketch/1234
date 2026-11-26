package l1j.server.server.model.npc.action;

import java.util.function.Consumer;

import org.w3c.dom.Element;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1NpcSetQuestAction extends L1NpcXmlAction {
	private final int _id;
	private final int _step;

	public L1NpcSetQuestAction(Element element) {
		super(element);

		_id = L1NpcXmlParser.parseQuestId(element.getAttribute("Id"));
		_step = L1NpcXmlParser.parseQuestStep(element.getAttribute("Step"));

		if (_id <= 0 || _step == -1) {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public L1NpcHtml execute(String actionName, L1PcInstance pc, L1Object obj, byte[] args) {
		throw new IllegalStateException("XML quest requires continuation entry");
	}

    /** Stage only after the enclosing craft has reserved the owner gate. */
    final void stageInCraft(L1InventoryTransaction tx, L1PcInstance pc) {
        final int expected = pc.getQuest().get_step(_id);
        CQuestSystem.StageQuestStep(tx, pc.getId(), _id, expected, _step, 0);
    }

    final void publishCraftStep(L1PcInstance pc) {
        pc.getQuest().applyCommittedStepIfLoaded(_id, _step);
    }

    @Override
    public L1NpcHtml executeDeferred(String name, L1PcInstance pc, L1Object obj,
            byte[] args, Consumer<L1NpcHtml> completion) {
        final var quest = pc.getQuest();
        final int expected = quest.get_step(_id);
        if (quest.RequestSetSelectedQuestDeferred(_id, expected, _step,
                () -> completion.accept(null), () -> completion.accept(ABORTED)))
            return DEFERRED;
        SC_SystemMessage.send(pc, "퀘스트 진행 상태를 변경할 수 없습니다.");
        return ABORTED;
    }

}
