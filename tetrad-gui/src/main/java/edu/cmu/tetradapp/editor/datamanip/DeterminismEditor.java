/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * Feb 11, 2019 4:17:17 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class DeterminismEditor extends JPanel implements FinalizingParameterEditor {

    private static final long serialVersionUID = 6513664419620810219L;

    private DataSet sourceDataSet;

    private DataModelList dataSets = null;

    private Parameters parameters;

    private final List<Map> interventionalVarPairs = new LinkedList<>();
    private final List<String> interventionalVars = new LinkedList<>();
    private final List<String> nonInterventionalVars = new LinkedList<>();

    public DeterminismEditor() {
    }

    @Override
    public void setup() {
        // Container
        Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(640, 460));

        // Group variables based ont their NodeVariableType
        groupVariables();

        // Domain variables
        Box domainVarsBox = Box.createVerticalBox();

        domainVarsBox.add(new JLabel("Domain variables:"));

        nonInterventionalVars.forEach(e -> {
            domainVarsBox.add(new JLabel(e));
        });

        // Intervention variables
        Box interventionVarsBox = Box.createVerticalBox();

        interventionVarsBox.add(new JLabel("Intervention variables:"));

        interventionalVarPairs.forEach(e -> {
            System.out.println("=======================");
            System.out.println(e.get("status"));
            System.out.println(e.get("value"));
            interventionVarsBox.add(new JLabel("Status variable: " + e.get("status") + ", Value variable: " + e.get("value")));
        });

        // Click button to detect deterministic variables that can be merged
        JButton detectBtn = new JButton("Detect deterministic variables");
        detectBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<Set<Integer>> deterministicList = new ArrayList<>();

                // Double for loop to build the initial deterministicList
                for (int i = 0; i < sourceDataSet.getVariables().size(); i++) {
                    Set<Integer> set = new HashSet<>();

                    for (int j = i + 1; j < sourceDataSet.getVariables().size(); j++) {
                        Node outerVar = sourceDataSet.getVariable(i);
                        Node innerVar = sourceDataSet.getVariable(j);

                        System.out.println("=====Checking============" + outerVar.getName() + " and " + innerVar.getName() + " =====Deterministic=======" + isDeterministic(outerVar, innerVar));
                        
                        if (isDeterministic(outerVar, innerVar)) {
                            set.add(j);
                        }
                    }

                    // Add to list
                    deterministicList.add(set);
                }

                System.out.println("===========deterministicList============");
                System.out.println(deterministicList);
                
                List<Set<Integer>> mergedList = new ArrayList<>();
                
                for (int k = 0; k < deterministicList.size(); k++) {
//                    // Add the set index to the set
//                    deterministicList.get(k).add(k);
                      
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

                // By now we have a sorted list of non-duplicated deterministic variable set
                System.out.println("===========mergedList============");
                System.out.println(mergedList);
            }
        });

        // Add data type box to container
        container.add(new JLabel("Merge deterministic variables"));

        container.add(domainVarsBox);

        container.add(interventionVarsBox);

        container.add(detectBtn);

        // Adds the specified component to the end of this container.
        add(container, BorderLayout.CENTER);
    }

    /**
     * Determine if variable x and y are deterministic (can be merged)
     *
     * @param x
     * @param y
     * @return
     */
    private boolean isDeterministic(Node x, Node y) {
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

    private void groupVariables() {
        sourceDataSet.getVariables().forEach(e -> {
            if (e.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS) {
                interventionalVars.add(e.getName());

                // Get the interventional variable pairs for required groups
                Map<String, String> interventionalVarPair = new HashMap<>();

                // Keep the pair info
                interventionalVarPair.put("status", e.getName());
                interventionalVarPair.put("value", e.getPairedInterventionalNode().getName());

                // Add to the list
                interventionalVarPairs.add(interventionalVarPair);
            } else if (e.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) {
                interventionalVars.add(e.getName());
            } else {
                nonInterventionalVars.add(e.getName());
            }
        });
    }

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

        this.sourceDataSet = (DataSet) model;

        // All loaded datasets
        this.dataSets = dataWrapper.getDataModelList();
    }

    @Override
    public boolean finalizeEdit() {
        return true;
    }

    @Override
    public boolean mustBeShown() {
        return true;
    }

}
