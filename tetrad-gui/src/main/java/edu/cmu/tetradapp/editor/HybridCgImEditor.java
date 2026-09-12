package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgIo;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.HybridCgImWrapper;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Hybrid CG IM Editor
 *
 * <ul>
 *   <li><b>Left:</b> variable list (+ filter)</li>
 *   <li><b>Right (card):</b>
 *     <ul>
 *       <li>Discrete — full CPT table (all strata) + Normalize/Randomize actions</li>
 *       <li>Continuous — full regression table (all strata) + ShareVariance/Randomize actions</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>All numeric cells use <code>0.###</code> formatting (renderer + editor).</p>
 */
public final class HybridCgImEditor extends JPanel {

    private HybridCgIm im;
    private HybridCgPm pm;
    private Node[] nodes;

    /** The session wrapper, when constructed standalone; null when embedded. */
    private HybridCgImWrapper wrapper;

    /** Persistent shaded-graph view, when constructed with a graph tab; null otherwise. */
    private HybridCgGraphViewer graphView;
    private JTabbedPane tabs;

    // LEFT
    private final DefaultListModel<Node> varListModel = new DefaultListModel<>();
    private final JList<Node> varList = new JList<>(varListModel);
    private final JTextField filterField = new JTextField();

    /** Shown on the blank card; explains what to do when the model is empty. */
    private final JLabel blankHint = new JLabel();

    // RIGHT
    private final CardLayout cards = new CardLayout();
    private final JPanel right = new JPanel(cards);

    // Discrete card
    private final JPanel discCard = new JPanel(new BorderLayout(8,8));
    private final JLabel discInfo = new JLabel(
            "Rows are strata (combinations of discrete parent values). Columns are the child’s categories.");
    private JScrollPane discScroll; // created per-selection
    private final JButton discNormalizeAll = new JButton("Normalize All Rows");
    private final JButton discRandomizeAll = new JButton("Randomize Table");

    // Continuous card
    private final JPanel contCard = new JPanel(new BorderLayout(8,8));
    private final JLabel contInfo = new JLabel(
            "Regression parameters by stratum. Intercept / one column per continuous parent / Variance.");
    private JScrollPane contScroll; // created per-selection
    private final JButton contShareVar = new JButton("Share Variance Across Rows");
    private final JButton contRandomizeAll = new JButton("Randomize Table");

    // state
    private int currentY = -1;

    /**
     * True while {@link #selectVariable} is changing the list selection. The list's selection listener checks this to
     * skip the IM-to-graph echo (re-centering the graph on a node the user just clicked there) and the
     * "selectedVariable" event (the caller already knows).
     */
    private boolean externalSelection = false;

    // number formatting
    private static final DecimalFormat DF3 = new DecimalFormat("0.###");

    // --- Forward "modelChanged" from inner tables ---
    private javax.swing.event.TableModelListener discTml;
    private javax.swing.event.TableModelListener contTml;
    private javax.swing.table.TableModel discModel;
    private javax.swing.table.TableModel contModel;

    /** Standalone editor (session box): IM tables plus a Graph tab, with File and Graph menus. */
    public HybridCgImEditor(HybridCgImWrapper wrapper) {
        this(wrapper.getHybridCgIm(), true);
        this.wrapper = wrapper;
        add(buildMenuBar(), BorderLayout.NORTH);
    }

    /** Embedded editor (e.g. inside the estimator, which supplies its own Graph tab): IM tables only. */
    public HybridCgImEditor(HybridCgIm im) { this(im, false); }

