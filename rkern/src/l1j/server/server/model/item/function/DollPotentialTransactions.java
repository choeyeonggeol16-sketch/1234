package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.utils.CommonUtil;

import l1j.server.Config;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;



final class DollPotentialTransactions {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(DollPotentialTransactions.class.getName());
    private DollPotentialTransactions() {}

    static void useDollPotential(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance doll) {
        if (pc == null || scroll == null || doll == null || scroll == doll) return;
        final int mode = scroll.getItemId();
        if (mode != 300001 && mode != 400001 && mode != 500001
                && mode != 600001 && mode != 700001) return;
        if (pc.getInventory().getItem(scroll.getId()) != scroll
                || pc.getInventory().getItem(doll.getId()) != doll || doll.isEquipped()) return;
        for (Object value : pc.getDollList().values()) {
            if (value instanceof L1DollInstance
                    && ((L1DollInstance) value).getItemObjId() == doll.getId()) {
                SC_SystemMessage.send(pc, "소환중인 인형은 강화할 수 없습니다.");
                return;
            }
        }

        final int previous = doll.get_item_level();
        final int tier;
        final int count;
        if (mode == 300001) {
            if (previous >= 0 && previous <= 19) {
                tier = 0; count = Config.일반갯수;
            } else if (previous >= 20 && previous <= 52) {
                tier = 1; count = Config.고급갯수;
            } else if (previous >= 53 && previous <= 92) {
                tier = 2; count = Config.희귀갯수;
            } else if (previous >= 93 && previous <= 130) {
                tier = 3; count = Config.영웅갯수;
            } else if (previous >= 131 && previous <= 139) {
                tier = 4; count = Config.전설갯수;
            } else {
                return;
            }
        } else {
            tier = mode == 400001 ? 1 : mode == 500001 ? 2 : mode == 600001 ? 3 : 4;
            count = 1;
        }
        if (count <= 0) {
            SC_SystemMessage.send(pc, "잠재력강화 아이템 설정을 확인해 주세요.");
            return;
        }
        final int roll = mode == 300001 ? CommonUtil.random(1000) : 0;
        final int next;
        if (mode == 300001) {
            if (tier == 0) {
                if (roll <= Config.잠재력없음일반확률) next = CommonUtil.random(19) + 1;
                else if (roll <= Config.잠재력없음일반확률 + Config.잠재력없음고급확률)
                    next = CommonUtil.random(33) + 20;
                else if (roll <= Config.잠재력없음일반확률 + Config.잠재력없음고급확률
                        + Config.잠재력없음희귀확률) next = CommonUtil.random(40) + 53;
                else if (roll <= Config.잠재력없음일반확률 + Config.잠재력없음고급확률
                        + Config.잠재력없음희귀확률 + Config.잠재력없음영웅확률)
                    next = CommonUtil.random(38) + 93;
                else next = rollLegendaryPotential(CommonUtil.random(90) + 1);
            } else if (tier == 1) {
                if (roll <= Config.잠재력고급될확률) next = CommonUtil.random(33) + 20;
                else if (roll <= Config.잠재력고급될확률 + Config.잠재력희귀될확률)
                    next = CommonUtil.random(40) + 53;
                else if (roll <= Config.잠재력고급될확률 + Config.잠재력희귀될확률
                        + Config.잠재력영웅될확률) next = CommonUtil.random(38) + 93;
                else next = rollLegendaryPotential(CommonUtil.random(90) + 1);
            } else if (tier == 2) {
                if (roll <= Config.잠재력희귀희귀확률) next = CommonUtil.random(40) + 53;
                else if (roll <= Config.잠재력희귀희귀확률 + Config.잠재력영웅영웅확률)
                    next = CommonUtil.random(38) + 93;
                else next = rollLegendaryPotential(CommonUtil.random(90) + 1);
            } else if (tier == 3 && roll <= Config.잠재력영웅확률) {
                next = CommonUtil.random(38) + 93;
            } else {
                next = rollLegendaryPotential(CommonUtil.random(90) + 1);
            }
        } else if (tier == 1) next = CommonUtil.random(33) + 20;
        else if (tier == 2) next = CommonUtil.random(40) + 53;
        else if (tier == 3) next = CommonUtil.random(38) + 93;
        else next = rollLegendaryPotential(CommonUtil.random(90) + 1);

        final String name = doll.getLogName();
        final boolean heroic = next >= 93 && next <= 130;
        final boolean legendary = next >= 131 && next <= 139;
        final String message = name + (legendary ? "에 \\f4잠재력: 전설\\fH이 스며듭니다."
                : heroic ? "에 \\f3잠재력: 영웅\\fH이 스며듭니다."
                : next >= 53 ? "에 \\aL잠재력: 희귀\\fH가 스며듭니다."
                : next >= 20 ? "에 \\aH잠재력: 고급\\fH이 스며듭니다."
                : "에 \\f=잠재력: 일반\\fH이 스며듭니다.");
        // Old staged upgrades announced heroic/legendary results only when
        // upgrading from the lower tiers. Forced-scroll upgrades never did.
        final boolean announceHero = mode == 300001 && previous <= 92
                && heroic && Config.잠재력성공;
        final boolean announceLegend = mode == 300001 && previous <= 130
                && legendary && Config.잠재력성공1;

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, scroll.getId())) {
            // 300001 may be stored in several stacks; the original rule checks
            // total template count, so debit the full amount across those stacks.
            final boolean debited = mode == 300001 ? tx.debit(300001, count) : tx.debit(scroll, 1);
            if (!debited) {
                SC_SystemMessage.send(pc, "\\f:잠재력강화 아이템이 부족합니다.");
                return;
            }
            tx.change(doll, image -> image.set_item_level(next));
            tx.onFailure(() -> SC_SystemMessage.send(pc, "잠재력 강화가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                try { pc.sendPackets(message); }
                catch (RuntimeException noticeFailure) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "Committed doll-potential message failed", noticeFailure);
                }
                if (announceHero) {
                    if (Config.영웅등급멘트)
                        try { SC_PacketBox.broadcastGreen(pc.getName() + "님의 마법인형이 \\f3[잠재력: 영웅]\\f2으로 강화되었습니다."); }
                        catch (RuntimeException noticeFailure) {
                            LOG.log(java.util.logging.Level.WARNING,
                                    "Committed doll-potential announcement failed", noticeFailure);
                        }
                    else
                        try { SC_PacketBox.broadcastGreen("아덴 월드 어느 용사의 마법인형이 \\f3[잠재력: 영웅]\\f2으로 강화되었습니다."); }
                        catch (RuntimeException noticeFailure) {
                            LOG.log(java.util.logging.Level.WARNING,
                                    "Committed doll-potential announcement failed", noticeFailure);
                        }
                } else if (announceLegend) {
                    if (Config.전설등급멘트)
                        try { SC_PacketBox.broadcastGreen(pc.getName() + "님의 마법인형이 \\f4[잠재력: 전설]\\f2로 강화되었습니다."); }
                        catch (RuntimeException noticeFailure) {
                            LOG.log(java.util.logging.Level.WARNING,
                                    "Committed doll-potential announcement failed", noticeFailure);
                        }
                    else
                        try { SC_PacketBox.broadcastGreen("아덴 월드 어느 용사의 마법인형이 \\f4[잠재력: 전설]\\f2로 강화되었습니다."); }
                        catch (RuntimeException noticeFailure) {
                            LOG.log(java.util.logging.Level.WARNING,
                                    "Committed doll-potential announcement failed", noticeFailure);
                        }
                }
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 잠재력 강화를 완료할 수 없습니다.");
        }
    }

    static int rollLegendaryPotential(int dice) {
        int threshold = Config.인형디케이;
        if (dice <= threshold) return 131;
        threshold += Config.인형pvp감소;
        if (dice <= threshold) return 132;
        threshold += Config.인형올스탯;
        if (dice <= threshold) return 133;
        threshold += Config.인형피;
        if (dice <= threshold) return 134;
        threshold += Config.인형엠피;
        if (dice <= threshold) return 135;
        threshold += Config.인형소울;
        if (dice <= threshold) return 136;
        threshold += Config.인형이뮨;
        if (dice <= threshold) return 137;
        threshold += Config.인형저지먼트;
        return dice <= threshold ? 138 : 139;
    }
}
