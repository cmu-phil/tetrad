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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

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
        container.setPreferredSize(new Dimension(500, 460));
        
        final List<String> variables = this.sourceDataSet.getVariableNames();

        DefaultListModel varNamesListModel = new DefaultListModel();
        
        variables.forEach(varName -> {
            System.out.println(varName);

            // Add each file name to the list model
            varNamesListModel.addElement(varName);
        });
        
        JList varNamesList = new JList(varNamesListModel);
        
        JScrollPane variablesListScroller = new JScrollPane(varNamesList);
        variablesListScroller.setPreferredSize(new Dimension(450, 150));
        variablesListScroller.setAlignmentX(LEFT_ALIGNMENT);

        //Lay out the label and scroll pane from top to bottom.
        JPanel variablesListPane = new JPanel();
        variablesListPane.setLayout(new BoxLayout(variablesListPane, BoxLayout.PAGE_AXIS));
        JLabel variablesLabel = new JLabel("Variables");

        variablesListPane.add(variablesLabel);
        variablesListPane.add(Box.createRigidArea(new Dimension(0,5)));
        variablesListPane.add(variablesListScroller);
        variablesListPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Add to container
        container.add(variablesListPane);
    
        System.out.println("========ExperimentalVariablesWraper setParentModels()=========");
        for (DataModel dataModel : dataSets) {
            String dataSetName = dataModel.getName();
            System.out.println(dataSetName);

            // Create context for each dataset
            Box contextBox = Box.createVerticalBox();
            contextBox.setPreferredSize(new Dimension(450, 140));
            JLabel contextLabel = new JLabel("Context of " + dataSetName);
            JTextPane contextArea = new JTextPane();
            contextArea.setPreferredSize(new Dimension(450, 100));
            
            contextBox.add(contextLabel);
            contextBox.add(Box.createVerticalStrut(10));
            contextBox.add(contextArea);
            contextBox.add(Box.createVerticalStrut(10));
            
            // Add to the parent container
            container.add(contextBox);
        }

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

}
