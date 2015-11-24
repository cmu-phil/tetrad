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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * This class provides the datastructures and methods for carrying out the Cyclic Causal Discovery algorithm (CCD)
 * described by Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and
 * Cooper eds.  The comments that appear below are keyed to the algorithm specification on pp. 269-271. </p> The search
 * method returns an instance of a Graph but it also constructs two lists of node triples which represent the underlines
 * and dotted underlines that the algorithm discovers.
 *
 * @author Frank C. Wimberly
 * @author Joseph Ramsey
 */
public final class Ccd implements GraphSearch {
    private IndependenceTest test;
    private int depth = -1;
    private IKnowledge knowledge;
    private List<Node> nodes;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose = true;
    private Graph initialGraph;

    /**
     * The arguments of the constructor are an oracle which answers conditional independence questions.  In the case of
     * a continuous dataset it will most likely be an instance of the IndTestCramerT class.  The second argument is not
     * used at this time.  The author (Wimberly) asked Thomas Richardson about how to use background knowledge and his
     * answer was that it should be applied after steps A-F had been executed.  Any implementation of the use of
     * background knowledge will be done later.
     *
     * @param knowledge Background knowledge. Not used yet--can be null.
     */
    public Ccd(IndependenceTest test, IKnowledge knowledge) {
        this.knowledge = knowledge;
        this.test = test;
        this.nodes = test.getVariables();
    }

    /**
     * The arguments of the constructor are an oracle which answers conditional independence questions.  In the case of
     * a continuous dataset it will most likely be an instance of the IndTestCramerT class.  The second argument is not
     * used at this time.  The author (Wimberly) asked Thomas Richardson about how to use background knowledge and his
     * answer was that it should be applied after steps A-F had been executed.  Any implementation of the use of
     * background knowledge will be done later.
     */
    public Ccd(IndependenceTest test) {
        this(test, new Knowledge2());
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). </p> Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {
        Map<Triple, List<Node>> supSepsets = new HashMap<>();

        //Step A
        TetradLogger.getInstance().log("info", "\nStep A");

        IFas search = new FasStableConcurrent(test);
        search.setDepth(depth);
        search.setKnowledge(getKnowledge());
        search.setVerbose(true);
        search.setInitialGraph(initialGraph);
        Graph psi = search.search();
        SepsetMap sepsetsFromFas = search.getSepsets();

        System.out.println("After FAS " + psi);

        SepsetProducer sepsets = new SepsetsMaxPValue(psi, test, null, depth);

        psi.reorientAllWith(Endpoint.CIRCLE);
        SearchGraphUtils.pcOrientbk(knowledge, psi, nodes);
        stepB(psi, sepsets);
        stepC(psi, sepsets, sepsetsFromFas);
        stepD(psi, sepsets, supSepsets, sepsetsFromFas);
        if (stepE(supSepsets, psi)) return psi;
        stepF(psi, sepsets, supSepsets);
        ruleR1(psi);


        TetradLogger.getInstance().log("graph", "\nFinal Graph:");
        TetradLogger.getInstance().log("graph", psi.toString());

        this.logger.log("graph", "\nReturning this graph: " + psi);

        return psi;
    }


    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return 0;
    }

    //======================================== PRIVATE METHODS ====================================//

    private boolean isArrowpointAllowed(Node from, Node to) {
        return !getKnowledge().isRequired(to.toString(), from.toString()) &&
                !getKnowledge().isForbidden(from.toString(), to.toString());
    }


    private void stepB(Graph psi, SepsetProducer sepsets) {
        addColliders(psi, sepsets, knowledge);
        addNoncolliders(psi, sepsets, knowledge);

//        List<Node> nodes1 = test.getVariables();
//
//        for (Node y : nodes1) {
//            List<Node> adjacentNodes = psi.getAdjacentNodes(y);
//
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                Node x = adjacentNodes.get(combination[0]);
//                Node z = adjacentNodes.get(combination[1]);
//
//                if (psi.isAdjacentTo(x, z)) {
//                    continue;
//                }
//
//                if (sepsets.isCollider(x, y, z)) {
//                    if (isArrowpointAllowed(x, y) && isArrowpointAllowed(z, y)) {
////                        psi.removeEdge(x, y);
////                        psi.removeEdge(y, z);
////                        psi.addDirectedEdge(x, y);
////                        psi.addDirectedEdge(z, y);
//
//                        psi.setEndpoint(x, y, Endpoint.ARROW);
//                        psi.setEndpoint(z, y, Endpoint.ARROW);
//                    }
//                } else if (sepsets.isNoncollider(x, y, z)) {
//                    psi.addUnderlineTriple(x, y, z);
//                }
//            }
//        }
    }

    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, verbose, knowledge);

        List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

