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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds a tetrad-style graph with all of the constructors necessary for it to
 * serve as a model for the tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GraphSelectionParams implements Params {
    private static final long serialVersionUID = 23L;

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphSelectionParams serializableInstance() {
        return new GraphSelectionParams();
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public List<Node> getHighlightInEditor() {
        return highlightInEditor;
    }

    // For relevant selection methods, the length or degree.
    private int n = 3;

    // Whether the length or degree is equal to n or at most n.
    private GraphSelectionWrapper.nType nType = GraphSelectionWrapper.nType.atMost;

    // The selection type.
    private GraphSelectionWrapper.Type type = GraphSelectionWrapper.Type.subgraph;

    private String dialogText = "";

    // The name of this wrapper; used by Tetrad.
    private String name;

    // The original graph loaded in; most selections are subsets of this.
    private Graph graph;

    // The selection graph, usually a subset of graph.
    private Graph selectionGraph = new EdgeListGraph();

    // The selected variables in graph.
    private List<Node> selectedVariables = new ArrayList<>();

    // The list of nodes that should be highlighted in the editor.
    private List<Node> highlightInEditor = new ArrayList<>();

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public GraphSelectionWrapper.nType getnType() {
        return nType;
    }

    public void setnType(GraphSelectionWrapper.nType nType) {
        this.nType = nType;
    }

    public GraphSelectionWrapper.Type getType() {
        return type;
    }

    public void setType(GraphSelectionWrapper.Type type) {
        this.type = type;
    }

    public String getDialogText() {
        return dialogText;
    }

    public void setDialogText(String dialogText) {
        this.dialogText = dialogText;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public Graph getSelectionGraph() {
        return selectionGraph;
    }

    public void setSelectionGraph(Graph selectionGraph) {
        this.selectionGraph = selectionGraph;
    }

    public List<Node> getSelectedVariables() {
        return selectedVariables;
    }

    public void setSelectedVariables(List<Node> selectedVariables) {
        this.selectedVariables = selectedVariables;
    }

    public void setHighlightInEditor(List<Node> highlightInEditor) {
        this.highlightInEditor = highlightInEditor;
    }
}





