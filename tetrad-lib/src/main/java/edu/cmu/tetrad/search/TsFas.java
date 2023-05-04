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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * This is a copy of Fas.java for the SvarFCI algorithm. The main difference is that if an edge is removed, it will also
 * remove all homologous edges to preserve the time-repeating structure assumed by SvarFCI. Based on (but not identicial
 * to) code by Entner and Hoyer for their 2010 paper. Modified by DMalinsky 4/21/2016.
 *
 * @author Joseph Ramsey.
 * @author DMalinsky
 * @see Fas
 */
public class TsFas implements IFas {

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
     * The logger, by default the empty logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    private int numIndependenceJudgements;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

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

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public TsFas(Graph graph, IndependenceTest test) {
        this.graph = graph;
        this.test = test;
    }

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public TsFas(IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
    }

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

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return this.graph;
    }

    /**
     * Sets the depth--i.e., the maximum number of variables conditioned on in any
     * test, -1 for unlimited.
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    /**
     * Sets the knowledge used in the search.
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the number of independence tests.
     * @return This number.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Returns a map for x _||_ y | Z from {x, y} to Z.
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets an external graph.
     * @param externalGraph This grpah.
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Sets whether verbose output should be printed.
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @throws UnsupportedOperationException This method is not used.
     */
    @Override
    public Graph search(List<Node> nodes) {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * @throws UnsupportedOperationException This method is not used.
     */
    @Override
    public long getElapsedTime() {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Returns the nodes of the test.
     * @return This list.
     */
    @Override
    public List<Node> getNodes() {
        return this.test.getVariables();
    }

    /**
     * @throws UnsupportedOperationException This method is not used.
     */
    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Sets the output stream for printing, default is System.out.
     * @param out The print stream.
     * @see PrintStream
     */
    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }

    private boolean searchAtDepth0(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        List<Node> empty = Collections.emptyList();
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
                boolean skippair = false;

                Iterator<Node> itx1 = simListX.iterator();
                Iterator<Node> ity1 = simListY.iterator();
                while (itx1.hasNext() && ity1.hasNext()) {
                    Node x1 = itx1.next();
                    Node y1 = ity1.next();
                    String simX = x1.getName();
                    String simY = y1.getName();
                    if ((Objects.equals(xName, simX) && Objects.equals(yName, simY)) ||
                            (Objects.equals(xName, simY) && Objects.equals(yName, simX))) {
                        skippair = true;
                        System.out.println("Skipping pair x,y = " + xName + ", " + yName);
                        break;
                    }
                }

                if (skippair) continue;

                if (this.externalGraph != null) {
                    Node x2 = this.externalGraph.getNode(x.getName());
                    Node y2 = this.externalGraph.getNode(y.getName());

                    if (!this.externalGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }

                boolean independent;

                try {
                    this.numIndependenceTests++;
                    independent = test.checkIndependence(x, y, empty).independent();
                    System.out.println("############# independence given empty set: x,y " + x + ", " +
                            y + " independence = " + independent);
                } catch (Exception e) {
                    e.printStackTrace();
                    independent = false;
                }

                if (independent) {
                    this.numIndependenceJudgements++;
                }

                boolean noEdgeRequired =
                        this.knowledge.noEdgeRequired(x.getName(), y.getName());

//                getSepsets().setReturnEmptyIfNotSet(false); // added 05.30.2016
                if (independent && noEdgeRequired) {
//                    if (!getSepsets().isReturnEmptyIfNotSet()) {
                    getSepsets().set(x, y, empty);
                    System.out.println("$$$$$$$$$$$ look for similar pairs x,y = " + x + ", " + y);
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

    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
                this.knowledge.isForbidden(name2, name1)) {
            this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

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
                        List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent;

                        try {
                            this.numIndependenceTests++;
                            independent = test.checkIndependence(x, y, condSet).independent();
                        } catch (Exception e) {
                            independent = false;
                        }

                        if (independent) {
                            this.numIndependenceJudgements++;
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

    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    // removeSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private void removeSimilarPairs(Map<Node, Set<Node>> adjacencies, IndependenceTest test, Node x, Node y, List<Node> condSet) {
        System.out.println("Entering removeSimilarPairs method...");
        System.out.println("original independence: " + x + " and " + y + " conditional on " + condSet);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            System.out.println("Not removing similar pairs b/c variable pair includes time.");
            return;
        }
        for (Node tempNode : condSet) {
            if (tempNode.getName().equals("time")) {
                System.out.println("Not removing similar pairs b/c conditioning set includes time.");
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
                List<Node> condSetAB = new ArrayList<>();
                for (Node tempNode : condSet) {
                    int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    List<String> temptier = this.knowledge.getTier(ind_temptier);
                    int ind_temp = -1;
                    for (int j = 0; j < temptier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - ind_temptier;
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
                List<Node> condSetAB = new ArrayList<>();
                for (Node tempNode : condSet) {
                    int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    List<String> temptier = this.knowledge.getTier(ind_temptier);
                    int ind_temp = -1;
                    for (int j = 0; j < temptier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - ind_temptier;
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


    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(IndependenceTest test, Node x, Node y) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
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

        System.out.println("original independence: " + x + " and " + y);

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
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = this.knowledge.getTier(i + tier_diff);
                List<String> tmp_tier2 = this.knowledge.getTier(i);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
            } else {
                List<String> tmp_tier1 = this.knowledge.getTier(i);
                List<String> tmp_tier2 = this.knowledge.getTier(i + tier_diff);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
            }
            if (A.equals(B)) continue;
            if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
            if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
            x1 = test.getVariable(A);
            y1 = test.getVariable(B);
            System.out.println("Adding pair to simList = " + x1 + " and " + y1);
            simListX.add(x1);
            simListY.add(y1);
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    private String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }
}
