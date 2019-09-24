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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.RfciBsc;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import java.awt.BorderLayout;
import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

    protected final JPanel mainPanel = new JPanel();

    public AlgorithmParameterPanel() {
        initComponents();
    }

    private void initComponents() {
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.NORTH);
    }

    public void addToPanel(GeneralAlgorithmRunner algorithmRunner) {
        mainPanel.removeAll();

        Algorithm algorithm = algorithmRunner.getAlgorithm();
        Parameters parameters = algorithmRunner.getParameters();

        // Hard-coded parameter groups for Rfci-Bsc
        if (algorithm instanceof RfciBsc) {
            // Phase one: PAG and constraints candidates Searching
            String title = algorithm
                    .getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
            Set<String> params = new LinkedHashSet<>();
            // RFCI
            params.add(Params.DEPTH);
            params.add(Params.MAX_PATH_LENGTH);
            params.add(Params.COMPLETE_RULE_SET_USED);
            params.add(Params.VERBOSE);
            mainPanel.add(createSubPanel(title, params, parameters));
            mainPanel.add(Box.createVerticalStrut(10));

            // Stage one: PAG and constraints candidates Searching
            title = "Stage One: PAG and constraints candidates Searching";
            params = new LinkedHashSet<>();
            // Thresholds
            params.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
            //params.add(Params.THRESHOLD_NO_RANDOM_DATA_SEARCH);
            //params.add(Params.CUTOFF_DATA_SEARCH);
            mainPanel.add(createSubPanel(title, params, parameters));
            mainPanel.add(Box.createVerticalStrut(10));

            // Stage two: Bayesian Scoring of Constraints
            title = "Stage Two: Bayesian Scoring of Constraints";
            params = new LinkedHashSet<>();
            params.add(Params.NUM_BSC_BOOTSTRAP_SAMPLES);
            params.add(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH);
            //params.add(Params.CUTOFF_CONSTRAIN_SEARCH);
            params.add(Params.LOWER_BOUND);
            params.add(Params.UPPER_BOUND);
            params.add(Params.OUTPUT_RBD);
            mainPanel.add(createSubPanel(title, params, parameters));
            mainPanel.add(Box.createVerticalStrut(10));

        } else {
            // add algorithm parameters
            Set<String> params = Params.getAlgorithmParameters(algorithm);

            if (!params.isEmpty()) {
                String title = algorithm
                        .getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
                mainPanel.add(createSubPanel(title, params, parameters));
                mainPanel.add(Box.createVerticalStrut(10));
            }

            params = Params.getScoreParameters(algorithm);
            if (!params.isEmpty()) {
                String title = ((UsesScoreWrapper) algorithm).getScoreWrapper()
                        .getClass().getAnnotation(Score.class).name();
                mainPanel.add(createSubPanel(title, params, parameters));
                mainPanel.add(Box.createVerticalStrut(10));
            }

            params = Params.getTestParameters(algorithm);
            if (!params.isEmpty()) {
                String title = ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper()
                        .getClass().getAnnotation(TestOfIndependence.class).name();
                mainPanel.add(createSubPanel(title, params, parameters));
                mainPanel.add(Box.createVerticalStrut(10));
            }

            if (algorithmRunner.getSourceGraph() == null) {
                params = Params.getBootstrappingParameters(algorithm);
                if (!params.isEmpty()) {
                    mainPanel.add(createSubPanel("Bootstrapping", params, parameters));
                    mainPanel.add(Box.createVerticalStrut(10));
                }
            }
        }

    }

    protected Box[] toArray(Map<String, Box> parameterComponents) {
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();

        List<Box> boolComps = new LinkedList<>();
        List<Box> otherComps = new LinkedList<>();
        parameterComponents.forEach((k, v) -> {
            if (paramDescs.get(k).getDefaultValue() instanceof Boolean) {
                boolComps.add(v);
            } else {
                otherComps.add(v);
            }
        });

        return Stream.concat(otherComps.stream(), boolComps.stream())
                .toArray(Box[]::new);
    }

    protected Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescs.get(e)),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    protected Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc) {
        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();
        if (defaultValue instanceof Double) {
            double lowerBoundDouble = paramDesc.getLowerBoundDouble();
            double upperBoundDouble = paramDesc.getUpperBoundDouble();
            component = getDoubleField(parameter, parameters, (Double) defaultValue, lowerBoundDouble, upperBoundDouble);
        } else if (defaultValue instanceof Integer) {
            int lowerBoundInt = paramDesc.getLowerBoundInt();
            int upperBoundInt = paramDesc.getUpperBoundInt();
            component = getIntTextField(parameter, parameters, (Integer) defaultValue, lowerBoundInt, upperBoundInt);
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
        } else if (defaultValue instanceof String) {
            component = getStringField(parameter, parameters, (String) defaultValue);
        } else {
            throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
        }

        Box paramRow = Box.createHorizontalBox();

        JLabel paramLabel = new JLabel(paramDesc.getShortDescription());
        String longDescription = paramDesc.getLongDescription();
        if (longDescription != null) {
            paramLabel.setToolTipText(longDescription);
        }
        paramRow.add(paramLabel);
        paramRow.add(Box.createHorizontalGlue());
        paramRow.add(component);

        return paramRow;
    }

    protected JPanel createSubPanel(String title, Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));

        Box paramsBox = Box.createVerticalBox();

        Box[] boxes = toArray(createParameterComponents(params, parameters));
        int lastIndex = boxes.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            paramsBox.add(boxes[i]);
            paramsBox.add(Box.createVerticalStrut(10));
        }
        paramsBox.add(boxes[lastIndex]);

        panel.add(new PaddingPanel(paramsBox), BorderLayout.CENTER);

        return panel;
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
