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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.AbstractWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Displays a vertical list of buttons that determine the next action the user
 * can take in the session editor workbench, whether it's selecting and moving
 * a node, adding a node of a particular type, or adding an edge.
 *
 * @author josephramsey
 * @see SessionEditor
 */
final class SessionEditorToolbar extends JPanel {

    /**
     * Node type token for the select/move tool.
     */
    private static final String SELECT_TYPE = "Select";

    /**
     * Node type token for the edge-drawing tool.
     */
    private static final String EDGE_TYPE = "Edge";

    /**
     * Maps each JToggleButton to its node-type string.
     */
    private final Map<JToggleButton, String> nodeTypes = new LinkedHashMap<>();

    /**
     * Maps icon-bearing buttons to their image resource names, for L&F refresh.
     */
    private final Map<JToggleButton, String> buttonImageNames = new LinkedHashMap<>();

    /**
     * The workbench this toolbar controls.
     */
    private final SessionEditorWorkbench workbench;

    /**
     * Whether the toolbar is currently responding to events.
     * Can be toggled off temporarily by callers.
     */
    private boolean respondingToEvents = true;

    /**
     * Whether the Shift key is currently held down.
     */
    private boolean shiftDown;

    /**
     * Constructs a new session toolbar.
     *
     * @param workbench the workbench this toolbar controls; must not be null
     */
    public SessionEditorToolbar(SessionEditorWorkbench workbench) {
        if (workbench == null) {
            throw new NullPointerException("Workbench must not be null.");
        }

        this.workbench = workbench;

        Box buttonsPanel = Box.createVerticalBox();
        buttonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        ButtonInfo[] buttonInfos = {
                new ButtonInfo(SELECT_TYPE, "Select and Move", "move",
                        "<html>Select and move nodes or groups of nodes "
                                + "<br>on the workbench.</html>"),
                new ButtonInfo(EDGE_TYPE, "Draw Edge", "flow",
                        "<html>Add an edge from one node to another to declare"
                                + "<br>that the object in the first node should be used "
                                + "<br>to construct the object in the second node."
                                + "<br>As a shortcut, hold down the Control key."
                                + "</html>"),
                new ButtonInfo("Graph",          "Graph",             "graph",       "<html>Add a graph node.</html>"),
                new ButtonInfo("Compare",        "Compare",           "compare",     "<html>Add a node to compare graphs or SEM IM's.</html>"),
                new ButtonInfo("GridSearch",     "Grid Search",       "search",      "<html>Add a node to do a grid search.</html>"),
                new ButtonInfo("PM",             "Parametric Model",  "pm",          "<html>Add a node for a parametric model.</html>"),
                new ButtonInfo("IM",             "Instantiated Model","semIm",       "<html>Add a node for an instantiated model.</html>"),
                new ButtonInfo("Estimator",      "Estimator",         "estimator",   "<html>Add a node for an estimator.</html>"),
                new ButtonInfo("Data",           "Data",              "data",        "<html>Add a node for a data object.</html>"),
                new ButtonInfo("Simulation",     "Simulation",        "simulation",  "<html>Add a node for a simulation object.</html>"),
                new ButtonInfo("Search",         "Search",            "search",      "<html>Add a node for a search algorithm.</html>"),
                new ButtonInfo("Latent_Clusters","Latent Clusters",   "cluster",     "<html>Add a node for a clustering algorithm.</html>"),
                new ButtonInfo("Latent_Structure","Latent Structure", "clustersearch","<html>Add a node for a block search.</html>"),
                new ButtonInfo("Knowledge",      "Knowledge",         "knowledge",   "<html>Add a knowledge box node.</html>"),
                new ButtonInfo("Updater",        "Updater",           "updater",     "<html>Add a node for an updater.</html>"),
                new ButtonInfo("Regression",     "Regression",        "regression",  "<html>Add a node for a regression.</html>"),
                new ButtonInfo("Note",           "Note",              "note",        "<html>Add a note to the session.</html>")
        };

        JToggleButton[] buttons = new JToggleButton[buttonInfos.length];
        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i] = constructButton(buttonInfos[i]);
        }

        ButtonGroup buttonGroup = new ButtonGroup();
        for (JToggleButton button : buttons) {
            buttonGroup.add(button);
        }

        ChangeListener changeListener = e -> {
            JToggleButton source = (JToggleButton) e.getSource();
            if (source.getModel().isSelected()) {
                setWorkbenchMode(source);
            }
        };

        for (JToggleButton button : buttons) {
            button.addChangeListener(changeListener);
            buttonsPanel.add(button);
            buttonsPanel.add(Box.createVerticalStrut(5));
        }

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buttonsPanel);
        scroll.setPreferredSize(new Dimension(130, 1000));
        add(scroll, BorderLayout.CENTER);

        // After an action, reset selection or keep edge button selected as appropriate.
        workbench.addPropertyChangeListener(e -> {
            if (!this.respondingToEvents) {
                return;
            }
            String prop = e.getPropertyName();
            if ("nodeAdded".equals(prop)) {
                if (!this.shiftDown) {
                    resetSelectMove();
                }
            } else if ("edgeAdded".equals(prop)) {
                JToggleButton edgeButton = getButtonForType(EDGE_TYPE);
                if (edgeButton != null && !edgeButton.isSelected()) {
                    edgeButton.doClick();
                    edgeButton.requestFocus();
                }
            }
        });

        // Track Shift key state globally.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    int keyCode = e.getKeyCode();
                    int id = e.getID();
                    if (keyCode == KeyEvent.VK_SHIFT) {
                        if (id == KeyEvent.KEY_PRESSED) {
                            this.shiftDown = true;
                        } else if (id == KeyEvent.KEY_RELEASED) {
                            this.shiftDown = false;
                            resetSelectMove();
                        }
                    }
                    return false;
                });

        resetSelectMove();
    }

    /**
     * Sets whether the toolbar should react to workbench events.
     * Can be toggled off temporarily by callers.
     *
     * @param respondingToEvents true to respond, false to suppress
     */
    public void setRespondingToEvents(boolean respondingToEvents) {
        this.respondingToEvents = respondingToEvents;
    }

    /**
     * {@inheritDoc}
     *
     * Refreshes button icons when the look-and-feel changes.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (this.buttonImageNames != null) {
            this.buttonImageNames.forEach((button, imageName) ->
                    button.setIcon(new ImageIcon(ImageUtils.getImage(this, imageName))));
        }
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resets the toolbar to the Select/Move tool.
     */
    private void resetSelectMove() {
        JToggleButton selectButton = getButtonForType(SELECT_TYPE);
        if (selectButton != null && !selectButton.isSelected()) {
            selectButton.doClick();
            selectButton.requestFocus();
        }
    }

    /**
     * Constructs a toggle button from a {@link ButtonInfo} descriptor,
     * wires up its mouse listener, and registers it in {@code nodeTypes}.
     */
    private JToggleButton constructButton(ButtonInfo buttonInfo) {
        String imagePrefix = buttonInfo.getImagePrefix();
        if (imagePrefix == null) {
            throw new NullPointerException("Image prefix must not be null.");
        }

        JToggleButton button = new JToggleButton();

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setShiftDown(e.isShiftDown());
            }
        });

        String nodeTypeName = buttonInfo.getNodeTypeName();
        if (SELECT_TYPE.equals(nodeTypeName) || EDGE_TYPE.equals(nodeTypeName)) {
            String imageName = imagePrefix + ".gif";
            button.setIcon(new ImageIcon(ImageUtils.getImage(this, imageName)));
            this.buttonImageNames.put(button, imageName);
        } else {
            button.setName(nodeTypeName);
            button.setText("<html><center>" + buttonInfo.getDisplayName() + "</center></html>");
        }

        button.setMaximumSize(new Dimension(110, 40));
        button.setToolTipText(buttonInfo.getToolTipText());
        this.nodeTypes.put(button, nodeTypeName);

        return button;
    }

    /**
     * Updates the workbench mode and cursor in response to the given button being selected.
     */
    private void setWorkbenchMode(JToggleButton button) {
        String nodeType = this.nodeTypes.get(button);

        if (SELECT_TYPE.equals(nodeType)) {
            this.workbench.setWorkbenchMode(AbstractWorkbench.SELECT_MOVE);
            this.workbench.setNextButtonType(null);
            Cursor hand = new Cursor(Cursor.HAND_CURSOR);
            setCursor(hand);
            this.workbench.setCursor(hand);
        } else if (EDGE_TYPE.equals(nodeType)) {
            this.workbench.setWorkbenchMode(AbstractWorkbench.ADD_EDGE);
            this.workbench.setNextButtonType(null);
            Cursor def = new Cursor(Cursor.DEFAULT_CURSOR);
            setCursor(def);
            this.workbench.setCursor(def);
        } else {
            this.workbench.setWorkbenchMode(AbstractWorkbench.ADD_NODE);
            this.workbench.setNextButtonType(nodeType);
            Cursor cross = new Cursor(Cursor.CROSSHAIR_CURSOR);
            setCursor(cross);
            this.workbench.setCursor(cross);
        }
    }

    /**
     * Returns the toggle button registered for the given node type, or null if none.
     */
    private JToggleButton getButtonForType(String nodeType) {
        for (Map.Entry<JToggleButton, String> entry : this.nodeTypes.entrySet()) {
            if (nodeType.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void setShiftDown(boolean shiftDown) {
        this.shiftDown = shiftDown;
    }

    // -------------------------------------------------------------------------
    // ButtonInfo
    // -------------------------------------------------------------------------

    /**
     * Holds the information needed to construct a single toolbar button.
     */
    private static final class ButtonInfo {

        /**
         * The node type name; used to construct nodes of this type on the graph.
         * Must coordinate with session node type names.
         */
        private final String nodeTypeName;

        /**
         * The label displayed on the button.
         */
        private final String displayName;

        /**
         * The image resource prefix. For Select and Edge buttons, the image
         * loaded is {@code <prefix>.gif}. Other buttons use text labels instead.
         */
        private final String imagePrefix;

        /**
         * Tooltip text shown on hover.
         */
        private final String toolTipText;

        public ButtonInfo(String nodeTypeName, String displayName,
                          String imagePrefix, String toolTipText) {
            this.nodeTypeName = nodeTypeName;
            this.displayName  = displayName;
            this.imagePrefix  = imagePrefix;
            this.toolTipText  = toolTipText;
        }

        public String getNodeTypeName()  { return this.nodeTypeName; }
        public String getDisplayName()   { return this.displayName;  }
        public String getImagePrefix()   { return this.imagePrefix;  }
        public String getToolTipText()   { return this.toolTipText;  }
    }
}