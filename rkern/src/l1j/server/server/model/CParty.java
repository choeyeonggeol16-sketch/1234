package l1j.server.server.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.drop.CContributeData;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST_CHANGE;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_MARK_CHANGE_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_STATUS;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_SYNC_PERIODIC_INFO;
import l1j.server.server.shared.GameRandom;

public class CParty {

    private static final Logger _log = Logger.getLogger(CParty.class.getName());

    private static final Object MEMBERSHIP = new Object();
    private static final Roster EMPTY = new Roster(new Slot[0], null);

    private static final class Slot {
        final L1PcInstance player;
        final int session;
        volatile int mark;

        Slot(L1PcInstance player) {
            this.player = player;
            session = player.getSessionStamp();
        }
    }

    private static final class Roster implements IntUnaryOperator {
        final Slot[] slots;
        final L1PcInstance[] players;
        final L1PcInstance leader;
        final List<L1PcInstance> view;

        Roster(Slot[] slots, L1PcInstance leader) {
            this.slots = slots;
            this.leader = leader;
            players = new L1PcInstance[slots.length];
            for (int i = 0; i < slots.length; i++) players[i] = slots[i].player;
            view = List.of(players);
        }

        int indexOf(L1PcInstance player) {
            for (int i = 0; i < players.length; i++) if (players[i] == player) return i;
            return -1;
        }

        boolean hasId(int id) {
            for (L1PcInstance player : players) if (player.getId() == id) return true;
            return false;
        }

        int mark(L1PcInstance player) {
            int index = indexOf(player);
            return index < 0 ? 0 : slots[index].mark;
        }

        public int applyAsInt(int index) { return slots[index].mark; }
    }

    private volatile Roster roster = EMPTY;
    private long lootCursor;
    private static final AtomicLong LOOT_IDENTITIES = new AtomicLong();
    private final long lootIdentity = LOOT_IDENTITIES.incrementAndGet();
    public final long GetLootIdentity() { return lootIdentity; }

    public synchronized int NextLootIndex(int size) {
        if (size <= 1) return 0;
        return (int) Math.floorMod(lootCursor++, (long) size);
    }

    private synchronized void ResetLootIndex() { lootCursor = 0L; }

    // ─────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────
    private static boolean ok(L1PcInstance pc) {
        return pc != null && pc.getNetConnection() != null;
    }

    public void broadcastRaw(byte[] data) {
        final Roster snapshot = roster;
        if (snapshot.players.length == 0) return;
        try (var shared = CSharedBroadcast.openRaw(data)) {
            if (shared == null) return;
            for (L1PcInstance member : snapshot.players)
                if (stillMember(snapshot, member)) CSharedBroadcast.sendRecipientBestEffort(shared, member);
        }
    }

    private void broadcastSnapshot(Roster snapshot, byte[] data) {
        if (snapshot.players.length == 0) return;
        try (var shared = CSharedBroadcast.openOwnedRaw(data)) {
            if (shared == null) return;
            for (L1PcInstance member : snapshot.players)
                if (stillMember(snapshot, member)) CSharedBroadcast.sendRecipientBestEffort(shared, member);
        }
    }

    private void sendPartyListTo(L1PcInstance target, Roster snapshot) {
        if (!ok(target) || !stillMember(snapshot, target)) return;
        try (var shared = CSharedBroadcast.openOwnedRaw(SC_PARTY_MEMBER_LIST.encodeConnectedMembers(
                snapshot.leader != null ? snapshot.leader.getName() : "", snapshot.players, snapshot))) {
            if (stillMember(snapshot, target)) CSharedBroadcast.sendRecipient(shared, target);
        }
    }

    private boolean stillMember(Roster snapshot, L1PcInstance player) {
        int index = snapshot.indexOf(player);
        if (index < 0 || player.getParty() != this || player.isLogoutStarted()) return false;
        Roster current = roster;
        int currentIndex = current.indexOf(player);
        return currentIndex >= 0 && current.slots[currentIndex] == snapshot.slots[index]
                && player.getSessionStamp() == snapshot.slots[index].session;
    }

