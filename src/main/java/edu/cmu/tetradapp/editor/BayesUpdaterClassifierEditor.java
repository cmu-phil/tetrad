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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BayesUpdaterClassifier;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RocCalculator;
import edu.cmu.tetradapp.model.BayesUpdaterClassifierWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * Allows the user to do classifications using the Bayes Updater Classifier.
 *
 * @author Jossph Ramsey
 */
public class BayesUpdaterClassifierEditor extends JPanel {
    private BayesUpdaterClassifier classifier;
    private JComboBox variableDropdown;
    private JTabbedPane tabbedPane;
    private JComboBox categoryDropdown;
//    private double binaryCutoff = 0.5;
//    private DoubleTextField binaryCutoffField;
    private GraphWorkbench workbench;
    private RocPlot rocPlot;
    private JMenuItem saveRoc;

    private BayesUpdaterClassifierEditor(BayesUpdaterClassifier classifier) {
        if (classifier == null) {
            throw new NullPointerException();
        }

        this.classifier = classifier;
        setLayout(new BorderLayout());

        setPreferredSize(new Dimension(600, 600));

        Box b = Box.createVerticalBox();
        b.add(getToolbar());
        b.add(getDisplayPanel());
        add(b, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));

        saveRoc = new JMenuItem("Save ROC Plot Image...");
        saveRoc.setEnabled(false);

        saveRoc.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveRocImage();
            }
        });

        file.add(saveRoc);
        add(menuBar, BorderLayout.NORTH);

        if (classifier.getClassifications() != null) {
            showClassification();
            showRocCurve();
            showConfusionMatrix();
        }
    }

    private void saveRocImage() {
        if (rocPlot == null) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Nothing to save.");
            return;
        }

        Action action = new SaveComponentImage(rocPlot, "");
        action.actionPerformed(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Save"));
    }

    private Component getDisplayPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        getTabbedPane().add("Graph", getGraphPanel());
        getTabbedPane().add("Test Data", getDataPanel());
        panel.add(getTabbedPane(), BorderLayout.CENTER);

        return panel;
    }

    private Component getDataPanel() {
        DataSet dataSet = getClassifier().getTestData();
        DataDisplay jTable = new DataDisplay(dataSet);
        return new JScrollPane(jTable);
    }

    private Component getGraphPanel() {
        Graph graph = getClassifier().getBayesIm().getDag();
        workbench = new GraphWorkbench(graph);
        return new JScrollPane(workbench);
    }

    private Component getToolbar() {
        JButton classifyButton = new JButton("Classify");

        classifyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        doClassify();
                        showClassification();
                        showRocCurve();
                        showConfusionMatrix();
                    }
                };
            }
        });

        List<Node> nodes = getClassifier().getBayesImVars();
        Node[] variables = nodes.toArray(new Node[0]);
        this.variableDropdown = new JComboBox(variables);
        getVariableDropdown().setBackground(Color.WHITE);
        getVariableDropdown().setMaximumSize(new Dimension(200, 50));

        DiscreteVariable variable = (DiscreteVariable) getVariableDropdown()
                .getSelectedItem();
        this.categoryDropdown =
                new JComboBox(variable.getCategories().toArray(new String[0]));
        getCategoryDropdown().setBackground(Color.WHITE);
        getCategoryDropdown().setMaximumSize(new Dimension(200, 50));

        this.variableDropdown.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                JComboBox comboBox = (JComboBox) e.getSource();
                Object selectedItem = comboBox.getSelectedItem();
                DiscreteVariable variable = (DiscreteVariable) selectedItem;
                List<String> categories = variable.getCategories();
                DefaultComboBoxModel newModel = new DefaultComboBoxModel(
                        categories.toArray(new String[0]));
                getCategoryDropdown().setModel(newModel);

//                if (categories.size() == 2) {
//                    getBinaryCutoffField().setEnabled(true);
//                    getBinaryCutoffField().setEditable(true);
//                }
//                else {
//                    getBinaryCutoffField().setEnabled(false);
//                    getBinaryCutoffField().setEditable(false);
//                }
            }
        });

        this.categoryDropdown.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                showRocCurve();
            }
        });

//        this.binaryCutoffField = new DoubleTextField(getBinaryCutoff(), 5,
//                NumberFormatUtil.getInstance().getNumberFormat());
//        this.binaryCutoffField.setFilter(new DoubleTextField.Filter() {
//            public double filter(double value, double oldValue) {
//                if (value >= 0.0 && value <= 1.0) {
//                    setBinaryCutoff(value);
//                    return value;
//                }
//
//                return oldValue;
//            }
//        });

