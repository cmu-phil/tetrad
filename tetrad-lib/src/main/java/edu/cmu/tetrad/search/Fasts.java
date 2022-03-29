///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Implements the "fast adjacency search" used in several causal algorithm in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithm, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Joseph Ramsey.
 * <p>
 * <p>
 * This is a copy of Fas.java for the SvarFCI algorithm. The main difference is that if an edge is removed, it will also
 * remove all homologous edges to preserve the time-repeating structure assumed by SvarFCI. Based on (but not identicial
 * to) code by Entner and Hoyer for their 2010 paper. Modified by DMalinsky 4/21/2016.
 */
public class Fasts implements IFas {

    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private final Graph graph;

    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;


    /**
     * The logger, by default the empty logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The true graph, for purposes of comparison. Temporary.
     */
    private Graph trueGraph;

    /**
     * The number of false dependence judgements, judged from the true graph using d-separation. Temporary.
     */
    private int numFalseDependenceJudgments;

    /**
     * The number of dependence judgements. Temporary.
     */
    private int numDependenceJudgement;

    private int numIndependenceJudgements;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    /**
     * True if this is being run by FCI--need to skip the knowledge forbid step.
     */
    private final boolean fci = false;

    /**
     * The depth 0 graph, specified initially.
     */
    private Graph externalGraph;

    private final NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;

    private PrintStream out = System.out;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public Fasts(final Graph graph, final IndependenceTest test) {
        this.graph = graph;
        this.test = test;
    }

