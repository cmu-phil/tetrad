///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.PagSamplingRfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.RfciBsc;
import edu.cmu.tetrad.algcomparison.utils.ParameterSettingsText;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
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
import edu.cmu.tetradapp.util.LongTextField;
import edu.cmu.tetradapp.util.ParameterFieldSync;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.Serial;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dec 4, 2017 5:05:42 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class AlgorithmParameterPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 274638263704283474L;

    /**
     * The main panel.
     */
    protected final JPanel mainPanel = new JPanel();

    /**
     * <p>Constructor for AlgorithmParameterPanel.</p>
     */
    public AlgorithmParameterPanel() {
        initComponents();
    }

    private void initComponents() {
        this.mainPanel.setLayout(new BoxLayout(this.mainPanel, BoxLayout.Y_AXIS));

        setLayout(new BorderLayout());
        add(this.mainPanel, BorderLayout.NORTH);
    }

    /**
     * <p>addToPanel.</p>
     *
     * @param algorithmRunner a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     */
    public void addToPanel(GeneralAlgorithmRunner algorithmRunner) {
        this.mainPanel.removeAll();

        Algorithm algorithm = algorithmRunner.getAlgorithm();
        Parameters parameters = algorithmRunner.getParameters();

        // A button that pops up a copyable plain-text rendering of the effective settings
        // shown on this panel, for pasting into emails, notes, or scripts.
        this.mainPanel.add(createSettingsTextRow(algorithmRunner));
        this.mainPanel.add(Box.createVerticalStrut(10));

        // Hard-coded parameter groups for Rfci-Bsc
        if (algorithm instanceof RfciBsc) {
            // Phase one: PAG and constraints candidates Searching
            String title = algorithm
                    .getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
            Set<String> params = new LinkedHashSet<>();
            // RFCI
            params.add(Params.DEPTH);
            params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
            params.add(Params.COMPLETE_RULE_SET_USED);
            params.add(Params.VERBOSE);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));

            // Stage one: PAG and constraints candidates Searching
            title = "Stage One: PAG and constraints candidates Searching";
            params = new LinkedHashSet<>();
            // Thresholds
            params.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));

            // Stage two: Bayesian Scoring of Constraints
            title = "Stage Two: Bayesian Scoring of Constraints";
            params = new LinkedHashSet<>();
            params.add(Params.NUM_BSC_BOOTSTRAP_SAMPLES);
            params.add(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH);
            //params.add(Params.CUTOFF_CONSTRAIN_SEARCH);
            params.add(Params.LOWER_BOUND);
            params.add(Params.UPPER_BOUND);
            params.add(Params.OUTPUT_RBD);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));

        } else if (algorithm instanceof PagSamplingRfci) {
            String title = algorithm.getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();

            Set<String> params = new LinkedHashSet<>();
            params.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
            params.add(Params.VERBOSE);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));

            title = "RFCI Parameters";
            params.clear();
            params.addAll(PagSamplingRfci.RFCI_PARAMETERS);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));

            title = "Probabilistic Test Parameters";
            params.clear();
            params.addAll(PagSamplingRfci.PROBABILISTIC_TEST_PARAMETERS);
            this.mainPanel.add(createSubPanel(title, params, parameters));
            this.mainPanel.add(Box.createVerticalStrut(10));
        } else {
            // add algorithm parameters
            Set<String> params = Params.getAlgorithmParameters(algorithm);

            if (!params.isEmpty()) {
                String title = algorithm
                        .getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
                this.mainPanel.add(createSubPanel(title, params, parameters));
                this.mainPanel.add(Box.createVerticalStrut(10));
            }

            params = Params.getScoreParameters(algorithm);
            if (!params.isEmpty()) {
                String title = ((TakesScoreWrapper) algorithm).getScoreWrapper()
                        .getClass().getAnnotation(Score.class).name();
                this.mainPanel.add(createSubPanel(title, params, parameters));
                this.mainPanel.add(Box.createVerticalStrut(10));
            }

            params = Params.getTestParameters(algorithm);
            if (!params.isEmpty()) {
                String title = ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper()
                        .getClass().getAnnotation(TestOfIndependence.class).name();
                this.mainPanel.add(createSubPanel(title, params, parameters));
                this.mainPanel.add(Box.createVerticalStrut(10));
            }

            // Changed 2026-8-13: bootstrapping parameters are shown for every algorithm that can actually
            // bootstrap (i.e., extends AbstractBootstrapAlgorithm - the same test Params.getBootstrappingParameters
            // uses), and every other case shows an explanatory label instead of silently showing nothing. The old
            // code additionally required the Bootstrapping annotation, so capable algorithms missing the annotation
            // lost the section without explanation; and when a source graph was supplied, the section vanished
            // silently as well.
            if (algorithmRunner.getSourceGraph() != null) {
                JLabel label = new JLabel("Bootstrapping is unavailable when a source graph is supplied.");
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBorder(BorderFactory.createTitledBorder("Bootstrapping"));
                panel.add(label, BorderLayout.WEST);
                this.mainPanel.add(panel);
            } else if (algorithm instanceof AbstractBootstrapAlgorithm) {
                params = Params.getBootstrappingParameters(algorithm);
                if (!params.isEmpty()) {
                    this.mainPanel.add(createSubPanel("Bootstrapping", params, parameters));
                    this.mainPanel.add(Box.createVerticalStrut(10));
                }
            } else {
                JLabel label = new JLabel("This algorithm is not configured to do bootstrapping.");
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBorder(BorderFactory.createTitledBorder("Bootstrapping"));
                panel.add(label, BorderLayout.WEST);
                this.mainPanel.add(panel);
            }
        }

    }

    /**
     * Creates the row holding the "Settings as Text..." button.
     */
    private JPanel createSettingsTextRow(GeneralAlgorithmRunner algorithmRunner) {
        JButton button = new JButton("Settings as Text...");
        button.setToolTipText("Show the settings on this panel as plain text that can be "
                + "selected and copied.");
        button.addActionListener(e -> showSettingsTextDialog(algorithmRunner));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(button);
        return row;
    }

    /**
     * Pops up a dialog containing the effective parameter settings as selectable text, with
     * a copy-to-clipboard option.
     */
    private void showSettingsTextDialog(GeneralAlgorithmRunner algorithmRunner) {
        String text = ParameterSettingsText.render(
                algorithmRunner.getAlgorithm(),
                algorithmRunner.getParameters(),
                algorithmRunner.getSourceGraph() != null);

        JTextArea area = new JTextArea(text, 25, 60);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);

        Object[] options = {"Copy to Clipboard", "Close"};
        int choice = JOptionPane.showOptionDialog(this, scroll, "Parameter Settings",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options,
                options[1]);

        if (choice == 0) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        }
    }

    /**
     * <p>toArray.</p>
     *
     * @param parameterComponents a {@link java.util.Map} object
     * @return an array of {@link javax.swing.Box} objects
     */
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

    /**
     * <p>createParameterComponents.</p>
     *
     * @param params     a {@link java.util.Set} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a {@link java.util.Map} object
     */
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

    /**
     * <p>createParameterComponent.</p>
     *
     * @param parameter  a {@link java.lang.String} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @param paramDesc  a {@link edu.cmu.tetrad.util.ParamDescription} object
     * @return a {@link javax.swing.Box} object
     */
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
        } else if (defaultValue instanceof Long) {
            long lowerBoundLong = paramDesc.getLowerBoundLong();
            long upperBoundLong = paramDesc.getUpperBoundLong();
            component = getLongTextField(parameter, parameters, (Long) defaultValue, lowerBoundLong, upperBoundLong);
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
        } else if (defaultValue instanceof String) {
            if (!paramDesc.getAllowedValues().isEmpty()) {
                component = getStringSelectionBox(parameter, parameters, (String) defaultValue,
                        paramDesc.getAllowedValues());
            } else {
                component = getStringField(parameter, parameters, (String) defaultValue);
            }
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

    /**
     * <p>createSubPanel.</p>
     *
     * @param title      a {@link java.lang.String} object
     * @param params     a {@link java.util.Set} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a {@link javax.swing.JPanel} object
     */
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

    /**
     * <p>getDoubleField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a double
     * @param lowerBound   a double
     * @param upperBound   a double
     * @return a {@link edu.cmu.tetradapp.util.DoubleTextField} object
     */
    protected DoubleTextField getDoubleField(String parameter, Parameters parameters,
                                             double defaultValue, double lowerBound, double upperBound) {
        DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
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
                ParameterFieldSync.valueChanged(parameters, parameter, field);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        ParameterFieldSync.register(parameters, parameter, field,
                () -> field.setValue(parameters.getDouble(parameter, defaultValue)));

        return field;
    }

    /**
     * <p>getIntTextField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a int
     * @param lowerBound   a double
     * @param upperBound   a double
     * @return a {@link edu.cmu.tetradapp.util.IntTextField} object
     */
    protected IntTextField getIntTextField(String parameter, Parameters parameters,
                                           int defaultValue, double lowerBound, double upperBound) {
        IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 8);

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
                ParameterFieldSync.valueChanged(parameters, parameter, field);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        ParameterFieldSync.register(parameters, parameter, field,
                () -> field.setValue(parameters.getInt(parameter, defaultValue)));

        return field;
    }

    /**
     * <p>getLongTextField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a long
     * @param lowerBound   a long
     * @param upperBound   a long
     * @return a {@link edu.cmu.tetradapp.util.LongTextField} object
     */
    protected LongTextField getLongTextField(String parameter, Parameters parameters,
                                             long defaultValue, long lowerBound, long upperBound) {
        LongTextField field = new LongTextField(parameters.getLong(parameter, defaultValue), 8);

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
                ParameterFieldSync.valueChanged(parameters, parameter, field);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        ParameterFieldSync.register(parameters, parameter, field,
                () -> field.setValue(parameters.getLong(parameter, defaultValue)));

        return field;
    }

    // Zhou's new implementation with yes/no radio buttons

    /**
     * <p>getBooleanSelectionBox.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a boolean
     * @return a {@link javax.swing.Box} object
     */
    protected Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean defaultValue) {
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
                ParameterFieldSync.valueChanged(parameters, parameter, selectionBox);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, false);
                ParameterFieldSync.valueChanged(parameters, parameter, selectionBox);
            }
        });

        ParameterFieldSync.register(parameters, parameter, selectionBox, () -> {
            boolean b = parameters.getBoolean(parameter, defaultValue);
            if (b && !yesButton.isSelected()) {
                yesButton.setSelected(true);
            } else if (!b && !noButton.isSelected()) {
                noButton.setSelected(true);
            }
        });

        return selectionBox;
    }

    /**
     * <p>getStringField.</p>
     *
     * @param parameter    a {@link java.lang.String} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     * @param defaultValue a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetradapp.util.StringTextField} object
     */
    /**
     * A dropdown for an enumerated String parameter (one whose {@link ParamDescription} declares a fixed list of
     * allowed values), replacing the free-text field so that illegal values are impossible rather than discovered as
     * exceptions at search time.
     *
     * @param parameter     The parameter name.
     * @param parameters    The parameters object to read from and write to.
     * @param defaultValue  The default value.
     * @param allowedValues The legal values, in display order.
     * @return The combo box.
     */
    protected JComboBox<String> getStringSelectionBox(String parameter, Parameters parameters, String defaultValue,
                                                      java.util.List<String> allowedValues) {
        JComboBox<String> comboBox = new JComboBox<>(allowedValues.toArray(new String[0]));

        String current = parameters.getString(parameter, defaultValue);
        int index = -1;

        for (int i = 0; i < allowedValues.size(); i++) {
            if (allowedValues.get(i).equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            comboBox.setSelectedIndex(index);
        } else {
            comboBox.setSelectedItem(defaultValue);
            parameters.set(parameter, defaultValue);
        }

        comboBox.addActionListener(e -> {
            Object selected = comboBox.getSelectedItem();
            if (selected != null) {
                parameters.set(parameter, selected.toString());
                ParameterFieldSync.valueChanged(parameters, parameter, comboBox);
            }
        });

        ParameterFieldSync.register(parameters, parameter, comboBox, () -> {
            String value = parameters.getString(parameter, defaultValue);
            Object selected = comboBox.getSelectedItem();
            if (selected == null || !selected.toString().equalsIgnoreCase(value)) {
                for (int i = 0; i < comboBox.getItemCount(); i++) {
                    if (comboBox.getItemAt(i).equalsIgnoreCase(value)) {
                        comboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });

        comboBox.setMaximumSize(comboBox.getPreferredSize());

        return comboBox;
    }

    protected StringTextField getStringField(String parameter, Parameters parameters, String defaultValue) {
        StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

        field.setFilter((value, oldValue) -> {
            if (value.equals(field.getValue().trim())) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
                ParameterFieldSync.valueChanged(parameters, parameter, field);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        ParameterFieldSync.register(parameters, parameter, field,
                () -> field.setValue(parameters.getString(parameter, defaultValue)));

        return field;
    }
}

