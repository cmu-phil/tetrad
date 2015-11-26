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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.model.TabularComparison;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays data objects and allows users to edit these objects as well as load
 * and save them.
 *
 * @author Joseph Ramsey
 */
public final class DataEditor extends JPanel implements KnowledgeEditable,
        PropertyChangeListener {

    /**
     * The data wrapper being displayed.
     */
    private DataWrapper dataWrapper;

    /**
     * A tabbed pane containing displays for all data models and displaying
     * 'dataModel' currently.
     */
    private JTabbedPane tabbedPane = new JTabbedPane();

    //==========================CONSTUCTORS===============================//

    /**
     * Constructs the data editor with an empty list of data displays.
     */
    public DataEditor() {
    }

    public DataEditor(TabularComparison comparison) {
        this(new DataWrapper(comparison.getDataSet()));

    }


    /**
     * Constructs a standalone data editor.
     */
    public DataEditor(DataWrapper dataWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException("Data wrapper must not be null.");
        }

        this.dataWrapper = dataWrapper;
        setLayout(new BorderLayout());
        reset();

        tabbedPane().addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if (SwingUtilities.isRightMouseButton(e)) {
                    Point point = e.getPoint();
                    final int index = tabbedPane().indexAtLocation(point.x, point.y);

                    if (index == -1) {
                        return;
                    }

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem close = new JMenuItem("Close Tab");
                    menu.add(close);

                    menu.show(DataEditor.this, point.x, point.y);

                    close.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            closeTab();
                            DataEditor.this.grabFocus();
                            firePropertyChange("modelChanged", null, null);
                        }
                    });
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    DataModel selectedModel = getSelectedDataModel();
                    getDataWrapper().getDataModelList().setSelectedModel(selectedModel);

                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
    }

    //==========================PUBLIC METHODS=============================//


    /**
     * Replaces the getModel Datamodels with the given one. Note, that by calling this
     * you are removing ALL the getModel data-models, they will be lost forever!
     *
     * @param model - The model, must not be null
     */
    public final void replace(DataModel model) {
        if (model == null) {
            throw new NullPointerException("The given model must not be null");
        }

        tabbedPane.removeAll();
        setPreferredSize(new Dimension(600, 400));
        DataModelList dataModelList = dataWrapper.getDataModelList();
        dataModelList.clear();

        // now rebuild
        if (model instanceof DataModelList) {
            for (DataModel dataModel : (DataModelList) model) {
                dataModelList.add(dataModel);
            }
        } else {
            dataModelList.add(model);
        }

        removeAll();

        if (model instanceof DataModelList) {
            for (int i = 0; i < ((DataModelList) model).size(); i++) {
                DataModel _model = ((DataModelList) model).get(i);
                this.tabbedPane.addTab(tabName(_model, 1), dataDisplay(_model));
            }

            add(this.tabbedPane, BorderLayout.CENTER);
            add(menuBar(), BorderLayout.NORTH);
        } else {
            this.tabbedPane.addTab(tabName(model, 1), dataDisplay(model));
            add(this.tabbedPane, BorderLayout.CENTER);
            add(menuBar(), BorderLayout.NORTH);
            validate();
        }

        dataWrapper.setDataModelList(dataModelList);
    }


    /**
     * Sets this editor to display contents of the given data model wrapper.
     */
    public final void reset() {
        tabbedPane().removeAll();
        setPreferredSize(new Dimension(600, 400));

        DataModelList dataModelList = dataWrapper.getDataModelList();
        DataModel selectedModel = dataWrapper.getSelectedDataModel();

        removeAll();
        removeEmptyModels(dataModelList);

        int selectedIndex = -1;

        for (int i = 0; i < dataModelList.size(); i++) {
            DataModel dataModel = dataModelList.get(i);
            tabbedPane().addTab(tabName(dataModel, i + 1),
                    dataDisplay(dataModel));
            if (selectedModel == dataModel) {
                selectedIndex = i;
            }
        }

        tabbedPane().setSelectedIndex(selectedIndex);

        tabbedPane().addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                DataModel selectedModel = getSelectedDataModel();

                if (selectedModel == null) {
                    return;
                }

                getDataWrapper().getDataModelList().setSelectedModel(
                        selectedModel);
            }
        });

        add(tabbedPane(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
        validate();
    }


    public final void reset(DataModelList extraModels) {
        tabbedPane().removeAll();
        setPreferredSize(new Dimension(600, 400));

        DataModelList dataModelList = dataWrapper.getDataModelList();
        dataModelList.addAll(extraModels);

        removeAll();
        tabbedPane().removeAll();
        removeEmptyModels(dataModelList);

        int tabIndex = 0;

        for (DataModel dataModel : dataModelList) {
            tabbedPane().addTab(tabName(dataModel, ++tabIndex),
                    dataDisplay(dataModel));
        }

        add(tabbedPane(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
        validate();

        firePropertyChange("modelChanged", null, null);
    }

    public final void reset(DataModel dataModel) {
        tabbedPane().removeAll();
        setPreferredSize(new Dimension(600, 400));

        DataModelList dataModelList = dataWrapper.getDataModelList();
        dataModelList.clear();
        dataModelList.add(dataModel);

        removeEmptyModels(dataModelList);
        tabbedPane().removeAll();

        for (int i = 0; i < dataModelList.size(); i++) {
            Object _dataModel = dataModelList.get(i);
            tabbedPane().addTab(tabName(dataModel, i + 1),
                    dataDisplay(_dataModel));
        }

        add(tabbedPane(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
        validate();

        firePropertyChange("modelChanged", null, null);
    }


    /**
     * @return the data sets that's currently in front.
     */
    public DataModel getSelectedDataModel() {
        Component selectedComponent = tabbedPane().getSelectedComponent();
        DataModelContainer scrollPane = (DataModelContainer) selectedComponent;

        if (scrollPane == null) {
            return null;
        }

        return scrollPane.getDataModel();
    }

    public void selectFirstTab() {
//        tabbedPane().setSelectedIndex(tabbedPane().getTabCount() - 1);
        tabbedPane().setSelectedIndex(0);
        DataModel selectedModel = getSelectedDataModel();

        if (selectedModel == null) {
            return;
        }

        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataModelList) {
            DataModelList dataModelList = (DataModelList) dataModel;
            dataModelList.setSelectedModel(selectedModel);

            firePropertyChange("modelChanged", null, null);
        }
    }

    public int getTabCount() {
        return tabbedPane().getTabCount();
    }

    public List<Node> getKnownVariables() {
        return dataWrapper.getKnownVariables();
    }

    public List<String> getVarNames() {
        return dataWrapper.getVarNames();
    }

    public Graph getSourceGraph() {
        return dataWrapper.getSourceGraph();
    }

    /**
     * Retrieves the data wrapper for this editor (read-only).
     */
    public DataWrapper getDataWrapper() {
        return this.dataWrapper;
    }

    public IKnowledge getKnowledge() {
        return dataWrapper.getKnowledge();
    }

    public void setKnowledge(IKnowledge knowledge) {
        dataWrapper.setKnowledge(knowledge);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }

    //=============================PRIVATE METHODS======================//

    private static void removeEmptyModels(DataModelList dataModelList) {
        for (int i = dataModelList.size() - 1; i >= 0; i--) {
            DataModel dataModel = dataModelList.get(i);

            if (dataModel instanceof DataSet &&
                    ((DataSet) dataModel).getNumColumns() == 0) {
                if (dataModelList.size() > 1) {
                    dataModelList.remove(dataModel);
                }
            }
        }
    }

    private JTable getSelectedJTable() {
        Object display = tabbedPane().getSelectedComponent();

        if (display instanceof DataDisplay) {
            return ((DataDisplay) display).getDataDisplayJTable();
        } else if (display instanceof CovMatrixDisplay) {
            return ((CovMatrixDisplay) display).getCovMatrixJTable();
        }

        return null;
    }

    private JTable getJTableAt(int index) {
        Object display = tabbedPane().getComponentAt(index);

        if (display instanceof DataDisplay) {
            return ((DataDisplay) display).getDataDisplayJTable();
        } else if (display instanceof CovMatrixDisplay) {
            return ((CovMatrixDisplay) display).getCovMatrixJTable();
        }

        return null;
    }

    private int getNumJTables() {
        return tabbedPane.getTabCount();
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");
        menuBar.add(file);

        LoadDataAction action = new LoadDataAction(this);
        action.addPropertyChangeListener(this);
        JMenuItem fileItem = new JMenuItem(action);
        file.add(fileItem);
        JMenuItem saveItem = new JMenuItem(new SaveDataAction(this));
        file.add(saveItem);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));

        fileItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        saveItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));

        JMenu editMenu = new JMenu("Edit");

        JMenuItem clearCells = new JMenuItem("Clear Cells");
        final JMenuItem deleteSelectedRowsOrColumns =
                new JMenuItem("Delete Selected Rows or Columns");
        final JMenuItem deleteNamedColumns =
                new JMenuItem("Delete named columns");
        final JMenuItem selectNamedColumns =
                new JMenuItem("Select named columns");
        JMenuItem copyCells = new JMenuItem("Copy Cells");
        JMenuItem cutCells = new JMenuItem("Cut Cells");
        JMenuItem pasteCells = new JMenuItem("Paste Cells");
        JMenuItem setToMissingCells = new JMenuItem("Set Constants Col To Missing");

        clearCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_K, ActionEvent.CTRL_MASK));
        deleteSelectedRowsOrColumns.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.CTRL_MASK));
        copyCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        cutCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        pasteCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        clearCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TabularDataJTable table =
                        (TabularDataJTable) getSelectedJTable();
                table.clearSelected();
            }
        });

        final ActionListener deleteActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTable table = getSelectedJTable();

                if (table instanceof TabularDataJTable) {
                    TabularDataJTable tableTabular = (TabularDataJTable) table;

                    if (!tableTabular.getRowSelectionAllowed() ||
                            !tableTabular.getColumnSelectionAllowed()) {
                        tableTabular.deleteSelected();
                    }

                } else if (table instanceof CovMatrixJTable) {
                    CovMatrixJTable covTable = (CovMatrixJTable) table;
                    covTable.deleteSelected();
                }

            }
        };

        deleteSelectedRowsOrColumns.addActionListener(deleteActionListener);

        final ActionListener removeNamedColumnsActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String variables = JOptionPane.showInputDialog(JOptionUtils.getCenteringFrame(),
                        "Type a space-separated list of variable names.");

                String[] tokens = variables.split(" ");

                for (int i = 0; i < getNumJTables(); i++) {
                    System.out.println(i);

                    JTable jTable = getJTableAt(i);

                    if (jTable instanceof TabularDataJTable) {
                        TabularDataJTable tableTabular =
                                (TabularDataJTable) getJTableAt(i);

                        DataSet dataSet = tableTabular.getDataSet();

                        for (Node node : dataSet.getVariables()) {
                            for (String token : tokens) {
                                if (token.equals(node.getName())) {
                                    dataSet.removeColumn(node);
                                }
                            }
                        }

                        TabularDataTable model = (TabularDataTable) jTable.getModel();
                        model.fireTableDataChanged();


//                        TabularDataTable table = new TabularDataTable(dataSet);
//                        tableTabular.setModel(table);

//                        jTable.getModel().fireTableDataChanged();
                    }
                }
            }
        };

        final ActionListener selectNamedColumnsActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String variables = JOptionPane.showInputDialog(JOptionUtils.getCenteringFrame(),
                        "Type a space-separated list of variable names.");

                String[] tokens = variables.split(" ");

                Set _tokens = new HashSet<String>();

                for (String token : tokens) {
                    _tokens.add(token);
                }

                for (int i = 0; i < getNumJTables(); i++) {
                    System.out.println(i);

                    JTable jTable = getJTableAt(i);

                    if (jTable instanceof TabularDataJTable) {
                        TabularDataJTable tableTabular =
                                (TabularDataJTable) getJTableAt(i);

                        DataSet dataSet = tableTabular.getDataSet();

                        for (Node node : dataSet.getVariables()) {
                            for (String token : tokens) {
                                if (!_tokens.contains(node.getName())) {
                                    dataSet.removeColumn(node);
                                }
                            }
                        }

                        TabularDataTable model = (TabularDataTable) jTable.getModel();
                        model.fireTableDataChanged();


//                        TabularDataTable table = new TabularDataTable(dataSet);
//                        tableTabular.setModel(table);

//                        jTable.getModel().fireTableDataChanged();
                    }
                }
            }
        };

        deleteNamedColumns.addActionListener(removeNamedColumnsActionListener);
        selectNamedColumns.addActionListener(selectNamedColumnsActionListener);

        copyCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTable table = getSelectedJTable();
                Action copyAction = TransferHandler.getCopyAction();
                ActionEvent actionEvent = new ActionEvent(table,
                        ActionEvent.ACTION_PERFORMED, "copy");
                copyAction.actionPerformed(actionEvent);
            }
        });

        cutCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTable table = getSelectedJTable();
                Action cutAction = TransferHandler.getCutAction();
                ActionEvent actionEvent = new ActionEvent(table,
                        ActionEvent.ACTION_PERFORMED, "cut");
                cutAction.actionPerformed(actionEvent);
            }
        });

        pasteCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTable table = getSelectedJTable();
                Action pasteAction = TransferHandler.getPasteAction();
                ActionEvent actionEvent = new ActionEvent(table,
                        ActionEvent.ACTION_PERFORMED, "paste");
                pasteAction.actionPerformed(actionEvent);
            }
        });

        setToMissingCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                for (int i = 0; i < getNumJTables(); i++) {
                    System.out.println(i);

                    JTable jTable = getJTableAt(i);

                    if (jTable instanceof TabularDataJTable) {
                        TabularDataJTable tableTabular =
                                (TabularDataJTable) getJTableAt(i);

                        DataSet dataSet = tableTabular.getDataSet();

                        COLUMN:
                        for (int j = 0; j < dataSet.getNumColumns(); j++) {
                            double first = dataSet.getDouble(0, j);

                            for (int k = 1; k < dataSet.getNumRows(); k++) {
                                if (dataSet.getDouble(k, j) != first) {
                                    continue COLUMN;
                                }
                            }

                            for (int k = 0; k < dataSet.getNumRows(); k++) {
                                dataSet.setDouble(k, j, Double.NaN);
                            }
                        }

                        TabularDataTable model = (TabularDataTable) jTable.getModel();
                        model.fireTableDataChanged();
                    }
                }
            }
        });

        JCheckBoxMenuItem categoryNames =
                new JCheckBoxMenuItem("Show Category Names");
        JTable selectedJTable = getSelectedJTable();

        if (selectedJTable != null && selectedJTable instanceof TabularDataJTable) {
            TabularDataJTable tableTabular = (TabularDataJTable) selectedJTable;
            categoryNames.setSelected(tableTabular.isShowCategoryNames());
        }

        categoryNames.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTable selectedJTable = getSelectedJTable();
                TabularDataJTable tableTabular =
                        (TabularDataJTable) selectedJTable;
                JCheckBoxMenuItem source = (JCheckBoxMenuItem) e.getSource();
                tableTabular.setShowCategoryNames(source.isSelected());
            }
        });

        editMenu.add(clearCells);
        editMenu.add(deleteSelectedRowsOrColumns);
        editMenu.add(deleteNamedColumns);
        editMenu.add(selectNamedColumns);
        editMenu.add(copyCells);
        editMenu.add(cutCells);
        editMenu.add(pasteCells);
        editMenu.addSeparator();
        editMenu.add(categoryNames);
        editMenu.add(setToMissingCells);

        menuBar.add(editMenu);
