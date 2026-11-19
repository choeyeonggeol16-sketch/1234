package l1j.server.server.model.Instance;

import java.util.ArrayList;

import l1j.server.Config;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.item.Exp.CalcExp;

import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;
public class L1ScarecrowInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	public L1ScarecrowInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance player) {
		CAttackAction.attackScarecrow(player, this);
	}

	/** Reward hook: called only for a confirmed hit, before damage is calculated. */
	public void onTrainingHitBeforeDamage(L1PcInstance player) {
		if (player.getLevel() < Config.SCARELEVEL && !player.noPlayerCK) {
			ArrayList<L1PcInstance> targetList = new ArrayList<L1PcInstance>();
			targetList.add(player);
			ArrayList<Integer> hateList = new ArrayList<Integer>();
			hateList.add(1);
			CalcExp.calcExp(player, getId(), targetList, hateList, getExp());
		}
	}

	/** Training display and rotation hook; no HP commit for the scarecrow. */
	public void onTrainingHitAfterDamage(L1PcInstance player, int dmg) {
		if (getNpcId() == 7320088) {
			SC_SystemMessage.send(player, "\\aH현재 물리대미지 : [ \\aA" + dmg + "\\aH ]");
		}
		if (getHeading() < 7) {
			setHeading(getHeading() + 1);
		} else {
			setHeading(0);
		}
		SC_ChangeHeading.broadcast(this);
	}

	@Override
	public void onTalkAction(L1PcInstance l1pcinstance) {}
	public void onFinalAction() {}
	public void doFinalAction() {}
}
