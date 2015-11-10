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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Lets the user interact with a SEM estimator.
 *
 * @author Joseph Ramsey
 */
public final class SemEstimatorEditor extends JPanel {
    private SemEstimatorWrapper wrapper;
    private JPanel panel;
    private JComboBox optimizerCombo = new JComboBox();
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    JComboBox scoreBox;
    IntTextField restarts;


    /**
     * Maximum number of free parameters for which statistics will be
     * calculated. (Calculating standard errors is high complexity.) Set this to
     * zero to turn  off statistics calculations (which can be problematic
     * sometimes).
     */
    private int maxFreeParamsForStatistics = 200;


    public SemEstimatorEditor(SemEstimatorWrapper _wrapper) {
        wrapper = _wrapper;
        panel = new JPanel();
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);

        optimizerCombo = new JComboBox();
        optimizerCombo.addItem("Regression");
        optimizerCombo.addItem("EM");
        optimizerCombo.addItem("Powell");
        optimizerCombo.addItem("Random Search");
        optimizerCombo.addItem("RICF");

        optimizerCombo.setMaximumSize(new Dimension(200, 25));

        optimizerCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                wrapper.setSemOptimizerType((String) box.getSelectedItem());
            }
        });

        scoreBox = new JComboBox();
        restarts = new IntTextField(1, 2);

        scoreBox.addItem("Fgls");
        scoreBox.addItem("Fml");
        scoreBox.setMaximumSize(new Dimension(100, 25));

        scoreBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                String type = (String) box.getSelectedItem();
                if ("Fgls".equals(type)) {
                    wrapper.setScoreType(SemIm.ScoreType.Fgls);
                } else if ("Fml".equals(type)) {
                    wrapper.setScoreType(SemIm.ScoreType.Fml);
                }
            }
        });

        restarts.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    wrapper.setNumRestarts(value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });

        String semOptimizerType = wrapper.getParams().getSemOptimizerType();

        optimizerCombo.setSelectedItem(semOptimizerType);
        SemIm.ScoreType scoreType = wrapper.getParams().getScoreType();
        if (scoreType == null) scoreType = SemIm.ScoreType.Fgls;
        scoreBox.setSelectedItem(scoreType.toString());
        restarts.setValue(wrapper.getParams().getNumRestarts());

        JButton estimateButton = new JButton("Estimate Again");

        estimateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        reestimate();
                    }
                };
            }
        });

        JButton report = new JButton("Report");

        report.addActionListener((new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final JTextArea textArea = new JTextArea();
                JScrollPane scroll = new JScrollPane(textArea);
//                scroll.setPreferredSize(new Dimension(600, 600));

                textArea.append(compileReport());

                Box b = Box.createVerticalBox();
                Box b2 = Box.createHorizontalBox();
                b2.add(scroll);
                textArea.setCaretPosition(0);
                b.add(b2);

                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());
                panel.add(b);

                EditorWindow window = new EditorWindow(panel,
                        "All Paths", "Close", false, SemEstimatorEditor.this);
                DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
                window.setVisible(true);
            }
        }));

        Box lowerBarA = Box.createHorizontalBox();
        lowerBarA.add(Box.createHorizontalGlue());
        lowerBarA.add(new JLabel("Score"));
        lowerBarA.add(scoreBox);
        lowerBarA.add(new JLabel("Random Restarts"));
        lowerBarA.add(restarts);

        Box lowerBarB = Box.createHorizontalBox();
        lowerBarB.add(Box.createHorizontalGlue());
        lowerBarB.add(new JLabel("Choose Optimizer:  "));
        lowerBarB.add(optimizerCombo);
        lowerBarB.add(Box.createHorizontalStrut(10));
        lowerBarB.add(estimateButton);

        Box lowerBar = Box.createVerticalBox();
        lowerBar.add(lowerBarA);
        lowerBar.add(lowerBarB);

        if (wrapper.getMultipleResultList().size() > 1) {
            lowerBar.add(report);
        }

        add(lowerBar, BorderLayout.SOUTH);