//        Collections.sort(colliders, new Comparator<Triple>() {
//            public int compare(Triple o1, Triple o2) {
//                return -Double.compare(collidersPs.get(o1), collidersPs.get(o2));
//            }
//        });

        for (Triple collider : colliders) {
            Node a = collider.getX();
            Node b = collider.getY();
            Node c = collider.getZ();

            if (!(isArrowpointAllowed(a, b) && isArrowpointAllowed(c, b))) {
                continue;
            }

            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
                graph.removeEdge(a, b);
                graph.removeEdge(c, b);
                graph.addDirectedEdge(a, b);
                graph.addDirectedEdge(c, b);
//                graph.setEndpoint(a, b, Endpoint.ARROW);
//                graph.setEndpoint(c, b, Endpoint.ARROW);
            }
        }
    }

    private void addNoncolliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        List<Triple> nonColliders = findNoncollidersUsingSepsets(sepsetProducer, graph, verbose, knowledge);

        for (Triple collider : nonColliders) {
            Node a = collider.getX();
            Node b = collider.getY();
            Node c = collider.getZ();

            graph.addUnderlineTriple(a, b, c);
        }
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public Map<Triple, Double> findCollidersUsingSepsets(SepsetProducer sepsetProducer, Graph graph, boolean verbose, IKnowledge knowledge) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        Map<Triple, Double> colliders = new HashMap<>();

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = sepsetProducer.getSepset(a, c);

                if (sepset == null) continue;

//                if (sepsetProducer.getPValue() < test.getAlpha()) continue;

                if (!sepset.contains(b)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    colliders.put(new Triple(a, b, c), sepsetProducer.getPValue());

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        System.out.println("Done finding colliders");

        return colliders;
    }

    public List<Triple> findNoncollidersUsingSepsets(SepsetProducer sepsetProducer, Graph graph, boolean verbose, IKnowledge knowledge) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        List<Triple> noncolliders = new ArrayList<>();

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = sepsetProducer.getSepset(a, c);

                if (sepset == null) continue;

