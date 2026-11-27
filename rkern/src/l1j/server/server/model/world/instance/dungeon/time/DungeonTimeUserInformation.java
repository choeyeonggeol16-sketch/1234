package l1j.server.server.model.world.instance.dungeon.time;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeProgressLoader;
import l1j.server.server.model.world.instance.dungeon.time.progress.AccountTimeProgress;
import l1j.server.server.model.world.instance.dungeon.time.progress.CharacterTimeProgress;
import l1j.server.server.model.world.instance.dungeon.time.progress.DungeonTimeProgress;
import l1j.server.server.shared.observer.CopyMapObservable;

public class DungeonTimeUserInformation {
    private final HashMap<Integer, DungeonTimeProgress<?>> character = new HashMap<>(8);
    private final HashMap<Integer, DungeonTimeProgress<?>> account = new HashMap<>(8);
    private long generation = DungeonTimeProgressLoader.generation();

    public static DungeonTimeUserInformation newInstance() { return new DungeonTimeUserInformation(); }

    private DungeonTimeUserInformation() { }

    private void refresh() {
        long current = DungeonTimeProgressLoader.generation();
        if (generation == current) return;
        character.clear();
        account.clear();
        generation = current;
    }

    public synchronized void put_dungeon_progress(int timerId, CharacterTimeProgress progress) {
        refresh();
        if (progress.generation() == generation) character.put(timerId, progress);
    }

    public synchronized void put_dungeon_progress(int timerId, AccountTimeProgress progress) {
        refresh();
        if (progress.generation() == generation) account.put(timerId, progress);
    }

    public synchronized DungeonTimeProgress<?> remove_dungeon_progress(int timerId) {
        refresh();
        DungeonTimeProgress<?> progress = character.remove(timerId);
        return progress == null ? account.remove(timerId) : progress;
    }

    public synchronized Collection<DungeonTimeProgress<?>> get_character_progresses() {
        refresh();
        return List.copyOf(character.values());
    }

    public synchronized Collection<DungeonTimeProgress<?>> get_account_progresses() {
        refresh();
        return List.copyOf(account.values());
    }

    public synchronized DungeonTimeProgress<?> get_progress(DungeonTimeInformation info) {
        if (info == null) return null;
        refresh();
        return (info.get_is_account_share() ? account : character).get(info.get_timer_id());
    }

    public synchronized DungeonTimeProgress<?> get_progress(int timerId) {
        refresh();
        DungeonTimeProgress<?> progress = account.get(timerId);
        return progress == null ? character.get(timerId) : progress;
    }

    private DungeonTimeProgress<?> findOrCreate(L1PcInstance pc, DungeonTimeInformation info) {
        refresh();
        var progresses = info.get_is_account_share() ? account : character;
        int timerId = info.get_timer_id();
        DungeonTimeProgress<?> progress = progresses.get(timerId);
        if (progress == null) {
            progress = info.to_progress(pc);
            if (progress != null && progress.generation() == generation) {
                progresses.put(timerId, progress);
                DungeonTimeProgressLoader.update(progress);
            }
        }
        return progress;
    }

    public void dec_dungeon_progress(L1PcInstance pc, DungeonTimeInformation info) {
        if (pc == null || info == null) return;
        final int remain;
        synchronized (this) {
            DungeonTimeProgress<?> progress = findOrCreate(pc, info);
            if (progress == null || progress.generation() != generation) return;
            remain = progress.dec_remain_seconds();
            if (remain <= 0 || remain % 60 == 0) DungeonTimeProgressLoader.update(progress);
        }
        if (remain <= 0) {
            pc.do_simple_teleport(CopyMapObservable.RESET_X, CopyMapObservable.RESET_Y, CopyMapObservable.RESET_MAPID);
            pc.sendPackets(String.format("%s 사용 시간이 만료되었습니다.", info.get_description()));
        }
    }

    public void send_dungeon_progress(L1PcInstance pc, DungeonTimeInformation info) {
        if (pc == null || info == null) return;
        final int remain;
        synchronized (this) {
            DungeonTimeProgress<?> progress = findOrCreate(pc, info);
            if (progress == null || progress.generation() != generation) return;
            remain = progress.get_remain_seconds();
        }
        SC_PacketBox.send(pc, SC_PacketBox.MAP_TIMER, remain);
    }

    public synchronized void initialize() {
        refresh();
        character.clear();
        account.clear();
    }
}
