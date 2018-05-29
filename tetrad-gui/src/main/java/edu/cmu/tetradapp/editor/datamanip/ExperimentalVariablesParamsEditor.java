/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.StringTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class ExperimentalVariablesParamsEditor extends JPanel implements FinalizingParameterEditor {

    private static final long serialVersionUID = 6513664419620810219L;

    /**
     * The data set
     */
    private DataSet sourceDataSet;

    private Parameters parameters;
    
    private DataModelList dataSets = null;

    private StringTextField interventionalVarNameField;
    
    private List<String> columnNames;
    
    private DefaultTableModel tableModel;
    
    //==========================CONSTUCTORS===============================//
    
    /**
     * Constructs a new editor that will allow the user to define the experimental variables
     * The editor will return the combined single data set.
     */
    public ExperimentalVariablesParamsEditor() {
    
    }

    
    //============================= Public Methods ===================================//

    /**
     * Sets up the GUI.
     */
    @Override
    public void setup() {
        System.out.println("=========ExperimentalVariablesParamsEditor setup()=========");

        // Container
        Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(640, 460));
        
        final List<String> variables = this.sourceDataSet.getVariableNames();
        
        // Container of all the contexts
        // Don't set size to this container since it grows 
        Box contextContainer = Box.createVerticalBox();
        
        // Container for interventional variable
        Box interventionalVarBox = Box.createHorizontalBox();
        interventionalVarBox.setPreferredSize(new Dimension(400, 20));
        
        interventionalVarNameField = new StringTextField("", 10);
        JButton addInterventionBtn= new JButton("Add");
        
        List<String> interventionalVariables = new LinkedList<>();
        
        interventionalVariables.add("I_default");
        
        // Add file button listener
        addInterventionBtn.addActionListener((ActionEvent e) -> {
            // Add the new interventional variable to each context
            String varName = "I_" + getInterventionalVarName();
            
            interventionalVariables.add(varName);
            
            // Add new row to table
            addRow(tableModel, varName);
            
            // Reset the input field
            resetInterventionalVarNameField();
        });
        
        interventionalVarBox.add(new JLabel("Interventional variable name: "));
        interventionalVarBox.add(interventionalVarNameField);
        interventionalVarBox.add(Box.createRigidArea(new Dimension(10, 1)));
        interventionalVarBox.add(addInterventionBtn);
        // Must use the glue, otherwise the label is not left-aligned
        interventionalVarBox.add(Box.createHorizontalGlue());
        
        // Add interventionalVarBox to contextContainer
        contextContainer.add(interventionalVarBox);
        contextContainer.add(Box.createVerticalStrut(10));
        
        
        
        // Create object of table and table model
        JTable table = new JTable();
        tableModel = new DefaultTableModel(0, 0);

        // Headers
        columnNames = new LinkedList<>();
        // The very left header
        columnNames.add("Interventional Variable");
        
        // Add headers of data file names
        dataSets.forEach(dataSet -> {
            columnNames.add(dataSet.getName());
        });
   
        // 
        tableModel.setColumnIdentifiers(columnNames.toArray());

        // Row of headers
        tableModel.addRow(columnNames.toArray());
        
        // Dynamically add table row based on interventional variables
        interventionalVariables.forEach(var -> {
            addRow(tableModel, var);
        });
        
        
        //set model into the table object
        table.setModel(tableModel);   
                
        // Add table to parent containerr       
        contextContainer.add(table);

        // Provides a scrollable view of the contextContainer
        JScrollPane contextScroller = new JScrollPane(contextContainer);
        contextScroller.setPreferredSize(new Dimension(600, 420));
        
        String contextContainerBorderTitle = "Interventions";
        // Use a titled border with 5 px inside padding
        contextScroller.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(contextContainerBorderTitle), new EmptyBorder(5, 5, 5, 5)));
        

        // Add to container
        container.add(contextScroller);
        
        // Adds the specified component to the end of this container.
        add(container, BorderLayout.CENTER);
    }


    /**
     * Adds all the info to the params.
     *
     * @return true if the edit was finalized.
     */
    @Override
    public boolean finalizeEdit() {
        return true;
    }


    /**
     * Sets the previous params, must be <code>DiscretizationParams</code>.
     * @param params
     */
    @Override
    public void setParams(Parameters params) {
        this.parameters = params;
    }

    /**
     * The parent model should be a <code>DataWrapper</code>.
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

    /**
     * @return true
     */
    @Override
    public boolean mustBeShown() {
        return true;
    }

    //=============================== Private Methods ================================//
    private String getInterventionalVarName() {
        if (!interventionalVarNameField.getText().isEmpty()) {
            return interventionalVarNameField.getText();
        } else {
            return "";
        }
    }
    
    private void resetInterventionalVarNameField() {
        if (!interventionalVarNameField.getText().isEmpty()) {
            interventionalVarNameField.setValue("");
        }
    }
    
    private void addRow(DefaultTableModel tableModel, String var) {
        List<Object> row = new LinkedList<>();
        row.add(var);

        dataSets.forEach(dataSet -> {
            row.add(false);
        });

        tableModel.addRow(row.toArray());
    }
}
