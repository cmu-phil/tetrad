package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;

/**
 * Optimizes a SEM using RICF (see that class).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemOptimizerRicf implements SemOptimizer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of restarts to use.
     */
    private int numRestarts = 1;

    /**
     * Blank constructor.
     */
    public SemOptimizerRicf() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemOptimizerRicf} object
     */
    public static SemOptimizerRicf serializableInstance() {
        return new SemOptimizerRicf();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Optimizes the fitting function of the given Sem using the Powell method from Numerical Recipes by adjusting the
     * freeParameters of the Sem.
     */
    public void optimize(SemIm semIm) {
        if (this.numRestarts < 1) this.numRestarts = 1;

        if (this.numRestarts != 1) {
            throw new IllegalArgumentException("Number of restarts must be 1 for this method.");
        }

        Matrix sampleCovar = semIm.getSampleCovar();

        if (sampleCovar == null) {
            throw new NullPointerException("Sample covar has not been set.");
        }

        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        TetradLogger.getInstance().log("Trying EM...");
        //        new SemOptimizerEm().optimize(semIm);

        CovarianceMatrix cov = new CovarianceMatrix(semIm.getMeasuredNodes(),
                sampleCovar, semIm.getSampleSize());

        SemGraph graph = semIm.getSemPm().getGraph();
        Ricf.RicfResult result = new Ricf().ricf(graph, cov, 0.001);

        Matrix bHat = new Matrix(result.getBhat().toArray());
        Matrix lHat = new Matrix(result.getLhat().toArray());
        Matrix oHat = new Matrix(result.getOhat().toArray());

        for (Parameter param : semIm.getFreeParameters()) {
            if (param.getType() == ParamType.COEF) {
                int i = semIm.getSemPm().getVariableNodes().indexOf(param.getNodeA());
                int j = semIm.getSemPm().getVariableNodes().indexOf(param.getNodeB());
                semIm.setEdgeCoef(param.getNodeA(), param.getNodeB(), -bHat.get(j, i));
            }

            if (param.getType() == ParamType.VAR) {
                int i = semIm.getSemPm().getVariableNodes().indexOf(param.getNodeA());
                if (lHat.get(i, i) != 0) {
                    semIm.setErrVar(param.getNodeA(), lHat.get(i, i));
                } else if (oHat.get(i, i) != 0) {
                    semIm.setErrVar(param.getNodeA(), oHat.get(i, i));
                }
            }

            if (param.getType() == ParamType.COVAR) {
                int i = semIm.getSemPm().getVariableNodes().indexOf(param.getNodeA());
                int j = semIm.getSemPm().getVariableNodes().indexOf(param.getNodeB());
                if (lHat.get(i, i) != 0) {
                    semIm.setErrCovar(param.getNodeA(), param.getNodeB(), lHat.get(j, i));
                } else if (oHat.get(i, i) != 0) {
                    semIm.setErrCovar(param.getNodeA(), param.getNodeB(), oHat.get(j, i));
                }
            }
        }

        System.out.println(result);
        System.out.println(semIm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Sem Optimizer RICF";
    }
}



