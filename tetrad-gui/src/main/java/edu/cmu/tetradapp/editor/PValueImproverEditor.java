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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.DagScorer;
import edu.cmu.tetrad.sem.Scorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetradapp.model.PValueImproverWrapper;
import edu.cmu.tetradapp.model.PcIndTestParams;
import edu.cmu.tetradapp.model.PcSearchParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * Edits BFF. (We used to call BFF the P Value Improver.)
 */
public class PValueImproverEditor extends JPanel implements LayoutEditable {
    private GraphWorkbench graphWorkbench;
    private PValueImproverWrapper wrapper;
    private PcSearchParams params;

    private DoubleTextField alphaField;
    private IntTextField beamWidthField;
    private DoubleTextField zeroEdgePField;

    private JPanel panel = new JPanel();
    private SemIm originalSemIm;
    private SemIm newSemIm;

    public PValueImproverEditor(final PValueImproverWrapper wrapper) {
        this.setWrapper(wrapper);
        this.params = (PcSearchParams) wrapper.getParams();

        panel = new JPanel();
        panel.setLayout(new BorderLayout());

        if (wrapper.getGraph() != null && wrapper.getResultGraph() != null && getOriginalSemIm() != null) {
            GraphUtils.circleLayout(wrapper.getGraph(), 200, 200, 150);
            setGraphWorkbench(new GraphWorkbench(wrapper.getGraph()));
            setOriginalSemIm(wrapper.getOriginalSemIm());
            setNewSemIm(wrapper.getNewSemIm());
        } else {
            EdgeListGraph graph = new EdgeListGraph();
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            SemIm im2 = new SemIm(pm);
            GraphUtils.circleLayout(graph, 200, 200,150);
            setGraphWorkbench(new GraphWorkbench(graph));
            setOriginalSemIm(im);
            setNewSemIm(im2);
        }

        final PcIndTestParams indTestParams = (PcIndTestParams) getParams().getIndTestParams();
        double alpha = indTestParams.getAlpha();
        int beamWidth = indTestParams.getBeamWidth();
        double zeroEdgeP = indTestParams.getZeroEdgeP();

        alphaField = new DoubleTextField(alpha, 6,
                new DecimalFormat("0.0########"));
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value >= 0 && value <= 1) {
                    indTestParams.setAlpha(value);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });

        beamWidthField = new IntTextField(beamWidth, 6);
        beamWidthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value >= 1) {
                    indTestParams.setBeamWidth(value);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });


        zeroEdgePField = new DoubleTextField(zeroEdgeP, 6,
                new DecimalFormat("0.0########"));
        zeroEdgePField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value >= 0 && value <= 1) {
                    indTestParams.setZeroEdgeP(value);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });

        final JButton search = new JButton("Search");

        search.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        getWrapper().execute();
                        Graph graph = getWrapper().getGraph();
                        GraphUtils.circleLayout(graph, 200, 200, 150);
                        setGraphWorkbench(new GraphWorkbench(graph));
                    }
                };
            }
        });

        alphaField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                search.doClick();
            }
        });

        zeroEdgePField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                search.doClick();
            }
        });

        beamWidthField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                search.doClick();
            }
        });

        JRadioButton beamRadioButton = new JRadioButton("Beam Search");
        JRadioButton gesRadioButton = new JRadioButton("GES Search");

        ButtonGroup group = new ButtonGroup();
        group.add(beamRadioButton);
        group.add(gesRadioButton);

        JCheckBox shuffleCheckbox = new JCheckBox("Shuffle Moves");
        shuffleCheckbox.setSelected(wrapper.isShuffleMoves());

        beamRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                wrapper.setAlgorithmType(PValueImproverWrapper.AlgorithmType.BEAM);
                alphaField.setEnabled(true);
                beamWidthField.setEnabled(true);
            }
        });

        shuffleCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkbox = (JCheckBox) actionEvent.getSource();
//                wrapper.setShuffleMoves(checkbox.isSelected());
            }
        });

        gesRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                wrapper.setAlgorithmType(PValueImproverWrapper.AlgorithmType.GES);
                alphaField.setEnabled(false);
                beamWidthField.setEnabled(false);
            }
        });

        if (getWrapper().getAlgorithmType() == PValueImproverWrapper.AlgorithmType.BEAM) {
            beamRadioButton.setSelected(true);
            alphaField.setEnabled(true);
            beamWidthField.setEnabled(true);
        }
        else if (getWrapper().getAlgorithmType() == PValueImproverWrapper.AlgorithmType.GES) {
            gesRadioButton.setSelected(true);
            alphaField.setEnabled(false);
            beamWidthField.setEnabled(false);
        }
        else {
            throw new IllegalStateException();
        }

        Box b = Box.createHorizontalBox();

        Box b2 = Box.createVerticalBox();
        Box b2a = Box.createHorizontalBox();
        b2a.add(new JLabel("Search Alpha"));
        b2a.add(Box.createHorizontalGlue());
        b2a.add(alphaField);
        b2.add(b2a);

        Box b2e = Box.createHorizontalBox();
        b2e.add(new JLabel("Zero Edge Alpha"));
        b2e.add(Box.createHorizontalGlue());
        b2e.add(zeroEdgePField);
        b2.add(b2e);

        Box b2b = Box.createHorizontalBox();
        b2b.add(new JLabel("Beam Width"));
        b2b.add(Box.createHorizontalGlue());
        b2b.add(beamWidthField);
        b2.add(b2b);

        Box b2c = Box.createHorizontalBox();
        b2c.add(beamRadioButton);
        b2c.add(Box.createHorizontalGlue());
        b2.add(b2c);