    /** 탈퇴자/해산자 UI 완전 정리 — out_user + 빈 LIST (피바/HP바 잔존 방지) */
    private void clearPartyUIForLeaver(L1PcInstance leaver, CAsyncSendConstBuffer outUser) {
        if (!ok(leaver)) return;
        CSharedBroadcast.sendRecipient(outUser, leaver);
        try (var empty = CSharedBroadcast.openOwnedRaw(SC_PARTY_MEMBER_LIST.encodeEmpty())) {
            CSharedBroadcast.sendRecipient(empty, leaver);
        }
    }

    // ─────────────────────────────────────────────
    // party ops
    // ─────────────────────────────────────────────
    public void addMember(L1PcInstance pc) { tryAddMember(pc); }

    public boolean tryAddMember(L1PcInstance pc) {
        Objects.requireNonNull(pc);
        if (pc.getAutoHunt()) {
            pc.sendPackets("자동사냥 중에는 파티에 참가할 수 없습니다.");
            return false;
        }
        Roster added = null;
        synchronized (MEMBERSHIP) {
            final Roster current = roster;
            if (pc.getParty() == null && !pc.isLogoutStarted() && !current.hasId(pc.getId())
                    && (current.players.length < Config.MAX_PT || current.leader != null && current.leader.isGm()))
                added = append(current, pc);
        }
        if (added == null) SC_ServerMessage.send(pc, 417);
        else joined(pc, added);
        return added != null;
    }

    /** Publish a new inviter/replier pair together. An existing party keeps its leader and quota. */
    public static CParty acceptInvite(L1PcInstance inviter, L1PcInstance replier, int partyType) {
        if (inviter.getId() == replier.getId() || inviter.getAutoHunt() || replier.getAutoHunt()) return null;
        final CParty party;
        final Roster added;
        final boolean created;
        synchronized (MEMBERSHIP) {
            if (inviter.isLogoutStarted() || replier.isLogoutStarted() || replier.getParty() != null) return null;
            CParty existing = inviter.getParty();
            created = existing == null;
            if (created) {
                if (Config.MAX_PT < 2 && !inviter.isGm()) return null;
                party = new CParty();
                added = new Roster(new Slot[] {new Slot(inviter), new Slot(replier)}, inviter);
                inviter.setPartyType(partyType);
                inviter.setParty(party);
                replier.setParty(party);
                party.roster = added;
            } else {
                party = existing;
                Roster current = party.roster;
                if (current.leader != inviter || current.hasId(replier.getId())
                        || (current.players.length >= Config.MAX_PT && !inviter.isGm())) return null;
                added = party.append(current, replier);
            }
        }
        if (created) party.trackCrown(inviter, added);
        party.joined(replier, added);
        return party;
    }

    private Roster append(Roster current, L1PcInstance pc) {
        Slot[] slots = Arrays.copyOf(current.slots, current.slots.length + 1);
        slots[current.slots.length] = new Slot(pc);
        Roster added = new Roster(slots, current.leader == null ? pc : current.leader);
        pc.setParty(this);
        roster = added;
        return added;
    }

    private void trackCrown(L1PcInstance pc, Roster added) {
        if (pc.isCrown() && pc.HaveSkill(121)
                && stillMember(added, pc)) AuraController.getInstance().addMember(pc);
    }

    private void joined(L1PcInstance pc, Roster added) {
        if (pc.isLogoutStarted()) {
            leaveMember(pc);
            return;
        }
        trackCrown(pc, added);
        if (added.players.length <= 1 || !stillMember(added, pc)) return;
        SC_PARTY_MEMBER_LIST.sendMembers(pc, added.leader.getName(), added.players, added);
        if (added.players.length == 2) {
            sendPartyListTo(added.players[0], added);
            return;
        }
        try (var shared = CSharedBroadcast.openOwnedRaw(SC_PARTY_MEMBER_LIST_CHANGE.encodeNewMember(pc, 0))) {
            for (L1PcInstance member : added.players)
                if (member != pc && stillMember(added, member) && stillMember(added, pc))
                    CSharedBroadcast.sendRecipient(shared, member);
        }
    }

    private static boolean detached(Slot member) {
        return member.player.getParty() == null && member.player.getSessionStamp() == member.session;
    }

