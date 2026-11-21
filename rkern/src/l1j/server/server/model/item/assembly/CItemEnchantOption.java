package l1j.server.server.model.item.assembly;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.Config;
import l1j.server.MJCTSystem.MJCTObject;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.Loader.MJCTSystemLoader;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.ItemPresentator.ItemPresentatorFactory;
import l1j.server.PrideSystem.PrideInfo;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.ThreadPool; //CrockController
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1EquipmentSlot;
import l1j.server.server.model.EquipmentEnchantOptions;
import l1j.server.server.model.L1EquipmentSlot.ClassArmorBonus;
import l1j.server.server.model.L1EquipmentTimer;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.doll.DollOption;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.MJBytesOutputStream;
import l1j.server.server.utils.eKind;
import l1j.server.server.model.Instance.L1ItemInstance;

/** CItemEnchantOption-equivalent enchant and registered bonus presentation.
 * Reads the same ItemEffectRegistry values as the equipment application path. */
public final class CItemEnchantOption {
    private CItemEnchantOption() {}

    static void AssembleEnchantOption(L1ItemInstance item, ItemPresentOutStream os, int itemId, int op_addAc) {
        AssembleRuneEnchantOption(item, os, itemId);
        
				
				ItemEffectRegistry.Def regDef = ItemEffectRegistry.get(itemId);
				if (regDef != null) {
					 final int ctype = (item._cha != null) ? item._cha.getType() : -1;  
				    if (regDef.isAlwaysAcLine()) {
				        os.writeC(2);
				        os.writeC(regDef.acLineValue(ctype, item.getEnchantLevel()) + op_addAc);
				    }
		
				    final int enchantLevel = item.getEnchantLevel();
				    final List<ItemStat> stats = regDef.stats();
				    for (int i = 0; i < stats.size(); i++) {
				        final ItemStat stat = stats.get(i);
				        if (stat.wt != ItemStat.WType.B && stat.wt != ItemStat.WType.H) continue;
				        final int value = regDef.statValue(stat, ctype, enchantLevel);
				        if (value == 0) continue;
				        os.writeC(stat.opcode);
				        if (stat.wt == ItemStat.WType.B) os.writeC(value);
				        else os.writeH(value);
				    }
				    for (int i = 0; i < stats.size(); i++) {
				        final ItemStat stat = stats.get(i);
				        if (stat != ItemStat.POTION_AMT && stat != ItemStat.HEAL_DEF) continue;
				        final int value = regDef.statValue(stat, ctype, enchantLevel);
				        if (value == 0) continue;
				        if (stat == ItemStat.POTION_AMT) os.writePotionrecovery(value, value);
				        else os.writeHealDefence(value);
				    }
				    if (regDef.getTooltipText() != null) {
				        os.writeC(39);
				        os.writeS(regDef.getTooltipText());
				    }
				}
				

				
    }

    private static void AssembleRuneEnchantOption(L1ItemInstance item, ItemPresentOutStream os, int itemId) {
		final int accuracy = L1EquipmentSlot.elixirRuneAccuracy(itemId);
		final L1PcInstance owner = item._cha;
		if (owner == null || accuracy == 0) return;

		if (accuracy == 5) {
			os.writeC(39);
			os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
		}
		switch (owner.getType()) {
		case 0 -> {
			os.writeDMGdown(3);
			os.writeShortHIT(2);
			os.writeability_pierce(accuracy);
		}
		case 1 -> {
			os.writeC(31);
			os.writeH(50);
			os.writeShortDMG(1);
			os.writeability_pierce(accuracy);
		}
		case 2 -> {
			os.writeMaxMP(50);
			os.writeShortDMG(1);
			os.writeLongDMG(1);
			os.writeaspirit_pierce(accuracy);
		}
		case 3 -> {
			os.writeC(38);
			os.writeC(3);
			os.writeAddSP(1);
			os.writeMagicHIT(accuracy);
		}
		case 4 -> {
			os.writeMaxMP(30);
			os.writeaspirit_pierce(accuracy);
		}
		case 5 -> {
			os.writeShortHIT(3);
			os.writeDMGdown(1);
			os.writeadragonS_pierce(accuracy);
		}
		case 6 -> {
			os.writeC(68);
			os.writeC(5);
			os.writeC(31);
			os.writeH(50);
			os.writeadragonS_pierce(accuracy);
		}
		case 7 -> {
			os.writeAddMR(5);
			os.writeC(31);
			os.writeH(50);
			os.writeafear_pierce(accuracy);
		}
		}
	
    }

    static void AssembleDollOption(ItemPresentOutStream os, int itemId) {
	    if (os == null) return;

	    final DollRegistry.Def def = DollRegistry.byItemId(itemId);
	    if (def == null) return;

	    final DollRegistry.Profile p = def.getProfile();

	    for (int i = 0; i < p.displayCount(); i++) {
	        final DollRegistry.DisplayEntry e = p.displayAt(i);
	        final int value = e.getValue();
	        if (value != 0) {
	            writeDollOption(os, e.getOption(), value);
	        }
	    }

	    final String magic = p.getMagicText();
	    if (magic != null && !magic.isEmpty()) {
	        switch (p.getMagicMode()) {
	        case MAGIC:
	            os.writeMagic(magic);
	            break;
	        case MAGIC2:
	            os.writeMagic2(magic);
	            break;
	        default:
	            break;
	        }
	    }
	
    }

    private static void writeDollOption(ItemPresentOutStream os, DollOption option, int value) {
	    switch (option.getTooltipKind()) {
	    case AC:              os.writeAddAc(value); break;
	    case MAX_HP:          os.writeAddMaxHP(value); break;
	    case MAX_MP:          os.writeMaxMP(value); break;
	    case DMG_DOWN:        os.writeDMGdown(value); break;
	    case MELEE_DMG:       os.writeShortDMG(value); break;
	    case MELEE_HIT:       os.writeShortHIT(value); break;
	    case BOW_DMG:         os.writeLongDMG(value); break;
	    case BOW_HIT:         os.writeLongHIT(value); break;
	    case SP:              os.writeAddSP(value); break;
	    case MAGIC_HIT:       os.writeMagicHIT(value); break;
	    case MELEE_CRIT:      os.writeShortCritical(value); break;
	    case ABILITY_RESIS:   os.writeability_resis(value); break;
	    case FEAR_RESIS:      os.writeafear_resis(value); break;
	    case ALL_RESIS:       os.writeaAll_resis(value); break;
	    case ABILITY_PIERCE:  os.writeability_pierce(value); break;
	    case SPIRIT_PIERCE:   os.writeaspirit_pierce(value); break;
	    case DRAGON_PIERCE:   os.writeadragonS_pierce(value); break;
	    case FEAR_PIERCE:     os.writeafear_pierce(value); break;
	    case ALL_PIERCE:      os.writeaAll_pierce(value); break;
	    case PVP_DMG:         os.writePVPAddDMG(value); break;
	    case PVP_DMG_RED:     os.writePVPAddDMGdown(value); break;
	    case BLESS_RATE:      os.writeaBlesssomo(value); break;
	    case WEIGHT_X50:      os.writeAddWeight(value * 50); break;
	    case EXP:             os.writeAddEXP(value); break;
	    case MP_UP:           os.writeaMPUP(value); break;
	    case HP_UP:           os.writeaHPUP(value); break;
	    case MPR:             os.writeAddMPPrecovery(value); break;
	    case PERCENT_DMG:     os.writeaPercentDmg(value); break;
	    case FOUSLAYER:       os.writeaFouslayer(value); break;
	    case NONE:
	    default:
	        break;
	    }
	
    }

