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

    private int numRestarts = 1;

    /** RICF tolerance. */
    private double tolerance = 1e-3;

    /** Max RICF iterations. */
    private int maxIters = 2000;

    public SemOptimizerRicf() { }

    public static SemOptimizerRicf serializableInstance() {
        return new SemOptimizerRicf();
    }

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

    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    public void setTolerance(double tolerance) { this.tolerance = tolerance; }
    public void setMaxIters(int maxIters) { this.maxIters = maxIters; }

    @Override
    public String toString() {
        return "Sem Optimizer RICF (EJML, ADMG)";
    }
}