    private static void cleanupEffects(Slot member) {
        if (!detached(member)) return;
        var pc = member.player;
        if (pc.isCrown()) AuraController.getInstance().removeMember(pc);
        if (pc.getPbavatar()) AuraController.getInstance().brave_end(pc);
        if (pc._CubeEffect && detached(member)) {
            if (pc.hasSkillEffect(L1SkillId.CUBE_AVATAR)) pc.removeSkillEffect(L1SkillId.CUBE_AVATAR);
            if (pc.hasSkillEffect(L1SkillId.CUBE_RICH)) pc.removeSkillEffect(L1SkillId.CUBE_RICH);
            if (pc.hasSkillEffect(L1SkillId.CUBE_GOLEM)) pc.removeSkillEffect(L1SkillId.CUBE_GOLEM);
            if (pc.hasSkillEffect(L1SkillId.CUBE_OGRE)) pc.removeSkillEffect(L1SkillId.CUBE_OGRE);
            pc._CubeEffect = false;
        }
    }

    public boolean isVacancy() { return roster.players.length < Config.MAX_PT; }

    public int getVacancy() { return Config.MAX_PT - roster.players.length; }

    public boolean isMember(L1PcInstance pc) { return roster.indexOf(pc) >= 0; }

    public L1PcInstance getLeader() { return roster.leader; }

    public boolean isLeader(L1PcInstance pc) { return pc != null && roster.leader == pc; }

    public boolean isAutoDivision(L1PcInstance pc) {
        return pc.getPartyType() == 1 || pc.getPartyType() == 4;
    }

    public String getMembersNameList() {
        StringBuilder names = new StringBuilder();
        for (L1PcInstance pc : roster.players) names.append(pc.getName()).append(' ');
        return names.toString();
    }

    public void refresh(L1PcInstance pc) {
        final int partyColor = isLeader(pc) ? 2 : 1;
        try (var shared = new CAsyncSendConstBuffer(
                SC_Party.nativeNameColorWriter(pc.getId(), partyColor), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance member : roster.players) {
                if (member == null || pc.getId() == member.getId()) continue;
                CSharedBroadcast.sendRecipient(shared, member);
            }
        }
    }

    public void memberDie(L1PcInstance pc) {// 파티원 죽었을 때 — 상태갱신 (0x0339)
        refreshPartyMemberStatus(pc);
    }

    public void broadcastPartyList() {
        // LIST는 "소속되었습니다" 멘트 트리거 → 주기동기화로 대체
        refreshPartyList();
    }

    public void refreshPartyList() {
        final Roster snapshot = roster;
        if (snapshot.players.length == 0) return;
        broadcastSnapshot(snapshot, SC_PARTY_SYNC_PERIODIC_INFO.encodeConnectedMembers(snapshot.players, snapshot));
    }

    public void handshakePartyMemberStatus(L1PcInstance pc1, L1PcInstance pc2) {
        if (!ok(pc1) || !ok(pc2)) return;
        SC_PARTY_MEMBER_STATUS.sendFrom(pc2, pc1, roster.mark(pc1), true);
        SC_PARTY_MEMBER_STATUS.sendFrom(pc1, pc2, roster.mark(pc2), true);
    }

    public void refreshPartyMemberStatus(L1PcInstance pc) {
        final Roster snapshot = roster;
        if (!ok(pc) || !stillMember(snapshot, pc)) return;
        broadcastSnapshot(snapshot, SC_PARTY_MEMBER_STATUS.encodeFrom(pc, snapshot.mark(pc), true));
    }

    public void passLeader(L1PcInstance requester, L1PcInstance pc) {
        final Roster changed;
        synchronized (MEMBERSHIP) {
            Roster current = roster;
            if (requester != current.leader || pc == null || current.indexOf(pc) < 0) return;
            roster = changed = new Roster(current.slots, pc);
        }
        if (roster == changed) {
            broadcastSnapshot(changed, SC_PARTY_MEMBER_LIST_CHANGE.encodeLeaderChange(pc.getName()));
            refreshPartyList();
        }
    }

    public void leaveMember(L1PcInstance pc) { leave(pc, true, false); }

    public void kickMember(L1PcInstance pc) { leave(pc, false, true); }

