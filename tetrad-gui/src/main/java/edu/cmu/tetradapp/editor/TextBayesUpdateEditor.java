package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.TextBayesUpdateModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Text-first Bayes updater tool:
 * - Choose updater implementation
 * - Specify variable glob(s)
 * - Specify evidence + do() manipulations via a pasteable text format
 * - Compute updated marginals and show in a wide table
 * - Show detailed copy/paste report for a selected variable
 */
public final class TextBayesUpdateEditor extends JPanel {

    // --- Updater choices (you can rename labels as desired) ---
    private enum UpdaterKind {
        JunctionTree,
        RowSummingExact,
        Approximate
    }

    private final TextBayesUpdateModel model;
    private final BayesIm bayesIm;
    private final Parameters params;

    private final JComboBox<UpdaterKind> updaterCombo = new JComboBox<>(UpdaterKind.values());
    private final JTextField varGlobField = new JTextField("*");
    private final JTextArea evidenceText = new JTextArea(10, 60);

    private final JButton doUpdateButton = new JButton("Do Update");
    private final JButton viewResultButton = new JButton("View Result");

    private final ResultsTableModel resultsTableModel = new ResultsTableModel();
    private final JTable resultsTable = new JTable(resultsTableModel);

    // last run state (for “View Result”)
    private ManipulatingBayesUpdater lastUpdater = null;
    private Evidence lastEvidence = null;
    private String lastEvidenceText = "";

    public TextBayesUpdateEditor(TextBayesUpdateModel model) {
        this.model = Objects.requireNonNull(model, "model");
        this.bayesIm = model.getBayesIm();
        this.params = model.getParams();

        setLayout(new BorderLayout(10, 10));
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        wireActions();
        seedDefaultsFromParams();

        TableRowSorter<ResultsTableModel> sorter= new TableRowSorter<>(resultsTableModel);
        resultsTable.setRowSorter(sorter);
        resultsTable.setTransferHandler(new DefaultTableTransferHandler(0));
    }

    private JPanel buildTopPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int row = 0;

