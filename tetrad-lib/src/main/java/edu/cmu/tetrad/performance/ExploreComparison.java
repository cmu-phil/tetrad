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
        params.setDataType(ComparisonParameters.DataType.Continuous);
        params.setAlgorithm(ComparisonParameters.Algorithm.FGS2);
//        params.setIndependenceTest(ComparisonParameters.IndependenceTestType.FisherZ);
        params.setScore(ComparisonParameters.ScoreType.SemBic);
//        params.setOneEdgeFaithfulnessAssumed(false);
        params.setNumVars(500);
        params.setNumEdges(1000);

        List<ComparisonResult> results = new ArrayList<>();

        for (int sampleSize = 100; sampleSize <= 2000; sampleSize += 100) {
            params.setSampleSize(sampleSize);
            results.add(Comparison.compare(params));
        }

        ArrayList<Comparison.TableColumn> tableColumns= new ArrayList<>();
        tableColumns.add(Comparison.TableColumn.AdjPrec);
        tableColumns.add(Comparison.TableColumn.AdjRec);
        tableColumns.add(Comparison.TableColumn.AhdPrec);
        tableColumns.add(Comparison.TableColumn.AhdRec);
        tableColumns.add(Comparison.TableColumn.SHD);
        tableColumns.add(Comparison.TableColumn.Elapsed);

        System.out.println(Comparison.summarize(results, tableColumns));
    }

    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }
}