    /**
     * @param im           the model to edit
     * @param withGraphTab if true, wrap the tables in an "IM" tab and add a "Graph" tab showing the model graph with
     *                     edges shaded by strength (see {@link HybridCgGraphViewer}); the graph is rebuilt each time
     *                     its tab is selected so it reflects edits made in the IM tab
     */
    public HybridCgImEditor(HybridCgIm im, boolean withGraphTab) {
        this.im = Objects.requireNonNull(im, "im");
        this.pm = im.getPm();
        this.nodes = pm.getNodes();

        JPanel tables = new JPanel(new BorderLayout(10,10));
        tables.add(buildLeft(), BorderLayout.WEST);
        tables.add(buildRight(), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        if (withGraphTab) {
            this.graphView = new HybridCgGraphViewer(im);
            this.tabs = new JTabbedPane();
            this.tabs.addTab("IM", tables);
            this.tabs.addTab("Graph", this.graphView.getComponent());
            this.tabs.setToolTipTextAt(1, "Model graph with edges shaded by strength");
            // Recolor on tab select so the graph reflects edits made in the IM tab. The workbench itself
            // persists, so menus and actions bound to it stay valid.
            this.tabs.addChangeListener(e -> {
                if (this.tabs.getSelectedIndex() == 1) {
                    this.graphView.update(this.im);
                    // update() rebuilt the display nodes, wiping any selection; re-apply the
                    // current variable so the graph opens centered on what the IM tab shows.
                    syncGraphSelection(varList.getSelectedValue());
                }
            });
            // Clicking a node in the Graph tab selects that variable in the IM tab, so its table is
            // showing when the user switches back. The workbench persists across update() calls, so
            // one listener suffices. Multi-node selections are ignored.
            this.graphView.getWorkbench().addPropertyChangeListener("selectedNodes", e -> {
                if (e.getNewValue() instanceof List<?> sel && sel.size() == 1
                    && sel.getFirst() instanceof Node n) {
                    selectVariable(n);
                }
            });
            add(this.tabs, BorderLayout.CENTER);
        } else {
            add(tables, BorderLayout.CENTER);
        }

        loadVariableList(null);
        if (!varListModel.isEmpty()) varList.setSelectedIndex(0);
        updateBlankHint();
    }

    /** Sets the blank-card text: an empty model gets a pointer to File > Load; otherwise the card is silent. */
    private void updateBlankHint() {
        boolean empty = this.nodes.length == 0;
        blankHint.setForeground(new Color(0x777777));
        blankHint.setText(empty
                ? "Empty model. Use File > Load Model From JSON... to load a saved Hybrid CG IM."
                : "");
    }

    // ============================ Menus ============================

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");

        JMenuItem saveJson = new JMenuItem("Save Model As JSON...");
        saveJson.addActionListener(e -> saveModelAsJson());
        file.add(saveJson);

        JMenuItem loadJson = new JMenuItem("Load Model From JSON...");
        loadJson.addActionListener(e -> loadModelFromJson());
        file.add(loadJson);

        file.addSeparator();
        file.add(onGraphTab(new SaveComponentImage(graphView.getWorkbench(), "Save Graph Image...")));

        menuBar.add(file);
        menuBar.add(graphView.graphMenu(this::showGraphTab, new Parameters()));
        menuBar.add(new LayoutMenu(graphView.getWorkbench()));
        return menuBar;
    }

    /** Wraps an action so the Graph tab is shown first (some actions need a laid-out workbench). */
    private JMenuItem onGraphTab(Action delegate) {
        JMenuItem item = new JMenuItem(new AbstractAction((String) delegate.getValue(Action.NAME)) {
            @Override public void actionPerformed(ActionEvent e) {
                showGraphTab();
                delegate.actionPerformed(e);
            }
        });
        return item;
    }

    private void showGraphTab() {
        if (this.tabs != null) this.tabs.setSelectedIndex(1);
    }

