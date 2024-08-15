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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;

/**
 * Adapts FAS for the time series setting, assuming the data is generated by a SVAR (structural vector autoregression).
 * The main difference is that time order is imposed, and if an edge is removed, it will also remove all homologous
 * edges to preserve the time-repeating structure assumed by SvarFCI. Based on (but not identical to) code by Entner and
 * Hoyer for their 2010 paper. Modified by dmalinsky 4/21/2016.
 * <p>
 * The references are as follows:
 * <p>
 * Malinsky, D., &amp; Spirtes, P. (2018, August). Causal structure learning from multivariate time series in settings
 * with unmeasured confounding. In Proceedings of 2018 ACM SIGKDD workshop on causal discovery (pp. 23-47). PMLR.
 * <p>
 * Entner, D., &amp; Hoyer, P. O. (2010). On causal discovery from time series data using FCI. Probabilistic graphical
 * models, 121-128.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author dmalinsky
 * @version $Id: $Id
 * @see Fas
 * @see Knowledge
 * @see SvarFci
 */
public class SvarFas implements IFas {

    /**
     * The search graph. It is assumed going in that all the true adjacencies of x are in this graph for every node x.
     * It is hoped (i.e., true in the large sample limit) that true adjacencies are never removed.
     */
    private final Graph graph;
    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;
    /**
     * Private final variable that holds the number format.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * Specification of which edges are forbidden or required.
     */
    private Knowledge knowledge = new Knowledge();
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
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();
    /**
     * The depth 0 graph, specified initially.
     */
    private Graph externalGraph;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The output stream for printing.
     */
    private transient PrintStream out;

