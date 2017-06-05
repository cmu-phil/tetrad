package edu.cmu.tetrad.algcomparison.directory_joe.ps7z;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

public class ExampleCompareFromFiles {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        // Can leave the simulation parameters out since
        // we're loading from file here
        parameters.set("alpha", 5e-2);
        parameters.set("maxDistinctValuesDiscrete", 5);
        parameters.set("symmetricFirstStep", true);
        parameters.set("depth", 4);
        parameters.set("penaltyDiscount", 2);
        parameters.set("numRuns",2);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TailPrecision());
        statistics.add(new TailRecall());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);
        statistics.setWeight("TP", 1.0);
        statistics.setWeight("TR", 0.5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Gfci(new ChiSquare(), new BdeuScore()));
//        algorithms.add(new Gfci(new ChiSquare(), new DiscreteBicScore()));
//        algorithms.add(new Fci(new FisherZ()));
//        algorithms.add(new Fci(new ChiSquare()));
        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));
//        algorithms.add(new Fges(new BdeuScore(),true));
//        algorithms.add(new Fges(new DiscreteBicScore(),true));
//        algorithms.add(new Fges(new SemBicScore()));
//        algorithms.add(new Gfci(new ChiSquare(), new DiscreteBicScore())));
//       algorithms.add(new Fges(new BdeuScore()));
//        algorithms.add(new Fges(new DiscreteBicScore()));
//        algorithms.add(new PcMax(new FisherZ(), false));
//        algorithms.add(new Pc(new ChiSquare()));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);
        //DagToPag p = new DagToPag(graph);

        comparison.compareFromFiles("comparison12", algorithms, statistics, parameters);
    }
}
