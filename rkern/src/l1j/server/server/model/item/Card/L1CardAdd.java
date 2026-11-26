package l1j.server.server.model.item.Card;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/**
 * 카드 컬렉션과 수호성 등록: 아이템 차감과 계정 필드 변경을 하나의
 * CacheD/InnoDB receipt로 확정하고, 메모리/패킷은 커밋 후 반영한다.
 * ID 조회는 primitive switch를 사용하여 HashMap<Integer, ...> 박싱과 노드 비용을 없앤다.
 */
public final class L1CardAdd {
    private static final Logger LOG = Logger.getLogger(L1CardAdd.class.getName());

    private L1CardAdd() {}

    // Category check and SQL column whitelist belong together: an item from a
    // different collection group is never accepted even when its ID is valid.
    private static String collectionColumn(int itemId, int type) {
        return switch (type) {
            case 1 -> switch (itemId) {
                case 875640306 -> "info_ds_poly";
                case 875640307 -> "info_dru_poly";
                case 875640308 -> "info_ga_poly";
                case 875640309 -> "info_ger_poly";
                case 875640310 -> "info_dev_poly";
                case 875640311 -> "info_pen_poly";
                case 875640312 -> "info_atun_poly";
                case 875640313 -> "info_moon_poly";
                case 875640314 -> "info_mar_poly";
                default -> null;
            };
            case 2 -> switch (itemId) {
                case 875640365 -> "legend11";
                case 875640366 -> "legend01";
                case 875640367 -> "legend12";
                case 875640368 -> "legend02";
                case 875640369 -> "legend03";
                case 875640370 -> "legend13";
                case 875640371 -> "legend04";
                case 875640372 -> "legend05";
                case 875640373 -> "legend06";
                case 875640374 -> "legend07";
                case 875640375 -> "legend08";
                case 875640376 -> "legend09";
                case 875640377 -> "legend14";
                case 875640378 -> "legend10";
                default -> null;
            };
            case 3 -> switch (itemId) {
                case 875640315 -> "info_heaven_poly";
                case 875640316 -> "info_val_poly";
                case 875640317 -> "info_larry_poly";
                case 875640318 -> "info_gunter_poly";
                case 875640319 -> "info_depor_poly";
                case 875640320 -> "info_deathred_poly";
                case 875640321 -> "info_ecil_poly";
                case 875640322 -> "info_assm_poly";
                case 875640323 -> "info_kerep_poly";
                case 875640324 -> "info_jowo_poly";
                case 875640325 -> "info_unik_poly";
                case 875640326 -> "info_unim_poly";
                case 875640327 -> "info_unir_poly";
                case 875640328 -> "info_yozil_poly";
                case 875640329 -> "info_owen_poly";
                case 875640330 -> "info_helv_poly";
                default -> null;
            };
            case 4 -> switch (itemId) {
                case 875640345 -> "hero05";
                case 875640346 -> "hero06";
                case 875640347 -> "hero03";
                case 875640348 -> "hero04";
                case 875640349 -> "hero13";
                case 875640350 -> "hero14";
                case 875640351 -> "hero07";
                case 875640352 -> "hero08";
                case 875640353 -> "hero09";
                case 875640354 -> "hero17";
                case 875640355 -> "hero15";
                case 875640356 -> "hero18";
                case 875640357 -> "hero16";
                case 875640358 -> "hero01";
                case 875640359 -> "hero19";
                case 875640360 -> "hero02";
                case 875640361 -> "hero10";
                case 875640362 -> "hero11";
                case 875640363 -> "hero20";
                case 875640364 -> "hero12";
                default -> null;
            };
            case 5 -> switch (itemId) {
                case 875640296 -> "info_class_poly";
                case 875640297 -> "info_ki_poly";
                case 875640298 -> "info_el_poly";
                case 875640299 -> "info_wi_poly";
                case 875640300 -> "info_da_poly";
                case 875640301 -> "info_wa_poly";
                case 875640302 -> "info_il_poly";
                case 875640303 -> "info_drag_poly";
                case 875640304 -> "info_dsn_poly";
                case 875640305 -> "info_bapo_poly";
                case 875640380 -> "myth01";
                default -> null;
            };
            default -> null;
        };
    }

