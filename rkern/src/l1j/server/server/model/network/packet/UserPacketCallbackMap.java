package l1j.server.server.model.network.packet;


import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.shop.PlayerPrivateStore;
import l1j.server.server.model.network.buffer.Disassemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_ActionUi;
import l1j.server.server.model.network.core.cs.packet.CS_AddBuddy;
import l1j.server.server.model.network.core.cs.packet.CS_AdenShop;
import l1j.server.server.model.network.core.cs.packet.CS_Amount;
import l1j.server.server.model.network.core.cs.packet.CS_AskExchange;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.cs.packet.CS_AuthLogin;
import l1j.server.server.model.network.core.cs.packet.CS_BanMember;
import l1j.server.server.model.network.core.cs.packet.CS_BanishParty;
import l1j.server.server.model.network.core.cs.packet.CS_BoardDelete;
import l1j.server.server.model.network.core.cs.packet.CS_BoardList;
import l1j.server.server.model.network.core.cs.packet.CS_BoardRead;
import l1j.server.server.model.network.core.cs.packet.CS_BoardWrite;
import l1j.server.server.model.network.core.cs.packet.CS_Bookmark;
import l1j.server.server.model.network.core.cs.packet.CS_BuySpell;
import l1j.server.server.model.network.core.cs.packet.CS_BuyableSpell;
import l1j.server.server.model.network.core.cs.packet.CS_CallPlayer;
import l1j.server.server.model.network.core.cs.packet.CS_ChangeCastleSecurity;
import l1j.server.server.model.network.core.cs.packet.CS_Channel;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_Chat;
import l1j.server.server.model.network.core.cs.packet.CS_ChatParty;
import l1j.server.server.model.network.core.cs.packet.CS_CheckInventory;
import l1j.server.server.model.network.core.cs.packet.CS_CheckPk;
import l1j.server.server.model.network.core.cs.packet.CS_Craft;
import l1j.server.server.model.network.core.cs.packet.CS_CreateChar;
import l1j.server.server.model.network.core.cs.packet.CS_CreatePledge;
import l1j.server.server.model.network.core.cs.packet.CS_DeleteBookmark;
import l1j.server.server.model.network.core.cs.packet.CS_DeleteChar;
import l1j.server.server.model.network.core.cs.packet.CS_Deposit;
import l1j.server.server.model.network.core.cs.packet.CS_DestroyItem;
import l1j.server.server.model.network.core.cs.packet.CS_Door;
import l1j.server.server.model.network.core.cs.packet.CS_DropItem;
import l1j.server.server.model.network.core.cs.packet.CS_Duel;
import l1j.server.server.model.network.core.cs.packet.CS_Emblem;
import l1j.server.server.model.network.core.cs.packet.CS_EmblemRequest;
import l1j.server.server.model.network.core.cs.packet.CS_EnterWorld;
import l1j.server.server.model.network.core.cs.packet.CS_ExchangeSpell;
import l1j.server.server.model.network.core.cs.packet.CS_ExchangeableSpell;
import l1j.server.server.model.network.core.cs.packet.CS_ExitGhost;
import l1j.server.server.model.network.core.cs.packet.CS_ExtraCommand;
import l1j.server.server.model.network.core.cs.packet.CS_Fix;
import l1j.server.server.model.network.core.cs.packet.CS_FixableItem;
import l1j.server.server.model.network.core.cs.packet.CS_GiveItem;
import l1j.server.server.model.network.core.cs.packet.CS_InvitePartyTarget;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.model.network.core.cs.packet.CS_JoinPledge;
import l1j.server.server.model.network.core.cs.packet.CS_LeaveParty;
import l1j.server.server.model.network.core.cs.packet.CS_LeavePledge;
import l1j.server.server.model.network.core.cs.packet.CS_Logout;
import l1j.server.server.model.network.core.cs.packet.CS_Mail;
import l1j.server.server.model.network.core.cs.packet.CS_Marriage;
import l1j.server.server.model.network.core.cs.packet.CS_NpcAction;
import l1j.server.server.model.network.core.cs.packet.CS_NpcItemControl;
import l1j.server.server.model.network.core.cs.packet.CS_NpcTalk;
import l1j.server.server.model.network.core.cs.packet.CS_OnOff;
import l1j.server.server.model.network.core.cs.packet.CS_PersonalShop;
import l1j.server.server.model.network.core.cs.packet.CS_Plate;
import l1j.server.server.model.network.core.cs.packet.CS_PledgeContent;
import l1j.server.server.model.network.core.cs.packet.CS_PledgeWatch;
import l1j.server.server.model.network.core.cs.packet.CS_QueryBuddy;
import l1j.server.server.model.network.core.cs.packet.CS_QueryCastleSecurity;
import l1j.server.server.model.network.core.cs.packet.CS_QueryPersonalShop;
import l1j.server.server.model.network.core.cs.packet.CS_Quit;
import l1j.server.server.model.network.core.cs.packet.CS_RankControl;
import l1j.server.server.model.network.core.cs.packet.CS_ReadNews;
import l1j.server.server.model.network.core.cs.packet.CS_RemoveBuddy;
import l1j.server.server.model.network.core.cs.packet.CS_Restart;
import l1j.server.server.model.network.core.cs.packet.CS_Result;
import l1j.server.server.model.network.core.cs.packet.CS_ReturnStatus;
import l1j.server.server.model.network.core.cs.packet.CS_ReturnSummon;
import l1j.server.server.model.network.core.cs.packet.CS_SaveIo;
import l1j.server.server.model.network.core.cs.packet.CS_SlaveControl;
import l1j.server.server.model.network.core.cs.packet.CS_Tax;
import l1j.server.server.model.network.core.cs.packet.CS_Throw;
import l1j.server.server.model.network.core.cs.packet.CS_Title;
import l1j.server.server.model.network.core.cs.packet.CS_TradeAccept;
import l1j.server.server.model.network.core.cs.packet.CS_TradeAddItem;
import l1j.server.server.model.network.core.cs.packet.CS_TradeCancel;
import l1j.server.server.model.network.core.cs.packet.CS_War;
import l1j.server.server.model.network.core.cs.packet.CS_WarehouseControl;
import l1j.server.server.model.network.core.cs.packet.CS_Who;
import l1j.server.server.model.network.core.cs.packet.CS_WhoParty;
import l1j.server.server.model.network.core.cs.packet.CS_WhoPledge;
import l1j.server.server.model.network.core.cs.packet.CS_Withdraw;
import l1j.server.server.model.network.core.sc.packet.SC_GameTime;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.packets.Protocol.MainServer.Proto.CSProto;
import l1j.server.server.packets.Protocol.MainServer.Proto.Dispatch.CS_ExtendedProto;
import l1j.server.server.utils.GameUtils;

/**
 * Opcode-indexed packet decoders for CUserSocket READ completion.
 *
 * Static opcode-to-function table: no intermediate TYPE translation or switch.
 *
 * Decoder가 primitive payload를 추출하고 현재 READ 완료 스택에서
 * 기존 게임 핸들러를 직접 실행한다. 중간 명령 저장/게시 단계는 없다.
 */
public final class UserPacketCallbackMap {

    @FunctionalInterface
    public interface PacketFunc {
        boolean execute(Disassemble buffer, CUserSocket client);
    }

    // Static, per-process function pointers. No per-packet allocations.
    public static final PacketFunc DummyUserPacket = (buffer, client) -> true;
    public static final PacketFunc[] gPacketTableFromClient = new PacketFunc[256];
    private static final boolean[] DIRECT_OPCODE = new boolean[256];

    private static final Charset MS949 = Charset.forName("MS949");
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    // Decoders finish copying into a String before invoking any packet handler.
    private static final ThreadLocal<byte[]> FIXED_STRING_SCRATCH = ThreadLocal.withInitial(() -> new byte[4096]);

    private static final ThreadLocal<byte[]> STRING_SCRATCH = ThreadLocal.withInitial(
            () -> new byte[CIOConfig.READ_BUFFER_SIZE]);

