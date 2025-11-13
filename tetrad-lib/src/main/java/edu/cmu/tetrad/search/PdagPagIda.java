/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                            //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,         //
// and Richard Scheines.                                                      //
//                                                                            //
// This program is free software: you can redistribute it and/or modify       //
// it under the terms of the GNU General Public License as published by       //
// the Free Software Foundation, either version 3 of the License, or          //
// (at your option) any later version.                                        //
//                                                                            //
// This program is distributed in the hope that it will be useful,            //
// but WITHOUT ANY WARRANTY; without even the implied warranty of             //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              //
// GNU General Public License for more details.                               //
//                                                                            //
// You should have received a copy of the GNU General Public License          //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.     //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * Implements PDAG/PAG IDA with **incremental FCI propagation** after each local orientation at X.
 *
 * <p>Key differences vs your previous version:</p>
 * <ul>
 *   <li>After each single-edge refinement at X (replacing a circle at X by tail/arrow), we immediately run
 *       the final FCI orientation rules and prune if the result no longer refines the base PAG/PDAG.</li>
 *   <li>We re-compute the set of orientable (circle-at-X) edges after each propagation so the search shrinks as
 *       implications fire.</li>
 *   <li>Amenability is checked only at the leaf (when no more circles at X remain). For amenability we do **not**
 *       pass any forced-visibility nodes; we reserve that option only for RA set generation.</li>
 * </ul>
 */
public class PdagPagIda {

    /**
     * The CPDAG/PAG under analysis.
     */
    private final Graph graph;
    /**
     * Map node name -> column index in covariance matrix.
     */
    private final Map<String, Integer> nodeIndices;
    /**
     * Covariance matrix of the data.
     */
    private final ICovarianceMatrix allCovariances;

    /**
     * True if the input was a DAG.
     */
    private final boolean dag;
    /**
     * True if the input was a PAG.
     */
    private final boolean pag;
    private final List<Node> possibleCauses;

    /**
     * IDA mode.
     */
    private IDA_TYPE idaType = IDA_TYPE.OPTIMAL;

    /**
     * Optional path-length bound for O-set and PD-path routines (-1 = no limit).
     */
    private int maxLengthAdjustment = -1;

    /**
     * If true, when running RA on a refinement PAG we pass children(X) as force-visible (your prior behavior).
     */
    private boolean forceChildrenVisibleInRA = true;

    /**
     * Constructor: accepts DAG/CPDAG/PDAG or PAG and a continuous dataset.
     */
    public PdagPagIda(DataSet dataSet, Graph graph, List<Node> possibleCauses) {
        if (dataSet == null) throw new NullPointerException("Data set must not be null.");
        if (graph == null) throw new NullPointerException("Graph must not be null.");

        boolean isDag = graph.paths().isLegalDag();
        boolean isPdag = graph.paths().isLegalPdag();
        boolean isPag = graph.paths().isLegalPag();
        if (!(isDag || isPdag || isPag)) {
            throw new IllegalArgumentException("Expecting a DAG/CPDAG/PDAG or PAG.");
        }
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        this.graph = graph;
        this.possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        this.allCovariances = new CovarianceMatrix(dataSet);

        this.nodeIndices = new HashMap<>();
        List<Node> covVars = this.allCovariances.getVariables(); // dataset/covariance order
        for (int i = 0; i < covVars.size(); i++) {
            this.nodeIndices.put(covVars.get(i).getName(), i);
        }

        this.dag = isDag;
        this.pag = isPag;
    }

    // --------------------------- Utility: endpoint refinement ---------------------------

    private static boolean endpointRefines(Endpoint oldMark, Endpoint newMark) {
        return switch (oldMark) {
            case TAIL -> newMark == Endpoint.TAIL;
            case ARROW -> newMark == Endpoint.ARROW;
            case CIRCLE -> (newMark == Endpoint.CIRCLE || newMark == Endpoint.TAIL || newMark == Endpoint.ARROW);
            default -> false;
        };
    }

