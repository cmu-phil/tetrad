package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

public class RankConditionalIndependenceTest implements IndependenceTest {

    // numerics
    private static final double RIDGE = 1e-10;
//    private static final double MIN_EIG = 1e-12;
    private final ICovarianceMatrix covMatrix;
    private final List<Node> variables;
    private final Map<Node, Integer> idx;
    private final SimpleMatrix S; // full covariance/correlation
    private boolean verbose = false;
    // RCCA params (used only for the diagnostic rank)
    private final double alpha;
    // rank-0 detection tolerance
//    private final double smaxTol = .1;   // tweak: singular-value threshold after whitening

    public RankConditionalIndependenceTest(ICovarianceMatrix covMatrix, double alpha) {
        this.covMatrix = new CorrelationMatrix(covMatrix);
        this.variables = this.covMatrix.getVariables();
        this.idx = indexMap(variables);
        this.S = this.covMatrix.getMatrix().getSimpleMatrix();
        this.alpha = alpha;
    }

    private static Map<Node, Integer> indexMap(List<Node> vars) {
        Map<Node, Integer> m = new HashMap<>(vars.size() * 2);
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i), i);
        return m;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ---------- Helpers ----------

    private static int[] range(int startInclusive, int endExclusive) {
        int[] r = new int[endExclusive - startInclusive];
        for (int i = 0; i < r.length; i++) r[i] = startInclusive + i;
        return r;
    }

    // Extract block S[rows, cols]
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(ri, cols[j]));
            }
        }
        return out;
    }

    // Solve A X = B with tiny ridge fallback
    private static SimpleMatrix safeSolve(SimpleMatrix A, SimpleMatrix B) {
        try {
            return A.solve(B);
        } catch (RuntimeException e) {
            SimpleMatrix Areg = A.copy();
            int n = Math.min(Areg.getNumRows(), Areg.getNumCols());
            for (int i = 0; i < n; i++) {
                Areg.set(i, i, Areg.get(i, i) + RIDGE);
            }
            return Areg.solve(B);
        }
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        final int n = covMatrix.getSampleSize();

        // map nodes to indices
        int[] xIdxOrig = new int[]{idx.get(x)};
        int[] yIdxOrig = new int[]{idx.get(y)};
        int[] zIdxOrig = (z == null || z.isEmpty())
                ? new int[0]
                : z.stream().map(idx::get).mapToInt(Integer::intValue).toArray();

        // Build S_VV|Z  (V = X ∪ Y)
        int[] vIdx = concat(xIdxOrig, yIdxOrig);
        SimpleMatrix Scond; // |V|×|V|

        if (zIdxOrig.length == 0) {
            Scond = block(S, vIdx, vIdx);
        } else {
            SimpleMatrix S_VV = block(S, vIdx, vIdx);
            SimpleMatrix S_VZ = block(S, vIdx, zIdxOrig);
            SimpleMatrix S_ZZ = block(S, zIdxOrig, zIdxOrig);
            SimpleMatrix S_ZV = S_VZ.transpose();
            // Schur complement: S_VV - S_VZ * (S_ZZ \ S_ZV)
            SimpleMatrix solved = safeSolve(S_ZZ, S_ZV);
            Scond = S_VV.minus(S_VZ.mult(solved));
        }

        // Local indices for X and Y inside Scond
        int[] xLoc = range(0, xIdxOrig.length);
        int[] yLoc = range(xIdxOrig.length, xIdxOrig.length + yIdxOrig.length);

        int estRank = RankTests.estimateRccaRank(Scond, xLoc, yLoc, n, alpha);

        boolean independent = estRank == 0;

        if (verbose) {
            System.out.printf(Locale.ROOT,
                    "Hybrid RCCA: X=%s Y=%s |Z|=%d -> indep=%s, rccaRank=%d%n",
                    x.getName(), y.getName(), zIdxOrig.length, independent, estRank);
        }

        // pValue unknown; score uses estRank (or 0 if independent by our rank-0 detector)
        double score = independent ? 0.0 : (estRank >= 0 ? estRank : 1.0);
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, Double.NaN, score);
    }

    // Boilerplate
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataModel getData() {
        return covMatrix;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return covMatrix;
    }

    @Override
    public String toString() {
        return "RccaHybridRankIndependenceTest";
    }
}