/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.StringTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

/**
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class InterventionalVariablesEditor extends JPanel implements FinalizingParameterEditor {

    private static final long serialVersionUID = 6513664419620810219L;

    /**
     * The data set
     */
    private DataSet sourceDataSet;

    private Parameters parameters;
    
    private DataModelList dataSets = null;

    private StringTextField interventionalVarNameField;
    
    private List<String> columnNames;
    
    private JTable table;
    
    private DefaultTableModel tableModel;
    
    private final String interventionalVarColumnName = "Interventional Variable";
    
    private final String interventionalVarPrefix = "I_";
    
    private List<String> interventionalVariables;
    
    private boolean discreteInterventionalVariables = false;
    
    //==========================CONSTUCTORS===============================//
    
    /**
     * Constructs a new editor that will allow the user to define the experimental variables
     * The editor will return the combined single data set.
     */
    public InterventionalVariablesEditor() {
    
    }

    
    //============================= Public Methods ===================================//

    /**
     * Sets up the GUI.
     */
    @Override
    public void setup() {
        System.out.println("=========InterventionalVariablesEditor setup()=========");

        // Container
        Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(640, 460));
        
        String contextContainerBorderTitle = "Interventions";
        // Use a titled border with 5 px inside padding
        container.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(contextContainerBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Container for interventional variable
        Box interventionalVarBox = Box.createHorizontalBox();
        interventionalVarBox.setPreferredSize(new Dimension(400, 20));
        
        interventionalVarNameField = new StringTextField("", 10);

        JButton addInterventionBtn= new JButton("Add");
        
        interventionalVariables = new LinkedList<>();

        // Add file button listener
        addInterventionBtn.addActionListener((ActionEvent e) -> {
            String varName = getInterventionalVarName();
            
            // Validation to prevent empty input
            if (varName.isEmpty()) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "Interventional variable name must be specified!");
            } else {
                // Add the new interventional variable to each context
                String formattedVarName = interventionalVarPrefix + varName;

                // Prevent duplicate
                if (interventionalVariables.stream().anyMatch(str -> str.trim().equalsIgnoreCase(formattedVarName))) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "Duplicate interventional variable name!");
                    return;
                }
                
                interventionalVariables.add(formattedVarName);

                // Add new row to table
                addRow(tableModel, formattedVarName);

                // Reset the input field
                resetInterventionalVarNameField();
            }
            
            // Input focus
            interventionalVarNameField.requestFocus();
        });
        
        interventionalVarBox.add(new JLabel("Interventional variable name: I_"));
        interventionalVarBox.add(interventionalVarNameField);
        interventionalVarBox.add(Box.createRigidArea(new Dimension(10, 1)));
        interventionalVarBox.add(addInterventionBtn);
        // Must use the glue, otherwise the label is not left-aligned
        interventionalVarBox.add(Box.createHorizontalGlue());
        
        // Add interventionalVarBox to contextContainer
        container.add(interventionalVarBox);
        container.add(Box.createVerticalStrut(10));

        // Create object of table and table model
        table = new JTable();
  
        tableModel = new DefaultTableModel();
        
        // Set model into the table object
        table.setModel(tableModel); 

        // Headers
        columnNames = new LinkedList<>();
        // The very left header
        columnNames.add(interventionalVarColumnName);
        
        // Add headers of data file names
        dataSets.forEach(dataSet -> {
            columnNames.add(dataSet.getName());
        });

        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());

        System.out.println("=========column count =========" + table.getColumnCount());

        // To be able to see the header, we need to put the table in a JScrollPane
        JScrollPane tablePane = new JScrollPane(table);

        // Show checkboxes in table cells
        for (int i = 0; i < dataSets.size(); i++) {
            TableColumn tc = table.getColumnModel().getColumn(1 + i);
            tc.setCellEditor(table.getDefaultEditor(Boolean.class));
            tc.setCellRenderer(table.getDefaultRenderer(Boolean.class));
        } 
                
        // Add table to parent containerr       
        container.add(tablePane);
        container.add(Box.createVerticalStrut(10));

        // Data type of interventional variables
        Box interventionalVarsDataTypeBox = Box.createHorizontalBox();
        
        JRadioButton continousRadioButton = new JRadioButton("Continuous");
        JRadioButton discreteRadioButton = new JRadioButton("Discrete");
 
        // // Continuous radio button is selected by default
        continousRadioButton.setSelected(true);
        
        // Event listener
        continousRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    discreteInterventionalVariables = false;
                }
            }
        });
        
        discreteRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    discreteInterventionalVariables = true;
                }
            }
        });
        
        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup dataTypeBtnGrp = new ButtonGroup();
        dataTypeBtnGrp.add(continousRadioButton);
        dataTypeBtnGrp.add(discreteRadioButton);
        
        interventionalVarsDataTypeBox.add(new JLabel("Data type of ALL added interventional variables: "));
        interventionalVarsDataTypeBox.add(continousRadioButton);
        interventionalVarsDataTypeBox.add(discreteRadioButton);
        // Must use the glue, otherwise the label is not left-aligned
        interventionalVarsDataTypeBox.add(Box.createHorizontalGlue());
        
        // Add data type box to container
        container.add(interventionalVarsDataTypeBox);
        
        // Adds the specified component to the end of this container.
        add(container, BorderLayout.CENTER);
    }


    /**
     * Tells the editor to commit any final details before it is closed (only called when the
     * user selects "Ok" or something of that nature). If false is returned the edit is considered
     * invalid and it will be treated as if the user selected "cancelAll".
     *
     * @return - true if the edit was committed.
     */
    @Override
    public boolean finalizeEdit() {
        if (interventionalVariables.isEmpty()) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "You must specify at least one interventional variable!");
            return false;
        }
        
        System.out.println("===============Added Interventioanl Variables==============");
        interventionalVariables.forEach(e->{
            System.out.println(e);
        });
        
        System.out.println("===============Datasets==============");
        dataSets.forEach(e -> {
            System.out.println(e.getName());
        });
        
        // 2D array of all cell values, true/false
        boolean interventions[][] = new boolean[interventionalVariables.size()][dataSets.size()];
        
        for (int row = 0; row < interventionalVariables.size(); row++) {
            for (int col = 0; col < dataSets.size(); col++) {
                // Skip the first column which is the interventional variable names
                boolean cellValue = (boolean) table.getValueAt(row, col + 1);
                interventions[row][col] = cellValue;
            }
        }
        
        System.out.println("===============Interventions (Row) & Datasets (Column)==============");
        System.out.println(Arrays.deepToString(interventions).replace("], ", "]\n"));
        
        System.out.println("===============Data type of ALL interventional variables==============: " + discreteInterventionalVariables);
        
        // Combine the datasets with added interventional variables 
        // For testing, use the first dataset
        parameters.set("combinedDataset", dataSets.get(0));
        
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
            return interventionalVarNameField.getText().trim();
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