    static {
        Arrays.fill(gPacketTableFromClient, DummyUserPacket);
        gPacketTableFromClient[Opcodes.C_PLATE & 0xFF] = UserPacketCallbackMap::decodePlate;
        DIRECT_OPCODE[Opcodes.C_PLATE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BUYABLE_SPELL & 0xFF] = UserPacketCallbackMap::decodeBuyableSpell;
        DIRECT_OPCODE[Opcodes.C_BUYABLE_SPELL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF] = UserPacketCallbackMap::decodeHypertextInputResult;
        DIRECT_OPCODE[Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ADD_BUDDY & 0xFF] = UserPacketCallbackMap::decodeAddBuddy;
        DIRECT_OPCODE[Opcodes.C_ADD_BUDDY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_WITHDRAW & 0xFF] = UserPacketCallbackMap::decodeWithdraw;
        DIRECT_OPCODE[Opcodes.C_WITHDRAW & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ACCEPT_XCHG & 0xFF] = (buffer, client) -> {
            handleAcceptXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ACCEPT_XCHG & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_HACTION & 0xFF] = UserPacketCallbackMap::HtmlCMDPacket;
        DIRECT_OPCODE[Opcodes.C_HACTION & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_EXCHANGEABLE_SPELL & 0xFF] = UserPacketCallbackMap::decodeExchangeableSpell;
        DIRECT_OPCODE[Opcodes.C_EXCHANGEABLE_SPELL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_USE_SPELL & 0xFF] = UserPacketCallbackMap::MagicSkillUsePacket;
        DIRECT_OPCODE[Opcodes.C_USE_SPELL & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_ATTACK & 0xFF] = UserPacketCallbackMap::AttackPacket;
        DIRECT_OPCODE[Opcodes.C_ATTACK & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_UPLOAD_EMBLEM & 0xFF] = UserPacketCallbackMap::decodeUploadEmblem;
        DIRECT_OPCODE[Opcodes.C_UPLOAD_EMBLEM & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_USE_ITEM & 0xFF] = UserPacketCallbackMap::UseItemPacket;
        DIRECT_OPCODE[Opcodes.C_USE_ITEM & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_LEAVE_PLEDGE & 0xFF] = (buffer, client) -> {
            handleLeavePledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LEAVE_PLEDGE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_RANK_CONTROL & 0xFF] = UserPacketCallbackMap::decodeRankControl;
        DIRECT_OPCODE[Opcodes.C_RANK_CONTROL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BOOKMARK & 0xFF] = UserPacketCallbackMap::decodeBookmark;
        DIRECT_OPCODE[Opcodes.C_BOOKMARK & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_MOVE & 0xFF] = UserPacketCallbackMap::MoveToLocationPacket;
        DIRECT_OPCODE[Opcodes.C_MOVE & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_WHO_PLEDGE & 0xFF] = (buffer, client) -> {
            handleWhoPledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_WHO_PLEDGE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_SLAVE_CONTROL & 0xFF] = UserPacketCallbackMap::decodeSlaveControl;
        DIRECT_OPCODE[Opcodes.C_SLAVE_CONTROL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_SAVEIO & 0xFF] = UserPacketCallbackMap::decodeSaveIo;
        DIRECT_OPCODE[Opcodes.C_SAVEIO & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DROP & 0xFF] = UserPacketCallbackMap::DropItemPacket;
        DIRECT_OPCODE[Opcodes.C_DROP & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ALIVE & 0xFF] = (buffer, client) -> {
            handleAlive(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ALIVE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_PERSONAL_SHOP & 0xFF] = UserPacketCallbackMap::decodePersonalShop;
        DIRECT_OPCODE[Opcodes.C_PERSONAL_SHOP & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DELETE_CHARACTER & 0xFF] = UserPacketCallbackMap::CharacterDeletePacket;
        DIRECT_OPCODE[Opcodes.C_DELETE_CHARACTER & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_SHUTDOWN & 0xFF] = UserPacketCallbackMap::decodePledgeContent;
        DIRECT_OPCODE[Opcodes.C_SHUTDOWN & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_WHO & 0xFF] = UserPacketCallbackMap::decodeWho;
        DIRECT_OPCODE[Opcodes.C_WHO & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_WHO_PARTY & 0xFF] = (buffer, client) -> {
            handleWhoParty(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_WHO_PARTY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_RETURN_SUMMON & 0xFF] = (buffer, client) -> {
            handleReturnSummon(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_RETURN_SUMMON & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_QUERY_PERSONAL_SHOP & 0xFF] = UserPacketCallbackMap::decodeQueryPersonalShop;
        DIRECT_OPCODE[Opcodes.C_QUERY_PERSONAL_SHOP & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_EXTENDED_PROTOBUF & 0xFF] = UserPacketCallbackMap::decodeExtendedMultiplexed;
        DIRECT_OPCODE[Opcodes.C_EXTENDED_PROTOBUF & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CANCEL_XCHG & 0xFF] = (buffer, client) -> {
            handleCancelXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_CANCEL_XCHG & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_NPC_ITEM_CONTROL & 0xFF] = UserPacketCallbackMap::decodeNpcItemControl;
        DIRECT_OPCODE[Opcodes.C_NPC_ITEM_CONTROL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_LEAVE_PARTY & 0xFF] = (buffer, client) -> {
            handleLeaveParty(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LEAVE_PARTY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ADD_XCHG & 0xFF] = UserPacketCallbackMap::TradeAddItemsPacket;
        DIRECT_OPCODE[Opcodes.C_ADD_XCHG & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_WAREHOUSE_CONTROL & 0xFF] = UserPacketCallbackMap::decodeWarehouseControl;
        DIRECT_OPCODE[Opcodes.C_WAREHOUSE_CONTROL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_MARRIAGE & 0xFF] = UserPacketCallbackMap::decodeMarriage;
        DIRECT_OPCODE[Opcodes.C_MARRIAGE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHANNEL & 0xFF] = UserPacketCallbackMap::decodeChannel;
        DIRECT_OPCODE[Opcodes.C_CHANNEL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_GET & 0xFF] = UserPacketCallbackMap::GetItemPacket;
        DIRECT_OPCODE[Opcodes.C_GET & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF] = UserPacketCallbackMap::CharacterCreatePacket;
        DIRECT_OPCODE[Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DEAD_RESTART & 0xFF] = (buffer, client) -> {
            handleDeadRestart(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_DEAD_RESTART & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_QUERY_CASTLE_SECURITY & 0xFF] = UserPacketCallbackMap::decodeQueryCastleSecurity;
        DIRECT_OPCODE[Opcodes.C_QUERY_CASTLE_SECURITY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_GIVE & 0xFF] = UserPacketCallbackMap::decodeGiveItem;
        DIRECT_OPCODE[Opcodes.C_GIVE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHAT & 0xFF] = UserPacketCallbackMap::SayPacket2;
        DIRECT_OPCODE[Opcodes.C_CHAT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_FIX & 0xFF] = UserPacketCallbackMap::decodeFix;
        DIRECT_OPCODE[Opcodes.C_FIX & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_QUERY_BUDDY & 0xFF] = (buffer, client) -> {
            handleQueryBuddy(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_QUERY_BUDDY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_FAR_ATTACK & 0xFF] = UserPacketCallbackMap::AttackPacket;
        DIRECT_OPCODE[Opcodes.C_FAR_ATTACK & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_CHANGE_DIRECTION & 0xFF] = UserPacketCallbackMap::decodeChangeHeading;
        DIRECT_OPCODE[Opcodes.C_CHANGE_DIRECTION & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_EXTENDED & 0xFF] = UserPacketCallbackMap::decodeExtended;
        DIRECT_OPCODE[Opcodes.C_EXTENDED & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_READ_NEWS & 0xFF] = (buffer, client) -> {
            handleReadNews(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_READ_NEWS & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_OPEN & 0xFF] = UserPacketCallbackMap::decodeOpen;
        DIRECT_OPCODE[Opcodes.C_OPEN & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DUEL & 0xFF] = (buffer, client) -> {
            handleDuel(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_DUEL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ASK_XCHG & 0xFF] = (buffer, client) -> {
            handleAskXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ASK_XCHG & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_FIXABLE_ITEM & 0xFF] = (buffer, client) -> {
            handleFixableItem(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_FIXABLE_ITEM & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHECK_PK & 0xFF] = (buffer, client) -> {
            handleCheckPk(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_CHECK_PK & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_TITLE & 0xFF] = UserPacketCallbackMap::GiveNicknamePacket;
        DIRECT_OPCODE[Opcodes.C_TITLE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DELETE_BOOKMARK & 0xFF] = UserPacketCallbackMap::decodeDeleteBookmark;
        DIRECT_OPCODE[Opcodes.C_DELETE_BOOKMARK & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_QUIT & 0xFF] = (buffer, client) -> {
            handleQuit(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_QUIT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BOARD_READ & 0xFF] = UserPacketCallbackMap::decodeBoardRead;
        DIRECT_OPCODE[Opcodes.C_BOARD_READ & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_EMBLEM & 0xFF] = UserPacketCallbackMap::decodeEmblem;
        DIRECT_OPCODE[Opcodes.C_EMBLEM & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_REMOVE_BUDDY & 0xFF] = UserPacketCallbackMap::FriendRemove;
        DIRECT_OPCODE[Opcodes.C_REMOVE_BUDDY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_EXCHANGE_SPELL & 0xFF] = UserPacketCallbackMap::decodeExchangeSpell;
        DIRECT_OPCODE[Opcodes.C_EXCHANGE_SPELL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BANISH_PARTY & 0xFF] = UserPacketCallbackMap::decodeBanishParty;
        DIRECT_OPCODE[Opcodes.C_BANISH_PARTY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_LOGOUT & 0xFF] = (buffer, client) -> {
            handleLogout(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LOGOUT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_INVITE_PARTY_TARGET & 0xFF] = UserPacketCallbackMap::decodeInvitePartyTarget;
        DIRECT_OPCODE[Opcodes.C_INVITE_PARTY_TARGET & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BOARD_DELETE & 0xFF] = UserPacketCallbackMap::decodeBoardDelete;
        DIRECT_OPCODE[Opcodes.C_BOARD_DELETE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_EXIT_GHOST & 0xFF] = (buffer, client) -> {
            handleExitGhost(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_EXIT_GHOST & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHECK_INVENTORY & 0xFF] = UserPacketCallbackMap::decodeCheckInventory;
        DIRECT_OPCODE[Opcodes.C_CHECK_INVENTORY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ENTER_PORTAL & 0xFF] = UserPacketCallbackMap::decodeEnterPortal;
        DIRECT_OPCODE[Opcodes.C_ENTER_PORTAL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_THROW & 0xFF] = (buffer, client) -> {
            handleThrow(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_THROW & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_GOTO_PORTAL & 0xFF] = UserPacketCallbackMap::decodeEnterPortal;
        DIRECT_OPCODE[Opcodes.C_GOTO_PORTAL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_WAR & 0xFF] = UserPacketCallbackMap::decodeWar;
        DIRECT_OPCODE[Opcodes.C_WAR & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BOARD_WRITE & 0xFF] = UserPacketCallbackMap::decodeBoardWrite;
        DIRECT_OPCODE[Opcodes.C_BOARD_WRITE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_VOICE_CHAT & 0xFF] = UserPacketCallbackMap::decodeReturnStatus;
        DIRECT_OPCODE[Opcodes.C_VOICE_CHAT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_JOIN_PLEDGE & 0xFF] = (buffer, client) -> {
            handleJoinPledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_JOIN_PLEDGE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_TAX & 0xFF] = UserPacketCallbackMap::decodeTax;
        DIRECT_OPCODE[Opcodes.C_TAX & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BUY_SELL & 0xFF] = UserPacketCallbackMap::decodeBuySell;
        DIRECT_OPCODE[Opcodes.C_BUY_SELL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ONOFF & 0xFF] = UserPacketCallbackMap::decodeOnOff;
        DIRECT_OPCODE[Opcodes.C_ONOFF & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DEPOSIT & 0xFF] = UserPacketCallbackMap::decodeDeposit;
        DIRECT_OPCODE[Opcodes.C_DEPOSIT & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BOARD_LIST & 0xFF] = UserPacketCallbackMap::decodeBoardList;
        DIRECT_OPCODE[Opcodes.C_BOARD_LIST & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_MAIL & 0xFF] = UserPacketCallbackMap::decodeMail;
        DIRECT_OPCODE[Opcodes.C_MAIL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_PLEDGE_WATCH & 0xFF] = UserPacketCallbackMap::decodePledgeWatch;
        DIRECT_OPCODE[Opcodes.C_PLEDGE_WATCH & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_RESTART & 0xFF] = (buffer, client) -> {
            handleCharSelect(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_RESTART & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_SUMMON & 0xFF] = UserPacketCallbackMap::decodeCallPlayer;
        DIRECT_OPCODE[Opcodes.C_SUMMON & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHAT_PARTY_CONTROL & 0xFF] = UserPacketCallbackMap::decodeChatPartyControl;
        DIRECT_OPCODE[Opcodes.C_CHAT_PARTY_CONTROL & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_LOGIN & 0xFF] = UserPacketCallbackMap::LoginPacket;
        DIRECT_OPCODE[Opcodes.C_LOGIN & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ENTER_WORLD & 0xFF] = UserPacketCallbackMap::EnterWorldPacket;
        DIRECT_OPCODE[Opcodes.C_ENTER_WORLD & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ATTACK_CONTINUE & 0xFF] = UserPacketCallbackMap::decodeAutoAttack;
        DIRECT_OPCODE[Opcodes.C_ATTACK_CONTINUE & 0xFF] = true;
        gPacketTableFromClient[Opcodes.C_CREATE_PLEDGE & 0xFF] = UserPacketCallbackMap::decodeCreatePledge;
        DIRECT_OPCODE[Opcodes.C_CREATE_PLEDGE & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BAN_MEMBER & 0xFF] = UserPacketCallbackMap::decodeBanMember;
        DIRECT_OPCODE[Opcodes.C_BAN_MEMBER & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ACTION & 0xFF] = UserPacketCallbackMap::decodeExtraCommand;
        DIRECT_OPCODE[Opcodes.C_ACTION & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_CHANGE_CASTLE_SECURITY & 0xFF] = UserPacketCallbackMap::decodeChangeCastleSecurity;
        DIRECT_OPCODE[Opcodes.C_CHANGE_CASTLE_SECURITY & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_ANSWER & 0xFF] = UserPacketCallbackMap::decodeAnswer;
        DIRECT_OPCODE[Opcodes.C_ANSWER & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DESTROY_ITEM & 0xFF] = UserPacketCallbackMap::DestroyItem;
        DIRECT_OPCODE[Opcodes.C_DESTROY_ITEM & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_DIALOG & 0xFF] = UserPacketCallbackMap::decodeNpcTalk;
        DIRECT_OPCODE[Opcodes.C_DIALOG & 0xFF] = false;
        gPacketTableFromClient[Opcodes.C_BUY_SPELL & 0xFF] = UserPacketCallbackMap::decodeBuySpell;
        DIRECT_OPCODE[Opcodes.C_BUY_SPELL & 0xFF] = false;
    }

    private UserPacketCallbackMap() {}

    public static boolean isDirectOpcode(int opcode) {
        return opcode >= 0 && opcode < 256 && DIRECT_OPCODE[opcode];
    }

    // ------------------------------------------------------------------
    // 문 / 상자 열기 (OPEN)
    // ------------------------------------------------------------------

    static boolean decodeOpen(Disassemble buffer, CUserSocket client) {

        if (buffer.remaining() < 8) {
            return true;
        }

        final int locX =
                buffer.readShort(1) & 0xFFFF;

        final int locY =
                buffer.readShort(3) & 0xFFFF;

        final int objectId =
                buffer.readInt(5);

        handleOpen(client, locX, locY, objectId);

        return true;
    }

    // ------------------------------------------------------------------
    // 이동 (MOVE)
    // ------------------------------------------------------------------

    static boolean MoveToLocationPacket(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 5) {
            return true;
        }
        final int x = buffer.readShort(1) & 0xFFFF;
        final int y = buffer.readShort(3) & 0xFFFF;
        final int h = buffer.readByte(5) & 0xFF;

        if (client.isConnected()) {
            try {
                move(client, x, y, h);
            } catch (Throwable failure) {
                reportHandlerError(failure);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 공격 / 원거리 공격 (ATTACK / FAR ATTACK)
    // ------------------------------------------------------------------

    static boolean AttackPacket(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 8) {
            return true;
        }
        final int targetId = buffer.readInt(1);
        final int x = buffer.readShort(5) & 0xFFFF;
        final int y = buffer.readShort(7) & 0xFFFF;

        if (client.isConnected()) {
            try {
                attack(client, targetId, x, y);
            } catch (Throwable failure) {
                reportHandlerError(failure);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 자동 공격 (AUTO ATTACK)
    // ------------------------------------------------------------------

    static boolean decodeAutoAttack(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int targetId = buffer.readInt(1);

        if (client.isConnected()) {
            try {
                autoAttack(client, targetId);
            } catch (Throwable failure) {
                reportHandlerError(failure);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 스킬 사용 (USE SKILL)
    // ------------------------------------------------------------------

    static boolean MagicSkillUsePacket(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 2) {
            return true;
        }

        final int c1 = buffer.readByte(1) & 0xFF;
        final int c2 = buffer.readByte(2) & 0xFF;
        final int skillId = (c1 * 8) + c2 + 1;

        int targetId = 0;
        int targetX = 0;
        int targetY = 0;
        String message = null;

        switch (skillId) {
        case L1SkillId.TRUE_TARGET:
            if (buffer.remaining() < 10) {
                return true;
            }
            targetId = buffer.readInt(3);
            targetX = buffer.readShort(7) & 0xFFFF;
            targetY = buffer.readShort(9) & 0xFFFF;
            message = readCStringMs949(buffer, 11, buffer.remaining() - 10);
            break;

        case L1SkillId.TELEPORT:
        case L1SkillId.MASS_TELEPORT:
            if (buffer.remaining() < 8) {
                return true;
            }
            targetId = buffer.readShort(3) & 0xFFFF;
            targetX = buffer.readShort(5) & 0xFFFF;
            targetY = buffer.readShort(7) & 0xFFFF;
            break;

        case L1SkillId.FIRE_WALL:
        case L1SkillId.LIFE_STREAM:
            if (buffer.remaining() < 6) {
                return true;
            }
            targetX = buffer.readShort(3) & 0xFFFF;
            targetY = buffer.readShort(5) & 0xFFFF;
            break;

        case L1SkillId.SUMMON_MONSTER:
            if (buffer.remaining() < 3) {
                return true;
            }
            targetX = buffer.readByte(3) & 0xFF;
            break;

        default:           
        	final int limit = 1 + buffer.remaining();
        	int cursor = 3;

        	if (cursor + 4 <= limit) {
        		targetId = buffer.readInt(cursor);
        		cursor += 4;
        	}

        	if (cursor + 2 <= limit) {
        		targetX = buffer.readShort(cursor) & 0xFFFF;
        		cursor += 2;
        	}

        	if (cursor + 2 <= limit) {
        		targetY = buffer.readShort(cursor) & 0xFFFF;
        	}
            break;
        }

        if (client.isConnected()) {
            try {
                useSkill(client, skillId, targetId, targetX, targetY, message);
            } catch (Throwable failure) {
                reportHandlerError(failure);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 아이템 줍기 (PICKUP)
    // ------------------------------------------------------------------

    static boolean GetItemPacket(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 12) {
            return true;
        }
        final int x = buffer.readShort(1) & 0xFFFF;
        final int y = buffer.readShort(3) & 0xFFFF;
        final int objId = buffer.readInt(5);
        final long amount = buffer.readInt(9);

        handlePickup(client, x, y, objId, amount);
        return true;
    }

    // ------------------------------------------------------------------
    // 방향 전환 (CHANGE HEADING)
    // ------------------------------------------------------------------

    static boolean decodeChangeHeading(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final int heading = buffer.readByte(1) & 0xFF;

        handleChangeHeading(client, heading);
        return true;
    }

    // ------------------------------------------------------------------
    // 채팅 (CHAT)
    // ------------------------------------------------------------------

    static boolean SayPacket2(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final String chatText = readCStringMs949(buffer, 2, buffer.remaining() - 1);

        handleChat(client, chatText);
        return true;
    }

    static boolean LoginPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 2) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int accountEnd = findCStringEnd(buffer, 1, limit);
        if (accountEnd < 0) {
            return true;
        }

        final int passwordStart = accountEnd + 1;
        if (passwordStart >= limit) {
            return true;
        }

        final int passwordEnd = findCStringEnd(buffer, passwordStart, limit);
        if (passwordEnd < 0) {
            return true;
        }

        final String accountName = readCStringMs949(buffer, 1, accountEnd - 1);
        final String password = readCStringMs949(buffer, passwordStart, passwordEnd - passwordStart);

        handleAuthLogin(client, accountName, password);
        return true;
    }

    static boolean EnterWorldPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final String charName = readCStringMs949(buffer, 1, payloadSize);

        handleEnterWorld(client, charName);
        return true;
    }

    private static int findCStringEnd(Disassemble buffer, int start, int limit) {
        for (int i = start; i < limit; i++) {
            if (buffer.readByte(i) == 0) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 문자열 유틸리티 (String)
    // ------------------------------------------------------------------

    private static String readCStringMs949(Disassemble buffer, int index, int available) {
        if (available <= 0) {
            return "";
        }

        final byte[] scratch = STRING_SCRATCH.get();
        final int max = Math.min(available, scratch.length);
        int length = 0;

        while (length < max) {
            final byte value = buffer.readByte(index + length);
            if (value == 0) {
                break;
            }
            scratch[length++] = value;
        }

        if (length == 0) {
            return "";
        }
        return new String(scratch, 0, length, MS949);
    }

    static boolean decodeEnterPortal(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int locx = buffer.readShort(1) & 0xFFFF;
        final int locy = buffer.readShort(3) & 0xFFFF;

        handleEnterPortal(client, locx, locy);
        return true;
    }

    static boolean decodeAnswer(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 2) {
            return true;
        }

        final int i = buffer.readShort(1) & 0xFFFF;
        int msgIdx = 0;
        int attrcode;
        int cVal = 0;
        String strVal = null;
        int cursor = 3;

        if (i == 479) {
            attrcode = 479;
        } else {
            if (payloadSize < 8) {
                return true;
            }
            msgIdx = buffer.readInt(3);
            attrcode = buffer.readShort(7) & 0xFFFF;
            cursor = 9;
        }

        if (attrcode == 622) {
            switch (msgIdx) {
            case CS_Attr.MSGCODE_622_KDINIT:
            case CS_Attr.MSGCODE_622_Name:
            case CS_Attr.MSGCODE_622_Card2:
            case CS_Attr.MSGCODE_622_Card3:
            case CS_Attr.MSGCODE_622_BOSS:
            case CS_Attr.MSGCODE_622_Tree:
                if (payloadSize < cursor) {
                    return true;
                }
                cVal = buffer.readByte(cursor) & 0xFF;
                break;
            case CS_Attr.MSGCODE_622_Card:
                if (payloadSize < cursor + 1) {
                    return true;
                }
                cVal = buffer.readByte(cursor + 1) & 0xFF;
                break;
            default:
                break;
            }
        } else {
            switch (attrcode) {
            case 180:
                if (payloadSize < cursor + 1) {
                    return true;
                }
                strVal = readCStringMs949(buffer, cursor + 1, payloadSize - cursor);
                break;
            case 97:
            case 3348:
            case 217:
            case 6008:
            case 221:
            case 222:
            case 4703:
            case 252:
            case 321:
            case 945:
            case 944:
            case 322:
            case 622:
            case 630:
            case 653:
            case 654:
            case 729:
            case 738:
            case 2551:
            case 951:
            case 953:
            case 954:
            case 1256:
            case 1268:
            case 2923:
                if (payloadSize < cursor) {
                    return true;
                }
                cVal = buffer.readByte(cursor) & 0xFF;
                break;
            case 325:
            case 512:
            case 479:
                if (payloadSize < cursor + 1) {
                    return true;
                }
                cVal = buffer.readByte(cursor) & 0xFF;
                strVal = readCStringMs949(buffer, cursor + 1, payloadSize - cursor);
                break;
            default:
                break;
            }
        }

        handleAnswer(client, attrcode, msgIdx, cVal, strVal);
        return true;
    }

    static boolean CharacterCreatePacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int nameEnd = findCStringEnd(buffer, 1, limit);
        if (nameEnd < 0) {
            return true;
        }

        final int cursor = nameEnd + 1;
        if (cursor + 8 > limit) {
            return true;
        }

        final String name = readCStringMs949(buffer, 1, nameEnd - 1);
        final int type    = buffer.readByte(cursor)     & 0xFF;
        final int sex     = buffer.readByte(cursor + 1) & 0xFF;
        final int baseStr = buffer.readByte(cursor + 2) & 0xFF;
        final int baseDex = buffer.readByte(cursor + 3) & 0xFF;
        final int baseCon = buffer.readByte(cursor + 4) & 0xFF;
        final int baseWis = buffer.readByte(cursor + 5) & 0xFF;
        final int baseCha = buffer.readByte(cursor + 6) & 0xFF;
        final int baseInt = buffer.readByte(cursor + 7) & 0xFF;

        final long packedStats = ((long) baseCon) | ((long) baseWis << 8) | ((long) baseCha << 16) | ((long) baseInt << 24);

        handleCreateCustomCharacter(client, type, sex, baseStr, baseDex, packedStats, name);
        return true;
    }

    static boolean decodeChannel(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int type = buffer.readByte(1) & 0xFF;

        final boolean characterSelection = switch (type) {
            case CS_Channel.케릭터생성, CS_Channel.케릭터비번생성,
                 CS_Channel.케릭터비번인증, CS_Channel.케릭터비번변경 -> true;
            default -> false;
        };
        if (characterSelection ? client.getAccount() == null : !client.ownsReadyGameSession()) {
            return true;
        }

        int readD1 = 0;
        int readD2 = 0;
        int readH1 = 0;
        int readH2 = 0;
        int mid = 0;

        String s0 = null;
        String s1 = null;
        Object o0 = null;

        switch (type) {
        case CS_Channel.케릭터비번생성:
        case CS_Channel.케릭터비번인증: {
            final PasswordRead pass = readSecondPassword(buffer, 2, limit);
            if (!pass.valid) {
                return true;
            }
            s0 = pass.value;
            break;
        }
        case CS_Channel.케릭터비번변경: {
            final PasswordRead pass1 = readSecondPassword(buffer, 2, limit);
            if (!pass1.valid || pass1.next >= limit) {
                return true;
            }
            final int secondStart = pass1.next + 1;
            final PasswordRead pass2 = readSecondPassword(buffer, secondStart, limit);
            if (!pass2.valid) {
                return true;
            }
            s0 = pass1.value;
            s1 = pass2.value;
            break;
        }
        case CS_Channel.자동신고:
        case CS_Channel.BOOKMARK_LOADING_SAVE:
            if (payloadSize < 5) {
                return true;
            }
            readD1 = buffer.readInt(2);
            break;
        case CS_Channel.시세검색:
        case CS_Channel.상인찾기: {
            final int end = findCStringEnd(buffer, 2, limit);
            if (end < 0) {
                return true;
            }
            s0 = readCStringMs949(buffer, 2, end - 2);
            break;
        }
        case CS_Channel.BOOKMARK_COLOR: {
            if (payloadSize < 5) {
                return true;
            }
            final int count = buffer.readInt(2);
            if (count < 0) {
                return true;
            }
            int cursor = 6;
            if (count > (limit - cursor) / 5) {
                return true;
            }
            final int[] numIds = new int[count];
            final String[] names = new String[count];

            for (int i = 0; i < count; i++) {
                if (cursor + 4 > limit) {
                    return true;
                }
                numIds[i] = buffer.readInt(cursor);
                cursor += 4;
                final int end = findCStringEnd(buffer, cursor, limit);
                if (end < 0) {
                    return true;
                }
                names[i] = readCStringMs949(buffer, cursor, end - cursor);
                cursor = end + 1;
            }
            o0 = new CS_Channel.BookmarkColorPayload(numIds, names);
            break;
        }
        case CS_Channel.BOOKMARK_SAVE: {
            final int length = payloadSize - 1;
            if (length <= 0) {
                return true;
            }
            final byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = buffer.readByte(2 + i);
            }
            o0 = data;
            break;
        }
        case CS_Channel.EMBLEM:
            if (payloadSize < 2) {
                return true;
            }
            readH1 = buffer.readByte(2) & 0xFF;
            break;
        case CS_Channel.TELPORT:
            if (payloadSize < 5) {
                return true;
            }
            readH1 = buffer.readShort(2) & 0xFFFF;
            readH2 = buffer.readShort(4) & 0xFFFF;
            break;
        case CS_Channel.MINI_MAP_SEND: {
            final int nameEnd = findCStringEnd(buffer, 2, limit);
            if (nameEnd < 0) {
                return true;
            }
            s0 = readCStringMs949(buffer, 2, nameEnd - 2);
            int cursor = nameEnd + 1;
            if (cursor + 6 > limit) {
                return true;
            }
            readH1 = buffer.readShort(cursor) & 0xFFFF;
            cursor += 2;
            readD1 = buffer.readShort(cursor) & 0xFFFF;
            cursor += 2;
            readD2 = buffer.readShort(cursor) & 0xFFFF;
            cursor += 2;
            if (cursor + 2 <= limit) {
                mid = buffer.readShort(cursor) & 0xFFFF;
            }
            break;
        }
        case CS_Channel.CHARNAME_CHANGED: {
            final int sourceEnd = findCStringEnd(buffer, 2, limit);
            if (sourceEnd < 0) {
                return true;
            }
            s0 = readCStringMs949(buffer, 2, sourceEnd - 2);
            final int destStart = sourceEnd + 1;
            if (destStart >= limit) {
                return true;
            }
            final int destEnd = findCStringEnd(buffer, destStart, limit);
            if (destEnd < 0) {
                return true;
            }
            s1 = readCStringMs949(buffer, destStart, destEnd - destStart);
            break;
        }
        default:
            break;
        }

        handleChannel(client, type, readD1, readD2, readH1, readH2, mid, s0, s1, o0);
        return true;
    }

    // ============================================================
    // CUserSocketPacket.java 내부 도우미(Helper) 클래스 및 메서드 
    // ============================================================

    private static final class PasswordRead {
        final boolean valid;
        final int next;
        final String value;

        PasswordRead(boolean valid, int next, String value) {
            this.valid = valid;
            this.next = next;
            this.value = value;
        }
    }

    private static PasswordRead readSecondPassword(Disassemble buffer, int start, int limit) {
        if (start >= limit) {
            return new PasswordRead(false, start, null);
        }

        int size = buffer.readByte(start) & 0xFF;
        if (size > 8) {
            size = 8;
        }

        final int end = start + 1 + size;
        if (end > limit) {
            return new PasswordRead(false, start, null);
        }

        final char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            final int num = buffer.readByte(start + 1 + i) & 0xFF;
            if (num > 9) {
                return new PasswordRead(false, end, null);
            }
            chars[i] = (char) ('0' + num);
        }
        return new PasswordRead(true, end, new String(chars));
    }

    static boolean CharacterDeletePacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int nameEnd = findCStringEnd(buffer, 1, limit);
        if (nameEnd < 0) {
            return true;
        }

        final String name = readCStringMs949(buffer, 1, nameEnd - 1);

        handleDeleteCharacter(client, name);
        return true;
    }

    static boolean DestroyItem(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 4) {
            return true;
        }

        final int length = buffer.readInt(1);
        if (length <= 0 || length > 100) {
            return true;
        }

        final int requiredBytes = 4 + (length << 3);
        if (payloadSize < requiredBytes) {
            return true;
        }

        int objectId = 0;
        int count = 0;
        int[] pairs = null;

        if (length == 1) {
            objectId = buffer.readInt(5);
            count = buffer.readInt(9);
            if (count < 0) {
                return true;
            }
        } else {
            pairs = new int[length << 1];
            int cursor = 5;
            for (int i = 0, end = pairs.length; i < end; i += 2) {
                pairs[i] = buffer.readInt(cursor);
                pairs[i + 1] = buffer.readInt(cursor + 4);
                if (pairs[i + 1] < 0) {
                    return true;
                }
                cursor += 8;
            }
        }

        handleDestroyItem(client, length, objectId, count, pairs);
        return true;
    }

    static boolean decodeGiveItem(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 16) {
            return true;
        }
        final int targetId = buffer.readInt(1);
        final int itemObjectId = buffer.readInt(9);
        final int count = buffer.readInt(13);

        handleGiveItem(client, targetId, itemObjectId, count);
        return true;
    }

    private static final int EMBLEM_SIZE = 384;

    static boolean decodeUploadEmblem(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < EMBLEM_SIZE) {
            return true;
        }
        final byte[] emblem = new byte[EMBLEM_SIZE];
        for (int i = 0; i < EMBLEM_SIZE; i++) {
            emblem[i] = buffer.readByte(1 + i);
        }

        handleUploadEmblem(client, EMBLEM_SIZE, emblem);
        return true;
    }

    static boolean decodeBookmark(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int end = findCStringEnd(buffer, 1, limit);
        if (end < 0 || end == 1) {
            return true;
        }

        final String bookmarkName = readCStringMs949(buffer, 1, end - 1);
        if (bookmarkName == null || bookmarkName.isEmpty()) {
            return true;
        }

        handleBookmark(client, bookmarkName);
        return true;
    }

    static boolean decodeNpcTalk(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int objId = buffer.readInt(1);

        handleNpcTalk(client, objId);
        return true;
    }

    static boolean decodeCreatePledge(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int nul = findCStringEnd(buffer, 1, limit);
        if (nul < 0) {
            return true;
        }

        int start = 1;
        int end = nul;
        while (start < end && (buffer.readByte(start) & 0xFF) <= 0x20) {
            start++;
        }
        while (end > start && (buffer.readByte(end - 1) & 0xFF) <= 0x20) {
            end--;
        }
        if (start >= end) {
            return true;
        }

        final int nameBytes = end - start;
        final String clanName = readCStringMs949(buffer, start, nameBytes);
        if (clanName == null || clanName.isEmpty()) {
            return true;
        }

        handleCreatePledge(client, nameBytes, clanName);
        return true;
    }

    static boolean decodeReturnStatus(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        int levelup = 0;
        int statusup = 0;
        long stats = 0L;

        switch (type) {
        case 1:
        case 3:
            if (payloadSize < 7) {
                return true;
            }
            final int str = buffer.readByte(2) & 0xFF;
            final int intel = buffer.readByte(3) & 0xFF;
            final int wis = buffer.readByte(4) & 0xFF;
            final int dex = buffer.readByte(5) & 0xFF;
            final int con = buffer.readByte(6) & 0xFF;
            final int cha = buffer.readByte(7) & 0xFF;

            stats = ((long) str) | ((long) intel << 8) | ((long) wis << 16) | ((long) dex << 24) | ((long) con << 32) | ((long) cha << 40);
            break;
        case 2:
            if (payloadSize < 2) {
                return true;
            }
            levelup = buffer.readByte(2) & 0xFF;
            if (levelup == 8) {
                if (payloadSize < 3) {
                    return true;
                }
                statusup = buffer.readByte(3) & 0xFF;
            }
            break;
        default:
            return true;
        }

        handleReturnStatus(client, type, levelup, statusup, stats);
        return true;
    }

    static boolean decodeWar(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 2) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        final int limit = 1 + payloadSize;
        final int end = findCStringEnd(buffer, 2, limit);
        if (end < 0) {
            return true;
        }

        final String clanName = readCStringMs949(buffer, 2, end - 2);

        handleWar(client, type, clanName);
        return true;
    }

    static boolean decodeDeleteBookmark(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int end = findCStringEnd(buffer, 1, limit);
        if (end < 0 || end == 1) {
            return true;
        }

        final String bookmarkName = readCStringMs949(buffer, 1, end - 1);
        if (bookmarkName == null || bookmarkName.isEmpty()) {
            return true;
        }

        handleDeleteBookmark(client, bookmarkName);
        return true;
    }

    static boolean decodePersonalShop(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        if (type != 0) {
            handlePersonalShop(client, type, null);
            return true;
        }

        if (payloadSize < 5) {
            return true;
        }

        final int dataSize = payloadSize - 1;
        final byte[] data = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            data[i] = buffer.readByte(2 + i);
        }

        handlePersonalShop(client, type, data);
        return true;
    }

    static boolean decodeWho(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String targetName = null;

        if (payloadSize > 0) {
            final int limit = 1 + payloadSize;
            final int end = findCStringEnd(buffer, 1, limit);
            if (end < 0) {
                return true;
            }
            if (end > 1) {
                targetName = readCStringMs949(buffer, 1, end - 1);
                if (targetName != null && targetName.isEmpty()) {
                    targetName = null;
                }
            }
        }

        handleWho(client, targetName);
        return true;
    }

    static boolean decodeBuySpell(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 2) {
            return true;
        }

        final int count = buffer.readShort(1) & 0xFFFF;
        if (count <= 0 || count > 100) {
            return true;
        }

        final int required = 2 + count * 4;
        if (payloadSize < required) {
            return true;
        }

        int level1 = 0, level2 = 0, level3 = 0;
        int offset = 3;

        for (int i = 0; i < count; i++, offset += 4) {
            final int sid = buffer.readInt(offset);
            if (sid > 24) {
                handleBuySpell(client, 0, 0, 0, 1);
                return true;
            }
            if (sid >= 0 && sid <= 7) {
                level1 |= 1 << sid;
            } else if (sid >= 8 && sid <= 15) {
                level2 |= 1 << (sid - 8);
            } else if (sid >= 16 && sid <= 23) {
                level3 |= 1 << (sid - 16);
            }
        }

        handleBuySpell(client, level1, level2, level3, 0);
        return true;
    }

    static boolean decodeExtraCommand(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final int actionId = buffer.readByte(1) & 0xFF;

        handleExtraCommand(client, actionId);
        return true;
    }

    static boolean DropItemPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 4) {
            return true;
        }

        final int length = buffer.readInt(1);
        if (length <= 0) {
            return true;
        }

        final long required = 4L + ((long) length * 12L);
        if (required > payloadSize) {
            return true;
        }

        if (length == 1) {
            final int x = buffer.readShort(5) & 0xFFFF;
            final int y = buffer.readShort(7) & 0xFFFF;
            final int objectId = buffer.readInt(9);
            final int count = buffer.readInt(13);

            handleDrop(client, 1, x, y, objectId, count, null);
            return true;
        }

        final int bytes = length * 12;
        final byte[] entries = new byte[bytes];
        int src = 5;
        for (int i = 0; i < bytes; i++) {
            entries[i] = buffer.readByte(src + i);
        }

        handleDrop(client, length, 0, 0, 0, 0L, entries);
        return true;
    }

    static boolean decodeSaveIo(Disassemble buffer, CUserSocket client) {
        if (!Config.CHARACTER_CONFIG_IN_SERVER_SIDE) {
            return true;
        }
        final int payloadSize = buffer.remaining();
        if (payloadSize < 4) {
            return true;
        }

        final int length = buffer.readInt(1);
        if (length <= 0 || length > 2048) {
            return true;
        }

        final int dataLength = payloadSize - 4;
        if (dataLength <= 0) {
            return true;
        }

        final byte[] data = new byte[dataLength];
        for (int i = 0; i < dataLength; i++) {
            data[i] = buffer.readByte(5 + i);
        }

        handleSaveio(client, length, data);
        return true;
    }

    static boolean HtmlCMDPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 5) {
            return true;
        }

        final int objid = buffer.readInt(1);
        final int limit = 1 + payloadSize;
        final int actionEnd = findCStringEnd(buffer, 5, limit);
        if (actionEnd < 0) {
            return true;
        }

        final String action = readCStringMs949(buffer, 5, actionEnd - 5);
        if (action == null) {
            return true;
        }

        if (action.equalsIgnoreCase("deadTrans") || action.equalsIgnoreCase("pvpSet") || action.equalsIgnoreCase("ShowHPMPRecovery") || action.equalsIgnoreCase("showDisableEffectIcon") || action.equalsIgnoreCase("showDungeonTimeLimit")) {
            return true;
        }

        int cursor = actionEnd + 1;
        String action2 = null;

        if (action.equalsIgnoreCase("select") || action.equalsIgnoreCase("map") || action.equalsIgnoreCase("apply")) {
            if (cursor >= limit) {
                return true;
            }
            final int action2End = findCStringEnd(buffer, cursor, limit);
            if (action2End < 0) {
                return true;
            }
            action2 = readCStringMs949(buffer, cursor, action2End - cursor);
            cursor = action2End + 1;
        } else if (action.equalsIgnoreCase("ent")) {
            final L1Object obj = CWorld.getInstance().findObject(objid);
            if (obj instanceof L1NpcInstance) {
                final L1NpcInstance npc = (L1NpcInstance) obj;
                if (npc.getNpcTemplate().get_npcId() == 80088) {
                    if (cursor >= limit) {
                        return true;
                    }
                    final int action2End = findCStringEnd(buffer, cursor, limit);
                    if (action2End < 0) {
                        return true;
                    }
                    action2 = readCStringMs949(buffer, cursor, action2End - cursor);
                    cursor = action2End + 1;
                }
            }
        }

        final int tailLength = limit - cursor;
        byte[] actionData = null;
        if (tailLength > 0) {
            actionData = new byte[tailLength];
            for (int i = 0; i < tailLength; i++) {
                actionData[i] = buffer.readByte(cursor + i);
            }
        }

        handleNpcAction(client, objid, action, action2, actionData);
        return true;
    }

    static boolean decodeChatPartyControl(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        if (type != 0 && type != 1 && type != 2 && type != 4 && type != 5) {
            return true;
        }

        String name = null;
        if (type == 0) {
            if (payloadSize < 2) {
                return true;
            }
            final int limit = 1 + payloadSize;
            final int end = findCStringEnd(buffer, 2, limit);
            if (end < 0 || end == 2) {
                return true;
            }
            name = readCStringMs949(buffer, 2, end - 2);
            if (name == null || name.isEmpty()) {
                return true;
            }
        }

        handleChatPartyControl(client, type, name);
        return true;
    }

    static boolean UseItemPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 4) {
            return true;
        }

        final int itemObjId = buffer.readInt(1);
        int targetId = 0, x = 0, y = 0, skillId = 0;
        String name = null;

        if (payloadSize >= 8) {
            targetId = buffer.readInt(5);
        } else {
            return dispatchUseItem(client,  itemObjId, targetId, x, y, name, skillId);
        }

        if (payloadSize >= 10) {
            x = buffer.readShort(9) & 0xFFFF;
        } else {
            return dispatchUseItem(client,  itemObjId, targetId, x, y, name, skillId);
        }

        if (payloadSize >= 12) {
            y = buffer.readShort(11) & 0xFFFF;
        } else {
            return dispatchUseItem(client,  itemObjId, targetId, x, y, name, skillId);
        }

        if (payloadSize == 12) {
            return dispatchUseItem(client,  itemObjId, targetId, x, y, name, skillId);
        }

        final int nameStart = 13;
        final int limit = 1 + payloadSize;
        final int nameEnd = findCStringEnd(buffer, nameStart, limit);

        if (nameEnd < 0) {
            return dispatchUseItem(client,  itemObjId, targetId, x, y, null, 0);
        }

        if (nameEnd == nameStart) {
            name = "";
        } else {
            name = readCStringMs949(buffer, nameStart, nameEnd - nameStart);
        }

        final int skillOffset = nameEnd + 1;
        if (skillOffset < limit) {
            skillId = buffer.readByte(skillOffset) & 0xFF;
        }

        return dispatchUseItem(client,  itemObjId, targetId, x, y, name, skillId);
    }

    private static boolean dispatchUseItem(CUserSocket client, int itemObjId, int targetId, int x, int y, String name, int skillId) {
        handleUseItem(client, itemObjId, targetId, x, y, skillId, name);
        return true;
    }

    static boolean decodeMarriage(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final int type = buffer.readByte(1) & 0xFF;
        if (type != 0 && type != 1) {
            return true;
        }

        handleMarriage(client, type);
        return true;
    }

    static boolean decodeSlaveControl(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 9) {
            return true;
        }
        final int petId = buffer.readInt(1);
        final int targetId = buffer.readInt(6);

        handleSlaveControl(client, petId, targetId);
        return true;
    }

    static boolean decodeRankControl(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 2) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        final int rank = buffer.readByte(2) & 0xFF;
        String name = null;

        if (type == 1 && payloadSize > 2) {
            final int limit = 1 + payloadSize;
            final int nameStart = 3;
            final int nameEnd = findCStringEnd(buffer, nameStart, limit);
            if (nameEnd >= 0) {
                if (nameEnd == nameStart) {
                    name = "";
                } else {
                    name = readCStringMs949(buffer, nameStart, nameEnd - nameStart);
                }
            }
        }

        handleRankControl(client, type, rank, name);
        return true;
    }

    static boolean decodeHypertextInputResult(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 10 || !client.ownsReadyGameSession()) {
            return true;
        }

        final int objectId = buffer.readInt(1);
        final int amount = buffer.readInt(5);
        if (amount <= 0) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int actionStart = 10;
        final int actionEnd = findCStringEnd(buffer, actionStart, limit);
        if (actionEnd < 0) {
            return true;
        }

        final String action;
        if (actionEnd == actionStart) {
            action = "";
        } else {
            action = readCStringMs949(buffer, actionStart, actionEnd - actionStart);
        }

        if (action == null) {
            return true;
        }

        handleHypertextInputResult(client, objectId, amount, action);
        return true;
    }

    static boolean decodeBuySell(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 7) {
            return true;
        }

        final int npcObjectId = buffer.readInt(1);
        final int resultType = buffer.readByte(5) & 0xFF;
        final int size = buffer.readByte(6) & 0xFF;
        // The entry block starts at byte 8. Byte 7 is the legacy separator.
        // A short list must be rejected before copying any entries or dispatching.
        final int entryOffset = 8;
        if (size > 0 && payloadSize < entryOffset + size * Long.BYTES) return true;
        final boolean gmSelection = size == 0 && resultType == 3
                && payloadSize >= entryOffset + Integer.BYTES;
        final int hasFirst = size > 0 || gmSelection ? 1 : 0;
        final long packed0 = size > 0 ? buffer.readLong(entryOffset)
                : gmSelection ? buffer.readInt(entryOffset) & 0xFFFF_FFFFL : 0L;
        byte[] rest = null;
        if (size > 1) {
            rest = new byte[(size - 1) * Long.BYTES];
            buffer.readBytes(entryOffset + Long.BYTES, rest, 0, rest.length);
        }

        handleBuySell(client, npcObjectId, resultType, size, hasFirst, packed0, rest);
        return true;
    }

    static boolean decodeNpcItemControl(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 6) {
            return true;
        }

        final int data = buffer.readByte(1) & 0xFF;
        final int petId = buffer.readInt(2);
        final int listNo = buffer.readByte(6) & 0xFF;

        handleNpcItemControl(client, data, petId, listNo);
        return true;
    }