//        menuBar.add(new Knowledge2Menu(this));

        JMenu tools = new JMenu("Tools");
        menuBar.add(tools);

        tools.add(new CalculatorAction(this));
        tools.add(new HistogramAction(this));
        tools.add(new ScatterPlotAction(this));
        tools.add(new QQPlotAction(this));
        tools.add(new NormalityTestAction(this));
        tools.add(new DescriptiveStatsAction(this));
//        tools.add(new ConditionalIndependenceTestAction(this));

//        final Params _params = dataWrapper.getParams();
//
//        if (_params instanceof BayesDataParams || _params instanceof SemDataParams) {
//            JMenuItem drawSample = new JMenuItem("Draw New Sample");
//            tools.add(drawSample);
//
//            drawSample.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent event) {
//                    if (_params instanceof BayesDataParams) {
//                        BayesDataParams params = (BayesDataParams) _params;
//
//                        BayesDataParamsEditor paramsEditor = new BayesDataParamsEditor();
//                        paramsEditor.setParams(params);
//                        paramsEditor.setup();
//
//                        int ret = JOptionPane.showConfirmDialog(DataEditor.this, paramsEditor,
//                                "Draw New Sample", JOptionPane.OK_CANCEL_OPTION);
//
//                        if (ret == JOptionPane.CANCEL_OPTION) {
//                            return;
//                        }
//
//                        BayesDataWrapper wrapper = (BayesDataWrapper) dataWrapper;
//
//                        int sampleSize = params.getSampleSize();
//                        boolean latentDataSaved = params.isLatentDataSaved();
//                        DataSet dataSet = wrapper.getBayesIm().simulateData(sampleSize, latentDataSaved);
//                        wrapper.setDataModel(dataSet);
//                        wrapper.setSourceGraph(wrapper.getBayesIm().getDag());
//
//                        replace(wrapper.getDataModelList());
//                        selectFirstTab();
//                        firePropertyChange("modelChanged", null, null);
//                    }
//                    else if (_params instanceof SemDataParams) {
//                        SemDataParams params = (SemDataParams) _params;
//                        SemDataWrapper wrapper = (SemDataWrapper) dataWrapper;
//
//                        SemDataParamsEditor paramsEditor = new SemDataParamsEditor();
//                        paramsEditor.setParams(params);
//                        paramsEditor.setup();
//
//                        int ret = JOptionPane.showConfirmDialog(DataEditor.this, paramsEditor,
//                                "Draw New Sample", JOptionPane.OK_CANCEL_OPTION);
//
//                        if (ret == JOptionPane.CANCEL_OPTION) {
//                            return;
//                        }
//
//                        int sampleSize = params.getSampleSize();
//                        boolean latentDataSaved = params.isIncludeLatents();
//                        DataSet dataSet = wrapper.getEstIm().simulateData(sampleSize, latentDataSaved);
//                        wrapper.setDataModel(dataSet);
//                        wrapper.setSourceGraph(wrapper.getEstIm().getSemPm().getGraph());
//
//                        replace(wrapper.getDataModelList());
//                        selectFirstTab();
//                        firePropertyChange("modelChanged", null, null);
//                    } else {
//                        throw new IllegalStateException("Someone added a new type of data simulation parameter withouut " +
//                                "modifying this bit of code It's in DataEditor, in the menuBar() method.");
//                    }
//                }
//            });
//        }

