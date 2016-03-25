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
        params.setAlgorithm(ComparisonParameters.Algorithm.GFCI);
        params.setIndependenceTest(ComparisonParameters.IndependenceTestType.ChiSquare);
//        params.setScore(ComparisonParameters.ScoreType.SemBic);

        List<ComparisonResult> results = new ArrayList<>();

        for (int sampleSize = 100; sampleSize <= 2000; sampleSize += 100) {
            params.setSampleSize(sampleSize);
            results.add(Comparison.compare(params));
        }

        ArrayList<Comparison.TableColumns> tableColumns= new ArrayList<>();
        tableColumns.add(Comparison.TableColumns.AdjPrec);
        tableColumns.add(Comparison.TableColumns.AdjRec);
        tableColumns.add(Comparison.TableColumns.AhdPrec);
        tableColumns.add(Comparison.TableColumns.AdjRec);
        tableColumns.add(Comparison.TableColumns.SHD);
        tableColumns.add(Comparison.TableColumns.Elapsed);

        System.out.println(Comparison.summarize(results, tableColumns));
    }

    public static void main(String... args) {
        new ExploreComparison().runFromSimulation();
    }
}
