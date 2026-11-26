package l1j.server.server.model.item.assembly;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.doll.DollOption;
import l1j.server.server.model.item.doll.DollPotentialEffects;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.utils.ItemPresentOutStream;

final class DollOptionPresentation {
    private DollOptionPresentation() {}

    static void write(ItemPresentOutStream os, int itemId) {
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

	    os.writeaHPUP(p.hpTick());
	    os.writeaMPUP(p.mpTick());

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

    static void writePotential(L1ItemInstance item, ItemPresentOutStream out) {
        if (Config.잠재력활성화) DollPotentialEffects.writeTooltip(out, item.get_item_level());
    }
}
