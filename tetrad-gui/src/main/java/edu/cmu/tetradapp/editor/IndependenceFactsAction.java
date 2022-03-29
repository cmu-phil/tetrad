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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.editor.IndependenceFactsAction.Result.Type;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.IntTextField.Filter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;


/**
 * Lists independence facts specified by user and allows the list to be sorted by independence fact or by p value.
 *
 * @author Joseph Ramsey
 */
public class IndependenceFactsAction extends AbstractAction {
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    private final Component centeringComp;
    private final LinkedList<String> vars;
    private final JTextField textField;
    private final IndTestProducer indTestProducer;
    private final List<Result> results = new ArrayList<>();
    private AbstractTableModel tableModel;
    private IndependenceTest independenceTest;
    private int sortDir;
    private int lastSortCol;

    /**
     * Constructs a new action to open sessions.
     *
     * @param comp            The component it should be in front of.
     * @param indTestProducer The gadget you get the independence test from.
     * @param menuName        The name that appears in the menu.
     */
    public IndependenceFactsAction(Component comp,
                                   IndTestProducer indTestProducer,
                                   String menuName) {
        super(menuName);

        if (indTestProducer == null) {
            throw new NullPointerException();
        }

        centeringComp = comp;
        this.indTestProducer = indTestProducer;
        vars = new LinkedList<>();
        textField = new JTextField(40);
        textField.setEditable(false);
        textField.setFont(new Font("Serif", Font.BOLD, 14));
        textField.setBackground(new Color(250, 250, 250));

        this.resetText();
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the action of opening a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        independenceTest = this.getIndTestProducer().getIndependenceTest();
        List<String> varNames = new ArrayList<>();
        varNames.add("VAR");
        varNames.addAll(this.getDataVars());
        varNames.add("?");
        varNames.add("+");

        JComboBox variableBox = new JComboBox();
        DefaultComboBoxModel aModel1 = new DefaultComboBoxModel(varNames.toArray(new String[varNames.size()]));
        aModel1.setSelectedItem("VAR");
        variableBox.setModel(aModel1);

//        variableBox.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent e) {
//                System.out.println(e);
//            }
//        });

        variableBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();

                String var = (String) box.getSelectedItem();
                LinkedList<String> vars = IndependenceFactsAction.this.getVars();
                int size = vars.size();

                if ("VAR".equals(var)) {
                    return;
                }

                for (int i = 2; i < IndependenceFactsAction.this.getVars().size() - 1; i++) {
                    if (IndependenceFactsAction.this.wildcard(i)) {
                        if (!("?".equals(var) || "+".equals(var))) {
                            JOptionPane.showMessageDialog(centeringComp, "Please specify wildcards after other variables (e.g. X _||_ ? | Y, +)");
                            return;
                        }
                    }
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

                if (IndependenceFactsAction.this.wildcard(0) && vars.size() >= 2 && !IndependenceFactsAction.this.wildcard(1)) {
                    JOptionPane.showMessageDialog(centeringComp, "Please specify wildcards after other variables (e.g. X _||_ ? | Y, +)");
                    return;
                }

                IndependenceFactsAction.this.resetText();

                // This is a workaround to an introduced bug in the JDK whereby
                // repeated selections of the same item send out just one
                // action event.
                DefaultComboBoxModel aModel = new DefaultComboBoxModel(
                        varNames.toArray(new String[varNames.size()]));
                aModel.setSelectedItem("VAR");
                variableBox.setModel(aModel);
            }
        });

        JButton delete = new JButton("Delete");

        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!IndependenceFactsAction.this.getVars().isEmpty()) {
                    IndependenceFactsAction.this.getVars().removeLast();
                    IndependenceFactsAction.this.resetText();
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
                    IndependenceFactsAction.this.resetText();
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
                    IndependenceFactsAction.this.resetText();
                }
            }
        });

        variableBox.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);

                if ('\b' == e.getKeyChar()) {
                    vars.removeLast();
                    IndependenceFactsAction.this.resetText();
                }
            }
        });

        JButton list = new JButton("LIST");
        list.setFont(new Font("Dialog", Font.BOLD, 14));

        list.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                IndependenceFactsAction.this.generateResults();
            }
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Test: "));
        b2.add(new JLabel(this.getIndependenceTest().toString()));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        Box b3 = Box.createHorizontalBox();
        b3.add(this.getTextField());
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
                    if (independenceTest instanceof IndTestDSep) {
                        return "D-Separation Relation";
                    } else {
                        return "Independence Relation";
                    }
                } else if (column == 2) {
                    return "Judgment";
                } else if (column == 3) {
                    return "P Value";
                }

                return null;
            }

            public int getColumnCount() {
                if (IndependenceFactsAction.this.usesDSeparation()) {
                    return 3;
                } else {
                    return 4;
                }
            }

            public int getRowCount() {
                return IndependenceFactsAction.this.getResults().size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                Result result = IndependenceFactsAction.this.getResults().get(rowIndex);

                if (columnIndex == 0) {
                    return result.getIndex() + 1;
                }
                if (columnIndex == 1) {
                    return result.getFact();
                } else if (columnIndex == 2) {
                    if (independenceTest instanceof IndTestDSep) {
                        if (result.getType() == Type.INDEPENDENT) {
                            return "D-Separated";
                        } else if (result.getType() == Type.DEPENDENT) {
                            return "D-Connected";
                        } else if (result.getType() == Type.UNDETERMINED) {
                            return "*";
                        }

//                        return result.getType() ? "D-Separated" : "D-Connected";
                    } else {
                        if (result.getType() == Type.INDEPENDENT) {
                            return "Independent";
                        } else if (result.getType() == Type.DEPENDENT) {
                            return "Dependent";
                        } else if (result.getType() == Type.UNDETERMINED) {
                            return "*";
                        }
//                        return result.getType() ? "Independent" : "Dependent";
                    }
                } else if (columnIndex == 3) {
                    return nf.format(result.getpValue());
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
                } else if (columnIndex == 3) {
                    return Number.class;
                }

                return null;
            }
        };

        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(200);
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(100);

        if (!(this.usesDSeparation())) {
            table.getColumnModel().getColumn(3).setMinWidth(80);
            table.getColumnModel().getColumn(3).setMaxWidth(80);
        }

        JTableHeader header = table.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                IndependenceFactsAction.this.sortByColumn(sortCol, true);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 400));
        b1.add(scroll);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Limit list to "));


        IntTextField field = new IntTextField(this.getListLimit(), 7);

        field.setFilter(new Filter() {
            public int filter(int value, int oldValue) {
                try {
                    IndependenceFactsAction.this.setListLimit(value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });

        b4.add(field);
        b4.add(new JLabel(" items."));
        b4.add(Box.createHorizontalGlue());
        b4.add(list);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        EditorWindow editorWindow =
                new EditorWindow(panel, "Independence Facts", "Save", false, centeringComp);
        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.setVisible(true);

        // Set the ok button so that pressing enter activates it.
        // jdramsey 5/5/02
        JRootPane root = SwingUtilities.getRootPane(editorWindow);
        if (root != null) {
            root.setDefaultButton(list);
        }
    }

    //=============================PRIVATE METHODS=======================//

    private boolean usesDSeparation() {
        return this.getIndependenceTest() instanceof IndTestDSep;
    }

    private void sortByColumn(int sortCol, boolean allowReverse) {
        if (allowReverse && sortCol == this.getLastSortCol()) {
            this.setSortDir(-1 * this.getSortDir());
        } else {
            this.setSortDir(1);
        }

        this.setLastSortCol(sortCol);

        Collections.sort(results, new Comparator<Result>() {
            public int compare(Result r1, Result r2) {

                switch (sortCol) {
                    case 0:
                        return IndependenceFactsAction.this.getSortDir() * (r1.getIndex() - r2.getIndex());
                    case 1:
                        return IndependenceFactsAction.this.getSortDir() * (r1.getIndex() - r2.getIndex());
                    case 2:
                        int ind1;
                        int ind2;

                        if (r1.getType() == Type.UNDETERMINED) {
                            ind1 = 0;
                        } else if (r1.getType() == Type.DEPENDENT) {
                            ind1 = 1;
                        } else {
                            ind1 = 2;
                        }

                        if (r2.getType() == Type.UNDETERMINED) {
                            ind2 = 0;
                        } else if (r2.getType() == Type.DEPENDENT) {
                            ind2 = 1;
                        } else {
                            ind2 = 2;
                        }

//                        int ind1 = r1.getType() ? 1 : 0;
//                        int ind2 = r2.getType() ? 1 : 0;
                        return IndependenceFactsAction.this.getSortDir() * (ind1 - ind2);
                    case 3:
                        double difference = IndependenceFactsAction.this.getSortDir() *
                                (r1.getpValue() - r2.getpValue());

                        if (difference < 0) {
                            return -1;
                        } else if (difference == 0) {
                            return 0;
                        } else {
                            return 1;
                        }
                    default:
                        return 0;
                }
            }
        });

        tableModel.fireTableDataChanged();
    }

    private List<String> getDataVars() {
        return this.getIndependenceTest().getVariableNames();
    }

    private void resetText() {
        StringBuilder buf = new StringBuilder();

        if (this.getVars().size() == 0) {
            buf.append("Choose variables and wildcards from dropdown-->");
        }

        if (this.getVars().size() > 0) {
            buf.append(" ").append(this.getVars().get(0));
            buf.append(" _||_ ");
        }

        if (this.getVars().size() > 1) {
            buf.append(this.getVars().get(1));
        }

        if (this.getVars().size() > 2) {
            buf.append(" | ");
        }

        for (int i = 2; i < this.getVars().size() - 1; i++) {
            buf.append(this.getVars().get(i));
            buf.append(", ");
        }

        if (this.getVars().size() > 2) {
            buf.append(this.getVars().get(this.getVars().size() - 1));
        }

        textField.setText(buf.toString());
    }

    private void generateResults() {
        getResults().clear();
        List<String> dataVars = this.getDataVars();

        if (this.getVars().size() < 2) {
            tableModel.fireTableDataChanged();
            return;
        }

        int minQuestionMark = 0, minPlus = 0, maxPlus = 0;

        for (int i = 2; i < this.getVars().size(); i++) {
            String var = this.getVars().get(i);

            if ("?".equals(var)) {
                minPlus++;
                maxPlus++;
            } else if ("+".equals(var)) {
                maxPlus++;
            } else {
                minQuestionMark++;
                minPlus++;
                maxPlus++;
            }
        }

        int resultIndex = -1;
        Set<Set<String>> alreadySeen = new HashSet<>();

        int xIndex = dataVars.indexOf(vars.get(0));
        int yIndex = dataVars.indexOf(vars.get(1));

        if (xIndex == -1 || yIndex == -1) {
            xIndex = 0;
            yIndex = 1;
        }

        for (int _i = 0; _i < dataVars.size(); _i++) {
            for (int _j = _i + 1; _j < dataVars.size(); _j++) {

                String _x;
                String _y;

                if (xIndex < yIndex) {
                    _x = dataVars.get(_i);
                    _y = dataVars.get(_j);
                } else {
                    _y = dataVars.get(_i);
                    _x = dataVars.get(_j);
                }

                if (!(vars.get(0).equals("?")) && !(vars.get(0).equals("+")) && !(vars.get(0).equals(_x))) {
                    continue;
                }

                if (!(vars.get(1).equals("?")) && !(vars.get(1).equals("+")) && !(vars.get(1).equals(_y))) {
                    continue;
                }

                Set<String> seen = new HashSet<>();
                seen.add(_x);
                seen.add(_y);

                if (alreadySeen.contains(seen)) continue;
                alreadySeen.add(seen);
//
//                if (!wildcard(0) && !getVars().get(0).equals(_y)) {
//                    continue;
//                }

                if (!this.wildcard(1) && !this.getVars().get(1).equals(_y)) {
                    continue;
                }

                List<String> unspecifiedVars = new ArrayList<>(dataVars);
                unspecifiedVars.remove(_x);
                unspecifiedVars.remove(_y);

                Node x = this.getIndependenceTest().getVariable(_x);
                Node y = this.getIndependenceTest().getVariable(_y);

                for (int j = 2; j < this.getVars().size(); j++) {
                    if (!this.wildcard(j)) {
                        unspecifiedVars.remove(this.getVars().get(j));
                    }
                }

                for (int n = minPlus; n <= maxPlus; n++) {
                    ChoiceGenerator gen2 = new ChoiceGenerator(unspecifiedVars.size(), n - minQuestionMark);
                    int[] choice2;

                    while ((choice2 = gen2.next()) != null) {
                        List<Node> z = new ArrayList<>();

                        for (int i = 0; i < minQuestionMark; i++) {
                            String _z = this.getVars().get(i + 2);
                            z.add(this.getIndependenceTest().getVariable(_z));
                        }

                        for (int choice : choice2) {
                            String _z = unspecifiedVars.get(choice);
                            z.add(this.getIndependenceTest().getVariable(_z));
                        }

                        Type indep;
                        double pValue;

                        try {
                            indep = this.getIndependenceTest().isIndependent(x, y, z) ? Type.INDEPENDENT : Type.DEPENDENT;
                        } catch (Exception e) {
                            indep = Type.UNDETERMINED;
                        }

                        try {
                            pValue = this.getIndependenceTest().getPValue();
                        } catch (Exception e) {
                            pValue = Double.NaN;
                        }

                        if (this.usesDSeparation()) {
                            this.getResults().add(new Result(++resultIndex,
                                    dsepFactString(x, y, z), indep, pValue));
                        } else {
                            this.getResults().add(new Result(++resultIndex,
                                    independenceFactString(x, y, z), indep, pValue));
                        }
                    }
                }
            }
        }

        tableModel.fireTableDataChanged();
    }

    private boolean wildcard(int index) {
        return index < this.getVars().size() && (this.getVars().get(index).equals("?") || this.getVars().get(index).equals("+"));
    }

    private LinkedList<String> getVars() {
        return vars;
    }

    private JTextField getTextField() {
        return textField;
    }

    private IndTestProducer getIndTestProducer() {
        return indTestProducer;
    }

    private List<Result> getResults() {
        return results;
    }

    public static final class Result {
        public enum Type {
            INDEPENDENT, DEPENDENT, UNDETERMINED
        }

        private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        private final int index;
        private final String fact;
        private final Type indep;
        private final double pValue;

        public Result(int index, String fact, Type indep, double pValue) {
            this.index = index;
            this.fact = fact;
            this.indep = indep;
            this.pValue = pValue;
        }

        public int getIndex() {
            return index;
        }

        public String getFact() {
            return fact;
        }

        public Type getType() {
            return indep;
        }

        public double getpValue() {
            return pValue;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Result: ");
            buf.append(this.getFact()).append("\t");
            buf.append(this.getType()).append("\t");
            buf.append(nf.format(this.getpValue()));
            return buf.toString();
        }
    }

    private static String independenceFactString(Node x, Node y,
                                                 List<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(" ").append(x.getName());
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

    private static String dsepFactString(Node x, Node y, List<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(" ").append("dsep(");
        sb.append(x.getName());
        sb.append(", ");
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

        sb.append(")");

        return sb.toString();
    }

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    private int getLastSortCol() {
        return lastSortCol;
    }

    private void setLastSortCol(int lastSortCol) {
        if (lastSortCol < 0 || lastSortCol > 4) {
            throw new IllegalArgumentException();
        }

        this.lastSortCol = lastSortCol;
    }

    private int getSortDir() {
        return sortDir;
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





