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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.model.RegressionParams;
import edu.cmu.tetradapp.model.RegressionRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Allows the user to execute a multiple linear regression in the GUI. Contains
 * a panel that lets the user specify a target variable and a list of continuous
 * regressors, plus a tabbed pane that includes (a) a display to show the result
 * of the regression and (b) a graph workbench to show the graph of the target
 * with significant regressors from the regression as parents.
 *
 * @author Tyler Gibosn
 * @author Aaron Powers
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM
 *         Bayes estimator
 */
public class RegressionEditor extends JPanel {

    /**
     * The workbench used to display the graph of significant regression into
     * the target.
     */
    private GraphWorkbench workbench;

    /**
     * A large text area into which the (String) output of the regression result
     * is dumped. (This is what needs to change.)
     */
    private JTextArea reportText = new JTextArea();

    /**
     * Presents the same information in reportText as a text preamble with a
     * table of coefficients etc.
     */
    private JComponent textWithTable = TextWithTable.emptyCompoenent();

    /**
     * The gadget that does the regression.
     */
    private RegressionRunner runner;

    /**
     * Constructs a regression editor. A regression runner is required, since
     * that's what does the actual regression.
     *
     * @throws NullPointerException if <code>regressionRunner</code> is null.
     */
    public RegressionEditor(RegressionRunner regressionRunner) {
        if (regressionRunner == null) {
            throw new NullPointerException("The regression runner is required.");
        }

        this.runner = regressionRunner;
        Graph outGraph = new EdgeListGraph();

        final JButton executeButton = new JButton("Execute");
        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runRegression();
                TetradLogger.getInstance().log("result", reportText.getText());                
            }
        });

        workbench = new GraphWorkbench(outGraph);

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        reportText = new JTextArea();
        reportText.setFont(new Font("Monospaced", Font.PLAIN, 12));
        reportText.setTabSize(10);

        if (runner != null && runner.getResult() != null) {
            reportText.setText(runner.getResult().toString());
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(600, 400));
        tabbedPane.add("Model", new JScrollPane(reportText));
//        tabbedPane.add("Tabularized Model", new JScrollPane(textWithTable));
        tabbedPane.add("Output Graph", workbenchScroll);

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        RegressionParamsEditorPanel editorPanel = new RegressionParamsEditorPanel(
                (RegressionParams) runner.getParams(),
                this.runner.getDataModel());

        editorPanel.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("significanceChanged".equals(propertyName)) {
                    runRegression();
                }
            }
        });

        b1.add(editorPanel);
        b1.add(tabbedPane);
        b.add(b1);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        buttonPanel.add(executeButton);
        b.add(buttonPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);

        setName("Regression Result:");
    }

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    //========================= Private Methods ========================//

    /**
     * Runs the regression, resetting the text output and graph output.
     */
    private void runRegression() {
        runner.execute();
        Graph graph = runner.getOutGraph();
        GraphUtils.circleLayout(graph, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(graph);
        workbench.setGraph(graph);
        RegressionResult report = runner.getResult();
        reportText.setText(report.toString());
        textWithTable.removeAll();
        textWithTable.setLayout(new BorderLayout());
        textWithTable.add(TextWithTable.component(report.getPreamble(),
                report.getResultsTable()));
        textWithTable.revalidate();
        textWithTable.repaint();

    }

    /**
     * Puts the output of getReport() into a component with preamble in a
     * textarea and the rest in a JTable backed by the TextTable. The
     * <code>TextTable.emptyComponent</code> is just a white JPanel, for initial
     * use. After that, call <code>TextTable.component</code>.
     *
     * @author Joseph Ramsey
     */
    private static class TextWithTable {

        private TextWithTable() {
            // Hidden.
        }

        public static JComponent emptyCompoenent() {
            JPanel jPanel = new JPanel();
            jPanel.setBackground(Color.WHITE);
            return jPanel;
        }

        public static JComponent component(String preamble, TextTable textTable) {
            TextWithTable textWithTable = new TextWithTable();

            JPanel panel = new JPanel();
            panel.setBackground(Color.WHITE);

            Box b = Box.createVerticalBox();

            Box b1 = Box.createHorizontalBox();
            b1.add(new JTextArea(preamble));
            b.add(b1);

            Box b2 = Box.createHorizontalBox();
            JScrollPane pane = new JScrollPane(textWithTable.getJTableFor(textTable));
            b2.add(pane);
            b.add(b2);

            panel.setLayout(new BorderLayout());
            panel.add(b, BorderLayout.CENTER);

            return panel;
        }

        private JTable getJTableFor(final TextTable textTable) {

            TableModel model = new AbstractTableModel() {

                public int getRowCount() {
                    return textTable.getNumRows();
                }

                public int getColumnCount() {
                    return textTable.getNumColumns();
                }

                public Object getValueAt(int rowIndex, int columnIndex) {
                    return textTable.getTokenAt(rowIndex, columnIndex);
                }

                public String getColumnName(int column) {
                    return null;
                }
            };

            JTable table = new JTable(model);

            for (int j = 0; j < 6; j++) {
                TableColumn column = table.getColumnModel().getColumn(j);

                column.setCellRenderer(new DefaultTableCellRenderer() {

                    // implements javax.swing.table.TableCellRenderer
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        Component renderer = super.getTableCellRendererComponent(table,
                                value, isSelected, hasFocus, row, column);
                        setText((String) value);
                        setHorizontalAlignment(JLabel.RIGHT);
                        return renderer;
                    }
                });
            }

            return table;
        }
    }
}