    /**
     * Determines whether one graph is a refinement of another.
     * A graph is considered a refinement of another if they share
     * the same nodes and the endpoints of edges in the finer graph
     * refine those in the coarser graph without alteration of the skeleton.
     *
     * @param coarse the coarse (less detailed) graph
     * @param fine the fine (more detailed) graph
     * @return true if the fine graph is a refinement of the coarse graph, false otherwise
     */
    public static boolean isRefinementOf(Graph coarse, Graph fine) {
        Set<Node> A = new HashSet<>(coarse.getNodes());
        Set<Node> B = new HashSet<>(fine.getNodes());
        if (!A.equals(B)) return false;
        for (Node u : A) {
            for (Node v : A) {
                if (u == v) continue;
                Edge ec = coarse.getEdge(u, v);
                Edge ef = fine.getEdge(u, v);
                if (ec == null && ef == null) continue;
                if (ec == null || ef == null) return false; // skeleton changed

                // cu = endpoint AT u on edge (u—v) in coarse; fu = endpoint AT u in fine
                Endpoint cu = coarse.getEndpoint(v, u), fu = fine.getEndpoint(v, u);
                // cv = endpoint AT v on edge (u—v) in coarse; fv = endpoint AT v in fine
                Endpoint cv = coarse.getEndpoint(u, v), fv = fine.getEndpoint(u, v);

//                Endpoint cu = coarse.getEndpoint(v, u), cv = coarse.getEndpoint(u, v);
//                Endpoint fu = fine.getEndpoint(v, u)  , fv = fine.getEndpoint(u, v);
                if (!endpointRefines(cu, fu)) return false;
                if (!endpointRefines(cv, fv)) return false;
            }
        }
        return true;
    }

    // --------------------------- Public API ---------------------------

    /**
     * Calculates the total effects of one variable on another.
     *
     * @param x the source node for which total effects are to be computed
     * @param y the target node influenced by the source node
     * @return a LinkedList containing the total effects of x on y
     */
    public LinkedList<Double> getTotalEffects(Node x, Node y) {
        if (pag) return getTotalEffectsPagInternal(x, y);
        return getTotalEffectsPdagInternal(x, y);
    }

    /**
     * Calculates the absolute total effects of one variable on another.
     *
     * @param x the source node for which total effects are to be computed
     * @param y the target node influenced by the source node
     * @return a LinkedList containing the absolute total effects of x on y
     */
    public LinkedList<Double> getAbsTotalEffects(Node x, Node y) {
        LinkedList<Double> eff = getTotalEffects(x, y);
        LinkedList<Double> out = new LinkedList<>();
        for (double d : eff) out.add(Math.abs(d));
        Collections.sort(out);
        return out;
    }

    /**
     * Sets the IDA type for the instance.
     *
     * @param idaType the IDA type to be set. It must not be null.
     *                Possible values are defined in the {@code IDA_TYPE} enum, which includes
     *                options such as {@code REGULAR} and {@code OPTIMAL}.
     * @throws NullPointerException if the provided idaType is null.
     */
    public void setIdaType(IDA_TYPE idaType) {
        if (idaType == null) throw new NullPointerException("IDA type must not be null.");
        this.idaType = idaType;
    }

    /**
     * Sets the maximum length adjustment value for internal computations.
     * This parameter is used to limit the search or computational adjustments
     * within the defined maximum length.
     *
     * @param maxLengthAdjustment the maximum adjustment value to set
     */
    public void setMaxLengthAdjustment(int maxLengthAdjustment) {
        this.maxLengthAdjustment = maxLengthAdjustment;
    }

    /**
     * Sets whether the children nodes should be forcibly marked as visible
     * during refinement adjustment (RA) processes.
     *
     * @param b a boolean value indicating whether to force the visibility
     *          of children nodes in the refinement adjustment process.
     *          If true, the visibility of children nodes is forced.
     */
    public void setForceChildrenVisibleInRA(boolean b) {
        this.forceChildrenVisibleInRA = b;
    }

    // --------------------------- PDAG / CPDAG branch ---------------------------

