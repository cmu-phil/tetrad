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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.RandomUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Generates random DAGs uniformly with certain classes of DAGs using variants
 * of Markov chain algorithms by Malancon, Dutour, and Philippe. Pieces of the
 * infrastructure of the algorithm are adapted from the the BNGenerator class by
 * Jaime Shinsuke Ide jaime.ide@poli.usp.br, released under the GNU General
 * Public License, for which the following statement is being included as part
 * of the license agreement:
 * <p/>
 * "The BNGenerator distribution is free software; you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation (either version 2 of the License
 * or, at your option, any later version), provided that this notice and the
 * name of the author appear in all copies. </p> "If you're using the software,
 * please notify jaime.ide@poli.usp.br so that you can receive updates and
 * patches. BNGenerator is distributed "as is", in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU
 * General Public License along with the BNGenerator distribution. If not, write
 * to the Free Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139,
 * USA."
 *
 * @author Joseph Ramsey
 */
public final class UniformGraphGenerator {
    public static final int ANY_DAG = 0;
    public static final int CONNECTED_DAG = 1;

    /**
     * Indicates the structural assumption. May be ANY_DAG, CONNECTED_DAG.
     */
    private int structure;

    /**
     * The number of nodes in a graph. The default is 4.
     */
    private int numNodes;

    /**
     * The maximum indegree for a node in a graph. The default is 3.
     */
    private int maxInDegree;

    /**
     * The maximum outdegree of a node in a graph. The defualt is 3.
     */
    private int maxOutDegree;

    /**
     * The maximum degree of a node in a graph. The default is the maximum
     * number possible (the value -1 is used for this).
     */
    private int maxDegree;

    /**
     * The maximum number of edges in the graph. The default is the number of
     * nodes minus 1.
     */
    private int maxEdges;

    /**
     * The number of iterations for the Markov chain process.
     */
    private int numIterations;

    /**
     * Matrix of parents for each node. parentMatrix[i][0] indicates the number
     * of parents; parentMatrix[i][k] represents the (k-1)'th parent, k =
     * 1...max.
     */
    private int[][] parentMatrix;

    /**
     * Matrix of parents for each node. childMatrix[i][0] indicates the number
     * of parents; childMatrix[i][k] represents the (k-1)'th child, k =
     * 1...max.
     */
    private int[][] childMatrix;

    /**
     * Parent of random edge. 0 is the default parent node.
     */
    private int randomParent = 0;

    /**
     * Child of random edge. 0 is the default child node.
     */
    private int randomChild = 1;

    /**
     * The random source.
     */
    private final RandomUtil randomUtil = RandomUtil.getInstance();
//    RandomUtil randomUtil = new SeededRandomUtil(23333342L);

    //===============================CONSTRUCTORS==========================//

    /**
     * Constructs a random graph generator for the given structure.
     *
     * @param structure One of ANY_DAG, POLYTREE, or CONNECTED_DAG.
     */
    public UniformGraphGenerator(int structure) {
        switch (structure) {
            case ANY_DAG:
            case CONNECTED_DAG:
                break;
            default:
                throw new IllegalArgumentException("Unrecognized structure.");
        }

        this.structure = structure;
        this.numNodes = 4;
        this.maxInDegree = 3;
        this.maxOutDegree = 3;
        this.maxDegree = 6;
        this.maxEdges = numNodes - 1;

        // Determining the number of iterations for the chain to converge is a
        // difficult task. This value follows the DagAlea (see Melancon;Bousque,
        // 2000) suggestion, and we verified that this number is satisfatory. (Ide.)
        this.numIterations = 6 * numNodes * numNodes;
    }

    //===============================PUBLIC METHODS========================//

    private int getNumNodes() {
        return numNodes;
    }

