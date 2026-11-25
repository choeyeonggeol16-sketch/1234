package l1j.server.server.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import l1j.server.server.BuilderCmdHandler;
import l1j.server.server.command.executor.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Command;

/** Legacy definitions and source compatibility; no separate command map. */
public final class L1Commands {
    private L1Commands() {}

    public static void registerBuilderCommands(Map<String, BuilderCmdPairType> loaded) {
        register(loaded, "감시", "L1Patrol", L1Patrol::getInstance);
        register(loaded, "개인버프", "L1AllBuff", L1AllBuff::getInstance);
        register(loaded, "검사", "L1CheckCharacter", L1CheckCharacter::getInstance);
        register(loaded, "검색", "L1Search1", L1Search1::getInstance);
        register(loaded, "계정압류", "L1AccountBanKick", L1AccountBanKick::getInstance);
        register(loaded, "계정정보", "L1CheckPassword", L1CheckPassword::getInstance);
        register(loaded, "고스폰", "L1ToSpawn", L1ToSpawn::getInstance);
        register(loaded, "공개채금", "L1ChatNG2", L1ChatNG2::getInstance);
        register(loaded, "공격로그", "L1AttackLog", L1AttackLog::getInstance);
        register(loaded, "광역버프", "L1RessBuff", L1RessBuff::getInstance);
        register(loaded, "광역추방", "L1RangeKick", L1RangeKick::getInstance);
        register(loaded, "귀환", "L1GMRoom", L1GMRoom::getInstance);
        register(loaded, "날씨", "L1ChangeWeather", L1ChangeWeather::getInstance);
        register(loaded, "누구", "L1Who", L1Who::getInstance);
        register(loaded, "답장", "L1LetterCommand", L1LetterCommand::getInstance);
        register(loaded, "데무", "L1DescIdWeapon", L1DescIdWeapon::getInstance);
        register(loaded, "데방", "L1DescIdArmor", L1DescIdArmor::getInstance);
        register(loaded, "데스크", "L1DescId", L1DescId::getInstance);
        register(loaded, "디비초기화", "L1DatabaseInit", L1DatabaseInit::getInstance);
        register(loaded, "레벨", "L1Level", L1Level::getInstance);
        register(loaded, "렙선물", "L1LevelPresent", L1LevelPresent::getInstance);
        register(loaded, "로보트", "L1SpawnRobot", L1SpawnRobot::getInstance);
        register(loaded, "로봇", "L1Robot3", null);
        register(loaded, "리로드", "L1Reload", L1Reload::getInstance);
        register(loaded, "리로드트랩", "L1ReloadTrap", L1ReloadTrap::getInstance);
        register(loaded, "리셋트랩", "L1ResetTrap", L1ResetTrap::getInstance);
        register(loaded, "메세지", "L1ServerMessage", L1ServerMessage::getInstance);
        register(loaded, "몬스터", "L1SpawnCmd", L1SpawnCmd::getInstance);
        register(loaded, "무인", "L1FakeCharacter", L1FakeCharacter::getInstance);
        register(loaded, "배치", "L1InsertSpawn", L1InsertSpawn::getInstance);
        register(loaded, "밴아이피", "L1BanIp", L1BanIp::getInstance);
        register(loaded, "버프", "L1Buff", L1Buff::getInstance);
        register(loaded, "버프아이콘", "L1BuffIcon", L1BuffIcon::getInstance);
        register(loaded, "변신", "L1Poly", L1Poly::getInstance);
        register(loaded, "불투명", "L1Visible", L1Visible::getInstance);
        register(loaded, "뻥", "L1UserCalc", null);
        register(loaded, "서먼", "L1Summon", L1Summon::getInstance);
        register(loaded, "선물", "L1Present", L1Present::getInstance);
        register(loaded, "설문", "L1GMQuestion", L1GMQuestion::getInstance);
        register(loaded, "세트아이템", "L1CreateItemSet", L1CreateItemSet::getInstance);
        register(loaded, "셀엔피씨", "L1ImPactGfxid1", L1ImPactGfxid1::getInstance);
        register(loaded, "셀이펙트", "L1ImPactGfxid", L1ImPactGfxid::getInstance);
        register(loaded, "셋팅", "L1Status", L1Status::getInstance);
        register(loaded, "소생", "L1Ress", L1Ress::getInstance);
        register(loaded, "소환", "L1Recall", L1Recall::getInstance);
        register(loaded, "속도", "L1Speed", L1Speed::getInstance);
        register(loaded, "쇼트랩", "L1ShowTrap", L1ShowTrap::getInstance);
        register(loaded, "스레드", "L1Thread", L1Thread::getInstance);
        register(loaded, "스킬", "L1Burf", L1Burf::getInstance);
        register(loaded, "스킬마스터", "L1AddSkill", L1AddSkill::getInstance);
        register(loaded, "스킬아이콘", "L1SkillIcon", L1SkillIcon::getInstance);
        register(loaded, "스폰이벤트", "L1EventSpawn", L1EventSpawn::getInstance);
        register(loaded, "실행", "L1Favorite", L1Favorite::getInstance);
        register(loaded, "아이템삭제", "L1ItemDelete", L1ItemDelete::getInstance);
        register(loaded, "아이피추방", "L1SKick", L1SKick::getInstance);
        register(loaded, "액션", "L1Action", L1Action::getInstance);
        register(loaded, "에코", "L1Echo", L1Echo::getInstance);
        register(loaded, "엔피씨다운", "L1NpcDown", L1NpcDown::getInstance);
        register(loaded, "영구추방", "L1PowerKick", L1PowerKick::getInstance);
        register(loaded, "영자상점1", "L1NpcShopSwitch", L1NpcShopSwitch::getInstance);
        register(loaded, "영자상점2", "L1NpcShopSwitch2", null);
        register(loaded, "영자상점3", "L1NpcShopSwitch3", null);
        register(loaded, "요리아이콘", "L1요리아이콘", null);
        register(loaded, "위치", "L1Loc", L1Loc::getInstance);
        register(loaded, "이동", "L1Move", L1Move::getInstance);
        register(loaded, "이름", "L1ServerName", L1ServerName::getInstance);
        register(loaded, "이미지", "L1GfxId", L1GfxId::getInstance);
        register(loaded, "이펙트", "L1SkillEffect", L1SkillEffect::getInstance);
        register(loaded, "인공지능", "L1Robot4", null);
        register(loaded, "인벤이미지", "L1InvGfxId", L1InvGfxId::getInstance);
        register(loaded, "장사", "L1AdShop", L1AdShop::getInstance);
        register(loaded, "정리", "L1Clear", L1Clear::getInstance);
        register(loaded, "정보", "L1Describe", L1Describe::getInstance);
        register(loaded, "조회", "L1QueryCharacter", L1QueryCharacter::getInstance);
        register(loaded, "종료", "L1Shutdown", L1Shutdown::getInstance);
        register(loaded, "지엠", "L1GM", L1GM::getInstance);
        register(loaded, "찾기", "L1Search", L1Search::getInstance);
        register(loaded, "채금", "L1ChatNG", L1ChatNG::getInstance);
        register(loaded, "채팅", "L1Chat", L1Chat::getInstance);
        register(loaded, "청소", "L1DeleteGroundItem", L1DeleteGroundItem::getInstance);
        register(loaded, "추방", "L1Kick", L1Kick::getInstance);
        register(loaded, "출두", "L1ToPC", L1ToPC::getInstance);
        register(loaded, "킬", "L1Kill", L1Kill::getInstance);
        register(loaded, "투명", "L1Invisible", L1Invisible::getInstance);
        register(loaded, "파티소환", "L1PartyRecall", L1PartyRecall::getInstance);
        register(loaded, "편지삭제", "L1RemoveLetter", L1RemoveLetter::getInstance);
        register(loaded, "피바", "L1HpBar", L1HpBar::getInstance);
        register(loaded, "홈타운", "L1HomeTown", L1HomeTown::getInstance);
    }

    private static void register(Map<String, BuilderCmdPairType> loaded, String name, String className,
                                 Supplier<L1CommandExecutor> factory) {
        if (loaded.putIfAbsent(name.toLowerCase(Locale.ROOT),
                BuilderCmdPairType.legacy(name, 9999, className, factory)) != null) {
            throw new IllegalStateException("Duplicate builder command: " + name);
        }
    }

    public static void reload() { BuilderCmdHandler.LoadBuilderCmdHandler(); }
    public static L1Command get(String name) {
        return BuilderCmdHandler.getInstance().getLegacyCommand(name);
    }
    public static List<L1Command> availableCommandList(int accessLevel) {
        return BuilderCmdHandler.getInstance().availableLegacyCommands(accessLevel);
    }
    public static boolean execute(L1PcInstance pc, L1Command command, String name, String arg) {
        return command != null && command.execute(pc, name, arg);
    }
}
