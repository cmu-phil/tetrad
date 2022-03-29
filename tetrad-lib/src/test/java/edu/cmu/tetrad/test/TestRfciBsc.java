package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.XdslXmlParser;
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

        final Graph g = GraphConverter.convert("X1-->X2,X1-->X3,X1-->X4,X1-->X5,X2-->X3,X2-->X4,X2-->X6,X3-->X4,X4-->X5,X5-->X6");
        final Dag dag = new Dag(g);

        // set a number of latent variables
        //int LV = 1;
        //GraphUtils.fixLatents4(LV, dag);
        //System.out.println("Variables set to be latent:" + getLatents(dag));

        final BayesPm bayesPm = new BayesPm(dag);
        final BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        // simulate data from instantiated model
        final DataSet fullData = bayesIm.simulateData(sampleSize, seed, true);
        refineData(fullData);
        final DataSet dataSet = DataUtils.restrictToMeasured(fullData);

        // get the true underlying PAG
        final DagToPag2 dagToPag = new DagToPag2(dag);
        dagToPag.setCompleteRuleSetUsed(false);
        Graph PAG_True = dagToPag.convert();
        PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

        final IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
        final edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
        rfci.setVerbose(true);
        rfci.setCompleteRuleSetUsed(false);
        rfci.setDepth(depth);

        final edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(true);

        final long start = System.currentTimeMillis();

        rfciBsc.search();

        final long stop = System.currentTimeMillis();

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
        final BayesIm im = loadBayesIm(modelName);
        final BayesPm pm = im.getBayesPm();
        final Graph dag = pm.getDag();

        // set a number of latent variables
        final int LV = 4;
        GraphUtils.fixLatents4(LV, dag);
        System.out.println("Variables set to be latent:" + getLatents(dag));

        // simulate data from instantiated model
        final DataSet fullData = im.simulateData(sampleSize, seed, true);
        refineData(fullData);

        final DataSet dataSet = DataUtils.restrictToMeasured(fullData);

        // get the true underlying PAG
        final DagToPag2 dagToPag = new DagToPag2(dag);
        dagToPag.setCompleteRuleSetUsed(false);
        Graph PAG_True = dagToPag.convert();
        PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

        final IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
        final edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
        rfci.setVerbose(true);
        rfci.setCompleteRuleSetUsed(false);
        rfci.setDepth(depth);

        final edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(true);

        final long start = System.currentTimeMillis();

        rfciBsc.search();

        final long stop = System.currentTimeMillis();

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

    private static void refineData(final DataSet fullData) {
        for (int c = 0; c < fullData.getNumColumns(); c++) {
            for (int r = 0; r < fullData.getNumRows(); r++) {
                if (fullData.getInt(r, c) < 0) {
                    fullData.setInt(r, c, 0);
                }
            }
        }

    }

    private static List<Node> getLatents(final Graph dag) {
        final List<Node> latents = new ArrayList<>();
        for (final Node n : dag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                latents.add(n);
            }
        }
        return latents;
    }

    private static BayesIm loadBayesIm(final String filename) {
        try {
            final Builder builder = new Builder();
            final File file = new File("src/test/resources/" + filename);
            System.out.println(file.getAbsolutePath());
            final Document document = builder.build(file);
            final XdslXmlParser parser = new XdslXmlParser();
            parser.setUseDisplayNames(true);
            return parser.getBayesIm(document.getRootElement());
        } catch (final ParsingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

}
