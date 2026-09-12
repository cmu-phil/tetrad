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

import edu.cmu.tetradapp.workbench.AbstractWorkbench;
import edu.cmu.tetradapp.workbench.WorkbenchIcons;
import edu.cmu.tetradapp.workbench.WorkbenchStyle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
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
     * Sentinel entry in the button list marking a visual gap between groups of buttons.
     */
    private static final ButtonInfo GROUP_GAP = null;

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
                GROUP_GAP,
                // --- Real-data pipeline: load data, add knowledge, search. ---
                new ButtonInfo("Data",           "Data",              "data",        "<html>Add a node for a data object.</html>"),
                new ButtonInfo("Knowledge",      "Knowledge",         "knowledge",   "<html>Add a knowledge box node.</html>"),
                new ButtonInfo("Search",         "Search",            "search",      "<html>Add a node for a search algorithm.</html>"),
                new ButtonInfo("Latent_Clusters","Latent Clusters",   "cluster",     "<html>Add a node for a clustering algorithm.</html>"),
                new ButtonInfo("Latent_Structure","Latent Structure", "clustersearch","<html>Add a node for a block search.</html>"),
                new ButtonInfo("Graph",          "Graph",             "graph",       "<html>Add a graph node.</html>"),
                new ButtonInfo("Compare",        "Compare",           "compare",     "<html>Add a node to compare graphs or SEM IM's.</html>"),
                GROUP_GAP,
                // --- Modeling and inference on a graph. ---
                new ButtonInfo("PM",             "Parametric Model",  "pm",          "<html>Add a node for a parametric model.</html>"),
                new ButtonInfo("Estimator",      "Estimator",         "estimator",   "<html>Add a node for an estimator.</html>"),
                new ButtonInfo("Updater",        "Updater",           "updater",     "<html>Add a node for an updater.</html>"),
                new ButtonInfo("Regression",     "Regression",        "regression",  "<html>Add a node for a regression.</html>"),
                new ButtonInfo("IM",             "Instantiated Model","semIm",       "<html>Add a node for an instantiated model.</html>"),
                GROUP_GAP,
                // --- Simulation and benchmarking. ---
                new ButtonInfo("Simulation",     "Simulation",        "simulation",  "<html>Add a node for a simulation object.</html>"),
                new ButtonInfo("GridSearch",     "Grid Search",       "search",      "<html>Add a node to do a grid search.</html>"),
                GROUP_GAP,
                // --- Annotation. ---
                new ButtonInfo("Note",           "Note",              "note",        "<html>Add a note to the session.</html>")
        };

        JToggleButton[] buttons = new JToggleButton[buttonInfos.length];
        for (int i = 0; i < buttonInfos.length; i++) {
            buttons[i] = buttonInfos[i] == GROUP_GAP ? null : constructButton(buttonInfos[i]);
        }

        ButtonGroup buttonGroup = new ButtonGroup();
        for (JToggleButton button : buttons) {
            if (button != null) {
                buttonGroup.add(button);
            }
        }

        ChangeListener changeListener = e -> {
            JToggleButton source = (JToggleButton) e.getSource();
            if (source.getModel().isSelected()) {
                setWorkbenchMode(source);
            }
        };

        for (JToggleButton button : buttons) {
            if (button == null) {
                buttonsPanel.add(Box.createVerticalStrut(15));
                continue;
            }
            button.addChangeListener(changeListener);
            buttonsPanel.add(button);
            buttonsPanel.add(Box.createVerticalStrut(5));
        }

//        buttonsPanel.setPreferredSize(new Dimension(120, 800));
        buttonsPanel.setPreferredSize(new Dimension(135, buttonsPanel.getPreferredSize().height));

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buttonsPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
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
            } else if ("selectMove".equals(prop)) {
                resetSelectMove();
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
        // Icons read their colors from WorkbenchStyle at paint time, so nothing needs reloading.
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
        if (SELECT_TYPE.equals(nodeTypeName)) {
            button.setIcon(WorkbenchIcons.forTool("move"));
        } else if (EDGE_TYPE.equals(nodeTypeName)) {
            button.setIcon(new SessionEdgeIcon());
        } else {
            button.setName(nodeTypeName);
            button.setText("<html>" + buttonInfo.getDisplayName() + "</html>");
            button.setIcon(new NodeTypeIcon(nodeTypeName));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setIconTextGap(8);
        }

        // Fix the button size so every button is the same width and the panel's
        // preferred width reflects that, rather than the unwrapped HTML text width.
        // The width leaves room for the type icon plus two lines of label text.
        button.setMargin(new Insets(2, 8, 2, 6));
        button.setPreferredSize(new Dimension(130, 40));
        button.setMaximumSize(new Dimension(130, 40));
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
    // NodeTypeIcon
    // -------------------------------------------------------------------------

    /**
     * A small vector icon that echoes the session node card for a node type: a rounded rectangle in the card fill
     * with a type-tinted band across the top. Colors come from {@link StdDisplayComp}, so the toolbar and the
     * workbench nodes always agree, and they are read at paint time, so the icon follows light and dark mode
     * without being rebuilt.
     */
    private static final class NodeTypeIcon implements Icon {

        private static final int W = 18;
        private static final int H = 16;
        private static final int ARC = 5;
        private static final int BAND = 6;

        private final String nodeType;

        private NodeTypeIcon(String nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                Shape card = new RoundRectangle2D.Double(x + 0.5, y + 0.5, W - 1, H - 1, ARC, ARC);

                g2.setColor(StdDisplayComp.cardFill());
                g2.fill(card);

                Area band = new Area(card);
                band.intersect(new Area(new Rectangle2D.Double(x, y, W, BAND)));
                g2.setColor(StdDisplayComp.bandFill(nodeType));
                g2.fill(band);

                g2.setStroke(new BasicStroke(1f));
                g2.setColor(StdDisplayComp.cardBorder());
                g2.draw(card);
            } finally {
                g2.dispose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // SessionEdgeIcon
    // -------------------------------------------------------------------------

    /**
     * Two miniature session cards joined by an arrow: the draw-edge tool. The cards are drawn the same way as
     * {@link NodeTypeIcon}, and the arrow the same way as the graph toolbar's edge icons.
     */
    private static final class SessionEdgeIcon implements Icon {

        private static final int W = 64;
        private static final int H = 26;

        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                int cw = 18, ch = 16, arc = 5, band = 6;
                int cy = y + (H - ch) / 2;
                int lx = x + 2, rx = x + W - 2 - cw;

                miniCard(g2, lx, cy, cw, ch, arc, band, "Data");
                miniCard(g2, rx, cy, cw, ch, arc, band, "Search");

                g2.setColor(WorkbenchStyle.edge());
                WorkbenchIcons.paintArrow(g2, lx + cw + 1, y + H / 2.0, rx - 1, y + H / 2.0);
            } finally {
                g2.dispose();
            }
        }

        private static void miniCard(Graphics2D g2, int x, int y, int w, int h, int arc, int band, String type) {
            Shape card = new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, arc, arc);
            g2.setColor(StdDisplayComp.cardFill());
            g2.fill(card);
            Area top = new Area(card);
            top.intersect(new Area(new Rectangle2D.Double(x, y, w, band)));
            g2.setColor(StdDisplayComp.bandFill(type));
            g2.fill(top);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(StdDisplayComp.cardBorder());
            g2.draw(card);
        }
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