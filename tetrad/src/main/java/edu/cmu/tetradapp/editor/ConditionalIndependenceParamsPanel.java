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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.LogisticRegressionParams;
import edu.cmu.tetradapp.model.StandardRegressionParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.workbench.LayoutUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Allows one to drop/drap variables from a source list to a X, a Y area (for X _||_ Y) and
 * a conditioning set. Also lets one specify an alpha level.
 *
 * @author Mike Freenor ripping off Tyler Gibson
 */
@SuppressWarnings({"unchecked"})
public class ConditionalIndependenceParamsPanel extends JPanel {


    /**
     * The params that are being edited.
     */
    private StandardRegressionParams params;


    /**
     * The list of predictors.
     */
    private static JList PREDICTORS_LIST;


    /**
     * The list of source variables.
     */
    private static JList SOURCE_LIST;


    /**
     * A list with a single item in it for the X variable (as in X _||_ Y)
     */
    private static JTextField X_FIELD;

    private static JTextField Y_FIELD;

    /**
     * A mapping between variable names and what sort of variable they are:
     * 1 - binary, 2- discrete, 3 - continuous.
     */
    private static Map<String, Integer> VAR_MAP = new HashMap<String, Integer>();


    /**
     * The font to render fields in.
     */
    private static Font FONT = new Font("Dialog", Font.PLAIN, 12);



    /**
     * Constructs the editor given the <code>RegressionParams</code> and the <code>DataModel</code>
     * that should be used.
     *
     * @param params
     * @param model
     */
    public ConditionalIndependenceParamsPanel(StandardRegressionParams params, DataModel model) {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        if (params == null) {
            throw new NullPointerException("The given params must not be null");
        }
        this.params = params;
        List<String> variableNames = params.getVarNames();
        // if null get the variables from the parent data set.
        if (variableNames == null) {
            if (model == null) {
                throw new NullPointerException("Data model must not be null");
            }
            variableNames = model.getVariableNames();
            if (variableNames == null) {
                throw new IllegalStateException("Could not load variables.");
            }
            params.setVarNames(variableNames);
        }
        // create components
        PREDICTORS_LIST = createList();
        VariableListModel predictorsModel = (VariableListModel) getPredictorsList().getModel();
        SOURCE_LIST = createList();
        if (params instanceof LogisticRegressionParams && model instanceof DataSet) {
            buildMap((DataSet) model);
            getSourceList().setCellRenderer(new LogisticRegRenderer());
        }
        VariableListModel variableModel = (VariableListModel) getSourceList().getModel();
        X_FIELD = createResponse(getSourceList(), 100);
        Y_FIELD = createResponse(getSourceList(), 100);

        // if regressors are already set use'em.
        String[] regressors = params.getRegressorNames();
        if (regressors != null) {
            List<String> elements = Arrays.asList(regressors);
            predictorsModel.addAll(elements);
            List<String> initVars = new ArrayList<String>(variableNames);
            initVars.removeAll(elements);
            variableModel.addAll(initVars);
        } else {
            variableModel.addAll(variableNames);
        }
        // if target is set use it too
        String target = params.getTargetName();
        if (target != null) {
            variableModel.remove(target);
            //     response.setText(target);
        }

        // deal with drag and drop
        new DropTarget(getSourceList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(getXField(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(getPredictorsList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);

        DragSource dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(getXField(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(getSourceList(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(getPredictorsList(), DnDConstants.ACTION_MOVE, new SourceListener());
        // build the gui
        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalStrut(10));
        Box label = createLabel("Variables:");
        int height = label.getPreferredSize().height + getXField().getPreferredSize().height + 10;
        Box vBox1 = Box.createVerticalBox();
        vBox1.add(label);
        JScrollPane pane = createScrollPane(getSourceList(), new Dimension(100, 350 + height));
        vBox1.add(pane);
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildAlphaArea(params.getAlpha()));
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildSortButton());
        vBox1.add(Box.createVerticalGlue());
        box.add(vBox1);

        box.add(Box.createHorizontalStrut(4));
        box.add(buildSelectorArea(label.getPreferredSize().height));
        box.add(Box.createHorizontalStrut(4));

        Box vBox = Box.createVerticalBox();
        vBox.add(createLabel("X:"));


        vBox.add(getXField());
        vBox.add(Box.createVerticalStrut(10));

        vBox.add(createLabel("Y:"));


        vBox.add(getYField());
        vBox.add(Box.createVerticalStrut(10));

        vBox.add(createLabel("Conditioning Set:"));
        vBox.add(createScrollPane(getPredictorsList(), new Dimension(100, 350)));
        vBox.add(Box.createVerticalGlue());

        box.add(vBox);
        box.add(Box.createHorizontalStrut(10));
        box.add(Box.createHorizontalGlue());

        this.add(Box.createVerticalStrut(20));
        this.add(box);
    }

    //============================= Private Methods =================================//


    private static List<Comparable> getSelected(JList list) {
        List selected = list.getSelectedValuesList();
        List<Comparable> selectedList = new ArrayList<Comparable>(selected == null ? 0 : selected.size());
        if (selected != null) {
            for (Object o : selected) {
                selectedList.add((Comparable) o);
            }
        }
        return selectedList;
    }


    /**
     * Bulids the arrows that allow one to move variables around (can also use drag and drop)
     */
    private Box buildSelectorArea(int startHeight) {
        Box box = Box.createVerticalBox();
        JButton moveToX = new JButton(">");
        JButton moveToY = new JButton(">");
        JButton moveToPredictor = new JButton(">");
        JButton moveToSource = new JButton("<");

        moveToX.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                String target = getXField().getText();
                List<Comparable> selected = getSelected(getSourceList());
                if (selected.isEmpty()) {
                    return;
                } else if (1 < selected.size()) {
                    JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this, "Cannot have more than one response variable");
                    return;
                } else if(params instanceof LogisticRegressionParams && !isBinary((String)selected.get(0))){
                    JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this,
                            "Response variable must be binary.");
                    return;
                }
                sourceModel.removeAll(selected);
                getXField().setText((String) selected.get(0));
                getXField().setCaretPosition(0);
                params.setTargetName((String) selected.get(0));
                if (target != null && target.length() != 0) {
                    sourceModel.add(target);
                }
            }
        });

