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

    /**
     * A constant representing an empty array of integers. This is used as a reusable, immutable instance to avoid
     * unnecessary memory allocation when an empty integer array is needed.
     */
    private static final int[] EMPTY = new int[0];
    /**
     * The base score object used as the foundation for the instance-augmented structural equation modeling (SEM) BIC
     * score. This variable holds the underlying {@link SemBicScore} instance that is leveraged for standard score
     * calculations within the {@code InstanceAugmentedSemBicScore} class. It provides the core functionality and access
     * to population-level statistical properties required for further augmentation with instance-specific data.
     * <p>
     * This field is immutable and serves as a critical component in bridging population-level scoring with
     * instance-level adjustments.
     */
    private final SemBicScore base;
    /**
     * Stores an array of instance-specific values that have been centered to the corresponding population means. Each
     * value in this array represents an instance-centered value used for calculations in the scoring process.
     * <p>
     * This array is immutable and intended for internal use within the class to perform operations that involve
     * centered data derived from the population.
     */
    private final double[] xCentered;        // instance values centered to POPULATION means
    /**
     * Represents the covariance matrix as a RealMatrix. This matrix is used to encapsulate the covariance relationships
     * among the variables in the associated data structures. The matrix provides the foundation for computations and
     * operations related to covariance analysis within the context of the InstanceAugmentedSemBicScore class.
     * <p>
     * The covariance matrix is immutable, ensuring thread safety and consistency across calculations. Operations
     * involving this matrix typically include accessing specific submatrices, solving linear systems, or extracting
     * vectors.
     * <p>
     * It is expected that this matrix adheres to structural and numerical constraints consistent with covariance
     * matrices, such as symmetry and positive semi-definiteness.
     */
    private final RealMatrix S;              // covariance matrix as RealMatrix
    /**
     * The variable `alpha` represents a scaling factor or regularization parameter used in computations within the
     * containing class. It influences the behavior of specific methods and is typically utilized in mathematical
     * operations or adjustments to tailor the model or computation to specific requirements.
     * <p>
     * The default value of `alpha` is set to 1.0.
     */
    private double alpha = 1.0;
    /**
     * Represents the minimum allowed variance value to prevent issues such as numerical instability or division by very
     * small values in calculations involving variance. Used as a lower boundary for variance computations to ensure
     * numeric stability.
     */
    private double varFloor = 1e-12;

    /**
     * Constructs an `InstanceAugmentedSemBicScore` object using a base `SemBicScore` and a centered row of instance
     * data. This constructor initializes the object with the covariance matrix derived from the base score and the
     * provided centered instance row.
     *
     * @param base                The base `SemBicScore` object providing the covariance matrix and variables. Must not
     *                            be null.
     * @param instanceRowCentered A fully centered data row representing the instance to be augmented. Its length must
     *                            match the number of variables in the base `SemBicScore`.
     */
    public InstanceAugmentedSemBicScore(SemBicScore base, double[] instanceRowCentered) {
        this.base = Objects.requireNonNull(base, "base");
        this.S = toRealMatrix(base.getCovariances());
        this.xCentered = requireAndCopy(instanceRowCentered, base.getVariables().size(), "instanceRow");
    }

    /**
     * Constructs an `InstanceAugmentedSemBicScore` object using a covariance matrix and a fully centered row of
     * instance data. This constructor initializes the object with the provided covariance matrix and instance row.
     *
     * @param cov                 The covariance matrix used for computation. Must not be null.
     * @param instanceRowCentered A fully centered data row representing the instance to be augmented. Its length must
     *                            match the number of variables in the covariance matrix.
     */
    public InstanceAugmentedSemBicScore(ICovarianceMatrix cov, double[] instanceRowCentered) {
        this(new SemBicScore(Objects.requireNonNull(cov, "cov")), instanceRowCentered);
    }

    /**
     * Constructs an `InstanceAugmentedSemBicScore` object using a base `SemBicScore`, a raw row of instance data, and
     * the population mean values. This constructor initializes the object with the covariance matrix from the base
     * score and a centered representation of the provided instance data.
     *
     * @param base            The base `SemBicScore` object providing the covariance matrix and variables. Must not be
     *                        null.
     * @param instanceRowRaw  An array representing the raw data row of the instance to be augmented. Its length must
     *                        match the number of variables in the base `SemBicScore`.
     * @param populationMeans An array representing the population means for each variable. Its length must match the
     *                        number of variables in the base `SemBicScore`.
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
     * Constructs an `InstanceAugmentedSemBicScore` object using a covariance matrix, a raw row of instance data, and
     * the population mean values. This constructor initializes the object with the provided covariance matrix, computes
     * a centered representation of the instance data, and prepares the score for further use.
     *
     * @param cov             The covariance matrix used for computation. Must not be null.
     * @param instanceRowRaw  An array representing the raw data row of the instance to be augmented. Its length must
     *                        match the number of variables in the covariance matrix.
     * @param populationMeans An array representing the population means for each variable. Its length must match the
     *                        number of variables in the covariance matrix.
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

    /**
     * Retrieves the alpha value associated with this score.
     *
     * @return the alpha parameter as a double value.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /* ------------------------ Internal: instance term ------------------------ */

    /**
     * Sets the alpha value for this instance. The alpha value typically represents a parameter that may influence the
     * scoring or computations within this object.
     *
     * @param alpha The alpha parameter to set, provided as a double.
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets the variance floor for this instance. The variance floor is used as a lower limit to variance values to
     * prevent computational issues during calculations.
     *
     * @param varFloor The minimum allowable variance value. Must be greater than 0. If a value less than or equal to 0
     *                 is provided, an {@code IllegalArgumentException} is thrown.
     */
    public void setVarianceFloor(double varFloor) {
        if (varFloor <= 0) throw new IllegalArgumentException("varFloor must be > 0");
        this.varFloor = varFloor;
    }

    /* ------------------------ Linear-algebra helpers ------------------------ */

    /**
     * Retrieves the list of variables associated with the current instance score.
     *
     * @return a list of {@code Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    /**
     * Retrieves the sample size associated with the base `SemBicScore`.
     *
     * @return the sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    /**
     * Retrieves the multiplier applied to the penalty term for this score.
     *
     * @return The penalty discount as a double value.
     */
    public double getPenaltyDiscount() {
        return base.getPenaltyDiscount();
    }

    /**
     * Sets the multiplier for the penalty term in this score. Adjusts the impact of the penalty term on the computed
     * score.
     *
     * @param d The penalty discount factor to apply. This value influences the weight of the penalty term in the score
     *          calculation.
     */
    public void setPenaltyDiscount(double d) {
        base.setPenaltyDiscount(d);
    }

    /**
     * Calculates the local score for a given variable index. This method incorporates both the base local score and an
     * instance-specific term scaled by a parameter alpha to compute the final score.
     *
     * @param i The index of the variable for which the local score is to be computed.
     * @return The computed local score as a double value.
     */
    @Override
    public double localScore(int i) {
        double pop = base.localScore(i);
        double inst = instanceTerm(i, EMPTY);
        return pop + alpha * inst;
    }

    /**
     * Calculates the local score for a given variable index and its parent set. This method integrates the base local
     * score with an instance-specific term scaled by a parameter alpha to compute the final score.
     *
     * @param i       The index of the variable for which the local score is to be computed.
     * @param parents An array of indices representing the parent variables of the specified variable.
     * @return The computed local score as a double value.
     */
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

    /**
     * Retrieves the base {@code SemBicScore} associated with this instance.
     *
     * @return the base {@code SemBicScore} object used for computations.
     */
    public SemBicScore getBase() {
        return base;
    }

    /**
     * Returns a copy of the centered instance row data associated with this instance. The centered data represents the
     * difference between the raw data and the respective population means for each variable.
     *
     * @return a copy of the centered instance row as a double array.
     */
    public double[] getInstanceRowCentered() {
        return Arrays.copyOf(xCentered, xCentered.length);
    }
}