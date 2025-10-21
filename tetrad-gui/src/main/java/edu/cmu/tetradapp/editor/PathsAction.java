/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.RecursiveAdjustment;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Represents an action that performs calculations on paths in a graph.
 */
public class PathsAction extends AbstractAction implements ClipboardOwner {

    /**
     * JLabel representing a message indicating that there are no parameters to edit.
     */
    private static final JLabel NO_PARAM_LBL = new JLabel("No parameters to edit");
    /**
     * The workbench.
     */
    private final GraphWorkbench workbench;
    /**
     * The parameters.
     */
    private final Parameters parameters;
    /**
     * The nodes to show paths from.
     */
    private List<Node> nodes1;
    /**
     * The nodes to show paths to.
     */
    private List<Node> nodes2;
    /**
     * The text area for the paths.
     */
    private JTextArea textArea;
    /**
     * The method for showing paths.
     */
    private String method;
    /**
     * The conditioning set.
     */
    private Set<Node> conditioningSet = new HashSet<>();

    /**
     * Represents an action that performs calculations on paths in a graph.
     *
     * @param workbench  the workbench
     * @param parameters the parameters
     */
    public PathsAction(GraphWorkbench workbench, Parameters parameters) {
        super("Paths");
        this.workbench = workbench;
        this.parameters = parameters;
    }

    /**
     * Creates a map of parameter components for the given set of parameters and a Parameters object.
     *
     * @param params            the set of parameter names
     * @param parameters        the Parameters object containing the parameter values
     * @param listOptionAllowed whether the option allows for a list of values
     * @param bothOptionAllowed whether the option allows for both true and false to be selected
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
     * Creates a ListLongTextField with the specified parameters.
     *
     * @param parameter     The parameter name to be set in the Parameters object.
     * @param parameters    The Parameters object to set the parameter value.
     * @param defaultValues The default values for the ListLongTextField.
     * @param lowerBound    The lower bound for valid values.
     * @param upperBound    The upper bound for valid values.
     * @return The created ListLongTextField.
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

    // Build a mask that tells RB "never add these to Z" (treat as latent for this run).
    static Set<Node> buildLatentMaskForTotalEffect(Graph graph,
                                                   Node X, Node Y,
                                                   Collection<List<Node>> amenablePaths,
                                                   Set<Node> containing,            // seed Z (may be empty)
                                                   boolean includeDescendantsOfX) { // Tier-B toggle
        Set<Node> mask = new HashSet<>();

        // --- Tier A: interior non-colliders along amenable X→Y paths ---
        for (List<Node> p : amenablePaths) {
            if (p.size() < 3) continue; // no interior nodes on length-2 path X–Y
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), b = p.get(i), c = p.get(i + 1);
                // Mask ONLY if b is a definite non-collider on this triple
                if (!graph.isDefCollider(a, b, c)) {
                    mask.add(b);
                }
            }
        }

        // --- Tier B (optional): Descendants of X (measured only) ---
        if (includeDescendantsOfX) {
            Map<Node, Set<Node>> descMap = graph.paths().getDescendantsMap(); // recompute per run
            Set<Node> descX = descMap.getOrDefault(X, Collections.emptySet());
            for (Node d : descX) {
                if (d != null && d.getNodeType() == NodeType.MEASURED) {
                    mask.add(d);
                }
            }
        }

        // --- Sanitize: remove endpoints, latents, and anything the user forced into Z ---
        mask.remove(X);
        mask.remove(Y);
        if (containing != null) {
            mask.removeAll(containing);
        }
        // Keep mask to measured nodes only; true latents are already handled by RB
        mask.removeIf(n -> n == null || n.getNodeType() != NodeType.MEASURED);

        return mask;
    }

    /**
     * Performs the action when an event occurs.
     *
     * @param e The action event.
     */
    public void actionPerformed(ActionEvent e) {
        Graph graph = this.workbench.getGraph();

        this.textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(this.textArea);
//        scroll.setPreferredSize(new Dimension(600, 400));

        List<Node> allNodes = graph.getNodes();
        allNodes.sort(Comparator.naturalOrder());
        allNodes.add(new GraphNode("SELECT_ALL"));
        Node[] array = allNodes.toArray(new Node[0]);

        JComboBox<Node> node1Box = new JComboBox<>(array);

        node1Box.addActionListener(e1 -> {
            JComboBox<Node> box = (JComboBox) e1.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes1 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes1 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathFrom", node.getName());

            update(graph, textArea, nodes1, nodes2, method);
        });

        node1Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node1Box.getSelectedItem() == null) {
            node1Box.setSelectedItem(node1Box.getItemAt(0));
        }
        nodes1 = Collections.singletonList((Node) node1Box.getSelectedItem());

