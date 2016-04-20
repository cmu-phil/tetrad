package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.util.TextTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithms on data set (simulation is OK), printing out error statistics.
 *
 * @author jdramsey 2016.03.24
 * modified by dmalinsky 2016.03.28
 */
public class ComparisonScript {

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(ComparisonParameters.DataType.Continuous);
        params.setNumVars(20);
        params.setNumEdges(40);
        int minSample = 100;
        int maxSample = 2000;
        int increment = 100;
        params.setNoData(false);

        int numTrials = 100;

        ArrayList<Comparison2.TableColumn> tableColumns= new ArrayList<>();
        tableColumns.add(Comparison2.TableColumn.AdjPrec);
        tableColumns.add(Comparison2.TableColumn.AdjRec);
        tableColumns.add(Comparison2.TableColumn.AhdPrec);
        tableColumns.add(Comparison2.TableColumn.AhdRec);
        tableColumns.add(Comparison2.TableColumn.SHD);
        tableColumns.add(Comparison2.TableColumn.Elapsed);

        // List<ComparisonResult> results = new ArrayList<>(); //not currently using this
        List<ComparisonParameters.Algorithm> algList = new ArrayList<>();

        // add algorithms to compare to the list algList. comment out those you don't want to consider.
        algList.add(ComparisonParameters.Algorithm.PC);
        algList.add(ComparisonParameters.Algorithm.FGS);

        // currently this runs multiple algs on the same sample size but NOT on the same data!


        if( params.isNoData() ) {
            System.out.println("running algorithms on NO DATA, only true graph");
            minSample = 1;
            maxSample = 1;
            increment = 1;
        }

        int count = 0;
        TextTable avgTable = new TextTable((((maxSample - minSample) / increment) + 1) * algList.size() + 2, tableColumns.size() + 1);
        for (int sampleSize = minSample; sampleSize <= maxSample; sampleSize += increment) {
            params.setSampleSize(sampleSize);
            System.out.println("sample size = " + sampleSize);
            for (ComparisonParameters.Algorithm alg : algList) {
                count++;
                params.setAlgorithm(alg);
                params.setIndependenceTest(ComparisonParameters.IndependenceTestType.FisherZ);
                params.setScore(ComparisonParameters.ScoreType.SemBic);
                // params.setOneEdgeFaithfulnessAssumed(false);

                // trials loop start
                List<ComparisonResult> resultsTrials = new ArrayList<>();
                for (int trial = 1; trial <= numTrials; trial++) {
                    System.out.println("trial # = " + trial);
                    resultsTrials.add(Comparison2.compare(params));
                }
                TextTable tempTable = new TextTable(numTrials + 2, tableColumns.size() + 1);
                tempTable = Comparison2.summarize(resultsTrials, tableColumns);

                System.out.println(tempTable);
                //System.out.println(tempTable.getTokenAt(tempTable.getNumRows()-1, tempTable.getNumColumns()-1));

                for (int k = 0; k <= tempTable.getNumColumns() - 1; k++) {
                    avgTable.setToken(count, k, tempTable.getTokenAt(tempTable.getNumRows() - 1, k));
                }

                avgTable.setToken(count, 0, "N=" + sampleSize + ", alg = " + alg);
                // results.add(Comparison.compare(params));
                } // loop over algorithms in algList
            } // loop over sample sizes

            // add column names, then print table
            for (int j = 0; j <= tableColumns.size() - 1; j++) {
                avgTable.setToken(0, j, tableColumns.get(j).toString());
            }
            System.out.println(avgTable);


        //System.out.println(Comparison.summarize(results, tableColumns));
    }

    public static void main(String... args) {
        new ComparisonScript().runFromSimulation();
    }
}
