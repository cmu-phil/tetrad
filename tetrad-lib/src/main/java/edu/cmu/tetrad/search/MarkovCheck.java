package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.*;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Checks whether a graph is Markov given a data set. First, a list of m-separation predictions are made for each pair
 * of variables in the graph given the parents of one of the variables. One list (for Markov) is for where the
 * m-separation holds and another list (for dependency checks) where the m-separation does not hold. Then the
 * predictions are tested against the data set using the independence test. For the Markov test, since an independence
 * test yielding p-values should be Uniform under the null hypothesis, these p-values are tested for Uniformity using
 * the Kolmogorov-Smirnov test. Also, a fraction of dependent judgments is returned, which should equal the alpha level
 * of the independence test if the test is Uniform under the null hypothesis. For the Faithfulness test, the p-values
 * are tested for Uniformity using the Kolmogorov-Smirnov test; these should be dependent. Also, a fraction of dependent
 * judgments is returned.
 * <p>
 * Knowledge may be supplied to the Markov check. This knowledge is used to specify independence and conditioning
 * ranges. For facts of the form X _||_ Y | Z, X and Y should be in the last tier of the knowledge, and Z should be in
 * previous tiers. Additional forbidden or required edges are not allowed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MarkovCheck {
    /**
     * The graph.
     */
    private final Graph graph;
    /**
     * The results of the Markov check for the independent case.
     */
    private final List<IndependenceResult> resultsIndep = new ArrayList<>();
    /**
     * The results of the Markov check for the dependent case.
     */
    private final List<IndependenceResult> resultsDep = new ArrayList<>();
    /**
     * True, just in case the given graph is a CPDAG (completed partially directed acyclic graph).
     */
    private final boolean isCpdag;
    /**
     * The independence test.
     */
    private IndependenceTest independenceTest;
    /**
     * The type of conditioning sets to use in the Markov check.
     */
    private ConditioningSetType setType;
    /**
     * True if the checks should be parallelized. (Not always a good idea.)
     */
    private boolean parallelized = false;
    /**
     * The fraction of dependent judgments for the independent case.
     */
    private double fractionDependentIndep = Double.NaN;
    /**
     * The fraction of dependent judgments for the dependent case.
     */
    private double fractionDependentDep = Double.NaN;
    /**
     * The Kolmogorov-Smirnov p-value for the independent case.
     */
    private double ksPValueIndep = Double.NaN;
    /**
     * The Kolmogorov-Smirnov p-value for the dependent case.
     */
    private double ksPValueDep = Double.NaN;
    /**
     * The Anderson-Darling A^2 statistic for the independent case.
     */
    private double aSquaredIndep = Double.NaN;
    /**
     * The Anderson-Darling A^2 statistic for the dependent case.
     */
    private double aSquaredDep = Double.NaN;
    /**
     * The Anderson-Darling A^2* statistic for the independent case.
     */
    private double aSquaredStarIndep = Double.NaN;
    /**
     * The Anderson-Darling A^2* statistic for the dependent case.
     */
    private double aSquaredStarDep = Double.NaN;
    /**
     * The Anderson-Darling p-value for the independent case.
     */
    private double andersonDarlingPIndep = Double.NaN;
    /**
     * The Anderson-Darling p-value for the dependent case.
     */
    private double andersonDarlingPDep = Double.NaN;
    /**
     * The Binomial p-value for the independent case.
     */
    private double binomialPIndep = Double.NaN;
    /**
     * The Binomial p-value for the dependent case.
     */
    private double binomialPDep = Double.NaN;
    /**
     * The percentage of all samples to use when resampling for each conditional independence test.
     */
    private double percentResample = 0.5;
    /**
     * The number of tests for the independent case.
     */
    private int numTestsIndep = 0;
    /**
     * The number of tests for the dependent case.
     */
    private int numTestsDep = 0;
    /**
     * A knowledge object to specify independence and conditioning ranges. Empty by default.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * For X _||_ Y | Z, X and Y must come from this set if knowledge is used.
     */
    private List<Node> independenceNodes;
    /**
     * For X _||_ Y | Z, the nodes in Z must come from this set if knowledge is used.
     */
    private List<Node> conditioningNodes;

    /**
     * Constructor. Takes a graph and an independence test over the variables of the graph.
     *
     * @param graph            The graph.
     * @param independenceTest The test over the variables of the graph.
     * @param setType          The type of conditioning sets to use in the Markov check.
     */
    public MarkovCheck(Graph graph, IndependenceTest independenceTest, ConditioningSetType setType) {
        this.graph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        this.isCpdag = graph.paths().isLegalCpdag();
        this.independenceTest = independenceTest;
        this.setType = setType;
        this.independenceNodes = new ArrayList<>(independenceTest.getVariables());
        this.conditioningNodes = new ArrayList<>(independenceTest.getVariables());
    }

    /**
     * Returns the set of independence facts used in the Markov check, for dseparation and dconnection separately.
     *
     * @return The set of independence facts used in the Markov check, for dseparation and dconnection separately.
     */
    @NotNull
    public AllSubsetsIndependenceFacts getAllSubsetsIndependenceFacts() {
        List<Node> variables = new ArrayList<>(getVariables(graph.getNodes(), independenceNodes, conditioningNodes));

        for (Node node : variables) {
            if (node == null) throw new NullPointerException("Null node in graph.");
        }

        MsepTest msepTest = new MsepTest(graph);

        List<Node> nodes = new ArrayList<>(variables);
        Set<IndependenceFact> msep = new HashSet<>();
        Set<IndependenceFact> mconn = new HashSet<>();

        for (Node x : nodes) {
            List<Node> other = new ArrayList<>(variables);
            other.remove(x);

            for (Node y : other) {
                List<Node> _other = new ArrayList<>(other);
                _other.remove(y);

                SublistGenerator generator = new SublistGenerator(_other.size(), _other.size());
                int[] list;

                while ((list = generator.next()) != null) {
                    Set<Node> z = GraphUtils.asSet(list, _other);

                    if (!checkNodeIndependenceAndConditioning(x, y, z)) {
                        continue;
                    }

                    IndependenceFact fact = new IndependenceFact(x, y, z);

                    if (msepTest.isMSeparated(x, y, z)) {
                        msep.add(fact);
                    } else {
                        mconn.add(fact);
                    }
                }
            }
        }

        return new AllSubsetsIndependenceFacts(msep, mconn);
    }

    /**
     * Retrieves the list of local independence facts for a given node.
     *
     * @param x The node for which to retrieve the local independence facts.
     * @return The list of local independence facts for the given node.
     */
    public List<IndependenceFact> getLocalIndependenceFacts(Node x) {
        Set<Node> parents = new HashSet<>(graph.getParents(x));

        // Remove all parent nodes and x node itself from the graph
        List<Node> graphNodes_others = graph.getNodes();
        graphNodes_others.remove(x);
        for (Node p : parents) graphNodes_others.remove(p);

        List<IndependenceFact> factList = new ArrayList<>();
        for (Node y : graphNodes_others) {
            // Make a new MsepTest based on the true graph.
            MsepTest msepTest = new MsepTest(graph);
            IndependenceResult testRes = msepTest.checkIndependence(x, y, parents);
            if (testRes.isValid()) factList.add(testRes.getFact());
        }
        return factList;
    }

    /**
     * Calculates the local p-values for a given independence test and a list of independence facts.
     *
     * @param independenceTest The independence test used for calculating the p-values.
     * @param facts            The list of independence facts.
     * @return The list of local p-values.
     */
    public List<Double> getLocalPValues(IndependenceTest independenceTest, List<IndependenceFact> facts) {
        // call pvalue function on each item, only include the non-null ones
        List<Double> pVals = new ArrayList<>();
        for (IndependenceFact f : facts) {
            Double pV;
            // For now, check if the test is FisherZ test.
            if (independenceTest instanceof IndTestFisherZ) {
                pV = ((IndTestFisherZ) independenceTest).getPValue(f.getX(), f.getY(), f.getZ());
                pVals.add(pV);
            } else if (independenceTest instanceof IndTestChiSquare) {
                pV = ((IndTestChiSquare) independenceTest).getPValue(f.getX(), f.getY(), f.getZ());
                if (pV != null) pVals.add(pV);
            }
        }
        return pVals;
    }

    /**
     * Tests a list of p-values against the Anderson-Darling Test.
     *
     * @param pValues the list of p-values to be tested
     * @return the p-value obtained from the Anderson-Darling Test
     */
    public Double checkAgainstAndersonDarlingTest(List<Double> pValues) {
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        return generalAndersonDarlingTest.getP();
    }

    /**
     * Calculates the Anderson-Darling test and classifies nodes as accepted or rejected based on the given threshold.
     *
     * @param independenceTest The independence test to be used for calculating p-values.
     * @param graph            The graph containing the nodes for testing.
     * @param threshold        The threshold value for classifying nodes.
     * @return A list containing two lists: the first list contains the accepted nodes and the second list contains the
     * rejected nodes.
     */
    public List<List<Node>> getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(IndependenceTest independenceTest, Graph graph, Double threshold) {
        // When calling, default reject null as <=0.05
        List<List<Node>> accepts_rejects = new ArrayList<>();
        List<Node> accepts = new ArrayList<>();
        List<Node> rejects = new ArrayList<>();
        List<Node> allNodes = graph.getNodes();
        for (Node x : allNodes) {
            List<IndependenceFact> localIndependenceFacts = getLocalIndependenceFacts(x);
            List<Double> localPValues = getLocalPValues(independenceTest, localIndependenceFacts);
            Double ADTest = checkAgainstAndersonDarlingTest(localPValues);
            if (ADTest <= threshold) {
                rejects.add(x);
            } else {
                accepts.add(x);
            }
        }
        accepts_rejects.add(accepts);
        accepts_rejects.add(rejects);
        return accepts_rejects;
    }

    /**
     * Calculates the precision and recall on the Markov Blanket graph for a given node. Prints the statistics to the
     * console.
     *
     * @param x              The target node.
     * @param estimatedGraph The estimated graph.
     * @param trueGraph      The true graph.
     */
    public void getPrecisionAndRecallOnMarkovBlanketGraph(Node x, Graph estimatedGraph, Graph trueGraph) {
        // Lookup graph is the same structure as trueGraph's structure but node objects replaced by estimated graph nodes.
        Graph lookupGraph = GraphUtils.replaceNodes(trueGraph, estimatedGraph.getNodes());
        Graph xMBLookupGraph = GraphUtils.getMarkovBlanketSubgraphWithTargetNode(lookupGraph, x);
        System.out.println("xMBLookupGraph:" + xMBLookupGraph);
        Graph xMBEstimatedGraph = GraphUtils.getMarkovBlanketSubgraphWithTargetNode(estimatedGraph, x);
        System.out.println("xMBEstimatedGraph:" + xMBEstimatedGraph);

        // TODO VBC: validate
        double ap = new AdjacencyPrecision().getValue(xMBLookupGraph, xMBEstimatedGraph, null);
        double ar = new AdjacencyRecall().getValue(xMBLookupGraph, xMBEstimatedGraph, null);
        double ahp = new ArrowheadPrecision().getValue(xMBLookupGraph, xMBEstimatedGraph, null);
        double ahr = new ArrowheadRecall().getValue(xMBLookupGraph, xMBEstimatedGraph, null);

        NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Node " + x + "'s statistics: " + " \n" +
                           " AdjPrecision = " + nf.format(ap) + " AdjRecall = " + nf.format(ar) + " \n" +
                           " ArrowHeadPrecision = " + nf.format(ahp) + " ArrowHeadRecall = " + nf.format(ahr));
    }

    /**
     * Returns the variables of the independence test.
     *
     * @param graphNodes        a {@link java.util.List} object
     * @param independenceNodes a {@link java.util.List} object
     * @param conditioningNodes a {@link java.util.List} object
     * @return The variables of the independence test.
     */
    public List<Node> getVariables(List<Node> graphNodes, List<Node> independenceNodes, List<Node> conditioningNodes) {
        List<Node> sublistedVariables = new ArrayList<>(independenceNodes);
        sublistedVariables.addAll(conditioningNodes);
        List<Node> vars = new ArrayList<>(graphNodes);
        vars.retainAll(sublistedVariables);
        return vars;
    }

    /**
     * Generates all results, for both the Markov and dependency checks, for each node in the graph given the parents of
     * that node. These results are stored in the resultsIndep and resultsDep lists. This should be called before any of
     * the result methods. Note that only results for X _||_ Y | Z1,...,Zn are generated, where X and Y are in the
     * independenceNodes list and Z1,...,Zn are in the conditioningNodes list.
     *
     * @param clear True, if the results should be cleared before generating new results; otherwise, the new results are
     *              appended to the existing results.
     * @see #getResults(boolean)
     */
    public void generateResults(boolean clear) {
        if (clear) {
            resultsIndep.clear();
            resultsDep.clear();
        }

        if (setType == ConditioningSetType.GLOBAL_MARKOV) {
            AllSubsetsIndependenceFacts result = getAllSubsetsIndependenceFacts();
            generateResultsAllSubsets(result.msep, result.mconn);
            generateResultsAllSubsets(result.msep, result.mconn);
        } else {
            List<Node> variables = getVariables(graph.getNodes(), independenceNodes, conditioningNodes);
            List<Node> nodes = new ArrayList<>(variables);

            List<Node> order = null;

            try {
                order = graph.paths().getValidOrder(graph.getNodes(), true);
            } catch (Exception e) {
                // Leave null. Not an error here. Just means we can't use the ordered local Markov check.
            }

            Set<IndependenceFact> allIndependenceFacts = new HashSet<>();
            Set<IndependenceFact> msep = new HashSet<>();
            Set<IndependenceFact> mconn = new HashSet<>();

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) continue;
                    Node x = nodes.get(i);
                    Node y = nodes.get(j);

                    Set<Node> z;

                    switch (setType) {
                        case LOCAL_MARKOV:
                            z = new HashSet<>(graph.getParents(x));
                            break;
                        case ORDERED_LOCAL_MARKOV:
                            if (order == null) throw new IllegalArgumentException("No valid order found.");
                            z = new HashSet<>(graph.getParents(x));

                            // Keep only the parents in Prefix(x).
                            for (Node w : new ArrayList<>(z)) {
                                int i1 = order.indexOf(x);
                                int i2 = order.indexOf(w);

                                if (i2 >= i1) {
                                    z.remove(w);
                                }
                            }

                            break;
                        case MARKOV_BLANKET:
                            z = GraphUtils.markovBlanket(x, graph);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown separation set type: " + setType);
                    }

                    if (x == y || z.contains(x) || z.contains(y)) continue;

                    if (!checkNodeIndependenceAndConditioning(x, y, z)) {
                        continue;
                    }

                    IndependenceFact fact = new IndependenceFact(x, y, z);
                    allIndependenceFacts.add(fact);
                }
            }

            generateMseps(new ArrayList<>(allIndependenceFacts), msep, mconn, new MsepTest(graph));
            generateResults(msep, true);
            generateResults(mconn, false);

            this.numTestsIndep = msep.size();
            this.numTestsDep = mconn.size();
        }

        calcStats(true);
        calcStats(false);
    }

    /**
     * Returns type of conditioning sets to use in the Markov check.
     *
     * @return The type of conditioning sets to use in the Markov check.
     * @see ConditioningSetType
     */
    public ConditioningSetType getSetType() {
        return this.setType;
    }

    /**
     * Sets the type of conditioning sets to use in the Markov check.
     *
     * @param setType The type of conditioning sets to use in the Markov check.
     * @see ConditioningSetType
     */
    public void setSetType(ConditioningSetType setType) {
        this.setType = setType;
    }

    /**
     * True if the checks should be parallelized. (Not always a good idea.)
     *
     * @param parallelized True if the checks should be parallelized.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * After the generateResults method has been called, this method returns the results for the Markov or dependency
     * check, depending on the value of the indep parameter.
     *
     * @param indep True for the Markov results, false for the dependency results.
     * @return The results for the Markov or dependency check.
     */
    public List<IndependenceResult> getResults(boolean indep) {
        if (indep) {
            return new ArrayList<>(this.resultsIndep);
        } else {
            return new ArrayList<>(this.resultsDep);
        }
    }

    /**
     * Returns the list of p-values for the given list of results.
     *
     * @param results The results.
     * @return Their p-values.
     */
    public List<Double> getPValues(List<IndependenceResult> results) {
        List<Double> pValues = new ArrayList<>();

        for (IndependenceResult result : results) {
            pValues.add(result.getPValue());
        }

        return pValues;
    }

    /**
     * Returns the fraction of dependent judgments for the given list of results.
     *
     * @param indep True for the Markov results, false for the dependency results.
     * @return The fraction of dependent judgments for this condition.
     */
    public double getFractionDependent(boolean indep) {
        if (indep) {
            return fractionDependentIndep;
        } else {
            return fractionDependentDep;
        }
    }

    /**
     * Calculates the fraction of dependent results.
     *
     * @param results the list of IndependenceResult objects
     * @return the fraction of dependent results as a double value
     */
    public double getFractionDependent(List<IndependenceResult> results) {
        int dependent = 0;

        for (IndependenceResult result : results) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        return dependent / (double) results.size();
    }

    /**
     * Returns the Kolmorogov-Smirnov p-value for the given list of results.
     *
     * @param indep True for the Markov results, false for the dependency results.
     * @return The Kolmorogov-Smirnov p-value for this condition.
     */
    public double getKsPValue(boolean indep) {
        if (indep) {
            return ksPValueIndep;
        } else {
            return ksPValueDep;
        }
    }

    /**
     * Returns the Anderson-Darling A^2 statistic for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Anderson-Darling A^2 statistic for the given list of results.
     */
    public double getAndersonDarlingA2(boolean indep) {
        if (indep) {
            return aSquaredIndep;
        } else {
            return aSquaredDep;
        }
    }

    /**
     * Returns the Anderson-Darling A^2* statistic for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Anderson-Darling A^2* statistic for the given list of results.
     */
    public double getAndersonDarlingA2Star(boolean indep) {
        if (indep) {
            return aSquaredStarIndep;
        } else {
            return aSquaredStarDep;
        }
    }

    /**
     * Returns the Anderson-Darling p-value for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Anderson-Darling p-value for the given list of results.
     */
    public double getAndersonDarlingP(boolean indep) {
        if (indep) {
            return andersonDarlingPIndep;
        } else {
            return andersonDarlingPDep;
        }
    }

    /**
     * Returns the Binomial p-value for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Binomial p-value for the given list of results.
     */
    public double getBinomialPValue(boolean indep) {
        if (indep) {
            return binomialPIndep;
        } else {
            return binomialPDep;
        }
    }

    /**
     * Returns the number of tests for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The number of tests for the given list of results.
     */
    public int getNumTests(boolean indep) {
        if (indep) {
            return numTestsIndep;
        } else {
            return numTestsDep;
        }
    }

    /**
     * Returns the variable with the given name.
     *
     * @param name The name of the variables.
     * @return The variable with the given name.
     */
    public Node getVariable(String name) {
        return independenceTest.getVariable(name);
    }

    /**
     * Returns the independence test being used.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets the independence test to be used for determining independence between variables.
     *
     * @param test the independence test to be set
     * @throws IllegalArgumentException if the test parameter is null
     */
    public void setIndependenceTest(IndependenceTest test) {
        if (test == null) {
            throw new IllegalArgumentException("Independence test cannot be null.");
        }

        this.independenceTest = test;
    }

    /**
     * Sets the percentage of all samples to use when resampling for each conditional independence test.
     *
     * @param percentResample The percentage of all samples to use when resampling for each conditional independence
     *                        test.
     */
    public void setPercentResample(double percentResample) {
        this.percentResample = percentResample;
    }

    /**
     * Returns the knowledge object for the Markov checker. This knowledge object should contain the tier knowledge for
     * the Markov checker. The last tier contains the possible X and Y for X _||_ Y | Z1,...,Zn, and the previous tiers
     * contain the possible Z1,...,Zn for X _||_ Y | Z1,...,Zn. Additional forbidden or required edges are ignored.
     *
     * @return The knowledge object.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge object for the Markov checker. The knowledge object should contain the tier knowledge for the
     * Markov checker. The last tier contains the possible X and Y for X _||_ Y | Z1,...,Zn, and the previous tiers
     * contain the possible Z1,...,Zn for X _||_ Y | Z1,...,Zn. Additional forbidden or required edges are ignored.
     *
     * @param knowledge The knowledge object.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (!(knowledge.getListOfExplicitlyForbiddenEdges().isEmpty() && knowledge.getListOfRequiredEdges().isEmpty())) {
            throw new IllegalArgumentException("Knowledge object for the Markov checker cannot contain required of " +
                                               "explicitly forbidden edges; only tier knowledge is used. The last tier contains the possible X " +
                                               "and Y for X _||_ Y | Z1,..,Zn, and the previous tiers contain the possible Z1,..,Zn for X _||_ Y " +
                                               "| Z1,..,Zn.");
        }

        int lastTier = 0;

        for (int t = 0; t < knowledge.getNumTiers(); t++) {
            if (!knowledge.getTier(t).isEmpty()) {
                lastTier = t;
            }
        }

        List<String> independenceNames = knowledge.getTier(lastTier);

        List<String> conditioningNames = new ArrayList<>();

        // Assuming all named nodes go into thd conditioning set.
        for (int i = 0; i <= lastTier; i++) {
            conditioningNames.addAll(knowledge.getTier(i));
        }

        List<Node> independenceNodes = new ArrayList<>();
        for (String name : independenceNames) {
            Node variable = getVariable(name);
            if (variable != null) {
                independenceNodes.add(variable);
            }
        }

        List<Node> conditioningNodes = new ArrayList<>();
        for (String name : conditioningNames) {
            Node variable = getVariable(name);
            if (variable != null) {
                conditioningNodes.add(variable);
            }
        }

        this.independenceNodes = independenceNodes;
        this.conditioningNodes = conditioningNodes;

        this.knowledge = knowledge.copy();
    }

    /**
     * Generates the results for the given set of independence facts as a single record.
     *
     * @return The Markov check record.
     * @see MarkovCheckRecord
     */
    public MarkovCheckRecord getMarkovCheckRecord() {
        setPercentResample(percentResample);
        generateResults(true);
        double adInd = getAndersonDarlingP(true);
        double adDep = getAndersonDarlingP(false);
        double binIndep = getBinomialPValue(true);
        double binDep = getBinomialPValue(false);
        double fracDepInd = getFractionDependent(true);
        double fracDepDep = getFractionDependent(false);
        int numTestsInd = getNumTests(true);
        int numTestsDep = getNumTests(false);
        return new MarkovCheckRecord(adInd, adDep, binIndep, binDep, fracDepInd, fracDepDep, numTestsInd, numTestsDep);
    }

    /**
     * Returns the Markov check record as a string.
     *
     * @return The Markov check record as a string.
     * @see MarkovCheckRecord
     */
    public String getMarkovCheckRecordString() {
        NumberFormat nf = new DecimalFormat("0.000");
        MarkovCheckRecord record = getMarkovCheckRecord();

        return "Anderson-Darling p-value (indep): " + nf.format(record.adInd) + "\n" +
               "Anderson-Darling p-value (dep): " + nf.format(record.adDep) + "\n" +
               "Binomial p-value (indep): " + nf.format(record.binIndep) + "\n" +
               "Binomial p-value (dep): " + nf.format(record.binDep) + "\n" +
               "Fraction of dependent judgments (indep): " + nf.format(record.fracDepInd) + "\n" +
               "Fraction of dependent judgments (dep): " + nf.format(record.fracDepDep) + "\n" +
               "Number of tests (indep): " + record.numTestsInd + "\n" +
               "Number of tests (dep): " + record.numTestsDep;
    }

    /**
     * Returns the nodes that are possible X and Y for X _||_ Y | Z1,...,Zn.
     *
     * @return The nodes that are possible X and Y for X _||_ Y | Z1,...,Zn.
     */
    public List<Node> getIndependenceNodes() {
        return independenceNodes;
    }

    /**
     * Returns the nodes that are possible Z1,...,Zn for X _||_ Y | Z1,...,Zn.
     *
     * @return The nodes that are possible Z1,...,Zn for X _||_ Y | Z1,...,Zn.
     */
    public List<Node> getConditioningNodes() {
        return conditioningNodes;
    }

    /**
     * Generates the m-separation sets for the given list of independence facts. The m-separation sets are stored in the
     * msep and mconn sets.
     *
     * @param allIndependenceFacts The list of independence facts.
     * @param msep                 The set of m-separation facts.
     * @param mconn                The set of m-connection facts.
     * @param msepTest             The m-separation test.
     */
    private void generateMseps(List<IndependenceFact> allIndependenceFacts, Set<IndependenceFact> msep, Set<IndependenceFact> mconn,
                               MsepTest msepTest) {
        class IndCheckTask implements Callable<Pair<Set<IndependenceFact>, Set<IndependenceFact>>> {
            private final int index;
            private final List<IndependenceFact> facts;
            private final MsepTest msepTest;

            IndCheckTask(int index, List<IndependenceFact> facts, MsepTest test) {
                this.index = index;
                this.facts = facts;
                this.msepTest = test;
            }

            @Override
            public Pair<Set<IndependenceFact>, Set<IndependenceFact>> call() {
                Set<IndependenceFact> msep = new HashSet<>();
                Set<IndependenceFact> mconn = new HashSet<>();

                IndependenceFact fact = facts.get(index);

                Node x = fact.getX();
                Node y = fact.getY();
                Set<Node> z = fact.getZ();

                if (this.msepTest.isMSeparated(x, y, z)) {
                    msep.add(fact);
                } else {
                    mconn.add(fact);
                }

                return new Pair<>(msep, mconn);
            }
        }

        List<Callable<Pair<Set<IndependenceFact>, Set<IndependenceFact>>>> tasks = new ArrayList<>();

        for (int i = 0; i < allIndependenceFacts.size() /*&& !Thread.currentThread().isInterrupted()*/; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            IndCheckTask task = new IndCheckTask(i, allIndependenceFacts, msepTest);

            if (!parallelized) {
                Pair<Set<IndependenceFact>, Set<IndependenceFact>> _results = task.call();
                msep.addAll(_results.getFirst());
                mconn.addAll(_results.getSecond());
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);

            List<Future<Pair<Set<IndependenceFact>, Set<IndependenceFact>>>> theseResults;
            theseResults = pool.invokeAll(tasks);

            for (Future<Pair<Set<IndependenceFact>, Set<IndependenceFact>>> future : theseResults) {
                try {
                    Pair<Set<IndependenceFact>, Set<IndependenceFact>> setPair = future.get();
                    msep.addAll(setPair.getFirst());
                    mconn.addAll(setPair.getSecond());
                } catch (InterruptedException | ExecutionException e) {
                    TetradLogger.getInstance().forceLogMessage(e.getMessage());
                }
            }

            pool.shutdown();
        }
    }

    /**
     * Generates the results for the given set of independence facts.
     *
     * @param facts The set of independence facts.
     * @param msep  True if for implied independencies, false if for implied dependencies.
     */
    private void generateResults(Set<IndependenceFact> facts, boolean msep) {
        class IndCheckTask implements Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>> {
            private final int index;
            private final List<IndependenceFact> facts;
            private final IndependenceTest independenceTest;

            IndCheckTask(int index, List<IndependenceFact> facts, IndependenceTest test) {
                this.index = index;
                this.facts = facts;
                this.independenceTest = test;
            }

            @Override
            public Pair<Set<IndependenceResult>, Set<IndependenceResult>> call() {
                Set<IndependenceResult> resultsIndep = new HashSet<>();
                Set<IndependenceResult> resultsDep = new HashSet<>();
                independenceTest.setVerbose(false);

                IndependenceFact fact = facts.get(index);

                Node x = fact.getX();
                Node y = fact.getY();
                Set<Node> z = fact.getZ();

                if (independenceTest instanceof RowsSettable) {
                    List<Integer> rows = getSubsampleRows(percentResample); // Default as 0.5
                    ((RowsSettable) independenceTest).setRows(rows); // FisherZ will only calc pvalues to those rows
                }
                addResults(resultsIndep, resultsDep, fact, x, y, z);

                return new Pair<>(resultsIndep, resultsDep);
            }

            private void addResults(Set<IndependenceResult> resultsIndep, Set<IndependenceResult> resultsDep, IndependenceFact fact, Node x, Node y, Set<Node> z) {
                boolean verbose = independenceTest.isVerbose();
                // Temporarily turn off verbose
                independenceTest.setVerbose(false);
                IndependenceResult result;
                try {
                    result = independenceTest.checkIndependence(x, y, z);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                boolean indep = result.isIndependent();
                double pValue = result.getPValue();
                independenceTest.setVerbose(verbose);

                if (!Double.isNaN(pValue)) {
                    if (msep) {
                        resultsIndep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                    } else {
                        resultsDep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                    }
                }
            }
        }

        List<Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>>> tasks = new ArrayList<>();

        for (int i = 0; i < facts.size() /*&& !Thread.currentThread().isInterrupted()*/; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            IndCheckTask task = new IndCheckTask(i, new ArrayList<>(facts), independenceTest);

            if (!parallelized) {
                Pair<Set<IndependenceResult>, Set<IndependenceResult>> _results = task.call();
                resultsIndep.addAll(_results.getFirst());
                resultsDep.addAll(_results.getSecond());
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            List<Future<Pair<Set<IndependenceResult>, Set<IndependenceResult>>>> theseResults;
            theseResults = pool.invokeAll(tasks);

            for (Future<Pair<Set<IndependenceResult>, Set<IndependenceResult>>> future : theseResults) {
                try {
                    resultsIndep.addAll(future.get().getFirst());
                    resultsDep.addAll(future.get().getSecond());
                } catch (InterruptedException | ExecutionException e) {
                    TetradLogger.getInstance().forceLogMessage(e.getMessage());
                }
            }

            pool.shutdown();
        }
    }

    /**
     * Calculates the statistics for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     */
    private void calcStats(boolean indep) {
        List<IndependenceResult> results = new ArrayList<>(getResultsLocal(indep));

        int dependent = 0;

        for (IndependenceResult result : results) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        List<Double> pValues = getPValues(results);
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        double aSquared = generalAndersonDarlingTest.getASquared();
        double aSquaredStar = generalAndersonDarlingTest.getASquaredStar();

        if (indep) {
            fractionDependentIndep = dependent / (double) results.size();
            if (pValues.size() < 2) {
                ksPValueIndep = Double.NaN;
                binomialPIndep = Double.NaN;
                aSquaredIndep = Double.NaN;
                aSquaredStarIndep = Double.NaN;
                andersonDarlingPIndep = Double.NaN;
            } else {
                ksPValueIndep = UniformityTest.getKsPValue(pValues, 0.0, 1.0);
                binomialPIndep = getBinomialPValue(pValues, independenceTest.getAlpha());
                aSquaredIndep = aSquared;
                aSquaredStarIndep = aSquaredStar;
                andersonDarlingPIndep = 1. - generalAndersonDarlingTest.getProbTail(pValues.size(), aSquaredStar);
            }
        } else {
            fractionDependentDep = dependent / (double) results.size();
            if (pValues.size() < 2) {
                ksPValueDep = Double.NaN;
                binomialPDep = Double.NaN;
                aSquaredDep = Double.NaN;
                aSquaredStarDep = Double.NaN;
                andersonDarlingPDep = Double.NaN;

            } else {
                ksPValueDep = UniformityTest.getKsPValue(pValues, 0.0, 1.0);
                binomialPDep = getBinomialPValue(pValues, independenceTest.getAlpha());
                aSquaredDep = aSquared;
                aSquaredStarDep = aSquaredStar;
                andersonDarlingPDep = 1. - generalAndersonDarlingTest.getProbTail(pValues.size(), aSquaredStar);
            }
        }
    }

    /**
     * Returns a list of row indices for a subsample of the data set.
     *
     * @param v The fraction of the data set to use.
     * @return A list of row indices for a subsample of the data set.
     */
    private List<Integer> getSubsampleRows(double v) {
        int sampleSize = independenceTest.getSampleSize();
        int subsampleSize = (int) FastMath.floor(sampleSize * v);
        List<Integer> rows = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            rows.add(i);
        }
        Collections.shuffle(rows);
        return rows.subList(0, subsampleSize);
    }

    /**
     * Generates the results for the given set of independence facts, for both the Markov and dependency checks.
     *
     * @param msep  The set of m-separation facts.
     * @param mconn The set of m-connection facts.
     */
    private void generateResultsAllSubsets(Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
        generateResults(msep, true);
        generateResults(mconn, false);
    }

    /**
     * Returns a Binomial p-value for the hypothesis that the distribution of p-values is not Uniform under the null
     * hypothesis. Values less than alpha imply non-uniform distributions.
     *
     * @param pValues The p-values.
     * @param alpha   The alpha level. Rejections with p-values less than this are considered dependent.
     * @return The Binomial p-value for non-uniformity.
     */
    private double getBinomialPValue(List<Double> pValues, double alpha) {
        int independentJudgements = 0;

        for (double pValue : pValues) {
            if (pValue > alpha) independentJudgements++;
        }

        int p = pValues.size();

        // The left tail of this binomial distribution is a p-value for getting too few dependent judgments for
        // the distribution to count as uniform.
        BinomialDistribution bd = new BinomialDistribution(p, alpha);

        // We want the area to the right of this, so we subtract from 1.
        return (1.0 - bd.cumulativeProbability(independentJudgements)) + (bd.probability(p - independentJudgements));
    }

    /**
     * Returns the list of results for the given condition.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The list of results for the given condition.
     */
    private List<IndependenceResult> getResultsLocal(boolean indep) {
        if (indep) {
            return this.resultsIndep;
        } else {
            return this.resultsDep;
        }
    }

    /**
     * Checks if both nodes x and y are independent and if the set of conditioning nodes contains all elements of z.
     *
     * @param x Node to check for independence along with y.
     * @param y Node to check for independence along with x.
     * @param z Set of nodes to check if all are contained within the conditioning nodes.
     * @return true if x and y are in the independence nodes and all elements of z are in the conditioning nodes; false
     * otherwise.
     */
    private boolean checkNodeIndependenceAndConditioning(Node x, Node y, Set<Node> z) {
        List<Node> independenceNodes = getIndependenceNodes();
        return (independenceNodes.contains(x) && independenceNodes.contains(y)
                && new HashSet<>(getConditioningNodes()).containsAll(z));
    }

    /**
     * Checks whether the given graph is a CPDAG (Completed Partially Directed Acyclic Graph).
     *
     * @return true if the graph is a CPDAG, false otherwise
     */
    public boolean isCpdag() {
        return isCpdag;
    }

    /**
     * Calculates the Kolmogorov-Smirnov (KS) p-value for a list of independence test results.
     *
     * @param visiblePairs a list of IndependenceResult objects representing the observed values and expected values for
     *                     a series of tests
     * @return the KS p-value calculated using the list of independence test results
     */
    public double getKsPValue(List<IndependenceResult> visiblePairs) {
        List<Double> pValues = getPValues(visiblePairs);
        return UniformityTest.getKsPValue(pValues, 0.0, 1.0);
    }

    /**
     * Calculates the binomial p-value based on the list of visible pairs.
     *
     * @param visiblePairs a list of IndependenceResult representing the visible pairs.
     * @return the binomial p-value.
     */
    public double getBinomialPValue(List<IndependenceResult> visiblePairs) {
        List<Double> pValues = getPValues(visiblePairs);
        return getBinomialPValue(pValues, independenceTest.getAlpha());
    }

    /**
     * Calculates the Anderson-Darling A2 value for a list of independence results.
     *
     * @param visiblePairs the list of independence results
     * @return the Anderson-Darling A2 value
     */
    public double getAndersonDarlingA2(List<IndependenceResult> visiblePairs) {
        List<Double> pValues = getPValues(visiblePairs);
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        return generalAndersonDarlingTest.getASquared();
    }

    /**
     * Calculates the Anderson-Darling p-value for a given list of independence results.
     *
     * @param visiblePairs the list of independence results
     * @return the Anderson-Darling p-value
     */
    public double getAndersonDarlingPValue(List<IndependenceResult> visiblePairs) {
        List<Double> pValues = getPValues(visiblePairs);
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
//        double aSquared = generalAndersonDarlingTest.getASquared();
        double aSquaredStar = generalAndersonDarlingTest.getASquaredStar();
        return 1. - generalAndersonDarlingTest.getProbTail(pValues.size(), aSquaredStar);
    }

    /**
     * A single record for the results of the Markov check.
     *
     * @param adInd       The Anderson-Darling p-value for the independent case.
     * @param adDep       The Anderson-Darling p-value for the dependent case.
     * @param binIndep    The Binomial p-value for the independent case.
     * @param binDep      The Binomial p-value for the dependent case.
     * @param fracDepInd  The fraction of dependent judgments for the independent case.
     * @param fracDepDep  The fraction of dependent judgments for the dependent case.
     * @param numTestsInd The number of tests for the independent case.
     * @param numTestsDep The number of tests for the dependent case.
     */
    public record MarkovCheckRecord(double adInd, double adDep, double binIndep, double binDep, double fracDepInd,
                                    double fracDepDep, int numTestsInd, int numTestsDep) {
    }

    /**
     * Stores the set of m-separation facts and the set of m-connection facts for a graph, for the global check.
     */
    public static final class AllSubsetsIndependenceFacts {

        /**
         * {@link Set} of m-separation facts.
         */
        private final Set<IndependenceFact> msep;

        /**
         * {@link Set} of m-connection facts.
         */
        private final Set<IndependenceFact> mconn;

        /**
         * Constructor.
         *
         * @param msep  The set of m-separation facts.
         * @param mconn The set of m-connection facts.
         */
        public AllSubsetsIndependenceFacts(Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
            this.msep = msep;
            this.mconn = mconn;
        }

        /**
         * Returns a string representation of the m-separation facts.
         *
         * @return A string representation of the m-separation facts.
         */
        public String toStringIndep() {
            StringBuilder builder = new StringBuilder("All subsets independence facts:\n");

            for (IndependenceFact fact : msep) {
                builder.append(fact).append("\n");
            }

            return builder.toString();
        }

        /**
         * Returns a string representation of the m-connection facts.
         *
         * @return A string representation of the m-connection facts.
         */
        public String toStringDep() {
            StringBuilder builder = new StringBuilder("All subsets independence facts:\n");

            for (IndependenceFact fact : mconn) {
                builder.append(fact).append("\n");
            }

            return builder.toString();
        }

        /**
         * Returns the set of m-separation facts.
         *
         * @return The set of m-separation facts.
         */
        public List<IndependenceFact> getMsep() {
            return new ArrayList<>(msep);
        }

        /**
         * Returns the set of m-connection facts.
         *
         * @return The set of m-connection facts.
         */
        public List<IndependenceFact> getMconn() {
            return new ArrayList<>(mconn);
        }
    }
}
