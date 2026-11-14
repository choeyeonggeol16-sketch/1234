package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.Config;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNRoom;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;

final class ItemUseTravelAndRecovery {
private static final int[] AUTO_BUFF_BOW = {14, 26, 42, 149, 206, 158, 37};
private static final int[] AUTO_BUFF_MELEE = {14, 26, 42, 48, 206, 158, 37};
private static final int[] LOST_ISLAND_X = {32775, 32787, 32788, 32773};
private static final int[] LOST_ISLAND_Y = {32757, 32755, 32765, 32769};
private static final int[] PIRATE_INNER_XY = {32739, 32740, 32741, 32742};
private static final int[] PIRATE_RETURN_X = {32433, 32434, 32435, 32436};
private static final int[] PIRATE_RETURN_Y = {33052, 33053, 33054, 33055};
private static final int[] LOST_RETURN_X = {33443, 33444, 33437, 33428};
private static final int[] LOST_RETURN_Y = {32804, 32810, 32812, 32808};

    private ItemUseTravelAndRecovery() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
            int[] loc = null;
        switch (itemId) {
		case 3000147:// 테베대항전
            final int tebeX = 32769 + CommonUtil.random(-5, 5);
            final int tebeY = 32832 + CommonUtil.random(-5, 5);
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(tebeX, tebeY, 782, pc.getHeading(), 169, true, pc.getAutoHunt());
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\aA[알림]: \\aG[주의] 강력한 힘을 가지고 있는 몬스터가 있습니다.");
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			});
			break;
		case 202099: {// 클라우디아 마을
			// if (pc.get_DuelLine() != 0) {
			// pc.sendPackets(new SC_SystemMessage("배틀존지역에서 사용할 수 없습니다."));
			// return;
			// }
			// if (pc.getMap().isEscapable() || pc.isGm()) {
			// int[]
			loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_claudia);
			// L1Teleport.teleport(pc, loc[0], loc[1], (short) loc[2],
			// pc.getHeading(), true);
			pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, pc.getAutoHunt());
			// }
		}
		break;
		case 3000229: {// 잊혀진 귀환 주문서
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 1708 && pc.getMapId() <= 1709)) {
				SC_SystemMessage.send(pc, "잊혀진 섬/대기실 에서만 사용 가능합니다.");
				return;
			}
			int[] x = LOST_ISLAND_X;
			int[] y = LOST_ISLAND_Y;
			int type = java.util.concurrent.ThreadLocalRandom.current().nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 1710);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;
		case 875640424:
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 440 && pc.getMapId() <= 441)) {
				SC_SystemMessage.send(pc, "해적섬에서만 사용할 수 있습니다.");
				return;
			}
			int[] xa = PIRATE_INNER_XY;
			int[] ya = PIRATE_INNER_XY;
			int typea = java.util.concurrent.ThreadLocalRandom.current().nextInt(xa.length);
			L1Location basea = new L1Location(xa[typea], ya[typea], 441);
			L1Location randoma = L1Location.randomLocation(basea, 0, 3, true);
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(randoma.getX(), randoma.getY(), randoma.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
			break;
		case 875640415: {// 해적섬 귀환
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 440 && pc.getMapId() <= 441)) {
				SC_SystemMessage.send(pc, "해적섬에서만 사용할 수 있습니다.");
				return;
			}
			int[] x = PIRATE_RETURN_X;
			int[] y = PIRATE_RETURN_Y;
			int type = java.util.concurrent.ThreadLocalRandom.current().nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 440);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;
		case 3000413: {// 강제 귀환 주문서 (잊섬->기란)
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1709) {

			} else {
				SC_SystemMessage.send(pc, "잊혀진 섬 필드 에서만 사용이 가능 합니다.");
				return;
			}
			int[] x = LOST_RETURN_X;
			int[] y = LOST_RETURN_Y;
			int type = java.util.concurrent.ThreadLocalRandom.current().nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 4);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;
		case 41260:// 장착
			for (L1Object object : L1World.getInstance().getVisibleObjects(pc, 3)) {
				if (object instanceof L1EffectInstance) {
					if (((L1NpcInstance) object).getNpcTemplate().get_npcId() == 81170) {
						SC_ServerMessage.send(pc, 1162);
						return;
					}
				}
			}
            ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
                final int[] front = pc.getFrontLoc();
                L1EffectSpawn.getInstance().spawnEffect(81170, 300000,
                        front[0], front[1], pc.getMapId());
            });
			break;
		case 240100:// 저주해진 텔레포트 스크롤(오리지날 아이템)
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, true, pc.getAutoHunt());
				pc.cancelAbsoluteBarrier(); // 아브소르트바리아의 해제
			});
			break;
		case 748: // 잡화상점 이동부적
			pc.start_teleport(33453, 32820, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;
		case 731: {
			if (pc.hasSkillEffect(L1SkillId.TOGO_BUFF)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				SC_InventoryIcon.icoReset(pc, L1SkillId.TOGO_BUFF, 4992, 1800L, true);
				SC_SkillSound.send(pc, pc.getId(), 7470);
				SC_SkillSound.broadcast(pc, pc.getId(), 7470);
			});
		}
		break;
		case 3000397: {// 야히군사 버프주문서
			if (pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(STATUS_CURSE_BARLOG, 1020 * 1000); // 1020
				SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 1, 1020);
				SC_SkillSound.send(pc, pc.getId(), 750);
				SC_SkillSound.broadcast(pc, pc.getId(), 750);
				SC_ServerMessage.send(pc, 1127);
			});
		}
		break;
		case 3000398: {// 발록군사 버프주문서
			if (pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(STATUS_CURSE_YAHEE, 1020 * 1000);
				SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 2, 1020);
				SC_SkillSound.send(pc, pc.getId(), 750);
				SC_SkillSound.broadcast(pc, pc.getId(), 750);
				SC_ServerMessage.send(pc, 1127);
			});
		}
		break;
		case 749: // 장비상점 이동부적
			pc.start_teleport(33435, 32754, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;
		case 650009: {
			if (Config.STANDBY_SERVER) {
				SC_ChatPacket.sendDirect(pc, pc, "오픈대기시간동안은 사용할 수 없습니다.");
				return;
			}
			if (pc.getWeapon() == null) {
				SC_ChatPacket.sendDirect(pc, pc, "무기를 착용하고 사용가능합니다.");
				return;
			}
			final boolean bow = pc.getWeapon().getItem().getType1() == 20;
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
                final int[] skills = bow ? AUTO_BUFF_BOW : AUTO_BUFF_MELEE;
				pc.setBuffnoch(1);
				for (int skill : skills) {
					L1SkillUse.cast(pc, skill, pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_GMBUFF);
				}
				SC_SkillSound.send(pc, pc.getId(), 830);
				pc.curePoison();
			});
			break;
		}
		case 3000128: {// 메티스의 축복주문서
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () ->
				L1SkillUse.cast(pc, L1SkillId.메티스축복주문서, pc.getId(), pc.getX(), pc.getY(), null, 0,
					L1SkillUse.TYPE_GMBUFF));
		}
		break;
		case 4100040:
		case 60208:
		case 60209:
		case 60210:
			IceBuffItem.use(pc, l1iteminstance);
			break;
		case 700021: // 봉인해제 주문서 신청서
			if (pc.getInventory().checkItem(50021)) {
				SC_ChatPacket.sendDirect(pc, pc, "이미 봉인해제 주문서를 갖고 있습니다.");
				return;
			}
			if (pc.getInventory().checkItem(700021, 1)) {
				ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 50021, 15, null);
			}
			break;
		case 700000: { // 경험치 물약
			final int level = pc.getLevel();
			if (level > Config.ExpPo2) {
				pc.sendPackets("" + Config.ExpPo2 + "레벨 이하만 사용이 가능합니다.");
				return;
			}
			final int expReward;
			if (level >= 1 && level <= 48) expReward = 326144;
			else if (level <= 64) expReward = 2609152;
			else if (level <= 69) expReward = 1304576;
			else if (level <= 74) expReward = 652288;
			else if (level <= 78) expReward = 326144;
			else if (level == 79) expReward = 163072;
			else if (level <= 81) expReward = 81536;
			else if (level <= 83) expReward = 40768;
			else if (level <= 85) expReward = 20384;
			else if (level == 86) expReward = 10192;
			else if (level == 87) expReward = 5096;
			else if (level == 88) expReward = 2048;
			else if (level == 89) expReward = 1024;
			else expReward = 512;
			CItemUse.useFixedExpPotion(pc, l1iteminstance, expReward);
			break;
		}
		case 4100074:
			if (pc.getLevel() > Config.ExpPo) {
				pc.sendPackets("" + Config.ExpPo + "레벨 이하만 사용이 가능합니다.");
				return;
			}
			CItemUse.useFixedExpPotion(pc, l1iteminstance, 360650);
			break;
		case 4100075:
			if (pc.getLevel() < Config.ExpPo1) {
				pc.sendPackets("" + Config.ExpPo1 + "레벨 이상만 사용이 가능합니다.");
				return;
			}
			CItemUse.useFixedExpPotion(pc, l1iteminstance, 360650);
			break;
