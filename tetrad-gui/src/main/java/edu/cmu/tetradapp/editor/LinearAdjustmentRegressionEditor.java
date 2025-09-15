/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.LinearAdjustmentRegressionModel;
import edu.cmu.tetradapp.model.MarkovBlanketSearchRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Editor + param editor for markov blanket searches.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class LinearAdjustmentRegressionEditor extends JPanel implements GraphEditable, IndTestTypeSetter {
    /**
     * JLabel representing a message indicating that there are no parameters to edit.
     */
    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    /**
     * The algorithm wrapper being viewed.
     */
    private final LinearAdjustmentRegressionModel model;
    /**
     * The JComboBox for the adjustment sets.
     */
    private final JComboBox<Set<Node>> adjustmentSetBox;
    /**
     * Represents whether a node selection has changed.
     */
    boolean changed = false;
    /**
     * Represents a message.
     */
    private String message = null;
    /**
     * The set of nodes to adjust for.
     */
    private Set<Node> adjustment;
    /**
     * The nodes to show paths from.
     */
    private Node source;
    /**
     * The nodes to show paths to.
     */
    private Node target;
    /**
     * The text area for the paths.
     */
    private JTextArea textArea;

    /**
     * Constructs the eidtor.
     *
     * @param model a {@link MarkovBlanketSearchRunner} object
     */
    public LinearAdjustmentRegressionEditor(LinearAdjustmentRegressionModel model) {
        if (model == null) {
            throw new NullPointerException();
        }
        this.model = model;
        Parameters params = model.getParameters();
        List<Node> vars = model.getVariables();

        Graph graph = model.getGraph();

        this.textArea = new JTextArea();

        Font monospacedFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        textArea.setFont(monospacedFont);

        JScrollPane scroll = new JScrollPane(this.textArea);
//        scroll.setPreferredSize(new Dimension(600, 400));

        List<Node> allNodes = graph.getNodes();
        allNodes.sort(Comparator.naturalOrder());
        Node[] array = allNodes.toArray(new Node[0]);

        JComboBox<Node> node1Box = new JComboBox<>(array);

        node1Box.addActionListener(e1 -> {
            JComboBox<Node> box = (JComboBox) e1.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            this.source = node;
            this.changed = true;
            update();

            Preferences.userRoot().put("pathFrom", node.getName());
        });

        node1Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node1Box.getSelectedItem() == null) {
            node1Box.setSelectedItem(node1Box.getItemAt(0));
        }
        source = (Node) node1Box.getSelectedItem();

        JComboBox<Node> node2Box = new JComboBox<>(array);

        node2Box.addActionListener(e12 -> {
            JComboBox<Node> box = (JComboBox) e12.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            this.target = node;
            this.changed = true;
            update();
        });

        node2Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node2Box.getSelectedItem() == null) {
            node2Box.setSelectedItem(node1Box.getItemAt(0));
        }
        target = (Node) node2Box.getSelectedItem();

        List<Set<Node>> adjustmentSets = new ArrayList<>();
        try {
            adjustmentSets = model.getAdjustmentSets(this.source, target);
            message = null;
        } catch (Exception e) {
            this.message = e.getMessage();
        }
        Set<Node>[] array1 = adjustmentSets.toArray(new Set[0]);

        adjustmentSetBox = new JComboBox<>(array1);

        adjustmentSetBox.addActionListener(e12 -> {
            JComboBox<Set<Node>> box = (JComboBox) e12.getSource();
            this.adjustment = (Set<Node>) box.getSelectedItem();
            update();
        });

//        adjustmentSetBox.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
//        if (node2Box.getSelectedItem() == null) {
//            node2Box.setSelectedItem(node1Box.getItemAt(0));
//        }
//        nodes2 = Collections.singletonList((Node) node2Box.getSelectedItem());

        JButton editParameters = new JButton("Edit Parameters");

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Source"));
        b1.add(node1Box);
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("Target"));
        b1.add(node2Box);
        b1.add(new JLabel("Adjustment"));
        b1.add(adjustmentSetBox);
        b1.add(editParameters);