    static boolean decodeInvitePartyTarget(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        int targetId = 0;
        String targetName = null;

        switch (type) {
        case 0:
        case 1:
        case 3:
            if (payloadSize >= 5) {
                targetId = buffer.readInt(2);
            }
            break;
        case 2:
        case 4:
        case 5:
            if (payloadSize > 1) {
                final int nameStart = 2;
                final int limit = 1 + payloadSize;
                final int nameEnd = findCStringEnd(buffer, nameStart, limit);
                if (nameEnd >= 0) {
                    targetName = nameEnd == nameStart ? "" : readCStringMs949(buffer, nameStart, nameEnd - nameStart);
                }
            }
            break;
        default:
            return true;
        }

        handleInvitePartyTarget(client, type, targetId, targetName);
        return true;
    }

    static boolean decodeCheckInventory(Disassemble buffer, CUserSocket client) {
        if (buffer.remaining() < 4) {
            return true;
        }

        final int petId = buffer.readInt(1);

        handleCheckInventory(client, petId);
        return true;
    }

    static boolean decodeOnOff(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int type = 0, button = 0;

        if (payloadSize >= 1) {
            type = buffer.readByte(1) & 0xFF;
        }
        if (payloadSize >= 2) {
            button = buffer.readByte(2) & 0xFF;
        }

        handleOnoff(client, type, button);
        return true;
    }

