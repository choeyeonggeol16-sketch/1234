package l1j.server.server.model.item.assembly;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.stream.PresentationOutputStream;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.eKind;

public class ItemPresentatorFactory {
	private static ConcurrentHashMap<Integer, Presentator> _presentators = new ConcurrentHashMap<Integer, Presentator>(256);
	
	public static Presentator create_presentator(int item_id){
		Presentator presentator = _presentators.get(item_id);
		if(presentator != null)
			return presentator;
//		Presentator presentator = null;
		
		switch(item_id){
		/** 아이템 안전인챈 표시 추가 **/
		
//		case 61: // 진명황의 집행검
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_str(item_template.get_addstr())
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					.write_short_critical(item_template.get_melee_critical_probability() + enchant)
//					.writeAbilityPierce(8 + enchant)
//					//.write_blessed_options(item)
//					.write_time(item)
//					//.write_time(item)
//					.write_weapon_level_options(item)
//				
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.add_melee_critical_rate((enchant) * signed);
//					pc.addSpecialPierce(eKind.ABILITY, (8 + enchant) * signed);
//				}
//			};
//			break;
//		case 12:	// 바람칼날의 단검
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					.write_drain_hp()
//					.write_time(item)
//					//.write_blessed_options(item)
//					
//					.write_weapon_level_options(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//				}
//			};
//			break;
//		case 134:	// 수정결정체 지팡이
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_int(item_template.get_addint())
//					.write_sp(item_template.get_addsp() + enchant)
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					
//					.write_text("\\fI마법 대미지: \\aA+"+enchant+"%")
//					.write_magic_hit(2 + enchant)	// for db input.
//					.write_magic_name(item, item_template.getMagicName())
//				//.write_blessed_options(item)
//				.write_time(item)
//					.write_weapon_level_options(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.getAbility().addSp(enchant * signed);
//					pc.addBaseMagicHitUp((2 + enchant) * signed);
//					pc.sendPackets(new S_SPMR(pc));
//				}
//			};
//			break;
//		case 86:	// 붉은 그림자의 이도류
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_str(item_template.get_addstr())
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
////					.write_magic_name(item_template.getMagicName())
//					.writeSpiritPierce(5 + enchant)
//					//.write_blessed_options(item)
//					.write_time(item)
//					.write_weapon_level_options(item)
//					.write_text("\\fI더블 발동 확률 증가")
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.addSpecialPierce(eKind.SPIRIT, (5 + enchant) * signed);
//				}
//			};
//			break;
//		case 202011:	// 가이아의 격노
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_dex(item_template.get_adddex())
//					.write_addsub_long_damage(item_template.getDmgModifier())
//					.write_long_hit(item_template.getHitModifier() + enchant / 2)
//					.write_damage_reduction(2)	// for db input.
//					.write_ignore_reduction(9 + enchant) // for db input.
//					.write_long_critical(1 + enchant)
////					.write_magic_name(item_template.getMagicName())
//					.writeSpiritPierce(2 + enchant)
//					//.write_blessed_options(item)
//					.write_time(item)
//					.write_weapon_level_options(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.addDamageReductionByArmor(2 * signed);
//					pc.addDamageReductionIgnore((9 + enchant) * signed);
//					pc.add_missile_critical_rate(enchant * signed);
//					pc.addSpecialPierce(eKind.SPIRIT, (2 + enchant) * signed);
//				}
//			};
//			break;
//		case 202013:	// 크로노스의 공포
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_str(item_template.get_addstr())
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_short_critical(1 + enchant)
//					.writeDragonSpellPierce(7 + enchant)
//					//.write_blessed_options(item)
//					.write_weapon_level_options(item)
//					.write_time(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.set_melee_critical_rate(enchant * signed);
//					pc.addSpecialPierce(eKind.DRAGON_SPELL, (7 + enchant) * signed);
//				}
//			};
//			break;
//		case 202012:	// 히페리온의 절망
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_int(item_template.get_addint())
//					.write_sp(item_template.get_addsp() + enchant)
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_max_hp(item_template.get_addhp())
//					.writeDragonSpellPierce(7 + enchant)
//					.write_magic_critical(1 + enchant)	// for db input.
//					.write_magic_name(item, item_template.getMagicName())
//					//.write_blessed_options(item)
//					.write_weapon_level_options(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.addSpecialPierce(eKind.DRAGON_SPELL, (7 + enchant) * signed);
//					pc.getAbility().addSp(enchant * signed);
//					pc.add_magic_critical_rate((1 + enchant) * signed);
//					pc.sendPackets(new S_SPMR(pc));
//				}
//			};
//			break;
//		case 202014:	// 타이탄의 분노
//			presentator = new Presentator(){
//				@Override
//				public byte[] do_presentation(L1ItemInstance item) throws IOException {
//					L1Item item_template = item.getItem();
//					int enchant = item.getEnchantLevel();
//					return PresentationOutputStream.newInstance(256) 
//					.write_weapon_info(item_template, item.getWeight())
//					.write_weapon_add_damage(enchant * 2, enchant * 2)
//					.write_weapon_etc_info(item_template, item.get_durability())
//					.write_str(item_template.get_addstr())
//					.write_short_hit(item_template.getHitModifier() + enchant / 2)
//					.write_addsub_short_damage(item_template.getDmgModifier())
//					.write_short_critical(item_template.get_melee_critical_probability() + enchant)
//					.writeFearPierce(3 + enchant)	// for db input.
//					.write_text("\\fI타이탄: 피 구간 5% 상승")
//					//.write_blessed_options(item)
//					.write_time(item)
//					.write_weapon_level_options(item)
//					//.write_safeenchant(0)//안전인챈 수동표기
//					.toArray();
//				}
//				//TODO 효과적용
//				@Override
//				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){
//					int enchant = item.getEnchantLevel();
//					pc.add_melee_critical_rate(enchant * signed);
//					pc.addSpecialPierce(eKind.FEAR, (3 + enchant) * signed);
//				}
//			};
//			break;
		case 123777:	// 아인하사드의 섬광
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "40+" + (enc * 5) : "+40") + "  명중:+" + (22 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123888:	// 그랑카인의 심판
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "44+" + (enc * 5) : "+44") + "  명중:+" + (18 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123666:	// 에바의 서약 (마법 무기)
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					.write_int(item_template.get_addint())
					.write_wis(item_template.get_addwis())
					.write_sp(item_template.get_addsp())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "25+" + (enc * 5) : "+25") + "  명중:+" + (22 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123800:	// 사이하의 집념 (원거리)
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info1(item_template, item.getWeight())
					.write_weapon_etc_info1(item_template, item.get_durability())
					.write_dex(item_template.get_adddex())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "39+" + (enc * 5) : "+39") + "  명중:+" + (18 + enc + (enc / 2)))
					.write_text("치명타:+" + (10 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123900:	// 실렌의 결의
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					//.write_blessed_options(item)
					.write_drain_hp()
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "36+" + (enc * 5) : "+36") + "  명중:+" + (20 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123555:	// 파아그리오의 공포
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "44+" + (enc * 5) : "+44") + "  명중:+" + (20 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123444:	// 단테스의 시련
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "36+" + (enc * 5) : "+36") + "  명중:+" + (21 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123333:	// 테이아의 혼돈 (마법 무기)
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					.write_int(item_template.get_addint())
					.write_wis(item_template.get_addwis())
					.write_sp(item_template.get_addsp())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "44+" + (enc * 2) : "+44") + "  명중:+" + (enc / 2))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 124111:	// 아우라키아의 초월
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enc = item.getEnchantLevel();
					PresentationOutputStream os = PresentationOutputStream.newInstance(256)
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					//.write_blessed_options(item)
					.write_time(item)
					.write_text("대미지:" + (enc > 0 ? "41+" + (enc * 5) : "+41") + "  명중:+" + (19 + enc + (enc / 2)))
					.write_text("치명타:+" + (7 + (enc * 2)) + "%  이뮨 무시:" + (20 + (enc * 2)) + "%")
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName());
					return os.toArray();
				}
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		case 123999:	// 사신의검
			presentator = new Presentator(){
				@Override
				public byte[] do_presentation(L1ItemInstance item) throws IOException {
					L1Item item_template = item.getItem();
					int enchant = item.getEnchantLevel();
					return PresentationOutputStream.newInstance(256) 
					.write_weapon_info(item_template, item.getWeight())
					.write_weapon_add_damage(enchant * 2, enchant * 2)
					.write_weapon_etc_info(item_template, item.get_durability())
					.write_str(item_template.get_addstr())
					.write_con(item_template.get_addcon())
					.write_short_hit(item_template.getHitModifier() + enchant / 2)
					.write_addsub_short_damage(item_template.getDmgModifier())
					.write_short_critical(item_template.get_melee_critical_probability() + enchant)
					.writeFearPierce(3 + enchant)	// for db input.
					.writeAbilityPierce(3 + enchant)
					.writeSpiritPierce(3 + enchant)
					//.write_blessed_options(item)
					.write_time(item)
					.write_weapon_level_options(item)
					.write_magic_name(item, item_template.getMagicName())
					//.write_safeenchant(0)//안전인챈 수동표기
					.toArray();
				}
				//TODO 효과적용
				@Override
				public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed){}
			};
			break;
		}
		
		
		if(presentator != null){
			_presentators.put(item_id, presentator);
			return presentator;
		}
		return null;
	}
	
	public static byte[] do_presentation(L1ItemInstance item) throws IOException{
		Presentator presentator = create_presentator(item.getItemId());
		if(presentator != null)
			return presentator.do_presentation(item);		
		return null;
	}
	
    public static void applyEquipment(L1PcInstance pc, L1ItemInstance item, boolean equipped) {
        final Presentator presentator = create_presentator(item.getItemId());
        if (presentator != null) presentator.do_equip(pc, item, equipped ? 1 : -1);
    }
}