//        b1.add(new JLabel("Max length"));
//        b1.add(maxField);

        b1.setMaximumSize(new Dimension(1000, 25));

        b.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b1);

        scroll.setPreferredSize(new Dimension(700, 400));

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        this.textArea.setCaretPosition(0);
        b2.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b2);

        setLayout(new BorderLayout());
        add(b);

        editParameters.addActionListener(e2 -> {
            Set<String> _params = new HashSet<>();
//            _params.add("pathsMaxLength");
            _params.add("pathsMaxNumSets");
            _params.add("pathsMaxDistanceFromEndpoint");
            _params.add("pathsNearWhichEndpoint");
            _params.add("pathsMaxLengthAdjustment");

            Box parameterBox = getParameterBox(_params, false, false, this.model.getParameters());
            new PaddingPanel(parameterBox);

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

//            // Create a panel for the buttons
            JPanel buttonPanel = betButtonPanel(dialog, graph);
//
//            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(this); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });

    }

    /**
     * Creates a map of parameter components for the given set of parameters and a Parameters object.
     *
     * @param params            the set of parameter names
     * @param parameters        the Parameters object containing the parameter values
     * @param listOptionAllowed whether the option allows one to select a list of values
     * @param bothOptionAllowed whether the option allows one to select both true and false
     * @return a map of parameter names to corresponding Box components
     */
    public static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters,
                                                             boolean listOptionAllowed, boolean bothOptionAllowed) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescriptions.get(e), listOptionAllowed, bothOptionAllowed),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    /**
     * Creates a component for a specific parameter based on its type and default value.
     *
     * @param parameter  the name of the parameter
     * @param parameters the Parameters object containing the parameter values
     * @param paramDesc  the ParamDescription object containing information about the parameter
     * @return a Box component representing the parameter
     */
    private static Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc,
                                                boolean listOptionAllowed, boolean bothOptionAllowed) {
        JComponent component;
        Object defaultValue = parameters.get(parameter);

        Object[] defaultValues = parameters.getValues(parameter);

        if (defaultValue instanceof Double) {
            double lowerBoundDouble = paramDesc.getLowerBoundDouble();
            double upperBoundDouble = paramDesc.getUpperBoundDouble();
            Double[] defValues = new Double[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Double) defaultValues[i];
            }

            if (listOptionAllowed) {
                component = getListDoubleTextField(parameter, parameters, defValues, lowerBoundDouble, upperBoundDouble);
            } else {
                component = getDoubleTextField(parameter, parameters, (Double) defaultValue, lowerBoundDouble, upperBoundDouble);
            }
        } else if (defaultValue instanceof Integer) {
            int lowerBoundInt = paramDesc.getLowerBoundInt();
            int upperBoundInt = paramDesc.getUpperBoundInt();
            Integer[] defValues = new Integer[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Integer) defaultValues[i];
            }

            if (listOptionAllowed) {
                component = getListIntTextField(parameter, parameters, defValues, lowerBoundInt, upperBoundInt);
            } else {
                component = getIntTextField(parameter, parameters, (Integer) defaultValue, lowerBoundInt, upperBoundInt);
            }
        } else if (defaultValue instanceof Long) {
            long lowerBoundLong = paramDesc.getLowerBoundLong();
            long upperBoundLong = paramDesc.getUpperBoundLong();
            Long[] defValues = new Long[defaultValues.length];
            for (int i = 0; i < defaultValues.length; i++) {
                defValues[i] = (Long) defaultValues[i];
            }
            if (listOptionAllowed) {
                component = getListLongTextField(parameter, parameters, defValues, lowerBoundLong, upperBoundLong);
            } else {
                component = getLongTextField(parameter, parameters, (Long) defaultValue, lowerBoundLong, upperBoundLong);
            }
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, bothOptionAllowed);
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

    /**
     * Returns a customized DoubleTextField with specified parameters.
     *
     * @param parameter    the name of the parameter to be set in the Parameters object
     * @param parameters   the Parameters object to store the parameter values
     * @param defaultValue the default value to set in the DoubleTextField
     * @param lowerBound   the lowerbound limit for valid input values in the DoubleTextField
     * @param upperBound   the upperbound limit for valid input values in the DoubleTextField
     * @return a DoubleTextField with the specified parameters
     */
    public static DoubleTextField getDoubleTextField(String parameter, Parameters parameters,
                                                     double defaultValue, double lowerBound, double upperBound) {
        DoubleTextField field = new DoubleTextField(defaultValue,
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((value, oldValues) -> {
            if (Double.isNaN(value)) {
                return oldValues;
            }

            if (value < lowerBound) {
                return oldValues;
            }

            if (value > upperBound) {
                return oldValues;
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

    /**
     * Creates a ListDoubleTextField component with the given parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListDoubleTextField component with the specified parameters
     */
    public static ListDoubleTextField getListDoubleTextField(String parameter, Parameters parameters,
                                                             Double[] defaultValues, double lowerBound, double upperBound) {
        ListDoubleTextField field = new ListDoubleTextField(defaultValues,
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Double> valuesList = new ArrayList<>();

            for (Double value : values) {
                if (Double.isNaN(value)) {
                    continue;
                }

                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Double[] newValues = valuesList.toArray(new Double[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

    /**
     * Returns an IntTextField with the specified parameters.
     *
     * @param parameter    the name of the parameter
     * @param parameters   the Parameters object to update with the new value
     * @param defaultValue the default value for the IntTextField
     * @param lowerBound   the lower bound for valid values
     * @param upperBound   the upper bound for valid values
     * @return an IntTextField with the specified parameters
     */
    public static IntTextField getIntTextField(String parameter, Parameters parameters,
                                               int defaultValue, double lowerBound, double upperBound) {
        IntTextField field = new IntTextField(defaultValue, 8);

        field.setFilter((value, oldValue) -> {
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

    /**
     * Returns a ListIntTextField component with the specified parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListIntTextField component with the specified parameters
     */
    public static ListIntTextField getListIntTextField(String parameter, Parameters parameters,
                                                       Integer[] defaultValues, double lowerBound, double upperBound) {
        ListIntTextField field = new ListIntTextField(defaultValues, 8);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Integer> valuesList = new ArrayList<>();

            for (Integer value : values) {
                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Integer[] newValues = valuesList.toArray(new Integer[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

    /**
     * Returns a LongTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter to set in the Parameters object.
     * @param parameters   The Parameters object to set the parameter in.
     * @param defaultValue The default value to use for the LongTextField.
     * @param lowerBound   The lower bound for the LongTextField value.
     * @param upperBound   The upper bound for the LongTextField value.
     * @return A LongTextField object with the specified parameters.
     */
    public static LongTextField getLongTextField(String parameter, Parameters parameters,
                                                 long defaultValue, long lowerBound, long upperBound) {
        LongTextField field = new LongTextField(defaultValue, 8);

        field.setFilter((value, oldValue) -> {
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

    /**
     * Returns a ListLongTextField component with the specified parameters.
     *
     * @param parameter     the name of the parameter
     * @param parameters    the Parameters object containing the parameter values
     * @param defaultValues the default values for the component
     * @param lowerBound    the lower bound for the values
     * @param upperBound    the upper bound for the values
     * @return a ListLongTextField component with the specified parameters
     */
    public static ListLongTextField getListLongTextField(String parameter, Parameters parameters,
                                                         Long[] defaultValues, long lowerBound, long upperBound) {
        ListLongTextField field = new ListLongTextField(defaultValues, 8);

        field.setFilter((values, oldValues) -> {
            if (values.length == 0) {
                return oldValues;
            }

            List<Long> valuesList = new ArrayList<>();

            for (Long value : values) {
                if (value < lowerBound) {
                    continue;
                }

                if (value > upperBound) {
                    continue;
                }

                valuesList.add(value);
            }

            if (valuesList.isEmpty()) {
                return oldValues;
            }

            Long[] newValues = valuesList.toArray(new Long[0]);

            try {
                parameters.set(parameter, (Object[]) newValues);
            } catch (Exception e) {
                // Ignore.
            }

            return newValues;
        });

        return field;
    }

    /**
     * Creates a StringTextField component with the specified parameters.
     *
     * @param parameter    the name of the parameter
     * @param parameters   the Parameters object containing the parameter values
     * @param defaultValue the default value for the component
     * @return a StringTextField component with the specified parameters
     */
    public static StringTextField getStringField(String parameter, Parameters parameters, String defaultValue) {
        StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

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

    /**
     * Returns a Box component representing a boolean selection box.
     *
     * @param parameter         the name of the parameter
     * @param parameters        the Parameters object containing the parameter values
     * @param bothOptionAllowed whether the option allows one to select both true and false
     * @return a Box component representing the boolean selection box
     */
    public static Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean bothOptionAllowed) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        JRadioButton bothButton = null;

        if (bothOptionAllowed) {
            bothButton = new JRadioButton("Both");
        }

        // Button group to ensure only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);

        if (bothOptionAllowed) {
            selectionBtnGrp.add(bothButton);
        }

        Object[] values = parameters.getValues(parameter);
        Boolean[] booleans = new Boolean[values.length];

        try {
            for (int i = 0; i < values.length; i++) {
                booleans[i] = (Boolean) values[i];
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Set default selection
        if (booleans.length == 1 && booleans[0]) {
            yesButton.setSelected(true);
        } else if (booleans.length == 1) {
            noButton.setSelected(true);
        } else if (booleans.length == 2 && bothOptionAllowed) {
            bothButton.setSelected(true);
        }

        // Add to containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        if (bothOptionAllowed) {
            selectionBox.add(bothButton);
        }

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                Object[] objects = new Object[1];
                objects[0] = Boolean.TRUE;
                parameters.set(parameter, objects);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                Object[] objects = new Object[1];
                objects[0] = Boolean.FALSE;
                parameters.set(parameter, objects);
            }
        });

        if (bothOptionAllowed) {
            bothButton.addActionListener((e) -> {
                JRadioButton button = (JRadioButton) e.getSource();
                if (button.isSelected()) {
                    Object[] objects = new Object[2];
                    objects[0] = Boolean.TRUE;
                    objects[1] = Boolean.FALSE;
                    parameters.set(parameter, objects);
                }
            });
        }

        return selectionBox;
    }

    // Need to update the contents of the adjustment JComboBox with the new adjustment sets when the nodes selections
    // are changed.
    private void update() {

        if (changed) {
            try {
                List<Set<Node>> adjustments = model.getAdjustmentSets(source, target);

                SwingUtilities.invokeLater(() -> {
                    adjustmentSetBox.removeAllItems();
                    for (Set<Node> adjustment : adjustments) {
                        adjustmentSetBox.addItem(adjustment);
                    }

                    if (!adjustments.isEmpty()) {
                        adjustmentSetBox.setSelectedItem(adjustments.get(0));
                    }

                    changed = false;
                });

            } catch (IllegalArgumentException e) {
                textArea.setText("\n\n" + e.getMessage());
                changed = false;
                adjustment = null;
            }
        }

        if (adjustment == null) {
            if (message != null) {
                textArea.setText("\n\n" + message);
            } else {
                textArea.setText("\n\nNo adjustment set available by that description; perhaps adjust the parameters.");
            }

            return;
        }

        // Need to update the text area with a regression result for the new adjustment set, which we can obtain
        // from the model.
        double totalEffect = model.totalEffect(source, target, adjustment);
        String regressionString = model.getRegressionString(source, target, adjustment);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        textArea.setText("\nProblem: " + source + " ~~> " + target + " with adjustment set " + adjustment);
        textArea.append("\n\nTotal effect: " + nf.format(totalEffect));
        textArea.append("\n" + regressionString);

    }

    @NotNull
    private Box getParameterBox(Set<String> params, boolean listOptionAllowed, boolean bothOptionAllowed, Parameters _parameters) {
        Box parameterBox = Box.createVerticalBox();
        parameterBox.removeAll();

        if (params.isEmpty()) {
            JLabel noParamLbl = NO_PARAM_LBL;
            noParamLbl.setBorder(new EmptyBorder(10, 10, 10, 10));
            parameterBox.add(noParamLbl, BorderLayout.NORTH);
        } else {
            Box parameters = Box.createVerticalBox();
            Box[] paramBoxes = ParameterComponents.toArray(
                    createParameterComponents(params, _parameters, listOptionAllowed, false));
            int lastIndex = paramBoxes.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                parameters.add(paramBoxes[i]);
                parameters.add(Box.createVerticalStrut(10));
            }
            parameters.add(paramBoxes[lastIndex]);

            Box horiz = Box.createHorizontalBox();

            if (listOptionAllowed) {
                horiz.add(new JLabel("Please type comma-separated lists of values, thus: 10, 100, 1000"));
            } else {
                horiz.add(new JLabel("Please type a single value."));
            }

            horiz.add(Box.createHorizontalGlue());
            horiz.setBorder(new EmptyBorder(0, 0, 10, 0));
            parameterBox.add(horiz, BorderLayout.NORTH);
            parameterBox.add(new JScrollPane(new PaddingPanel(parameters)), BorderLayout.CENTER);
            parameterBox.setBorder(new EmptyBorder(10, 10, 10, 10));
            parameterBox.setPreferredSize(new Dimension(800, 400));
        }
        return parameterBox;
    }

    @NotNull
    private JPanel betButtonPanel(JDialog dialog, Graph graph) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton doneButton = new JButton("Done");

        doneButton.addActionListener(e1 -> {
            dialog.dispose();
        });

        buttonPanel.add(doneButton);
        return buttonPanel;
    }

    @Override
    public List getSelectedModelComponents() {
        return List.of();
    }

    @Override
    public void pasteSubsession(List<Object> sessionElements, Point upperLeft) {

    }

    @Override
    public GraphWorkbench getWorkbench() {
        return null;
    }

    @Override
    public Graph getGraph() {
        return null;
    }

    @Override
    public void setGraph(Graph graph) {

    }

    @Override
    public IndTestType getTestType() {
        return null;
    }

    @Override
    public void setTestType(IndTestType testType) {

    }

    @Override
    public DataModel getDataModel() {
        return null;
    }

    @Override
    public Object getSourceGraph() {
        return null;
    }
}




