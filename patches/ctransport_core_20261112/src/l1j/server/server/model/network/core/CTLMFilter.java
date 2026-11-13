package l1j.server.server.model.network.core;

import l1j.server.server.model.L1Object;

/** L1 adaptation of the original recipient-filter role; keeps L1 tile/screen geometry. */
public final class CTLMFilter {
    private CTLMFilter() {}
    public static boolean sameWorldOther(L1Object origin, L1Object candidate) {
        return candidate != null && origin != null && candidate.getId() != origin.getId()
                && candidate.getMapId() == origin.getMapId();
    }
    public static boolean nearby(L1Object origin, L1Object candidate, int radius, boolean screen) {
        if (!sameWorldOther(origin, candidate)) return false;
        if (screen) return origin.getLocation().isInScreen(candidate.getLocation());
        if (radius == 0) return origin.getLocation().isSamePoint(candidate.getLocation());
        return origin.getLocation().getTileLineDistance(candidate.getLocation()) <= radius;
    }
    /** Empty/inverted rectangles remain empty, as required by AOI delta scans. */
    public static boolean insideRectangle(int x, int y, int minX, int maxX, int minY, int maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
