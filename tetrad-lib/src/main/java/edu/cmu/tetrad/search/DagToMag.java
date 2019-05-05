package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class DagToMag {

    private final Graph dag;

    /*
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    // /**
    //  * Glag for complete rule set, true if should use complete rule set, false otherwise.
    //  */
    // private boolean completeRuleSetUsed = false;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private int maxPathLength = -1;
    private Graph trueMag;  // if PAG, truePag

    //============================CONSTRUCTORS============================//

    public DagToMag(Graph dag) {
        this.dag = dag;
    }

    //========================PUBLIC METHODS==========================//

    public Graph convert() {
        logger.log("info", "Starting DAG to MAG_of_the_true_DAG.");

        if (verbose) {
            System.out.println("DAG to MAG_of_the_true_DAG: Starting adjacency search (should have same adjacency as PAG_of_the_true_DAG)");
        }

        Graph graph = calcAdjacencyGraph();

        // start orienting according to ancestral or non-ancestral info from true DAG
        if (verbose) {
            System.out.println("DAG to MAG_of_the_true_DAG: Starting ancestral information gathering");
        }

        orientAdjacencyGraphOfPag(graph, dag);

        if (verbose) {
            System.out.println("Finishing orientation");
        }

        return graph;
    }

    private Graph calcAdjacencyGraph() {
        List<Node> allNodes = dag.getNodes();
        List<Node> measured = new ArrayList<Node>();

        for (Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        Graph graph = new EdgeListGraphSingleConnections(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

                final List<Node> inducingPath = GraphUtils.getInducingPath(n1, n2, dag);

                boolean exists = inducingPath != null;

                if (exists) {
                    graph.addEdge(Edges.directedEdge(n1, n2));
                }
            }
        }

        return graph;
    }

    private void orientAdjacencyGraphOfPag(Graph graph, Graph dag) {
        graph.reorientAllWith(Endpoint.TAIL);

        List<Node> allNodes = dag.getNodes();
        List<Node> measured = new ArrayList<Node>();

        for (Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        // get ancestral and non-ancestral information from DAG
        for (Node n : measured) {
            List<Node> adjn = graph.getAdjacentNodes(n);

            if (adjn.size() < 2) continue;

            for (int i = 0; i < adjn.size(); i++) {
                Node c = adjn.get(i);

                boolean nIsAncestorOfC = graph.isAncestorOf(n, c);
                boolean cIsAncestorOfN = graph.isAncestorOf(c, n);

                if (true == nIsAncestorOfC && false == cIsAncestorOfN) {  // n->c
                    graph.setEndpoint(n, c, Endpoint.ARROW);
                } else if (false == nIsAncestorOfC && true == cIsAncestorOfN) {  // n<-c
                    graph.setEndpoint(c, n, Endpoint.ARROW);
                } else if (false == nIsAncestorOfC && false == cIsAncestorOfN) {  // n<->c
                    graph.setEndpoint(n, c, Endpoint.ARROW);
                    graph.setEndpoint(c, n, Endpoint.ARROW);
                } else {  // cycle in the original DAG
                    throw new IllegalArgumentException("Cycle found in input DAG");
                }
            }
        }
    }

    public static boolean existsInducingPathInto(Node x, Node y, Graph graph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<Node>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            Edge edge = graph.getEdge(x, b);
            if (!edge.pointsTowards(x)) continue;

            if (GraphUtils.existsInducingPathVisit(graph, x, b, x, y, path)) {
                return true;
            }
        }

        return false;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

//    /**
//     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
//     * should be used. False by default.
//     */
//    public boolean isCompleteRuleSetUsed() {
//        return completeRuleSetUsed;
//    }
//
//     /**
//      * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
//      *                            set of the original FCI) should be used. False by default.
//      */
//     public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
//         this.completeRuleSetUsed = completeRuleSetUsed;
//     }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public Graph getTrueMag() {
        return trueMag;
    }

    public void setTruePag(Graph trueMag) {
        this.trueMag = trueMag;
    }
}
