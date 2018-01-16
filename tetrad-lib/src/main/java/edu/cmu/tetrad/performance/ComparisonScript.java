package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.util.TextTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs algorithm on data set (simulation is OK), printing out error statistics.
 *
 * @author jdramsey 2016.03.24
 * @author dmalinsky 2016.03.28
 */
public class ComparisonScript {

    private void runFromSimulation() {
        ComparisonParameters params = new ComparisonParameters();
        params.setDataType(ComparisonParameters.DataType.Continuous); // Continuous or Discrete
        params.setNumVars(12); // number of variables
        params.setNumEdges(12); // number of edges
        int minSample = 100; // smallest sample size to generate
        int maxSample = 1000; // largest sample size to generate
        int increment = 100; // ramp up sample size by this increment
        int numTrials = 2; // number of data sets to run for a particular sample size, results will be averaged

        /** If you want to run data sets from file instead of generating random graphs **/
        params.setDataFromFile(false); // set this to true
        int maxGraphs = 2; // how many true graphs are in your directory?
        int dataSetsPerGraph = 3; // how many data sets are there in your directory for each true graph?
        // remember the path to the data directory is set in Comparison2.java
        /** ******************** **/

        /** If you want to run on NO DATA, i.e., just run each algorithm directly on some random true graphs **/
        params.setNoData(false); // set this to true
        // note that the number of random graphs will be equal to numTrials, set above
        /** ******************** **/

        if ( params.isDataFromFile() && params.isNoData() ){
            throw new IllegalArgumentException("Cannot have setDataFromFile and setNoData both be true!");
        }

        ArrayList<Comparison2.TableColumn> tableColumns= new ArrayList<>();
        tableColumns.add(Comparison2.TableColumn.AdjPrec);
        tableColumns.add(Comparison2.TableColumn.AdjRec);
        tableColumns.add(Comparison2.TableColumn.AhdPrec);
        tableColumns.add(Comparison2.TableColumn.AhdRec);
        tableColumns.add(Comparison2.TableColumn.SHD);
        tableColumns.add(Comparison2.TableColumn.Elapsed);

        List<ComparisonParameters.Algorithm> algList = new ArrayList<>();

        /** add algorithm to compare to the list algList. comment out those you don't want to consider. **/
        //algList.add(ComparisonParameters.Algorithm.PC);
        //algList.add(ComparisonParameters.Algorithm.FGES);
        //algList.add(ComparisonParameters.Algorithm.FCI);
        algList.add(ComparisonParameters.Algorithm.TsFCI);

        /** User shouldn't need to change anything below this line **/
        /***********************************************************/

        if( params.isDataFromFile() ){
            System.out.println("running algorithm on data from input files");
            minSample = 1;
            maxSample = maxGraphs;
            increment = 1;
            numTrials = dataSetsPerGraph;
        }

        if( params.isNoData() ) {
            System.out.println("running algorithm on NO DATA, only true graph");
            minSample = 1;
            maxSample = 1;
            increment = 1;
        }

        int count = 0;
        TextTable avgTable = new TextTable((((maxSample - minSample) / increment) + 1) * algList.size() + 2, tableColumns.size() + 1);
        for (int sampleSize = minSample; sampleSize <= maxSample; sampleSize += increment) {
            params.setSampleSize(sampleSize);
            if(params.isDataFromFile()){
                params.setGraphNum(sampleSize);
                System.out.println("graph file number = " + sampleSize);
            } else System.out.println("sample size = " + sampleSize);


            for (ComparisonParameters.Algorithm alg : algList) {
                count++;
                params.setAlgorithm(alg);
                params.setIndependenceTest(ComparisonParameters.IndependenceTestType.FisherZ);
                params.setScore(ScoreType.SemBic);
                // params.setOneEdgeFaithfulnessAssumed(false);

                List<ComparisonResult> resultsTrials = new ArrayList<>();
                for (int trial = 1; trial <= numTrials; trial++) {
                    params.setTrial(trial);
                    resultsTrials.add(Comparison2.compare(params));
                }
                TextTable tempTable = new TextTable(numTrials + 2, tableColumns.size() + 1);
                tempTable = Comparison2.summarize(resultsTrials, tableColumns);

                System.out.println(tempTable);

                for (int k = 0; k <= tempTable.getNumColumns() - 1; k++) {
                    avgTable.setToken(count, k, tempTable.getTokenAt(tempTable.getNumRows() - 1, k));
                }

                if(params.isDataFromFile()){
                    avgTable.setToken(count, 0, "G=" + sampleSize + ", alg = " + alg);
                } else if(params.isNoData()) {
                    avgTable.setToken(count, 0, "N=" + 0 + ", alg = " + alg);
                    } else avgTable.setToken(count, 0, "N=" + sampleSize + ", alg = " + alg);

                } // loop over algorithm in algList
            } // loop over sample sizes

            // add column names, then print table
            for (int j = 0; j <= tableColumns.size() - 1; j++) {
                avgTable.setToken(0, j, tableColumns.get(j).toString());
            }
            System.out.println(avgTable);
    }

    public static void main(String... args) {
        new ComparisonScript().runFromSimulation();
    }
}
