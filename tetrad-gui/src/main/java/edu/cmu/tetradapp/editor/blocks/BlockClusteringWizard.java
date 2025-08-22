package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.*;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.search.ntad_test.Wishart;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.editor.simulation.ParameterTab;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ParameterComponents;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

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
    private final Box pageSetup = Box.createVerticalBox();//  new JPanel(new BorderLayout(8, 8));
    private final JPanel pageResult = new JPanel(new BorderLayout());
    // ... UI fields ...
    private final JComboBox<String> cbAlgorithm = new JComboBox<>(new String[]{"FOFC", "BPC", "FTFC", "TSC Test", "TSC Score"});

    // Put near your fields
    private final JComboBox<String> cbTetradTest = new JComboBox<>();
    private final JButton btnSearch = new JButton("Search");
    private final JLabel status = new JLabel("Ready.");
    private final JButton btnBack = new JButton("◀ Back");
    private final JLabel lblResultTitle = new JLabel();
    private final BlockSpecEditorPanel editorPanel;
    // ---- State ----
    private final DataSet dataSet;
    private final java.util.List<BlockSpecListener> specListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Parameters parameters;
    private BlockSpec blockSpec = null;
    private JPanel parameterBox = new JPanel(new BorderLayout());
    private Set<String> paramList = new HashSet<>();

    public BlockClusteringWizard(DataSet dataSet, String alg, String test, String blockText, Parameters parameters) {
        super(new BorderLayout(8, 8));
        this.dataSet = Objects.requireNonNull(dataSet);
        this.parameters = parameters;

        // Page 1 (setup)
        Box top = Box.createHorizontalBox();

        top.add(new JLabel("Algorithm:"));
        cbAlgorithm.setSelectedItem(alg);
        top.add(cbAlgorithm);

        refreshTestChoices();

        parameterBox.setBorder(new TitledBorder("Parameters"));

        top.add(new JLabel("Ntad test:"));
        cbTetradTest.setSelectedItem(test);
        top.add(cbTetradTest);
        top.add(Box.createHorizontalGlue());

        Box southSetup = Box.createHorizontalBox();

        southSetup.add(status);
        southSetup.add(Box.createHorizontalGlue());
        southSetup.add(btnSearch);

        pageSetup.add(top);
        pageSetup.add(parameterBox);
        pageSetup.add(Box.createVerticalGlue());
        pageSetup.add(southSetup);

        setParamList();
        showParameters();

        // Page 2 (results)
        editorPanel = new BlockSpecEditorPanel(dataSet, blockText);
        // in BlockClusteringWizard constructor, after creating editorPanel:
        // forward Apply to the same listener bus
        editorPanel.setOnApply(this::fireBlockSpec);

        JPanel resultTop = new JPanel(new BorderLayout());
        resultTop.add(btnBack, BorderLayout.WEST);

        if (cbAlgorithm.getSelectedItem() == null) {
            System.out.println();
        }

        lblResultTitle.setText(" Discovered Blocks for " + cbAlgorithm.getSelectedItem() + " (editable) ");
        resultTop.add(lblResultTitle, BorderLayout.CENTER);
        pageResult.add(resultTop, BorderLayout.NORTH);
        pageResult.add(editorPanel, BorderLayout.CENTER);

        // Cards
        cardPanel.add(pageSetup, "setup");
        cardPanel.add(pageResult, "result");

        add(cardPanel, BorderLayout.CENTER);

        // constructor (after building controls, before listeners)
        cbAlgorithm.setSelectedItem(alg);
        refreshTestChoices();

        if (!editorPanel.getBlockText().isEmpty()) {
            cards.show(cardPanel, "result");
        }

        // listeners
        cbAlgorithm.addActionListener(e -> {
            refreshTestChoices();
            setParamList();
            showParameters();
        });

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
            f.setContentPane(new BlockClusteringWizard(ds, "FOFC", "CCA", "", new Parameters()));
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

    // Add to class:
    private void refreshTestChoices() {
        String alg = (String) cbAlgorithm.getSelectedItem();

        if (alg == null) {
            System.out.println();
        }

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
            default -> {
                tests = TESTS_NONE;
                enable = false;
            }
        }

        cbTetradTest.setEnabled(enable);
        for (String t : tests) cbTetradTest.addItem(t);

        if (!tests.isEmpty()) {
            cbTetradTest.setSelectedIndex(0);
        }

//        if (enable && cbTetradTest.getItemCount() > 0) {
//            cbTetradTest.setSelectedIndex(0);
//
////            // sensible default
////            cbTetradTest.setSelectedItem(TEST_CCA);
//        }

        showParameters();
    }

    // ---------- Demo ----------

    // ---------- Run search ----------
    private void onSearch(ActionEvent evt) {
        new WatchedProcess() {
            @Override
            public void watch() {
                String alg = (String) cbAlgorithm.getSelectedItem();

                if (alg == null) {
                    System.out.println();
                }

                String testName = cbTetradTest.isEnabled() ? (String) cbTetradTest.getSelectedItem() : null;

                // Safety guard: enforce compatibility again (in case of weird UI states)
                if ("FTFC".equals(alg) && TEST_WIS.equals(testName)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "FTFC supports only CCA or Bollen-Ting.", "Test Selection", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                btnSearch.setEnabled(false);
                status.setText("Searching with " + alg + (testName != null ? (" + " + testName) : "") + " …");

                BlockDiscoverer discoverer = buildDiscoverer(alg, testName);
                BlockSpec spec = discoverer.discover();

                int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
                SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

                if (policy == SingleClusterPolicy.NOISE_VAR) {
                    spec = BlocksUtil.renameLastVarAsNoise(spec);
                }

                try {
                    editorPanel.setDataSet(spec.dataSet());
                    editorPanel.setText(BlockSpecTextCodec.format(spec));

                    // update title…
                    String algRan = (String) cbAlgorithm.getSelectedItem();

                    if (algRan == null) {
                        System.out.println();
                    }

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

    private BlockDiscoverer buildDiscoverer(String alg, String testName) {
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

        setParamList();

        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

        return switch (alg) {
            case "TSC Test" -> {
                yield BlockDiscoverers.tscTest(dataSet, parameters.getDouble(Params.ALPHA), policy,
                        parameters.getInt(Params.EXPECTED_SAMPLE_SIZE));
            }
            case "TSC Score" -> {
                yield BlockDiscoverers.tscScore(dataSet, parameters.getDouble(Params.ALPHA),
                        parameters.getDouble(Params.EBIC_GAMMA), parameters.getDouble(Params.REGULARIZATION_LAMBDA),
                        parameters.getDouble(Params.PENALTY_DISCOUNT),
                        parameters.getInt(Params.EXPECTED_SAMPLE_SIZE), policy);
            }
            case "FOFC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false); // sensible default
                }
                yield BlockDiscoverers.fofc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            case "BPC" -> {
                if (test == null) {
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.bpc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            case "FTFC" -> {
                if (test == null || TEST_WIS.equals(testName)) {
                    // enforce: FTFC cannot use Wishart
                    test = new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
                }
                yield BlockDiscoverers.ftfc(dataSet, test, parameters.getDouble(Params.ALPHA), policy);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
        };
    }

    private void setParamList() {
        this.paramList.clear();
        String alg = (String) cbAlgorithm.getSelectedItem();

        if (alg == null) {
            System.out.println();
        }

        assert alg != null;

        switch (alg) {
            case "TSC Score" -> {
                paramList.add(Params.ALPHA);
                paramList.add(Params.EBIC_GAMMA);
                paramList.add(Params.REGULARIZATION_LAMBDA);
                paramList.add(Params.PENALTY_DISCOUNT);
                paramList.add(Params.EXPECTED_SAMPLE_SIZE);
            }
            case "TSC Test" -> {
                paramList.add(Params.ALPHA);
                paramList.add(Params.EXPECTED_SAMPLE_SIZE);
            }
            default -> paramList.add(Params.ALPHA);
        }

        paramList.add(Params.TSC_SINGLETON_POLICY);
    }

    private void showParameters() {
        this.parameterBox.removeAll();

        if (paramList.isEmpty()) {
            JLabel noParamLbl = ParameterTab.NO_PARAM_LBL;
            noParamLbl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            this.parameterBox.add(noParamLbl, BorderLayout.NORTH);
        } else {
            Box parameters = Box.createVerticalBox();
            Box[] paramBoxes = ParameterComponents.toArray(
                    ParameterComponents.createParameterComponents(paramList, this.getParameters()));
            int lastIndex = paramBoxes.length - 1;
            for (int i = 0; i < lastIndex; i++) {
                parameters.add(paramBoxes[i]);
                parameters.add(Box.createVerticalStrut(10));
            }
            parameters.add(paramBoxes[lastIndex]);

            this.parameterBox.add(new PaddingPanel(parameters), BorderLayout.CENTER);
        }
        this.parameterBox.validate();
        this.parameterBox.repaint();
    }

    public Parameters getParameters() {
        return parameters;
    }

    public String getAlg() {
        String alg = (String) cbAlgorithm.getSelectedItem();

        if (alg == null) {
            System.out.println();
        }

        assert alg != null;
        return alg;
    }

    public String getTest() {
        return (String) cbTetradTest.getSelectedItem();
    }

    public String getBlockTest() {
        return editorPanel.getText();
    }
}