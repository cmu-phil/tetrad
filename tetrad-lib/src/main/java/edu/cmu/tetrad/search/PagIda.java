/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
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
 * Implements the IDA algorithm. The reference is here:
 * <p>
 * Maathuis, Marloes H., Markus Kalisch, and Peter BÃ¼hlmann. "Estimating high-dimensional intervention effects from
 * observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
 * <p>
 * The IDA algorithm seeks to give a list of possible parents of a given variable Y and their corresponding
 * lower-bounded effects on Y. It regresses Y on X &cup; S, where X is a possible parent of Y and S is a set of possible
 * parents of X, and reports the regression coefficient. The set of such regressions is then sorted in ascending order
 * to give the total effects of X on Y. The absolute total effects are calculated as the absolute values of the total
 * effects.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Cstar
 * @see NodeEffects
 */
public class PagIda {

    /**
     * The CPDAG (found, e.g., by running PC, or some other CPDAG producing algorithm)
     */
    private final Graph graph;
    /**
     * A map from node names to indices in the covariance matrix.
     */
    private final Map<String, Integer> nodeIndices;
    /**
     * The covariance matrix for the dataset.
     */
    private final ICovarianceMatrix allCovariances;
    /**
     * True if the input graph is a DAG (as opposed to a CPDAG / PDAG).
     */
    private final boolean dag;
    /**
     * A boolean variable indicating whether the PAG-based IDA approach is used in the computation.
     * PAG (Partial Ancestral Graph) represents a generalization of causal graphs that can include
     * latent variables and selection bias. This variable is central to controlling whether the
     * related methods and computations in the class are applied under this framework.
     */
    private final boolean pag;
    /**
     * Represents the type of IDA (Intervention Calculus when the DAG is Absent). This variable can have one of the
     * values defined in the {@code IDA_TYPE} enum. It is used to differentiate between the regular IDA type and the
     * optimal IDA type.
     */
    private IDA_TYPE idaType = IDA_TYPE.OPTIMAL;
    /**
     * Optional maximum path length for O-set search in CPDAG mode. Use -1 (or any negative) for "no limit".
     */
    private int maxLengthAdjustment = -1;

    /**
     * Constructor for the PagIda class. This initializes an instance of PagIda using the provided
     * dataset and graph. It ensures the given dataset is continuous and the graph is a valid DAG,
     * CPDAG, PDAG, or PAG. Also sets up internal structures necessary for computation, such as node
     * indices and covariance matrices.
     *
     * @param dataSet The dataset to be used, which must be continuous. Must not be null.
     * @param graph The graph to be analyzed, which must be a DAG, CPDAG, PDAG, or PAG. Must not be null.
     * @throws NullPointerException If either {@code dataSet} or {@code graph} is null.
     * @throws IllegalArgumentException If the graph is not a DAG, CPDAG, PDAG, or PAG, or if the dataset is not continuous.
     */
    public PagIda(DataSet dataSet, Graph graph) {
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        // Allow DAG/CPDAG/PDAG or PAG; reject other graph types.
        boolean isDag = graph.paths().isLegalDag();
        boolean isPdag = graph.paths().isLegalPdag();
        boolean isPag = graph.paths().isLegalPag();   // assumes you have this; if not, call your legality checker

        if (!(isDag || isPdag || isPag)) {
            throw new IllegalArgumentException("Expecting a DAG/CPDAG/PDAG or PAG.");
        }

        this.dag = isDag;
        this.pag = isPag;

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        this.graph = graph;
        this.allCovariances = new CovarianceMatrix(dataSet);
        this.nodeIndices = new HashMap<>();

        for (int i = 0; i < graph.getNodes().size(); i++) {
            this.nodeIndices.put(graph.getNodes().get(i).getName(), i);
        }
    }

    private static boolean endpointRefines(Endpoint oldMark, Endpoint newMark) {
        // We assume oldMark and newMark are non-null, since they are only called for adjacent nodes.

        return switch (oldMark) {
            case TAIL ->
                // Tail is invariant: refinement must keep a tail.
                    newMark == Endpoint.TAIL;
            case ARROW ->
                // Arrowhead is invariant: refinement must keep an arrowhead.
                    newMark == Endpoint.ARROW;
            case CIRCLE ->
                // Circle is "unknown": refinement may choose circle, tail, or arrow.
                    newMark == Endpoint.CIRCLE
                    || newMark == Endpoint.TAIL
                    || newMark == Endpoint.ARROW;
            default ->
                // Shouldn't happen, but be conservative.
                    false;
        };
    }

