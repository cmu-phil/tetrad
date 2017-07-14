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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.bootstrap.BootstrapFges;
import edu.cmu.tetrad.algcomparison.algorithm.bootstrap.BootstrapGfci;
import edu.cmu.tetrad.algcomparison.algorithm.bootstrap.BootstrapRfci;
import edu.cmu.tetrad.algcomparison.algorithm.cluster.Bpc;
import edu.cmu.tetrad.algcomparison.algorithm.cluster.Fofc;
import edu.cmu.tetrad.algcomparison.algorithm.cluster.Ftfc;
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Lingam;
import edu.cmu.tetrad.algcomparison.algorithm.mixed.Mgm;
import edu.cmu.tetrad.algcomparison.algorithm.multi.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.algorithm.other.Glasso;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.*;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.score.*;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.JsonUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.action.HpcJobActivityAction;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.pitt.dbmi.ccd.commons.file.MessageDigestHash;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
import edu.pitt.dbmi.ccd.rest.client.service.algo.AbstractAlgorithmRequest;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParameter;
import edu.pitt.dbmi.tetrad.db.entity.DataValidation;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcParameter;
import edu.pitt.dbmi.tetrad.db.entity.JvmOption;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang3.StringUtils;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements FinalizingEditor {

    // Note: When adding an algorithm, make sure you do all of the following:
    // 1. Add a new type to private enum AlgName.
    // 2. Add a desription for it to final List<AlgorithmDescription> descriptions.
    // 3. In private Algorithm getAlgorithm, add a new case to the switch statement returning
    // an instance of the algorithm.
    private static final long serialVersionUID = -5719467682865706447L;

    private final HashMap<AlgName, AlgorithmDescription> mappedDescriptions;
    private final GeneralAlgorithmRunner runner;
    private final JComboBox<String> algTypesDropdown = new JComboBox<>();
    private final JComboBox<AlgName> algNamesDropdown = new JComboBox<>();
    private final JComboBox<TestType> testDropdown = new JComboBox<>();
    private final JComboBox<ScoreType> scoreDropdown = new JComboBox<>();
    private final GraphSelectionEditor graphEditor;
    private final Parameters parameters;
    private final HelpSet helpSet;

    private final TetradDesktop desktop;
    private HpcJobInfo hpcJobInfo;

    private String jsonResult;

    private final List<AlgorithmDescription> descriptions;

    private JList suggestedAlgosList;

    private String selectedAlgoName;

    private int selectedAlgoIndex;

    private JTextArea algoDescriptionTextArea;

    private ParameterPanel parametersPanel;

    private Box parametersBox;

    private JDialog loadingIndicatorDialog;

    private Box graphContainer;

    private JButton step1BackBtn;

    private JButton step2Btn;

    private JButton step2BackBtn;

    private JButton step3Btn;

    private JButton doneBtn;

    //=========================CONSTRUCTORS============================//
    /**
     * Opens up an editor to let the user view the given PcRunner.
     *
     * @param runner
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.runner = runner;

        this.loadingIndicatorDialog = new JDialog();

        // Initialize variables
        algoDescriptionTextArea = new JTextArea();

        String helpHS = "/resources/javahelp/TetradHelp.hs";

        try {
            URL url = this.getClass().getResource(helpHS);
            this.helpSet = new HelpSet(null, url);
        } catch (Exception ee) {
            System.out.println("HelpSet " + ee.getMessage());
            System.out.println("HelpSet " + helpHS + " not found");
            throw new IllegalArgumentException();
        }

        algTypesDropdown.setFont(new Font("Dialog", Font.PLAIN, 13));
        algNamesDropdown.setFont(new Font("Dialog", Font.PLAIN, 13));
        testDropdown.setFont(new Font("Dialog", Font.PLAIN, 13));
        scoreDropdown.setFont(new Font("Dialog", Font.PLAIN, 13));

        List<TestType> discreteTests = new ArrayList<>();
        discreteTests.add(TestType.ChiSquare);
        discreteTests.add(TestType.GSquare);
        discreteTests.add(TestType.Discrete_BIC_Test);
        discreteTests.add(TestType.Conditional_Gaussian_LRT);

        List<TestType> continuousTests = new ArrayList<>();
        continuousTests.add(TestType.Fisher_Z);
        continuousTests.add(TestType.Correlation_T);
        continuousTests.add(TestType.SEM_BIC);
        continuousTests.add(TestType.Conditional_Correlation);
        continuousTests.add(TestType.Conditional_Gaussian_LRT);

        List<TestType> mixedTests = new ArrayList<>();
        mixedTests.add(TestType.Conditional_Gaussian_LRT);

        List<TestType> dsepTests = new ArrayList<>();
        dsepTests.add(TestType.D_SEPARATION);

        List<ScoreType> discreteScores = new ArrayList<>();
        discreteScores.add(ScoreType.BDeu);
        discreteScores.add(ScoreType.Discrete_BIC);
        discreteScores.add(ScoreType.Conditional_Gaussian_BIC);

        List<ScoreType> continuousScores = new ArrayList<>();
        continuousScores.add(ScoreType.SEM_BIC);
        continuousScores.add(ScoreType.Conditional_Gaussian_BIC);

        List<ScoreType> mixedScores = new ArrayList<>();
        mixedScores.add(ScoreType.Conditional_Gaussian_BIC);

        List<ScoreType> dsepScores = new ArrayList<>();
        dsepScores.add(ScoreType.D_SEPARATION);

        descriptions = new ArrayList<>();

        descriptions.add(new AlgorithmDescription(AlgName.PC, AlgType.forbid_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPC, AlgType.forbid_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PCStable, AlgType.forbid_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPCStable, AlgType.forbid_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PcStableMax, AlgType.forbid_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.FGES, AlgType.forbid_latent_common_causes, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_Discrete, AlgType.forbid_latent_common_causes, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_Continuous, AlgType.forbid_latent_common_causes, OracleType.None));
//        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_CCD, AlgType.forbid_latent_common_causes, OracleType.None));
//        descriptions.add(new AlgorithmDescription(AlgName.CCD, AlgType.forbid_latent_common_causes, OracleType.Test));
//        descriptions.add(new AlgorithmDescription(AlgName.CCD_MAX, AlgType.forbid_latent_common_causes, OracleType.Test));
//        descriptions.add(new AlgorithmDescription(AlgName.FANG, AlgType.forbid_latent_common_causes, OracleType.None));
//        descriptions.add(new AlgorithmDescription(AlgName.EFANG   , AlgType.forbid_latent_common_causes, OracleType.None));

        descriptions.add(new AlgorithmDescription(AlgName.FCI, AlgType.allow_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.RFCI, AlgType.allow_latent_common_causes, OracleType.Test));
//        descriptions.add(new AlgorithmDescription(AlgName.CFCI, AlgType.allow_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GFCI, AlgType.allow_latent_common_causes, OracleType.Both));
        descriptions.add(new AlgorithmDescription(AlgName.TsFCI, AlgType.allow_latent_common_causes, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.TsGFCI, AlgType.allow_latent_common_causes, OracleType.Both));
        descriptions.add(new AlgorithmDescription(AlgName.TsImages, AlgType.allow_latent_common_causes, OracleType.Test));

        descriptions.add(new AlgorithmDescription(AlgName.FgesMb, AlgType.search_for_Markov_blankets, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.MBFS, AlgType.search_for_Markov_blankets, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.FAS, AlgType.produce_undirected_graphs, OracleType.Test));

//        descriptions.add(new AlgorithmDescription(AlgName.LiNGAM, AlgType.DAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.MGM, AlgType.produce_undirected_graphs, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.GLASSO, AlgType.produce_undirected_graphs, OracleType.None));

        descriptions.add(new AlgorithmDescription(AlgName.Bpc, AlgType.search_for_structure_over_latents, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.Fofc, AlgType.search_for_structure_over_latents, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.Ftfc, AlgType.search_for_structure_over_latents, OracleType.None));

        descriptions.add(new AlgorithmDescription(AlgName.EB, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R1, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R2, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R3, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R4, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.RSkew, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.RSkewE, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.Skew, AlgType.orient_pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.SkewE, AlgType.orient_pairwise, OracleType.None));
//        descriptions.add(new AlgorithmDescription(AlgName.Tahn, AlgType.orient_pairwise, OracleType.None));

        descriptions.add(new AlgorithmDescription(AlgName.BootstrapFGES,
                AlgType.bootstrapping, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.BootstrapGFCI,
                AlgType.bootstrapping, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.BootstrapRFCI,
                AlgType.bootstrapping, OracleType.Score));

        mappedDescriptions = new HashMap<>();

        for (AlgorithmDescription description : descriptions) {
            mappedDescriptions.put(description.getAlgName(), description);
        }

        this.parameters = runner.getParameters();

        graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(runner.getGraphs(), new Parameters()));

//        if (runner.getDataModelList() == null) {
//            throw new NullPointerException("No data has been provided.");
//        }
        List<TestType> tests;

        DataModelList dataModelList = runner.getDataModelList();

        if ((dataModelList.isEmpty() && runner.getSourceGraph() != null)) {
            tests = dsepTests;
        } else if (!(dataModelList.isEmpty())) {
            DataModel dataSet = dataModelList.get(0);

            if (dataSet.isContinuous()) {
                tests = continuousTests;
            } else if (dataSet.isDiscrete()) {
                tests = discreteTests;
            } else if (dataSet.isMixed()) {
                tests = mixedTests;
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException("You need either some data sets or a graph as input.");
        }

        for (TestType item : tests) {
            testDropdown.addItem(item);
        }

        List<ScoreType> scores;

        if ((dataModelList.isEmpty() && runner.getSourceGraph() != null)) {
            tests = dsepTests;
        } else if (!dataModelList.isEmpty()) {
            DataModel dataSet = dataModelList.get(0);

            if (dataSet.isContinuous()) {
                tests = continuousTests;
            } else if (dataSet.isDiscrete()) {
                tests = discreteTests;
            } else if (dataSet.isMixed()) {
                tests = mixedTests;
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException("You need either some data sets or a graph as input.");
        }

        if (dataModelList.isEmpty() && runner.getGraphs() != null) {
            scores = dsepScores;
        } else if (!(dataModelList.isEmpty())) {
            DataModel dataSet = dataModelList.get(0);

            if (dataSet.isContinuous()) {
                scores = continuousScores;
            } else if (dataSet.isDiscrete()) {
                scores = discreteScores;
            } else if (dataSet.isMixed()) {
                scores = mixedScores;
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException("You need either some data sets or a graph as input.");
        }

        for (ScoreType item : scores) {
            scoreDropdown.addItem(item);
        }

        for (AlgType item : AlgType.values()) {
            algTypesDropdown.addItem(item.toString().replace("_", " "));
        }

        for (AlgorithmDescription description : descriptions) {
            if (description.getAlgType() == getAlgType() || getAlgType() == AlgType.ALL) {
                algNamesDropdown.addItem(description.getAlgName());
            }
        }

        algTypesDropdown.setSelectedItem(getAlgType().toString().replace("_", " "));
        algNamesDropdown.setSelectedItem(getAlgName());

        // Initialize selectedAlgoName before calling setAlgorithm()
        // Use the first algorithm as default - Zhou
        selectedAlgoIndex = 0;
        selectedAlgoName = descriptions.get(selectedAlgoIndex).getAlgName().toString();

        if (tests.contains(getTestType())) {
            testDropdown.setSelectedItem(getTestType());
        }

        if (scores.contains(getScoreType())) {
            scoreDropdown.setSelectedItem(getScoreType());
        }

        testDropdown.setEnabled(parameters.getBoolean("testEnabled", true));
        scoreDropdown.setEnabled(parameters.getBoolean("scoreEnabled", false));

        algTypesDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                algNamesDropdown.removeAllItems();

                for (AlgorithmDescription description : descriptions) {
                    AlgType selectedItem = AlgType.valueOf(((String) algTypesDropdown.getSelectedItem()).replace(" ", "_"));
                    if (description.getAlgType() == selectedItem
                            || selectedItem == AlgType.ALL) {
                        algNamesDropdown.addItem(description.getAlgName());
                    }
                }
            }
        });

        algNamesDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAlgorithm();

                JComboBox<AlgName> box = (JComboBox<AlgName>) e.getSource();
                Object selectedItem = box.getSelectedItem();

                if (selectedItem != null) {
                    helpSet.setHomeID(selectedItem.toString());
                }
            }
        });

        testDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAlgorithm();
            }
        });

        scoreDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAlgorithm();
            }
        });

        // Show the algo chooser
        showAlgoChooserDialog();

        this.desktop = (TetradDesktop) DesktopController.getInstance();
    }

    private void doSearch(final GeneralAlgorithmRunner runner) {
        HpcAccount hpcAccount = null;

        AlgName name = AlgName.valueOf(selectedAlgoName);
        switch (name) {
            case FGES:
            case GFCI:
                hpcAccount = showRemoteComputingOptions(name);
                break;
            default:
        }

        if (hpcAccount == null) {

            runner.execute();

            // Show graph
            graphEditor.saveLayout();
            runner.execute();
            graphEditor.replace(runner.getGraphs());
            graphEditor.validate();
            firePropertyChange("modelChanged", null, null);

            // Update the graphContainer
            graphContainer.add(graphEditor);
        } else {
            try {
                doRemoteCompute(runner, hpcAccount);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private HpcAccount showRemoteComputingOptions(AlgName name) {
        List<HpcAccount> hpcAccounts = desktop.getHpcAccountManager().getHpcAccounts();

        if (hpcAccounts == null || hpcAccounts.size() == 0) {
            return null;
        }

        String no_answer = "No, thanks";
        String yes_answer = "Please run it on ";

        Object[] options = new String[hpcAccounts.size() + 1];
        options[0] = no_answer;
        for (int i = 0; i < hpcAccounts.size(); i++) {
            String connName = hpcAccounts.get(i).getConnectionName();
            options[i + 1] = yes_answer + connName;
        }

        int n = JOptionPane.showOptionDialog(this, "Would you like to execute a " + name + " search in the cloud?",
                "A Silly Question", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (n == 0) {
            return null;
        }
        return hpcAccounts.get(n - 1);
    }

    private void doRemoteCompute(final GeneralAlgorithmRunner runner, final HpcAccount hpcAccount) throws Exception {

        // **********************
        // Show progress panel *
        // **********************
        Frame ancestor = (Frame) JOptionUtils.centeringComp().getTopLevelAncestor();
        final JDialog progressDialog = new JDialog(ancestor, "HPC Job Submission's Progress...", false);

        Dimension progressDim = new Dimension(500, 150);

        JTextArea progressTextArea = new JTextArea();
        progressTextArea.setPreferredSize(progressDim);
        progressTextArea.setEditable(false);

        JScrollPane progressScroller = new JScrollPane(progressTextArea);
        progressScroller.setAlignmentX(LEFT_ALIGNMENT);

        progressDialog.setLayout(new BorderLayout());
        progressDialog.getContentPane().add(progressScroller, BorderLayout.CENTER);
        progressDialog.pack();
        Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
        progressDialog.setLocation((screenDim.width - progressDim.width) / 2,
                (screenDim.height - progressDim.height) / 2);
        progressDialog.setVisible(true);

        int totalProcesses = 4;
        String newline = "\n";
        String tab = "\t";
        int progressTextLength = 0;

        DataModel dataModel = runner.getDataModel();

        // 1. Generate temp file
        Path file = null;
        Path prior = null;
        try {
            // ****************************
            // Data Preparation Progress *
            // ****************************
            String dataMessage = String.format("1/%1$d Data Preparation", totalProcesses);
            progressTextArea.append(dataMessage);
            progressTextArea.append(tab);

            progressTextLength = progressTextArea.getText().length();

            progressTextArea.append("Preparing...");
            progressTextArea.updateUI();

            file = Files.createTempFile("Tetrad-data-", ".txt");
            // System.out.println(file.toAbsolutePath().toString());
            List<String> tempLine = new ArrayList<>();

            // Header
            List<Node> variables = dataModel.getVariables();
            if ((variables == null || variables.isEmpty()) && runner.getSourceGraph() != null) {
                variables = runner.getSourceGraph().getNodes();
            }

            String vars = StringUtils.join(variables.toArray(), tab);
            tempLine.add(vars);

            // Data
            DataSet dataSet = (DataSet) dataModel;
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                String line = null;
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    String cell = null;
                    if (dataSet.isContinuous()) {
                        cell = String.valueOf(dataSet.getDouble(i, j));
                    } else {
                        cell = String.valueOf(dataSet.getInt(i, j));
                    }
                    if (line == null) {
                        line = cell;
                    } else {
                        line = line + "\t" + cell;
                    }
                }
                tempLine.add(line);
            }

            // for (String line : tempLine) {
            // System.out.println(line);
            // }
            Files.write(file, tempLine);

            // Get file's MD5 hash and use it as its identifier
            String datasetMd5 = MessageDigestHash.computeMD5Hash(file);

            progressTextArea.replaceRange("Done", progressTextLength, progressTextArea.getText().length());
            progressTextArea.append(newline);
            progressTextArea.updateUI();

            // ***************************************
            // Prior Knowledge Preparation Progress *
            // ***************************************
            String priorMessage = String.format("2/%1$d Prior Knowledge Preparation", totalProcesses);
            progressTextArea.append(priorMessage);
            progressTextArea.append(tab);

            progressTextLength = progressTextArea.getText().length();

            progressTextArea.append("Preparing...");
            progressTextArea.updateUI();

            // 2. Generate temp prior knowledge file
            Knowledge2 knowledge = (Knowledge2) dataModel.getKnowledge();
            if (knowledge != null && !knowledge.isEmpty()) {
                prior = Files.createTempFile(file.getFileName().toString(), ".prior");
                knowledge.saveKnowledge(Files.newBufferedWriter(prior));

                progressTextArea.replaceRange("Done", progressTextLength, progressTextArea.getText().length());
                progressTextArea.append(newline);
                progressTextArea.updateUI();
            } else {
                progressTextArea.replaceRange("Skipped", progressTextLength, progressTextArea.getText().length());
                progressTextArea.append(newline);
                progressTextArea.updateUI();
            }
            // Get knowledge file's MD5 hash and use it as its identifier
            String priorKnowledgeMd5 = null;
            if (prior != null) {
                priorKnowledgeMd5 = MessageDigestHash.computeMD5Hash(prior);
            }

            // *******************************************
            // Algorithm Parameter Preparation Progress *
            // *******************************************
            String algorMessage = String.format("3/%1$d Algorithm Preparation", totalProcesses);
            progressTextArea.append(algorMessage);
            progressTextArea.append(tab);

            progressTextLength = progressTextArea.getText().length();

            progressTextArea.append("Preparing...");
            progressTextArea.updateUI();

            // 3.1 Algorithm name
            String algorithmName = AbstractAlgorithmRequest.FGES;
            Algorithm algorithm = runner.getAlgorithm();
            System.out.println("Algorithm: " + algorithm.getDescription());
            AlgName name = (AlgName) algNamesDropdown.getSelectedItem();
            switch (name) {
                case FGES:
                    algorithmName = AbstractAlgorithmRequest.FGES;
                    if (dataModel.isDiscrete()) {
                        algorithmName = AbstractAlgorithmRequest.FGES_DISCRETE;
                    }
                    break;
                case GFCI:
                    algorithmName = AbstractAlgorithmRequest.GFCI;
                    if (dataModel.isDiscrete()) {
                        algorithmName = AbstractAlgorithmRequest.GFCI_DISCRETE;
                    }
                    break;
                default:
                    return;
            }

            // 3.2 Parameters
            AlgorithmParamRequest algorithmParamRequest = new AlgorithmParamRequest();

            // Dataset and Prior paths
            String datasetPath = file.toAbsolutePath().toString();
            System.out.println(datasetPath);
            algorithmParamRequest.setDatasetPath(datasetPath);
            algorithmParamRequest.setDatasetMd5(datasetMd5);
            if (prior != null) {
                String priorKnowledgePath = prior.toAbsolutePath().toString();
                System.out.println(priorKnowledgePath);
                algorithmParamRequest.setPriorKnowledgePath(priorKnowledgePath);
                algorithmParamRequest.setPriorKnowledgeMd5(priorKnowledgeMd5);
            }

            // VariableType
            if (dataModel.isContinuous()) {
                algorithmParamRequest.setVariableType("continuous");
            } else {
                algorithmParamRequest.setVariableType("discrete");
            }

            // FileDelimiter
            String fileDelimiter = "tab"; // Pre-determined
            algorithmParamRequest.setFileDelimiter(fileDelimiter);

            // Default Data Validation Parameters
            DataValidation dataValidation = new DataValidation();
            dataValidation.setUniqueVarName(true);
            if (dataModel.isContinuous()) {
                dataValidation.setNonZeroVariance(true);
            } else {
                dataValidation.setCategoryLimit(true);
            }
            algorithmParamRequest.setDataValidation(dataValidation);

            List<AlgorithmParameter> AlgorithmParameters = new ArrayList<>();

            Parameters parameters = runner.getParameters();
            List<String> parameterNames = runner.getAlgorithm().getParameters();
            for (String parameter : parameterNames) {
                String value = parameters.get(parameter).toString();
                System.out.println("parameter: " + parameter + "\tvalue: " + value);
                if (value != null) {
                    AlgorithmParameter algorParam = new AlgorithmParameter();
                    algorParam.setParameter(parameter);
                    algorParam.setValue(value);
                    AlgorithmParameters.add(algorParam);
                }
            }

            algorithmParamRequest.setAlgorithmParameters(AlgorithmParameters);

            String maxHeapSize = null;
            do {
                maxHeapSize = JOptionPane.showInputDialog(progressDialog, "Enter Your Request Java Max Heap Size (GB):",
                        "5");
            } while (maxHeapSize != null && !StringUtils.isNumeric(maxHeapSize));

            if (maxHeapSize != null) {
                JvmOption jvmOption = new JvmOption();
                jvmOption.setParameter("maxHeapSize");
                jvmOption.setValue(maxHeapSize);
                List<JvmOption> jvmOptions = new ArrayList<>();
                jvmOptions.add(jvmOption);
                algorithmParamRequest.setJvmOptions(jvmOptions);
            }

            // Hpc parameters
            final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
            JsonWebToken jsonWebToken = HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount);
            if (jsonWebToken.getWallTime() != null) {
                // User allowed to customize the job's wall time
                String[] wallTime = jsonWebToken.getWallTime();
                Object userwallTime = JOptionPane.showInputDialog(progressDialog, "Wall Time:",
                        "Choose Your Wall Time (in Hour)", JOptionPane.QUESTION_MESSAGE, null, wallTime, wallTime[0]);

                if (wallTime != null && userwallTime != null) {
                    HpcParameter hpcParameter = new HpcParameter();
                    hpcParameter.setKey("walltime");
                    hpcParameter.setValue(userwallTime.toString());
                    System.out.println("walltime: " + userwallTime.toString());
                    algorithmParamRequest.setHpcParameters(Collections.singletonList(hpcParameter));
                }
            }

            progressTextArea.replaceRange("Done", progressTextLength, progressTextArea.getText().length());
            progressTextArea.append(newline);
            progressTextArea.updateUI();

            // ********************************
            // Adding HPC Job Queue Progress *
            // ********************************
            String dbMessage = String.format("4/%1$d HPC Job Queue Submission", totalProcesses);
            progressTextArea.append(dbMessage);
            progressTextArea.append(tab);

            progressTextLength = progressTextArea.getText().length();

            progressTextArea.append("Preparing...");
            progressTextArea.updateUI();

            HpcJobManager hpcJobManager = desktop.getHpcJobManager();

            // 4.1 Save HpcJobInfo
            hpcJobInfo = new HpcJobInfo();
            hpcJobInfo.setAlgorithmName(algorithmName);
            hpcJobInfo.setAlgorithmParamRequest(algorithmParamRequest);
            hpcJobInfo.setStatus(-1);
            hpcJobInfo.setHpcAccount(hpcAccount);
            hpcJobManager.submitNewHpcJobToQueue(hpcJobInfo, this);

            progressTextArea.replaceRange("Done", progressTextLength, progressTextArea.getText().length());
            progressTextArea.append(newline);
            progressTextArea.updateUI();

            this.jsonResult = null;

            JOptionPane.showMessageDialog(ancestor, "The " + hpcJobInfo.getAlgorithmName() + " job on the "
                    + hpcJobInfo.getHpcAccount().getConnectionName() + " node is in the queue successfully!");

        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            progressDialog.setVisible(false);
            progressDialog.dispose();
        }

        (new HpcJobActivityAction("")).actionPerformed(null);

    }

    public void setAlgorithmResult(String jsonResult) {
        this.jsonResult = jsonResult;

        final Graph graph = JsonUtils.parseJSONObjectToTetradGraph(jsonResult);
        final List<Graph> graphs = new ArrayList<>();
        graphs.add(graph);
        int size = runner.getGraphs().size();
        for (int index = 0; index < size; index++) {
            runner.getGraphs().remove(index);
        }
        runner.getGraphs().add(graph);
        graphEditor.replace(graphs);
        graphEditor.validate();
        System.out.println("Remote graph result assigned to runner!");
        firePropertyChange("modelChanged", null, null);

    }

    public void setAlgorithmErrorResult(String errorResult) {
        JOptionPane.showMessageDialog(desktop, jsonResult);
        throw new IllegalArgumentException(errorResult);
    }

    public Algorithm getAlgorithmFromInterface() {
        AlgName name = AlgName.valueOf(selectedAlgoName);

        if (name == null) {
            throw new NullPointerException();
        }

        IndependenceWrapper independenceWrapper = getIndependenceWrapper();
        ScoreWrapper scoreWrapper = getScoreWrapper();

        Algorithm algorithm = getAlgorithm(name, independenceWrapper, scoreWrapper);

        return algorithm;
    }

    private Algorithm getAlgorithm(AlgName name, IndependenceWrapper independenceWrapper, ScoreWrapper scoreWrapper) {
        Algorithm algorithm;

        switch (name) {
            case FGES:
                algorithm = new Fges(scoreWrapper);

//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new Fges(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                algorithm = new Fges(scoreWrapper);
//                }
                break;
//            case FgesMeasurement:
//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new FgesMeasurement(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                    algorithm = new FgesMeasurement(scoreWrapper);
//                }
//                break;
            case PC:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new Pc(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Pc(independenceWrapper);
                }
                break;
            case CPC:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new Cpc(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Cpc(independenceWrapper);
                }
                break;
            case CPCStable:
                algorithm = new CpcStable(independenceWrapper);
                break;
            case PCStable:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new PcStable(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new PcStable(independenceWrapper);
                }
                break;
            case GFCI:
                algorithm = new Gfci(independenceWrapper, scoreWrapper);
                break;
            case FCI:
                algorithm = new Fci(independenceWrapper);
//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new Fci(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                    algorithm = new Fci(independenceWrapper);
//                }
                break;
            case RFCI:
                algorithm = new Rfci(independenceWrapper);
                break;
            case CFCI:
                algorithm = new Cfci(independenceWrapper);
                break;
            case TsFCI:
                algorithm = new TsFci(independenceWrapper);
//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new TsFci(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                    algorithm = new TsFci(independenceWrapper);
//                }
                break;
            case TsGFCI:
                algorithm = new TsGfci(independenceWrapper, scoreWrapper);
                break;
            case TsImages:
                algorithm = new TsImages(scoreWrapper);
                break;
            case CCD:
                algorithm = new Ccd(independenceWrapper);
                break;
            case CCD_MAX:
                algorithm = new CcdMax(independenceWrapper);
                break;
            case FANG:
                algorithm = new FangConcatenated();
                break;
            case EFANG:
                algorithm = new EFangConcatenated();
                break;
            case FAS:
                algorithm = new FAS(independenceWrapper);
                break;
            case FgesMb:
                algorithm = new FgesMb(scoreWrapper);
//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new FgesMb(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                    algorithm = new FgesMb(scoreWrapper);
//                }
                break;
            case MBFS:
                algorithm = new MBFS(independenceWrapper);
                break;
            case PcStableMax:
                algorithm = new PcStableMax(independenceWrapper, false);
                break;
            case JCPC:
                algorithm = new Jcpc(independenceWrapper, scoreWrapper);
                break;
            case LiNGAM:
                algorithm = new Lingam();
                break;
            case MGM:
                algorithm = new Mgm();
                break;
            case IMaGES_Discrete:
                algorithm = new ImagesBDeu();
                break;
            case IMaGES_Continuous:
                algorithm = new ImagesSemBic();
                break;
            case IMaGES_CCD:
                algorithm = new ImagesCcd();
                break;
            case GLASSO:
                algorithm = new Glasso();
                break;
            case Bpc:
                algorithm = new Bpc();
                break;
            case Fofc:
                algorithm = new Fofc();
                break;
            case Ftfc:
                algorithm = new Ftfc();
                break;

            // LOFS algorithms.
            case EB:
                algorithm = new EB(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case R1:
                algorithm = new R1(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case R2:
                algorithm = new R2(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case R3:
                algorithm = new R3(new SingleGraphAlg(runner.getSourceGraph()));
                ((R3) algorithm).setKnowledge(runner.getKnowledge());
                break;
            case R4:
                algorithm = new R4(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case RSkew:
                algorithm = new RSkew(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case RSkewE:
                algorithm = new RSkewE(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case Skew:
                algorithm = new Skew(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case SkewE:
                algorithm = new SkewE(new SingleGraphAlg(runner.getSourceGraph()));
                break;
            case Tahn:
                algorithm = new Tanh(new SingleGraphAlg(runner.getSourceGraph()));
                break;

            // Bootstrapping
            case BootstrapFGES:
                algorithm = new BootstrapFges(scoreWrapper);
                break;
            case BootstrapGFCI:
                algorithm = new BootstrapGfci(independenceWrapper, scoreWrapper);
                break;
            case BootstrapRFCI:
                algorithm = new BootstrapRfci(independenceWrapper);
                break;

            default:
                throw new IllegalArgumentException("Please configure that algorithm: " + name);

        }
        return algorithm;
    }

    private ScoreWrapper getScoreWrapper() {
        ScoreType score = (ScoreType) scoreDropdown.getSelectedItem();
        ScoreWrapper scoreWrapper;

        switch (score) {
            case BDeu:
                scoreWrapper = new BdeuScore();
                break;
            case Conditional_Gaussian_BIC:
                scoreWrapper = new ConditionalGaussianBicScore();
                break;
            case Discrete_BIC:
                scoreWrapper = new DiscreteBicScore();
                break;
            case SEM_BIC:
                scoreWrapper = new SemBicScore();
                break;
            case D_SEPARATION:
                scoreWrapper = new DseparationScore(new SingleGraph(runner.getSourceGraph()));
                break;
            default:
                throw new IllegalArgumentException("Please configure that score: " + score);
        }
        return scoreWrapper;
    }

    private IndependenceWrapper getIndependenceWrapper() {
        TestType test = (TestType) testDropdown.getSelectedItem();

        IndependenceWrapper independenceWrapper;

        switch (test) {
            case ChiSquare:
                independenceWrapper = new ChiSquare();
                break;
            case Conditional_Correlation:
                independenceWrapper = new ConditionalCorrelation();
                break;
            case Conditional_Gaussian_LRT:
                independenceWrapper = new ConditionalGaussianLRT();
                break;
            case Fisher_Z:
                independenceWrapper = new FisherZ();
                break;
            case Correlation_T:
                independenceWrapper = new CorrelationT();
                break;
            case GSquare:
                independenceWrapper = new GSquare();
                break;
            case SEM_BIC:
                independenceWrapper = new SemBicTest();
                break;
            case D_SEPARATION:
                independenceWrapper = new DSeparationTest(new SingleGraph(runner.getSourceGraph()));
                break;
            default:
                throw new IllegalArgumentException("Please configure that test: " + test);
        }

        List<IndependenceTest> tests = new ArrayList<>();

        for (DataModel dataModel : runner.getDataModelList()) {
            IndependenceTest _test = independenceWrapper.getTest(dataModel, parameters);
            tests.add(_test);
        }

        runner.setIndependenceTests(tests);
        return independenceWrapper;
    }

    private void setAlgorithm() {
        // Comment out this to test new UI - Zhou
        //AlgName name = (AlgName) algNamesDropdown.getSelectedItem();

        // Get the equivalent enum type of selectedAlgoName string
        AlgName name = AlgName.valueOf(selectedAlgoName);

        AlgorithmDescription description = mappedDescriptions.get(name);

        if (name == null) {
            return;
        }

        TestType test = (TestType) testDropdown.getSelectedItem();
        ScoreType score = (ScoreType) scoreDropdown.getSelectedItem();

        // Set the algo on each selection change
        Algorithm algorithm = getAlgorithmFromInterface();

        System.out.println("algo parameters ..............");
        System.out.println(algorithm.getParameters());

        runner.setAlgorithm(algorithm);

        OracleType oracle = description.getOracleType();

        if (oracle == OracleType.None) {
            testDropdown.setEnabled(false);
            scoreDropdown.setEnabled(false);
        } else if (oracle == OracleType.Score) {
            testDropdown.setEnabled(false);
            scoreDropdown.setEnabled(true);
        } else if (oracle == OracleType.Test) {
            testDropdown.setEnabled(true);
            scoreDropdown.setEnabled(false);
        } else if (oracle == OracleType.Both) {
            testDropdown.setEnabled(true);
            scoreDropdown.setEnabled(true);
        }

        parameters.set("testEnabled", testDropdown.isEnabled());
        parameters.set("scoreEnabled", scoreDropdown.isEnabled());

        setAlgName(name);
        setTestType(test);
        setScoreType(score);
        setAlgType(selectedAlgoName.replace(" ", "_"));

        // Also need to update the corresponding parameters
        parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
        // Remove all and add new
        parametersBox.removeAll();
        parametersBox.add(parametersPanel);
    }

    private void showAlgoChooserDialog() {
        // Overall container
        // contains data preview panel, loading params panel, and load button
        Box container = Box.createVerticalBox();
        // Must set the size of container
        container.setPreferredSize(new Dimension(940, 640));

        helpSet.setHomeID("tetrad_overview");

        JButton explain1 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain2 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain3 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain4 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));

        explain1.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain2.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain3.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain4.setBorder(new EmptyBorder(0, 0, 0, 0));

        explain1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                helpSet.setHomeID("types_of_algorithms");
                helpSet.setHomeID("under_construction");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
            }
        });

        explain2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) algNamesDropdown;
                String name = box.getSelectedItem().toString();
//                helpSet.setHomeID(name.toLowerCase());
                helpSet.setHomeID("under_construction");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
            }
        });

        explain3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) testDropdown;
//                String name = box.getSelectedItem().toString();
//                helpSet.setHomeID(name.toLowerCase());
                helpSet.setHomeID("under_construction");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
            }
        });

        explain4.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) scoreDropdown;
//                String name = box.getSelectedItem().toString();
//                helpSet.setHomeID(name.toLowerCase());
                helpSet.setHomeID("under_construction");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
            }
        });

        // Are the relationships between your variables linear?
        Box varLinearRelationshipsBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box varLinearRelationshipsLabelBox = Box.createHorizontalBox();
        varLinearRelationshipsLabelBox.add(new JLabel("Linear variables: "));
        varLinearRelationshipsLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option 1
        Box varLinearRelationshipsOption1Box = Box.createHorizontalBox();
        varLinearRelationshipsOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton varLinearRelationshipsYes = new JRadioButton("Yes");

        // Add padding and option
        varLinearRelationshipsOption1Box.add(Box.createRigidArea(new Dimension(20, 20)));
        varLinearRelationshipsOption1Box.add(varLinearRelationshipsYes);

        // Option 2
        Box varLinearRelationshipsOption2Box = Box.createHorizontalBox();
        varLinearRelationshipsOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton varLinearRelationshipsNo = new JRadioButton("No");

        // Add padding and option
        varLinearRelationshipsOption2Box.add(Box.createRigidArea(new Dimension(20, 20)));
        varLinearRelationshipsOption2Box.add(varLinearRelationshipsNo);

        // Option 3
        Box varLinearRelationshipsOption3Box = Box.createHorizontalBox();
        varLinearRelationshipsOption3Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton varLinearRelationshipsUnknown = new JRadioButton("Let's find out");

        // Add padding and option
        varLinearRelationshipsOption3Box.add(Box.createRigidArea(new Dimension(20, 20)));
        varLinearRelationshipsOption3Box.add(varLinearRelationshipsUnknown);

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup varLinearRelationshipsBtnGrp = new ButtonGroup();
        varLinearRelationshipsBtnGrp.add(varLinearRelationshipsYes);
        varLinearRelationshipsBtnGrp.add(varLinearRelationshipsNo);
        varLinearRelationshipsBtnGrp.add(varLinearRelationshipsUnknown);

        // Add to containing box
        varLinearRelationshipsBox.add(varLinearRelationshipsLabelBox);
        varLinearRelationshipsBox.add(varLinearRelationshipsOption1Box);
        varLinearRelationshipsBox.add(varLinearRelationshipsOption2Box);
        varLinearRelationshipsBox.add(varLinearRelationshipsOption3Box);
        varLinearRelationshipsBox.add(Box.createHorizontalGlue());

        // Are your variables Gaussian?
        Box gaussianVariablesBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box gaussianVariablesLabelBox = Box.createHorizontalBox();
        gaussianVariablesLabelBox.add(new JLabel("Gaussian variables: "));
        gaussianVariablesLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option 1
        Box gaussianVariablesOption1Box = Box.createHorizontalBox();
        gaussianVariablesOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton gaussianVariablesYes = new JRadioButton("Yes");

        // Add padding and option
        gaussianVariablesOption1Box.add(Box.createRigidArea(new Dimension(20, 20)));
        gaussianVariablesOption1Box.add(gaussianVariablesYes);

        // Option 2
        Box gaussianVariablesOption2Box = Box.createHorizontalBox();
        gaussianVariablesOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton gaussianVariablesNo = new JRadioButton("No");

        // Add padding and option
        gaussianVariablesOption2Box.add(Box.createRigidArea(new Dimension(20, 20)));
        gaussianVariablesOption2Box.add(gaussianVariablesNo);

        // Option 3
        Box gaussianVariablesOption3Box = Box.createHorizontalBox();
        gaussianVariablesOption3Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton gaussianVariablesUnknown = new JRadioButton("Let's find out");

        // Add padding and option
        gaussianVariablesOption3Box.add(Box.createRigidArea(new Dimension(20, 20)));
        gaussianVariablesOption3Box.add(gaussianVariablesUnknown);

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup gaussianVariablesBtnGrp = new ButtonGroup();
        gaussianVariablesBtnGrp.add(gaussianVariablesYes);
        gaussianVariablesBtnGrp.add(gaussianVariablesNo);
        gaussianVariablesBtnGrp.add(gaussianVariablesUnknown);

        // Add to containing box
        gaussianVariablesBox.add(gaussianVariablesLabelBox);
        gaussianVariablesBox.add(gaussianVariablesOption1Box);
        gaussianVariablesBox.add(gaussianVariablesOption2Box);
        gaussianVariablesBox.add(gaussianVariablesOption3Box);
        gaussianVariablesBox.add(Box.createHorizontalGlue());

        // Is there a prior knowledge file?
        Box priorKnowledgeBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box priorKnowledgeLabelBox = Box.createHorizontalBox();
        priorKnowledgeLabelBox.add(new JLabel("Prior knowledge file: "));
        priorKnowledgeLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option 1
        Box priorKnowledgeOption1Box = Box.createHorizontalBox();
        priorKnowledgeOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton priorKnowledgeYes = new JRadioButton("Yes");

        // Add padding and option
        priorKnowledgeOption1Box.add(Box.createRigidArea(new Dimension(20, 20)));
        priorKnowledgeOption1Box.add(priorKnowledgeYes);

        // Option 2
        Box priorKnowledgeOption2Box = Box.createHorizontalBox();
        priorKnowledgeOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton priorKnowledgeNo = new JRadioButton("No");

        // Add padding and option
        priorKnowledgeOption2Box.add(Box.createRigidArea(new Dimension(20, 20)));
        priorKnowledgeOption2Box.add(priorKnowledgeNo);

        // Add to containg box
        priorKnowledgeBox.add(priorKnowledgeLabelBox);
        priorKnowledgeBox.add(priorKnowledgeOption1Box);
        priorKnowledgeBox.add(priorKnowledgeOption2Box);

        // Output Goals, use checkboxes to allow multiple goals
        // Can't use buttonGroup on checkboxes since it's multiple-exclusion
        Box outputGoalsBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box outputGoalsLabelBox = Box.createHorizontalBox();
        outputGoalsLabelBox.add(new JLabel("Output Goals: "));
        outputGoalsLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option 1
        Box outputGoalsOption1Box = Box.createHorizontalBox();
        outputGoalsOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JCheckBox outputGoalsPairwiseOrientation = new JCheckBox("Pairwise orientation");

        // Add padding and option
        outputGoalsOption1Box.add(Box.createRigidArea(new Dimension(20, 20)));
        outputGoalsOption1Box.add(outputGoalsPairwiseOrientation);

        // Option 2
        Box outputGoalsOption2Box = Box.createHorizontalBox();
        outputGoalsOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JCheckBox outputGoalsMarkovBlanket = new JCheckBox("Markov blanket");

        // Add padding and option
        outputGoalsOption2Box.add(Box.createRigidArea(new Dimension(20, 20)));
        outputGoalsOption2Box.add(outputGoalsMarkovBlanket);

        // Option 3
        Box outputGoalsOption3Box = Box.createHorizontalBox();
        outputGoalsOption3Box.setAlignmentX(LEFT_ALIGNMENT);

        JCheckBox outputGoalsUndirectedGraph = new JCheckBox("Undirected graph");

        // Add padding and option
        outputGoalsOption3Box.add(Box.createRigidArea(new Dimension(20, 20)));
        outputGoalsOption3Box.add(outputGoalsUndirectedGraph);

        // Option 4
        Box outputGoalsOption4Box = Box.createHorizontalBox();
        outputGoalsOption4Box.setAlignmentX(LEFT_ALIGNMENT);

        JCheckBox outputGoalsCausalGraph = new JCheckBox("Causal graph");

        // Add padding and option
        outputGoalsOption4Box.add(Box.createRigidArea(new Dimension(20, 20)));
        outputGoalsOption4Box.add(outputGoalsCausalGraph);

        // Add to containg box
        outputGoalsBox.add(outputGoalsLabelBox);
        outputGoalsBox.add(outputGoalsOption1Box);
        outputGoalsBox.add(outputGoalsOption2Box);
        outputGoalsBox.add(outputGoalsOption3Box);
        outputGoalsBox.add(outputGoalsOption4Box);
        // We need this glue, otherwise the components are centered instead of left aligned - Zhou
        outputGoalsBox.add(Box.createHorizontalGlue());

        // Include unmeasured confounders?
        Box includeUnmeasuredConfoundersBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box includeUnmeasuredConfoundersLabelBox = Box.createHorizontalBox();
        includeUnmeasuredConfoundersLabelBox.add(new JLabel("Unmeasured confounders: "));
        includeUnmeasuredConfoundersLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option 1
        Box includeUnmeasuredConfoundersOption1Box = Box.createHorizontalBox();
        includeUnmeasuredConfoundersOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton includeUnmeasuredConfoundersYes = new JRadioButton("Yes");

        // Add padding and option
        includeUnmeasuredConfoundersOption1Box.add(Box.createRigidArea(new Dimension(20, 20)));
        includeUnmeasuredConfoundersOption1Box.add(includeUnmeasuredConfoundersYes);

        // Option 2
        Box includeUnmeasuredConfoundersOption2Box = Box.createHorizontalBox();
        includeUnmeasuredConfoundersOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton includeUnmeasuredConfoundersNo = new JRadioButton("No");

        // Add padding and option
        includeUnmeasuredConfoundersOption2Box.add(Box.createRigidArea(new Dimension(20, 20)));
        includeUnmeasuredConfoundersOption2Box.add(includeUnmeasuredConfoundersNo);

        // Option 3
        Box includeUnmeasuredConfoundersOption3Box = Box.createHorizontalBox();
        includeUnmeasuredConfoundersOption3Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton includeUnmeasuredConfoundersUnknown = new JRadioButton("Let's find out");

        // Add padding and option
        includeUnmeasuredConfoundersOption3Box.add(Box.createRigidArea(new Dimension(20, 20)));
        includeUnmeasuredConfoundersOption3Box.add(includeUnmeasuredConfoundersUnknown);

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup includeUnmeasuredConfoundersBtnGrp = new ButtonGroup();
        includeUnmeasuredConfoundersBtnGrp.add(includeUnmeasuredConfoundersYes);
        includeUnmeasuredConfoundersBtnGrp.add(includeUnmeasuredConfoundersNo);
        includeUnmeasuredConfoundersBtnGrp.add(includeUnmeasuredConfoundersUnknown);

        // Add to containing box
        includeUnmeasuredConfoundersBox.add(includeUnmeasuredConfoundersLabelBox);
        includeUnmeasuredConfoundersBox.add(includeUnmeasuredConfoundersOption1Box);
        includeUnmeasuredConfoundersBox.add(includeUnmeasuredConfoundersOption2Box);
        includeUnmeasuredConfoundersBox.add(includeUnmeasuredConfoundersOption3Box);
        includeUnmeasuredConfoundersBox.add(Box.createHorizontalGlue());

        // Clear filter selections
        JButton clearFilterSelectionsBtn = new JButton("Clear filter selections");

        // Joe's current UI - Zhou
        Box d3 = Box.createHorizontalBox();
        JLabel label3 = new JLabel("List Algorithms that ");
        label3.setFont(new Font("Dialog", Font.BOLD, 13));
        d3.add(label3);
        algTypesDropdown.setMaximumSize(algTypesDropdown.getPreferredSize());
        d3.add(algTypesDropdown);
        JLabel label4 = new JLabel(" : ");
        label4.setFont(new Font("Dialog", Font.BOLD, 13));
        d3.add(label4);
        algNamesDropdown.setMaximumSize(algNamesDropdown.getPreferredSize());
        d3.add(algNamesDropdown);
        d3.add(explain2);
        d3.add(new JLabel("    "));
        d3.add(Box.createHorizontalGlue());

        Box testBox = Box.createHorizontalBox();
        JLabel label1 = new JLabel("Test if needed:");
        testBox.add(label1);
        testDropdown.setMaximumSize(testDropdown.getPreferredSize());
        testBox.add(testDropdown);
        testBox.add(explain3);
        testBox.add(Box.createHorizontalGlue());

        Box scoreBox = Box.createHorizontalBox();
        JLabel label2 = new JLabel("Score if needed:");
        scoreBox.add(label2);
        scoreDropdown.setMaximumSize(scoreDropdown.getPreferredSize());
        scoreBox.add(scoreDropdown);
        scoreBox.add(explain4);
        scoreBox.add(Box.createHorizontalGlue());

        // This container, step 1
        // contains 3 columns, leftContainer, middleContainer, and rightContainer
        Box algoChooserContainer = Box.createHorizontalBox();
        algoChooserContainer.setPreferredSize(new Dimension(940, 600));

        // Parameters container, step 2
        Box parametersContainer = Box.createVerticalBox();
        parametersContainer.setPreferredSize(new Dimension(940, 600));

        // Graph container, step 3
        graphContainer = Box.createVerticalBox();
        graphContainer.setPreferredSize(new Dimension(940, 580));

        // Contains data description and result description
        Box leftContainer = Box.createVerticalBox();
        leftContainer.setPreferredSize(new Dimension(260, 580));

        Box middleContainer = Box.createVerticalBox();
        middleContainer.setPreferredSize(new Dimension(260, 580));

        // Contains algo list, algo description, test, score, and parameters
        Box rightContainer = Box.createVerticalBox();
        rightContainer.setPreferredSize(new Dimension(400, 580));

        // Describe your data and result using these filters
        Box algoFiltersBox = Box.createVerticalBox();
        algoFiltersBox.setMinimumSize(new Dimension(250, 570));
        algoFiltersBox.setMaximumSize(new Dimension(250, 570));
        algoFiltersBox.setAlignmentX(LEFT_ALIGNMENT);

        // Use a titled border with 5 px inside padding - Zhou
        String algoFiltersBoxBorderTitle = "Algorithm filters";
        algoFiltersBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(algoFiltersBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Items to put in data description box
        //dataDescriptionBox.add(timeSeriesDataBox);
        algoFiltersBox.add(varLinearRelationshipsBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(gaussianVariablesBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(priorKnowledgeBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(outputGoalsBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(includeUnmeasuredConfoundersBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(clearFilterSelectionsBtn);

        // Add to leftContainer
        leftContainer.add(algoFiltersBox);

        // Components in middleContainer
        // Show a list of filtered algorithms
        Box suggestedAlgosBox = Box.createVerticalBox();
        suggestedAlgosBox.setMinimumSize(new Dimension(250, 570));
        suggestedAlgosBox.setMaximumSize(new Dimension(250, 570));

        // Use a titled border with 5 px inside padding - Zhou
        String suggestedAlgosBoxBorderTitle = "Choose algorithm";
        suggestedAlgosBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(suggestedAlgosBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        DefaultListModel suggestedAlgosListModel = new DefaultListModel();

        // Add algo to list model
        for (AlgorithmDescription algo : descriptions) {
            // Add each algo name to the list model
            suggestedAlgosListModel.addElement(algo.algName);
        }

        suggestedAlgosList = new JList(suggestedAlgosListModel);

        // Only allow single selection
        suggestedAlgosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Use the currently selected index every time getAlgorithmPane() is called - Zhou
        suggestedAlgosList.setSelectedIndex(selectedAlgoIndex);
        // Set default description as the first algorithm
        algoDescriptionTextArea.setText("Description of " + selectedAlgoName);

        suggestedAlgosList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                // More about why use getValueIsAdjusting()
                // http://docs.oracle.com/javase/8/docs/api/javax/swing/ListSelectionModel.html#getValueIsAdjusting--
                if (!e.getValueIsAdjusting()) {
                    // Currently selected item index
                    // we'll need to set this because getAlgorithmPane() gets called
                    // every time setAlgorithm() is called - Zhou
                    selectedAlgoIndex = suggestedAlgosList.getSelectedIndex();

                    // Show the description of this algo in descriptopn text area
                    selectedAlgoName = suggestedAlgosList.getSelectedValue().toString();
                    System.out.println("Selected algo ..." + selectedAlgoName);
                    algoDescriptionTextArea.setText("Description of " + selectedAlgoName);

                    // Finally, set the selected algo and update the test and score dropdown menus
                    setAlgorithm();
                }
            }
        });

        // Set default algo in runner
        Algorithm algorithm = getAlgorithmFromInterface();
        runner.setAlgorithm(algorithm);

        // Put the list in a scrollable area
        JScrollPane suggestedAlgosListScrollPane = new JScrollPane(suggestedAlgosList);
        suggestedAlgosListScrollPane.setMinimumSize(new Dimension(250, 570));
        suggestedAlgosListScrollPane.setMaximumSize(new Dimension(250, 570));

        suggestedAlgosBox.add(suggestedAlgosListScrollPane);

        middleContainer.add(suggestedAlgosBox);

        // Components in rightContainer
        // Algo description
        Box algoDescriptionBox = Box.createVerticalBox();
        algoDescriptionBox.setMinimumSize(new Dimension(390, 250));
        algoDescriptionBox.setMaximumSize(new Dimension(390, 250));

        // Use a titled border with 5 px inside padding - Zhou
        String algoDescriptionBoxBorderTitle = "Algorithm description";
        algoDescriptionBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(algoDescriptionBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Set line arap
        algoDescriptionTextArea.setWrapStyleWord(true);
        algoDescriptionTextArea.setLineWrap(true);

        // Read only
        algoDescriptionTextArea.setEditable(false);

        JScrollPane algoDescriptionScrollPane = new JScrollPane(algoDescriptionTextArea);
        algoDescriptionScrollPane.setMinimumSize(new Dimension(390, 240));
        algoDescriptionScrollPane.setMaximumSize(new Dimension(390, 240));

        algoDescriptionBox.add(algoDescriptionScrollPane);

        // Choose corresponding test and score based on algorithm
        Box testAndScoreBox = Box.createVerticalBox();
        testAndScoreBox.setMinimumSize(new Dimension(390, 120));
        testAndScoreBox.setMaximumSize(new Dimension(390, 120));

        // Use a titled border with 5 px inside padding - Zhou
        String testAndScoreBoxBorderTitle = "Choose Test and Score";
        testAndScoreBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(testAndScoreBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        testAndScoreBox.add(testBox);
        // Add some gap between test and score
        testAndScoreBox.add(Box.createVerticalStrut(10), 1);
        testAndScoreBox.add(scoreBox);

        // Parameters
        parametersBox = Box.createVerticalBox();
        parametersBox.setMinimumSize(new Dimension(920, 560));
        parametersBox.setMaximumSize(new Dimension(920, 600));

        // Use a titled border with 5 px inside padding - Zhou
        String parametersBoxBorderTitle = "Specify algorithm parameters";
        parametersBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(parametersBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Parameters
        // This is only the parameters pane of the default algorithm - Zhou
        parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());

        parametersPanel.setMinimumSize(new Dimension(920, 560));
        parametersPanel.setMaximumSize(new Dimension(920, 560));

        // Add to parameters box
        parametersBox.add(parametersPanel);

        // Add to parametersContainer
        parametersContainer.add(parametersBox);

        // Back to step 1 button
        step1BackBtn = new JButton("< Choose Algorithm");

        // Step 1 button listener
        step1BackBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Hide parameters
                parametersContainer.setVisible(false);

                // Show algo step 1
                algoChooserContainer.setVisible(true);

                // Show step 2 button
                step2Btn.setVisible(true);

                // Hide step 3 button
                step3Btn.setVisible(false);

                // Hide back button
                step1BackBtn.setVisible(false);
            }
        });

        // Hide step 2
        parametersContainer.setVisible(false);

        // Parameters button
        step2Btn = new JButton("Set Parameters >");
        step2BackBtn = new JButton("< Set Parameters");

        // Step 2 button listener
        step2Btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Show parameters
                parametersContainer.setVisible(true);

                // Hide algo step 1
                algoChooserContainer.setVisible(false);

                // SHow back to step 1 button and search button
                step1BackBtn.setVisible(true);
                step3Btn.setVisible(true);

                // Hide step 2 button
                step2Btn.setVisible(false);
            }
        });

        // Step 2 button listener
        step2BackBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Show parameters
                parametersContainer.setVisible(true);

                // Hide algo step 1
                algoChooserContainer.setVisible(false);

                // Hide step 3 graph
                graphContainer.setVisible(false);

                // SHow back to step 1 button and search button
                step1BackBtn.setVisible(true);
                step3Btn.setVisible(true);

                // Hide step 2 button
                step2Btn.setVisible(false);

                // Hide back button
                step2BackBtn.setVisible(false);

                // Hide done button
                doneBtn.setVisible(false);
            }
        });

        // Step 3 button
        step3Btn = new JButton("Run Search & Generate Graph >");

        step3Btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // Load all data files and hide the loading indicator once done
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        doSearch(runner);

                        // Schedule a Runnable which will be executed on the Event Dispatching Thread
                        // SwingUtilities.invokeLater means that this call will return immediately
                        // as the event is placed in Event Dispatcher Queue,
                        // and run() method will run asynchronously
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                // Hide the loading indicator
                                hideLoadingIndicator();

                                // Hide algo chooser
                                algoChooserContainer.setVisible(false);

                                // Hide parameters
                                parametersContainer.setVisible(false);

                                // Show graphContainer
                                graphContainer.setVisible(true);

                                // Show back to step 2 button
                                step2BackBtn.setVisible(true);

                                // Show done button
                                doneBtn.setVisible(true);

                                // Hide step 1 back button
                                step1BackBtn.setVisible(false);

                                // Hide step 3 button
                                step3Btn.setVisible(false);
                            }
                        });
                    }
                }).start();

                // Create the loading indicator dialog and show
                showLoadingIndicator("Runing...");
            }
        });

        // Done button
        doneBtn = new JButton("Done");

        // Close the dialog
        doneBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Window w = SwingUtilities.getWindowAncestor(doneBtn);
                if (w != null) {
                    w.setVisible(false);
                }

                // Will also need to close the old search window
            }
        });

        // Add to rightContainer
        rightContainer.add(Box.createVerticalStrut(10));
        rightContainer.add(algoDescriptionBox);
        rightContainer.add(Box.createVerticalStrut(10));
        rightContainer.add(testAndScoreBox);
        rightContainer.add(Box.createVerticalStrut(10));

        // Buttons container
        Box buttonsContainer = Box.createVerticalBox();

        // Add some padding between preview/summary and buttons container
        buttonsContainer.add(Box.createVerticalStrut(10));

        // Buttons box
        Box buttonsBox = Box.createHorizontalBox();
        buttonsBox.add(step1BackBtn);
        // Don't use Box.createHorizontalStrut(20)
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step2Btn);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step2BackBtn);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step3Btn);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(doneBtn);

        // Default to only show step 2 forward button
        step1BackBtn.setVisible(false);
        step2BackBtn.setVisible(false);
        step3Btn.setVisible(false);
        doneBtn.setVisible(false);

        // Add to buttons container
        buttonsContainer.add(buttonsBox);

        //rightContainer.add(searchBtn);
        //algoFiltersContainer.add(algoBox);
        // Add to algoChooserContainer as the first column
        algoChooserContainer.add(leftContainer);

        // Add some gap
        algoChooserContainer.add(Box.createHorizontalStrut(10), 1);

        // Add middleContainer
        algoChooserContainer.add(middleContainer);

        // Add some gap
        algoChooserContainer.add(Box.createHorizontalStrut(10), 1);

        // Add to algoChooserContainer as the third column
        algoChooserContainer.add(rightContainer);

        // Add to big panel
        container.add(algoChooserContainer);

        container.add(parametersContainer);

        container.add(graphContainer);

        container.add(buttonsContainer);

        JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), container,
                "Algorithm Chooser", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{}, null);
    }

    /**
     * Create the loading indicator dialog and show
     */
    private void showLoadingIndicator(String message) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        // An indeterminate progress bar continuously displays animation
        progressBar.setIndeterminate(true);

        Box dataLoadingIndicatorBox = Box.createVerticalBox();
        dataLoadingIndicatorBox.setPreferredSize(new Dimension(200, 60));

        JLabel label = new JLabel(message);
        // JLabel label = new JLabel(message, SwingConstants.CENTER); doesn't
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        Box progressBarBox = Box.createHorizontalBox();
        progressBarBox.add(Box.createRigidArea(new Dimension(10, 1)));
        progressBarBox.add(progressBar);
        progressBarBox.add(Box.createRigidArea(new Dimension(10, 1)));

        // Put the label on top of progress bar
        dataLoadingIndicatorBox.add(Box.createVerticalStrut(10));
        dataLoadingIndicatorBox.add(label);
        dataLoadingIndicatorBox.add(Box.createVerticalStrut(10));
        dataLoadingIndicatorBox.add(progressBarBox);

        Frame ancestor = (Frame) JOptionUtils.centeringComp().getTopLevelAncestor();
        // Set modal true to block user input to other top-level windows when shown
        loadingIndicatorDialog = new JDialog(ancestor, true);
        // Remove the whole dialog title bar
        loadingIndicatorDialog.setUndecorated(true);
        loadingIndicatorDialog.getContentPane().add(dataLoadingIndicatorBox);
        loadingIndicatorDialog.pack();
        loadingIndicatorDialog.setLocationRelativeTo(JOptionUtils.centeringComp());

        loadingIndicatorDialog.setVisible(true);
    }

    /**
     * Hide the loading indicator
     */
    private void hideLoadingIndicator() {
        loadingIndicatorDialog.setVisible(false);
        // Also release all of the native screen resources used by this dialog
        loadingIndicatorDialog.dispose();
    }

    private Parameters getParameters() {
        return parameters;
    }

    private AlgType getAlgType() {
        return AlgType.valueOf(parameters.getString("algType", "ALL").replace(" ", "_"));
    }

    private void setAlgType(String algType) {
        parameters.set("algType", algType.replace(" ", "_"));
    }

    /**
     * Returns the object value of the given parameter, using "PC" as default.
     *
     * @return AlgName
     */
    private AlgName getAlgName() {
        return AlgName.valueOf(parameters.getString("algName", "PC"));
    }

    private void setAlgName(AlgName algName) {
        parameters.set("algName", algName.toString());
    }

    private TestType getTestType() {
        return TestType.valueOf(parameters.getString("testType", "ChiSquare"));
    }

    private void setTestType(TestType testType) {
        parameters.set("testType", testType.toString());
    }

    private ScoreType getScoreType() {
        return ScoreType.valueOf(parameters.getString("scoreType", "BDeu"));
    }

    private void setScoreType(ScoreType scoreType) {
        parameters.set("scoreType", scoreType.toString());
    }

    @Override
    public boolean finalizeEditor() {
        List<Graph> graphs = runner.getGraphs();

        if (hpcJobInfo == null && (graphs == null || graphs.isEmpty())) {
            int option = JOptionPane.showConfirmDialog(this, "You have not performed a search. Close anyway?", "Close?",
                    JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private class AlgorithmDescription {

        private AlgName algName;
        private AlgType algType;
        private OracleType oracleType;

        public AlgorithmDescription(AlgName name, AlgType algType, OracleType oracleType) {
            this.algName = name;
            this.algType = algType;
            this.oracleType = oracleType;
        }

        public AlgName getAlgName() {
            return algName;
        }

        public AlgType getAlgType() {
            return algType;
        }

        public OracleType getOracleType() {
            return oracleType;
        }
    }

    private enum AlgName {
        PC, PCStable, CPC, CPCStable, FGES, /*PcLocal,*/ PcStableMax, FAS,
        FgesMb, MBFS, Wfges, JCPC, /*FgesMeasurement,*/
        FCI, RFCI, CFCI, GFCI, TsFCI, TsGFCI, TsImages, CCD, CCD_MAX,
        LiNGAM, MGM,
        IMaGES_Discrete, IMaGES_Continuous, IMaGES_CCD,
        Bpc, Fofc, Ftfc,
        GLASSO,
        EB, R1, R2, R3, R4, RSkew, RSkewE, Skew, SkewE, FANG, EFANG, Tahn,
        BootstrapFGES, BootstrapGFCI, BootstrapRFCI
    }

    private enum OracleType {
        None, Test, Score, Both
    }

    private enum AlgType {
        ALL, forbid_latent_common_causes, allow_latent_common_causes, /*DAG, */
        search_for_Markov_blankets, produce_undirected_graphs, orient_pairwise,
        search_for_structure_over_latents, bootstrapping
    }

    private enum TestType {
        ChiSquare, Conditional_Correlation, Conditional_Gaussian_LRT, Fisher_Z, GSquare,
        SEM_BIC, D_SEPARATION, Discrete_BIC_Test, Correlation_T
    }

    public enum ScoreType {
        BDeu, Conditional_Gaussian_BIC, Discrete_BIC, SEM_BIC, D_SEPARATION
    }

}