    private void saveModelAsJson() {
        File outfile = EditorUtils.getSaveFile("hybridcg_im", "json", this, false, "Save Model As JSON...");
        if (outfile == null) return;
        try {
            HybridCgIo.save(this.im, outfile);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadModelFromJson() {
        JFileChooser chooser = new JFileChooser();
        String dir = Preferences.userRoot().get("fileSaveLocation", Preferences.userRoot().absolutePath());
        chooser.setCurrentDirectory(new File(dir));
        chooser.setDialogTitle("Load Model From JSON...");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (file == null) return;
        Preferences.userRoot().put("fileSaveLocation", file.getParent());

        try {
            HybridCgIm loaded = HybridCgIo.load(file);
            if (this.wrapper != null) this.wrapper.setIm(loaded);
            setModel(loaded);
            firePropertyChange("modelChanged", null, null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Replaces the displayed model in place, rebuilding the variable list, tables, and graph view.
     *
     * @param newIm the model to display
     */
    public void setModel(HybridCgIm newIm) {
        this.im = Objects.requireNonNull(newIm, "im");
        this.pm = newIm.getPm();
        this.nodes = this.pm.getNodes();
        this.currentY = -1;

        filterField.setText("");
        varList.clearSelection();
        loadVariableList(null);
        if (!varListModel.isEmpty()) varList.setSelectedIndex(0);
        else cards.show(right, "blank");
        updateBlankHint();

        if (this.graphView != null) this.graphView.update(newIm);
        revalidate();
        repaint();
    }

    /**
     * Selects the variable with the given node's name in the variable list, clearing the name filter if it is hiding
     * that variable, and scrolls the list to it. The right-hand table follows via the list's selection listener.
     * Matching is by name, since callers (e.g. a graph view) may hold node objects from a copied graph. No-op if the
     * model has no variable of that name.
     *
     * @param node a node whose name identifies the variable to select
     */
    public void selectVariable(Node node) {
        if (node == null) return;

        Node match = null;
        for (Node n : this.nodes) {
            if (n.getName().equals(node.getName())) { match = n; break; }
        }
        if (match == null) return;

        this.externalSelection = true;
        try {
            if (!varListModel.contains(match)) { // hidden by the filter
                filterField.setText("");
                loadVariableList(null);
            }
            varList.setSelectedValue(match, true);
        } finally {
            this.externalSelection = false;
        }
    }

    /**
     * Selects and centers the given variable's node in the Graph tab's workbench, matching by name (the colored graph
     * holds copied nodes). No-op when this editor has no graph tab (the embedded case) or the node isn't in the
     * displayed graph.
     */
    private void syncGraphSelection(Node node) {
        if (this.graphView == null || node == null) return;
        Node wbNode = this.graphView.getWorkbench().getGraph().getNode(node.getName());
        if (wbNode != null) this.graphView.getWorkbench().centerWorkbenchOnNode(wbNode);
    }

    // ============================ LEFT ============================

    private JComponent buildLeft() {
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setBorder(new TitledBorder("Variables"));

        varList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        varList.setVisibleRowCount(18);
        varList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Node n) setText(n.getName());
                return this;
            }
        });
        varList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Node sel = varList.getSelectedValue();
            onSelectChild(sel);
            if (!externalSelection && sel != null) {
                // User-originated selection: mirror it in the Graph tab (standalone case) and tell
                // any container (the estimator syncs its own graph view off this event).
                syncGraphSelection(sel);
                firePropertyChange("selectedVariable", null, sel);
            }
        });

        filterField.setToolTipText("Filter by name (press Enter)");
        filterField.addActionListener(e -> loadVariableList(filterField.getText().trim()));

        left.add(filterField, BorderLayout.NORTH);
        left.add(new JScrollPane(varList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(220, 480));
        return left;
    }

    private void loadVariableList(String filter) {
        varListModel.clear();
        for (Node n : nodes) {
            if (filter == null || filter.isEmpty()
                || n.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) {
                varListModel.addElement(n);
            }
        }
    }

    // ============================ RIGHT ============================

    private JComponent buildRight() {
        // Discrete card scaffold
        discInfo.setBorder(BorderFactory.createEmptyBorder(4,8,0,8));
        JPanel discBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        discBtns.add(discNormalizeAll);
        discBtns.add(discRandomizeAll);
        discCard.add(discInfo, BorderLayout.NORTH);
        discCard.add(new JPanel(), BorderLayout.CENTER); // placeholder replaced per selection
        discCard.add(discBtns, BorderLayout.SOUTH);

        discNormalizeAll.addActionListener(e -> normalizeAllRows(currentY));
        discRandomizeAll.addActionListener(e -> { randomizeDiscreteTable(currentY); refreshActiveTable(); });

        // Continuous card scaffold
        contInfo.setBorder(BorderFactory.createEmptyBorder(4,8,0,8));
        JPanel contBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contBtns.add(contShareVar);
        contBtns.add(contRandomizeAll);
        contCard.add(contInfo, BorderLayout.NORTH);
        contCard.add(new JPanel(), BorderLayout.CENTER); // placeholder replaced per selection
        contCard.add(contBtns, BorderLayout.SOUTH);

        contShareVar.addActionListener(e -> shareVarianceAcrossRows(currentY));
        contRandomizeAll.addActionListener(e -> { randomizeContinuousTable(currentY); refreshActiveTable(); });

        right.setLayout(cards);
        JPanel blank = new JPanel(new GridBagLayout());
        blank.add(blankHint);
        right.add(blank, "blank");
        right.add(discCard, "disc");
        right.add(contCard, "cont");
        return right;
    }