//        JMenuItem nonsingularityCheck = new JMenuItem("Check nonsingularity");
//
//        nonsingularityCheck.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                DataModel dataModel = dataWrapper.getSelectedDataModel();
//
//                if (dataModel instanceof CovarianceMatrix) {
//                    CovarianceMatrix dataSet = (CovarianceMatrix) dataModel;
//
//                    TetradMatrix data = dataSet.getMatrix();
//
//                    System.out.println(data);
//
//                    LUDecomposition decomposition = new LUDecomposition(data);
//
//                    TetradMatrix L = decomposition.getL();
//                    System.out.println(L);
//
////                        for (int i )
//
//                    boolean nonsingular = decomposition.isNonsingular();
//
////                        boolean nonsingular = true;
////
//                    try {
//                        TetradMatrix b = TetradAlgebra.times(data, data.transpose());
//                        TetradAlgebra.inverse(b);
//                    } catch (Exception e1) {
//                        System.out.println("Could not invert");
//
////                            nonsingular = false;
//                    }
//
//                    if (nonsingular) {
//                        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                                "This dataset has full rank.");
//                        return;
//                    } else {
//                        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                                "This dataset has less than full rank.");
//                        return;
//                    }
//                }
//            }
//        });
//
//        if (getDataWrapper().getSelectedDataModel() instanceof CovarianceMatrix) {
//            tools.add(nonsingularityCheck);
//        }

        int vkBackSpace = KeyEvent.VK_BACK_SPACE;
        int vkDelete = KeyEvent.VK_DELETE;

        KeyStroke backspaceKeystroke = KeyStroke.getKeyStroke(vkBackSpace, 0);
        KeyStroke deleteKeystroke = KeyStroke.getKeyStroke(vkDelete, 0);

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(backspaceKeystroke,
                "DELETE");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(deleteKeystroke,
                "DELETE");

        Action deleteAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                deleteActionListener.actionPerformed(null);
            }
        };

        getActionMap().put("DELETE", deleteAction);

        return menuBar;
    }

    private void closeTab() {
        int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                "Closing this tab will remove the data it contains. Continue?",
                "Confirm", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (ret == JOptionPane.OK_OPTION) {
            DataModel dataModel = getSelectedDataModel();
            setPreferredSize(new Dimension(600, 400));
            DataModelList dataModelList = dataWrapper.getDataModelList();
            dataModelList.remove(dataModel);
            dataWrapper.setDataModel(dataModelList);
            tabbedPane.removeAll();

            for (int i = 0; i < dataModelList.size(); i++) {
                Object _dataModel = dataModelList.get(i);
                JComponent display = dataDisplay(_dataModel);
                tabbedPane().addTab(tabName(_dataModel, i + 1), display);
            }

            tabbedPane().addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                    if ("proposedVariableNameChange".equals(propertyChangeEvent.getPropertyName())) {
                        String newName = (String) propertyChangeEvent.getNewValue();

                        // Have to make sure none of the data sets already has the new name...
                        for (int i = 0; i < tabbedPane().getTabCount(); i++) {
                            DataModel model = dataWrapper.getDataModelList().get(i);

                            for (Node node : model.getVariables()) {
                                if (newName.equals(node.getName())) {
                                    throw new IllegalArgumentException(model.getName() + " already has that variable name.");
                                }
                            }
                        }
                    } else if ("variableNameChange".equals(propertyChangeEvent.getPropertyName())) {
                        String oldName = (String) propertyChangeEvent.getOldValue();
                        String newName = (String) propertyChangeEvent.getNewValue();

                        for (int i = 0; i < tabbedPane().getTabCount(); i++) {
                            DataModel model = dataWrapper.getDataModelList().get(i);

                            for (Node node : model.getVariables()) {
                                if (oldName.equals(node.getName())) {
                                    node.setName(newName);
                                }
                            }
                        }
                    }
                }
            });

            add(tabbedPane(), BorderLayout.CENTER);
            add(menuBar(), BorderLayout.NORTH);
            validate();
        }
    }

    private static String tabName(Object dataModel, int i) {
        String tabName = ((DataModel) dataModel).getName();

        if (tabName == null) {
            tabName = "Data Set " + i;
        }

        return tabName;
    }

    /**
     * @return the data display for the given model.
     */
    private JComponent dataDisplay(Object model) {
        if (model instanceof DataSet) {
            DataDisplay dataDisplay = new DataDisplay((DataSet) model);
            dataDisplay.addPropertyChangeListener(this);
            return dataDisplay;
        } else if (model instanceof ICovarianceMatrix) {
            CovMatrixDisplay covMatrixDisplay = new CovMatrixDisplay((ICovarianceMatrix) model);
            covMatrixDisplay.addPropertyChangeListener(this);
            return covMatrixDisplay;
        } else if (model instanceof TimeSeriesData) {
            return new TimeSeriesDataDisplay((TimeSeriesData) model);
        } else {
            throw new IllegalArgumentException("Unrecognized data type.");
        }
    }

    private JTabbedPane tabbedPane() {
        return tabbedPane;
    }

    public DataModelList getDataModelList() {
        return dataWrapper.getDataModelList();
    }
}




