package l1j.server.server.packets.Protocol;

/**
 * 프로토 메시지 ID — 클라와의 와이어 계약.
 * 값은 절대 변경 금지. 추가만 허용.
 *
 * 라우팅 여부 판단은 여기서 하지 않는다 — CSProto 레지스트리가 단일 진실 공급원.
 * (CS 라우팅 가능 여부 → {@link CSProto#isRoutable(int)})
 */
public final class ProtoMsg {
    private ProtoMsg() {}

    // 2019 native Play Support trial (GM only).
    public static final int CS_START_PLAY_SUPPORT_REQ       = 0x0835;
    public static final int SC_START_PLAY_SUPPORT_ACK       = 0x0836;
    public static final int CS_FINISH_PLAY_SUPPORT_REQ      = 0x0837;
    public static final int SC_FINISH_PLAY_SUPPORT_ACK      = 0x0838;
    public static final int SC_FORCE_FINISH_PLAY_SUPPORT_NOTI = 0x0839;
    public static final int CS_PSS_TIME_CHECK_REQ           = 0x083A;
    public static final int SC_PSS_TIME_CHECK_ACK           = 0x083B;
    public static final int CS_CHANGE_PLAY_SUPPORT_SETTING_REQ = 0x083F;

    // ═══════════ SC — 서버 → 클라 ═══════════

    // 지배의 탑 하이브리드 재접속 (GM 테스트 전용)
    public static final int SC_CONNECT_HIBREEDSERVER_NOTI_PACKET = 0x0071;
    public static final int SC_HIBREED_AUTH_ACK_PACKET = 0x0074;

    // 시스템/버전
    public static final int SC_SERVER_VERSION_INFO          = 0x0335;

    // 인벤토리/아이템
    public static final int SC_ADD_INVENTORY_NOTI           = 0x024C;
    public static final int SC_UPDATE_INVENTORY_NOTI        = 0x024D;
    public static final int SC_WAREHOUSE_ITEM_LIST_NOTI     = 0x0408;
    public static final int SC_PERSONAL_SHOP_ITEM_LIST_NOTI = 0x0407;
    public static final int SC_BOX_ATTR_CHANGE_NOTI_PACKET         = 0x0081;

    // 파티
    public static final int SC_PARTY_OPERATION_RESULT_NOTI       = 0x021B;
    public static final int SC_PARTY_MEMBER_LIST            = 0x0337;
    public static final int SC_PARTY_MEMBER_LIST_CHANGE     = 0x0338;
    public static final int SC_PARTY_MEMBER_STATUS          = 0x0339;
    public static final int SC_PARTY_SYNC_PERIODIC_INFO     = 0x033B;
    public static final int SC_PARTY_MEMBER_MARK_CHANGE_NOTI     = 0x0153;

    // 공성
    public static final int SC_SIEGE_ZONE_UPDATE_NOT        = 0x0042;
    public static final int SC_SIEGE_INJURY_TIME_NOIT        = 0x004C;

    // 랭킹
    public static final int SC_MY_RANKING_ACK               = 0x03FE;
    public static final int SC_TOP_RANKER_ACK               = 0x0088;
    public static final int SC_TOP_RANKER_NOTI              = 0x0089;

    // 스킬/버프
    public static final int SC_SPELL_BUFF_NOTI              = 0x006E;
    public static final int SC_ALL_SPELL_PASSIVE_NOTI       = 0x0191;
    public static final int SC_ADD_ACTIVE_SPELL_EX_NOTI     = 0x0192;

    // 유저 정보
    public static final int SC_USER_PLAY_INFO_NOTI          = 0x0323;
    public static final int SC_REST_EXP_INFO_NOTI           = 0x03FC;
    public static final int SC_SPECIAL_RESISTANCE_NOTI      = 0x03F7;
    public static final int SC_WORLD_PUT_OBJECT_NOTI        = 0x0077;

    // 공지/이벤트
    public static final int SC_NOTIFICATION_MESSAGE_NOT    = 0x0040;
    public static final int SC_NOTIFICATION_MESSAGE         = 0x013C;
    public static final int SC_NOTIFICATION_INFO_NOTI       = 0x008D;
    public static final int SC_NOTIFICATION_CHANGE_NOTI     = 0x008E;
    public static final int SC_EVENT_COUNTDOWN_NOTI_PACKET         = 0x021C;
    public static final int SC_FATIGUE_NOTI                 = 0x014E; // 그랑카인

    // 탐
    public static final int SC_ACCOUNT_TAM     = 0x01CD;
    
    // 맵 아이디    
    public static final int SC_MAP_ID = 0x0076;
    // 연금술
    public static final int SC_ALCHEMY_EXTRA_INFO_NOTI      = 0x0080;
    public static final int SC_ALCHEMY_DESIGN_ACK           = 0x007B;
    public static final int SC_ALCHEMY_MAKE_ACK             = 0x007D;
    
