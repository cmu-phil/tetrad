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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.AbstractWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Displays a vertical list of buttons that determine the next action the user
 * can take in the session editor workbench, whether it's selecting and moving a
 * node, adding a node of a particular type, or adding an edge.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see SessionEditor
 * @see SessionEditorToolbar
 */
final class SessionEditorToolbar extends JPanel {

    //=========================MEMBER FIELDS============================//

    /**
     * True iff the toolbar is responding to events.
     */
    private boolean respondingToEvents = true;
    /**
     * The node type of the button that is used for the Select/Move tool.
     */
    private final String selectType = "Select";

    /**
     * The node type of the button that is used for the edge-drawing tool.
     */
    private final String edgeType = "Edge";

    /**
     * Node infos for all of the nodes.
     */
    private final ButtonInfo[] buttonInfos = new ButtonInfo[]{new ButtonInfo(
            "Select", "Select and Move", "move",
            "<html>Select and move nodes or groups of nodes " +
                    "<br>on the workbench.</html>"),
            new ButtonInfo("Graph", "Graph", "graph", "<html>Add a graph node.</html>"),
            new ButtonInfo("GraphManip", "Graph Manipulation", "graph",
                    "<html>Add a node for graph manipulations</html>"),
            new ButtonInfo("Compare", "Comparison", "compare",
                    "<html>Add a node to compare graphs or SEM IM's.</html>"),
            new ButtonInfo( "PM", "Parametric Model", "pm",
            "<html>Add a node for a parametric model.</html>"),
            new ButtonInfo( "IM", "Instantiated Model", "im",
            "<html>Add a node for an instantiated model.</html>"),
            new ButtonInfo("Data", "Data", "data",
                    "<html>Add a node for a data object.</html>"),
            new ButtonInfo("DataManip",
                "Data Manipulation", "data",
                "<html>Add a node for manipulated data.</html>"),
            new ButtonInfo("Estimator", "Estimator", "estimator",
                    "<html>Add a node for an estimator.</html>"),
            new ButtonInfo("Updater", "Updater", "updater",
                    "<html>Add a node for an updater.</html>"),
            //        new ButtonInfo("MB", "Markov Blanket", "search",
            //                "<html>Add a node for a Markov blanket.</html>"),
            new ButtonInfo("Classify", "Classify", "search",
                    "<html>Add a node for a classifier.</html>"),
           new ButtonInfo("Knowledge", "Knowledge", "knowledge", "<html>Add a knowledge box node.</html>"),
                    
            new ButtonInfo("Search", "Search", "search",
                    "<html>Add a node for a search algorithm.</html>"),
//            new ButtonInfo("FS", "Feature Selection", "fs", "<html>Add a node for a Markov Blanket search.</html>"),
            new ButtonInfo("Regression", "Regression", "regression",
                    "<html>Add a node for a regression.</html>"),
//        new ButtonInfo("Predict",
//                "Predict",
//                "predict",
//                "<html>Add a node for a prediction algorithm.</html>"),
//            new ButtonInfo("Knowledge", "Knowledge", "knowledge",
//                    "<html>Store knowledge for use by multiple algorithms." +
//                            "</html>"),
            new ButtonInfo("Edge", "Draw Edge", "flow",
                    "<html>Add an edge from one node to another to declare" +
                            "<br>that the object in the first node should be used " +
                            "<br>to construct the object in the second node." +
                            "<br>As a shortcut, hold down the Control key." +
                            "</html>"),
            new ButtonInfo("Note", "Note", "note",
                    "<html>Add a note to the session.</html>")
    };


    /**
     * The map from JToggleButtons to String node types.
     */
    private final Map<JToggleButton, String> nodeTypes = new HashMap<JToggleButton, String>();

    /**
     * True iff the shift key was down on last click.
     */
    private boolean shiftDown = false;