    static void AssembleMythicWeaponOption(L1ItemInstance item, ItemPresentOutStream os) {
		int enc = Math.min(item.getEnchantLevel(), 15);
		int ignoreReduction = 20 + (enc * 2);
		switch (item.getItemId()) {
		case 123555: { // 파아그리오의 공포 (근거리)
			os.writeShortDMG(44 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(7 + enc * 2);
			os.writeafear_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123777: { // 아인하사드의 섬광 (근거리)
			os.writeShortDMG(40 + enc * 5);
			os.writeShortHIT(22 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeability_pierce(16 + enc + (enc / 2));
			os.writeaspirit_pierce(16 + enc + (enc / 2));
			os.writeafear_pierce(16 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123888: { // 그랑카인의 심판 (근거리)
			os.writeShortDMG(44 + enc * 5);
			os.writeShortHIT(enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeability_pierce(20 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123900: { // 실렴의 결의 (근거리)
			os.writeShortDMG(36 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeaAll_pierce(16 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123800: { // 사이하의 집념 (원거리/활)
			os.writeLongDMG(39 + enc * 5);
			os.writeLongHIT(18 + enc + (enc / 2));
			os.writeLongCritical(10 + enc * 2);
			os.writeaspirit_pierce(10 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123444: { // 단테스의 시련 (근거리)
			os.writeShortDMG(36 + enc * 5);
			os.writeShortHIT(21 + enc + (enc / 2));
			os.writeShortCritical(7 + enc * 2);
			os.writeaspirit_pierce(17 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123333: { // 테이아의 혼돈 (마법/지팡이)
			os.writeShortHIT(enc / 2);
			os.writeAddDMG(enc * 2);
			os.writeMagicCritical(7 + enc * 2);
			os.writeAddSP(enc * 2 + (enc / 2));
			os.writeadragonS_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 124111: { // 아우라키아의 초월 (근거리)
			os.writeShortDMG(40 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeadragonS_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		default:
			break;
		}
	
    }

    public static void AssemblePotentialOption(L1ItemInstance item, ItemPresentOutStream 템) {
		
		if (Config.잠재력활성화) {
			switch (item.get_item_level()) {
			case 1:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 치명타: \\aA+1%");break;
			case 2:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 치명타: \\aA+1%");break;
			case 3:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH마법 치명타: \\aA+1%");break;
			case 4:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 대미지: \\aA+1");break;
			case 5:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 대미지: \\aA+1");break;
			case 6:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 명중: \\aA+1");break;
			case 7:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 명중: \\aA+1");break;
			case 8:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH대미지 감소: \\aA+1");break;
			case 9:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH추가 방어력: \\aA+1");break;
			case 10:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHMR: \\aA+3");break;
			case 11:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH최대 HP: \\aA+30");break;
			case 12:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH최대 MP: \\aA+20");break;
			case 13:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHPVP 추가 대미지: \\aA+1");break;
			case 14:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHPVP 대미지 감소: \\aA+1");break;
			case 15:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA불 +10");break;
			case 16:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA땅 +10");break;
			case 17:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA물 +10");break;
			case 18:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA바람 +10");break;
			case 19:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH소지 무게 증가: \\aA+100");break;
			case 20:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 치명타: \\aA+3%");break;
			case 21:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 치명타: \\aA+3%");break;
			case 22:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH마법 치명타: \\aA+3%");break;
			case 23:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 대미지: \\aA+2");break;
			case 24:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 대미지: \\aA+2");break;
			case 25:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 명중: \\aA+2");break;
			case 26:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 명중: \\aA+2");break;
			case 27:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH대미지 감소: \\aA+2");break;
			case 28:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH추가 방어력: \\aA+2");break;
			case 29:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHMR: \\aA+6");break;
			case 30:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH최대 HP: \\aA+60");break;
			case 31:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH최대 MP: \\aA+40");break;
			case 32:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHDG: \\aA+3");break;
			case 33:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHHP 절대 회복: \\aA+30(32초)");break;
			case 34:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHMP 절대 회복: \\aA+10(64초)");break;
			case 35:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHER: \\aA+3");break;
			case 36:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP 추가 대미지: \\aA+2");break;
			case 37:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP 대미지 감소: \\aA+2");break;
			case 38:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP마법대미지 감소: \\aA+2%");break;
			case 39:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA기술 +3");break;
			case 40:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA정령 +3");break;
			case 41:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA용언 +3");break;
			case 42:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA공포 +3");break;
				
			case 43:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA불+20");break;
			case 44:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA땅+20");break;
			case 45:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA물+20");break;
			case 46:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA바람+20");break;
			case 47:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aASTR +1");break;
			case 48:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aADEX +1");break;
			case 49:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aACON +1");break;
			case 50:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aAWIS +1");break;
			case 51:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aAINT +1");break;
			case 52:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH소지 무게 증가: \\aA+200");break;
			case 53:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 치명타: \\aA+5%");break;
			case 54:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 치명타: \\aA+5%");break;
			case 55:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL마법 치명타: \\aA+5%");break;
			case 56:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 대미지: \\aA+3");break;
			case 57:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 대미지: \\aA+3");break;
			case 58:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 명중: \\aA+3");break;
			case 59:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 명중: \\aA+3");break;
			case 60:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aLSP: \\aA+3");break;
			case 61:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL마법적중: \\aA+3");break;
			case 62:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL대미지감소: \\aA+3");
				break;
			case 63:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL추가 방어력: \\aA+3");
				break;
			case 64:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLMR: \\aA+10");
				break;
			case 65:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL최대 HP: \\aA+150");
				break;
			case 66:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL최대 MP: \\aA+100");
				break;
			case 67:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLDG: \\aA+5");
				break;
			case 68:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLHP절대 회복: \\aA+50(32초)");
				break;
			case 69:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLMP절대 회복: \\aA+12(64초)");
				break;
			case 70:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLER: \\aA+5");
				break;
			case 71:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP 추가 대미지: \\aA+3");
				break;
			case 72:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP 대미지 감소: \\aA+3");
				break;
			case 73:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP마법대미지 감소 :\\aA+5%");
				break;
			case 74:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA기술 +5");
				break;
			case 75:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA정령 +5");
				break;
			case 76:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA용언 +5");
				break;
			case 77:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA공포 +5");
				break;
			case 78:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA기술 +3");
				break;
			case 79:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA정령 +3");
				break;
			case 80:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA용언 +3");
				break;
			case 81:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA공포 +3");
				break;
			case 82:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA불+30");
				break;
			case 83:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA땅+30");
				break;
			case 84:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA물+30");
				break;
			case 85:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA바람+30");
				break;
			case 86:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL모든 속성 저항: \\aA+10");
				break;
			case 87:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aASTR+2");
				break;
			case 88:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aADEX+2");
				break;
			case 89:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aACON+2");
				break;
			case 90:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aAWIS+2");
				break;
			case 91:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aAINT+2");
				break;
			case 92:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL소지 무게 증가: \\aA+200");
				break;
			case 93:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3근거리 대미지: \\aA+5");
				break;
			case 94:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3원거리 대미지: \\aA+5");
				break;
			case 95:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3근거리 명중: \\aA+5");
				break;
			case 96:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3원거리 명중: \\aA+5");
				break;
			case 97:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3SP: \\aA+5");
				break;
			case 98:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3마법적중: \\aA+5");
				break;
			case 99:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3대미지감소: \\aA+5");
				break;
			case 100:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3추가 방어력: \\aA+5");
				break;
			case 101:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MR: \\aA+15");
				break;
			case 102:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3최대 HP: \\aA+300");
				break;
			case 103:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3최대 MP: \\aA+200");
				break;
			case 104:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3DG: \\aA+10");
				break;
			case 105:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3HP절대 회복: \\aA+150(32초)");
				break;
			case 106:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MP절대 회복: \\aA+30(64초)");
				break;
			case 107:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3ER: \\aA+12");
				break;
			case 108:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP 추가 대미지: \\aA+7");
				break;
			case 109:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP 대미지 감소: \\aA+7");
				break;
			case 110:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP마법대미지 감소 :\\aA+10%");
				break;
			case 111:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP대미지감소무시 :\\aA+10");
				break;
			case 112:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA기술 +8");
				break;
			case 113:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA정령 +8");
				break;
			case 114:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA용언 +8");
				break;
			case 115:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA공포 +8");
				break;
			case 116:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 내성 :\\aA+3");
				break;
			case 117:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA기술 +5");
				break;
			case 118:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA정령 +5");
				break;
			case 119:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA용언 +5");
				break;
			case 120:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA공포 +5");
				break;
			case 121:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 적중 :\\aA+3");
				break;
		case 122:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 속성 저항: \\aA+30");;
				break;
			case 123:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aASTR +3");
				break;
			case 124:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aADEX +3");
				break;
			case 125:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aACON +3");
				break;
			case 126:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aAWIS +3");
				break;
			case 127:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aAINT +3");
				break;
			case 128:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 스탯: \\aA+1");
				break;
			case 129:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MP 흡수");
				break;
			case 130:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3소지 무게 증가: \\aA+500");
				break;
			case 131:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4디케이 포션 발동");
				break;
			case 132:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4PVP대미지감소무시 :\\aA+"+Config.인형PVP감소수치);
				break;
			case 133:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4모든 스탯 :\\aA+3");
				break;
			case 134:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4HP 흡수");
				break;
			case 135:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4MP 흡수");
				break;
			case 136:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4소울오브프레임 발동");
				break;
			case 137:
				int aa = (int) ((double) Config.잠재력이뮨 * 100);
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4이뮨투함 무시 \\aA+"+aa+"%");
				break;
			case 138:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4저지먼트 발동");
				break;
			case 139:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f43단가속 상시 효과");
				break;
			}
			}
		
		
	
    }

    /** Weapon and armor attribute/enchant rendering in original L1 packet order.
     * Existing specialized option values are preserved while definitions are consolidated. */
    static void AssembleEquipmentItem(L1ItemInstance item, ItemPresentOutStream os,
                                      int itemType2, int itemId, int itemgrade) throws Exception { // weapon | armor

				int op_addAc = 0;
                // Compute the shared accessory effects only once per item.
                // Scratch storage is acquired on the current thread and released
                // even if a malformed item's tooltip throws while assembling.
                final boolean snapper = itemType2 == 2 && isSnapperRing(itemId);
                final boolean roomtis = itemType2 == 2 && isRoomtisItem(itemId);
                final int accessoryType = item.getItem().getType();
                final boolean genericAccessory = itemType2 == 2
                        && ((itemgrade >= 0 && itemgrade <= 2 && accessoryType >= 8 && accessoryType <= 12)
                            || (itemgrade == 5 && (accessoryType == 9 || accessoryType == 11)));
                final boolean needsBonus = snapper || roomtis
                        || (genericAccessory && item.getEnchantLevel() > 0);
                final int[] accessoryBonus = needsBonus
                        ? EquipmentEnchantOptions.accessoryBonusSnapshot(item.getItem(), item.getEnchantLevel())
                        : null;
                final int[] snapperBonus = snapper ? accessoryBonus : null;
                final int[] roomtisBonus = roomtis ? accessoryBonus : null;
                final int[] genericBonus = genericAccessory && !snapper
                        && item.getEnchantLevel() > 0 ? accessoryBonus : null;
                try {

				/** 아이템 안전인챈 표시 추가 **/
				
				  int SafeEnchant = item.getItem().get_safeenchant(); os.writeC(39);
				  if (SafeEnchant < 0) {
					  SafeEnchant = 0; }
				  os.writeS("\\aG[안전인챈:+" + SafeEnchant + "]");
				 

				if (itemType2 == 1) { // weapon 무기 타격치
					os.writeC(1);
					os.writeC(item.getItem().getDmgSmall());
					os.writeC(item.getItem().getDmgLarge());
					os.writeC(item.getItem().getMaterial());
					os.writeD(item.getWeight());
				} else if (itemType2 == 2) { // armor
					if (item.getItem().getType() == 14) {
						os.writeC(19);
						int ac = CItemConstInfo.GetRuneAc(item);
						os.writeC(ac - ac - ac);
					} else {
						os.writeC(19);
						int ac = ((L1Armor) item.getItem()).get_ac();
						int Grade = ((L1Armor) item.getItem()).getGrade();
						if (ac < 0) {
							ac = ac - ac - ac;
						} else
							ac *= -1;
						os.writeC(ac - item.get_durability());
					}

					os.writeC(item.getItem().getMaterial());
					os.writeH(-1);
					os.writeD(item.getWeight());
				}

				if (itemId == 900081 || itemId == 900082 || itemId == 900083) { // 커츠의
					if (item.getEnchantLevel() == 4) {
						os.writeC(2);
						os.writeC(1);
					} else if (item.getEnchantLevel() == 5) {
						os.writeC(2);
						os.writeC(3);
					} else if (item.getEnchantLevel() == 6) {
						os.writeC(2);
						os.writeC(5);
					} else if (item.getEnchantLevel() == 7) {
						os.writeC(2);
						os.writeC(6);
					} else if (item.getEnchantLevel() == 8) {
						os.writeC(2);
						os.writeC(7);
					} else if (item.getEnchantLevel() == 9) {
						os.writeC(2);
						os.writeC(8);
					}
				
				} else if (itemId == 900152 || itemId == 900153 || itemId == 900154) {
					if (item.getEnchantLevel() == 4) {
						os.writeC(2);
						os.writeC(1);
					} else if (item.getEnchantLevel() == 5) {
						os.writeC(2);
						os.writeC(2);
					} else if (item.getEnchantLevel() == 6) {
						os.writeC(2);
						os.writeC(3);
					} else if (item.getEnchantLevel() == 7) {
						os.writeC(2);
						os.writeC(3);
					} else if (item.getEnchantLevel() == 8) {
						os.writeC(2);
						os.writeC(3);
					}
				} else if (itemId == 900084) { // 커츠의 수호 휘장
					if (item.getEnchantLevel() >= 6) {
						os.writeC(2);
						os.writeC(item.getEnchantLevel() - 1);
					} else if (item.getEnchantLevel() >= 3) {
						os.writeC(2);
						os.writeC(item.getEnchantLevel() - 2);
					}
				} else if (itemId == 900118 || itemId == 900117 || itemId == 900119 || itemId == 900120) {
					int enchant = item.getEnchantLevel();
					os.write(0x02);
					if (enchant >= 5) {
						os.write(enchant);
					} else {
						os.write(0x00);
					}
				} else if (roomtisBonus != null) {
                    // This equipment's AC enchant effect must come from the actual
                    // equip/unequip calculation, not from item-ID or grade guesses.
                    final int ac = -roomtisBonus[ItemStat.AC.ordinal()];
                    if (ac > 0) {
                        os.writeC(2);
                        os.writeC(ac + op_addAc);
                    }
                } else if (item.getEnchantLevel() >= 5 && itemType2 == 2
						&& (item.getItem().get장신구처리() == 8 || item.getItem().get장신구처리() == 12)) {
					int enchantAc = item.getEnchantLevel() - 4;
					if (item.getEnchantLevel() >= 10) {
						enchantAc = 6;
					} else {
					if (enchantAc >= 5) {
						enchantAc = 5;
					}
					}
					os.writeC(2);
					os.writeC(enchantAc);
				} else if (item.getEnchantLevel() != 0 && !(itemType2 == 2 && item.getItem().getGrade() >= 0)) {
					if (item.getItemId() == 66) {
						os.writeC(0x6B);
						int v = item.getEnchantLevel() * 2;
						os.writeC(v);
						os.writeC(v);
					} else {
						os.writeC(2);
						os.writeC(item.getEnchantLevel());
					}
					/** 룸티스 검은빛 귀걸이 AC표현처리 부분 **/
				} else if (itemType2 == 2 && itemId == 22224 || itemId == 22225 || itemId == 22227) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeC(3 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 마법저항반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 22228 || itemId == 22226) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
						os.writeC(3 + op_addAc);
						break;
					case 6:
					case 7:
						os.writeC(4 + op_addAc);
						break;
					case 8:
						os.writeC(6 + op_addAc);
						break;
					case 9:
						os.writeC(8 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 지혜 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222290) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
						os.writeC(32);
						os.writeH(15);
						break;
					case 2:
						os.writeC(2);
						os.writeC(1 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 3:
						os.writeC(2);
						os.writeC(2 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 4:
					case 5:
						os.writeC(2);
						os.writeC(3 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 6:
						os.writeC(2);
						os.writeC(4 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(2);
						os.writeC(4 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						os.writeMagicHIT(1);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(2);
						os.writeC(6 + op_addAc);
						os.writeC(32);
						os.writeH(30);
						os.writeMagicHIT(3);
						os.writeability_resis(9);
						os.writeafear_resis(1);
						break;
					case 9:
						os.writeC(2);
						os.writeC(8 + op_addAc);
						os.writeC(32);
						os.writeH(70);
						os.writeMagicHIT(5);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					default:
						os.writeC(2);
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼의 지혜 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222335) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeAddMaxHP(5);
						break;
					case 2:
						os.writeC(1 + op_addAc);
						os.writeAddMaxHP(10);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(30);
						break;
					case 6:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(35);
						os.writeMagicHIT(1);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						os.writeAddMaxHP(40);
						os.writeMagicHIT(2);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						os.writeAddMaxHP(50);
						os.writeMagicHIT(5);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						os.writeAddMaxHP(60);
						os.writeMagicHIT(7);
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 용사 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222291) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(1 + op_addAc);
						break;
					case 2:
						os.writeC(2 + op_addAc);
						break;
					case 3:
						os.writeC(3 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeC(4 + op_addAc);
						break;
					case 8:
						os.writeC(6 + op_addAc);
						break;
					case 9:
						os.writeC(8 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
				} else if (itemId == 222332) {
					
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						break;
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					
					/** 축복받은 스냅퍼 체력,마법저항 반지 AC부분 처리 **/
				} else if (itemId == 222334) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						break;
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼의 회복,집중,마나 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222330 || itemId == 222331 || itemId == 222333) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeC(3 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼 용사의반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222336) {
					os.writeC(2);
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(1 + op_addAc);
						break;
					case 2:
						os.writeC(2 + op_addAc);
						break;
					case 3:
						os.writeC(3 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
				}
				
				CItemEnchantOption.AssembleEnchantOption(item, os, itemId, op_addAc);
				/** 스냅퍼 추가 대미지: 장착 시 적용하는 옵션을 그대로 표시 */
                if (snapperBonus != null) {
                    final int extra = snapperBonus[ItemStat.MELEE_DAMAGE_UP.ordinal()];
                    final int total = item.getItem().getDmgModifier() + extra;
                    if (total > 0) os.writeAddDMG(total);
                } else if (itemType2 == 1 && itemId == 202012) {
					if (item.getEnchantLevel() > 0) {
						os.writeWeaponDmg(item.getEnchantLevel() * 2, item.getEnchantLevel() * 2);
						os.writeShortDMG(item.getItem().getDmgModifier());
						os.writeShortHIT(0);
					}
					
				} else if (item.getItem().getDmgModifier() != 0) {
					if (itemType2 == 1 && item.getItem().getType1() != 20) {
						
						if (item.getEnchantLevel() > 0) {
							if(L1ItemInstance.추타2무기(itemId)){
								os.writeWeaponDmg(item.getEnchantLevel() * 2, item.getEnchantLevel() * 2);
							}else {
								os.writeWeaponDmg(item.getEnchantLevel(), item.getEnchantLevel());
							}
						}
						os.writeShortDMG(item.getItem().getDmgModifier());
					} else if (itemType2 == 1) {
						
						if (item.getEnchantLevel() > 0) {
							if(itemId == 202011 || itemId == 2202011){
								os.writeWeaponDmg(item.getEnchantLevel() * 2, item.getEnchantLevel() * 2);
							}else {
								os.writeWeaponDmg(item.getEnchantLevel(), item.getEnchantLevel());
							}
						}
						os.writeLongDMG(item.getItem().getDmgModifier());
					} else {
						os.writeAddDMG(item.getItem().getDmgModifier());
					}
				} else {
					if (item.isUndeadDmg()) {
						if (itemType2 == 1 && item.getItem().getType1() != 20) {
							os.writeShortDMG(0);
						} else {
							os.writeLongDMG(0);
						}
					}
				}
				if (item.getItem().isTwohandedWeapon()) { // 양손무기
					os.writeC(4);
				}
				if (item.get_durability() != 0) { // 손상도
					os.writeC(3);
					os.writeC(item.get_durability());
				}
				/** 클래스 착용 부분 **/
				int bit = 0;
				bit |= item.getItem().isUseRoyal() ? 1 : 0;
				bit |= item.getItem().isUseKnight() ? 2 : 0;
				bit |= item.getItem().isUseElf() ? 4 : 0;
				bit |= item.getItem().isUseMage() ? 8 : 0;
				bit |= item.getItem().isUseDarkelf() ? 16 : 0;
				bit |= item.getItem().isUseDragonKnight() ? 32 : 0;
				bit |= item.getItem().isUseBlackwizard() ? 64 : 0;
				bit |= item.getItem().isUse전사() ? 128 : 0;
				os.writeC(7);
				os.writeC(bit);

				os.writeC(130);
				if (!item.getItem().isTradable()) {
					os.writeD(6);
				} else {
					os.writeD(7);
				}

				if (item.isCanbeDmg()) {
					os.writeC(131);
					os.writeD(1); // 비손상
				}

				os.writeC(132);
				os.writeD(3); // 성별 구분 1.남자 2.여자 3.전체

				if (itemType2 == 1 && item.isUndeadDmg()) {
					os.writeC(114);
					os.writeD(1); // 언데드
				}
				/** 스냅퍼의 용사 반지 공격성공 표기 **/
				if (itemType2 == 2 && itemId == 222291) {
					
					if (item.getEnchantLevel() > 4 && item.getEnchantLevel() <= 7 ) {
						os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 4);
						os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 4);
						} else if (item.getEnchantLevel() == 8) {
							os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 3);
							os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 3);
						}else if (item.getEnchantLevel() >= 9) {
							os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 2);
							os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 2);
						}
				/** 축복받은 스냅퍼의 용사 반지 공격성공 표기 **/
			} else if(itemType2 == 2 && itemId == 222336) {
				if (item.getEnchantLevel() > 3 && item.getEnchantLevel() <= 7 ) {
					os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 3);
					os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 3);
				} else if (item.getEnchantLevel() == 8) {
					os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 1);
					os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel() - 1);
				} else if (item.getEnchantLevel() >= 9) {
					os.writeShortHIT(item.getItem().getHitModifier() + item.getEnchantLevel());
					os.writeLongHIT(item.getItem().getHitModifier() + item.getEnchantLevel());
				}
			}
				/** 반지 5이상 추가 대미지 **/
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& item.getItem().get장신구처리() == 9 || item.getItem().get장신구처리() == 11) {
					if (item.getEnchantLevel() > 4) {
						os.writeShortDMG(item.getEnchantLevel() - 4);
						os.writeLongDMG(item.getEnchantLevel() - 4);
					}
				}
				/** 스냅퍼 PvP 추가 대미지: 실제 적용 수치 사용 */
                if (snapperBonus != null) {
                    final int pvp = snapperBonus[ItemStat.PVP_DMG.ordinal()];
                    if (pvp > 0) os.writePVPAddDMG(pvp);
                } else if (itemId >= 307 && itemId <= 314) {
					if (item.getEnchantLevel() == 7) {
						os.writeC(59);
						os.writeC(item.getEnchantLevel() - 4);
					} else if (item.getEnchantLevel() == 8) {
						os.writeC(59);
						os.writeC(item.getEnchantLevel() - 3);
					} else if (item.getEnchantLevel() == 9) {
						os.writeC(59);
						os.writeC(item.getEnchantLevel() - 2);
					} else if (item.getEnchantLevel() == 10) {
						os.writeC(59);
						os.writeC(item.getEnchantLevel());
					}
				}

				/** 스냅퍼의 반지류 HP증가 표시 **/
				if (snapperBonus != null && itemId >= 22224 && itemId <= 22228) {
                    final int hp = snapperBonus[ItemStat.MAX_HP.ordinal()];
                    if (hp > 0) os.writeAddMaxHP(item.getItem().get_addhp() + hp);
                } else if (itemType2 == 2 && itemId == 222332 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 15);
						break;
					case 2:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 20);
						break;
					case 3:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 30);
						break;
					case 4:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 35);
						break;
					case 5:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 40);
						break;
					case 6:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 45);
						break;
					case 7:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 55);
						break;
					case 8:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 65);
						break;
					case 9:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 75);
						break;
					default:
						break;
					}
					/** 축복받은 스냅퍼의 마법저항,집중,마나 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId >= 222330 && itemId <= 222331
						|| itemId >= 222333 && itemId <= 222334 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 15);
						break;
					case 2:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 20);
						break;
					case 3:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 30);
						break;
					case 4:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 35);
						break;
					case 5:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 40);
						break;
					case 6:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 45);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 50);
						break;
					case 9:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 60);
						break;
					default:
						break;
					}
					/** 스냅퍼의 지혜 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222290) {
					if (item.getEnchantLevel() > 0 && item.getEnchantLevel() <= 8) {
					int 지혜반지HP증가 = (item.getEnchantLevel() * 5);
					os.writeC(14);
					os.writeH(item.getItem().get_addhp() + 지혜반지HP증가);
					} else if (item.getEnchantLevel() >= 9) {
						os.writeC(14);
						os.writeH(50);
					}
					/** 축복받은 스냅퍼의 지혜 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222335 && item.getEnchantLevel() >= 0) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 1:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 5);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 2:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 10);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 3:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 20);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 4:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 25);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 5:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 30);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 6:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 35);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 7:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 40);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 30);
						break;
					case 8:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 50);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 35);
						break;
					case 9:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 50);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 80);
						break;
					default:
						break;
					}
					/** 스냅퍼의 용사 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222291) {
					if (item.getEnchantLevel() >= 3 && item.getEnchantLevel() <= 8) {
				
					int 용사반지HP증가 = (item.getEnchantLevel() - 2) * 5;
					os.writeC(14);
					os.writeH(item.getItem().get_addhp() + 용사반지HP증가);
					} else if (item.getEnchantLevel() >= 9) {
						
						os.writeC(14);
						os.writeH(40);
					}
					/** 축복받은 스냅퍼의 용사 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222336 && item.getEnchantLevel() > 2) {
					switch (item.getEnchantLevel()) {
					case 3:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 10);
						break;
					case 4:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 15);
						break;
					case 5:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 20);
						break;
					case 6:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 25);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 30);
						break;
					case 9:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 40);
						break;
					default:
						break;
					}
					/** 룸티스 붉은빛 귀걸이 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22229 && item.getEnchantLevel() > 0) {
					int 붉귀HP증가 = (item.getEnchantLevel() * 10) + 10;
					os.writeC(14);
					os.writeH(item.getItem().get_addhp() + 붉귀HP증가);
					/** 축복받은 룸티스 붉은빛 귀걸이 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222337 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 20);
						break;
					case 2:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 30);
						break;
					case 3:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 50);
						break;
					case 4:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 60);
						break;
					case 5:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 70);
						break;
					case 6:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 80);
						break;
					case 7:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 90);
						break;
					case 8:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 140);
						break;
					case 9:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 150);
						break;
					default:
						break;
					}
					/** 체력의 가더 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22256 && item.getEnchantLevel() >= 5) {
					switch (item.getEnchantLevel()) {
					case 5:
					case 6:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 25);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 50);
						break;
					case 9:
					case 10:
						os.writeC(14);
						os.writeH(item.getItem().get_addhp() + 75);
						break;
					default:
						break;
					}
					// TODO 반지 귀걸이 인챈에대한 HP표기
				} else if (genericBonus != null) {
                    final int extraHp = genericBonus[ItemStat.MAX_HP.ordinal()];
                    if (extraHp > 0) {
                        os.writeAddMaxHP(item.getItem().get_addhp() + extraHp);
                    } else if (item.getItem().get_addhp() > 0) {
                        os.writeAddMaxHP(item.getItem().get_addhp());
                    }
                } else if (item.getItem().get_addhp() != 0) {
					os.writeC(14);
					os.writeH(item.getItem().get_addhp());
				}
				// TODO 벨트 인챈에 대한 MP증가 표기
				if (itemType2 == 2 && item.getItem().getType() == 10 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 10);
						break;
					case 3:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 20);
						break;
					case 4:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 30);
						break;
					case 5:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 40);
						break;
					case 6:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 40);
						break;
					case 7:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 50);
						break;
					case 8:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 50);
						break;
					case 9:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 60);
						break;
					case 10:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 70);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 MP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 3:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 30);
						break;
					case 4:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 35);
						break;
					case 5:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 50);
						break;
					case 6:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 55);
						break;
					case 7:
						os.writeMagicHIT(1);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 70);
						break;
					case 8:
						os.writeMagicHIT(5);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 95);
						os.writeAddMaxHP(50);
						break;
					case 9:
						os.writeMagicHIT(9);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 125);
						os.writeAddMaxHP(100);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 MP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 15);
						break;
					case 3:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 35);
						break;
					case 4:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 50);
						break;
					case 5:
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 55);
						break;
					case 6:
						os.writeMagicHIT(1);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 70);
						break;
					case 7:
						os.writeMagicHIT(3);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 95);
						break;
					case 8:
						os.writeAddMaxHP(100);
						os.writeMagicHIT(7);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 125);
						break;
					case 9:
						os.writeAddMaxHP(150);
						os.writeMagicHIT(12);
						os.writeC(32);
						os.writeH(item.getItem().get_addmp() + 155);
						break;
					default:
						break;
					}
					/** 대마법사의 모자 인챈트 MP증가 표시 **/
				} else if (itemId == 202022) {
					os.writeC(32);
					os.writeH(item.getItem().get_addmp() + (item.getEnchantLevel() * 10));
					os.writeC(15);
					os.writeH(item.getMr() + (item.getEnchantLevel() * 2));
					/** MP패킷변경됨 C/H **/
				} else if (item.addmp() != 0) {
					os.writeC(32);
					os.writeH(item.addmp());// mp부분패킷변경됨
				}
				// 피틱 표시
				if (item.getItem().get_addhpr() != 0) {
					os.writeC(37);
					os.writeC(item.getItem().get_addhpr());
				}
				// 엠틱 표시
				if (itemId == 1134 || itemId == 101134) {
					os.writeC(38);
					os.writeC(item.getItem().get_addmpr() + item.getEnchantLevel()); // 명상의
				} else if (item.getItem().get_addmpr() != 0) {
					os.writeC(38);
					os.writeC(item.getItem().get_addmpr());
				}
	
				if (item.getItem().getType2() == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& item.getEnchantLevel() > 4) {
					if (item.getItem().getType() == 8) { // 목걸이
						int enchantPotion = ((item.getEnchantLevel() - 4) * 2) + 1;
						if (item.getEnchantLevel() >= 10) {
							enchantPotion = 11;
						} else {
							if (enchantPotion >= 10) {
								enchantPotion = 10;
						}
						}
						os.writeC(65);
						os.writeC(enchantPotion);
						os.writeC(item.getEnchantLevel() == 6 ? 3
								: item.getEnchantLevel() == 7 ? 5
										: item.getEnchantLevel() == 8 ? 7 : item.getEnchantLevel() == 9 ? 8 : item.getEnchantLevel() >= 10 ? 9 : 0);
						os.writeC(96);
						os.writeC(enchantPotion);
						os.writeC(15);//마방
						os.writeH(item.getEnchantLevel() == 5 ? 1 : item.getEnchantLevel() == 6 ? 3 :item.getEnchantLevel() == 7 ? 5 :
							item.getEnchantLevel() == 8 ? 7 : item.getEnchantLevel() == 9 ? 10 : item.getEnchantLevel() >= 10 ? 12 : 0 );
					}
					if (item.getItem().getType() == 12) { // 귀걸이
						int enchantPotion = (item.getEnchantLevel() - 4) * 2;
						if (item.getEnchantLevel() >= 10) {
							enchantPotion = 10;
						} else {
						if (enchantPotion >= 9) {
							enchantPotion = 9;
						}
						}
						os.writeC(65);
						os.writeC(enchantPotion);
						os.writeC(item.getEnchantLevel() == 6 ? 2
								: item.getEnchantLevel() == 7 ? 4
										: item.getEnchantLevel() == 8 ? 6 : item.getEnchantLevel() == 9 ? 7: item.getEnchantLevel() >= 10 ? 8 : 0);
						os.writeC(96);
						os.writeC(enchantPotion);
					}
				}

				if (itemType2 == 2 && itemId == 222340 || itemId == 222341) {
					if (item.getEnchantLevel() == 3) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
						}
					} else if (item.getEnchantLevel() == 4) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
							os.writeAddDmgPer(2, 20);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 3);
						}
					} else if (item.getEnchantLevel() == 5) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
							os.writeAddDmgPer(3, 20);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 3);
							os.writeAddDmgPer(2, 20);
						}
					} else if (item.getEnchantLevel() == 6) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
							os.writeAddDmgPer(4, 20);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 3);
							os.writeAddDmgPer(3, 20);
						}
					} else if (item.getEnchantLevel() == 7) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 2);
							os.writeAddDmgPer(5, 20);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 3);
							os.writeAddDmgPer(4, 20);
						}
					} else if (item.getEnchantLevel() == 8) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() + 1);
							os.writeAddDmgPer(6, 30);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() - 1);
							os.writeAddDmgPer(5, 25);
						}
					}else if (item.getEnchantLevel() == 9) {
						if (item.getItemId() == 222341) {
							os.writeC(6);
							os.writeC(item.getEnchantLevel() + 3);
							os.writeAddDmgPer(8, 30);
						} else {
							os.writeC(6);
							os.writeC(item.getEnchantLevel());
							os.writeAddDmgPer(7, 25);
						}
					}
				}

				/** 축복받은 스냅퍼의 마법 저항 반지 MR표시 **/
				if (itemType2 == 2 && itemId == 222334) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeC(15);
						os.writeH(7);
						break;
					case 6:
						os.writeC(15);
						os.writeH(8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(9);
						break;
					case 8:
						os.writeC(15);
						os.writeH(12);
						break;
					case 9:
						os.writeC(15);
						os.writeH(15);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 마방 표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeC(15);
						os.writeH(item.getMr() + 2);
						break;
					case 1:
						os.writeC(15);
						os.writeH(item.getMr() + 5);
						break;
					case 2:
						os.writeC(15);
						os.writeH(item.getMr() + 6);
						break;
					case 3:
						os.writeC(15);
						os.writeH(item.getMr() + 7);
						break;
					case 4:
						os.writeC(15);
						os.writeH(item.getMr() + 8);
						break;
					case 5:
						os.writeC(15);
						os.writeH(item.getMr() + 9);
						break;
					case 6:
						os.writeC(15);
						os.writeH(item.getMr() + 10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(item.getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(item.getMr() + 17);
						break;
					case 9:
						os.writeC(15);
						os.writeH(item.getMr() + 20);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 마방 표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && item.getEnchantLevel() > 0) {
					switch (item.getEnchantLevel()) {
					case 3:
						os.writeC(15);
						os.writeH(item.getMr() + 8);
						break;
					case 4:
						os.writeC(15);
						os.writeH(item.getMr() + 9);
						break;
					case 5:
						os.writeC(15);
						os.writeH(item.getMr() + 10);
						break;
					case 6:
						os.writeC(15);
						os.writeH(item.getMr() + 12);
						break;
					case 7:
						os.writeC(15);
						os.writeH(item.getMr() + 15);
						break;
					case 8:
						os.writeC(15);
						os.writeH(item.getMr() + 20);
						break;
					case 9:
						os.writeC(15);
						os.writeH(item.getMr() + 23);
						break;
					default:
						break;
					}
				} else if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (item.getItem().get장신구처리() == 9 || item.getItem().get장신구처리() == 11) && item.getEnchantLevel() >= 6) {
					int enchantMr = 0;
					switch (item.getEnchantLevel()) {
					case 6:
						enchantMr = 1;
						break;
					case 7:
						enchantMr = 3;
						break;
					case 8:
						enchantMr = 5;
						break;
					case 9:
						enchantMr = 7;
						break;
					case 10:
						enchantMr = 9;
						break;
					}
					if (item.getEnchantLevel() >= 10) {
						enchantMr = 9;
					}
					os.writeC(15);
					os.writeH(item.getMr() + enchantMr);
				} else if (item.getMr() != 0) { // MR
					os.writeC(15);
					os.writeH(item.getMr());
				}
				/** 스냅퍼의 지혜 반지 SP표시 **/
				if (itemType2 == 2 && itemId == 222290 && item.getEnchantLevel() > 4) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 6:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 7);
						break;
					default:
						break;
					}
					/** 축복받은 스냅퍼의 지혜 반지 SP표시 **/
				} else if (itemType2 == 2 && itemId == 222335 && item.getEnchantLevel() > 3) {
					switch (item.getEnchantLevel()) {
					case 4:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 5:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 6:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 4);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 7);
						break;
					case 9:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 9);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 SP표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && item.getEnchantLevel() > 2) {
					switch (item.getEnchantLevel()) {
					case 3:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 4:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 5:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 6:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 7);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 SP표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && item.getEnchantLevel() > 2) {
					switch (item.getEnchantLevel()) {
					case 3:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 4:
					case 5:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 6:
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 9);
						break;
					default:
						break;
					}
					/** 화령의 가더 **/
				} else if (itemId == 900117) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddDMG(1);
						break;
					case 5:
						os.writeAddDMG(1);
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeAddDMG(1);
						os.writeShortHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeAddDMG(2);
						os.writeShortHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeAddDMG(3);
						os.writeShortHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeAddDMG(4);
						os.writeShortHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeAddDMG(5);
						os.writeShortHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 풍령의 가더 **/
				} else if (itemId == 900118) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeLongHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeLongDMG(2);
						os.writeLongHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeLongDMG(3);
						os.writeLongHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeLongDMG(4);
						os.writeLongHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeLongDMG(5);
						os.writeLongHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 수령의 가더 **/
				} else if (itemId == 900119) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeMagicHIT(1);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeMagicHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeAddSP(2);
						os.writeMagicHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeAddSP(3);
						os.writeMagicHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeAddSP(4);
						os.writeMagicHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeAddSP(5);
						os.writeMagicHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 지령의 가더 **/
				} else if (itemId == 900120) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeDMGdown(1);
						break;
					case 5:
						os.writeDMGdown(1);
						os.writeAddMR(2);
						break;
					case 6:
						os.writeDMGdown(1);
						os.writeAddMR(3);
						break;
					case 7:
						os.writeDMGdown(2);
						os.writeAddMR(4);
						break;
					case 8:
						os.writeDMGdown(3);
						os.writeAddMR(5);
						break;
					case 9:
						os.writeDMGdown(4);
						os.writeAddMR(6);
						break;
					case 10:
						os.writeDMGdown(5);
						os.writeAddMR(7);
						break;
					default:
						break;
					}
					/** 마법사의 가더 SP표시 **/
				} else if (itemType2 == 2 && itemId == 22255 && item.getEnchantLevel() > 4) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 6:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 9:
					case 10:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					default:
						break;
					}
					/** 바포메트의 지팡이 SP표시 **/
				} else if (itemType2 == 1 && itemId == 124 && item.getEnchantLevel() > 6) {
					switch (item.getEnchantLevel()) {
					case 7:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 1);
						break;
					case 8:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 2);
						break;
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeC(17);
						os.writeC(item.getItem().get_addsp() + 3);
						break;
					default:
						break;
					}
					/** 리치 로브 SP표시 **/
				} else if (itemType2 == 2 && itemId == 20107 && item.getEnchantLevel() >= 3) {
					os.writeC(17);
					os.writeC(item.getItem().get_addsp() + item.getEnchantLevel() - 2);
				} else if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (item.getItem().get장신구처리() == 9 || item.getItem().get장신구처리() == 11) && item.getEnchantLevel() >= 7) {
					int enchantSp = item.getEnchantLevel() - 6;
					if (enchantSp >= 4) {
						enchantSp = 4;
					}
					if (itemId != 900163 ) {
					os.writeC(17);
					// os.writeC(addsp() + enchantSp);
					os.writeC(enchantSp);
					}
				}
				if (itemId == 800110) {
					final ClassArmorBonus bonus = item._cha == null ? ClassArmorBonus.MELEE
							: item._cha.getEquipSlot().getClassArmorBonus(item);
					os.writeAddAc(8);
					switch (bonus) {
					case MAGIC -> {
						os.writeAddSP(2);
						os.writeMagicHIT(2);
					}
					case BOW -> {
						os.writeLongHIT(2);
						os.writeLongDMG(2);
					}
					case MELEE -> {
						os.writeShortHIT(2);
						os.writeShortDMG(2);
					}
					}
					os.writeDMGdown(2);
					os.writeafear_resis(2);
				}
				// 마나 흡수
			if (itemId == 126 || itemId == 127) {
				os.writeC(16);
			}

				// 피 흡수
				if (itemId == 12 || itemId == 601 || itemId == 1123 || itemId == 202013) {
					os.writeC(34);
				}

				/** 벨트 5이상 대미지 리덕션 **/
				if (itemType2 == 2 && (item.getItem().getType() == 10 && item.getEnchantLevel() >= 5)) {// 벨트
					os.writeDMGdown(item.getEnchantLevel() - 4);
				}
				/** 룸티스의 붉은빛 귀걸이 대미지 리덕션 **/
				if (item.getItemId() == 22229 && item.getEnchantLevel() > 2) {
					switch (item.getEnchantLevel()) {
					case 3:
					case 4:
						os.writeDMGdown(item.getItem().get_damage_reduction() + 1);
						break;
					case 5:
						os.writeDMGdownprobability(2, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 2);
						break;
					case 6:
						os.writeDMGdownprobability(3, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 3);
						break;
					case 7:
						os.writeDMGdownprobability(4, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 4);
						os.writeShortHIT(1);
						os.writeLongHIT(1);
						break;
					case 8:
						os.writeDMGdownprobability(5, 25);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 7);
						os.writeShortHIT(3);
						os.writeLongHIT(3);
						break;
					case 9:
						os.writeDMGdownprobability(7, 25);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 7);
						os.writeShortHIT(5);
						os.writeLongHIT(5);
						break;
					default:
						break;
					}
				}
				if (item.getItemId() == 22228) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(7);
						break;
					case 6:
						os.writeability_resis(5);
						os.writeAddMR(7);
						break;
					case 7:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+1");
						os.writeability_resis(7);
						os.writeAddMR(7);
						break;
					case 8:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+4");
						os.writeability_resis(9);
						os.writeafear_resis(1);
						os.writeAddMR(9);
						break;
					case 9:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+7");
						os.writeability_resis(9);
						os.writeafear_resis(2);
						os.writeAddMR(12);
						break;
					default:
						break;
					}
				}
				if (item.getItemId() == 222334) {
					switch (item.getEnchantLevel()) {
					case 6:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+1");
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+3");
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+5");
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+9");
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					default:
						break;
					}
				}
				/** 축복받은 룸티스의 붉은빛 귀걸이 대미지 리덕션 **/
				if (itemId == 222337 && item.getEnchantLevel() > 2) {
					switch (item.getEnchantLevel()) {
					case 3:
						os.writeDMGdown(item.getItem().get_damage_reduction() + 1);
						break;
					case 4:
						os.writeDMGdownprobability(2, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 2);
						break;
					case 5:
						os.writeDMGdownprobability(3, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 3);
						break;
					case 6:
						os.writeDMGdownprobability(4, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 4);
						os.writeShortHIT(1);
						os.writeLongHIT(1);
						break;
					case 7:
						os.writeDMGdownprobability(5, 20);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 5);
						os.writeShortHIT(3);
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeDMGdownprobability(6, 30);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 7);
						os.writeShortHIT(5);
						os.writeLongHIT(5);
						break;
					case 9:
						os.writeDMGdownprobability(8, 30);
						os.writeDMGdown(item.getItem().get_damage_reduction() + 9);
						os.writeShortHIT(7);
						os.writeLongHIT(7);
						break;
					default:
						break;
					}
				}

				if (itemId == 988171) {
					switch (item.getEnchantLevel()) {
					case 0:

					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeAddMaxHP(50);
						os.writeMaxMP(20);
						os.writeAddMR(20);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+2%");						
						os.writeAddAc(5);
						os.writePVPAddDMG(1);
						break;
					case 8:
						os.writeAddMaxHP(60);
						os.writeMaxMP(30);
						os.writeAddMR(21);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+3%");						
						os.writeAddAc(6);
						os.writePVPAddDMG(2);
						break;
					case 9:
						os.writeAddMaxHP(70);
						os.writeMaxMP(40);
						os.writeAddMR(22);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+4%");						
						os.writeAddAc(7);
						os.writePVPAddDMG(3);
						break;
					case 10:
						os.writeAddMaxHP(80);
						os.writeMaxMP(50);
						os.writeAddMR(23);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+5%");						
						os.writeAddAc(8);
						os.writePVPAddDMG(4); 
						break;
					default:

					}
				}
				/** 실프의티셔츠 **/
				if (itemId == 900019) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 1:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 2:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 3:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 4:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 5:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 6:
						os.writeAddMR(13);
						os.writeDMGdown(2);
						os.writeaAll_resis(2);
						os.writeability_resis(11);
						break;
					case 7:
						os.writeAddMR(14);
						os.writeDMGdown(3);
						os.writeaAll_resis(3);
						os.writeability_resis(11);
						break;
					case 8:
						os.writeAddMR(15);
						os.writeDMGdown(4);
						os.writeaAll_resis(4);
						os.writeability_resis(11);
						break;
					case 9:
						os.writeAddMR(16);
						os.writeDMGdown(5);
						os.writeaAll_resis(5);
						os.writeability_resis(11);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(17);
						os.writeDMGdown(6);
						os.writeaAll_resis(6);
						os.writeability_resis(11);
						break;
					default:
						break;
					}
				}
				
