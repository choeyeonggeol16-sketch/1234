package l1j.server.server.model.item.Card;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 변신 카드 컬렉션 능력치 부여 및 정보 출력 핸들러
 */
public class L1CardCollection {
    private static final Logger LOG = Logger.getLogger(L1CardCollection.class.getName());

    // --- 컬렉션 완성 기준 수량 ---
    private static final int COUNT_MYTH = 11;
    private static final int COUNT_LEGEND = 23;
    private static final int COUNT_HERO = 36;

    /**
     * 캐릭터 접속 시 또는 데이터 갱신 시 전체 컬렉션 보너스 동기화
     */
    public static void Join(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();

        // 1유일 컬렉션 (기르타스)
        if (acc.getInfo_kir_poly() != 0 && pc.getBcol_y() == 0) {
            applyUniqueBonus(pc);
        }

        // 신화 컬렉션
        if (acc.getInfo_col_m() >= COUNT_MYTH && pc.getBcol_m() == 0) {
            applyMythBonus(pc);
        }

        //  전설 컬렉션
        if (acc.getInfo_col_l() >= COUNT_LEGEND && pc.getBcol_l() == 0) {
            applyLegendBonus(pc);
        }

        // 영웅 컬렉션
        if (acc.getInfo_col_h() >= COUNT_HERO && pc.getBcol_h() == 0) {
            applyHeroBonus(pc);
        }

        refreshPackets(pc);
    }

    // --- 각 등급별 개별 트리거 (카드 등록 시 호출) ---
    public static void solejoin(L1PcInstance pc) {
        if (pc.getAccount().getInfo_kir_poly() != 0 && pc.getBcol_y() == 0) {
            applyUniqueBonus(pc);
            sendSuccessMsg(pc);
        }
    }

    public static void mythjoin(L1PcInstance pc) {
        if (pc.getAccount().getInfo_col_m() >= COUNT_MYTH && pc.getBcol_m() == 0) {
            applyMythBonus(pc);
            sendSuccessMsg(pc);
        }
    }

    public static void legendjoin(L1PcInstance pc) {
        if (pc.getAccount().getInfo_col_l() >= COUNT_LEGEND && pc.getBcol_l() == 0) {
            applyLegendBonus(pc);
            sendSuccessMsg(pc);
        }
    }

    public static void herojoin(L1PcInstance pc) {
        if (pc.getAccount().getInfo_col_h() >= COUNT_HERO && pc.getBcol_h() == 0) {
            applyHeroBonus(pc);
            sendSuccessMsg(pc);
        }
    }

    // --- 실제 능력치 부여 로직 ---
    private static void applyUniqueBonus(L1PcInstance pc) {
        pc.setBcol_y(1);
        pc.addDmgup(10); pc.addBowDmgup(10); pc.AddStatSp(10);
        pc.addDamageReduction(15);
        pc.addHitup(5); pc.addBowHitup(5); pc.addMagicHit(5);
        pc.addMaxHp(200); pc.addMaxMp(100);
        pc.AddMDefend(10);
        pc.add_melee_critical_rate(10); pc.add_magic_critical_rate(10); pc.add_missile_critical_rate(10);
    }

    private static void applyMythBonus(L1PcInstance pc) {
        pc.setBcol_m(1);
        pc.addDmgup(8); pc.addBowDmgup(8); pc.AddStatSp(8);
        pc.addDamageReduction(10);
        pc.addHitup(3); pc.addBowHitup(3); pc.addMagicHit(3);
        pc.addMaxHp(200); pc.addMaxMp(100);
        pc.AddMDefend(8);
        pc.add_melee_critical_rate(5); pc.add_magic_critical_rate(5); pc.add_missile_critical_rate(5);
    }

    private static void applyLegendBonus(L1PcInstance pc) {
        pc.setBcol_l(1);
        pc.addDmgup(5); pc.addBowDmgup(5); pc.AddStatSp(5);
        pc.addDamageReduction(10);
        pc.addHitup(2); pc.addBowHitup(2); pc.addMagicHit(2);
        pc.addMaxHp(100); pc.addMaxMp(50);
        pc.AddMDefend(5);
        pc.add_melee_critical_rate(5); pc.add_magic_critical_rate(5); pc.add_missile_critical_rate(5);
    }