        // Updater dropdown
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("Updater:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        p.add(updaterCombo, c);
        row++;

        // Variable globs
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("Variables (globs):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        p.add(varGlobField, c);
        row++;

        // Evidence text
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.anchor = GridBagConstraints.NORTHWEST;
        p.add(new JLabel("Condition / Manipulate:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0; c.fill = GridBagConstraints.BOTH;
        JScrollPane sp = new JScrollPane(evidenceText);
        sp.setPreferredSize(new Dimension(650, 180));
        p.add(sp, c);

        return p;
    }

    private JPanel buildCenterPanel() {
        JPanel p = new JPanel(new BorderLayout(5,5));
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane sp = new JScrollPane(resultsTable);
        sp.setPreferredSize(new Dimension(800, 320));

        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(doUpdateButton);
        p.add(viewResultButton);
        return p;
    }

    private void wireActions() {
        doUpdateButton.addActionListener(e -> doUpdate());
        viewResultButton.addActionListener(e -> viewResultForSelectedRow());
    }

    private void seedDefaultsFromParams() {
        // Optional: restore prior UI state
        Object updater = params.get("textUpdater.kind");
        if (updater instanceof String s) {
            try { updaterCombo.setSelectedItem(UpdaterKind.valueOf(s)); } catch (Exception ignored) {}
        }
        Object glob = params.get("textUpdater.glob");
        if (glob instanceof String s) varGlobField.setText(s);

        Object ev = params.get("textUpdater.evidenceText");
        if (ev instanceof String s) evidenceText.setText(s);
        else {
            evidenceText.setText("""
                    # Examples:
                    # C: X = 1
                    # C: Y in {0,2}
                    # M: T = 1
                    """);
        }
    }

    private void persistUiToParams() {
        params.set("textUpdater.kind", String.valueOf(updaterCombo.getSelectedItem()));
        params.set("textUpdater.glob", varGlobField.getText());
        params.set("textUpdater.evidenceText", evidenceText.getText());
    }

    // ============================================================
    // Core action: parse text -> build Evidence -> run updater
    // ============================================================

    private void doUpdate() {
        persistUiToParams();

        try {
            EvidenceSpec spec = EvidenceSpec.parse(evidenceText.getText(), bayesIm);
            Evidence ev = spec.toEvidence(); // source-indexed Evidence

            ManipulatingBayesUpdater updater = createUpdater((UpdaterKind) updaterCombo.getSelectedItem(), bayesIm, ev);

            // Build table rows over selected variable subset
            List<Integer> nodeIndices = filterVariablesByGlob(bayesIm, varGlobField.getText());
            List<ResultRow> rows = new ArrayList<>();

            for (int nodeIndex : nodeIndices) {
                String name = bayesIm.getNode(nodeIndex).getName();
                int k = bayesIm.getNumColumns(nodeIndex);
                double[] probs = new double[k];
                for (int cat = 0; cat < k; cat++) {
                    probs[cat] = updater.getMarginal(nodeIndex, cat);
                }
                rows.add(new ResultRow(nodeIndex, name, probs));
            }

            resultsTableModel.setResults(rows, bayesIm);

            // Keep for “View Result”
            this.lastUpdater = updater;
            this.lastEvidence = ev;
            this.lastEvidenceText = evidenceText.getText();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Update failed:\n" + ex.getMessage(),
                    "Bayes Update Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private ManipulatingBayesUpdater createUpdater(UpdaterKind kind, BayesIm bayesIm, Evidence ev) {
        // IMPORTANT: the updater is the single source of truth for evidence.
        // No background listeners, no partial sync, no epicycles.
        return switch (kind) {
            case JunctionTree -> new JunctionTreeUpdater(bayesIm, ev);
            case RowSummingExact -> new RowSummingExactUpdater(bayesIm, ev);
            case Approximate -> {
                // Replace with your actual approximate class.
                // Example placeholder:
                yield new ApproximateUpdater(bayesIm, ev);
            }
        };
    }

    // ============================================================
    // View Result: copyable detailed report dialog
    // ============================================================

    private void viewResultForSelectedRow() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a variable row first.");
            return;
        }
        if (lastUpdater == null) {
            JOptionPane.showMessageDialog(this, "Run 'Do Update' first.");
            return;
        }

        ResultRow rr = resultsTableModel.getRow(row);
        int nodeIndex = rr.nodeIndex;

        String report = buildReport(nodeIndex, lastUpdater, lastEvidence, lastEvidenceText);

        JTextArea ta = new JTextArea(report, 28, 90);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ta.setCaretPosition(0);

        JScrollPane sp = new JScrollPane(ta);

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Updated Result: " + rr.varName);
        dlg.setModal(false);
        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(sp, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> {
            ta.selectAll();
            ta.copy();
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        buttons.add(copy);
        buttons.add(close);

        dlg.getContentPane().add(buttons, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private String buildReport(int nodeIndex, ManipulatingBayesUpdater updater, Evidence ev, String evText) {
        StringBuilder sb = new StringBuilder();

        Node node = bayesIm.getNode(nodeIndex);
        DiscreteVariable var = (DiscreteVariable) bayesIm.getBayesPm().getVariable(node);

        sb.append("Variable: ").append(node.getName()).append("\n");
        sb.append("Updater: ").append(updater.getClass().getSimpleName()).append("\n\n");

        sb.append("Evidence / DO (as entered):\n");
        sb.append(evText.strip()).append("\n\n");

        sb.append("Updated Marginal P(").append(node.getName()).append(" | evidence, do):\n");
        int k = bayesIm.getNumColumns(nodeIndex);
        double sum = 0.0;
        for (int cat = 0; cat < k; cat++) {
            double p = updater.getMarginal(nodeIndex, cat);
            sum += Double.isFinite(p) ? p : 0.0;
            sb.append(String.format("  %s = %s (index %d) : %.8f%n",
                    node.getName(),
                    categoryLabel(var, cat),
                    cat + 1,
                    p));
        }
        sb.append(String.format("  (sum=%.8f)%n%n", sum));

        // CPT from updated BayesIm (if supported; should be for ManipulatingBayesUpdater)
        BayesIm updated = updater.getUpdatedBayesIm();
        sb.append("Updated CPT rows for ").append(node.getName()).append(":\n");

        int[] parents = updated.getParents(nodeIndex);
        if (parents.length == 0) {
            sb.append("  (no parents)\n");
        } else {
            sb.append("  Parents: ");
            for (int i = 0; i < parents.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(updated.getNode(parents[i]).getName());
            }
            sb.append("\n");
        }

        int rows = updated.getNumRows(nodeIndex);
        int cols = updated.getNumColumns(nodeIndex);

        for (int r = 0; r < rows; r++) {
            int[] pv = updated.getParentValues(nodeIndex, r);
            sb.append("  Row ").append(r).append("  ");
            if (parents.length > 0) {
                sb.append("[");
                for (int i = 0; i < parents.length; i++) {
                    if (i > 0) sb.append(", ");
                    Node pNode = updated.getNode(parents[i]);
                    DiscreteVariable pvVar = (DiscreteVariable) updated.getBayesPm().getVariable(pNode);
                    sb.append(pNode.getName()).append("=").append(categoryLabel(pvVar, pv[i]));
                }
                sb.append("]");
            }
            sb.append("\n");

            for (int c = 0; c < cols; c++) {
                sb.append(String.format("    %s: %.8f%n", categoryLabel(var, c),
                        updated.getProbability(nodeIndex, r, c)));
            }
        }

        return sb.toString();
    }

    private static String categoryLabel(DiscreteVariable v, int catIndex) {
        try {
            String name = v.getCategory(catIndex);
            return (name != null) ? name : String.valueOf(catIndex);
        } catch (Exception e) {
            return String.valueOf(catIndex) + 1;
        }
    }

    // ============================================================
    // Variable globbing
    // ============================================================

    private static List<Integer> filterVariablesByGlob(BayesIm bayesIm, String globText) {
        String s = (globText == null) ? "*" : globText.trim();
        if (s.isEmpty()) s = "*";

        // allow comma-separated globs
        String[] globs = Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toArray(String[]::new);

        List<Pattern> patterns = new ArrayList<>();
        for (String g : globs) {
            patterns.add(Pattern.compile(globToRegex(g), Pattern.CASE_INSENSITIVE));
        }

        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            String name = bayesIm.getNode(i).getName();
            for (Pattern p : patterns) {
                if (p.matcher(name).matches()) {
                    out.add(i);
                    break;
                }
            }
        }
        return out;
    }

    private static String globToRegex(String glob) {
        // very small glob: '*' -> ".*", '?' -> "."
        StringBuilder sb = new StringBuilder("^");
        for (char ch : glob.toCharArray()) {
            switch (ch) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if ("+()^$|{}[]".indexOf(ch) >= 0) sb.append('\\');
                    sb.append(ch);
                }
            }
        }
        sb.append("$");
        return sb.toString();
    }

    // ============================================================
    // Table model
    // ============================================================

    private static final class ResultRow {
        final int nodeIndex;
        final String varName;
        final double[] marginals;

        ResultRow(int nodeIndex, String varName, double[] marginals) {
            this.nodeIndex = nodeIndex;
            this.varName = varName;
            this.marginals = marginals;
        }
    }

    private static final class ResultsTableModel extends AbstractTableModel {
        private List<ResultRow> rows = List.of();
        private String[] colNames = {"#", "Variable"};
        private int numCats = 0;

        void setResults(List<ResultRow> rows, BayesIm bayesIm) {
            this.rows = (rows == null) ? List.of() : rows;

            // pick a reasonable column schema:
            // if rows vary in category count, choose max (rare in BayesIm, but safe).
            int maxCats = 0;
            for (ResultRow r : this.rows) maxCats = Math.max(maxCats, r.marginals.length);
            this.numCats = maxCats;

            this.colNames = new String[2 + numCats];
            this.colNames[0] = "#";
            this.colNames[1] = "Variable";
            for (int c = 0; c < numCats; c++) {
                this.colNames[2 + c] = "P(cat " + (c + 1) + ")";
            }

            fireTableStructureChanged();
        }

        ResultRow getRow(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return colNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return colNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ResultRow r = rows.get(rowIndex);
            if (columnIndex == 0) return rowIndex;
            if (columnIndex == 1) return r.varName;
            int cat = columnIndex - 2;
            if (cat < 0 || cat >= r.marginals.length) return null;
            return r.marginals[cat];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex < 2) return String.class;
            return Double.class;
        }
    }

    // ============================================================
    // Evidence parser: lines like "C: X = 1", "M: T in {0,1}"
    // ============================================================

    private static final class EvidenceSpec {
        // source-indexed restrictions
        final Map<Integer, BitSet> allowed = new HashMap<>();
        final BitSet manipulated = new BitSet();

        final BayesIm bayesIm;

        private EvidenceSpec(BayesIm bayesIm) {
            this.bayesIm = bayesIm;
        }

        Evidence toEvidence() {
            Evidence e = Evidence.tautology(bayesIm);

            // apply allowed-category restrictions
            Proposition p = e.getProposition();
            for (Map.Entry<Integer, BitSet> en : allowed.entrySet()) {
                int node = en.getKey();
                BitSet ok = en.getValue();
                int k = e.getNumCategories(node);
                for (int cat = 0; cat < k; cat++) {
                    if (!ok.get(cat)) {
                        p.removeCategory(node, cat);
                    }
                }
            }

            // apply manipulations
            for (int i = manipulated.nextSetBit(0); i >= 0; i = manipulated.nextSetBit(i + 1)) {
                e.setManipulated(i, true);
            }

            return e;
        }

        static EvidenceSpec parse(String text, BayesIm bayesIm) {
            EvidenceSpec spec = new EvidenceSpec(bayesIm);

            String[] lines = (text == null ? "" : text).split("\\R");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;

                boolean isEvidence = startsWithIgnoreCase(line, "C:");
                boolean isDo = startsWithIgnoreCase(line, "M:");

                if (!isEvidence && !isDo) {
                    throw new IllegalArgumentException("Bad line (must start with C: or M: for condition on manipulate): " + raw);
                }

                String body = line.substring(line.indexOf(':') + 1).trim();
                // accepted forms:
                //   X = 1
                //   X in {0,2}
                //   X in {low, high}
                int eq = body.indexOf('=');
                int in = indexOfIgnoreCase(body, " in ");

                String varName;
                List<String> tokens;

                if (eq >= 0) {
                    varName = body.substring(0, eq).trim();
                    String rhs = body.substring(eq + 1).trim();
                    tokens = List.of(rhs);
                } else if (in >= 0) {
                    varName = body.substring(0, in).trim();
                    String rhs = body.substring(in + 4).trim();
                    tokens = parseSetTokens(rhs);
                } else {
                    throw new IllegalArgumentException("Bad line (expected '=' or 'in {...}'): " + raw);
                }

                int node = nodeIndexByName(bayesIm, varName);
                int k = bayesIm.getNumColumns(node);

                BitSet ok = new BitSet(k);
                for (String t : tokens) {
                    int cat = parseCategoryToken(bayesIm, node, t);
                    if (cat < 0 || cat >= k) {
                        throw new IllegalArgumentException("Category out of range for " + varName + ": " + t);
                    }
                    ok.set(cat);
                }

                // if M: mark manipulated
                if (isDo) {
                    spec.manipulated.set(node);
                }

                // store allowed; if repeated lines, intersect for evidence, but for DO we just set final allowed
                BitSet prior = spec.allowed.get(node);
                if (prior == null) {
                    spec.allowed.put(node, ok);
                } else {
                    // successive C: lines intersect (tighter restriction)
                    prior.and(ok);
                }
            }

            return spec;
        }

        private static List<String> parseSetTokens(String rhs) {
            String s = rhs.trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                s = s.substring(1, s.length() - 1).trim();
            }
            if (s.isEmpty()) return List.of();
            String[] parts = s.split(",");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        private static int nodeIndexByName(BayesIm bayesIm, String name) {
            Node n = bayesIm.getNode(name);
            if (n == null) throw new IllegalArgumentException("Unknown variable: " + name);
            return bayesIm.getNodeIndex(n);
        }

        private static int parseCategoryToken(BayesIm bayesIm, int node, String token) {
            // allow integer index
            String t = token.trim();
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {}

            // allow category name if DiscreteVariable exposes categories
            try {
                Node n = bayesIm.getNode(node);
                DiscreteVariable dv = (DiscreteVariable) bayesIm.getBayesPm().getVariable(n);
                for (int i = 0; i < dv.getNumCategories(); i++) {
                    String cn = dv.getCategory(i);
                    if (cn != null && cn.equalsIgnoreCase(t)) return i;
                }
            } catch (Exception ignored) {}

            throw new IllegalArgumentException("Unknown category token for node " + bayesIm.getNode(node).getName() + ": " + token);
        }

        private static boolean startsWithIgnoreCase(String s, String prefix) {
            return s.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        private static int indexOfIgnoreCase(String s, String needle) {
            String sl = s.toLowerCase(Locale.ROOT);
            String nl = needle.toLowerCase(Locale.ROOT);
            return sl.indexOf(nl);
        }
    }
}