//		case 350198:// 티칼달력
//			if (CrockController.getInstance().isTimeCrock()) {
//				pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "tcalendaro"));
//			} else {
//				pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "tcalendarc"));
//			}
//			break;
		case 40033: // 엘릭서 힘
		case 40034: // 엘릭서 체력
		case 40035: // 엘릭서 민첩
		case 40036: // 엘릭서 지능
		case 40037: // 엘릭서 지혜
		case 40038: // 엘릭서 매력
			ItemUseDurable.UseElixirAtomic(pc, l1iteminstance);
			break;
		case 3000126: // 회복의문장 +3 주머니
		case 3000127: // 성장의문장 +3 주머니
			if (pc.getInventory().getSize() > 120) {
				SC_SystemMessage.send(pc, "소지하고 있는 아이템이 너무 많습니다.");
				return;
			}
			if (pc.getInventory().getWeight100() > 82) {
				SC_SystemMessage.send(pc, "소지품이 너무 무거워서 사용 할 수 없습니다.");
				return;
			}
			ItemUseDurable.UseDurableEnchantedBag(pc, l1iteminstance,
					itemId == 3000126 ? 900021 : 900020, 3);
			break;
		case 3000123: { // 보스소환 주문서
			if (pc.is_combat_field() || StadiumManager.getInstance().is_on_stadium(pc.getMapId())) {
				pc.sendPackets("해당 맵에서는 사용할 수 없습니다.");
				return;
			}

			int castle_id = L1CastleLocation.getCastleIdByArea(pc);
			if (castle_id != 0) {
				pc.sendPackets("공성지역은 소환이 불가능합니다.");
				return;
			}
			if (itemId == 3000123) {
				if (pc.getZoneType() != 0) {// 세이프존
					pc.sendPackets("노멀존(필드) 에서만 사용이 가능합니다.");
					return;
				}
                ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
                    ItemUseDurable.UseMobEventSpawnWand(pc, l1iteminstance,
                            java.util.concurrent.ThreadLocalRandom.current());
                    pc.sendPackets("누군가가 소환되었다.");
                });
            } else {
                pc.sendPackets("노멀 존에서만 사용이 가능합니다.");
            }
		}
		break;
		case 40124:
		case 30086:// 혈맹 귀환 스크롤
			int castle_id = 0;
			int house_id = 0;
			if (pc.getMapId() == 13005) {
				SC_SystemMessage.send(pc, "얌마 막았어 쓸생각하지마~ 메롱.");
				return;
			}	
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1712) {
				SC_ServerMessage.send(pc, 647);
				return;
			}
			if (pc.getMapId() == 2236) {
				SC_SystemMessage.send(pc, "이곳에서 사용할수 없습니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.getClanid() != 0) { // 크란 소속
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					castle_id = clan.getCastleId();
					house_id = clan.getHouseId();
				}
			}
			final boolean clanOwnedReturn = castle_id != 0 || house_id != 0;
			if (castle_id != 0) { // 성주 크란원
				loc = L1CastleLocation.getCastleLoc(castle_id);
			} else if (house_id != 0) { // 아지트 소유 크란원
				loc = L1HouseLocation.getHouseLoc(house_id);
			} else if (pc.getHomeTownId() > 0) {
				loc = L1TownLocation.getGetBackLoc(pc.getHomeTownId());
			} else {
				loc = Getback.GetBack_Location(pc, true);
			}
			if (loc == null || loc.length < 3) return;
			final int returnX = loc[0], returnY = loc[1], returnMap = loc[2];
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(returnX, returnY, returnMap,
					clanOwnedReturn ? pc.getHeading() : 5, 169, true, pc.getAutoHunt());
				if (!clanOwnedReturn) {
					SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				}
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			});
			break;
		case MJINNHelper.INN_KEYID:
			if (pc.isParalyzed() || pc.isSleeped() || pc.isDead()) {
				return;
			}
			if ((pc.hasSkillEffect(L1SkillId.SHOCK_STUN)) || pc.hasSkillEffect(L1SkillId.EMPIRE)
					|| (pc.hasSkillEffect(L1SkillId.ICE_LANCE)) || (pc.hasSkillEffect(L1SkillId.BONE_BREAK))
					|| (pc.hasSkillEffect(L1SkillId.EARTH_BIND) || pc.hasSkillEffect(L1SkillId.DESPERADO))
					|| (pc.hasSkillEffect(L1SkillId.FOG_OF_SLEEPING))) {
				return;
			}
			if (pc.getMapId() == 13005) {
				pc.setCurrentHp(pc.getMaxHp() / 5);
				pc.setCurrentMp(pc.getMaxMp() / 5);
				pc.start_teleport(33431, 32798, 4, 5, 169, true, pc.getAutoHunt()); 

			}	
			MJINNRoom.input(l1iteminstance, pc);
			break;
		case 60256:
			if (pc.getInventory().checkItem(60255)) {
				SC_ServerMessage.send(pc, 939);
				SC_SystemMessage.send(pc, "드래곤의 자수정 보유.");
				return;
			}
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 60255);
			break;
		case 89136: // 특수 슬롯 확장권 (20 -> 40)
			ItemUseDurable.UseSpecialSizeExpansion(pc, l1iteminstance);
			break;
		case 875640417:
			SC_SkillSound.send(pc, pc.getId(), 21174);
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop02");
			break;
		case 875640450:
			SC_SkillSound.send(pc, pc.getId(), 21174);
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop19");
			break;
		case 123124: 
			if (pc.getDollList().size() >= 1) {
				SC_SystemMessage.send(pc, "\\f:인형을 소환해제 후 가능합니다.");
				return;
			}			
			if (pc.getClanid() != 0) {
				SC_ChatPacket.sendDirect(pc, pc, "혈맹을 먼저 탈퇴하여 주시기 바랍니다.");
				return;
			}
			SC_NPCTalkReturn.send(pc, pc.getId(), "change");			
			break; 
		case 12302: 
			SC_NPCTalkReturn.send(pc, pc.getId(), "chang");			
			break; 
		case 210104:
			if (pc.isstop() || pc.hasSkillEffect(L1SkillId.DESPERADO) || pc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {
				SC_SystemMessage.send(pc, "사용할 수 없는 상태입니다.");
				return;
			}
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1710) {
				SC_SystemMessage.send(pc, "여기서는 사용할 수 없습니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약) || pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_SystemMessage.send(pc, "사용할 수 없는 상태입니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}

            final String recoveryAccount = client.getAccountName();
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(recoveryAccount))) {
                if (!tx.debit(l1iteminstance, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    // Never consume a recovery ticket merely because another offline
                    // character on the account was updated: the active character
                    // itself must have an eligible, locked row in this transaction.
                    try (PreparedStatement check = con.prepareStatement(
                            "SELECT objid FROM characters WHERE account_name=? AND objid=? "
                            + "AND MapID NOT IN (38,5001,99,997,5166,39,34,701,2000,1708) FOR UPDATE")) {
                        check.setString(1, recoveryAccount);
                        check.setInt(2, pc.getId());
                        try (ResultSet rows = check.executeQuery()) {
                            if (!rows.next()) throw new SQLException("Recovery character row unavailable");
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET LocX=33432,LocY=32807,MapID=4 "
                            + "WHERE account_name=? AND (OnlineStatus=0 OR objid=?) "
                            + "AND MapID NOT IN (38,5001,99,997,5166,39,34,701,2000,1708)")) {
                        ps.setString(1, recoveryAccount);
                        ps.setInt(2, pc.getId());
                        ps.executeUpdate();
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc,
                        "좌표 복구가 확정되지 않았습니다. 아이템을 확인해 주세요."));
                CDB.RequestUseItem(pc, tx, () -> {
                    // Logout's final snapshot must contain the durable recovery
                    // position; never let the old location overwrite this SQL.
                    pc.setX(33432);pc.setY(32807);pc.setMap((short)4);
                    SC_SystemMessage.send(pc,"좌표 복구를 저장했습니다. 다시 접속해 주세요.");
                    client.kick();
                });
            } catch (RuntimeException failure) {
                SC_SystemMessage.send(pc,"좌표 복구를 저장하지 못했습니다. 아이템과 잔액을 확인해 주세요.");
            }

			break;

		case 700023:
			L1ItemBookMark.useBookmarkItem(pc, l1iteminstance);
			break;
        }
    }
}
