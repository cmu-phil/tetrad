package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.performance.Comparison.TableColumn;
import edu.cmu.tetrad.performance.ComparisonParameters.Algorithm;
import edu.cmu.tetrad.performance.ComparisonParameters.DataType;
import edu.cmu.tetrad.sem.ScoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithm on data set (simulation is OK), printing out error statistics.
 *
 * @author jdramsey 2016.03.24
 */
public class ExploreComparison {

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(DataType.Continuous);
        params.setAlgorithm(Algorithm.FGES2);
//        params.setIndependenceTest(ComparisonParameters.IndependenceTestType.FisherZ);
        params.setScore(ScoreType.SemBic);
//        params.setOneEdgeFaithfulnessAssumed(false);
        params.setNumVars(100);
        params.setNumEdges(100);
        params.setPenaltyDiscount(4);

        List<ComparisonResult> results = new ArrayList<>();

        for (int sampleSize = 1000; sampleSize <= 1000; sampleSize += 100) {
            params.setSampleSize(sampleSize);
            results.add(Comparison.compare(params));
        }

        ArrayList<TableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(TableColumn.AdjPrec);
        tableColumns.add(TableColumn.AdjRec);
        tableColumns.add(TableColumn.AhdPrec);
        tableColumns.add(TableColumn.AhdRec);
        tableColumns.add(TableColumn.SHD);
        tableColumns.add(TableColumn.Elapsed);

        System.out.println(Comparison.summarize(results, tableColumns));
    }

    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }
}
