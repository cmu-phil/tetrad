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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * This is the toolbar for the GraphEditor.  Its tools are as follows: <ul> <li>
 * The 'move' tool, allows the user to select and move items in the workbench
 * workbench. <li> The 'addObserved' tool, allows the user to add new observed
 * variables. <li> The 'addLatent' tool, allows the user to add new latent
 * variables. <li> The 'addDirectedEdge' tool, allows the user to add new
 * directed edges. <li> The 'addNondirectedEdge' tool, allows the user to add
 * new nondirected edges. <li> The 'addPartiallyOrientedEdge' tool, allows the
 * user to create new partially oriented edges. <li> The 'addBidirectedEdge'
 * tool, allows the user to create new bidirected edges. </ul>
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 * @see GraphEditor
 */
class SemGraphToolbar extends JPanel implements PropertyChangeListener {

    /**
     * The mutually exclusive button group for the buttons.
     */
    private ButtonGroup group;

    /**
     * The panel that the buttons are in.
     */
    private Box buttonsPanel = Box.createVerticalBox();

    // The buttons in the toolbar.
    private JToggleButton move, addObserved, addLatent, addDirectedEdge, addBidirectedEdge;

    /**
     * The workbench this toolbar governs.
     */
    private GraphWorkbench workbench;

    /**
     * Constructs a new Graph toolbar governing the modes of the given
     * GraphWorkbench.
     */
    public SemGraphToolbar(GraphWorkbench workbench) {
        if (workbench == null) {
            throw new NullPointerException();
        }

        this.workbench = workbench;
        group = new ButtonGroup();

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        buttonsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(buttonsPanel);

        // construct the bottons.
        move = new JToggleButton();
        addObserved = new JToggleButton();
        addLatent = new JToggleButton();
        addDirectedEdge = new JToggleButton();
        addBidirectedEdge = new JToggleButton();

        // Adding this listener fixes a previous bug where if you
        // select a button and then move the mouse away from the
        // button without releasing the mouse it would deselect. J
        // Ramsey 11/02/01
        FocusListener focusListener = new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                JToggleButton component = (JToggleButton) e.getComponent();
                component.doClick();
            }
        };

        move.addFocusListener(focusListener);
        addObserved.addFocusListener(focusListener);
        addLatent.addFocusListener(focusListener);
        addDirectedEdge.addFocusListener(focusListener);
        addBidirectedEdge.addFocusListener(focusListener);

        // add listeners
        move.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                move.getModel().setSelected(true);
                setWorkbenchMode(GraphWorkbench.SELECT_MOVE);
            }
        });
        addObserved.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addObserved.getModel().setSelected(true);
                setWorkbenchMode(GraphWorkbench.ADD_NODE);
                setNodeMode(GraphWorkbench.MEASURED_NODE);
            }
        });
        addLatent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addLatent.getModel().setSelected(true);
                setWorkbenchMode(GraphWorkbench.ADD_NODE);
                setNodeMode(GraphWorkbench.LATENT_NODE);
            }
        });
        addDirectedEdge.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addDirectedEdge.getModel().setSelected(true);
                setWorkbenchMode(GraphWorkbench.ADD_EDGE);
                setEdgeMode(GraphWorkbench.DIRECTED_EDGE);
            }
        });
        addBidirectedEdge.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addBidirectedEdge.getModel().setSelected(true);
                setWorkbenchMode(GraphWorkbench.ADD_EDGE);
                setEdgeMode(GraphWorkbench.BIDIRECTED_EDGE);
            }
        });

        // add buttons to the toolbar.
        addButton(move, "move");
        addButton(addObserved, "variable");
        addButton(addLatent, "latent");
        addButton(addDirectedEdge, "directed");
        addButton(addBidirectedEdge, "bidirected");
        workbench.addPropertyChangeListener(this);
        selectArrowTools();

        buttonsPanel.add(Box.createGlue());
    }

    /**
     * Convenience method to set the mode of the workbench.  Placed here because
     * Java will not allow access to the variable 'workbench' from inner
     * classes.
     */
    private void setWorkbenchMode(int mode) {
        workbench.setWorkbenchMode(mode);
        setCursor(workbench.getCursor());
    }

    /**
     * Convenience method to set the mode of the workbench.  Placed here because
     * Java will not allow access to the variable 'workbench' from inner
     * classes.
     */
    private void setEdgeMode(int mode) {
        workbench.setEdgeMode(mode);
    }

    /**
     * Convenience method to set the mode of the workbench.  Placed here because
     * Java will not allow access to the variable 'workbench' from inner
     * classes.
     */
    private void setNodeMode(int mode) {
        workbench.setNodeType(mode);
    }

    /**
     * Adds the various buttons to the toolbar, setting their properties
     * appropriately.
     */
    private void addButton(JToggleButton button, String name) {
        button.setIcon(
                new ImageIcon(ImageUtils.getImage(this, name + "3.gif")));
        button.setMaximumSize(new Dimension(80, 40));
        button.setPreferredSize(new Dimension(80, 40));
        buttonsPanel.add(button);
        buttonsPanel.add(Box.createVerticalStrut(5));
        group.add(button);
    }

    /**
     * Responds to property change events.
     */
    public void propertyChange(PropertyChangeEvent e) {
        if ("graph".equals(e.getPropertyName())) {
            selectArrowTools();
        }
    }

    /**
     * For each workbench type, enables the arrow tools which that workbench can
     * use and disables all others.
     */
    private void selectArrowTools() {
        addDirectedEdge.setEnabled(true);
        addBidirectedEdge.setEnabled(true);
    }
}