        moveToY.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                String target = getYField().getText();
                List<Comparable> selected = getSelected(getSourceList());
                if (selected.isEmpty()) {
                    return;
                } else if (1 < selected.size()) {
                    JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this, "Cannot have more than one response variable");
                    return;
                } else if(params instanceof LogisticRegressionParams && !isBinary((String)selected.get(0))){
                    JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this,
                            "Response variable must be binary.");
                    return;
                }
                sourceModel.removeAll(selected);
                getYField().setText((String) selected.get(0));
                getYField().setCaretPosition(0);
                //params.setTargetName((String) selected.get(0));
                if (target != null && target.length() != 0) {
                    sourceModel.add(target);
                }
            }
        });

        moveToPredictor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableListModel predictorsModel = (VariableListModel) getPredictorsList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                List<Comparable> selected = getSelected(getSourceList());
                sourceModel.removeAll(selected);
                predictorsModel.addAll(selected);
                params.setRegressorNames(getPredictors());
            }
        });

        moveToSource.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableListModel predictorsModel = (VariableListModel) getPredictorsList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                List<Comparable> selected = getSelected(getPredictorsList());
                // if not empty remove/add, otherwise try the response list.
                if (!selected.isEmpty()) {
                    predictorsModel.removeAll(selected);
                    sourceModel.addAll(selected);
                    params.setRegressorNames(getPredictors());
                } else if (getXField().getText() != null && getXField().getText().length() != 0) {
                    String text = getXField().getText();
                    params.setTargetName(null);
                    getXField().setText(null);
                    sourceModel.addAll(Collections.singletonList(text));
                }
            }
        });

        box.add(Box.createVerticalStrut(startHeight));
        box.add(moveToX);
        box.add(Box.createVerticalStrut(10));
        box.add(moveToY);
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
        sort.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableListModel predictorsModel = (VariableListModel) getPredictorsList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                predictorsModel.sort();
                sourceModel.sort();
            }
        });
        Box box = Box.createHorizontalBox();
        box.add(sort);
        box.add(Box.createHorizontalGlue());

        return box;
    }


    private Box buildAlphaArea(double alpha) {
        DoubleTextField field = new DoubleTextField(alpha, 4, NumberFormatUtil.getInstance().getNumberFormat());
        field.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (0.0 <= value && value <= 1.0) {
                    params.setAlpha(value);
                    ConditionalIndependenceParamsPanel.this.firePropertyChange("significanceChanged",
                            oldValue, value);
                    return value;
                }
                return oldValue;
            }
        });



        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Alpha: "));
        box.add(field);
        box.add(Box.createHorizontalGlue());
        return box;
    }


    private void buildMap(DataSet model) {
        for (Node node : model.getVariables()) {
            if (DataUtils.isBinary(model, model.getColumn(node))) {
                getVarMap().put(node.getName(), 1);
            } else if (node instanceof DiscreteVariable) {
                getVarMap().put(node.getName(), 2);
            } else {
                getVarMap().put(node.getName(), 3);
            }
        }
    }


    private static JScrollPane createScrollPane(JList comp, Dimension dim) {
        JScrollPane pane = new JScrollPane(comp);
        LayoutUtils.setAllSizes(pane, dim);
        return pane;
    }


    private static Box createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalGlue());
        return box;
    }


    private JTextField createResponse(JList list, int width) {
        JTextField pane = new JTextField();
        pane.setFont(getFONT());
        pane.setFocusable(true);
        pane.setEditable(false);
        pane.setBackground(list.getBackground());

        String target = params.getTargetName();
        if (target != null) {
            pane.setText(target);
        } else {
            pane.setText("Hello");
        }
        pane.setCaretPosition(0);
        LayoutUtils.setAllSizes(pane, new Dimension(width, pane.getPreferredSize().height));
        if (target == null) {
            pane.setText(null);
        }
        pane.addFocusListener(new FocusAdapter() {

            public void focusGained(FocusEvent e) {
                getPredictorsList().clearSelection();
            }
        });

        return pane;
    }


    private static JList createList() {
        JList list = new JList(new VariableListModel());
        list.setFont(getFONT());
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


    private String[] getPredictors() {
        ListModel model = getPredictorsList().getModel();
        String[] predictors = new String[model.getSize()];
        for (int i = 0; i < model.getSize(); i++) {
            predictors[i] = (String) model.getElementAt(i);
        }
        return predictors;
    }


    private void addToSource(String var) {
        VariableListModel model = (VariableListModel) getSourceList().getModel();
        model.add(var);
    }


    private boolean isBinary(String node) {
        int i = getVarMap().get(node);
        return i == 1;
    }

    private static Map<String, Integer> getVarMap() {
        return VAR_MAP;
    }

    public static JList getPredictorsList() {
        return PREDICTORS_LIST;
    }

    private static JList getSourceList() {
        return SOURCE_LIST;
    }

    public static JTextField getXField() {
        return X_FIELD;
    }

    public static JTextField getYField() {
        return Y_FIELD;
    }

    public static Font getFONT() {
        return FONT;
    }

    //========================== Inner classes (a lot of'em) =========================================//


    /**
     * A renderer that adds info about whether a variable is binary or not.
     */
    private static class LogisticRegRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String var = (String) value;
            if (var == null) {
                setText(" ");
                return this;
            }
            int binary = getVarMap().get(var);
            if (binary == 1) {
                var += " (Binary)";
            } else if (binary == 2) {
                var += " (Discrete)";
            } else if (binary == 3) {
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


    private class TargetListener extends DropTargetAdapter {

        public void drop(DropTargetDropEvent dtde) {
            Transferable t = dtde.getTransferable();
            Component comp = dtde.getDropTargetContext().getComponent();
            if (comp instanceof JList || comp instanceof JTextField) {
                try {
                    // if response, remove everything first
                    if (comp == getXField()) {
                        String var = getXField().getText();
                        if (var != null && var.length() != 0) {
                            addToSource(var);
                        }
                        List<Comparable> vars = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        if (vars.isEmpty()) {
                            dtde.rejectDrop();
                            return;
                        } else if (1 < vars.size()) {
                            JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this,
                                    "There can only be one response variable.");
                            dtde.rejectDrop();
                            return;
                        } else if (params instanceof LogisticRegressionParams && !isBinary((String) vars.get(0))) {
                            JOptionPane.showMessageDialog(ConditionalIndependenceParamsPanel.this,
                                    "The response variable must be binary");
                            dtde.rejectDrop();
                            return;
                        }
                        getXField().setText((String) vars.get(0));
                        getXField().setCaretPosition(0);
                    } else {
                        JList list = (JList) comp;
                        VariableListModel model = (VariableListModel) list.getModel();
                        List<Comparable> vars = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        model.addAll(vars);
                    }
                    params.setTargetName(getXField().getText());
                    params.setRegressorNames(getPredictors());
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
     * A source/gesture listener for the JLists
     */
    private class SourceListener extends DragSourceAdapter implements DragGestureListener {

        public void dragDropEnd(DragSourceDropEvent evt) {
            if (evt.getDropSuccess()) {
                Component comp = evt.getDragSourceContext().getComponent();
                Transferable t = evt.getDragSourceContext().getTransferable();
                if (t instanceof ListTransferable) {
                    try {
                        //noinspection unchecked
                        List<Comparable> o = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        if (comp instanceof JList) {
                            JList list = (JList) comp;
                            VariableListModel model = (VariableListModel) list.getModel();
                            for (Comparable c : o) {
                                model.removeFirst(c);
                            }
                        } else {
                            JTextField pane = (JTextField) comp;
                            pane.setText(null);
                        }
                        params.setTargetName(getXField().getText());
                        params.setRegressorNames(getPredictors());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        public void dragGestureRecognized(DragGestureEvent dge) {
            Component comp = dge.getComponent();
            List selected = null;
            if (comp instanceof JList) {
                JList list = (JList) comp;
                selected = list.getSelectedValuesList();
            } else {
                JTextField pane = (JTextField) comp;
                String text = pane.getText();
                if (text != null && text.length() != 0) {
                    selected = Collections.singletonList(text);
                }
            }
            if (selected != null) {
                ListTransferable t = new ListTransferable(Arrays.asList(selected));
                dge.startDrag(DragSource.DefaultMoveDrop, t, this);
            }
        }
    }


    /**
     * A basic model for the list (needed an addAll feature, which the detault model didn't have)
     */
    private static class VariableListModel extends AbstractListModel {

        private Vector<Comparable> delegate = new Vector<Comparable>();

        public int getSize() {
            return this.delegate.size();
        }

        public Object getElementAt(int index) {
            return this.delegate.get(index);
        }

        public void remove(Comparable element) {
            int index = this.delegate.indexOf(element);
            if (0 <= index) {
                this.delegate.remove(index);
                this.fireIntervalRemoved(this, index, index);
            }
        }

        public void add(Comparable element) {
            this.delegate.add(element);
            this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
        }


        public void removeFirst(Comparable element) {
            this.delegate.removeElement(element);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll(List<? extends Comparable> elements) {
            this.delegate.removeAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void addAll(List<? extends Comparable> elements) {
            this.delegate.addAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll() {
            this.delegate.clear();
            this.fireContentsChanged(this, 0, 0);
        }


        public void sort() {
            Collections.sort(this.delegate);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }


    }

    /**
     * A basic transferable.
     */
    private static class ListTransferable implements Transferable {

        private final static DataFlavor FLAVOR = getListDataFlavor();

        private List object;

        public ListTransferable(List object) {
            if (object == null) {
                throw new NullPointerException();
            }
            this.object = object;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{FLAVOR};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor == FLAVOR;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (FLAVOR != flavor) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.object;
        }
    }


}





