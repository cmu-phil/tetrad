package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.linear.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Instance-augmented SEM-BIC score for continuous data.
 * <p>
 * Local score: IS-local(i | Pa) = Pop-local(i | Pa) + alpha * logN(x_i | b^T x_Pa, s2)
 * <p>
 * where b = Sigma_{Pa,Pa}^{-1} Sigma_{Pa,i} and s2 = Sigma_{ii} - Sigma_{i,Pa} Sigma_{Pa,Pa}^{-1} Sigma_{Pa,i}.
 * <p>
 * Supports either: - centered instance row x (use 2-arg ctor), or - raw instance row + population means (use 3-arg
 * ctor), in which case the instance is centered internally using those means.
 */
public final class InstanceAugmentedSemBicScore implements InstanceSpecificScore {

    private static final int[] EMPTY = new int[0];
    private final SemBicScore base;
    private final double[] xCentered;        // instance values centered to POPULATION means
    private final RealMatrix S;              // covariance matrix as RealMatrix
    private double alpha = 1.0;

    /* ------------------------ Constructors ------------------------ */
    private double varFloor = 1e-12;

    /**
     * Use this if your instance row is already centered to the population means.
     */
    public InstanceAugmentedSemBicScore(SemBicScore base, double[] instanceRowCentered) {
        this.base = Objects.requireNonNull(base, "base");
        this.S = toRealMatrix(base.getCovariances());
        this.xCentered = requireAndCopy(instanceRowCentered, base.getVariables().size(), "instanceRow");
    }

    /**
     * Same as above but start from an ICovarianceMatrix.
     */
    public InstanceAugmentedSemBicScore(ICovarianceMatrix cov, double[] instanceRowCentered) {
        this(new SemBicScore(Objects.requireNonNull(cov, "cov")), instanceRowCentered);
    }

    /**
     * Use this when the instance comes from a different dataset and is NOT centered. Provide population means (aligned
     * with base.getVariables()) and the class will center the instance internally.
     */
    public InstanceAugmentedSemBicScore(SemBicScore base, double[] instanceRowRaw, double[] populationMeans) {
        this.base = Objects.requireNonNull(base, "base");
        this.S = toRealMatrix(base.getCovariances());
        int p = base.getVariables().size();
        double[] raw = requireAndCopy(instanceRowRaw, p, "instanceRow");
        double[] mu = requireAndCopy(populationMeans, p, "populationMeans");
        this.xCentered = subtract(raw, mu); // center to POP means
    }

    /* ------------------------ InstanceSpecificScore ------------------------ */

    /**
     * Same as above but start from an ICovarianceMatrix.
     */
    public InstanceAugmentedSemBicScore(ICovarianceMatrix cov, double[] instanceRowRaw, double[] populationMeans) {
        this(new SemBicScore(Objects.requireNonNull(cov, "cov")), instanceRowRaw, populationMeans);
    }

    private static RealMatrix toRealMatrix(ICovarianceMatrix cov) {
        var M = cov.getMatrix(); // TetradMatrix or RealMatrix
        try {
            if (M instanceof RealMatrix rm) return rm;
        } catch (Throwable ignore) {
        }
        double[][] a = M.toArray();
        return MatrixUtils.createRealMatrix(a);
    }

    /* ------------------------ Optional knobs ------------------------ */

    private static RealMatrix submatrix(RealMatrix S, int[] rows, int[] cols) {
        double[][] out = new double[rows.length][cols.length];
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < cols.length; c++)
                out[r][c] = S.getEntry(rows[r], cols[c]);
        return MatrixUtils.createRealMatrix(out);
    }

    /* ------------------------ Useful delegations ------------------------ */

    private static RealVector column(RealMatrix S, int colIndex, int[] rows) {
        double[] v = new double[rows.length];
        for (int r = 0; r < rows.length; r++) v[r] = S.getEntry(rows[r], colIndex);
        return MatrixUtils.createRealVector(v);
    }

    private static RealVector pick(double[] x, int[] idx) {
        double[] v = new double[idx.length];
        for (int k = 0; k < idx.length; k++) v[k] = x[idx[k]];
        return MatrixUtils.createRealVector(v);
    }

    private static RealVector solveWithJitter(RealMatrix A, RealVector b, double lambda) {
        int n = A.getRowDimension();
        RealMatrix Aj = A.copy();
        for (int d = 0; d < n; d++) Aj.addToEntry(d, d, lambda);
        return new LUDecomposition(Aj).getSolver().solve(b);
    }

    private static double[] requireAndCopy(double[] v, int expectedLen, String what) {
        Objects.requireNonNull(v, what);
        if (v.length != expectedLen) {
            throw new IllegalArgumentException(what + " length (" + v.length + ") must match number of variables (" + expectedLen + ").");
        }
        return v.clone();
    }

    /* ------------------------ Score API ------------------------ */

    private static double[] subtract(double[] a, double[] b) {
        double[] out = new double[a.length];
        for (int k = 0; k < a.length; k++) out[k] = a[k] - b[k];
        return out;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    /* ------------------------ Internal: instance term ------------------------ */

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setVarianceFloor(double varFloor) {
        if (varFloor <= 0) throw new IllegalArgumentException("varFloor must be > 0");
        this.varFloor = varFloor;
    }

    /* ------------------------ Linear-algebra helpers ------------------------ */

    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    public double getPenaltyDiscount() {
        return base.getPenaltyDiscount();
    }

    public void setPenaltyDiscount(double d) {
        base.setPenaltyDiscount(d);
    }

    @Override
    public double localScore(int i) {
        double pop = base.localScore(i);
        double inst = instanceTerm(i, EMPTY);
        return pop + alpha * inst;
    }

    @Override
    public double localScore(int i, int[] parents) {
        double pop = base.localScore(i, parents);
        double inst = instanceTerm(i, parents);
        return pop + alpha * inst;
    }

    /* ------------------------ Utils ------------------------ */

    private double instanceTerm(int i, int[] parents) {
        if (Double.isNaN(xCentered[i])) return 0.0;
        for (int p : parents) if (Double.isNaN(xCentered[p])) return 0.0;

        final double mu, s2;

        if (parents == null || parents.length == 0) {
            // Centered model ⇒ E[X_i | ∅] = 0, Var = S_ii
            mu = 0.0;
            s2 = clampVar(S.getEntry(i, i));
        } else {
            RealMatrix Spp = submatrix(S, parents, parents);    // Σ_{Pa,Pa}
            RealVector Spi = column(S, i, parents);             // Σ_{Pa,i}
            RealVector b;
            try {
                b = new LUDecomposition(Spp).getSolver().solve(Spi);
            } catch (SingularMatrixException ex) {
                b = solveWithJitter(Spp, Spi, 1e-8);
            }
            RealVector xPa = pick(xCentered, parents);
            mu = b.dotProduct(xPa);                             // b^T x_Pa
            s2 = clampVar(S.getEntry(i, i) - Spi.dotProduct(b)); // Σ_ii - Σ_{i,Pa} Σ_{Pa,Pa}^{-1} Σ_{Pa,i}
        }

        double diff = xCentered[i] - mu;
        return -0.5 * (Math.log(2.0 * Math.PI * s2) + (diff * diff) / s2);
    }

    private double clampVar(double s2) {
        if (Double.isNaN(s2) || s2 <= 0.0) return varFloor;
        return Math.max(s2, varFloor);
    }

    /* ------------------------ Accessors ------------------------ */

    public SemBicScore getBase() {
        return base;
    }

    public double[] getInstanceRowCentered() {
        return Arrays.copyOf(xCentered, xCentered.length);
    }
}