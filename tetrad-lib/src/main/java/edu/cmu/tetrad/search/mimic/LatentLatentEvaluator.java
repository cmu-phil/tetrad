package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates the adequacy of latent-latent connections in an estimated MIMIC-style graph against
 * a true graph.
 * <p>
 * The procedure is:
 * <ol>
 *     <li>Identify latent nodes in the true and estimated graphs.</li>
 *     <li>For each latent, construct a signature consisting of its measured parents and measured children.</li>
 *     <li>Match estimated latents to true latents using maximum similarity of signatures.</li>
 *     <li>Compare latent-latent adjacencies in the matched latent graphs.</li>
 * </ol>
 * <p>
 * This class is intentionally focused on adjacency adequacy of latent-latent structure. It does
 * not attempt to evaluate measured-measured or measured-latent edge recovery.
 */
public final class LatentLatentEvaluator {

    /**
     * Constructs a new evaluator.
     */
    public LatentLatentEvaluator() {}

    /**
     * Evaluates the estimated graph against the true graph and returns a report object.
     *
     * @param trueGraph the true graph
     * @param estGraph  the estimated graph
     * @return the evaluation report
     */
    public Report evaluate(Graph trueGraph, Graph estGraph) {
        if (trueGraph == null) {
            throw new NullPointerException("True graph must not be null.");
        }

        if (estGraph == null) {
            throw new NullPointerException("Estimated graph must not be null.");
        }

        List<Node> trueLatents = getLatents(trueGraph);
        List<Node> estLatents = getLatents(estGraph);

        Map<Node, LatentSignature> trueSignatures = buildSignatures(trueGraph, trueLatents);
        Map<Node, LatentSignature> estSignatures = buildSignatures(estGraph, estLatents);

        Map<Node, Node> estToTrue = matchLatents(trueLatents, estLatents, trueSignatures, estSignatures);

        Set<LatentPair> truePairs = actualLatentPairs(trueGraph, trueLatents);
        Set<LatentPair> estPairsMapped = mappedEstimatedPairs(estGraph, estLatents, estToTrue);

        Set<LatentPair> trueOnly = new LinkedHashSet<>(truePairs);
        trueOnly.removeAll(estPairsMapped);

        Set<LatentPair> estOnly = new LinkedHashSet<>(estPairsMapped);
        estOnly.removeAll(truePairs);

        Set<LatentPair> recovered = new LinkedHashSet<>(truePairs);
        recovered.retainAll(estPairsMapped);

        int tp = recovered.size();
        int fn = trueOnly.size();
        int fp = estOnly.size();

        double precision = tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);

