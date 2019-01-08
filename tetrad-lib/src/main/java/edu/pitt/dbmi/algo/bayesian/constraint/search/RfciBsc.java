/**
 * 
 */
package edu.pitt.dbmi.algo.bayesian.constraint.search;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.XdslXmlParser;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.ParsingException;

/**
 * Dec 17, 2018 3:28:15 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RfciBsc implements GraphSearch {

	private final Rfci rfci;

	private Graph graphBND = null, graphBNI = null;

	private double normalizedlnQBSCD = 0.0, normalizedlnQBSCI = 0.0;

	private List<Graph> pags = new ArrayList<>();

	private int numRandomizedSearchModels = 5;

	private int numBscBootstrapSamples = 10;

	private double lowerBound = 0.3;

	private double upperBound = 0.7;

	private static final int MININUM_EXPONENT = -1022;

	private boolean outputBND = true;

	public RfciBsc(Rfci rfci) {
		this.rfci = rfci;
	}

	@Override
	public Graph search() {

		IndTestProbabilistic test = (IndTestProbabilistic) rfci.getIndependenceTest();
		test.setThreshold(false);

		pags.clear();

		// run RFCI-BSC (RB) search using BSC test and obtain constraints that
		// are queried during the search
		// ***************
		// can be parallel
		// ***************
		System.out.println("numRandomizedSearchModels: " + numRandomizedSearchModels);
		for (int i = 0; i < numRandomizedSearchModels; i++) {
			Graph pag = rfci.search();
			pag = GraphUtils.replaceNodes(pag, test.getVariables());
			System.out.println("pag: " + i);
			System.out.println(pag);
			pags.add(pag);
		}

		// A map from independence facts to their probabilities of independence.
		Map<IndependenceFact, Double> h = test.getH();

		// create empirical data for constraints
		DataSet data = DataUtils.getDiscreteDataSet(test.getData());

		List<Node> vars = new ArrayList<>();
		Map<IndependenceFact, Double> hCopy = new HashMap<>();
		for (IndependenceFact f : h.keySet()) {
			if (h.get(f) > lowerBound && h.get(f) < upperBound) {
				hCopy.put(f, h.get(f));
				DiscreteVariable var = new DiscreteVariable(f.toString());
				vars.add(var);
			}
		}

		DataSet depData = new ColtDataSet(numBscBootstrapSamples, vars);

		// ***************
		// can be parallel
		// ***************
		for (int b = 0; b < numBscBootstrapSamples; b++) {
			DataSet bsData = DataUtils.getBootstrapSample(data, data.getNumRows());
			IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
			bsTest.setThreshold(true);
			for (IndependenceFact f : hCopy.keySet()) {
				boolean ind = bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
				int value = ind ? 1 : 0;
				depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
			}
		}

		// learn structure of constraints using empirical data => constraint meta data
		BDeuScore sd = new BDeuScore(depData);
		sd.setSamplePrior(1.0);
		sd.setStructurePrior(1.0);

		Fges fges = new Fges(sd);
		fges.setVerbose(false);
		fges.setNumPatternsToStore(0);
		fges.setFaithfulnessAssumed(true);

		Graph depPattern = fges.search();
		depPattern = GraphUtils.replaceNodes(depPattern, depData.getVariables());
		Graph estDepBN = SearchGraphUtils.dagFromPattern(depPattern);

		// estimate parameters of the graph learned for constraints
		BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
		DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
		BayesIm imHat = DirichletEstimator.estimate(prior, depData);

		// compute scores of graphs that are output by RB search using
		// BSC-I and BSC-D methods
		Map<Graph, Double> pagLnBSCD = new HashMap<>();
		Map<Graph, Double> pagLnBSCI = new HashMap<>();

		double maxLnDep = -1, maxLnInd = -1;

		for (int i = 0; i < pags.size(); i++) {
			Graph pagOrig = pags.get(i);
			if (!pagLnBSCD.containsKey(pagOrig)) {
				double lnInd = getLnProb(pagOrig, h);
				System.out.println("lnInd: " + lnInd);
				System.out.println(pagOrig);

				if (lnInd > maxLnInd || pagLnBSCI.size() == 0) {
					maxLnInd = lnInd;
					graphBNI = pagOrig;
				}

				// Filtering
				double lnDep = getLnProbUsingDepFiltering(pagOrig, h, imHat, estDepBN);
				System.out.println("lnDep: " + lnDep);

				if (lnDep > maxLnDep || pagLnBSCD.size() == 0) {
					maxLnDep = lnDep;
					graphBND = pagOrig;
				}

				pagLnBSCD.put(pagOrig, lnDep);
				pagLnBSCI.put(pagOrig, lnInd);
			}
		}

		System.out.println("maxLnDep: " + maxLnDep + " maxLnInd: " + maxLnInd);

		double lnQBSCDTotal = lnQTotal(pagLnBSCD);
		double lnQBSCITotal = lnQTotal(pagLnBSCI);

		// normalize the scores
		normalizedlnQBSCD = maxLnDep - lnQBSCDTotal;
		normalizedlnQBSCD = Math.exp(normalizedlnQBSCD);

		normalizedlnQBSCI = maxLnInd - lnQBSCITotal;
		normalizedlnQBSCI = Math.exp(normalizedlnQBSCI);

		System.out.println("normalizedlnQBSCD: " + normalizedlnQBSCD + " normalizedlnQBSCI: " + normalizedlnQBSCI);

		System.out.println("graphBND:\n" + graphBND);
		System.out.println("graphBNI:\n" + graphBNI);
		
		if (!outputBND) {
			return graphBNI;
		}

		return graphBND;// graphBNI
	}

	protected static double lnXplusY(double lnX, double lnY) {
		double lnYminusLnX, temp;

		if (lnY > lnX) {
			temp = lnX;
			lnX = lnY;
			lnY = temp;
		}

		lnYminusLnX = lnY - lnX;

		if (lnYminusLnX < MININUM_EXPONENT) {
			return lnX;
		} else {
			double w = Math.log1p(exp(lnYminusLnX));
			return w + lnX;
		}
	}

	private static double lnQTotal(Map<Graph, Double> pagLnProb) {
		Set<Graph> pags = pagLnProb.keySet();
		Iterator<Graph> iter = pags.iterator();
		double lnQTotal = pagLnProb.get(iter.next());

		while (iter.hasNext()) {
			Graph pag = iter.next();
			double lnQ = pagLnProb.get(pag);
			lnQTotal = lnXplusY(lnQTotal, lnQ);
		}

		return lnQTotal;
	}

	private static double getLnProbUsingDepFiltering(Graph pag, Map<IndependenceFact, Double> H, BayesIm im, Graph dep) {
		double lnQ = 0;

		for (IndependenceFact fact : H.keySet()) {
			BCInference.OP op;
			double p = 0.0;

			if (pag.isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
				op = BCInference.OP.independent;
			} else {
				op = BCInference.OP.dependent;
			}

			if (im.getNode(fact.toString()) != null) {
				Node node = im.getNode(fact.toString());

				int[] parents = im.getParents(im.getNodeIndex(node));

				if (parents.length > 0) {

					int[] parentValues = new int[parents.length];

					for (int parentIndex = 0; parentIndex < parentValues.length; parentIndex++) {
						String parentName = im.getNode(parents[parentIndex]).getName();
						String[] splitParent = parentName.split(Pattern.quote("_||_"));
						Node X = pag.getNode(splitParent[0].trim());

						String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
						Node Y = pag.getNode(splitParent2[0].trim());

						List<Node> Z = new ArrayList<>();
						if (splitParent2.length > 1) {
							String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
							for (String s : splitParent3) {
								Z.add(pag.getNode(s.trim()));
							}
						}
						IndependenceFact parentFact = new IndependenceFact(X, Y, Z);
						if (pag.isDSeparatedFrom(parentFact.getX(), parentFact.getY(), parentFact.getZ())) {
							parentValues[parentIndex] = 1;
						} else {
							parentValues[parentIndex] = 0;
						}
					}

					int rowIndex = im.getRowIndex(im.getNodeIndex(node), parentValues);
					p = im.getProbability(im.getNodeIndex(node), rowIndex, 1);

					if (op == BCInference.OP.dependent) {
						p = 1.0 - p;
					}
				} else {
					p = im.getProbability(im.getNodeIndex(node), 0, 1);
					if (op == BCInference.OP.dependent) {
						p = 1.0 - p;
					}
				}

				if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
					throw new IllegalArgumentException("p illegally equals " + p);
				}

				double v = lnQ + log(p);

				if (Double.isNaN(v) || Double.isInfinite(v)) {
					continue;
				}

				lnQ = v;
			} else {
				p = H.get(fact);

				if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
					throw new IllegalArgumentException("p illegally equals " + p);
				}

				if (op == BCInference.OP.dependent) {
					p = 1.0 - p;
				}

				double v = lnQ + log(p);

				if (Double.isNaN(v) || Double.isInfinite(v)) {
					continue;
				}

				lnQ = v;
			}
		}

		return lnQ;
	}

	private static double getLnProb(Graph pag, Map<IndependenceFact, Double> H) {
		double lnQ = 0;
		for (IndependenceFact fact : H.keySet()) {
			BCInference.OP op;

			if (pag.isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
				op = BCInference.OP.independent;
			} else {
				op = BCInference.OP.dependent;
			}

			double p = H.get(fact);

			if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
				throw new IllegalArgumentException("p illegally equals " + p);
			}

			if (op == BCInference.OP.dependent) {
				p = 1.0 - p;
			}

			double v = lnQ + log(p);

			if (Double.isNaN(v) || Double.isInfinite(v)) {
				continue;
			}

			lnQ = v;
		}
		return lnQ;
	}

	@Override
	public long getElapsedTime() {
		return 0;
	}

	public void setNumRandomizedSearchModels(int numRandomizedSearchModels) {
		this.numRandomizedSearchModels = numRandomizedSearchModels;
	}

	public void setNumBscBootstrapSamples(int numBscBootstrapSamples) {
		this.numBscBootstrapSamples = numBscBootstrapSamples;
	}

	public void setLowerBound(double lowerBound) {
		this.lowerBound = lowerBound;
	}

	public void setUpperBound(double upperBound) {
		this.upperBound = upperBound;
	}

	public void setOutputBND(boolean outputBND) {
		this.outputBND = outputBND;
	}

	public Graph getGraphBND() {
		return graphBND;
	}

	public Graph getGraphBNI() {
		return graphBNI;
	}

	public double getNormalizedlnQBSCD() {
		return normalizedlnQBSCD;
	}

	public double getNormalizedlnQBSCI() {
		return normalizedlnQBSCI;
	}

	public static void main(String[] args) throws IOException {
		String modelName = "Alarm";
		int numModels = 5;
		int numBootstrapSamples = 10;
		int numCases = 1000;
		boolean threshold1 = false;
		boolean threshold2 = true;
		double lower = 0.3;
		double upper = 0.7;

		Long seed = 878376L;
		RandomUtil.getInstance().setSeed(seed);

		// get the Bayesian network (graph and parameters) of the given model
		BayesIm im = getBayesIM(modelName);
		BayesPm pm = im.getBayesPm();
		Graph dag = pm.getDag();
		
		// set the "numLatentConfounders" percentage of variables to be latent
		int LV = 0;
		GraphUtils.fixLatents4(LV, dag);
		System.out.println("Variables set to be latent:" + getLatents(dag));

		// simulate data from instantiated model
		DataSet fullData = im.simulateData(numCases, seed, true);
		fullData = refineData(fullData);
		DataSet data = DataUtils.restrictToMeasured(fullData);

		// get the true underlying PAG
		final DagToPag dagToPag = new DagToPag(dag);
		dagToPag.setCompleteRuleSetUsed(false);
		Graph PAG_True = dagToPag.convert();
		PAG_True = GraphUtils.replaceNodes(PAG_True, data.getVariables());

		// run RFCI-BSC (RB) search using BSC test and obtain constraints that
		// are queried during the search
		List<Graph> bscPags = new ArrayList<Graph>();
		IndTestProbabilistic testBSC = runRB(data, bscPags, numModels, threshold1);
		Map<IndependenceFact, Double> H = testBSC.getH();
		System.out.println("RB (RFCI-BSC) done!");
		
		// create empirical data for constraints
		DataSet depData = createDepDataFiltering(H, data, numBootstrapSamples, threshold2, lower, upper);
		System.out.println("Dep data creation done!");

		// learn structure of constraints using empirical data
		Graph depPattern = runFGS(depData);
		Graph estDepBN = SearchGraphUtils.dagFromPattern(depPattern);
		System.out.println("estDepBN: " + estDepBN.getEdges());
		System.out.println("Dependency graph done!");

		// estimate parameters of the graph learned for constraints
		/*BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
		DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
		BayesIm imHat = DirichletEstimator.estimate(prior, depData);
		System.out.println("Dependency BN_Param done");*/

		// compute scores of graphs that are output by RB search using BSC-I and
		// BSC-D methods
		//allScores lnProbs = getLnProbsAll(bscPags, H, data, imHat, estDepBN);
		Map<Graph, Double> pagLnBSCD = new HashMap<>();
		Map<Graph, Double> pagLnBSCI = new HashMap<>();

		for (int i = 0; i < bscPags.size(); i++) {
			Graph pagOrig = bscPags.get(i);
			if (!pagLnBSCD.containsKey(pagOrig)) {
				double lnInd = getLnProb(pagOrig, H);

				// Filtering
				double lnDep = getLnProbUsingDepFiltering(pagOrig, H, im, estDepBN);
				pagLnBSCD.put(pagOrig, lnDep);
				pagLnBSCI.put(pagOrig, lnInd);
			}
		}

		System.out.println("pags size: " + bscPags.size());
		System.out.println("unique pags size: " + pagLnBSCD.size());

		// normalize the scores
		Map<Graph, Double> normalizedDep = normalProbs(pagLnBSCD);

		Map<Graph, Double> normalizedInd = normalProbs(pagLnBSCI);

		// get the most probable PAG using each scoring method
		normalizedDep = MapUtil.sortByValue(normalizedDep);
		Graph maxBND = normalizedDep.keySet().iterator().next();

		normalizedInd = MapUtil.sortByValue(normalizedInd);
		Graph maxBNI = normalizedInd.keySet().iterator().next();
		
		System.out.println("P(maxBNI): \n" + normalizedInd.get(maxBNI));
		System.out.println("P(maxBND): \n" + normalizedDep.get(maxBND));
		System.out.println("------------------------------------------");
		System.out.println("PAG_True: \n" + PAG_True);
		System.out.println("------------------------------------------");
		System.out.println("RB-I: \n" + maxBNI);
		System.out.println("------------------------------------------");
		System.out.println("RB-D: \n" + maxBND);		
	}

	private static Map<Graph, Double> normalProbs(Map<Graph, Double> pagLnProbs) {
		double lnQTotal = lnQTotal(pagLnProbs);
		Map<Graph, Double> normalized = new HashMap<Graph, Double>();
		for (Graph pag : pagLnProbs.keySet()) {
			double lnQ = pagLnProbs.get(pag);
			double normalizedlnQ = lnQ - lnQTotal;
			normalized.put(pag, Math.exp(normalizedlnQ));
		}
		return normalized;
	}

	private static class MapUtil {
		public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
			List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
				public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
					return (o2.getValue()).compareTo(o1.getValue());
				}
			});

			Map<K, V> result = new LinkedHashMap<>();
			for (Map.Entry<K, V> entry : list) {
				result.put(entry.getKey(), entry.getValue());
			}
			return result;
		}
	}

	private static Graph runFGS(DataSet data) {
		BDeuScore sd = new BDeuScore(data);
		sd.setSamplePrior(1.0);
		sd.setStructurePrior(1.0);
		Fges fgs = new Fges(sd);
		fgs.setVerbose(false);
		fgs.setNumPatternsToStore(0);
		fgs.setFaithfulnessAssumed(true);
		Graph fgsPattern = fgs.search();
		fgsPattern = GraphUtils.replaceNodes(fgsPattern, data.getVariables());
		return fgsPattern;
	}

	private static DataSet createDepDataFiltering(Map<IndependenceFact, Double> H, DataSet data, int numBootstrapSamples,
			boolean threshold, double lower, double upper) {
		List<Node> vars = new ArrayList<>();
		Map<IndependenceFact, Double> HCopy = new HashMap<>();
		for (IndependenceFact f : H.keySet()) {
			if (H.get(f) > lower && H.get(f) < upper) {
				HCopy.put(f, H.get(f));
				DiscreteVariable var = new DiscreteVariable(f.toString());
				vars.add(var);
			}
		}

		DataSet depData = new ColtDataSet(numBootstrapSamples, vars);
		System.out.println("\nDep data rows: " + depData.getNumRows() + ", columns: " + depData.getNumColumns());
		System.out.println("HCopy size: " + HCopy.size());

		for (int b = 0; b < numBootstrapSamples; b++) {
			DataSet bsData = DataUtils.getBootstrapSample(data, data.getNumRows());
			IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
			bsTest.setThreshold(threshold);
			for (IndependenceFact f : HCopy.keySet()) {
				boolean ind = bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
				int value = ind ? 1 : 0;
				depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
			}
		}
		return depData;
	}

	private static IndTestProbabilistic runRB(DataSet data, List<Graph> pags, int numModels, boolean threshold) {
		IndTestProbabilistic BSCtest = new IndTestProbabilistic(data);

		BSCtest.setThreshold(threshold);
		Rfci BSCrfci = new Rfci(BSCtest);

		BSCrfci.setVerbose(false);
		BSCrfci.setCompleteRuleSetUsed(false);
		BSCrfci.setDepth(5);

		for (int i = 0; i < numModels; i++) {
			Graph BSCPag = BSCrfci.search();
			BSCPag = GraphUtils.replaceNodes(BSCPag, data.getVariables());
			pags.add(BSCPag);

		}
		return BSCtest;
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

	private static BayesIm getBayesIM(String type) {
		if ("Alarm".equals(type)) {
			return loadBayesIm("Alarm.xdsl", true);
		} else if ("Hailfinder".equals(type)) {
			return loadBayesIm("Hailfinder.xdsl", false);
		} else if ("Hepar".equals(type)) {
			return loadBayesIm("Hepar2.xdsl", true);
		} else if ("Win95".equals(type)) {
			return loadBayesIm("win95pts.xdsl", false);
		} else if ("Barley".equals(type)) {
			return loadBayesIm("barley.xdsl", false);
		}

		throw new IllegalArgumentException("Not a recogized Bayes IM type.");
	}

	private static BayesIm loadBayesIm(String filename, boolean useDisplayNames) {
		String dataPath = System.getProperty("user.dir");
		try {
			Builder builder = new Builder();
			File dir = new File(dataPath + "/xdsl");
			File file = new File(dir, filename);
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