    /**
     * Determines whether the provided fine graph is a refinement of the coarse graph.
     * A graph is considered a refinement of another if they have the same set of nodes,
     * the same adjacencies between nodes, and the endpoints on edges in the fine graph
     * refine the endpoints in the coarse graph.
     *
     * @param coarse the coarse graph to be checked against. Must not be null.
     * @param fine the fine graph to be validated as a refinement. Must not be null.
     * @return true if the fine graph is a refinement of the coarse graph, false otherwise.
     */
    public static boolean isRefinementOf(Graph coarse, Graph fine) {
        // 1. Same node set
        Set<Node> coarseNodes = new HashSet<>(coarse.getNodes());
        Set<Node> fineNodes = new HashSet<>(fine.getNodes());

        if (!coarseNodes.equals(fineNodes)) {
            return false;
        }

        // 2. Same adjacencies and endpoint-wise refinement
        for (Node a : coarseNodes) {
            for (Node b : coarseNodes) {
                if (a == b) continue;

                Edge eCoarse = coarse.getEdge(a, b);
                Edge eFine = fine.getEdge(a, b);

                if (eCoarse == null && eFine == null) {
                    // no edge in either graph: OK
                    continue;
                }

                if (eCoarse == null || eFine == null) {
                    // skeleton changed: not a refinement
                    return false;
                }

                // 3. Endpoint-wise refinement at both ends
                Endpoint ca = coarse.getEndpoint(a, b);
                Endpoint cb = coarse.getEndpoint(b, a);
                Endpoint fa = fine.getEndpoint(a, b);
                Endpoint fb = fine.getEndpoint(b, a);

                if (!endpointRefines(ca, fa)) return false;
                if (!endpointRefines(cb, fb)) return false;
            }
        }

        return true;
    }

    /**
     * Returns the distance between the effects and the true effect.
     *
     * @param effects    a {@link LinkedList} object
     * @param trueEffect a double
     * @return This difference.
     */
    public double distance(LinkedList<Double> effects, double trueEffect) {
        effects = new LinkedList<>(effects);
        if (effects.isEmpty()) return trueEffect;

        if (effects.size() == 1) {
            double effect = effects.get(0);
            return abs(effect - trueEffect);
        } else {
            Collections.sort(effects);
            double min = effects.getFirst();
            double max = effects.getLast();

            if (trueEffect >= min && trueEffect <= max) {
                return 0.0;
            } else {
                double m1 = abs(trueEffect - min);
                double m2 = abs(trueEffect - max);
                return min(m1, m2);
            }
        }
    }

    /**
     * Computes the total effects of a specified node x on another node y based on the internal graph type.
     * Depending on whether the PAG field is active, the method leverages either PAG-based logic or
     * the original DAG/CPDAG/PAG orientation and adjustment logic.
     *
     * @param x The source node from which the total effects are being calculated.
     * @param y The target node to which the total effects are applied.
     * @return A LinkedList of doubles representing the total effects of node x on node y.
     */
    public LinkedList<Double> getTotalEffects(Node x, Node y) {
        if (pag) {
            return getTotalEffectsPagInternal(x, y);
        } else {
            // original DAG/CPDAG logic
            return getTotalEffectsPdagInternal(x, y);
        }
    }

    /**
     * Calculates the total causal effects for a given pair of nodes (x, y) in a partially directed acyclic graph (PDAG),
     * considering various configurations of adjacent nodes.
     *
     * @param x the node for which total effects are calculated as the cause node
     * @param y the node for which total effects are calculated as the effect node
     * @return a sorted linked list of total effects between node x and node y
     */
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
                    int[] choice2;

