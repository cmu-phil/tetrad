///////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.bayes.Proposition;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;

/**
 * Edits evidence for a Bayes net--that is, which categories (perhaps
 * disjunctive) are known for which variables, and which variables are
 * manipulated.
 *
 * @author Joseph Ramsey
 */
class EvidenceEditor extends JPanel {
    private final Evidence evidence;
    private final JToggleButton[][] buttons;

    private final HashMap<JToggleButton, Integer> buttonsToVariables =
            new HashMap<>();
    private final HashMap<JToggleButton, Integer> buttonsToCategories =
            new HashMap<>();
    private final HashMap<JCheckBox, Integer> checkBoxesToVariables =
            new HashMap<>();
    private final HashMap<Integer, JCheckBox> variablesToCheckboxes =
            new HashMap<>();

    public EvidenceEditor(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        this.evidence = evidence;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box d = Box.createHorizontalBox();
        d.add(new JLabel("Variable/Categories"));
        d.add(Box.createHorizontalGlue());
        d.add(new JLabel("Manipulated"));
        add(d);

        this.buttons = new JToggleButton[evidence.getNumNodes()][];

        for (int i = 0; i < evidence.getNumNodes(); i++) {
            Box c = Box.createHorizontalBox();
            c.add(new JLabel(evidence.getNode(i).getName() + ":  ") {
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            });

            this.buttons[i] = new JToggleButton[evidence.getNumCategories(i)];
            Node node = evidence.getNode(i);

            for (int j = 0; j < evidence.getNumCategories(i); j++) {
                String name = evidence.getCategory(node, j);
                JToggleButton button = new JToggleButton(" " + name + " ") {
                    public Dimension getMaximumSize() {
                        return getPreferredSize();
                    }
                };

                button.setBorder(new CompoundBorder(
                        new LineBorder(Color.DARK_GRAY, 1, true),
                        new EmptyBorder(0, 2, 0, 1)));
                button.setBackground(Color.WHITE);
                button.setUI(new BasicButtonUI());
                button.setFont(new Font("Serif", Font.BOLD, 12));

                this.buttonsToVariables.put(button, i);
                this.buttonsToCategories.put(button, j);

                this.buttons[i][j] = button;

                button.addActionListener(e -> {
                    JToggleButton button1 = (JToggleButton) e.getSource();
                    int i1 = EvidenceEditor.this.buttonsToVariables.get(button1);
                    int j1 = EvidenceEditor.this.buttonsToCategories.get(button1);

                    Proposition proposition =
                            getEvidence().getProposition();

                    if (proposition.getNumAllowed(i1) ==
                            getEvidence().getNumCategories(i1)) {
                        proposition.setCategory(i1, j1);
                    } else if (proposition.getNumAllowed(i1) == 1) {
                        if (proposition.getSingleCategory(i1) == j1) {
                            proposition.removeCategory(i1, j1);
                        } else {
                            if ((ActionEvent.SHIFT_MASK &
                                    e.getModifiers()) != 1) {
                                proposition.setVariable(i1, false);
                            }

                            proposition.addCategory(i1, j1);
                        }
                    } else {
                        if (proposition.isAllowed(i1, j1)) {
                            proposition.removeCategory(i1, j1);
                        } else {
                            proposition.addCategory(i1, j1);
                        }
                    }

                    if (proposition.getNumAllowed(i1) == 0) {
                        proposition.setVariable(i1, true);
                    }

                    resetSelected(i1);
                });

                c.add(button);
                c.add(Box.createHorizontalStrut(2));
            }

            c.add(Box.createHorizontalGlue());
            JCheckBox checkbox = new JCheckBox() {
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            checkbox.setSelected(getEvidence().isManipulated(i));
            this.checkBoxesToVariables.put(checkbox, i);
            this.variablesToCheckboxes.put(i, checkbox);
            checkbox.addActionListener(e -> {
                JCheckBox checkbox1 = (JCheckBox) e.getSource();
                boolean selected = checkbox1.isSelected();
                int variable = this.checkBoxesToVariables.get(checkbox1);

                if (getEvidence().getProposition().getSingleCategory(
                        variable) == -1) {
                    JOptionPane.showMessageDialog(checkbox1,
                            "Please choose a single category to manipulate on.");
                    checkbox1.setSelected(false);
                    getEvidence().setManipulated(variable, false);
                } else {
                    getEvidence().setManipulated(variable, selected);
                }
            });
            checkbox.setBackground(Color.WHITE);
            checkbox.setBorder(null);
            c.add(checkbox);
            c.setMaximumSize(new Dimension(1000, 30));
            add(c);

            resetSelected(i);
        }
    }

    private void highlightCorrectly(Proposition proposition) {
        for (JToggleButton _button : this.buttonsToVariables.keySet()) {
            int _i = this.buttonsToVariables.get(_button);
            int _j = this.buttonsToCategories.get(_button);

            if (proposition.isConditioned(_i) && proposition.isAllowed(_i, _j)) {
                _button.setBackground(Color.LIGHT_GRAY);
            } else {
                _button.setBackground(Color.WHITE);
            }
        }
    }

    public Evidence getEvidence() {
        return this.evidence;
    }

    private void resetSelected(int variable) {
        if (this.evidence.hasNoEvidence(variable)) {
            for (int j = 0; j < this.buttons[variable].length; j++) {
                this.buttons[variable][j].setSelected(false);
            }
        } else {
            for (int j = 0; j < this.buttons[variable].length; j++) {
                this.buttons[variable][j].setSelected(
                        this.evidence.getProposition().isAllowed(variable, j));
            }
        }

        if (this.evidence.getProposition().getSingleCategory(variable) == -1) {
            JCheckBox checkbox = this.variablesToCheckboxes.get(variable);
            checkbox.setSelected(false);
            getEvidence().setManipulated(variable, false);
        }


        highlightCorrectly(this.evidence.getProposition());
    }
}





