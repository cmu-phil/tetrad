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
import javax.swing.Box;
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
    
    
    public DeterminismEditor() {
    }

    @Override
    public boolean finalizeEdit() {
        return true;
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
    public void setup() {
        // Container
        Box container = Box.createVerticalBox();
        container.setPreferredSize(new Dimension(640, 460));

        
        // Add data type box to container
        container.add(new JLabel("Merge deterministic variables"));

        // Adds the specified component to the end of this container.
        add(container, BorderLayout.CENTER);
    }

    @Override
    public boolean mustBeShown() {
        return true;
    }

}
