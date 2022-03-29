/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import org.apache.commons.lang3.SerializationUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Feb 11, 2019 4:17:17 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class DeterminismEditor extends JPanel implements FinalizingParameterEditor {

    private static final long serialVersionUID = 6513664419620810219L;

    private DataSet sourceDataSet;

    private DataSet sourceDataSetCopy;

    private DataSet mergedDataSet;

    private Parameters parameters;

    private final String fullyDeterminisedDomainVar = "fullyDeterminisedDomainVar";

    //==========================CONSTUCTORS===============================//

    /**
     * Constructs a new editor that will allow the user to merge determistic
     * interventional variables. The editor will return the merged dataset
     */
    public DeterminismEditor() {
    }

    //============================= Public Methods ===================================//

    /**
     * Sets up the GUI.
     */
    @Override
    public void setup() {
        // Container
        final Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(360, 200));

        // Detect
        final List<Set<Integer>> mergedList = detectDeterministicVars();

        // Intervention variables
        final Box mergedVarsBox = Box.createVerticalBox();
        mergedVarsBox.setPreferredSize(new Dimension(320, 160));
        mergedVarsBox.setBorder(BorderFactory.createTitledBorder("Deterministic Interventional Variables"));

        // Merged variables table
        final JTable table = new JTable();

        final DefaultTableModel tableModel = new DefaultTableModel();

        table.setModel(tableModel);

        // Headers
        final List<String> columnNames = Arrays.asList("Deterministic variables", "Merged new variable");

        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());

        mergedList.forEach(indexSet -> {
            final List<String> varNameList = new LinkedList<>();

            final Integer[] indexArray = indexSet.toArray(new Integer[indexSet.size()]);

            for (int i = 0; i < indexArray.length; i++) {
                varNameList.add(this.sourceDataSetCopy.getVariable(indexArray[i]).getName());
            }

            final String varNames = String.join(", ", varNameList);
            final String mergedVarName = String.join("_", varNameList);

            final List<String> rowData = Arrays.asList(varNames, mergedVarName);
            tableModel.addRow(rowData.toArray());

        });

        final JScrollPane scrollPane = new JScrollPane(table);

        mergedVarsBox.add(scrollPane);

        // Add to container
        container.add(mergedVarsBox);

        // Adds the specified component to the end of this container.
        add(container, BorderLayout.CENTER);

        // Merge
        mergeDeterministicVars(mergedList);
    }

    /**
     * Tells the editor to commit any final details before it is closed (only
     * called when the user selects "Ok" or something of that nature). If false
     * is returned the edit is considered invalid and it will be treated as if
     * the user selected "cancelAll".
     *
     * @return - true if the edit was committed.
     */
    @Override
    public boolean finalizeEdit() {
        // This way we can pass the merged dataset to the wrapper
        this.parameters.set("DeterminisedDataset", this.mergedDataSet);

        return true;
    }

    /**
     * Sets the previous params, must be <code>DiscretizationParams</code>.
     *
     * @param params
     */
    @Override
    public void setParams(final Parameters params) {
        this.parameters = params;
    }

    /**
     * The parent model should be a <code>DataWrapper</code>.
     *
     * @param parentModels
     */
    @Override
    public void setParentModels(final Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }

        DataWrapper dataWrapper = null;

        for (final Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                dataWrapper = (DataWrapper) parent;
            }
        }

        if (dataWrapper == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }

        final DataModel model = dataWrapper.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The dataset must be a rectangular dataset");
        }

        if (!containsInterventionalVariables(model)) {
            throw new IllegalArgumentException("The dataset must contain interventional variables");
        }

        // Get the source dataset, keep it untouched
        final DataSet dataSet = (DataSet) model;

        this.sourceDataSet = dataSet;

        // Work on this dataset for merging, deep copy
        this.sourceDataSetCopy = SerializationUtils.clone(dataSet);
    }

    @Override
    public boolean mustBeShown() {
        return true;
    }

    //=============================== Private Methods ================================//

    /**
     * Determinism detection among interventional variables
     *
     * @return
     */
    private List<Set<Integer>> detectDeterministicVars() {
        final List<Set<Integer>> deterministicList = new ArrayList<>();

        // Double for loop to build the initial deterministicList
        for (int i = 0; i < this.sourceDataSetCopy.getVariables().size(); i++) {
            final Set<Integer> set = new HashSet<>();

            for (int j = i + 1; j < this.sourceDataSetCopy.getVariables().size(); j++) {
                final Node outerVar = this.sourceDataSetCopy.getVariable(i);
                final Node innerVar = this.sourceDataSetCopy.getVariable(j);

                // Bidirectional check
                if (isDeterministic(outerVar, innerVar) && isDeterministic(innerVar, outerVar)) {
                    if (outerVar.getNodeVariableType() == innerVar.getNodeVariableType()) {
                        set.add(j);
                    } else {
                        if ((outerVar.getNodeVariableType() != NodeVariableType.DOMAIN) && (innerVar.getNodeVariableType() != NodeVariableType.DOMAIN)) {
                            set.add(j);
                        } else {
                            // Add new attribute to domain variables that are fully determinised
                            // But don't merge
                            if (outerVar.getNodeVariableType() == NodeVariableType.DOMAIN) {
                                outerVar.addAttribute(this.fullyDeterminisedDomainVar, true);
                            } else {
                                innerVar.addAttribute(this.fullyDeterminisedDomainVar, true);
                            }
                        }
                    }
                }
            }

            // Add to list
            deterministicList.add(set);
        }

        final List<Set<Integer>> mergedList = new ArrayList<>();

        for (int k = 0; k < deterministicList.size(); k++) {
            // Create a new set for non-empty set and add all the elements of the sets whose index is in this set
            if (!deterministicList.get(k).isEmpty()) {
                final Set<Integer> mergedSet = new HashSet<>();

                // Add the index of this set to the merged set
                mergedSet.add(k);

                mergedSet.addAll(deterministicList.get(k));

                for (final Integer index : deterministicList.get(k)) {
                    mergedSet.addAll(deterministicList.get(index));

                    // Then empty that set
                    deterministicList.get(index).clear();
                }

                // Finally add to the mergedList
                mergedList.add(mergedSet);
            }
        }

        return mergedList;
    }

    /**
     * Merge the deterministic interventional variables and create new dataset
     *
     * @param mergedList
     */
    private void mergeDeterministicVars(final List<Set<Integer>> mergedList) {
        final List<Integer> toBeRemovedColumns = new LinkedList<>();

        mergedList.forEach(indexSet -> {
            final List<Node> varList = new LinkedList<>();
            final List<String> varNameList = new LinkedList<>();

            final Integer[] indexArray = indexSet.toArray(new Integer[indexSet.size()]);

            for (int i = 0; i < indexArray.length; i++) {
                varList.add(this.sourceDataSetCopy.getVariable(indexArray[i]));
                varNameList.add(this.sourceDataSetCopy.getVariable(indexArray[i]).getName());

                // Add index for removal except the first one
                // Remember the column to be removed after merging
                if (i > 0) {
                    toBeRemovedColumns.add(indexArray[i]);
                }
            }

            final String mergedVarName = String.join("_", varNameList);

            // Keep the first node as the merged node and rename
            final Node mergedNode = varList.get(0);
            mergedNode.setName(mergedVarName);

            //Use this hierarchy for determining the merged variable's type: [value] > [status]
            final NodeVariableType mergedNodeVariableType = mergedNode.getNodeVariableType();
            for (int i = 0; i < indexArray.length; i++) {
                final Node node = this.sourceDataSetCopy.getVariable(indexArray[i]);
                if ((node.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) && (node.getNodeVariableType() != mergedNodeVariableType)) {
                    mergedNode.setNodeVariableType(NodeVariableType.INTERVENTION_VALUE);
                    break;
                }
            }
        });

        // Target variables
        final List<Node> nodeList = new LinkedList<>();
        final Map<Node, Integer> origIndexMap = new HashMap<>();
        this.sourceDataSetCopy.getVariables().forEach(node -> {
            final int columnIndex = this.sourceDataSetCopy.getColumn(node);
            // Skip these columns when creating the new dataset
            if (!toBeRemovedColumns.contains(columnIndex)) {
                nodeList.add(node);
                origIndexMap.put(node, columnIndex);
            }
        });

        // Now scan all the coloumns and create the data box
        final DataSet dataBox = createDataBoxData(nodeList, origIndexMap);

        // Finally convert to data model
        this.mergedDataSet = dataBox;
    }

    /**
     * Check to see if the dataset has interventional variables
     *
     * @param model
     * @return
     */
    private boolean containsInterventionalVariables(final DataModel model) {
        final List<String> interventionalVars = new LinkedList<>();

        model.getVariables().forEach(e -> {
            if (e.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS || e.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) {
                interventionalVars.add(e.getName());
            }
        });

        return interventionalVars.size() > 0;
    }

    /**
     * Determine if discrete variable x and discrete variable y are deterministic
     *
     * @param x
     * @param y
     * @return
     */
    private boolean isDeterministic(final Node x, final Node y) {
        // For now only check between discrete variables
        if ((x instanceof DiscreteVariable) && (y instanceof DiscreteVariable)) {
            final Map<Object, Object> map = new HashMap<>();

            final int xColumnIndex = this.sourceDataSet.getColumn(x);
            final int yColumnIndex = this.sourceDataSet.getColumn(y);
            final int numRows = this.sourceDataSet.getNumRows();

            for (int i = 0; i < numRows; i++) {
                // objX as key, and objY as value
                final Object objX = this.sourceDataSet.getObject(i, xColumnIndex);
                final Object objY = this.sourceDataSet.getObject(i, yColumnIndex);

                if (map.containsKey(objX)) {
                    if (!map.get(objX).equals(objY)) {
                        return false;
                    }
                } else {
                    map.put(objX, objY);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Create BoxDataSet from the target nodes
     *
     * @param nodeList
     * @param origIndexMap
     * @return
     */
    private BoxDataSet createDataBoxData(final List<Node> nodeList, final Map<Node, Integer> origIndexMap) {
        // Now scan all the coloumns and create the data box
        boolean isDiscrete = false;
        boolean isContinuous = false;
        for (final Node node : nodeList) {
            if (node instanceof ContinuousVariable) {
                isContinuous = true;
            } else {
                isDiscrete = true;
            }
        }

        if (isDiscrete && isContinuous) {
            return createMixedDataBox(nodeList, origIndexMap);
        } else if (isContinuous) {
            return createContinuousDataBox(nodeList, origIndexMap);
        } else if (isDiscrete) {
            return createDiscreteDataBox(nodeList, origIndexMap);
        } else {
            return null;
        }
    }

    private BoxDataSet createMixedDataBox(final List<Node> nodeList, final Map<Node, Integer> origIndexMap) {
        final int numOfCols = nodeList.size();
        final int numOfRows = this.sourceDataSetCopy.getNumRows();
        final double[][] continuousData = new double[numOfCols][];
        final int[][] discreteData = new int[numOfCols][];

        for (int i = 0; i < numOfCols; i++) {
            final Node node = nodeList.get(i);

            // initialize data
            if (node instanceof DiscreteVariable) {
                discreteData[i] = new int[numOfRows];
                for (int j = 0; j < numOfRows; j++) {
                    discreteData[i][j] = this.sourceDataSetCopy.getInt(j, origIndexMap.get(node));
                }
            } else {
                continuousData[i] = new double[numOfRows];
                for (int j = 0; j < numOfRows; j++) {
                    continuousData[i][j] = this.sourceDataSetCopy.getDouble(j, origIndexMap.get(node));
                }
            }
        }

        return new BoxDataSet(new MixedDataBox(nodeList, numOfRows, continuousData, discreteData), nodeList);
    }

    /**
     * The data can only be mixed or discrete. This won't be used for now
     *
     * @param nodeList
     * @param origIndexMap
     * @return
     */
    private BoxDataSet createContinuousDataBox(final List<Node> nodeList, final Map<Node, Integer> origIndexMap) {
        final int numOfCols = nodeList.size();
        final int numOfRows = this.sourceDataSetCopy.getNumRows();
        final double[][] data = new double[numOfRows][numOfCols];

        for (int i = 0; i < numOfRows; i++) {
            for (int j = 0; j < numOfCols; j++) {
                final Node node = nodeList.get(j);
                data[i][j] = this.sourceDataSetCopy.getDouble(i, origIndexMap.get(node));
            }
        }

        return new BoxDataSet(new DoubleDataBox(data), nodeList);
    }

    private BoxDataSet createDiscreteDataBox(final List<Node> nodeList, final Map<Node, Integer> origIndexMap) {
        final int numOfCols = nodeList.size();
        final int numOfRows = this.sourceDataSetCopy.getNumRows();
        final int[][] data = new int[numOfCols][numOfRows];

        for (int i = 0; i < numOfCols; i++) {
            for (int j = 0; j < numOfRows; j++) {
                final Node node = nodeList.get(i);
                data[i][j] = this.sourceDataSetCopy.getInt(j, origIndexMap.get(node));
            }
        }

        return new BoxDataSet(new VerticalIntDataBox(data), nodeList);
    }
}