    private static int cardCount(Account acc, int itemId) {
        return switch (itemId) {
            case 875640306 -> acc.getInfo_ds_poly();
            case 875640307 -> acc.getInfo_dru_poly();
            case 875640308 -> acc.getInfo_ga_poly();
            case 875640309 -> acc.getInfo_ger_poly();
            case 875640310 -> acc.getInfo_dev_poly();
            case 875640311 -> acc.getInfo_pen_poly();
            case 875640312 -> acc.getInfo_atun_poly();
            case 875640313 -> acc.getInfo_moon_poly();
            case 875640314 -> acc.getInfo_mar_poly();
            case 875640365 -> acc.getlegend11();
            case 875640366 -> acc.getlegend01();
            case 875640367 -> acc.getlegend12();
            case 875640368 -> acc.getlegend02();
            case 875640369 -> acc.getlegend03();
            case 875640370 -> acc.getlegend13();
            case 875640371 -> acc.getlegend04();
            case 875640372 -> acc.getlegend05();
            case 875640373 -> acc.getlegend06();
            case 875640374 -> acc.getlegend07();
            case 875640375 -> acc.getlegend08();
            case 875640376 -> acc.getlegend09();
            case 875640377 -> acc.getlegend14();
            case 875640378 -> acc.getlegend10();
            case 875640315 -> acc.getInfo_heaven_poly();
            case 875640316 -> acc.getInfo_val_poly();
            case 875640317 -> acc.getInfo_larry_poly();
            case 875640318 -> acc.getInfo_gunter_poly();
            case 875640319 -> acc.getInfo_depor_poly();
            case 875640320 -> acc.getInfo_deathred_poly();
            case 875640321 -> acc.getInfo_ecil_poly();
            case 875640322 -> acc.getInfo_assm_poly();
            case 875640323 -> acc.getInfo_kerep_poly();
            case 875640324 -> acc.getInfo_jowo_poly();
            case 875640325 -> acc.getInfo_unik_poly();
            case 875640326 -> acc.getInfo_unim_poly();
            case 875640327 -> acc.getInfo_unir_poly();
            case 875640328 -> acc.getInfo_yozil_poly();
            case 875640329 -> acc.getInfo_owen_poly();
            case 875640330 -> acc.getInfo_helv_poly();
            case 875640345 -> acc.gethero05();
            case 875640346 -> acc.gethero06();
            case 875640347 -> acc.gethero03();
            case 875640348 -> acc.gethero04();
            case 875640349 -> acc.gethero13();
            case 875640350 -> acc.gethero14();
            case 875640351 -> acc.gethero07();
            case 875640352 -> acc.gethero08();
            case 875640353 -> acc.gethero09();
            case 875640354 -> acc.gethero17();
            case 875640355 -> acc.gethero15();
            case 875640356 -> acc.gethero18();
            case 875640357 -> acc.gethero16();
            case 875640358 -> acc.gethero01();
            case 875640359 -> acc.gethero19();
            case 875640360 -> acc.gethero02();
            case 875640361 -> acc.gethero10();
            case 875640362 -> acc.gethero11();
            case 875640363 -> acc.gethero20();
            case 875640364 -> acc.gethero12();
            case 875640296 -> acc.getInfo_class_poly();
            case 875640297 -> acc.getInfo_ki_poly();
            case 875640298 -> acc.getInfo_el_poly();
            case 875640299 -> acc.getInfo_wi_poly();
            case 875640300 -> acc.getInfo_da_poly();
            case 875640301 -> acc.getInfo_wa_poly();
            case 875640302 -> acc.getInfo_il_poly();
            case 875640303 -> acc.getInfo_drag_poly();
            case 875640304 -> acc.getInfo_dsn_poly();
            case 875640305 -> acc.getInfo_bapo_poly();
            case 875640380 -> acc.getmyth01();
            default -> -1;
        };
    }