    private LinkedList<Double> getTotalEffectsPdagInternal(Node x, Node y) {
        List<Node> parents = this.graph.getParents(x);
        List<Node> children = this.graph.getChildren(x);
        List<Node> siblings = new ArrayList<>(this.graph.getAdjacentNodes(x));
        siblings.removeAll(parents);
        siblings.removeAll(children);

        int size = siblings.size();
        SublistGenerator gen = new SublistGenerator(size, size);
        int[] choice;
        LinkedList<Double> totalEffects = new LinkedList<>();

        CHOICE:
        while ((choice = gen.next()) != null) {
            try {
                List<Node> siblingsChoice = GraphUtils.asList(choice, siblings);
                if (siblingsChoice.size() > 1) {
                    ChoiceGenerator gen2 = new ChoiceGenerator(siblingsChoice.size(), 2);
                    int[] c2;
                    while ((c2 = gen2.next()) != null) {
                        List<Node> adj = GraphUtils.asList(c2, siblingsChoice);
                        if (this.graph.isAdjacentTo(adj.get(0), adj.get(1))) continue CHOICE;
                    }
                }
                if (!siblingsChoice.isEmpty()) {
                    for (Node p : parents) {
                        for (Node s : siblingsChoice) {
                            if (this.graph.isAdjacentTo(p, s)) continue CHOICE;
                        }
                    }
                }

                double beta;
                if (idaType == IDA_TYPE.REGULAR) {
                    Set<Node> regs = new LinkedHashSet<>();
                    regs.add(x);
                    regs.addAll(parents);
                    regs.addAll(siblingsChoice);
                    List<Node> regressors = new ArrayList<>(regs);
                    beta = regressors.contains(y) ? 0.0 : getBeta(regressors, x, y);
                } else {
                    Graph gPrime = new EdgeListGraph(this.graph);
                    for (Node s : siblings) {
                        if (!gPrime.isAdjacentTo(x, s)) continue;
                        gPrime.removeEdge(x, s);
                        if (siblingsChoice.contains(s)) gPrime.addDirectedEdge(s, x); // s -> X
                        else gPrime.addDirectedEdge(x, s);                            // X -> s
                    }
                    MeekRules rules = new MeekRules();
                    rules.setRevertToUnshieldedColliders(false);
                    rules.orientImplied(gPrime);

                    Set<Node> oSet;
                    try {
                        if (dag) oSet = OSet.oSetDag(gPrime, x, y);
                        else oSet = OSet.oSetCpdag(gPrime, x, y, maxLengthAdjustment);
                    } catch (Exception e) {
                        totalEffects.add(0.0);
                        continue;
                    }
                    if (oSet == null) {
                        if (!gPrime.paths().isGraphAmenable(x, y, "PDAG", -1, Set.of())) {
                            throw new IllegalArgumentException("PDAG not amenable for " + x + " ~~> " + y);
                        } else {
                            totalEffects.add(0.0);
                            continue;
                        }
                    }
                    Set<Node> regs = new LinkedHashSet<>();
                    regs.add(x);
                    regs.addAll(oSet);
                    regs.remove(y);
                    beta = getBeta(new ArrayList<>(regs), x, y);
                }
                totalEffects.add(beta);
            } catch (Exception e) {
                TetradLogger.getInstance().log(e.getMessage());
            }
        }
        Collections.sort(totalEffects);
        return totalEffects;
    }

    // --------------------------- PAG branch with incremental FCI per step ---------------------------

    private LinkedList<Double> getTotalEffectsPagInternal(Node x, Node y) {
        LinkedList<Double> totalEffects = new LinkedList<>();
        Graph base = this.graph;

        Set<List<Node>> pdPaths = base.paths().potentiallyDirectedPaths(x, y, maxLengthAdjustment);
        if (pdPaths == null || pdPaths.isEmpty()) {
            totalEffects.add(0.0);
            return totalEffects;
        }

        dfsPagAroundX(base, x, y, totalEffects);
        if (totalEffects.isEmpty()) totalEffects.add(0.0); // optional
        Collections.sort(totalEffects);
        return totalEffects;
    }

