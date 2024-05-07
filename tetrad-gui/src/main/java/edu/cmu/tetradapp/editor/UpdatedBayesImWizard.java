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
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.UpdaterWrapper;
import edu.cmu.tetradapp.util.SortingComboBox;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;

/**
 * Allows the user to choose a variable in a Bayes net and edit the parameters associated with that variable. Parameters
 * are of the form P(Node=value1|Parent1=value2, Parent2=value2,...); values for these parameters are probabilities
 * ranging from 0.0 to 1.0. For a given combination of parent values for selectedNode N, the probabilities for the
 * values of N conditional on that combination of parent values must sum to 1.0
 *
 * @author josephramsey
 */
public final class UpdatedBayesImWizard extends JPanel {

    /**
     * The evidence.
     */
    private final Evidence evidence;

    /**
     * The workbench.
     */
    private final GraphWorkbench workbench;

    /**
     * The updater wrapper.
     */
    private final UpdaterWrapper updaterWrapper;

    /**
     * The JComboBox containing the names of the variables in the getModel.
     */
    private final JComboBox varNamesComboBox;

    /**
     * The JComboBox containing the names of the variables in the getModel.
     */
    private final JComboBox varNamesComboBox2;

    /**
     * The JPanel containing the marginal probabilities for the getModel selectedNode.
     */
    private final JPanel marginalsPanel;

    /**
     * Last node selected.
     */
    private Node selectedNode;

    /**
     * The table for editing the getModel parameters.
     */
    private UpdaterEditingTable editingTable;

    /**
     * The JPanel containing the table for editing the getModel parameters.
     */
    private JPanel tablePanel;

    /**
     * Constructs a new getModel wizard from a given updater wrapper.
     *
     * @param updaterWrapper the updater wrapper
     * @param workbench      the workbench
     * @param tab            the tab
     * @param selectedNode   the selected node
     */
    public UpdatedBayesImWizard(UpdaterWrapper updaterWrapper,
                                GraphWorkbench workbench, int tab, Node selectedNode) {
        if (updaterWrapper == null) {
            throw new NullPointerException();
        }

        this.updaterWrapper = updaterWrapper;
        this.selectedNode = selectedNode;

        this.evidence = updaterWrapper.getBayesUpdater().getEvidence();
        this.workbench = workbench;
        this.workbench.setAllowDoubleClickActions(false);

        setLayout(new BorderLayout());

        // Set up components.
        this.varNamesComboBox = makeVarNamesDropdown();
        this.varNamesComboBox2 = makeVarNamesDropdown();

        Node modelNode = (Node) (this.varNamesComboBox.getSelectedItem());
        workbench.deselectAll();
        workbench.selectNode(modelNode);
        selectedNode = (Node) (this.varNamesComboBox.getSelectedItem());

        this.marginalsPanel = new JPanel();
        this.marginalsPanel.setLayout(new BorderLayout());
        JComponent marginalDisplay = createMarginalDisplay(selectedNode);

        this.marginalsPanel.add(marginalDisplay,
                BorderLayout.CENTER);

        JTabbedPane probsPane = new JTabbedPane(SwingConstants.TOP);

        setupMarginalsDisplay(probsPane);

        if (updaterWrapper.getBayesUpdater().getUpdatedBayesIm() != null) {
            setupConditionalProbabilitiesDisplay(selectedNode, updaterWrapper,
                    probsPane);
        }

        tab = tab < probsPane.getTabCount() ? tab : 0;
        probsPane.setSelectedIndex(tab);

        add(new JScrollPane(probsPane), BorderLayout.CENTER);

        // Add listeners.
        this.varNamesComboBox.addActionListener(e -> {
            Node node = (Node) (UpdatedBayesImWizard.this.varNamesComboBox.getSelectedItem());
            setCurrentNode(node);
        });

        this.varNamesComboBox2.addActionListener(e -> {
            Node node = (Node) (UpdatedBayesImWizard.this.varNamesComboBox2.getSelectedItem());
            setCurrentNode(node);
        });

        workbench.addPropertyChangeListener(e -> {
            if (e.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (e.getNewValue());

                if (selection.size() == 1) {
                    Node node = (Node) (selection.get(0));
                    UpdatedBayesImWizard.this.varNamesComboBox.setSelectedItem(node);

                    DisplayNode graphNode = getWorkbench().getSelectedNode();

                    if (graphNode == null) {
                        return;
                    }

                    Node tetradNode = graphNode.getModelNode();
                    updaterWrapper.getParams().set("variable", updaterWrapper.getBayesUpdater().getBayesIm().getBayesPm().getVariable(tetradNode));
                }
            }
        });
    }