    // 소설액션
    public static final int SC_SOCIAL_ACTION_NOTI = 0x0140;
    // 낚시 ui
    public static final int SC_FISHING_NOT = 0x003F;
    // 슬롯 체인지    
    public static final int SC_SLOT_CHANGE = 0x0320;
    // 인형 ON / OFF
    public static final int SC_DOLL_ON_OFF = 0x0911;
    
    // 제작
    public static final int SC_CRAFT_LIST_ALL_ACK           = 0x0037;
    public static final int SC_CRAFT_ID_LIST_ACK            = 0x0039;
    public static final int SC_CRAFT_MAKE_ACK               = 0x003B;
    public static final int SC_CRAFT_BATCH_ACK              = 0x005D;

    // 출석
    public static final int SC_ATTENDANCE_BONUS_INFO_EXTEND = 0x0220;
    public static final int SC_ATTENDANCE_USER_DATA_EXTEND  = 0x0221;
    public static final int SC_ATTENDANCE_BONUS_GROUP_INFO  = 0x0224;
    public static final int SC_ATTENDANCE_DATA_UPDATE_EXTEND = 0x0223;
    public static final int SC_ATTENDANCE_REWARD_ACK        = 0x03EF;
    
    // 텔레포트 ui
    public static final int SC_MAP_TELEPORT_INFO_NOTI = 0x0243;

    // 몬스터북
    public static final int SC_MONSTER_BOOK_V2_INFO_NOTI    = 0x032A;
    public static final int SC_MONSTER_BOOK_V2_REWARD_ACK   = 0x032C;
    public static final int SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI = 0x032D;
    public static final int SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI  = 0x032E;
    public static final int SC_MONSTER_BOOK_V2_TELEPORT_ACK = 0x0330;

    // 업적
    public static final int SC_ADD_COMPLETED_ACHIEVEMENT_BATCH = 0x022F;
    public static final int SC_ADD_CRITERIA_PROGRESS_BATCH  = 0x0230;
    public static final int SC_COMPLETED_ACHIEVEMENT_REWARD_ACK = 0x0234;
    public static final int SC_ACHIEVEMENT_TELEPORT_ACk     = 0x0236;
    public static final int SC_CRITERIA_UPDATE_NOTI         = 0x0237;
    public static final int SC_ACHIEVEMENT_COMPLETE_NOTI    = 0x0238;

    // 컴패니언
    public static final int SC_COMPANION_STATUS_NOTI        = 0x07D0;
    public static final int SC_COMPANION_NAME_CHANGE_ACK    = 0x07D2;
    public static final int SC_COMPANION_BUFF_NOTI          = 0x07D5;
    public static final int SC_COMPANION_SKILL_NOTI         = 0x07D6;
    public static final int SC_COMPANION_SKILL_ENCHANT_ACK  = 0x07D9;
    public static final int SC_COMPANION_COMBAT_DATA_NOTI   = 0x07DA;

    // ═══════════ CS — 클라 → 서버 ═══════════

    // 기존 하이브리드 재접속 인증 요청
    public static final int CS_HIBREED_AUTH_REQ_PACKET = 0x0073;

    // 시스템
    public static final int CS_CLIENT_VERSION_INFO      = 0x0334;
    public static final int CS_USER_PLAY_INFO_REQ           = 0x0322;
    public static final int CS_PARTY_CONTROL_REQ            = 0x033C;

    // 랭킹
    public static final int CS_MY_RANKING_REQ               = 0x03FD;
    public static final int CS_TOP_RANKER_REQ               = 0x0087;

    // 연금술
    public static final int CS_ALCHEMY_DESIGN_REQ           = 0x007A;
    public static final int CS_ALCHEMY_MAKE_REQ             = 0x007C;

    // 제작
    public static final int CS_CRAFT_LIST_ALL_REQ           = 0x0036;
    public static final int CS_CRAFT_ID_LIST_REQ            = 0x0038;
    public static final int CS_CRAFT_MAKE_REQ               = 0x003A;
    public static final int CS_CRAFT_BATCH_REQ              = 0x005C;

    // 출석
    public static final int CS_ATTENDANCE_BONUS_REQUEST_EXTEND = 0x0222;
    public static final int CS_ATTENDANCE_REWARD_REQ        = 0x03EE;

    // 몬스터북
    public static final int CS_MONSTER_BOOK_V2_REWARD_REQ   = 0x032B;
    public static final int CS_MONSTER_BOOK_V2_TELEPORT_REQ = 0x032F;

    // 업적
    public static final int CS_COMPLETED_ACHIEVEMENT_REWARD_REQ = 0x0233;
    public static final int CS_ACHIEVEMENT_TELEPORT_REQ     = 0x0235;

    // 컴패니언
    public static final int CS_COMPANION_NAME_CHANGE_REQ    = 0x07D1;
    public static final int CS_COMPANION_STAT_INCREASE_REQ  = 0x07D3;
    public static final int CS_COMPANION_TM_COMMAND_REQ     = 0x07D4;
    public static final int CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ = 0x07D7;
    public static final int CS_COMPANION_SKILL_ENCHANT_REQ  = 0x07D8;
}
