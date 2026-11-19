package l1j.server.server.datatables;

import static l1j.server.server.model.character.skill.L1SkillId.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;

public class CharBuffTable {
	
	private CharBuffTable() { }

	private static final Logger _log = Logger.getLogger(CharBuffTable.class.getName());

	public static final int[] buffSkill = { 2, 67, // 라이트, 셰이프 체인지
			3, 151, 168, // 쉴드 어스스킨 아이언스킨
			43, 54, 1000, 1001, STATUS_ELFBRAVE, // 헤이 파업, 그레이터 헤이 파업, 치우침 이브 일부, 그린 일부, 엘븐 와퍼
			52, 101, 150, // 호-리 워크, 무빙 악 세레이션, 윈드워크
			26, 42, 109, 110, // PE:DEX, PE:STR, 드레스마이티, 드레스데크스타리티
			114, 115, 117, // 그로윙오라, 샤이닝오라, 치우침 이브 아우라
			148, 155, 163, // 파이아웨폰, 파이어 호흡, 바닝웨폰
			149, 156, 166, // 윈도우 쇼트, 스토무아이, 스톰 쇼트
			1002, // 블루 일부, 채팅 금지
			STATUS_CHAT_PROHIBITED, //채금버프
			4914, 천하장사버프, //흑사버프
			POLY_RING_MASTER, 8135, 8136, 8137,
			
			SIDE_OF_ME_BLESSING, RE_START_BLESSING, NEW_START_BLESSING, LIFE_BLESSING, 혈맹버프AC, 혈맹버프마방, 혈맹버프PVP추가, 혈맹버프PVP감소, 혈맹버프HP증가, 혈맹버프올내성, 혈맹버프올적중, 혈맹버프올스텟, 혈맹버프회복,
			
			/** 버프저장 패킷 차례 */
			DECREASE_WEIGHT, DECAY_POTION, SILENCE, VENOM_RESIST, WEAKNESS, DISEASE, 일루션리치, 일루션골렘, 버닝, 이베이젼, 비욘드, 무인혈맹,
			BERSERKERS, NATURES_TOUCH, WIND_SHACKLE, CYCLONE,
			ERASE_MAGIC, ADDITIONAL_FIRE, ELEMENTAL_FIRE, QUAKE, BLOW_ATTACK,
			STRIKER_GALE, SOUL_OF_FLAME, POLLUTE_WATER, 
			CONCENTRATION, INSIGHT, PANIC, COUNTER_MIRROR,
			MORTAL_BODY, HORROR_OF_DEATH, FEAR, 램페이지, 래스트, 맥시멈, 에볼루션, 아머데스티니, 버서크대기, 포스스턴, 레이징포스, 네메시스, 세인트, 가드피어, 가드피어호러, 솔리드캐리지, 프라이드, 리덕션베테랑, 에테리얼, 어드밴스,
			PATIENCE, GUARD_BREAK, DRAGON_SKIN, 엠파이어오버로드, 샤이닝아머, 마제스티, 기간틱, 카배마스터, 게일샷, lv90, lv91, lv92, lv93, hp300, mp300, ac5, mr15, 경험치10, 모든속성저항20, 힘3, 덱3, 콘3,
			위즈3, 인트3, pvp대미지7, pvp감소7, 근거리대미지5, 원거리대미지5, sp5, 마법적중5, 기술내성8, 정령내성8, 용언내성3, 공포내성3, 모든내성3, 정령적중5, 용언적중5, 공포적중5, 기술적중5, 모든적중3, 경험치보너스20, 대미지감소5, 킬1, 킬2, 킬3, 킬4, 킬5, 엘리멘탈대기, 라우풀고정,
			/** 드래곤 버프 **/
			ANTA_BUFF, FAFU_BUFF, RIND_BUFF, 할파스의신의, 할파스의신의PVP,
			
			/** 요정 전용 버프 */
			RESIST_MAGIC, CLEAR_MIND, RESIST_ELEMENTAL, ELEMENTAL_PROTECTION,
			
			/** 컬러풀 패키지 아이템 및 천상의 물약 */
			EXP_POTION, EXP_BUFF,
			STATUS_BLUE_POTION2, STATUS_FRUIT,
			STATUS_CASHSCROLL, STATUS_CASHSCROLL2, STATUS_CASHSCROLL3, STATUS_CASHSCROLL4, STATUS_CASHSCROLL5, STATUS_CASHSCROLL6, STATUS_DRAGON_PEARL,
						
			/** 요리 1단계 효과 재부여 */
			COOKING_1_0_N, COOKING_1_0_S, COOKING_1_1_N, COOKING_1_1_S,
			COOKING_1_2_N, COOKING_1_2_S, COOKING_1_3_N, COOKING_1_3_S,
			COOKING_1_4_N, COOKING_1_4_S, COOKING_1_5_N, COOKING_1_5_S,
			COOKING_1_6_N, COOKING_1_6_S, 
			
			/** 요리 2단계 효과 재부여 */
			COOKING_1_8_N, COOKING_1_8_S, COOKING_1_9_N, COOKING_1_9_S, 
			COOKING_1_10_N, COOKING_1_10_S, COOKING_1_11_N, COOKING_1_11_S, 
			COOKING_1_12_N, COOKING_1_12_S, COOKING_1_13_N, COOKING_1_13_S,
			COOKING_1_14_N, COOKING_1_14_S, 
			
			/** 요리 3단계 효과 재부여 */
			COOKING_1_16_N, COOKING_1_16_S, COOKING_1_17_N, COOKING_1_17_S,
			COOKING_1_18_N, COOKING_1_18_S, COOKING_1_19_N, COOKING_1_19_S,
			COOKING_1_20_N, COOKING_1_20_S, COOKING_1_21_N, COOKING_1_21_S,
			COOKING_1_22_N, COOKING_1_22_S, 메티스축복주문서, 메티스정성스프, 메티스정성요리,
			
			COMA_A, COMA_B, SetBuff, 나루토감사캔디,
			/** 리뉴얼 요리 */
			COOK_STR, COOK_DEX, COOK_INT, COOK_GROW,
			COOK_DEX_1, COOK_GROW_1, COOK_STR_1, COOK_INT_1, COOK_STR_12,
			
			/** 추가 버프 **/
			레벨업보너스,

			/** 드래곤의 에메랄드 */
			EMERALD_NO, EMERALD_YES, DRAGON_TOPAZ, DRAGON_PUPLE, TOP_RANKER,
			/** 마안 버프 & 운세 버프 **/
			ANTA_MAAN, FAFU_MAAN, VALA_MAAN, LIND_MAAN, BIRTH_MAAN, SHAPE_MAAN, LIFE_MAAN,
			FEATHER_BUFF_A, FEATHER_BUFF_B, FEATHER_BUFF_C, FEATHER_BUFF_D,
			WITCH_MANA_POTION, BUYER_COOLTIME,
			FOCUS_WAVE, HURRICANE, SAND_STORM,
			USER_WANTED, USER_WANTED1, 
			BUFF_특별한성장축복, BUFF_특별한방어축복, BUFF_특별한공격축복
	};

