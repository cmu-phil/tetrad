package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Harness for comparing FASK and CPW edge-orientation accuracy across all five
 * pairwise left-right rules on random acyclic linear SEMs with Gumbel(0,1) errors.
 *
 * <p>For each rule (1–5) and each algorithm (FASK, CPW), across NUM_REPLICATES
 * independent graph/data draws:
 * <ol>
 *   <li>Generate a random acyclic graph and simulate data (Gumbel errors, n=SAMPLE_SIZE).</li>
 *   <li>Run FASK (with the given left-right rule) and CPW (FCI + pairwise rule).</li>
 *   <li>For every singly-directed true edge Xi → Xj, check whether the output
 *       graph contains exactly Xi → Xj (correct), Xj → Xi (misoriented), or
 *       neither (missing from output).</li>
 *   <li>Report: total correct, misoriented, missing, and overall orientation
 *       error rate (misoriented / eligible).</li>
 * </ol>
 *
 * <p>Two-cycles in the true graph are skipped. The comparison is based on
 * directed edges only; undirected or partially-oriented edges in the output
 * graph that cover a true directed edge are counted as "missing".
 */
public final class FaskCpwOrientationHarness {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int NUM_REPLICATES = 100;
    private static final int SAMPLE_SIZE    = 1000;
    private static final int NUM_MEASURES   = 10;
    private static final int AVG_DEGREE     = 4;

    /** Fisher Z alpha for CPW's independence test. */
    private static final double ALPHA        = 0.05;

    /** SEM BIC penalty discount used by FASK's internal score. */
    private static final double PENALTY_DISC = 2.0;

    private static final DecimalFormat DF = new DecimalFormat("0.000");

    private FaskCpwOrientationHarness() {}

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Runs the orientation harness for FASK and CPW across all five pairwise
     * left-right rules and prints a comparative summary table.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("=== FASK vs CPW orientation harness ===");
        System.out.printf("Replicates=%d  n=%d  vars=%d  avgDeg=%d  alpha=%.3f%n%n",
                NUM_REPLICATES, SAMPLE_SIZE, NUM_MEASURES, AVG_DEGREE, ALPHA);

        System.out.printf("%-8s  %-6s  %8s  %11s  %7s  %8s  %10s  %8s%n",
                "Algo", "Rule",
                "Correct", "Misoriented", "Missing", "Eligible", "ExtraEdges", "ErrRate");
        System.out.println("-".repeat(85));

        for (int rule = 1; rule <= 5; rule++) {
            OrientationSummary faskSummary = runFask(rule);
            printRow("FASK", rule, faskSummary);
        }

        System.out.println();

