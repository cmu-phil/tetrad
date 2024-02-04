package edu.pitt.dbmi.algo.bayesian.constraint.search;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.test.IndTestProbabilistic;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;
import org.apache.commons.math3.util.FastMath;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.apache.commons.math3.util.FastMath.exp;
import static org.apache.commons.math3.util.FastMath.log;

/**
 * Dec 17, 2018 3:28:15 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class RfciBsc implements IGraphSearch {

    private static final int MININUM_EXPONENT = -1022;
    private final Rfci rfci;
    private final List<Graph> pAGs = Collections.synchronizedList(new ArrayList<>());
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    private Graph graphRBD, graphRBI;
    private double bscD, bscI;
    private int numRandomizedSearchModels = 10;
    private int numBscBootstrapSamples = 100;
    private double lowerBound = 0.3;
    private double upperBound = 0.7;
    private boolean outputRBD = true;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    // Where printed output is sent.
    private PrintStream out = System.out;

    private boolean thresholdNoRandomDataSearch;
    private double cutoffDataSearch = 0.5;

    private boolean thresholdNoRandomConstrainSearch = true;
    private double cutoffConstrainSearch = 0.5;

    public RfciBsc(Rfci rfci) {
        this.rfci = rfci;
    }

    private static double lnXplusY(double lnX, double lnY) {
        double lnYminusLnX;
        double temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < RfciBsc.MININUM_EXPONENT) {
            return lnX;
        } else {
            double w = FastMath.log1p(exp(lnYminusLnX));
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
            lnQTotal = RfciBsc.lnXplusY(lnQTotal, lnQ);
        }

        return lnQTotal;
    }

    private static double getLnProbUsingDepFiltering(Graph pag, Map<IndependenceFact, Double> H, BayesIm im, Graph dep) {
        double lnQ = 0;

        for (IndependenceFact fact : H.keySet()) {
            BCInference.OP op;
            double p = 0.0;

            if (pag.paths().isMSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
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
                        Node _X = pag.getNode(splitParent[0].trim());

                        String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
                        Node _Y = pag.getNode(splitParent2[0].trim());

                        Set<Node> _Z = new HashSet<>();
                        if (splitParent2.length > 1) {
                            String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
                            for (String s : splitParent3) {
                                _Z.add(pag.getNode(s.trim()));
                            }
                        }
                        IndependenceFact parentFact = new IndependenceFact(_X, _Y, _Z);
                        if (pag.paths().isMSeparatedFrom(parentFact.getX(), parentFact.getY(), parentFact.getZ())) {
                            parentValues[parentIndex] = 1;
                        } else {
                            parentValues[parentIndex] = 0;
                        }
                    }

                    int rowIndex = im.getRowIndex(im.getNodeIndex(node), parentValues);
                    p = im.getProbability(im.getNodeIndex(node), rowIndex, 1);

                } else {
                    p = im.getProbability(im.getNodeIndex(node), 0, 1);
                }
                if (op == BCInference.OP.dependent) {
                    p = 1.0 - p;
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

            if (pag.paths().isMSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
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

    /**
     * Performs the search.
     *
     * @return the graph that was learned.
     */
    @Override
    public Graph search() {
        long stop = 0;
        long start = MillisecondTimes.timeMillis();

        IndTestProbabilistic _test = (IndTestProbabilistic) this.rfci.getIndependenceTest();

        // create empirical data for constraints
        DataSet dataSet = SimpleDataLoader.getDiscreteDataSet(_test.getData());

        this.pAGs.clear();

        // A map from independence facts to their probabilities of independence.
        List<Node> vars = Collections.synchronizedList(new ArrayList<>());
        List<String> var_lookup = Collections.synchronizedList(new ArrayList<>());

        Map<IndependenceFact, Double> h = new ConcurrentHashMap<>();
        Map<IndependenceFact, Double> hCopy = new ConcurrentHashMap<>();

        // run RFCI-BSC (RB) search using BSC test and obtain constraints that
        // are queried during the search
        class SearchPagTask implements Callable<Boolean> {

            private final IndTestProbabilistic test;
            private final Rfci rfci;

            /**
             * Constructor.
             */
            public SearchPagTask() {
                this.test = new IndTestProbabilistic(dataSet);
                this.test.setThreshold(RfciBsc.this.thresholdNoRandomDataSearch);
                if (RfciBsc.this.thresholdNoRandomDataSearch) {
                    this.test.setCutoff(RfciBsc.this.cutoffDataSearch);
                }

                this.rfci = new Rfci(this.test);
            }

            /**
             * Performs the search.
             * @return true if the search was successful.
             * @throws Exception if an error occurred.
             */
            @Override
            public Boolean call() throws Exception {

                Graph pag = this.rfci.search();
                pag = GraphUtils.replaceNodes(pag, this.test.getVariables());
                RfciBsc.this.pAGs.add(pag);

                Map<IndependenceFact, Double> _h = this.test.getH();

                for (IndependenceFact f : _h.keySet()) {
                    String indFact = f.toString();


                    if (!hCopy.containsKey(f)) {

                        h.put(f, _h.get(f));

                        if (_h.get(f) > RfciBsc.this.lowerBound && _h.get(f) < RfciBsc.this.upperBound) {
                            hCopy.put(f, _h.get(f));
                            DiscreteVariable var = new DiscreteVariable(indFact);

                            if (!vars.contains(var)) {
                                vars.add(var);

                                if (!var_lookup.contains(indFact)) {
                                    var_lookup.add(indFact);
                                }

                            }

                        }

                    }

                }

                return true;
            }

        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int trial = 0;

        int numCandidatePagSearchTrial = 1000;
        while (vars.size() == 0 && trial < numCandidatePagSearchTrial) {
            tasks.clear();

            for (int i = 0; i < this.numRandomizedSearchModels; i++) {
                tasks.add(new SearchPagTask());
            }

            ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

            try {
                pool.invokeAll(tasks);
            } catch (InterruptedException exception) {
                if (this.verbose) {
                    this.logger.log("error", "Task has been interrupted");
                }
                Thread.currentThread().interrupt();
            }

            shutdownAndAwaitTermination(pool);
            trial++;
        }

        // Failed to generate a list of qualified constraints
        if (trial == numCandidatePagSearchTrial) {
            return new EdgeListGraph(dataSet.getVariables());
        }

        DataBox dataBox = new VerticalIntDataBox(this.numBscBootstrapSamples, vars.size());
        DataSet depData = new BoxDataSet(dataBox, vars);

        class BootstrapDepDataTask implements Callable<Boolean> {

            private final int row_index;

            private final IndTestProbabilistic bsTest;

            /**
             * Constructor.
             */
            public BootstrapDepDataTask(int row_index, int rows) {
                this.row_index = row_index;

                DataSet bsData = DataTransforms.getBootstrapSample(dataSet, rows);
                this.bsTest = new IndTestProbabilistic(bsData);
                this.bsTest.setThreshold(RfciBsc.this.thresholdNoRandomConstrainSearch);
                if (RfciBsc.this.thresholdNoRandomConstrainSearch) {
                    this.bsTest.setCutoff(RfciBsc.this.cutoffConstrainSearch);
                }
            }

            /**
             * Performs the search.
             *
             * @return true if the search was successful.
             * @throws Exception  if an error occurred.
             */
            @Override
            public Boolean call() throws Exception {
                for (IndependenceFact f : hCopy.keySet()) {
                    boolean ind = this.bsTest.checkIndependence(f.getX(), f.getY(), f.getZ()).isIndependent();
                    int value = ind ? 1 : 0;

                    String indFact = f.toString();

                    int col = var_lookup.indexOf(indFact);
                    synchronized (depData) {
                        depData.setInt(this.row_index, col, value);
                    }

                }
                return true;
            }

        }

        tasks.clear();

        int rows = dataSet.getNumRows();
        for (int b = 0; b < this.numBscBootstrapSamples; b++) {
            tasks.add(new BootstrapDepDataTask(b, rows));
        }

        ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
            if (this.verbose) {
                this.logger.log("error", "Task has been interrupted");
            }
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        // learn structure of constraints using empirical data => constraint data
        BdeuScore sd = new BdeuScore(depData);
        sd.setSamplePrior(1.0);
        sd.setStructurePrior(1.0);

        Fges fges = new Fges(sd);
        fges.setVerbose(false);
        fges.setFaithfulnessAssumed(true);

        Graph depPattern = fges.search();
        depPattern = GraphUtils.replaceNodes(depPattern, depData.getVariables());
        Graph estDepBN = GraphTransforms.dagFromCpdag(depPattern, null);

        if (this.verbose) {
            this.out.println("estDepBN:");
            this.out.println(estDepBN);
        }

        // estimate parameters of the graph learned for constraints
        BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
        DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
        BayesIm imHat = DirichletEstimator.estimate(prior, depData);

        // compute scores of graphs that are output by RB search using
        // BSC-I and BSC-D methods
        Map<Graph, Double> pagLnBSCD = new ConcurrentHashMap<>();
        Map<Graph, Double> pagLnBSCI = new ConcurrentHashMap<>();

        double maxLnDep = -1, maxLnInd = -1;

        class CalculateBscScoreTask implements Callable<Boolean> {

            final Graph pagOrig;

            public CalculateBscScoreTask(Graph pagOrig) {
                this.pagOrig = pagOrig;
            }

            @Override
            public Boolean call() throws Exception {
                if (!pagLnBSCD.containsKey(this.pagOrig)) {
                    double lnInd = RfciBsc.getLnProb(this.pagOrig, h);

                    // Filtering
                    double lnDep = RfciBsc.getLnProbUsingDepFiltering(this.pagOrig, h, imHat, estDepBN);

                    pagLnBSCD.put(this.pagOrig, lnDep);
                    pagLnBSCI.put(this.pagOrig, lnInd);
                }
                return true;
            }

        }

        tasks.clear();

        for (Graph pagOrig : this.pAGs) {
            tasks.add(new CalculateBscScoreTask(pagOrig));
        }

        pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
            if (this.verbose) {
                this.logger.log("error", "Task has been interrupted");
            }
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        for (int i = 0; i < this.pAGs.size(); i++) {
            Graph pagOrig = this.pAGs.get(i);

            double lnDep = pagLnBSCD.get(pagOrig);
            double lnInd = pagLnBSCI.get(pagOrig);

            if (lnInd > maxLnInd || i == 0) {
                maxLnInd = lnInd;
                this.graphRBI = pagOrig;
            }

            if (lnDep > maxLnDep || i == 0) {
                maxLnDep = lnDep;
                this.graphRBD = pagOrig;
            }

        }

        if (this.verbose) {
            this.out.println("maxLnDep: " + maxLnDep + " maxLnInd: " + maxLnInd);
        }

        double lnQBSCDTotal = RfciBsc.lnQTotal(pagLnBSCD);
        double lnQBSCITotal = RfciBsc.lnQTotal(pagLnBSCI);

        // normalize the scores
        this.bscD = maxLnDep - lnQBSCDTotal;
        this.bscD = exp(this.bscD);
        this.graphRBD.addAttribute("bscD", this.bscD);

        double _bscI = pagLnBSCI.get(this.graphRBD) - lnQBSCITotal;
        _bscI = exp(_bscI);
        this.graphRBD.addAttribute("bscI", _bscI);


        double _bscD = pagLnBSCD.get(this.graphRBI) - lnQBSCDTotal;
        _bscD = exp(_bscD);
        this.graphRBI.addAttribute("bscD", _bscD);

        this.bscI = maxLnInd - lnQBSCITotal;
        this.bscI = exp(this.bscI);
        this.graphRBI.addAttribute("bscI", this.bscI);

        if (this.verbose) {

            this.out.println("bscD: " + this.bscD + " bscI: " + this.bscI);

            this.out.println("graphRBD:\n" + this.graphRBD);
            this.out.println("graphRBI:\n" + this.graphRBI);

            stop = MillisecondTimes.timeMillis();

            this.out.println("Elapsed " + (stop - start) + " ms");
        }

        Graph output = this.graphRBD;

        if (!this.outputRBD) {
            output = this.graphRBI;
        }

        return generateBootstrappingAttributes(output);


    }

    private Graph generateBootstrappingAttributes(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node nodeA = edge.getNode1();
            Node nodeB = edge.getNode2();

            List<EdgeTypeProbability> edgeTypeProbabilities = getProbability(nodeA, nodeB);

            for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                edge.addEdgeTypeProbability(etp);
            }
        }

        return graph;
    }

    private List<EdgeTypeProbability> getProbability(Node node1, Node node2) {
        Map<String, Integer> edgeDist = new HashMap<>();
        int no_edge_num = 0;
        for (Graph g : this.pAGs) {
            Edge e = g.getEdge(node1, node2);
            if (e != null) {
                String edgeString = e.toString();
                if (e.getEndpoint1() == e.getEndpoint2() && node1.compareTo(e.getNode1()) != 0) {
                    Edge edge = new Edge(node1, node2, e.getEndpoint1(), e.getEndpoint2());
                    for (Property property : e.getProperties()) {
                        edge.addProperty(property);
                    }
                    edgeString = edge.toString();
                }
                Integer num_edge = edgeDist.get(edgeString);
                if (num_edge == null) {
                    num_edge = 0;
                }
                num_edge = num_edge + 1;
                edgeDist.put(edgeString, num_edge);
            } else {
                no_edge_num++;
            }
        }
        int n = this.pAGs.size();
        // Normalization
        List<EdgeTypeProbability> edgeTypeProbabilities = edgeDist.size() == 0 ? null : new ArrayList<>();
        for (String edgeString : edgeDist.keySet()) {
            int edge_num = edgeDist.get(edgeString);
            double probability = (double) edge_num / n;

            String[] token = edgeString.split("\\s+");
            String n1 = token[0];
            String arc = token[1];
            String n2 = token[2];

            char end1 = arc.charAt(0);
            char end2 = arc.charAt(2);

            Endpoint _end1, _end2;

            if (end1 == '<') {
                _end1 = Endpoint.ARROW;
            } else if (end1 == 'o') {
                _end1 = Endpoint.CIRCLE;
            } else if (end1 == '-') {
                _end1 = Endpoint.TAIL;
            } else {
                throw new IllegalArgumentException();
            }

            if (end2 == '>') {
                _end2 = Endpoint.ARROW;
            } else if (end2 == 'o') {
                _end2 = Endpoint.CIRCLE;
            } else if (end2 == '-') {
                _end2 = Endpoint.TAIL;
            } else {
                throw new IllegalArgumentException();
            }

            if (node1.getName().equalsIgnoreCase(n2) && node2.getName().equalsIgnoreCase(n1)) {
                Endpoint tmp = _end1;
                _end1 = _end2;
                _end2 = tmp;
            }

            EdgeType edgeType = EdgeType.nil;

            if (_end1 == Endpoint.TAIL && _end2 == Endpoint.ARROW) {
                edgeType = EdgeType.ta;
            }
            if (_end1 == Endpoint.ARROW && _end2 == Endpoint.TAIL) {
                edgeType = EdgeType.at;
            }
            if (_end1 == Endpoint.CIRCLE && _end2 == Endpoint.ARROW) {
                edgeType = EdgeType.ca;
            }
            if (_end1 == Endpoint.ARROW && _end2 == Endpoint.CIRCLE) {
                edgeType = EdgeType.ac;
            }
            if (_end1 == Endpoint.CIRCLE && _end2 == Endpoint.CIRCLE) {
                edgeType = EdgeType.cc;
            }
            if (_end1 == Endpoint.ARROW && _end2 == Endpoint.ARROW) {
                edgeType = EdgeType.aa;
            }
            if (_end1 == Endpoint.TAIL && _end2 == Endpoint.TAIL) {
                edgeType = EdgeType.tt;
            }

            EdgeTypeProbability etp = new EdgeTypeProbability(edgeType, probability);

            // Edge's properties
            if (token.length > 3) {
                for (int i = 3; i < token.length; i++) {
                    etp.addProperty(Property.valueOf(token[i]));
                }
            }
            edgeTypeProbabilities.add(etp);
        }

        if (no_edge_num < n && edgeTypeProbabilities != null) {
            edgeTypeProbabilities.add(new EdgeTypeProbability(EdgeType.nil, (double) no_edge_num / n));
        }

        return edgeTypeProbabilities;
    }

    /**
     * Sets the number of randomized search models.
     * @param numRandomizedSearchModels the number of randomized search models.
     */
    public void setNumRandomizedSearchModels(int numRandomizedSearchModels) {
        this.numRandomizedSearchModels = numRandomizedSearchModels;
    }

    /**
     * Sets the number of bootstrap samples.
     * @param numBscBootstrapSamples the number of bootstrap samples.
     */
    public void setNumBscBootstrapSamples(int numBscBootstrapSamples) {
        this.numBscBootstrapSamples = numBscBootstrapSamples;
    }

    /**
     * Sets the lower bound.
     * @param lowerBound the lower bound.
     */
    public void setLowerBound(double lowerBound) {
        this.lowerBound = lowerBound;
    }

    /**
     * Sets the upper bound.
     * @param upperBound the upper bound.
     */
    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    /**
     * Sets whether the output should be RBD.
     * @param outputRBD true if the output should be RBD.
     */
    public void setOutputRBD(boolean outputRBD) {
        this.outputRBD = outputRBD;
    }

    /**
     * Returns the graph that was learned using the BSC-D method.
     * @return
     */
    public Graph getGraphRBD() {
        return this.graphRBD;
    }

    /**
     * Returns the graph that was learned using the BSC-I method.
     * @return the graph that was learned using the BSC-I method.
     */
    public Graph getGraphRBI() {
        return this.graphRBI;
    }

    /**
     * Returns the BSC-D score.
     * @return  the BSC-D score.
     */
    public double getBscD() {
        return this.bscD;
    }

    /**
     * Returns the BSC-I score.
     * @return the BSC-I score.
     */
    public double getBscI() {
        return this.bscI;
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
     * @param  verbose true if verbose output should be produced.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return the output stream that output (except for log output) should be sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By detault System.out.
     * @param out the output stream that output (except for log output) should be sent to.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @param thresholdNoRandomDataSearch the thresholdNoRandomDataSearch to set
     */
    public void setThresholdNoRandomDataSearch(boolean thresholdNoRandomDataSearch) {
        this.thresholdNoRandomDataSearch = thresholdNoRandomDataSearch;
    }

    /**
     * @param cutoffDataSearch the cutoffDataSearch to set
     */
    public void setCutoffDataSearch(double cutoffDataSearch) {
        this.cutoffDataSearch = cutoffDataSearch;
    }

    /**
     * @param thresholdNoRandomConstrainSearch the thresholdNoRandomConstrainSearch to set
     */
    public void setThresholdNoRandomConstrainSearch(boolean thresholdNoRandomConstrainSearch) {
        this.thresholdNoRandomConstrainSearch = thresholdNoRandomConstrainSearch;
    }

    /**
     * @param cutoffConstrainSearch the cutoffConstrainSearch to set
     */
    public void setCutoffConstrainSearch(double cutoffConstrainSearch) {
        this.cutoffConstrainSearch = cutoffConstrainSearch;
    }

}
