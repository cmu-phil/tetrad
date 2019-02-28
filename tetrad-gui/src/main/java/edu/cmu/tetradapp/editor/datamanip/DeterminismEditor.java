/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.apache.commons.lang3.SerializationUtils;

/**
 *
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
        Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(360, 200));

        // Detect
        List<Set<Integer>> mergedList = detectDeterministicVars();

        // Intervention variables
        Box mergedVarsBox = Box.createVerticalBox();
        mergedVarsBox.setPreferredSize(new Dimension(320, 160));
        mergedVarsBox.setBorder(BorderFactory.createTitledBorder("Deterministic Interventional Variables"));

        // Merged variables table
        JTable table = new JTable();

        DefaultTableModel tableModel = new DefaultTableModel();

        table.setModel(tableModel);

        // Headers
        List<String> columnNames = Arrays.asList("Deterministic variables", "Merged new variable");

        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());

        mergedList.forEach(indexSet -> {
            List<String> varNameList = new LinkedList<>();

            Integer[] indexArray = indexSet.toArray(new Integer[indexSet.size()]);

            for (int i = 0; i < indexArray.length; i++) {
                varNameList.add(sourceDataSetCopy.getVariable(indexArray[i]).getName());
            }

            String varNames = String.join(", ", varNameList);
            String mergedVarName = String.join("_", varNameList);

            List<String> rowData = Arrays.asList(varNames, mergedVarName);
            tableModel.addRow(rowData.toArray());

        });

        JScrollPane scrollPane = new JScrollPane(table);

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
        parameters.set("DeterminisedDataset", mergedDataSet);

        return true;
    }

    /**
     * Sets the previous params, must be <code>DiscretizationParams</code>.
     *
     * @param params
     */
    @Override
    public void setParams(Parameters params) {
        this.parameters = params;
    }

    /**
     * The parent model should be a <code>DataWrapper</code>.
     *
     * @param parentModels
     */
    @Override
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }

        DataWrapper dataWrapper = null;

        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                dataWrapper = (DataWrapper) parent;
            }
        }
        
        if (dataWrapper == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        
        DataModel model = dataWrapper.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The dataset must be a rectangular dataset");
        }

        if (!containsInterventionalVariables(model)) {
            throw new IllegalArgumentException("The dataset must contain interventional variables");
        }
        
        // Get the source dataset, keep it untouched
        DataSet dataSet = (DataSet) model;

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
        List<Set<Integer>> deterministicList = new ArrayList<>();

        // Double for loop to build the initial deterministicList
        for (int i = 0; i < sourceDataSetCopy.getVariables().size(); i++) {
            Set<Integer> set = new HashSet<>();

            for (int j = i + 1; j < sourceDataSetCopy.getVariables().size(); j++) {
                Node outerVar = sourceDataSetCopy.getVariable(i);
                Node innerVar = sourceDataSetCopy.getVariable(j);

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
                                outerVar.addAttribute(fullyDeterminisedDomainVar, true);
                            } else {
                                innerVar.addAttribute(fullyDeterminisedDomainVar, true);
                            }
                        }
                    }
                }
            }

            // Add to list
            deterministicList.add(set);
        }

        List<Set<Integer>> mergedList = new ArrayList<>();

        for (int k = 0; k < deterministicList.size(); k++) {
            // Create a new set for non-empty set and add all the elements of the sets whose index is in this set
            if (!deterministicList.get(k).isEmpty()) {
                Set<Integer> mergedSet = new HashSet<>();

                // Add the index of this set to the merged set
                mergedSet.add(k);

                mergedSet.addAll(deterministicList.get(k));

                for (Integer index : deterministicList.get(k)) {
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
    private void mergeDeterministicVars(List<Set<Integer>> mergedList) {
        List<Integer> toBeRemovedColumns = new LinkedList<>();

        mergedList.forEach(indexSet -> {
            List<Node> varList = new LinkedList<>();
            List<String> varNameList = new LinkedList<>();

            Integer[] indexArray = indexSet.toArray(new Integer[indexSet.size()]);

            for (int i = 0; i < indexArray.length; i++) {
                varList.add(sourceDataSetCopy.getVariable(indexArray[i]));
                varNameList.add(sourceDataSetCopy.getVariable(indexArray[i]).getName());

                // Add index for removal except the first one
                // Remember the column to be removed after merging
                if (i > 0) {
                    toBeRemovedColumns.add(indexArray[i]);
                }
            }

            String mergedVarName = String.join("_", varNameList);

            // Keep the first node as the merged node and rename
            Node mergedNode = varList.get(0);
            mergedNode.setName(mergedVarName);

            //Use this hierarchy for determining the merged variable's type: [value] > [status]
            NodeVariableType mergedNodeVariableType = mergedNode.getNodeVariableType();
            for (int i = 0; i < indexArray.length; i++) {
                Node node = sourceDataSetCopy.getVariable(indexArray[i]);
                if ((node.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) && (node.getNodeVariableType() != mergedNodeVariableType)) {
                    mergedNode.setNodeVariableType(NodeVariableType.INTERVENTION_VALUE);
                    break;
                }
            }
        });

        // Target variables
        List<Node> nodeList = new LinkedList<>();
        Map<Node, Integer> origIndexMap = new HashMap<>();
        sourceDataSetCopy.getVariables().forEach(node -> {
            int columnIndex = sourceDataSetCopy.getColumn(node);
            // Skip these columns when creating the new dataset
            if (!toBeRemovedColumns.contains(columnIndex)) {
                nodeList.add(node);
                origIndexMap.put(node, columnIndex);
            }
        });

        // Now scan all the coloumns and create the data box
        DataSet dataBox = (DataSet) createDataBoxData(nodeList, origIndexMap);

        // Finally convert to data model
        mergedDataSet = dataBox;
    }

    /**
     * Check to see if the dataset has interventional variables
     * @param model
     * @return 
     */
    private boolean containsInterventionalVariables(DataModel model) {
        List<String> interventionalVars = new LinkedList<>();
        
        model.getVariables().forEach(e->{ 
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
    private boolean isDeterministic(Node x, Node y) {
        // For now only check between discrete variables
        if ((x instanceof DiscreteVariable) && (y instanceof DiscreteVariable)) {
            Map<Object, Object> map = new HashMap<>();

            int xColumnIndex = sourceDataSet.getColumn(x);
            int yColumnIndex = sourceDataSet.getColumn(y);
            int numRows = sourceDataSet.getNumRows();

            for (int i = 0; i < numRows; i++) {
                // objX as key, and objY as value
                Object objX = sourceDataSet.getObject(i, xColumnIndex);
                Object objY = sourceDataSet.getObject(i, yColumnIndex);

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
    private BoxDataSet createDataBoxData(List<Node> nodeList, Map<Node, Integer> origIndexMap) {
        // Now scan all the coloumns and create the data box
        boolean isDiscrete = false;
        boolean isContinuous = false;
        for (Node node : nodeList) {
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

    private BoxDataSet createMixedDataBox(List<Node> nodeList, Map<Node, Integer> origIndexMap) {
        int numOfCols = nodeList.size();
        int numOfRows = sourceDataSetCopy.getNumRows();
        double[][] continuousData = new double[numOfCols][];
        int[][] discreteData = new int[numOfCols][];

        for (int i = 0; i < numOfCols; i++) {
            Node node = nodeList.get(i);

            // initialize data
            if (node instanceof DiscreteVariable) {
                discreteData[i] = new int[numOfRows];
                for (int j = 0; j < numOfRows; j++) {
                    discreteData[i][j] = sourceDataSetCopy.getInt(j, origIndexMap.get(node));
                }
            } else {
                continuousData[i] = new double[numOfRows];
                for (int j = 0; j < numOfRows; j++) {
                    continuousData[i][j] = sourceDataSetCopy.getDouble(j, origIndexMap.get(node));
                }
            }
        }

        return new BoxDataSet(new MixedDataBox(nodeList, numOfRows, continuousData, discreteData), nodeList);
    }

    /**
     * The data can only be mixed or discrete. This won't be used for now 
     * @param nodeList
     * @param origIndexMap
     * @return 
     */
    private BoxDataSet createContinuousDataBox(List<Node> nodeList, Map<Node, Integer> origIndexMap) {
        int numOfCols = nodeList.size();
        int numOfRows = sourceDataSetCopy.getNumRows();
        double[][] data = new double[numOfRows][numOfCols];

        for (int i = 0; i < numOfRows; i++) {
            for (int j = 0; j < numOfCols; j++) {
                Node node = nodeList.get(j);
                data[i][j] = sourceDataSetCopy.getDouble(i, origIndexMap.get(node));
            }
        }

        return new BoxDataSet(new DoubleDataBox(data), nodeList);
    }

    private BoxDataSet createDiscreteDataBox(List<Node> nodeList, Map<Node, Integer> origIndexMap) {
        int numOfCols = nodeList.size();
        int numOfRows = sourceDataSetCopy.getNumRows();
        int[][] data = new int[numOfCols][numOfRows];
        
        for (int i = 0; i < numOfCols; i++) {
            for (int j = 0; j < numOfRows; j++) {
                Node node = nodeList.get(i);
                data[i][j] = sourceDataSetCopy.getInt(j, origIndexMap.get(node));
            }
        }

        return new BoxDataSet(new VerticalIntDataBox(data), nodeList);
    }
}
