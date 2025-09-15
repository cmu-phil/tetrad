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

package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.*;
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
    //    private static final List<String> TESTS_FOFC = List.of(TEST_CCA, TEST_BT, TEST_WIS);
//    private static final List<String> TESTS_BPC = List.of(TEST_CCA, TEST_BT, TEST_WIS);
//    private static final List<String> TESTS_FTFC = List.of(TEST_CCA, TEST_BT);      // no Wishart
    private static final List<String> TESTS_NONE = List.of();                       // TSC
    // private static final String TEST_ARK = "ARK"; // commented out per instruction
    // ---- UI ----
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JPanel pageResult = new JPanel(new BorderLayout());
    // ... UI fields ...
    private final JComboBox<String> cbAlgorithm = new JComboBox<>(new String[]{"TSC", "FOFC", "FTFC", "GFFC", "BPC"});

    // Put near your fields
    private final JComboBox<String> cbTetradTest = new JComboBox<>();
    private final JButton btnSearch = new JButton("Search");
    private final JLabel status = new JLabel("Ready.");
    private final BlockSpecEditorPanel editorPanel;
    // ---- State ----
    private final DataSet dataSet;
    private final java.util.List<BlockSpecListener> specListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Parameters parameters;
    private final JPanel parameterBox = new JPanel(new BorderLayout());
    private final Set<String> paramList = new HashSet<>();
    private final Map<String, List<String>> trueClusters;

    /**
     * Constructs a new BlockClusteringWizard with the given dataset, algorithm, test, block text, and parameters. This
     * constructor sets up the user interface, initializes components, and establishes behavior for clustering
     * operations.
     *
     * @param dataSet      The dataset to be used for block clustering analysis. Cannot be null.
     * @param alg          The name of the selected algorithm for clustering.
     * @param test         The name of the selected dependency test.
     * @param blockText    The text representing the initial block structure or specification.
     * @param trueClusters A map from latent names to true children of latents, to be used to help give estimated
     *                     clusters good names.
     * @param parameters   The parameters required for the selected clustering algorithm and test.
     */
    public BlockClusteringWizard(DataSet dataSet, String alg, String test, String blockText,
                                 Map<String, List<String>> trueClusters, Parameters parameters) {
        super(new BorderLayout(8, 8));
        this.dataSet = Objects.requireNonNull(dataSet);
        this.parameters = parameters;
        this.trueClusters = trueClusters;

        // Page 1 (setup)
        Box top = Box.createHorizontalBox();

        top.add(new JLabel("Algorithm:"));
        cbAlgorithm.setSelectedItem(alg);
        top.add(cbAlgorithm);

        refreshTestChoices(test);

        parameterBox.setBorder(new TitledBorder("Parameters"));

//        top.add(new JLabel("Ntad test:"));
//        top.add(cbTetradTest);
        top.add(Box.createHorizontalGlue());

        Box southSetup = Box.createHorizontalBox();

        southSetup.add(status);
        southSetup.add(Box.createHorizontalGlue());
        southSetup.add(btnSearch);

        //  new JPanel(new BorderLayout(8, 8));
        Box pageSetup = Box.createVerticalBox();
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
        JButton btnBack = new JButton("â Back");
        resultTop.add(btnBack, BorderLayout.WEST);

        JLabel lblResultTitle = new JLabel();
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
        refreshTestChoices(test);

        if (!editorPanel.getBlockText().isEmpty()) {
            cards.show(cardPanel, "result");
        }

        // listeners
        cbAlgorithm.addActionListener(e -> {
            refreshTestChoices(test);
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

    /**
     * The main entry point of the application. It initializes a simulation of a multi-indicator model (MIM) with a
     * latent variable chain, creates a block clustering wizard user interface, and displays it in a JFrame.
     *
     * @param args Command-line arguments passed to the program. These arguments are not used within the application.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Simulate a 3-latent chain L1 -> L2 -> L3, 5 indicators each, n=5000
            DataSet ds = simulateMIM_Chain(5000, 5, 0.8, 0.8, 0.7, 0.6);
            JFrame f = new JFrame("Block Clustering Wizard (Demo)");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(new BlockClusteringWizard(ds, "TSC", "CCA", "", new HashMap<>(), new Parameters()));
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
        double sdNoiseIndicator = Math.sqrt(1.0 - loading * loading); // ensure Var(X) â 1

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

    /**
     * Adds a BlockSpecListener to the list of listeners. The listener will be notified when a BlockSpec event is
     * fired.
     *
     * @param l The BlockSpecListener to add. If the provided listener is null, it will not be added.
     */
    public void addBlockSpecListener(BlockSpecListener l) {
        if (l != null) specListeners.add(l);
    }

    /**
     * Removes a BlockSpecListener from the list of registered listeners. The listener will no longer be notified of any
     * BlockSpec events.
     *
     * @param l The BlockSpecListener to remove. If the provided listener is null, no action will be taken.
     */
    public void removeBlockSpecListener(BlockSpecListener l) {
        specListeners.remove(l);
    }

    private void fireBlockSpec(BlockSpec spec) {
        specListeners.forEach(l -> l.onBlockSpec(spec));
    }

    // Add to class:
    private void refreshTestChoices(String test) {
        String alg = (String) cbAlgorithm.getSelectedItem();

        assert alg != null;
        cbTetradTest.removeAllItems();

        boolean enable;
        List<String> tests;
        switch (alg) {
            case "FOFC" -> {
                tests = TESTS_NONE;// TESTS_FOFC;
                enable = true;
            }
            case "BPC" -> {
                tests = TESTS_NONE;
                enable = true;
            }
            case "FTFC" -> {
                tests = TESTS_NONE;// TESTS_FTFC;
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
            cbTetradTest.setSelectedItem(test);

            if (cbTetradTest.getSelectedItem() == null) {
                cbTetradTest.setSelectedIndex(0);
            }
        }

        showParameters();
    }

    // ---------- Demo ----------

    // ---------- Run search ----------
    private void onSearch(ActionEvent evt) {
        new WatchedProcess() {
            @Override
            public void watch() {
                String alg = (String) cbAlgorithm.getSelectedItem();
                String testName = cbTetradTest.isEnabled() ? (String) cbTetradTest.getSelectedItem() : null;

                // Safety guard: enforce compatibility again (in case of weird UI states)
                if ("FTFC".equals(alg) && TEST_WIS.equals(testName)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "FTFC supports only CCA or Bollen-Ting.", "Test Selection", JOptionPane.WARNING_MESSAGE);
                    return;
                }

//                btnSearch.setEnabled(false);
                status.setText("Searching with " + alg + (testName != null ? (" + " + testName) : "") + " â¦");

                int ess = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
                ess = ess == -1 ? dataSet.getNumRows() : ess;

                BlockDiscoverer discoverer = buildDiscoverer(alg, testName, ess);
                BlockSpec spec = discoverer.discover();
                spec = BlocksUtil.giveGoodLatentNames(spec, trueClusters, BlocksUtil.NamingMode.LEARNED_SINGLE);

                try {
                    editorPanel.setDataSet(spec.dataSet());
                    editorPanel.setText(BlockSpecTextCodec.format(spec));

                    // update titleâ¦
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

//                btnSearch.setEnabled(true);
            }
        };
    }

    private BlockDiscoverer buildDiscoverer(String alg, String testName, int ess) {
        setParamList();

        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        SingleClusterPolicy policy = SingleClusterPolicy.values()[_singletonPolicy - 1];

        return switch (alg) {
            case "TSC" -> {
                yield BlockDiscoverers.tsc(dataSet, parameters.getDouble(Params.ALPHA),
                        parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE),
                        parameters.getDouble(Params.REGULARIZATION_LAMBDA),
                        parameters.getInt(Params.MAX_RANK),
                        policy,
                        parameters.getInt(Params.TSC_MIN_REDUNDANCY),
                        parameters.getBoolean(Params.VERBOSE)
                );
            }
            case "FOFC" -> {
                yield BlockDiscoverers.fofc(dataSet, parameters.getDouble(Params.ALPHA),
                        parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE), policy,
                        parameters.getBoolean(Params.VERBOSE)
                );
            }
            case "BPC" -> {
                yield BlockDiscoverers.bpc(dataSet, parameters.getDouble(Params.ALPHA),
                        ess, policy, parameters.getBoolean(Params.VERBOSE));
            }
            case "FTFC" -> {
                yield BlockDiscoverers.ftfc(dataSet, parameters.getDouble(Params.ALPHA), ess, policy,
                        parameters.getBoolean(Params.VERBOSE));
            }
            case "GFFC" -> {
                yield BlockDiscoverers.gffc(dataSet, parameters.getDouble(Params.ALPHA), ess,
                        parameters.getInt(Params.MAX_RANK), policy,
                        parameters.getBoolean(Params.VERBOSE));
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
        };
    }

    private void setParamList() {
        this.paramList.clear();
        String alg = (String) cbAlgorithm.getSelectedItem();
        assert alg != null;

        switch (alg) {
            case "TSC" -> {
                paramList.add(Params.ALPHA);
                paramList.add(Params.REGULARIZATION_LAMBDA);
                paramList.add(Params.MAX_RANK);
                paramList.add(Params.TSC_MIN_REDUNDANCY);
            }
            case "GFFC" -> {
                paramList.add(Params.ALPHA);
                paramList.add(Params.MAX_RANK);
            }
            default -> paramList.add(Params.ALPHA);
        }

        paramList.add(Params.EFFECTIVE_SAMPLE_SIZE);
        paramList.add(Params.TSC_SINGLETON_POLICY);
        paramList.add(Params.VERBOSE);
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

    /**
     * Retrieves the current parameters associated with the BlockClusteringWizard.
     *
     * @return the Parameters object containing the configuration and settings for the block clustering operations
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Retrieves the currently selected algorithm from the combo box.
     *
     * @return the name of the currently selected algorithm as a String. The returned value is guaranteed to be
     * non-null.
     */
    public String getAlg() {
        String alg = (String) cbAlgorithm.getSelectedItem();
        assert alg != null;
        return alg;
    }

    /**
     * Retrieves the currently selected tetrad test from the combo box.
     *
     * @return the name of the currently selected tetrad test as a String. The returned value is guaranteed to be
     * non-null.
     */
    public String getTest() {
        return (String) cbTetradTest.getSelectedItem();
    }

    /**
     * Retrieves the currently selected block test from the editor panel.
     *
     * @return the text entered in the editor panel as a String. The returned value is guaranteed to be non-null.
     */
    public String getBlockTest() {
        return editorPanel.getText();
    }
}