    /**
     * Sets the number of nodes and resets all of the other parameters to
     * default values accordingly.
     *
     * @param numNodes Must be an integer >= 4.
     */
    public void setNumNodes(int numNodes) {
        if (numNodes < 1) {
            throw new IllegalArgumentException("Number of nodes must be >= 1.");
        }

        this.numNodes = numNodes;
        this.maxDegree = numNodes - 1;
        this.maxInDegree = numNodes - 1;
        this.maxOutDegree = numNodes - 1;
        this.maxEdges = numNodes - 1;
        this.numIterations = 6 * numNodes * numNodes;

        if (this.numIterations > 300000000) {
            this.numIterations = 300000000;
        }

        this.parentMatrix = null;
        this.childMatrix = null;
    }

    private int getMaxDegree() {
        return maxDegree;
    }

    /**
     * Sets the maximum degree of any nodes in the graph.
     *
     * @param maxDegree An integer between 3 and numNodes - 1, inclusively.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < 3) {
            throw new IllegalArgumentException("Degree of nodes must be >= 3.");
        }

        this.maxDegree = maxDegree;
    }

    private int getMaxInDegree() {
        return maxInDegree;
    }

    public void setMaxInDegree(int maxInDegree) {
        if (ANY_DAG == getStructure() && getMaxInDegree() < 0) {
            throw new IllegalArgumentException("Max indegree must be >= 1 " +
                    "when generating DAGs without the assumption of " +
                    "connectedness.");
        } else if (CONNECTED_DAG == getStructure() && getMaxInDegree() < 2) {
            throw new IllegalArgumentException("Max indegree must be >= 2 " +
                    "when generating DAGs under the assumption of " +
                    "connectedness.");
        }

        this.maxInDegree = maxInDegree;
    }

    private int getMaxOutDegree() {
        return maxOutDegree;
    }

    public void setMaxOutDegree(int maxOutDegree) {
        if (ANY_DAG == getStructure() && getMaxInDegree() < 1) {
            throw new IllegalArgumentException("Max indegree must be >= 1 " +
                    "when generating DAGs without the assumption of " +
                    "connectedness.");
        }

        if (CONNECTED_DAG == getStructure() && getMaxInDegree() < 2) {
            throw new IllegalArgumentException("Max indegree must be >= 2 " +
                    "when generating DAGs under the assumption of " +
                    "connectedness.");
        }

        this.maxOutDegree = maxOutDegree;
    }

    private int getMaxEdges() {
        return maxEdges;
    }

    private int getMaxPossibleEdges() {
        return getNumNodes() * getMaxDegree() / 2;
    }

    public void setMaxEdges(int maxEdges) {
        if (maxEdges < 0) {
            throw new IllegalArgumentException("Max edges must be >= 0.");
        }

        if (maxEdges > getMaxPossibleEdges()) {
            maxEdges = getMaxPossibleEdges();
//            System.out.println("\nThe value maxEdges = " +
//                    maxEdges + " is too high; it has been set to the maximum " +
//                    "number of possible edges, which is " +
//                    getMaxPossibleEdges() + ".");
        }

        this.maxEdges = maxEdges;
    }

    private int getNumIterations() {
        return numIterations;
    }

    public void setNumIterations(int numIterations) {
        this.numIterations = numIterations;
    }

    private int getStructure() {
        return structure;
    }

    public void generate() {
        if (ANY_DAG == getStructure()) {
            generateArbitraryDag();
        } else if (CONNECTED_DAG == getStructure()) {
            generateConnectedDag();
        } else {
            throw new IllegalStateException("Unknown structure type.");
        }
    }

    public Graph getDag() {
        //System.out.println("Converting to DAG");

        List<Node> nodes = new ArrayList<>();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(0);

        int numDigits = (int) Math.ceil(Math.log(numNodes) / Math.log(10.0));
        nf.setMinimumIntegerDigits(numDigits);
        nf.setGroupingUsed(false);


        for (int i = 1; i <= getNumNodes(); i++) {
            GraphNode node = new GraphNode("X" + nf.format(i));
//            dag.addIndex(node);
            nodes.add(node);
        }

        return getDag(nodes);
    }

    public Graph getDag(List<Node> nodes) {
        if (nodes.size() != getNumNodes()) {
            throw new IllegalArgumentException("Only " + nodes.size() + " nodes were provided, but the " +
                    "simulated graph has " + getNumNodes() + ".");
        }

        Graph dag = new EdgeListGraph(nodes);

        for (int i = 0; i < getNumNodes(); i++) {
            Node child = nodes.get(i);

            if (parentMatrix[i][0] != 1) {
                for (int j = 1; j < parentMatrix[i][0]; j++) {
                    Node parent = nodes.get(parentMatrix[i][j]);
                    dag.addDirectedEdge(parent, child);
//                    System.out.println("Added " + dag.getEdge(parent, child));
                }
            }
        }

//        System.out.println("Arranging in circle.");
        GraphUtils.circleLayout(dag, 200, 200, 150);

        //System.out.println("DAG conversion completed.");

        return dag;
    }

    public void printEdges() {
        System.out.println("Edges:");
        for (int i = 0; i < getNumNodes(); i++) {
            for (int j = 1; j < childMatrix[i][0]; j++) {
                System.out.println("\t" + i + " --> " + childMatrix[i][j]);
            }
        }
    }

    public String toString() {
        String buf = "\nStructural information for generated graph:" +
                "\n\tNumber of nodes:" + getNumNodes() +
                "\n\tMax degree for each node:" + getMaxDegree() +
                "\n\tMaximum number of incoming edges for each node:" +
                getMaxInDegree() +
                "\n\tMaximum number of outgoing edges for each node:" +
                getMaxOutDegree() +
                "\n\tMaximum total number of edges:" + getMaxEdges() +
                " of " + getNumNodes() * getMaxDegree() / 2 +
                " possibles" +
                "\n\tNumber of transitions between samples:" +
                getNumIterations();

        return buf;
    }

    //================================PRIVATE METHODS======================//

    private void generateArbitraryDag() {
        initializeGraphAsEmpty();

        if (getNumNodes() <= 1) {
            return;
        }

        int numEdges = 0;

        for (int i = 0; i < getNumIterations(); i++) {
//            if (i % 10000000 == 0) System.out.println("..." + i);

            sampleEdge();

            if (edgeExists()) {
                removeEdge();
                numEdges--;
            } else {
                if ((numEdges < getMaxEdges() && maxDegreeNotExceeded() &&
                        maxIndegreeNotExceeded() && maxOutdegreeNotExceeded() &&
                        isAcyclic())) {
                    addEdge();
                    numEdges++;
                }
            }
        }
    }

    /**
     * This is the algorithm in Melancon and Philippe, "Generating connected
     * acyclic digraphs uniformly at random" (draft of March 25, 2004). In
     * addition to acyclicity, some other conditions have been added in.
     */
    private void generateConnectedDag() {
        initializeGraphAsChain();

        if (getNumNodes() <= 1) {
            return;
        }

        int totalEdges = getNumNodes() - 1;

        while (isDisconnecting()) {
            sampleEdge();

            if (edgeExists()) {
                continue;
            }

            if (isAcyclic() && maxDegreeNotExceeded()) {
                addEdge();
                totalEdges++;
            }
        }

        for (int i = 0; i < getNumIterations(); i++) {
            sampleEdge();

            if (edgeExists()) {
                if (isDisconnecting()) {
                    removeEdge();
                    reverseDirection();

                    if (totalEdges < getMaxEdges() && maxDegreeNotExceeded() &&
                            maxIndegreeNotExceeded() &&
                            maxOutdegreeNotExceeded() && isAcyclic()) {
                        addEdge();
                    } else {
                        reverseDirection();
                        addEdge();
                    }
                } else {
                    removeEdge();
                    totalEdges--;
                }
            } else {
                if (totalEdges < getMaxEdges() && maxDegreeNotExceeded() &&
                        maxIndegreeNotExceeded() && maxOutdegreeNotExceeded() &&
                        isAcyclic()) {
                    addEdge();
                    totalEdges++;
                }
            }
        }
    }