    private void setupMarginalsDisplay(JTabbedPane probsPane) {
        probsPane.add("Marginal Probabilities", this.marginalsPanel);
        probsPane.addChangeListener(e -> {
            JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
            int tab = tabbedPane.getSelectedIndex();
            firePropertyChange("updatedBayesImWizardTab", null, tab);
        });
    }

    private void setupConditionalProbabilitiesDisplay(Node selectedNode,
                                                      UpdaterWrapper updaterWrapper, JTabbedPane probsPane) {
        UpdaterEditingTableModel editingTableModel =
                new UpdaterEditingTableModel(selectedNode,
                        updaterWrapper.getBayesUpdater().getUpdatedBayesIm(), this);
        this.editingTable = new UpdaterEditingTable(editingTableModel);
        JScrollPane scroll = new JScrollPane(this.editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));

        this.tablePanel = new JPanel();
        this.tablePanel.setLayout(new BorderLayout());
        this.tablePanel.add(scroll, BorderLayout.CENTER);
        this.editingTable.grabFocus();

        probsPane.add("Conditional Probabilities", createConditionalDisplay());
    }

    private JComboBox makeVarNamesDropdown() {
        JComboBox varNamesComboBox = new SortingComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        varNamesComboBox.setBackground(Color.white);

        Graph graph = this.updaterWrapper.getBayesUpdater().getManipulatedGraph();

        for (Object o : graph.getNodes()) {
            varNamesComboBox.addItem(o);
        }

        if (this.selectedNode != null) {
            varNamesComboBox.setSelectedItem(this.selectedNode);
        } else {
            varNamesComboBox.setSelectedIndex(0);
            this.selectedNode = (Node) varNamesComboBox.getSelectedItem();
        }

        return varNamesComboBox;
    }

    private JComponent createConditionalDisplay() {
        Box conditionalBox = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Probabilities for values of "));
        b1.add(this.varNamesComboBox);
        //b1.add(new JLabel(" conditional on values"));
        b1.add(Box.createHorizontalGlue());
        conditionalBox.add(b1);

        Box b0 = Box.createHorizontalBox();


        b0.add(new JLabel(
                "conditional on the following additional evidence:"));
        b0.add(Box.createHorizontalGlue());

        conditionalBox.add(b0);
        conditionalBox.add(Box.createVerticalStrut(10));

        addListOfEvidence(conditionalBox);

        conditionalBox.add(Box.createVerticalStrut(20));

        Box b2 = Box.createHorizontalBox();
        b2.add(this.tablePanel);
        conditionalBox.add(b2);

        return conditionalBox;
    }

    private void addListOfEvidence(Box verticalBox) {
        boolean foundACondition = false;

        for (int i = 0; i < this.evidence.getNumNodes(); i++) {
            if (this.evidence.hasNoEvidence(i)) {
                continue;
            }

            foundACondition = true;

            Node node = this.evidence.getNode(i);
            Box c = Box.createHorizontalBox();
            c.add(Box.createRigidArea(new Dimension(30, 1)));
            StringBuilder buf = new StringBuilder();

            buf.append("<html>").append(node.getName()).append(" = ");
            boolean listedOneAlready = false;

            for (int j = 0; j < this.evidence.getNumCategories(i); j++) {
                if (this.evidence.getProposition().isAllowed(i, j)) {
                    if (listedOneAlready) {
                        buf.append(" <i>OR</i>  ");
                    }

                    BayesIm manipulatedBayesIm =
                            this.updaterWrapper.getBayesUpdater().getManipulatedBayesIm();
                    String valueName = manipulatedBayesIm.getBayesPm()
                            .getCategory(node, j);
                    buf.append(valueName);
                    listedOneAlready = true;
                }
            }

            buf.append("</html>");

            c.add(new JLabel(buf.toString()));
            c.add(Box.createHorizontalGlue());
            verticalBox.add(c);
        }

        if (!foundACondition) {
            Box e = Box.createHorizontalBox();
            e.add(Box.createRigidArea(new Dimension(30, 1)));
            e.add(new JLabel("--No Evidence--"));
            e.add(Box.createHorizontalGlue());
            verticalBox.add(e);
        }
    }

    private JComponent createMarginalDisplay(Node node) throws RuntimeException {
        if (node == null) {
            throw new NullPointerException();
        }

        Box marginalBox = Box.createVerticalBox();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Marginal probabilities for variable "));
        b1.add(this.varNamesComboBox2);
        b1.add(new JLabel(", updated"));
        b1.add(Box.createHorizontalGlue());
        marginalBox.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("to reflect the following evidence (P = prior, U = updated):"));
        b2.add(Box.createHorizontalGlue());
        marginalBox.add(b2);

        marginalBox.add(Box.createRigidArea(new Dimension(1, 10)));
        addListOfEvidence(marginalBox);
        marginalBox.add(Box.createRigidArea(new Dimension(1, 20)));

        Node node1 = this.updaterWrapper.getBayesUpdater().getBayesIm().getNode(node.getName());
        int nodeIndex = this.updaterWrapper.getBayesUpdater().getBayesIm().getNodeIndex(node1);

        double[] priorMarginals = this.updaterWrapper.getBayesUpdater().calculatePriorMarginals(nodeIndex);
        double[] updatedMarginals = this.updaterWrapper.getBayesUpdater().calculateUpdatedMarginals(nodeIndex);

        Font font = getFont();
        FontMetrics fontMetrics = getFontMetrics(font);

        Font smallFont = new Font("Dialog", Font.BOLD, 10);
        int maxWidth = 0;

        for (int i = 0;
             i < this.updaterWrapper.getBayesUpdater().getBayesIm().getNumColumns(nodeIndex); i++) {
            String value =
                    this.updaterWrapper.getBayesUpdater().getBayesIm().getBayesPm().getCategory(node, i);
            String label = node + " = " + value;
            int width = fontMetrics.stringWidth(label);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }

        for (int i = 0;
             i < this.updaterWrapper.getBayesUpdater().getBayesIm().getNumColumns(nodeIndex); i++) {
            String value =
                    this.updaterWrapper.getBayesUpdater().getBayesIm().getBayesPm().getCategory(node, i);
            Box c = Box.createHorizontalBox();
            c.add(Box.createRigidArea(new Dimension(10, 1)));

            String label = node + " = " + value;
            int width = fontMetrics.stringWidth(label);

            c.add(Box.createRigidArea(new Dimension(maxWidth - width, 0)));
            c.add(new JLabel(label));

            int priorWidth = (int) (150.0 * priorMarginals[i]);
            int updatedWidth = (int) (150.0 * updatedMarginals[i]);

            JPanel priorBar;

            if (Double.isNaN(priorMarginals[i])) {
                priorBar = makeBar(150, 3, Color.LIGHT_GRAY);
            } else {
                priorBar = makeBar(priorWidth, 6, Color.BLUE.brighter());
            }

            JPanel updatedBar;

            if (Double.isNaN(updatedMarginals[i])) {
                updatedBar = makeBar(150, 3, Color.LIGHT_GRAY);
            } else {
                updatedBar = makeBar(updatedWidth, 6, Color.RED);
            }

            c.add(Box.createRigidArea(new Dimension(10, 1)));

            Box d = Box.createVerticalBox();

            Box e1 = Box.createHorizontalBox();
            e1.add(priorBar);
            e1.add(Box.createHorizontalGlue());

            Box e2 = Box.createHorizontalBox();
            e2.add(updatedBar);
            e2.add(Box.createHorizontalGlue());

            d.add(e1);
            d.add(Box.createVerticalStrut(2));
            d.add(e2);

            c.add(d);
            c.add(Box.createHorizontalGlue());

            Box f = Box.createVerticalBox();
            Box g1 = Box.createHorizontalBox();
            Box g2 = Box.createHorizontalBox();

            String priorText = Double.isNaN(priorMarginals[i])
                    ? "(P) Undefined" : "(P) " + nf.format(priorMarginals[i]);

            JLabel priorValueLabel = new JLabel(priorText);


            String marginalText = Double.isNaN(updatedMarginals[i])
                    ? "(U) Undefined" : "(U) " + nf.format(updatedMarginals[i]);

            JLabel marginalValueLabel = new JLabel(marginalText);
            priorValueLabel.setFont(smallFont);

            g1.add(Box.createHorizontalGlue());
            g1.add(priorValueLabel);

            g2.add(Box.createHorizontalGlue());
            g2.add(marginalValueLabel);

            f.add(g1);
            f.add(g2);

            c.add(f);
            marginalBox.add(c);
            marginalBox.add(Box.createRigidArea(new Dimension(1, 5)));
        }

        marginalBox.add(Box.createGlue());
        return marginalBox;
    }

    private JPanel makeBar(int width, int height, Color color) {
        JPanel bar = new JPanel() {
            public Dimension getPreferredSize() {
                return new Dimension(width, height);
            }

            public Dimension getMaximumSize() {
                return new Dimension(width, height);
            }
        };

        bar.setBackground(color);
        return bar;
    }

    /**
     * Sets the getModel display to reflect the stored values of the getModel selectedNode.
     */
    private void setCurrentNode(Node node) {
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                setCurrentNodeSub(node);
            }
        }

        new MyWatchedProcess();
    }

    private void setCurrentNodeSub(Node node) {
        if (node == this.selectedNode) {
            return;
        }

        this.selectedNode = node;

        getWorkbench().deselectAll();
        getWorkbench().selectNode(this.selectedNode);

        if (this.varNamesComboBox.getSelectedItem() != node) {
            this.varNamesComboBox.setSelectedItem(node);
        }

        if (this.varNamesComboBox2.getSelectedItem() != node) {
            this.varNamesComboBox2.setSelectedItem(node);
        }

        if (this.updaterWrapper.getBayesUpdater().getUpdatedBayesIm() != null) {
            TableCellEditor cellEditor = this.editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

            UpdaterEditingTableModel editingTableModel =
                    new UpdaterEditingTableModel(node,
                            this.updaterWrapper.getBayesUpdater().getUpdatedBayesIm(), this);
            this.editingTable = new UpdaterEditingTable(editingTableModel);

            JScrollPane scroll = new JScrollPane(this.editingTable);
            scroll.setPreferredSize(new Dimension(0, 150));

            this.tablePanel.removeAll();
            this.tablePanel.add(scroll, BorderLayout.CENTER);
            this.tablePanel.revalidate();
            this.tablePanel.repaint();
        }

        this.marginalsPanel.removeAll();
        this.marginalsPanel.add(createMarginalDisplay(node), BorderLayout.CENTER);
        this.marginalsPanel.revalidate();
        this.marginalsPanel.repaint();
    }

    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    /**
     * @return the getModel selectedNode.
     */
    public Node getSelectedNode() {
        return this.selectedNode;
    }
}

