package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.sem.ScoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithm on data set (simulation is OK), printing out error statistics.
 *
 * @author josephramsey 2016.03.24
 * @version $Id: $Id
 */
public class ExploreComparison {

    /**
     * Private constructor to prevent instantiation.
     */
    private ExploreComparison() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(ComparisonParameters.DataType.Continuous);
        params.setAlgorithm(ComparisonParameters.Algorithm.FGES);
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

        ArrayList<Comparison.TableColumn> tableColumns = new ArrayList<>();
        tableColumns.add(Comparison.TableColumn.AdjPrec);
        tableColumns.add(Comparison.TableColumn.AdjRec);
        tableColumns.add(Comparison.TableColumn.AhdPrec);
        tableColumns.add(Comparison.TableColumn.AhdRec);
        tableColumns.add(Comparison.TableColumn.SHD);
        tableColumns.add(Comparison.TableColumn.Elapsed);

        System.out.println(Comparison.summarize(results, tableColumns));
    }
}