    private void reverseDirection() {
        int temp = randomChild;
        randomChild = randomParent;
        randomParent = temp;
    }

    /**
     * @return true if the edge parent-->child exists in the graph.
     */
    private boolean edgeExists() {
        for (int i = 1; i < parentMatrix[randomChild][0]; i++) {
            if (parentMatrix[randomChild][i] == randomParent) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if the degree of the getModel nodes randomParent and
     * randomChild do not exceed maxDegree.
     */
    private boolean maxDegreeNotExceeded() {
        int parentDegree = parentMatrix[randomParent][0] +
                childMatrix[randomParent][0] - 1;
        int childDegree =
                parentMatrix[randomChild][0] + childMatrix[randomChild][0] - 1;
        return parentDegree <= getMaxDegree() && childDegree <= getMaxDegree();
    }

    /**
     * @return true if the degrees of the getModel nodes randomParent and
     * randomChild do not exceed maxIndegree.
     */
    private boolean maxIndegreeNotExceeded() {
        return parentMatrix[randomChild][0] <= getMaxInDegree();
    }

    /**
     * @return true if the degrees of the getModel nodes randomParent and
     * randomChild do not exceed maxOutdegree.
     */
    private boolean maxOutdegreeNotExceeded() {
        return childMatrix[randomParent][0] <= getMaxOutDegree();
    }

    /**
     * @return true iff the random edge randomParent-->randomChild would be
     * disconnecting were it to be removed.
     */
    private boolean isDisconnecting() {
        boolean visited[] = new boolean[getNumNodes()];
        int list[] = new int[getNumNodes()];
        int index = 0;
        int lastIndex = 1;
        list[0] = 0;
        visited[0] = true;
        while (index < lastIndex) {
            int currentNode = list[index];

            // verify parents of getModel node
            for (int i = 1; i < parentMatrix[currentNode][0]; i++) {
                if (currentNode == randomChild &&
                        parentMatrix[currentNode][i] == randomParent) {
                    continue;
                }

                if (!visited[parentMatrix[currentNode][i]]) {
                    list[lastIndex] = parentMatrix[currentNode][i];
                    visited[parentMatrix[currentNode][i]] = true;
                    lastIndex++;
                }
            }

            // verify children of getModel node
            for (int i = 1; i < childMatrix[currentNode][0]; i++) {
                if (currentNode == randomParent &&
                        childMatrix[currentNode][i] == randomChild) {
                    continue;
                }

                if (!visited[childMatrix[currentNode][i]]) {
                    list[lastIndex] = childMatrix[currentNode][i];
                    visited[childMatrix[currentNode][i]] = true;
                    lastIndex++;
                }
            }

            index++;
        }

        // verify whether all nodes were visited
        for (boolean aVisited : visited) {
            if (!aVisited) {
                return true;
            }
        }

        return false;
    }


    /**
     * @return true if the graph is still acyclic after the last edge was added.
     * This method only works before adding the random edge, not after removing
     * an edge.
     */
    private boolean isAcyclic() {
        boolean visited[] = new boolean[getNumNodes()];
        boolean noCycle = true;
        int list[] = new int[getNumNodes() + 1];
        int index = 0;
        int lastIndex = 1;
        list[0] = randomParent;
        visited[randomParent] = true;
        while (index < lastIndex && noCycle) {
            int currentNode = list[index];
            int i = 1;

            // verify parents of getModel node
            while ((i < parentMatrix[currentNode][0]) && noCycle) {
                if (!visited[parentMatrix[currentNode][i]]) {
                    if (parentMatrix[currentNode][i] != randomChild) {
                        list[lastIndex] = parentMatrix[currentNode][i];
                        lastIndex++;
                    } else {
                        noCycle = false;
                    }
                    visited[parentMatrix[currentNode][i]] = true;
                }
                i++;
            }
            index++;
        }
        //System.out.println("\tnoCycle:"+noCycle);
        return noCycle;
    }

    /**
     * Initializes the graph to have no edges.
     */
    private void initializeGraphAsEmpty() {
        int max =
                Math.max(getMaxInDegree() + getMaxOutDegree(), getMaxDegree());
        max += 1;

        parentMatrix = new int[getNumNodes()][max];
        childMatrix = new int[getNumNodes()][max];

        for (int i = 0; i < getNumNodes(); i++) {
            parentMatrix[i][0] = 1; //set first node
            childMatrix[i][0] = 1;
        }

        for (int i = 0; i < getNumNodes(); i++) {
            for (int j = 1; j < max; j++) {
                parentMatrix[i][j] = -5; //set first node
                childMatrix[i][j] = -5;
            }
        }
    }

    /**
     * Initializes the graph as a simple ordered tree, 0-->1-->2-->...-->n.
     */
    private void initializeGraphAsChain() {
        parentMatrix = new int[getNumNodes()][getMaxDegree() + 2];
        childMatrix = new int[getNumNodes()][getMaxDegree() + 2];

        for (int i = 0; i < getNumNodes(); i++) {
            for (int j = 1; j < getMaxDegree() + 1; j++) {
                parentMatrix[i][j] = -5; //set first node
                childMatrix[i][j] = -5;
            }
        }
        parentMatrix[0][0] = 1; //set first node
        childMatrix[0][0] = 2;    //set first node
        childMatrix[0][1] = 1;    //set first node
        parentMatrix[getNumNodes() - 1][0] = 2;  //set last node
        parentMatrix[getNumNodes() - 1][1] = getNumNodes() - 2;  //set last node
        childMatrix[getNumNodes() - 1][0] = 1;     //set last node
        for (int i = 1; i < (getNumNodes() - 1); i++) {  // set the other nodes
            parentMatrix[i][0] = 2;
            parentMatrix[i][1] = i - 1;
            childMatrix[i][0] = 2;
            childMatrix[i][1] = i + 1;
        }
    }

    /**
     * Sets randomParent-->randomChild to a random edge, chosen uniformly.
     */
    private void sampleEdge() {
        int rand = randomUtil.nextInt(getNumNodes() * (getNumNodes() - 1));
        randomParent = rand / (getNumNodes() - 1);
        int rest = rand - randomParent * (getNumNodes() - 1);
        if (rest >= randomParent) {
            randomChild = rest + 1;
        } else {
            randomChild = rest;
        }
    }

    /**
     * Adds the edge randomParent-->randomChild to the graph.
     */
    private void addEdge() {
        childMatrix[randomParent][childMatrix[randomParent][0]] = randomChild;
        childMatrix[randomParent][0]++;
        parentMatrix[randomChild][parentMatrix[randomChild][0]] = randomParent;
        parentMatrix[randomChild][0]++;
    }

    /**
     * Removes the edge randomParent-->randomChild from the graph.
     */
    private void removeEdge() {
        boolean go = true;
        int lastNode;
        int proxNode;
        int atualNode;
        if ((parentMatrix[randomChild][0] != 1) &&
                (childMatrix[randomParent][0] != 1)) {
            lastNode =
                    parentMatrix[randomChild][parentMatrix[randomChild][0] - 1];
            for (int i = (parentMatrix[randomChild][0] - 1); (i > 0 && go); i--) { // remove element from parentMatrix
                atualNode = parentMatrix[randomChild][i];
                if (atualNode != randomParent) {
                    proxNode = atualNode;
                    parentMatrix[randomChild][i] = lastNode;
                    lastNode = proxNode;
                } else {
                    parentMatrix[randomChild][i] = lastNode;
                    go = false;
                }
            }
            if ((childMatrix[randomParent][0] != 1) &&
                    (childMatrix[randomParent][0] != 1)) {
                lastNode = childMatrix[randomParent][
                        childMatrix[randomParent][0] - 1];
                go = true;
                for (int i = (childMatrix[randomParent][0] - 1); (i > 0 &&
                        go); i--) { // remove element from childMatrix
                    atualNode = childMatrix[randomParent][i];
                    if (atualNode != randomChild) {
                        proxNode = atualNode;
                        childMatrix[randomParent][i] = lastNode;
                        lastNode = proxNode;
                    } else {
                        childMatrix[randomParent][i] = lastNode;
                        go = false;
                    }
                } // end of for
            }
            childMatrix[randomParent][(childMatrix[randomParent][0] - 1)] = -4;
            childMatrix[randomParent][0]--;
            parentMatrix[randomChild][(parentMatrix[randomChild][0] - 1)] = -4;
            parentMatrix[randomChild][0]--;
        }
    }
}






