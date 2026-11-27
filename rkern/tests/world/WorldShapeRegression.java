package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

import sun.misc.Unsafe;

/** Compares the bounded world queries with the former tile-step and rotation predicates. */
public final class WorldShapeRegression {
    private static final int MAP_ID = 91;
    private static final int CENTER = 120;
    private static final int RADIUS = 14;

    private WorldShapeRegression() {}

    public static void main(String[] args) throws Exception {
        installMapDefinitions();
        final CWorld world = CWorld.getInstance();
        final L1Map map = map(MAP_ID);
        final L1Map otherGeneration = map(MAP_ID);
        final ArrayList<L1Object> objects = new ArrayList<>();
        int ordinal = 1;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                // Scatter IDs across all radix leaves and above the sign bit.
                final L1Object actor = at(map, ordinal++ * 0x9e37_79b9, CENTER + dx, CENTER + dy);
                world.addVisibleObject(actor);
                objects.add(actor);
            }
        }
        final L1Object source = objects.get(objects.size() / 2);
        final L1Object stacked = at(map, 5, CENTER, CENTER);
        final L1Object stale = at(otherGeneration, 7, CENTER, CENTER);
        world.addVisibleObject(stacked);
        world.addVisibleObject(stale);
        objects.add(stacked);

        verifyLines(world, map, objects, source);
        verifyWideLines(world, map, objects);
        verifyBoxes(world, objects, source);
        require(world.getVisibleLineObjects(stale, source).isEmpty(),
                "Different generations of one map cannot share a line");
        require(world.getVisibleLineObjects(source, stale).isEmpty(),
                "Line source generation must match the target");
        require(world.getVisibleBoxObjects(stale, 2, 0, 0).isEmpty(),
                "A same-tile object in another map generation cannot share a box");

        final L1Object absent = at(map(MAP_ID + 1), 9, CENTER, CENTER);
        require(world.getVisibleLineObjects(absent, absent) == null
                && world.getVisibleBoxObjects(absent, 0, 2, 2) == null, "Undefined visibility map returns null");
        world.addVisibleObject(absent);
        world.removeVisibleObject(absent);
        require(world.getVisibleLineObjects(absent, absent) == null
                && world.getVisibleBoxObjects(absent, 0, 2, 2) == null, "Empty visibility map returns null");

        final L1Object outside = at(map, 12, 150, 150);
        final var empty = world.getVisibleBoxObjects(outside, 0, 0, 0);
        require(empty != null && empty.isEmpty(), "Nonempty map with no matches returns an empty list");
        empty.add(source);
        require(empty.size() == 1, "The empty result remains mutable");
        System.out.println("PASS bounded world line/box parity, unsigned ID order and map generation");
    }

    private static void verifyLines(CWorld world, L1Map map, List<L1Object> objects, L1Object source) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                final L1Object end = at(map, 8, CENTER + dx, CENTER + dy);
                final List<L1Object> expected = legacyLine(objects, source, end);
                require(expected.equals(world.getVisibleLineObjects(source, end)),
                        "Forward Bresenham mismatch at " + dx + "," + dy);
                require(legacyLine(objects, end, source).equals(world.getVisibleLineObjects(end, source)),
                        "Reverse Bresenham mismatch at " + dx + "," + dy);
            }
        }
    }

    private static void verifyWideLines(CWorld world, L1Map map, List<L1Object> objects) {
        final L1Object minimum = at(map, 8, Integer.MIN_VALUE, Integer.MIN_VALUE);
        final L1Object maximum = at(map, 9, Integer.MAX_VALUE, Integer.MAX_VALUE);
        final List<L1Object> diagonal = objects.stream()
                .filter(actor -> actor.getX() == actor.getY()).sorted(WorldShapeRegression::compareId).toList();
        require(diagonal.equals(world.getVisibleLineObjects(minimum, maximum)),
                "Entire int-coordinate span uses correct unsigned interpolation");
        require(diagonal.equals(world.getVisibleLineObjects(maximum, minimum)),
                "Reversing the entire int-coordinate span keeps its diagonal");

        maximum.setY(Integer.MAX_VALUE - 1);
        final List<L1Object> afterMidpoint = objects.stream()
                .filter(actor -> actor.getY() == actor.getX() - 1).sorted(WorldShapeRegression::compareId).toList();
        require(afterMidpoint.equals(world.getVisibleLineObjects(minimum, maximum)),
                "A one-tile shallower slope crosses to the lower row after its midpoint");
    }

    private static List<L1Object> legacyLine(List<L1Object> objects, L1Object source, L1Object target) {
        int x = source.getX(), y = source.getY();
        final int dx = Math.abs(target.getX() - x), dy = Math.abs(target.getY() - y);
        final int sx = target.getX() >= x ? 1 : -1, sy = target.getY() >= y ? 1 : -1;
        final Set<Integer> tiles = new HashSet<>();
        if (dx >= dy) {
            int error = -dx;
            for (int i = 0; i <= dx; i++) {
                tiles.add((x << 16) + y);
                x += sx;
                error += dy << 1;
                if (error >= 0) {
                    y += sy;
                    error -= dx << 1;
                }
            }
        } else {
            int error = -dy;
            for (int i = 0; i <= dy; i++) {
                tiles.add((x << 16) + y);
                y += sy;
                error += dx << 1;
                if (error >= 0) {
                    x += sx;
                    error -= dy << 1;
                }
            }
        }
        return objects.stream()
                .filter(actor -> actor != source && tiles.contains((actor.getX() << 16) + actor.getY()))
                .sorted(WorldShapeRegression::compareId).toList();
    }

    private static void verifyBoxes(CWorld world, List<L1Object> objects, L1Object source) {
        final int[] headings = {6, 7, 0, 1, 2, 3, 4, 5};
        final int[] widths = {0, 1, 3, 6, 20};
        final int[] heights = {0, 1, 4, 9, 15, Integer.MAX_VALUE};
        for (int heading = 0; heading < headings.length; heading++) {
            final double angle = headings[heading] * Math.PI / 4;
            final double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int width : widths) {
                for (int height : heights) {
                    final List<L1Object> expected = objects.stream()
                            .filter(actor -> actor != source && legacyBox(source, actor, cos, sin, width, height))
                            .sorted(WorldShapeRegression::compareId).toList();
                    require(expected.equals(world.getVisibleBoxObjects(source, heading, width, height)),
                            "Rotated box mismatch at heading " + heading + ", width " + width + ", height " + height);
                }
            }
        }
    }

    private static boolean legacyBox(L1Object source, L1Object actor, double cos, double sin, int width, int height) {
        final int dx = actor.getX() - source.getX(), dy = actor.getY() - source.getY();
        final int distance = source.getLocation().getTileLineDistance(actor.getLocation());
        if (dx == 0 && dy == 0) return true;
        final int forward = (int) Math.round(dx * cos + dy * sin);
        final int lateral = (int) Math.round(-dx * sin + dy * cos);
        return forward > 0 && distance <= height && lateral >= -width && lateral <= width;
    }

    private static int compareId(L1Object left, L1Object right) {
        return Integer.compareUnsigned(left.getId(), right.getId());
    }

    private static L1Map map(int id) {
        return new L1V1Map(id, new byte[64 * 64], 64, 64, 96, 96,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static L1Object at(L1Map map, int id, int x, int y) {
        final L1Object actor = new L1Object();
        actor.setId(id);
        actor.getLocation().set(x, y, map);
        return actor;
    }

    static void installMapDefinitions() throws Exception {
        // Same DB-free fixture seam as WorldBroadcastAudienceRegression.
        final Unsafe allocator = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable table = (MapsTable) allocator.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(table, Map.of());
        field(MapsTable.class, "_instance").set(null, table);
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
}
