package l1j.server.server.model.item.Exp;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.MJCompanion.Basic.HuntingGround.MJCompanionHuntingGround;
import l1j.server.server.GameServerSetting;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.Packet.SC_REST_EXP_INFO_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.MJCaptchaSystem.MJCaptcha;
import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.MJTemplate.MJRnd;

/**
 * CalcExp.AddExp()에서 "경험치 배수/아인/에메랄드/콤보/캡차/레벨캡" 계산/사이드이펙트 적용
 */
public final class ExpGainCalculator {

    private static final Logger _log = Logger.getLogger(ExpGainCalculator.class.getName());

    private ExpGainCalculator() {}

    public static int calcAddExpAndApplySideEffects(L1PcInstance pc, int exp) {
        if (pc == null) return 0;
        if (Config.STANDBY_SERVER) return 0;
        if (pc.isGhost()) return 0;
        if (exp <= 0) return 0;
        return calcImpl(pc, exp);
    }

    private static int calcImpl(L1PcInstance pc, int exp) {
        try {
            final int level = pc.getLevel();
            final int mapId = pc.getMapId();

            // 사냥터 레벨 제한 (폭렙 금지구역 차단)
            if (level >= 75 && (mapId >= 1911 && mapId <= 1912)) {
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해당 사냥터에서 레벨 74이상은 경험치가 오르지 않습니다.");
                return 0;
            }
            if (level > Config.New_Cha1 && (mapId >= 1 && mapId <= 2)) {
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해당 사냥터에서 레벨 [" + Config.New_Cha1 + "] 이상은 경험치가 오르지 않습니다.");
                return 0;
            }

            // 동반자 사냥터 share
            exp = applyCompanionShare(pc, exp);
            if (exp <= 0) return 0;

            final boolean pcRoom = pc.PC방_버프;
            final Account account = pc.getAccount();
            final boolean hasAccount = account != null;
            final int ainBefore = hasAccount ? account.getBlessOfAin() : 0;
            final double exppenalty = ExpTable.getPenaltyRate(level);

            // ---- 보너스들 ----
            double foodBonus = 1.0;
            double expposion = 1.0;
            double levelupBonus = 1.0;
            double clanBonus = 1.0; 
            double comboBonus = 1.0;

            double comab = 1.0;

            double dragon_hunter_bless = 1.0; 
            double dragon_SET_bless = 1.0;
            double HUNTER_BLESS = 1.0;
            double HUNTER_BLESS3 = 1.0;
            double HUNTER_BLESS4 = 1.0;

            double BloodBonus = 1.0;
            double exp_posion = 1.0;

            // 캐시 가져오기: 인형/장비/문장 
            ExpBonusState st = pc.expBonusState();
            double dollBonus = st.getDollMul();
            double Arka      = st.getArkaMul();
            double insignia  = st.getInsigniaMul();

            // =========================================================
            // 성혈버프 보너스 처리
            // =========================================================


            L1Clan clan = null;
            if (pc.getClanid() != 0) {
                clan = pc.getClan();
                if (clan == null) clan = L1World.getInstance().getClan(pc.getClanid());
            }
            if (clan != null && clan.getCastleId() != 0) {
                BloodBonus += Config.성혈경험치;
            }

            // =========================================================
            // Skill effects
            // =========================================================
            final boolean sk_dragon_set   = pc.hasSkillEffect(L1SkillId.DRAGON_SET);
            final boolean sk_hunter1      = pc.hasSkillEffect(L1SkillId.HUNTER_BLESS);
            final boolean sk_hunter3      = pc.hasSkillEffect(L1SkillId.HUNTER_BLESS3);
            final boolean sk_togo         = pc.hasSkillEffect(L1SkillId.TOGO_BUFF);
            final boolean sk_comaB        = pc.hasSkillEffect(L1SkillId.COMA_B);
            final boolean sk_exp_potion   = pc.hasSkillEffect(L1SkillId.EXP_POTION);
            final boolean sk_tam_fruit5   = pc.hasSkillEffect(L1SkillId.Tam_Fruit5);
            final boolean sk_exp_buff     = pc.hasSkillEffect(L1SkillId.EXP_BUFF);
            final boolean sk_levelup      = pc.hasSkillEffect(L1SkillId.레벨업보너스);
            final boolean sk_combo        = pc.hasSkillEffect(80006);
            
            // 커스텀 스킬 이펙트 2종 
            final boolean sk_exp10        = pc.hasSkillEffect(L1SkillId.경험치10);
            final boolean sk_exp20        = pc.hasSkillEffect(L1SkillId.경험치보너스20);

            // (요리류)
            final boolean cook_1_7  = pc.hasSkillEffect(L1SkillId.COOKING_1_7_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_7_S);
            final boolean cook_1_15 = pc.hasSkillEffect(L1SkillId.COOKING_1_15_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_15_S) || pc.hasSkillEffect(L1SkillId.BUFF_특별한성장축복);
            final boolean cook_1_23 = pc.hasSkillEffect(L1SkillId.COOKING_1_23_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_23_S);
            final boolean cook_soup = pc.hasSkillEffect(L1SkillId.수련자의닭고기스프) || pc.hasSkillEffect(L1SkillId.COOK_GROW) || pc.hasSkillEffect(L1SkillId.COOK_GROW_1);
            final boolean cook_power= pc.hasSkillEffect(L1SkillId.천하장사버프);
            final boolean cook_miso = pc.hasSkillEffect(L1SkillId.miso1);
            final boolean cook_metis= pc.hasSkillEffect(L1SkillId.메티스정성스프);

            // ---- 적용 ----
            if (sk_dragon_set) dragon_SET_bless += 0.2;
            if (sk_hunter1)    HUNTER_BLESS     += 0.05;
            if (sk_hunter3)    HUNTER_BLESS3    += 0.02;
            if (sk_togo)       HUNTER_BLESS4    += 0.05;
            
            if (sk_exp10)      HUNTER_BLESS4    += 0.10;
            if (sk_exp20)      HUNTER_BLESS4    += 0.20;

            if (sk_comaB) comab += 0.2;

            // Food bonus
            if (cook_1_7) foodBonus = 1.01;
            else if (cook_1_15) foodBonus = 1.05;
            else if (cook_1_23) foodBonus = 1.09;

            if (cook_miso)  foodBonus = 1.1;
            if (cook_metis) foodBonus = 1.05;
            if (cook_soup)  foodBonus = 1.04;
            if (cook_power) foodBonus = 1.2;

            // EXP_POTION / Tam_Fruit5
            if (sk_exp_potion) {
                expposion += pcRoom ? 0.4D : 0.3D;
            }
            if (sk_tam_fruit5) expposion += 0.05;

            // EXP_BUFF
            if (sk_exp_buff && hasAccount && pc.getAccount().getBlessOfAin() >= 10000) {
                if (level <= 79) exp_posion += 1.30D;
                else if (level <= 81) exp_posion += 1.20D;
                else if (level <= 83) exp_posion += 1.10D;
                else if (level <= 85) exp_posion += 1.00D;
                else if (level <= 86) exp_posion += 0.90D;
                else if (level <= 87) exp_posion += 0.80D;
                else if (level <= 88) exp_posion += 0.70D;
                else if (level <= 99) exp_posion += 0.60D;
            }

            // 레벨업 보너스
            if (sk_levelup) levelupBonus += 1.23;

            // 초보 보너스
            double beginnerBonus = 1.0;
            if (Config.ALT_BEGINNER_BONUS && level <= 65) beginnerBonus += 0.5;

            // ---- 아인/콤보/에메랄드/드래곤류 ----
            double einhasadBonus = 1.0;
            double emeraldBonus = 1.0;

            // One immutable input for this award. Bonus branches never debit
            // blessing; the accepted award has one settlement point below.
            final boolean hasAin = ainBefore > 10000;
            final boolean emerald = pc.hasSkillEffect(L1SkillId.EMERALD_YES);
            final boolean purple = pc.hasSkillEffect(L1SkillId.DRAGON_PUPLE);
            final boolean topaz = pc.hasSkillEffect(L1SkillId.DRAGON_TOPAZ);
            if (hasAccount) {
                if (ainBefore > 0 && sk_combo) {
                    final int cc = pc.getComboCount();
                    if (cc <= 20) comboBonus += cc * 0.1;
                }
                if (hasAin) {
                    if (ainBefore <= 18000000) einhasadBonus += 1.0;
                    else if (ainBefore <= 34000000) einhasadBonus += 1.3;
                    else einhasadBonus += 1.6;
                    if (pcRoom) einhasadBonus += 0.40;
                    if (emerald) emeraldBonus += 0.80;
                    else if (purple) {
                        if (level >= 49 && level <= 54) einhasadBonus += 0.53;
                        else if (level <= 59) einhasadBonus += 0.43;
                        else if (level <= 64) einhasadBonus += 0.33;
                        else einhasadBonus += 0.23;
                    } else if (topaz) einhasadBonus += 0.8;
                }
            }

            // 혈맹 온라인 버프
            double clanOnlineBonus = 1.0;
            if (clan != null) {
                final Object[] online = clan.getOnlineClanMember();
                if (online != null && online.length >= Config.CLAN_COUNT) {
                    clanOnlineBonus += Config.혈맹버프퍼센트;
                }
            }

            // 랭크
            double rankRate = 1.0;
            final int rankLevel = pc.getRankLevel();
            if (rankLevel >= 1 && rankLevel <= 5) {
                if (hasAccount && ainBefore > 2000000) {
                    rankRate += 0.10;
                }
            }

            // =========================================================
            // 최종 add_exp 계산
            // =========================================================
            final double mul =
                    exppenalty
                    * Config.RATE_XP
                    * levelupBonus
                    * beginnerBonus
                    * foodBonus
                    * clanBonus
                    * expposion
                    * comab
                    * comboBonus
                    * einhasadBonus
                    * emeraldBonus
                    * clanOnlineBonus
                    * dollBonus
                    * insignia
                    * Arka
                    * (dragon_hunter_bless * dragon_SET_bless)
                    * (HUNTER_BLESS * HUNTER_BLESS3 * HUNTER_BLESS4)
                    * exp_posion
                    * rankRate
                    * BloodBonus;    // 성혈 보너스

            // exp * mul -> long으로 받고 cap
            long addExpL = (long) (exp * mul);
            
            if (addExpL <= 0L) return 0;
            if (addExpL > Integer.MAX_VALUE) addExpL = Integer.MAX_VALUE;

            int add_exp = (int) addExpL;

            // =========================================================
            // 오토방지 매크로 캡챠(Captcha) 호출
            // =========================================================
            if (!processCaptcha(pc, mapId)) {
                return 0;
            }

            // 폭렙방지
            add_exp = clampToNextLevel(pc, add_exp);

            // 레벨 제한
            if (level >= Config.LIMITLEVEL && !pc.isGm()) {
                SC_SystemMessage.send(pc, "레벨제한으로 더이상 경험치 획득이 불가능합니다");
                return 0;
            }

            // max level cap
            if (level >= GameServerSetting.getInstance().get_maxLevel()) {
                int maxexp = ExpTable.getExpByLevel(GameServerSetting.getInstance().get_maxLevel() + 1);
                if ((long) pc.getExp() + (long) add_exp >= (long) maxexp) return 0;
            }

            if (add_exp <= 0) return 0;

            // A single eligible hunting award consumes once. Combo belongs in
            // its base once; PC-room and dragon bonuses do not execute another
            // copy of the identical base debit or clan credit.
            if (hasAin || (ainBefore > 0 && comboBonus > 1.0)) {
                final double eligible = Math.max(0.0, exp * comboBonus);
                final double calculated = SC_REST_EXP_INFO_NOTI.calcDecreaseCharacterEinhasad(pc, eligible, ainBefore);
                final int debit = (int) Math.max(0.0, Math.min(Integer.MAX_VALUE, calculated));
                if (debit > 0) account.addBlessOfAin(-debit, pc);
                if (clan != null) clan.addBlessCount((int) Math.min(Integer.MAX_VALUE, eligible));
                if (hasAin && pcRoom) SC_PacketBox.send(pc, 82, pc);
                if (hasAin && account.getBlessOfAin() <= 10000) {
                    if (purple && !emerald) pc.removeSkillEffect(L1SkillId.DRAGON_PUPLE);
                    else if (topaz && !emerald) pc.removeSkillEffect(L1SkillId.DRAGON_TOPAZ);
                }
            }
            return add_exp;

        } catch (Throwable t) {
            _log.log(Level.WARNING, "ExpGainCalculator error", t);
            return 0;
        }
    }