//                if (sepsetProducer.getPValue() < test.getAlpha()) continue;

                if (sepset.contains(b)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    noncolliders.add(new Triple(a, b, c));

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        System.out.println("Done finding noncolliders");

        return noncolliders;
    }


    private void stepC(Graph psi, SepsetProducer sepsets, SepsetMap sepsetsFromFas) {
        TetradLogger.getInstance().log("info", "\nStep C");

        EDGE:
        for (Edge edge : psi.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> adjx = psi.getAdjacentNodes(x);
            List<Node> adjy = psi.getAdjacentNodes(y);

            for (Node node : adjx) {
                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
                    continue EDGE;
                }
            }

            int count = 0;

            // Check each A
            for (Node a : nodes) {
                if (a == x) continue;
                if (a == y) continue;

                // Orientable...
                if (psi.getEndpoint(y, x) != Endpoint.CIRCLE) continue;

                //...A is not adjacent to X and A is not adjacent to Y...
                if (adjx.contains(a)) continue;
                if (adjy.contains(a)) continue;

                //...X is not in sepset<A, Y>...
                List<Node> sepset = sepsets.getSepset(a, y);
                if (sepset == null) sepset = sepsetsFromFas.get(a, y);

                if (sepset.contains(x)) continue;

                if (!sepsets.isIndependent(a, x, sepset)) {
                    count++;
                }
            }

            if (count >= 2) {
                System.out.println("C. Orienting " + psi.getEdge(x, y) + " as " + y + " --> " + x);
                psi.setEndpoint(y, x, Endpoint.ARROW);
                psi.setEndpoint(x, y, Endpoint.TAIL);
            }
        }
    }

    private void stepD(Graph psi, SepsetProducer sepsets, Map<Triple, List<Node>> supSepsets, SepsetMap fasSepsets) {
        TetradLogger.getInstance().log("info", "\nStep D");

        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        int m = 1;

        //maxCountLocalMinusSep is the largest cardinality of all sets of the
        //form Loacl(psi,A)\(SepSet<A,C> union {B,C})
        while (maxCountLocalMinusSep(psi, sepsets, fasSepsets, local) >= m) {
            for (Node b : nodes) {
                List<Node> adj = psi.getAdjacentNodes(b);

                if (adj.size() < 2) continue;

                ChoiceGenerator gen1 = new ChoiceGenerator(adj.size(), 2);
                int[] choice1;

                while ((choice1 = gen1.next()) != null) {
                    Node a = adj.get(choice1[0]);
                    Node c = adj.get(choice1[1]);

                    if (psi.isAdjacentTo(a, c)) {
                        continue;
                    }

                    if (b == c || b == a) {
                        continue;
                    }

                    // This should never happen..
                    if (supSepsets.get(new Triple(a, b, c)) != null) {
                        continue;
                    }

                    // A-->B<--C
                    if (!psi.isDefCollider(a, b, c)) {
                        continue;
                    }

                    //Compute the number of elements (count)
                    //in Local(psi,A)\(sepset<A,C> union {B,C})
                    Set<Node> localMinusSep = countLocalMinusSep(sepsets, fasSepsets, local, a, b, c);

                    int count = localMinusSep.size();

                    if (count < m) {
                        continue; //If not >= m skip to next triple.
                    }

                    //Compute the set T (setT) with m elements which is a subset of
                    //Local(psi,A)\(sepset<A,C> union {B,C})
                    Object[] v = new Object[count];
                    for (int i = 0; i < count; i++) {
                        v[i] = (localMinusSep.toArray())[i];
                    }

                    ChoiceGenerator generator = new ChoiceGenerator(count, m);
                    int[] choice;

                    while ((choice = generator.next()) != null) {
                        Set<Node> setT = new LinkedHashSet<Node>();
                        for (int i = 0; i < m; i++) {
                            setT.add((Node) v[choice[i]]);
                        }

                        setT.add(b);
                        List<Node> sepset = sepsets.getSepset(a, c);
                        if (sepset == null) sepset = fasSepsets.get(a, c);
                        setT.addAll(sepset);

                        List<Node> listT = new ArrayList<Node>(setT);

                        //Note:  B is a collider between A and C (see above).
                        //If anode and cnode are d-separated given T union
                        //sep[a][c] union {bnode} create a dotted underline triple
                        //<A,B,C> and record T union sepset<A,C> union {B} in
                        //supsepset<A,B,C> and in supsepset<C,B,A>

                        if (test.isIndependent(a, c, listT)) {
                            supSepsets.put(new Triple(a, b, c), listT);

                            psi.addDottedUnderlineTriple(a, b, c);
                            TetradLogger.getInstance().log("underlines", "Adding dotted underline: " +
                                    new Triple(a, b, c));

                            break;
                        }
                    }
                }
            }

            m++;
        }
    }

    /**
     * Computes and returns the size (cardinality) of the largest set of the form Local(psi,A)\(SepSet<A,C> union {B,C})
     * where B is a collider between A and C and where A and C are not adjacent.  A, B and C should not be a dotted
     * underline triple.
     */
    private static int maxCountLocalMinusSep(Graph psi, SepsetProducer sep, SepsetMap fasSepsets,
                                             Map<Node, List<Node>> loc) {
        List<Node> nodes = psi.getNodes();
        int maxCount = -1;

        for (Node b : nodes) {
            List<Node> adjacentNodes = psi.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (psi.isAdjacentTo(a, c)) {
                    continue;
                }

                // Want B to be a collider between A and C but not for
                //A, B, and C to be an underline triple.
                if (psi.isUnderlineTriple(a, b, c)) {
                    continue;
                }

                //Is B a collider between A and C?
                if (!psi.isDefCollider(a, b, c)) {
                    continue;
                }

                Set<Node> localMinusSep = countLocalMinusSep(sep, fasSepsets, loc,
                        a, b, c);
                int count = localMinusSep.size();

                if (count > maxCount) {
                    maxCount = count;
                }
            }
        }

        return maxCount;
    }

    /**
     * For a given GaSearchGraph psi and for a given set of sepsets, each of which is associated with a pair of vertices
     * A and C, computes and returns the set Local(psi,A)\(SepSet<A,C> union {B,C}).
     */
    private static Set<Node> countLocalMinusSep(SepsetProducer sepset, SepsetMap fasSepsets,
                                                Map<Node, List<Node>> local, Node anode,
                                                Node bnode, Node cnode) {
        Set<Node> localMinusSep = new HashSet<>();
        localMinusSep.addAll(local.get(anode));
        List<Node> sepset1 = sepset.getSepset(anode, cnode);
//        if (sepset1 == null) sepset1 = fasSepsets.get(anode, cnode);
        localMinusSep.removeAll(sepset1);
        localMinusSep.remove(bnode);
        localMinusSep.remove(cnode);

        return localMinusSep;
    }


    private boolean stepE(Map<Triple, List<Node>> supSepset, Graph psi) {
        TetradLogger.getInstance().log("info", "\nStep E");

        if (nodes.size() < 4) {
            return true;
        }

        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();

            List<Node> aAdj = psi.getAdjacentNodes(a);

            for (Node d : aAdj) {
                if (d == b) continue;

                if (supSepset.get(triple).contains(d)) {
                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                }

            }

            for (Node d : aAdj) {
                if (d == b) continue;

                if (supSepset.get(triple).contains(d)) {
                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                }
            }
        }

        return false;
    }


    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, List<Node>> supSepsets) {
        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            Set<Node> adj = new HashSet<>(psi.getAdjacentNodes(a));
            adj.addAll(psi.getAdjacentNodes(c));

            for (Node d : adj) {
                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                    continue;
                }

                if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                    continue;
                }

                //...and D is not adjacent to both A and C in psi...
                if (psi.isAdjacentTo(a, d) && psi.isAdjacentTo(c, d)) {
                    continue;
                }

                //...and B and D are adjacent...
                if (!psi.isAdjacentTo(b, d)) {
                    continue;
                }

                Set<Node> supSepUnionD = new HashSet<>();
                supSepUnionD.add(d);
                supSepUnionD.addAll(supSepsets.get(triple));
                List<Node> listSupSepUnionD = new ArrayList<>(supSepUnionD);

                //If A and C are a pair of vertices d-connected given
                //SupSepset<A,B,C> union {D} then orient Bo-oD or B-oD
                //as B->D in psi.
                if (!sepsets.isIndependent(a, c, listSupSepUnionD)) {
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                }
            }
        }
    }

    private void ruleR1(Graph psi) {
        boolean changed;

        do {
            changed = rulesR1cycle(psi);
        } while (changed);
    }

    private List<Node> local(Graph psi, Node z) {
        List<Node> local = new ArrayList<>();

        //Is X p-adjacent to V in psi?
        for (Node x : nodes) {
            if (x == z) {
                continue;
            }

            if (psi.isAdjacentTo(z, x)) {
                local.add(x);
            }

            //or is there a collider between X and V in psi?
            for (Node y : nodes) {
                if (y == z || y == x) {
                    continue;
                }

                if (psi.isDefCollider(x, y, z)) {
                    if (!local.contains(x)) {
                        local.add(x);
                    }
                }
            }
        }

        return local;
    }

    private boolean rulesR1cycle(Graph graph) {
        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node B : nodes) {
            List<Node> adj = graph.getAdjacentNodes(B);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesn't do diff orders, so must switch A & C around.
                changed = changed || ruleR1(A, B, C, graph);
                changed = changed || ruleR1(C, B, A, graph);
            }
        }

        return changed;
    }

    private boolean ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return false;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!graph.isUnderlineTriple(a, b, c)) {
                return false;
            }

            graph.removeEdge(b, c);
            graph.addDirectedEdge(b, c);

            return true;
        }

        return false;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }
}






