package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;
import edu.cmu.tetrad.search.test.IndTestFisherZ;

import java.io.Serial;

/**
 * Represents a markov check statistic that calculates the Binomial P value for whether the p-values for the estimated
 * graph are distributed as U(0, 1). This version reports the best p-value out of 10 repetitions.
 *
 * @author josephramsey
 */
public class MarkovCheckBinomialPBestOf10 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     */
    public MarkovCheckBinomialPBestOf10() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-BP10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Binomial P; best of 10 reps";
    }

    /**
     * Calculates the Binomial P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The Binomial P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        IndependenceTest independenceTest;

        if (dataModel.isContinuous()) {
            independenceTest = new IndTestFisherZ((DataSet) dataModel, 0.01);
        } else if (dataModel.isDiscrete()) {
            independenceTest = new IndTestChiSquare((DataSet) dataModel, 0.01);
        } else if (dataModel.isMixed()) {
            independenceTest = new IndTestConditionalGaussianLrt((DataSet) dataModel, 0.01, true);
        } else {
            throw new IllegalArgumentException("Data model is not continuous, discrete, or mixed.");
        }

        // Find the best of 10 repetitions
        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 10; i++) {
            MarkovCheck markovCheck = new MarkovCheck(estGraph, independenceTest, ConditioningSetType.LOCAL_MARKOV);
            markovCheck.generateResults(true);
            double p = markovCheck.getBinomialPValue(true);
            if (p > max) {
                max = p;
            }
        }

        return max;
    }

    /**
     * Calculates the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