    // ---------------------------
    // 내부 유틸
    // ---------------------------

    private static int applyCompanionShare(L1PcInstance pc, int exp) {
        MJCompanionHuntingGround hground = MJCompanionHuntingGround.get_hunting_ground(pc.getMapId());
        if (hground != null) {
            double mag = hground.get_magnification_by_exp();
            if (mag > 0) {
                long shareL = (long) (exp * mag);
                if (shareL > 0) {
                    if (shareL > exp) shareL = exp;
                    int share = (int) shareL;

                    exp -= share;
                    if (pc.get_companion() != null) pc.get_companion().update_exp(share);
                }
            }
        }
        return exp;
    }

    private static int clampToNextLevel(L1PcInstance pc, int add_exp) {
        if (add_exp <= 0) return 0;
        if (pc.getLevel() >= 1) {
            int nextLvExp = ExpTable.getExpByLevel((pc.getLevel() + 1));
            long will = (long) add_exp + (long) pc.getExp();
            if (will > (long) nextLvExp) {
                long diff = (long) nextLvExp - (long) pc.getExp();
                if (diff <= 0L) return 0;
                if (diff > Integer.MAX_VALUE) diff = Integer.MAX_VALUE;
                add_exp = (int) diff;
            }
        }
        return add_exp;
    }
    
    // 캡챠 시스템 분리 모듈화
    private static boolean processCaptcha(L1PcInstance pc, int mapId) {
        if (pc.getSafetyZone()) return true;
        if (mapId == 1936 || mapId == 107 || mapId == 2101 || mapId == 2151 || mapId == 612 || mapId == 254 || mapId == 1930) {
            return true;
        }
        
        MJCaptcha captcha = pc.get_captcha();
        if (captcha == null) captcha = pc.create_captcha();

        if (captcha.is_keep_captcha()) {
            if (captcha.inc_relay_count() >= MJCaptchaLoadManager.CAPTCHA_RELAY_COUNT) {
                captcha.do_fail(pc);
            }
        } else {
            if (MJCaptchaLoadManager.CAPTCHA_IS_RUNNING) {
                if (!captcha.is_pass_captcha() && MJRnd.isWinning(1000000, MJCaptchaLoadManager.CAPTCHA_SHOW_PROBABILITY_BYMILLIMON)) {
                    if (pc.getAutoHunt()) {
                        return false; 
                    }
                    captcha.drain_captcha(pc);
                }
            }
        }
        return true;
    }
}