        JComboBox<Node> node2Box = new JComboBox<>(array);

        node2Box.addActionListener(e12 -> {
            JComboBox<Node> box = (JComboBox) e12.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes2 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes2 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathMethod", PathsAction.this.method);

            update(graph, textArea, nodes1, nodes2, method);
        });

        node2Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node2Box.getSelectedItem() == null) {
            node2Box.setSelectedItem(node1Box.getItemAt(0));
        }
        nodes2 = Collections.singletonList((Node) node2Box.getSelectedItem());

        JComboBox<String> methodBox = new JComboBox<>(new String[]{
                "Directed Paths",
                "Semidirected Paths",
                "Treks",
                "Confounder Paths",
                "Latent Confounder Paths",
                "Cycles",
                "All Paths",
                "Adjacents",
                "Adjustment Sets",
                "Recursive Blocking Sets",
                "Recursive Adjustment Sets",
                "Amenable paths",
                "Backdoor paths"
        });

        methodBox.setSelectedItem(Preferences.userRoot().get("pathMethod", null));
        if (methodBox.getSelectedItem() == null) {
            methodBox.setSelectedItem(node1Box.getItemAt(0));
        }
        method = (String) methodBox.getSelectedItem();

        methodBox.addActionListener(e13 -> {
            JComboBox<String> box = (JComboBox) e13.getSource();
            PathsAction.this.method = (String) box.getSelectedItem();
            Preferences.userRoot().put("pathMethod", PathsAction.this.method);
            update(graph, textArea, nodes1, nodes2, method);
        });

        methodBox.setSelectedItem(this.method);

        JButton editParameters = new JButton("Edit Parameters");

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("From "));
        b1.add(node1Box);
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(" To "));
        b1.add(node2Box);
        b1.add(Box.createHorizontalGlue());
        b1.add(methodBox);
        b1.add(editParameters);

//        b1.add(new JLabel("Max length"));
//        b1.add(maxField);

        b1.setMaximumSize(new Dimension(1000, 25));

        b.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b1);

        EditorUtils.JTextFieldWithPrompt comp = new EditorUtils.JTextFieldWithPrompt("Enter conditioning variables...");
        comp.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(1, 3, 1, 3)));
        comp.setPreferredSize(new Dimension(750, 20));
        comp.setMaximumSize(new Dimension(1000, 20));

        comp.addActionListener(e16 -> {
            String text = comp.getText();
            String[] parts = text.split("[\\s,\\[\\]]");

            Set<Node> conditioningSet = new HashSet<>();

            for (String part : parts) {
                Node node = graph.getNode(part);

                if (node != null) {
                    conditioningSet.add(node);
                }
            }

            PathsAction.this.conditioningSet = conditioningSet;
            update(graph, textArea, nodes1, nodes2, method);
        });


        Box b1a = Box.createHorizontalBox();
        b1a.add(new JLabel("Condition on:"));
        b1a.add(comp);
        b1a.setBorder(new EmptyBorder(2, 3, 2, 2));
        b1a.add(Box.createHorizontalGlue());

        b1a.setMaximumSize(new Dimension(1000, 25));

        b.add(b1a);

        scroll.setPreferredSize(new Dimension(700, 400));

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        this.textArea.setCaretPosition(0);
        b2.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b2);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b);

        EditorWindow window = new EditorWindow(panel,
                "Paths", null, false, this.workbench);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