        for (int rule = 1; rule <= 5; rule++) {
            OrientationSummary cpwSummary = runCpw(rule);
            printRow("CPW", rule, cpwSummary);
        }
    }

    // -----------------------------------------------------------------------
    // FASK evaluation loop
    // -----------------------------------------------------------------------

    private static OrientationSummary runFask(int pwRule) {
        long totalCorrect     = 0;
        long totalMisoriented = 0;
        long totalMissing     = 0;
        long totalEligible    = 0;
        long totalExtraEdges  = 0;

        for (int r = 0; r < NUM_REPLICATES; r++) {
            Graph trueGraph = generateRandomAcyclicGraph();
            DataSet dataSet = simulateData(trueGraph);
            trueGraph = GraphUtils.replaceNodes(trueGraph, dataSet.getVariables());

            // Run FASK
            SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            score.setPenaltyDiscount(PENALTY_DISC);

            Fask fask = new Fask(dataSet, score);
            fask.setLeftRight(toLeftRight(pwRule));
            fask.setKnowledge(new Knowledge());

            Graph outputGraph;
            try {
                outputGraph = fask.search();
            } catch (Exception e) {
                System.err.println("FASK search failed on replicate " + r + ": " + e.getMessage());
                continue;
            }

            OrientationCounts counts = evaluateOrientations(trueGraph, outputGraph);
            totalCorrect     += counts.correct;
            totalMisoriented += counts.misoriented;
            totalMissing     += counts.missing;
            totalEligible    += counts.eligible;
            totalExtraEdges  += counts.extraEdges;
        }

        return new OrientationSummary(totalCorrect, totalMisoriented, totalMissing, totalEligible, totalExtraEdges);
    }

    // -----------------------------------------------------------------------
    // CPW evaluation loop
    // -----------------------------------------------------------------------

    private static OrientationSummary runCpw(int pwRule) {
        long totalCorrect     = 0;
        long totalMisoriented = 0;
        long totalMissing     = 0;
        long totalEligible    = 0;
        long totalExtraEdges  = 0;

        for (int r = 0; r < NUM_REPLICATES; r++) {
            Graph trueGraph = generateRandomAcyclicGraph();
            DataSet dataSet = simulateData(trueGraph);
            trueGraph = GraphUtils.replaceNodes(trueGraph, dataSet.getVariables());

            // Standardize once (CPW does this internally too, but we want the
            // test to share the same data pre-processing as FASK for fairness).
            DataSet z = DataTransforms.standardizeData(dataSet);
            double[][] data = z.getDoubleData().transpose().toArray();
            List<Node> nodes = z.getVariables();

            // Build CPW forbidden knowledge from pairwise left-right statistic.
            Knowledge knowledge = buildPwForbiddenKnowledge(pwRule, nodes, data);

            // Run FCI with that knowledge.
            IndTestFisherZ fisherZ = new IndTestFisherZ(z, ALPHA);
            edu.cmu.tetrad.search.Fci fci = new edu.cmu.tetrad.search.Fci(fisherZ);
            fci.setKnowledge(knowledge);
            fci.setVerbose(false);

            Graph pag;
            try {
                pag = fci.search();
            } catch (Exception e) {
                System.err.println("CPW/FCI search failed on replicate " + r + ": " + e.getMessage());
                continue;
            }

            // Post-process: orient ambiguous edges using the pairwise statistic
            // (mirrors Cpw.runSearch edge-case logic).
            pag = applyPairwiseOrientation(pag, pwRule, nodes, data);

            OrientationCounts counts = evaluateOrientations(trueGraph, pag);
            totalCorrect     += counts.correct;
            totalMisoriented += counts.misoriented;
            totalMissing     += counts.missing;
            totalEligible    += counts.eligible;
            totalExtraEdges  += counts.extraEdges;
        }

        return new OrientationSummary(totalCorrect, totalMisoriented, totalMissing, totalEligible, totalExtraEdges);
    }

    // -----------------------------------------------------------------------
    // Core evaluation: compare output graph against true directed edges
    // -----------------------------------------------------------------------

    /**
     * For each singly-directed true edge Xi → Xj, determines whether the
     * output graph correctly oriented it, reversed it, or omitted it entirely.
     * Also counts extra edges in the output that have no counterpart in the
     * true graph's adjacencies.
     */
    private static OrientationCounts evaluateOrientations(Graph trueGraph, Graph outputGraph) {
        int correct     = 0;
        int misoriented = 0;
        int missing     = 0;
        int eligible    = 0;
        int extraEdges  = 0;

        List<Node> outputNodes = outputGraph.getNodes();

        for (Edge trueEdge : trueGraph.getEdges()) {
            Node xi = trueEdge.getNode1();
            Node xj = trueEdge.getNode2();

            boolean iToJ = trueGraph.isDirectedFromTo(xi, xj);
            boolean jToI = trueGraph.isDirectedFromTo(xj, xi);

            // Skip undirected true edges and two-cycles.
            if (iToJ == jToI) continue;

            eligible++;

            // Resolve nodes in the output graph by name (instance identity may differ).
            Node oxi = findNodeByName(outputNodes, xi.getName());
            Node oxj = findNodeByName(outputNodes, xj.getName());

            if (oxi == null || oxj == null) {
                missing++;
                continue;
            }

            boolean outIToJ = outputGraph.isDirectedFromTo(oxi, oxj);
            boolean outJToI = outputGraph.isDirectedFromTo(oxj, oxi);

            if (iToJ && outIToJ) {
                correct++;
            } else if (iToJ && outJToI) {
                misoriented++;
            } else if (jToI && outJToI) {
                correct++;
            } else if (jToI && outIToJ) {
                misoriented++;
            } else {
                // Edge is present but not fully directed, or absent entirely.
                missing++;
            }
        }

        // Count output edges whose endpoint pair has no adjacency in the true graph.
        for (Edge outEdge : outputGraph.getEdges()) {
            Node ox = outEdge.getNode1();
            Node oy = outEdge.getNode2();

            Node tx = findNodeByName(trueGraph.getNodes(), ox.getName());
            Node ty = findNodeByName(trueGraph.getNodes(), oy.getName());

            if (tx == null || ty == null || !trueGraph.isAdjacentTo(tx, ty)) {
                extraEdges++;
            }
        }

        return new OrientationCounts(correct, misoriented, missing, eligible, extraEdges);
    }

    // -----------------------------------------------------------------------
    // CPW pairwise orientation post-processing (mirrors Cpw.runSearch logic)
    // -----------------------------------------------------------------------

    private static Graph applyPairwiseOrientation(Graph pag, int pwRule, List<Node> nodes, double[][] data) {
        List<Node> pagNodes = pag.getNodes();

        for (int pass = 0; pass < 2; pass++) {
            for (Edge e : new ArrayList<>(pag.getEdges())) {
                Node x = e.getNode1();
                Node y = e.getNode2();

                Endpoint exy = pag.getEndpoint(x, y);
                Endpoint eyx = pag.getEndpoint(y, x);

                Node nx = findNodeByName(nodes, x.getName());
                Node ny = findNodeByName(nodes, y.getName());
                if (nx == null || ny == null) continue;

                int ix = nodes.indexOf(nx);
                int iy = nodes.indexOf(ny);
                if (ix < 0 || iy < 0) continue;

                double diff = Fask.leftRightDiff(data[ix], data[iy], pwRule);

                // tail-tail → fully direct
                if (exy == Endpoint.TAIL && eyx == Endpoint.TAIL) {
                    pag.removeEdge(x, y);
                    if (diff > 0) pag.addDirectedEdge(x, y);
                    else          pag.addDirectedEdge(y, x);
                }

                // x —o y  (tail at x, circle at y)
                if (exy == Endpoint.CIRCLE && eyx == Endpoint.TAIL) {
                    pag.removeEdge(x, y);
                    if (diff > 0) pag.addDirectedEdge(x, y);
                    else          pag.addUndirectedEdge(x, y);
                }

                // x o— y  (circle at x, tail at y)
                if (exy == Endpoint.TAIL && eyx == Endpoint.CIRCLE) {
                    pag.removeEdge(x, y);
                    if (diff < 0) pag.addDirectedEdge(y, x);
                    else          pag.addUndirectedEdge(x, y);
                }

                // x o-o y
                if (exy == Endpoint.CIRCLE && eyx == Endpoint.CIRCLE) {
                    if (diff > 0) pag.setEndpoint(x, y, Endpoint.ARROW);
                    else          pag.setEndpoint(y, x, Endpoint.ARROW);
                }

                // x o-> y  → x --> y  (causal sufficiency)
                if (exy == Endpoint.ARROW && eyx == Endpoint.CIRCLE) {
                    pag.setEndpoint(y, x, Endpoint.TAIL);
                }

                // x <-o y  → x <-- y  (causal sufficiency)
                if (exy == Endpoint.CIRCLE && eyx == Endpoint.ARROW) {
                    pag.setEndpoint(x, y, Endpoint.TAIL);
                }
            }
        }

        return pag;
    }

    // -----------------------------------------------------------------------
    // CPW forbidden-knowledge builder (mirrors Cpw.buildPwForbiddenKnowledge)
    // -----------------------------------------------------------------------

    private static Knowledge buildPwForbiddenKnowledge(int pwRule, List<Node> nodes, double[][] data) {
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                double diff = Fask.leftRightDiff(data[i], data[j], pwRule);

                if (diff > 0) {
                    knowledge.setForbidden(nodes.get(j).getName(), nodes.get(i).getName());
                } else {
                    knowledge.setForbidden(nodes.get(i).getName(), nodes.get(j).getName());
                }
            }
        }

        return knowledge;
    }

    // -----------------------------------------------------------------------
    // Graph / data generation
    // -----------------------------------------------------------------------

    private static Graph generateRandomAcyclicGraph() {
        return RandomGraph.randomGraph(
                NUM_MEASURES,
                0,
                AVG_DEGREE * NUM_MEASURES / 2,
                100, 100, 100,
                false
        );
    }

    private static DataSet simulateData(Graph graph) {
        Parameters parameters = new Parameters();
        parameters.set(Params.CUSTOM_NOISE_OPTION, 2);
        parameters.set(Params.CUSTOM_NOISE_EXPRESSION, "Gumbel(0, 1)");

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm, parameters);

        try {
            return im.simulateData(SAMPLE_SIZE, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Fask.LeftRight toLeftRight(int rule) {
        return switch (rule) {
            case 1 -> Fask.LeftRight.FASK1;
            case 2 -> Fask.LeftRight.FASK2;
            case 3 -> Fask.LeftRight.RSKEW;
            case 4 -> Fask.LeftRight.SKEW;
            case 5 -> Fask.LeftRight.TANH;
            default -> throw new IllegalArgumentException("Unknown rule: " + rule);
        };
    }

    private static Node findNodeByName(List<Node> nodes, String name) {
        for (Node n : nodes) {
            if (n.getName().equals(name)) return n;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Output
    // -----------------------------------------------------------------------

    private static void printRow(String algo, int rule, OrientationSummary s) {
        double errRate = s.eligible == 0 ? Double.NaN : (double) s.misoriented / s.eligible;
        System.out.printf("%-8s  rule=%-2d  %8d  %11d  %7d  %8d  %10d  %8s%n",
                algo, rule,
                s.correct, s.misoriented, s.missing, s.eligible, s.extraEdges,
                Double.isNaN(errRate) ? "N/A" : DF.format(errRate));
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    private record OrientationCounts(int correct, int misoriented, int missing, int eligible, int extraEdges) {}

    private record OrientationSummary(
            long correct,
            long misoriented,
            long missing,
            long eligible,
            long extraEdges
    ) {}
}