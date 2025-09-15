///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper class for {@link IndependenceTest} that enforces False Discovery Rate (FDR) control on independence
 * decisions. This class uses either Benjamini-Hochberg (BH) or Benjamini-Yekutieli (BY) FDR control methods and
 * supports both global and stratified FDR control across conditioning sets.
 * <p>
 * The workflow is divided into two phases: 1. Recording Epoch: Raw p-values for independence tests are cached from the
 * underlying test. 2. Decision Epoch: Enforces FDR-controlled cutoffs based on cached p-values.
 * <p>
 * The cutoffs can be computed globally or within groups based on the cardinality of the conditioning set (|Z|).
 * <p>
 * This class also maintains a mechanism to track "mind-changes," i.e., decisions that change between subsequent
 * algorithm passes.
 * <p>
 * The wrapper allows for integrating FDR-controlled independence testing into iterative search algorithms without
 * re-computing p-values across epochs, ensuring reproducibility and efficiency.
 */
public final class IndTestFdrWrapper implements IndependenceTest {
    private final IndependenceTest base;
    private final double alpha;
    private final double fdrQ;             // target FDR level, e.g., 0.05
    private final Map<IndependenceFact, Double> pvals = new ConcurrentHashMap<>();
    private Map<IndependenceFact, Boolean> lastDecisions = new HashMap<>();
    private double alphaStar = Double.NaN;
    private boolean negativelyCorrelated = false;

    public IndTestFdrWrapper(IndependenceTest base, boolean negativelyCorrelated, double alpha, double fdrQ) {
        this.base = Objects.requireNonNull(base);
        if (!(alpha >= 0 && alpha <= 1)) throw new IllegalArgumentException("Alpha must be in (0,1)");
        if (!(fdrQ >= 0 && fdrQ <= 1)) throw new IllegalArgumentException("FDR q must be in (0,1)");
        this.alpha = alpha;
        this.fdrQ = fdrQ;
        this.negativelyCorrelated = negativelyCorrelated;
        base.setVerbose(false);
    }

    public static Graph doFdrLoop(IGraphSearch search, boolean negativelyCorrelated,
                                  double alpha, double fdrQ, boolean verbose) throws InterruptedException {
        IndependenceTest test = null;
        try {
            test = search.getTest();
        } catch (Exception e) {
            throw new IllegalStateException("For FDR to work, the search must be able to return a test. If this\n" +
                                            "is a constraint-based algroithm, please complain to the developers\n" +
                                            "because they did not complete their work. Be very mean about it :-).");
        }

        IndTestFdrWrapper wrap = new IndTestFdrWrapper(test, negativelyCorrelated, alpha, fdrQ);
        wrap.setVerbose(verbose);
        search.setTest(wrap);

        final int maxEpochs = 5;
        final int tauChanges = 0;      // stop when â¤ this many changes

        Graph g = null;

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            // Epoch 0: alphaStar is NaN â wrapper uses base alpha
            // Epoch â¥1: wrapper uses the alphaStar computed in the previous iteration
            g = search.search();

            // Recompute Î±* from ALL p-values gathered so far
            wrap.computeAlphaStar();

            // Count changes vs. last epoch (new facts or flips both count)
            int changes = wrap.countMindChanges();
            String s = String.format("FDR epoch %d: %s | mind-changes=%d", epoch, wrap.cutoffsSummary(), changes);
            TetradLogger.getInstance().log(s);

            if (changes <= tauChanges) break;
        }
        return g;
    }

    /* ===== Epoch control ===== */

    public void computeAlphaStar() {

        // Allow duplicate p-values.
        List<Double> pValues = new ArrayList<>();

        for (var fact : pvals.keySet()) {
            pValues.add(pvals.get(fact));
        }

        this.alphaStar = StatUtils.fdrCutoff(fdrQ, pValues, negativelyCorrelated);
    }

    /**
     * Returns the number of mind-changes between the previous decision epoch and the current one. Call this AFTER you
     * complete an algorithm pass using this wrapper in decision mode.
     */
    public int countMindChanges() {
        if (Double.isNaN(alphaStar)) return 0;

        int changes = 0;
        Map<IndependenceFact, Boolean> current = new HashMap<>();

        for (var e : pvals.entrySet()) {
            var fact = e.getKey();
            boolean indep = decideIndependenceFromCutoff(e.getValue());
            current.put(fact, indep);

            Boolean prev = (lastDecisions == null) ? null : lastDecisions.get(fact);
            if (prev == null || prev != indep) changes++;   // count new & flipped
        }
        lastDecisions = current;   // snapshot for next epoch
        return changes;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceFact fact = new IndependenceFact(x, y, z);

        // get (or compute) raw p
        Double p = pvals.get(fact);
        if (p == null) {
            IndependenceResult r = base.checkIndependence(x, y, z);
            p = r.getPValue();
            pvals.put(fact, p);
        }

        double alphaUsed = Double.isNaN(alphaStar) ? alpha : alphaStar;

        boolean indep = p > alphaUsed;
        return new IndependenceResult(fact, indep, p, alphaUsed - p);
    }

    /* ===== IndependenceTest implementation ===== */

    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    @Override
    public DataModel getData() {
        return base.getData();
    }

    @Override
    public boolean isVerbose() {
        return base.isVerbose();
    }

    @Override
    public void setVerbose(boolean verbose) {
        base.setVerbose(verbose);
    }

    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    @Override
    public List<DataSet> getDataSets() {
        return base.getDataSets();
    }

    private boolean decideIndependenceFromCutoff(double p) {
        return p >= (Double.isNaN(alphaStar) ? alpha : alphaStar);
    }

    // pretty-print the current Î±* cutoffs
    public String cutoffsSummary() {
        int m = pvals.size();
        return String.format(Locale.US, "FDR[%s] q=%.3g  m=%d  â  Î±* = %.6f",
                negativelyCorrelated ? "negatively correlated" : "positively correlated",
                fdrQ, m, alphaStar);
    }

    /* ===== Helpers ===== */

    private double getAlphaStarFor(IndependenceFact fact) {
        return (Double.isNaN(alphaStar) ? 1.0 : alphaStar);
    }
}
