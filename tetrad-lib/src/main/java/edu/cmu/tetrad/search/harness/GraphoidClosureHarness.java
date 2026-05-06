package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphoidAxioms;

import java.util.*;

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
     * The conditioning set type to use in the Markov Checker.
     */
    public enum ConditioningSetType {
        PARENTS, MARKOV_BLANKET, LOCAL
    }

    /**
     * The graphoid closure type to use.
     */
    public enum ClosureType {
        SEMIGRAPHOID, GRAPHOID, COMPOSITIONAL_GRAPHOID
    }

    /**
     * Runs the harness.
     *
     * @param dataSet           The dataset to search over.
     * @param conditioningSetType The type of conditioning set to use in the Markov Checker.
     * @param closureType       The graphoid closure type to apply.
     */
    public void run(DataSet dataSet, ConditioningSetType conditioningSetType, ClosureType closureType) {

        // Step 1: Run a search to get a graph.
        Graph graph = runSearch(dataSet);
        System.out.println("Search graph: " + graph);

        // Step 2: Extract CI facts from the Markov Checker.
        List<Node> variables = dataSet.getVariables();
        Set<GraphoidAxioms.GraphoidIndFact> originalFacts =
                extractMarkovCheckerFacts(graph, dataSet, conditioningSetType);

        System.out.println("\nOriginal CI facts (" + originalFacts.size() + "):");
        for (GraphoidAxioms.GraphoidIndFact fact : originalFacts) {
            System.out.println("  " + fact);
        }

        // Step 3: Build the GraphoidAxioms object and compute the closure.
        GraphoidAxioms axioms = new GraphoidAxioms(originalFacts, variables);
        axioms.ensureTriviality();
        axioms.ensureSymmetry();

        String closureTypeStr = switch (closureType) {
            case SEMIGRAPHOID -> "semigraphoid";
            case GRAPHOID -> "graphoid";
            case COMPOSITIONAL_GRAPHOID -> "compositional graphoid";
        };

        Set<GraphoidAxioms.GraphoidIndFact> closure = axioms.closure(closureTypeStr);

        System.out.println("\nClosure under " + closureTypeStr + " (" + closure.size() + " facts):");
        for (GraphoidAxioms.GraphoidIndFact fact : closure) {
            System.out.println("  " + fact);
        }

        // Step 4: Extract singleton facts from the closure.
        Set<GraphoidAxioms.GraphoidIndFact> closureSingletons =
                GraphoidAxioms.singletonFacts(closure);
        Set<GraphoidAxioms.GraphoidIndFact> originalSingletons =
                GraphoidAxioms.singletonFacts(originalFacts);

        // Step 5: Report the new singleton facts implied by the closure
        // that were not in the original set.
        Set<GraphoidAxioms.GraphoidIndFact> newSingletons = new LinkedHashSet<>(closureSingletons);
        newSingletons.removeAll(originalSingletons);

        System.out.println("\nNew singleton CI facts implied by " + closureTypeStr
                + " closure (" + newSingletons.size() + "):");
        for (GraphoidAxioms.GraphoidIndFact fact : newSingletons) {
            System.out.println("  " + fact);
        }

        // Step 6: Optionally test the new singleton facts against the data.
        if (!newSingletons.isEmpty()) {
            System.out.println("\nTesting new singleton facts against data:");
            testFactsAgainstData(newSingletons, dataSet);
        }
    }

    /**
     * Runs a causal search algorithm on the dataset and returns the resulting graph.
     * Fill this in with whichever search you want to use -- PC, FGES, GRaSP, etc.
     *
     * @param dataSet The dataset to search over.
     * @return The resulting graph.
     */
    private Graph runSearch(DataSet dataSet) {
        throw new UnsupportedOperationException("Implement with your preferred search -- PC, FGES, GRaSP, etc.");
    }

    /**
     * Extracts the CI facts from the Markov Checker for the given graph, dataset,
     * and conditioning set type, and converts them into GraphoidIndFact format.
     * You'll need to wire this into MarkovChecker's existing fact-listing infrastructure.
     *
     * @param graph               The graph to check.
     * @param dataSet             The dataset.
     * @param conditioningSetType The conditioning set type.
     * @return The set of GraphoidIndFacts.
     */
    private Set<GraphoidAxioms.GraphoidIndFact> extractMarkovCheckerFacts(
            Graph graph, DataSet dataSet, ConditioningSetType conditioningSetType) {
        throw new UnsupportedOperationException("Wire into MarkovChecker's fact-listing for the given conditioning set type.");
    }

    /**
     * Tests each of the given CI facts against the data using your preferred CI test,
     * and reports which pass and which fail.
     * Fill in with Fisher's Z, G-squared, etc. as appropriate for your data type.
     *
     * @param facts   The facts to test.
     * @param dataSet The dataset.
     */
    private void testFactsAgainstData(
            Set<GraphoidAxioms.GraphoidIndFact> facts, DataSet dataSet) {
        throw new UnsupportedOperationException("Implement with your preferred CI test -- FisherZ, G-squared, etc.");
    }
}