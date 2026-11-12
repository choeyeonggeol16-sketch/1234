package l1j.server.server.model.character.Attack.Helper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Arrays;

public final class HitEvasionModel {

    public static volatile double HIT_MIN = 0.0;
    public static volatile double HIT_MAX = 1.0;

    private static final int AC_MAX = 255;

    private static final double REFERENCE_HIT_LOW = 124.0;
    private static final double REFERENCE_HIT_HIGH = 175.0;

    public static volatile double DG_K = 1.0;
    public static volatile double ER_K = 1.0;

    public static volatile double DG_PER_POINT = 0.0075;
    public static volatile double ER_PER_POINT = 0.0075;


    private static final int[] AC_KNOT = {
            64, 65, 67, 69, 70, 72, 80, 90, 100,
            120, 125, 130, 135, 140, 145, 150
    };

    private static final double[] REFERENCE_EVASION_LOW_PCT = {
            4, 5, 7, 12, 12, 15, 23, 25, 32,
            43.4, 44.7, 47.9, 50.2, 52.3, 60.7, 61.8
    };

    private static final int[] REFERENCE_AC_HIGH = {
            120, 125, 130, 135, 140, 145, 150
    };

    private static final double[] REFERENCE_EVASION_HIGH_PCT = {
            13.4, 18.8, 19.8, 28.0, 30.2, 33.2, 36.8
    };

    public static volatile double S_MIN = 18.0;
    public static volatile double S_MAX = 120.0;

    public static volatile double S_TAIL_SLOPE_PER_AC = 0.00;

    private static final double[] MU_KNOT = buildMuKnots();
    private static final double[] S_KNOT  = buildSKnotsForAll();

    private static final MonotoneCubicSpline MU_SPLINE =
            new MonotoneCubicSpline(AC_KNOT, MU_KNOT);

    private static final PiecewiseLinear S_LERP =
            new PiecewiseLinear(AC_KNOT, S_KNOT);

    // A single publication prevents a reload from mixing old centers and new
    // spreads. Each immutable curve is the lower envelope of the existing
    // calibrated logits at this and weaker armor values. This removes curve
    // crossings without fitting a new hit-stat offset or changing valid points.
    private static volatile HitCurve[] HIT_LUT;

    static {
        rebuildLut(); 
    }

    private HitEvasionModel() {}

    /**
     * Rebuild LUT. Call only on admin/reload timing (NOT in hot path).
     */
    public static void rebuildLut() {
        final double[] muLut  = new double[AC_MAX + 1];
        final double[] invLut = new double[AC_MAX + 1];

        final int lastKnot = AC_KNOT[AC_KNOT.length - 1];
        final double sMin = S_MIN;
        final double sMax = S_MAX;
        final double tail = S_TAIL_SLOPE_PER_AC;
        if (!Double.isFinite(sMin) || !Double.isFinite(sMax)
                || sMin <= 0.0 || sMax < sMin || !Double.isFinite(tail)) {
            throw new IllegalArgumentException("Invalid hit spread bounds/tail");
        }

        for (int ac = 0; ac <= AC_MAX; ac++) {
            // There is no calibration below AC 64. Extrapolating the first
            // one-point tangent made H=0 vs AC 0 almost certain to hit. Use the
            // nearest observed boundary until low-AC measurements exist.
            muLut[ac] = MU_SPLINE.value(Math.max(AC_KNOT[0], ac));

            double s = S_LERP.value(ac);
            if (ac >= lastKnot && tail != 0.0) {
                s += (ac - lastKnot) * tail;
            }
            if (s < sMin) s = sMin;
            else if (s > sMax) s = sMax;

            invLut[ac] = 1.0 / s;
        }

        final Integer[] order = new Integer[AC_MAX + 1];
        for (int ac = 0; ac <= AC_MAX; ac++) order[ac] = ac;
        Arrays.sort(order, (a, b) -> {
            int compared = Double.compare(invLut[b], invLut[a]);
            if (compared != 0) return compared;
            return Double.compare(-muLut[a] * invLut[a], -muLut[b] * invLut[b]);
        });
        final HitCurve[] next = new HitCurve[AC_MAX + 1];
        for (int ac = 0; ac <= AC_MAX; ac++) {
            next[ac] = new HitCurve(ac, order, muLut, invLut);
        }
        HIT_LUT = next;
    }

    /** Convert signed game AC to model defense strength; positive AC is weak. */
    public static int defenseAc(int armorClass) {
        return (int) Math.max(0L, Math.min(AC_MAX, -(long) armorClass));
    }

    public static double calcHitChance(double H,
                                       int acDef,
                                       int dg,
                                       int er,
                                       boolean isRanged,
                                       double evadeBonusRate) {

        if (Double.isNaN(H)) return 0.0;
        final int ac = (int) Math.min(AC_MAX, Math.abs((long) acDef));
        final HitCurve[] lut = HIT_LUT;
        double hit = sigmoid(lut[ac].logOdds(H));

        int stat = isRanged ? er : dg;
        if (stat > 0) {
            final double perPoint = isRanged ? ER_PER_POINT : DG_PER_POINT;
            final double k = isRanged ? ER_K : DG_K;
            hit -= (stat * perPoint * k);
        }

        if (evadeBonusRate != 0.0) hit -= evadeBonusRate;

        final double min = probability(HIT_MIN);
        if (hit < min) return min;
        final double max = Math.max(min, probability(HIT_MAX));
        if (hit > max) return max;
        return probability(hit);
    }