    /**
     * Depth-first search over local orientations at X with FCI propagation after each edge orientation.
     */
    private void dfsPagAroundX(Graph current, Node x, Node y, List<Double> out) {
        // Collect the edges incident to X whose endpoint at X is still a circle.
        List<Edge> orientable = new ArrayList<>();
        for (Edge e : current.getEdges(x)) {
            Node o = e.getDistalNode(x);
            if (current.getEndpoint(o, x) == Endpoint.CIRCLE) orientable.add(e);
        }

        // Leaf: no more circles at X → evaluate this refinement.
        if (orientable.isEmpty()) {
            evaluatePagLeaf(current, x, y, out);
            return;
        }

        // Pick one circle edge and branch on the three fully-oriented possibilities at X.
        Edge e = orientable.get(0);
        Node other = e.getDistalNode(x);
        Endpoint baseAtX = this.graph.getEndpoint(other, x);
        Endpoint baseAtO = this.graph.getEndpoint(x, other);

        // Try X -> other (TAIL at X, ARROW at other)
        tryPagStep(current, x, other, baseAtX, baseAtO, Endpoint.TAIL, Endpoint.ARROW, y, out);
        // Try X <- other (ARROW at X, TAIL at other)
        tryPagStep(current, x, other, baseAtX, baseAtO, Endpoint.ARROW, Endpoint.TAIL, y, out);
        // Try X <-> other (ARROW at X, ARROW at other)
        tryPagStep(current, x, other, baseAtX, baseAtO, Endpoint.ARROW, Endpoint.ARROW, y, out);
    }

    /**
     * One branching move: refine a single X–other edge, run FCI, prune if needed, then recurse.
     */
    private void tryPagStep(Graph current,
                            Node x,
                            Node other,
                            Endpoint oldAtX,
                            Endpoint oldAtOther,
                            Endpoint newAtX,
                            Endpoint newAtOther,
                            Node y,
                            List<Double> out) {
        if (!endpointRefines(oldAtX, newAtX)) return;
        if (!endpointRefines(oldAtOther, newAtOther)) return;

        Graph gNext = new EdgeListGraph(current);
        Edge old = gNext.getEdge(x, other);
        if (old != null) gNext.removeEdge(old);
        if ((newAtX == Endpoint.TAIL) && newAtOther == Endpoint.ARROW) {
            gNext.addDirectedEdge(x, other); // X -> other
        } else if (newAtX == Endpoint.ARROW && newAtOther == Endpoint.TAIL) {
            gNext.addDirectedEdge(other, x); // other -> X
        } else if (newAtX == Endpoint.ARROW && newAtOther == Endpoint.ARROW) {
            gNext.addBidirectedEdge(x, other); // X <-> other
        } else {
            return; // only fully-oriented choices at X
        }

        // Run final FCI after this single local choice.
        FciOrient finalFci = new FciOrient(R0R4StrategyTestBased.defaultConfiguration(this.graph, new Knowledge()));
        finalFci.finalOrientation(gNext);

        // Must remain a refinement of the base graph.
        if (!isRefinementOf(this.graph, gNext)) return;

        // Early PD-path prune: if X ↛ Y is impossible now, record 0 (or prune).
        Set<List<Node>> pd = gNext.paths().potentiallyDirectedPaths(x, y, maxLengthAdjustment);
        if (pd == null || pd.isEmpty()) {
            out.add(0.0);
            return;
        }

        // Recurse with the updated graph; new orientables will be gathered there.
        dfsPagAroundX(gNext, x, y, out);
    }

    /**
     * Leaf evaluation for a PAG refinement: check amenability, then run RA to get one adjustment set and a beta.
     */
    private void evaluatePagLeaf(Graph gPrime, Node x, Node y, List<Double> out) {
        Set<List<Node>> pdPaths = gPrime.paths().potentiallyDirectedPaths(x, y, maxLengthAdjustment);
        if (pdPaths == null || pdPaths.isEmpty()) {
            out.add(0.0);
            return;
        }

        if (!isRefinementOf(this.graph, gPrime)) return;

        // IMPORTANT: no forced visibility in the amenability *check*
        if (!gPrime.paths().isGraphAmenable(x, y, "PAG", maxLengthAdjustment, Set.of())) return;

        collectEffectsFromRefinementPag(
                gPrime, x, y, out,
                forceChildrenVisibleInRA ? new HashSet<>(gPrime.getChildren(x)) : Set.of()
        );
    }

