/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import java.util.HashMap;
import java.util.List;
import javax.swing.JPanel;

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
        System.out.println("=========ExperimentalVariablesParamsEditor=========");

        final List<Node> variables = this.sourceDataSet.getVariables();
        
        variables.forEach(var -> {
            System.out.println(var);
        });
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
        this.parameters.set("discretizationSpecs", new HashMap<Node, DiscretizationSpec>());
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
        DataWrapper data = null;
        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                data = (DataWrapper) parent;
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The dataset must be a rectangular dataset");
        }
        this.sourceDataSet = (DataSet) model;
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
