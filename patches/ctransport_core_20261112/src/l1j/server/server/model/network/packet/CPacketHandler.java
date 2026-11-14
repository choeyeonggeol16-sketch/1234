package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.core.diagnostics.CNetworkDiagnostics;

import java.nio.charset.Charset;
import java.util.Arrays;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_ActionUi;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.cs.packet.CS_Channel;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.packets.Protocol.CSProto;

import l1j.server.server.model.network.core.sc.packet.SC_GameTime;
import java.util.logging.Logger;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.network.core.cs.packet.CS_AddBuddy;
import l1j.server.server.model.network.core.cs.packet.CS_AdenShop;
import l1j.server.server.model.network.core.cs.packet.CS_Amount;
import l1j.server.server.model.network.core.cs.packet.CS_AskExchange;
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
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_Chat;
import l1j.server.server.model.network.core.cs.packet.CS_ChatParty;
import l1j.server.server.model.network.core.cs.packet.CS_CheckInventory;
import l1j.server.server.model.network.core.cs.packet.CS_CheckPk;
import l1j.server.server.model.network.core.cs.packet.CS_ClanMatching;
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
import l1j.server.server.model.network.core.cs.packet.CS_ExtendedProto;
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
import l1j.server.server.utils.MJCommons;

/**
 * Opcode-indexed packet decoders for CUserSocket READ completion.
 *
 * Static opcode-to-function table: no intermediate TYPE translation or switch.
 *
 * Decoder가 primitive payload를 추출하고 현재 READ 완료 스택에서
 * 기존 게임 핸들러를 직접 실행한다. 중간 명령 저장/게시 단계는 없다.
 */
public final class CPacketHandler {

    @FunctionalInterface
    public interface PacketHandler {
        boolean execute(ReadBuffer buffer, CIOClient client);
    }

    // Static, per-process function pointers. No per-packet allocations.
    public static final PacketHandler[] HANDLERS = new PacketHandler[256];
    private static final boolean[] DIRECT_OPCODE = new boolean[256];

    private static final Charset MS949 = Charset.forName("MS949");

    private static final ThreadLocal<byte[]> STRING_SCRATCH = ThreadLocal.withInitial(
            () -> new byte[ConnectionConfig.READ_BUFFER_SIZE]);

