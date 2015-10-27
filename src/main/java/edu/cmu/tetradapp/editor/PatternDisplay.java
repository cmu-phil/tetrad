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
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Assumes that the search method of the pattern search has been run and shows the
 * various options for DAG's consistent with correlation information over the variables.
 *
 * @author Joseph Ramsey
 */
public class PatternDisplay extends JPanel implements GraphEditable {
    private GraphWorkbench workbench;

    public PatternDisplay(final Graph graph) {
        final List dags = SearchGraphUtils.generatePatternDags(graph, false);

        if (dags.size() == 0) {
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    "There are no consistent DAG's.");
            return;
        }

        Graph dag = (Graph) dags.get(0);
        workbench = new GraphWorkbench(dag);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(1, 1, dags.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue();
                workbench.setGraph(
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
                    List _dags = SearchGraphUtils.generatePatternDags(graph, false);
                    dags.clear();
                    dags.addAll(_dags);
                    final SpinnerNumberModel model =
                            new SpinnerNumberModel(1, 1,
                                    dags.size(), 1);
                    model.addChangeListener(new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            int index =
                                    model.getNumber().intValue();
                            workbench.setGraph(
                                    (Graph) dags.get(index - 1));
                        }
                    });
                    spinner.setModel(model);
                    totalLabel.setText(" of " + dags.size());
                    workbench.setGraph((Graph) dags.get(0));
                }
                else if ("Orient ---, <->".equals(option)) {
                    List _dags = SearchGraphUtils.generatePatternDags(graph, true);
                    dags.clear();
                    dags.addAll(_dags);
                    final SpinnerNumberModel model =
                            new SpinnerNumberModel(1, 1,
                                    dags.size(), 1);
                    model.addChangeListener(new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            int index =
                                    model.getNumber().intValue();
                            workbench.setGraph(
                                    (Graph) dags.get(index - 1));
                        }
                    });
                    spinner.setModel(model);
                    totalLabel.setText(" of " + dags.size());
                    workbench.setGraph((Graph) dags.get(0));
                }
            }
        });

        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(orient);
        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("DAG "));
        b1.add(spinner);
        b1.add(totalLabel);
//        b1.add(Box.createHorizontalStrut(10));
//        b1.add(Box.createHorizontalGlue());
//        b1.add(new JButton(new CopySubgraphAction(this)));

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(workbench);
        jScrollPane.setPreferredSize(new Dimension(400, 400));
        graphPanel.add(jScrollPane);
        graphPanel.setBorder(new TitledBorder("DAG in Pattern"));
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
        add(menuBar(), BorderLayout.NORTH);
        add(b, BorderLayout.CENTER);
    }

    public List getSelectedModelComponents() {
        Component[] components = getWorkbench().getComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            }
            else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    public void pasteSubsession(List sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Graph getGraph() {
        return workbench.getGraph();
    }

    public void setGraph(Graph graph) {
        workbench.setGraph(graph);
    }

    /**
     * Creates the "file" menu, which allows the user to load, save, and post
     * workbench models.
     *
     * @return this menu.
     */
    private JMenuBar menuBar() {
        JMenu edit = new JMenu("Edit");
        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        edit.add(copy);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(edit);

        return menuBar;
    }
}