//        DiscreteVariable selectedVar =
//                (DiscreteVariable) this.variableDropdown.getSelectedItem();
//        List<String> categories = selectedVar.getCategories();
//
//        if (categories.size() == 2) {
//            getBinaryCutoffField().setEnabled(true);
//            getBinaryCutoffField().setEditable(true);
//        }
//        else {
//            getBinaryCutoffField().setEnabled(false);
//            getBinaryCutoffField().setEditable(false);
//        }


        Box toolbar = Box.createVerticalBox();

        Box row1 = Box.createHorizontalBox();
        row1.add(Box.createHorizontalStrut(5));
        row1.add(new JLabel("Target = "));
        row1.add(getVariableDropdown());
        row1.add(Box.createHorizontalStrut(5));
        row1.add(new JLabel("Category for ROC ="));
        row1.add(getCategoryDropdown());
        row1.add(Box.createHorizontalStrut(10));
        row1.add(classifyButton);
        row1.add(Box.createHorizontalGlue());
        toolbar.add(row1);
        toolbar.add(Box.createVerticalStrut(5));

//        Box row2 = Box.createHorizontalBox();
//        row2.add(Box.createHorizontalStrut(5));
//        row2.add(new JLabel("(Cutoff for binary target = "));
//        row2.add(getBinaryCutoffField());
//        row2.add(new JLabel(" )"));
//        row2.add(Box.createHorizontalGlue());
//        toolbar.add(row2);

        toolbar.setBorder(new EmptyBorder(2, 2, 2, 2));

        return toolbar;
    }

    private void doClassify() {
        DiscreteVariable variable = (DiscreteVariable) getVariableDropdown()
                .getSelectedItem();
        String varName = variable.getName();

        String category = (String) getCategoryDropdown().getSelectedItem();
        int catIndex = variable.getIndex(category);

        getClassifier().setTarget(varName, catIndex);
        getClassifier().classify();
    }

    private void showClassification() {
        int tabIndex = -1;

        for (int i = 0; i < getTabbedPane().getTabCount(); i++) {
            if ("Classification".equals(getTabbedPane().getTitleAt(i))) {
                getTabbedPane().remove(i);
                tabIndex = i;
            }
        }

        // Put the class information into a DataSet.
        int[] classifications = getClassifier().getClassifications();
        double[][] marginals = getClassifier().getMarginals();

        int maxCategory = 0;

        for (int classification : classifications) {
            if (classification > maxCategory) {
                maxCategory = classification;
            }
        }

        List<Node> variables = new LinkedList<Node>();

        DiscreteVariable targetVariable = classifier.getTargetVariable();
        DiscreteVariable classVar =
                new DiscreteVariable(targetVariable.getName(), maxCategory + 1);

        variables.add(classVar);

        for (int i = 0; i < marginals.length; i++) {
            String name = "P(" + targetVariable + "=" + i + ")";
            ContinuousVariable scoreVar = new ContinuousVariable(name);
            variables.add(scoreVar);
        }

        classVar.setName("Result");

        DataSet dataSet =
                new ColtDataSet(classifications.length, variables);

        for (int i = 0; i < classifications.length; i++) {
            dataSet.setInt(i, 0, classifications[i]);

            for (int j = 0; j < marginals.length; j++) {
                dataSet.setDouble(i, j + 1, marginals[j][i]);
            }
        }

        DataDisplay jTable = new DataDisplay(dataSet);
        JScrollPane scroll = new JScrollPane(jTable);

        if (tabIndex == -1) {
            getTabbedPane().add("Classification", scroll);
        }
        else {
            getTabbedPane().add(scroll, tabIndex);
            getTabbedPane().setTitleAt(tabIndex, "Classification");
        }
    }

    private void showRocCurve() {
        int tabIndex = -1;

        for (int i = 0; i < getTabbedPane().getTabCount(); i++) {
            if ("ROC Plot".equals(getTabbedPane().getTitleAt(i))) {
                getTabbedPane().remove(i);
                tabIndex = i;
                this.rocPlot = null;
                this.saveRoc.setEnabled(false);
            }
        }

        double[][] marginals = getClassifier().getMarginals();
        int ncases = getClassifier().getNumCases();

        boolean[] inCategory = new boolean[ncases];

        DataSet testData = getClassifier().getTestData();
        DiscreteVariable targetVariable = classifier.getTargetVariable();
        String targetName = targetVariable.getName();
        Node variable2 = testData.getVariable(targetName);
        int varIndex = testData.getVariables().indexOf(variable2);

        // If the target is not in the data set, don't compute a ROC plot.
        if (varIndex == -1) {
            return;
        }

        String category = (String) getCategoryDropdown().getSelectedItem();
        DiscreteVariable variable = (DiscreteVariable) variable2;
        int catIndex = variable.getIndex(category);

        for (int i = 0; i < inCategory.length; i++) {
            inCategory[i] = (testData.getInt(i, varIndex) == catIndex);
        }

        double[] scores = marginals[catIndex];

        RocCalculator rocc =
                new RocCalculator(scores, inCategory, RocCalculator.ASCENDING);
        double[][] points = rocc.getScaledRocPlot();
        double area = rocc.getAuc();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String info = "AUC = " + nf.format(area);

        String title = "ROC Plot, " + classifier.getTargetVariable() + " = " +
                category;

        RocPlot plot = new RocPlot(points, title, info);
        this.rocPlot = plot;
        this.saveRoc.setEnabled(true);

        if (tabIndex == -1) {
            getTabbedPane().add("ROC Plot", plot);
        }
        else {
            getTabbedPane().add(plot, tabIndex);
            getTabbedPane().setTitleAt(tabIndex, "ROC Plot");
        }
    }

    private void showConfusionMatrix() {
        int tabIndex = -1;

        for (int i = 0; i < getTabbedPane().getTabCount(); i++) {
            if ("Confusion Matrix".equals(getTabbedPane().getTitleAt(i))) {
                getTabbedPane().remove(i);
                tabIndex = i;
            }
        }

        StringBuilder buf = new StringBuilder();

        int[][] crossTabs = getClassifier().crossTabulation();

        // Crosstabs will be null if the target is not in the test data. In
        // this case, don't put the confusion matrix back in.
        if (crossTabs == null) {
            return;
        }

        DiscreteVariable targetVariable = getClassifier().getTargetVariable();
        int nvalues = targetVariable.getNumCategories();

        int ncases = getClassifier().getNumCases();
        int ntot = getClassifier().getTotalUsableCases();

        //System.out.println("Number correct = " + numCorrect);
        //        buf.append("<html><pre>");
        buf.append("Total number of usable cases = ");
        buf.append(ntot);
        buf.append(" out of ");
        buf.append(ncases);
        buf.append("\n\nTarget Variable ");
        buf.append(targetVariable);
        buf.append("\n\t\tEstimated\t");
        buf.append("\nObserved\t");

        for (int i = 0; i < nvalues - 1; i++) {
            buf.append(targetVariable.getCategory(i));
            buf.append("\t");
        }

        buf.append(targetVariable.getCategory(nvalues - 1));

        for (int i = 0; i < nvalues; i++) {
            buf.append("\n");
            buf.append(targetVariable.getCategory(i));
            buf.append("\t");
            for (int j = 0; j < nvalues - 1; j++) {
                buf.append(crossTabs[i][j]);
                buf.append("\t");
            }
            buf.append(crossTabs[i][nvalues - 1]);
        }

        buf.append("\n\nPercentage correctly classified:  ");
        buf.append(getClassifier().getPercentCorrect());
        //        buf.append("</pre></html>");

        JTextArea label = new JTextArea(buf.toString());
        //        label.setFocusable(false);
        label.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(Color.WHITE);
        Box b1 = Box.createVerticalBox();
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalStrut(5));
        b2.add(label);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalGlue());
        b1.add(Box.createVerticalGlue());
        panel.add(b1, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(panel);

        if (tabIndex == -1) {
            getTabbedPane().add("Confusion Matrix", scroll);
        }
        else {
            getTabbedPane().add(scroll, tabIndex);
            getTabbedPane().setTitleAt(tabIndex, "Confusion Matrix");
        }
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesUpdaterClassifierEditor(BayesUpdaterClassifierWrapper wrapper) {
        this(wrapper.getClassifier());
    }

    private BayesUpdaterClassifier getClassifier() {
        return classifier;
    }

    private JComboBox getVariableDropdown() {
        return variableDropdown;
    }

    private JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    private JComboBox getCategoryDropdown() {
        return categoryDropdown;
    }

//    private double getBinaryCutoff() {
//        return binaryCutoff;
//    }
//
//    private void setBinaryCutoff(double binaryCutoff) {
//        this.binaryCutoff = binaryCutoff;
//        getClassifier().setBinaryCutoff(binaryCutoff);
//    }
//
//    private DoubleTextField getBinaryCutoffField() {
//        return binaryCutoffField;
//    }
}





