package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockDiscoverer;
import edu.cmu.tetrad.search.blocks.BlockDiscoverers;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlockSpecTextCodec;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.search.ntad_test.Wishart;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Wizard: Step 1: Choose clustering algorithm + tweak simple params, then Search. Step 2: View/Edit discovered blocks
 * via BlockSpecEditorPanel.
 */
public class BlockClusteringWizard extends JPanel {

    // ---- Test families (ARK is commented out / removed here) ----
    private static final String TEST_CCA = "CCA";
    private static final String TEST_BT = "Bollen-Ting";
    private static final String TEST_WIS = "Wishart";
    private static final List<String> TESTS_FOFC = List.of(TEST_CCA, TEST_BT, TEST_WIS);
    private static final List<String> TESTS_BPC = List.of(TEST_CCA, TEST_BT, TEST_WIS);
    private static final List<String> TESTS_FTFC = List.of(TEST_CCA, TEST_BT);      // no Wishart
    private static final List<String> TESTS_NONE = List.of();                       // TSC
    // private static final String TEST_ARK = "ARK"; // commented out per instruction
    // ---- UI ----
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JPanel pageSetup = new JPanel(new BorderLayout(8, 8));
    private final JPanel pageResult = new JPanel(new BorderLayout());
    // ... UI fields ...
    private final JComboBox<String> cbAlgorithm = new JComboBox<>(new String[]{"FOFC", "BPC", "FTFC", "TSC Test", "TSC Score"});

    // Put near your fields
    private final JFormattedTextField tfAlpha = createAlphaField();
    private final JComboBox<String> cbTetradTest = new JComboBox<>();
    private final JButton btnSearch = new JButton("Search");
    private final JLabel status = new JLabel("Ready.");
    private final JButton btnBack = new JButton("◀ Back");
    private final JLabel lblResultTitle = new JLabel();
    private final BlockSpecEditorPanel editorPanel;
    // ---- State ----
    private final DataSet dataSet;
    private final java.util.List<BlockSpecListener> specListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private BlockSpec blockSpec = null;
    private double alpha = 0.01;

