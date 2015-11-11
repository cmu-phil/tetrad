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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.MbUtils;
import edu.cmu.tetrad.search.Mbfs;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Assumes that the search method of the fan search has been run and
 * shows the various options for Markov blanket DAGs consistent with
 * correlation information over the variables.
 *
 * @author Joseph Ramsey
 */
public class MbPatternDisplay extends JPanel {

    public MbPatternDisplay(final Mbfs search) {
        final List dags = MbUtils.generateMbDags(search.resultGraph(), false,
                search.getTest(), search.getDepth(), search.getTarget());

        if (dags.size() == 0) {
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    "There are no consistent DAG's.");
            return;
        }

        Graph dag = (Graph) dags.get(0);
        final GraphWorkbench graphWorkbench =
                new GraphWorkbench(dag);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(1, 1, dags.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue();
                graphWorkbench.setGraph(
                        (Graph) dags.get(index - 1));
            }
        });

        final JSpinner spinner = new JSpinner();
        JComboBox orient = new JComboBox(
                new String[]{"Orient --- only", "Orient ---, <->"});
        spinner.setModel(model);
        final JLabel totalLabel = new JLabel(" of " + dags.size());

        orient.setMaximumSize(orient.getPreferredSize());
        orient.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                String option = (String) box.getSelectedItem();

                if ("Orient --- only".equals(option)) {
                    List _dags = MbUtils.generateMbDags(search.resultGraph(), false,
                            search.getTest(), search.getDepth(), search.getTarget());
                    dags.clear();
                    dags.addAll(_dags);
                    final SpinnerNumberModel model =
                            new SpinnerNumberModel(1, 1,
                                    dags.size(), 1);
                    model.addChangeListener(new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            int index =
                                    model.getNumber().intValue();
                            graphWorkbench.setGraph(
                                    (Graph) dags.get(index - 1));
                        }
                    });
                    spinner.setModel(model);
                    totalLabel.setText(" of " + dags.size());
                    graphWorkbench.setGraph((Graph) dags.get(0));
                }
                else if ("Orient ---, <->".equals(option)) {
                    List _dags = MbUtils.generateMbDags(search.resultGraph(), true,
                            search.getTest(), search.getDepth(), search.getTarget());
                    dags.clear();
                    dags.addAll(_dags);
                    final SpinnerNumberModel model =
                            new SpinnerNumberModel(1, 1,
                                    dags.size(), 1);
                    model.addChangeListener(new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            int index =
                                    model.getNumber().intValue();
                            graphWorkbench.setGraph(
                                    (Graph) dags.get(index - 1));
                        }
                    });
                    spinner.setModel(model);
                    totalLabel.setText(" of " + dags.size());
                    graphWorkbench.setGraph((Graph) dags.get(0));
                }
            }
        });

        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Target = " + search.getTarget()));
        b1.add(Box.createHorizontalStrut(10));
        b1.add(orient);
        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("DAG "));
        b1.add(spinner);
        b1.add(totalLabel);
        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(graphWorkbench);
        jScrollPane.setPreferredSize(new Dimension(400, 400));
        graphPanel.add(jScrollPane);
        graphPanel.setBorder(new TitledBorder("Markov Blank DAG"));
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }
}



