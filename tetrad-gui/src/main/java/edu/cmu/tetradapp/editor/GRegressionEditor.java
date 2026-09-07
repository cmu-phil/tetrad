package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.GRegression;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.GRegressionModel;
import edu.cmu.tetradapp.model.GRegressionModel.EffectMode;
import edu.cmu.tetradapp.model.GRegressionModel.ResultRow;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Editor for the "G-Regression Effects" regression tool ({@link GRegressionModel}).
 * <p>
 * The user gives treatments X and outcomes Y (names or wildcards), chooses pairwise point interventions or a joint
 * intervention, sets the number of bootstrap replications, and runs. The table shows one row per treatment
 * coordinate of each (A, Y) case: whether the effect is identified from the MPDAG (and if not, the witness path
 * whose first edge needs orienting), the estimate, its bootstrap standard error and 95% interval, and the true
 * effect when the data were simulated from a SEM.
 */
public final class GRegressionEditor extends JPanel {

    private final GRegressionModel model;
    private final Graph graph;

    private final JRadioButton pairwiseRadio = new JRadioButton("Point interventions: do(x) on y for all X–Y pairs");
    private final JRadioButton jointRadio = new JRadioButton("Joint intervention: do(X) on each y in Y");
    private final JRadioButton parentsRadio = new JRadioButton("Joint intervention: do(parents(y)) on each y in Y "
                                                               + "(X is ignored; in a DAG this gives y's structural "
                                                               + "equation)");
    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();
    private final JTextField bootstrapsField = new JTextField(6);
    private final JTextField seedField = new JTextField(6);
    private final JCheckBox meekCloseBox = new JCheckBox("Close graph under Meek's rules before estimating");
    private final JButton runButton = new JButton("Estimate total effects");
    private final JButton parentsButton = new JButton("X \u2190 parents of Y");
    private final JButton detailsButton = new JButton("Details...");
    private final JLabel graphLabel = new JLabel();

    private final JTable resultTable;
    private final ResultTableModel tableModel;

