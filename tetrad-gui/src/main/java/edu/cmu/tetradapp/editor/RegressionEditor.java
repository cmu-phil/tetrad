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
 * Bayes estimator
 */
public class RegressionEditor extends JPanel {

    /**
     * The workbench used to display the graph of significant regression into
     * the target.
     */
    private final GraphWorkbench workbench;

    /**
     * A large text area into which the (String) output of the regression result
     * is dumped. (This is what needs to change.)
     */
    private JTextArea reportText = new JTextArea();

    /**
     * Presents the same information in reportText as a text preamble with a
     * table of coefficients etc.
     */
    private final JComponent textWithTable = TextWithTable.emptyCompoenent();

    /**
     * The gadget that does the regression.
     */
    private final RegressionRunner runner;

    /**
     * Constructs a regression editor. A regression runner is required, since
     * that's what does the actual regression.
     *
     * @throws NullPointerException if <code>regressionRunner</code> is null.
     */
    public RegressionEditor(final RegressionRunner regressionRunner) {
        if (regressionRunner == null) {
            throw new NullPointerException("The regression runner is required.");
        }

        this.runner = regressionRunner;
        final Graph outGraph = new EdgeListGraph();

        final JButton executeButton = new JButton("Execute");
        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                runRegression();
                TetradLogger.getInstance().log("result", RegressionEditor.this.reportText.getText());
            }
        });

        this.workbench = new GraphWorkbench(outGraph);

        final JScrollPane workbenchScroll = new JScrollPane(this.workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        this.reportText = new JTextArea();
        this.reportText.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.reportText.setTabSize(10);

        if (this.runner != null && this.runner.getResult() != null) {
            this.reportText.setText(this.runner.getResult().toString());
        }

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(600, 400));
        tabbedPane.add("Model", new JScrollPane(this.reportText));
//        tabbedPane.add("Tabularized Model", new JScrollPane(textWithTable));
        tabbedPane.add("Output Graph", workbenchScroll);

        final Box b = Box.createVerticalBox();
        final Box b1 = Box.createHorizontalBox();
        final RegressionParamsEditorPanel editorPanel = new RegressionParamsEditorPanel(this.runner, this.runner.getParams(),
                this.runner.getDataModel(), false);

        editorPanel.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                final String propertyName = evt.getPropertyName();

                if ("significanceChanged".equals(propertyName)) {
                    runRegression();
                }
            }
        });

        b1.add(editorPanel);
        b1.add(tabbedPane);
        b.add(b1);

        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        buttonPanel.add(executeButton);
        b.add(buttonPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);

        final int numModels = this.runner.getNumModels();

        if (numModels > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < numModels; i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    RegressionEditor.this.runner.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            final Box c = Box.createHorizontalBox();
            c.add(new JLabel("Using model"));
            c.add(comp);
            c.add(new JLabel("from "));
            c.add(new JLabel(this.runner.getModelSourceName()));
            c.add(Box.createHorizontalGlue());

            add(c, BorderLayout.NORTH);
        }

        setName("Regression Result:");
    }

    /**
     * Sets the name of this editor.
     */
    public void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    //========================= Private Methods ========================//

    /**
     * Runs the regression, resetting the text output and graph output.
     */
    private void runRegression() {
        this.runner.execute();
        final Graph graph = this.runner.getOutGraph();
        GraphUtils.circleLayout(graph, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(graph);
        this.workbench.setGraph(graph);
        final RegressionResult report = this.runner.getResult();
        this.reportText.setText(report.toString());
        this.textWithTable.removeAll();
        this.textWithTable.setLayout(new BorderLayout());
        this.textWithTable.add(TextWithTable.component(report.getPreamble(),
                report.getResultsTable()));
        this.textWithTable.revalidate();
        this.textWithTable.repaint();

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
            final JPanel jPanel = new JPanel();
            jPanel.setBackground(Color.WHITE);
            return jPanel;
        }

        public static JComponent component(final String preamble, final TextTable textTable) {
            final TextWithTable textWithTable = new TextWithTable();

            final JPanel panel = new JPanel();
            panel.setBackground(Color.WHITE);

            final Box b = Box.createVerticalBox();

            final Box b1 = Box.createHorizontalBox();
            b1.add(new JTextArea(preamble));
            b.add(b1);

            final Box b2 = Box.createHorizontalBox();
            final JScrollPane pane = new JScrollPane(textWithTable.getJTableFor(textTable));
            b2.add(pane);
            b.add(b2);

            panel.setLayout(new BorderLayout());
            panel.add(b, BorderLayout.CENTER);

            return panel;
        }

        private JTable getJTableFor(final TextTable textTable) {

            final TableModel model = new AbstractTableModel() {

                public int getRowCount() {
                    return textTable.getNumRows();
                }

                public int getColumnCount() {
                    return textTable.getNumColumns();
                }

                public Object getValueAt(final int rowIndex, final int columnIndex) {
                    return textTable.getTokenAt(rowIndex, columnIndex);
                }

                public String getColumnName(final int column) {
                    return null;
                }
            };

            final JTable table = new JTable(model);

            for (int j = 0; j < 6; j++) {
                final TableColumn column = table.getColumnModel().getColumn(j);

                column.setCellRenderer(new DefaultTableCellRenderer() {

                    // implements javax.swing.table.TableCellRenderer
                    public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
                        final Component renderer = super.getTableCellRendererComponent(table,
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





