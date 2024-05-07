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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class should access the getMappings mapped to it from the mapping to the search classes. This class is the
 * parameter editor currently for BuildPureClusters parameters.
 *
 * @author Ricardo Silva rbas@cs.cmu.edu
 * @version $Id: $Id
 */
public class BuildPureClustersParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter wrapper being viewed.
     */
    private Parameters params;

    /**
     * The parent models.
     */
    private Object[] parentModels;

    /**
     * Opens up an editor to let the user view the given BuildPureClustersRunner.
     */
    public BuildPureClustersParamsEditor() {
    }

    /**
     * <p>Setter for the field <code>parentModels</code>.</p>
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException();
        }

        this.parentModels = parentModels;
    }

    /**
     * <p>setup.</p>
     */
    public void setup() {
        DoubleTextField alphaField = new DoubleTextField(
                this.params.getDouble("alpha", 0.001), 4, NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter((value, oldValue) -> {
            try {
                getParams().set("alpha", 0.001);
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });

        BpcTestType[] descriptions = BpcTestType.getTestDescriptions();
        JComboBox testSelector = new JComboBox(descriptions);
        testSelector.setSelectedItem(getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART));

        testSelector.addActionListener(e -> {
            JComboBox combo = (JComboBox) e.getSource();
            BpcTestType testType = (BpcTestType) combo.getSelectedItem();
            getParams().set("tetradTestType", testType);
        });

        BpcTestType[] purifyDescriptions = BpcTestType.getPurifyTestDescriptions();
        JComboBox purifySelector = new JComboBox(purifyDescriptions);
        purifySelector.setSelectedItem(getParams().get("purifyTestType", BpcTestType.NONE));

        purifySelector.addActionListener(e -> {
            JComboBox combo = (JComboBox) e.getSource();
            BpcTestType testType = (BpcTestType) combo.getSelectedItem();
            getParams().set("purifyTestType", testType);
        });

        //Where is it setting the appropriate knowledge for the search?
        DataModel dataModel = null;

        for (Object parentModel : this.parentModels) {
            if (parentModel instanceof DataWrapper dataWrapper) {
                dataModel = dataWrapper.getSelectedDataModel();
            }
        }

        if (dataModel == null) {
            throw new IllegalStateException("Null data model.");
        }

        List<String> varNames =
                new ArrayList<>(dataModel.getVariableNames());

        boolean isDiscreteModel;
        if (dataModel instanceof ICovarianceMatrix) {
            isDiscreteModel = false;
        } else {
            DataSet dataSet = (DataSet) dataModel;
            isDiscreteModel = dataSet.isDiscrete();

        }

        this.params.set("varNames", varNames);
        alphaField.setValue(this.params.getDouble("alpha", 0.001));

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Alpha:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(alphaField);
        b.add(b1);

        if (!isDiscreteModel) {
            Box b2 = Box.createHorizontalBox();
            b2.add(new JLabel("Statistical Test:"));
            b2.add(Box.createHorizontalGlue());
            b2.add(testSelector);
            b.add(b2);

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("Purify Test:"));
            b3.add(Box.createHorizontalGlue());
            b3.add(purifySelector);
            b.add(b3);
        } else {
            this.params.set("purifyTestType", BpcTestType.DISCRETE_LRT);
            this.params.set("tetradTestType", BpcTestType.DISCRETE);
        }

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return false;
    }

    private Parameters getParams() {
        return this.params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }
}





