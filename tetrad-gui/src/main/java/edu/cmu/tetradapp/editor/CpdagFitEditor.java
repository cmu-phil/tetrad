///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetradapp.model.CPDAGFitModel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Displays a CpdagFitModel object as a JTable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CpdagFitEditor extends JPanel {

    /**
     * The model to display.
     */
    private final CPDAGFitModel comparison;


    /**
     * Constructs the editor given the model
     *
     * @param comparison a {@link CPDAGFitModel} object
     */
    public CpdagFitEditor(CPDAGFitModel comparison) {
        this.comparison = comparison;
        setup();
    }

    //============================ Private Methods =========================//

    private void setup() {
        JTabbedPane pane = new JTabbedPane(SwingConstants.LEFT);

        DataModelList data = this.comparison.getDataModelList();
        List<BayesIm> bayesIms = this.comparison.getBayesIms();
        List<SemPm> semPms = this.comparison.getSemPms();

        if (bayesIms != null && semPms != null) {
            throw new IllegalArgumentException("That's weird; both Bayes and SEM estimations were done. Please complain.");
        }

        if (bayesIms != null) {
            for (int i = 0; i < bayesIms.size(); i++) {
                BayesEstimatorEditor editor = new BayesEstimatorEditor(bayesIms.get(i), (DataSet) data.get(i));

                JPanel panel = new JPanel();

                JScrollPane scroll = new JScrollPane(editor);
                scroll.setPreferredSize(new Dimension(900, 600));

                panel.add(Box.createVerticalStrut(10));

                Box box = Box.createHorizontalBox();
                panel.add(box);
                panel.add(Box.createVerticalStrut(10));

                Box box1 = Box.createHorizontalBox();
                box1.add(new JLabel("Graph Comparison: "));
                box1.add(Box.createHorizontalGlue());

                add(box1);
                setLayout(new BorderLayout());

                pane.add("" + (i + 1), scroll);
            }
        }

        if (semPms != null) {
            for (int i = 0; i < semPms.size(); i++) {
                SemEstimatorEditor editor = new SemEstimatorEditor(semPms.get(i), (DataSet) data.get(i));
                pane.add("" + (i + 1), editor);
            }
        }

        add(pane);
    }

}



