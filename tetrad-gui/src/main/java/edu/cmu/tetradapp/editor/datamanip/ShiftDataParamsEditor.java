///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.search.ShiftSearch;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.datamanip.ShiftDataParams;
import edu.cmu.tetradapp.util.TextAreaOutputStream;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Tyler Gibson
 */
public class ShiftDataParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private ShiftDataParams params;
    private Object[] parentModels;
    private ShiftSearch search;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public ShiftDataParamsEditor() {
        super(new BorderLayout());
    }


    /**
     * Sets the parameters.
     */
    public void setParams(Params params) {
        this.params = (ShiftDataParams) params;
    }

    /**
     * Does nothing
     */
    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    /**
     * Builds the panel.
     */
    public void setup() {
        DataModelList dataModelList = null;

        for (Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModel;
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

        final List<DataModel> dataSets = new ArrayList<>();

        for (Object aDataModelList : dataModelList) {
            dataSets.add((DataSet) aDataModelList);
        }

        SpinnerModel maxVarsModel = new SpinnerNumberModel(
            Preferences.userRoot().getInt("shiftSearchMaxNumShifts", 3), 1, 50, 1);
        JSpinner maxVarsSpinner = new JSpinner(maxVarsModel);
        maxVarsSpinner.setMaximumSize(maxVarsSpinner.getPreferredSize());

        maxVarsSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                int value = (Integer) model.getValue();
                Preferences.userRoot().putInt("shiftSearchMaxNumShifts", value);
            }
        });

        SpinnerModel maxShiftModel = new SpinnerNumberModel(
            Preferences.userRoot().getInt("shiftSearchMaxShift", 2), 1, 50, 1);
        JSpinner maxShiftSpinner = new JSpinner(maxShiftModel);
        maxShiftSpinner.setMaximumSize(maxShiftSpinner.getPreferredSize());

        maxShiftSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                int value = (Integer) model.getValue();
                Preferences.userRoot().putInt("shiftSearchMaxShift", value);
            }
        });

        JButton searchButton = new JButton("Search");
        final JButton stopButton = new JButton("Stop");

        final JTextArea textArea = new JTextArea();
        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setPreferredSize(new Dimension(500, 200));

        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                final Thread thread = new Thread() {
                    public void run() {
                        textArea.setText("");
                        doShiftSearch(dataSets, textArea);
                    }
                };

                thread.start();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (search != null) {
                    search.stop();
                }
            }
        });


        JComboBox directionBox = new JComboBox(new String[] {"forward", "backward"});
        directionBox.setSelectedItem(params.isForwardSearch() ? "forward" : "backward");
        directionBox.setMaximumSize(directionBox.getPreferredSize());

        directionBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JComboBox source = (JComboBox) actionEvent.getSource();
                String selected = (String) source.getSelectedItem();
                params.setForwardSearch("forward".equals(selected));
            }
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

        final Box a1 = Box.createVerticalBox();

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

        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                System.out.println("a1 shown");
                a1.removeAll();
                setUpA1(dataSets, a1);
            }
        });
    }

    private void setUpA1(List<DataModel> dataSets, Box a1) {
        int[] shifts = params.getShifts();

        if (shifts.length != ((DataSet)dataSets.get(0)).getNumColumns()) {
            shifts = new int[((DataSet)dataSets.get(0)).getNumColumns()];
            params.setShifts(shifts);
        }

        final int[] _shifts = shifts;

        for (int i = 0; i < ((DataSet)dataSets.get(0)).getNumColumns(); i++) {
            Node node = ((DataSet)dataSets.get(0)).getVariable(i);
            Box a5 = Box.createHorizontalBox();

            SpinnerModel shiftModel = new SpinnerNumberModel(_shifts[i], -50, 50, 1);
            JSpinner shiftSpinner = new JSpinner(shiftModel);
            shiftSpinner.setMaximumSize(shiftSpinner.getPreferredSize());
            final int nodeIndex = i;

            shiftSpinner.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    JSpinner spinner = (JSpinner) e.getSource();
                    SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                    int value = (Integer) model.getValue();
                    _shifts[nodeIndex] = value;
                    params.setShifts(_shifts);
                }
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

        search = new ShiftSearch(dataSets);
        search.setMaxNumShifts(Preferences.userRoot().getInt("shiftSearchMaxNumShifts", 2));
        search.setMaxShift(Preferences.userRoot().getInt("shiftSearchMaxShift", 2));
        search.setC(1);
        search.setOut(out);
        search.setForwardSearch(params.isForwardSearch());
        int[] backshifts = search.search();

//        List<DataSet> backshiftedDataSets = shiftDataSets(dataSets, backshifts);
//
//        DataModelList _list = new DataModelList();
//
//        for (DataSet dataSet : backshiftedDataSets) {
//            _list.add(dataSet);
//        }

        params.setShifts(backshifts);
    }

//    private List<DataSet> shiftDataSets(List<DataSet> dataSets, int[] shifts) {
//        List<DataSet> shiftedDataSets = new ArrayList<DataSet>();
//
//        for (DataSet dataSet : dataSets) {
//            shiftedDataSets.add(TimeSeriesUtils.createShiftedData(dataSet, shifts));
//        }
//
//        return shiftedDataSets;
//    }

    /**
     * @return true
     */
    public boolean mustBeShown() {
        return true;
    }
}