    /** (Re)attach a TableModelListener that re-fires "modelChanged" when the user edits the table. */
    private void wireModelChanged(JTable table, boolean isDiscreteCard) {
        if (table == null) return;
        javax.swing.table.TableModel model = table.getModel();

        // Remove any old listener on the previous model for this card
        if (isDiscreteCard) {
            if (discModel != null && discTml != null) discModel.removeTableModelListener(discTml);
        } else {
            if (contModel != null && contTml != null) contModel.removeTableModelListener(contTml);
        }

        // New listener that forwards updates
        javax.swing.event.TableModelListener tml = e -> {
            // We forward on any update/insert/delete. If you want only edits, check e.getType().
            firePropertyChange("modelChanged", null, null);
        };

        model.addTableModelListener(tml);

        // Keep references so we can detach next time
        if (isDiscreteCard) {
            discTml = tml;
            discModel = model;
        } else {
            contTml = tml;
            contModel = model;
        }

        // (Optional) If the table itself fires a property change "modelChanged", forward that too:
        table.addPropertyChangeListener(evt -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });
    }

    // =========================== Selection ===========================

    private void onSelectChild(Node child) {
        if (child == null) { cards.show(right, "blank"); return; }

        currentY = pm.indexOf(child);

        if (pm.isDiscrete(currentY)) {
            // Build Bayes-style CPT table for this discrete child
            HybridCgCptEditingTable table = new HybridCgCptEditingTable(child, im);
            installDoubleFormatting(table); // numeric columns inside will use Double.class
//            discScroll = new JScrollPane(table);

            discScroll = new JScrollPane(table);
            replaceCenter(discCard, discScroll);

            discCard.remove(1);
            discCard.add(discScroll, BorderLayout.CENTER);

            wireModelChanged(table, true);

            // Update instructions
            int[] dps = pm.getDiscreteParents(currentY);
            if (dps.length == 0) {
                discInfo.setText("No discrete parents — single row with probabilities for the child’s categories.");
            } else {
                List<String> names = Arrays.stream(dps).mapToObj(i -> pm.getNodes()[i].getName()).collect(Collectors.toList());
                discInfo.setText("Rows are strata of " + names + "; columns are categories of " + child.getName() + ".");
            }

            cards.show(right, "disc");
        } else {
            // Full regression table
            HybridCgRegEditingTable table = new HybridCgRegEditingTable(im, pm, currentY);
            installDoubleFormatting(table); // ensure 0.### in-place
//            contScroll = new JScrollPane(table);

            contScroll = new JScrollPane(table);
            replaceCenter(contCard, contScroll);

            contCard.remove(1);
            contCard.add(contScroll, BorderLayout.CENTER);

            wireModelChanged(table, false);

            // Update instructions
            int[] dps = pm.getDiscreteParents(currentY);
            if (dps.length == 0) {
                contInfo.setText("No discrete parents — single stratum (one row). Columns: Intercept, parents, Variance.");
            } else {
                List<String> names = Arrays.stream(dps).mapToObj(i -> pm.getNodes()[i].getName()).collect(Collectors.toList());
                contInfo.setText("Rows are strata of " + names + ". Columns: Intercept, parents, Variance.");
            }

            cards.show(right, "cont");
        }
        revalidate(); repaint();
    }

    private static void replaceCenter(JPanel borderLayoutPanel, Component newCenter) {
        BorderLayout bl = (BorderLayout) borderLayoutPanel.getLayout();
        Component oldCenter = bl.getLayoutComponent(BorderLayout.CENTER);
        if (oldCenter != null) borderLayoutPanel.remove(oldCenter);
        borderLayoutPanel.add(newCenter, BorderLayout.CENTER);
        borderLayoutPanel.revalidate();
        borderLayoutPanel.repaint();
    }

