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
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
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
    private List<List<IndependenceResult>> results = new ArrayList<List<IndependenceResult>>();
    private AbstractTableModel tableModel;
    private int sortDir;
    private int lastSortCol;
    private NumberFormat nf = new DecimalFormat("0.0000");
    private boolean showPs = false;

    public IndependenceFactsEditor(IndTestModel model) {
        this.indTestProducers = model.getIndTestProducers();
        this.model = model;

        this.vars = new LinkedList<String>();
        this.textField = new JTextField(40);
        this.textField.setEditable(false);
        this.textField.setFont(new Font("Serif", Font.BOLD, 14));
        this.textField.setBackground(new Color(250, 250, 250));

        this.vars = model.getVars();
        this.results = model.getResults();
        if (this.results == null) {
            this.results = new ArrayList<List<IndependenceResult>>();
        }

        resetText();

        if (indTestProducers.isEmpty()) {
            throw new IllegalArgumentException("At least one source must be specified");
        }

        List<String> names = indTestProducers.get(0).getIndependenceTest().getVariableNames();

        for (int i = 1; i < indTestProducers.size(); i++) {
            List<String> _names = indTestProducers.get(i).getIndependenceTest().getVariableNames();

            if (!new HashSet<String>(names).equals(new HashSet<String>(_names))) {
                throw new IllegalArgumentException("All sources must have the same variable names.");
            }
        }

        buildGui();
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the action of opening a session from a file.
     */
    public void buildGui() {
//        this.independenceTest = getIndTestProducer().getIndependenceTest();
        final List<String> varNames = new ArrayList<String>();
        varNames.add("VAR");
        varNames.addAll(getDataVars());
        varNames.add("?");
        varNames.add("+");

        final JComboBox variableBox = new JComboBox();
        DefaultComboBoxModel aModel1 = new DefaultComboBoxModel(varNames.toArray(new String[varNames.size()]));
        aModel1.setSelectedItem("VAR");
        variableBox.setModel(aModel1);

        variableBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();

                String var = (String) box.getSelectedItem();
                LinkedList<String> vars = getVars();
                int size = vars.size();

                if ("VAR".equals(var)) {
                    return;
                }

                if ("?".equals(var)) {
                    if (size >= 0 && !vars.contains("+")) {
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
                DefaultComboBoxModel aModel = new DefaultComboBoxModel(
                        varNames.toArray(new String[varNames.size()]));
                aModel.setSelectedItem("VAR");
                variableBox.setModel(aModel);
            }
        });

        final JButton delete = new JButton("Delete");

        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!getVars().isEmpty()) {
                    getVars().removeLast();
                    resetText();
                }
            }
        });

        textField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if ('?' == e.getKeyChar()) {
                    variableBox.setSelectedItem("?");
                } else if ('+' == e.getKeyChar()) {
                    variableBox.setSelectedItem("+");
                } else if ('\b' == e.getKeyChar()) {
                    vars.removeLast();
                    resetText();
                }

                e.consume();
            }
        });

        delete.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                if ('?' == e.getKeyChar()) {
                    variableBox.setSelectedItem("?");
                } else if ('+' == e.getKeyChar()) {
                    variableBox.setSelectedItem("+");
                } else if ('\b' == e.getKeyChar()) {
                    vars.removeLast();
                    resetText();
                }
            }
        });

        variableBox.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);

                if ('\b' == e.getKeyChar()) {
                    vars.removeLast();
                    resetText();
                }
            }
        });

        JButton list = new JButton("LIST");
        list.setFont(new Font("Dialog", Font.BOLD, 14));

        list.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                generateResults();
            }
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Compares conditional independence tests from the given sources: "));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        for (int i = 0; i < indTestProducers.size(); i++) {
            Box b2a = Box.createHorizontalBox();
            b2a.add(new JLabel(indTestProducers.get(i).getName() + ": " + getIndependenceTest(i).toString()));
            b2a.add(Box.createHorizontalGlue());
            b1.add(b2a);
        }

        b1.add(Box.createVerticalStrut(5));

        Box b3 = Box.createHorizontalBox();
        b3.add(getTextField());
        b3.add(variableBox);
        b3.add(delete);
        b1.add(b3);
        b1.add(Box.createVerticalStrut(10));

        tableModel = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                }
                if (column == 1) {
                    return "Fact";
                } else if (column >= 2) {
                    return indTestProducers.get(column - 2).getName();//  "Judgment";
                }

                return null;
            }

            public int getColumnCount() {
                return 2 + indTestProducers.size();
            }

            public int getRowCount() {
                return results.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > results.size()) return null;

                if (columnIndex == 0) {
                    return results.get(rowIndex).get(0).getIndex();
                }
                if (columnIndex == 1) {
                    return results.get(rowIndex).get(0).getFact();
                }

                IndependenceResult independenceResult = results.get(rowIndex).get(columnIndex - 2);

                for (int i = 0; i < indTestProducers.size(); i++) {
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
                                return nf.format(independenceResult.getpValue());
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

            public Class getColumnClass(int columnIndex) {
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

        JTable table = new JTable(tableModel);

        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(200);
        table.getColumnModel().getColumn(1).setCellRenderer(new Renderer(this));

        for (int i = 2; i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setMinWidth(100);
            table.getColumnModel().getColumn(i).setMaxWidth(100);
            table.getColumnModel().getColumn(i).setCellRenderer(new Renderer(this));
        }

        JTableHeader header = table.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                sortByColumn(sortCol, true);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 400));
        b1.add(scroll);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Limit list to "));


        IntTextField field = new IntTextField(getListLimit(), 7);

        field.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    setListLimit(value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });

        b4.add(field);
        b4.add(new JLabel(" items."));

        b4.add(Box.createHorizontalStrut(10));

        final JButton showPValues = new JButton("Show P Values");
        showPValues.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                toggleShowPs();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                toggleShowPs();
            }
        });

        b4.add(showPValues);

        b4.add(Box.createHorizontalGlue());
        b4.add(list);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        JPanel panel = this;
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    //=============================PRIVATE METHODS=======================//

    private void sortByColumn(final int sortCol, boolean allowReverse) {
        if (allowReverse && sortCol == getLastSortCol()) {
            setSortDir(-1 * getSortDir());
        } else {
            setSortDir(1);
        }

        setLastSortCol(sortCol);

        Collections.sort(results, new Comparator<List<IndependenceResult>>() {
            public int compare(List<IndependenceResult> r1, List<IndependenceResult> r2) {

                switch (sortCol) {
                    case 0:
                        return getSortDir() * (r1.get(0).getIndex() - r2.get(0).getIndex());
                    case 1:
                        return getSortDir() * (r1.get(0).getIndex() - r2.get(0).getIndex());
                    default:
                        int ind1, ind2;
                        int col = sortCol - 2;

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
            }
        });

        tableModel.fireTableDataChanged();
    }

    public boolean isShowPs() {
        return showPs;
    }

    public void toggleShowPs() {
        this.showPs = !this.showPs;
        tableModel.fireTableDataChanged();
    }

    static class Renderer extends DefaultTableCellRenderer {
        private final IndependenceFactsEditor editor;
        private JTable table;
        private int row;
        private int col;
        private boolean selected;

        public Renderer(IndependenceFactsEditor editor) {
            super();
            this.editor = editor;
        }

        public void setValue(Object value) {
            int indep = 0;

            int numCols = table.getModel().getColumnCount();

            if (selected) {
                super.setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                for (int i = 2; i < numCols; i++) {
                    Object _value = table.getModel().getValueAt(row, i);

                    if ("INDEPENDENT".equals(_value) || "D-SEPARATED".equals(_value)) {
                        indep++;
                    }
                }

                if (!editor.isShowPs()) {
                    if (!(indep == 0 || indep == numCols - 2)) {
                        setForeground(table.getForeground());
                        setBackground(Color.YELLOW);
                    } else {
                        setForeground(table.getForeground());
                        setBackground(table.getBackground());
                    }
                }
                else {
                    setForeground(table.getForeground());
                    setBackground(table.getBackground());
                }
            }

            setText((String) value);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.table = table;
            this.row = row;
            this.col = column;
            this.selected = isSelected;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }


    private List<String> getDataVars() {
        return getIndependenceTest(0).getVariableNames();
    }

    private void resetText() {
        StringBuilder buf = new StringBuilder();

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

        model.setVars(getVars());
        textField.setText(buf.toString());
    }

    private void generateResults() {
        results = new ArrayList<List<IndependenceResult>>();

        List<String> dataVars = getDataVars();

        if (getVars().size() < 2) {
            tableModel.fireTableDataChanged();
            return;
        }

        // Need a choice generator over the ?'s, and a depth choice generator over the +'s. The +'s all have to come
        // at the end at index >= 2.
        int numQuestionMarksFirstTwo = 0;
        int numQuestionMarksRest = 0;
        int numPluses = 0;
        int numFixed = 0;

        for (int i = 0; i < vars.size(); i++) {
            String var = vars.get(i);
            if ("?".equals(var) && i < 2) numQuestionMarksFirstTwo++;
            else if ("?".equals(var)) numQuestionMarksRest++;
            else if ("+".equals(var)) numPluses++;
            else numFixed++;
        }

        int[] questionMarkFirstTwoIndices = new int[numQuestionMarksFirstTwo];
        int[] questionMarkRestIndices = new int[numQuestionMarksRest];
        int[] plusIndices = new int[numPluses];
        int[] fixedIndices = new int[numFixed];
        String[] fixedVars = new String[numFixed];

        int _i = -1;
        int _j = -1;
        int _k = -1;
        int _l = -1;

        for (int i = 0; i < vars.size(); i++) {
            if ("?".equals(vars.get(i)) && i < 2) questionMarkFirstTwoIndices[++_i] = i;
            else if ("?".equals(vars.get(i))) questionMarkRestIndices[++_j] = i;
            else if ("+".equals(vars.get(i))) plusIndices[++_k] = i;
            else {
                fixedIndices[++_l] = i;
                fixedVars[_l] = vars.get(i);
            }
        }

        List<String> vars1 = new ArrayList<String>(dataVars);
        vars1.removeAll(vars);

        ChoiceGenerator gen1 = new ChoiceGenerator(vars1.size(), questionMarkFirstTwoIndices.length);
        int[] choice1;

        LOOP:
        while ((choice1 = gen1.next()) != null) {
            List<String> s2 = asList(choice1, vars1);

            List<String> vars2 = new ArrayList<String>(vars1);
            vars2.removeAll(s2);

            ChoiceGenerator gen2 = new ChoiceGenerator(vars2.size(), questionMarkRestIndices.length);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                List<String> s3 = asList(choice2, vars2);

                List<String> vars3 = new ArrayList<String>(vars2);
                vars3.removeAll(s3);

                DepthChoiceGenerator gen3 = new DepthChoiceGenerator(vars3.size(), plusIndices.length);
                int[] choice3;

                while ((choice3 = gen3.next()) != null) {
                    results.add(new ArrayList<IndependenceResult>());

                    for (int prod = 0; prod < indTestProducers.size(); prod++) {
                        String[] vars4 = new String[fixedIndices.length + questionMarkFirstTwoIndices.length
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

                        IndependenceTest independenceTest = getIndependenceTest(prod);

                        Node x = independenceTest.getVariable(vars4[0]);
                        Node y = independenceTest.getVariable(vars4[1]);

                        List<Node> z = new ArrayList<Node>();

                        for (int i = 2; i < vars4.length; i++) {
                            z.add(independenceTest.getVariable(vars4[i]));
                        }

                        IndependenceResult.Type indep;
                        double pValue;

                        try {
                            indep = independenceTest.isIndependent(x, y, z) ? IndependenceResult.Type.INDEPENDENT : IndependenceResult.Type.DEPENDENT;
                            pValue = independenceTest.getPValue();
                        } catch (Exception e) {
                            indep = IndependenceResult.Type.UNDETERMINED;
                            pValue = Double.NaN;
                        }

                        results.get(results.size() - 1).add(new IndependenceResult(results.size(),
                                factString(x, y, z), indep, pValue));
                    }

                    if (results.size() > getListLimit()) break LOOP;
                }
            }
        }

        model.setResults(results);

        tableModel.fireTableDataChanged();
    }

    public static List<String> asList(int[] indices, List<String> nodes) {
        List<String> list = new LinkedList<String>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private LinkedList<String> getVars() {
        return vars;
    }

    private JTextField getTextField() {
        return textField;
    }

    public IndependenceFactsEditor(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
    }


    private static String factString(Node x, Node y, List<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(x.getName());
        sb.append(" _||_ ");
        sb.append(y.getName());

        Iterator<Node> it = condSet.iterator();

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

    private IndependenceTest getIndependenceTest(int i) {
        return indTestProducers.get(i).getIndependenceTest();
    }

    private int getLastSortCol() {
        return this.lastSortCol;
    }

    private void setLastSortCol(int lastSortCol) {
        if (lastSortCol < 0 || lastSortCol > 4) {
            throw new IllegalArgumentException();
        }

        this.lastSortCol = lastSortCol;
    }

    private int getSortDir() {
        return this.sortDir;
    }

    private void setSortDir(int sortDir) {
        if (!(sortDir == 1 || sortDir == -1)) {
            throw new IllegalArgumentException();
        }

        this.sortDir = sortDir;
    }

    private int getListLimit() {
        return Preferences.userRoot().getInt("indFactsListLimit", 10000);
    }

    private void setListLimit(int listLimit) {
        if (listLimit < 1) {
            throw new IllegalArgumentException();
        }

        Preferences.userRoot().putInt("indFactsListLimit", listLimit);
    }
}





