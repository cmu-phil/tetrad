package edu.cmu.tetrad.performance;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithms on data set (simulation is OK), printing out error statistics.
 *
 * @author jdramsey 2016.03.24
 */
public class ExploreComparison {

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(ComparisonParameters.DataType.Discrete);
        params.setAlgorithm(ComparisonParameters.Algorithm.Pc);
        params.setNumVars(100);
        params.setNumEdges(100);
        params.setIndependenceTest(ComparisonParameters.IndependenceTestType.ChiSquare);
        params.setAlpha(0.001);
//        params.setScore(ComparisonParameters.ScoreType.Bdeu);
//        params.setPenaltyDiscount(4);

        List<ComparisonResult> results = new ArrayList<>();

        for (int sampleSize = 100; sampleSize <= 2000; sampleSize += 100) {
            params.setSampleSize(sampleSize);
            results.add(Comparison.compare(params));
        }

        System.out.println(Comparison.summarize(results));
    }

    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }

}