    static boolean decodeQueryCastleSecurity(Disassemble buffer, CUserSocket client) {
        int objId = 0;
        if (buffer.remaining() >= 4) {
            objId = buffer.readInt(1);
        }

        handleQueryCastleSecurity(client, objId);
        return true;
    }

    static boolean decodeWarehouseControl(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int type = buffer.readByte(1) & 0xFF;
        int value1 = 0, value2 = 0;

        switch (type) {
        case 0:
            if (payloadSize >= 4) {
                value1 = readCH3(buffer, 2);
            }
            if (payloadSize >= 8) {
                value2 = readCH3(buffer, 6);
            }
            break;
        case 1:
            if (payloadSize >= 4) {
                value1 = readCH3(buffer, 2);
            }
            if (payloadSize >= 9) {
                value2 = buffer.readInt(6);
            }
            break;
        default:
            return true;
        }

        handleWarehouseControl(client, type, value1, value2);
        return true;
    }

    private static int readCH3(Disassemble buffer, int offset) {
        return (buffer.readByte(offset) & 0xFF) | ((buffer.readByte(offset + 1) & 0xFF) << 8) | ((buffer.readByte(offset + 2) & 0xFF) << 16);
    }

    static boolean decodeEmblem(Disassemble buffer, CUserSocket client) {
        int emblemId = 0;
        if (buffer.remaining() >= 4) {
            emblemId = buffer.readInt(1);
        }

        handleEmblem(client, emblemId);
        return true;
    }

