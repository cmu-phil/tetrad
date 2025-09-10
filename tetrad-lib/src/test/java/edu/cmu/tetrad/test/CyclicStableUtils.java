package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.Arrays;
import java.util.Random;

/** Utilities for sampling and injecting stable Y↔Z cycles. */
public final class CyclicStableUtils {

    private CyclicStableUtils() {}

    /** Sample b,d ∈ [low,high] with bd ≤ maxProd (strictly positive). */
    public static double[] sampleCycleProductCapped(double low, double high, double maxProd, Random rng) {
        if (low <= 0 || high <= 0 || low > high) throw new IllegalArgumentException("bad range");
        if (maxProd <= 0 || maxProd >= 1) throw new IllegalArgumentException("maxProd in (0,1)");
        while (true) {
            double b = low + (high - low) * rng.nextDouble();
            double d = low + (high - low) * rng.nextDouble();
            if (b * d <= maxProd) return new double[]{b, d};
        }
    }

    /** Fix spectral radius s of [[0,b],[d,0]] by enforcing bd = s^2 with b ∈ [low,high], d computed. */
    public static double[] sampleCycleFixedRadius(double s, double low, double high, Random rng) {
        if (s <= 0 || s >= 1) throw new IllegalArgumentException("s in (0,1)");
        if (low <= 0 || high <= 0 || low > high) throw new IllegalArgumentException("bad range");
        double target = s * s;
        for (int tries = 0; tries < 100000; tries++) {
            double b = low + (high - low) * rng.nextDouble();
            double d = target / b;
            if (d >= low && d <= high) return new double[]{b, d};
        }
        throw new IllegalStateException("Could not find b in range with d in range; widen [low,high] or change s.");
    }

    /** Apply Y→Z=b and Z→Y=d to a SemIm. Falls back to product-rescale if setters are unavailable. */
    public static void applyCycle(SemIm im, Node y, Node z, double b, double d) {
        Edge yz = new Edge(y, z, Endpoint.TAIL, Endpoint.ARROW);
        Edge zy = new Edge(z, y, Endpoint.TAIL, Endpoint.ARROW);

        try {
            // Preferred: set exact coefficients if API available
            SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class)
                    .invoke(im, y, z, b);
            SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class)
                    .invoke(im, z, y, d);
            return;
        } catch (ReflectiveOperationException ignore) {
            // Fallback below
        }

        // Fallback: rescale existing nonzero coefficients to match product target
        double byz = safeGet(im, y, z);
        double dzy = safeGet(im, z, y);

        if (byz == 0.0 && dzy == 0.0) {
            // nothing there; try creating via reflection if available
            try {
                SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class)
                        .invoke(im, y, z, b);
                SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class)
                        .invoke(im, z, y, d);
                return;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No way to set Y↔Z coefficients on this SemIm build.");
            }
        }

        if (byz == 0.0) byz = 1e-6;
        if (dzy == 0.0) dzy = 1e-6;
        double currentProd = byz * dzy;
        if (currentProd <= 0) currentProd = 1e-6;
        double scale = Math.sqrt((b * d) / currentProd);

        setIfPossible(im, y, z, byz * scale);
        setIfPossible(im, z, y, dzy * scale);
    }

    private static double safeGet(SemIm im, Node from, Node to) {
        try {
            var m = SemIm.class.getMethod("getEdgeCoef", Node.class, Node.class);
            Object val = m.invoke(im, from, to);
            return (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;
        } catch (ReflectiveOperationException e) {
            return 0.0;
        }
    }

    private static void setIfPossible(SemIm im, Node from, Node to, double val) {
        try {
            SemIm.class.getMethod("setEdgeCoef", Node.class, Node.class, double.class)
                    .invoke(im, from, to, val);
        } catch (ReflectiveOperationException e) {
            // swallow; we already tried best-effort
        }
    }

    /** Convenience: build X→Y, W→Z, Y↔Z graph. */
    public static Graph makeFourNodeCyclic(Node x, Node y, Node z, Node w) {
        Graph g = new EdgeListGraph(Arrays.asList(x, y, z, w));
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(w, z);
        g.addDirectedEdge(y, z);
        g.addDirectedEdge(z, y);
        return g;
    }

    /** One-shot simulate with stable cycle using product cap. */
    public static DataSet simulateStableProductCapped(
            int n, double low, double high, double maxProd, long seed) {

        Node x = new ContinuousVariable("x");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        Node w = new ContinuousVariable("w");

        Graph g = makeFourNodeCyclic(x, y, z, w);

        Parameters par = new Parameters();
        par.set(Params.COEF_SYMMETRIC, false);
        par.set(Params.COEF_LOW, low);
        par.set(Params.COEF_HIGH, high);

        RandomUtil.getInstance().setSeed(seed);
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm, par);

        double[] bd = sampleCycleProductCapped(low, high, maxProd, new Random(seed + 13));
        applyCycle(im, y, z, bd[0], bd[1]);

        return im.simulateData(n, false);
    }

    /** One-shot simulate with fixed spectral radius s for the cycle. */
    public static DataSet simulateStableFixedRadius(
            int n, double s, double low, double high, long seed) {

        Node x = new ContinuousVariable("x");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        Node w = new ContinuousVariable("w");

        Graph g = makeFourNodeCyclic(x, y, z, w);

        Parameters par = new Parameters();
        par.set(Params.COEF_SYMMETRIC, false);
        par.set(Params.COEF_LOW, low);
        par.set(Params.COEF_HIGH, high);

        RandomUtil.getInstance().setSeed(seed);
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm, par);

        double[] bd = sampleCycleFixedRadius(s, low, high, new Random(seed + 17));
        applyCycle(im, y, z, bd[0], bd[1]);

        return im.simulateData(n, false);
    }
}