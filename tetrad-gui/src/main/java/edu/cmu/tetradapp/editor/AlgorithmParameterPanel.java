/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.annotation.Algorithm;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import java.awt.BorderLayout;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 *
 * Dec 4, 2017 5:05:42 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmParameterPanel extends JPanel {

    private static final long serialVersionUID = 274638263704283474L;

    protected static final String DEFAULT_TITLE_BORDER = "Algorithm Parameters";

    protected static final Set<String> BOOTSTRAP_PARAMS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "numberResampling",
                    "percentResampleSize",
                    "resamplingWithReplacement",
                    "resamplingEnsemble",
                    "addOriginalDataset"
            ))
    );

    public AlgorithmParameterPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
    }

    public void addToPanel(GeneralAlgorithmRunner runner) {
        List<String> parametersToEdit = runner.getAlgorithm().getParameters();
        Parameters parameters = runner.getParameters();

        Algorithm algoAnno = runner.getAlgorithm().getClass().getAnnotation(Algorithm.class);
        String title = (algoAnno == null)
                ? DEFAULT_TITLE_BORDER
                : String.format("%s Parameters", algoAnno.name());
        setBorder(BorderFactory.createTitledBorder(title));

        removeAll();

        Box paramsBox = Box.createVerticalBox();

        // Some algorithms don't have algorithm parameters to edit
        // E.g., EB, R1...R4, RSkew, RSkewE, Skew, SkewE
        // But we added bootstrap parameters anyway  - Zhou
        if (parametersToEdit.isEmpty()) {
            Box row = Box.createHorizontalBox();
            row.add(new JLabel("No parameters to edit"));
            paramsBox.add(row);
        } else {
            boolean isRunFromGraph = runner.getSourceGraph() != null;
            List<String> uniqueParams = parametersToEdit.stream()
                    .filter(e -> {
                        return isRunFromGraph ? !BOOTSTRAP_PARAMS.contains(e) : true;
                    })
                    .distinct()
                    .collect(Collectors.toList());

            uniqueParams.forEach(parameter -> {
                ParamDescription paramDesc = ParamDescriptions.getInstance().get(parameter);

                JComponent parameterSelection;
                Object defaultValue = paramDesc.getDefaultValue();
                if (defaultValue instanceof Double) {
                    double lowerBoundDouble = paramDesc.getLowerBoundDouble();
                    double upperBoundDouble = paramDesc.getUpperBoundDouble();
                    parameterSelection = getDoubleField(parameter, parameters, (Double) defaultValue, lowerBoundDouble, upperBoundDouble);
                } else if (defaultValue instanceof Integer) {
                    int lowerBoundInt = paramDesc.getLowerBoundInt();
                    int upperBoundInt = paramDesc.getUpperBoundInt();
                    parameterSelection = getIntTextField(parameter, parameters, (Integer) defaultValue, lowerBoundInt, upperBoundInt);
                } else if (defaultValue instanceof Boolean) {
                    // Joe's old implementation with dropdown yes or no
                    //parameterSelection = getBooleanBox(parameter, parameters, (Boolean) defaultValue);
                    // Zhou's new implementation with yes/no radio buttons
                    parameterSelection = getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
                } else if (defaultValue instanceof String) {
                    parameterSelection = getStringField(parameter, parameters, (String) defaultValue);
                } else {
                    throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
                }

                // Each parameter row contains parameter label and selection/input field
                Box paramRow = Box.createHorizontalBox();

                JLabel paramLabel = new JLabel(paramDesc.getShortDescription());
                String longDescription = paramDesc.getLongDescription();
                if (longDescription != null) {
                    paramLabel.setToolTipText(longDescription);
                }
                paramRow.add(paramLabel);
                paramRow.add(Box.createHorizontalGlue());
                paramRow.add(parameterSelection);

                // Add each paramRow to paramsBox
                paramsBox.add(paramRow);

                // Also add some gap between rows
                paramsBox.add(Box.createVerticalStrut(10));
            });
        }

        add(new PaddingPanel(paramsBox), BorderLayout.CENTER);
    }

    protected DoubleTextField getDoubleField(final String parameter, final Parameters parameters,
            double defaultValue, final double lowerBound, final double upperBound) {
        final DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    protected IntTextField getIntTextField(final String parameter, final Parameters parameters,
            final int defaultValue, final double lowerBound, final double upperBound) {
        final IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 8);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    // Joe's old implementation with dropdown yes or no
    protected JComboBox getBooleanBox(final String parameter, final Parameters parameters, boolean defaultValue) {
        JComboBox<String> box = new JComboBox<>(new String[]{"Yes", "No"});

        boolean aBoolean = parameters.getBoolean(parameter, defaultValue);
        if (aBoolean) {
            box.setSelectedItem("Yes");
        } else {
            box.setSelectedItem("No");
        }

        box.addActionListener((e) -> {
            if (((JComboBox) e.getSource()).getSelectedItem().equals("Yes")) {
                parameters.set(parameter, true);
            } else {
                parameters.set(parameter, false);
            }
        });

        box.setMaximumSize(box.getPreferredSize());

        return box;
    }

    // Zhou's new implementation with yes/no radio buttons
    protected Box getBooleanSelectionBox(final String parameter, final Parameters parameters, boolean defaultValue) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        // Button group to ensure only only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);

        boolean aBoolean = parameters.getBoolean(parameter, defaultValue);

        // Set default selection
        if (aBoolean) {
            yesButton.setSelected(true);
        } else {
            noButton.setSelected(true);
        }

        // Add to containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, true);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, false);
            }
        });

        return selectionBox;
    }

    protected StringTextField getStringField(final String parameter, final Parameters parameters, String defaultValue) {
        final StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

        field.setFilter((value, oldValue) -> {
            if (value.equals(field.getValue().trim())) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

}
