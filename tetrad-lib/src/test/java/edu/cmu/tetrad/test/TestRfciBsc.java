/**
 * 
 */
package edu.cmu.tetrad.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphConverter;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.XdslXmlParser;
import edu.cmu.tetrad.util.RandomUtil;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.ParsingException;

/**
 * Jan 10, 2019 12:23:21 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class TestRfciBsc {

	@Test
	public void testRandomDiscreteData() {
		// RFCI
		int depth = 5;
		boolean verbose = true;
		boolean completeRuleSetUsed = false;
		// BSC
		int numModels = 10;
		int numBootstrapSamples = 100;
		int sampleSize = 10000;
		double lower = 0.3;
		double upper = 0.7;
		
		Long seed = 878376L;
		RandomUtil.getInstance().setSeed(seed);
		
        Graph g = GraphConverter.convert("X1-->X2,X1-->X3,X1-->X4,X1-->X5,X2-->X3,X2-->X4,X2-->X6,X3-->X4,X4-->X5,X5-->X6");
        Dag dag = new Dag(g);
        
		// set a number of latent variables
		//int LV = 1;
		//GraphUtils.fixLatents4(LV, dag);
		//System.out.println("Variables set to be latent:" + getLatents(dag));
        
        BayesPm bayesPm = new BayesPm(dag);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        
		// simulate data from instantiated model
		DataSet fullData = bayesIm.simulateData(sampleSize, seed, true);
		fullData = refineData(fullData);
		DataSet dataSet = DataUtils.restrictToMeasured(fullData);

		// get the true underlying PAG
		final DagToPag2 dagToPag = new DagToPag2(dag);
		dagToPag.setCompleteRuleSetUsed(false);
		Graph PAG_True = dagToPag.convert();
		PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

		IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
		edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
		rfci.setVerbose(verbose);
		rfci.setCompleteRuleSetUsed(completeRuleSetUsed);
		rfci.setDepth(depth);
		
        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(verbose);
        
        long start = System.currentTimeMillis();
        
        rfciBsc.search();
        
        long stop = System.currentTimeMillis();

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
	
	@Test
	public void testDiscreteRealData() {
		// Dataset
		String modelName = "Alarm.xdsl";
		// RFCI
		int depth = 5;
		boolean verbose = true;
		boolean completeRuleSetUsed = false;
		// BSC
		int numModels = 10;
		int numBootstrapSamples = 100;
		int sampleSize = 1000;
		double lower = 0.3;
		double upper = 0.7;
		
		Long seed = 878376L;
		RandomUtil.getInstance().setSeed(seed);

		// get the Bayesian network (graph and parameters) of the given model
		BayesIm im = loadBayesIm(modelName, true);
		BayesPm pm = im.getBayesPm();
		Graph dag = pm.getDag();
		
		// set a number of latent variables
		int LV = 4;
		GraphUtils.fixLatents4(LV, dag);
		System.out.println("Variables set to be latent:" + getLatents(dag));

		// simulate data from instantiated model
		DataSet fullData = im.simulateData(sampleSize, seed, true);
		fullData = refineData(fullData);
		
		DataSet dataSet = DataUtils.restrictToMeasured(fullData);

		// get the true underlying PAG
		final DagToPag2 dagToPag = new DagToPag2(dag);
		dagToPag.setCompleteRuleSetUsed(false);
		Graph PAG_True = dagToPag.convert();
		PAG_True = GraphUtils.replaceNodes(PAG_True, dataSet.getVariables());

		IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
		edu.cmu.tetrad.search.Rfci rfci = new edu.cmu.tetrad.search.Rfci(test);
		rfci.setVerbose(verbose);
		rfci.setCompleteRuleSetUsed(completeRuleSetUsed);
		rfci.setDepth(depth);
		
        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc rfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(rfci);
        rfciBsc.setNumBscBootstrapSamples(numBootstrapSamples);
        rfciBsc.setNumRandomizedSearchModels(numModels);
        rfciBsc.setLowerBound(lower);
        rfciBsc.setUpperBound(upper);
        rfciBsc.setOutputRBD(true);
        rfciBsc.setVerbose(verbose);
        
        long start = System.currentTimeMillis();
        
        rfciBsc.search();
        
        long stop = System.currentTimeMillis();

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
	
	private static DataSet refineData(DataSet fullData) {
		for (int c = 0; c < fullData.getNumColumns(); c++) {
			for (int r = 0; r < fullData.getNumRows(); r++) {
				if (fullData.getInt(r, c) < 0) {
					fullData.setInt(r, c, 0);
				}
			}
		}

		return fullData;
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

	private static BayesIm loadBayesIm(String filename, boolean useDisplayNames) {
		try {
			Builder builder = new Builder();
			File file = new File("src/test/resources/" + filename);
			System.out.println(file.getAbsolutePath());
			Document document = builder.build(file);
			XdslXmlParser parser = new XdslXmlParser();
			parser.setUseDisplayNames(useDisplayNames);
			return parser.getBayesIm(document.getRootElement());
		} catch (ParsingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