    private static void incrementCard(Account acc, int itemId) {
        switch (itemId) {
            case 875640306 -> acc.setInfo_ds_poly(acc.getInfo_ds_poly() + 1);
            case 875640307 -> acc.setInfo_dru_poly(acc.getInfo_dru_poly() + 1);
            case 875640308 -> acc.setInfo_ga_poly(acc.getInfo_ga_poly() + 1);
            case 875640309 -> acc.setInfo_ger_poly(acc.getInfo_ger_poly() + 1);
            case 875640310 -> acc.setInfo_dev_poly(acc.getInfo_dev_poly() + 1);
            case 875640311 -> acc.setInfo_pen_poly(acc.getInfo_pen_poly() + 1);
            case 875640312 -> acc.setInfo_atun_poly(acc.getInfo_atun_poly() + 1);
            case 875640313 -> acc.setInfo_moon_poly(acc.getInfo_moon_poly() + 1);
            case 875640314 -> acc.setInfo_mar_poly(acc.getInfo_mar_poly() + 1);
            case 875640365 -> acc.setlegend11(acc.getlegend11() + 1);
            case 875640366 -> acc.setlegend01(acc.getlegend01() + 1);
            case 875640367 -> acc.setlegend12(acc.getlegend12() + 1);
            case 875640368 -> acc.setlegend02(acc.getlegend02() + 1);
            case 875640369 -> acc.setlegend03(acc.getlegend03() + 1);
            case 875640370 -> acc.setlegend13(acc.getlegend13() + 1);
            case 875640371 -> acc.setlegend04(acc.getlegend04() + 1);
            case 875640372 -> acc.setlegend05(acc.getlegend05() + 1);
            case 875640373 -> acc.setlegend06(acc.getlegend06() + 1);
            case 875640374 -> acc.setlegend07(acc.getlegend07() + 1);
            case 875640375 -> acc.setlegend08(acc.getlegend08() + 1);
            case 875640376 -> acc.setlegend09(acc.getlegend09() + 1);
            case 875640377 -> acc.setlegend14(acc.getlegend14() + 1);
            case 875640378 -> acc.setlegend10(acc.getlegend10() + 1);
            case 875640315 -> acc.setInfo_heaven_poly(acc.getInfo_heaven_poly() + 1);
            case 875640316 -> acc.setInfo_val_poly(acc.getInfo_val_poly() + 1);
            case 875640317 -> acc.setInfo_larry_poly(acc.getInfo_larry_poly() + 1);
            case 875640318 -> acc.setInfo_gunter_poly(acc.getInfo_gunter_poly() + 1);
            case 875640319 -> acc.setInfo_depor_poly(acc.getInfo_depor_poly() + 1);
            case 875640320 -> acc.setInfo_deathred_poly(acc.getInfo_deathred_poly() + 1);
            case 875640321 -> acc.setInfo_ecil_poly(acc.getInfo_ecil_poly() + 1);
            case 875640322 -> acc.setInfo_assm_poly(acc.getInfo_assm_poly() + 1);
            case 875640323 -> acc.setInfo_kerep_poly(acc.getInfo_kerep_poly() + 1);
            case 875640324 -> acc.setInfo_jowo_poly(acc.getInfo_jowo_poly() + 1);
            case 875640325 -> acc.setInfo_unik_poly(acc.getInfo_unik_poly() + 1);
            case 875640326 -> acc.setInfo_unim_poly(acc.getInfo_unim_poly() + 1);
            case 875640327 -> acc.setInfo_unir_poly(acc.getInfo_unir_poly() + 1);
            case 875640328 -> acc.setInfo_yozil_poly(acc.getInfo_yozil_poly() + 1);
            case 875640329 -> acc.setInfo_owen_poly(acc.getInfo_owen_poly() + 1);
            case 875640330 -> acc.setInfo_helv_poly(acc.getInfo_helv_poly() + 1);
            case 875640345 -> acc.sethero05(acc.gethero05() + 1);
            case 875640346 -> acc.sethero06(acc.gethero06() + 1);
            case 875640347 -> acc.sethero03(acc.gethero03() + 1);
            case 875640348 -> acc.sethero04(acc.gethero04() + 1);
            case 875640349 -> acc.sethero13(acc.gethero13() + 1);
            case 875640350 -> acc.sethero14(acc.gethero14() + 1);
            case 875640351 -> acc.sethero07(acc.gethero07() + 1);
            case 875640352 -> acc.sethero08(acc.gethero08() + 1);
            case 875640353 -> acc.sethero09(acc.gethero09() + 1);
            case 875640354 -> acc.sethero17(acc.gethero17() + 1);
            case 875640355 -> acc.sethero15(acc.gethero15() + 1);
            case 875640356 -> acc.sethero18(acc.gethero18() + 1);
            case 875640357 -> acc.sethero16(acc.gethero16() + 1);
            case 875640358 -> acc.sethero01(acc.gethero01() + 1);
            case 875640359 -> acc.sethero19(acc.gethero19() + 1);
            case 875640360 -> acc.sethero02(acc.gethero02() + 1);
            case 875640361 -> acc.sethero10(acc.gethero10() + 1);
            case 875640362 -> acc.sethero11(acc.gethero11() + 1);
            case 875640363 -> acc.sethero20(acc.gethero20() + 1);
            case 875640364 -> acc.sethero12(acc.gethero12() + 1);
            case 875640296 -> acc.setInfo_class_poly(acc.getInfo_class_poly() + 1);
            case 875640297 -> acc.setInfo_ki_poly(acc.getInfo_ki_poly() + 1);
            case 875640298 -> acc.setInfo_el_poly(acc.getInfo_el_poly() + 1);
            case 875640299 -> acc.setInfo_wi_poly(acc.getInfo_wi_poly() + 1);
            case 875640300 -> acc.setInfo_da_poly(acc.getInfo_da_poly() + 1);
            case 875640301 -> acc.setInfo_wa_poly(acc.getInfo_wa_poly() + 1);
            case 875640302 -> acc.setInfo_il_poly(acc.getInfo_il_poly() + 1);
            case 875640303 -> acc.setInfo_drag_poly(acc.getInfo_drag_poly() + 1);
            case 875640304 -> acc.setInfo_dsn_poly(acc.getInfo_dsn_poly() + 1);
            case 875640305 -> acc.setInfo_bapo_poly(acc.getInfo_bapo_poly() + 1);
            case 875640380 -> acc.setmyth01(acc.getmyth01() + 1);
            default -> throw new IllegalArgumentException("Unknown collection card ID: " + itemId);
        }
    }

