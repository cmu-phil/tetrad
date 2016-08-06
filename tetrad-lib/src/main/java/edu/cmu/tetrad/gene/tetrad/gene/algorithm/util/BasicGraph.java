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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.util;

/**
 * Basic functionality that all graph-derived classes should provide.
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import java.io.*;

public abstract class BasicGraph {

    /**
     * Name of this graph object
     */
    protected String graphName;

    /**
     * Number of nodes
     */
    protected int nNodes;

    /**
     * Node names
     */
    protected String[] nodeNames;

    /**
     * Total number of edges in the graph
     */
    protected int nEdges;

    protected BasicGraph() {
    }

    /**
     * Creates a graph with <code>gName</code> name, and <code>n</code> nodes.
     */
    public BasicGraph(String gName, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Invalid # nodes " + n);
        }
        this.graphName = gName;
        this.nNodes = n;
        this.nEdges = 0;
        this.nodeNames = new String[n];
        for (int i = 0; i < n; i++) {
            this.nodeNames[i] = "Node" + i;
        }
        this.initializeEdges();
    }

    /**
     * Creates a Graph reading it from file <code>fname</code>. The file has to
     * be an ascii one and follow a particular format:<p> </p> *GRAPH*
     * [Name_of_graph]<br> [n]<br> [node name 0]<br> [node name 1]<br> [node
     * name 2]<br> :  <br> [node name n-1]<br> <br> [parent node #]  [child node
     * #]  [edgeValue]  <br> [parent node #]  [child node #]  [edgeValue]  <br>
     * :<br> [parent node #]  [child node #]  [edgeValue]  <br> <br> First token
     * should be a word with "graph" as a substring (case insens.), followed by
     * the name of the graph (one word). [n] is the number of nodes in the
     * graph, which should be followed by the list of all node names (one word
     * each) in order from node 0 to node n-1.<br> </p> The node names are
     * followed by the list of edges.  Each edge consists of three elements: the
     * # of the parent node, the # of the child node, and the value of the
     * edge. Non specified edges will have a value of zero which is
     * equivalent to "no edge".<p> </p> GRAPH sample<br> <br> 4 // # nodes<br>
     * <br> // Node Names Node0<br> Node1<br> Node2<br> Node3<br> <br> //
     * edges<br> 0 1  1 <br> 0 2 1 <br> 0 3  1 <br> 1 2 -1 <br> 1 3 -1 <br> 2 3
     * 2 <br> </b> <br> </p> Notice there can be slash-slash (and also
     * slash-star) style comments anywhere in the file.  Tokens can be separated
     * by any number of blank delimiters: tabs, spaces, carriage returns.
     * Support of int, long, floating point, or doubles as edge values will
     * depend on how a subclass of Graph implement the set of edges.
     */
    public BasicGraph(String fname) throws FileNotFoundException, IOException {
        // Create and prepare stream tokenizer
        File f = new File(fname);
        BufferedReader in = new BufferedReader(new FileReader(f));
        StreamTokenizer strmTok = new StreamTokenizer(in);
        strmTok.slashStarComments(true);
        strmTok.slashSlashComments(true);
        strmTok.parseNumbers();
        strmTok.wordChars('_', '_'); // underscore is a word char

        // Read graph name
        int nt = strmTok.nextToken();
        if ((strmTok.sval == null) ||
                (strmTok.sval.toUpperCase().indexOf("GRAPH") < 0)) {
            throw new IllegalArgumentException(
                    "First token does not contain 'GRAPH': " + strmTok.sval);
        }

        nt = strmTok.nextToken();
        this.graphName = strmTok.sval;

        // Read # of nodes in graph
        nt = strmTok.nextToken();
        if (nt != strmTok.TT_NUMBER) {
            throw new IllegalArgumentException(
                    "Expecting # of nodes in graph instead of: " + strmTok
                            .sval);
        }
        this.nNodes = (int) strmTok.nval;
        if (this.nNodes <= 0) {
            throw new IllegalArgumentException(
                    "Invalid # nodes " + this.nNodes);
        }

        // Read names of all nodes
        this.nodeNames = new String[this.nNodes];
        for (int i = 0; i < this.nNodes; i++) {
            nt = strmTok.nextToken();
            this.nodeNames[i] = strmTok.sval;
        }

        // Read edges
        this.initializeEdges();
        while (true) {
            nt = strmTok.nextToken();
            if (nt == strmTok.TT_EOF) {
                break;
            }
            int node_i = (int) strmTok.nval;
            nt = strmTok.nextToken();
            int node_j = (int) strmTok.nval;
            nt = strmTok.nextToken();
            double edgeValue = strmTok.nval;
            this.setEdge(node_i, node_j, edgeValue);
        }
        in.close();
    }

    /**
     * Returns the name of the graph
     */
    public String getGraphName() {
        return this.graphName;
    }

    /**
     * Sets the name of the graph
     */
    public void setGraphName(String newName) {
        this.graphName = newName;
    }

    /**
     * Returns the # nodes in this graph
     */
    public int getSize() {
        return nNodes;
    }

    /**
     * Sets the name of node <code>i</code> in this graph
     */
    public void setNodeName(int i, String nodeName) {
        if ((i < 0) || (i > this.nNodes - 1)) {
            this.badNodeIndex(i);
        }
        this.nodeNames[i] = nodeName;
    }

    /**
     * Returns the name of node <code>i</code> in this graph
     */
    public String getNodeName(int i) {
        if ((i < 0) || (i > this.nNodes - 1)) {
            this.badNodeIndex(i);
        }
        return this.nodeNames[i];
    }

    /**
     * Returns the Total # of edges in this graph
     */
    public int getNumEdges() {
        return this.nEdges;
    }

    /**
     * Returns a specially formatted string with all the contents of this Graph.
     * Actually this string is exactly the same format expected when reading the
     * graph from a file.
     */
    public String toString() {
        String s = this.getClass().getName() + " " + this.graphName + "\n" +
                this.nNodes + " // <-- Total # nodes\n";
        s = s + "\n// Node names:\n";
        for (int i = 0; i < this.nNodes; i++) {
            s = s + this.getNodeName(i) + "\t// # " + i + "\n";
        }
        s = s + "\n// edges:\n" + this.EdgesToString();
        return s;
    }

    protected void badNodeIndex(int i) {
        throw new IllegalArgumentException("Bad node index " + i +
                " for Graph with " + this.nNodes + " nodes");
    }

    /**
     * Returns a clone of this graph
     */
    public abstract Object clone();

    /**
     * Initializes the data structure that holds the set of edges
     */
    protected abstract void initializeEdges();

    /**
     * Sets a value of edge between nodes i and j
     */
    public abstract void setEdge(int i, int j, double value);

    /**
     * Returns the value of edge between nodes i and j
     */
    public abstract double getEdge(int i, int j);

    /**
     * Returns a string representation of the set of edges in this graph
     */
    public abstract String EdgesToString();

}






