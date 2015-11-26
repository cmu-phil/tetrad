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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.ImageUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Displays a vertical list of buttons that determine the next action the user
 * can take in the session editor workbench, whether it's selecting and moving a
 * node, adding a node of a particular type, or adding an edge.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class KnowledgeEditorToolbar extends JPanel {

    //=========================MEMBER FIELDS============================//

    /**
     * Node infos for all of the nodes.
     */
    private ButtonInfo[] buttonInfos = new ButtonInfo[]{new ButtonInfo("Select",
            "Select and Move", "move",
            "<html>Select and move nodes or groups of nodes " +
                    "<br>on the workbench.</html>"), new ButtonInfo("Forbidden",
            "Add Forbidden", "flow",
            "<html>Add an edge from one node to another to indicate " +
                    "<br>that for purposes of searches that edge will not be " +
                    "<br>allowed in the graph.</html>"), new ButtonInfo(
            "Required", "Add Required", "flow",
            "<html>Add an edge from one node to another to indicate " +
                    "<br>that for purposes of searches that edge will be " +
                    "<br>required in the graph.</html>"), new ButtonInfo(
            "Source Layout", "Source Layout", "flow",
            "<html>Lays out the nodes according to the source graph.</html>"),
            new ButtonInfo("Knowledge Layout", "Knowledge Layout", "flow",
                    "<html>Lays out the nodes according to knowledge tiers.</html>")};

    /**
     * The map from JToggleButtons to String node types.
     */
    private Map<JToggleButton, String> nodeTypes =
            new HashMap<JToggleButton, String>();

    /**
     * The workbench this toolbar controls.
     */
    private KnowledgeWorkbench workbench;
    private Graph sourceGraph;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new session toolbar.
     *
     * @param workbench   the workbench this toolbar controls.
     * @param sourceGraph
     */
    public KnowledgeEditorToolbar(KnowledgeWorkbench workbench,
                                  Graph sourceGraph) {
        if (workbench == null) {
            throw new NullPointerException("Workbench must not be null.");
        }

        this.workbench = workbench;
        this.sourceGraph = sourceGraph;

        // Set up panel.
        Box buttonsPanel = Box.createVerticalBox();

        //setMinimumSize(new Dimension(200, 10));
        Border insideBorder =
                new MatteBorder(10, 10, 10, 10, this.getBackground());
        Border outsideBorder = new EtchedBorder();

        buttonsPanel.setBorder(new CompoundBorder(outsideBorder, insideBorder));

        // Create buttons.
        JToggleButton[] buttons = new JToggleButton[buttonInfos.length];

        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i] = constructButton(buttonInfos[i]);
        }

        // Add all buttons to a button group.
        ButtonGroup buttonGroup = new ButtonGroup();

        for (int i = 0; i < buttonInfos.length; i++) {
            buttonGroup.add(buttons[i]);
        }

        buttons[0].setSelected(true);

        // Add a focus listener to help buttons not deselect when the
        // mouse slides away from the button.
        FocusListener focusListener = new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                JToggleButton component = (JToggleButton) e.getComponent();
                component.getModel().setSelected(true);
            }
        };

        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i].addFocusListener(focusListener);
        }

        // Add an action listener to help send messages to the
        // workbench.
        ActionListener changeListener = new ActionListener() {
            public void  actionPerformed(ActionEvent e) {
                JToggleButton _button = (JToggleButton) e.getSource();

                if (_button.getModel().isSelected()) {
                    setWorkbenchMode(_button);
                }
            }
        };

        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i].addActionListener(changeListener);
        }

        // Add the buttons to the workbench.
        for (int i = 0; i < buttonInfos.length; i++) {
            buttonsPanel.add(buttons[i]);
            buttonsPanel.add(Box.createVerticalStrut(5));
        }

        // Put the panel in a scrollpane.
        this.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buttonsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(130, 1000));
        add(scroll, BorderLayout.CENTER);
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * Constructs the button with the given node type and image prefix. If the
     * node type is "Select", constructs a button that allows nodes to be
     * selected and moved. If the node type is "Edge", constructs a button that
     * allows edges to be drawn. For other node types, constructs buttons that
     * allow those type of nodes to be added to the workbench. If a non-null
     * image prefix is provided, images for <prefix>Up.gif, <prefix>Down.gif,
     * <prefix>Off.gif and <prefix>Roll.gif are loaded from the /images
     * directory relative to this compiled class and used to provide up, down,
     * off, and rollover images for the constructed button. On construction,
     * nodes are mapped to their node types in the Map, <code>nodeTypes</code>.
     * Listeners are added to the node.
     *
     * @param buttonInfo contains the info needed to construct the button.
     */
    private JToggleButton constructButton(ButtonInfo buttonInfo) {
        String imagePrefix = buttonInfo.getImagePrefix();

        if (imagePrefix == null) {
            throw new NullPointerException("Image prefix must not be null.");
        }

        JToggleButton button = new JToggleButton();

        if ("Select".equals(buttonInfo.getNodeTypeName())) {
            button.setIcon(
                    new ImageIcon(ImageUtils.getImage(this, "move.gif")));
        } else {
            button.setName(buttonInfo.getNodeTypeName());
            button.setText("<html><center>" + buttonInfo.getDisplayName() +
                    "</center></html>");
        }

        button.setMaximumSize(new Dimension(100, 40)); // For a vertical box.
        button.setToolTipText(buttonInfo.getToolTipText());
        this.nodeTypes.put(button, buttonInfo.getNodeTypeName());

        return button;
    }

    /**
     * Sets the state of the workbench in response to a button press.
     *
     * @param button the JToggleButton whose workbench state is to be set.
     */
    private void setWorkbenchMode(JToggleButton button) {
        String nodeType = this.nodeTypes.get(button);

        if ("Select".equals(nodeType)) {
            workbench.setWorkbenchMode(KnowledgeWorkbench.SELECT_MOVE);
        } else if ("Forbidden".equals(nodeType)) {
            workbench.setWorkbenchMode(KnowledgeWorkbench.ADD_EDGE);
            workbench.setEdgeMode(KnowledgeWorkbench.FORBIDDEN_EDGE);
        } else if ("Required".equals(nodeType)) {
            workbench.setWorkbenchMode(KnowledgeWorkbench.ADD_EDGE);
            workbench.setEdgeMode(KnowledgeWorkbench.REQUIRED_EDGE);
        } else if ("Source Layout".equals(nodeType)) {
            KnowledgeGraph graph = (KnowledgeGraph) workbench.getGraph();
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
            workbench.setGraph(graph);
        } else if ("Knowledge Layout".equals(nodeType)) {
            KnowledgeGraph graph = (KnowledgeGraph) workbench.getGraph();
            IKnowledge knowledge = graph.getKnowledge();
            try {
                SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
                workbench.setGraph(graph);
            } catch (IllegalArgumentException ex) {
                System.out.print(ex.getMessage());
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), ex.getMessage());
            }
        }
    }

    public Graph getSourceGraph() {
        return sourceGraph;
    }

    /**
     * Holds info for constructing a single button.
     */
    private static class ButtonInfo {

        /**
         * This is the name used to construct nodes on the graph of this type.
         * Need to coordinate with session.
         */
        private String nodeTypeName;

        /**
         * The name displayed on the button.
         */
        private String displayName;

        /**
         * The prefixes for images for this button. It is assumed that files
         * <prefix>Up.gif, <prefix>Down.gif, <prefix>Off.gif and
         * <prefix>Roll.gif are located in the /images directory relative to
         * this compiled class.
         */
        private String imagePrefix;

        /**
         * Tool tip text displayed for the button.
         */
        private String toolTipText;

        public ButtonInfo(String nodeTypeName, String displayName,
                          String imagePrefix, String toolTipText) {
            this.nodeTypeName = nodeTypeName;
            this.displayName = displayName;
            this.imagePrefix = imagePrefix;
            this.toolTipText = toolTipText;
        }

        public String getNodeTypeName() {
            return nodeTypeName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setNodeTypeName(String nodeTypeName) {
            this.nodeTypeName = nodeTypeName;
        }

        public String getImagePrefix() {
            return imagePrefix;
        }

        public String getToolTipText() {
            return toolTipText;
        }
    }
}




