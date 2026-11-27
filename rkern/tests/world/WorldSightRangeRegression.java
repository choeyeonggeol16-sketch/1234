package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

public final class WorldSightRangeRegression {
    private static final int LAST_TILE = (1 << 19) - 1;

    private static final class Viewer extends L1PcInstance {
        final List<L1Object> seen = new ArrayList<>();
        @Override public void reconcileSight(L1Object obj) { seen.add(obj); }
        @Override public int groundSightEpoch() { return 1; }
    }

    public static void main(String[] args) throws Exception {
        int previous = Config.PC_RECOGNIZE_RANGE;
        try {
            final FutureTask<Void> test = new FutureTask<>(() -> {
                exercise();
                return null;
            });
            Thread.ofVirtual().start(test);
            test.get(5, TimeUnit.SECONDS);
        } finally {
            Config.PC_RECOGNIZE_RANGE = previous;
        }
        System.out.println("PASS maximum configured sight, ordered movement union and map-edge isolation");
    }

    private static void exercise() {
        LocalWorldMap grid = new LocalWorldMap();
        L1Map map = new L1V1Map(91, new byte[1], 1, 1, 0, 0,
                false, true, true, true, true, true, true, true, true, true, true, false);
        L1Object left = at(new L1Object(), map, 1, 0);
        L1Object right = at(new L1Object(), map, 2, LAST_TILE);
        L1Object middle = at(new L1Object(), map, 3, 256);
        L1ItemInstance drop = at(new L1ItemInstance(), map, 4, 256);
        Viewer viewer = at(new Viewer(), map, 5, 0);
        grid.enterSilently(right);
        grid.enterSilently(middle);
        grid.enterSilently(drop);
        grid.enterSilently(left);

        Config.PC_RECOGNIZE_RANGE = 8;
        grid.enter(viewer);
        check(viewer.seen.equals(List.of(left)), "negative cells must not wrap to the last cell");
        viewer.seen.clear();
        viewer.setX(LAST_TILE);
        grid.relocate(viewer, 91, 0, 10);
        check(viewer.seen.equals(List.of(left, right)), "old/new map-edge cells visited once in union order");
        grid.leaveSilently(viewer);
        viewer.seen.clear();
        grid.enter(viewer);
        check(viewer.seen.equals(List.of(right)), "cells above the last cell must not wrap to zero");
        grid.leaveSilently(viewer);

        Config.PC_RECOGNIZE_RANGE = Integer.MAX_VALUE;
        viewer.setX(0);
        viewer.seen.clear();
        grid.enter(viewer);
        check(viewer.seen.equals(List.of(left, middle, drop, right)), "maximum sight must keep cell and bucket order");
        viewer.seen.clear();
        viewer.setX(LAST_TILE);
        grid.relocate(viewer, 91, 0, 10);
        check(viewer.seen.equals(List.of(left, middle, right)), "maximum sight reconciles creatures once and skips unchanged ground");
    }

    private static <T extends L1Object> T at(T obj, L1Map map, int id, int x) {
        obj.setMap(map);
        obj.setId(id);
        obj.setX(x);
        obj.setY(10);
        return obj;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
