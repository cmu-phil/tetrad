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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.MbUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Picks a DAG from the given graph.
 *
 * @author Tyler Gibson
 */
public class ExtractMarkovBlanketWrapper extends GraphWrapper{
    static final long serialVersionUID = 23L;

    public ExtractMarkovBlanketWrapper(GraphSource source){
        this(source.getGraph());
    }


    public ExtractMarkovBlanketWrapper(Graph graph){
        super(new EdgeListGraph(), "Extract Markov Blanket");

        String targetName = getVariableName(graph);
        Graph mb = getMb(graph, targetName);
        super.setGraph(mb);

        TetradLogger.getInstance().log("graph", "\nMarkov blanket for variable '" + targetName + "':");
        TetradLogger.getInstance().log("graph", getGraph() + "");
    }


    public static BidirectedToUndirectedWrapper serializableInstance(){
        return new BidirectedToUndirectedWrapper(EdgeListGraph.serializableInstance());
    }

    @Override
    public boolean allowRandomGraph() {
        return false;
    }

    //======================== Private Methods ================================//

    private Graph getMb(Graph graph, String target) {
        if (target == null) {
            return new EdgeListGraph();
        }

        Graph mb = new EdgeListGraph(graph);
        Node _target = mb.getNode(target);

        MbUtils.trimToMbNodes(mb, _target, false);
        MbUtils.trimEdgesAmongParents(mb, _target);
        MbUtils.trimEdgesAmongParentsOfChildren(mb, _target);

        System.out.println("MB # nodes = " + mb.getNumNodes());

        return mb;
    }

    private String getVariableName(final Graph graph) {
        Box box = Box.createVerticalBox();
        List<Node> nodes = graph.getNodes();

        List<String> nodeNames = new ArrayList<String>();

        for (Node node : nodes) {
            nodeNames.add(node.getName());
        }

        if (nodeNames.isEmpty()) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "There are no nodes in the graph.");
            return null;
        }

        JComboBox comboBox = new JComboBox(nodeNames.toArray());

        String savedNode = Preferences.userRoot().get("mbSavedNode", nodeNames.get(0));

        if (nodeNames.contains(savedNode)) {
            comboBox.setSelectedItem(savedNode);
        }

        box.add(comboBox);
        box.add(Box.createVerticalStrut(4));

        box.setBorder(new TitledBorder("Parameters"));

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                box, "Which target variable?", JOptionPane.QUESTION_MESSAGE);

        String setNode = (String) comboBox.getSelectedItem();

        Preferences.userRoot().put("mbSavedNode", setNode);

        return setNode;
    }
}


