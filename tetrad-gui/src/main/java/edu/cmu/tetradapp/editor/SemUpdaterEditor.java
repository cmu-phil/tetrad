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
import edu.cmu.tetradapp.model.SemImWrapper;
import edu.cmu.tetradapp.model.SemUpdaterWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Lets the user calculate updated probabilities for a SEM.
 *
 * @author Joseph Ramsey
 */
public class SemUpdaterEditor extends JPanel {

    private static final long serialVersionUID = 7548536266087867739L;

    /**
     * The SEM updater being edited.
     */
    private final SemUpdater semUpdater;

    private final Map<JCheckBox, Integer> checkBoxesToVariables = new HashMap<>();
    private final Map<Integer, JCheckBox> variablesToCheckboxes = new HashMap<>();
    private final Map<Integer, DoubleTextField> variablesToTextFields = new HashMap<>();
    private final SemImEditor semImEditor;
    private final LinkedList<DoubleTextField> focusTraversalOrder = new LinkedList<>();
    private final Map<DoubleTextField, Integer> labels = new HashMap<>();

    /**
     * Constructs a new instantiated model editor from a SEM Updater.
     *
     * @param semUpdater
     */
    public SemUpdaterEditor(final SemUpdater semUpdater) {
        if (semUpdater == null) {
            throw new NullPointerException(
                    "Bayes semUpdater must not be null.");
        }

        this.semUpdater = semUpdater;
        setLayout(new BorderLayout());
        setName("Bayes Updater Editor");

        final Box b1 = Box.createHorizontalBox();

        this.semImEditor = new SemImEditor(new SemImWrapper(semUpdater.getSemIm()));
        this.semImEditor.add(getUpdatePanel(), BorderLayout.WEST);
        this.semImEditor.setEditable(false);
        b1.add(this.semImEditor);

        add(b1, BorderLayout.CENTER);
    }

