package l1j.server.server.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import l1j.server.server.command.executor.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Command;

/**
 * Java-owned GM command definitions migrated from the 88-row commands table.
 * Keep original names, level 9999 and executor-class names as metadata.
 * Missing legacy executors remain recorded but are not resurrected.
 */
public final class L1Commands {
    private static final Map<String, L1Command> COMMANDS = createCommands();

    private L1Commands() {}

    private static Map<String, L1Command> createCommands() {
        final Map<String, L1Command> loaded = new LinkedHashMap<>(128);
        register(loaded, "감시", "L1Patrol");
        register(loaded, "개인버프", "L1AllBuff");
        register(loaded, "검사", "L1CheckCharacter");
        register(loaded, "검색", "L1Search1");
        register(loaded, "계정압류", "L1AccountBanKick");
        register(loaded, "계정정보", "L1CheckPassword");
        register(loaded, "고스폰", "L1ToSpawn");
        register(loaded, "공개채금", "L1ChatNG2");
        register(loaded, "공격로그", "L1AttackLog");
        register(loaded, "광역버프", "L1RessBuff");
        register(loaded, "광역추방", "L1RangeKick");
        register(loaded, "귀환", "L1GMRoom");
        register(loaded, "날씨", "L1ChangeWeather");
        register(loaded, "누구", "L1Who");
        register(loaded, "답장", "L1LetterCommand");
        register(loaded, "데무", "L1DescIdWeapon");
        register(loaded, "데방", "L1DescIdArmor");
        register(loaded, "데스크", "L1DescId");
        register(loaded, "디비초기화", "L1DatabaseInit");
        register(loaded, "레벨", "L1Level");
        register(loaded, "렙선물", "L1LevelPresent");
        register(loaded, "로보트", "L1SpawnRobot");
        register(loaded, "로봇", "L1Robot3");
        register(loaded, "리로드", "L1Reload");
        register(loaded, "리로드트랩", "L1ReloadTrap");
        register(loaded, "리셋트랩", "L1ResetTrap");
        register(loaded, "메세지", "L1ServerMessage");
        register(loaded, "몬스터", "L1SpawnCmd");
        register(loaded, "무인", "L1FakeCharacter");
        register(loaded, "배치", "L1InsertSpawn");
        register(loaded, "밴아이피", "L1BanIp");
        register(loaded, "버프", "L1Buff");
        register(loaded, "버프아이콘", "L1BuffIcon");
        register(loaded, "변신", "L1Poly");
        register(loaded, "불투명", "L1Visible");
        register(loaded, "뻥", "L1UserCalc");
        register(loaded, "서먼", "L1Summon");
        register(loaded, "선물", "L1Present");
        register(loaded, "설문", "L1GMQuestion");
        register(loaded, "세트아이템", "L1CreateItemSet");
        register(loaded, "셀엔피씨", "L1ImPactGfxid1");
        register(loaded, "셀이펙트", "L1ImPactGfxid");
        register(loaded, "셋팅", "L1Status");
        register(loaded, "소생", "L1Ress");
        register(loaded, "소환", "L1Recall");
        register(loaded, "속도", "L1Speed");
        register(loaded, "쇼트랩", "L1ShowTrap");
        register(loaded, "스레드", "L1Thread");
        register(loaded, "스킬", "L1Burf");
        register(loaded, "스킬마스터", "L1AddSkill");
        register(loaded, "스킬아이콘", "L1SkillIcon");
        register(loaded, "스폰이벤트", "L1EventSpawn");
        register(loaded, "실행", "L1Favorite");
        register(loaded, "아이템삭제", "L1ItemDelete");
        register(loaded, "아이피추방", "L1SKick");
        register(loaded, "액션", "L1Action");
        register(loaded, "에코", "L1Echo");
        register(loaded, "엔피씨다운", "L1NpcDown");
        register(loaded, "영구추방", "L1PowerKick");
        register(loaded, "영자상점1", "L1NpcShopSwitch");
        register(loaded, "영자상점2", "L1NpcShopSwitch2");
        register(loaded, "영자상점3", "L1NpcShopSwitch3");
        register(loaded, "요리아이콘", "L1요리아이콘");
        register(loaded, "위치", "L1Loc");
        register(loaded, "이동", "L1Move");
        register(loaded, "이름", "L1ServerName");
        register(loaded, "이미지", "L1GfxId");
        register(loaded, "이펙트", "L1SkillEffect");
        register(loaded, "인공지능", "L1Robot4");
        register(loaded, "인벤이미지", "L1InvGfxId");
        register(loaded, "장사", "L1AdShop");
        register(loaded, "정리", "L1Clear");
        register(loaded, "정보", "L1Describe");
        register(loaded, "조회", "L1QueryCharacter");
        register(loaded, "종료", "L1Shutdown");
        register(loaded, "지엠", "L1GM");
        register(loaded, "찾기", "L1Search");
        register(loaded, "채금", "L1ChatNG");
        register(loaded, "채팅", "L1Chat");
        register(loaded, "청소", "L1DeleteGroundItem");
        register(loaded, "추방", "L1Kick");
        register(loaded, "출두", "L1ToPC");
        register(loaded, "킬", "L1Kill");
        register(loaded, "투명", "L1Invisible");
        register(loaded, "파티소환", "L1PartyRecall");
        register(loaded, "편지삭제", "L1RemoveLetter");
        register(loaded, "피바", "L1HpBar");
        register(loaded, "홈타운", "L1HomeTown");
        return Collections.unmodifiableMap(loaded);
    }