    public Fasts(final IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        this.graph.removeEdges(this.graph.getEdges());

        this.sepset = new SepsetMap();
//        sepset.setReturnEmptyIfNotSet(true);

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        final Map<Node, Set<Node>> adjacencies = new HashMap<>();
        final List<Node> nodes = this.graph.getNodes();

        for (final Node node : nodes) {
            adjacencies.put(node, new TreeSet<Node>());
        }

        for (int d = 0; d <= _depth; d++) {
            final boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, this.test, adjacencies);
            } else {
                more = searchAtDepth(nodes, this.test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    this.graph.addUndirectedEdge(x, y);
                }
            }
        }

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return this.graph;
    }

    public Map<Node, Set<Node>> searchMapOnly() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        this.graph.removeEdges(this.graph.getEdges());

        this.sepset = new SepsetMap();

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }


        final Map<Node, Set<Node>> adjacencies = new HashMap<>();
        final List<Node> nodes = this.graph.getNodes();

        for (final Node node : nodes) {
            adjacencies.put(node, new TreeSet<Node>());
        }

        for (int d = 0; d <= _depth; d++) {
            final boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, this.test, adjacencies);
            } else {
                more = searchAtDepth(nodes, this.test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        return adjacencies;
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(final int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    private boolean searchAtDepth0(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        final List<Node> empty = Collections.emptyList();
        final List<Node> simListX = new ArrayList<>();
        final List<Node> simListY = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (this.verbose) {
                if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));
            }


            final Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {

                final Node y = nodes.get(j);

                //if the current nodes under consideration were already handled by similarNodes, skip this pair
                final String xName = x.getName();
                final String yName = y.getName();
                boolean skippair = false;

                final Iterator itx1 = simListX.iterator();
                final Iterator ity1 = simListY.iterator();
                while (itx1.hasNext() && ity1.hasNext()) {
                    final Node x1 = (Node) itx1.next();
                    final Node y1 = (Node) ity1.next();
                    final String simX = x1.getName();
                    final String simY = y1.getName();
                    if ((Objects.equals(xName, simX) && Objects.equals(yName, simY)) ||
                            (Objects.equals(xName, simY) && Objects.equals(yName, simX))) {
                        skippair = true;
                        System.out.println("Skipping pair x,y = " + xName + ", " + yName);
                        break;
                    }
                }

                if (skippair) continue;

                if (this.externalGraph != null) {
                    final Node x2 = this.externalGraph.getNode(x.getName());
                    final Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }

                boolean independent;

                try {
                    this.numIndependenceTests++;
                    independent = test.isIndependent(x, y, empty);
                    System.out.println("############# independence given empty set: x,y " + x + ", " +
                            y + " independence = " + independent);
                } catch (final Exception e) {
                    e.printStackTrace();
                    independent = false;
                }

                if (independent) {
                    this.numIndependenceJudgements++;
                } else {
                    this.numDependenceJudgement++;
                }

                final boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());

//                getSepsets().setReturnEmptyIfNotSet(false); // added 05.30.2016
                if (independent && noEdgeRequired) {
//                    if (!getSepsets().isReturnEmptyIfNotSet()) {
                    getSepsets().set(x, y, empty);
                    System.out.println("$$$$$$$$$$$ look for similar pairs x,y = " + x + ", " + y);
                    final List<List<Node>> simList = returnSimilarPairs(test, x, y);
                    if (simList.isEmpty()) continue;
                    final List<Node> x1List = simList.get(0);
                    final List<Node> y1List = simList.get(1);
                    simListX.addAll(x1List);
                    simListY.addAll(y1List);
                    final Iterator itx = x1List.iterator();
                    final Iterator ity = y1List.iterator();
                    while (itx.hasNext() && ity.hasNext()) {
                        final Node x1 = (Node) itx.next();
                        final Node y1 = (Node) ity.next();
                        System.out.println("$$$$$$$$$$$ found similar pair x,y = " + x1 + ", " + y1);
                        getSepsets().set(x1, y1, empty);
                    }
//                    }

                    TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFact(x, y, empty) + " score = " +
                            this.nf.format(test.getScore()));

                    if (this.verbose) {
                        this.out.println(SearchLogUtils.independenceFact(x, y, empty) + " score = " +
                                this.nf.format(test.getScore()));
                    }

                } else if (!forbiddenEdge(x, y)) {
                    System.out.println("adding edge between x = " + x + " and y = " + y);
                    adjacencies.get(x).add(y);
                    adjacencies.get(y).add(x);
                    // This would add edges to all similar pairs which are found to be dependent...
                    final List<List<Node>> simList = returnSimilarPairs(test, x, y);
                    if (simList.isEmpty()) continue;
                    final List<Node> x1List = simList.get(0);
                    final List<Node> y1List = simList.get(1);
                    simListX.addAll(x1List);
                    simListY.addAll(y1List);
                    final Iterator itx = x1List.iterator();
                    final Iterator ity = y1List.iterator();
                    while (itx.hasNext() && ity.hasNext()) {
                        final Node x1 = (Node) itx.next();
                        final Node y1 = (Node) ity.next();
                        System.out.println("$$$$$$$$$$$ similar pair x,y = " + x1 + ", " + y1);
                        System.out.println("adding edge between x = " + x1 + " and y = " + y1);
                        adjacencies.get(x1).add(y1);
                        adjacencies.get(y1).add(x1);
                    }

                    if (this.verbose) {
                        TetradLogger.getInstance().log("dependencies", SearchLogUtils.independenceFact(x, y, empty) + " score = " +
                                this.nf.format(test.getScore()));
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > 0;
    }

    private int freeDegree(final List<Node> nodes, final Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (final Node x : nodes) {
            final Set<Node> opposites = adjacencies.get(x);

            for (final Node y : opposites) {
                final Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    private boolean forbiddenEdge(final Node x, final Node y) {
        final String name1 = x.getName();
        final String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
                this.knowledge.isForbidden(name2, name1)) {
            this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

    private boolean searchAtDepth(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies, final int depth) {
        int count = 0;

        for (final Node x : nodes) {
            if (this.verbose) {
                if (++count % 100 == 0) this.out.println("count " + count + " of " + nodes.size());
            }

            final List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (final Node y : adjx) {
                final List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                final List<Node> ppx = possibleParents(x, _adjx, this.knowledge);

                if (ppx.size() >= depth) {
                    final ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        final List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent;

                        try {
                            this.numIndependenceTests++;
                            independent = test.isIndependent(x, y, condSet);
                        } catch (final Exception e) {
                            independent = false;
                        }

                        if (independent) {
                            this.numIndependenceJudgements++;
                        } else {
                            this.numDependenceJudgement++;
                        }

                        final boolean noEdgeRequired =
                                this.knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);

                            getSepsets().set(x, y, condSet);

                            // This is the added component to enforce repeating structure
                            removeSimilarPairs(adjacencies, test, x, y, condSet);


                            if (this.verbose) {
                                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFact(x, y, condSet) +
                                        " score = " + this.nf.format(test.getScore()));
                                this.out.println(SearchLogUtils.independenceFactMsg(x, y, condSet, test.getPValue()));
                            }

                            continue EDGE;
                        }
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(final Node x, final List<Node> adjx,
                                       final IKnowledge knowledge) {
        final List<Node> possibleParents = new LinkedList<>();
        final String _x = x.getName();

        for (final Node z : adjx) {
            final String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(final String z, final String x, final IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    // removeSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private void removeSimilarPairs(final Map<Node, Set<Node>> adjacencies, final IndependenceTest test, final Node x, final Node y, final List<Node> condSet) {
        System.out.println("Entering removeSimilarPairs method...");
        System.out.println("original independence: " + x + " and " + y + " conditional on " + condSet);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            System.out.println("Not removing similar pairs b/c variable pair includes time.");
            return;
        }
        for (final Node tempNode : condSet) {
            if (tempNode.getName().equals("time")) {
                System.out.println("Not removing similar pairs b/c conditioning set includes time.");
                return;
            }
        }
        final int ntiers = this.knowledge.getNumTiers();
        final int indx_tier = this.knowledge.isInWhichTier(x);
        final int indy_tier = this.knowledge.isInWhichTier(y);
        final int max_tier = Math.max(indx_tier, indy_tier);
        final int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        final List tier_x = this.knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        final List tier_y = this.knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;
            final String A;
            final Node x1;
            final String B;
            final Node y1;
            if (indx_tier >= indy_tier) {
                final List tmp_tier1 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                final List tmp_tier2 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                adjacencies.get(x1).remove(y1);
                adjacencies.get(y1).remove(x1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                final List<Node> condSetAB = new ArrayList<>();
                for (final Node tempNode : condSet) {
                    final int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    final List temptier = this.knowledge.getTier(ind_temptier);
//                    Collections.sort(temptier);
                    int ind_temp = -1;
                    for (int j = 0; j < temptier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    final int cond_diff = indx_tier - ind_temptier;
                    final int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;
//                    System.out.println("tempNode = " + tempNode);
//                    System.out.println("ind_temptier = " + ind_temptier);
//                    System.out.println("indx_tier = " + indx_tier);
//                    System.out.println("cond_diff = " + cond_diff);
//                    System.out.println("condAB_tier = " + condAB_tier);
//                    System.out.println("max_tier = " + max_tier);
//                    System.out.println("ntiers = " + ntiers);
                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                            || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
//                        List<Node> empty = Collections.emptyList();
//                        getSepsets2().set(x1, y1, empty); // added 05.01.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                + "of window, so not added to SepSet");
                        continue;
                    }
                    final List new_tier = this.knowledge.getTier(condAB_tier);
//                    Collections.sort(new_tier);
                    final String tempNode1 = (String) new_tier.get(ind_temp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            } else {
                //System.out.println("############## WARNING (removeSimilarPairs): did not catch x,y pair " + x + ", " + y);
                //System.out.println();
                final List tmp_tier1 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier1);
                final List tmp_tier2 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                adjacencies.get(x1).remove(y1);
                adjacencies.get(y1).remove(x1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                final List<Node> condSetAB = new ArrayList<>();
                for (final Node tempNode : condSet) {
                    final int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    final List temptier = this.knowledge.getTier(ind_temptier);
//                    Collections.sort(temptier);
                    int ind_temp = -1;
                    for (int j = 0; j < temptier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    final int cond_diff = indx_tier - ind_temptier;
                    final int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;
//                    System.out.println("tempNode = " + tempNode);
//                    System.out.println("ind_temptier = " + ind_temptier);
//                    System.out.println("indx_tier = " + indx_tier);
//                    System.out.println("cond_diff = " + cond_diff);
//                    System.out.println("condAB_tier = " + condAB_tier);
//                    System.out.println("max_tier = " + max_tier);
//                    System.out.println("ntiers = " + ntiers);
                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                            || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
//                        List<Node> empty = Collections.emptyList();
//                        getSepsets2().set(x1, y1, empty); // added 05.01.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                + "of window, so not added to SepSet");
                        continue;
                    }
                    final List new_tier = this.knowledge.getTier(condAB_tier);
//                    Collections.sort(new_tier);
                    final String tempNode1 = (String) new_tier.get(ind_temp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            }
        }
    }

    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(final IndependenceTest test, final Node x, final Node y) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
//        System.out.println("Knowledge within returnSimilar : " + knowledge);
        final int ntiers = this.knowledge.getNumTiers();
        final int indx_tier = this.knowledge.isInWhichTier(x);
        final int indy_tier = this.knowledge.isInWhichTier(y);
        final int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        final List tier_x = this.knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        final List tier_y = this.knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        final List<Node> simListX = new ArrayList<>();
        final List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;
            final String A;
            final Node x1;
            final String B;
            final Node y1;
            if (indx_tier >= indy_tier) {
                final List tmp_tier1 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                final List tmp_tier2 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            } else {
                //System.out.println("############## WARNING (returnSimilarPairs): did not catch x,y pair " + x + ", " + y);
                //System.out.println();
                final List tmp_tier1 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier1);
                final List tmp_tier2 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            }
        }

        final List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }


    public String getNameNoLag(final Object obj) {
        final String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    public void setTrueGraph(final Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgments() {
        return this.numFalseDependenceJudgments;
    }

    public int getNumDependenceJudgments() {
        return this.numDependenceJudgement;
    }

    public SepsetMap getSepsets() {
        return this.sepset;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }

    @Override
    public Graph search(final List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public List<Node> getNodes() {
        return this.test.getVariables();
    }

    @Override
    public List<Triple> getAmbiguousTriples(final Node node) {
        return null;
    }

    public int getNumIndependenceJudgements() {
        return this.numIndependenceJudgements;
    }

    @Override
    public void setOut(final PrintStream out) {
        this.out = out;
    }
}

