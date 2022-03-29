/**
 *
 */
package edu.pitt.dbmi.algo.bayesian.constraint.search;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Dec 17, 2018 3:28:15 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RfciBsc implements GraphSearch {

    private final Rfci rfci;

    private Graph graphRBD, graphRBI;

    private double bscD, bscI;

    private final List<Graph> pAGs = Collections.synchronizedList(new ArrayList<>());

    private int numRandomizedSearchModels = 10;

    private int numBscBootstrapSamples = 100;

    private double lowerBound = 0.3;

    private double upperBound = 0.7;

    private static final int MININUM_EXPONENT = -1022;

    private boolean outputRBD = true;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    // Where printed output is sent.
    private PrintStream out = System.out;

    private long start;

    private long stop;

    private boolean thresholdNoRandomDataSearch;
    private double cutoffDataSearch = 0.5;

    private boolean thresholdNoRandomConstrainSearch = true;
    private double cutoffConstrainSearch = 0.5;

    private final int numCandidatePagSearchTrial = 1000;

    public RfciBsc(final Rfci rfci) {
        this.rfci = rfci;
    }

    @Override
    public Graph search() {
        this.stop = 0;
        this.start = System.currentTimeMillis();

        final IndTestProbabilistic _test = (IndTestProbabilistic) this.rfci.getIndependenceTest();

        // create empirical data for constraints
        final DataSet dataSet = DataUtils.getDiscreteDataSet(_test.getData());

        this.pAGs.clear();

        // A map from independence facts to their probabilities of independence.
        final List<Node> vars = Collections.synchronizedList(new ArrayList<>());
        final List<String> var_lookup = Collections.synchronizedList(new ArrayList<>());

        final Map<IndependenceFact, Double> h = new ConcurrentHashMap<>();
        final Map<IndependenceFact, Double> hCopy = new ConcurrentHashMap<>();

        // run RFCI-BSC (RB) search using BSC test and obtain constraints that
        // are queried during the search
        class SearchPagTask implements Callable<Boolean> {

            private final IndTestProbabilistic test;
            private final Rfci rfci;

            public SearchPagTask() {
                this.test = new IndTestProbabilistic(dataSet);
                this.test.setThreshold(RfciBsc.this.thresholdNoRandomDataSearch);
                if (RfciBsc.this.thresholdNoRandomDataSearch) {
                    this.test.setCutoff(RfciBsc.this.cutoffDataSearch);
                }

                this.rfci = new Rfci(this.test);
            }

            @Override
            public Boolean call() throws Exception {

                Graph pag = this.rfci.search();
                pag = GraphUtils.replaceNodes(pag, this.test.getVariables());
                RfciBsc.this.pAGs.add(pag);

                final Map<IndependenceFact, Double> _h = this.test.getH();

                for (final IndependenceFact f : _h.keySet()) {
                    final String indFact = f.toString();


                    if (!hCopy.containsKey(f)) {

                        h.put(f, _h.get(f));

                        if (_h.get(f) > RfciBsc.this.lowerBound && _h.get(f) < RfciBsc.this.upperBound) {
                            hCopy.put(f, _h.get(f));
                            final DiscreteVariable var = new DiscreteVariable(indFact);

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

        final List<Callable<Boolean>> tasks = new ArrayList<>();

        int trial = 0;

        while (vars.size() == 0 && trial < this.numCandidatePagSearchTrial) {
            tasks.clear();

            for (int i = 0; i < this.numRandomizedSearchModels; i++) {
                tasks.add(new SearchPagTask());
            }

            final ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

            try {
                pool.invokeAll(tasks);
            } catch (final InterruptedException exception) {
                if (this.verbose) {
                    this.logger.log("error", "Task has been interrupted");
                }
                Thread.currentThread().interrupt();
            }

            shutdownAndAwaitTermination(pool);
            trial++;
        }

        // Failed to generate a list of qualified constraints
        if (trial == this.numCandidatePagSearchTrial) {
            return new EdgeListGraph(dataSet.getVariables());
        }

        final DataBox dataBox = new VerticalIntDataBox(this.numBscBootstrapSamples, vars.size());
        final DataSet depData = new BoxDataSet(dataBox, vars);

        class BootstrapDepDataTask implements Callable<Boolean> {

            private final int row_index;

            private final DataSet bsData;
            private final IndTestProbabilistic bsTest;

            public BootstrapDepDataTask(final int row_index, final int rows) {
                this.row_index = row_index;

                this.bsData = DataUtils.getBootstrapSample(dataSet, rows);
                this.bsTest = new IndTestProbabilistic(this.bsData);
                this.bsTest.setThreshold(RfciBsc.this.thresholdNoRandomConstrainSearch);
                if (RfciBsc.this.thresholdNoRandomConstrainSearch) {
                    this.bsTest.setCutoff(RfciBsc.this.cutoffConstrainSearch);
                }
            }

            @Override
            public Boolean call() throws Exception {
                for (final IndependenceFact f : hCopy.keySet()) {
                    final boolean ind = this.bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
                    final int value = ind ? 1 : 0;

                    final String indFact = f.toString();

                    final int col = var_lookup.indexOf(indFact);
                    synchronized (depData) {
                        depData.setInt(this.row_index, col, value);
                    }

                }
                return true;
            }

        }

        tasks.clear();

        final int rows = dataSet.getNumRows();
        for (int b = 0; b < this.numBscBootstrapSamples; b++) {
            tasks.add(new BootstrapDepDataTask(b, rows));
        }

        ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (final InterruptedException exception) {
            if (this.verbose) {
                this.logger.log("error", "Task has been interrupted");
            }
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        // learn structure of constraints using empirical data => constraint data
        final BDeuScore sd = new BDeuScore(depData);
        sd.setSamplePrior(1.0);
        sd.setStructurePrior(1.0);

        final Fges fges = new Fges(sd);
        fges.setVerbose(false);
        fges.setFaithfulnessAssumed(true);

        Graph depPattern = fges.search();
        depPattern = GraphUtils.replaceNodes(depPattern, depData.getVariables());
        final Graph estDepBN = SearchGraphUtils.dagFromCPDAG(depPattern);

        if (this.verbose) {
            this.out.println("estDepBN:");
            this.out.println(estDepBN);
        }

        // estimate parameters of the graph learned for constraints
        final BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
        final DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
        final BayesIm imHat = DirichletEstimator.estimate(prior, depData);

        // compute scores of graphs that are output by RB search using
        // BSC-I and BSC-D methods
        final Map<Graph, Double> pagLnBSCD = new ConcurrentHashMap<>();
        final Map<Graph, Double> pagLnBSCI = new ConcurrentHashMap<>();

        double maxLnDep = -1, maxLnInd = -1;

        class CalculateBscScoreTask implements Callable<Boolean> {

            final Graph pagOrig;

            public CalculateBscScoreTask(final Graph pagOrig) {
                this.pagOrig = pagOrig;
            }

            @Override
            public Boolean call() throws Exception {
                if (!pagLnBSCD.containsKey(this.pagOrig)) {
                    final double lnInd = RfciBsc.getLnProb(this.pagOrig, h);

                    // Filtering
                    final double lnDep = RfciBsc.getLnProbUsingDepFiltering(this.pagOrig, h, imHat, estDepBN);

                    pagLnBSCD.put(this.pagOrig, lnDep);
                    pagLnBSCI.put(this.pagOrig, lnInd);
                }
                return true;
            }

        }

        tasks.clear();

        for (int i = 0; i < this.pAGs.size(); i++) {
            final Graph pagOrig = this.pAGs.get(i);
            tasks.add(new CalculateBscScoreTask(pagOrig));
        }

        pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (final InterruptedException exception) {
            if (this.verbose) {
                this.logger.log("error", "Task has been interrupted");
            }
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        for (int i = 0; i < this.pAGs.size(); i++) {
            final Graph pagOrig = this.pAGs.get(i);

            final double lnDep = pagLnBSCD.get(pagOrig);
            final double lnInd = pagLnBSCI.get(pagOrig);

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

        final double lnQBSCDTotal = RfciBsc.lnQTotal(pagLnBSCD);
        final double lnQBSCITotal = RfciBsc.lnQTotal(pagLnBSCI);

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

            this.stop = System.currentTimeMillis();

            this.out.println("Elapsed " + (this.stop - this.start) + " ms");
        }

        Graph output = this.graphRBD;

        if (!this.outputRBD) {
            output = this.graphRBI;
        }

        return generateBootstrappingAttributes(output);


    }

    private Graph generateBootstrappingAttributes(final Graph graph) {
        for (final Edge edge : graph.getEdges()) {
            final Node nodeA = edge.getNode1();
            final Node nodeB = edge.getNode2();

            final List<EdgeTypeProbability> edgeTypeProbabilities = getProbability(nodeA, nodeB);

            for (final EdgeTypeProbability etp : edgeTypeProbabilities) {
                edge.addEdgeTypeProbability(etp);
            }
        }

        return graph;
    }

    private List<EdgeTypeProbability> getProbability(final Node node1, final Node node2) {
        final Map<String, Integer> edgeDist = new HashMap<>();
        int no_edge_num = 0;
        for (final Graph g : this.pAGs) {
            final Edge e = g.getEdge(node1, node2);
            if (e != null) {
                String edgeString = e.toString();
                if (e.getEndpoint1() == e.getEndpoint2() && node1.compareTo(e.getNode1()) != 0) {
                    final Edge edge = new Edge(node1, node2, e.getEndpoint1(), e.getEndpoint2());
                    for (final Property property : e.getProperties()) {
                        edge.addProperty(property);
                    }
                    edgeString = edge.toString();
                }
                Integer num_edge = edgeDist.get(edgeString);
                if (num_edge == null) {
                    num_edge = 0;
                }
                num_edge = num_edge.intValue() + 1;
                edgeDist.put(edgeString, num_edge);
            } else {
                no_edge_num++;
            }
        }
        final int n = this.pAGs.size();
        // Normalization
        final List<EdgeTypeProbability> edgeTypeProbabilities = edgeDist.size() == 0 ? null : new ArrayList<>();
        for (final String edgeString : edgeDist.keySet()) {
            final int edge_num = edgeDist.get(edgeString);
            final double probability = (double) edge_num / n;

            final String[] token = edgeString.split("\\s+");
            final String n1 = token[0];
            final String arc = token[1];
            final String n2 = token[2];

            final char end1 = arc.charAt(0);
            final char end2 = arc.charAt(2);

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
                final Endpoint tmp = _end1;
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

    private static double lnXplusY(double lnX, double lnY) {
        final double lnYminusLnX;
        final double temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < RfciBsc.MININUM_EXPONENT) {
            return lnX;
        } else {
            final double w = Math.log1p(exp(lnYminusLnX));
            return w + lnX;
        }
    }

    private static double lnQTotal(final Map<Graph, Double> pagLnProb) {
        final Set<Graph> pags = pagLnProb.keySet();
        final Iterator<Graph> iter = pags.iterator();
        double lnQTotal = pagLnProb.get(iter.next());

        while (iter.hasNext()) {
            final Graph pag = iter.next();
            final double lnQ = pagLnProb.get(pag);
            lnQTotal = RfciBsc.lnXplusY(lnQTotal, lnQ);
        }

        return lnQTotal;
    }

    private static double getLnProbUsingDepFiltering(final Graph pag, final Map<IndependenceFact, Double> H, final BayesIm im, final Graph dep) {
        double lnQ = 0;

        for (final IndependenceFact fact : H.keySet()) {
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
                        Node _X = pag.getNode(splitParent[0].trim());

                        String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
                        Node _Y = pag.getNode(splitParent2[0].trim());

                        List<Node> _Z = new ArrayList<>();
                        if (splitParent2.length > 1) {
                            String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
                            for (String s : splitParent3) {
                                _Z.add(pag.getNode(s.trim()));
                            }
                        }
                        IndependenceFact parentFact = new IndependenceFact(_X, _Y, _Z);
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

            final double v = lnQ + log(p);

            if (Double.isNaN(v) || Double.isInfinite(v)) {
                continue;
            }

            lnQ = v;
        }
        return lnQ;
    }

    @Override
    public long getElapsedTime() {
        return (this.stop - this.start);
    }

    public void setNumRandomizedSearchModels(final int numRandomizedSearchModels) {
        this.numRandomizedSearchModels = numRandomizedSearchModels;
    }

    public void setNumBscBootstrapSamples(final int numBscBootstrapSamples) {
        this.numBscBootstrapSamples = numBscBootstrapSamples;
    }

    public void setLowerBound(final double lowerBound) {
        this.lowerBound = lowerBound;
    }

    public void setUpperBound(final double upperBound) {
        this.upperBound = upperBound;
    }

    public void setOutputRBD(final boolean outputRBD) {
        this.outputRBD = outputRBD;
    }

    public Graph getGraphRBD() {
        return this.graphRBD;
    }

    public Graph getGraphRBI() {
        return this.graphRBI;
    }

    public double getBscD() {
        return this.bscD;
    }

    public double getBscI() {
        return this.bscI;
    }

    private void shutdownAndAwaitTermination(final ExecutorService pool) {
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
        } catch (final InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sets whether verbose output should be produced.
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(final PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    public void setThresholdNoRandomDataSearch(final boolean thresholdNoRandomDataSearch) {
        this.thresholdNoRandomDataSearch = thresholdNoRandomDataSearch;
    }

    public void setCutoffDataSearch(final double cutoffDataSearch) {
        this.cutoffDataSearch = cutoffDataSearch;
    }

    public void setThresholdNoRandomConstrainSearch(final boolean thresholdNoRandomConstrainSearch) {
        this.thresholdNoRandomConstrainSearch = thresholdNoRandomConstrainSearch;
    }

    public void setCutoffConstrainSearch(final double cutoffConstrainSearch) {
        this.cutoffConstrainSearch = cutoffConstrainSearch;
    }

}
