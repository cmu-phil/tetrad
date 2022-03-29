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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.sem.StandardizedSemIm.ParameterRange;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.DoubleTextField.Filter;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.List;

/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
final class StandardizedSemImGraphicalEditor extends JPanel {

    static final long serialVersionUID = 23L;

    /**
     * Font size for parameter values in the graph.
     */
    private static final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * Background color of the edit panel when you click on the parameters.
     */
    private static final Color LIGHT_YELLOW = new Color(255, 255, 215);

    /**
     * The SemIM being edited.
     */
    private final StandardizedSemIm semIm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * Stores the last active edge so that it can be reset properly.
     */
    private Object lastEditedObject;

    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;

    /**
     * True iff this graphical display is editable.
     */
    private boolean editable = true;

    /**
     * The minimum of the range being edited.
     */
    private double min = Double.NEGATIVE_INFINITY;

    /**
     * The maximum of the range being edited.
     */
    private double max = Double.POSITIVE_INFINITY;

    /**
     * The edge being edited.
     */
    private Edge editingEdge;

    /**
     * The label for the minimum range value.
     */
    private final JLabel minRangeLabel;

    /**
     * The label for the maximum range value.
     */
    private final JLabel maxRangeLabel;

    /**
     * The label that displayed the getModel edge being edited.
     */
    private final JLabel edgeLabel;

    /**
     * The textfield that displays the getModel value of the parameter being
     * edited.
     */
    private final DoubleTextField valueField;

    /**
     * The slider that lets the user select a value within range for the
     * getModel parameter being edited.
     */
    private final JSlider slider;

    private boolean enableEditing = true;

