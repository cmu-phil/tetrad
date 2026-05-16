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

    /**
     * The main method serves as the entry point for the program execution.
     * It invokes the method to test a random linear Gaussian (LG) model by
     * generating a graph, simulating data, performing a structure search,
     * and running a graphoid closure analysis.
     *
     * @param args Command-line arguments passed to the program execution.
     */
    public static void main(String[] args) {
//        tryRandomLgModel(30, 40);
        trySimpleXorModel();
    }

    private static void tryRandomLgModel(int numMeasures, int numEdges) {
        try {
            Graph graph = RandomGraph.randomGraph(numMeasures, 0, numEdges,
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
     * Computes the closure of a set of {@link IndependenceFact}s under the given graphoid
     * assumption type, using the variables in the provided list as the full variable universe.
     *
     * <p>The closure is returned as a {@link Set} of {@link IndependenceFact}s. Each fact
     * in the returned set has singleton X and Y sets (i.e., the result contains only
     * standard pairwise CI statements), reflecting the convention used elsewhere in this
     * harness.
     *
     * <p>The three closure types are:
     * <ul>
     *   <li>{@link ClosureType#SEMIGRAPHOID} – applies the semigraphoid axioms
     *       (symmetry, decomposition, weak union, contraction).</li>
     *   <li>{@link ClosureType#GRAPHOID} – additionally applies the intersection axiom.</li>
     *   <li>{@link ClosureType#COMPOSITIONAL_GRAPHOID} – computes the graphoid closure first,
     *       then applies composition to its singleton facts.</li>
     * </ul>
     *
     * @param facts     the seed set of independence facts; each fact must have a singleton X and Y
     * @param variables the full variable universe used by {@link GraphoidAxioms}
     * @param closure   which closure type to apply
     * @return the closed set of {@link IndependenceFact}s (singleton X and Y only)
     */
    public static Set<IndependenceFact> computeClosure(Set<IndependenceFact> facts,
                                                List<Node> variables,
                                                ClosureType closure) {
        Set<GraphoidAxioms.GraphoidIndFact> graphoidFacts = toGraphoidFacts(facts);

        GraphoidAxioms axioms = new GraphoidAxioms(graphoidFacts, variables);
        axioms.ensureTriviality();
        axioms.ensureSymmetry();

        String closureTypeStr = switch (closure) {
            case SEMIGRAPHOID -> "semigraphoid";
            case GRAPHOID -> "graphoid";
            case COMPOSITIONAL_GRAPHOID -> "compositional graphoid";
        };

        Set<GraphoidAxioms.GraphoidIndFact> closedGraphoidFacts;

        if (closure == ClosureType.COMPOSITIONAL_GRAPHOID) {
            Set<GraphoidAxioms.GraphoidIndFact> graphoidClosure = axioms.closure("graphoid");
            closedGraphoidFacts = axioms.singletonClosureWithComposition(graphoidClosure);
            closedGraphoidFacts.addAll(graphoidClosure);
        } else {
            closedGraphoidFacts = axioms.closure(closureTypeStr);
        }

        return toIndependenceFacts(GraphoidAxioms.singletonFacts(closedGraphoidFacts));
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
        List<Node> variables = dataSet.getVariables();

        // Step 1: Extract CI facts from the Markov Checker.
        Set<IndependenceFact> originalFacts = MarkovCheck.computeAllImpliedFacts(graph, conditioningSetType);
        Set<IndependenceFact> originalSingletons = singletonIndependenceFacts(originalFacts);

        System.out.println("CI facts (" + originalFacts.size() + ") implied by " + conditioningSetType + ":");
        for (IndependenceFact fact : originalFacts) {
            System.out.println("  " + fact);
        }

        // Step 2: Compute the closure.
        Set<IndependenceFact> closureSingletons = computeClosure(originalFacts, variables, closureType);

        String closureTypeStr = switch (closureType) {
            case SEMIGRAPHOID -> "semigraphoid";
            case GRAPHOID -> "graphoid";
            case COMPOSITIONAL_GRAPHOID -> "compositional graphoid";
        };

        System.out.println("\nClosure singletons under " + closureTypeStr + " (" + closureSingletons.size() + " facts):");
        for (IndependenceFact fact : closureSingletons) {
            System.out.println("  " + fact);
        }

        // Step 3: Report new singleton facts implied by the closure that were not in the original set.
        Set<IndependenceFact> newSingletons = new LinkedHashSet<>(closureSingletons);
        newSingletons.removeAll(originalSingletons);

        System.out.println("\nNew singleton CI facts implied by " + closureTypeStr
                + " closure (" + newSingletons.size() + "):");
        for (IndependenceFact fact : newSingletons) {
            System.out.println("  " + fact);
        }

        // Step 4: Optionally test the closure singleton facts against the data.
        if (!closureSingletons.isEmpty()) {
            System.out.println("\nTesting closure singleton facts against data:");
            testFactsAgainstData(closureSingletons, test);
        }
    }

    /**
     * Converts a set of {@link IndependenceFact}s (with singleton X and Y) into
     * the internal {@link GraphoidAxioms.GraphoidIndFact} representation.
     */
    private static Set<GraphoidAxioms.GraphoidIndFact> toGraphoidFacts(Set<IndependenceFact> facts) {
        return facts.stream()
                .map(fact -> new GraphoidAxioms.GraphoidIndFact(
                        Set.of(fact.getX()),
                        Set.of(fact.getY()),
                        fact.getZ()))
                .collect(Collectors.toSet());
    }

    /**
     * Converts a set of {@link GraphoidAxioms.GraphoidIndFact}s with singleton X and Y sets
     * into standard {@link IndependenceFact}s. Facts with non-singleton X or Y are silently
     * skipped, matching the convention used in extractSingletonFacts.
     */
    private static Set<IndependenceFact> toIndependenceFacts(Set<GraphoidAxioms.GraphoidIndFact> graphoidFacts) {
        Set<IndependenceFact> result = new LinkedHashSet<>();
        for (GraphoidAxioms.GraphoidIndFact fact : graphoidFacts) {
            if (fact.getX().size() != 1 || fact.getY().size() != 1) continue;
            result.add(new IndependenceFact(
                    fact.getX().iterator().next(),
                    fact.getY().iterator().next(),
                    fact.getZ()));
        }
        return result;
    }

    /**
     * Filters a set of {@link IndependenceFact}s to those with singleton X and Y.
     */
    private Set<IndependenceFact> singletonIndependenceFacts(Set<IndependenceFact> facts) {
        // IndependenceFact always has singleton X and Y by construction, but
        // this mirrors the graphoid-side filtering for symmetry.
        return new LinkedHashSet<>(facts);
    }

    /**
     * Tests each of the given CI facts against the data using your preferred CI test,
     * and reports which pass and which fail.
     *
     * @param facts The facts to test.
     * @param test  The independence test to use.
     */
    private void testFactsAgainstData(Set<IndependenceFact> facts, IndependenceTest test) {
        try {
            for (IndependenceFact fact : facts) {
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
