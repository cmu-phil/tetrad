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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.JoinDatasets;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.datamanip.JoinDatasetsWrapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parameter editor for {@link JoinDatasetsWrapper}: which parent supplies the left (primary) table, one or more
 * key pairs (a left variable matched to a right variable), the join type, whether a right key may repeat, and
 * the suffixes for colliding column names. The parameters are written when the user clicks OK, after validation.
 *
 * @author josephramsey
 */
public class JoinDatasetsParamsEditor extends JPanel implements FinalizingParameterEditor {

    private static final long serialVersionUID = 23L;

    private Parameters params;
    private final List<DataWrapper> parents = new ArrayList<>();

    private JComboBox<String> leftParentBox;
    private JLabel rightParentLabel;
    private final List<KeyRow> keyRows = new ArrayList<>();
    private JPanel keyRowsPanel;
    private JRadioButton leftJoin;
    private JRadioButton innerJoin;
    private JRadioButton fullOuterJoin;
    private JCheckBox allowOneToMany;
    private JTextField leftSuffix;
    private JTextField rightSuffix;

    /**
     * Constructs the editor; call {@link #setup()} to build it.
     */
    public JoinDatasetsParamsEditor() {
        super(new BorderLayout());
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParentModels(Object[] parentModels) {
        this.parents.clear();
        if (parentModels == null) return;

        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper data) {
                this.parents.add(data);
            } else if (parent instanceof Object[] array) {
                // Tolerate the constructor-argument form (DataWrapper[], Parameters) as well as the flat list.
                for (Object o : array) if (o instanceof DataWrapper data) this.parents.add(data);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean mustBeShown() {
        return true;
    }

    /**
     * Builds the panel.
     */
    public void setup() {
        if (this.parents.size() != 2) {
            add(new JLabel("Join needs exactly two data parents; found " + this.parents.size() + "."),
                    BorderLayout.CENTER);
            return;
        }

        String[] names = {parentName(0), parentName(1)};
        this.leftParentBox = new JComboBox<>(names);
        String savedLeft = this.params.getString(JoinDatasetsWrapper.LEFT_PARENT, "");
        this.leftParentBox.setSelectedIndex(names[1].equals(savedLeft) ? 1 : 0);
        this.leftParentBox.setMaximumSize(this.leftParentBox.getPreferredSize());
        this.rightParentLabel = new JLabel();
        this.leftParentBox.addActionListener(e -> onLeftParentChanged(true));

        Box parentsBox = Box.createHorizontalBox();
        parentsBox.add(new JLabel("Left (primary) table: "));
        parentsBox.add(this.leftParentBox);
        parentsBox.add(Box.createHorizontalStrut(20));
        parentsBox.add(new JLabel("Right table: "));
        parentsBox.add(this.rightParentLabel);
        parentsBox.add(Box.createHorizontalGlue());

        Box keyHeader = Box.createHorizontalBox();
        keyHeader.add(new JLabel("Key variables (a left variable matched to a right variable; add rows for a "
                + "composite key):"));
        keyHeader.add(Box.createHorizontalGlue());

        this.keyRowsPanel = new JPanel();
        this.keyRowsPanel.setLayout(new BoxLayout(this.keyRowsPanel, BoxLayout.Y_AXIS));

        JButton addKey = new JButton("Add key pair");
        addKey.addActionListener(e -> {
            addKeyRow(null, null);
            refreshKeyRows();
        });
        Box addBox = Box.createHorizontalBox();
        addBox.add(addKey);
        addBox.add(Box.createHorizontalGlue());

        this.leftJoin = new JRadioButton("Left: keep every left row (unmatched left rows get missing right values)");
        this.innerJoin = new JRadioButton("Inner: keep only rows matched on both sides");
        this.fullOuterJoin = new JRadioButton("Full outer: also append unmatched right rows");
        ButtonGroup group = new ButtonGroup();
        group.add(this.leftJoin);
        group.add(this.innerJoin);
        group.add(this.fullOuterJoin);
        String type = this.params.getString(JoinDatasetsWrapper.JOIN_TYPE, JoinDatasets.JoinType.LEFT.name());
        if (JoinDatasets.JoinType.INNER.name().equals(type)) this.innerJoin.setSelected(true);
        else if (JoinDatasets.JoinType.FULL_OUTER.name().equals(type)) this.fullOuterJoin.setSelected(true);
        else this.leftJoin.setSelected(true);

        this.allowOneToMany = new JCheckBox("Allow a right key to repeat (one-to-many: each matching left row is "
                + "emitted once per right row)", this.params.getBoolean(JoinDatasetsWrapper.ALLOW_ONE_TO_MANY, false));

        this.leftSuffix = new JTextField(this.params.getString(JoinDatasetsWrapper.LEFT_SUFFIX, "_left"), 8);
        this.leftSuffix.setMaximumSize(this.leftSuffix.getPreferredSize());
        this.rightSuffix = new JTextField(this.params.getString(JoinDatasetsWrapper.RIGHT_SUFFIX, "_right"), 8);
        this.rightSuffix.setMaximumSize(this.rightSuffix.getPreferredSize());

        Box suffixBox = Box.createHorizontalBox();
        suffixBox.add(new JLabel("Suffixes for colliding column names:  left "));
        suffixBox.add(this.leftSuffix);
        suffixBox.add(new JLabel("  right "));
        suffixBox.add(this.rightSuffix);
        suffixBox.add(Box.createHorizontalGlue());

        Box vert = Box.createVerticalBox();
        vert.add(parentsBox);
        vert.add(Box.createVerticalStrut(12));
        vert.add(keyHeader);
        vert.add(Box.createVerticalStrut(4));
        vert.add(this.keyRowsPanel);
        vert.add(addBox);
        vert.add(Box.createVerticalStrut(12));
        vert.add(leftAligned(new JLabel("Join type:")));
        vert.add(leftAligned(this.leftJoin));
        vert.add(leftAligned(this.innerJoin));
        vert.add(leftAligned(this.fullOuterJoin));
        vert.add(Box.createVerticalStrut(8));
        vert.add(leftAligned(this.allowOneToMany));
        vert.add(Box.createVerticalStrut(8));
        vert.add(suffixBox);
        vert.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(vert, BorderLayout.CENTER);

        onLeftParentChanged(false);
    }

    private static Box leftAligned(JComponent c) {
        Box b = Box.createHorizontalBox();
        b.add(c);
        b.add(Box.createHorizontalGlue());
        return b;
    }

    private String parentName(int i) {
        String name = this.parents.get(i).getName();
        if (name == null || name.isBlank()) name = "Data " + (i + 1);
        return name;
    }

    private int leftIndex() {
        return this.leftParentBox.getSelectedIndex() == 1 ? 1 : 0;
    }

    private List<String> variableNames(int parentIndex) {
        DataModel model = this.parents.get(parentIndex).getSelectedDataModel();
        if (model instanceof DataSet dataSet) return dataSet.getVariableNames();
        return new ArrayList<>();
    }

    /**
     * Rebuilds the key rows for the current left/right assignment. When the user swaps sides, the existing key
     * pairs are kept with their sides swapped; on first build, the saved keys (or, failing that, the first
     * variable name common to both tables) are used.
     */
    private void onLeftParentChanged(boolean swap) {
        this.rightParentLabel.setText(parentName(1 - leftIndex()));

        List<String[]> pairs = new ArrayList<>();

        if (swap) {
            for (KeyRow row : this.keyRows) pairs.add(new String[]{row.right(), row.left()});
        } else {
            List<String> lk = JoinDatasetsWrapper.split(this.params.getString(JoinDatasetsWrapper.LEFT_KEYS, ""));
            List<String> rk = JoinDatasetsWrapper.split(this.params.getString(JoinDatasetsWrapper.RIGHT_KEYS, ""));
            for (int i = 0; i < Math.min(lk.size(), rk.size()); i++) pairs.add(new String[]{lk.get(i), rk.get(i)});

            if (pairs.isEmpty()) {
                Set<String> common = new LinkedHashSet<>(variableNames(leftIndex()));
                common.retainAll(variableNames(1 - leftIndex()));
                String first = common.isEmpty() ? null : common.iterator().next();
                pairs.add(new String[]{first, first});
            }
        }

        this.keyRows.clear();
        for (String[] p : pairs) addKeyRow(p[0], p[1]);
        refreshKeyRows();
    }

    private void addKeyRow(String left, String right) {
        this.keyRows.add(new KeyRow(variableNames(leftIndex()), left, variableNames(1 - leftIndex()), right));
    }

    private void refreshKeyRows() {
        this.keyRowsPanel.removeAll();

        for (KeyRow row : this.keyRows) {
            Box b = Box.createHorizontalBox();
            b.add(row.leftBox);
            b.add(new JLabel("  =  "));
            b.add(row.rightBox);
            b.add(Box.createHorizontalStrut(10));
            JButton remove = new JButton("Remove");
            remove.setEnabled(this.keyRows.size() > 1);
            remove.addActionListener(e -> {
                this.keyRows.remove(row);
                refreshKeyRows();
            });
            b.add(remove);
            b.add(Box.createHorizontalGlue());
            this.keyRowsPanel.add(b);
        }

        this.keyRowsPanel.revalidate();
        this.keyRowsPanel.repaint();
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) w.pack();
    }

    /**
     * Validates the choices and writes them to the parameters.
     *
     * @return True if the choices are valid.
     */
    public boolean finalizeEdit() {
        if (this.parents.size() != 2) return false;

        List<String> leftKeys = new ArrayList<>();
        List<String> rightKeys = new ArrayList<>();

        for (KeyRow row : this.keyRows) {
            if (row.left() == null || row.right() == null) {
                JOptionPane.showMessageDialog(this, "Each key pair needs a variable on both sides.",
                        "Join", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            leftKeys.add(row.left());
            rightKeys.add(row.right());
        }

        if (leftKeys.isEmpty()) {
            JOptionPane.showMessageDialog(this, "At least one key pair is needed.", "Join",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (new LinkedHashSet<>(leftKeys).size() != leftKeys.size()
                || new LinkedHashSet<>(rightKeys).size() != rightKeys.size()) {
            JOptionPane.showMessageDialog(this, "A variable is used in more than one key pair.", "Join",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        String type = this.innerJoin.isSelected() ? JoinDatasets.JoinType.INNER.name()
                : this.fullOuterJoin.isSelected() ? JoinDatasets.JoinType.FULL_OUTER.name()
                : JoinDatasets.JoinType.LEFT.name();

        this.params.set(JoinDatasetsWrapper.LEFT_PARENT, parentName(leftIndex()));
        this.params.set(JoinDatasetsWrapper.LEFT_KEYS, JoinDatasetsWrapper.join(leftKeys));
        this.params.set(JoinDatasetsWrapper.RIGHT_KEYS, JoinDatasetsWrapper.join(rightKeys));
        this.params.set(JoinDatasetsWrapper.JOIN_TYPE, type);
        this.params.set(JoinDatasetsWrapper.ALLOW_ONE_TO_MANY, this.allowOneToMany.isSelected());
        this.params.set(JoinDatasetsWrapper.LEFT_SUFFIX, this.leftSuffix.getText());
        this.params.set(JoinDatasetsWrapper.RIGHT_SUFFIX, this.rightSuffix.getText());
        return true;
    }

    /**
     * One key pair: a left variable chooser and a right variable chooser.
     */
    private static final class KeyRow {
        final JComboBox<String> leftBox;
        final JComboBox<String> rightBox;

        KeyRow(List<String> leftVars, String left, List<String> rightVars, String right) {
            this.leftBox = new JComboBox<>(leftVars.toArray(new String[0]));
            this.rightBox = new JComboBox<>(rightVars.toArray(new String[0]));
            if (left != null && leftVars.contains(left)) this.leftBox.setSelectedItem(left);
            if (right != null && rightVars.contains(right)) this.rightBox.setSelectedItem(right);
            this.leftBox.setMaximumSize(this.leftBox.getPreferredSize());
            this.rightBox.setMaximumSize(this.rightBox.getPreferredSize());
        }

        String left() {
            return (String) this.leftBox.getSelectedItem();
        }

        String right() {
            return (String) this.rightBox.getSelectedItem();
        }
    }
}