    private final DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer() {
        {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number n && !Double.isNaN(n.doubleValue())) {
                setText(NumberFormatUtil.getInstance().getNumberFormat().format(n.doubleValue()));
            } else if (value == null || (value instanceof Number)) {
                setText("");
            } else {
                setText(String.valueOf(value));
            }
        }
    };

    /**
     * Constructs the editor.
     *
     * @param model the model; must not be null
     */
    public GRegressionEditor(GRegressionModel model) {
        this.model = Objects.requireNonNull(model);
        this.graph = model.getGraph();

        this.tableModel = new ResultTableModel(model);
        this.resultTable = new JTable(tableModel);
        this.resultTable.setTransferHandler(new DefaultTableTransferHandler(0));
        this.resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        installSorter();
        installRenderers();

        treatmentsField.setText(model.getTreatmentsText());
        outcomesField.setText(model.getOutcomesText());
        bootstrapsField.setText(String.valueOf(model.getNumBootstraps()));
        seedField.setText(String.valueOf(model.getBootstrapSeed()));
        meekCloseBox.setSelected(model.isMeekClose());
        if (model.isKnowledgeAttached()) {
            meekCloseBox.setText("Close graph under Meek's rules (automatic: knowledge attached)");
            meekCloseBox.setEnabled(false);
        }

        treatmentsField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                model.setTreatmentsText(treatmentsField.getText());
            }
        });

        outcomesField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                model.setOutcomesText(outcomesField.getText());
            }
        });

        initUI();
        runButton.addActionListener(this::onRun);
        parentsButton.addActionListener(this::onParentsOfY);
        detailsButton.addActionListener(this::onDetails);
        updateGraphLabel();
    }

    // ---------------------------------------------------------------------------------------------------------
    // UI.
    // ---------------------------------------------------------------------------------------------------------

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(pairwiseRadio);
        modeGroup.add(jointRadio);
        modeGroup.add(parentsRadio);
        switch (model.getEffectMode()) {
            case JOINT -> jointRadio.setSelected(true);
            case JOINT_PARENTS -> parentsRadio.setSelected(true);
            default -> pairwiseRadio.setSelected(true);
        }

        JPanel modePanel = new JPanel(new GridLayout(0, 1));
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(pairwiseRadio);
        modePanel.add(jointRadio);
        modePanel.add(parentsRadio);
        modePanel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(modePanel);

        // X plays no role in the do(parents(y)) mode; gray it out there so that is visible at a glance.
        java.awt.event.ItemListener modeListener = e -> treatmentsField.setEnabled(!parentsRadio.isSelected());
        pairwiseRadio.addItemListener(modeListener);
        jointRadio.addItemListener(modeListener);
        parentsRadio.addItemListener(modeListener);
        treatmentsField.setEnabled(!parentsRadio.isSelected());

        // Labels take their natural width; the fields take the rest of the row.
        JPanel xyPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 5, 2, 5);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        xyPanel.add(new JLabel("Treatments (X):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        xyPanel.add(treatmentsField, c);
        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        xyPanel.add(new JLabel("Outcomes (Y):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        xyPanel.add(outcomesField, c);
        xyPanel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(xyPanel);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.add(new JLabel("Bootstrap replications (0 = none):"));
        optionsPanel.add(bootstrapsField);
        optionsPanel.add(new JLabel("Seed (-1 = none):"));
        optionsPanel.add(seedField);
        optionsPanel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(optionsPanel);

        // On its own row: a FlowLayout row that wraps in a narrow window gets its second line clipped.
        JPanel meekPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        meekPanel.add(meekCloseBox);
        meekPanel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(meekPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(parentsButton);
        buttonPanel.add(detailsButton);
        parentsButton.setToolTipText("Set the treatments to the directed parents of the single outcome in Y and "
                                     + "switch to a joint intervention; in a DAG this estimates Y's structural "
                                     + "equation (its direct effects).");
        buttonPanel.add(graphLabel);
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(buttonPanel);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // A wrapping, read-only text area, so the note takes the width it is given rather than dictating it.
        JTextArea note = new JTextArea("Assumes a linear SEM with independent errors and no latent confounding, "
                                       + "and that the graph is an MPDAG (DAG, CPDAG, or CPDAG plus knowledge). "
                                       + "For effects that are not identified, the first edge of the listed path "
                                       + "is the orientation the graph is missing; see Linear IDA Check for bounds.");
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setFocusable(false);
        note.setOpaque(false);
        note.setFont(new JLabel().getFont());
        note.setColumns(60);
        note.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        add(note, BorderLayout.SOUTH);
    }

    private void updateGraphLabel() {
        graphLabel.setText("   Graph: " + model.describeGraph());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Actions.
    // ---------------------------------------------------------------------------------------------------------

    private void onRun(ActionEvent e) {
        try {
            updateModelFromUI();
            // Validation is synchronous; estimation may run in a watched process, which does not block, so the
            // table is refreshed from the completion callback rather than here.
            model.recompute(this::refreshTable);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot estimate", JOptionPane.ERROR_MESSAGE);
            refreshTable();
        }
    }

    private void refreshTable() {
        tableModel.fireTableStructureChanged();
        installSorter();
        installRenderers();
        updateGraphLabel();
    }

    /**
     * Fills the treatments field with the directed parents of the single outcome named in the Y field and
     * switches to JOINT mode, so that running estimates the joint effect of do(Pa(Y)) on Y -- in a DAG, exactly
     * the structural equation of Y. Undirected neighbors of Y are deliberately not added, since the MPDAG does
     * not say whether they are parents; they are reported so the user can add them by hand if wanted, and the
     * identification check on Run adjudicates whatever is chosen.
     */
    private void onParentsOfY(ActionEvent e) {
        Set<Node> ys;
        try {
            ys = parseNodeList(outcomesField.getText().trim());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot fill parents", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (ys.size() != 1) {
            JOptionPane.showMessageDialog(this, "Please put a single outcome variable in the Y field first.",
                    "Cannot fill parents", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Graph g = model.getEffectiveGraph() != null ? model.getEffectiveGraph() : graph;
        Node y = g.getNode(ys.iterator().next().getName());

        List<Node> parents = new ArrayList<>(g.getParents(y));
        parents.sort(java.util.Comparator.comparing(Node::getName));

        List<String> undirected = g.getAdjacentNodes(y).stream()
                .filter(n -> {
                    var edge = g.getEdge(y, n);
                    return edge != null && edu.cmu.tetrad.graph.Edges.isUndirectedEdge(edge);
                })
                .map(Node::getName).sorted().toList();

        if (parents.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    y.getName() + " has no directed parents in the graph"
                    + (undirected.isEmpty() ? "." : "; its neighbors " + String.join(", ", undirected)
                                                    + " are connected only by undirected edges."),
                    "No parents", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        treatmentsField.setText(parents.stream().map(Node::getName).collect(Collectors.joining(", ")));
        model.setTreatmentsText(treatmentsField.getText());
        jointRadio.setSelected(true);

        if (!undirected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Set X to the directed parents of " + y.getName() + ": " + treatmentsField.getText() + ".\n\n"
                    + y.getName() + " also has undirected neighbors: " + String.join(", ", undirected) + ".\n"
                    + "The graph does not say whether these are parents, so they were not added; add them by\n"
                    + "hand if background knowledge says they are. Whether the joint effect on the listed\n"
                    + "parents is identified will be checked when you run.",
                    "Parents of " + y.getName(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onDetails(ActionEvent e) {
        int viewIndex = resultTable.getSelectedRow();

        if (viewIndex < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row first.", "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ResultRow row = tableModel.rowAt(resultTable.convertRowIndexToModel(viewIndex));
        Graph g = model.getEffectiveGraph() != null ? model.getEffectiveGraph() : graph;

        StringBuilder sb = new StringBuilder();
        sb.append("Treatments: ").append(row.formatTreatments()).append('\n');
        sb.append("Outcome:    ").append(row.outcome.getName()).append("\n\n");

        if (row.isIdentified()) {
            sb.append("Identified from the MPDAG.\n\n");
            sb.append(String.format("%-14s %12s %12s %12s%n", "Treatment", "Estimate", "Boot SE", "True"));
            for (int k = 0; k < row.treatments.size(); k++) {
                sb.append(String.format("%-14s %12.4f %12s %12s%n", row.treatments.get(k).getName(),
                        row.effect[k],
                        row.se == null ? "" : String.format("%.4f", row.se[k]),
                        row.trueEffect == null ? "" : String.format("%.4f", row.trueEffect[k])));
            }

            Set<Node> ySet = null;
            for (Set<Node> b : GRegression.bucketDecomposition(g)) {
                if (b.contains(row.outcome)) ySet = b;
            }
            if (ySet != null) {
                sb.append("\nBucket of the outcome: ").append(names(ySet));
                sb.append("\nIts external parents:  ").append(names(GRegression.externalParents(g, ySet)));

                try {
                    var eq = model.structuralEquation(row.outcome);
                    if (!eq.isEmpty()) {
                        StringBuilder terms = new StringBuilder();
                        var nf = NumberFormatUtil.getInstance().getNumberFormat();
                        for (var entry : eq.entrySet()) {
                            if (terms.length() > 0) terms.append(" + ");
                            terms.append(nf.format(entry.getValue())).append("*").append(entry.getKey().getName());
                        }
                        sb.append("\n\nStructural equation (bucket-recursive form):\n    ")
                                .append(row.outcome.getName()).append(" = ").append(terms).append(" + error\n");
                        sb.append(ySet.size() == 1
                                ? "(Singleton bucket: these are the direct effects, the coefficients of "
                                  + row.outcome.getName() + "'s structural equation.)"
                                : "(Bucket of size " + ySet.size() + ": these are the coefficients of the "
                                  + "bucket-recursive reparameterization, equal across all DAGs in the class; "
                                  + "they fold within-bucket paths and are not direct effects.)");
                    }
                } catch (IllegalArgumentException ignored) {
                    // Graph or data unusable for the equation; the dialog just omits it.
                }
            }
        } else {
            sb.append("Not identified. Witness path:\n\n    ")
                    .append(GRegression.pathString(g, row.witness)).append("\n\n");
            Node a = row.witness.get(0);
            Node u = row.witness.get(1);
            sb.append("This is a proper possibly causal path from a treatment to the outcome that starts with the "
                      + "undirected edge ").append(a.getName()).append(" --- ").append(u.getName())
                    .append(".\nIn DAGs of the class where it is ").append(a.getName()).append(" --> ")
                    .append(u.getName()).append(" the path can carry effect; where it is ").append(u.getName())
                    .append(" --> ").append(a.getName()).append(" it cannot, so the DAGs disagree.\n\n")
                    .append("To make progress, supply the orientation of that edge as background knowledge "
                            + "(then Meek-close), or use Linear IDA Check for bounds over the class.");
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "G-regression: " + row.formatTreatments() + " → " + row.outcome.getName(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static String names(Set<Node> s) {
        if (s.isEmpty()) return "∅";
        return s.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
    }

    private void updateModelFromUI() {
        EffectMode mode = pairwiseRadio.isSelected() ? EffectMode.PAIRWISE
                : jointRadio.isSelected() ? EffectMode.JOINT : EffectMode.JOINT_PARENTS;

        Set<Node> Y = parseNodeList(outcomesField.getText().trim());
        if (Y.isEmpty()) {
            throw new IllegalArgumentException("The outcomes set must not be empty.");
        }

        Set<Node> X = mode == EffectMode.JOINT_PARENTS ? java.util.Set.of()
                : parseNodeList(treatmentsField.getText().trim());
        if (X.isEmpty() && mode != EffectMode.JOINT_PARENTS) {
            throw new IllegalArgumentException("The treatments set must not be empty in this mode.");
        }

        model.setX(X);
        model.setY(Y);
        model.setEffectMode(mode);
        model.setMeekClose(meekCloseBox.isSelected());

        try {
            model.setNumBootstraps(Integer.parseInt(bootstrapsField.getText().trim()));
            model.setBootstrapSeed(Long.parseLong(seedField.getText().trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bootstrap replications and seed must be integers.");
        }
    }

    /**
     * Parses a comma- or space-separated list of node names, with shell-style wildcards (* and ?).
     */
    private Set<Node> parseNodeList(String text) {
        LinkedHashSet<Node> nodes = new LinkedHashSet<>();
        if (text.isEmpty()) return nodes;

        for (String tok : text.split("[,\\s]+")) {
            String name = tok.trim();
            if (name.isEmpty()) continue;

            if (!name.contains("*") && !name.contains("?")) {
                Node n = graph.getNode(name);
                if (n == null) throw new IllegalArgumentException("Unknown variable: " + name);
                nodes.add(n);
                continue;
            }

            Pattern p = Pattern.compile(wildcardToRegex(name));
            boolean matchedAny = false;
            for (Node n : graph.getNodes()) {
                if (p.matcher(n.getName()).matches()) {
                    nodes.add(n);
                    matchedAny = true;
                }
            }
            if (!matchedAny) {
                throw new IllegalArgumentException("Wildcard pattern \"" + name + "\" matched no variables.");
            }
        }

        return nodes;
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '\\', '.', '[', ']', '{', '}', '(', ')', '+', '-', '^', '$', '|' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.append('$').toString();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Table.
    // ---------------------------------------------------------------------------------------------------------

    private void installRenderers() {
        var cm = resultTable.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            String name = cm.getColumn(i).getHeaderValue().toString();
            switch (name) {
                case ResultTableModel.COL_EST, ResultTableModel.COL_SE, ResultTableModel.COL_TRUE ->
                        cm.getColumn(i).setCellRenderer(numberRenderer);
                default -> {
                }
            }
            cm.getColumn(i).setPreferredWidth(switch (name) {
                case ResultTableModel.COL_NUM -> 40;
                case ResultTableModel.COL_A, ResultTableModel.COL_IDENT -> 220;
                case ResultTableModel.COL_CI -> 150;
                default -> 90;
            });
        }
    }

    private void installSorter() {
        TableRowSorter<ResultTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(1, NaturalSort.naturalComparator());
        sorter.setComparator(2, NaturalSort.naturalComparator());
        sorter.setComparator(3, NaturalSort.naturalComparator());
        resultTable.setRowSorter(sorter);
    }

    /**
     * One table row per treatment coordinate of each result row, so a joint intervention on k treatments shows as
     * k rows sharing the same A and Y.
     */
    private static final class ResultTableModel extends AbstractTableModel {
        static final String COL_NUM = "#";
        static final String COL_A = "Treatments (A)";
        static final String COL_X = "Effect of";
        static final String COL_Y = "Outcome (Y)";
        static final String COL_IDENT = "Identified";
        static final String COL_EST = "Estimate";
        static final String COL_SE = "Boot SE";
        static final String COL_CI = "95% interval";
        static final String COL_TRUE = "True effect";

        private final GRegressionModel model;

        ResultTableModel(GRegressionModel model) {
            this.model = model;
        }

        private String[] columns() {
            List<String> cols = new ArrayList<>(List.of(COL_NUM));
            if (model.getEffectMode() != EffectMode.PAIRWISE) cols.add(COL_A);
            cols.addAll(List.of(COL_X, COL_Y, COL_IDENT, COL_EST));
            if (model.getNumBootstraps() > 1) cols.addAll(List.of(COL_SE, COL_CI));
            if (model.isTrueSemImAvailable()) cols.add(COL_TRUE);
            return cols.toArray(new String[0]);
        }

        /**
         * Flattened index: (result row, coordinate) pairs.
         */
        private List<int[]> index() {
            List<int[]> idx = new ArrayList<>();
            List<ResultRow> rows = model.getResults();
            for (int r = 0; r < rows.size(); r++) {
                for (int k = 0; k < rows.get(r).treatments.size(); k++) idx.add(new int[]{r, k});
            }
            return idx;
        }

        ResultRow rowAt(int flatIndex) {
            return model.getResults().get(index().get(flatIndex)[0]);
        }

        @Override
        public int getRowCount() {
            return index().size();
        }

        @Override
        public int getColumnCount() {
            return columns().length;
        }

        @Override
        public String getColumnName(int column) {
            return columns()[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            String col = columns()[columnIndex];
            if (COL_NUM.equals(col)) return Integer.class;
            if (COL_EST.equals(col) || COL_SE.equals(col) || COL_TRUE.equals(col)) return Double.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<int[]> idx = index();
            if (rowIndex < 0 || rowIndex >= idx.size()) return null;
            int r = idx.get(rowIndex)[0], k = idx.get(rowIndex)[1];
            ResultRow row = model.getResults().get(r);
            String col = columns()[columnIndex];
            Graph g = model.getEffectiveGraph() != null ? model.getEffectiveGraph() : model.getGraph();

            switch (col) {
                case COL_NUM:
                    return rowIndex + 1;
                case COL_A:
                    return row.formatTreatments();
                case COL_X:
                    return row.treatments.get(k).getName();
                case COL_Y:
                    return row.outcome.getName();
                case COL_IDENT:
                    return row.formatIdentification(g);
                case COL_EST:
                    return row.effect == null ? Double.NaN : row.effect[k];
                case COL_SE:
                    return row.se == null ? Double.NaN : row.se[k];
                case COL_CI: {
                    if (row.effect == null || row.se == null) return "";
                    var nf = NumberFormatUtil.getInstance().getNumberFormat();
                    double lo = row.effect[k] - 1.96 * row.se[k], hi = row.effect[k] + 1.96 * row.se[k];
                    return "[" + nf.format(lo) + ", " + nf.format(hi) + "]";
                }
                case COL_TRUE:
                    return row.trueEffect == null ? Double.NaN : row.trueEffect[k];
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