    private static void register(Map<String, L1Command> loaded, String name, String className) {
        loaded.put(name.toLowerCase(Locale.ROOT), new L1Command(name, 9999, className));
    }

    /** Compatibility with GameServer startup: all registrations are now static. */
    public static void reload() {
        // Class initialization already published the immutable command map.
    }

    public static L1Command get(String name) {
        return name == null ? null : COMMANDS.get(name.toLowerCase(Locale.ROOT));
    }

    public static List<L1Command> availableCommandList(int accessLevel) {
        final List<L1Command> result = new ArrayList<>();
        for (L1Command command : COMMANDS.values()) {
            if (command.getLevel() <= accessLevel) {
                result.add(command);
            }
        }
        return result;
    }

    /**
     * Preserve one executor creation per call, as in the old reflection path.
     * Unavailable legacy classes return false so GMCommands retains its existing
     * hardcoded fallback. No database access or reflection occurs.
     */
    public static boolean execute(L1PcInstance pc, L1Command command, String name, String arg) {
        if (command == null) return false;

        final L1CommandExecutor executor;
        switch (command.getExecutorClassName()) {
            case "L1Patrol": executor = L1Patrol.getInstance(); break;
            case "L1AllBuff": executor = L1AllBuff.getInstance(); break;
            case "L1CheckCharacter": executor = L1CheckCharacter.getInstance(); break;
            case "L1Search1": executor = L1Search1.getInstance(); break;
            case "L1AccountBanKick": executor = L1AccountBanKick.getInstance(); break;
            case "L1CheckPassword": executor = L1CheckPassword.getInstance(); break;
            case "L1ToSpawn": executor = L1ToSpawn.getInstance(); break;
            case "L1ChatNG2": executor = L1ChatNG2.getInstance(); break;
            case "L1AttackLog": executor = L1AttackLog.getInstance(); break;
            case "L1RessBuff": executor = L1RessBuff.getInstance(); break;
            case "L1RangeKick": executor = L1RangeKick.getInstance(); break;
            case "L1GMRoom": executor = L1GMRoom.getInstance(); break;
            case "L1ChangeWeather": executor = L1ChangeWeather.getInstance(); break;
            case "L1Who": executor = L1Who.getInstance(); break;
            case "L1LetterCommand": executor = L1LetterCommand.getInstance(); break;
            case "L1DescIdWeapon": executor = L1DescIdWeapon.getInstance(); break;
            case "L1DescIdArmor": executor = L1DescIdArmor.getInstance(); break;
            case "L1DescId": executor = L1DescId.getInstance(); break;
            case "L1DatabaseInit": executor = L1DatabaseInit.getInstance(); break;
            case "L1Level": executor = L1Level.getInstance(); break;
            case "L1LevelPresent": executor = L1LevelPresent.getInstance(); break;
            case "L1SpawnRobot": executor = L1SpawnRobot.getInstance(); break;
            case "L1Reload": executor = L1Reload.getInstance(); break;
            case "L1ReloadTrap": executor = L1ReloadTrap.getInstance(); break;
            case "L1ResetTrap": executor = L1ResetTrap.getInstance(); break;
            case "L1ServerMessage": executor = L1ServerMessage.getInstance(); break;
            case "L1SpawnCmd": executor = L1SpawnCmd.getInstance(); break;
            case "L1FakeCharacter": executor = L1FakeCharacter.getInstance(); break;
            case "L1InsertSpawn": executor = L1InsertSpawn.getInstance(); break;
            case "L1BanIp": executor = L1BanIp.getInstance(); break;
            case "L1Buff": executor = L1Buff.getInstance(); break;
            case "L1BuffIcon": executor = L1BuffIcon.getInstance(); break;
            case "L1Poly": executor = L1Poly.getInstance(); break;
            case "L1Visible": executor = L1Visible.getInstance(); break;
            case "L1Summon": executor = L1Summon.getInstance(); break;
            case "L1Present": executor = L1Present.getInstance(); break;
            case "L1GMQuestion": executor = L1GMQuestion.getInstance(); break;
            case "L1CreateItemSet": executor = L1CreateItemSet.getInstance(); break;
            case "L1ImPactGfxid1": executor = L1ImPactGfxid1.getInstance(); break;
            case "L1ImPactGfxid": executor = L1ImPactGfxid.getInstance(); break;
            case "L1Status": executor = L1Status.getInstance(); break;
            case "L1Ress": executor = L1Ress.getInstance(); break;
            case "L1Recall": executor = L1Recall.getInstance(); break;
            case "L1Speed": executor = L1Speed.getInstance(); break;
            case "L1ShowTrap": executor = L1ShowTrap.getInstance(); break;
            case "L1Thread": executor = L1Thread.getInstance(); break;
            case "L1Burf": executor = L1Burf.getInstance(); break;
            case "L1AddSkill": executor = L1AddSkill.getInstance(); break;
            case "L1SkillIcon": executor = L1SkillIcon.getInstance(); break;
            case "L1EventSpawn": executor = L1EventSpawn.getInstance(); break;
            case "L1Favorite": executor = L1Favorite.getInstance(); break;
            case "L1ItemDelete": executor = L1ItemDelete.getInstance(); break;
            case "L1SKick": executor = L1SKick.getInstance(); break;
            case "L1Action": executor = L1Action.getInstance(); break;
            case "L1Echo": executor = L1Echo.getInstance(); break;
            case "L1NpcDown": executor = L1NpcDown.getInstance(); break;
            case "L1PowerKick": executor = L1PowerKick.getInstance(); break;
            case "L1NpcShopSwitch": executor = L1NpcShopSwitch.getInstance(); break;
            case "L1요리아이콘": executor = L1요리아이콘.getInstance(); break;
            case "L1Loc": executor = L1Loc.getInstance(); break;
            case "L1Move": executor = L1Move.getInstance(); break;
            case "L1ServerName": executor = L1ServerName.getInstance(); break;
            case "L1GfxId": executor = L1GfxId.getInstance(); break;
            case "L1SkillEffect": executor = L1SkillEffect.getInstance(); break;
            case "L1InvGfxId": executor = L1InvGfxId.getInstance(); break;
            case "L1AdShop": executor = L1AdShop.getInstance(); break;
            case "L1Clear": executor = L1Clear.getInstance(); break;
            case "L1Describe": executor = L1Describe.getInstance(); break;
            case "L1QueryCharacter": executor = L1QueryCharacter.getInstance(); break;
            case "L1Shutdown": executor = L1Shutdown.getInstance(); break;
            case "L1GM": executor = L1GM.getInstance(); break;
            case "L1Search": executor = L1Search.getInstance(); break;
            case "L1ChatNG": executor = L1ChatNG.getInstance(); break;
            case "L1Chat": executor = L1Chat.getInstance(); break;
            case "L1DeleteGroundItem": executor = L1DeleteGroundItem.getInstance(); break;
            case "L1Kick": executor = L1Kick.getInstance(); break;
            case "L1ToPC": executor = L1ToPC.getInstance(); break;
            case "L1Kill": executor = L1Kill.getInstance(); break;
            case "L1Invisible": executor = L1Invisible.getInstance(); break;
            case "L1PartyRecall": executor = L1PartyRecall.getInstance(); break;
            case "L1RemoveLetter": executor = L1RemoveLetter.getInstance(); break;
            case "L1HpBar": executor = L1HpBar.getInstance(); break;
            case "L1HomeTown": executor = L1HomeTown.getInstance(); break;
            default: return false;
        }

        executor.execute(pc, name, arg);
        return true;
    }
}
