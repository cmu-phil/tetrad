package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestProbabilistic;
import edu.cmu.tetrad.search.utils.BayesImParser;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.RandomUtil;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.ParsingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jan 10, 2019 12:23:21 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class TestRfciBsc {

    private static void refineData(DataSet fullData) {
        for (int c = 0; c < fullData.getNumColumns(); c++) {
            for (int r = 0; r < fullData.getNumRows(); r++) {
                if (fullData.getInt(r, c) < 0) {
                    fullData.setInt(r, c, 0);
                }
            }
        }

    }

    private static List<Node> getLatents(Graph dag) {
        List<Node> latents = new ArrayList<>();
        for (Node n : dag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                latents.add(n);
            }
        }
        return latents;
    }

    private static BayesIm loadBayesIm() {
        try {
            Builder builder = new Builder();
            File file = new File("src/test/resources/" + "Alarm.xdsl");
            System.out.println(file.getAbsolutePath());
            Document document = builder.build(file);
            BayesImParser parser = new BayesImParser();
            parser.setUseDisplayNames(true);
            return parser.getBayesIm(document.getRootElement());
        } catch (ParsingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //	@Test
    public void testRandomDiscreteData() {
        // RFCI
        final int depth = 5;
        // BSC
        final int numModels = 10;
        final int numBootstrapSamples = 100;
        final int sampleSize = 1000;
        final double lower = 0.3;
        final double upper = 0.7;

        final long seed = 878376L;
        RandomUtil.getInstance().setSeed(seed);

        Graph g = GraphUtils.convert("X1-->X2,X1-->X3,X1-->X4,X1-->X5,X2-->X3,X2-->X4,X2-->X6,X3-->X4,X4-->X5,X5-->X6");
        Dag dag = new Dag(g);

        // set a number of latent variables

        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.InitializationMethod.RANDOM);

        RandomUtil.getInstance().setSeed(seed);

        // simulate data from instantiated model
        DataSet fullData = bayesIm.simulateData(sampleSize, true);
        TestRfciBsc.refineData(fullData);
        DataSet dataSet = DataTransforms.restrictToMeasured(fullData);

        // get the true underlying PAG
//        DagToPag dagToPag = new DagToPag(dag);
//        dagToPag.setCompleteRuleSetUsed(false);
//        Graph PAG_True = dagToPag.convert();

        Graph PAG_True = GraphTransforms.dagToPag(dag);

        PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

        IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
        edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
        rfci.setVerbose(true);
        rfci.setDepth(depth);

        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(true);

        long start = MillisecondTimes.timeMillis();

        rfciBsc.search();

        long stop = MillisecondTimes.timeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");
        System.out.println("\nBSC-I: " + rfciBsc.getBscI());
        System.out.println("\nBSC-D: " + rfciBsc.getBscD());
        System.out.println("------------------------------------------");
        System.out.println("PAG_True: \n" + PAG_True);
        System.out.println("------------------------------------------");
        System.out.println("RB-I: \n" + rfciBsc.getGraphRBI());
        System.out.println("------------------------------------------");
        System.out.println("RB-D: \n" + rfciBsc.getGraphRBD());
    }

    //	@Test
    public void testDiscreteRealData() {
        // Dataset
        final String modelName = "Alarm.xdsl";
        // RFCI
        final int depth = 5;
        // BSC
        final int numModels = 10;
        final int numBootstrapSamples = 10;
        final int sampleSize = 500;
        final double lower = 0.3;
        final double upper = 0.7;

        final long seed = 878376L;
        RandomUtil.getInstance().setSeed(seed);

        // get the Bayesian network (graph and parameters) of the given model
        BayesIm im = TestRfciBsc.loadBayesIm();
        BayesPm pm = im.getBayesPm();
        Graph dag = pm.getDag();

        // set a number of latent variables
        final int LV = 4;
        RandomGraph.fixLatents4(LV, dag);
        System.out.println("Variables set to be latent:" + TestRfciBsc.getLatents(dag));

        RandomUtil.getInstance().setSeed(seed);

        // simulate data from instantiated model
        DataSet fullData = im.simulateData(sampleSize, true);
        TestRfciBsc.refineData(fullData);

        DataSet dataSet = DataTransforms.restrictToMeasured(fullData);

        // get the true underlying PAG
//        DagToPag dagToPag = new DagToPag(dag);
//        dagToPag.setCompleteRuleSetUsed(false);
//        Graph PAG_True = dagToPag.convert();

        Graph PAG_True = GraphTransforms.dagToPag(dag);

        PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

        IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
        edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
        rfci.setVerbose(true);
        rfci.setDepth(depth);

        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(true);

        long start = MillisecondTimes.timeMillis();

        rfciBsc.search();

        long stop = MillisecondTimes.timeMillis();

        System.out.println("Elapsed " + (stop - start) + " ms");
        System.out.println("\nBSC-I: " + rfciBsc.getBscI());
        System.out.println("\nBSC-D: " + rfciBsc.getBscD());
        System.out.println("------------------------------------------");
        System.out.println("PAG_True: \n" + PAG_True);
        System.out.println("------------------------------------------");
        System.out.println("RB-I: \n" + rfciBsc.getGraphRBI());
        System.out.println("------------------------------------------");
        System.out.println("RB-D: \n" + rfciBsc.getGraphRBD());
    }

}