    private static void applyHeroBonus(L1PcInstance pc) {
        pc.setBcol_h(1);
        pc.addDmgup(3); pc.addBowDmgup(3); pc.AddStatSp(3);
        pc.addDamageReduction(5);
        pc.addHitup(2); pc.addBowHitup(2); pc.addMagicHit(2);
        pc.AddMDefend(3);
        pc.add_melee_critical_rate(5); pc.add_magic_critical_rate(5); pc.add_missile_critical_rate(5);
    }

    // --- 정보 창 출력 ---
    public static void Info(L1PcInstance pc) { sendCollectionInfo(pc, "2008bonus3"); }
    public static void Info2(L1PcInstance pc) { sendCollectionInfo(pc, "2008bonus2"); }

    private static void sendCollectionInfo(L1PcInstance pc, String html) {
        if (pc == null || pc.getAccount() == null) return;
        Account acc = pc.getAccount();

        // 합산 수치 계산
        int totalDmg = 0, totalHit = 0, totalHp = 0, totalMp = 0, totalCrit = 0, totalMCrit = 0, totalReduc = 0;

        if (acc.getInfo_kir_poly() != 0) {
            totalDmg += 10; totalHit += 5; totalHp += 200; totalMp += 100; totalCrit += 10; totalMCrit += 10; totalReduc += 15;
        }
        if (acc.getInfo_col_m() >= COUNT_MYTH) {
            totalDmg += 8; totalHit += 3; totalHp += 200; totalMp += 100; totalCrit += 5; totalMCrit += 3; totalReduc += 10;
        }
        if (acc.getInfo_col_l() >= COUNT_LEGEND) {
            totalDmg += 5; totalHit += 2; totalHp += 100; totalMp += 50; totalCrit += 5; totalMCrit += 3; totalReduc += 10;
        }
        if (acc.getInfo_col_h() >= COUNT_HERO) {
            totalDmg += 3; totalHit += 2; totalCrit += 5; totalMCrit += 3; totalReduc += 5;
        }

        List<String> args = new ArrayList<>(15);
        args.add(totalDmg != 0 ? "[+" + totalDmg + "]" : "[0]");
        args.add(totalHit != 0 ? "[+" + totalHit + "]" : "[0]");
        args.add(totalHp != 0 ? "[+" + totalHp + "]" : "[0]");
        args.add(totalMp != 0 ? "[+" + totalMp + "]" : "[0]");
        
        args.add("[" + acc.getInfo_col_h() + "/" + COUNT_HERO + "]");
        args.add("[" + acc.getInfo_col_l() + "/" + COUNT_LEGEND + "]");
        args.add("[" + acc.getInfo_col_m() + "/" + COUNT_MYTH + "]");
        args.add("[" + (acc.getInfo_kir_poly() != 0 ? 1 : 0) + "/1]");
        
        args.add(acc.getInfo_col_h() >= COUNT_HERO ? "209" : "208");
        args.add(acc.getInfo_col_l() >= COUNT_LEGEND ? "209" : "208");
        args.add(acc.getInfo_col_m() >= COUNT_MYTH ? "209" : "208");
        args.add(acc.getInfo_kir_poly() != 0 ? "209" : "208");
        
        args.add(totalCrit != 0 ? "[+" + totalCrit + "]" : "[0]");
        args.add(totalMCrit != 0 ? "[+" + totalMCrit + "]" : "[0]");
        args.add(totalReduc != 0 ? "[+" + totalReduc + "]" : "[0]");

        SC_NPCTalkReturn.sendList(pc, pc.getId(), html, args);
    }

    public static void out(L1PcInstance pc) {
        pc.setBcol_y(0); pc.setBcol_m(0); pc.setBcol_l(0); pc.setBcol_h(0);
    }

    private static void refreshPackets(L1PcInstance pc) {
        SC_SPMR.send(pc);
        SC_OwnCharStatus.send(pc);
    }

    private static void sendSuccessMsg(L1PcInstance pc) {
        // All collection bonuses have already been applied. Packet output is
        // optional and must never quarantine a committed card registration.
        try {
            refreshPackets(pc);
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "컬렉션 능력치가 상승하였습니다.");
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "컬렉션 정보를 통해 확인하세요.");
        } catch (RuntimeException packetFailure) {
            LOG.log(Level.WARNING, "Collection bonus applied but notification failed: " + pc.getId(),
                    packetFailure);
        }
    }
}