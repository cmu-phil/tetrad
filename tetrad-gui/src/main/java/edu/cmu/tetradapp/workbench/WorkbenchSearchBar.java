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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A strip above a graph workbench with a text field for selecting variables by name. Names are separated by commas,
 * and * and ? are wildcards (any run of characters, any one character); case is ignored. As the text is typed, every
 * variable whose name matches one of the terms is selected, in addition to whatever was selected already; when a
 * variable stops matching, it is deselected again, but only if it was this field that selected it. Enter scrolls
 * the matches into view, and Escape clears the field.
 * <p>
 * The strip is the column header of the scroll pane the workbench sits in, so it stays put when the graph is
 * scrolled, is not part of the workbench itself, and does not appear in a saved image of the graph. It is installed
 * by the workbench when it is shown in a scroll pane (see {@link AbstractWorkbench#addNotify()}).
 *
 * @author josephramsey
 */
final class WorkbenchSearchBar extends JPanel {

    /**
     * Marks this bar's text field, so that the workbench can tell when it has the keyboard focus.
     */
    static final String FIELD_KEY = "edu.cmu.tetradapp.workbench.WorkbenchSearchBar.field";

    private final JScrollPane scrollPane;
    private final JLabel label = new JLabel("Select by name:");
    private final JTextField field = new JTextField(24);
    private final JLabel count = new JLabel("");

    /**
     * The nodes this field has selected and not yet deselected. Nodes that were selected already when they came
     * to match are not in it, so clearing the field leaves them selected.
     */
    private final Set<Node> added = new HashSet<>();

    private AbstractWorkbench workbench;

    private WorkbenchSearchBar(AbstractWorkbench workbench, JScrollPane scrollPane) {
        super(null);
        this.workbench = workbench;
        this.scrollPane = scrollPane;

        String tip = "<html>Type variable names, separated by commas; * and ? are wildcards (for example: X1*, age,"
                     + " ?_score).<br>Matching variables are selected in addition to those selected already."
                     + "<br>Enter scrolls to the matches; Escape clears the field.</html>";
        this.field.setToolTipText(tip);
        this.label.setToolTipText(tip);
        this.field.putClientProperty(FIELD_KEY, Boolean.TRUE);

        add(this.label);
        add(this.field);
        add(this.count);

        this.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });

        this.field.addActionListener(e -> scrollToMatches());

        this.field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
        this.field.getActionMap().put("clearSearch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                WorkbenchSearchBar.this.field.setText("");
            }
        });

        // The strip scrolls sideways with the graph, as a column header does; keep the field at the left edge
        // of what is showing.
        scrollPane.getViewport().addChangeListener(e -> {
            revalidate();
            doLayout();
            repaint();
        });
    }

    /**
     * Gives the scroll pane the given workbench sits in a search bar for it, if the workbench is one that shows a
     * graph's variables, sits directly in a scroll pane, and the scroll pane has no column header of its own. If
     * the scroll pane already has a search bar (it showed another workbench before), the bar is pointed at this
     * workbench.
     *
     * @param workbench the workbench
     */
    static void install(AbstractWorkbench workbench) {
        if (!(workbench instanceof GraphWorkbench)) return;
        if (!(workbench.getParent() instanceof JViewport viewport)) return;
        if (!(viewport.getParent() instanceof JScrollPane scrollPane)) return;
        if (scrollPane.getViewport() != viewport) return;

        JViewport header = scrollPane.getColumnHeader();
        Component existing = header == null ? null : header.getView();

        if (existing instanceof WorkbenchSearchBar bar) {
            bar.setWorkbench(workbench);
        } else if (existing == null) {
            scrollPane.setColumnHeaderView(new WorkbenchSearchBar(workbench, scrollPane));
        }
    }

    private void setWorkbench(AbstractWorkbench workbench) {
        if (this.workbench == workbench) return;
        this.workbench = workbench;
        this.added.clear();
        update();
    }

    @Override
    public Dimension getPreferredSize() {
        Component view = this.scrollPane.getViewport().getView();
        int width = view == null ? 0 : Math.max(view.getWidth(), view.getPreferredSize().width);
        Dimension l = this.label.getPreferredSize();
        Dimension f = this.field.getPreferredSize();
        Dimension c = this.count.getPreferredSize();
        return new Dimension(Math.max(width, l.width + f.width + c.width + 30), f.height + 6);
    }

    @Override
    public void doLayout() {
        int x = this.scrollPane.getViewport().getViewPosition().x + 6;
        Dimension l = this.label.getPreferredSize();
        Dimension f = this.field.getPreferredSize();
        int height = f.height;

        this.label.setBounds(x, 3, l.width, height);
        this.field.setBounds(x + l.width + 6, 3, f.width, height);
        this.count.setBounds(x + l.width + f.width + 12, 3, 160, height);
    }

    /**
     * Brings the selection into line with the text: selects the matches not selected yet, and deselects the ones
     * this field selected that match no longer.
     */
    private void update() {
        if (this.workbench == null || !this.workbench.isSearchSelectable()) {
            this.count.setText(this.field.getText().isBlank() ? "" : "selection is off here");
            return;
        }

        List<Pattern> patterns = patterns(this.field.getText());
        Set<Node> matches = new HashSet<>();

        for (Node node : this.workbench.getGraph().getNodes()) {
            if (node.getName() == null) continue;

            for (Pattern pattern : patterns) {
                if (pattern.matcher(node.getName()).matches()) {
                    matches.add(node);
                    break;
                }
            }
        }

        for (Node node : new ArrayList<>(this.added)) {
            if (!matches.contains(node)) {
                this.workbench.setNodeSelectedBySearch(node, false);
                this.added.remove(node);
            }
        }

        for (Node node : matches) {
            if (this.workbench.getModelNodesToDisplay().get(node) instanceof DisplayNode display
                && !display.isSelected()) {
                this.workbench.setNodeSelectedBySearch(node, true);
                this.added.add(node);
            }
        }

        this.count.setText(patterns.isEmpty() ? "" : matches.size() + " matched");
        this.workbench.fireSearchSelection();
    }

    private void scrollToMatches() {
        if (this.workbench == null) return;

        List<Node> shown = new ArrayList<>();

        for (Node node : this.added) {
            if (this.workbench.getModelNodesToDisplay().get(node) != null) shown.add(node);
        }

        if (!shown.isEmpty()) this.workbench.scrollNodesToVisible(shown);
    }

    /**
     * The terms of the text as patterns: split at commas, blanks trimmed, empty terms dropped; * for any run of
     * characters, ? for any one, everything else literal; case ignored.
     */
    static List<Pattern> patterns(String text) {
        List<Pattern> patterns = new ArrayList<>();

        for (String term : text.split(",")) {
            term = term.trim();
            if (term.isEmpty()) continue;

            StringBuilder regex = new StringBuilder();
            StringBuilder literal = new StringBuilder();

            for (char ch : term.toCharArray()) {
                if (ch == '*' || ch == '?') {
                    if (literal.length() > 0) {
                        regex.append(Pattern.quote(literal.toString()));
                        literal.setLength(0);
                    }

                    regex.append(ch == '*' ? ".*" : ".");
                } else {
                    literal.append(ch);
                }
            }

            if (literal.length() > 0) regex.append(Pattern.quote(literal.toString()));
            patterns.add(Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }

        return patterns;
    }
}
