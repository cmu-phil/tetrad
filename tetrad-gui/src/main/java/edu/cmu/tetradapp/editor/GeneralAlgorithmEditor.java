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

import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.JsonUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.action.HpcJobActivityAction;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.cmu.tetradapp.editor.search.AlgorithmCard;
import edu.cmu.tetradapp.editor.search.GraphCard;
import edu.cmu.tetradapp.editor.search.ParameterCard;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.ScoreModel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.pitt.dbmi.ccd.commons.file.MessageDigestHash;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParameter;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcParameter;
import edu.pitt.dbmi.tetrad.db.entity.JvmOptions;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements PropertyChangeListener, FinalizingEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralAlgorithmEditor.class);

    private static final long serialVersionUID = -5719467682865706447L;

    private HpcJobInfo hpcJobInfo;

    private final String ALGORITHM_CARD = "algorithm card";
    private final String PARAMETER_CARD = "parameter card";
    private final String GRAPH_CARD = "graph card";

    private String jsonResult;

    private final GeneralAlgorithmRunner algorithmRunner;
    private final TetradDesktop desktop;
    private final AlgorithmCard algorithmCard;
    private final ParameterCard parameterCard;
    private final GraphCard graphCard;

    public GeneralAlgorithmEditor(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.algorithmCard = new AlgorithmCard(this, algorithmRunner);
        this.parameterCard = new ParameterCard(this, algorithmRunner);
        this.graphCard = new GraphCard(this, algorithmRunner);

        initComponents();

        // repopulate all the previous selections if reopen the search box
        if (algorithmRunner.getGraphs() != null && algorithmRunner.getGraphs().size() > 0) {
            this.algorithmCard.refresh();
            this.parameterCard.refresh();
            this.graphCard.refresh();
            changeCard(GRAPH_CARD);
        }
    }

    private void initComponents() {
        setLayout(new CardLayout());
        add(algorithmCard, ALGORITHM_CARD);
        add(parameterCard, PARAMETER_CARD);
        add(graphCard, GRAPH_CARD);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {
            case "algoFwd":
                parameterCard.refresh();
                changeCard(PARAMETER_CARD);
                break;
            case "paramBack":
                changeCard(ALGORITHM_CARD);
                break;
            case "paramFwd":
                parameterCard.disableButtons();
                doSearch();
                break;
            case "graphBack":
                parameterCard.refresh();
                changeCard(PARAMETER_CARD);
                break;
        }
    }

    private void changeCard(String card) {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.show(this, card);
    }

    public void setAlgorithmResult(String jsonResult) {
        this.jsonResult = jsonResult;
        System.out.println("json result: " + jsonResult);

        final Graph graph = JsonUtils.parseJSONObjectToTetradGraph(jsonResult);
        algorithmRunner.getGraphs().clear();
        algorithmRunner.getGraphs().add(graph);

        LOGGER.info("Remote graph result assigned to algorithmRunner!");
        firePropertyChange("modelChanged", null, null);

        graphCard.refresh();
        changeCard(GRAPH_CARD);
    }

    public void setAlgorithmErrorResult(String errorResult) {
        JOptionPane.showMessageDialog(desktop, jsonResult);

        throw new IllegalArgumentException(errorResult);
    }

    private void doRemoteCompute(final GeneralAlgorithmRunner algorithmRunner, final HpcAccount hpcAccount) throws Exception {
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

        DataModel dataModel = algorithmRunner.getDataModel();

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
            //  LOGGER.info(file.toAbsolutePath().toString());
            List<String> tempLine = new ArrayList<>();

            // Header
            List<Node> variables = dataModel.getVariables();
            if ((variables == null || variables.isEmpty()) && algorithmRunner.getSourceGraph() != null) {
                variables = algorithmRunner.getSourceGraph().getNodes();
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
            //  LOGGER.info(line);
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
                DataWriter.saveKnowledge(knowledge, Files.newBufferedWriter(prior));

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

            // 3.1 Algorithm Id, Independent Test Id, Score Id
            AlgorithmModel algoModel = algorithmCard.getSelectedAlgorithm();
            String algoId = algoModel.getAlgorithm().getAnnotation().command();
            // Test
            String testId = null;
            IndependenceTestModel indTestModel = algorithmCard.getSelectedIndependenceTest();
            if (indTestModel != null) {
                testId = indTestModel.getIndependenceTest().getAnnotation().command();
            }
            // Score
            String scoreId = null;
            ScoreModel scoreModel = algorithmCard.getSelectedScore();
            if (scoreModel != null) {
                scoreId = scoreModel.getScore().getAnnotation().command();
            }

            // 3.2 Parameters
            AlgorithmParamRequest algorithmParamRequest = new AlgorithmParamRequest();

            // Test and score
            algorithmParamRequest.setTestId(testId);
            algorithmParamRequest.setScoreId(scoreId);

            // Dataset and Prior paths
            String datasetPath = file.toAbsolutePath().toString();
            LOGGER.info(datasetPath);
            algorithmParamRequest.setDatasetPath(datasetPath);
            algorithmParamRequest.setDatasetMd5(datasetMd5);
            if (prior != null) {
                String priorKnowledgePath = prior.toAbsolutePath().toString();
                LOGGER.info(priorKnowledgePath);
                algorithmParamRequest.setPriorKnowledgePath(priorKnowledgePath);
                algorithmParamRequest.setPriorKnowledgeMd5(priorKnowledgeMd5);
            }

            // VariableType
            if (dataModel.isContinuous()) {
                algorithmParamRequest.setVariableType("continuous");
            } else if (dataModel.isDiscrete()) {
                algorithmParamRequest.setVariableType("discrete");
            } else {
                algorithmParamRequest.setVariableType("mixed");
            }

            // FileDelimiter
            String fileDelimiter = "tab"; // Pre-determined
            algorithmParamRequest.setFileDelimiter(fileDelimiter);

            Set<AlgorithmParameter> AlgorithmParameters = new HashSet<>();

            Parameters parameters = algorithmRunner.getParameters();
            List<String> parameterNames = algorithmRunner.getAlgorithm().getParameters();
            for (String parameter : parameterNames) {
                String value = parameters.get(parameter).toString();
                LOGGER.info("parameter: " + parameter + "\tvalue: " + value);
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
                JvmOptions jvmOptions = new JvmOptions();
                jvmOptions.setMaxHeapSize(Integer.parseInt(maxHeapSize));
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
                    LOGGER.info("walltime: " + userwallTime.toString());
                    Set<HpcParameter> hpcParameters = new HashSet<>();
                    hpcParameters.add(hpcParameter);
                    algorithmParamRequest.setHpcParameters(hpcParameters);
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
            hpcJobInfo.setAlgoId(algoId);
            hpcJobInfo.setAlgorithmParamRequest(algorithmParamRequest);
            hpcJobInfo.setStatus(-1);
            hpcJobInfo.setHpcAccount(hpcAccount);
//            hpcJobManager.submitNewHpcJobToQueue(hpcJobInfo, this);

            progressTextArea.replaceRange("Done", progressTextLength, progressTextArea.getText().length());
            progressTextArea.append(newline);
            progressTextArea.updateUI();

            this.jsonResult = null;

            JOptionPane.showMessageDialog(ancestor, "The " + hpcJobInfo.getAlgoId() + " job on the "
                    + hpcJobInfo.getHpcAccount().getConnectionName() + " node is in the queue successfully!");

        } catch (IOException exception) {
            LOGGER.error("", exception);
        } finally {
            progressDialog.setVisible(false);
            progressDialog.dispose();
        }

        (new HpcJobActivityAction("")).actionPerformed(null);

    }

    private HpcAccount showRemoteComputingOptions(String algoId) {
        List<HpcAccount> hpcAccounts = desktop.getHpcAccountManager().getHpcAccounts();

        if (hpcAccounts == null || hpcAccounts.isEmpty()) {
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

        int n = JOptionPane.showOptionDialog(this, "Would you like to execute a " + algoId + " search in the cloud?",
                "A Silly Question", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (n == 0) {
            return null;
        }
        return hpcAccounts.get(n - 1);
    }

    private void doSearch() {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                AlgorithmModel algoModel = algorithmCard.getSelectedAlgorithm();
                if (algoModel != null) {
                    HpcAccount hpcAccount = null;

                    if (algoModel.getAlgorithm().getAnnotation().algoType() != AlgType.orient_pairwise
                            && algorithmRunner.getDataModelList().getModelList().size() == 1) {
                        String algoName = algoModel.getAlgorithm().getAnnotation().name();

                        hpcAccount = showRemoteComputingOptions(algoName);
                    }

                    if (hpcAccount == null) {
                        try {
                            algorithmRunner.execute();

                            algorithmCard.saveStates();

                            firePropertyChange("modelChanged", null, null);
                            graphCard.refresh();
                            changeCard(GRAPH_CARD);
                        } catch (Exception exception) {
                            exception.printStackTrace(System.err);
                        }

                        // Update the graphContainer
                        parameterCard.enableButtons();
                    } else {
                        try {
                            doRemoteCompute(algorithmRunner, hpcAccount);
                        } catch (Exception exception) {
                            LOGGER.error("Unable to run algorithm.", exception);
                        }
                    }
                }

                HpcAccount hpcAccount = null;
                if (hpcAccount == null) {
                    algorithmRunner.execute();

                    firePropertyChange("modelChanged", null, null);

                }
            }
        };

    }

    @Override
    public boolean finalizeEditor() {
        List<Graph> graphs = algorithmRunner.getGraphs();
        if (hpcJobInfo == null && (graphs == null || graphs.isEmpty())) {
            int option = JOptionPane.showConfirmDialog(this, "You have not performed a search. Close anyway?", "Close?",
                    JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }

        return true;
    }

}