    private static int collectionScore(Account acc, int type) {
        if (type <= 2) return acc.getInfo_col_l();
        if (type <= 4) return acc.getInfo_col_h();
        return acc.getInfo_col_m();
    }

    private static void incrementScore(Account acc, int type) {
        if (type <= 2) acc.setInfo_col_l(acc.getInfo_col_l() + 1);
        else if (type <= 4) acc.setInfo_col_h(acc.getInfo_col_h() + 1);
        else acc.setInfo_col_m(acc.getInfo_col_m() + 1);
    }

    private static String collectionScoreColumn(int type) {
        return type <= 2 ? "info_col_l" : type <= 4 ? "info_col_h" : "info_col_m";
    }

    private static void showCollectionBonus(L1PcInstance pc, int type) {
        if (type <= 2) L1CardCollection.legendjoin(pc);
        else if (type <= 4) L1CardCollection.herojoin(pc);
        else L1CardCollection.mythjoin(pc);
    }

    private static void processCollection(L1PcInstance pc, int type) {
        if (pc == null) return;
        final L1ItemInstance item = pc.getCollecItem();
        if (item == null) return;
        final Account acc = pc.getAccount();
        if (acc == null) { pc.setCollecItem(null); return; }
        final int itemId = item.getItemId();
        final String column = collectionColumn(itemId, type);
        if (column == null) { pc.setCollecItem(null); return; }
        final int before = cardCount(acc, itemId);
        final int scoreBefore = collectionScore(acc, type);
        if (before < 0 || before == Integer.MAX_VALUE || scoreBefore < 0 || scoreBefore == Integer.MAX_VALUE) {
            pc.setCollecItem(null);
            SC_SystemMessage.send(pc, "카드 컬렉션 수량을 확인할 수 없습니다.");
            return;
        }
        final boolean first = before == 0;
        final String scoreColumn = collectionScoreColumn(type);
        final String accountName = acc.getName();
        final String itemName = item.getItem().getNameId();
        final String sql = "UPDATE accounts SET " + column + "=" + column + "+1"
                + (first ? "," + scoreColumn + "=" + scoreColumn + "+1" : "")
                + " WHERE login=? AND " + column + "=?"
                + (first ? " AND " + scoreColumn + "=?" : "");
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey(accountName))) {
            // Recheck after the inventory gate: the selected item and account
            // balance may have changed since this menu action captured them.
            if (pc.getAccount() != acc || pc.getCollecItem() != item
                    || cardCount(acc, itemId) != before
                    || (first && collectionScore(acc, type) != scoreBefore)) {
                SC_SystemMessage.send(pc, "카드 선택 또는 수량이 변경되었습니다. 다시 확인해 주세요.");
                return;
            }
            if (!tx.debit(item, 1)) {
                SC_SystemMessage.send(pc, "등록할 카드를 보유하고 있지 않습니다.");
                return;
            }
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, accountName);
                    ps.setInt(2, before);
                    if (first) ps.setInt(3, scoreBefore);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Card collection preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "카드 등록 거래를 저장하지 못했습니다. 보유 카드를 다시 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (pc.getAccount() != acc || cardCount(acc, itemId) != before
                        || (first && collectionScore(acc, type) != scoreBefore))
                    throw new IllegalStateException("Card collection memory/session preimage changed");
                incrementCard(acc, itemId);
                if (first) incrementScore(acc, type);
                // Applying a newly earned stat bonus is part of game state;
                // never let a failure sending the success text skip it.
                showCollectionBonus(pc, type);
                try {
                    if (first) {
                        SC_SystemMessage.send(pc, itemName + "(이)가 변신컬렉션에 등록되었습니다.");
                    } else {
                        SC_SystemMessage.send(pc, itemName + "(이)가 변신컬렉션에 등록되었습니다. (현재 "
                                + (before + 1) + "장)");
                    }
                } catch (RuntimeException packetFailure) {
                    LOG.log(Level.WARNING, "Card collection committed but notification failed: " + pc.getId(),
                            packetFailure);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Card collection receipt prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "카드 등록을 처리하지 못했습니다.");
        } finally {
            // Preserve a newer UI selection; retire only our captured token.
            if (pc.getCollecItem() == item) pc.setCollecItem(null);
        }
    }

    public static void legend1(L1PcInstance pc) { processCollection(pc, 1); }
    public static void legend2(L1PcInstance pc) { processCollection(pc, 2); }
    public static void hero1(L1PcInstance pc)   { processCollection(pc, 3); }
    public static void hero2(L1PcInstance pc)   { processCollection(pc, 4); }
    public static void myth(L1PcInstance pc)    { processCollection(pc, 5); }

    // The tree card group has 14 distinct fields and four existing UI categories.
    private static String treeColumn(int itemId) {
        return switch (itemId) {
            case 875640331 -> "tree_iceq";
            case 875640332 -> "tree_fireq";
            case 875640333 -> "tree_final";
            case 875640334 -> "tree_ices";
            case 875640335 -> "tree_rain";
            case 875640336 -> "tree_cri";
            case 875640337 -> "tree_crim1";
            case 875640338 -> "tree_crim2";
            case 875640339 -> "tree_ecil1";
            case 875640340 -> "tree_ecil2";
            case 875640341 -> "tree_ecil3";
            case 875640446 -> "tree_atun1";
            case 875640447 -> "tree_atun2";
            case 875640448 -> "tree_atun3";
            default -> null;
        };
    }

    private static int treeCount(Account acc, int itemId) {
        return switch (itemId) {
            case 875640331 -> acc.getTree_iceq();
            case 875640332 -> acc.getTree_fireq();
            case 875640333 -> acc.getTree_final();
            case 875640334 -> acc.getTree_ices();
            case 875640335 -> acc.getTree_rain();
            case 875640336 -> acc.getTree_cri();
            case 875640337 -> acc.getTree_crim1();
            case 875640338 -> acc.getTree_crim2();
            case 875640339 -> acc.getTree_ecil1();
            case 875640340 -> acc.getTree_ecil2();
            case 875640341 -> acc.getTree_ecil3();
            case 875640446 -> acc.getTree_atun1();
            case 875640447 -> acc.getTree_atun2();
            case 875640448 -> acc.getTree_atun3();
            default -> -1;
        };
    }

    private static void registerTree(Account acc, int itemId) {
        switch (itemId) {
            case 875640331 -> acc.setTree_iceq(1);
            case 875640332 -> acc.setTree_fireq(1);
            case 875640333 -> acc.setTree_final(1);
            case 875640334 -> acc.setTree_ices(1);
            case 875640335 -> acc.setTree_rain(1);
            case 875640336 -> acc.setTree_cri(1);
            case 875640337 -> acc.setTree_crim1(1);
            case 875640338 -> acc.setTree_crim2(1);
            case 875640339 -> acc.setTree_ecil1(1);
            case 875640340 -> acc.setTree_ecil2(1);
            case 875640341 -> acc.setTree_ecil3(1);
            case 875640446 -> acc.setTree_atun1(1);
            case 875640447 -> acc.setTree_atun2(1);
            case 875640448 -> acc.setTree_atun3(1);
            default -> throw new IllegalArgumentException("Unknown skill tree item: " + itemId);
        }
    }

    private static int treeCategory(int itemId) {
        return switch (itemId) {
            case 875640331, 875640332, 875640333, 875640334, 875640335 -> 1;
            case 875640336, 875640337, 875640338 -> 2;
            case 875640339, 875640340, 875640341 -> 3;
            case 875640446, 875640447, 875640448 -> 4;
            default -> 0;
        };
    }

    public static void SkillTree(L1PcInstance pc) {
        if (pc == null) return;
        final L1ItemInstance item = pc.getCollecItem();
        if (item == null) return;
        final Account acc = pc.getAccount();
        if (acc == null) { pc.setCollecItem(null); return; }
        final int itemId = item.getItemId();
        final String column = treeColumn(itemId);
        if (column == null || treeCount(acc, itemId) != 0) {
            SC_SystemMessage.send(pc, "이미 추가된 수호성 스킬입니다.");
            pc.setCollecItem(null);
            return;
        }
        final int category = treeCategory(itemId);
        final String accountName = acc.getName();
        final String itemName = item.getItem().getNameId();
        final String sql = "UPDATE accounts SET " + column + "=1 WHERE login=? AND " + column + "=0";
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey(accountName))) {
            // An earlier selection must not consume the newly selected item.
            if (pc.getAccount() != acc || pc.getCollecItem() != item
                    || treeCount(acc, itemId) != 0) {
                SC_SystemMessage.send(pc, "수호성 선택 또는 등록 상태가 변경되었습니다. 다시 확인해 주세요.");
                return;
            }
            if (!tx.debit(item, 1)) {
                SC_SystemMessage.send(pc, "등록할 수호성 카드가 없습니다.");
                return;
            }
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, accountName);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Guardian tree preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "수호성 스킬 등록 거래를 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (pc.getAccount() != acc || treeCount(acc, itemId) != 0)
                    throw new IllegalStateException("Guardian tree memory/session preimage changed");
                registerTree(acc, itemId);
                // The account skill bit is already committed and mirrored.
                // UI rendering and chat are optional, independent effects.
                try {
                    if (category == 1) L1SkillTree.Info(pc);
                    else if (category == 2) L1SkillTree.Info2(pc);
                    else if (category == 3) L1SkillTree.Info3(pc);
                    else L1SkillTree.Info5(pc);
                } catch (RuntimeException packetFailure) {
                    LOG.log(Level.WARNING, "Guardian tree committed but UI failed: " + pc.getId(),
                            packetFailure);
                }
                try {
                    SC_SystemMessage.send(pc, itemName + "(이)가 수호성에 등록되었습니다.");
                } catch (RuntimeException packetFailure) {
                    LOG.log(Level.WARNING, "Guardian tree committed but notification failed: " + pc.getId(),
                            packetFailure);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Guardian tree receipt prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "수호성 등록을 처리하지 못했습니다.");
        } finally {
            if (pc.getCollecItem() == item) pc.setCollecItem(null);
        }
    }
}
