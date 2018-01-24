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
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemOptimizer;
import edu.cmu.tetrad.sem.SemOptimizerEm;
import edu.cmu.tetrad.sem.SemOptimizerPowell;
import edu.cmu.tetrad.sem.SemOptimizerRegression;
import edu.cmu.tetrad.sem.SemOptimizerRicf;
import edu.cmu.tetrad.sem.SemOptimizerScattershot;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.model.SemImWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import java.awt.BorderLayout;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/**
 * Lets the user interact with a SEM estimator.
 *
 * @author Joseph Ramsey
 */
public final class SemEstimatorEditor extends JPanel {

    private static final long serialVersionUID = 960988184083427499L;

    private final SemEstimatorWrapper wrapper;
    private final JPanel panel;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JComboBox scoreBox;
    private final IntTextField restarts;

    public SemEstimatorEditor(SemPm semPm, DataSet dataSet) {
        this(new SemEstimatorWrapper(dataSet, semPm, new Parameters()));
    }

    public SemEstimatorEditor(SemEstimatorWrapper _wrapper) {
        this.wrapper = _wrapper;
        panel = new JPanel();
        setLayout(new BorderLayout());

        JComboBox optimizerCombo = new JComboBox();
        optimizerCombo.addItem("Regression");
        optimizerCombo.addItem("EM");
        optimizerCombo.addItem("Powell");
        optimizerCombo.addItem("Random Search");
        optimizerCombo.addItem("RICF");

        optimizerCombo.addActionListener((e) -> {
            JComboBox box = (JComboBox) e.getSource();
            wrapper.setSemOptimizerType((String) box.getSelectedItem());
        });

        scoreBox = new JComboBox();
        restarts = new IntTextField(1, 2);

        scoreBox.addItem("Fgls");
        scoreBox.addItem("Fml");

        scoreBox.addActionListener((e) -> {
            JComboBox box = (JComboBox) e.getSource();
            String type = (String) box.getSelectedItem();
            if ("Fgls".equals(type)) {
                wrapper.setScoreType(ScoreType.Fgls);
            } else if ("Fml".equals(type)) {
                wrapper.setScoreType(ScoreType.Fml);
            }
        });

        restarts.setFilter((value, oldValue) -> {
            try {
                wrapper.setNumRestarts(value);
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });

        String semOptimizerType = wrapper.getParams().getString("semOptimizerType", "Regression");

        optimizerCombo.setSelectedItem(semOptimizerType);
        ScoreType scoreType = (ScoreType) wrapper.getParams().get("scoreType", ScoreType.Fgls);
        if (scoreType == null) {
            scoreType = ScoreType.Fgls;
        }
        scoreBox.setSelectedItem(scoreType.toString());
        restarts.setValue(wrapper.getParams().getInt("numRestarts", 1));

        JButton estimateButton = new JButton("Estimate Again");

        estimateButton.addActionListener((e) -> {
            Window owner = (Window) getTopLevelAncestor();

            new WatchedProcess(owner) {
                @Override
                public void watch() {
                    reestimate();
                }
            };
        });

        JButton report = new JButton("Report");

        report.addActionListener((e) -> {
            final JTextArea textArea = new JTextArea();
            JScrollPane scroll = new JScrollPane(textArea);

            textArea.append(compileReport());

            Box b = Box.createVerticalBox();
            Box b2 = Box.createHorizontalBox();
            b2.add(scroll);
            textArea.setCaretPosition(0);
            b.add(b2);

            JPanel editorPanel = new JPanel(new BorderLayout());
            editorPanel.add(b);

            EditorWindow window = new EditorWindow(editorPanel,
                    "All Paths", "Close", false, SemEstimatorEditor.this);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.setVisible(true);
        });

        Box lowerBarA = Box.createHorizontalBox();
        lowerBarA.add(new JLabel("Score"));
        lowerBarA.add(scoreBox);
        lowerBarA.add(Box.createHorizontalGlue());
        lowerBarA.add(new JLabel("Random Restarts"));
        lowerBarA.add(restarts);

        Box lowerBarB = Box.createHorizontalBox();
        lowerBarB.add(new JLabel("Choose Optimizer:  "));
        lowerBarB.add(optimizerCombo);
        lowerBarB.add(Box.createHorizontalGlue());
        lowerBarB.add(estimateButton);

        Box lowerBar = Box.createVerticalBox();
        lowerBar.add(lowerBarA);
        lowerBar.add(lowerBarB);

        if (wrapper.getMultipleResultList().size() > 1) {
            lowerBar.add(report);
        }

        add(lowerBar, BorderLayout.SOUTH);

        resetSemImEditor();

        add(panel, BorderLayout.CENTER);
        validate();
    }

    private String compileReport() {
        StringBuilder builder = new StringBuilder();

        builder.append("Datset\tFrom\tTo\tType\tValue\tSE\tT\tP");

        java.util.List<SemEstimator> estimators = wrapper.getMultipleResultList();

        for (int i = 0; i < estimators.size(); i++) {
            SemEstimator estimator = estimators.get(i);

            SemIm estSem = estimator.getEstimatedSem();
            String dataName = estimator.getDataSet().getName();

            estSem.getFreeParameters().forEach(parameter -> {
                builder.append("\n");
                builder.append(dataName).append("\t");
                builder.append(parameter.getNodeA()).append("\t");
                builder.append(parameter.getNodeB()).append("\t");
                builder.append(typeString(parameter)).append("\t");
                builder.append(asString(paramValue(estSem, parameter))).append("\t");

                /**
                 * Maximum number of free parameters for which statistics will
                 * be calculated. (Calculating standard errors is high
                 * complexity.) Set this to zero to turn off statistics
                 * calculations (which can be problematic sometimes).
                 */
                int maxFreeParamsForStatistics = 200;
                builder.append(asString(estSem.getStandardError(parameter,
                        maxFreeParamsForStatistics))).append("\t");
                builder.append(asString(estSem.getTValue(parameter,
                        maxFreeParamsForStatistics))).append("\t");
                builder.append(asString(estSem.getPValue(parameter,
                        maxFreeParamsForStatistics))).append("\t");
            });

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
                builder.append(dataName).append("\t");
                builder.append(nodes.get(j)).append("\t");
                builder.append(nodes.get(j)).append("\t");
                builder.append("Mean").append("\t");
                builder.append(asString(mean)).append("\t");
                builder.append(asString(stdErr)).append("\t");
                builder.append(asString(tValue)).append("\t");
                builder.append(asString(p)).append("\t");
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

        if (parameter.getType() == ParamType.VAR) {
            paramValue = Math.sqrt(paramValue);
        }

        return paramValue;
    }

    private void reestimate() {
        SemOptimizer optimizer;

        String type = wrapper.getSemOptimizerType();

        switch (type) {
            case "Regression":
                optimizer = new SemOptimizerRegression();
                break;
            case "EM":
                optimizer = new SemOptimizerEm();
                break;
            case "Powell":
                optimizer = new SemOptimizerPowell();
                break;
            case "Random Search":
                optimizer = new SemOptimizerScattershot();
                break;
            case "RICF":
                optimizer = new SemOptimizerRicf();
                break;
            default:
                throw new IllegalArgumentException("Unexpected optimizer type: "
                        + type);
        }

        int numRestarts = wrapper.getNumRestarts();
        optimizer.setNumRestarts(numRestarts);

        java.util.List<SemEstimator> estimators = wrapper.getMultipleResultList();
        java.util.List<SemEstimator> newEstimators = new ArrayList<>();

        estimators.forEach(estimator -> {
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
                throw new IllegalStateException("Only continuous rectangular"
                        + " data sets and covariance matrices can be processed.");
            }

            newEstimator.estimate();
            newEstimators.add(newEstimator);
        });

        wrapper.setSemEstimator(newEstimators.get(0));

        wrapper.setMultipleResultList(newEstimators);
        resetSemImEditor();
    }

    private void resetSemImEditor() {
        java.util.List<SemEstimator> semEstimators = wrapper.getMultipleResultList();

        if (semEstimators.size() == 1) {
            SemEstimator estimatedSem = semEstimators.get(0);
            SemImEditor editor = new SemImEditor(new SemImWrapper(estimatedSem.getEstimatedSem()));
            panel.removeAll();
            panel.add(editor, BorderLayout.CENTER);
            panel.revalidate();
            panel.repaint();

        } else {
            JTabbedPane tabs = new JTabbedPane();

            for (int i = 0; i < semEstimators.size(); i++) {
                SemEstimator estimatedSem = semEstimators.get(i);
                SemImEditor editor = new SemImEditor(new SemImWrapper(estimatedSem.getEstimatedSem()));
                JPanel _panel = new JPanel();
                _panel.setLayout(new BorderLayout());
                _panel.add(editor, BorderLayout.CENTER);
                tabs.addTab(estimatedSem.getDataSet().getName(), _panel);
            }

            panel.removeAll();
            panel.add(tabs);
            panel.validate();
        }
    }
}
