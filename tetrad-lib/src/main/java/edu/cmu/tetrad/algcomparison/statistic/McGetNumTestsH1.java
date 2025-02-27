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
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the number of tests Kolmogorov-Smirnoff under the alternative hypothesis H1 of dependennce for the Markov
 * check of whether the p-values for the estimated graph are distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class McGetNumTestsH1 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Calculates the number of tests for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     */
    public McGetNumTestsH1() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-H1-NumTests";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Num Tests H0";
    }

    /**
     * Calculates the number of tests done under the null hypothesis of independence for the Markov check of whether the
     * p-values for the estimated graph are distributed as U(0, 1).
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The Anderson Darling P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }
        IndependenceTest independenceTest;

        if (dataModel.isContinuous()) {
            independenceTest = new IndTestFisherZ((DataSet) dataModel, 0.01);
//            independenceTest = new IndTestBasisFunctionLrt((DataSet) dataModel, parameters.getInt(Params.TRUNCATION_LIMIT),
//                    parameters.getInt(Params.BASIS_TYPE), parameters.getInt(Params.BASIS_SCALE));
        } else if (dataModel.isDiscrete()) {
            independenceTest = new IndTestChiSquare((DataSet) dataModel, 0.01);
        } else if (dataModel.isMixed()) {
            independenceTest = new IndTestConditionalGaussianLrt((DataSet) dataModel, 0.01, true);
        } else {
            throw new IllegalArgumentException("Data model is not continuous, discrete, or mixed.");
        }

        MarkovCheck markovCheck = new MarkovCheck(estGraph, independenceTest, ConditioningSetType.LOCAL_MARKOV);
        markovCheck.generateResults(false, true);
        return markovCheck.getNumTests(false);
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
