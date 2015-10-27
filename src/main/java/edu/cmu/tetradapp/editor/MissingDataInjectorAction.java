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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Ricardo Silva
 */
public final class MissingDataInjectorAction extends AbstractAction {

    /**
     * The data editor.                         -
     */
    private DataEditor dataEditor;

    /**
     * 0.02 is the default probability that a given variable will be missing for
     * a given case.
     *
     * @serial Range [0, 1].
     */
    private double prob = 0.02;

    /**
     * Creates a new action to split by collinear columns.
     */
    public MissingDataInjectorAction(DataEditor editor) {
        super("Inject Missing Data Randomly");

        if (editor == null) {
            throw new NullPointerException();
        }

        this.dataEditor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        DataModel dataModel = getDataEditor().getSelectedDataModel();


        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;


            JComponent editor = editor();
            int selection = JOptionPane.showOptionDialog(
                    JOptionUtils.centeringComp(), editor, "Probability",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"Done", "Cancel"}, "Done");

            if (selection != 0) {
                return;
            }

            int numVars = dataSet.getNumColumns();

            double prob = getProb();
            double[] probs = new double[numVars];

            for (int i = 0; i < probs.length; i++) {
                probs[i] = prob;
            }

            DataSet newDataSet =
                    DataUtils.addMissingData(dataSet, probs);

            DataModelList list = new DataModelList();
            list.add(newDataSet);
            getDataEditor().reset(list);
            getDataEditor().selectFirstTab();
        }
        else {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Must be a tabular data set.");
        }
    }

    private JComponent editor() {
        JPanel editor = new JPanel();
        editor.setLayout(new BorderLayout());

        final DoubleTextField probField = new DoubleTextField(getProb(), 6, NumberFormatUtil.getInstance().getNumberFormat());
        probField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    setProb(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>" +
                "The input dataset will have missing data values inserted " +
                "<br>independently for each variable in each case with the" +
                "<br>probability specified." + "</html>"));

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html>" + "<i>Probability:  </i>" + "</html>"));
        b7.add(probField);

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        editor.add(b1, BorderLayout.CENTER);
        return editor;
    }

    private DataEditor getDataEditor() {
        return dataEditor;
    }

    private double getProb() {
        return prob;
    }

    private void setProb(double prob) {
        if (prob < 0.0 || prob > 1.0) {
            throw new IllegalArgumentException(
                    "Probability must be between 0.0 and 1.0: " + prob);
        }

        this.prob = prob;
    }
}