/**
 * This is the JTable which displays the getModel parameter set.
 *
 * @author josephramsey
 * @see BayesImEditorWizard
 * @see UpdaterEditingTableModel
 */
final class UpdaterEditingTable extends JTable {
    private int focusRow;
    private int focusCol;

    /**
     * Constructs a new editing table from a given editing table model.
     *
     * @param model the table model containing the parameters to be edited.
     */
    public UpdaterEditingTable(UpdaterEditingTableModel model) {
        super(model);

        NumberCellEditor editor = new NumberCellEditor();
        editor.setEmptyString("*");
        setDefaultEditor(Number.class, editor);
        NumberCellRenderer renderer = new NumberCellRenderer();
        renderer.setEmptyString("*");
        setDefaultRenderer(Number.class, renderer);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);

        ListSelectionModel rowSelectionModel = getSelectionModel();

        rowSelectionModel.addListSelectionListener(e -> {
            ListSelectionModel m = (ListSelectionModel) (e.getSource());
            setFocusRow(m.getAnchorSelectionIndex());
        });

        ListSelectionModel columnSelectionModel = getColumnModel()
                .getSelectionModel();

        columnSelectionModel.addListSelectionListener(
                e -> {
                    ListSelectionModel m =
                            (ListSelectionModel) (e.getSource());
                    setFocusColumn(m.getAnchorSelectionIndex());
                });