    public static boolean rollHit(double hitChance) {
        int r = ThreadLocalRandom.current().nextInt(10000);
        int p = (int) (probability(hitChance) * 10000.0 + 0.5);
        return r < p;
    }

    private static double probability(double value) {
        if (!(value > 0.0)) return 0.0; // Includes NaN.
        return Math.min(1.0, value);
    }

    /** Built only at startup/reload; no allocation or lock in a combat roll. */
    private static final class HitCurve {
        private final double[] slope;
        private final double[] intercept;
        private final double[] start;

        HitCurve(int ac, Integer[] order, double[] mu, double[] inverseSpread) {
            final double[] a = new double[ac + 1];
            final double[] b = new double[ac + 1];
            final double[] from = new double[ac + 1];
            int count = 0;
            for (int index : order) {
                if (index > ac) continue;
                final double nextA = inverseSpread[index];
                final double nextB = -mu[index] * nextA;
                // Slopes descend, equal slopes have the lowest intercept first.
                if (count > 0 && nextA == a[count - 1]) continue;
                double crossing = Double.NEGATIVE_INFINITY;
                while (count > 0) {
                    crossing = (nextB - b[count - 1]) / (a[count - 1] - nextA);
                    if (crossing > from[count - 1]) break;
                    count--;
                }
                a[count] = nextA;
                b[count] = nextB;
                from[count] = count == 0 ? Double.NEGATIVE_INFINITY : crossing;
                count++;
            }
            slope = Arrays.copyOf(a, count);
            intercept = Arrays.copyOf(b, count);
            start = Arrays.copyOf(from, count);
        }

        double logOdds(double hit) {
            int low = 0, high = start.length - 1;
            while (low < high) {
                final int middle = (low + high + 1) >>> 1;
                if (hit >= start[middle]) low = middle;
                else high = middle - 1;
            }
            return hit * slope[low] + intercept[low];
        }
    }

    private static double[] buildMuKnots() {
        final double[] hitSpreadAtHighReferenceKnots = new double[REFERENCE_AC_HIGH.length];

        for (int i = 0; i < REFERENCE_AC_HIGH.length; i++) {
            int ac = REFERENCE_AC_HIGH[i];

            double lowReferenceHitChance = 1.0 - pctToProb(lookupEvasionAtLowReference(ac));
            double highReferenceHitChance = 1.0 - pctToProb(REFERENCE_EVASION_HIGH_PCT[i]);

            double lowReferenceLogOdds = logit(lowReferenceHitChance);
            double highReferenceLogOdds = logit(highReferenceHitChance);

            hitSpreadAtHighReferenceKnots[i] = (REFERENCE_HIT_HIGH - REFERENCE_HIT_LOW) / (highReferenceLogOdds - lowReferenceLogOdds);
        }

        double Sseed = robustMedian(hitSpreadAtHighReferenceKnots);

        double[] mu = new double[AC_KNOT.length];

        for (int i = 0; i < AC_KNOT.length; i++) {
            int ac = AC_KNOT[i];

            double lowReferenceHitChance = 1.0 - pctToProb(REFERENCE_EVASION_LOW_PCT[i]);

            double S = (ac >= 120 && ac <= 150) ? hitSpreadAtHighReferenceKnot(ac, hitSpreadAtHighReferenceKnots) : Sseed;
            mu[i] = REFERENCE_HIT_LOW - S * logit(lowReferenceHitChance);
        }

        for (int i = 1; i < mu.length; i++) {
            if (mu[i] < mu[i - 1]) mu[i] = mu[i - 1];
        }

        return mu;
    }

    private static double[] buildSKnotsForAll() {
        final double[] hitSpreadAtHighReferenceKnots = new double[REFERENCE_AC_HIGH.length];
        for (int i = 0; i < REFERENCE_AC_HIGH.length; i++) {
            int ac = REFERENCE_AC_HIGH[i];

            double lowReferenceHitChance = 1.0 - pctToProb(lookupEvasionAtLowReference(ac));
            double highReferenceHitChance = 1.0 - pctToProb(REFERENCE_EVASION_HIGH_PCT[i]);

            double lowReferenceLogOdds = logit(lowReferenceHitChance);
            double highReferenceLogOdds = logit(highReferenceHitChance);

            hitSpreadAtHighReferenceKnots[i] = (REFERENCE_HIT_HIGH - REFERENCE_HIT_LOW) / (highReferenceLogOdds - lowReferenceLogOdds);
        }
        double Sseed = robustMedian(hitSpreadAtHighReferenceKnots);

        double[] S = new double[AC_KNOT.length];
        for (int i = 0; i < AC_KNOT.length; i++) {
            int ac = AC_KNOT[i];
            double s = (ac >= 120 && ac <= 150) ? hitSpreadAtHighReferenceKnot(ac, hitSpreadAtHighReferenceKnots) : Sseed;

            if (s < S_MIN) s = S_MIN;
            else if (s > S_MAX) s = S_MAX;

            S[i] = s;
        }
        return S;
    }

