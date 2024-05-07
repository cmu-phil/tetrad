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
import edu.cmu.tetrad.graph.NodeType;
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
 * Display the updated marginal and joint probabilities Probably do not need the two classes following this main one:
 * UpdaterEditingTableObs and UpdaterEditingTableModelObs. They are for displaying the conditional probability tables
 * after updating an MlBayesIm.
 *
 * @author josephramsey
 */
public class UpdatedBayesImWizardObs extends JPanel {

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
     * The variable names combo box.
     */
    private final JComboBox varNamesComboBox;

    /**
     * The variable names combo box.
     */
    private final JComboBox varNamesComboBox2;

    /**
     * The panel that displays the marginal probabilities.
     */
    private final JPanel marginalsPanel;

    /**
     * Last node selected.
     */
    private Node selectedNode;

    /**
     * The table for editing conditional probabilities.
     */
    private UpdaterEditingTableObs editingTable;

    /**
     * The panel that displays the conditional probabilities.
     */
    private JPanel tablePanel;

    /**
     * <p>Constructor for UpdatedBayesImWizardObs.</p>
     *
     * @param updaterWrapper a {@link edu.cmu.tetradapp.model.UpdaterWrapper} object
     * @param workbench      a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     * @param tab            a int
     * @param selectedNode   a {@link edu.cmu.tetrad.graph.Node} object
     */
    public UpdatedBayesImWizardObs(UpdaterWrapper updaterWrapper,
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
            Node node = (Node) (UpdatedBayesImWizardObs.this.varNamesComboBox.getSelectedItem());
            setCurrentNode(node);
        });

        this.varNamesComboBox2.addActionListener(e -> {
            Node node = (Node) (UpdatedBayesImWizardObs.this.varNamesComboBox2.getSelectedItem());
            setCurrentNode(node);
        });

        workbench.addPropertyChangeListener(e -> {
            if (e.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (e.getNewValue());

                if (selection.size() == 1) {
                    Node node = (Node) (selection.get(0));
                    UpdatedBayesImWizardObs.this.varNamesComboBox.setSelectedItem(node);

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
        UpdaterEditingTableModelObs editingTableModel =
                new UpdaterEditingTableModelObs(selectedNode,
                        updaterWrapper.getBayesUpdater().getUpdatedBayesIm(), this);
        this.editingTable = new UpdaterEditingTableObs(editingTableModel);
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
            // skip latent variables in Identifiability Wrapper
            Node nodeO = (Node) o;
            if (nodeO.getNodeType() == NodeType.MEASURED) {
                varNamesComboBox.addItem(o);
            }
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
        b1.add(new JLabel(" conditional on values"));
        b1.add(Box.createHorizontalGlue());
        conditionalBox.add(b1);

        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel(
                "of its parents, updated to reflect the following evidence:"));
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

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Marginal probabilities for variable "));
        b1.add(this.varNamesComboBox2);
        b1.add(new JLabel(", updated"));
        b1.add(Box.createHorizontalGlue());
        marginalBox.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("to reflect the following evidence:"));
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

            // identifiability returns -1 if the requested prob is unidentifiable
            if ((Double.isNaN(priorMarginals[i])) || (priorMarginals[i] < 0.0)) {
                priorBar = makeBar(150, 3, Color.LIGHT_GRAY);
            } else {
                priorBar = makeBar(priorWidth, 6, Color.BLUE.brighter());
            }

            JPanel updatedBar;

            // identifiability returns -1 if the requested prob is unidentifiable
            if ((Double.isNaN(updatedMarginals[i])) || (updatedMarginals[i] < 0.0)) {
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

            // format and wording of the probability values
            JLabel priorValueLabel = new JLabel(textLabel(priorMarginals[i]));
            JLabel marginalValueLabel = new JLabel(textLabel(updatedMarginals[i]));

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

    // format and wording of the probability value
    private String textLabel(double prob) {
        if (Double.isNaN(prob)) {
            return "Undefined";
        }
        // identifiability returns -1 if the requested prob is unidentifiable
        else if (prob < 0.0) {
            return "Unidentifiable";
        } else {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
            return nf.format(prob);
        }
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

            UpdaterEditingTableModelObs editingTableModel =
                    new UpdaterEditingTableModelObs(node,
                            this.updaterWrapper.getBayesUpdater().getUpdatedBayesIm(), this);
            this.editingTable = new UpdaterEditingTableObs(editingTableModel);

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
     * @return the getModel parameter set.
     */
    public Node getSelectedNode() {
        return this.selectedNode;
    }
}


////////////////////////////////////////////////////////////////////////////


/**
 * This is the JTable which displays the getModel parameter set.
 *
 * @author josephramsey
 * @see BayesImEditorWizard
 * @see UpdaterEditingTableModel
 */
final class UpdaterEditingTableObs extends JTable {
    private int focusRow;
    private int focusCol;

    /**
     * Constructs a new editing table from a given editing table model.
     *
     * @param model the table model containing the parameters to be edited.
     */
    public UpdaterEditingTableObs(UpdaterEditingTableModelObs model) {
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

        if (getModel() instanceof UpdaterEditingTableModelObs model) {
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
        UpdaterEditingTableModelObs editingTableModel =
                (UpdaterEditingTableModelObs) getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        this.focusRow = row;

        if (this.focusRow < getRowCount()) {
            setRowSelectionInterval(this.focusRow, this.focusRow);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    /**
     * Sets the focus column to the anchor column currently being selected.
     */
    private void setFocusColumn(int col) {
        UpdaterEditingTableModelObs editingTableModel =
                (UpdaterEditingTableModelObs) getModel();
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
        UpdaterEditingTableModelObs editingTableModel =
                (UpdaterEditingTableModelObs) getModel();
        BayesIm bayesIm = editingTableModel.getBayesIm();
        int nodeIndex = editingTableModel.getNodeIndex();
        return bayesIm.getNumParents(nodeIndex);
    }
}

/////////////////////////////////////////////////////////////////////////

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
final class UpdaterEditingTableModelObs extends AbstractTableModel {

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
    private final UpdatedBayesImWizardObs wizard;

    private int failedRow = -1;
    private int failedCol = -1;

    /**
     * Constructs a new editing table model for a given a node in a given bayesIm.
     */
    public UpdaterEditingTableModelObs(Node node, BayesIm bayesIm,
                                       UpdatedBayesImWizardObs wizard) {
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

    public UpdatedBayesImWizardObs getWizard() {
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