    /**
     * Collects effects from a refined PAG (Partial Ancestral Graph) based on the specified parameters.
     * The method computes adjustment sets using Recursive Adjustment (RA) and calculates
     * total effects, which are appended to the provided output list.
     *
     * @param gPrime           A refined PAG (Partial Ancestral Graph) used for effect calculations.
     * @param x                The source variable node.
     * @param y                The target variable node.
     * @param out              A list to collect the computed effect values.
     * @param forceVisibility  A set of nodes to be forcefully marked as visible during the refinement adjustment process.
     */
    private void collectEffectsFromRefinementPag(Graph gPrime,
                                                 Node x,
                                                 Node y,
                                                 List<Double> out,
                                                 Set<Node> forceVisibility) {
        try {
            RecursiveAdjustment ra = new RecursiveAdjustment(gPrime);
            int maxNumSets = 1;                        // one minimal set
            int maxRadius = gPrime.getNodes().size();  // full radius
            int nearWhichEndpoint = 3;                 // 1=X,2=Y,3=both
            boolean avoidAmenable = true;              // RA mode
            Set<Node> notFollowed = null;
            Set<Node> containing = null;

            List<Set<Node>> adjSets = ra.adjustmentSets(
                    x, y,
                    "PAG",
                    maxNumSets,
                    maxRadius,
                    nearWhichEndpoint,
                    maxLengthAdjustment,
                    RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST,
                    avoidAmenable,
                    notFollowed,
                    containing,
                    forceVisibility);

            if (adjSets.isEmpty()) return; // identified but we failed to find a set

            Set<Node> Z = adjSets.get(0);
            Set<Node> regs = new LinkedHashSet<>();
            regs.add(x);
            regs.addAll(Z);
            regs.remove(y);

            double beta = getBeta(new ArrayList<>(regs), x, y);
            out.add(beta);
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
        }
    }

    // --------------------------- Helpers ---------------------------

    /**
     * Computes the minimum distance between a true causal effect and a set of estimated effects.
     * If the true effect lies within the range of the estimated effects, the distance is returned as 0.
     * Otherwise, the distance is the smallest absolute difference between the true effect and the closest extreme of the range of estimated effects.
     *
     * @param effects a LinkedList of estimated causal effects
     * @param trueEffect the true causal effect to compute the distance to
     * @return the minimum distance between the true causal effect and the set of estimated effects
     */
    public double distance(LinkedList<Double> effects, double trueEffect) {
        effects = new LinkedList<>(effects);
        if (effects.isEmpty()) return trueEffect;
        if (effects.size() == 1) return abs(effects.get(0) - trueEffect);
        Collections.sort(effects);
        double minv = effects.getFirst();
        double maxv = effects.getLast();
        if (trueEffect >= minv && trueEffect <= maxv) return 0.0;
        return min(abs(trueEffect - minv), abs(trueEffect - maxv));
    }

    /**
     * Computes the regression coefficient (beta) that represents the strength
     * of the relationship between the parent and the child node, given a set of regressors.
     * The method ensures that the parent node is included in the regressors list
     * and calculates the beta using covariance matrices.
     *
     * @param regressors the list of nodes used as regressors, which must include the parent node
     * @param parent the parent node whose influence on the child node is being calculated
     * @param child the child node influenced by the parent
     * @return the regression coefficient (beta) for the parent node with respect to the child node
     * @throws IllegalArgumentException if the regressors list does not contain the parent node
     * @throws RuntimeException if a singularity occurs during matrix inversion
     */
    private double getBeta(List<Node> regressors, Node parent, Node child) {
        if (!regressors.contains(parent))
            throw new IllegalArgumentException("Regressors must contain parent node.");
        try {
            int xIndex = regressors.indexOf(parent);
            int yIndex = this.nodeIndices.get(child.getName());
            int[] xIndices = new int[regressors.size()];
            for (int i = 0; i < regressors.size(); i++) xIndices[i] = this.nodeIndices.get(regressors.get(i).getName());
            Matrix rX = this.allCovariances.getSelection(xIndices, xIndices);
            Matrix rY = this.allCovariances.getSelection(xIndices, new int[]{yIndex});
            Matrix bStar = rX.inverse().times(rY);
            return (bStar != null) ? bStar.get(xIndex, 0) : 0.0;
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity in regression " + LogUtilsSearch.getScoreFact(child, regressors));
        }
    }


