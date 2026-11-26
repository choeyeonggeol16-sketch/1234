package l1j.server.server.model.BookQuest;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQRewardsLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.MonsterBookV2Info.DeckT;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.MonsterBookV2Info.SystemT.RewardT;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.eMonsterBookV2DeckState;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.eMonsterBookV2RewardGrade;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_MONSTER_BOOK_V2_REWARD_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** One claim for a completed character, week and deck, irrespective of reward grade. */
public final class WeeklyMonsterBookRewards {
    private static final Logger LOG = Logger.getLogger(WeeklyMonsterBookRewards.class.getName());
    private record ExperienceCredit(int level, int before, int after) {
        int amount() { return after - before; }
    }

    private WeeklyMonsterBookRewards() {}

    public static void claim(L1PcInstance pc, int deckIndex, eMonsterBookV2RewardGrade grade) {
        if (pc == null) return;
        if (grade == null) {
            reject(pc, deckIndex, null, eResultCode.REWARD_FAIL);
            return;
        }
        if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
            reject(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
            pc.sendPackets("현재 도감 시스템이 일시 중지 상태입니다.");
            return;
        }

        final BQSCharacterData book = pc.getBqs();
        if (book == null) return;
        final DeckT deck = book.get_deck(deckIndex);
        if (deck == null) {
            reject(pc, deckIndex, grade, eResultCode.REWARD_FAIL_INVALID_INDEX);
            return;
        }
        final var state = deck.get_state();
        if (state != eMonsterBookV2DeckState.DS_COMPLETED) {
            reject(pc, deckIndex, grade, state == eMonsterBookV2DeckState.DS_IN_PROGRESS
                    ? eResultCode.REWARD_FAIL_NOT_ENOUGH_AMOUNT : eResultCode.REWARD_FAIL);
            return;
        }
        final long version = book.get_decks_version();
        final Calendar activeWeek = BQSLoadManager.BQS_UPDATE_CALENDAR;
        if (activeWeek == null || !book.has_decks_version()
                || Math.abs(activeWeek.getTimeInMillis() - version) > 1000L
                || book.get_object_id() != pc.getId() || pc.isItemTransferBusy()) {
            reject(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
            return;
        }

        final eResultCode failure;
        try {
            final var rewards = BQSWQRewardsLoader.getInstance().get();
            final RewardT reward = rewards == null ? null : rewards.get_rewards(grade);
            failure = reward == null ? eResultCode.REWARD_FAIL
                    : submit(pc, book, deck, version, deckIndex, grade, reward);
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Weekly book reward was not admitted for character " + pc.getId(), ex);
            reject(pc, deckIndex, grade, eResultCode.REWARD_FAIL);
            return;
        }
        if (failure != null) reject(pc, deckIndex, grade, failure);
    }

    private static eResultCode submit(L1PcInstance pc, BQSCharacterData book, DeckT deck, long version,
            int deckIndex, eMonsterBookV2RewardGrade grade, RewardT reward) {
        final int characterId = pc.getId();
        final int fee = reward.get_item_id();
        final int rewardExp = reward.get_reward_exp();
        if (fee < 0 || rewardExp < 0) return eResultCode.REWARD_FAIL;
        // CacheD suppresses ordinary character saves while a receipt owns
        // this character. Put the current EXP preimage ahead of that receipt.
        final ExperienceCredit experience = captureExperience(pc, rewardExp);
        try (var tx = L1InventoryTransaction.begin(pc, CacheD.accountOrderKey("monster-book:", characterId))) {
            if (fee > 0 && !tx.debit(fee, 1)) return eResultCode.REWARD_FAIL_NOT_ENOUGH_ITEM;
            if (reward.has_item_rewards()) {
                for (var item : reward.get_item_rewards()) {
                    if (tx.grant(item.get_item_id(), item.get_amount()) == null) return eResultCode.REWARD_FAIL;
                }
            }
            if (experience != null) stageExperience(tx, characterId, experience);
            BQSCharacterDataLoader.stageWeeklyReward(tx, characterId, version, deckIndex);
            tx.onFailure(() -> reject(pc, deckIndex, grade, eResultCode.REWARD_FAIL));
            tx.commitAfterInventoryRelease(() -> {
                if (matchesCurrentDeck(pc, book, deck, version, deckIndex))
                    deck.set_state(eMonsterBookV2DeckState.DS_REWARDED);
            }, () -> {
                if (experience != null) applyExperience(pc, experience.amount());
                // A weekly allocation during a receipt owns its own, new deck.
                if (matchesCurrentDeck(pc, book, deck, version, deckIndex)) {
                    SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, eResultCode.REWARD_SUCCESS);
                    SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI.send(pc, deck);
                }
            });
            return null;
        }
    }

    private static boolean matchesCurrentDeck(L1PcInstance pc, BQSCharacterData book, DeckT deck,
            long version, int index) {
        return pc.getBqs() == book && book.get_decks_version() == version && book.get_deck(index) == deck;
    }

    private static ExperienceCredit captureExperience(L1PcInstance pc, int rewardExp) {
        if (rewardExp <= 0 || Config.STANDBY_SERVER) return null;
        pc.lockUserState();
        try {
            final int level = pc.getLevel();
            final int before = pc.getExp();
            final int credit = (int) (rewardExp * ExpTable.getPenaltyRate(level));
            final int after = (int) Math.min(ExpTable.MAX_EXP, (long) before + credit);
            if (after <= before) return null;
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            return new ExperienceCredit(level, before, after);
        } finally {
            pc.unlockUserState();
        }
    }

    private static void stageExperience(L1InventoryTransaction tx, int characterId, ExperienceCredit credit) {
        tx.requireTables("characters");
        tx.sql(con -> {
            try (PreparedStatement update = con.prepareStatement(
                    "UPDATE characters SET Exp=? WHERE objid=? AND level=? AND Exp=?")) {
                update.setInt(1, credit.after());
                update.setInt(2, characterId);
                update.setInt(3, credit.level());
                update.setInt(4, credit.before());
                if (update.executeUpdate() != 1)
                    throw new SQLException("Weekly book experience preimage changed: " + characterId);
            }
        });
    }

    private static void applyExperience(L1PcInstance pc, int credit) {
        if (credit == 0) return;
        pc.lockUserState();
        try {
            if (Config.STANDBY_SERVER)
                throw new IllegalStateException("Standby changed after committing weekly book experience");
            final int current = pc.getExp();
            final int allowed = (int) Math.min(credit, (long) ExpTable.MAX_EXP - current);
            if (allowed > 0) pc.addExp(allowed);
            // Preserve additional live EXP gained while the receipt was pending.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
        } finally {
            pc.unlockUserState();
        }
    }

    private static void reject(L1PcInstance pc, int deckIndex, eMonsterBookV2RewardGrade grade, eResultCode code) {
        SC_MONSTER_BOOK_V2_REWARD_ACK.send(pc, deckIndex, grade, code);
    }
}
