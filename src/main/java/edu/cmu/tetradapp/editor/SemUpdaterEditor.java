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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemEvidence;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemUpdater;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.SemUpdaterWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Lets the user calculate updated probabilities for a SEM.
 *
 * @author Joseph Ramsey
 */
public class SemUpdaterEditor extends JPanel {

    /**
     * The SEM updater being edited.
     */
    private SemUpdater semUpdater;

    private Map<JCheckBox,Integer> checkBoxesToVariables = new HashMap<JCheckBox, Integer>();
    private Map<Integer,JCheckBox> variablesToCheckboxes = new HashMap<Integer, JCheckBox>();
    private Map<Integer, DoubleTextField> variablesToTextFields = new HashMap<Integer, DoubleTextField>();
    private SemImEditor semImEditor;
    private LinkedList<DoubleTextField> focusTraversalOrder = new LinkedList<DoubleTextField>();
    private Map<DoubleTextField, Integer> labels = new HashMap<DoubleTextField, Integer>();

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new instanted model editor from a SEM Updater.
     */
    public SemUpdaterEditor(SemUpdater semUpdater) {
        if (semUpdater == null) {
            throw new NullPointerException(
                    "Bayes semUpdater must not be null.");
        }

        this.semUpdater = semUpdater;
        setLayout(new BorderLayout());
        setName("Bayes Updater Editor");

        Box b1 = Box.createHorizontalBox();

        semImEditor = new SemImEditor(semUpdater.getSemIm(), "Graphical View",
                "Tabular View");
        semImEditor.add(getUpdatePanel(), BorderLayout.WEST);
        semImEditor.setEditable(false);
        b1.add(semImEditor);

        add(b1, BorderLayout.CENTER);
    }

    private Box getUpdatePanel() {
        final SemEvidence evidence = semUpdater.getEvidence();
        focusTraversalOrder.clear();

        Box b = Box.createVerticalBox();

        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>" +
                "In the list below, specify values for variables you have evidence " +
                "<br>for. Click the 'Do Update Now' button to view updated model."));
        b0.add(Box.createHorizontalGlue());
        b.add(b0);
        b.add(Box.createVerticalStrut(10));

        Box d = Box.createHorizontalBox();
        d.add(new JLabel("Variable = value"));
        d.add(Box.createHorizontalGlue());
        d.add(new JLabel("Manipulated"));
        b.add(d);

        for (int i = 0; i < evidence.getNumNodes(); i++) {
            Box c = Box.createHorizontalBox();
            SemIm semIm = evidence.getSemIm();
            Node node = semIm.getVariableNodes().get(i);
            String name = node.getName();
            JLabel label = new JLabel(name + " =  ") {
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            c.add(label);

            double mean = evidence.getProposition().getValue(i);
            final DoubleTextField field = new DoubleTextField(mean, 5, NumberFormatUtil.getInstance().getNumberFormat());

            field.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        final int nodeIndex = labels.get(field);

                        if (Double.isNaN(value) &&
                                evidence.isManipulated(nodeIndex)) {
                            throw new IllegalArgumentException();
                        }

                        evidence.getProposition().setValue(nodeIndex, value);
                        SemIm updatedSem = semUpdater.getUpdatedSemIm();
                        semImEditor.setSemIm(updatedSem,
                                semImEditor.getTabSelectionIndex(),
                                semImEditor.getMatrixSelection());
                        return value;
                    }
                    catch (IllegalArgumentException e) {
                        return oldValue;
                    }
                }
            });

            labels.put(field, i);
            variablesToTextFields.put(i, field);
            focusTraversalOrder.add(field);

            c.add(field);
            c.add(Box.createHorizontalStrut(2));
            c.add(Box.createHorizontalGlue());

            JCheckBox checkbox = new JCheckBox() {
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };

            checkbox.setSelected(evidence.isManipulated(i));
            checkBoxesToVariables.put(checkbox, i);
            variablesToCheckboxes.put(i, checkbox);
            checkbox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JCheckBox checkbox = (JCheckBox) e.getSource();
                    boolean selected = checkbox.isSelected();
                    Integer o = checkBoxesToVariables.get(checkbox);

                    // If no value has been set for this variable, set it to
                    // the mean.
                    double value = evidence.getProposition().getValue(o);

                    if (Double.isNaN(value)) {
                        DoubleTextField field = variablesToTextFields.get(o);
                        SemIm semIm = semUpdater.getSemIm();
                        Node node = semIm.getVariableNodes().get(o);
                        double mean = semIm.getMean(node);
                        field.setValue(mean);
                    }

                    semUpdater.getEvidence().setManipulated(o, selected);
                    SemIm updatedSem = semUpdater.getUpdatedSemIm();
                    semImEditor.setSemIm(updatedSem,
                            semImEditor.getTabSelectionIndex(),
                            semImEditor.getMatrixSelection());
                }
            });
            checkbox.setBackground(Color.WHITE);
            checkbox.setBorder(null);
            c.add(checkbox);
            c.setMaximumSize(new Dimension(1000, 30));
            b.add(c);
        }

        b.add(Box.createVerticalGlue());

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        JButton button = new JButton("Do Update Now");

        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SemIm updatedSem = semUpdater.getUpdatedSemIm();
                semImEditor.setSemIm(updatedSem,
                        semImEditor.getTabSelectionIndex(),
                        semImEditor.getMatrixSelection());
            }
        });

        b2.add(button);
        b.add(b2);

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            public Component getComponentAfter(Container focusCycleRoot,
                    Component aComponent) {
                int index = focusTraversalOrder.indexOf(aComponent);
                int size = focusTraversalOrder.size();

                if (index != -1) {
                    return focusTraversalOrder.get((index + 1) % size);
                }
                else {
                    return getFirstComponent(focusCycleRoot);
                }
            }

            public Component getComponentBefore(Container focusCycleRoot,
                    Component aComponent) {
                int index = focusTraversalOrder.indexOf(aComponent);
                int size = focusTraversalOrder.size();

                if (index != -1) {
                    return focusTraversalOrder.get((index - 1) % size);
                }
                else {
                    return getFirstComponent(focusCycleRoot);
                }
            }

            public Component getFirstComponent(Container focusCycleRoot) {
                return focusTraversalOrder.getFirst();
            }

            public Component getLastComponent(Container focusCycleRoot) {
                return focusTraversalOrder.getLast();
            }

            public Component getDefaultComponent(Container focusCycleRoot) {
                return getFirstComponent(focusCycleRoot);
            }
        });

        setFocusCycleRoot(true);

        return b;
    }

    /**
     * Constructs a new instanted model editor from a SEM IM wrapper.
     */
    public SemUpdaterEditor(SemUpdaterWrapper wrapper) {
        this(wrapper.getSemUpdater());
    }

    //================================PUBLIC METHODS========================//

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }
}





