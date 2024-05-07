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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.ShiftSearch;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.TextAreaOutputStream;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * <p>ShiftDataParamsEditor class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class ShiftDataParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private Parameters params;

    /**
     * The parent models.
     */
    private Object[] parentModels;

    /**
     * The search.
     */
    private ShiftSearch search;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public ShiftDataParamsEditor() {
        super(new BorderLayout());
    }


    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameters.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Does nothing
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    /**
     * Builds the panel.
     */
    public void setup() {
        DataModelList dataModelList = null;

        for (Object parentModel : this.parentModels) {
            if (parentModel instanceof DataWrapper dataWrapper) {
                dataModelList = dataWrapper.getDataModelList();
            }
        }

        if (dataModelList == null) {
            throw new NullPointerException("Null data model list.");
        }

        for (DataModel model : dataModelList) {
            if (!(model instanceof DataSet)) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "For the shift search, all of the data in the data box must be in the form of data sets.");
                return;
            }
        }

        List<DataModel> dataSets = new ArrayList<>();

        for (Object aDataModelList : dataModelList) {
            dataSets.add((DataSet) aDataModelList);
        }

        SpinnerModel maxVarsModel = new SpinnerNumberModel(
                Preferences.userRoot().getInt("shiftSearchMaxNumShifts", 3), 1, 50, 1);
        JSpinner maxVarsSpinner = new JSpinner(maxVarsModel);
        maxVarsSpinner.setMaximumSize(maxVarsSpinner.getPreferredSize());

        maxVarsSpinner.addChangeListener(e -> {
            JSpinner spinner = (JSpinner) e.getSource();
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            int value = (Integer) model.getValue();
            Preferences.userRoot().putInt("shiftSearchMaxNumShifts", value);
        });

        SpinnerModel maxShiftModel = new SpinnerNumberModel(
                Preferences.userRoot().getInt("shiftSearchMaxShift", 2), 1, 50, 1);
        JSpinner maxShiftSpinner = new JSpinner(maxShiftModel);
        maxShiftSpinner.setMaximumSize(maxShiftSpinner.getPreferredSize());

        maxShiftSpinner.addChangeListener(e -> {
            JSpinner spinner = (JSpinner) e.getSource();
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            int value = (Integer) model.getValue();
            Preferences.userRoot().putInt("shiftSearchMaxShift", value);
        });

        JButton searchButton = new JButton("Search");
        JButton stopButton = new JButton("Stop");

        JTextArea textArea = new JTextArea();
        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setPreferredSize(new Dimension(500, 200));

        searchButton.addActionListener(actionEvent -> {
            Thread thread = new Thread(() -> {
                textArea.setText("");
                doShiftSearch(dataSets, textArea);
            });

            thread.start();
        });

        stopButton.addActionListener(actionEvent -> {
            if (ShiftDataParamsEditor.this.search != null) {
                ShiftDataParamsEditor.this.search.stop();
                TaskManager.getInstance().setCanceled(true);
            }
        });


        JComboBox directionBox = new JComboBox(new String[]{"forward", "backward"});
        directionBox.setSelectedItem(this.params.getBoolean("forwardSearch", true) ? "forward" : "backward");
        directionBox.setMaximumSize(directionBox.getPreferredSize());

        directionBox.addActionListener(actionEvent -> {
            JComboBox source = (JComboBox) actionEvent.getSource();
            String selected = (String) source.getSelectedItem();
            ShiftDataParamsEditor.this.params.set("forwardSearch", "forward".equals(selected));
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Maximum number of variables in shift set is: "));
        b2.add(maxVarsSpinner);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Maximum "));
        b3.add(directionBox);
        b3.add(new JLabel(" shift: "));
        b3.add(maxShiftSpinner);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Output:"));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        Box b5 = Box.createHorizontalBox();
        b5.add(textScroll);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(searchButton);
        b6.add(stopButton);
        b1.add(b6);

        Box a1 = Box.createVerticalBox();

        Box a2 = Box.createHorizontalBox();
        a2.add(new JLabel("Specify the shift (positive or negative) for each variable:"));
        a2.add(Box.createHorizontalGlue());
        a1.add(a2);

        a1.add(Box.createVerticalStrut(20));

        setUpA1(dataSets, a1);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Shift", new JScrollPane(a1));
        tabbedPane.addTab("Search", new JScrollPane(b1));

        add(tabbedPane, BorderLayout.CENTER);

        tabbedPane.addChangeListener(changeEvent -> {
            System.out.println("a1 shown");
            a1.removeAll();
            setUpA1(dataSets, a1);
        });
    }

    private void setUpA1(List<DataModel> dataSets, Box a1) {
        int[] shifts = (int[]) this.params.get("shifts", null);

        if (dataSets.isEmpty()) {
            throw new IllegalArgumentException("There are not datasets to shift.");
        }

        if (shifts.length != ((DataSet) dataSets.get(0)).getNumColumns()) {
            shifts = new int[((DataSet) dataSets.get(0)).getNumColumns()];
            this.params.set("shifts", shifts);
        }

        int[] _shifts = shifts;

        for (int i = 0; i < ((DataSet) dataSets.get(0)).getNumColumns(); i++) {
            Node node = ((DataSet) dataSets.get(0)).getVariable(i);
            Box a5 = Box.createHorizontalBox();

            SpinnerModel shiftModel = new SpinnerNumberModel(_shifts[i], -50, 50, 1);
            JSpinner shiftSpinner = new JSpinner(shiftModel);
            shiftSpinner.setMaximumSize(shiftSpinner.getPreferredSize());
            int nodeIndex = i;

            shiftSpinner.addChangeListener(e -> {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                int value = (Integer) model.getValue();
                _shifts[nodeIndex] = value;
                ShiftDataParamsEditor.this.params.set("shifts", _shifts);
            });

            a5.add(new JLabel("    Shift for "));
            a5.add(new JLabel(node.getName()));
            a5.add(new JLabel(" is "));
            a5.add(shiftSpinner);
            a5.add(Box.createHorizontalGlue());
            a1.add(a5);
        }
    }

    private void doShiftSearch(List<DataModel> dataSets, JTextArea textArea) {
        TextAreaOutputStream out = new TextAreaOutputStream(textArea);

        this.search = new ShiftSearch(dataSets);
        this.search.setMaxNumShifts(Preferences.userRoot().getInt("shiftSearchMaxNumShifts", 2));
        this.search.setMaxShift(Preferences.userRoot().getInt("shiftSearchMaxShift", 2));
        this.search.setC(1);
        this.search.setOut(out);
        this.search.setForwardSearch(this.params.getBoolean("forwardSearch", true));
        int[] backshifts = this.search.search();

        this.params.set("shifts", backshifts);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return true
     */
    public boolean mustBeShown() {
        return true;
    }
}