    private static double lookupEvasionAtLowReference(int ac) {
        for (int i = 0; i < AC_KNOT.length; i++) {
            if (AC_KNOT[i] == ac) return REFERENCE_EVASION_LOW_PCT[i];
        }
        throw new IllegalArgumentException("H=124 evade knot missing for AC=" + ac);
    }

    private static double hitSpreadAtHighReferenceKnot(int ac, double[] hitSpreadAtHighReferenceKnots) {
        for (int i = 0; i < REFERENCE_AC_HIGH.length; i++) {
            if (REFERENCE_AC_HIGH[i] == ac) return hitSpreadAtHighReferenceKnots[i];
        }
        return robustMedian(hitSpreadAtHighReferenceKnots);
    }

    private static double robustMedian(double[] a) {
        double[] b = a.clone();
        for (int i = 0; i < b.length - 1; i++) {
            for (int j = i + 1; j < b.length; j++) {
                if (b[j] < b[i]) {
                    double t = b[i]; b[i] = b[j]; b[j] = t;
                }
            }
        }
        return b[b.length / 2];
    }

    private static double pctToProb(double pct) { return pct / 100.0; }

    private static double logit(double p) {
        if (p <= 1e-12) p = 1e-12;
        else if (p >= 1.0 - 1e-12) p = 1.0 - 1e-12;
        return Math.log(p / (1.0 - p));
    }

    private static double sigmoid(double x) {
        if (x > 60) return 1.0;
        if (x < -60) return 0.0;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static final class PiecewiseLinear {
        private final int n;
        private final int[] x;
        private final double[] y;

        PiecewiseLinear(int[] xIn, double[] yIn) {
            if (xIn.length != yIn.length || xIn.length < 2)
                throw new IllegalArgumentException("bad knots");
            n = xIn.length;
            x = xIn.clone();
            y = yIn.clone();
            for (int i = 1; i < n; i++) {
                if (x[i] <= x[i - 1]) throw new IllegalArgumentException("x must be increasing");
            }
        }

        double value(double xi) {
            if (xi <= x[0]) return y[0];
            if (xi >= x[n - 1]) return y[n - 1];

            int lo = 0, hi = n - 2, idx = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (x[mid] <= xi && xi <= x[mid + 1]) { idx = mid; break; }
                if (xi < x[mid]) hi = mid - 1;
                else lo = mid + 1;
            }

            double t = (xi - x[idx]) / (double) (x[idx + 1] - x[idx]);
            return y[idx] + (y[idx + 1] - y[idx]) * t;
        }
    }

    private static final class MonotoneCubicSpline {
        private final int n;
        private final double[] x;
        private final double[] y;
        private final double[] m;

        MonotoneCubicSpline(int[] xIn, double[] yIn) {
            if (xIn.length != yIn.length || xIn.length < 2)
                throw new IllegalArgumentException("bad knots");
            n = xIn.length;
            x = new double[n];
            y = new double[n];
            for (int i = 0; i < n; i++) { x[i] = xIn[i]; y[i] = yIn[i]; }

            double[] d = new double[n - 1];
            double[] h = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                h[i] = x[i + 1] - x[i];
                if (h[i] <= 0) throw new IllegalArgumentException("x must be increasing");
                d[i] = (y[i + 1] - y[i]) / h[i];
            }

            m = new double[n];
            m[0] = d[0];
            m[n - 1] = d[n - 2];

            for (int i = 1; i < n - 1; i++) {
                if (d[i - 1] * d[i] <= 0) {
                    m[i] = 0.0;
                } else {
                    double w1 = 2 * h[i] + h[i - 1];
                    double w2 = h[i] + 2 * h[i - 1];
                    m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]);
                }
            }
        }

        double value(double xi) {
            if (xi <= x[0]) return y[0] + m[0] * (xi - x[0]);
            if (xi >= x[n - 1]) return y[n - 1] + m[n - 1] * (xi - x[n - 1]);

            int i = 0;
            int lo = 0, hi = n - 2;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (x[mid] <= xi && xi <= x[mid + 1]) { i = mid; break; }
                if (xi < x[mid]) hi = mid - 1;
                else lo = mid + 1;
            }

            double h = x[i + 1] - x[i];
            double t = (xi - x[i]) / h;

            double t2 = t * t;
            double t3 = t2 * t;

            double h00 = (2 * t3 - 3 * t2 + 1);
            double h10 = (t3 - 2 * t2 + t);
            double h01 = (-2 * t3 + 3 * t2);
            double h11 = (t3 - t2);

            return h00 * y[i] + h10 * h * m[i] + h01 * y[i + 1] + h11 * h * m[i + 1];
        }
    }
}