    /**
     * Constructs a new FastAdjacencySearch.
     *
     * @param test The independence test.
     */
    public SvarFas(IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
        out = System.out;
    }

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent, conditional on some other set of variables in the graph (the "sepset"). These
     * are removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then
     * edges which are independent conditional on one other variable are removed, then two, then three, and so on, until
     * no more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        TetradLogger.getInstance().log("Starting Fast Adjacency Search.");
        this.graph.removeEdges(this.graph.getEdges());
        this.sepset = new SepsetMap();
        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        Map<Node, Set<Node>> adjacencies = new HashMap<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node node : nodes) {
            adjacencies.put(node, new TreeSet<>());
        }

        for (int d = 0; d <= _depth; d++) {
            boolean more;

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
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    this.graph.addUndirectedEdge(x, y);
                }
            }
        }

        TetradLogger.getInstance().log("Finishing Fast Adjacency Search.");

        return this.graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the depth--i.e., the maximum number of variables conditioned on in any test, -1 for unlimited.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the knowledge used in the search.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the number of independence tests.
     *
     * @return This number.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Returns a map for x _||_ y | Z from {x, y} to Z.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets an external graph.
     *
     * @param externalGraph This graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Sets the verbosity of the program.
     *
     * @param verbose True if verbosity is enabled, False otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @throws UnsupportedOperationException since not implementedd.
     */
    @Override
    public long getElapsedTime() {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Retrieves the list of nodes from the current object.
     *
     * @return The list of nodes.
     */
    @Override
    public List<Node> getNodes() {
        return this.test.getVariables();
    }

    /**
     * Retrieves the list of ambiguous triples involving the given node.
     *
     * @param node The node.
     * @return The list of ambiguous triples involving the given node.
     */
    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Sets the output stream for printing.
     *
     * @param out The output stream to be set.
     */
    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Searches for nodes at depth 0.
     *
     * @param nodes       the list of nodes
     * @param test        the independence test
     * @param adjacencies the map of adjacencies
     * @return true if there is a free degree, false otherwise
     */
    private boolean searchAtDepth0(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        Set<Node> empty = Collections.emptySet();
        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (this.verbose) {
                if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));
            }


            Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {

                Node y = nodes.get(j);

                //if the current nodes under consideration were already handled by similarNodes, skip this pair
                String xName = x.getName();
                String yName = y.getName();
                boolean skipPair = false;

                Iterator<Node> itx1 = simListX.iterator();
                Iterator<Node> ity1 = simListY.iterator();
                while (itx1.hasNext() && ity1.hasNext()) {
                    Node x1 = itx1.next();
                    Node y1 = ity1.next();
                    String simX = x1.getName();
                    String simY = y1.getName();
                    if ((Objects.equals(xName, simX) && Objects.equals(yName, simY)) ||
                        (Objects.equals(xName, simY) && Objects.equals(yName, simX))) {
                        skipPair = true;
                        System.out.println("Skipping pair x,y = " + xName + ", " + yName);
                        break;
                    }
                }

                if (skipPair) continue;

                if (this.externalGraph != null) {
                    Node x2 = this.externalGraph.getNode(x.getName());
                    Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }

                IndependenceResult result;

                try {
                    this.numIndependenceTests++;
                    result = test.checkIndependence(x, y, empty);
                    System.out.println("############# independence given empty set: x,y " + x + ", " +
                                       y + " independence = " + result.isIndependent());
                } catch (Exception e) {
                    result = new IndependenceResult(new IndependenceFact(x, y, empty), false, Double.NaN, Double.NaN);
                }

                boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());

                if (result.isIndependent() && noEdgeRequired) {
                    getSepsets().set(x, y, empty);
                    List<List<Node>> simList = returnSimilarPairs(test, x, y);
                    if (simList.isEmpty()) continue;
                    List<Node> x1List = simList.get(0);
                    List<Node> y1List = simList.get(1);
                    simListX.addAll(x1List);
                    simListY.addAll(y1List);
                    Iterator<Node> itx = x1List.iterator();
                    Iterator<Node> ity = y1List.iterator();
                    while (itx.hasNext() && ity.hasNext()) {
                        Node x1 = itx.next();
                        Node y1 = ity.next();
                        getSepsets().set(x1, y1, empty);
                    }

                    String message = LogUtilsSearch.independenceFact(x, y, empty) + " score = " +
                                     this.nf.format(result.getScore());
                    TetradLogger.getInstance().log(message);

                    if (this.verbose) {
                        this.out.println(LogUtilsSearch.independenceFact(x, y, empty) + " score = " +
                                         this.nf.format(result.getScore()));
                    }

                } else if (!forbiddenEdge(x, y)) {
                    System.out.println("adding edge between x = " + x + " and y = " + y);
                    adjacencies.get(x).add(y);
                    adjacencies.get(y).add(x);
                    // This would add edges to all similar pairs that are found to be dependent...
                    List<List<Node>> simList = returnSimilarPairs(test, x, y);
                    if (simList.isEmpty()) continue;
                    List<Node> x1List = simList.get(0);
                    List<Node> y1List = simList.get(1);
                    simListX.addAll(x1List);
                    simListY.addAll(y1List);
                    Iterator<Node> itx = x1List.iterator();
                    Iterator<Node> ity = y1List.iterator();
                    while (itx.hasNext() && ity.hasNext()) {
                        Node x1 = itx.next();
                        Node y1 = ity.next();
                        System.out.println("adding edge between x = " + x1 + " and y = " + y1);
                        adjacencies.get(x1).add(y1);
                        adjacencies.get(y1).add(x1);
                    }

                    if (this.verbose) {
                        String message = LogUtilsSearch.independenceFact(x, y, empty) + " score = " +
                                         this.nf.format(result.getScore());
                        TetradLogger.getInstance().log(message);
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > 0;
    }

    /**
     * Calculates the maximum free degree of a list of nodes given their adjacencies.
     *
     * @param nodes       The list of nodes.
     * @param adjacencies The map of adjacencies for each node.
     * @return The maximum free degree of the nodes.
     */
    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    /**
     * Checks if an edge between two nodes is forbidden based on background knowledge.
     *
     * @param x The first node.
     * @param y The second node.
     * @return True if the edge is forbidden, false otherwise.
     */
    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
            this.knowledge.isForbidden(name2, name1)) {
            String message = "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                             "forbidden by background knowledge.";
            TetradLogger.getInstance().log(message);

            return true;
        }

        return false;
    }

    /**
     * Searches for nodes at a specific depth.
     *
     * @param nodes       The list of nodes.
     * @param test        The independence test.
     * @param adjacencies The map of adjacencies.
     * @param depth       The depth to search for.
     * @return True if there is a free degree, false otherwise.
     */
    private boolean searchAtDepth(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        for (Node x : nodes) {
            if (this.verbose) {
                if (++count % 100 == 0) this.out.println("count " + count + " of " + nodes.size());
            }

            List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (Node y : adjx) {
                List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                List<Node> ppx = possibleParents(x, _adjx, this.knowledge);

                if (ppx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        Set<Node> condSet = GraphUtils.asSet(choice, ppx);

                        boolean independent;

                        try {
                            this.numIndependenceTests++;
                            independent = test.checkIndependence(x, y, condSet).isIndependent();
                        } catch (Exception e) {
                            independent = false;
                        }

                        boolean noEdgeRequired =
                                this.knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);

                            getSepsets().set(x, y, condSet);

                            // This is the added component to enforce repeating structure
                            removeSimilarPairs(adjacencies, test, x, y, condSet);

                            continue EDGE;
                        }
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    /**
     * Returns a list of possible parents for a given node.
     *
     * @param x         The node.
     * @param adjx      The list of adjacent nodes.
     * @param knowledge The knowledge of the search.
     * @return A list of possible parents for the node.
     */
    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       Knowledge knowledge) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * Checks if the given node z is a possible parent of node x based on the provided knowledge.
     *
     * @param z         The node to check if it is a possible parent.
     * @param x         The node whose parent is being checked.
     * @param knowledge The knowledge used for the check.
     * @return True if z is a possible parent of x, false otherwise.
     */
    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Removes pairs of nodes from the adjacencies map that have similar structure knowledge based on the given test and
     * conditions. removeSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
     *
     * @param adjacencies The map of nodes and their adjacent nodes.
     * @param test        The independence test used to retrieve variables.
     * @param x           The first node in the pair.
     * @param y           The second node in the pair.
     * @param condSet     The set of nodes used for conditional independence test.
     */
    private void removeSimilarPairs(Map<Node, Set<Node>> adjacencies, IndependenceTest test, Node x, Node y, Set<Node> condSet) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return;
        }
        for (Node tempNode : condSet) {
            if (tempNode.getName().equals("time")) {
                return;
            }
        }
        int ntiers = this.knowledge.getNumTiers();
        int indx_tier = this.knowledge.isInWhichTier(x);
        int indy_tier = this.knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = this.knowledge.getTier(indx_tier);
        List<String> tier_y = this.knowledge.getTier(indy_tier);

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
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = this.knowledge.getTier(i + tier_diff);
                List<String> tmp_tier2 = this.knowledge.getTier(i);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                adjacencies.get(x1).remove(y1);
                adjacencies.get(y1).remove(x1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                Set<Node> condSetAB = new HashSet<>();
                for (Node tempNode : condSet) {
                    int indTempTier = this.knowledge.isInWhichTier(tempNode);
                    List<String> tempTier = this.knowledge.getTier(indTempTier);
                    int indTemp = -1;
                    for (int j = 0; j < tempTier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(tempTier.get(j)))) {
                            indTemp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - indTempTier;
                    int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;
                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                        || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                           + "of window, so not added to SepSet");
                        continue;
                    }
                    List<String> new_tier = this.knowledge.getTier(condAB_tier);
                    String tempNode1 = new_tier.get(indTemp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            } else {
                List<String> tmp_tier1 = this.knowledge.getTier(i);
                List<String> tmp_tier2 = this.knowledge.getTier(i + tier_diff);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                adjacencies.get(x1).remove(y1);
                adjacencies.get(y1).remove(x1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                Set<Node> condSetAB = new HashSet<>();
                for (Node tempNode : condSet) {
                    int indTempTier = this.knowledge.isInWhichTier(tempNode);
                    List<String> tempTier = this.knowledge.getTier(indTempTier);
                    int ind_temp = -1;
                    for (int j = 0; j < tempTier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(tempTier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - indTempTier;
                    int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;
                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                        || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                           + "of window, so not added to SepSet");
                        continue;
                    }
                    List<String> new_tier = this.knowledge.getTier(condAB_tier);
                    String tempNode1 = new_tier.get(ind_temp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            }
        }
    }

    /**
     * Returns a list of similar pairs of nodes based on the provided independence test, Node x, and Node y.
     * returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
     *
     * @param test the independence test to be used
     * @param x    the first Node
     * @param y    the second Node
     * @return a list of similar pairs of nodes
     */
    private List<List<Node>> returnSimilarPairs(IndependenceTest test, Node x, Node y) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
        int ntiers = this.knowledge.getNumTiers();
        int indx_tier = this.knowledge.isInWhichTier(x);
        int indy_tier = this.knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = this.knowledge.getTier(indx_tier);
        List<String> tier_y = this.knowledge.getTier(indy_tier);

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


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            List<String> tmp_tier1;
            List<String> tmp_tier2;
            if (indx_tier >= indy_tier) {
                tmp_tier1 = this.knowledge.getTier(i + tier_diff);
                tmp_tier2 = this.knowledge.getTier(i);
            } else {
                tmp_tier1 = this.knowledge.getTier(i);
                tmp_tier2 = this.knowledge.getTier(i + tier_diff);
            }
            A = tmp_tier1.get(indx_comp);
            B = tmp_tier2.get(indy_comp);
            if (A.equals(B)) continue;
            if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
            if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
            x1 = test.getVariable(A);
            y1 = test.getVariable(B);
            simListX.add(x1);
            simListY.add(y1);
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    /**
     * Returns the name of the object without any additional lag.x``
     *
     * @param obj The object whose name needs to be extracted.
     * @return The name of the object without any additional lag.
     */
    private String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }
}