//        update(graph, this.textArea, this.nodes1, this.nodes2, this.method);

        editParameters.addActionListener(e2 -> {
            Set<String> params = new HashSet<>();
            params.add("pathsMaxLength");
            params.add("pathsMaxNumSets");
            params.add("pathsMaxDistanceFromEndpoint");
            params.add("pathsNearWhichEndpoint");
            params.add("pathsMaxLengthAdjustment");

            Box parameterBox = getParameterBox(params, false, false, parameters);
            new PaddingPanel(parameterBox);

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(window), "Edit Parameters", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            // Add your panel to the center of the dialog
            dialog.add(parameterBox, BorderLayout.CENTER);

//            // Create a panel for the buttons
            JPanel buttonPanel = betButtonPanel(dialog, graph);
//
//            // Add the button panel to the bottom of the dialog
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.pack(); // Adjust dialog size to fit its contents
            dialog.setLocationRelativeTo(window); // Center dialog relative to the parent component
            dialog.setVisible(true);
        });
    }

    @NotNull
    private JPanel betButtonPanel(JDialog dialog, Graph graph) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton doneButton = new JButton("Done");

        doneButton.addActionListener(e1 -> {
            dialog.dispose();
            update(graph, textArea, nodes1, nodes2, method);
        });

        buttonPanel.add(doneButton);
        return buttonPanel;
    }

    /**
     * Updates the text area based on the selected method.
     *
     * @param graph    The graph object.
     * @param textArea The text area object.
     * @param nodes1   The first list of nodes.
     * @param nodes2   The second list of nodes.
     * @param method   The selected method.
     * @throws IllegalArgumentException If the method is unknown.
     */
    private void update(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2, String method) {
        new WatchedProcess() {
            @Override
            public void watch() {
                if ("Directed Paths".equals(method)) {
                    allDirectedPaths(graph, textArea, nodes1, nodes2);
                } else if ("Semidirected Paths".equals(method)) {
                    allSemidirectedPaths(graph, textArea, nodes1, nodes2);
                } else if ("Amenable paths".equals(method)) {
                    allAmenablePathsMpdagMag(graph, textArea, nodes1, nodes2);
                } else if ("Backdoor paths".equals(method)) {
                    allBackdoorPaths(graph, textArea, nodes1, nodes2);
                } else if ("All Paths".equals(method)) {
                    allPaths(graph, textArea, nodes1, nodes2);
                } else if ("Treks".equals(method)) {
                    allTreks(graph, textArea, nodes1, nodes2);
                } else if ("Confounder Paths".equals(method)) {
                    confounderPaths(graph, textArea, nodes1, nodes2);
                } else if ("Latent Confounder Paths".equals(method)) {
                    latentConfounderPaths(graph, textArea, nodes1, nodes2);
                } else if ("Adjacents".equals(method)) {
                    adjacentNodes(graph, textArea, nodes1, nodes2);
                } else if ("Adjustment Sets".equals(method)) {
                    adjustmentSets(graph, textArea, nodes1, nodes2);
                } else if ("Recursive Blocking Sets".equals(method)) {
                    recursiveBlockingSets(graph, textArea, nodes1, nodes2);
                } else if ("Recursive Adjustment Sets".equals(method)) {
                    recursiveAdjustmentSets(graph, textArea, nodes1, nodes2);
                } else if ("Cycles".equals(method)) {
                    allCyclicPaths(graph, textArea, nodes1, nodes2);
                } else {
                    throw new IllegalArgumentException("Unknown method: " + method);
                }

                textArea.setCaretPosition(0);
            }
        };
    }

    private void addConditionNote(JTextArea textArea) {
        String conditioningSymbol = "\u2714";
        textArea.append("\n" + conditioningSymbol + " indicates that the marked variable is in the conditioning set; (L) that L is latent.");
    }

    /**
     * Appends all directed paths from nodes in list nodes1 to nodes in list nodes2 to a given text area.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allDirectedPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are causal paths--i.e. paths that are directed from X to Y, of the form X ~~> Y.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> paths = graph.paths().directedPaths(node1, node2,
                        parameters.getInt("pathsMaxLength"));

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, paths);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo directed paths found.");
        }
    }

    /**
     * Appends all directed paths from nodes in list nodes1 to nodes in list nodes2 to a given text area.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allCyclicPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are nodes in cyclic paths--i.e. paths that are directed from X to X, of the form X ~~> X. Note
                that only the nodes selected in the From box above are considered.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            List<List<Node>> paths = graph.paths().directedPaths(node1, node1,
                    parameters.getInt("pathsMaxLength"));

            if (paths.isEmpty()) {
                continue;
            } else {
                pathListed = true;
            }

            textArea.append("\n\nBetween " + node1 + " and " + node1 + ":");
            listPaths(graph, textArea, paths);
        }

        if (!pathListed) {
            textArea.append("\n\nNo directed paths found.");
        }
    }

    /**
     * Appends all semidirected paths from nodes in list nodes1 to nodes in list nodes2 to the given text area. A
     * semidirected path is a path that, with additional knowledge, could be causal from source to target.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allSemidirectedPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are paths that with additional knowledge could be causal from source to target.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> paths = graph.paths().semidirectedPaths(node1, node2,
                        parameters.getInt("pathsMaxLength"));

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                listPaths(graph, textArea, paths);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo semidirected paths found.");
        }
    }

    /**
     * Appends all amenable paths from nodes in the first list to nodes in the second list to the given text area. An
     * amenable path starts with a directed edge out of the starting node and does not block any of these paths.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allAmenablePathsMpdagMag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are semidirected paths from X to Y that start with a directed edge out of X. An 
                adjustment set should not block any of these paths.
                """);

        addConditionNote(textArea);

        boolean mpdag = false;
        boolean mag = false;
        boolean pag = false;

        if (graph.paths().isLegalMpdag()) {
            mpdag = true;
        } else if (graph.paths().isLegalMag()) {
            mag = true;
        } else if (graph.paths().isLegalPag()) {
            pag = true;
        }

        if (pag) {
            allAmenablePathsPag(graph, textArea, nodes1, nodes2);
        } else if (!mpdag && !mag) {
            textArea.append("\nThe graph is not a DAG, CPDAG, MPDAG, MAG or PAG.");
            return;
        }

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> amenable = graph.paths().amenablePathsMpdagMag(node1, node2,
                        parameters.getInt("pathsMaxLengthAdjustment"));

                if (amenable.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                listPaths(graph, textArea, amenable);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo amenable paths found.");
        }
    }

    /**
     * Appends all amenable paths from nodes in the first list to nodes in the second list to the given text area for a
     * PAG. An amenable path starts with a visible edge out of the starting node and does not block any of these paths.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allAmenablePathsPag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are semidirected paths from X to Y that start with a directed edge out of X. An 
                adjustment set should not block any of these paths.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> amenable = graph.paths().amenablePathsPag(node1, node2,
                        parameters.getInt("pathsMaxLengthAdjustment"));

                if (amenable.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                listPaths(graph, textArea, amenable);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo amenable paths found.");
        }
    }

    /**
     * Appends all backdoor paths from nodes in the first list to nodes in the second list to the given text area. A
     * backdoor path is a path from x to y that begins with z -> x. An adjustment set should block all of these paths.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allBackdoorPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are paths between x and y that start with z -> x for some z.
                """);

        addConditionNote(textArea);

        boolean mpdag = false;
        boolean mag = false;

        if (graph.paths().isLegalMpdag()) {
            mpdag = true;
        } else if (graph.paths().isLegalMag()) {
            mag = true;
        } else if (!graph.paths().isLegalPag()) {
            textArea.append("\nThe graph is not a DAG, CPDAG, MPDAG, MAG or PAG.");
            return;
        }

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                Set<List<Node>> _backdoor = graph.paths().allPaths(node1, node2,
                        parameters.getInt("pathsMaxLengthAdjustment"));
                List<List<Node>> backdoor = new ArrayList<>(_backdoor);

                if (mpdag || mag) {
                    backdoor.removeIf(path -> path.size() < 2 ||
                                              !(graph.getEdge(path.get(0), path.get(1)).pointsTowards(path.get(0))));
                } else {
                    backdoor.removeIf(path -> {
                        if (path.size() < 2) {
                            return false;
                        }
                        Node x = path.get(0);
                        Node w = path.get(1);
                        Node y = node2;
                        return !(graph.getEdge(x, w).pointsTowards(x)
                                 || Edges.isUndirectedEdge(graph.getEdge(x, w))
                                 || (Edges.isBidirectedEdge(graph.getEdge(x, w))
                                     && (graph.paths().existsDirectedPath(w, x)
                                         || (graph.paths().existsDirectedPath(w, x)
                                             && graph.paths().existsDirectedPath(w, y)))));
                    });
                }

                if (backdoor.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, backdoor);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo backdoor paths found.");
        }
    }

    /**
     * Appends all paths from the source nodes to the target nodes to a given text area.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of source nodes.
     * @param nodes2   The list of target nodes.
     */
    private void allPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are paths from the source to the target, however oriented. Not all paths may be listed, as a bound
                is placed on their length.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                Set<List<Node>> _paths = graph.paths().allPaths(node1, node2,
                        parameters.getInt("pathsMaxLength"));
                List<List<Node>> paths = new ArrayList<>(_paths);

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, paths);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo paths found.");
        }
    }

    private void listPaths(Graph graph, JTextArea textArea, List<List<Node>> paths) {
        textArea.append("\n\n    Not Blocked:\n");

        boolean allowSelectionBias = graph.paths().isLegalPag();

        for (Edge edge : graph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.CIRCLE || edge.getEndpoint2() == Endpoint.CIRCLE) {
                allowSelectionBias = true;
                break;
            }
        }

        boolean found1 = false;

        boolean mpdag = false;
        boolean mag = false;
        boolean pag = false;

        if (graph.paths().isLegalMpdag()) {
            mpdag = true;
        } else if (graph.paths().isLegalMag()) {
            mag = true;
        } else if (!graph.paths().isLegalPag()) {
            pag = true;
        }

        for (List<Node> path : paths) {
            if (path.size() < 2) {
                continue;
            }

            if (graph.paths().isMConnectingPath(path, conditioningSet, !mpdag)) {
                textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet,
                        !mpdag, allowSelectionBias));
                found1 = true;
            }
        }

        if (!found1) {
            textArea.append("\n    --NONE--");
        }

        textArea.append("\n\n    Blocked:\n");

        boolean found2 = false;

        for (List<Node> path : paths) {
            if (path.size() < 2) {
                continue;
            }

            if (!graph.paths().isMConnectingPath(path, conditioningSet, !mpdag)) {
                textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet, true,
                        allowSelectionBias));
                found2 = true;
            }
        }

        if (!found2) {
            textArea.append("\n    --NONE--");
        }
    }

    /**
     * Appends all treks of the form X <~~ S ~~> Y, S ~~> Y or X <~~ S for some source S
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the treks to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void allTreks(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are paths of the form X <~~ S ~~> Y, S ~~> Y or X <~~ S for some source S.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> treks = graph.paths().treks(node1, node2, parameters.getInt("pathsMaxLength"));

                if (treks.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, treks);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo treks found.");
        }
    }

    /**
     * Appends all confounder paths of the form X <~~ S ~~> Y, where S is the source, to the given text area.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void confounderPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are paths of the form X <~~ S ~~> Y for some source S. The source S would be the confounder.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> confounderPaths = graph.paths().treks(node1, node2, parameters.getInt("pathsMaxLength"));
                List<List<Node>> directPaths1 = graph.paths().directedPaths(node1, node2, parameters.getInt("pathsMaxLength"));
                List<List<Node>> directPaths2 = graph.paths().directedPaths(node2, node1, parameters.getInt("pathsMaxLength"));

                confounderPaths.removeAll(directPaths1);

                for (List<Node> _path : directPaths2) {
                    Collections.reverse(_path);
                    confounderPaths.remove(_path);
                }

                confounderPaths.removeIf(path -> path.get(0).getNodeType() != NodeType.MEASURED
                                                 || path.get(path.size() - 1).getNodeType() != NodeType.MEASURED);

                if (confounderPaths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, confounderPaths);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo confounder paths found.");
        }
    }

    /**
     * Appends all confounder paths along which all nodes except for endpoints are latent to the given text area.
     *
     * @param graph    The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1   The list of starting nodes.
     * @param nodes2   The list of ending nodes.
     */
    private void latentConfounderPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                These are confounder paths along which all nodes except for endpoints are latent. These are unmeasured nodes
                whose influence on the measured nodes is not accounted for.
                """);

        addConditionNote(textArea);

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> latentConfounderPaths = graph.paths().treks(node1, node2, parameters.getInt("pathsMaxLength"));
                List<List<Node>> directPaths1 = graph.paths().directedPaths(node1, node2, parameters.getInt("pathsMaxLength"));
                List<List<Node>> directPaths2 = graph.paths().directedPaths(node2, node1, parameters.getInt("pathsMaxLength"));
                latentConfounderPaths.removeAll(directPaths1);

                for (List<Node> _path : directPaths2) {
                    Collections.reverse(_path);
                    latentConfounderPaths.remove(_path);
                }

                for (List<Node> path : new ArrayList<>(latentConfounderPaths)) {
                    for (int i = 1; i < path.size() - 1; i++) {
                        Node node = path.get(i);

                        if (node.getNodeType() != NodeType.LATENT) {
                            latentConfounderPaths.remove(path);
                        }
                    }

                    if (path.get(0).getNodeType() != NodeType.MEASURED
                        || path.get(path.size() - 1).getNodeType() != NodeType.MEASURED) {
                        latentConfounderPaths.remove(path);
                    }
                }

                if (latentConfounderPaths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");
                listPaths(graph, textArea, latentConfounderPaths);
            }
        }

        if (!pathListed) {
            textArea.append("\n\nNo latent confounder paths found.");
        }
    }

    /**
     * Calculates and displays the adjacent nodes for each pair of nodes in the given lists.
     *
     * @param graph    The graph object representing the graph.
     * @param textArea The JTextArea object to append the results to.
     * @param nodes1   The first list of nodes.
     * @param nodes2   The second list of nodes.
     */
    private void adjacentNodes(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        List<Node> allNodes = new ArrayList<>();

        for (Node node : nodes1) {
            if (!allNodes.contains(node)) allNodes.add(node);
        }

        for (Node node : nodes2) {
            if (!allNodes.contains(node)) allNodes.add(node);
        }

        for (Node node1 : allNodes) {
            List<Node> parents = graph.getParents(node1);
            List<Node> children = graph.getChildren(node1);

            List<Node> ambiguous = new ArrayList<>(graph.getAdjacentNodes(node1));
            ambiguous.removeAll(parents);
            ambiguous.removeAll(children);

            textArea.append("\n\nAdjacents for " + node1 + ":");
            textArea.append("\n\nParents: " + niceList(parents));
            textArea.append("\nChildren: " + niceList(children));
            textArea.append("\nAmbiguous: " + niceList(ambiguous));
        }
    }

    /**
     * Calculates some adjustment sets for a given set of nodes in a graph.
     *
     * @param graph    The graph to calculate the adjustment sets in.
     * @param textArea The text area to display the results in.
     * @param nodes1   The first set of nodes.
     * @param nodes2   The second set of nodes.
     */
    private void adjustmentSets(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""     
            An adjustment set is a set of nodes that blocks all paths that can't be causal while leaving
            all causal paths unblocked. In particular, all confounders of the source and target will be
            blocked. By conditioning on an adjustment set (if one exists) one can estimate the total 
            effect of a source on a target.
            
            To check to see if a particular set of nodes is an adjustment set, type (or paste) the nodes
            into the text field above. Then press Enter. Then select "Amenable Paths" from the above 
            dropdown. All amenable paths (paths that can be causal) should be unblocked. If any are 
            blocked, the set is not an adjustment set. Also select "Backdoor paths" from the dropdown. 
            All backdoor paths (paths that can't be causal) should be blocked. If any are unblocked, the 
            set is not an adjustment set.
            
            In the below perhaps not all adjustment sets are listed. Rather, the algorithm is designed to
            find up to a maximum number of adjustment sets that are no more than a certain distance from
            either the source or the target node, or either. Also, while all amenable paths are taken
            into account, backdoor paths considered are only those that with no more than a certain number 
            of nodes. These parameters can be edited.
            """);

        boolean found = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                int maxNumSet = parameters.getInt("pathsMaxNumSets");
                int maxDistanceFromEndpoint = parameters.getInt("pathsMaxDistanceFromEndpoint");
                int nearWhichEndpoint = parameters.getInt("pathsNearWhichEndpoint");
                int maxLengthAdjustment = parameters.getInt("pathsMaxLengthAdjustment");

                // === NEW: Check for amenable (causal) paths ===
                List<List<Node>> amenablePaths;
                if (graph.paths().isLegalMpdag() || graph.paths().isLegalMag()) {
                    amenablePaths = graph.paths().amenablePathsMpdagMag(node1, node2, -1);
                } else if (graph.paths().isLegalPag()) {
                    amenablePaths = graph.paths().amenablePathsPag(node1, node2, -1);
                } else {
                    throw new IllegalArgumentException("Graph must be a legal MPDAG, MAG, or PAG");
                }

                if (amenablePaths.isEmpty()) {
                    textArea.append("\n\nAdjustment set(s) for " + node1 + " ~~> " + node2 + ":\n");
                    textArea.append("No amenable (causal) paths exist between " + node1 + " and " + node2 + ".\n");
                    textArea.append("Hence, no adjustment set is needed or defined.\n");
                    continue;
                }

                // --- Run the all-paths adjustment finder ---
                List<Set<Node>> adjustments;
                try {
                    adjustments = graph.paths().adjustmentSets(
                            node1, node2, maxNumSet,
                            maxDistanceFromEndpoint, nearWhichEndpoint, maxLengthAdjustment
                    );
                } catch (Exception e) {
                    // Skip on error
                    continue;
                }

                textArea.append("\n\nAdjustment sets for " + node1 + " ~~> " + node2 + ":\n");

                if (adjustments.isEmpty()) {
                    textArea.append("\n    --NONE--");
                    continue;
                }

                for (Set<Node> adjustment : adjustments) {
                    textArea.append("\n    " + adjustment);
                }

                found = true;
            }
        }

        if (!found) {
            textArea.append("\n\nNo adjustment sets found.");
        }
    }

    private void recursiveAdjustmentSets(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""     
            An adjustment set is a set of nodes that blocks all paths that can't be causal while leaving
            all causal paths unblocked. In particular, all confounders of the source and target will be
            blocked. By conditioning on an adjustment set (if one exists) one can estimate the total 
            effect of a source on a target. We look for adjustment sets here using a masked version of
            the recursive blocking set algorithm.
            
            To check to see if a particular set of nodes is an adjustment set, type (or paste) the nodes
            into the text field above. Then press Enter. Then select "Amenable Paths" from the above 
            dropdown. All amenable paths (paths that can be causal) should be unblocked. If any are 
            blocked, the set is not an adjustment set. Also select "Backdoor paths" from the dropdown. 
            All backdoor paths (paths that can't be causal) should be blocked. If any are unblocked, the 
            set is not an adjustment set.
            """);

        boolean found = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                int maxNumSet = parameters.getInt("pathsMaxNumSets");
                int maxDistanceFromEndpoint = parameters.getInt("pathsMaxDistanceFromEndpoint");
                int nearWhichEndpoint = parameters.getInt("pathsNearWhichEndpoint");
                int maxLengthAdjustment = parameters.getInt("pathsMaxLengthAdjustment");

                List<List<Node>> amenablePaths;

                if (graph.paths().isLegalMpdag() || graph.paths().isLegalMag()) {
                    amenablePaths = graph.paths().amenablePathsMpdagMag(node1, node2, -1);
                } else if (graph.paths().isLegalPag()) {
                    amenablePaths = graph.paths().amenablePathsPag(node1, node2, -1);
                } else {
                    throw new IllegalArgumentException("Graph must be a legal MPDAG, MAG, or PAG");
                }

                try {
                    boolean single = false;

                    // === NEW NOTE: Check if there are any amenable paths ===
                    if (amenablePaths.isEmpty()) {
                        textArea.append("\n\nAdjustment set(s) for " + node1 + " ~~> " + node2 + ":\n");
                        textArea.append("No amenable (causal) paths exist between " + node1 + " and " + node2 + ".\n");
                        textArea.append("Hence, no adjustment set is needed or defined.\n");
                        continue;
                    }

                    if (single) {
                        // Step 1. Build the latent mask (forbidden nodes on amenable paths)
                        Set<Node> latentMask = RecursiveAdjustment.buildAmenableNoncolliderMask(
                                graph, node1, node2, amenablePaths, Collections.emptySet()
                        );

                        // Step 2. Run the recursive adjustment search
                        Set<Node> adjustmentSet = RecursiveAdjustment.findAdjustmentSet(
                                graph,
                                node1,
                                node2,
                                /* seedZ */ Collections.emptySet(),
                                /* notFollowed */ Collections.emptySet(),
                                /* maxPathLength */ -1,
                                latentMask
                        );

                        textArea.append("\n\nAdjustment set for " + node1 + " ~~> " + node2 + ":\n");
                        textArea.append("Latent mask = " + latentMask + "\n");

                        if (adjustmentSet == null) {
                            textArea.append("\n    --NONE--");
                            continue;
                        }

                        textArea.append("\n    " + adjustmentSet);
                        found = true;

                    } else {
                        Set<Node> latentMask = buildLatentMaskForTotalEffect(
                                graph, node1, node2, amenablePaths, Collections.emptySet(), true
                        );

                        // Call the enumerator for multiple adjustment sets
                        List<Set<Node>> adjustmentSets = RecursiveAdjustment.findAdjustmentSets(
                                graph,
                                node1,
                                node2,
                                /* seedZ */ Collections.emptySet(),
                                /* notFollowed */ Collections.emptySet(),
                                /* maxPathLength */ -1,
                                latentMask,
                                /* maxSets */ -1,
                                /* minimizeEach */ false
                        );

                        textArea.append("\n\nAdjustment set(s) for " + node1 + " ~~> " + node2 + ":\n");
                        textArea.append("Latent mask = " + latentMask + "\n");

                        if (adjustmentSets.isEmpty()) {
                            textArea.append("\n    --NONE--");
                            continue;
                        }

                        for (Set<Node> adjustment : adjustmentSets) {
                            textArea.append("\n    " + adjustment);
                        }

                        found = true;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!found) {
            textArea.append("\n\nNo adjustment sets found.");
        }
    }

    private void recursiveBlockingSets(Graph graph, JTextArea textArea,
                                       List<Node> nodes1, List<Node> nodes2) {
        textArea.setText("""
                A recursive blocking (RB) set for nodes x and y is a set of variables that blocks all 
                non-inducing paths between x and y (it may leave a direct edge x — y or other inducing paths unblocked).
                
                • If there is a direct edge x → y, the RB set isolates the EDGE-SPECIFIC (LOCAL) EFFECT associated 
                  with that edge. It blocks all alternative non-inducing paths from x to y while leaving the edge 
                  itself unblocked.
                
                • If multiple inducing paths exist between x and y (for example, due to latent confounding), the RB 
                  set will leave those open as well. In such cases, it does not correspond to a unique adjustment set 
                  or direct-effect estimate.
                
                • Only in special cases—when x and y are amenable and there is a unique directed causal route from 
                  x to y—will an RB set coincide with an ADJUSTMENT SET for estimating the total effect of x on y.
                
                Tip: To assess amenability, select “Amenable paths” above. All amenable paths (those that can be causal) 
                should remain unblocked; backdoor paths should be blocked.
                """);

        boolean anyFound = false;
        int maxDistanceFromEndpoint = parameters.getInt("pathsMaxDistanceFromEndpoint");

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                if (node1 == node2) continue;

                Set<Node> blocking1 = null;
                Set<Node> blocking2 = null;
                try {
                    blocking1 = RecursiveBlocking.blockPathsRecursively(
                            graph, node1, node2, conditioningSet, Set.of(), maxDistanceFromEndpoint);
                    blocking2 = RecursiveBlocking.blockPathsRecursively(
                            graph, node2, node1, conditioningSet, Set.of(), maxDistanceFromEndpoint);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                textArea.append("\n\nRecursive blocking sets for <" + node1 + ", " + node2 + ">:\n");

                textArea.append("\nFrom " + node1 + " to " + node2 + ":");
                textArea.append(blocking1 == null ? "\n    --NONE--" : "\n    " + blocking1);

                textArea.append("\n\nFrom " + node2 + " to " + node1 + ":");
                textArea.append(blocking2 == null ? "\n    --NONE--" : "\n    " + blocking2);

                boolean foundPair = (blocking1 != null) || (blocking2 != null);
                anyFound |= foundPair;

                boolean hasEdge = (graph.getEdge(node1, node2) != null);
                boolean amenable =
                        hasAmenablePaths(graph, node1, node2) || hasAmenablePaths(graph, node2, node1);

                if (foundPair && hasEdge && amenable) {
                    textArea.append("\n\nResult: The pair <" + node1 + ", " + node2 + "> is amenable. The above RB set(s) " +
                                    "serve as EDGE-SPECIFIC BLOCKING SETS for isolating the local effect of the edge " +
                                    node1 + " → " + node2 + ".");
                } else if (foundPair && hasEdge) {
                    textArea.append("\n\nResult: An edge exists between " + node1 + " and " + node2 +
                                    ", but amenability is not established. The RB set(s) block non-inducing paths,\n " +
                                    "but multiple inducing paths may remain unblocked; thus, these are not guaranteed " +
                                    "to represent valid direct-effect blocking sets.");
                } else if (foundPair) {
                    String indep = " _||_ ";
                    textArea.append("\n\nResult: No edge between " + node1 + " and " + node2 +
                                    ". The RB set(s) are separating sets:\n    " +
                                    node1 + indep + node2 + " | " + (blocking1 == null ? "{}" : blocking1) + " and\n    " +
                                    node1 + indep + node2 + " | " + (blocking2 == null ? "{}" : blocking2));
                } else {
                    textArea.append("\n\nResult: No RB sets found for this pair under the current search limits.");
                }
            }
        }

        if (!anyFound) {
            textArea.append("\n\nNo recursive blocking sets found under the current parameters.");
        }
    }

    /**
     * Returns true if there exist amenable paths (Perković) from x to y in this graph.
     */
    private boolean hasAmenablePaths(Graph graph, Node x, Node y) {
        int L = parameters.getInt("pathsMaxLengthAdjustment");
        // Prefer a PAG-aware amenable routine if available, else fall back to MPDAG/MAG version.
        if (graph.paths().isLegalPag()) {
            List<List<Node>> aps = graph.paths().amenablePathsPag(x, y, L);
            return aps != null && !aps.isEmpty();
        } else if (graph.paths().isLegalMpdag() || graph.paths().isLegalMag()) {
            List<List<Node>> aps = graph.paths().amenablePathsMpdagMag(x, y, L);
            return aps != null && !aps.isEmpty();
        }
        return false;
    }

    /**
     * Converts a list of Nodes into a comma-separated string representation. If the list is empty, returns "--NONE--".
     *
     * @param _nodes The list of Nodes to convert.
     * @return The comma-separated string representation of the Nodes list, or "--NONE--" if the list is empty.
     */
    private String niceList(List<Node> _nodes) {
        if (_nodes.isEmpty()) {
            return "--NONE--";
        }

        List<Node> nodes = new ArrayList<>(_nodes);

        Collections.sort(nodes);

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < nodes.size(); i++) {
            buf.append(nodes.get(i));

            if (i < nodes.size() - 1) {
                buf.append(", ");
            }
        }

        return buf.toString();
    }

    /**
     * Notifies that the ownership of the specified clipboard contents has been lost.
     *
     * @param clipboard The clipboard object that lost ownership of the contents.
     * @param contents  The contents that were lost by the clipboard.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
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

}




