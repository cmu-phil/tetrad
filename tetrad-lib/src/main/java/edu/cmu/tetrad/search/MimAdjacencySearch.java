///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * <p>This class implements the adjacency search step of the Generalize MimBuild algorithm as described on p 362 of CPS,
 * 2nd edition.  The only function that is publically available is adjSearch().</p>
 *
 * @author Ricardo Silva (rbas@cs.cmu.edu)
 */
public final class MimAdjacencySearch {
    private final Graph graph;
    private final IndependenceTest ind;
    private final IKnowledge knowledge;
    private final List<Node> latents;
    private int depth = Integer.MAX_VALUE;

    /**
     * Constructs a new MimAdjacencySearch. IT IS ASSUMED THAT THE INDEPENDENCE CHECKER IS OF CLASS IndTestMimBuild
     */
    public MimAdjacencySearch(final Graph graph, final IndependenceTest ind,
                              final IKnowledge knowledge, final List<Node> latents) {
        this.graph = graph;
        this.ind = ind;
        this.knowledge = knowledge;
        this.latents = latents;
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
    public SepsetMap adjSearch() {

        int n = 0;
        final SepsetMap sepset = new SepsetMap();

        while (adjStep(this.graph, this.ind, this.knowledge, sepset, n) && getDepth() > n) {
            n++;
        }

        return sepset;
    }

    /**
     * Return an array of Nodes who can be conditional checks can be done on stipulated by temporal tiers </p> Shane
     * Harwood harwood+@andrew.cmu.edu
     */
    private Object[] forbidFilter(final Set<String> set1, final String x, final IKnowledge bk) {
        final Iterator<String> it = set1.iterator();
        final List<String> arr = new LinkedList<>();

        while (it.hasNext()) {
            final String z = it.next();

            if (!bk.isForbidden(z, x)) {
                arr.add(z);
            }
        }

        return arr.toArray();
    }

    /**
     * Removes edges from the a graph based on conditional independence facts using conditioning sets of size n and
     * records these conditioning set ("separating sets").  More specifically, for every node x and node y (connected to
     * x), and every set s of size n of nodes connected to x (not including y), checks if x and y are independent
     * conditional on s and, if so, removes the edge from x to y from the graph and records the separating set used to
     * remove that edge.
     *
     * @param knowledge background knowledge
     * @param n         the size of sets to check.
     * @param graph     the graph being checked.
     * @param ind       the independence checker.
     * @param sepset    As edges are removed, their separating sets (the sets conditional on which their endpoints are
     *                  independent) are recorded in this sepset.  This is for use later on in the algorithm.
     * @return false if no independencies are found for this step.  (This means we are finished with adjacency search.)
     */
    private boolean adjStep(final Graph graph, final IndependenceTest ind,
                            final IKnowledge knowledge, final SepsetMap sepset, final int n) {
        // Note: as a stateful object, all of these arguments should belong
        // to the state.  There is no need to pass them in every time.
        final Iterator<Node> it = this.latents.iterator();
        boolean result = false;
        final List<Node> visited = new LinkedList<>();    //list of visited nodes

        // for each node x...
        while (it.hasNext()) {
            final Node nodeX = it.next();
            final Set<Node> set = new HashSet<>();

            for (final Node node : graph.getAdjacentNodes(nodeX)) {
                if (this.latents.contains(node)) {
                    set.add(node);
                }
            }

            //trim all unnecessary comapres, we have already visited them
            for (final Node aVisited : visited) {
                set.remove(aVisited);
            }

            visited.add(nodeX);

            final Iterator<Node> it1 = (new HashSet<>(set)).iterator();

            // for each node y connected to x ...
            while (it1.hasNext()) {
                final Node nodeY = it1.next();

                final Set<String> set1 = new HashSet<>();
                for (final Node node : graph.getAdjacentNodes(nodeX)) {
                    if (this.latents.contains(node)) {
                        set1.add(node.toString());
                    }
                }

                final Set<String> set2 = new HashSet<>();    //find parents of Y
                for (final Node node : graph.getAdjacentNodes(nodeY)) {
                    if (this.latents.contains(node)) {
                        set2.add(node.toString());
                    }
                }

                set1.addAll(set2);
                set1.remove(nodeY.toString());
                set1.remove(nodeX.toString());

                // seta: all nodes adjacent to x other than y and come
                // before x or before y temporally
                final Object[] seta = forbidFilter(set1, nodeX.getName(), knowledge);

                if (seta.length >= n) {
                    result = true;

                    final ChoiceGenerator cg = new ChoiceGenerator(seta.length, n);
                    int[] subset;

                    // for each subset of size n ...
                    while ((subset = cg.next()) != null) {
                        final List<Node> condSet = MimAdjacencySearch.asList(subset, seta);
                        if (ind.isIndependent(nodeX, nodeY, condSet) &&
                                knowledge.noEdgeRequired(nodeX.getName(),
                                        nodeY.getName())) {
                            //                            double pValue = ind.getLikelihoodRatioP();
                            //                            SearchLogUtils.logIndependenceFact(nodeX, nodeY, condSet,
                            //                                    pValue, LOGGER);
                            set.remove(nodeY);
                            graph.removeEdge(nodeX, nodeY);
                            sepset.set(nodeX, nodeY, new LinkedList(condSet));

                            break;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * The ChoiceGenerator supplies an int array.  This class will transform that int array (interpretted as indices
     * array of objects) into an  into a List of objects
     *
     * @param i indices into o
     * @param o the objects from which we obtain our list
     * @return the list described above.
     */
    private static List asList(final int[] i, final Object[] o) {
        final Object[] temp = new Object[i.length];

        for (int a = 0; a < i.length; a++) {
            temp[a] = o[i[a]];
        }

        return Arrays.asList(temp);
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(final int depth) {
        this.depth = depth;
    }
}