        setFocusRow(0);
        setFocusColumn(0);
    }

    public void setDefaultRenderer(Class columnClass,
                                   TableCellRenderer renderer) {
        super.setDefaultRenderer(columnClass, renderer);

        if (getModel() instanceof UpdaterEditingTableModel model) {
            FontMetrics fontMetrics = getFontMetrics(getFont());

            for (int i = 0; i < model.getColumnCount(); i++) {
                TableColumn column = getColumnModel().getColumn(i);
                String columnName = model.getColumnName(i);
                int currentWidth = column.getPreferredWidth();

                if (columnName != null) {
                    int minimumWidth = fontMetrics.stringWidth(columnName) + 8;

                    if (minimumWidth > currentWidth) {
                        column.setPreferredWidth(minimumWidth);
                    }
                }
            }
        }
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
    private void setFocusRow(int row) {
        UpdaterEditingTableModel editingTableModel =
                (UpdaterEditingTableModel) getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        this.focusRow = row;

        if (this.focusCol < getRowCount()) {
            setRowSelectionInterval(this.focusRow, this.focusRow);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    /**
     * Sets the focus column to the anchor column currently being selected.
     */
    private void setFocusColumn(int col) {
        UpdaterEditingTableModel editingTableModel =
                (UpdaterEditingTableModel) getModel();
        int failedCol = editingTableModel.getFailedCol();

        if (failedCol != -1) {
            col = failedCol;
            editingTableModel.resetFailedCol();
        }

        if (col < getNumParents()) {
            col = getNumParents();
        }

        this.focusCol = FastMath.max(col, getNumParents());

        if (this.focusCol >= getNumParents() &&
            this.focusCol < getColumnCount()) {
            setColumnSelectionInterval(this.focusCol, this.focusCol);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    private int getNumParents() {
        UpdaterEditingTableModel editingTableModel =
                (UpdaterEditingTableModel) getModel();
        BayesIm bayesIm = editingTableModel.getBayesIm();
        int nodeIndex = editingTableModel.getNodeIndex();
        return bayesIm.getNumParents(nodeIndex);
    }
}

/**
 * The abstract table model containing the parameters to be edited for a given node.  Parameters for a given node N with
 * parents P1, P2, ..., are of the form P(N=v0 | P1=v1, P2=v2, ..., Pn = vn).  The first n columns of this table for
 * each row contains a combination of values for (P1, P2, ... Pn), such as (v0, v1, ..., vn).  If there are m values for
 * N, the next m columns contain numbers in the range [0.0, 1.0] representing conditional probabilities that N takes on
 * that corresponding value given this combination of parent values. These conditional probabilities may be edited.  As
 * they are being edited for a given row, the only condition is that they be greater than or equal to 0.0.
 *
 * @author josephramsey
 */
final class UpdaterEditingTableModel extends AbstractTableModel {

    /**
     * The BayesIm being edited.
     */
    private final BayesIm bayesIm;

    /**
     * This table can only display conditional probabilities for one node at at time. This is the node.
     */
    private final int nodeIndex;

    /**
     * The wizard that takes the user through the process of editing the probability tables.
     */
    private final UpdatedBayesImWizard wizard;

    private int failedRow = -1;
    private int failedCol = -1;

    /**
     * Constructs a new editing table model for a given a node in a given bayesIm.
     */
    public UpdaterEditingTableModel(Node node, BayesIm bayesIm,
                                    UpdatedBayesImWizard wizard) {
        if (node == null) {
            throw new NullPointerException("Node must not be null.");
        }

        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }

        if (wizard == null) {
            throw new NullPointerException("Wizard must not be null.");
        }

        this.bayesIm = bayesIm;
        this.nodeIndex = bayesIm.getNodeIndex(node);
        this.wizard = wizard;
    }

    /**
     * @return the name of the given column.
     */
    public String getColumnName(int col) {
        Node node = getBayesIm().getNode(getNodeIndex());

        if (col < getBayesIm().getNumParents(getNodeIndex())) {
            int parent = getBayesIm().getParent(getNodeIndex(), col);
            return getBayesIm().getNode(parent).getName();
        } else {
            int numNodeVals = getBayesIm().getNumColumns(getNodeIndex());
            int valIndex = col - getBayesIm().getNumParents(getNodeIndex());

            if (valIndex < numNodeVals) {
                String value =
                        getBayesIm().getBayesPm().getCategory(node, valIndex);
                return node.getName() + "=" + value;
            }

            return null;
        }
    }

    /**
     * @return the number of rows in the table.
     */
    public int getRowCount() {
        return getBayesIm().getNumRows(getNodeIndex());
    }

    /**
     * @return the total number of columns in the table, which is equal to the number of parents for the node plus the
     * number of values for the node.
     */
    public int getColumnCount() {
        int numParents = getBayesIm().getNumParents(getNodeIndex());
        int numColumns = getBayesIm().getNumColumns(getNodeIndex());
        return numParents + numColumns;
    }

    /**
     * @return the value of the table at the given row and column. The type of value returned depends on the column.  If
     * there are n parent values and m node values, then the first n columns have String values representing the values
     * of the parent nodes for a particular combination (row) and the next m columns have Double values representing
     * conditional probabilities of node values given parent value combinations.
     */
    public Object getValueAt(int tableRow, int tableCol) {
        int[] parentVals =
                getBayesIm().getParentValues(getNodeIndex(), tableRow);

        if (tableCol < parentVals.length) {
            Node columnNode = getBayesIm().getNode(
                    getBayesIm().getParent(getNodeIndex(), tableCol));
            BayesPm bayesPm = getBayesIm().getBayesPm();
            return bayesPm.getCategory(columnNode, parentVals[tableCol]);
        } else {
            int colIndex = tableCol - parentVals.length;

            if (colIndex < getBayesIm().getNumColumns(getNodeIndex())) {
                return getBayesIm().getProbability(getNodeIndex(), tableRow,
                        colIndex);
            }

            return "null";
        }
    }

    /**
     * Determines whether a cell is in the column range to allow for editing.
     */
    public boolean isCellEditable(int row, int col) {
        return !(col < getBayesIm().getNumParents(getNodeIndex()));
    }

    /**
     * @return the class of the column.
     */
    public Class getColumnClass(int col) {
        boolean isParent = col < getBayesIm().getNumParents(getNodeIndex());
        return isParent ? Object.class : Number.class;
    }

    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    public int getNodeIndex() {
        return this.nodeIndex;
    }

    public UpdatedBayesImWizard getWizard() {
        return this.wizard;
    }

    public int getFailedRow() {
        return this.failedRow;
    }

    public int getFailedCol() {
        return this.failedCol;
    }

    public void resetFailedRow() {
        this.failedRow = -1;
    }

    public void resetFailedCol() {
        this.failedCol = -1;
    }
}