    /**
     * Calculates the minimum total effects of all potential cause nodes on the specified target node y.
     * For each possible cause node x, the total effect of x on y is computed and the minimum effect is recorded.
     * Only nodes present in the graph are processed.
     *
     * @param y the target node for which the minimum total effects from possible cause nodes are to be calculated
     * @return a map where each key is a possible cause node and each value is the minimum total effect of that
     *         node on y
     */
    public Map<Node, Double> calculateMinimumTotalEffectsOnY(Node y) {
        SortedMap<Node, Double> minEffects = new TreeMap<>();

        for (Node x : this.possibleCauses) {
            if (!(this.graph.containsNode(x) && this.graph.containsNode(y))) continue;
            LinkedList<Double> effects = getTotalEffects(x, y);
            minEffects.put(x, effects.getFirst());
        }

        return minEffects;
    }

    // --------------------------- Types ---------------------------

    /**
     * The {@code IDA_TYPE} enum represents the different algorithmic approaches
     * available in IDA (Intervention Distribution Adjustment) processes.
     *
     * It specifies the method to be used during computations, primarily affecting
     * how causal effects and related measures are calculated. This enum is used
     * to configure and define the operational strategy within the context of
     * graph-based causal inference and adjustment.
     */
    public enum IDA_TYPE {

        /**
         * Represents the standard algorithmic approach in the IDA (Intervention Distribution
         * Adjustment) process.
         *
         * The {@code REGULAR} value indicates the use of the default method for performing
         * computations in graph-based causal inference and adjustment. It prescribes the
         * baseline approach for determining causal effects and other related measures.
         */
        REGULAR,

        /**
         * Represents the optimal algorithmic approach in the IDA (Intervention Distribution
         * Adjustment) process.
         *
         * The {@code OPTIMAL} value indicates a computational method designed to achieve
         * the best possible outcome in terms of efficiency or accuracy during the calculation
         * of causal effects and related measures within graph-based causal inference and
         * adjustment.
         */
        OPTIMAL}

    /**
     * Convenience container used elsewhere in Tetrad.
     */
    public static class NodeEffects {
        private List<Node> nodes;
        private LinkedList<Double> effects;

        /**
         * Constructs a NodeEffects object with specified nodes and their corresponding effects.
         *
         * @param nodes   the list of nodes to be included in this container
         * @param effects the list of effect values corresponding to the nodes
         */
        NodeEffects(List<Node> nodes, LinkedList<Double> effects) {
            this.nodes = nodes;
            this.effects = effects;
        }

        /**
         * Retrieves the list of nodes contained in this object.
         *
         * @return the list of nodes
         */
        public List<Node> getNodes() {
            return nodes;
        }

        /**
         * Sets the list of nodes in this object.
         *
         * @param nodes the list of nodes to be set
         */
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }

        /**
         * Retrieves the list of effect values associated with the nodes.
         *
         * @return a LinkedList of Double values representing the effects
         */
        public LinkedList<Double> getEffects() {
            return effects;
        }

        /**
         * Sets the list of effect values associated with the nodes.
         *
         * @param effects the LinkedList of Double values representing the effects to be set
         */
        public void setEffects(LinkedList<Double> effects) {
            this.effects = effects;
        }

        /**
         * Converts the object to a readable string representation by iterating through
         * the nodes and their corresponding effects, appending them in the format
         * "node=effect" with a space delimiter.
         *
         * @return a string representation where each node and its corresponding effect
         *         are paired and separated by a space
         */
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < nodes.size(); i++)
                b.append(nodes.get(i)).append("=").append(effects.get(i)).append(" ");
            return b.toString();
        }
    }
}