    static boolean decodeQueryPersonalShop(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int type = 0, objectId = 0;

        if (payloadSize >= 1) {
            type = buffer.readByte(1) & 0xFF;
        }
        if (payloadSize >= 5) {
            objectId = buffer.readInt(2);
        }

        handleQueryPersonalShop(client, type, objectId);
        return true;
    }

    static boolean TradeAddItemsPacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int itemObjectId = 0, itemCount = 0;

        if (payloadSize >= 4) {
            itemObjectId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            itemCount = buffer.readInt(5);
        }

        handleAddXchg(client, itemObjectId, itemCount);
        return true;
    }

    static boolean decodeChangeCastleSecurity(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int objId = 0, type = 0;

        if (payloadSize >= 4) {
            objId = buffer.readInt(1);
        }
        if (payloadSize >= 5) {
            type = buffer.readByte(5) & 0xFF;
        }

        handleChangeCastleSecurity(client, objId, type);
        return true;
    }

    static boolean FriendRemove(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String charName = null;

        if (payloadSize > 0) {
            final int start = 1;
            final int limit = 1 + payloadSize;
            final int end = findCStringEnd(buffer, start, limit);
            if (end >= 0) {
                charName = end == start ? "" : readCStringMs949(buffer, start, end - start);
            }
        }

        handleRemoveBuddy(client, charName);
        return true;
    }

    static boolean decodeBanishParty(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String memberName = null;

        if (payloadSize > 0) {
            final int start = 1;
            final int limit = 1 + payloadSize;
            final int end = findCStringEnd(buffer, start, limit);
            if (end >= 0) {
                memberName = end == start ? "" : readCStringMs949(buffer, start, end - start);
            }
        }

        handleBanishParty(client, memberName);
        return true;
    }

    static boolean decodeBoardDelete(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int objId = 0, topicId = 0;

        if (payloadSize >= 4) {
            objId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            topicId = buffer.readInt(5);
        }

        handleBoardDelete(client, objId, topicId);
        return true;
    }

    static boolean decodeBoardWrite(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int id = 0;
        String title = null, content = null;

        if (payloadSize >= 4) {
            id = buffer.readInt(1);
            if (payloadSize > 4) {
                final int limit = 1 + payloadSize;
                final int titleStart = 5;
                final int titleEnd = findCStringEnd(buffer, titleStart, limit);

                if (titleEnd >= 0) {
                    title = titleEnd == titleStart ? "" : readCStringMs949(buffer, titleStart, titleEnd - titleStart);
                    final int contentStart = titleEnd + 1;
                    if (contentStart < limit) {
                        final int contentEnd = findCStringEnd(buffer, contentStart, limit);
                        if (contentEnd >= 0) {
                            content = contentEnd == contentStart ? "" : readCStringMs949(buffer, contentStart, contentEnd - contentStart);
                        }
                    }
                }
            }
        }

        handleBoardWrite(client, id, title, content);
        return true;
    }

    static boolean decodeTax(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int taxRate = 0;

        if (payloadSize >= 5) {
            taxRate = buffer.readByte(5) & 0xFF;
        }

        handleTax(client, taxRate);
        return true;
    }

    static boolean decodeDeposit(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int targetId = 0, amount = 0;

        if (payloadSize >= 4) {
            targetId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            amount = buffer.readInt(5);
        }

        handleDeposit(client, targetId, amount);
        return true;
    }

    static boolean decodeBoardList(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int objId = 0, topicNumber = 0;

        if (payloadSize >= 4) {
            objId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            topicNumber = buffer.readInt(5);
        }

        handleBoardList(client, objId, topicNumber);
        return true;
    }

    static boolean decodeMail(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int type = 0;

        if (payloadSize >= 1) {
            type = buffer.readByte(1) & 0xFF;
        }

        int value0 = 0, value1 = 0;
        String receiverName = null, subject = null;
        Object object = null;
        final int limit = 1 + payloadSize;

        switch (type) {
        case 0:
        case 1:
        case 2:
            break;
        case 16:
        case 17:
        case 18:
        case 48:
        case 49:
        case 50:
        case 64:
            if (payloadSize >= 5) {
                value0 = buffer.readInt(2);
            }
            break;
        case 32:
        case 33: {
            int pos = 2;
            if (limit - pos >= 2) {
                value0 = buffer.readShort(pos) & 0xFFFF;
                pos += 2;
            }
            if (pos < limit) {
                final int end = findMailCStringEnd(buffer, pos, limit);
                receiverName = readMailCStringEucKr(buffer, pos, end);
                pos = end < limit ? end + 1 : limit;
            }
            if (limit - pos >= 2) {
                final int next = findMailSsNext(buffer, pos, limit);
                subject = readMailSs(buffer, pos, next);
                pos = next;
            }
            if (limit - pos >= 2) {
                final int next = findMailSsNext(buffer, pos, limit);
                object = readMailSs(buffer, pos, next);
            }
            break;
        }
        case 96:
        case 97:
        case 98: {
            int deleteCount = 0;
            if (payloadSize >= 5) {
                deleteCount = buffer.readInt(2);
            }
            value0 = deleteCount;

            if (deleteCount <= 0 || deleteCount >= 1000) {
                break;
            }
            if (deleteCount == 1) {
                if (payloadSize >= 9) {
                    value1 = buffer.readInt(6);
                }
                break;
            }

            final int[] deleteIds = new int[deleteCount];
            int pos = 6;
            for (int i = 0; i < deleteCount; i++) {
                if (limit - pos < 4) {
                    break;
                }
                deleteIds[i] = buffer.readInt(pos);
                pos += 4;
            }
            object = deleteIds;
            break;
        }
        default:
            return true;
        }

        handleMail(client, type, value0, value1, receiverName, subject, object);
        return true;
    }


    private static int findMailCStringEnd(Disassemble buffer, int start, int limit) {
        for (int pos = start; pos < limit; pos++) {
            if (buffer.readByte(pos) == 0) {
                return pos;
            }
        }
        return limit;
    }

    private static String readMailCStringEucKr(Disassemble buffer, int start, int end) {
        final byte[] temp = FIXED_STRING_SCRATCH.get();
        final int length = Math.min(end - start, temp.length);

        for (int i = 0; i < length; i++) {
            temp[i] = buffer.readByte(start + i);
        }
        return new String(temp, 0, length, EUC_KR);
    }

    private static int findMailSsNext(Disassemble buffer, int start, int limit) {
        int pos = start;
        int guard = 0;

        while (limit - pos >= 2) {
            final int b0 = buffer.readByte(pos) & 0xFF;
            final int b1 = buffer.readByte(pos + 1) & 0xFF;
            pos += 2;

            if (b0 == 0 && b1 == 0) {
                break;
            }
            if (++guard > 4096) {
                break;
            }
        }
        return pos;
    }

    private static String readMailSs(Disassemble buffer, int start, int next) {
        final byte[] temp = FIXED_STRING_SCRATCH.get();
        int end = next;

        if (end - start >= 2 && (buffer.readByte(end - 2) & 0xFF) == 0 && (buffer.readByte(end - 1) & 0xFF) == 0) {
            end -= 2;
        }

        int index = 0;
        int pos = start;

        while (end - pos >= 2) {
            final int b0 = buffer.readByte(pos) & 0xFF;
            final int b1 = buffer.readByte(pos + 1) & 0xFF;
            pos += 2;

            if (b0 >= 0x7F || b1 >= 0x7F) {
                if (index + 1 < temp.length) {
                    temp[index++] = (byte) b1;
                    temp[index++] = (byte) b0;
                }
            } else {
                if (index < temp.length) {
                    temp[index++] = (byte) b0;
                }
            }
        }
        return new String(temp, 0, index, MS949);
    }


    private static String readCStringEucKr(Disassemble buffer, int start, int end) {
        final int rawLength = end - start;
        if (rawLength <= 0) {
            return "";
        }

        final byte[] temp = FIXED_STRING_SCRATCH.get();
        final int length = Math.min(rawLength, temp.length);

        for (int i = 0; i < length; i++) {
            temp[i] = buffer.readByte(start + i);
        }
        return new String(temp, 0, length, EUC_KR);
    }

    static boolean decodePledgeContent(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int data = 0;
        String content = null;

        if (payloadSize >= 1) {
            data = buffer.readByte(1) & 0xFF;
        }

        if ((data == 15 || data == 16) && payloadSize > 1) {
            final int start = 2;
            final int limit = 1 + payloadSize;
            int end = start;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }
            content = readCStringEucKr(buffer, start, end);
        }

        handlePledgeContent(client, data, content);
        return true;
    }

    static boolean decodeCallPlayer(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String name = null;

        if (payloadSize > 0) {
            final int start = 1;
            final int limit = 1 + payloadSize;
            int end = start;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }
            name = readCStringEucKr(buffer, start, end);
        }

        handleCallPlayer(client, name);
        return true;
    }

    static boolean decodeBanMember(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String targetName = null;

        if (payloadSize > 0) {
            final int start = 1;
            final int limit = 1 + payloadSize;
            int end = start;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }
            targetName = readCStringEucKr(buffer, start, end);
        }

        handleBanMember(client, targetName);
        return true;
    }

    static boolean decodeExchangeableSpell(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int value = 0;

        if (payloadSize >= 4) {
            value = buffer.readInt(1);
        }

        handleExchangeableSpell(client, value);
        return true;
    }

    static boolean decodeWithdraw(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int drawAmount = 0;

        if (payloadSize >= 8) {
            drawAmount = buffer.readInt(5);
        }

        handleWithdraw(client, drawAmount);
        return true;
    }

    static boolean decodeAddBuddy(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String charName = null;

        if (payloadSize > 0) {
            final int start = 1;
            final int limit = 1 + payloadSize;
            int end = start;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }
            charName = readCStringEucKr(buffer, start, end);
        }

        handleAddBuddy(client, charName);
        return true;
    }

    static boolean decodeBuyableSpell(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int id = 0;

        if (payloadSize >= 4) {
            id = buffer.readInt(1);
        }

        handleBuyableSpell(client, id);
        return true;
    }

    static boolean decodePlate(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int objectId = 0;

        if (payloadSize >= 4) {
            objectId = buffer.readInt(1);
        }

        handlePlate(client, objectId);
        return true;
    }

    static boolean decodeFix(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int itemObjectId = 0, npcObjectId = 0;

        if (payloadSize >= 4) {
            itemObjectId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            npcObjectId = buffer.readInt(5);
        }

        handleFix(client, itemObjectId, npcObjectId);
        return true;
    }

    static boolean decodeExtended(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int type = 0;

        if (payloadSize >= 2) {
            type = buffer.readShort(1) & 0xFFFF;
        }

        int size = 0, firstId = 0, firstCount = 0;
        Object object = null;

        if (type == 4) {
            final int limit = 1 + payloadSize;
            int pos = 3;

            for (int i = 0; i < 1000; i++) {
                int ff = 0;
                if (limit - pos >= 2) {
                    ff = buffer.readShort(pos) & 0xFFFF;
                    pos += 2;
                }
                if (ff == 0) {
                    break;
                }
            }

            final int skip = Math.min(129, limit - pos);
            pos += skip;
            
            if (limit - pos >= 2) {
                size = buffer.readShort(pos) & 0xFFFF;
                pos += 2;
            }

            if (size > 0) {
                if (limit - pos >= 4) {
                    firstId = buffer.readInt(pos);
                    pos += 4;
                }
                if (limit - pos >= 2) {
                    firstCount = buffer.readShort(pos) & 0xFFFF;
                    pos += 2;
                }
                if (size > 1 && pos < limit) {
                    final int maxTail = (size - 1) * 6;
                    final int length = Math.min(limit - pos, maxTail);
                    if (length > 0) {
                        final byte[] tail = new byte[length];
                        for (int i = 0; i < length; i++) {
                            tail[i] = buffer.readByte(pos + i);
                        }
                        object = tail;
                    }
                }
            }
        }

        handleExtended(client, type, size, firstId, firstCount, object);
        return true;
    }

    
    static boolean decodePledgeWatch(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int data = 0;
        String targetClanName = null;

        if (payloadSize >= 1) {
            data = buffer.readByte(1) & 0xFF;
        }

        if ((data == 0 || data == 1) && payloadSize > 1) {
            final int start = 2;
            final int limit = 1 + payloadSize;
            int end = start;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }
            targetClanName = readCStringEucKr(buffer, start, end);
        }

        handlePledgeWatch(client, data, targetClanName);
        return true;
    }
    
    static boolean decodeBoardRead(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        int objId = 0;
        int topicNumber = 0;

        if (payloadSize >= 4) {
            objId = buffer.readInt(1);
        }
        if (payloadSize >= 8) {
            topicNumber = buffer.readInt(5);
        }

        handleBoardRead(client, objId, topicNumber);
        return true;
    }

    
    static boolean GiveNicknamePacket(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();
        String charName = null;
        String title = null;

        if (payloadSize > 0) {
            final int limit = 1 + payloadSize;
            int pos = 1;
            int end = pos;

            while (end < limit && buffer.readByte(end) != 0) {
                end++;
            }

            charName = readCStringEucKr(buffer, pos, end);

            if (end < limit) {
                pos = end + 1;
                if (pos < limit) {
                    end = pos;
                    while (end < limit && buffer.readByte(end) != 0) {
                        end++;
                    }
                    title = readCStringEucKr(buffer, pos, end);
                }
            }
        }

        handleTitle(client, charName, title);
        return true;
    }
    
    
    
    
    static boolean decodeExchangeSpell(Disassemble buffer, CUserSocket client) {
        int payloadSize = buffer.remaining();
        if (payloadSize < 2) return true;
        int count = buffer.readShort(1) & 0xFFFF;
        if (count == 0 || count > 100 || payloadSize < 2 + count * Integer.BYTES) return true;

        int selection = 0;
        for (int i = 0, offset = 3; i < count; i++, offset += Integer.BYTES) {
            int sid = buffer.readInt(offset);
            if (sid > 24) {
                handleExchangeSpell(client, 0, true);
                return true;
            }
            if (sid >= 0 && sid < 24) selection |= 1 << sid;
        }
        handleExchangeSpell(client, selection, false);
        return true;
    }


    
    
    private static final int CRAFT_PACKET_CHAT = 0x02;
    private static final int CRAFT_PACKET_EXCLUDE = 0x1F;
    private static final int CRAFT_PACKET_NEW_STAT = 228;
    private static final int CRAFT_PACKET_SEAL = 0x39;
    private static final int CRAFT_PACKET_CLAN_BUFF = 140;
    private static final int CRAFT_PACKET_CLAN_RANKING = 146;
    private static final int CRAFT_PACKET_EQUIP_SWAP = 33;
    private static final int CRAFT_PACKET_CLAN_BUFF_CHANGE = 0xF9;
    private static final int CRAFT_PACKET_CLAN_BUFF_CHOICE = 0xF8;

    private static final ThreadLocal<byte[]> CRAFT_STRING_BUFFER = ThreadLocal.withInitial(() -> new byte[256]);
    
    private static boolean dispatchExtendedProto(Disassemble buffer, CUserSocket client, int payloadSize) {
        final byte[] frames = new byte[payloadSize + 1];
        frames[0] = (byte) (Opcodes.C_EXTENDED_PROTOBUF & 0xFF);

        for (int i = 0; i < payloadSize; i++) {
            frames[i + 1] = buffer.readByte(i + 1);
        }

        handleExtendedProto(client, frames);
        return true;
    }
    
    private static boolean isCraftType(int type) {
        switch (type) {
        case CRAFT_PACKET_CHAT:
        case CRAFT_PACKET_EXCLUDE:
        case CRAFT_PACKET_NEW_STAT:
        case CRAFT_PACKET_SEAL:
        case CRAFT_PACKET_CLAN_BUFF:
        case CRAFT_PACKET_CLAN_RANKING:
        case CRAFT_PACKET_EQUIP_SWAP:
        case CRAFT_PACKET_CLAN_BUFF_CHANGE:
        case CRAFT_PACKET_CLAN_BUFF_CHOICE:
            return true;
        default:
            return false;
        }
    }
    
    private static boolean decodeCraft(Disassemble buffer, CUserSocket client, int type, int payloadSize) {
        final int limit = 1 + payloadSize;

        /*
         * [0] opcode
         * [1] C_Craft type
         */
        int pos = 2;
        int p1 = 0, p2 = 0, p3 = 0;
        long l0 = 0L, l1 = 0L;
        String s0 = null, s1 = null;

        switch (type) {
        case CRAFT_PACKET_CLAN_BUFF_CHANGE:
            break;

        case CRAFT_PACKET_CLAN_BUFF_CHOICE: {
            if (limit - pos >= 2) pos += 2;
            if (limit - pos >= 4) pos += 4;

            final long var = readCraftVarInt(buffer, pos, limit);
            p1 = (int) var;
            pos = (int) (var >>> 32);

            if (pos < limit) pos++;
            if (pos < limit) {
                p2 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            break;
        }

        case CRAFT_PACKET_EQUIP_SWAP: {
            if (limit - pos >= 2) pos += 2;
            if (pos < limit) pos++;
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            if (pos < limit) {
                p2 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            break;
        }

        case CRAFT_PACKET_CLAN_BUFF: {
            if (limit - pos >= 2) pos += 2;
            if (limit - pos >= 2) pos += 2;

            final long var = readCraftVarInt(buffer, pos, limit);
            p1 = ((int) var) - 2724;
            break;
        }

        case CRAFT_PACKET_SEAL: {
            if (limit - pos >= 2) pos += 2;
            if (limit - pos >= 2) pos += 2;

            final long var = readCraftVarInt(buffer, pos, limit);
            p1 = (int) var;
            break;
        }

        case CRAFT_PACKET_CLAN_RANKING: {
            if (limit - pos >= 2) pos += 2;
            break;
        }

        case CRAFT_PACKET_NEW_STAT: {
            if (pos < limit) pos++;

            int totalLength = 0;
            if (limit - pos >= 2) {
                totalLength = (buffer.readByte(pos) & 0xFF) | ((buffer.readByte(pos + 1) & 0xFF) << 8);
                pos += 2;
            }

            if (totalLength < 6 || totalLength > 64) {
                p3 = -1;
                break;
            }

            if (limit - pos >= 2) pos += 2;
            if (pos < limit) pos++;
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            if (pos < limit) pos++;
            if (pos < limit) {
                p2 = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            int remaining = totalLength - 6;
            int mask = 0;
            long packedStats = 0L;

            while (remaining > 0) {
                int charStat = 0;
                int stat = 0;

                if (pos < limit) {
                    charStat = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                if (pos < limit) {
                    stat = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                remaining -= 2;

                if (charStat == 0) {
                    break;
                }

                if (charStat >= 0x30 && charStat <= 0x58 && (charStat & 7) == 0) {
                    final int index = (charStat - 0x30) >> 3;
                    final int shift = index << 3;

                    packedStats &= ~(0xFFL << shift);
                    packedStats |= ((long) stat & 0xFFL) << shift;
                    mask |= 1 << index;
                }
            }

            p3 = mask;
            l0 = packedStats;
            break;
        }

        case CRAFT_PACKET_EXCLUDE: {
            if (pos < limit) pos++;
            if (pos < limit) pos++;
            if (limit - pos >= 2) pos += 2;
            
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            if (p1 != 0) {
                if (pos < limit) pos++;
                if (pos < limit) {
                    p2 = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                int dummy = 0;
                if (pos < limit) {
                    dummy = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                if (dummy != 0 && dummy != 64) {
                    int nameLength = 0;
                    if (pos < limit) {
                        nameLength = buffer.readByte(pos) & 0xFF;
                        pos++;
                    }

                    if (nameLength > 0 && nameLength <= 12) {
                        if (pos < limit) {
                            final int actualLength = Math.min(nameLength, limit - pos);
                            final byte[] scratch = CRAFT_STRING_BUFFER.get();
                            int stringLength = 0;

                            for (int i = 0; i < actualLength; i++) {
                                final byte value = buffer.readByte(pos + i);
                                scratch[i] = value;
                                if (stringLength == i && value != 0) {
                                    stringLength++;
                                }
                            }

                            for (int i = 0; i < actualLength; i++) {
                                if (scratch[i] == 0) {
                                    stringLength = i;
                                    break;
                                }
                            }

                            s0 = new String(scratch, 0, stringLength, MS949);
                        }
                    }
                }
            }
            break;
        }

        case CRAFT_PACKET_CHAT: {
            pos = Math.min(limit, pos + 4);
            final int valueK = client.getChatCount() / 128;
            int chatCount = 0;

            if (valueK == 0) {
                if (pos < limit) {
                    chatCount = buffer.readByte(pos) & 0xFF;
                    pos++;
                }
            } else if (valueK <= 127) {
                if (limit - pos >= 2) {
                    chatCount = ((buffer.readByte(pos) & 0xFF) - 128);
                    chatCount |= (buffer.readByte(pos + 1) & 0xFF) << 7;
                    pos += 2;
                }
            } else if (valueK <= 16383) {
                if (limit - pos >= 3) {
                    chatCount = ((buffer.readByte(pos) & 0xFF) - 128);
                    chatCount |= (((buffer.readByte(pos + 1) & 0xFF) - 128) << 7);
                    chatCount |= (buffer.readByte(pos + 2) & 0xFF) << 14;
                    pos += 3;
                }
            } else {
                if (limit - pos >= 4) {
                    chatCount = ((buffer.readByte(pos) & 0xFF) - 128);
                    chatCount |= (((buffer.readByte(pos + 1) & 0xFF) - 128) << 7);
                    chatCount |= (((buffer.readByte(pos + 2) & 0xFF) - 128) << 14);
                    chatCount |= (buffer.readByte(pos + 3) & 0xFF) << 21;
                    pos += 4;
                }
            }

            client.setChatCount(chatCount + 1);

            if (pos < limit) pos++;
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            if (pos < limit) pos++;

            int messageLength = 0;
            if (pos < limit) {
                messageLength = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            if (messageLength <= 0) {
                s0 = "";
            } else {
                final byte[] scratch = CRAFT_STRING_BUFFER.get();
                Arrays.fill(scratch, 0, messageLength, (byte) 0);
                final int actual = Math.min(messageLength, limit - pos);

                for (int i = 0; i < actual; i++) {
                    scratch[i] = buffer.readByte(pos + i);
                }
                pos += actual;
                s0 = new String(scratch, 0, messageLength, MS949);
            }

            if (p1 == 1) {
                if (pos < limit) pos++;
                int targetLength = 0;
                if (pos < limit) {
                    targetLength = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                if (targetLength <= 0) {
                    s1 = "";
                } else {
                    final byte[] scratch = CRAFT_STRING_BUFFER.get();
                    Arrays.fill(scratch, 0, targetLength, (byte) 0);
                    final int actual = Math.min(targetLength, limit - pos);

                    for (int i = 0; i < actual; i++) {
                        scratch[i] = buffer.readByte(pos + i);
                    }
                    s1 = new String(scratch, 0, targetLength, MS949);
                }
            }
            break;
        }

        default:
            return false;
        }

        handleCraft(client, type, p1, p2, p3, l0, l1, s0, s1);
        return true;
    }
    
    /*
     * high 32bit = next position
     * low  32bit = decoded value
     */
    private static long readCraftVarInt(Disassemble buffer, int pos, int limit) {
        if (pos >= limit) {
            return ((long) pos << 32);
        }

        int value = 0;
        int shift = 0;
        int current = pos;

        while (current < limit && shift < 35) {
            final int b = buffer.readByte(current) & 0xFF;
            current++;
            value |= (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }

        return ((long) current << 32) | (value & 0xFFFFFFFFL);
    }
    
    static boolean decodeExtendedMultiplexed(Disassemble buffer, CUserSocket client) {
        final int payloadSize = buffer.remaining();

        if (payloadSize < 2) {
            return true;
        }

        final int msgId = buffer.readShort(1) & 0xFFFF;

        if (CSProto.isRoutable(msgId)) {
            return dispatchExtendedProto(buffer, client,  payloadSize);
        }

        if (!client.ownsReadyGameSession()) {
            return true;
        }

        final int craftType = buffer.readByte(1) & 0xFF;

        if (isCraftType(craftType)) {
            return decodeCraft(buffer, client,  craftType, payloadSize);
        }

        return decodeActionUi(buffer, client,  msgId, payloadSize);
    }
    
    private static final int ACTION_CS_PING_ACK = 0x03E9;
    private static final int ACTION_ACCOUNT_TAM = 0x01CC;
    private static final int ACTION_ACCOUNT_TAM_CANCEL = 0x01E0;
    private static final int ACTION_ACCOUNT_TAM_UPDATE = 0x013D;
    private static final int ACTION_SOCIAL = 0x013F;
    private static final int ACTION_SKY_GARDEN_TELEPORT = 0x84;
    private static final int ACTION_CASTLE_WAR_PROCLAIM = 0x45;
    private static final int ACTION_PARTY_FLAG_SET = 0x0152;
    private static final int ACTION_EVENT_TELEPORT = 143;
    private static final int ACTION_NEWSHOP = 0x0331;
    private static final int ACTION_BLESSED_TELEPORT = 0x033D;
    private static final int ACTION_CLAN_JOIN_SETTING = 0x0146;
    private static final int ACTION_CLAN_JOIN_INFO = 0x014C;
    private static final int ACTION_CLAN_JOIN = 322;
    private static final int ACTION_ENVIRONMENT_SETTING = 1002;

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    
    private static boolean decodeActionUi(Disassemble buffer, CUserSocket client, int type, int payloadSize) {
        final int limit = 1 + payloadSize;

 
        int pos = 3;
        int p1 = 0, p2 = 0, p3 = 0;
        long l0 = 0L, l1 = 0L;
        String s0 = null, s1 = null;
        Object o0 = null;

        switch (type) {

        case ACTION_ENVIRONMENT_SETTING:
        case ACTION_CS_PING_ACK:
        case ACTION_EVENT_TELEPORT:
        case ACTION_SKY_GARDEN_TELEPORT:
        case ACTION_CLAN_JOIN_INFO:
        case ACTION_ACCOUNT_TAM_UPDATE:
        case ACTION_ACCOUNT_TAM:
            break;

        case ACTION_NEWSHOP: {
            final long lengthResult = readActionVarInt(buffer, pos, limit);
            final int newShopLength = (int) lengthResult;
            pos = (int) (lengthResult >>> 32);

            if (pos < limit) pos++;
            if (pos < limit) pos++;

            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            if (p1 == 0) {
                final int maxEntries = Math.min(PlayerPrivateStore.MAX_ITEMS_PER_SIDE * 2,
                        Math.min(Math.max(0, newShopLength), Math.max(0, limit - pos)));
                final int[] entries = maxEntries == 0 ? CS_ActionUi.EMPTY_SHOP_ENTRIES : new int[maxEntries << 2];
                int entryCount = 0;

                for (int i = 0; i < newShopLength; i++) {
                    int code = 0;
                    if (pos < limit) {
                        code = buffer.readByte(pos) & 0xFF;
                        pos++;
                    }

                    if (code != 0x12 && code != 0x1A) {
                        break;
                    }
                    if (entryCount == maxEntries) return true;

                    if (pos < limit) pos++;

                    int objectId = 0, price = 0, count = 0;

                    for (int field = 0; field < 3; field++) {
                        int code2 = 0;
                        if (pos < limit) {
                            code2 = buffer.readByte(pos) & 0xFF;
                            pos++;
                        }

                        if (code2 == 0x08 || code2 == 0x10 || code2 == 0x18) {
                            final long valueResult = readActionVarInt(buffer, pos, limit);
                            final int value = (int) valueResult;
                            pos = (int) (valueResult >>> 32);

                            if (code2 == 0x08) objectId = value;
                            else if (code2 == 0x10) price = value;
                            else count = value;
                        }
                    }

                    final int base = entryCount << 2;
                    entries[base] = code;
                    entries[base + 1] = objectId;
                    entries[base + 2] = price;
                    entries[base + 3] = count;
                    entryCount++;
                }

                int chatLength = 0;
                if (pos < limit) {
                    chatLength = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                final int actualChatLength = Math.min(chatLength, Math.max(0, limit - pos));
                final byte[] chat;

                if (actualChatLength <= 0) {
                    chat = EMPTY_BYTES;
                } else {
                    chat = new byte[actualChatLength];
                    for (int i = 0; i < actualChatLength; i++) {
                        chat[i] = buffer.readByte(pos + i);
                    }
                    pos += actualChatLength;
                }

                if (pos < limit) pos++;

                int polyLength = 0;
                if (pos < limit) {
                    polyLength = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                final String polyNum = readActionFixedEucKr(buffer, pos, polyLength, limit);
                o0 = new CS_ActionUi.ShopPayload(entries, entryCount, chat, polyNum);
            }
            break;
        }

        case ACTION_BLESSED_TELEPORT: {
            if (pos < limit) pos++;
            if (limit - pos >= 2) pos += 2;

            int length = 0;
            if (pos < limit) {
                length = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            final byte[] bytes = new byte[length];
            final int available = Math.min(length, Math.max(0, limit - pos));

            for (int i = 0; i < available; i++) {
                bytes[i] = buffer.readByte(pos + i);
            }

            o0 = bytes;
            break;
        }

        case ACTION_CLAN_JOIN_SETTING: {
            if (pos < limit) pos++;
            if (limit - pos >= 2) pos += 2;
            
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            
            if (pos < limit) pos++;
            
            if (pos < limit) {
                p2 = buffer.readByte(pos) & 0xFF;
                pos++;
            }

            if (p2 == 2) {
                if (pos < limit) pos++;

                int size = 0;
                if (pos < limit) {
                    size = buffer.readByte(pos) & 0xFF;
                    pos++;
                }

                s0 = readActionHex(buffer, pos, size, limit);
            }
            break;
        }

        case ACTION_CLAN_JOIN: {
            if (pos < limit) pos++;
            if (limit - pos >= 2) pos += 2;

            int nameLength = 0;
            if (pos < limit) {
                nameLength = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            p1 = nameLength;

            final int actualNameLength = Math.min(nameLength, Math.max(0, limit - pos));
            final byte[] nameBytes;

            if (actualNameLength <= 0) {
                nameBytes = EMPTY_BYTES;
            } else {
                nameBytes = new byte[actualNameLength];
                for (int i = 0; i < actualNameLength; i++) {
                    nameBytes[i] = buffer.readByte(pos + i);
                }
                pos += actualNameLength;
            }

            // The join-type byte was already consumed before the two-byte
            // header and the clan name. The remaining password payload is
            // readD(4), readC(1), length(1), then exactly length bytes.
            // Do not interpret the first byte of readD as the join type.
            if (limit - pos >= 6) {
                pos += 5;
                final int size = buffer.readByte(pos) & 0xFF;
                pos++;
                if (size > 0 && size <= limit - pos) {
                    s0 = readActionHex(buffer, pos, size, limit);
                }
            }
            
            o0 = nameBytes;
            break;
        }

        case ACTION_PARTY_FLAG_SET: {
            if (limit - pos < Short.BYTES) {
                return true;
            }
            final int size = buffer.readShort(pos) & 0xFFFF;
            pos += Short.BYTES;
            if (size > limit - pos) {
                return true;
            }

            final byte[] bytes = size == 0 ? EMPTY_BYTES : new byte[size];
            buffer.readBytes(pos, bytes, 0, size);
            o0 = bytes;
            break;
        }

        case ACTION_ACCOUNT_TAM_CANCEL: {
            int length = -1;
            if (pos < limit) {
                length = (buffer.readByte(pos) & 0xFF) - 1;
                pos++;
            }
            
            if (limit - pos >= 2) pos += 2;

            final StringBuilder sb = new StringBuilder(Math.max(0, length) << 1);

            for (int i = 0; i < length; i++) {
                int value = 0;
                if (pos < limit) {
                    value = buffer.readByte(pos) & 0xFF;
                    pos++;
                }
                sb.append((byte) value);
            }

            s0 = sb.toString();
            break;
        }

        case ACTION_SOCIAL: {
            if (limit - pos >= 2) pos += 2;
            if (pos < limit) pos++;
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            if (pos < limit) pos++;
            if (pos < limit) {
                p2 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            break;
        }

        case ACTION_CASTLE_WAR_PROCLAIM: {
            if (limit - pos >= 2) pos += 2;
            if (pos < limit) pos++;
            if (pos < limit) {
                p1 = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            break;
        }

        default:
            break;
        }

        handleActionUi(client, type, p1, p2, p3, l0, l1, s0, s1, o0);
        return true;
    }
    
    private static String readActionHex(Disassemble buffer, int pos, int size, int limit) {
        if (size <= 0) {
            return "";
        }

        final char[] chars = new char[size << 1];
        for (int i = 0; i < size; i++) {
            int value = 0;
            if (pos < limit) {
                value = buffer.readByte(pos) & 0xFF;
                pos++;
            }
            final int base = i << 1;
            chars[base] = HEX[value >>> 4];
            chars[base + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }

    /*
     * high 32bit = next position
     * low  32bit = value
     */
    private static long readActionVarInt(Disassemble buffer, int pos, int limit) {
        if (pos >= limit) {
            return ((long) pos << 32);
        }

        int value = 0;
        int shift = 0;
        int current = pos;

        while (current < limit && shift < 35) {
            final int b = buffer.readByte(current) & 0xFF;
            current++;
            value |= (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return ((long) current << 32) | (value & 0xFFFFFFFFL);
    }
    
    private static String readActionFixedEucKr(Disassemble buffer, int pos, int requestedLength, int limit) {
        if (requestedLength <= 0 || pos >= limit) {
            return null;
        }

        final int length = Math.min(requestedLength, limit - pos);
        final byte[] scratch = FIXED_STRING_SCRATCH.get();

        for (int i = 0; i < length; i++) {
            scratch[i] = buffer.readByte(pos + i);
        }

        return new String(scratch, 0, length, EUC_KR);
    }

    private static final Logger HANDLER_LOG =
            Logger.getLogger(UserPacketCallbackMap.class.getName());


    private static void reportHandlerError(Throwable failure) {
        final Thread thread = Thread.currentThread();
        final Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(thread, failure);
        } else {
            HANDLER_LOG.log(java.util.logging.Level.SEVERE,
                    "Uncaught exception in CIOThread inbound dispatch", failure);
        }
    }

    static void handleOpen( CUserSocket client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            CS_Door.handle(
            client,
            p0, // locX
            p1, // locY
            p2  // objectId
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePickup( CUserSocket client, int p0, int p1, int p2, long l0 ) {
        if (!client.isConnected()) return;
        try {
            pickup(client, p0, p1, p2, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChangeHeading( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            changeHeading(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChat( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Chat.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAuthLogin( CUserSocket client, String s0, String s1 ) {
        if (!client.isConnected()) return;
        try {
            CS_AuthLogin.handle(client, s0, s1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleEnterWorld( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_EnterWorld.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleEnterPortal( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            enterPortal(client, p0, p1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAnswer( CUserSocket client, int p0, int p1, int p2, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Attr.handle(client, p0, p1, p2, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCreateCustomCharacter( CUserSocket client, int p0, int p1, int p2, int p3, long l0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            createCustomCharacter(client, s0, p0, p1, p2, p3, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChannel( CUserSocket client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Channel.handle(client, p0, p1, p2, p3, (int) l0, (int) l1, s0, s1, o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeleteCharacter( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DeleteChar.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDestroyItem( CUserSocket client, int p0, int p1, int p2, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DestroyItem.handle(client, p0, p1, p2, (int[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeadRestart( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_Restart.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleGiveItem( CUserSocket client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            CS_GiveItem.handle(client, p0, p1, p2);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleUploadEmblem( CUserSocket client, int p0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Emblem.handle(client, (byte[]) o0,  p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAlive( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            alive(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBookmark( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Bookmark.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCharSelect( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_CharSelect.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleNpcTalk( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_NpcTalk.handle(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCreatePledge( CUserSocket client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CreatePledge.handle(client, s0, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReturnStatus( CUserSocket client, int p0, int p1, int p2, long l0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ReturnStatus.handle(client, p0, p1, p2, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWar( CUserSocket client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_War.handle(client, p0, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeleteBookmark( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DeleteBookmark.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePersonalShop( CUserSocket client, int p0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_PersonalShop.handle(client, p0, (byte[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWho( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Who.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReturnSummon( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_ReturnSummon.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBuySpell( CUserSocket client, int p0, int p1, int p2, int p3 ) {
        if (!client.isConnected()) return;
        try {
            CS_BuySpell.handle(client, p0, p1, p2, p3);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExtraCommand( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExtraCommand.handle(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleUseItem( CUserSocket client, int p0, int p1, int p2, int p3, long l0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ItemUse.handle(
                    client,
                    p0,         // itemObjId
                    p1,         // targetId
                    p2,         // x
                    p3,         // y
                    s0,         // name
                    (int) l0);  // skillId
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDrop( CUserSocket client, int p0, int p1, int p2, int p3, long l0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DropItem.handle(
                    client,
                    p0,
                    p1,
                    p2,
                    p3,
                    (int) l0,
                    (byte[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleSaveio( CUserSocket client, int p0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_SaveIo.handle(
                    client,
                    p0,
                    (byte[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleNpcAction( CUserSocket client, int p0, String s0, String s1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_NpcAction.handle(
                    client,
                    p0,
                    s0,
                    s1,
                    (byte[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChatPartyControl( CUserSocket client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ChatParty.handle(
                    client,
                    p0,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleMarriage( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Marriage.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleSlaveControl( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_SlaveControl.handle(
                    client,
                    p0,
                    p1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWhoPledge( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_WhoPledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleRankControl( CUserSocket client, int p0, int p1, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_RankControl.handle(
                    client,
                    p0, // type
                    p1, // rank
                    s0  // name
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleHypertextInputResult( CUserSocket client, int p0, int p1, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Amount.handle(
                    client,
                    p0, // objectId
                    p1, // amount
                    s0  // action
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBuySell( CUserSocket client, int p0, int p1, int p2, int p3, long l0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Result.handle(
                    client,
                    p0,         // npcObjectId
                    p1,         // resultType
                    p2,         // size
                    p3,         // hasFirst
                    l0,         // first raw 8-byte entry
                    (byte[]) o0 // entry #1..N-1
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleNpcItemControl( CUserSocket client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            CS_NpcItemControl.handle(
                    client,
                    p0, // data
                    p1, // petId
                    p2  // listNo
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleLogout( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_Logout.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleInvitePartyTarget( CUserSocket client, int p0, int p1, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_InvitePartyTarget.handle(
                    client,
                    p0, // type
                    p1, // targetId
                    s0  // targetName
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCheckInventory( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CheckInventory.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleOnoff( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_OnOff.handle(
                    client,
                    p0,
                    p1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleQueryCastleSecurity( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_QueryCastleSecurity.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWarehouseControl( CUserSocket client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            CS_WarehouseControl.handle(
                    client,
                    p0, // type
                    p1, // oldPass / chkPass
                    p2  // newPass / objId
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleEmblem( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_EmblemRequest.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleQueryPersonalShop( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_QueryPersonalShop.handle(
                    client,
                    p0, // type
                    p1  // objectId
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAddXchg( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_TradeAddItem.handle(
                    client,
                    p0, // itemObjectId
                    p1  // itemCount
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChangeCastleSecurity( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_ChangeCastleSecurity.handle(
                    client,
                    p0, // objId
                    p1  // type
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleQueryBuddy( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_QueryBuddy.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleRemoveBuddy( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_RemoveBuddy.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBanishParty( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BanishParty.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardDelete( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_BoardDelete.handle(
                    client,
                    p0, // objId
                    p1  // topicId
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExitGhost( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_ExitGhost.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleThrow( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_Throw.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardWrite( CUserSocket client, int p0, String s0, String s1 ) {
        if (!client.isConnected()) return;
        try {
            CS_BoardWrite.handle(
                    client,
                    p0, // id
                    s0, // title
                    s1  // content
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleJoinPledge( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_JoinPledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleTax( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Tax.handle(
                    client,
                    p0); // taxRate
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeposit( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_Deposit.handle(
                    client,
                    p0, // targetId
                    p1  // amount
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardList( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_BoardList.handle(
                    client,
                    p0, // objId
                    p1  // topicNumber
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleMail( CUserSocket client, int p0, int p1, int p2, String s0, String s1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Mail.handle(
                    client,
                    p0, // type
                    p1, // paper / targetId / deleteCount
                    p2, // single deleteId
                    s0, // receiverName
                    s1, // subject
                    o0  // content String / int[] deleteIds
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePledgeContent( CUserSocket client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_PledgeContent.handle(
                    client,
                    p0, // data
                    s0  // content
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWhoParty( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_WhoParty.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCancelXchg( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_TradeCancel.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCallPlayer( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CallPlayer.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBanMember( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BanMember.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExchangeableSpell( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExchangeableSpell.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleLeavePledge( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_LeavePledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAcceptXchg( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_TradeAccept.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWithdraw( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Withdraw.handle(
                    client,
                    p0); // drawAmount
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAddBuddy( CUserSocket client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_AddBuddy.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBuyableSpell( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BuyableSpell.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePlate( CUserSocket client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Plate.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleLeaveParty( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_LeaveParty.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleFix( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_Fix.handle(
                    client,
                    p0, // itemObjectId
                    p1  // npcObjectId
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExtended( CUserSocket client, int p0, int p1, int p2, int p3, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_AdenShop.handle(
                    client,
                    p0, // type
                    p1, // size
                    p2, // firstId
                    p3, // firstCount
                    o0  // remaining raw entries
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDuel( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_Duel.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePledgeWatch( CUserSocket client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_PledgeWatch.handle(
                    client,
                    p0,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardRead( CUserSocket client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            CS_BoardRead.handle(
                    client,
                    p0,
                    p1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleQuit( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_Quit.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleTitle( CUserSocket client, String s0, String s1 ) {
        if (!client.isConnected()) return;
        try {
            CS_Title.handle(
                    client,
                    s0,
                    s1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCheckPk( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_CheckPk.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleFixableItem( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_FixableItem.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAskXchg( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_AskExchange.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExchangeSpell(CUserSocket client, int selection, boolean invalidSid) {
        if (!client.isConnected()) return;
        try {
            CS_ExchangeSpell.handle(client, selection, invalidSid);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReadNews( CUserSocket client ) {
        if (!client.isConnected()) return;
        try {
            CS_ReadNews.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExtendedProto( CUserSocket client, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExtendedProto.handle(
                    client,
                    o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCraft( CUserSocket client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1 ) {
        if (!client.isConnected()) return;
        try {
            CS_Craft.handle(
                    client,
                    p0,
                    p1,
                    p2,
                    p3,
                    l0,
                    l1,
                    s0,
                    s1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleActionUi( CUserSocket client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ActionUi.handle(
                    client,
                    p0,
                    p1,
                    p2,
                    p3,
                    l0,
                    l1,
                    s0,
                    s1,
                    o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    // A disconnected/old CUserSocket may still retain its previous activeChar
    // while the new login owns that PC. Never enqueue old-session input into
    // the existing 32-slot controller or mutate world state with that actor.
    private static boolean ownsCurrentSession(CUserSocket client, L1PcInstance pc) {
        return client != null && client.isConnected()
                && pc != null && pc.getNetConnection() == client;
    }

	// ------------------------------------------------------------------
	// 이동 (MOVE)
	// ------------------------------------------------------------------

	private static void move(CUserSocket client, int x, int y, int h) {
		final L1PcInstance pc = client.getActiveChar();

		if (!ownsCurrentSession(client, pc) || pc.isDead()) {
			return;
		}
		pc.getCreatureController().Move(x, y, h);
	}

	// ------------------------------------------------------------------
	// 공격 / 원거리 공격 (ATTACK / FAR ATTACK)
	// ------------------------------------------------------------------

	private static void attack(CUserSocket client, int targetId, int x, int y) {

		final L1PcInstance pc = client.getActiveChar();

		if (!ownsCurrentSession(client, pc)) {
			return;
		}

		if (pc.isDead() || pc.isGhost() || pc.isInvisDelay() || pc.isInvisble()) {
			return;
		}

		pc.getCreatureController().Attack(targetId, x, y, false);
	}

	// ------------------------------------------------------------------
	// 자동 공격 (AUTO ATTACK)
	// ------------------------------------------------------------------

	private static void autoAttack(CUserSocket client, int targetId) {

		final L1PcInstance pc = client.getActiveChar();

		if (!ownsCurrentSession(client, pc)) {
			return;
		}

		if (pc.isDead() || pc.isGhost() || pc.isInvisDelay() || pc.isInvisble()) {
			return;
		}
		pc.getCreatureController().Attack(targetId, 0, 0, true);
	}

	// ------------------------------------------------------------------
	// 스킬 사용 (USE SKILL)
	// ------------------------------------------------------------------

	private static void useSkill(CUserSocket client, int skillId, int targetId, int targetX, int targetY, String message) {

		final L1PcInstance pc = client.getActiveChar();

		if (!ownsCurrentSession(client, pc)) {
			return;
		}

		if (pc.isDead() || pc.isGhost()) {
			return;
		}

		if ((pc.isInvisble() || pc.isInvisDelay() && !SkillUseHelper.canCastWithInvis(skillId))) {
			return;
		}
		pc.getCreatureController().Skill(skillId, targetId, targetX, targetY, message);
	}

	// ------------------------------------------------------------------
	// 아이템 줍기 (PICKUP)
	// ------------------------------------------------------------------

	private static void pickup(CUserSocket client, int x, int y, int objId, long amount) {
		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();
		if (!ownsCurrentSession(client, pc) || pc.isDead() || pc.isGhost()
                || pc.isInvisble() || pc.isInvisDelay()) {
			return;
		}
		if (GameUtils.isNonAction(pc)) {
			return;
		}
		if (pc.getOnlineStatus() != 1) {
			return;
		}
		pc.getCreatureController().GetItem(x, y, objId, amount);
	}


	private static void changeHeading(CUserSocket client, int heading) {

		final L1PcInstance pc = client.getActiveChar();
		if (!ownsCurrentSession(client, pc)) {
			return;
		}

        // Keep heading changes ordered with MOVE/ATTACK callbacks in the
        // existing per-character 32-slot executor, not beside it on a shard.
        if (heading >= 0 && heading <= 7 && pc.getCreatureController() != null) {
            pc.getCreatureController().ChangeHeading(heading);
        }
	}

	private static void enterPortal(CUserSocket client, int locx, int locy) {
		final L1PcInstance pc = client.getActiveChar();
		if (!ownsCurrentSession(client, pc)) {
			return;
		}
		Dungeon.getInstance().dg(locx, locy, pc.getMap().getId(), pc);
	}
	
	private static void createCustomCharacter(CUserSocket client, String name, int type, int sex, int baseStr, int baseDex, long packedStats) {

	    final int baseCon = (int) (packedStats       & 0xFFL);
	    final int baseWis = (int) ((packedStats >>> 8)  & 0xFFL);
	    final int baseCha = (int) ((packedStats >>> 16) & 0xFFL);
	    final int baseInt = (int) ((packedStats >>> 24) & 0xFFL);
	    CS_CreateChar.handle(client, name, type, sex, baseStr, baseDex, baseCon, baseWis, baseCha, baseInt);
	}

	private static void alive(CUserSocket client) {
	    final L1PcInstance pc = client.getActiveChar();

	    if (pc == null) {
	        return;
	    }
	    SC_GameTime.send(pc);
	}
}
