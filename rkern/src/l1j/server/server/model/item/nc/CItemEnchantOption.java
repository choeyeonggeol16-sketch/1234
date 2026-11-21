package l1j.server.server.model.item.nc;

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
				        os.writeC(regDef.acLineValue(item.getEnchantLevel()) + op_addAc);
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
}
