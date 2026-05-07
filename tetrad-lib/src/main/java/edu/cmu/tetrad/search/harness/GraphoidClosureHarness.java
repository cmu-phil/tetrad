package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphoidAxioms;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A harness for exploring graphoid closure of CI facts implied by a Markov Checker.
 * Runs a search, extracts the CI facts from the Markov Checker for a given conditioning
 * set type, computes the closure under semigraphoid, graphoid, or compositional graphoid
 * assumptions, and lists the implied singleton facts not in the original set.
 *
 * @author josephramsey
 */
public class GraphoidClosureHarness {

    /**
     * Constructor.
     */
    public GraphoidClosureHarness() {
    }

    private static void tryRandomLgModel() {
        try {
            Graph graph = RandomGraph.randomGraph(20, 0, 20,
                    100, 100, 100, false);

            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);

            DataSet data = im.simulateData(1000, false);

            SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
            PermutationSearch search = new PermutationSearch(new Boss(score));
            try {
                graph = search.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            IndependenceTest test = new IndTestFisherZ(data, 0.01);

            new GraphoidClosureHarness().run(graph, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
                    ClosureType.COMPOSITIONAL_GRAPHOID);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static void trySimpleXorModel() {
        Graph graph = new EdgeListGraph();

        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");

        graph.addNode(x);
        graph.addNode(y);
        graph.addNode(z);

        graph.addDirectedEdge(x, z);
        graph.addDirectedEdge(y, z);

        BayesPm pm = new BayesPm(graph, 2, 2);
        MlBayesIm im = new MlBayesIm(pm);

        // X is marginally uniform: P(X=0) = P(X=1) = 0.5
        im.setProbability(im.getNodeIndex(x), 0, 0, 0.5);
        im.setProbability(im.getNodeIndex(x), 0, 1, 0.5);

        // Y is marginally uniform: P(Y=0) = P(Y=1) = 0.5
        im.setProbability(im.getNodeIndex(y), 0, 0, 0.5);
        im.setProbability(im.getNodeIndex(y), 0, 1, 0.5);

        // Z = XOR(X, Y): Z=1 iff X != Y
        // Parent order is X, Y; rows are (X=0,Y=0), (X=0,Y=1), (X=1,Y=0), (X=1,Y=1)
        int zIndex = im.getNodeIndex(z);
        im.setProbability(zIndex, 0, 0, 1.0); // X=0, Y=0 => Z=0
        im.setProbability(zIndex, 0, 1, 0.0);
        im.setProbability(zIndex, 1, 0, 0.0); // X=0, Y=1 => Z=1
        im.setProbability(zIndex, 1, 1, 1.0);
        im.setProbability(zIndex, 2, 0, 0.0); // X=1, Y=0 => Z=1
        im.setProbability(zIndex, 2, 1, 1.0);
        im.setProbability(zIndex, 3, 0, 1.0); // X=1, Y=1 => Z=0
        im.setProbability(zIndex, 3, 1, 0.0);

        System.out.println("IM: " + im);

        DataSet data = im.simulateData(1000, false);

        IndependenceTest test = new ChiSquare().getTest(data, new Parameters());
        test.setVerbose(false);

        Graph testGraph;
        try {
            Pc pc = new Pc(test);
            testGraph = pc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        new GraphoidClosureHarness().run(testGraph, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY, ClosureType.COMPOSITIONAL_GRAPHOID);
    }

    /**
     * The main method serves as the entry point for the program execution.
     * It invokes the method to test a random linear Gaussian (LG) model by
     * generating a graph, simulating data, performing a structure search,
     * and running a graphoid closure analysis.
     *
     * @param args Command-line arguments passed to the program execution.
     */
    public static void main(String[] args) {
//        tryRandomLgModel();
        trySimpleXorModel();
    }

    /**
     * Runs the harness.
     *
     * @param graph               The graph to search over.
     * @param test                The independence test to use for Markov Checker.
     * @param conditioningSetType The type of conditioning set to use in the Markov Checker.
     * @param closureType         The graphoid closure type to apply.
     */
    public void run(Graph graph, IndependenceTest test, ConditioningSetType conditioningSetType, ClosureType closureType) {

        DataSet dataSet = (DataSet) test.getData();

        // Step 1: Extract CI facts from the Markov Checker.
        List<Node> variables = dataSet.getVariables();
        Set<GraphoidAxioms.GraphoidIndFact> originalFacts =
                extractMarkovCheckerFacts(graph, conditioningSetType);

        System.out.println("CI facts (" + originalFacts.size() + ") implied by " + conditioningSetType + ":");
        for (GraphoidAxioms.GraphoidIndFact fact : originalFacts) {
            System.out.println("  " + fact);
        }

        // Step 2: Build the GraphoidAxioms object and compute the closure.
        GraphoidAxioms axioms = new GraphoidAxioms(originalFacts, variables);
        axioms.ensureTriviality();
        axioms.ensureSymmetry();

        String closureTypeStr = switch (closureType) {
            case SEMIGRAPHOID -> "semigraphoid";
            case GRAPHOID -> "graphoid";
            case COMPOSITIONAL_GRAPHOID -> "compositional graphoid";
        };

        Set<GraphoidAxioms.GraphoidIndFact> closure;

        if (closureType == ClosureType.COMPOSITIONAL_GRAPHOID) {
            // First compute the graphoid closure, then apply composition to its singletons
            Set<GraphoidAxioms.GraphoidIndFact> graphoidClosure = axioms.closure("graphoid");
            closure = axioms.singletonClosureWithComposition(graphoidClosure);
            closure.addAll(graphoidClosure);
        } else {
            closure = axioms.closure(closureTypeStr);
        }

        System.out.println("\nClosure under " + closureTypeStr + " (" + closure.size() + " facts):");
        for (GraphoidAxioms.GraphoidIndFact fact : closure) {
            System.out.println("  " + fact);
        }

        // Step 3: Extract singleton facts from the closure.
        Set<GraphoidAxioms.GraphoidIndFact> closureSingletons =
                GraphoidAxioms.singletonFacts(closure);
        Set<GraphoidAxioms.GraphoidIndFact> originalSingletons =
                GraphoidAxioms.singletonFacts(originalFacts);

        // Step 4: Report the new singleton facts implied by the closure
        // that were not in the original set.
        Set<GraphoidAxioms.GraphoidIndFact> newSingletons = new LinkedHashSet<>(closureSingletons);
        newSingletons.removeAll(originalSingletons);

        System.out.println("\nNew singleton CI facts implied by " + closureTypeStr
                + " closure (" + newSingletons.size() + "):");
        for (GraphoidAxioms.GraphoidIndFact fact : newSingletons) {
            System.out.println("  " + fact);
        }

        // Step 7: Optionally test the original singleton facts against the data.
        if (!closureSingletons.isEmpty()) {
            System.out.println("\nTesting closure singleton facts against data:");
            testFactsAgainstData(closureSingletons, test);
        }
    }

    /**
     * Extracts the CI facts from the Markov Checker for the given graph, dataset,
     * and conditioning set type, and converts them into GraphoidIndFact format.
     * You'll need to wire this into MarkovChecker's existing fact-listing infrastructure.
     *
     * @param graph               The graph to check.
     * @param conditioningSetType The conditioning set type.
     * @return The set of GraphoidIndFacts.
     */
    private Set<GraphoidAxioms.GraphoidIndFact> extractMarkovCheckerFacts(
            Graph graph, ConditioningSetType conditioningSetType) {
        Set<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(graph, conditioningSetType);

        return facts.stream()
                .map(fact -> new GraphoidAxioms.GraphoidIndFact(Set.of(fact.getX()), Set.of(fact.getY()),
                        fact.getZ()))
                .collect(Collectors.toSet());
    }

    /**
     * Tests each of the given CI facts against the data using your preferred CI test,
     * and reports which pass and which fail.
     * Fill in with Fisher's Z, G-squared, etc. as appropriate for your data type.
     *
     * @param facts The facts to test.
     * @param test  The independence test to use.
     */
    private void testFactsAgainstData(Set<GraphoidAxioms.GraphoidIndFact> facts, IndependenceTest test) {
        try {
            Set<IndependenceFact> factsToTest = extractSingletonFacts(facts);

            for (IndependenceFact fact : factsToTest) {
                if (test.checkIndependence(fact.getX(), fact.getY(), fact.getZ()).isIndependent()) {
                    System.out.println("Fact passed: " + fact);
                } else {
                    System.out.println("Fact FAILED: " + fact);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<IndependenceFact> extractSingletonFacts(Set<GraphoidAxioms.GraphoidIndFact> facts) {
        Set<IndependenceFact> result = new LinkedHashSet<>();
        for (GraphoidAxioms.GraphoidIndFact fact : facts) {
            if (fact.getX().size() != 1) {
                continue;
            }

            if (fact.getY().size() != 1) {
                continue;
            }

            result.add(new IndependenceFact(fact.getX().iterator().next(), fact.getY().iterator().next(), fact.getZ()));
        }

        return result;
    }

    /**
     * The graphoid closure type to use.
     */
    public enum ClosureType {
        /**
         * Semigraphoid closure.
         */
        SEMIGRAPHOID,
        /**
         * Graphoid closure.
         */
        GRAPHOID,
        /**
         * Compositional graphoid closure.
         */
        COMPOSITIONAL_GRAPHOID
    }
}