//				//21주년
				if (itemId == 474700) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMR(1);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 1:
						os.writeAddMR(3);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 2:
						os.writeAddMR(5);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 3:
						os.writeAddMR(7);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 4:
						os.writeAddMR(9);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 5:
						os.writeAddMR(11);
						os.writeafear_resis(6);
						os.writeability_resis(6);
						break;
					case 6:
						os.writeAddMR(13);
						os.writeafear_resis(7);
						os.writeability_resis(7);
						break;
					case 7:
						os.writeAddMR(15);
						os.writeafear_resis(8);
						os.writeability_resis(8);
						break;
					case 8:
						os.writeAddMR(17);
						os.writeafear_resis(9);
						os.writeability_resis(9);
						break;
					case 9:
						os.writeAddMR(19);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 10:
						os.writeAddMR(21);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 11:
						os.writeAddMR(23);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 12:
						os.writeAddMR(25);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
//				default:
//				if (getEnchantLevel() > 12 ){
//						os.writeAddMR(((getEnchantLevel() - 12) * 2) + 25);
//						os.writeafear_resis(10);
//						os.writeability_resis(10);
//					}
//						break;
					}
				}

				
				if (itemId == 565611) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeafear_resis(1);
						os.writeaspirit_resis(1);
						os.writeadragonS_resis(1);
						os.writeability_resis(12);
						break;
					default:
						if (item.getEnchantLevel() > 5) {
							os.writeAddMR(item.getEnchantLevel() - 5 + 12);
							os.writeDMGdown(item.getEnchantLevel() - 5 + 1);
							os.writeafear_resis(item.getEnchantLevel() - 5 + 1);
							os.writeaspirit_resis(item.getEnchantLevel() - 5 + 1);
							os.writeadragonS_resis(item.getEnchantLevel() - 5 + 1);
							os.writeability_resis(item.getEnchantLevel() - 5 + 12);
							os.writePVPAddDMG(item.getEnchantLevel() - 5);
						}
						break;
					}
				}

				/** 수호의 가더 대미지 리덕션 **/
				if (itemId == 22254) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeDMGdown(1);
						break;
					case 5:
					case 6:
						os.writeDMGdown(2);
						break;
					case 7:
					case 8:
						os.writeDMGdown(3);
						break;
					case 9:
					case 10:
						os.writeDMGdown(4);
						break;
					default:
						break;
					}
				}
				/** 유니콘의 완력 각반 **/
				if (itemId == 900030) {
					if (item.getEnchantLevel() >= 9)
						os.writeShortDMG(1);
				}
				/** 유니콘의 민첩 각반 **/
				if (itemId == 900031) {
					if (item.getEnchantLevel() >= 9)
						os.writeLongDMG(1);
				}
				/** 유니콘의 지식 각반 **/
				if (itemId == 900032) {
					if (item.getEnchantLevel() >= 9)
						os.writeAddSP(1);
				}
				/** 나이트발드의 양손검 스턴레벨+1 **/
				if (itemId == 59 || itemId == 617  || itemId == 11121) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 15)
						os.writeability_pierce(5);
				}
				/** 악몽의 장궁 정령 적중 **/
				if (itemId == 622 || itemId == 1136) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 15)
						os.writeaspirit_pierce(3);
				}
				/** 섬멸자의 체인소드 용언 적중 **/
				if (itemId == 203017 || itemId == 618) {
				switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeadragonS_pierce(1);
						break;
					case 8:
						os.writeadragonS_pierce(2);
						break;
					case 9:
						os.writeadragonS_pierce(3);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_pierce(4);
						break;
					default:
						break;
						}
				}
				/** 악마왕의 도끼**/
				if (itemId == 147) {
					if (item.getEnchantLevel() >= 1 && item.getEnchantLevel() <= 15)
					os.writeShortDMG(item.getEnchantLevel());
				}
			
		
			
				/** 아르카의 유물 **/
				if (itemId == 900033 || itemId == 9961) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(2);
						break;
					default:
						break;
					}
				}

				if (itemId == 555000 || itemId == 666000) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(2);
						break;
					default:
						break;
					}
				}
				if (itemId == 555001 || itemId == 555002 || itemId == 555003 || itemId == 666001 || itemId == 666002
						|| itemId == 666003) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(10);
						break;
					default:
						break;
					}
				}
				if (itemId == 700117) {
					
					
					
					os.writeAddMR(item.getEnchantLevel() * 2 + 3);
					os.writeAddSP(item.getEnchantLevel() + 2);
					os.writePVPAddDMG(item.getEnchantLevel() + 2);
					os.writePVPAddDMGdown(item.getEnchantLevel() + 2);
					os.writeAddEXP(item.getEnchantLevel() + 1);
					os.writeMagicHIT(item.getEnchantLevel()+ 1);
					
					if (item.getEnchantLevel() >= 5) {
					os.writeaAll_pierce(item.getEnchantLevel() - 4);
					os.writeaAll_resis(item.getEnchantLevel() - 4);
					}
					
				} 
				if (itemId == 900116) {
					if(item.getEnchantLevel() <= 5 ) {
						os.writeAddMaxHP(100);
						os.writeAddMR(10);
						os.writePVPAddDMG(3);
						os.writePVPAddDMGdown(1);
						os.writeAddEXP(5);
					} else if(item.getEnchantLevel()== 6 ) {
						os.writeAddMaxHP(120);
						os.writeAddMR(12);
						os.writePVPAddDMG(4);
						os.writePVPAddDMGdown(2);
						os.writeAddEXP(6);
					} else if(item.getEnchantLevel()== 7 ) {
						os.writeAddMaxHP(130);
						os.writeAddMR(13);
						os.writePVPAddDMG(5);
						os.writePVPAddDMGdown(3);
						os.writeAddEXP(8);
					} else if(item.getEnchantLevel()== 8 ) {
						os.writeAddMaxHP(150);
						os.writeAddMR(15);
						os.writePVPAddDMG(6);
						os.writePVPAddDMGdown(4);
						os.writeAddEXP(10);
					}

				}
				if (itemId == 900024) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeMagicHIT(3);
				}
				if (itemId == 900114) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeMagicHIT(2);
				}
				if (itemId == 541) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeAddStunHit(10);
				}
				if (itemId == 542) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeDMGdown(2);
						os.writeReductiondown(12);
						break;
					default:
						break;
					}
				}
				if (itemId == 543) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeShortDMG(2);
						break;
					default:
						break;
					}
				}
				if (itemId == 544) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeShortDMG(10);
						break;
					default:
						break;
					}
				}
				if (itemId == 545) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeShortDMG(17);
						break;
					default:
						break;
					}
				}
				if (itemId == 547) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeShortDMG(11);
						break;
					default:
						break;
					}
				}
				/** 축복받은 제로스의 지팡이 **/
				if (itemId == 620) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeMagicCritical(5);
						break;
					default:
						break;
					}
				}
				if (itemId == 7000223 || itemId == 505015) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeMagicCritical(1);
						break;
					case 7:
						os.writeadragonS_pierce(1);
						os.writeMagicCritical(2);
						break;
					case 8:
						os.writeadragonS_pierce(2);
						os.writeMagicCritical(3);
						break;
					case 9:
						os.writeadragonS_pierce(3);
						os.writeMagicCritical(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_pierce(4);
						os.writeMagicCritical(5);
						break;
					default:
						break;
					}
				}

				/** 태풍의 도끼 공포적중+1 **/
				if (itemId == 203006 || itemId == 616) {
					switch (item.getEnchantLevel()) {
					case 8:
						os.writeafear_pierce(1);
						break;
					case 9:
						os.writeafear_pierce(2);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeafear_pierce(3);
						break;
					default:
						break;
					}
				}

				if (itemId == 14061 || itemId == 14012 || itemId == 2202013 
						|| itemId == 2202014 || itemId == 14086 ) {
					os.writeDMGdown(1);					
				}
				if (itemId == 14134 || itemId == 2202012 ) {
					os.writeDMGdown(2);					
				}
				if (itemId == 61 || itemId == 14061) {
					if (item.getEnchantLevel() > 0) {
						os.writeability_pierce(8 + item.getEnchantLevel());
						os.writeShortCritical(1 + item.getEnchantLevel());
					} else {
						os.writeability_pierce(8);
						os.writeShortCritical(1);
					}
					
				}
				
				if (itemId == 12 || itemId == 14012) {
					if (item.getEnchantLevel() > 0) {
						os.writeaAll_pierce(item.getEnchantLevel());
					} 
				}
				
				if (itemId == 134 || itemId == 14134) {
					if (item.getEnchantLevel() > 0) {
						os.writeAddSP(7 + item.getEnchantLevel());
						os.writeMagicHIT(2 + item.getEnchantLevel());
					} else {
						os.writeAddSP(7);
						os.writeMagicHIT(2);
					}
				}
				
				if (itemId == 86 || itemId == 14086) {
					if (item.getEnchantLevel() > 0) {
						os.writeaspirit_pierce(5 + item.getEnchantLevel());
						os.writeC(39);
						os.writeS("\\fI더블 대미지 발동 확률 증가");
					} else {
						os.writeaspirit_pierce(5);
						os.writeC(39);
						os.writeS("\\fI더블 대미지 발동 확률 증가");
					}
				}
				if (itemId == 202011 || itemId == 2202011) {
					if (item.getEnchantLevel() > 0) {
						os.writeaspirit_pierce(5 + item.getEnchantLevel());
						os.writeReductiondown(9+ item.getEnchantLevel());
						os.writeLongCritical(1 + item.getEnchantLevel());
						os.writeDMGdown(2);
					} else {
						os.writeaspirit_pierce(5);
						os.writeLongCritical(1);
						os.writeReductiondown(9);
						os.writeDMGdown(2);
					}
				}
				
				if (itemId == 202013 || itemId == 2202013) {
					if (item.getEnchantLevel() > 0) {
						os.writeShortCritical(1 + item.getEnchantLevel());
						os.writeadragonS_pierce(7 + item.getEnchantLevel());
					}else {
						os.writeShortCritical(1);
						os.writeadragonS_pierce(7);
					}
				}
				
				
				if (itemId == 202012 || itemId == 2202012) {
					if (item.getEnchantLevel() > 0) {
						os.writeAddSP(2 + item.getEnchantLevel());
						os.writeadragonS_pierce(7 + item.getEnchantLevel());
						os.writeMagicCritical(1 + item.getEnchantLevel());
					}else {
						os.writeAddSP(2);
						os.writeadragonS_pierce(7);
						os.writeMagicCritical(1);
					}
					
				}
				
			if (itemId == 202014 || itemId == 2202014) {
				if (item.getEnchantLevel() > 0) {
					os.writeShortCritical(1 + item.getEnchantLevel());
					os.writeafear_pierce(3 + item.getEnchantLevel());
					os.writeaTitan(5);
				}else {
					os.writeShortCritical(1);
					os.writeafear_pierce(3);
					os.writeaTitan(5);
				}
			}
				/** 고대의 가호 타입 **/
				if (itemId == 900022) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeC(39);
						os.writeS("\\fI효과:\\aA착용중 사망시");
						os.writeC(39);
						os.writeS("\\aA패널티없음");
						break;
					default:
						break;
					}
				}
	
				/** 신성한 엘름의 축복 **/
				if (itemId == 900035 || itemId == 900072 && item.getEnchantLevel() >= 5) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeC(15);
						os.writeH(item.getMr() + 4);
						break;
					case 6:
						os.writeC(15);
						os.writeH(item.getMr() + 8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(item.getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(item.getMr() + 16);
						break;
					case 9:
						os.writeC(15);
						os.writeH(item.getMr() + 20);
						break;
					case 10:
						os.writeC(15);
						os.writeH(item.getMr() + 24);
						break;
					default:
						break;
					}
				}
				/** 흑기사의 면갑 **/
				if (itemId == 900038 || itemId == 900054 && item.getEnchantLevel() >= 5) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeC(15);
						os.writeH(item.getMr() + 4);
						break;
					case 6:
						os.writeC(15);
						os.writeH(item.getMr() + 8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(item.getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(item.getMr() + 16);
						break;
					case 9:
						os.writeC(15);
						os.writeH(item.getMr() + 20);
						break;
					case 10:
						os.writeC(15);
						os.writeH(item.getMr() + 24);
						break;
					default:
						break;
					}
				}
				/** 신성한 완력의 목걸이 **/
				if (itemId == 900039) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeShortHIT(2);
				}
				/** 신성한 민첩의 목걸이 **/
				if (itemId == 900040) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeLongHIT(2);
				}
				/** 신성한 지식의 목걸이 **/
				if (itemId == 900041) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 10)
						os.writeMagicHIT(2);
				}
				/** 수호성의 파워 글로브 **/
				if (itemId == 900036 || itemId == 900073 && item.getEnchantLevel() >= 5) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeShortHIT(2);
						break;
					case 7:
						os.writeShortHIT(3);
						break;
					case 8:
						os.writeShortHIT(4);
						break;
					case 9:
						os.writeShortHIT(5);
						break;
					case 10:
						os.writeShortHIT(5);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeShortHIT(5);
						}
						break;
					}
				}
				/** 수호성의 활 골무 **/
				if (itemId == 900037 || itemId == 900074 && item.getEnchantLevel() >= 5) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongHIT(2);
						break;
					case 7:
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeLongHIT(4);
						break;
					case 9:
						os.writeLongHIT(5);
						break;
					case 10:
						os.writeLongHIT(5);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeLongHIT(5);
						}
						break;
					}
				}
				/** 격분의 장갑 **/
				if (itemId == 222317) {
					switch (item.getEnchantLevel()) {
					case 7:
						os.writeShortHIT(4);
						break;
					case 8:
						os.writeShortHIT(5);
						break;
					case 9:
						os.writeShortHIT(6);
						break;
					case 10:
						os.writeShortHIT(7);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeShortHIT(item.getEnchantLevel() - 3);
						}
						break;
					}
				}

				if (itemId == 22214) {

					if (item.getEnchantLevel() > 6) {
						os.writeMagicHIT(item.getEnchantLevel() - 6);

					}
				}

				if (itemId == 20279) {

					os.writePVPAddDMGdown(2);

				}
				if (itemId == 20314) {

					os.writePVPAddDMG(2);

				}

				/** 머미로드의 왕관 **/
				if (itemId == 20017) {
					int cc = item.getEnchantLevel() - 4;
					if (item.getEnchantLevel() > 4) {
						os.writeLongDMG(cc);
						os.writeAddMR(cc * 3);

					}
				}
				/** 체력의 각반 **/
				if (itemId == 900023) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(10);
						break;
					case 1:
						os.writeAddMaxHP(15);
						break;
					case 2:
						os.writeAddMaxHP(20);
						break;
					case 3:
						os.writeAddMaxHP(25);
						break;
					case 4:
						os.writeAddMaxHP(30);
						break;
					case 5:
						os.writeAddMaxHP(35);
						break;
					case 6:
						os.writeAddMaxHP(40);
						break;
					case 7:
						os.writeAddMaxHP(45);
						break;
					case 8:
						os.writeAddMaxHP(50);
						break;
					case 9:
						os.writeAddMaxHP(55);
						break;
					default:
						break;
					}
				}
				/** 예언자의 견갑 **/
				if (itemId == 900080) {
					if (item.getEnchantLevel() >= 0 && item.getEnchantLevel() <= 15)
						os.writeMagicHIT(1);
				}

				/** 맘보 모자 **/
				if (itemId == 20016 || itemId == 120016 || itemId == 20112 || itemId == 120112) {
					switch (item.getEnchantLevel()) {
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaCHA_Bu(1);
						break;
					default:
						break;
					}
				}
				/** 투사의 휘장 **/
				if (itemId == 900152) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortDMG(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortDMG(2);
						os.writeShortCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortDMG(3);
						os.writeShortCritical(3);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortDMG(4);
						os.writeShortCritical(5);
						break;
					default:
						break;
					}
				}
				/** 명궁의 휘장 **/
				if (itemId == 900153) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeLongDMG(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeLongDMG(2);
						os.writeLongCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeLongDMG(3);
						os.writeLongCritical(3);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeLongDMG(4);
						os.writeLongCritical(5);
						break;
					default:
						break;
					}
				}
				if (itemId == 123001) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeability_pierce(2);
						os.writeaspirit_pierce(2);
						os.writeafear_pierce(2);
						break;
					case 9:
						os.writeability_pierce(3);
						os.writeaspirit_pierce(3);
						os.writeafear_pierce(3);
						break;
					case 10:
						os.writeability_pierce(4);
						os.writeaspirit_pierce(4);
						os.writeafear_pierce(4);
						break;
					case 11:
						os.writeability_pierce(5);
						os.writeaspirit_pierce(5);
						os.writeafear_pierce(5);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeability_pierce(5);
							os.writeaspirit_pierce(5);
							os.writeafear_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123002) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeability_pierce(5);
						break;
					case 8:
						os.writeability_pierce(6);
						break;
					case 9:
						os.writeability_pierce(7);
						break;
					case 10:
						os.writeability_pierce(8);
						break;
					case 11:
						os.writeability_pierce(8);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeability_pierce(8);
						}
						break;
					}
				}
				if (itemId == 123003) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+2");
						break;
					case 9:
						os.writeaspirit_pierce(1);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+3");
						break;
					case 10:
						os.writeaspirit_pierce(2);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+4");
						break;
					case 11:
						os.writeaspirit_pierce(3);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+5");
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeaspirit_pierce(3);
							os.write(0x27);
							os.writeS("\\f9리덕션 무시: \\aA+5");
						}
						break;
					}
				}
				if (itemId == 123004) {
					os.write(0x27);
					os.writeS("\\f9더블 발동 확률 증가");
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeaspirit_pierce(2);
						break;
					case 9:
						os.writeaspirit_pierce(3);
						break;
					case 10:
						os.writeaspirit_pierce(4);
						break;
					case 11:
						os.writeaspirit_pierce(5);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeaspirit_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123005) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeMagicHIT(1);
						break;
					case 9:
						os.writeMagicHIT(2);
						break;
					case 10:
						os.writeMagicHIT(3);
						break;
					case 11:
						os.writeMagicHIT(4);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeMagicHIT(4);
						}
						break;
					}
				}

				if (itemId == 123006) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeafear_pierce(2);
						break;
					case 9:
						os.writeafear_pierce(3);
						break;
					case 10:
						os.writeafear_pierce(4);
						break;
					case 11:
						os.writeafear_pierce(5);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeafear_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123007) {
					os.write(0x27);
					os.writeS("\\f9약점 노출 확률 증가");
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeadragonS_pierce(5);
						break;
					case 9:
						os.writeadragonS_pierce(6);
						break;
					case 10:
						os.writeadragonS_pierce(7);
						break;
					case 11:
						os.writeadragonS_pierce(8);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeadragonS_pierce(8);
						}
						break;
					}
				}
				if (itemId == 123008) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeadragonS_pierce(5);
						os.writeAddSP(1);
						break;
					case 9:
						os.writeadragonS_pierce(5);
						os.writeAddSP(2);
						break;
					case 10:
						os.writeadragonS_pierce(5);
						os.writeAddSP(3);
						break;
					case 11:
						os.writeadragonS_pierce(5);
						os.writeAddSP(4);
						break;
					default:
						if (item.getEnchantLevel() > 11) {
							os.writeadragonS_pierce(5);
							os.writeAddSP(4);
						}
						break;
					}
				}
				
				if (itemId == 808011) {
					 os.writeC(39);
					 os.writeS("\\fv인챈: 안전 +0/최대 +5");
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMR(12);
						
						break;
					case 1:
						os.writeAddMR(26);
						os.writeAddAc(6);
						break;
					case 2:
						os.writeAddMR(28);
						os.writeAddAc(6);
						os.writeAddMaxHP(20);
						os.writeMaxMP(10);
						break;
					case 3:
						os.writeAddMR(30);
						os.writeAddAc(6);
						os.writeAddMaxHP(40);
						os.writeMaxMP(20);
						os.writeaAll_resis(3);
						break;
					case 4:
						os.writeAddMR(32);
						os.writeAddAc(6);
						os.writeAddMaxHP(60);
						os.writeMaxMP(30);
						os.writeaAll_resis(4);
						break;
					case 5:
						os.writeAddMR(34);
						os.writeAddAc(6);
						os.writeAddMaxHP(80);
						os.writeMaxMP(40);
						os.writeaAll_resis(5);
						break;
				
					default:
						if (item.getEnchantLevel() > 5) {
							os.writeAddMR(34);
							os.writeAddAc(6);
							os.writeAddMaxHP(80);
							os.writeMaxMP(40);
							os.writeaAll_resis(5);
						}
						break;
					}
				}
				// 할파스의 마력

				if (itemId == 123111) {
					//os.write(0x27);
					//os.writeS("\\f9대미지 리덕션 무시: \\aA+5");
					os.writeReductiondown(5);
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeMagicCritical(5);
						os.writeDMGdown(7);
						break;
					case 1:
						os.writeMagicCritical(5);
						os.writeDMGdown(9);
						break;
					case 2:
						os.writeMagicCritical(5);
						os.writeDMGdown(11);
						break;
					case 3:
						os.writeMagicCritical(5);
						os.writeDMGdown(13);
						break;
					case 4:
						os.writeMagicCritical(5);
						os.writeDMGdown(15);
						break;
					case 5:
						os.writeMagicCritical(5);
						os.writeDMGdown(17);
						break;
					case 6:
						os.writeMagicCritical(5);
						os.writeDMGdown(19);
						break;
					case 7:
						os.writeMagicCritical(5);
						os.writeDMGdown(21);
						break;
					case 8:
						os.writeMagicCritical(5);
						os.writeDMGdown(23);
						break;
					case 9:
						os.writeMagicCritical(5);
						os.writeDMGdown(25);
						break;
					case 10:
						os.writeMagicCritical(5);
						os.writeDMGdown(27);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeMagicCritical(5);
							os.writeDMGdown(item.getEnchantLevel() * 2 + 7);
						}
						break;
					}
				}
				// 할파스의 예지력
				if (itemId == 123112) {
					//os.write(0x27);
					//os.writeS("\\f9대미지 리덕션 무시: \\aA+5");
					os.writeReductiondown(5);
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeLongCritical(5);
						os.writeDMGdown(7);
						break;
					case 1:
						os.writeLongCritical(5);
						os.writeDMGdown(9);
						break;
					case 2:
						os.writeLongCritical(5);
						os.writeDMGdown(11);
						break;
					case 3:
						os.writeLongCritical(5);
						os.writeDMGdown(13);
						break;
					case 4:
						os.writeLongCritical(5);
						os.writeDMGdown(15);
						break;
					case 5:
						os.writeLongCritical(5);
						os.writeDMGdown(17);
						break;
					case 6:
						os.writeLongCritical(5);
						os.writeDMGdown(19);
						break;
					case 7:
						os.writeLongCritical(5);
						os.writeDMGdown(21);
						break;
					case 8:
						os.writeLongCritical(5);
						os.writeDMGdown(23);
						break;
					case 9:
						os.writeLongCritical(5);
						os.writeDMGdown(25);
						break;
					case 10:
						os.writeLongCritical(5);
						os.writeDMGdown(27);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeLongCritical(5);
							os.writeDMGdown(item.getEnchantLevel() * 2 + 7);
						}
						break;
					}
				}

				/** 현자의 휘장 **/
				if (itemId == 900154) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortHIT(2);
						os.writeMagicCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortHIT(3);
						os.writeMagicCritical(2);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortHIT(4);
						os.writeMagicCritical(4);
						break;
					default:
						break;
					}
				}
				/** 커츠의 투사 휘장 **/
				if (itemId == 900081) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortDMG(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortDMG(2);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeShortHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortDMG(3);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeShortHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortDMG(4);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeShortHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeShortDMG(5);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeShortHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 명궁 휘장 **/
				if (itemId == 900082) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeLongDMG(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeLongDMG(2);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeLongHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeLongDMG(3);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeLongHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeLongDMG(4);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeLongHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeLongDMG(5);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeLongHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 현자 휘장 **/
				if (itemId == 900083) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortHIT(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeShortHIT(2);
						os.writeMagicCritical(1);
						os.writeMagicHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeShortHIT(3);
						os.writeMagicCritical(2);
						os.writeMagicHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeShortHIT(4);
						os.writeMagicCritical(3);
						os.writeMagicHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeShortHIT(5);
						os.writeMagicCritical(4);
						os.writeMagicHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 수호 휘장 **/
				if (itemId == 900084) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeDMGdown(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						break;
					default:
						break;
					}
				}
				/** 대마법사의 모자 **/
				if (itemId == 202022) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 4:
					case 5:
						os.writeaspirit_resis(2);
						break;
					case 6:
						os.writeaspirit_resis(3);
						break;
					case 7:
						os.writeaspirit_resis(4);
						break;
					case 8:
						os.writeaspirit_resis(5);
						break;
					case 9:
						os.writeaspirit_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(7);
						break;
					default:
						break;
					}
				}
				if (itemId == 20050) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+1");
						break;
					case 6:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+2");
						break;
					case 7:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+3");
						break;
					case 8:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+4");
						break;
					case 9:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+5");
					
						break;
						
					default:
						if (item.getEnchantLevel() > 9) {
							os.write(0x27);
							os.writeS("\\f9ER: \\aA+5");
						}
						break;
					}
				}
				/** 거대여왕 개미의 금빛,은빛 날개 **/
				if (itemId == 20049 || itemId == 20050 || itemId == 900056 || itemId == 900057) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeaspirit_resis(3);
						break;
					case 6:
						os.writeaspirit_resis(4);
						break;
					case 7:
						os.writeaspirit_resis(5);
						break;
					case 8:
						os.writeaspirit_resis(6);
						break;
					case 9:
						os.writeaspirit_resis(7);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(7);
						break;
					default:
						break;
					}
				}
				/** 안타라스의 완력/예지력/인내력/마력 **/
				if (itemId >= 22196 && itemId <= 22199) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(2);
						os.writeDMGdown(3);
						break;
					case 6:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(3);
						os.writeDMGdown(3);
						break;
					case 7:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(4);
						os.writeDMGdown(4);
						break;
					case 8:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(5);
						os.writeDMGdown(5);
						break;
					case 9:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(6);
						os.writeDMGdown(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(7);
						os.writeDMGdown(6);
						break;
					default:
						break;
					}
				}
				/** 파푸리온의 완력/예지력/인내력/마력 **/
				if (itemId >= 22200 && itemId <= 22203) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeadragonS_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}
				/** 린드비오르의 완력/예지력/인내력/마력 **/
				if (itemId >= 22204 && itemId <= 22207) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeadragonS_resis(2);
						break;
					case 5:
						os.writeadragonS_resis(3);
						break;
					case 6:
						os.writeadragonS_resis(4);
						break;
					case 7:
						os.writeadragonS_resis(5);
						break;
					case 8:
						os.writeadragonS_resis(6);
						break;
					case 9:
						os.writeadragonS_resis(7);
						break;
					case 10:
						os.writeadragonS_resis(8);
						break;
					default:
						if (item.getEnchantLevel() > 10) {
							os.writeadragonS_resis(9);
						}
						break;
					}
				}
				/** 발라카스의 완력 **/
				if (itemId >= 22208 && itemId <= 22211) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeReductiondown(3);
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeReductiondown(3);
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeReductiondown(4);
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeReductiondown(5);
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeReductiondown(6);
						os.writeadragonS_resis(6);
						break;
					case 10:
						os.writeReductiondown(7);
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}

			
				/** 에바의 방패 **/
				if (itemId == 20235) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeadragonS_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}
				/** 시어의 심안 **/
				if (itemId == 22214) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeaspirit_resis(3);
						break;
					case 6:
						os.writeaspirit_resis(4);
						break;
					case 7:
						os.writeaspirit_resis(5);
						break;
					case 8:
						os.writeaspirit_resis(6);
						break;
					case 9:
						os.writeaspirit_resis(7);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(8);
						break;
					default:
						break;
					}
				}
				/** 리치의 반지 **/
				if (itemId == 900163) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeAddSP(3);
						os.writeMagicCritical(3);
						break;
					case 7:
						os.writeAddSP(4);
						os.writeMagicCritical(3);
						break;
					case 8:
						os.writeAddSP(5);
						os.writeMagicCritical(3);
						break;
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(6);
						os.writeMagicCritical(3);
						break;
					default:
						break;
					}
				}

				/** 뱀파이어의 망토 **/
				if (itemId == 20079) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeafear_resis(2);
						break;
					case 6:
						os.writeafear_resis(3);
						break;
					case 7:
						os.writeafear_resis(4);
						break;
					case 8:
						os.writeafear_resis(5);
						break;
					case 9:
						os.writeafear_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeafear_resis(7);
						break;
					default:
						break;
					}
				}
				/** 아이리스의 부츠 **/
				if (itemId == 900155) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}

				/** 머미로드의 장갑 **/
				if (itemId == 900156) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeAddSP(1);
						break;
					case 7:
						os.writeAddSP(2);
						break;
					case 8:
						os.writeAddSP(3);
						break;
					case 9:
						os.writeAddSP(3);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						break;
					default:
						break;
					}
				}
				/** 쿠거의 가더 **/
				if (itemId == 900157) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortHIT(3);
						break;
					case 5:
						os.writeShortHIT(4);
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeShortHIT(5);
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeShortHIT(6);
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeShortHIT(7);
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeShortHIT(8);
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortHIT(8);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}
				/** 우그누스의 가더 **/
				if (itemId == 900158) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongHIT(3);
						break;
					case 5:
						os.writeLongHIT(4);
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeLongHIT(5);
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeLongHIT(6);
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeLongHIT(7);
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeLongHIT(8);
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongHIT(8);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}
				/** 나이트발드의 각반 **/
				if (itemId == 900159) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeShortHIT(2);
						break;
					case 7:
						os.writeShortHIT(3);
						break;
					case 8:
						os.writeShortHIT(4);
						break;
					case 9:
						os.writeShortHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortHIT(5);
						break;
					default:
						break;
					}
				}
				/** 아이리스의 각반 **/
				if (itemId == 900160) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongHIT(2);
						break;
					case 7:
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeLongHIT(4);
						break;
					case 9:
						os.writeLongHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongHIT(5);
						break;
					default:
						break;
					}
				}
				/** 뱀파이어의 각반 **/
				if (itemId == 900161) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeMagicHIT(1);
						break;
					case 6:
						os.writeMagicHIT(2);
						break;
					case 7:
						os.writeMagicHIT(3);
						break;
					case 8:
						os.writeMagicHIT(4);
						break;
					case 9:
						os.writeMagicHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeMagicHIT(5);
						break;
					default:
						break;
					}
				}
				/** 지룡의 티셔츠 **/
				if (itemId == 900025) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(4);
						os.writeDMGdown(1);
						break;
					case 6:
						os.writeAddMR(5);
						os.writeDMGdown(1);
						break;
					case 7:
						os.writeAddMR(6);
						os.writeDMGdown(1);
						break;
					case 8:
						os.writeAddMR(8);
						os.writeAddEXP(2);
						os.writeDMGdown(1);
						break;
					case 9:
						os.writeAddMR(11);
						os.writeAddEXP(4);
						os.writeDMGdown(2);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(14);
						os.writeAddEXP(6);
						os.writeDMGdown(2);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 화룡의 티셔츠 **/
				if (itemId == 900026) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortDMG(1);
						break;
					case 5:
						os.writeShortDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeShortDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeShortDMG(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeShortDMG(1);
						os.writeShortHIT(2);
						os.writeability_resis(12);
						break;
					case 9:
						os.writeShortDMG(2);
						os.writeShortHIT(4);
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortDMG(2);
						os.writeShortHIT(6);
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 풍룡의티셔츠 **/
				if (itemId == 900027) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeLongDMG(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeLongDMG(1);
						os.writeLongHIT(2);
						os.writeability_resis(12);
						break;
					case 9:
						os.writeLongDMG(2);
						os.writeLongHIT(4);
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongDMG(2);
						os.writeLongHIT(6);
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 수룡의 티셔츠 **/
				if (itemId == 900028) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeAddSP(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeAddSP(2);
						os.writeMagicHIT(1);// 마법 적중
						os.writeability_resis(12);
						break;
					case 9:
						os.writeAddSP(2);
						os.writeMagicHIT(3);// 마법 적중
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						os.writeMagicHIT(4);// 마법 적중
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}

				/** 축복받은 지룡의 티셔츠 **/
				if (itemId == 900184) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeAddMR(4);
						break;
					case 6:
						os.writeAddMR(5);
						break;
					case 7:
						os.writeAddMR(6);
						os.writeDMGdown(1);
						os.writeAddEXP(2);
						break;
					case 8:
						os.writeAddMR(8);
						os.writeDMGdown(1);
						os.writeAddEXP(4);
						break;
					case 9:
						os.writeAddMR(11);
						os.writeDMGdown(2);
						os.writeAddEXP(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(14);
						os.writeDMGdown(2);
						os.writeAddMaxHP(100);
						os.writeAddEXP(8);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 화룡의티셔츠 **/
				if (itemId == 900185) {
					switch (item.getEnchantLevel()) {
					case 5:
						os.writeability_resis(8);
						break;
					case 6:
						os.writeability_resis(9);
						break;
					case 7:
						os.writeShortDMG(1);
						os.writeShortHIT(1);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeShortDMG(1);
						os.writeShortHIT(3);
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeShortDMG(2);
						os.writeShortHIT(5);
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortDMG(2);
						os.writeShortHIT(7);
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 풍룡의티셔츠 **/
				if (itemId == 900186) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeLongDMG(1);
						os.writeLongHIT(1);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeLongDMG(1);
						os.writeLongHIT(3);
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeLongDMG(2);
						os.writeLongHIT(5);
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongDMG(2);
						os.writeLongHIT(7);
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 수룡의 티셔츠 **/
				if (itemId == 900187) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeAddSP(2);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeAddSP(2);
						os.writeMagicHIT(2);// 마법 적중
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeAddSP(2);
						os.writeMagicHIT(4);// 마법 적중
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						os.writeMagicHIT(5);// 마법 적중
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}

				/** 고대투사의가더 근거리 대미지 **/
				if (itemId == 22003) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortDMG(1);
						break;
					case 5:
					case 6:
						os.writeShortDMG(2);
						break;
					case 7:
					case 8:
						os.writeShortDMG(3);
						break;
					case 9:
					case 10:
						os.writeShortDMG(4);
						break;
					default:
						break;
					}
				}

				/** 고대명궁의가더 원거리 대미지 **/
				if (itemId == 22000) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
					case 6:
						os.writeLongDMG(2);
						break;
					case 7:
					case 8:
						os.writeLongDMG(3);
						break;
					case 9:
					case 10:
						os.writeLongDMG(4);
						break;
					default:
						break;
					}
				}

				if (itemId == 20051) {
					switch (item.getEnchantLevel()) {
					case 0:
						os.writeC(15);
						os.writeH(item.getMr() + 4);
						break;
					case 1:
						os.writeC(15);
						os.writeH(item.getMr() + 6);
						break;
					default:
						if (item.getEnchantLevel() > 1) {
							os.writeC(15);
							os.writeH(item.getMr() + item.getEnchantLevel() * 2 + 4);
						}
						break;
					}
				}
				if (itemId == 22360) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeC(15);
						os.writeH(1);
						break;

					default:
						if (item.getEnchantLevel() > 4) {
							os.writeC(15);
							os.writeH(((item.getEnchantLevel() - 4) * 2 )+ 1);
						}
						break;
					}
				}
				
				if (itemId == 900007 || itemId == 500012) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 2);
						break;
					case 6:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 3);
						break;
					case 7:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 5);
						break;
					case 8:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 7);
						break;
					case 9:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 9);
						break;
					case 10:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 11);
						break;
					}
				}

				/** 벨트 PVP 데미지 감소 **/
				if (item.getEnchantLevel() >= 6 && itemType2 == 2 && item.getItem().get장신구처리() == 10) {
					if (!(itemId == 900007 || itemId == 500012)) {
					
					switch (item.getEnchantLevel()) {
					case 6:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 1);
						break;
					case 7:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 3);
						break;
					case 8:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 5);
						break;
					case 9:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 7);
						break;
					case 10:
						os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense() + 9);
						break;
					}
					}
				}

				/** PVP 추가 데미지 **/
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (item.getItem().get장신구처리() == 9 || item.getItem().get장신구처리() == 11)) {
					switch (item.getEnchantLevel()) {
					case 6:
						os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage() + 1);

						break;
					case 7:
						os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage() + 2);

						break;
					case 8:
						os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage() + 3);

						break;
					case 9:
						os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage() + 5);

						break;
					default:
						if (item.getEnchantLevel() > 9) {
							os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage() + 7);
						}
						break;
					}
				}
				
				/** 빛나는 마력의 장갑 **/
				
				if (itemId == 20274) {
					switch (item.getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddWeight(300);
						break;
					default:
						if (item.getEnchantLevel() > 4) {
							os.writeAddWeight(300 + ((item.getEnchantLevel() - 4) * 60));
						}
						break;
					}
				}
				
				if (itemId == 900090) {
					os.writeDMGdownprobability(item.getEnchantLevel(), 20);
				}
				if (itemId == 22263) {
					os.writeDMGdownprobability(item.getEnchantLevel() * 2, 50);
					if (item.getEnchantLevel() > 4) {
						os.writeAddMaxHP((item.getEnchantLevel() - 4) * 20);
					}
				}
				if (itemId == 900046 || itemId == 900071) {
					os.writeDMGdownprobability(item.getEnchantLevel(), 10);
				}
				if (itemId == 22359 || itemId == 222308 || itemId == 222309 || itemId == 222307) {
					switch (item.getEnchantLevel()) {
					case 7:
						os.writeAddMaxHP(20);
						break;
					case 8:
						os.writeAddMaxHP(40);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeDMGdown(1);
						break;
					case 10:
						os.writeAddMaxHP(60);
						os.writeDMGdown(1);
						break;
					default:
						break;
					}
				}
				
				// TODO 스냅퍼의 반지류 기술 내성 추가
				if (itemId == 22225 || itemId == 222331 || itemId == 22227 || itemId == 222333 || itemId == 22224
						|| itemId == 222330 || itemId == 222291 || itemId == 222336) {
					switch (item.getEnchantLevel()) {
					case 6:
						os.writeability_resis(5);
						break;
					case 7:
						os.writeability_resis(7);
						break;
					case 8:
						os.writeability_resis(9);
						if (itemId == 222291) {
							os.writeafear_resis(1);
						} else if (itemId == 222336) {
							os.writeafear_resis(2);
						}
						break;
					case 9:
						os.writeability_resis(9);
						if (itemId == 222291) {
							os.writeafear_resis(2);
						} else if (itemId == 222336) {
							os.writeafear_resis(4);
						}
						break;
					
					}
				}
				// TODO 축복받은 스냅퍼의 체력 반지 리뉴얼
				if (itemId == 222332) {
					switch (item.getEnchantLevel()) {
					case 6:
						os.writeDMGdown(1);
						os.writeDMGdownprobability(1, 20);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeDMGdown(2);
						os.writeDMGdownprobability(2, 20);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeDMGdown(5);
						os.writeDMGdownprobability(3, 20);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeDMGdown(7);
						os.writeDMGdownprobability(5, 20);
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					}
				}
				
		        
				// TODO 스냅퍼의 체력 반지 리뉴얼
				if (itemId == 22226) {
					switch (item.getEnchantLevel()) {
					case 6:
						os.writeability_resis(5);
						break;
					case 7:
						os.writeDMGdown(1);
						os.writeDMGdownprobability(1, 20);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeDMGdown(3);
						os.writeDMGdownprobability(2, 20);
						os.writeability_resis(9);
						os.writeafear_resis(1);
						break;
					case 9:
						os.writeDMGdown(5);
						os.writeDMGdownprobability(4, 20);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					}
				}

				// 디비 아이템 추가 옵션 부분
				if (item.getItem().getHitModifier() != 0) { // 무기에 붙는 명중
					if (itemType2 == 1 && item.getItem().getType1() != 20) {
						os.writeShortHIT(item.getItem().getHitModifier());
					} else {
						os.writeLongHIT(item.getItem().getHitModifier());
					}
				}
				if (item.getItem().getHitRate() != 0) { // 근거리 명중
					os.writeShortHIT(item.getItem().getHitRate());
				} else {
					os.writeShortHIT(0);
				}
				if (item.getItem().getDmgRate() != 0) { // 근거리 데미지
					os.writeShortDMG(item.getItem().getDmgRate());
				}
				if (item.getItem().getBowHitRate() != 0) { // 원거리 명중
					os.writeLongHIT(item.getItem().getBowHitRate());
				}
				if (item.getItem().getBowDmgRate() != 0) { // 원거리 대미지
					os.writeLongDMG(item.getItem().getBowDmgRate());
				}
				// STR~CHA
				if (item.getItem().get_addstr() != 0) { // 스탯 힘
					os.writeaSTR_Bu(item.getItem().get_addstr());
				}
				if (item.getItem().get_adddex() != 0) { // 스탯 덱스
					os.writeaDEX_Bu(item.getItem().get_adddex());
				}
				if (item.getItem().get_addcon() != 0) { // 스탯 콘
					os.writeaCON_Bu(item.getItem().get_addcon());
				}
				if (item.getItem().get_addwis() != 0) { // 스탯 위즈
					os.writeaWIS_Bu(item.getItem().get_addwis());
				}
				if (item.getItem().get_addint() != 0) { // 스탯 인트
					os.writeaINT_Bu(item.getItem().get_addint());
				}
				if (item.getItem().get_addcha() != 0) { // 스탯 카리
					os.writeaCHA_Bu(item.getItem().get_addcha());
				}
				if (item.getItem().get_addeinhasadper() != 0) { // 축복 소모 효율
					os.writeaBlesssomo(item.getItem().get_addeinhasadper());
				}
				if (item.getItem().getMinLevel() != 0) { // 최소 사용 레벨
					os.writeUseLevel(item.getItem().getMinLevel());
				}
				if (item.getItem().getMaxLevel() != 0) { // 최소~최대 사용 레벨
					os.writeLimitLevel(item.getItem().getMinLevel(), item.getItem().getMaxLevel());
				}
				if (item.addsp() != 0) { // sp
					os.writeAddSP(item.addsp());
				}
				if (item.getItem().isHasteItem()) { // 헤이스트 아이템
					os.writeC(39);
					os.writeS("\\fI효과: \\aA헤이스트");
				}
				if (item.getItem().get_damage_reduction() != 0) { // 대미지 리덕션
					os.writeDMGdown(item.getItem().get_damage_reduction());
				}
				if (item.getItem().getWeightReduction() != 0) { // 무게 게이지
					os.writeAddWeight(item.getItem().getWeightReduction());
				}
				if (item.getItem().get_regist_PVPweaponTotalDamage() != 0) { // PVP 대미지
					os.writePVPAddDMG(item.getItem().get_regist_PVPweaponTotalDamage());
				}
				if (item.getItem().get_regist_calcPcDefense() != 0) { // PVP 리덕션
					os.writePVPAddDMGdown(item.getItem().get_regist_calcPcDefense());
				}
				if (item.getItem().get_melee_critical_probability() > 0) { // 근거리 치명타
					os.writeShortCritical(item.getItem().get_melee_critical_probability());
				}
				if (item.getItem().get_missile_critical_probability() > 0) { // 원거리
																		// 치명타
					os.writeLongCritical(item.getItem().get_missile_critical_probability());
				}
				if (item.getItem().getMagicHitup() != 0) { // 마법 적중
					os.writeMagicHIT(item.getItem().getMagicHitup());
				}

				// 불의 속성
				if (item.getItem().get_defense_fire() != 0) {
					os.writeRegistFire(item.getItem().get_defense_fire());
				}
				// 물의 속성
				if (item.getItem().get_defense_water() != 0) {
					os.writeRegistWater(item.getItem().get_defense_water());
				}
				// 바람 속성
				if (item.getItem().get_defense_wind() != 0) {
					os.writeRegistWind(item.getItem().get_defense_wind());
				}
				// 땅의 속성
				if (item.getItem().get_defense_earth() != 0) {
					os.writeRegistEarth(item.getItem().get_defense_earth());
				}
				
				if (item.getItem().getMagicName() != null) {
				    os.writeC(74);
				    os.writeS(item.getItem().getMagicName());
				}

				if (itemType2 == 2) {
					int pierce = item.getItem().getSpecialStats().getPierce(eKind.ABILITY);
					int lvl = item.getEnchantLevel();
					if (itemId == 22263 && item.getEnchantLevel() > 4) {
						int tempval = (lvl - 4);
						pierce += tempval;
					}

					if (pierce > 0) {
						os.writeC(122);
						os.writeC(pierce);
					}

					pierce = item.getItem().getSpecialStats().getPierce(eKind.SPIRIT);
					if (pierce > 0) {
						os.writeC(123);
						os.writeC(pierce);
					}

					pierce = item.getItem().getSpecialStats().getPierce(eKind.DRAGON_SPELL);
					if (pierce > 0) {
						os.writeC(124);
						os.writeC(pierce);
					}

					pierce = item.getItem().getSpecialStats().getPierce(eKind.FEAR);
					if (pierce > 0) {
						os.writeC(125);
						os.writeC(pierce);
					}

					pierce = item.getItem().getSpecialStats().getPierce(eKind.ALL);
					if (pierce > 0) {
						os.writeC(126);
						os.writeC(pierce);
					}
				}

				// 정령내성
				int resistance = item.getItem().getSpecialStats().getResist(eKind.SPIRIT);
				if (resistance > 0) {
					os.writeC(118);
					os.writeC(resistance);
				}

				// 용언내성
				resistance = item.getItem().getSpecialStats().getResist(eKind.DRAGON_SPELL);
				if (resistance > 0) {
					os.writeC(119);
					os.writeC(resistance);
				}

				// 공포내성
				resistance = item.getItem().getSpecialStats().getResist(eKind.FEAR);
				if ( itemType2 == 2 && (item.getItem().get장신구처리() == 12)) {
					int tempval = 0;
					if (item.getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (itemType2 == 2 && (item.getItem().get장신구처리() == 8)) {
					int tempval = 0;
					if (item.getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (itemType2 == 2 && (item.getItem().get장신구처리() == 10)) {
					int tempval = 0;
					if (item.getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (resistance > 0) {
					os.writeC(120);
					os.writeC(resistance);
				}

				// 전체내성
				resistance = item.getItem().getSpecialStats().getResist(eKind.ALL);
				
				if ( itemType2 == 2 && (item.getItem().get장신구처리() == 12)) {
					int tempval = 0;
					if (item.getEnchantLevel() >= 10) 
						tempval = 1;
					resistance += tempval;
				}
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& item.getItem().get장신구처리() == 9 || item.getItem().get장신구처리() == 11) {
					int tempval = 0;
					if (item.getEnchantLevel() >= 10 ) {
						tempval = 1;
						resistance += tempval;
					}
				}
				if (resistance > 0) {
					os.writeC(121);
					os.writeC(resistance);
				}

				// 기술내성
				resistance = item.getItem().getSpecialStats().getResist(eKind.ABILITY);
				int lvl = item.getEnchantLevel();

				if (item.getEnchantLevel() >= 7 && itemType2 == 2
						&& (item.getItem().get장신구처리() == 8 || item.getItem().get장신구처리() == 12)) {
					int tempval = (lvl - 5);
					if (lvl >= 10) {
						tempval = 4;
					} 
					resistance += tempval;
				}
				if (itemId == 22360 && item.getEnchantLevel() > 4) {
					int tempval = (lvl - 4);
					resistance += tempval;
				}

				if (resistance > 0) {
					os.writeC(117);
					os.writeC(resistance);
				}


				if (item.getItem().getType2() == 1 && item.get_bless_level() != 0) {//sgmall
					int type = item.getItem().getType();
					if (item.getItem().getType2() == 1 ) { 
					if (type == 7 || type == 16 || type == 17) {
						os.writeC(39);
						os.writeS("축복" + item.get_bless_level() + "단: SP +" + item.get_bless_level());
					} else {
						os.writeC(39);
						os.writeS("축복" + item.get_bless_level() + "단: DMG +" + item.get_bless_level());
					}
					} 
					if (item.getItem().getType2() == 2 ) { 
						if (item.get_bless_level() == 1) {
							os.writeC(39); 
							os.writeS("\\aL최대HP : 30");
						} else if (item.get_bless_level() == 2) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 20");
						} else if (item.get_bless_level() == 3) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 10");
						} else if (item.get_bless_level() == 4) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 30");
						} else if (item.get_bless_level() == 5) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 40");
						} else if (item.get_bless_level() == 6) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 50");
						} else if (item.get_bless_level() == 7) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +1");
						} else if (item.get_bless_level() == 8) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +2");
						} else if (item.get_bless_level() == 10) {
							os.writeC(39);
							os.writeS("\\aL기술적중 : +1");
						} else if (item.get_bless_level() == 11) {
							os.writeC(39);
							os.writeS("\\aL공포적중 : +1");
						} else if (item.get_bless_level() == 12) {
							os.writeC(39);
							os.writeS("\\aL용언적중 : +1");
						} else if (item.get_bless_level() == 13) {
							os.writeC(39);
							os.writeS("\\aL정령적중 : +1");
						} else if (item.get_bless_level() == 14) {
							os.writeC(39);
							os.writeS("\\aL마법적중 : +1");
						} else if (item.get_bless_level() == 15) {
							os.writeC(39);
							os.writeS("\\aL기술내성 : +1");
						} else if (item.get_bless_level() == 16) {
							os.writeC(39);
							os.writeS("\\aL공포내성 : +1");
						} else if (item.get_bless_level() == 17) {
							os.writeC(39);
							os.writeS("\\aL용언내성 : +1");
						} else if (item.get_bless_level() == 18) {
							os.writeC(39);
							os.writeS("\\aL정령내성 : +1");
						} else if (item.get_bless_level() == 19) {
							os.writeC(39);
							os.writeS("\\aL근거리대미지 : +1");
						} else if (item.get_bless_level() == 20) {
							os.writeC(39);
							os.writeS("\\aL원거리대미지 : +1");
						} else if (item.get_bless_level() == 21) {
							os.writeC(39);
							os.writeS("\\aL근거리명중 : +1");
						} else if (item.get_bless_level() == 22) {
							os.writeC(39);
							os.writeS("\\aL원거리명중 : +1");
						} else if (item.get_bless_level() == 23) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +1");
						} else if (item.get_bless_level() == 24) {
							os.writeC(39);
							os.writeS("\\aL추가방어력 : +1");
						} else if (item.get_bless_level() == 25) {
							os.writeC(39);
							os.writeS("\\aLMR : +3");
						} else if (item.get_bless_level() == 26) {
							os.writeC(39);
							os.writeS("\\aLSP : +1");
						}
					}
				}
				if (item.get_bless_level2() != 0) {
					int level = item.get_bless_level2();
					double dmg = (level *1.0)/10; 
					int type = item.getItem().getType();
					if (item.getItem().getType2() == 1 ) { 						
					if (type == 7 || type == 16 || type == 17) {						
						os.writeC(39);
						os.writeS("품질: " + item.get_bless_level2());
						os.writeC(39);
						os.writeS("추댐: "+ dmg +"%");
					} else {
						os.writeC(39);
						os.writeS("품질: " + item.get_bless_level2());
						os.writeC(39);
						os.writeS("추댐: "+ dmg +"%");
					}
					} else {
						os.writeC(39);
						os.writeS("품질: " + item.get_bless_level2());
						os.writeC(39);
						os.writeS("추가HP: "+ item.get_bless_level2());
					}
				}
				/** 특수 인챈트 시스템 **/

				if (item.getItemId() == 20100 || item.getItemId() == 20099 || item.getItemId() == 20150 || item.getItemId() == 22258
						|| item.getItemId() == 20118 || item.getItemId() == 20151 || item.getItemId() == 22301
						|| item.getItemId() == 20091 || L1EquipmentSlot.isSetMainItem(itemId)) { // 세트아이템
					os.writeC(69); // -- 셋트아이템에 대한 표기 ( 고정값 )
					os.writeC(item.get_main_set_armor() ? 1 : 2); // -- 1: 셋트 아이템 다
																// 착용시 /
																// 2: 셋트 아이템 미
																// 착용시
					// os.writeC(71); // -- [ 셋트 보너스 ] //더미값지정 본섭화
					switch (item.getItemId()) {
					case 20100:
						os.writeC(71);
						os.writeH(23929);// 검은 데스나이트
						os.writeAddAc(10);
						os.writeaSTR_Bu(2);
						os.writeShortDMG(2);
						break;
					case 20099:
						os.writeC(71);
						os.writeH(4241);// 데몬
						os.writeAddAc(2);
						os.writeAddHPPrecovery(5);
						break;
					case 22258:
						os.writeAddAc(5);
						os.writeAddMR(15);
						os.writeAddMaxHP(100);
						os.writeAddHPPrecovery(8);
						os.writeAddMPPrecovery(4);
						os.writeRegistWater(20);
						break;
					case 20150:
						os.writeC(71);
						os.writeH(23926);// 커츠
						os.writeAddAc(10);
						os.writeaCON_Bu(2);
						os.writeAddMaxHP(100);
						os.writeDMGdown(2);
						break;
					case 20118:
						os.writeC(71);
						os.writeH(23928);// 켄라우헬
						os.writeAddAc(3);
						os.writeAddMPPrecovery(18);
						break;
					case 20151:
						os.writeC(71);
						os.writeH(23927);// 케레니스
						os.writeaINT_Bu(2);
						os.writeAddMaxHP(100);
						os.writeAddMPPrecovery(18);
						break;
					case 22301:
						os.writeAddAc(3);
						os.writeAddHPPrecovery(4);
						os.writeAddMPPrecovery(1);
						break;
					case 20091:// 강철세트
						os.writeAddAc(3);
						break;
					}
					os.writeC(69); // -- dummy
					os.writeC(0x00); // -- dummy
				}
			
    } finally {
        EquipmentEnchantOptions.releaseAccessoryBonusSnapshot(accessoryBonus);
    }
    }
    private static boolean isSnapperRing(int itemId) {
        return (itemId >= 22224 && itemId <= 22228)
                || itemId == 222290 || itemId == 222291
                || (itemId >= 222330 && itemId <= 222336);
    }

    private static boolean isRoomtisItem(int id) {
        return id == 22229 || id == 22230 || id == 22231
                || id == 222337 || id == 222338 || id == 222339
                || id == 222340 || id == 222341;
    }

}