	public static void DeleteBuff(L1PcInstance pc) {
		String sql = "DELETE FROM character_buff WHERE char_obj_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, pc.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "CharBuffTable DeleteBuff error", e);
		}
	}
	
    public static void SaveBuff(L1PcInstance pc) {
        if (pc == null) return;
        final int charId = pc.getId();
        // Capture and job admission share the existing CacheD lock with session
        // publication and economy reservations. A stale/pending logout never
        // builds a snapshot that could erase a newly committed paid buff.
        CacheD.getInstance().submitSessionDbJobOrdered(charId, pc.getCacheDSessionEpoch(), () -> {
            final int[] snapshot = new int[buffSkill.length * 3];
            int count = 0;
            for (int skillId : buffSkill) {
                if (!pc.hasSkillEffect(skillId)) continue;
                snapshot[count++] = skillId;
                snapshot[count++] = pc.getSkillEffectTimeSec(skillId);
                snapshot[count++] = (skillId == SHAPE_CHANGE || skillId == POLY_RING_MASTER)
                        ? pc.getCurrentSpriteId() : 0;
            }
            final int length = count;
            return () -> {
                try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                    con.setAutoCommit(false);
                    try {
                        try (PreparedStatement delete = con.prepareStatement(
                                "DELETE FROM character_buff WHERE char_obj_id = ?")) {
                            delete.setInt(1, charId);
                            delete.executeUpdate();
                        }
                        if (length != 0) {
                            try (PreparedStatement insert = con.prepareStatement(
                                    "INSERT INTO character_buff SET char_obj_id = ?, skill_id = ?, remaining_time = ?, poly_id = ?")) {
                                for (int i = 0; i < length; i += 3) {
                                    insert.setInt(1, charId);
                                    insert.setInt(2, snapshot[i]);
                                    insert.setInt(3, snapshot[i + 1]);
                                    insert.setInt(4, snapshot[i + 2]);
                                    insert.addBatch();
                                }
                                insert.executeBatch();
                            }
                        }
                        con.commit();
                    } catch (SQLException | RuntimeException e) {
                        try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                        throw e;
                    }
                } catch (SQLException e) {
                    _log.log(Level.SEVERE, "CharBuffTable SaveBuff error: " + charId, e);
                    throw new IllegalStateException("Character buffs DB save failed: " + charId, e);
                }
            };
        });
    }
}