    private Box getUpdatePanel() {
        final SemEvidence evidence = this.semUpdater.getEvidence();
        this.focusTraversalOrder.clear();

        final Box b = Box.createVerticalBox();

        final Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>"
                + "In the list below, specify values for variables you have evidence "
                + "<br>for. Click the 'Do Update Now' button to view updated model."));
        b0.add(Box.createHorizontalGlue());
        b.add(b0);
        b.add(Box.createVerticalStrut(10));

        final Box d = Box.createHorizontalBox();
        d.add(new JLabel("Variable = value"));
        d.add(Box.createHorizontalGlue());
        d.add(new JLabel("Manipulated"));
        b.add(d);

        for (int i = 0; i < evidence.getNumNodes(); i++) {
            final Box c = Box.createHorizontalBox();
            final SemIm semIm = evidence.getSemIm();
            final Node node = semIm.getVariableNodes().get(i);
            final String name = node.getName();
            final JLabel label = new JLabel(name + " =  ") {
                private static final long serialVersionUID = 820570350956700782L;

                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            c.add(label);

            final double mean = evidence.getProposition().getValue(i);
            final DoubleTextField field = new DoubleTextField(mean, 5, NumberFormatUtil.getInstance().getNumberFormat());

            field.setFilter((value, oldValue) -> {
                try {
                    final int nodeIndex = this.labels.get(field);

                    if (Double.isNaN(value)
                            && evidence.isManipulated(nodeIndex)) {
                        throw new IllegalArgumentException();
                    }

                    evidence.getProposition().setValue(nodeIndex, value);
//                    semIm.setMean(node, value);
                    final SemIm updatedSem = this.semUpdater.getUpdatedSemIm();
                    this.semImEditor.displaySemIm(updatedSem,
                            this.semImEditor.getTabSelectionIndex(),
                            this.semImEditor.getMatrixSelection());
                    return value;
                } catch (final IllegalArgumentException e) {
                    return oldValue;
                }
            });

            this.labels.put(field, i);
            this.variablesToTextFields.put(i, field);
            this.focusTraversalOrder.add(field);

            c.add(field);
            c.add(Box.createHorizontalStrut(2));
            c.add(Box.createHorizontalGlue());

            final JCheckBox checkbox = new JCheckBox() {
                private static final long serialVersionUID = -3808843047563493212L;

                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };

            checkbox.setSelected(evidence.isManipulated(i));
            this.checkBoxesToVariables.put(checkbox, i);
            this.variablesToCheckboxes.put(i, checkbox);
            checkbox.addActionListener((e) -> {
                final JCheckBox chkbox = (JCheckBox) e.getSource();
                final boolean selected = chkbox.isSelected();
                final Integer o = this.checkBoxesToVariables.get(chkbox);

                // If no value has been set for this variable, set it to
                // the mean.
                final double value = evidence.getProposition().getValue(o);
//
                if (Double.isNaN(value)) {
                    final DoubleTextField dblTxtField = this.variablesToTextFields.get(o);
                    final SemIm semIM = this.semUpdater.getSemIm();
                    final Node varNode = semIM.getVariableNodes().get(o);
                    final double semIMMean = semIM.getMean(varNode);
                    dblTxtField.setValue(semIMMean);
                }

                this.semUpdater.getEvidence().setManipulated(o, selected);
                final SemIm updatedSem = this.semUpdater.getUpdatedSemIm();
                this.semImEditor.displaySemIm(updatedSem,
                        this.semImEditor.getTabSelectionIndex(),
                        this.semImEditor.getMatrixSelection());
            });
            checkbox.setBackground(Color.WHITE);
            checkbox.setBorder(null);
            c.add(checkbox);
            c.setMaximumSize(new Dimension(1000, 30));
            b.add(c);
        }

        b.add(Box.createVerticalGlue());

        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        final JButton button = new JButton("Do Update Now");

        button.addActionListener((e) -> {
            final SemIm updatedSem = this.semUpdater.getUpdatedSemIm();
            this.semImEditor.displaySemIm(updatedSem,
                    this.semImEditor.getTabSelectionIndex(),
                    this.semImEditor.getMatrixSelection());
//            semUpdater.setEvidence(new SemEvidence(updatedSem));
        });

        b2.add(button);
        b.add(b2);

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(final Container focusCycleRoot,
                                               final Component aComponent) {
                final int index = SemUpdaterEditor.this.focusTraversalOrder.indexOf(aComponent);
                final int size = SemUpdaterEditor.this.focusTraversalOrder.size();

                if (index != -1) {
                    return SemUpdaterEditor.this.focusTraversalOrder.get((index + 1) % size);
                } else {
                    return getFirstComponent(focusCycleRoot);
                }
            }

            @Override
            public Component getComponentBefore(final Container focusCycleRoot,
                                                final Component aComponent) {
                final int index = SemUpdaterEditor.this.focusTraversalOrder.indexOf(aComponent);
                final int size = SemUpdaterEditor.this.focusTraversalOrder.size();

                if (index != -1) {
                    return SemUpdaterEditor.this.focusTraversalOrder.get((index - 1) % size);
                } else {
                    return getFirstComponent(focusCycleRoot);
                }
            }

            @Override
            public Component getFirstComponent(final Container focusCycleRoot) {
                return SemUpdaterEditor.this.focusTraversalOrder.getFirst();
            }

            @Override
            public Component getLastComponent(final Container focusCycleRoot) {
                return SemUpdaterEditor.this.focusTraversalOrder.getLast();
            }

            @Override
            public Component getDefaultComponent(final Container focusCycleRoot) {
                return getFirstComponent(focusCycleRoot);
            }
        });

        setFocusCycleRoot(true);

        return b;
    }

    /**
     * Constructs a new instantiated model editor from a SEM IM wrapper.
     *
     * @param wrapper
     */
    public SemUpdaterEditor(final SemUpdaterWrapper wrapper) {
        this(wrapper.getSemUpdater());
    }

    //================================PUBLIC METHODS========================//

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }
}
