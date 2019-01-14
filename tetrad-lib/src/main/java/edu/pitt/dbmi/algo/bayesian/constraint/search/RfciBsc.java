/**
 * 
 */
package edu.pitt.dbmi.algo.bayesian.constraint.search;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

/**
 * Dec 17, 2018 3:28:15 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RfciBsc implements GraphSearch {

	private final Rfci rfci;

	private Graph graphRBD = null, graphRBI = null;

	private double bscD = 0.0, bscI = 0.0;

	private List<Graph> pags = new ArrayList<>();

	private int numRandomizedSearchModels = 5;

	private int numBscBootstrapSamples = 10;

	private double lowerBound = 0.3;

	private double upperBound = 0.7;

	private static final int MININUM_EXPONENT = -1022;

	private boolean outputRBD = true;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    // Where printed output is sent.
    private PrintStream out = System.out;

    private long start = 0;
    
    private long stop = 0;
    
    public RfciBsc(Rfci rfci) {
		this.rfci = rfci;
	}

	@Override
	public Graph search() {
		stop = 0;
		start = System.currentTimeMillis();
		
		IndTestProbabilistic test = (IndTestProbabilistic) rfci.getIndependenceTest();
		test.setThreshold(false);

		// create empirical data for constraints
		DataSet dataSet = DataUtils.getDiscreteDataSet(test.getData());
		
		pags.clear();

		// run RFCI-BSC (RB) search using BSC test and obtain constraints that
		// are queried during the search
		class SearchPagTask implements Callable<Boolean> {

			@Override
			public Boolean call() throws Exception {
				IndTestProbabilistic test = new IndTestProbabilistic(dataSet);
				test.setThreshold(false);
				Rfci rfci = new Rfci(test);
				Graph pag = rfci.search();
				pag = GraphUtils.replaceNodes(pag, test.getVariables());
				pags.add(pag);
				return true;
			}
			
		}
		
		List<Callable<Boolean>> tasks = new ArrayList<>();
		
		for (int i = 0; i < numRandomizedSearchModels; i++) {
			tasks.add(new SearchPagTask());
		}

        ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
        	if(verbose) {
                logger.log("error","Task has been interrupted");
        	}
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        // A map from independence facts to their probabilities of independence.
		Map<IndependenceFact, Double> h = test.getH();

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

		class BootstrapDepDataTask implements Callable<Boolean> {
			
			private final int b;
			private final int rows;
			
			public BootstrapDepDataTask(int b, int rows) {
				this.b = b;
				this.rows = rows;
			}
			
			@Override
			public Boolean call() throws Exception {
				DataSet bsData = DataUtils.getBootstrapSample(dataSet, rows);
				IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
				bsTest.setThreshold(true);
				for (IndependenceFact f : hCopy.keySet()) {
					boolean ind = bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
					int value = ind ? 1 : 0;
					depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
				}
				return true;
			}
			
		}
		
		tasks.clear();
		
		int rows = dataSet.getNumRows();
		for (int b = 0; b < numBscBootstrapSamples; b++) {
			tasks.add(new BootstrapDepDataTask(b,rows));
		}

		pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
        	if(verbose) {
                logger.log("error","Task has been interrupted");
        	}
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);
		
		// learn structure of constraints using empirical data => constraint data
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
				graphRBD.addAttribute("p(bscD)", String.format("%.4f", bscD));

				// Filtering
				double lnDep = getLnProbUsingDepFiltering(pagOrig, h, imHat, estDepBN);
				
				if (lnInd > maxLnInd || pagLnBSCI.size() == 0) {
					maxLnInd = lnInd;
					graphRBI = pagOrig;
				}

				if (lnDep > maxLnDep || pagLnBSCD.size() == 0) {
					maxLnDep = lnDep;
					graphRBD = pagOrig;
				}

				pagLnBSCD.put(pagOrig, lnDep);
				pagLnBSCI.put(pagOrig, lnInd);
			}
		}

		out.println("maxLnDep: " + maxLnDep + " maxLnInd: " + maxLnInd);

		double lnQBSCDTotal = lnQTotal(pagLnBSCD);
		double lnQBSCITotal = lnQTotal(pagLnBSCI);

		// normalize the scores
		bscD = maxLnDep - lnQBSCDTotal;
		bscD = Math.exp(bscD);
		graphRBD.addAttribute("p(bscD)", String.format("%.4f", bscD));

		bscI = maxLnInd - lnQBSCITotal;
		bscI = Math.exp(bscI);
		graphRBI.addAttribute("p(bscI)", String.format("%.4f", bscI));

		out.println("bscD: " + bscD + " bscI: " + bscI);

		out.println("graphRBD:\n" + graphRBD);
		out.println("graphRBI:\n" + graphRBI);
		
		stop = System.currentTimeMillis();
		
		if (!outputRBD) {
			return graphRBI;
		}

		return graphRBD;// graphRBI
	}

	private static double lnXplusY(double lnX, double lnY) {
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
		return (stop - start);
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

	public void setOutputRBD(boolean outputRBD) {
		this.outputRBD = outputRBD;
	}

	public Graph getGraphRBD() {
		return graphRBD;
	}

	public Graph getGraphRBI() {
		return graphRBI;
	}

	public double getBscD() {
		return bscD;
	}

	public double getBscI() {
		return bscI;
	}

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sets whether verbose output should be produced.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return out;
    }
    
}
