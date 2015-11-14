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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.Sem2DataParams;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating data from OldSem nets.
 *
 * @author Joseph Ramsey
 */
public class Sem2DataParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Sem2DataParams params = null;

    /**
     * Constructs a dialog to edit the given workbench  simulation
     * getMappings object.
     */
    public Sem2DataParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (Sem2DataParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    public void setup() {

        // set up text and ties them to the parameters object being edited.
        IntTextField sampleSizeField = new IntTextField(getParams().getSampleSize(), 4);
        sampleSizeField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getParams().setSampleSize(value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });
        JCheckBox latentVarsBox = new JCheckBox("Include Latent Variables");
        latentVarsBox.setHorizontalTextPosition(SwingConstants.LEFT);
        latentVarsBox.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                JCheckBox b = (JCheckBox)e.getSource();
                getParams().setIncludeLatents(b.isSelected());
            }
        });

        JCheckBox positiveOnlyBox = new JCheckBox("Positive Data Only");
        positiveOnlyBox.setHorizontalTextPosition(SwingConstants.LEFT);
        positiveOnlyBox.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                JCheckBox b = (JCheckBox)e.getSource();
                getParams().setPositiveDataOnly(b.isSelected());
            }
        });


        setLayout(new BorderLayout());

        // continue workbench construction.
        Box b6 = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();

        b1.add(new JLabel("Sample size:  "));
        b1.add(Box.createHorizontalGlue());
        b1.add(sampleSizeField);

        Box b2 = Box.createHorizontalBox();
        b2.add(latentVarsBox);
        b2.add(Box.createHorizontalGlue());

        Box b3 = Box.createHorizontalBox();
        b3.add(positiveOnlyBox);
        b3.add(Box.createHorizontalGlue());

        b6.add(b1);
    //    b6.add(Box.createVerticalStrut(5));
     //   b6.add(hBox);
        b6.add(Box.createVerticalGlue());
        add(b6, BorderLayout.CENTER);
    }

    public boolean mustBeShown() {
        return true;
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private synchronized Sem2DataParams getParams() {
        return this.params;
    }
}



