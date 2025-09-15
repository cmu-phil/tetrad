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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Displays a window for editing the temporal tiers
 *
 * @author Shane Harwood
 */
class TemporalTierEditor extends JPanel
        implements PropertyChangeListener, ActionListener {


    private final Knowledge knowledge;
    //private JButton nameTiers;
    private final JButton clear;

    /**
     * Field tierlist
     */
    private final TierList tierList;

    /**
     * <p>Constructor for TemporalTierEditor.</p>
     *
     * @param knowledge   a {@link edu.cmu.tetrad.data.Knowledge} object
     * @param varNames    a {@link java.util.List} object
     * @param sessionName a {@link java.lang.String} object
     */
    public TemporalTierEditor(Knowledge knowledge, List varNames,
                              String sessionName) {

        //nameTiers = new JButton("VariableNameImpliedTiers");
        this.clear = new JButton("Clear All Knowledge");


        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        if (varNames == null) {
            throw new NullPointerException(
                    "AbstractVariable names must not be null.");
        }

        if (sessionName == null) {
            throw new NullPointerException("Session name must not be null.");
        }

        System.out.println("old Knowledge in Forb is: " + knowledge);

        this.knowledge = knowledge;

        System.out.println("\nnew Knowledge2 in Forb is: " + knowledge);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.tierList = new TierList(knowledge, varNames, this);

        this.tierList.addPropertyChangeListener(this);
        add(this.tierList);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        //southPanel.add(nameTiers);
        southPanel.add(this.clear);

        add(southPanel);

        //nameTiers.addActionListener(this);
        this.clear.addActionListener(this);

        setName(getTitle());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reacts to property change events
     */
    public void propertyChange(PropertyChangeEvent e) {


        this.tierList.repaint();
        validate();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    /**
     * @return the title of this editor.
     */
    private String getTitle() {
        final String graphName = ("Background Knowledge");
        return getName() + ":  " + graphName;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent a) {
        if (a.getSource() == this.clear) {
            this.knowledge.clear();
            this.tierList.repaint();
            this.tierList.refreshInfo();
            validate();
        }
    }
}






