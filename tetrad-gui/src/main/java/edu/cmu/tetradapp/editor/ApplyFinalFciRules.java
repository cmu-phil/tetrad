///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class represents an action to run the final FCI (Fast Causal Inference) rules on a graph in a GraphWorkbench.
 * It extends the AbstractAction class and implements the ClipboardOwner interface.
 */
public class ApplyFinalFciRules extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Runs the final FCI (Fast Causal Inference) rules on a graph in a GraphWorkbench.
     * This action is triggered by clicking a button or selecting a menu option.
     *
     * @param workbench the GraphWorkbench instance containing the graph to run final FCI rules on.
     * @throws NullPointerException if workbench is null.
     */
    public ApplyFinalFciRules(GraphWorkbench workbench) {
        super("Apply Final FCI Rules");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event that triggered the action.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();
        Graph graph = this.workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(this.workbench, "No graph to apply final FCI rules to.");
            return;
        }

        // R4 (the discriminating path rule) decides its collider/noncollider branch by asking
        // whether the middle vertex lies in a separating set of the path's endpoints. With no
        // data and no recorded sepsets, that question must be answered by m-separation in some
        // graph. Historically this action used the graph being oriented as its own oracle; this
        // dialog additionally allows a pasted reference graph (e.g., the true DAG or PAG), so
        // that the editor can reproduce what an algorithm's orientation does when its sepset
        // evidence comes from elsewhere than the graph being oriented.
        JRadioButton useSelf = new JRadioButton("Use the graph being oriented (previous behavior)", true);
        JRadioButton useRef = new JRadioButton("Use a reference graph pasted below (e.g., the true DAG, MAG, or PAG)");
        ButtonGroup group = new ButtonGroup();
        group.add(useSelf);
        group.add(useRef);

        JTextArea refArea = new JTextArea(14, 50);
        refArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        refArea.setEnabled(false);
        useSelf.addActionListener(ev -> refArea.setEnabled(false));
        useRef.addActionListener(ev -> refArea.setEnabled(true));

        Box panel = Box.createVerticalBox();
        panel.add(new JLabel("R4 (the discriminating path rule) decides its branch by whether the middle"));
        panel.add(new JLabel("vertex lies in a separating set. Which graph should answer those"));
        panel.add(new JLabel("m-separation queries?"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(useSelf);
        panel.add(useRef);
        panel.add(Box.createVerticalStrut(4));
        panel.add(new JScrollPane(refArea));
        panel.add(Box.createVerticalStrut(4));
        panel.add(new JLabel("Paste in the saved text format (Graph Nodes: ... Graph Edges: ...). A reference"));
        panel.add(new JLabel("with circle endpoints is first instantiated to a MAG (Zhang), since m-separation"));
        panel.add(new JLabel("is invariant over its class. Extra reference nodes (e.g., latents) are fine; every"));
        panel.add(new JLabel("node of the graph being oriented must appear in the reference."));

        int result = JOptionPane.showConfirmDialog(this.workbench, panel, "Apply Final FCI Rules",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Graph oracle = graph;
        String oracleDesc = "the graph being oriented";

        if (useRef.isSelected()) {
            String text = refArea.getText();
            if (text == null || text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this.workbench, "No reference graph was pasted.");
                return;
            }

            Graph reference;
            try {
                reference = GraphSaveLoadUtils.readerToGraphTxt(text);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this.workbench,
                        "Could not parse the pasted reference graph: " + ex.getMessage());
                return;
            }

            Set<String> refNames = new HashSet<>();
            for (Node node : reference.getNodes()) {
                refNames.add(node.getName());
            }
            for (Node node : graph.getNodes()) {
                if (!refNames.contains(node.getName())) {
                    JOptionPane.showMessageDialog(this.workbench,
                            "The reference graph has no node named '" + node.getName()
                            + "', which the graph being oriented contains.");
                    return;
                }
            }

            reference = GraphUtils.replaceNodes(reference, graph.getNodes());

            boolean hasCircles = false;
            for (Edge edge : reference.getEdges()) {
                if (edge.getEndpoint1() == Endpoint.CIRCLE || edge.getEndpoint2() == Endpoint.CIRCLE) {
                    hasCircles = true;
                    break;
                }
            }

            if (hasCircles) {
                try {
                    reference = GraphTransforms.zhangMagFromPag(reference);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this.workbench,
                            "Could not instantiate a MAG from the pasted graph (is it a legal PAG?): "
                            + ex.getMessage());
                    return;
                }
                oracleDesc = "pasted reference graph (instantiated to a MAG)";
            } else {
                oracleDesc = "pasted reference graph";
            }

            oracle = reference;
        }

        Graph __g = new EdgeListGraph(graph);
        R0R4StrategyTestBased strategy =
                (R0R4StrategyTestBased) R0R4StrategyTestBased.defaultConfiguration(oracle, new Knowledge());

        // R4's separator search has two inputs: the independence test (which answers "is this a
        // separating set?") and a graph in which candidate blocking sets are constructed (which
        // proposes them). Both must come from the oracle. With only the test rewired, blocking
        // sets are still built from the live graph's paths, so vertices forced in by the live
        // graph's own (possibly spurious) edges -- exactly the ones a reference oracle exists to
        // adjudicate -- are placed into the separator, flipping R4 to its tail branch.
        if (oracle != graph) {
            strategy.setSepsetGraph(oracle);
        }

        FciOrient finalFciRules = new FciOrient(strategy);
        TetradLogger.getInstance().log("Apply Final FCI Rules: R4 conditioning sets from " + oracleDesc + ".");
        finalFciRules.finalOrientation(__g);
        workbench.setGraph(__g);
    }

    /**
     * Called when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost ownership
     * @param contents the contents that were lost
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




