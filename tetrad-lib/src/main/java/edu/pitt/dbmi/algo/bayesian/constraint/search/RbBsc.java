/**
 * 
 */
package edu.pitt.dbmi.algo.bayesian.constraint.search;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

/**
 * Dec 17, 2018 3:28:15 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RbBsc implements GraphSearch {

    private final Rfci rfci;

    private List<Graph> pags = new ArrayList<>();
    
    private int numModels = 5;
    
    private int numBscBootstrapSamples = 10;
    
    private boolean randomizedGeneratingConstraints = true;
    
    private double lowerBound = 0.3;
    
    private double upperBound = 0.7;
    
	private static final int MININUM_EXPONENT = -1022;

    public RbBsc(Rfci rfci) {
    	this.rfci = rfci;
    }
    
	@Override
	public Graph search() {
		
		IndTestProbabilistic test = (IndTestProbabilistic)rfci.getIndependenceTest();
		
		pags.clear();
		
		// run RFCI-BSC (RB) search using BSC test and obtain constraints that
		// are queried during the search
		for (int i=0;i<numModels;i++) {
			Graph pag = rfci.search();
			pag = GraphUtils.replaceNodes(pag, test.getVariables());
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
		
		for (int b = 0; b < numBscBootstrapSamples; b++) {
			DataSet bsData = DataUtils.getBootstrapSample(data, data.getNumRows());
			IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
			test.setThreshold(randomizedGeneratingConstraints);
			for (IndependenceFact f : hCopy.keySet()) {
				boolean ind = bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
				int value = ind ? 1 : 0;
				depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
			}
		}
		
		// learn structure of constraints using empirical data => constraint meta data
		BDeuScore sd = new BDeuScore(data);
		sd.setSamplePrior(1.0);
		sd.setStructurePrior(1.0);

		Fges fgs = new Fges(sd);
		fgs.setVerbose(false);
		fgs.setNumPatternsToStore(0);
		fgs.setFaithfulnessAssumed(true);
		
		Graph depPattern = fgs.search();
		depPattern = GraphUtils.replaceNodes(depPattern, data.getVariables());
		Graph estDepBN = SearchGraphUtils.dagFromPattern(depPattern);
		
		// estimate parameters of the graph learned for constraints
		BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
		DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
		BayesIm imHat = DirichletEstimator.estimate(prior, depData);

		// compute scores of graphs that are output by RB search using 
		// BSC-I and BSC-D methods
		Map<Graph, Double> pagLnBSCD = new HashMap<>();
		Map<Graph, Double> pagLnBSCI = new HashMap<>();
		
		Graph maxBND = null,maxBNI = null;
		double maxLnDep = 0.0,maxLnInd = 0.0;
		
		for (int i = 0; i < pags.size(); i++) {
			Graph pagOrig = pags.get(i);
			if (!pagLnBSCD.containsKey(pagOrig)) {
				double lnInd = getLnProb(pagOrig, h);
				
				if(lnInd > maxLnInd) {
					maxLnInd = lnInd;
					maxBNI = pagOrig;
				}
				
				// Filtering
				double lnDep = getLnProbUsingDepFiltering(pagOrig, h, imHat, estDepBN);
				
				if(lnDep > maxLnDep) {
					maxLnDep = lnDep;
					maxBND = pagOrig;
				}
				
				pagLnBSCD.put(pagOrig, lnDep);
				pagLnBSCI.put(pagOrig, lnInd);
			}
		}

		double lnQBSCDTotal = lnQTotal(pagLnBSCD);
		double lnQBSCITotal = lnQTotal(pagLnBSCI);
		
		// normalize the scores
		double normalizedlnQBSCD = maxLnDep - lnQBSCDTotal;
		normalizedlnQBSCD = Math.exp(normalizedlnQBSCD);

		double normalizedlnQBSCI = maxLnInd - lnQBSCITotal;
		normalizedlnQBSCI = Math.exp(normalizedlnQBSCI);
		
		return maxBND;//maxBNI
	}

	protected double lnXplusY(double lnX, double lnY) {
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

	private double lnQTotal(Map<Graph, Double> pagLnProb) {
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

	private double getLnProbUsingDepFiltering(Graph pag, Map<IndependenceFact, Double> H, BayesIm im, Graph dep) {
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

	private double getLnProb(Graph pag, Map<IndependenceFact, Double> H) {
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

	public void setNumModels(int numModels) {
		this.numModels = numModels;
	}

	public void setNumBscBootstrapSamples(int numBscBootstrapSamples) {
		this.numBscBootstrapSamples = numBscBootstrapSamples;
	}

	public void setRandomizedGeneratingConstraints(boolean randomizedGeneratingConstraints) {
		this.randomizedGeneratingConstraints = randomizedGeneratingConstraints;
	}

}
