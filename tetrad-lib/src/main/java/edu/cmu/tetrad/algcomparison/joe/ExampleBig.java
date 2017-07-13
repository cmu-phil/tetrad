package edu.cmu.tetrad.algcomparison.joe;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.score.FisherZScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * Created by user on 7/13/17.
 */
public class ExampleBig {

    public static void main(String...args) {
        int numMeasures = 5000;//Integer.parseInt(args[0]);
        int avgDegree = 6;//Integer.parseInt(args[1]);

        Parameters parameters = new Parameters();

        parameters.set("numMeasures", numMeasures);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 20);
        parameters.set("maxIndegree", 20);
        parameters.set("maxOutdegree", 20);
        parameters.set("connected", false);

        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 0.9);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 3);
        parameters.set("verbose", false);
        parameters.set("coefSymmetric", true);
        parameters.set("numRuns", 1);
        parameters.set("percentDiscrete", 0);
        parameters.set("numCategories", 3);
        parameters.set("differentGraphs", true);
        parameters.set("sampleSize", 1000);
        parameters.set("intervalBetweenShocks", 10);
        parameters.set("intervalBetweenRecordings", 10);
        parameters.set("fisherEpsilon", 0.001);
        parameters.set("randomizeColumns", true);

        parameters.set("symmetricFirstStep", false);
        parameters.set("faithfulnessAssumed", true);
        parameters.set("maxDegree", 100);

        RandomGraph graph = new RandomForward();
        LinearFisherModel sim = new LinearFisherModel(graph);
        sim.createData(parameters);
        ScoreWrapper score = new FisherZScore();
        Algorithm alg = new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(score);

        parameters.set("alpha", 1e-4);

        for (int i  = 0; i < 5; i++) {
            long start = System.currentTimeMillis();

            Graph out1 = alg.search(sim.getDataModel(0), parameters);

            long stop = System.currentTimeMillis();

            System.out.println("Elapased " + (stop - start) / 1000.0 + " seconds");


//            System.out.println(out1);
        }
    }
}