//        Box b2c2 = Box.createHorizontalBox();
//        b2c2.add(Box.createRigidArea(new Dimension(20, 4)));
//        b2c2.add(shuffleCheckbox);
//        b2c2.add(Box.createHorizontalGlue());
//        b2.add(b2c2);

        Box b2d = Box.createHorizontalBox();
        b2d.add(gesRadioButton);
        b2d.add(Box.createHorizontalGlue());
        b2.add(b2d);

        b2.setBorder(new TitledBorder("Parameters"));
        b2.add(Box.createVerticalStrut(30));

        Box b3 = Box.createHorizontalBox();
        b3.add(Box.createHorizontalGlue());
        b3.add(search);
        b2.add(b3);
        b2.add(Box.createVerticalGlue());

        b.add(b2);

        b.add(panel);

        setLayout(new BorderLayout());

        add(b, BorderLayout.CENTER);

        setPreferredSize(new Dimension(700,  600));

    }


    private void setNewSemIm(SemIm newSemIm) {
        this.newSemIm = newSemIm;
    }

    private void setOriginalSemIm(SemIm originalSemIm) {
        if (this.originalSemIm == null) {
            this.originalSemIm = originalSemIm;
        }
    }

    public Graph getGraph() {
        return getWrapper().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return graphWorkbench.getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return graphWorkbench.getModelNodesToDisplay();
    }

    public IKnowledge getKnowledge() {
        return getWrapper().getParams().getKnowledge();
    }

    public Graph getSourceGraph() {
        return getWrapper().getSourceGraph();
    }

    public void layoutByGraph(Graph graph) {
        getGraphWorkbench().layoutByGraph(graph);
    }

    public void layoutByKnowledge() {
        getGraphWorkbench().layoutByKnowledge();
    }

    public PValueImproverWrapper getWrapper() {
        return wrapper;
    }

    public void setWrapper(PValueImproverWrapper wrapper) {
        this.wrapper = wrapper;
    }

    private GraphWorkbench getGraphWorkbench() {
        return graphWorkbench;
    }

    public void setGraphWorkbench(final GraphWorkbench graphWorkbench) {
        JTabbedPane tabbedPane = new JTabbedPane();

        this.graphWorkbench = graphWorkbench;
        if (getOriginalSemIm() != null) {
            setOriginalSemIm(new SemIm(getWrapper().getOriginalSemIm()));
        }
        this.newSemIm = getWrapper().getNewSemIm();

        if (getNewSemIm() != null) {
            SemImEditor newEditor = new SemImEditor(getNewSemIm(), "Graphical Editor",
                    "Tabular Editor", SemImEditor.TabbedPaneDefault.STATS);
            final GraphWorkbench workbench = newEditor.getWorkbench();

            workbench.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                    if ("modelChanged".equals(propertyChangeEvent.getPropertyName())) {
                        System.out.println(propertyChangeEvent);
                        Graph graph = workbench.getGraph();

                        System.out.println(graph);

                        try {
                            new Dag(graph);
                        } catch (IllegalArgumentException e) {
                            return;
                        }

                        Scorer scorer = new DagScorer((DataSet) getWrapper().getDataModel());
                        scorer.score(graph);
                        getWrapper().setNewSemIm(scorer.getEstSem());
                        setGraphWorkbench(graphWorkbench);
                    }
                }
            });

            tabbedPane.addTab("New Model", newEditor);
        }

        if (getOriginalSemIm() != null) {
            SemImEditor originalEditor = new SemImEditor(getOriginalSemIm(), "Graphical Editor",
                    "Tabular Editor", SemImEditor.TabbedPaneDefault.STATS);
            tabbedPane.addTab("Original Model", originalEditor);
        }

        if (graphWorkbench != null) {
            tabbedPane.addTab("Pattern", graphWorkbench);
        }

        panel.removeAll();
        panel.add(tabbedPane, BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();

        firePropertyChange("modelChanged", null, null);
    }

    public PcSearchParams getParams() {
        return params;
    }

    public SemIm getOriginalSemIm() {
        return originalSemIm;
    }

    public SemIm getNewSemIm() {
        return newSemIm;
    }
}



