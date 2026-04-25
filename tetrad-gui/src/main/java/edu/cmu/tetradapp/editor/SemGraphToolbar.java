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

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.AbstractWorkbench;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is the toolbar for the SEM graph editor. Its tools are as follows:
 * <ul>
 *   <li>The 'move' tool: allows the user to select and move items in the workbench.</li>
 *   <li>The 'addObserved' tool: allows the user to add new observed variables.</li>
 *   <li>The 'addLatent' tool: allows the user to add new latent variables.</li>
 *   <li>The 'addDirectedEdge' tool: allows the user to add new directed edges.</li>
 *   <li>The 'addBidirectedEdge' tool: allows the user to add new bidirected edges.</li>
 * </ul>
 *
 * @author Donald Crimbchin
 * @author josephramsey
 * @see GraphEditor
 */
class SemGraphToolbar extends JPanel implements PropertyChangeListener {

    /**
     * The mutually exclusive button group for the toolbar buttons.
     */
    private final ButtonGroup group = new ButtonGroup();

    /**
     * The panel that holds the buttons vertically.
     */
    private final Box buttonsPanel = Box.createVerticalBox();

    /**
     * Toolbar buttons.
     */
    private final JToggleButton move            = new JToggleButton();
    private final JToggleButton addObserved     = new JToggleButton();
    private final JToggleButton addLatent       = new JToggleButton();
    private final JToggleButton addDirectedEdge = new JToggleButton();
    private final JToggleButton addBidirectedEdge = new JToggleButton();

    /**
     * Maps each button to its icon resource name, used for icon refresh on L&F changes.
     */
    private final Map<JToggleButton, String> buttonImageNames = new LinkedHashMap<>();

    /**
     * The workbench this toolbar governs.
     */
    private final GraphWorkbench workbench;

    /**
     * Constructs a new SEM graph toolbar governing the modes of the given GraphWorkbench.
     *
     * @param workbench the {@link GraphWorkbench} this toolbar controls; must not be null
     */
    public SemGraphToolbar(GraphWorkbench workbench) {
        if (workbench == null) {
            throw new NullPointerException("workbench must not be null");
        }

        this.workbench = workbench;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.buttonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(this.buttonsPanel);

        // Wire up action listeners.
        this.move.addActionListener(e -> {
            this.move.getModel().setSelected(true);
            setWorkbenchMode(AbstractWorkbench.SELECT_MOVE);
        });
        this.addObserved.addActionListener(e -> {
            this.addObserved.getModel().setSelected(true);
            setWorkbenchMode(AbstractWorkbench.ADD_NODE);
            this.workbench.setNodeType(GraphWorkbench.MEASURED_NODE);
        });
        this.addLatent.addActionListener(e -> {
            this.addLatent.getModel().setSelected(true);
            setWorkbenchMode(AbstractWorkbench.ADD_NODE);
            this.workbench.setNodeType(GraphWorkbench.LATENT_NODE);
        });
        this.addDirectedEdge.addActionListener(e -> {
            this.addDirectedEdge.getModel().setSelected(true);
            setWorkbenchMode(AbstractWorkbench.ADD_EDGE);
            this.workbench.setEdgeMode(GraphWorkbench.DIRECTED_EDGE);
        });
        this.addBidirectedEdge.addActionListener(e -> {
            this.addBidirectedEdge.getModel().setSelected(true);
            setWorkbenchMode(AbstractWorkbench.ADD_EDGE);
            this.workbench.setEdgeMode(GraphWorkbench.BIDIRECTED_EDGE);
        });

        // Register buttons with the toolbar.
        addButton(this.move,              "move");
        addButton(this.addObserved,       "variable");
        addButton(this.addLatent,         "latent");
        addButton(this.addDirectedEdge,   "directed");
        addButton(this.addBidirectedEdge, "bidirected");

        this.buttonsPanel.add(Box.createGlue());

        workbench.addPropertyChangeListener(this);

        // Select the move tool by default so toolbar and workbench are in sync.
        this.move.doClick();
    }

    /**
     * {@inheritDoc}
     *
     * Responds to property change events from the workbench.
     * Re-enables the edge buttons whenever the graph changes.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if ("graph".equals(e.getPropertyName())) {
            this.addDirectedEdge.setEnabled(true);
            this.addBidirectedEdge.setEnabled(true);
        }
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
            refreshButtonIcons();
        }
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sets the workbench interaction mode and updates the toolbar cursor accordingly.
     */
    private void setWorkbenchMode(int mode) {
        this.workbench.setWorkbenchMode(mode);
        setCursor(mode == AbstractWorkbench.SELECT_MOVE
                ? new Cursor(Cursor.HAND_CURSOR)
                : this.workbench.getCursor());
    }

    /**
     * Registers a button with the toolbar: sets its icon, size, and adds it
     * to the panel and button group.
     */
    private void addButton(JToggleButton button, String name) {
        String imageName = name + "3.gif";
        button.setIcon(new ImageIcon(ImageUtils.getImage(this, imageName)));
        button.setMaximumSize(new Dimension(80, 40));
        button.setPreferredSize(new Dimension(80, 40));
        this.buttonImageNames.put(button, imageName);
        this.buttonsPanel.add(button);
        this.buttonsPanel.add(Box.createVerticalStrut(5));
        this.group.add(button);
    }

    /**
     * Reloads all button icons; called after a look-and-feel change.
     */
    private void refreshButtonIcons() {
        this.buttonImageNames.forEach((button, imageName) ->
                button.setIcon(new ImageIcon(ImageUtils.getImage(this, imageName))));
    }
}