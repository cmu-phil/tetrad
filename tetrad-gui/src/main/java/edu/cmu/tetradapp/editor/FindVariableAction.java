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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Opens a small window listing the variables of the graph in a workbench, with a filter field above the list.
 * Typing in the field narrows the list to names containing the typed text (case-insensitive); selecting a name
 * scrolls the workbench so that the variable is centered in view and selects it. Enter in the field jumps to the
 * first listed match. The window is not modal and stays open, so the user can jump from variable to variable, and
 * it re-reads the graph each time it is opened, so it reflects edits made since.
 * <p>
 * This exists because a search result with many variables does not fit in its scroll pane, and locating one
 * variable by scrolling around is slow; the workbench already knew how to scroll to a node and select it (see
 * {@code AbstractWorkbench.scrollWorkbenchToNode}), so what was missing was a way to name the node.
 *
 * @author josephramsey
 */
public class FindVariableAction extends AbstractAction {

    /**
     * The workbench whose graph is searched.
     */
    private final GraphWorkbench workbench;

    /**
     * The window, kept so that a second invocation raises it instead of opening another.
     */
    private JDialog dialog;

    /**
     * Constructs the action for the given workbench.
     *
     * @param workbench The workbench.
     */
    public FindVariableAction(GraphWorkbench workbench) {
        super("Find Variable...");
        this.workbench = workbench;
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        putValue(Action.SHORT_DESCRIPTION, "Scroll the graph to a variable chosen by name and select it.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (this.dialog != null && this.dialog.isDisplayable()) {
            refresh();
            this.dialog.toFront();
            this.dialog.requestFocus();
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this.workbench);
        this.dialog = new JDialog(owner, "Find Variable", Dialog.ModalityType.MODELESS);
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.dialog.getContentPane().add(buildPanel(), BorderLayout.CENTER);
        this.dialog.pack();
        this.dialog.setLocationRelativeTo(this.workbench);
        this.dialog.setVisible(true);
    }

    private JTextField filterField;
    private JList<String> list;
    private DefaultListModel<String> listModel;
    private JLabel countLabel;

    private JPanel buildPanel() {
        this.filterField = new JTextField(24);
        this.listModel = new DefaultListModel<>();
        this.list = new JList<>(this.listModel);
        this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.list.setVisibleRowCount(16);
        this.countLabel = new JLabel(" ");

        refresh();

        this.filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });

        // Enter in the field: go to the first listed match. Down arrow: move into the list.
        this.filterField.addActionListener(e -> {
            if (this.listModel.getSize() > 0) {
                this.list.setSelectedIndex(0);
                goTo(this.listModel.get(0));
            }
        });
        this.filterField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "toList");
        this.filterField.getActionMap().put("toList", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (FindVariableAction.this.listModel.getSize() > 0) {
                    FindVariableAction.this.list.setSelectedIndex(0);
                    FindVariableAction.this.list.requestFocusInWindow();
                }
            }
        });

        // A click (or arrow-key movement) on a name goes there at once.
        this.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String name = this.list.getSelectedValue();
            if (name != null) goTo(name);
        });

        this.list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String name = FindVariableAction.this.list.getSelectedValue();
                if (name != null) goTo(name);
            }
        });

        JButton close = new JButton("Close");
        close.addActionListener(e -> this.dialog.dispose());

        Box top = Box.createHorizontalBox();
        top.add(new JLabel("Filter: "));
        top.add(this.filterField);

        Box bottom = Box.createHorizontalBox();
        bottom.add(this.countLabel);
        bottom.add(Box.createHorizontalGlue());
        bottom.add(close);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(this.list), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> this.filterField.requestFocusInWindow());
        return panel;
    }

    /**
     * Rebuilds the list from the workbench's current graph and the filter text.
     */
    private void refresh() {
        if (this.listModel == null) return;

        String filter = this.filterField.getText().trim().toLowerCase();
        List<String> names = new ArrayList<>();

        for (Node node : this.workbench.getGraph().getNodes()) {
            String name = node.getName();
            if (filter.isEmpty() || name.toLowerCase().contains(filter)) names.add(name);
        }

        names.sort(Comparator.comparing(String::toLowerCase));

        String selected = this.list.getSelectedValue();
        this.listModel.clear();
        for (String name : names) this.listModel.addElement(name);
        if (selected != null && names.contains(selected)) this.list.setSelectedValue(selected, true);

        int total = this.workbench.getGraph().getNumNodes();
        this.countLabel.setText(names.size() == total ? total + " variables"
                : names.size() + " of " + total + " variables");
    }

    /**
     * Centers the workbench on the named variable and selects it.
     */
    private void goTo(String name) {
        Node node = this.workbench.getGraph().getNode(name);
        if (node == null) return;

        if (!this.workbench.centerWorkbenchOnNode(node)) {
            this.workbench.scrollWorkbenchToNode(node);
        }
    }
}
