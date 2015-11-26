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

import edu.cmu.tetrad.data.IKnowledge;

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
public class TemporalTierEditor extends JPanel
        implements PropertyChangeListener, ActionListener {


    private IKnowledge knowledge;
    //private JButton nameTiers;
    private JButton clear;

    /**
     * Field tierlist
     */
    private TierList tierList;

    public TemporalTierEditor(IKnowledge knowledge, List varNames,
            String sessionName) {

        //nameTiers = new JButton("VariableNameImpliedTiers");
        clear = new JButton("Clear All Knowledge");

        
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

        tierList = new TierList(knowledge, varNames, this);

        tierList.addPropertyChangeListener(this);
        add(tierList);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        //southPanel.add(nameTiers);
        southPanel.add(clear);

        add(southPanel);

        //nameTiers.addActionListener(this);
        clear.addActionListener(this);

        setName(getTitle());
    }

    /**
     * Reacts to property change events
     *
     * @param e the property change event.
     */
    public void propertyChange(PropertyChangeEvent e) {

        //System.out.println("prop change");
        //firePropertyChange("TemporalTierEditor",null,null);
        tierList.repaint();
        validate();
    }

    /**
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
        String graphName = ("Background Knowledge");
        return getName() + ":  " + graphName;
    }

    public void actionPerformed(ActionEvent a) {
        if (a.getSource() == this.clear) {
            knowledge.clear();
            tierList.repaint();
            tierList.refreshInfo();
            validate();
        }
        /*
        if (a.getSource() == this.nameTiers) {
            knowledge.varNameTiers();
            tierList.repaint();
            tierList.refreshInfo();
            validate();
        }
        */
    }
}





