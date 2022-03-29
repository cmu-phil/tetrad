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
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetradapp.model.IndTestModel;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.IndependenceResult;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;


/**
 * Lists independence facts specified by user and allows the list to be sorted by independence fact or by p value.
 *
 * @author Joseph Ramsey
 */
public class IndependenceFactsEditor extends JPanel {
    private IndTestModel model;
    private LinkedList<String> vars;
    private JTextField textField;
    private List<IndTestProducer> indTestProducers;
    private List<List<IndependenceResult>> results = new ArrayList<>();
    private AbstractTableModel tableModel;
    private int sortDir;
    private int lastSortCol;
    private final NumberFormat nf = new DecimalFormat("0.0000");
    private boolean showPs = false;

    public IndependenceFactsEditor(final IndTestModel model) {
        this.indTestProducers = model.getIndTestProducers();
        this.model = model;

        this.vars = new LinkedList<>();
        this.textField = new JTextField(40);
        this.textField.setEditable(false);
        this.textField.setFont(new Font("Serif", Font.BOLD, 14));
        this.textField.setBackground(new Color(250, 250, 250));

        this.vars = model.getVars();
        this.results = model.getResults();
        if (this.results == null) {
            this.results = new ArrayList<>();
        }

        resetText();

        if (this.indTestProducers.isEmpty()) {
            throw new IllegalArgumentException("At least one source must be specified");
        }

        final List<String> names = this.indTestProducers.get(0).getIndependenceTest().getVariableNames();

        for (int i = 1; i < this.indTestProducers.size(); i++) {
            final List<String> _names = this.indTestProducers.get(i).getIndependenceTest().getVariableNames();

            if (!new HashSet<>(names).equals(new HashSet<>(_names))) {
                throw new IllegalArgumentException("All sources must have the same variable names.");
            }
        }

        buildGui();
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the action of opening a session from a file.
     */
    private void buildGui() {
//        this.independenceTest = getIndTestProducer().getIndependenceTest();
        final List<String> varNames = new ArrayList<>();
        varNames.add("VAR");
        varNames.addAll(getDataVars());
        varNames.add("?");
        varNames.add("+");

        final JComboBox<String> variableBox = new JComboBox<>();
        final DefaultComboBoxModel<String> aModel1 = new DefaultComboBoxModel<>(varNames.toArray(new String[0]));
        aModel1.setSelectedItem("VAR");
        variableBox.setModel(aModel1);

        variableBox.addActionListener(e -> {
            final JComboBox box = (JComboBox) e.getSource();

            final String var = (String) box.getSelectedItem();
            final LinkedList<String> vars = getVars();
            final int size = vars.size();

            if ("VAR".equals(var)) {
                return;
            }

            if ("?".equals(var)) {
                if (!vars.contains("+")) {
                    vars.addLast(var);
                }
            } else if ("+".equals(var)) {
                if (size >= 2) {
                    vars.addLast(var);
                }
            } else if ((vars.indexOf("?") < 2) && !(vars.contains("+")) &&
                    !(vars.contains(var))) {
                vars.add(var);
            }

            resetText();

            // This is a workaround to an introduced bug in the JDK whereby
            // repeated selections of the same item send out just one
            // action event.
            final DefaultComboBoxModel<String> aModel = new DefaultComboBoxModel<>(
                    varNames.toArray(new String[0]));
            aModel.setSelectedItem("VAR");
            variableBox.setModel(aModel);
        });

        final JButton delete = new JButton("Delete");

        delete.addActionListener(e -> {
            if (!getVars().isEmpty()) {
                getVars().removeLast();
                resetText();
            }
        });

        this.textField.addKeyListener(new KeyAdapter() {
            public void keyTyped(final KeyEvent e) {
                if ('?' == e.getKeyChar()) {
                    variableBox.setSelectedItem("?");
                } else if ('+' == e.getKeyChar()) {
                    variableBox.setSelectedItem("+");
                } else if ('\b' == e.getKeyChar()) {
                    IndependenceFactsEditor.this.vars.removeLast();
                    resetText();
                }

                e.consume();
            }
        });

        delete.addKeyListener(new KeyAdapter() {
            public void keyTyped(final KeyEvent e) {
                if ('?' == e.getKeyChar()) {
                    variableBox.setSelectedItem("?");
                } else if ('+' == e.getKeyChar()) {
                    variableBox.setSelectedItem("+");
                } else if ('\b' == e.getKeyChar()) {
                    IndependenceFactsEditor.this.vars.removeLast();
                    resetText();
                }
            }
        });

        variableBox.addKeyListener(new KeyAdapter() {
            public void keyTyped(final KeyEvent e) {
                super.keyTyped(e);

                if ('\b' == e.getKeyChar()) {
                    IndependenceFactsEditor.this.vars.removeLast();
                    resetText();
                }
            }
        });

        final JButton list = new JButton("LIST");
        list.setFont(new Font("Dialog", Font.BOLD, 14));

        list.addActionListener(e -> generateResults());

        final Box b1 = Box.createVerticalBox();

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Compares conditional independence tests from the given sources: "));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        for (int i = 0; i < this.indTestProducers.size(); i++) {
            final Box b2a = Box.createHorizontalBox();
            b2a.add(new JLabel(this.indTestProducers.get(i).getName() + ": " + getIndependenceTest(i).toString()));
            b2a.add(Box.createHorizontalGlue());
            b1.add(b2a);
        }

        b1.add(Box.createVerticalStrut(5));

        final Box b3 = Box.createHorizontalBox();
        b3.add(getTextField());
        b3.add(variableBox);
        b3.add(delete);
        b1.add(b3);
        b1.add(Box.createVerticalStrut(10));

        this.tableModel = new AbstractTableModel() {
            public String getColumnName(final int column) {
                if (column == 0) {
                    return "Index";
                }
                if (column == 1) {
                    return "Fact";
                } else if (column >= 2) {
                    return IndependenceFactsEditor.this.indTestProducers.get(column - 2).getName();//  "Judgment";
                }

                return null;
            }

            public int getColumnCount() {
                return 2 + IndependenceFactsEditor.this.indTestProducers.size();
            }

            public int getRowCount() {
                return IndependenceFactsEditor.this.results.size();
            }

            public Object getValueAt(final int rowIndex, final int columnIndex) {
                if (rowIndex > IndependenceFactsEditor.this.results.size()) return null;

                if (columnIndex == 0) {
                    return IndependenceFactsEditor.this.results.get(rowIndex).get(0).getIndex();
                }
                if (columnIndex == 1) {
                    return IndependenceFactsEditor.this.results.get(rowIndex).get(0).getFact();
                }

                final IndependenceResult independenceResult = IndependenceFactsEditor.this.results.get(rowIndex).get(columnIndex - 2);

                for (int i = 0; i < IndependenceFactsEditor.this.indTestProducers.size(); i++) {
                    if (columnIndex == i + 2) {
                        if (getIndependenceTest(i) instanceof IndTestDSep) {
                            if (independenceResult.getType() == IndependenceResult.Type.INDEPENDENT) {
                                return "D-SEPARATED";
                            } else if (independenceResult.getType() == IndependenceResult.Type.DEPENDENT) {
                                return "d-connected";
                            } else if (independenceResult.getType() == IndependenceResult.Type.UNDETERMINED) {
                                return "*";
                            }
                        } else {
                            if (isShowPs()) {
                                return IndependenceFactsEditor.this.nf.format(independenceResult.getpValue());
                            } else {
                                if (independenceResult.getType() == IndependenceResult.Type.INDEPENDENT) {
                                    return "INDEPENDENT";
                                } else if (independenceResult.getType() == IndependenceResult.Type.DEPENDENT) {
                                    return "dependent";
                                } else if (independenceResult.getType() == IndependenceResult.Type.UNDETERMINED) {
                                    return "*";
                                }
                            }
                        }
                    }
                }

                return null;
            }

            public Class getColumnClass(final int columnIndex) {
                if (columnIndex == 0) {
                    return Number.class;
                }
                if (columnIndex == 1) {
                    return String.class;
                } else if (columnIndex == 2) {
                    return Number.class;
                } else {
                    return Number.class;
                }
            }
        };

        final JTable table = new JTable(this.tableModel);

        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(200);
        table.getColumnModel().getColumn(1).setCellRenderer(new Renderer(this));

        for (int i = 2; i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setMinWidth(100);
            table.getColumnModel().getColumn(i).setMaxWidth(100);
            table.getColumnModel().getColumn(i).setCellRenderer(new Renderer(this));
        }

        final JTableHeader header = table.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(final MouseEvent e) {
                final JTableHeader header = (JTableHeader) e.getSource();
                final Point point = e.getPoint();
                final int col = header.columnAtPoint(point);
                final int sortCol = header.getTable().convertColumnIndexToModel(col);

                sortByColumn(sortCol);
            }
        });

        final JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 400));
        b1.add(scroll);

        final Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Limit list to "));


        final IntTextField field = new IntTextField(getListLimit(), 7);

        field.setFilter((value, oldValue) -> {
            try {
                setListLimit(value);
                return value;
            } catch (final Exception e) {
                return oldValue;
            }
        });

        b4.add(field);
        b4.add(new JLabel(" items."));

        b4.add(Box.createHorizontalStrut(10));

        final JButton showPValues = new JButton("Show P Values");
        showPValues.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                toggleShowPs();

                if (IndependenceFactsEditor.this.showPs) {
                    showPValues.setText("Show Independencies");
                } else {
                    showPValues.setText("Show P Values or Scores");
                }
            }
        });

        b4.add(showPValues);

        b4.add(Box.createHorizontalGlue());
        b4.add(list);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        final JPanel panel = this;
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    //=============================PRIVATE METHODS=======================//

    private void sortByColumn(final int sortCol) {
        if (sortCol == getLastSortCol()) {
            setSortDir(-1 * getSortDir());
        } else {
            setSortDir(1);
        }

        setLastSortCol(sortCol);

        this.results.sort((r1, r2) -> {
            switch (sortCol) {
                case 0:
                case 1:
                    return getSortDir() * (r1.get(0).getIndex() - r2.get(0).getIndex());
                default:
                    final int ind1;
                    final int ind2;
                    final int col = sortCol - 2;

                    if (r1.get(col).getType() == IndependenceResult.Type.UNDETERMINED) {
                        ind1 = 0;
                    } else if (r1.get(col).getType() == IndependenceResult.Type.DEPENDENT) {
                        ind1 = 1;
                    } else {
                        ind1 = 2;
                    }

                    if (r2.get(col).getType() == IndependenceResult.Type.UNDETERMINED) {
                        ind2 = 0;
                    } else if (r2.get(col).getType() == IndependenceResult.Type.DEPENDENT) {
                        ind2 = 1;
                    } else {
                        ind2 = 2;
                    }

                    return getSortDir() * (ind1 - ind2);
            }
        });

        this.tableModel.fireTableDataChanged();
    }

    private boolean isShowPs() {
        return this.showPs;
    }

    private void toggleShowPs() {
        this.showPs = !this.showPs;
        this.tableModel.fireTableDataChanged();
    }

    static class Renderer extends DefaultTableCellRenderer {
        private final IndependenceFactsEditor editor;
        private JTable table;
        private int row;
        private boolean selected;

        public Renderer(final IndependenceFactsEditor editor) {
            super();
            this.editor = editor;
        }

        public void setValue(final Object value) {
            int indep = 0;

            final int numCols = this.table.getModel().getColumnCount();

            if (this.selected) {
                super.setForeground(this.table.getSelectionForeground());
                super.setBackground(this.table.getSelectionBackground());
            } else {
                for (int i = 2; i < numCols; i++) {
                    final Object _value = this.table.getModel().getValueAt(this.row, i);

                    if ("INDEPENDENT".equals(_value) || "D-SEPARATED".equals(_value)) {
                        indep++;
                    }
                }

                setForeground(this.table.getForeground());

                if (!this.editor.isShowPs()) {
                    if (!(indep == 0 || indep == numCols - 2)) {
                        setBackground(Color.YELLOW);
                    } else {
                        setBackground(this.table.getBackground());
                    }
                } else {
                    setBackground(this.table.getBackground());
                }
            }

            setText((String) value);
        }

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            this.table = table;
            this.row = row;
            this.selected = isSelected;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }


    private List<String> getDataVars() {
        return getIndependenceTest(0).getVariableNames();
    }

    private void resetText() {
        final StringBuilder buf = new StringBuilder();

        if (getVars().size() == 0) {
            buf.append("Choose variables and wildcards from dropdown-->");
        }

        if (getVars().size() > 0) {
            buf.append(" ").append(getVars().get(0));
            buf.append(" _||_ ");
        }

        if (getVars().size() > 1) {
            buf.append(getVars().get(1));
        }

        if (getVars().size() > 2) {
            buf.append(" | ");
        }

        for (int i = 2; i < getVars().size() - 1; i++) {
            buf.append(getVars().get(i));
            buf.append(", ");
        }

        if (getVars().size() > 2) {
            buf.append(getVars().get(getVars().size() - 1));
        }

        this.model.setVars(getVars());
        this.textField.setText(buf.toString());
    }

    private void generateResults() {
        this.results = new ArrayList<>();

        final List<String> dataVars = getDataVars();

        if (getVars().size() < 2) {
            this.tableModel.fireTableDataChanged();
            return;
        }

        // Need a choice generator over the ?'s, and a depth choice generator over the +'s. The +'s all have to come
        // at the end at index >= 2.
        int numQuestionMarksFirstTwo = 0;
        int numQuestionMarksRest = 0;
        int numPluses = 0;
        int numFixed = 0;

        for (int i = 0; i < this.vars.size(); i++) {
            final String var = this.vars.get(i);
            if ("?".equals(var) && i < 2) numQuestionMarksFirstTwo++;
            else if ("?".equals(var)) numQuestionMarksRest++;
            else if ("+".equals(var)) numPluses++;
            else numFixed++;
        }

        final int[] questionMarkFirstTwoIndices = new int[numQuestionMarksFirstTwo];
        final int[] questionMarkRestIndices = new int[numQuestionMarksRest];
        final int[] plusIndices = new int[numPluses];
        final int[] fixedIndices = new int[numFixed];
        final String[] fixedVars = new String[numFixed];

        int _i = -1;
        int _j = -1;
        int _k = -1;
        int _l = -1;

        for (int i = 0; i < this.vars.size(); i++) {
            if ("?".equals(this.vars.get(i)) && i < 2) questionMarkFirstTwoIndices[++_i] = i;
            else if ("?".equals(this.vars.get(i))) questionMarkRestIndices[++_j] = i;
            else if ("+".equals(this.vars.get(i))) plusIndices[++_k] = i;
            else {
                fixedIndices[++_l] = i;
                fixedVars[_l] = this.vars.get(i);
            }
        }

        final List<String> vars1 = new ArrayList<>(dataVars);
        vars1.removeAll(this.vars);

        final ChoiceGenerator gen1 = new ChoiceGenerator(vars1.size(), questionMarkFirstTwoIndices.length);
        int[] choice1;

        LOOP:
        while ((choice1 = gen1.next()) != null) {
            final List<String> s2 = IndependenceFactsEditor.asList(choice1, vars1);

            final List<String> vars2 = new ArrayList<>(vars1);
            vars2.removeAll(s2);

            final ChoiceGenerator gen2 = new ChoiceGenerator(vars2.size(), questionMarkRestIndices.length);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                final List<String> s3 = IndependenceFactsEditor.asList(choice2, vars2);

                final List<String> vars3 = new ArrayList<>(vars2);
                vars3.removeAll(s3);

                final DepthChoiceGenerator gen3 = new DepthChoiceGenerator(vars3.size(), plusIndices.length);
                int[] choice3;

                while ((choice3 = gen3.next()) != null) {
                    this.results.add(new ArrayList<>());

                    for (int prod = 0; prod < this.indTestProducers.size(); prod++) {
                        final String[] vars4 = new String[fixedIndices.length + questionMarkFirstTwoIndices.length
                                + questionMarkRestIndices.length + choice3.length];

                        for (int i = 0; i < fixedIndices.length; i++) {
                            vars4[fixedIndices[i]] = fixedVars[i];
                        }

                        for (int i = 0; i < choice1.length; i++) {
                            vars4[questionMarkFirstTwoIndices[i]] = vars1.get(choice1[i]);
                        }

                        for (int i = 0; i < choice2.length; i++) {
                            vars4[questionMarkRestIndices[i]] = vars2.get(choice2[i]);
                        }

                        for (int i = 0; i < choice3.length; i++) {
                            vars4[plusIndices[i]] = vars3.get(choice3[i]);
                        }

                        final IndependenceTest independenceTest = getIndependenceTest(prod);

                        final Node x = independenceTest.getVariable(vars4[0]);
                        final Node y = independenceTest.getVariable(vars4[1]);

                        final List<Node> z = new ArrayList<>();

                        for (int i = 2; i < vars4.length; i++) {
                            z.add(independenceTest.getVariable(vars4[i]));
                        }

                        IndependenceResult.Type indep;
                        double pValue;

                        try {
                            indep = independenceTest.isIndependent(x, y, z) ? IndependenceResult.Type.INDEPENDENT : IndependenceResult.Type.DEPENDENT;
                            pValue = independenceTest.getPValue();
                        } catch (final Exception e) {
                            indep = IndependenceResult.Type.UNDETERMINED;
                            pValue = Double.NaN;
                        }

                        this.results.get(this.results.size() - 1).add(new IndependenceResult(this.results.size(),
                                IndependenceFactsEditor.factString(x, y, z), indep, pValue));
                    }

                    if (this.results.size() > getListLimit()) break LOOP;
                }
            }
        }

        this.model.setResults(this.results);

        this.tableModel.fireTableDataChanged();
    }

    private static List<String> asList(final int[] indices, final List<String> nodes) {
        final List<String> list = new LinkedList<>();

        for (final int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private LinkedList<String> getVars() {
        return this.vars;
    }

    private JTextField getTextField() {
        return this.textField;
    }

    public IndependenceFactsEditor(final LayoutManager layout, final boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
    }


    private static String factString(final Node x, final Node y, final List<Node> condSet) {
        final StringBuilder sb = new StringBuilder();

        sb.append(x.getName());
        sb.append(" _||_ ");
        sb.append(y.getName());

        final Iterator<Node> it = condSet.iterator();

        if (it.hasNext()) {
            sb.append(" | ");
            sb.append(it.next());
        }

        while (it.hasNext()) {
            sb.append(", ");
            sb.append(it.next());
        }

        return sb.toString();
    }

    private IndependenceTest getIndependenceTest(final int i) {
        return this.indTestProducers.get(i).getIndependenceTest();
    }

    private int getLastSortCol() {
        return this.lastSortCol;
    }

    private void setLastSortCol(final int lastSortCol) {
        if (lastSortCol < 0 || lastSortCol > 4) {
            throw new IllegalArgumentException();
        }

        this.lastSortCol = lastSortCol;
    }

    private int getSortDir() {
        return this.sortDir;
    }

    private void setSortDir(final int sortDir) {
        if (!(sortDir == 1 || sortDir == -1)) {
            throw new IllegalArgumentException();
        }

        this.sortDir = sortDir;
    }

    private int getListLimit() {
        return Preferences.userRoot().getInt("indFactsListLimit", 10000);
    }

    private void setListLimit(final int listLimit) {
        if (listLimit < 1) {
            throw new IllegalArgumentException();
        }

        Preferences.userRoot().putInt("indFactsListLimit", listLimit);
    }
}