    public BlockClusteringWizard(DataSet dataSet) {
        super(new BorderLayout(8, 8));
        this.dataSet = Objects.requireNonNull(dataSet);

        tfAlpha.setValue(alpha);
        tfAlpha.setColumns(6);

        // Page 1 (setup)
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.gridy = 0;
        top.add(new JLabel("Algorithm:"), gc);
        gc.gridx = 1;
        cbAlgorithm.setSelectedItem("TSC");
        top.add(cbAlgorithm, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        top.add(new JLabel("Alpha:"), gc);
        gc.gridx = 1;
        tfAlpha.setToolTipText("Significance level (e.g., 0.01)");
        top.add(tfAlpha, gc);

        gc.gridx = 0;
        gc.gridy = 2;
        top.add(new JLabel("Ntad test:"), gc);
        gc.gridx = 1;
        cbTetradTest.setSelectedItem("CCA");
        top.add(cbTetradTest, gc);

        JPanel southSetup = new JPanel(new BorderLayout());
        southSetup.add(status, BorderLayout.CENTER);
        southSetup.add(btnSearch, BorderLayout.EAST);

        pageSetup.add(top, BorderLayout.NORTH);
        pageSetup.add(new JSeparator(), BorderLayout.CENTER);
        pageSetup.add(southSetup, BorderLayout.SOUTH);

        // Page 2 (results)
        editorPanel = new BlockSpecEditorPanel(dataSet);
        // in BlockClusteringWizard constructor, after creating editorPanel:
        // forward Apply to the same listener bus
        editorPanel.setOnApply(this::fireBlockSpec);

        JPanel resultTop = new JPanel(new BorderLayout());
        resultTop.add(btnBack, BorderLayout.WEST);
        lblResultTitle.setText(" Discovered Blocks for " + cbAlgorithm.getSelectedItem() + " (editable) ");
        resultTop.add(lblResultTitle, BorderLayout.CENTER);
        pageResult.add(resultTop, BorderLayout.NORTH);
        pageResult.add(editorPanel, BorderLayout.CENTER);

        // Cards
        cardPanel.add(pageSetup, "setup");
        cardPanel.add(pageResult, "result");

        add(cardPanel, BorderLayout.CENTER);

        // constructor (after building controls, before listeners)
        cbAlgorithm.setSelectedItem("TSC");
        refreshTestChoices();

        // listeners
        cbAlgorithm.addActionListener(e -> refreshTestChoices());

        // Listeners
        btnSearch.addActionListener(this::onSearch);
        btnBack.addActionListener(e -> {
            status.setText("Ready.");
            cards.show(cardPanel, "setup");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Simulate a 3-latent chain L1 -> L2 -> L3, 5 indicators each, n=5000
            DataSet ds = simulateMIM_Chain(5000, 5, 0.8, 0.8, 0.7, 0.6);
            JFrame f = new JFrame("Block Clustering Wizard (Demo)");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(new BlockClusteringWizard(ds));
            f.setSize(980, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /**
     * Simulate linear-Gaussian MIM: L1 -> L2 -> L3; each Li has m indicators Xi_j = loading*Li + noise.
     *
     * @param n         samples
     * @param m         indicators per latent
     * @param beta12    path L1->L2
     * @param beta23    path L2->L3
     * @param loading   common indicator loading
     * @param latentVar variance of latent disturbances (std normal scaled to this var ~ 1.0)
     */
    private static DataSet simulateMIM_Chain(int n, int m, double beta12, double beta23, double loading, double latentVar) {
        int L = 3; // L1, L2, L3
        int p = L * m;

        // Create variables X1..X{3m}
        List<Node> vars = new ArrayList<>(p);
        for (int i = 1; i <= p; i++) vars.add(new ContinuousVariable("X" + i));

        // Allocate data box
        VerticalDoubleDataBox box = new VerticalDoubleDataBox(n, p);

        Random rnd = new Random(42);
        double sdLatent = Math.sqrt(latentVar);
        double sdNoiseIndicator = Math.sqrt(1.0 - loading * loading); // ensure Var(X) ≈ 1

        for (int t = 0; t < n; t++) {
            // Latent disturbances (std normal scaled)
            double e1 = sdLatent * rnd.nextGaussian();
            double e2 = sdLatent * rnd.nextGaussian();
            double e3 = sdLatent * rnd.nextGaussian();

            // Latents (structural)
            double L1 = e1;
            double L2 = beta12 * L1 + e2;
            double L3 = beta23 * L2 + e3;

            // Indicators: m each
            int col = 0;
            for (int j = 0; j < m; j++, col++) {
                double x = loading * L1 + sdNoiseIndicator * rnd.nextGaussian();
                box.set(t, col, x);
            }
            for (int j = 0; j < m; j++, col++) {
                double x = loading * L2 + sdNoiseIndicator * rnd.nextGaussian();
                box.set(t, col, x);
            }
            for (int j = 0; j < m; j++, col++) {
                double x = loading * L3 + sdNoiseIndicator * rnd.nextGaussian();
                box.set(t, col, x);
            }
        }

        return new BoxDataSet(box, vars);
    }

    public void addBlockSpecListener(BlockSpecListener l) {
        if (l != null) specListeners.add(l);
    }

    public void removeBlockSpecListener(BlockSpecListener l) {
        specListeners.remove(l);
    }

    private void fireBlockSpec(BlockSpec spec) {
        specListeners.forEach(l -> l.onBlockSpec(spec));
    }

    private JFormattedTextField createAlphaField() {
        java.text.DecimalFormat fmt = new java.text.DecimalFormat("0.############");
        fmt.setGroupingUsed(false);
        javax.swing.text.NumberFormatter nf = new javax.swing.text.NumberFormatter(fmt);
        nf.setValueClass(Double.class);
        nf.setMinimum(0.0);
        nf.setMaximum(1.0);
        nf.setCommitsOnValidEdit(true);      // updates value as you type
        JFormattedTextField f = new JFormattedTextField(nf);
        f.setColumns(6);
        return f;
    }

    // Add to class:
    private void refreshTestChoices() {
        String alg = (String) cbAlgorithm.getSelectedItem();
        assert alg != null;
        cbTetradTest.removeAllItems();

        boolean enable;
        List<String> tests;
        switch (alg) {
            case "FOFC" -> {
                tests = TESTS_FOFC;
                enable = true;
            }
            case "BPC" -> {
                tests = TESTS_BPC;
                enable = true;
            }
            case "FTFC" -> {
                tests = TESTS_FTFC;
                enable = true;
            }
            case "TSC" -> {
                tests = TESTS_NONE;
                enable = false;
            }
            default -> {
                tests = TESTS_NONE;
                enable = false;
            }
        }

        cbTetradTest.setEnabled(enable);
        for (String t : tests) cbTetradTest.addItem(t);
        if (enable && cbTetradTest.getItemCount() > 0) {
            // sensible default
            cbTetradTest.setSelectedItem(TEST_CCA);
        }
    }

    // ---------- Demo ----------

    // ---------- Run search ----------
    private void onSearch(ActionEvent evt) {
        new WatchedProcess() {
            @Override
            public void watch() {
                String alg = (String) cbAlgorithm.getSelectedItem();

                setAlpha((Double) tfAlpha.getValue());
                String testName = cbTetradTest.isEnabled() ? (String) cbTetradTest.getSelectedItem() : null;

                // Safety guard: enforce compatibility again (in case of weird UI states)
                if ("FTFC".equals(alg) && TEST_WIS.equals(testName)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "FTFC supports only CCA or Bollen-Ting.", "Test Selection", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                btnSearch.setEnabled(false);
                status.setText("Searching with " + alg + (testName != null ? (" + " + testName) : "") + " …");

                BlockDiscoverer discoverer = buildDiscoverer(alg, testName, alpha);
                BlockSpec spec = discoverer.discover();

                try {
                    editorPanel.setDataSet(spec.dataSet());
                    editorPanel.setText(BlockSpecTextCodec.format(spec));

                    // update title…
                    String algRan = (String) cbAlgorithm.getSelectedItem();
                    ((JLabel) ((BorderLayout) ((JPanel) pageResult.getComponent(0)).getLayout())
                            .getLayoutComponent(BorderLayout.CENTER))
                            .setText(" Discovered Blocks for " + algRan + " (editable) ");

                    // NEW: notify listeners (ClusterEditor) that a fresh spec is ready
                    fireBlockSpec(spec);

                    cards.show(cardPanel, "result");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(BlockClusteringWizard.this,
                            "Search failed: " + cause.getMessage(),
                            "Search", JOptionPane.ERROR_MESSAGE);
                    status.setText("Search failed.");
                }

                btnSearch.setEnabled(true);
            }
        };

//        new MyWatchedProcess().watch();
    }

    private void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    private BlockDiscoverer buildDiscoverer(String alg, String testName, double alpha) {
        NtadTest test = null;
        if (testName != null) {
            switch (testName) {
                case TEST_BT -> test = new BollenTing(dataSet.getDoubleData().getSimpleMatrix(), false);
                case TEST_WIS -> test = new Wishart(dataSet.getDoubleData().getSimpleMatrix(), false);
                // case TEST_ARK -> test = new Ark(...); // still commented out
                case TEST_CCA -> test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                default -> test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
            }
        }

        return switch (alg) {
            case "TSC Test" -> {
                yield BlockDiscoverers.tscTest(dataSet, alpha);
            }
            case "TSC Score" -> {
                yield BlockDiscoverers.tscScore(dataSet, alpha, 0.8, 1e-8, 2);
            }
            case "FOFC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false); // sensible default
                }
                yield BlockDiscoverers.fofc(dataSet, test, alpha);
            }
            case "BPC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.bpc(dataSet, test, alpha);
            }
            case "FTFC" -> {
                if (test == null || TEST_WIS.equals(testName)) {
                    // enforce: FTFC cannot use Wishart
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.ftfc(dataSet, test, alpha);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
        };
    }
}