//        setPreferredSize(new Dimension(600,  600));

        resetSemImEditor();
    }

    private String compileReport() {
        StringBuilder builder = new StringBuilder();

        builder.append("Datset\tFrom\tTo\tType\tValue\tSE\tT\tP");

        java.util.List<SemEstimator> estimators = wrapper.getMultipleResultList();

        for (int i = 0; i < estimators.size(); i++) {
            SemEstimator estimator = estimators.get(i);

            SemIm estSem = estimator.getEstimatedSem();
            String dataName = estimator.getDataSet().getName();

            for (Parameter parameter : estSem.getFreeParameters()) {
                builder.append("\n");
                builder.append(dataName + "\t");
                builder.append(parameter.getNodeA() + "\t");
                builder.append(parameter.getNodeB() + "\t");
                builder.append(typeString(parameter) + "\t");
                builder.append(asString(paramValue(estSem, parameter)) + "\t");
                builder.append(asString(estSem.getStandardError(parameter,
                        maxFreeParamsForStatistics)) + "\t");
                builder.append(asString(estSem.getTValue(parameter,
                        maxFreeParamsForStatistics)) + "\t");
                builder.append(asString(estSem.getPValue(parameter,
                        maxFreeParamsForStatistics)) + "\t");
            }

            List<Node> nodes = estSem.getVariableNodes();

            for (int j = 0; j < nodes.size(); j++) {
                Node node = nodes.get(j);

                int n = estSem.getSampleSize();
                int df = n - 1;
                double mean = estSem.getMean(node);
                double stdDev = estSem.getMeanStdDev(node);
                double stdErr = stdDev / Math.sqrt(n);

                double tValue = mean / stdErr;
                double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));

                builder.append("\n");
                builder.append(dataName + "\t");
                builder.append(nodes.get(j) + "\t");
                builder.append(nodes.get(j) + "\t");
                builder.append("Mean" + "\t");
                builder.append(asString(mean) + "\t");
                builder.append(asString(stdErr) + "\t");
                builder.append(asString(tValue) + "\t");
                builder.append(asString(p) + "\t");
            }
        }

        return builder.toString();
    }

    private String asString(double value) {
        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return nf.format(value);
        }
    }

    private String typeString(Parameter parameter) {
        ParamType type = parameter.getType();

        if (type == ParamType.COEF) {
            return "Coef";
        }

//        if (editor.isEditCovariancesAsCorrelations()) {
//            if (type == ParamType.VAR) {
//                return "Correlation";
//            }
//
//            if (type == ParamType.COVAR) {
//                return "Correlation";
//            }
//        }

        if (type == ParamType.VAR) {
            //return "Variance";
            return "StdDev";
        }

        if (type == ParamType.COVAR) {
            return "Covar";
        }

        throw new IllegalStateException("Unknown param type.");
    }

    private double paramValue(SemIm im, Parameter parameter) {
        double paramValue = im.getParamValue(parameter);

//        if (editor.isEditCovariancesAsCorrelations()) {
//            if (parameter.getType() == ParamType.VAR) {
//                paramValue = 1.0;
//            }
//            if (parameter.getType() == ParamType.COVAR) {
//                Node nodeA = parameter.getNodeA();
//                Node nodeB = parameter.getNodeB();
//
//                double varA = semIm().getParamValue(nodeA, nodeA);
//                double varB = semIm().getParamValue(nodeB, nodeB);
//
//                paramValue *= Math.sqrt(varA * varB);
//            }
//        } else {
        if (parameter.getType() == ParamType.VAR) {
            paramValue = Math.sqrt(paramValue);
        }
//        }

        return paramValue;
    }

    private void reestimate() {
        SemOptimizer optimizer;
//        Object type = optimizerCombo.getSelectedItem();

        String type = wrapper.getSemOptimizerType();

        if ("Regression".equals(type)) {
            optimizer = new SemOptimizerRegression();
        } else if ("EM".equals(type)) {
            optimizer = new SemOptimizerEm();
        } else if ("Powell".equals(type)) {
            optimizer = new SemOptimizerPowell();
        } else if ("Random Search".equals(type)) {
            optimizer = new SemOptimizerScattershot();
        } else if ("RICF".equals(type)) {
            optimizer = new SemOptimizerRicf();
        } else if ("Powell".equals(type)) {
            optimizer = new SemOptimizerPowell();
        } else {
            throw new IllegalArgumentException("Unexpected optimizer " +
                    "type: " + type);
        }

        int numRestarts = wrapper.getNumRestarts();
        optimizer.setNumRestarts(numRestarts);

        java.util.List<SemEstimator> estimators = wrapper.getMultipleResultList();
        java.util.List<SemEstimator> newEstimators = new ArrayList<SemEstimator>();

        for (SemEstimator estimator : estimators) {
            SemPm semPm = estimator.getSemPm();

            DataSet dataSet = estimator.getDataSet();
            ICovarianceMatrix covMatrix = estimator.getCovMatrix();

            SemEstimator newEstimator;

            if (dataSet != null) {
                newEstimator = new SemEstimator(dataSet, semPm, optimizer);
                newEstimator.setNumRestarts(numRestarts);
                newEstimator.setScoreType(wrapper.getScoreType());
            } else if (covMatrix != null) {
                newEstimator = new SemEstimator(covMatrix, semPm, optimizer);
                newEstimator.setNumRestarts(numRestarts);
                newEstimator.setScoreType(wrapper.getScoreType());
            } else {
                throw new IllegalStateException("Only continuous " +
                        "rectangular data sets and covariance matrices " +
                        "can be processed.");
            }

            newEstimator.estimate();
            newEstimators.add(newEstimator);
        }

        wrapper.setSemEstimator(newEstimators.get(0));

        wrapper.setMultipleResultList(newEstimators);
        resetSemImEditor();
    }

    private void resetSemImEditor() {
        java.util.List<SemEstimator> semEstimators = wrapper.getMultipleResultList();

        if (semEstimators.size() == 1) {
            SemEstimator estimatedSem = semEstimators.get(0);
            SemImEditor editor = new SemImEditor(estimatedSem.getEstimatedSem());
            final JPanel _panel = new JPanel();
            _panel.setLayout(new BorderLayout());
            _panel.add(editor, BorderLayout.CENTER);

            panel.removeAll();
            panel.add(_panel, BorderLayout.CENTER);
            panel.revalidate();
            panel.repaint();

            // Fixes the problem of the IM panel not resizing correctly. -jdramsey 2/2/12
            panel.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent componentEvent) {
                    Component component = componentEvent.getComponent();
                    _panel.setPreferredSize(component.getSize());
//                    _panel.setMinimumSize(component.getSize());
                    panel.revalidate();
                    panel.repaint();
                }
            });

        } else {
            JTabbedPane tabs = new JTabbedPane();

            for (int i = 0; i < semEstimators.size(); i++) {
                SemEstimator estimatedSem = semEstimators.get(i);
                SemImEditor editor = new SemImEditor(estimatedSem.getEstimatedSem());
                JPanel _panel = new JPanel();
                _panel.setLayout(new BorderLayout());
                _panel.add(editor, BorderLayout.CENTER);
                tabs.addTab(estimatedSem.getDataSet().getName(), _panel);
            }

            panel.removeAll();
            panel.add(tabs);
            panel.revalidate();
            panel.repaint();
        }
    }
}