        return new Report(
                trueLatents,
                estLatents,
                trueSignatures,
                estSignatures,
                estToTrue,
                truePairs,
                estPairsMapped,
                recovered,
                trueOnly,
                estOnly,
                tp,
                fn,
                fp,
                precision,
                recall
        );
    }

    /**
     * Returns all unordered latent-latent adjacent pairs actually present in the graph.
     *
     * @param graph the graph
     * @param latents the latent nodes
     * @return latent adjacency pairs
     */
    private Set<LatentPair> actualLatentPairs(Graph graph, List<Node> latents) {
        Set<LatentPair> pairs = new LinkedHashSet<>();
        Set<Node> latentSet = new LinkedHashSet<>(latents);

        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (!latentSet.contains(a) || !latentSet.contains(b)) {
                continue;
            }

            pairs.add(new LatentPair(a.getName(), b.getName()));
        }

        return pairs;
    }

    /**
     * Returns all latent nodes in the graph, sorted by name.
     *
     * @param graph the graph
     * @return the latent nodes
     */
    private List<Node> getLatents(Graph graph) {
        List<Node> latents = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        latents.sort(Comparator.comparing(Node::getName));
        return latents;
    }

    /**
     * Builds measured-parent and measured-child signatures for the given latents.
     *
     * @param graph   the graph
     * @param latents the latent nodes
     * @return a map from latent to signature
     */
    private Map<Node, LatentSignature> buildSignatures(Graph graph, List<Node> latents) {
        Map<Node, LatentSignature> signatures = new LinkedHashMap<>();

        for (Node latent : latents) {
            Set<String> measuredParents = new LinkedHashSet<>();
            Set<String> measuredChildren = new LinkedHashSet<>();

            for (Node parent : graph.getParents(latent)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    measuredParents.add(parent.getName());
                }
            }

            for (Node child : graph.getChildren(latent)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    measuredChildren.add(child.getName());
                }
            }

            signatures.put(latent, new LatentSignature(measuredParents, measuredChildren));
        }

        return signatures;
    }

    /**
     * Matches estimated latents to true latents greedily by best similarity score.
     *
     * @param trueLatents     true latents
     * @param estLatents      estimated latents
     * @param trueSignatures  true signatures
     * @param estSignatures   estimated signatures
     * @return map from estimated latent to matched true latent
     */
    private Map<Node, Node> matchLatents(List<Node> trueLatents,
                                         List<Node> estLatents,
                                         Map<Node, LatentSignature> trueSignatures,
                                         Map<Node, LatentSignature> estSignatures) {
        List<MatchCandidate> candidates = new ArrayList<>();

        for (Node est : estLatents) {
            for (Node truth : trueLatents) {
                double score = similarity(estSignatures.get(est), trueSignatures.get(truth));
                candidates.add(new MatchCandidate(est, truth, score));
            }
        }

        candidates.sort(Comparator
                .comparingDouble(MatchCandidate::score).reversed()
                .thenComparing(c -> c.est().getName())
                .thenComparing(c -> c.truth().getName()));

        Map<Node, Node> estToTrue = new LinkedHashMap<>();
        Set<Node> usedEst = new LinkedHashSet<>();
        Set<Node> usedTrue = new LinkedHashSet<>();

        for (MatchCandidate candidate : candidates) {
            if (usedEst.contains(candidate.est())) {
                continue;
            }

            if (usedTrue.contains(candidate.truth())) {
                continue;
            }

            estToTrue.put(candidate.est(), candidate.truth());
            usedEst.add(candidate.est());
            usedTrue.add(candidate.truth());
        }

        return estToTrue;
    }

    /**
     * Similarity between two latent signatures.
     *
     * @param a first signature
     * @param b second signature
     * @return similarity score
     */
    private double similarity(LatentSignature a, LatentSignature b) {
        int parentOverlap = overlap(a.measuredParents(), b.measuredParents());
        int childOverlap = overlap(a.measuredChildren(), b.measuredChildren());

        int parentUnion = unionSize(a.measuredParents(), b.measuredParents());
        int childUnion = unionSize(a.measuredChildren(), b.measuredChildren());

        double parentJaccard = parentUnion == 0 ? 1.0 : (double) parentOverlap / parentUnion;
        double childJaccard = childUnion == 0 ? 1.0 : (double) childOverlap / childUnion;

        return parentOverlap + childOverlap + 0.5 * parentJaccard + 0.5 * childJaccard;
    }

    /**
     * Returns the number of overlapping strings in two sets.
     *
     * @param a first set
     * @param b second set
     * @return overlap size
     */
    private int overlap(Set<String> a, Set<String> b) {
        int count = 0;

        for (String s : a) {
            if (b.contains(s)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns the size of the union of two sets.
     *
     * @param a first set
     * @param b second set
     * @return union size
     */
    private int unionSize(Set<String> a, Set<String> b) {
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return union.size();
    }

    /**
     * Returns all unordered latent-latent adjacent pairs in the graph.
     *
     * @param latents the latents from the true graph
     * @return latent pairs
     */
    private Set<LatentPair> latentPairs(List<Node> latents) {
        Set<LatentPair> pairs = new LinkedHashSet<>();

        for (int i = 0; i < latents.size(); i++) {
            for (int j = i + 1; j < latents.size(); j++) {
                pairs.add(new LatentPair(latents.get(i).getName(), latents.get(j).getName()));
            }
        }

        return pairs;
    }

    /**
     * Returns the latent-latent adjacency pairs from the estimated graph after mapping estimated
     * latents to true latent names.
     *
     * @param estGraph   estimated graph
     * @param estLatents estimated latents
     * @param estToTrue  estimated-to-true matching
     * @return mapped latent adjacency pairs
     */
    private Set<LatentPair> mappedEstimatedPairs(Graph estGraph,
                                                 List<Node> estLatents,
                                                 Map<Node, Node> estToTrue) {
        Set<LatentPair> pairs = new LinkedHashSet<>();
        Set<Node> estLatentSet = new LinkedHashSet<>(estLatents);

        for (Edge edge : estGraph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (!estLatentSet.contains(a) || !estLatentSet.contains(b)) {
                continue;
            }

            Node trueA = estToTrue.get(a);
            Node trueB = estToTrue.get(b);

            if (trueA == null || trueB == null || trueA == trueB) {
                continue;
            }

            pairs.add(new LatentPair(trueA.getName(), trueB.getName()));
        }

        return pairs;
    }

    /**
     * Result object for a latent-latent adequacy evaluation.
     */
    public static final class Report {
        private final List<Node> trueLatents;
        private final List<Node> estLatents;
        private final Map<Node, LatentSignature> trueSignatures;
        private final Map<Node, LatentSignature> estSignatures;
        private final Map<Node, Node> estToTrue;
        private final Set<LatentPair> allTruePairs;
        private final Set<LatentPair> allEstimatedPairsMapped;
        private final Set<LatentPair> recoveredPairs;
        private final Set<LatentPair> missingPairs;
        private final Set<LatentPair> extraPairs;
        private final int truePositives;
        private final int falseNegatives;
        private final int falsePositives;
        private final double precision;
        private final double recall;

        private Report(List<Node> trueLatents,
                       List<Node> estLatents,
                       Map<Node, LatentSignature> trueSignatures,
                       Map<Node, LatentSignature> estSignatures,
                       Map<Node, Node> estToTrue,
                       Set<LatentPair> allTruePairs,
                       Set<LatentPair> allEstimatedPairsMapped,
                       Set<LatentPair> recoveredPairs,
                       Set<LatentPair> missingPairs,
                       Set<LatentPair> extraPairs,
                       int truePositives,
                       int falseNegatives,
                       int falsePositives,
                       double precision,
                       double recall) {
            this.trueLatents = Collections.unmodifiableList(new ArrayList<>(trueLatents));
            this.estLatents = Collections.unmodifiableList(new ArrayList<>(estLatents));
            this.trueSignatures = Collections.unmodifiableMap(new LinkedHashMap<>(trueSignatures));
            this.estSignatures = Collections.unmodifiableMap(new LinkedHashMap<>(estSignatures));
            this.estToTrue = Collections.unmodifiableMap(new LinkedHashMap<>(estToTrue));
            this.allTruePairs = Collections.unmodifiableSet(new LinkedHashSet<>(allTruePairs));
            this.allEstimatedPairsMapped = Collections.unmodifiableSet(new LinkedHashSet<>(allEstimatedPairsMapped));
            this.recoveredPairs = Collections.unmodifiableSet(new LinkedHashSet<>(recoveredPairs));
            this.missingPairs = Collections.unmodifiableSet(new LinkedHashSet<>(missingPairs));
            this.extraPairs = Collections.unmodifiableSet(new LinkedHashSet<>(extraPairs));
            this.truePositives = truePositives;
            this.falseNegatives = falseNegatives;
            this.falsePositives = falsePositives;
            this.precision = precision;
            this.recall = recall;
        }

        /**
         * Returns the estimated-to-true latent matching.
         *
         * @return the matching
         */
        public Map<Node, Node> getEstimatedToTrueMatching() {
            return this.estToTrue;
        }

        /**
         * Returns the recovered latent-latent adjacency pairs.
         *
         * @return recovered pairs
         */
        public Set<LatentPair> getRecoveredPairs() {
            return this.recoveredPairs;
        }

        /**
         * Returns the true pairs missing from the estimate.
         *
         * @return missing pairs
         */
        public Set<LatentPair> getMissingPairs() {
            return this.missingPairs;
        }

        /**
         * Returns the extra estimated pairs not present in the truth.
         *
         * @return extra pairs
         */
        public Set<LatentPair> getExtraPairs() {
            return this.extraPairs;
        }

        /**
         * Returns the precision.
         *
         * @return precision
         */
        public double getPrecision() {
            return this.precision;
        }

        /**
         * Returns the recall.
         *
         * @return recall
         */
        public double getRecall() {
            return this.recall;
        }

        /**
         * Returns the number of true positives.
         *
         * @return true positives
         */
        public int getTruePositives() {
            return this.truePositives;
        }

        /**
         * Returns the number of false negatives.
         *
         * @return false negatives
         */
        public int getFalseNegatives() {
            return this.falseNegatives;
        }

        /**
         * Returns the number of false positives.
         *
         * @return false positives
         */
        public int getFalsePositives() {
            return this.falsePositives;
        }

        /**
         * Returns a readable text report.
         *
         * @return the report as a string
         */
        public String toDisplayString() {
            DecimalFormat nf = new DecimalFormat("0.000");
            StringBuilder sb = new StringBuilder();

            sb.append("\nLatent-Latent Adequacy Report\n");
            sb.append("============================\n");

            sb.append("\nTrue Latents\n");
            sb.append("------------\n");
            for (Node latent : trueLatents) {
                sb.append(latent.getName())
                        .append("  ")
                        .append(trueSignatures.get(latent))
                        .append('\n');
            }

            sb.append("\nEstimated Latents\n");
            sb.append("-----------------\n");
            for (Node latent : estLatents) {
                sb.append(latent.getName())
                        .append("  ")
                        .append(estSignatures.get(latent))
                        .append('\n');
            }

            sb.append("\nEstimated -> True Matching\n");
            sb.append("-------------------------\n");
            for (Map.Entry<Node, Node> entry : estToTrue.entrySet()) {
                Node est = entry.getKey();
                Node truth = entry.getValue();
                sb.append(est.getName()).append(" -> ").append(truth.getName()).append('\n');
            }

            sb.append("\nRecovered Latent-Latent Adjacencies\n");
            sb.append("----------------------------------\n");
            appendPairsOrNone(sb, recoveredPairs);

            sb.append("\nMissing True Latent-Latent Adjacencies\n");
            sb.append("-------------------------------------\n");
            appendPairsOrNone(sb, missingPairs);

            sb.append("\nExtra Estimated Latent-Latent Adjacencies\n");
            sb.append("----------------------------------------\n");
            appendPairsOrNone(sb, extraPairs);

            sb.append("\nSummary\n");
            sb.append("-------\n");
            sb.append("TP = ").append(truePositives).append('\n');
            sb.append("FN = ").append(falseNegatives).append('\n');
            sb.append("FP = ").append(falsePositives).append('\n');
            sb.append("Precision = ").append(nf.format(precision)).append('\n');
            sb.append("Recall    = ").append(nf.format(recall)).append('\n');

            return sb.toString();
        }

        @Override
        public String toString() {
            return toDisplayString();
        }

        private void appendPairsOrNone(StringBuilder sb, Set<LatentPair> pairs) {
            if (pairs.isEmpty()) {
                sb.append("(none)\n");
                return;
            }

            List<LatentPair> sorted = new ArrayList<>(pairs);
            sorted.sort(Comparator.naturalOrder());

            for (LatentPair pair : sorted) {
                sb.append(pair).append('\n');
            }
        }
    }

    /**
     * Signature of a latent node based on its measured parents and measured children.
     *
     * @param measuredParents measured parents
     * @param measuredChildren measured children
     */
    public record LatentSignature(Set<String> measuredParents, Set<String> measuredChildren) {
        @Override
        public String toString() {
            return "[parents=" + measuredParents + ", children=" + measuredChildren + "]";
        }
    }

    /**
     * Unordered pair of latent names representing latent-latent adjacency.
     *
     * @param a first name
     * @param b second name
     */
    public record LatentPair(String a, String b) implements Comparable<LatentPair> {

        /**
         * Constructs a LatentPair with two distinct, non-null latent names.
         * Ensures that the pair is always stored in a consistent order,
         * with the lexicographically smaller name as the first element.
         *
         * @param a the first latent name, must not be null
         * @param b the second latent name, must not be null and must
         *          not be equal to the first name
         * @throws NullPointerException if either latent name is null
         * @throws IllegalArgumentException if the two latent names are identical
         */
        public LatentPair {
            if (a == null || b == null) {
                throw new NullPointerException("Latent names must not be null.");
            }

            if (a.equals(b)) {
                throw new IllegalArgumentException("Latent pair members must be distinct.");
            }

            if (a.compareTo(b) > 0) {
                String temp = a;
                a = b;
                b = temp;
            }
        }

        @Override
        public int compareTo(LatentPair other) {
            int c = this.a.compareTo(other.a);
            if (c != 0) {
                return c;
            }
            return this.b.compareTo(other.b);
        }

        @Override
        public String toString() {
            return a + " -- " + b;
        }
    }

    /**
     * Candidate match between an estimated latent and a true latent.
     *
     * @param est   estimated latent
     * @param truth true latent
     * @param score similarity score
     */
    private record MatchCandidate(Node est, Node truth, double score) {
    }
}