                    while ((choice2 = gen2.next()) != null) {
                        List<Node> adj = GraphUtils.asList(choice2, siblingsChoice);
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
                    // === Original (parent-based) IDA ===
                    Set<Node> _regressors = new HashSet<>();
                    _regressors.add(x);
                    _regressors.addAll(parents);
                    _regressors.addAll(siblingsChoice);
                    List<Node> regressors = new ArrayList<>(_regressors);

                    if (regressors.contains(y)) {
                        beta = 0.0;
                    } else {
                        beta = getBeta(regressors, x, y);
                    }
                } else {
                    // === Optimal IDA (Witte et al. 2020) ===

                    // 1) Build a local orientation of the graph around X
                    Graph gPrime = new EdgeListGraph(this.graph);

                    for (Node s : siblings) {
                        if (!gPrime.isAdjacentTo(x, s)) continue;
                        gPrime.removeEdge(x, s);

                        if (siblingsChoice.contains(s)) {
                            // Treat s as a parent: s -> X
                            gPrime.addDirectedEdge(s, x);
                        } else {
                            // Treat s as a child: X -> s
                            gPrime.addDirectedEdge(x, s);
                        }
                    }

                    // 2) Apply Meek rules to propagate orientations
                    MeekRules rules = new MeekRules();
                    rules.setRevertToUnshieldedColliders(false);
                    rules.orientImplied(gPrime);

                    // 3) Compute the O-set for (X, Y) in gPrime
                    Set<Node> oSet;

                    try {
                        if (dag) {
                            oSet = OSet.oSetDag(gPrime, x, y);
                        } else {
                            oSet = OSet.oSetCpdag(gPrime, x, y, maxLengthAdjustment);
                        }
                    } catch (Exception e) {
                        beta = 0.0;
                        totalEffects.add(beta);
                        continue;
                    }

                    if (oSet == null) {
                        if (!gPrime.paths().isGraphAmenable(x, y, "PDAG", -1, Set.of())) {
                            throw new IllegalArgumentException("PDAG is weirdly not amenable for " + x + " ~~> " + y
                                                               + "; that must not have been a legal CPDAG.");
                        } else {
                            beta = 0.0;
                            totalEffects.add(beta);
                            continue;
                        }
                    }

                    Set<Node> regressorsSet = new LinkedHashSet<>();
                    regressorsSet.add(x);
                    regressorsSet.addAll(oSet);
                    regressorsSet.remove(y);
                    List<Node> regressors = new ArrayList<>(regressorsSet);
                    beta = getBeta(regressors, x, y);
                }