    static {
        HANDLERS[Opcodes.C_PLATE & 0xFF] = CPacketHandler::decodePlate;
        DIRECT_OPCODE[Opcodes.C_PLATE & 0xFF] = false;
        HANDLERS[Opcodes.C_BUYABLE_SPELL & 0xFF] = CPacketHandler::decodeBuyableSpell;
        DIRECT_OPCODE[Opcodes.C_BUYABLE_SPELL & 0xFF] = false;
        HANDLERS[Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF] = CPacketHandler::decodeHypertextInputResult;
        DIRECT_OPCODE[Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF] = false;
        HANDLERS[Opcodes.C_ADD_BUDDY & 0xFF] = CPacketHandler::decodeAddBuddy;
        DIRECT_OPCODE[Opcodes.C_ADD_BUDDY & 0xFF] = false;
        HANDLERS[Opcodes.C_WITHDRAW & 0xFF] = CPacketHandler::decodeWithdraw;
        DIRECT_OPCODE[Opcodes.C_WITHDRAW & 0xFF] = false;
        HANDLERS[Opcodes.C_ACCEPT_XCHG & 0xFF] = (buffer, client) -> {
            handleAcceptXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ACCEPT_XCHG & 0xFF] = false;
        HANDLERS[Opcodes.C_HACTION & 0xFF] = CPacketHandler::decodeNpcAction;
        DIRECT_OPCODE[Opcodes.C_HACTION & 0xFF] = false;
        HANDLERS[Opcodes.C_EXCHANGEABLE_SPELL & 0xFF] = CPacketHandler::decodeExchangeableSpell;
        DIRECT_OPCODE[Opcodes.C_EXCHANGEABLE_SPELL & 0xFF] = false;
        HANDLERS[Opcodes.C_USE_SPELL & 0xFF] = CPacketHandler::decodeUseSkill;
        DIRECT_OPCODE[Opcodes.C_USE_SPELL & 0xFF] = true;
        HANDLERS[Opcodes.C_ATTACK & 0xFF] = CPacketHandler::decodeAttack;
        DIRECT_OPCODE[Opcodes.C_ATTACK & 0xFF] = true;
        HANDLERS[Opcodes.C_UPLOAD_EMBLEM & 0xFF] = CPacketHandler::decodeUploadEmblem;
        DIRECT_OPCODE[Opcodes.C_UPLOAD_EMBLEM & 0xFF] = false;
        HANDLERS[Opcodes.C_USE_ITEM & 0xFF] = CPacketHandler::decodeUseItem;
        DIRECT_OPCODE[Opcodes.C_USE_ITEM & 0xFF] = false;
        HANDLERS[Opcodes.C_LEAVE_PLEDGE & 0xFF] = (buffer, client) -> {
            handleLeavePledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LEAVE_PLEDGE & 0xFF] = false;
        HANDLERS[Opcodes.C_RANK_CONTROL & 0xFF] = CPacketHandler::decodeRankControl;
        DIRECT_OPCODE[Opcodes.C_RANK_CONTROL & 0xFF] = false;
        HANDLERS[Opcodes.C_BOOKMARK & 0xFF] = CPacketHandler::decodeBookmark;
        DIRECT_OPCODE[Opcodes.C_BOOKMARK & 0xFF] = false;
        HANDLERS[Opcodes.C_MOVE & 0xFF] = CPacketHandler::decodeMove;
        DIRECT_OPCODE[Opcodes.C_MOVE & 0xFF] = true;
        HANDLERS[Opcodes.C_WHO_PLEDGE & 0xFF] = (buffer, client) -> {
            handleWhoPledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_WHO_PLEDGE & 0xFF] = false;
        HANDLERS[Opcodes.C_SLAVE_CONTROL & 0xFF] = CPacketHandler::decodeSlaveControl;
        DIRECT_OPCODE[Opcodes.C_SLAVE_CONTROL & 0xFF] = false;
        HANDLERS[Opcodes.C_SAVEIO & 0xFF] = CPacketHandler::decodeSaveIo;
        DIRECT_OPCODE[Opcodes.C_SAVEIO & 0xFF] = false;
        HANDLERS[Opcodes.C_DROP & 0xFF] = CPacketHandler::decodeDropItem;
        DIRECT_OPCODE[Opcodes.C_DROP & 0xFF] = false;
        HANDLERS[Opcodes.C_ALIVE & 0xFF] = (buffer, client) -> {
            handleAlive(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ALIVE & 0xFF] = false;
        HANDLERS[Opcodes.C_PERSONAL_SHOP & 0xFF] = CPacketHandler::decodePersonalShop;
        DIRECT_OPCODE[Opcodes.C_PERSONAL_SHOP & 0xFF] = false;
        HANDLERS[Opcodes.C_DELETE_CHARACTER & 0xFF] = CPacketHandler::decodeDeleteCharacter;
        DIRECT_OPCODE[Opcodes.C_DELETE_CHARACTER & 0xFF] = false;
        HANDLERS[Opcodes.C_SHUTDOWN & 0xFF] = CPacketHandler::decodePledgeContent;
        DIRECT_OPCODE[Opcodes.C_SHUTDOWN & 0xFF] = false;
        HANDLERS[Opcodes.C_WHO & 0xFF] = CPacketHandler::decodeWho;
        DIRECT_OPCODE[Opcodes.C_WHO & 0xFF] = false;
        HANDLERS[Opcodes.C_WHO_PARTY & 0xFF] = (buffer, client) -> {
            handleWhoParty(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_WHO_PARTY & 0xFF] = false;
        HANDLERS[Opcodes.C_RETURN_SUMMON & 0xFF] = (buffer, client) -> {
            handleReturnSummon(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_RETURN_SUMMON & 0xFF] = false;
        HANDLERS[Opcodes.C_QUERY_PERSONAL_SHOP & 0xFF] = CPacketHandler::decodeQueryPersonalShop;
        DIRECT_OPCODE[Opcodes.C_QUERY_PERSONAL_SHOP & 0xFF] = false;
        HANDLERS[Opcodes.C_EXTENDED_PROTOBUF & 0xFF] = CPacketHandler::decodeExtendedMultiplexed;
        DIRECT_OPCODE[Opcodes.C_EXTENDED_PROTOBUF & 0xFF] = false;
        HANDLERS[Opcodes.C_CANCEL_XCHG & 0xFF] = (buffer, client) -> {
            handleCancelXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_CANCEL_XCHG & 0xFF] = false;
        HANDLERS[Opcodes.C_NPC_ITEM_CONTROL & 0xFF] = CPacketHandler::decodeNpcItemControl;
        DIRECT_OPCODE[Opcodes.C_NPC_ITEM_CONTROL & 0xFF] = false;
        HANDLERS[Opcodes.C_LEAVE_PARTY & 0xFF] = (buffer, client) -> {
            handleLeaveParty(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LEAVE_PARTY & 0xFF] = false;
        HANDLERS[Opcodes.C_ADD_XCHG & 0xFF] = CPacketHandler::decodeAddExchange;
        DIRECT_OPCODE[Opcodes.C_ADD_XCHG & 0xFF] = false;
        HANDLERS[Opcodes.C_WAREHOUSE_CONTROL & 0xFF] = CPacketHandler::decodeWarehouseControl;
        DIRECT_OPCODE[Opcodes.C_WAREHOUSE_CONTROL & 0xFF] = false;
        HANDLERS[Opcodes.C_MARRIAGE & 0xFF] = CPacketHandler::decodeMarriage;
        DIRECT_OPCODE[Opcodes.C_MARRIAGE & 0xFF] = false;
        HANDLERS[Opcodes.C_CHANNEL & 0xFF] = CPacketHandler::decodeChannel;
        DIRECT_OPCODE[Opcodes.C_CHANNEL & 0xFF] = false;
        HANDLERS[Opcodes.C_GET & 0xFF] = CPacketHandler::decodePickup;
        DIRECT_OPCODE[Opcodes.C_GET & 0xFF] = true;
        HANDLERS[Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF] = CPacketHandler::decodeCreateCustomCharacter;
        DIRECT_OPCODE[Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF] = false;
        HANDLERS[Opcodes.C_DEAD_RESTART & 0xFF] = (buffer, client) -> {
            handleDeadRestart(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_DEAD_RESTART & 0xFF] = false;
        HANDLERS[Opcodes.C_QUERY_CASTLE_SECURITY & 0xFF] = CPacketHandler::decodeQueryCastleSecurity;
        DIRECT_OPCODE[Opcodes.C_QUERY_CASTLE_SECURITY & 0xFF] = false;
        HANDLERS[Opcodes.C_GIVE & 0xFF] = CPacketHandler::decodeGiveItem;
        DIRECT_OPCODE[Opcodes.C_GIVE & 0xFF] = false;
        HANDLERS[Opcodes.C_CHAT & 0xFF] = CPacketHandler::decodeChat;
        DIRECT_OPCODE[Opcodes.C_CHAT & 0xFF] = false;
        HANDLERS[Opcodes.C_FIX & 0xFF] = CPacketHandler::decodeFix;
        DIRECT_OPCODE[Opcodes.C_FIX & 0xFF] = false;
        HANDLERS[Opcodes.C_QUERY_BUDDY & 0xFF] = (buffer, client) -> {
            handleQueryBuddy(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_QUERY_BUDDY & 0xFF] = false;
        HANDLERS[Opcodes.C_FAR_ATTACK & 0xFF] = CPacketHandler::decodeAttack;
        DIRECT_OPCODE[Opcodes.C_FAR_ATTACK & 0xFF] = true;
        HANDLERS[Opcodes.C_CHANGE_DIRECTION & 0xFF] = CPacketHandler::decodeChangeHeading;
        DIRECT_OPCODE[Opcodes.C_CHANGE_DIRECTION & 0xFF] = true;
        HANDLERS[Opcodes.C_EXTENDED & 0xFF] = CPacketHandler::decodeExtended;
        DIRECT_OPCODE[Opcodes.C_EXTENDED & 0xFF] = false;
        HANDLERS[Opcodes.C_READ_NEWS & 0xFF] = (buffer, client) -> {
            handleReadNews(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_READ_NEWS & 0xFF] = false;
        HANDLERS[Opcodes.C_OPEN & 0xFF] = CPacketHandler::decodeOpen;
        DIRECT_OPCODE[Opcodes.C_OPEN & 0xFF] = false;
        HANDLERS[Opcodes.C_DUEL & 0xFF] = (buffer, client) -> {
            handleDuel(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_DUEL & 0xFF] = false;
        HANDLERS[Opcodes.C_ASK_XCHG & 0xFF] = (buffer, client) -> {
            handleAskXchg(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_ASK_XCHG & 0xFF] = false;
        HANDLERS[Opcodes.C_FIXABLE_ITEM & 0xFF] = (buffer, client) -> {
            handleFixableItem(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_FIXABLE_ITEM & 0xFF] = false;
        HANDLERS[Opcodes.C_CHECK_PK & 0xFF] = (buffer, client) -> {
            handleCheckPk(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_CHECK_PK & 0xFF] = false;
        HANDLERS[Opcodes.C_TITLE & 0xFF] = CPacketHandler::decodeTitle;
        DIRECT_OPCODE[Opcodes.C_TITLE & 0xFF] = false;
        HANDLERS[Opcodes.C_DELETE_BOOKMARK & 0xFF] = CPacketHandler::decodeDeleteBookmark;
        DIRECT_OPCODE[Opcodes.C_DELETE_BOOKMARK & 0xFF] = false;
        HANDLERS[Opcodes.C_QUIT & 0xFF] = (buffer, client) -> {
            handleQuit(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_QUIT & 0xFF] = false;
        HANDLERS[Opcodes.C_BOARD_READ & 0xFF] = CPacketHandler::decodeBoardRead;
        DIRECT_OPCODE[Opcodes.C_BOARD_READ & 0xFF] = false;
        HANDLERS[Opcodes.C_EMBLEM & 0xFF] = CPacketHandler::decodeEmblem;
        DIRECT_OPCODE[Opcodes.C_EMBLEM & 0xFF] = false;
        HANDLERS[Opcodes.C_REMOVE_BUDDY & 0xFF] = CPacketHandler::decodeRemoveBuddy;
        DIRECT_OPCODE[Opcodes.C_REMOVE_BUDDY & 0xFF] = false;
        HANDLERS[Opcodes.C_EXCHANGE_SPELL & 0xFF] = CPacketHandler::decodeExchangeSpell;
        DIRECT_OPCODE[Opcodes.C_EXCHANGE_SPELL & 0xFF] = false;
        HANDLERS[Opcodes.C_BANISH_PARTY & 0xFF] = CPacketHandler::decodeBanishParty;
        DIRECT_OPCODE[Opcodes.C_BANISH_PARTY & 0xFF] = false;
        HANDLERS[Opcodes.C_LOGOUT & 0xFF] = (buffer, client) -> {
            handleLogout(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_LOGOUT & 0xFF] = false;
        HANDLERS[Opcodes.C_INVITE_PARTY_TARGET & 0xFF] = CPacketHandler::decodeInvitePartyTarget;
        DIRECT_OPCODE[Opcodes.C_INVITE_PARTY_TARGET & 0xFF] = false;
        HANDLERS[Opcodes.C_BOARD_DELETE & 0xFF] = CPacketHandler::decodeBoardDelete;
        DIRECT_OPCODE[Opcodes.C_BOARD_DELETE & 0xFF] = false;
        HANDLERS[Opcodes.C_EXIT_GHOST & 0xFF] = (buffer, client) -> {
            handleExitGhost(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_EXIT_GHOST & 0xFF] = false;
        HANDLERS[Opcodes.C_MATCH_MAKING & 0xFF] = CPacketHandler::decodeMatchMaking;
        DIRECT_OPCODE[Opcodes.C_MATCH_MAKING & 0xFF] = false;
        HANDLERS[Opcodes.C_CHECK_INVENTORY & 0xFF] = CPacketHandler::decodeCheckInventory;
        DIRECT_OPCODE[Opcodes.C_CHECK_INVENTORY & 0xFF] = false;
        HANDLERS[Opcodes.C_ENTER_PORTAL & 0xFF] = CPacketHandler::decodeEnterPortal;
        DIRECT_OPCODE[Opcodes.C_ENTER_PORTAL & 0xFF] = false;
        HANDLERS[Opcodes.C_THROW & 0xFF] = (buffer, client) -> {
            handleThrow(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_THROW & 0xFF] = false;
        HANDLERS[Opcodes.C_GOTO_PORTAL & 0xFF] = CPacketHandler::decodeEnterPortal;
        DIRECT_OPCODE[Opcodes.C_GOTO_PORTAL & 0xFF] = false;
        HANDLERS[Opcodes.C_WAR & 0xFF] = CPacketHandler::decodeWar;
        DIRECT_OPCODE[Opcodes.C_WAR & 0xFF] = false;
        HANDLERS[Opcodes.C_BOARD_WRITE & 0xFF] = CPacketHandler::decodeBoardWrite;
        DIRECT_OPCODE[Opcodes.C_BOARD_WRITE & 0xFF] = false;
        HANDLERS[Opcodes.C_VOICE_CHAT & 0xFF] = CPacketHandler::decodeReturnStatus;
        DIRECT_OPCODE[Opcodes.C_VOICE_CHAT & 0xFF] = false;
        HANDLERS[Opcodes.C_JOIN_PLEDGE & 0xFF] = (buffer, client) -> {
            handleJoinPledge(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_JOIN_PLEDGE & 0xFF] = false;
        HANDLERS[Opcodes.C_TAX & 0xFF] = CPacketHandler::decodeTax;
        DIRECT_OPCODE[Opcodes.C_TAX & 0xFF] = false;
        HANDLERS[Opcodes.C_BUY_SELL & 0xFF] = CPacketHandler::decodeBuySell;
        DIRECT_OPCODE[Opcodes.C_BUY_SELL & 0xFF] = false;
        HANDLERS[Opcodes.C_ONOFF & 0xFF] = CPacketHandler::decodeOnOff;
        DIRECT_OPCODE[Opcodes.C_ONOFF & 0xFF] = false;
        HANDLERS[Opcodes.C_DEPOSIT & 0xFF] = CPacketHandler::decodeDeposit;
        DIRECT_OPCODE[Opcodes.C_DEPOSIT & 0xFF] = false;
        HANDLERS[Opcodes.C_BOARD_LIST & 0xFF] = CPacketHandler::decodeBoardList;
        DIRECT_OPCODE[Opcodes.C_BOARD_LIST & 0xFF] = false;
        HANDLERS[Opcodes.C_MAIL & 0xFF] = CPacketHandler::decodeMail;
        DIRECT_OPCODE[Opcodes.C_MAIL & 0xFF] = false;
        HANDLERS[Opcodes.C_PLEDGE_WATCH & 0xFF] = CPacketHandler::decodePledgeWatch;
        DIRECT_OPCODE[Opcodes.C_PLEDGE_WATCH & 0xFF] = false;
        HANDLERS[Opcodes.C_RESTART & 0xFF] = (buffer, client) -> {
            handleCharSelect(client);
            return true;
        };
        DIRECT_OPCODE[Opcodes.C_RESTART & 0xFF] = false;
        HANDLERS[Opcodes.C_SUMMON & 0xFF] = CPacketHandler::decodeCallPlayer;
        DIRECT_OPCODE[Opcodes.C_SUMMON & 0xFF] = false;
        HANDLERS[Opcodes.C_CHAT_PARTY_CONTROL & 0xFF] = CPacketHandler::decodeChatPartyControl;
        DIRECT_OPCODE[Opcodes.C_CHAT_PARTY_CONTROL & 0xFF] = false;
        HANDLERS[Opcodes.C_LOGIN & 0xFF] = CPacketHandler::decodeAuthLogin;
        DIRECT_OPCODE[Opcodes.C_LOGIN & 0xFF] = false;
        HANDLERS[Opcodes.C_ENTER_WORLD & 0xFF] = CPacketHandler::decodeEnterWorld;
        DIRECT_OPCODE[Opcodes.C_ENTER_WORLD & 0xFF] = false;
        HANDLERS[Opcodes.C_ATTACK_CONTINUE & 0xFF] = CPacketHandler::decodeAutoAttack;
        DIRECT_OPCODE[Opcodes.C_ATTACK_CONTINUE & 0xFF] = true;
        HANDLERS[Opcodes.C_CREATE_PLEDGE & 0xFF] = CPacketHandler::decodeCreatePledge;
        DIRECT_OPCODE[Opcodes.C_CREATE_PLEDGE & 0xFF] = false;
        HANDLERS[Opcodes.C_BAN_MEMBER & 0xFF] = CPacketHandler::decodeBanMember;
        DIRECT_OPCODE[Opcodes.C_BAN_MEMBER & 0xFF] = false;
        HANDLERS[Opcodes.C_ACTION & 0xFF] = CPacketHandler::decodeExtraCommand;
        DIRECT_OPCODE[Opcodes.C_ACTION & 0xFF] = false;
        HANDLERS[Opcodes.C_CHANGE_CASTLE_SECURITY & 0xFF] = CPacketHandler::decodeChangeCastleSecurity;
        DIRECT_OPCODE[Opcodes.C_CHANGE_CASTLE_SECURITY & 0xFF] = false;
        HANDLERS[Opcodes.C_ANSWER & 0xFF] = CPacketHandler::decodeAnswer;
        DIRECT_OPCODE[Opcodes.C_ANSWER & 0xFF] = false;
        HANDLERS[Opcodes.C_DESTROY_ITEM & 0xFF] = CPacketHandler::decodeDestroyItem;
        DIRECT_OPCODE[Opcodes.C_DESTROY_ITEM & 0xFF] = false;
        HANDLERS[Opcodes.C_DIALOG & 0xFF] = CPacketHandler::decodeNpcTalk;
        DIRECT_OPCODE[Opcodes.C_DIALOG & 0xFF] = false;
        HANDLERS[Opcodes.C_BUY_SPELL & 0xFF] = CPacketHandler::decodeBuySpell;
        DIRECT_OPCODE[Opcodes.C_BUY_SPELL & 0xFF] = false;
    }

    private CPacketHandler() {}

    public static boolean isFastOpcode(int opcode) {
        return opcode >= 0 && opcode < 256 && HANDLERS[opcode] != null;
    }

    public static boolean isDirectOpcode(int opcode) {
        return opcode >= 0 && opcode < 256 && DIRECT_OPCODE[opcode];
    }

    /** Opcodes able to enter/leave a session or dispatch a stateful subprotocol.
     * These remain synchronized with state-changing CacheD completions.
     */
    public static boolean isSessionOpcode(int opcode) {
        return l1j.server.server.model.network.core.connection.CUserSocket.isSessionOpcode(opcode);
    }

    // ------------------------------------------------------------------
    // 문 / 상자 열기 (OPEN)
    // ------------------------------------------------------------------

    static boolean decodeOpen(ReadBuffer buffer, CIOClient client) {

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

    static boolean decodeMove(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 5) {
            return true;
        }
        final int x = buffer.readShort(1) & 0xFFFF;
        final int y = buffer.readShort(3) & 0xFFFF;
        final int h = buffer.readByte(5) & 0xFF;

        handleMove(client, x, y, h);
        return true;
    }

    // ------------------------------------------------------------------
    // 공격 / 원거리 공격 (ATTACK / FAR ATTACK)
    // ------------------------------------------------------------------

    static boolean decodeAttack(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 8) {
            return true;
        }
        final int targetId = buffer.readInt(1);
        final int x = buffer.readShort(5) & 0xFFFF;
        final int y = buffer.readShort(7) & 0xFFFF;

        handleAttack(client, targetId, x, y);
        return true;
    }

    // ------------------------------------------------------------------
    // 자동 공격 (AUTO ATTACK)
    // ------------------------------------------------------------------

    static boolean decodeAutoAttack(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int targetId = buffer.readInt(1);

        handleAutoAttack(client, targetId);
        return true;
    }

    // ------------------------------------------------------------------
    // 스킬 사용 (USE SKILL)
    // ------------------------------------------------------------------

    static boolean decodeUseSkill(ReadBuffer buffer, CIOClient client) {
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

        handleUseSkill(client, skillId, targetId, targetX, targetY, message);
        return true;
    }

    // ------------------------------------------------------------------
    // 아이템 줍기 (PICKUP)
    // ------------------------------------------------------------------

    static boolean decodePickup(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeChangeHeading(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeChat(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final String chatText = readCStringMs949(buffer, 2, buffer.remaining() - 1);

        handleChat(client, chatText);
        return true;
    }

    static boolean decodeAuthLogin(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeEnterWorld(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize <= 0) {
            return true;
        }

        final String charName = readCStringMs949(buffer, 1, payloadSize);

        handleEnterWorld(client, charName);
        return true;
    }

    private static int findCStringEnd(ReadBuffer buffer, int start, int limit) {
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

    private static String readCStringMs949(ReadBuffer buffer, int index, int available) {
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

    static boolean decodeEnterPortal(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int locx = buffer.readShort(1) & 0xFFFF;
        final int locy = buffer.readShort(3) & 0xFFFF;

        handleEnterPortal(client, locx, locy);
        return true;
    }

    static boolean decodeAnswer(ReadBuffer buffer, CIOClient client) {
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
            case CS_Attr.MSGCODE_622_Lawful:
            case CS_Attr.MSGCODE_622_Name:
            case CS_Attr.MSGCODE_622_Card2:
            case CS_Attr.MSGCODE_622_Card3:
            case CS_Attr.MSGCODE_622_LFC:
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

    static boolean decodeCreateCustomCharacter(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeChannel(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 1) {
            return true;
        }

        final int limit = 1 + payloadSize;
        final int type = buffer.readByte(1) & 0xFF;

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

    private static PasswordRead readSecondPassword(ReadBuffer buffer, int start, int limit) {
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

    static boolean decodeDeleteCharacter(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeDestroyItem(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeGiveItem(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeUploadEmblem(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBookmark(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeNpcTalk(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 4) {
            return true;
        }
        final int objId = buffer.readInt(1);

        handleNpcTalk(client, objId);
        return true;
    }

    static boolean decodeCreatePledge(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeReturnStatus(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeWar(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeDeleteBookmark(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodePersonalShop(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeWho(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBuySpell(ReadBuffer buffer, CIOClient client) {
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
        int level1Cost = 0, level2Cost = 0, level3Cost = 0;
        int offset = 3;

        for (int i = 0; i < count; i++, offset += 4) {
            final int sid = buffer.readInt(offset);
            if (sid > 24) {
                handleBuySpell(client, 0, 0, 0, 1, 0L);
                return true;
            }
            if (sid >= 0 && sid <= 7) {
                level1 |= 1 << sid;
                level1Cost += 100;
            } else if (sid >= 8 && sid <= 15) {
                level2 |= 1 << (sid - 8);
                level2Cost += 400;
            } else if (sid >= 16 && sid <= 23) {
                level3 |= 1 << (sid - 16);
                level3Cost += 900;
            }
        }

        final long packedCost = ((long) level1Cost) | ((long) level2Cost << 20) | ((long) level3Cost << 40);
        handleBuySpell(client, level1, level2, level3, 0, packedCost);
        return true;
    }

    static boolean decodeExtraCommand(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 1) {
            return true;
        }
        final int actionId = buffer.readByte(1) & 0xFF;

        handleExtraCommand(client, actionId);
        return true;
    }

    static boolean decodeDropItem(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeSaveIo(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeNpcAction(ReadBuffer buffer, CIOClient client) {
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
            final L1Object obj = L1World.getInstance().findObject(objid);
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

    static boolean decodeChatPartyControl(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeUseItem(ReadBuffer buffer, CIOClient client) {
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

    private static boolean dispatchUseItem(CIOClient client, int itemObjId, int targetId, int x, int y, String name, int skillId) {
        handleUseItem(client, itemObjId, targetId, x, y, skillId, name);
        return true;
    }

    static boolean decodeMarriage(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeSlaveControl(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 9) {
            return true;
        }
        final int petId = buffer.readInt(1);
        final int targetId = buffer.readInt(6);

        handleSlaveControl(client, petId, targetId);
        return true;
    }

    static boolean decodeRankControl(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeHypertextInputResult(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 10) {
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

    static boolean decodeBuySell(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        if (payloadSize < 7) {
            return true;
        }

        final int npcObjectId = buffer.readInt(1);
        final int resultType = buffer.readByte(5) & 0xFF;
        final int size = buffer.readByte(6) & 0xFF;
        final int bodyBytes = payloadSize - 7;
        final int hasFirst = (size > 0 || resultType == 3) ? 1 : 0;
        long packed0 = 0L;

        if (hasFirst != 0 && bodyBytes > 0) {
            final int firstBytes = Math.min(bodyBytes, 8);
            for (int i = 0; i < firstBytes; i++) {
                packed0 |= ((long) buffer.readByte(8 + i) & 0xFFL) << (i << 3);
            }
        }

        byte[] rest = null;
        if (size > 1) {
            final int restLength = (size - 1) << 3;
            rest = new byte[restLength];
            int available = bodyBytes - 8;
            if (available > 0) {
                if (available > restLength) {
                    available = restLength;
                }
                for (int i = 0; i < available; i++) {
                    rest[i] = buffer.readByte(16 + i);
                }
            }
        }

        handleBuySell(client, npcObjectId, resultType, size, hasFirst, packed0, rest);
        return true;
    }

    static boolean decodeNpcItemControl(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 6) {
            return true;
        }

        final int data = buffer.readByte(1) & 0xFF;
        final int petId = buffer.readInt(2);
        final int listNo = buffer.readByte(6) & 0xFF;

        handleNpcItemControl(client, data, petId, listNo);
        return true;
    }

    static boolean decodeInvitePartyTarget(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeCheckInventory(ReadBuffer buffer, CIOClient client) {
        if (buffer.remaining() < 4) {
            return true;
        }

        final int petId = buffer.readInt(1);

        handleCheckInventory(client, petId);
        return true;
    }

    static boolean decodeOnOff(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeQueryCastleSecurity(ReadBuffer buffer, CIOClient client) {
        int objId = 0;
        if (buffer.remaining() >= 4) {
            objId = buffer.readInt(1);
        }

        handleQueryCastleSecurity(client, objId);
        return true;
    }

    static boolean decodeWarehouseControl(ReadBuffer buffer, CIOClient client) {
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

    private static int readCH3(ReadBuffer buffer, int offset) {
        return (buffer.readByte(offset) & 0xFF) | ((buffer.readByte(offset + 1) & 0xFF) << 8) | ((buffer.readByte(offset + 2) & 0xFF) << 16);
    }

    static boolean decodeEmblem(ReadBuffer buffer, CIOClient client) {
        int emblemId = 0;
        if (buffer.remaining() >= 4) {
            emblemId = buffer.readInt(1);
        }

        handleEmblem(client, emblemId);
        return true;
    }

    static boolean decodeMatchMaking(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int type = 0, objid = 0, htype = 0;
        String text = null;

        if (payloadSize >= 1) {
            type = buffer.readByte(1) & 0xFF;
        }

        switch (type) {
        case 0:
            if (payloadSize >= 2) {
                htype = buffer.readByte(2) & 0xFF;
            }
            if (payloadSize > 2) {
                final int textStart = 3;
                final int limit = 1 + payloadSize;
                final int textEnd = findCStringEnd(buffer, textStart, limit);
                if (textEnd >= 0) {
                    text = textEnd == textStart ? "" : readCStringMs949(buffer, textStart, textEnd - textStart);
                }
            }
            break;
        case 5:
            if (payloadSize >= 5) {
                objid = buffer.readInt(2);
            }
            break;
        case 6:
            if (payloadSize >= 5) {
                objid = buffer.readInt(2);
            }
            if (payloadSize >= 6) {
                htype = buffer.readByte(6) & 0xFF;
            }
            break;
        default:
            break;
        }

        handleMatchMaking(client, type, objid, htype, text);
        return true;
    }

    static boolean decodeQueryPersonalShop(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeAddExchange(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeChangeCastleSecurity(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeRemoveBuddy(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBanishParty(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBoardDelete(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBoardWrite(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeTax(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int taxRate = 0;

        if (payloadSize >= 5) {
            taxRate = buffer.readByte(5) & 0xFF;
        }

        handleTax(client, taxRate);
        return true;
    }

    static boolean decodeDeposit(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBoardList(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeMail(ReadBuffer buffer, CIOClient client) {
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

    private static final Charset MAIL_EUC_KR = Charset.forName("EUC-KR");
    private static final Charset MAIL_MS949 = Charset.forName("MS949");
    private static final ThreadLocal<byte[]> MAIL_STRING_BUFFER = ThreadLocal.withInitial(() -> new byte[4096]);

    private static int findMailCStringEnd(ReadBuffer buffer, int start, int limit) {
        for (int pos = start; pos < limit; pos++) {
            if (buffer.readByte(pos) == 0) {
                return pos;
            }
        }
        return limit;
    }

    private static String readMailCStringEucKr(ReadBuffer buffer, int start, int end) {
        final byte[] temp = MAIL_STRING_BUFFER.get();
        final int length = Math.min(end - start, temp.length);

        for (int i = 0; i < length; i++) {
            temp[i] = buffer.readByte(start + i);
        }
        return new String(temp, 0, length, MAIL_EUC_KR);
    }

    private static int findMailSsNext(ReadBuffer buffer, int start, int limit) {
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

    private static String readMailSs(ReadBuffer buffer, int start, int next) {
        final byte[] temp = MAIL_STRING_BUFFER.get();
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
        return new String(temp, 0, index, MAIL_MS949);
    }

    private static final Charset CS_EUC_KR = Charset.forName("EUC-KR");
    private static final ThreadLocal<byte[]> EUC_STRING_BUFFER = ThreadLocal.withInitial(() -> new byte[4096]);

    private static String readCStringEucKr(ReadBuffer buffer, int start, int end) {
        final int rawLength = end - start;
        if (rawLength <= 0) {
            return "";
        }

        final byte[] temp = EUC_STRING_BUFFER.get();
        final int length = Math.min(rawLength, temp.length);

        for (int i = 0; i < length; i++) {
            temp[i] = buffer.readByte(start + i);
        }
        return new String(temp, 0, length, CS_EUC_KR);
    }

    static boolean decodePledgeContent(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeCallPlayer(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBanMember(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeExchangeableSpell(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int value = 0;

        if (payloadSize >= 4) {
            value = buffer.readInt(1);
        }

        handleExchangeableSpell(client, value);
        return true;
    }

    static boolean decodeWithdraw(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int drawAmount = 0;

        if (payloadSize >= 8) {
            drawAmount = buffer.readInt(5);
        }

        handleWithdraw(client, drawAmount);
        return true;
    }

    static boolean decodeAddBuddy(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeBuyableSpell(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int id = 0;

        if (payloadSize >= 4) {
            id = buffer.readInt(1);
        }

        handleBuyableSpell(client, id);
        return true;
    }

    static boolean decodePlate(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int objectId = 0;

        if (payloadSize >= 4) {
            objectId = buffer.readInt(1);
        }

        handlePlate(client, objectId);
        return true;
    }

    static boolean decodeFix(ReadBuffer buffer, CIOClient client) {
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

    static boolean decodeExtended(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int type = 0;

        if (payloadSize >= 2) {
            type = buffer.readShort(1) & 0xFFFF;
            CNetworkDiagnostics.subcommand(Opcodes.C_EXTENDED & 255,
                    CNetworkDiagnostics.PacketKind.EXTENDED, type);
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

    
    static boolean decodePledgeWatch(ReadBuffer buffer, CIOClient client) {
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
    
    static boolean decodeBoardRead(ReadBuffer buffer, CIOClient client) {
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

    
    static boolean decodeTitle(ReadBuffer buffer, CIOClient client) {
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
    
    
    
    
    static boolean decodeExchangeSpell(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();
        int count = 0;

        if (payloadSize >= 2) {
            count = buffer.readShort(1) & 0xFFFF;
        }

        int sid0 = 0, sid1 = 0, sid2 = 0, sid3 = 0, sid4 = 0, sid5 = 0, sid6 = 0;
        Object tailObject = null;

        if (count > 0) {
            final int limit = 1 + payloadSize;
            int pos = 3;
            final int directCount = Math.min(count, 7);

            for (int i = 0; i < directCount; i++) {
                int sid = 0;

                if (limit - pos >= 4) {
                    sid = buffer.readInt(pos);
                    pos += 4;
                }

                switch (i) {
                case 0: sid0 = sid; break;
                case 1: sid1 = sid; break;
                case 2: sid2 = sid; break;
                case 3: sid3 = sid; break;
                case 4: sid4 = sid; break;
                case 5: sid5 = sid; break;
                case 6: sid6 = sid; break;
                }
            }

            if (count > 7 && pos < limit) {
                final int maxTailBytes = (count - 7) * 4;
                final int length = Math.min(limit - pos, maxTailBytes);

                if (length > 0) {
                    final byte[] tail = new byte[length];
                    for (int i = 0; i < length; i++) {
                        tail[i] = buffer.readByte(pos + i);
                    }
                    tailObject = tail;
                }
            }
        }

        final long packed34 = ((long) sid3 << 32) | (sid4 & 0xFFFFFFFFL);
        final long packed56 = ((long) sid5 << 32) | (sid6 & 0xFFFFFFFFL);

        handleExchangeSpell(client, count, sid0, sid1, sid2, packed34, packed56, tailObject);
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

    private static final Charset CRAFT_MS949 = Charset.forName("MS949");
    private static final ThreadLocal<byte[]> CRAFT_STRING_BUFFER = ThreadLocal.withInitial(() -> new byte[256]);
    
    private static boolean dispatchExtendedProto(ReadBuffer buffer, CIOClient client, int payloadSize) {
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
    
    private static boolean decodeCraft(ReadBuffer buffer, CIOClient client, int type, int payloadSize) {
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

                            s0 = new String(scratch, 0, stringLength, CRAFT_MS949);
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
                s0 = new String(scratch, 0, messageLength, CRAFT_MS949);
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
                    s1 = new String(scratch, 0, targetLength, CRAFT_MS949);
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
    private static long readCraftVarInt(ReadBuffer buffer, int pos, int limit) {
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
    
    static boolean decodeExtendedMultiplexed(ReadBuffer buffer, CIOClient client) {
        final int payloadSize = buffer.remaining();

        if (payloadSize < 2) {
            return true;
        }

        final int msgId = buffer.readShort(1) & 0xFFFF;

        if (CSProto.isRoutable(msgId)) {
            CNetworkDiagnostics.subcommand(Opcodes.C_EXTENDED_PROTOBUF & 255,
                    CNetworkDiagnostics.PacketKind.PROTOBUF, msgId);
            return dispatchExtendedProto(buffer, client,  payloadSize);
        }

        final int craftType = buffer.readByte(1) & 0xFF;

        if (isCraftType(craftType)) {
            CNetworkDiagnostics.subcommand(Opcodes.C_EXTENDED_PROTOBUF & 255,
                    CNetworkDiagnostics.PacketKind.CRAFT, craftType);
            return decodeCraft(buffer, client,  craftType, payloadSize);
        }

        CNetworkDiagnostics.subcommand(Opcodes.C_EXTENDED_PROTOBUF & 255,
                CNetworkDiagnostics.PacketKind.ACTION_UI, msgId);
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

    private static final Charset ACTION_EUC_KR = Charset.forName("EUC-KR");
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final ThreadLocal<byte[]> ACTION_STRING_BUFFER = ThreadLocal.withInitial(() -> new byte[4096]);
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    
    private static boolean decodeActionUi(ReadBuffer buffer, CIOClient client, int type, int payloadSize) {
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
                final int maxEntries = Math.min(Math.max(0, newShopLength), Math.max(0, limit - pos));
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

                    if (entryCount < maxEntries) {
                        final int base = entryCount << 2;
                        entries[base] = code;
                        entries[base + 1] = objectId;
                        entries[base + 2] = price;
                        entries[base + 3] = count;
                        entryCount++;
                    }
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
            int size = 0;
            if (limit - pos >= 2) {
                size = (buffer.readByte(pos) & 0xFF) | ((buffer.readByte(pos + 1) & 0xFF) << 8);
                pos += 2;
            }

            final byte[] bytes = new byte[size];
            final int available = Math.min(size, Math.max(0, limit - pos));

            for (int i = 0; i < available; i++) {
                bytes[i] = buffer.readByte(pos + i);
            }

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
    
    private static String readActionHex(ReadBuffer buffer, int pos, int size, int limit) {
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
    private static long readActionVarInt(ReadBuffer buffer, int pos, int limit) {
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
    
    private static String readActionFixedEucKr(ReadBuffer buffer, int pos, int requestedLength, int limit) {
        if (requestedLength <= 0 || pos >= limit) {
            return null;
        }

        final int length = Math.min(requestedLength, limit - pos);
        final byte[] scratch = ACTION_STRING_BUFFER.get();

        for (int i = 0; i < length; i++) {
            scratch[i] = buffer.readByte(pos + i);
        }

        return new String(scratch, 0, length, ACTION_EUC_KR);
    }

    private static final Logger HANDLER_LOG =
            Logger.getLogger(CPacketHandler.class.getName());


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

    static void handleOpen( CIOClient client, int p0, int p1, int p2 ) {
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

    static void handleMove( CIOClient client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            move(client, p0, p1, p2);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAttack( CIOClient client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            attack(client, p0, p1, p2);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAutoAttack( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            autoAttack(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleUseSkill( CIOClient client, int p0, int p1, int p2, int p3, String s0 ) {
        if (!client.isConnected()) return;
        try {
            useSkill(client, p0, p1, p2, p3, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePickup( CIOClient client, int p0, int p1, int p2, long l0 ) {
        if (!client.isConnected()) return;
        try {
            pickup(client, p0, p1, p2, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChangeHeading( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            changeHeading(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChat( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Chat.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAuthLogin( CIOClient client, String s0, String s1 ) {
        if (!client.isConnected()) return;
        try {
            CS_AuthLogin.handle(client, s0, s1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleEnterWorld( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_EnterWorld.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleEnterPortal( CIOClient client, int p0, int p1 ) {
        if (!client.isConnected()) return;
        try {
            enterPortal(client, p0, p1);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAnswer( CIOClient client, int p0, int p1, int p2, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Attr.handle(client, p0, p1, p2, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCreateCustomCharacter( CIOClient client, int p0, int p1, int p2, int p3, long l0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            createCustomCharacter(client, s0, p0, p1, p2, p3, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleChannel( CIOClient client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Channel.handle(client, p0, p1, p2, p3, (int) l0, (int) l1, s0, s1, o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeleteCharacter( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DeleteChar.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDestroyItem( CIOClient client, int p0, int p1, int p2, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DestroyItem.handle(client, p0, p1, p2, (int[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeadRestart( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_Restart.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleGiveItem( CIOClient client, int p0, int p1, int p2 ) {
        if (!client.isConnected()) return;
        try {
            CS_GiveItem.handle(client, p0, p1, p2);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleUploadEmblem( CIOClient client, int p0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Emblem.handle(client, (byte[]) o0,  p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAlive( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            alive(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBookmark( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Bookmark.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCharSelect( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_CharSelect.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleNpcTalk( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_NpcTalk.handle(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCreatePledge( CIOClient client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CreatePledge.handle(client, s0, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReturnStatus( CIOClient client, int p0, int p1, int p2, long l0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ReturnStatus.handle(client, p0, p1, p2, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWar( CIOClient client, int p0, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_War.handle(client, p0, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeleteBookmark( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_DeleteBookmark.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePersonalShop( CIOClient client, int p0, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_PersonalShop.handle(client, p0, (byte[]) o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWho( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Who.handle(client, s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReturnSummon( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_ReturnSummon.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBuySpell( CIOClient client, int p0, int p1, int p2, int p3, long l0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BuySpell.handle(client, p0, p1, p2, p3, l0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExtraCommand( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExtraCommand.handle(client, p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleUseItem( CIOClient client, int p0, int p1, int p2, int p3, long l0, String s0 ) {
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

    static void handleDrop( CIOClient client, int p0, int p1, int p2, int p3, long l0, Object o0 ) {
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

    static void handleSaveio( CIOClient client, int p0, Object o0 ) {
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

    static void handleNpcAction( CIOClient client, int p0, String s0, String s1, Object o0 ) {
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

    static void handleChatPartyControl( CIOClient client, int p0, String s0 ) {
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

    static void handleMarriage( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Marriage.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleSlaveControl( CIOClient client, int p0, int p1 ) {
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

    static void handleWhoPledge( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_WhoPledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleRankControl( CIOClient client, int p0, int p1, String s0 ) {
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

    static void handleHypertextInputResult( CIOClient client, int p0, int p1, String s0 ) {
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

    static void handleBuySell( CIOClient client, int p0, int p1, int p2, int p3, long l0, Object o0 ) {
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

    static void handleNpcItemControl( CIOClient client, int p0, int p1, int p2 ) {
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

    static void handleLogout( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_Logout.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleInvitePartyTarget( CIOClient client, int p0, int p1, String s0 ) {
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

    static void handleCheckInventory( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CheckInventory.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleOnoff( CIOClient client, int p0, int p1 ) {
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

    static void handleQueryCastleSecurity( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_QueryCastleSecurity.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWarehouseControl( CIOClient client, int p0, int p1, int p2 ) {
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

    static void handleEmblem( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_EmblemRequest.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleMatchMaking( CIOClient client, int p0, int p1, int p2, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ClanMatching.handle(
                    client,
                    p0, // type
                    p1, // objid
                    s0, // text
                    p2  // htype
            );
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleQueryPersonalShop( CIOClient client, int p0, int p1 ) {
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

    static void handleAddXchg( CIOClient client, int p0, int p1 ) {
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

    static void handleChangeCastleSecurity( CIOClient client, int p0, int p1 ) {
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

    static void handleQueryBuddy( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_QueryBuddy.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleRemoveBuddy( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_RemoveBuddy.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBanishParty( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BanishParty.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardDelete( CIOClient client, int p0, int p1 ) {
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

    static void handleExitGhost( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_ExitGhost.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleThrow( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_Throw.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBoardWrite( CIOClient client, int p0, String s0, String s1 ) {
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

    static void handleJoinPledge( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_JoinPledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleTax( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Tax.handle(
                    client,
                    p0); // taxRate
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleDeposit( CIOClient client, int p0, int p1 ) {
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

    static void handleBoardList( CIOClient client, int p0, int p1 ) {
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

    static void handleMail( CIOClient client, int p0, int p1, int p2, String s0, String s1, Object o0 ) {
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

    static void handlePledgeContent( CIOClient client, int p0, String s0 ) {
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

    static void handleWhoParty( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_WhoParty.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCancelXchg( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_TradeCancel.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCallPlayer( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_CallPlayer.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBanMember( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BanMember.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExchangeableSpell( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExchangeableSpell.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleLeavePledge( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_LeavePledge.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAcceptXchg( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_TradeAccept.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleWithdraw( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Withdraw.handle(
                    client,
                    p0); // drawAmount
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAddBuddy( CIOClient client, String s0 ) {
        if (!client.isConnected()) return;
        try {
            CS_AddBuddy.handle(
                    client,
                    s0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleBuyableSpell( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_BuyableSpell.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePlate( CIOClient client, int p0 ) {
        if (!client.isConnected()) return;
        try {
            CS_Plate.handle(
                    client,
                    p0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleLeaveParty( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_LeaveParty.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleFix( CIOClient client, int p0, int p1 ) {
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

    static void handleExtended( CIOClient client, int p0, int p1, int p2, int p3, Object o0 ) {
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

    static void handleDuel( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_Duel.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handlePledgeWatch( CIOClient client, int p0, String s0 ) {
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

    static void handleBoardRead( CIOClient client, int p0, int p1 ) {
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

    static void handleQuit( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_Quit.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleTitle( CIOClient client, String s0, String s1 ) {
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

    static void handleCheckPk( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_CheckPk.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleFixableItem( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_FixableItem.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleAskXchg( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_AskExchange.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExchangeSpell( CIOClient client, int p0, int p1, int p2, int p3, long l0, long l1, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExchangeSpell.handle(
                    client,
                    p0,
                    p1,
                    p2,
                    p3,
                    l0,
                    l1,
                    o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleReadNews( CIOClient client ) {
        if (!client.isConnected()) return;
        try {
            CS_ReadNews.handle(client);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleExtendedProto( CIOClient client, Object o0 ) {
        if (!client.isConnected()) return;
        try {
            CS_ExtendedProto.handle(
                    client,
                    o0);
        } catch (Throwable failure) {
            reportHandlerError(failure);
        }
    }

    static void handleCraft( CIOClient client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1 ) {
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

    static void handleActionUi( CIOClient client, int p0, int p1, int p2, int p3, long l0, long l1, String s0, String s1, Object o0 ) {
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

    // A disconnected/old CIOClient may still retain its previous activeChar
    // while the new login owns that PC. Never enqueue old-session input into
    // the existing 32-slot controller or mutate world state with that actor.
    private static boolean ownsCurrentSession(CIOClient client, L1PcInstance pc) {
        return client != null && client.isConnected()
                && pc != null && pc.getNetConnection() == client;
    }

	// ------------------------------------------------------------------
	// 이동 (MOVE)
	// ------------------------------------------------------------------

	private static void move(CIOClient client, int x, int y, int h) {
		final L1PcInstance pc = client.getActiveChar();

		if (!ownsCurrentSession(client, pc) || pc.isDead()) {
			return;
		}
		pc.getCreatureController().Move(x, y, h);
	}

	// ------------------------------------------------------------------
	// 공격 / 원거리 공격 (ATTACK / FAR ATTACK)
	// ------------------------------------------------------------------

	private static void attack(CIOClient client, int targetId, int x, int y) {

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

	private static void autoAttack(CIOClient client, int targetId) {

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

	private static void useSkill(CIOClient client, int skillId, int targetId, int targetX, int targetY, String message) {

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

	private static void pickup(CIOClient client, int x, int y, int objId, long amount) {
		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();
		if (!ownsCurrentSession(client, pc) || pc.isDead() || pc.isGhost()
                || pc.isInvisble() || pc.isInvisDelay()) {
			return;
		}
		if (MJCommons.isNonAction(pc)) {
			return;
		}
		if (pc.getOnlineStatus() != 1) {
			return;
		}
		pc.getCreatureController().GetItem(x, y, objId, amount);
	}


	private static void changeHeading(CIOClient client, int heading) {

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

	private static void enterPortal(CIOClient client, int locx, int locy) {
		final L1PcInstance pc = client.getActiveChar();
		if (!ownsCurrentSession(client, pc)) {
			return;
		}
		Dungeon.getInstance().dg(locx, locy, pc.getMap().getId(), pc);
	}
	
	private static void createCustomCharacter(CIOClient client, String name, int type, int sex, int baseStr, int baseDex, long packedStats) {

	    final int baseCon = (int) (packedStats       & 0xFFL);
	    final int baseWis = (int) ((packedStats >>> 8)  & 0xFFL);
	    final int baseCha = (int) ((packedStats >>> 16) & 0xFFL);
	    final int baseInt = (int) ((packedStats >>> 24) & 0xFFL);
	    CS_CreateChar.handle(client, name, type, sex, baseStr, baseDex, baseCon, baseWis, baseCha, baseInt);
	}

	private static void alive(CIOClient client) {
	    final L1PcInstance pc = client.getActiveChar();

	    if (pc == null) {
	        return;
	    }
	    SC_GameTime.send(pc);
	}
}