    protected final void leave(L1PcInstance pc, boolean dissolveIfLeader, boolean kick) {
        final Roster previous;
        final Roster remaining;
        final Slot leaver;
        synchronized (MEMBERSHIP) {
            previous = roster;
            int index = previous.indexOf(pc);
            if (pc == null || index < 0) return;
            leaver = previous.slots[index];
            if ((dissolveIfLeader && previous.leader == pc) || previous.players.length == 2) {
                for (L1PcInstance player : previous.players)
                    if (player.getParty() == this) player.setParty(null);
                roster = remaining = EMPTY;
                ResetLootIndex();
            } else {
                Slot[] slots = new Slot[previous.slots.length - 1];
                System.arraycopy(previous.slots, 0, slots, 0, index);
                System.arraycopy(previous.slots, index + 1, slots, index, slots.length - index);
                remaining = slots.length == 0 ? EMPTY
                        : new Roster(slots, previous.leader == pc ? slots[0].player : previous.leader);
                if (pc.getParty() == this) pc.setParty(null);
                roster = remaining;
            }
        }
        if (remaining == EMPTY && previous.players.length > 1) {
            try (var shared = new CAsyncSendConstBuffer(
                    SC_ServerMessage.nativeWriter(418), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (Slot member : previous.slots) {
                    try {
                        cleanupEffects(member);
                        if (!detached(member) || !ok(member.player)) continue;
                        try (var out = CSharedBroadcast.openOwnedRaw(
                                SC_PARTY_MEMBER_LIST_CHANGE.encodeOutUser(member.player.getName()))) {
                            clearPartyUIForLeaver(member.player, out);
                        }
                        if (detached(member)) CSharedBroadcast.sendRecipientBestEffort(shared, member.player);
                    } catch (RuntimeException failure) {
                        _log.log(Level.WARNING, "Could not clear party UI for " + member.player.getId(), failure);
                    }
                }
            }
        } else {
            cleanupEffects(leaver);
            try (var out = CSharedBroadcast.openOwnedRaw(SC_PARTY_MEMBER_LIST_CHANGE.encodeOutUser(pc.getName()))) {
                for (L1PcInstance member : remaining.players)
                    if (stillMember(remaining, member)) CSharedBroadcast.sendRecipient(out, member);
                if (detached(leaver)) clearPartyUIForLeaver(pc, out);
            }
            if (remaining.leader != null && previous.leader != remaining.leader && roster == remaining)
                broadcastSnapshot(remaining, SC_PARTY_MEMBER_LIST_CHANGE.encodeLeaderChange(remaining.leader.getName()));
            try (var shared = new CAsyncSendConstBuffer(
                    SC_ServerMessage.nativeWriter(420, pc.getName()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (L1PcInstance member : remaining.players)
                    if (stillMember(remaining, member)) CSharedBroadcast.sendRecipientBestEffort(shared, member);
                if (!kick && detached(leaver)) CSharedBroadcast.sendRecipientBestEffort(shared, pc);
            }
            if (kick && detached(leaver)) SC_ServerMessage.send(pc, 419);
        }
        if (kick && detached(leaver)) SC_ServerMessage.send(pc, 419);
    }

    public L1PcInstance[] getMembers() { return roster.players.clone(); }

    public int getNumOfMembers() { return roster.players.length; }

    public List<L1PcInstance> getList() { return roster.view; }

    public Stream<L1PcInstance> createMembersStream() { return Arrays.stream(roster.players); }

    public void broadcastRootMent(String message) {
        if(message==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:roster.players){
                if(pc!=null && pc.RootMent1)
                    CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }

    

    /** Same map/range/hate policy for both currency and item routing. */
    private boolean CheckRouteCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        if (member == null || member.getParty() != this || member.isDead()
                || member.getMap() != origin.getMap()
                || member.getLocation().getTileLineDistance(origin) > Config.PARTY_LOOT_RANGE) return false;
        if (Config.PARTY_LOOT_ELIGIBILITY != 0) return true;
        if (snap == null) return false;
        return snap.IsRouteContributor(member.getId());
    }

    private boolean CheckRouteAdenaCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        return CheckRouteCond(origin, snap, member);
    }

    public boolean Route(L1NpcInstance npc, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner) {
        return npc != null && Route(npc.getLocation(), source, item, snap, primaryOwner, null);
    }

    /** Queued drops retain the original party, roster and death position. */
    public boolean Route(L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner, L1PcInstance[] admittedMembers) {
        if (!Config.PARTY_LOOT_ENABLE || primaryOwner == null || primaryOwner.getParty() != this
                || origin == null || source == null || item == null) return false;
        final CWorld world = CWorld.getInstance();
        if (!world.PickItemCond(source, item, item.getCount())) return false;

        final boolean adena = item.getItemId() == L1ItemId.ADENA;
        final L1PcInstance[] members = admittedMembers == null ? getMembers() : admittedMembers.clone();
        int size = 0;
        for (L1PcInstance member : members) {
            if (adena ? CheckRouteAdenaCond(origin, snap, member) : CheckRouteCond(origin, snap, member)) {
                members[size++] = member;
            }
        }
        if (size == 0) return false;

        final L1PcInstance leader = getLeader();
        final String tag = leader != null && leader.getPartyType() == 1 ? "자동 분배" : "일반 분배";
        if (adena) RouteAdena(world, origin, source, item, snap, members, size, tag);
        else RouteItem(world, origin, source, item, snap, members, size, tag);
        return true;
    }

    private void RouteAdena(CWorld world, L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance[] members, int size, String tag) {
        final int winnerIndex = GameRandom.nextInt(size);
        final long total = item.getCount();
        final long each = total / size;
        final long remainder = total % size;

        for (int i = 0; i < size; i++) {
            final int amount = (int) (each + (i == winnerIndex ? remainder : 0L));
            if (amount <= 0) continue;
            final L1PcInstance member = members[i];
            final L1PcInstance recipient = CheckRouteAdenaCond(origin, snap, member) ? member : null;
            final int result = world.PickItem(origin, source, item, amount, recipient);
            if (result == CWorld.PICK_INVENTORY && member.RootMent1) {
                try {
                    SC_SystemMessage.send(member, item.getNumberedName(amount)
                            + " 획득 : " + member.getName() + "(" + tag + ")");
                } catch (RuntimeException e) {
                    _log.log(Level.WARNING, "Party adena notification failed", e);
                }
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RouteItem(CWorld world, L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance[] members, int size, String tag) {
        final int mode = Config.PARTY_LOOT_MODE;
        // The previous general mode already chose a random eligible winner.
        // Random mode also had a uniform final selection; keep that distribution.
        final int index = mode == 2 ? NextLootIndex(size)
                : GameRandom.nextInt(size);
        final L1PcInstance target = members[index];
        final int amount = item.getCount();
        final L1PcInstance recipient = CheckRouteCond(origin, snap, target) ? target : null;
        final int result = world.PickItem(origin, source, item, amount, recipient);
        if (result == CWorld.PICK_INVENTORY) {
            try {
                final String msg = item.getNumberedName(amount) + " 획득 : "
                        + target.getName() + "(" + tag + ")";
                try (var shared = new CAsyncSendConstBuffer(
                        SC_SystemMessage.nativeWriter(msg), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                    for (int i = 0; i < size; i++) {
                        final L1PcInstance member = members[i];
                        if (member.getParty() == this && member.RootMent1)
                            CSharedBroadcast.sendRecipient(shared, member);
                    }
                }
            } catch (RuntimeException e) {
                _log.log(Level.WARNING, "Party item notification failed", e);
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RecoverRemainder(CWorld world, L1Location origin, CInventory source, L1ItemInstance item) {
        if (source.GetByServerID(item.getId()) != item || item.getCount() <= 0) return;
        if (!world.RecoverDropRemainder(origin, source, item)) {
            _log.warning("Party drop ground recovery rejected: item=" + item.getId()
                    + ", count=" + item.getCount());
        }
    }

    // ─────────────────────────────────────────────
    // 표식 — 0x0153 전용 NOTI + 상태 갱신
    // ─────────────────────────────────────────────
    public void setPartyMark(L1PcInstance requester, L1PcInstance target, int markId) {
        if (target == null) return;
        final Roster snapshot;
        synchronized (MEMBERSHIP) {
            snapshot = roster;
            int index = snapshot.indexOf(target);
            if (requester != snapshot.leader || index < 0) return;
            snapshot.slots[index].mark = markId;
        }
        if (!stillMember(snapshot, target)) return;
        broadcastSnapshot(snapshot, SC_PARTY_MEMBER_MARK_CHANGE_NOTI.encodeFields(target.getId(), markId));
        refreshPartyMemberStatus(target);
    }
}