                totalEffects.add(beta);
            } catch (Exception e) {
                TetradLogger.getInstance().log(e.getMessage());
            }
        }

        Collections.sort(totalEffects);
        return totalEffects;
    }

    /**
     * PAG-based IDA: For each local refinement g' of the PAG around X that: (i)  refines the original PAG, (ii) is a
     * legal PAG, (iii) makes (X,Y) amenable in the Perković sense, we compute an adjustment set via RecursiveAdjustment
     * / GAC on g' and regress Y on X ∪ Z to get one total effect.
     * <p>
     * The multiset of all such betas is returned.
     */
    private LinkedList<Double> getTotalEffectsPagInternal(Node x, Node y) {
        LinkedList<Double> totalEffects = new LinkedList<>();

        Graph base = this.graph;  // original PAG

        // Quick sanity: if there are no potentially directed paths at all, total effect is 0.
        Set<List<Node>> pdPaths = base.paths().potentiallyDirectedPaths(x, y, maxLengthAdjustment);

        if (pdPaths == null || pdPaths.isEmpty()) {
            // No possibly directed X ~> Y path under any completion.
            totalEffects.add(0.0);
            return totalEffects;
        }

        // Edges incident to X where the endpoint at X is a circle: X o-* W
        List<Edge> orientable = new ArrayList<>();
        for (Edge e : base.getEdges(x)) {
            Node other = e.getDistalNode(x);
            Endpoint atX = base.getEndpoint(x, other);
            if (atX == Endpoint.CIRCLE) {
                orientable.add(e);
            }
        }

        // If there is nothing to orient locally around X, just run the DFS once on the base PAG.
        if (orientable.isEmpty()) {
            dfsOrientAndCollectPag(base, x, y, orientable, 0, totalEffects);
            Collections.sort(totalEffects);
            return totalEffects;
        }

        Set<Node> forceVisible = new HashSet<>();

        // Enumerate all local orientation patterns around X on the circle-edges,
        // then for each pattern:
        //   - run FCI final rules,
        //   - check refinement + legal PAG + amenability,
        //   - run RA and collect a beta.
        dfsOrientAndCollectPag(base, x, y, orientable, 0, totalEffects);

        // If nothing succeeded, you can decide whether to treat this as "no effect"
        // or leave it empty so the caller can distinguish the case. Here we leave it.
        Collections.sort(totalEffects);
        return totalEffects;
    }

    /**
     * This method applies a depth-first search (DFS) approach to orient edges in a
     * Potential Ancestral Graph (PAG) and collect causal effects based on refinements.
     * It processes possible edge orientations recursively and evaluates the compatibility
     * of locally oriented graphs with certain causal graph properties and rules.
     *
     * @param current the current PAG being refined and evaluated in the DFS process.
     * @param x the source node for orientation and effect evaluation.
     * @param y the target node for potential causal effect computation.
     * @param orientable the list of edges that can be oriented in the current DFS step.
     * @param index the current index of the edge in the orientable list being processed.
     * @param totalEffects the list to collect computed total effects for valid graph refinements.
     */
    private void dfsOrientAndCollectPag(Graph current,
                                        Node x,
                                        Node y,
                                        List<Edge> orientable,
                                        int index,
                                        List<Double> totalEffects) {

        if (index == orientable.size()) {
            // Leaf: we have a locally oriented graph "current" around X.
            // Now apply FCI final rules and then RA, if consistent.

            Graph gPrime = new EdgeListGraph(current);

            // Apply FCI final orientation rules (R0–R4) using the original PAG as configuration template.
            FciOrient finalFciRules =
                    new FciOrient(R0R4StrategyTestBased.defaultConfiguration(this.graph, new Knowledge()));
            finalFciRules.finalOrientation(gPrime);

            // 0) If there is no potentially directed X ~> Y path, treat this completion as "effect 0".
            Set<List<Node>> pdPaths = gPrime.paths().potentiallyDirectedPaths(x, y, maxLengthAdjustment);
            if (pdPaths == null || pdPaths.isEmpty()) {
                totalEffects.add(0.0);
                return;
            }

            // 1) Must refine the original PAG.
            if (!isRefinementOf(this.graph, gPrime)) {
                return;
            }

            // 3) (X,Y) must be amenable in this refinement under the chosen semantics.
            if (!gPrime.paths().isGraphAmenable(x, y, "PAG", maxLengthAdjustment, new HashSet<>(gPrime.getChildren(x)))) {
                return;
            }

            // 4) If all that passes, run RA + regression to collect one beta.
            collectEffectsFromRefinementPag(gPrime, x, y, totalEffects, new HashSet<>(gPrime.getChildren(x)));
            return;
        }

        // Still have edges to orient.
        Edge template = orientable.get(index);
        Node other = template.getDistalNode(x);

        // Look up the original endpoints in the base graph for refinement checks.
        Endpoint oldAtX = this.graph.getEndpoint(x, other);
        Endpoint oldAtOther = this.graph.getEndpoint(other, x);

        // Try each of the three fully-oriented possibilities that remove the circle at X:
        //   1) X -> other   (TAIL at X, ARROW at other)
        //   2) X <- other   (ARROW at X, TAIL at other)
        //   3) X <-> other  (ARROW at X, ARROW at other)
        tryOrientationPag(current, x, other,
                oldAtX, oldAtOther,
                Endpoint.TAIL, Endpoint.ARROW,   // X -> other
                orientable, index, y, totalEffects);

        tryOrientationPag(current, x, other,
                oldAtX, oldAtOther,
                Endpoint.ARROW, Endpoint.TAIL,   // X <- other
                orientable, index, y, totalEffects);

        tryOrientationPag(current, x, other,
                oldAtX, oldAtOther,
                Endpoint.ARROW, Endpoint.ARROW,  // X <-> other
                orientable, index, y, totalEffects);
    }

    /**
     * Attempts to orient the edges of the PAG (Partial Ancestral Graph) by refining the
     * endpoints of a specific edge and recursively processing subsequent edges. This method
     * constructs a fresh graph copy for each orientation attempt and applies the updated
     * connectivity based on the given endpoints. It ensures only valid endpoint refinements
     * are processed.
     *
     * @param current The current graph instance being analyzed and oriented.
     * @param x The starting node of the edge being refined.
     * @param other The other node connected to x in the edge being refined.
     * @param oldAtX The original endpoint at node x.
     * @param oldAtOther The original endpoint at node other.
     * @param newAtX The new endpoint at node x for the edge.
     * @param newAtOther The new endpoint at node other for the edge.
     * @param orientable The list of orientable edges to process.
     * @param index The index of the current edge within the orientable list.
     * @param y The specific node used for some conditional orientation decisions or evaluations.
     * @param totalEffects A list of total effect values collected during the orientation process.
     */
    private void tryOrientationPag(Graph current,
                                   Node x,
                                   Node other,
                                   Endpoint oldAtX,
                                   Endpoint oldAtOther,
                                   Endpoint newAtX,
                                   Endpoint newAtOther,
                                   List<Edge> orientable,
                                   int index,
                                   Node y,
                                   List<Double> totalEffects) {

        // Must refine the original endpoints from the base graph.
        if (!endpointRefines(oldAtX, newAtX)) return;
        if (!endpointRefines(oldAtOther, newAtOther)) return;

        // Work on a fresh copy for this branch.
        Graph gNext = new EdgeListGraph(current);

        Edge old = gNext.getEdge(x, other);
        if (old != null) {
            gNext.removeEdge(old);
        }

        // Add the new oriented edge consistent with (newAtX, newAtOther).
        if (newAtX == Endpoint.TAIL && newAtOther == Endpoint.ARROW) {
            // X -> other
            gNext.addDirectedEdge(x, other);
        } else if (newAtX == Endpoint.ARROW && newAtOther == Endpoint.TAIL) {
            // other -> X
            gNext.addDirectedEdge(other, x);
        } else if (newAtX == Endpoint.ARROW && newAtOther == Endpoint.ARROW) {
            // X <-> other
            gNext.addBidirectedEdge(x, other);
        } else {
            // We only handle fully-oriented (no circles) cases here.
            return;
        }

        // Recurse to orient the next edge.
        dfsOrientAndCollectPag(gNext, x, y, orientable, index + 1, totalEffects);
    }

    /**
     * Collects effects from a refinement PAG (Partial Ancestral Graph) using
     * Recursive Adjustment methods and adds computed effects to the totalEffects list.
     *
     * @param gPrime The input graph (PAG) on which the refinement and adjustments are performed.
     * @param x The exposure node for which effects are being calculated.
     * @param y The outcome node for which effects are being calculated.
     * @param totalEffects A list to store the computed total effects from the analysis.
     * @param forceVisibility A set of nodes forced to be considered visible in the adjustment process.
     */
    private void collectEffectsFromRefinementPag(Graph gPrime,
                                                 Node x,
                                                 Node y,
                                                 List<Double> totalEffects,
                                                 Set<Node> forceVisibility) {
        try {
            RecursiveAdjustment ra = new RecursiveAdjustment(gPrime);

            int maxNumSets = 1;                        // take one minimal adjustment set
            int maxRadius = gPrime.getNodes().size();  // allow full radius
            int nearWhichEndpoint = 3;                 // 1=X, 2=Y, 3=both
            boolean avoidAmenable = true;             // RA-mode, not RB-mode
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

            if (adjSets.isEmpty()) {
                // Amenable but RA could not find an adjustment set; skip.
                return;
            }

            Set<Node> Z = adjSets.getFirst();

            // Build regressor set X ∪ Z; remove Y if it sneaks in.
            Set<Node> regressorsSet = new LinkedHashSet<>();
            regressorsSet.add(x);
            regressorsSet.addAll(Z);
            regressorsSet.remove(y);

            List<Node> regressors = new ArrayList<>(regressorsSet);

            double beta = getBeta(regressors, x, y);
            totalEffects.add(beta);
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
        }
    }

    /**
     * This method calculates the absolute total effects of node x on node y.
     *
     * @param x The node for which the total effects are calculated.
     * @param y The node whose total effects are obtained.
     * @return A LinkedList of Double values representing the absolute total effects of node x on node y. The LinkedList
     * is sorted in ascending order.
     */
    public LinkedList<Double> getAbsTotalEffects(Node x, Node y) {
        LinkedList<Double> totalEffects = getTotalEffects(x, y);
        LinkedList<Double> absTotalEffects = new LinkedList<>();
        for (double d : totalEffects) {
            absTotalEffects.add(Math.abs(d));
        }

        Collections.sort(absTotalEffects);
        return absTotalEffects;
    }

    /**
     * Calculates the beta coefficient for a given set of regressors and a child node.
     * <p>
     * Note that x must be the first regressor.
     *
     * @param regressors The list of regressor nodes.
     * @param parent     The parent node for which the beta coefficient is calculated.
     * @param child      The child node for which the beta coefficient is calculated.
     * @return The beta coefficient for the parent->child regression.
     * @throws RuntimeException If a singularity is encountered during the regression process.
     */
    private double getBeta(List<Node> regressors, Node parent, Node child) {
        if (!regressors.contains(parent))
            throw new IllegalArgumentException("The regressors must contain the parent node.");

        try {
            int xIndex = regressors.indexOf(parent);
            int yIndex = this.nodeIndices.get(child.getName());
            int[] xIndices = new int[regressors.size()];
            for (int i = 0; i < regressors.size(); i++) xIndices[i] = this.nodeIndices.get(regressors.get(i).getName());

            Matrix rX = this.allCovariances.getSelection(xIndices, xIndices);
            Matrix rY = this.allCovariances.getSelection(xIndices, new int[]{yIndex});
            Matrix bStar = rX.inverse().times(rY);
            return bStar != null ? bStar.get(xIndex, 0) : 0.0;
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when regressing " + LogUtilsSearch.getScoreFact(child, regressors));
        }
    }

    /**
     * Sets the IDA type for this instance.
     *
     * @param idaType the IDA_TYPE to be set; must not be null
     * @throws NullPointerException if the provided idaType is null
     */
    public void setIdaType(IDA_TYPE idaType) {
        if (idaType == null) {
            throw new NullPointerException("IDA type must not be null.");
        }
        this.idaType = idaType;
    }

    /**
     * Sets the maximum length adjustment value.
     * This value is used to adjust the maximum length constraints.
     *
     * @param maxLengthAdjustment the maximum length adjustment value to be set
     */
    public void setMaxLengthAdjustment(int maxLengthAdjustment) {
        this.maxLengthAdjustment = maxLengthAdjustment;
    }

    /**
     * Enumeration representing the types of IDA (Intervention-based Directed Acyclic analysis). IDA_TYPE provides two
     * classification options: REGULAR and OPTIMAL.
     */
    public enum IDA_TYPE {

        /**
         * Represents the REGULAR classification of IDA_TYPE. This classification is used to denote a standard or
         * conventional type of Intervention-based Directed Acyclic analysis.
         */
        REGULAR,

        /**
         * Represents the OPTIMAL classification of IDA_TYPE (Witte et al. 2020). This classification is used to signify
         * a refined, efficient, or optimized type of Intervention-based Directed Acyclic analysis.
         */
        OPTIMAL
    }

    /**
     * Gives a list of nodes (parents or children) and corresponding minimum effects for the IDA algorithm.
     *
     * @author josephramsey
     */
    public static class NodeEffects {
        /**
         * The nodes.
         */
        private List<Node> nodes;
        /**
         * The effects.
         */
        private LinkedList<Double> effects;

        /**
         * Constructor.
         *
         * @param nodes   The nodes.
         * @param effects The effects.
         */
        NodeEffects(List<Node> nodes, LinkedList<Double> effects) {
            this.setNodes(nodes);
            this.setEffects(effects);
        }

        /**
         * Returns the nodes.
         *
         * @return The nodes.
         */
        public List<Node> getNodes() {
            return this.nodes;
        }

        /**
         * Sets the nodes.
         *
         * @param nodes The nodes.
         */
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }

        /**
         * Returns the effects.
         *
         * @return The effects.
         */
        public LinkedList<Double> getEffects() {
            return this.effects;
        }

        /**
         * Sets the effects.
         *
         * @param effects The effects.
         */
        public void setEffects(LinkedList<Double> effects) {
            this.effects = effects;
        }

        /**
         * Returns a string representation of this object.
         *
         * @return A string representation of this object.
         */
        public String toString() {
            StringBuilder b = new StringBuilder();

            for (int i = 0; i < this.nodes.size(); i++) {
                b.append(this.nodes.get(i)).append("=").append(this.effects.get(i)).append(" ");
            }

            return b.toString();
        }
    }
}