//    private void refreshActiveTable() {
//        // Simple repaint of current card’s table after bulk operations
//        if (currentY < 0) return;
//        if (pm.isDiscrete(currentY)) {
//            if (discScroll != null && discScroll.getViewport().getView() instanceof JComponent jc) {
//                jc.repaint();
//            }
//        } else {
//            if (contScroll != null && contScroll.getViewport().getView() instanceof JComponent jc) {
//                jc.repaint();
//            }
//        }
//    }

    /** After bulk ops, refresh the active table more robustly (model event if possible). */
    private void refreshActiveTable() {
        if (currentY < 0) return;

        JScrollPane sc = pm.isDiscrete(currentY) ? discScroll : contScroll;
        if (sc == null) return;

        Component view = sc.getViewport().getView();

        if (view instanceof JTable jt) {
            if (jt.getModel() instanceof AbstractTableModel atm) {
                atm.fireTableDataChanged();  // tell the model to refresh
                return;
            }
            jt.revalidate();
            jt.repaint();
        } else if (view instanceof JComponent jc) {
            jc.revalidate();
            jc.repaint();
        }

        firePropertyChange("modelChanged", null, null);
    }

    // =========================== Actions ===========================

    private void normalizeAllRows(int y) {
        if (y < 0 || !pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        for (int r = 0; r < rows; r++) im.normalizeRow(y, r);
        refreshActiveTable();
        firePropertyChange("modelChanged", null, null);
    }

    private void shareVarianceAcrossRows(int y) {
        if (y < 0 || pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        double s = 0.0; int cnt = 0;
        for (int r = 0; r < rows; r++) { s += TMath.max(1e-12, im.getVariance(y, r)); cnt++; }
        double pooled = (cnt > 0) ? (s / cnt) : 1.0;
        for (int r = 0; r < rows; r++) im.setVariance(y, r, pooled);
        refreshActiveTable();
        firePropertyChange("modelChanged", null, null);
    }

    private void randomizeDiscreteTable(int y) {
        if (y < 0 || !pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        int K = pm.getCardinality(y);
        for (int r = 0; r < rows; r++) {
            double[] e = new double[K];
            double sum = 0.0;
            for (int k = 0; k < K; k++) { e[k] = -TMath.log(1.0 - RandomUtil.getInstance().nextDouble()); sum += e[k]; }
            for (int k = 0; k < K; k++) im.setProbability(y, r, k, e[k] / sum);
        }
        firePropertyChange("modelChanged", null, null);
    }

    private void randomizeContinuousTable(int y) {
        if (y < 0 || pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        int m = pm.getContinuousParents(y).length;
        for (int r = 0; r < rows; r++) {
            im.setMean(y, r, RandomUtil.getInstance().nextGaussian() * 0.25);
            for (int j = 0; j < m; j++) im.setCoefficient(y, r, j, RandomUtil.getInstance().nextUniform(-1, 1));
            im.setVariance(y, r, 0.25 + 0.75 * RandomUtil.getInstance().nextDouble());
        }
        firePropertyChange("modelChanged", null, null);
    }

    // ======================= Formatting helpers =======================

    /** Install 0.### renderer/editor on a JTable (or a JTable wrapped in our helper components). */
//    private void installDoubleFormatting(JTable table) {
//        if (table == null) return;
//        table.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
//            @Override protected void setValue(Object value) {
//                if (value instanceof Number n) setText(DF3.format(n.doubleValue()));
//                else super.setValue(value);
//            }
//        });
//        table.setDefaultEditor(Double.class, new DoubleCellEditor("0.###"));
//        table.setRowHeight(22);
//    }

    /** Install 0.### renderer/editor on a JTable (works for Double.class and Number.class). */
    private void installDoubleFormatting(JTable table) {
        if (table == null) return;

        DefaultTableCellRenderer numRenderer = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (value instanceof Number n) setText(DF3.format(n.doubleValue()));
                else super.setValue(value);
            }
        };

        // Apply to both Double and Number to be safe
        table.setDefaultRenderer(Double.class, numRenderer);
        table.setDefaultRenderer(Number.class, numRenderer);

        // Reuse our formatted editor for both keys
        TableCellEditor numEditor = new DoubleCellEditor("0.###");
        table.setDefaultEditor(Double.class, numEditor);
        table.setDefaultEditor(Number.class, numEditor);

        table.setRowHeight(22);
    }

    // Simple re-usable editor with formatted seed text
    public static final class DoubleCellEditor extends DefaultCellEditor {
        private final DecimalFormat fmt;
        public DoubleCellEditor(String pattern) {
            super(new JTextField());
            this.fmt = new DecimalFormat(pattern);
        }
        @Override public Object getCellEditorValue() {
            String s = ((JTextField)getComponent()).getText().trim();
            try { return Double.valueOf(s); } catch (Exception ex) { return Double.NaN; }
        }
        @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int r, int c) {
            ((JTextField)getComponent()).setText((v instanceof Number) ? fmt.format(((Number) v).doubleValue()) : "");
            return getComponent();
        }
    }

    // (kept for completeness if you ever need a tiny model table inline)
    private static double parseDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); }
        catch (Exception e) { return Double.NaN; }
    }

    // ======================= (Optional) tiny models =======================
    // You no longer need the old single-row table models; the dedicated
    // HybridCgCptEditingTable and HybridCgRegEditingTable show the full tables.
}