    /**
     * The workbench this toolbar controls.
     */
    private SessionEditorWorkbench workbench;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new session toolbar.
     *
     * @param workbench the workbench this toolbar controls.
     */
    public SessionEditorToolbar(final SessionEditorWorkbench workbench) {
        if (workbench == null) {
            throw new NullPointerException("Workbench must not be null.");
        }

        this.workbench = workbench;

        // Set up panel.
        Box buttonsPanel = Box.createVerticalBox();
//        buttonsPanel.setBackground(new Color(198, 232, 252));
        buttonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

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

        // This seems to be fixed. Now creating weirdness. jdramsey 3/4/2014
//        // Add a focus listener to help buttons not deselect when the
//        // mouse slides away from the button.
//        FocusListener focusListener = new FocusAdapter() {
//            public void focusGained(FocusEvent e) {
//                JToggleButton component = (JToggleButton) e.getComponent();
//                component.getModel().setSelected(true);
//            }
//        };
//
//        for (int i = 0; i < buttonInfos.length; i++) {
//            buttons[i].addFocusListener(focusListener);
//        }

        // Add an action listener to help send messages to the
        // workbench.
        ChangeListener changeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JToggleButton _button = (JToggleButton) e.getSource();

                if (_button.getModel().isSelected()) {
                    setWorkbenchMode(_button);
                    setCursor(workbench.getCursor());
                }
            }
        };

        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i].addChangeListener(changeListener);
        }

        // Select the Select button.
        JToggleButton button = getButtonForType(this.selectType);

        button.getModel().setSelected(true);

        // Add the buttons to the workbench.
        for (int i = 0; i < buttonInfos.length; i++) {
            buttonsPanel.add(buttons[i]);
            buttonsPanel.add(Box.createVerticalStrut(5));
        }

        // Put the panel in a scrollpane.
        this.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buttonsPanel);
        scroll.setPreferredSize(new Dimension(130, 1000));
        add(scroll, BorderLayout.CENTER);

        // Add property change listener so that selection can be moved
        // back to "SELECT_MOVE" after an action.
        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                if (!isRespondingToEvents()) {
                    return;
                }

                String propertyName = e.getPropertyName();

                if ("nodeAdded".equals(propertyName)) {
                    if (!isShiftDown()) {
                        resetSelectMove();
                    }
                }
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        int keyCode = e.getKeyCode();
                        int id = e.getID();

                        if (keyCode == KeyEvent.VK_SHIFT) {
                            if (id == KeyEvent.KEY_PRESSED) {
                                setShiftDown(true);
                            }
                            else if (id == KeyEvent.KEY_RELEASED) {
                                setShiftDown(false);
                                resetSelectMove();
                            }
                        }

                        return false;
                    }
                });

        resetSelectMove();
    }

    /**
     * Sets the selection back to move/select.
     */
    private void resetSelectMove() {
        JToggleButton selectButton = getButtonForType(selectType);
        if (!(selectButton.isSelected())) {
            selectButton.doClick();
            selectButton.requestFocus();
        }
    }

//    /**
//     * Sets the selection back to Flowchart.
//     */
//    public void resetFlowchart() {
//        JToggleButton edgeButton = getButtonForType(edgeType);
//        edgeButton.doClick();
//        edgeButton.requestFocus();
//    }

    /**
     * True iff the toolbar is responding to events. This may need to be turned
     * off temporarily.
     */
    private boolean isRespondingToEvents() {
        return respondingToEvents;
    }

    /**
     * Sets whether the toolbar should react to events. This may need to be
     * turned off temporarily.
     */
    public void setRespondingToEvents(boolean respondingToEvents) {
        this.respondingToEvents = respondingToEvents;
    }

    protected void processKeyEvent(KeyEvent e) {
        System.out.println("process key event " + e);
        super.processKeyEvent(e);
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

        button.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                setShiftDown(e.isShiftDown());
//                setControlDown(e.isControlDown());
            }
        });

        if ("Select".equals(buttonInfo.getNodeTypeName())) {
            button.setIcon(new ImageIcon(ImageUtils.getImage(this, "move.gif")));
        }
        else if ("Edge".equals(buttonInfo.getNodeTypeName())) {
            button.setIcon(
                    new ImageIcon(ImageUtils.getImage(this, "flow.gif")));
        }
        else {
            button.setName(buttonInfo.getNodeTypeName());
            button.setText("<html><center>" + buttonInfo.getDisplayName() +
                    "</center></html>");
        }

        button.setMaximumSize(new Dimension(110, 40)); // For a vertical box.
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

        if (selectType.equals(nodeType)) {
            workbench.setWorkbenchMode(AbstractWorkbench.SELECT_MOVE);
            workbench.setNextButtonType(null);
        }
        else if (edgeType.equals(nodeType)) {
            workbench.setWorkbenchMode(AbstractWorkbench.ADD_EDGE);
            workbench.setNextButtonType(null);
            setCursor(workbench.getCursor());
        }
        else {
            workbench.setWorkbenchMode(AbstractWorkbench.ADD_NODE);
            workbench.setNextButtonType(nodeType);
            setCursor(workbench.getCursor());
        }
    }

    /**
     * @return the JToggleButton for the given node type, or null if no such
     * button exists.
     */
    private JToggleButton getButtonForType(String nodeType) {
        for (Object o : nodeTypes.keySet()) {
            JToggleButton button = (JToggleButton) o;

            if (nodeType.equals(nodeTypes.get(button))) {
                return button;
            }
        }

        return null;
    }

    private boolean isShiftDown() {
        return shiftDown;
    }

    private void setShiftDown(boolean shiftDown) {
        this.shiftDown = shiftDown;
    }

//    public boolean isControlDown() {
//        return shiftDown;
//    }
//
//    private void setControlDown(boolean shiftDown) {
//        this.shiftDown = shiftDown;
//    }

    /**
     * Holds info for constructing a single button.
     */
    private static final class ButtonInfo {

        /**
         * This is the name used to construct nodes on the graph of this type.
         * Need to coordinate with session.
         */
        private String nodeTypeName;

        /**
         * The name displayed on the button.
         */
        private final String displayName;

        /**
         * The prefixes for images for this button. It is assumed that files
         * <prefix>Up.gif, <prefix>Down.gif, <prefix>Off.gif and
         * <prefix>Roll.gif are located in the /images directory relative to
         * this compiled class.
         */
        private final String imagePrefix;

        /**
         * Tool tip text displayed for the button.
         */
        private final String toolTipText;

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





