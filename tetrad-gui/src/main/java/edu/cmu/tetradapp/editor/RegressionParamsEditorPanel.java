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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.RegressionModel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.workbench.LayoutUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Allows one to drop/drag variables from a source list to a response area and a predictors list. Also lets one specify
 * an alpha level.
 *
 * @author Tyler Gibson
 */
class RegressionParamsEditorPanel extends JPanel {

    private static final long serialVersionUID = -194301447990323529L;

    /**
     * A mapping between variable names and what sort of variable they are: 1 - binary, 2 - discrete, 3 - continuous.
     */
    private static final Map<String, Integer> VAR_MAP = new HashMap<>();

    /**
     * The font to render fields in.
     */
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 12);

    /**
     * The list of predictors.
     */
    private static JList<String> PREDICTORS_LIST;

    /**
     * The list of source variables.
     */
    private static JList<String> SOURCE_LIST;

    /**
     * A text field for the response variable.
     */
    private static JTextField RESPONSE_FIELD;

    private final boolean logistic;
    private final RegressionModel regressionModel;

    /**
     * The params that are being edited.
     */
    private final Parameters params;

    /**
     * Constructs the editor given the <code>Parameters</code> and the
     * <code>DataModel</code> that should be used.
     *
     * @param regressionModel a {@link edu.cmu.tetradapp.model.RegressionModel} object
     * @param parameters      a {@link edu.cmu.tetrad.util.Parameters} object
     * @param model           a {@link edu.cmu.tetrad.data.DataModel} object
     * @param logistic        a boolean
     */
    public RegressionParamsEditorPanel(RegressionModel regressionModel, Parameters parameters,
                                       DataModel model, boolean logistic) {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        if (parameters == null) {
            throw new NullPointerException("The given params must not be null");
        }
        this.params = parameters;
        this.logistic = logistic;
        List<String> variableNames = regressionModel.getVariableNames();
        this.regressionModel = regressionModel;

        // create components
        RegressionParamsEditorPanel.PREDICTORS_LIST = RegressionParamsEditorPanel.createList();
        VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
        RegressionParamsEditorPanel.SOURCE_LIST = RegressionParamsEditorPanel.createList();
        if (logistic && model instanceof DataSet) {
            buildMap((DataSet) model);
            RegressionParamsEditorPanel.getSourceList().setCellRenderer(new LogisticRegRenderer());
        }
        VariableListModel variableModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
        RegressionParamsEditorPanel.RESPONSE_FIELD = createResponse(RegressionParamsEditorPanel.getSourceList());

        // if regressors are already set use them.
        List<String> regressors = regressionModel.getRegressorNames();
        if (regressors != null) {
            predictorsModel.addAll(regressors);
            List<String> initVars = new ArrayList<>(variableNames);
            initVars.removeAll(regressors);
            variableModel.addAll(initVars);
        } else {
            variableModel.addAll(variableNames);
        }

        // if target is set use it too
        String target = regressionModel.getTargetName();
        if (target != null) {
            variableModel.remove(target);
        }

        // deal with drag and drop
        new DropTarget(RegressionParamsEditorPanel.getSourceList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(RegressionParamsEditorPanel.getResponseField(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(RegressionParamsEditorPanel.getPredictorsList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);

        DragSource dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getResponseField(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getSourceList(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getPredictorsList(), DnDConstants.ACTION_MOVE, new SourceListener());

        // build the gui
        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalStrut(10));
        Box label = RegressionParamsEditorPanel.createLabel("Variables:");
        int height = label.getPreferredSize().height + RegressionParamsEditorPanel.getResponseField().getPreferredSize().height + 10;
        Box vBox1 = Box.createVerticalBox();
        vBox1.add(label);
        JScrollPane pane = RegressionParamsEditorPanel.createScrollPane(RegressionParamsEditorPanel.getSourceList(), new Dimension(100, 350 + height));
        vBox1.add(pane);
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildAlphaArea(this.params.getDouble("alpha", 0.001)));
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildSortButton());
        vBox1.add(Box.createVerticalGlue());
        box.add(vBox1);

        box.add(Box.createHorizontalStrut(4));
        box.add(buildSelectorArea(label.getPreferredSize().height));
        box.add(Box.createHorizontalStrut(4));

        Box vBox = Box.createVerticalBox();
        vBox.add(RegressionParamsEditorPanel.createLabel("Response:"));
        vBox.add(RegressionParamsEditorPanel.getResponseField());
        vBox.add(Box.createVerticalStrut(10));
        vBox.add(RegressionParamsEditorPanel.createLabel("Predictor(s):"));
        vBox.add(RegressionParamsEditorPanel.createScrollPane(RegressionParamsEditorPanel.getPredictorsList(), new Dimension(100, 350)));
        vBox.add(Box.createVerticalGlue());

        box.add(vBox);
        box.add(Box.createHorizontalStrut(10));
        box.add(Box.createHorizontalGlue());

        this.add(Box.createVerticalStrut(20));
        this.add(box);
    }

    //============================= Private Methods =================================//

    private static List<String> getSelected(JList<String> list) {
        List<String> selected = list.getSelectedValuesList();
        return selected == null ? new ArrayList<>() : new ArrayList<>(selected);
    }

    private static JScrollPane createScrollPane(JList<String> comp, Dimension dim) {
        JScrollPane pane = new JScrollPane(comp);
        LayoutUtils.setAllSizes(pane, dim);
        return pane;
    }

    private static Box createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private static JList<String> createList() {
        JList<String> list = new JList<>(new VariableListModel());
        list.setFont(RegressionParamsEditorPanel.getFONT());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(10);
        return list;
    }

    private static DataFlavor getListDataFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=java.lang.Object",
                    "Local Variable List");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Map<String, Integer> getVarMap() {
        return RegressionParamsEditorPanel.VAR_MAP;
    }

    private static JList<String> getPredictorsList() {
        return RegressionParamsEditorPanel.PREDICTORS_LIST;
    }

    private static JList<String> getSourceList() {
        return RegressionParamsEditorPanel.SOURCE_LIST;
    }

    private static JTextField getResponseField() {
        return RegressionParamsEditorPanel.RESPONSE_FIELD;
    }

    private static Font getFONT() {
        return RegressionParamsEditorPanel.FONT;
    }

    /**
     * Builds the arrows that allow one to move variables around (can also use drag and drop).
     */
    private Box buildSelectorArea(int startHeight) {
        Box box = Box.createVerticalBox();
        JButton moveToResponse = new JButton(">");
        JButton moveToPredictor = new JButton(">");
        JButton moveToSource = new JButton("<");

        moveToResponse.addActionListener((e) -> {
            VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            String target = RegressionParamsEditorPanel.getResponseField().getText();
            List<String> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getSourceList());
            if (selected.isEmpty()) {
                return;
            } else if (1 < selected.size()) {
                JOptionPane.showMessageDialog(this, "Cannot have more than one response variable");
                return;
            } else if (this.logistic && !isBinary(selected.get(0))) {
                JOptionPane.showMessageDialog(this, "Response variable must be binary.");
                return;
            }
            sourceModel.removeAll(selected);
            RegressionParamsEditorPanel.getResponseField().setText(selected.get(0));
            RegressionParamsEditorPanel.getResponseField().setCaretPosition(0);
            this.regressionModel.setTargetName(selected.get(0));
            if (target != null && target.length() != 0) {
                sourceModel.add(target);
            }
        });

        moveToPredictor.addActionListener((e) -> {
            VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            List<String> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getSourceList());
            sourceModel.removeAll(selected);
            predictorsModel.addAll(selected);
            this.regressionModel.setRegressorName(getPredictors());
        });

        moveToSource.addActionListener((e) -> {
            VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            List<String> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getPredictorsList());
            if (!selected.isEmpty()) {
                predictorsModel.removeAll(selected);
                sourceModel.addAll(selected);
                this.regressionModel.setRegressorName(getPredictors());
            } else if (RegressionParamsEditorPanel.getResponseField().getText() != null
                    && RegressionParamsEditorPanel.getResponseField().getText().length() != 0) {
                String text = RegressionParamsEditorPanel.getResponseField().getText();
                this.regressionModel.setTargetName(null);
                RegressionParamsEditorPanel.getResponseField().setText(null);
                sourceModel.addAll(Collections.singletonList(text));
            }
        });

        box.add(Box.createVerticalStrut(startHeight));
        box.add(moveToResponse);
        box.add(Box.createVerticalStrut(150));
        box.add(moveToPredictor);
        box.add(Box.createVerticalStrut(10));
        box.add(moveToSource);
        box.add(Box.createVerticalGlue());

        return box;
    }

    private Box buildSortButton() {
        JButton sort = new JButton("Sort Variables");
        sort.setFont(sort.getFont().deriveFont(11f));
        sort.setMargin(new Insets(3, 3, 3, 3));
        sort.addActionListener((e) -> {
            VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            predictorsModel.sort();
            sourceModel.sort();
        });
        Box box = Box.createHorizontalBox();
        box.add(sort);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private Box buildAlphaArea(double alpha) {
        DoubleTextField field = new DoubleTextField(alpha, 4, NumberFormatUtil.getInstance().getNumberFormat());
        field.setFilter((value, oldValue) -> {
            if (0.0 <= value && value <= 1.0) {
                this.params.set("alpha", value);
                this.firePropertyChange("significanceChanged", oldValue, value);
                return value;
            }
            return oldValue;
        });

        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Alpha: "));
        box.add(field);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private void buildMap(DataSet model) {
        for (Node node : model.getVariables()) {
            if (DataUtils.isBinary(model, model.getColumnIndex(node))) {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 1);
            } else if (node instanceof DiscreteVariable) {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 2);
            } else {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 3);
            }
        }
    }

    private JTextField createResponse(JList<String> list) {
        JTextField pane = new JTextField();
        pane.setFont(RegressionParamsEditorPanel.getFONT());
        pane.setFocusable(true);
        pane.setEditable(false);
        pane.setBackground(list.getBackground());

        String target = this.regressionModel.getTargetName();
        if (target != null) {
            pane.setText(target);
        } else {
            pane.setText("Hello");
        }
        pane.setCaretPosition(0);
        LayoutUtils.setAllSizes(pane, new Dimension(100, pane.getPreferredSize().height));
        if (target == null) {
            pane.setText(null);
        }
        pane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                RegressionParamsEditorPanel.getPredictorsList().clearSelection();
            }
        });

        return pane;
    }

    private List<String> getPredictors() {
        ListModel<String> model = RegressionParamsEditorPanel.getPredictorsList().getModel();
        List<String> predictors = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            predictors.add(model.getElementAt(i));
        }
        return predictors;
    }

    private void addToSource(String var) {
        VariableListModel model = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
        model.add(var);
    }

    private boolean isBinary(String node) {
        int i = RegressionParamsEditorPanel.getVarMap().get(node);
        return i == 1;
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }

    //========================== Inner classes =========================================//

    /**
     * A renderer that adds info about whether a variable is binary, discrete, or continuous.
     */
    private static class LogisticRegRenderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            String var = (String) value;
            if (var == null) {
                setText(" ");
                return this;
            }
            int type = RegressionParamsEditorPanel.getVarMap().get(var);
            if (type == 1) {
                var += " (Binary)";
            } else if (type == 2) {
                var += " (Discrete)";
            } else if (type == 3) {
                var += " (Continuous)";
            }
            setText(var);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }

    /**
     * A typed list model for String variable names.
     */
    private static class VariableListModel extends AbstractListModel<String> {

        private final Vector<String> delegate = new Vector<>();

        public int getSize() {
            return this.delegate.size();
        }

        public String getElementAt(int index) {
            return this.delegate.get(index);
        }

        public void remove(String element) {
            int index = this.delegate.indexOf(element);
            if (0 <= index) {
                this.delegate.remove(index);
                this.fireIntervalRemoved(this, index, index);
            }
        }

        public void add(String element) {
            this.delegate.add(element);
            this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
        }

        public void removeFirst(String element) {
            this.delegate.removeElement(element);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll(List<String> elements) {
            this.delegate.removeAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void addAll(List<String> elements) {
            this.delegate.addAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll() {
            this.delegate.clear();
            this.fireContentsChanged(this, 0, 0);
        }

        public void sort() {
            delegate.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }
    }

    /**
     * A typed transferable for lists of Strings.
     */
    private static class ListTransferable implements Transferable {

        private static final DataFlavor FLAVOR = RegressionParamsEditorPanel.getListDataFlavor();

        private final List<String> object;

        public ListTransferable(List<String> object) {
            if (object == null) {
                throw new NullPointerException();
            }
            this.object = object;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{ListTransferable.FLAVOR};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor == ListTransferable.FLAVOR;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (ListTransferable.FLAVOR != flavor) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.object;
        }
    }

    private class TargetListener extends DropTargetAdapter {

        @SuppressWarnings("unchecked")
        public void drop(DropTargetDropEvent dtde) {
            Transferable t = dtde.getTransferable();
            Component comp = dtde.getDropTargetContext().getComponent();
            if (comp instanceof JList || comp instanceof JTextField) {
                try {
                    if (comp == RegressionParamsEditorPanel.getResponseField()) {
                        String var = RegressionParamsEditorPanel.getResponseField().getText();
                        if (var != null && var.length() != 0) {
                            addToSource(var);
                        }
                        List<String> vars = (List<String>) t.getTransferData(ListTransferable.FLAVOR);
                        if (vars.isEmpty()) {
                            dtde.rejectDrop();
                            return;
                        } else if (1 < vars.size()) {
                            JOptionPane.showMessageDialog(RegressionParamsEditorPanel.this,
                                    "There can only be one response variable.");
                            dtde.rejectDrop();
                            return;
                        } else if (RegressionParamsEditorPanel.this.logistic && !isBinary(vars.get(0))) {
                            JOptionPane.showMessageDialog(RegressionParamsEditorPanel.this,
                                    "The response variable must be binary");
                            dtde.rejectDrop();
                            return;
                        }
                        RegressionParamsEditorPanel.getResponseField().setText(vars.get(0));
                        RegressionParamsEditorPanel.getResponseField().setCaretPosition(0);
                    } else {
                        JList<String> list = (JList<String>) comp;
                        VariableListModel model = (VariableListModel) list.getModel();
                        List<String> vars = (List<String>) t.getTransferData(ListTransferable.FLAVOR);
                        model.addAll(vars);
                    }

                    RegressionParamsEditorPanel.this.regressionModel.setTargetName(
                            RegressionParamsEditorPanel.getResponseField().getText());
                    RegressionParamsEditorPanel.this.regressionModel.setRegressorName(getPredictors());
                    dtde.getDropTargetContext().dropComplete(true);
                } catch (Exception ex) {
                    dtde.rejectDrop();
                    ex.printStackTrace();
                }
            } else {
                dtde.rejectDrop();
            }
        }
    }

    /**
     * A source/gesture listener for the JLists.
     */
    private class SourceListener extends DragSourceAdapter implements DragGestureListener {

        @SuppressWarnings("unchecked")
        public void dragDropEnd(DragSourceDropEvent evt) {
            if (evt.getDropSuccess()) {
                Component comp = evt.getDragSourceContext().getComponent();
                Transferable t = evt.getDragSourceContext().getTransferable();
                if (t instanceof ListTransferable) {
                    try {
                        List<String> o = (List<String>) t.getTransferData(ListTransferable.FLAVOR);
                        if (comp instanceof JList<?> list) {
                            VariableListModel model = (VariableListModel) list.getModel();
                            for (String s : o) {
                                model.removeFirst(s);
                            }
                        } else {
                            JTextField pane = (JTextField) comp;
                            pane.setText(null);
                        }

                        RegressionParamsEditorPanel.this.regressionModel.setTargetName(
                                RegressionParamsEditorPanel.getResponseField().getText());
                        RegressionParamsEditorPanel.this.regressionModel.setRegressorName(getPredictors());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        public void dragGestureRecognized(DragGestureEvent dge) {
            Component comp = dge.getComponent();
            List<String> selected = null;
            if (comp instanceof JList<?> list) {
                selected = ((JList<String>) list).getSelectedValuesList();
            } else {
                JTextField pane = (JTextField) comp;
                String text = pane.getText();
                if (text != null && text.length() != 0) {
                    selected = Collections.singletonList(text);
                }
            }
            if (selected != null) {
                ListTransferable t = new ListTransferable(new ArrayList<>(selected));
                dge.startDrag(DragSource.DefaultMoveDrop, t, this);
            }
        }
    }
}
