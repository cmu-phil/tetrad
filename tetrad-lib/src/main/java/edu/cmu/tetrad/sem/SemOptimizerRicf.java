package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.search.RicfEjml;   // <-- new
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.List;

/**
 * Optimizes a SEM using RICF for ADMGs (no selection bias).
 */
public class SemOptimizerRicf implements SemOptimizer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents the number of restarts allowed for the optimization process.
     * This variable is used to control the maximum number of times the optimization
     * algorithm can restart if it fails to converge to a solution in previous attempts.
     * It is initialized to a default value of 1.
     */
    private int numRestarts = 1;

    /** RICF tolerance. */
    private double tolerance = 1e-3;

    /** Max RICF iterations. */
    private int maxIters = 2000;

    /**
     * Default constructor for the SemOptimizerRicf class. This class implements
     * a structural equation model (SEM) optimizer using the Regression Iterative
     * Conditional Fitting (RICF) algorithm. The optimizer is specifically designed
     * for models with Acyclic Directed Mixed Graphs (ADMGs) and is built on the
     * EJML library.
     *
     * The constructor initializes a new instance with default configurations.
     * The optimizer allows customization, including setting the number of restarts,
     * tolerance, and maximum iterations.
     */
    public SemOptimizerRicf() { }

    /**
     * Creates and returns a new instance of the SemOptimizerRicf class that is serializable.
     * This method is typically used for serialization purposes to provide a consistent
     * and accessible way to instantiate the class.
     *
     * @return a new serializable instance of the SemOptimizerRicf class.
     */
    public static SemOptimizerRicf serializableInstance() {
        return new SemOptimizerRicf();
    }

    /**
     * Optimizes the given structural equation model by utilizing the RICF (Regression Iterative Conditional Fitting)
     * algorithm based on the EJML library, tailored for models with ADMG (Acyclic Directed Mixed Graphs) and no
     * selection bias. The method updates the SEM parameters such as edge coefficients, error variances, and error
     * covariances based on the input specification.
     *
     * @param semIm the structural equation model (SEM) instance to be optimized. It must contain the sample
     *              covariance matrix and a defined SEM parameterization. The method checks for missing values
     *              in the covariance matrix, and an exception is thrown if such values are found. The number of
     *              restarts for the optimizer must be explicitly set to 1 before this method is called.
     *              Results are logged after the RICF optimization is complete.
     */
    @Override
    public void optimize(SemIm semIm) {
        if (this.numRestarts < 1) this.numRestarts = 1;
        if (this.numRestarts != 1) {
            throw new IllegalArgumentException("Number of restarts must be 1 for this method.");
        }

        Matrix sampleCovar = semIm.getSampleCovar();
        if (sampleCovar == null) throw new NullPointerException("Sample covar has not been set.");
        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        CovarianceMatrix cov = new CovarianceMatrix(
                semIm.getMeasuredNodes(),
                sampleCovar,
                semIm.getSampleSize()
        );

        SemGraph graph = semIm.getSemPm().getGraph();

        TetradLogger.getInstance().log("Running RICF (EJML, ADMG/no-selection-bias) ...");

        RicfEjml.RicfResult r = new RicfEjml().ricf(graph, cov, tolerance, maxIters);

        // B = I - Beta, so Beta(j <- i) = -B(j,i) for i -> j
        // Omega is the error covariance (bidirected structure) in the observed-variable order.
        Matrix bHat = new Matrix(SimpleMatrix.wrap(r.getBhat()));
        Matrix omegaHat = new Matrix(SimpleMatrix.wrap(r.getOmegahat()));

        List<Node> vars = semIm.getSemPm().getVariableNodes();

        for (Parameter param : semIm.getFreeParameters()) {
            Node A = param.getNodeA();
            Node B = param.getNodeB();

            int i = vars.indexOf(A);
            int j = (B == null ? -1 : vars.indexOf(B));

            if (i < 0) continue;
            if (param.getType() == ParamType.COEF) {
                if (j < 0) continue;
                // edge A -> B, coefficient = Beta(B <- A) = -Bhat(B, A)
                double beta = -bHat.get(j, i);
                semIm.setEdgeCoef(A, B, beta);
            }
            else if (param.getType() == ParamType.VAR) {
                double v = omegaHat.get(i, i);
                if (v > 0.0) semIm.setErrVar(A, v);
            }
            else if (param.getType() == ParamType.COVAR) {
                if (j < 0) continue;
                double c = omegaHat.get(j, i);
                semIm.setErrCovar(A, B, c);
            }
        }

        TetradLogger.getInstance().log("RICF done: iters=" + r.getIters() + " diff=" + r.getDiff());
    }

    /**
     * Returns the number of restarts used by the optimizer.
     *
     * @return the number of restarts currently set for the optimizer.
     */
    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    /**
     * Sets the number of restarts for the optimizer. The number of restarts determines
     * how many times the optimization process is repeated to potentially improve results.
     *
     * @param numRestarts the number of restarts to set for the optimizer. Must be a non-negative integer.
     */
    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    /**
     * Sets the tolerance level for the optimizer. The tolerance is typically used as a
     * convergence criterion to determine when the optimization process should stop.
     * A smaller value allows for more precise optimization but may increase computation time.
     *
     * @param tolerance the tolerance value to set. Must be a non-negative double.
     */
    public void setTolerance(double tolerance) { this.tolerance = tolerance; }

    /**
     * Sets the maximum number of iterations for the optimizer. The maximum iteration limit
     * is used to manage the computational effort and prevent the optimization process from
     * running excessively long if convergence is not achieved within the specified limit.
     *
     * @param maxIters the maximum number of iterations to allow, specified as a positive integer.
     */
    public void setMaxIters(int maxIters) { this.maxIters = maxIters; }

    /**
     * Returns a string representation of the SemOptimizerRicf instance,
     * providing information about the algorithm and underlying libraries.
     *
     * @return a string describing the SemOptimizerRicf as "Sem Optimizer RICF (EJML, ADMG)".
     */
    @Override
    public String toString() {
        return "Sem Optimizer RICF (EJML, ADMG)";
    }
}