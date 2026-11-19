package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class ManaConsumables {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ManaConsumables.class.getName());

    private ManaConsumables() {}

    // An inventory receipt is already committed when these packets are sent.
    // A transport failure must not skip restoration or quarantine an applied MP effect.
    private static void sendMpNotice(L1PcInstance pc) {
        try {
            SC_ServerMessage.send(pc, 338, "$1084");
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Committed MP restoration; notice failed: " + pc.getId(), failure);
        }
    }

    private static void sendMpSound(L1PcInstance pc) {
        try {
            SC_SkillSound.send(pc, pc.getId(), 190);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Committed MP restoration; sound failed: " + pc.getId(), failure);
        }
    }
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 40067:// 쑥송편
		case 41414:// 복월병
			final int restoreMpStrong = 15 + java.util.concurrent.ThreadLocalRandom.current().nextInt(30);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMpStrong);
				sendMpSound(pc);
			});
			break;

		case 40066:// 송편
		case 41413:// 월병
			final int restoreMpSmall = 5 + java.util.concurrent.ThreadLocalRandom.current().nextInt(10);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMpSmall);
				sendMpSound(pc);
			});
			break;

		case 500220:
			if (pc.hasSkillEffect(L1SkillId.WITCH_MANA_POTION)) {
				int n = pc.getSkillEffectTimeSec(L1SkillId.WITCH_MANA_POTION);
				SC_SystemMessage.send(pc, "" + n + "초 후에 사용할 수 있습니다.");
				return;
			}
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(L1SkillId.WITCH_MANA_POTION, 1800 * 1000);
				pc.setCurrentMp(pc.getCurrentMp() + 1000);
				sendMpNotice(pc);
				sendMpSound(pc);
			});
			break;

		case 410002:// 빛나는 나뭇잎
			final int restoreMp410002 = 44;
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMp410002);
				sendMpNotice(pc);
			});
			break;

		case 40735:// 용기의 코인
			final int restoreMp40735 = 60;
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMp40735);
				sendMpNotice(pc);
			});
			break;

		case 40042:// 정신력의 물약
			final int restoreMp40042 = 50;
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMp40042);
				sendMpNotice(pc);
			});
			break;

		case 41404:// 쿠작의 영약
			final int restoreMp41404 = (80 + java.util.concurrent.ThreadLocalRandom.current().nextInt(21));
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMp41404);
				sendMpNotice(pc);
			});
			break;

		case 41412:// 금쫑즈
			final int restoreMp41412 = (5 + java.util.concurrent.ThreadLocalRandom.current().nextInt(16));
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setCurrentMp(pc.getCurrentMp() + restoreMp41412);
				sendMpNotice(pc);
			});
			break;

        }
    }
}
