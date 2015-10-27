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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Allows the user to choose a variable in a Bayes net and edit the parameters
 * associated with that variable.  
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */

//////////////////////////////////////
// there is only one JPD table
//
public final class BayesImEditorWizardObs extends JPanel {
    private BayesIm bayesIm;
    private JComboBox varNamesComboBox;
    private GraphWorkbench workbench;
    private BayesImNodeEditingTableObs editingTable;
    private JPanel tablePanel;

    public BayesImEditorWizardObs(BayesIm bayesIm, GraphWorkbench workbench) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setFont(new Font("SanSerif", Font.BOLD, 12));		

        editingTable = new BayesImNodeEditingTableObs(bayesIm);
        editingTable.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));
        tablePanel = new JPanel();
        tablePanel.setLayout(new BorderLayout());
        tablePanel.add(scroll, BorderLayout.CENTER);
        editingTable.grabFocus();

        // Do Layout.
		
        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Click in the appropriate box and assign "
                + "a probability to each combination"));
        b3.add(Box.createHorizontalGlue());

        Box b3a = Box.createHorizontalBox();
        b3a.add(new JLabel("of variable values in that row."));
        b3a.add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(tablePanel, BorderLayout.CENTER);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Right click in table to randomize."));
        b5.add(Box.createHorizontalGlue());
		
        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Note: Editing this table with arbitrary numbers "
								+ "may result in a table "));
        b6.add(Box.createHorizontalGlue());

        Box b6a = Box.createHorizontalBox();
        b6a.add(new JLabel("inconsistent with the graph constraints."));
        b6a.add(Box.createHorizontalGlue());

        add(b3);
        add(b3a);
        add(b4);
        add(b5);
        add(Box.createVerticalStrut(5));
        add(b6);
        add(b6a);

        this.bayesIm = bayesIm;
        this.workbench = workbench;
    }

    public BayesIm getBayesIm() {
        return bayesIm;
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }
}