    /**
     * Constructs a SemIm graphical editor for the given SemIm.
     */
    public StandardizedSemImGraphicalEditor(StandardizedSemIm semIm, StandardizedSemImEditor editor) {
        this.semIm = semIm;
        /*
      The editor that sits inside the SemImEditor that allows the user to edit
      the SemIm graphically.
         */
        StandardizedSemImEditor editor1 = editor;

        this.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(this.workbench());
        scroll.setPreferredSize(new Dimension(450, 400));

        this.add(scroll, BorderLayout.CENTER);

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();

        minRangeLabel = new JLabel();
        maxRangeLabel = new JLabel();
        edgeLabel = new JLabel();
        valueField = new DoubleTextField(0, 8, NumberFormatUtil.getInstance().getNumberFormat());
        slider = new JSlider();
        this.setEditorToEdge(semIm.getSemPm().getGraph().getEdges().iterator().next());

        valueField.setFilter(new Filter() {
            public double filter(double value, double oldValue) {
                if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY || (value < max && value > min)) {
                    StandardizedSemImGraphicalEditor.this.setSliderToValue(value, min, max);
                    StandardizedSemImGraphicalEditor.this.semIm().setParameterValue(editingEdge, value);
                    StandardizedSemImGraphicalEditor.this.resetLabels();
                    firePropertyChange("modelChanged", null, null);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });

        valueField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                try {
                    double value = Double.parseDouble(valueField.getText());
                    semIm.setParameterValue(editingEdge, value);
                    StandardizedSemImGraphicalEditor.this.resetLabels();
                } catch (NumberFormatException e1) {
                    // Do nothing.
                }
            }
        });

        slider.addChangeListener(new ChangeListener() {
            public synchronized void stateChanged(ChangeEvent e) {
                double value = StandardizedSemImGraphicalEditor.this.getValueFromSlider(min, max);
                valueField.setValue(value);
//                semIm().setParameterValue(editingEdge, value);
                StandardizedSemImGraphicalEditor.this.resetLabels();
            }
        });

        b1.add(new JLabel("Adjustable range for edge "));
        b1.add(edgeLabel);
        b1.add(new JLabel(" = ("));
        b1.add(minRangeLabel);
        b1.add(new JLabel(", "));
        b1.add(maxRangeLabel);
        b1.add(new JLabel(")"));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);
        b.add(Box.createVerticalStrut(5));

        Box b2 = Box.createHorizontalBox();

        b2.add(slider);
        b2.add(Box.createHorizontalStrut(5));
        b2.add(new JLabel("Value ="));
        b2.add(Box.createHorizontalStrut(5));
        valueField.setMaximumSize(valueField.getPreferredSize());
        b2.add(valueField);
        b.add(b2);

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        this.add(b, BorderLayout.SOUTH);

        this.setBorder(new TitledBorder("Click parameter values to edit. (Error variances are not parameters.)"));

        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        this.setSavedTooltipDelay(toolTipManager.getInitialDelay());

        // Laborious code that follows is intended to make sure tooltips come
        // almost immediately within the sem im editor but more slowly outside.
        // Ugh.
        this.workbench().addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                StandardizedSemImGraphicalEditor.this.resetLabels();
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(100);
            }

            public void componentHidden(ComponentEvent e) {
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(StandardizedSemImGraphicalEditor.this.getSavedTooltipDelay());
            }
        });

        this.workbench().addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (StandardizedSemImGraphicalEditor.this.workbench().contains(e.getPoint())) {

                    // Commenting out the resetLabels, since it seems to make
                    // people confused when they can't move the mouse away
                    // from the text field they are editing without the
                    // textfield disappearing. jdramsey 3/16/2005.
//                    resetLabels();
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }
            }

            public void mouseExited(MouseEvent e) {
                if (!StandardizedSemImGraphicalEditor.this.workbench().contains(e.getPoint())) {
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(StandardizedSemImGraphicalEditor.this.getSavedTooltipDelay());
                }
            }
        });

        // Make sure the graphical editor reflects changes made to parameters
        // in other editors.
        this.addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                StandardizedSemImGraphicalEditor.this.resetLabels();
            }
        });
    }

    //=================================PUBLIC METHODS=======================================//
    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public void setEditable(boolean editable) {
        this.workbench().setAllowEdgeReorientations(editable);
        this.workbench().setAllowDoubleClickActions(editable);
        this.workbench().setAllowNodeEdgeSelection(editable);
        this.editable = editable;
    }

    //========================PRIVATE METHODS===========================//
    private void beginEdgeEdit(Edge edge) {
        this.finishEdit();

        if (!this.isEditable()) {
            return;
        }

        this.setEditorToEdge(edge);

        this.setLastEditedObject(edge);
        this.workbench().repaint();
    }

    private void setEditorToEdge(Edge edge) {
        if (edge.getNode1().getNodeType() == NodeType.ERROR || edge.getNode2().getNodeType() == NodeType.ERROR) {
            return;
        }

        if (editingEdge != null) {
            try {
                double value = Double.parseDouble(valueField.getText());
                semIm.setParameterValue(editingEdge, value);
                this.resetLabels();
            } catch (NumberFormatException e) {
                // Do nothing.
            }
        }

        double d = this.semIm().getParameterValue(edge);

        ParameterRange range = this.semIm().getParameterRange(edge);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        editingEdge = edge;
        edgeLabel.setText(edge.toString());
        min = range.getLow();
        max = range.getHigh();
        minRangeLabel.setText(nf.format(range.getLow()));
        maxRangeLabel.setText(nf.format(range.getHigh()));

        this.setSliderToValue(d, min, max);
        valueField.setValue(d);
        valueField.grabFocus();
        valueField.setCaretPosition(0);
        valueField.moveCaretPosition(valueField.getText().length());
    }

    private void finishEdit() {
        if (this.lastEditedObject() != null) {
            this.resetLabels();
        }
    }

    private StandardizedSemIm semIm() {
        return semIm;
    }

    private Graph graph() {
        return semIm().getSemPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (this.getWorkbench() == null) {
            workbench = new GraphWorkbench(this.graph());
            this.getWorkbench().setAllowDoubleClickActions(false);
            this.getWorkbench().setAllowEdgeReorientations(false);
            this.getWorkbench().addPropertyChangeListener(
                    new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            if ("BackgroundClicked".equals(
                                    evt.getPropertyName())) {
                                StandardizedSemImGraphicalEditor.this.finishEdit();
                            }
                        }
                    });
            this.resetLabels();
        }
        return this.getWorkbench();
    }

    private void setLastEditedObject(Object o) {
        lastEditedObject = o;
    }

    private Object lastEditedObject() {
        return lastEditedObject;
    }

    public void resetLabels() {
        for (Object o : this.graph().getEdges()) {
            this.resetEdgeLabel((Edge) (o));
        }

        List<Node> nodes = this.graph().getNodes();

        for (Object node : nodes) {
            this.resetNodeLabel((Node) node);
        }

        this.workbench().repaint();
    }

    private double getValueFromSlider(double c1, double c2) {

        int n = slider.getMaximum() - slider.getMinimum();
        int slider = this.slider.getValue();

        if (c1 == Double.NEGATIVE_INFINITY && slider == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        if (c2 == Double.POSITIVE_INFINITY && slider == 100) {
            return Double.POSITIVE_INFINITY;
        }

        return this.sliderToValue(slider, c1, c2, n);
    }

    private void setSliderToValue(double value, double c1, double c2) {
        if (value == Double.NEGATIVE_INFINITY) {
            value = Double.MIN_VALUE;
        } else if (value == Double.POSITIVE_INFINITY) {
            value = Double.MAX_VALUE;
        }

        int n = slider.getMaximum() - slider.getMinimum();
        double x;

        int slider = this.valueToSlider(value, c1, c2, n);
        this.slider.setValue(slider);
    }

    private double sliderToValue(int slider, double min, double max, int n) {
        double f;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            f = min + ((double) slider / n) * (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            f = min + Math.tan(((double) slider / n) * (Math.PI / 2));
        } else if (max != Double.POSITIVE_INFINITY) {
            f = max + Math.tan(-(((double) n - slider) / n) * (Math.PI / 2));
//            System.out.println("slider = " + slider + " min = " + min + " max = " + max + "  f = " + f);
        } else {
            f = Math.tan(-Math.PI / 2 + ((double) slider / n) * Math.PI);
        }
        return f;
    }

    private int valueToSlider(double value, double min, double max, int n) {
        double x;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            x = n * (value - min) / (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            x = (2. * n) / Math.PI * Math.atan(value - min);
        } else if (max != Double.POSITIVE_INFINITY) {
            x = n + (2. * n) / Math.PI * Math.atan(value - max);
//            System.out.println("value = " + value + " x = " + x);
        } else {
            x = (n / Math.PI) * (Math.atan(value) + Math.PI / 2);
        }

        int slider = (int) Math.round(x);
        if (slider > 100) {
            slider = 100;
        }
        if (slider < 0) {
            slider = 0;
        }
        return slider;
    }

    private void resetEdgeLabel(Edge edge) {
        if (this.semIm().containsParameter(edge)) {
            double val = this.semIm().getParameterValue(edge);

            JLabel label = new JLabel();

            if (Edges.isBidirectedEdge(edge)) {
                label.setForeground(Color.GREEN.darker().darker());
            }

            label.setBackground(Color.white);
            label.setOpaque(true);
            label.setFont(SMALL_FONT);
            label.setText(" " + this.asString(val) + " ");
            label.addMouseListener(new EdgeMouseListener(edge, this));

            this.workbench().setEdgeLabel(edge, label);
        } else {
            this.workbench().setEdgeLabel(edge, null);
        }
    }

    private void resetNodeLabel(Node node) {
        JLabel label = new JLabel();
        label.setBackground(Color.WHITE);
        label.addMouseListener(new NodeMouseListener(node, this));
        label.setFont(SMALL_FONT);

        NodeType nodeType = node.getNodeType();

        if (nodeType != NodeType.ERROR) {
            return;
        }

        double error = semIm.getErrorVariance(node);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        label.setText(nf.format(error));

        // Offset the nodes slightly differently depending on whether
        // they're error nodes or not.
        if (nodeType == NodeType.ERROR) {
            label.setOpaque(false);
            this.workbench().setNodeLabel(node, label, -10, -10);
        } else {
            label.setOpaque(false);
            this.workbench().setNodeLabel(node, label, 0, 0);
        }
    }

    private void setEdgeValue(Edge edge, String text) {
        this.resetLabels();
        this.workbench().repaint();
        this.setLastEditedObject(null);
    }

    private void setNodeValue(Node node, String text) {
        this.resetLabels();
        this.workbench().repaint();
        this.setLastEditedObject(null);
    }

    private int getSavedTooltipDelay() {
        return savedTooltipDelay;
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        if (this.savedTooltipDelay == 0) {
            this.savedTooltipDelay = savedTooltipDelay;
        }
    }

    private String asString(double value) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return nf.format(value);
        }
    }

    private boolean isEditable() {
        return editable;
    }

    public boolean isEnableEditing() {
        return enableEditing;
    }

    public void enableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (workbench != null) {
            workbench.enableEditing(enableEditing);
        }
    }

    static final class EdgeMouseListener extends MouseAdapter {

        private final Edge edge;
        private final StandardizedSemImGraphicalEditor editor;

        public EdgeMouseListener(Edge edge, StandardizedSemImGraphicalEditor editor) {
            this.edge = edge;
            this.editor = editor;
        }

        private Edge getEdge() {
            return edge;
        }

        private StandardizedSemImGraphicalEditor getEditor() {
            return editor;
        }

        public void mousePressed(MouseEvent e) {
            this.getEditor().beginEdgeEdit(this.getEdge());
        }
    }

    static final class NodeMouseListener extends MouseAdapter {

        private final Node node;
        private final StandardizedSemImGraphicalEditor editor;

        public NodeMouseListener(Node node, StandardizedSemImGraphicalEditor editor) {
            this.node = node;
            this.editor = editor;
        }

        private Node getNode() {
            return node;
        }

        private StandardizedSemImGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
//            getEditor().beginNodeEdit(getIndex());
        }
    }

    static final class EdgeActionListener implements ActionListener {

        private final StandardizedSemImGraphicalEditor editor;
        private final Edge edge;

        public EdgeActionListener(StandardizedSemImGraphicalEditor editor, Edge edge) {
            this.editor = editor;
            this.edge = edge;
        }

        public void actionPerformed(ActionEvent ev) {
            DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
            String s = doubleTextField.getText();
            this.getEditor().setEdgeValue(this.getEdge(), s);
        }

        private StandardizedSemImGraphicalEditor getEditor() {
            return editor;
        }

        private Edge getEdge() {
            return edge;
        }
    }

    static final class NodeActionListener implements ActionListener {

        private final StandardizedSemImGraphicalEditor editor;
        private final Node node;

        public NodeActionListener(StandardizedSemImGraphicalEditor editor, Node node) {
            this.editor = editor;
            this.node = node;
        }

        public void actionPerformed(ActionEvent ev) {
            DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
            String s = doubleTextField.getText();
            this.getEditor().setNodeValue(this.getNode(), s);
        }

        private StandardizedSemImGraphicalEditor getEditor() {
            return editor;
        }

        private Node getNode() {
            return node;
        }
    }
}
