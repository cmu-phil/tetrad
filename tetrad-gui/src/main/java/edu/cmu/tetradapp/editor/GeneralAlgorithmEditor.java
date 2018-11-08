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
import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.TsImages;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Gaussian;
import edu.cmu.tetrad.annotation.Linear;
import edu.cmu.tetrad.annotation.Nonexecutable;
import edu.cmu.tetrad.data.*;
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
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import edu.cmu.tetradapp.ui.model.AlgorithmModels;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.ui.model.ScoreModel;
import edu.cmu.tetradapp.ui.model.ScoreModels;
import edu.cmu.tetradapp.util.BootstrapTable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
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
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
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
public class GeneralAlgorithmEditor extends JPanel implements FinalizingEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralAlgorithmEditor.class);

    private static final long serialVersionUID = -5719467682865706447L;

    private final String ALGORITHM_CARD = "algorithm card";
    private final String PARAMETER_CARD = "parameter card";
    private final String GRAPH_CARD = "graph card";

    private final String ALGO_PARAM = "algo";
    private final String IND_TEST_PARAM = "ind_test";
    private final String SCORE_PARAM = "score";
    private final String ALGO_TYPE_PARAM = "algo_type";
    private final String LINEAR_PARAM = "linear";
    private final String GAUSSIAN_PARAM = "gaussian";
    private final String KNOWLEDGE_PARAM = "knowledge";

    private String jsonResult;
    private HpcJobInfo hpcJobInfo;

    private boolean updatingTestModels;
    private boolean updatingScoreModels;

    private final Map<AlgorithmModel, Map<DataType, IndependenceTestModel>> defaultIndTestModels = new HashMap<>();
    private final Map<AlgorithmModel, Map<DataType, ScoreModel>> defaultScoreModels = new HashMap<>();

    private final JPanel mainPanel = new JPanel(new CardLayout());
    private final List<JRadioButton> algoTypeOpts = new ArrayList<>();
    private final ButtonGroup algoFilterBtnGrp = new ButtonGroup();
    private final JCheckBox knowledgeChkBox = new JCheckBox("accepts knowledge");
    private final JCheckBox gaussianVarChkBox = new JCheckBox("Gaussian variables");
    private final JCheckBox linearVarChkBox = new JCheckBox("Variables with linear relationship");
    private final DefaultListModel<AlgorithmModel> algoModels = new DefaultListModel<>();
    private final JList<AlgorithmModel> algorithmList = new JList<>(algoModels);
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    private final JComboBox<ScoreModel> scoreComboBox = new JComboBox<>();
    private final JTextArea algoDescTextArea = new JTextArea();
    private final Box graphContainer = Box.createHorizontalBox();
    private final JLabel algorithmGraphTitle = new JLabel();

    private final AlgorithmParameterPanel parametersPanel;
    private final JButton paramSetFwdBtn = new JButton("Set Parameters   >");

    private final GeneralAlgorithmRunner runner;
    private final TetradDesktop desktop;
    private final DataType dataType;
    
    public GeneralAlgorithmEditor(GeneralAlgorithmRunner runner) {
        this.runner = runner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.dataType = getDataType();
        this.parametersPanel = new AlgorithmParameterPanel();

        if (dataType == null) {
            String errMsg = "You need either some datasets or a graph as input.";
            throw new IllegalArgumentException(errMsg);
        }

        initComponents();
        resetAllSettings();
        restorePreviousState(runner.getModels());

        // Repopulate all the previous selections if reopen the search box
        if (runner.getGraphs() != null && runner.getGraphs().size() > 0) {
            parametersPanel.addToPanel(runner);
            
            // show the generated graph with bootstrap table if reopen the search box
            graphContainer.add(createSearchResultPane(runner.getGraph()));
            changeCard(GRAPH_CARD);
        }
    }

    private void storeStates(Map<String, Object> models) {
        models.put(ALGO_PARAM, algorithmList.getSelectedValue());
        models.put(IND_TEST_PARAM, indTestComboBox.getSelectedItem());
        models.put(SCORE_PARAM, scoreComboBox.getSelectedItem());
        models.put(ALGO_TYPE_PARAM, algoFilterBtnGrp.getSelection().getActionCommand());
        models.put(LINEAR_PARAM, linearVarChkBox.isSelected());
        models.put(GAUSSIAN_PARAM, gaussianVarChkBox.isSelected());
        models.put(KNOWLEDGE_PARAM, knowledgeChkBox.isSelected());
    }

    private void restorePreviousState(Map<String, Object> models) {
        Object obj = models.get(LINEAR_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            linearVarChkBox.setSelected((Boolean) obj);
        }
        obj = models.get(GAUSSIAN_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            gaussianVarChkBox.setSelected((Boolean) obj);
        }
        obj = models.get(KNOWLEDGE_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            knowledgeChkBox.setSelected((Boolean) obj);
        }
        obj = models.get(ALGO_TYPE_PARAM);
        if ((obj != null) && (obj instanceof String)) {
            String actCmd = String.valueOf(obj);
            Optional<JRadioButton> opt = algoTypeOpts.stream()
                    .filter(e -> e.getActionCommand().equals(actCmd))
                    .findFirst();
            if (opt.isPresent()) {
                opt.get().setSelected(true);
            }
        }

        refreshAlgorithmList();
        refreshTestAndScoreList();

        obj = models.get(ALGO_PARAM);
        if ((obj != null) && (obj instanceof AlgorithmModel)) {
            String value = ((AlgorithmModel) obj).toString();
            Enumeration<AlgorithmModel> enums = algoModels.elements();
            while (enums.hasMoreElements()) {
                AlgorithmModel model = enums.nextElement();
                if (model.toString().equals(value)) {
                    models.put(ALGO_PARAM, model);
                    algorithmList.setSelectedValue(model, true);

                    String title = String.format("Algorithm: %s", model.getAlgorithm().getAnnotation().name());
                    algorithmGraphTitle.setText(title);
                    break;
                }
            }
        }
        obj = models.get(IND_TEST_PARAM);
        if ((obj != null) && (obj instanceof IndependenceTestModel)) {
            String value = ((IndependenceTestModel) obj).toString();
            ComboBoxModel<IndependenceTestModel> comboBoxModels = indTestComboBox.getModel();
            int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                IndependenceTestModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    models.put(IND_TEST_PARAM, model);
                    indTestComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }
        obj = models.get(SCORE_PARAM);
        if ((obj != null) && (obj instanceof ScoreModel)) {
            String value = ((ScoreModel) obj).toString();
            ComboBoxModel<ScoreModel> comboBoxModels = scoreComboBox.getModel();
            int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                ScoreModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    models.put(SCORE_PARAM, model);
                    scoreComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }
    }

    private void initComponents() {
        algoDescTextArea.setWrapStyleWord(true);
        algoDescTextArea.setLineWrap(true);
        algoDescTextArea.setEditable(false);

        populateAlgoTypeOptions(algoTypeOpts);

        knowledgeChkBox.addActionListener((e) -> {
            refreshAlgorithmList();
        });
        linearVarChkBox.addActionListener((ActionEvent e) -> {
            refreshTestAndScoreList();
        });
        gaussianVarChkBox.addActionListener((ActionEvent e) -> {
            refreshTestAndScoreList();
        });
        algorithmList.addListSelectionListener((e) -> {
            if (!(e.getValueIsAdjusting() || algorithmList.isSelectionEmpty())) {
                setAlgorithmDescription();
                refreshTestAndScoreList();
                validateAlgorithmOption();
            }
        });
        paramSetFwdBtn.addActionListener((e) -> {
            AlgorithmModel algoModel = algorithmList.getSelectedValue();
            IndependenceTestModel indTestModel = indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex());
            ScoreModel scoreModel = scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex());
            if (isValid(algoModel, indTestModel, scoreModel)) {
                setParameterPanel(algoModel, indTestModel, scoreModel);
                changeCard(PARAMETER_CARD);
            }
        });
        indTestComboBox.addActionListener((e) -> {
            if (!updatingTestModels && indTestComboBox.getSelectedIndex() >= 0) {
                AlgorithmModel algoModel = algorithmList.getSelectedValue();
                Map<DataType, IndependenceTestModel> map = defaultIndTestModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    defaultIndTestModels.put(algoModel, map);
                }
                map.put(dataType, indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex()));
            }
        });
        scoreComboBox.addActionListener((e) -> {
            if (!updatingScoreModels && scoreComboBox.getSelectedIndex() >= 0) {
                AlgorithmModel algoModel = algorithmList.getSelectedValue();
                Map<DataType, ScoreModel> map = defaultScoreModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    defaultScoreModels.put(algoModel, map);
                }
                map.put(dataType, scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex()));
            }
        });

        mainPanel.add(new AlgorithmCard(), ALGORITHM_CARD);
        mainPanel.add(new ParameterCard(), PARAMETER_CARD);
        mainPanel.add(new GraphCard(), GRAPH_CARD);
        mainPanel.setPreferredSize(new Dimension(860, 640));

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
//        add(new JScrollPane(mainPanel), BorderLayout.CENTER);
    }

    private void setParameterPanel(AlgorithmModel algoModel, IndependenceTestModel indTestModel, ScoreModel scoreModel) {
        runner.setAlgorithm(getAlgorithmFromInterface(algoModel, indTestModel, scoreModel));
        parametersPanel.addToPanel(runner);
    }

    private boolean isValid(AlgorithmModel algoModel, IndependenceTestModel indTestModel, ScoreModel scoreModel) {
        boolean missingTest = algoModel.isRequiredTest() && (indTestModel == null);
        boolean missingScore = algoModel.isRequiredScore() && (scoreModel == null);
        if (missingTest && missingScore) {
            String msg = String.format("%s requires both test and score.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingTest) {
            String msg = String.format("%s requires independence test.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingScore) {
            String msg = String.format("%s requires score.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else {
            return true;
        }
    }

    private void validateAlgorithmOption() {
        paramSetFwdBtn.setEnabled(true);

        AlgorithmModel algoModel = algorithmList.getSelectedValue();
        Class algoClass = algoModel.getAlgorithm().getClazz();

        if (algoClass.isAnnotationPresent(Nonexecutable.class)) {
            String msg;
            try {
                Object algo = algoClass.newInstance();
                Method m = algoClass.getDeclaredMethod("getDescription");
                m.setAccessible(true);
                try {
                    msg = String.valueOf(m.invoke(algo));
                } catch (InvocationTargetException exception) {
                    msg = "";
                }

            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException exception) {
                LOGGER.error("", exception);
                msg = "";
            }

            paramSetFwdBtn.setEnabled(false);
            JOptionPane.showMessageDialog(desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Check if initial graph is provided for those pairwise algorithms
            if (TakesInitialGraph.class.isAssignableFrom(algoClass)) {
                if (runner.getSourceGraph() == null || runner.getDataModelList().isEmpty()) {
                    try {
                        Object algo = algoClass.newInstance();
                        Method m = algoClass.getDeclaredMethod("setInitialGraph", Algorithm.class);
                        m.setAccessible(true);
                        try {
                            Algorithm algorithm = null;
                            m.invoke(algo, algorithm);
                        } catch (InvocationTargetException | IllegalArgumentException exception) {
                            paramSetFwdBtn.setEnabled(false);
                            JOptionPane.showMessageDialog(desktop, exception.getCause().getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (IllegalAccessException | InstantiationException | NoSuchMethodException exception) {
                        LOGGER.error("", exception);
                    }
                }
            }
            
            // Time-series (TsFci, TsGfci, TsImages) algorithms need lagged data
            String cmd = algoModel.getAlgorithm().getAnnotation().command();
            if(cmd.equalsIgnoreCase("ts-fci") ||
            		cmd.equalsIgnoreCase("ts-gfci") ||
            		cmd.equalsIgnoreCase("ts-imgs")) {
            	DataModel dataModel = runner.getDataModel();
            	IKnowledge knowledge = runner.getKnowledge();
            	if((knowledge == null || knowledge.isEmpty()) &&
            			(dataModel.getKnowledge() == null || dataModel.getKnowledge().isEmpty())) {
            		paramSetFwdBtn.setEnabled(false);
                    JOptionPane.showMessageDialog(desktop, "Time-series algorithm needs lagged data", "Please Note", JOptionPane.INFORMATION_MESSAGE);
            	}
            }
        }

        // Check dataset data type for those algorithms take mixed data?
    }

    private void setAlgorithmDescription() {
        AlgorithmModel model = algorithmList.getSelectedValue();
        if (model == null) {
            algoDescTextArea.setText("");
        } else {
            algoDescTextArea.setText(model.getDescription());
            algoDescTextArea.setCaretPosition(0);
        }
    }

    private void refreshTestAndScoreList() {
        refreshTestList();
        refreshScoreList();
    }

    private void refreshScoreList() {
        updatingScoreModels = true;
        scoreComboBox.removeAllItems();
        AlgorithmModel algoModel = algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredScore()) {
            boolean linear = linearVarChkBox.isSelected();
            boolean gaussian = gaussianVarChkBox.isSelected();
            List<ScoreModel> models = ScoreModels.getInstance().getModels(dataType);
            List<ScoreModel> scoreModels = new LinkedList<>();
            if (linear && gaussian) {
                models.stream()
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> scoreModels.add(e));
            } else if (linear) {
                models.stream()
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> !e.getScore().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> scoreModels.add(e));
            } else if (gaussian) {
                models.stream()
                        .filter(e -> !e.getScore().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> scoreModels.add(e));
            } else {
                models.stream()
                        .forEach(e -> scoreModels.add(e));
            }

            // TsIMaGES can only take SEM BIC score for continuous data
            // or BDeu score for discrete data
            if (TsImages.class.equals(algoModel.getAlgorithm().getClazz())) {
                switch (dataType) {
                    case Continuous:
                        scoreModels.stream()
                                .filter(e -> e.getScore().getClazz().equals(SemBicScore.class))
                                .forEach(e -> scoreComboBox.addItem(e));
                        break;
                    case Discrete:
                        scoreModels.stream()
                                .filter(e -> e.getScore().getClazz().equals(BdeuScore.class))
                                .forEach(e -> scoreComboBox.addItem(e));
                        break;
                }
            } else {
                scoreModels.forEach(e -> scoreComboBox.addItem(e));
            }
        }
        updatingScoreModels = false;
        if (scoreComboBox.getItemCount() > 0) {
            scoreComboBox.setEnabled(true);

            Map<DataType, ScoreModel> map = defaultScoreModels.get(algoModel);
            if (map == null) {
                map = new EnumMap<>(DataType.class);
                defaultScoreModels.put(algoModel, map);
            }

            ScoreModel scoreModel = map.get(dataType);
            if (scoreModel == null) {
                scoreModel = ScoreModels.getInstance().getDefaultModel(dataType);
                if (scoreModel == null) {
                    scoreModel = scoreComboBox.getItemAt(0);
                }
            }
            scoreComboBox.setSelectedItem(scoreModel);
        } else {
            scoreComboBox.setEnabled(false);
        }
    }

    private void refreshTestList() {
        updatingTestModels = true;
        indTestComboBox.removeAllItems();
        AlgorithmModel algoModel = algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredTest()) {
            boolean linear = linearVarChkBox.isSelected();
            boolean gaussian = gaussianVarChkBox.isSelected();
            List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(dataType);
            if (linear && gaussian) {
                models.stream()
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> indTestComboBox.addItem(e));
            } else if (linear) {
                models.stream()
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> !e.getIndependenceTest().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> indTestComboBox.addItem(e));
            } else if (gaussian) {
                models.stream()
                        .filter(e -> !e.getIndependenceTest().getClazz().isAnnotationPresent(Linear.class))
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(Gaussian.class))
                        .forEach(e -> indTestComboBox.addItem(e));
            } else {
                models.stream()
                        .forEach(e -> indTestComboBox.addItem(e));
            }
        }
        updatingTestModels = false;
        if (indTestComboBox.getItemCount() > 0) {
            indTestComboBox.setEnabled(true);

            Map<DataType, IndependenceTestModel> map = defaultIndTestModels.get(algoModel);
            if (map == null) {
                map = new EnumMap<>(DataType.class);
                defaultIndTestModels.put(algoModel, map);
            }

            IndependenceTestModel testModel = map.get(dataType);
            if (testModel == null) {
                testModel = IndependenceTestModels.getInstance().getDefaultModel(dataType);
                if (testModel == null) {
                    testModel = indTestComboBox.getItemAt(0);
                }
            }
            indTestComboBox.setSelectedItem(testModel);
        } else {
            indTestComboBox.setEnabled(false);
        }
    }

    private void refreshAlgorithmList() {
        algoModels.clear();

        ButtonModel selectedAlgoType = algoFilterBtnGrp.getSelection();
        if (selectedAlgoType != null) {
            AlgorithmModels algorithmModels = AlgorithmModels.getInstance();
            String algoType = selectedAlgoType.getActionCommand();
            if ("all".equals(algoType)) {
                if (knowledgeChkBox.isSelected()) {
                    algorithmModels.getModels().stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> algoModels.addElement(e));
                } else {
                    algorithmModels.getModels().stream()
                            .forEach(e -> algoModels.addElement(e));
                }
            } else {
                if (knowledgeChkBox.isSelected()) {
                    algorithmModels.getModels(AlgType.valueOf(algoType)).stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> algoModels.addElement(e));
                } else {
                    algorithmModels.getModels(AlgType.valueOf(algoType)).stream()
                            .forEach(e -> algoModels.addElement(e));
                }
            }

            if (algoModels.isEmpty()) {
                paramSetFwdBtn.setEnabled(false);
            } else {
                algorithmList.setSelectedIndex(0);
                paramSetFwdBtn.setEnabled(true);
            }
        }
        scoreComboBox.setEnabled(scoreComboBox.getItemCount() > 0);
    }

    private DataType getDataType() {
        DataModelList dataModelList = runner.getDataModelList();
        if (dataModelList.containsEmptyData()) {
            if (runner.getSourceGraph() == null) {
                return null;
            } else {
                return DataType.Graph;
            }
        } else {
            DataModel dataSet = dataModelList.get(0);
            if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
                // covariance dataset is continuous at the same time - Zhou
                return DataType.Continuous;
            } else if (dataSet.isDiscrete()) {
                return DataType.Discrete;
            } else if (dataSet.isMixed()) {
                return DataType.Mixed;
            } else if (dataSet instanceof ICovarianceMatrix) { // Better to add an isCovariance() - Zhou
                return DataType.Covariance;
            } else {
                return null;
            }
        }
    }

    /**
     * Create new radio buttons and add them to both the radio button list and
     * radio button group.
     *
     * @param radioButtons
     */
    private void populateAlgoTypeOptions(List<JRadioButton> radioButtons) {
        JRadioButton showAllRadBtn = new JRadioButton("show all");
        showAllRadBtn.setActionCommand("all");
        showAllRadBtn.addActionListener((e) -> {
            algoTypeSelectAction(e);
        });
        radioButtons.add(showAllRadBtn);
        algoFilterBtnGrp.add(showAllRadBtn);

        for (AlgType item : AlgType.values()) {
            String name = item.name();

            JRadioButton radioButton = new JRadioButton(name.replace("_", " "));
            radioButton.setActionCommand(name);
            radioButton.addActionListener((e) -> {
                algoTypeSelectAction(e);
            });

            radioButtons.add(radioButton);
            algoFilterBtnGrp.add(radioButton);
        }
    }

    private void algoTypeSelectAction(ActionEvent e) {
        refreshAlgorithmList();
    }

    public void setAlgorithmResult(String jsonResult) {
        this.jsonResult = jsonResult;
        System.out.println("json result: " + jsonResult);

        final Graph graph = JsonUtils.parseJSONObjectToTetradGraph(jsonResult);
        final List<Graph> graphs = new ArrayList<>();
        graphs.add(graph);
        int size = runner.getGraphs().size();
        for (int index = 0; index < size; index++) {
            runner.getGraphs().remove(index);
        }
        runner.getGraphs().add(graph);

        LOGGER.info("Remote graph result assigned to runner!");
        firePropertyChange("modelChanged", null, null);

        // Update the graphContainer
        graphContainer.removeAll();
        graphContainer.add(createSearchResultPane(graph));

        changeCard(GRAPH_CARD);
    }

    public void setAlgorithmErrorResult(String errorResult) {
        JOptionPane.showMessageDialog(desktop, jsonResult);
        throw new IllegalArgumentException(errorResult);
    }

    /**
     * Initialize algorithm
     *
     * @param algoModel
     * @param indTestModel
     * @param scoreModel
     * @return Algorithm
     */
    public Algorithm getAlgorithmFromInterface(AlgorithmModel algoModel, IndependenceTestModel indTestModel, ScoreModel scoreModel) {
        Class algoClass = algoModel.getAlgorithm().getClazz();
        Class indTestClass = (indTestModel == null) ? null : indTestModel.getIndependenceTest().getClazz();
        Class scoreClass = (scoreModel == null) ? null : scoreModel.getScore().getClazz();

        Algorithm algorithm = null;

        try {
            algorithm = AlgorithmFactory.create(algoClass, indTestClass, scoreClass);
        } catch (IllegalAccessException | InstantiationException exception) {
            LOGGER.error("", exception);
        }

        // Those pairwise algos (R3, RShew, Skew..) require source graph to initialize - Zhou
        if (algorithm != null && algorithm instanceof TakesInitialGraph && runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
            Algorithm initialGraph = new SingleGraphAlg(runner.getSourceGraph());
            ((TakesInitialGraph) algorithm).setInitialGraph(initialGraph);
        }

        return algorithm;
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
            //  LOGGER.info(file.toAbsolutePath().toString());
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
                DataWriter.saveKnowledge(knowledge,Files.newBufferedWriter(prior));

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
            AlgorithmModel algoModel = algorithmList.getSelectedValue();
            String algoId = algoModel.getAlgorithm().getAnnotation().command();
            // Test
            String testId = null;
            if (indTestComboBox.isEnabled()) {
                IndependenceTestModel indTestModel = indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex());
                testId = indTestModel.getIndependenceTest().getAnnotation().command();
            }
            // Score
            String scoreId = null;
            if (scoreComboBox.isEnabled()) {
                ScoreModel scoreModel = scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex());
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

            Parameters parameters = runner.getParameters();
            List<String> parameterNames = runner.getAlgorithm().getParameters();
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
            hpcJobManager.submitNewHpcJobToQueue(hpcJobInfo, this);

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

    private void doSearch(final GeneralAlgorithmRunner runner) {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                AlgorithmModel algoModel = algorithmList.getSelectedValue();
                if (algoModel != null) {
                    String title = String.format("Algorithm: %s", algoModel.getAlgorithm().getAnnotation().name());
                    algorithmGraphTitle.setText(title);

                    HpcAccount hpcAccount = null;

                    if (algoModel.getAlgorithm().getAnnotation().algoType() != AlgType.orient_pairwise
                            && runner.getDataModelList().getModelList().size() == 1) {
                        String algoName = algoModel.getAlgorithm().getAnnotation().name();

                        hpcAccount = showRemoteComputingOptions(algoName);
                    }

                    if (hpcAccount == null) {
                        runner.execute();

                        firePropertyChange("modelChanged", null, null);

                        // Update the graphContainer
                        graphContainer.removeAll();
                        graphContainer.add(createSearchResultPane(runner.getGraph()));

                        changeCard(GRAPH_CARD);
                    } else {
                        try {
                            doRemoteCompute(runner, hpcAccount);
                        } catch (Exception exception) {
                            LOGGER.error("Unable to run algorithm.", exception);
                        }
                    }
                }
            }
        };

    }

    @Override
    public boolean finalizeEditor() {
        storeStates(runner.getModels());

        List<Graph> graphs = runner.getGraphs();
        if (hpcJobInfo == null && (graphs == null || graphs.isEmpty())) {
            int option = JOptionPane.showConfirmDialog(this, "You have not performed a search. Close anyway?", "Close?",
                    JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private void resetAllSettings() {
        // clear cache
        defaultIndTestModels.clear();
        defaultScoreModels.clear();

        // uncheck all checkboxes
        linearVarChkBox.setSelected(false);
        gaussianVarChkBox.setSelected(false);
        knowledgeChkBox.setSelected(false);

        if (!algoTypeOpts.isEmpty()) {
            algoTypeOpts.get(0).setSelected(true);
        }
        refreshAlgorithmList();
        refreshTestList();
        refreshScoreList();
    }

    private JPanel createAlgorithmFilterPanel() {
        // Filter based on algo types dropdown
        Box algoTypesBox = Box.createVerticalBox();

        // Algo types label box
        Box algTypesBoxLabelBox = Box.createHorizontalBox();
        algTypesBoxLabelBox.add(new JLabel("Show algorithms that: "));
        algTypesBoxLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Add label to containing box
        algoTypesBox.add(algTypesBoxLabelBox);

        // All option
        Box algoTypeOptionAllBox = Box.createHorizontalBox();
        algoTypeOptionAllBox.setAlignmentX(LEFT_ALIGNMENT);

        // Add all option to containing box
        algoTypesBox.add(algoTypeOptionAllBox);

        // add radio buttons to panel
        if (!algoTypeOpts.isEmpty()) {
            Dimension indentSize = new Dimension(10, 20);
            algoTypeOpts.forEach(btn -> {
                Box box = Box.createHorizontalBox();
                box.setAlignmentX(LEFT_ALIGNMENT);
                box.add(Box.createRigidArea(indentSize));
                box.add(btn);
                algoTypesBox.add(box);
            });
        }

        // Is there a prior knowledge file?
        Box priorKnowledgeBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box priorKnowledgeLabelBox = Box.createHorizontalBox();
        priorKnowledgeLabelBox.add(new JLabel("Show only: "));
        priorKnowledgeLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Checkbox container
        Box priorKnowledgeOptionBox = Box.createHorizontalBox();
        priorKnowledgeOptionBox.setAlignmentX(LEFT_ALIGNMENT);

        // Add padding and option
        priorKnowledgeOptionBox.add(Box.createRigidArea(new Dimension(10, 20)));
        priorKnowledgeOptionBox.add(knowledgeChkBox);

        // Add to containg box
        priorKnowledgeBox.add(priorKnowledgeLabelBox);
        priorKnowledgeBox.add(priorKnowledgeOptionBox);

        Box algoFiltersBox = Box.createVerticalBox();
        algoFiltersBox.setAlignmentX(LEFT_ALIGNMENT);
        algoFiltersBox.add(algoTypesBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(priorKnowledgeBox);

        JPanel algoFilter = new JPanel(new BorderLayout());
        algoFilter.setBorder(BorderFactory.createTitledBorder("Algorithm Filters"));
        algoFilter.add(new PaddingPanel(algoFiltersBox), BorderLayout.CENTER);

        return algoFilter;
    }

    private class TestAndScorePanel extends JPanel {

        private static final long serialVersionUID = -4389655965283163014L;
        private JLabel assumptionsLabel;
        private JLabel scoreLabel;
        private JLabel testLabel;

        public TestAndScorePanel() {
            initComponents();
        }

        private void initComponents() {
            assumptionsLabel = new JLabel();
            testLabel = new JLabel();
            scoreLabel = new JLabel();

            setBorder(BorderFactory.createTitledBorder("Choose Statistical Test and Score"));
            setPreferredSize(new Dimension(330, 200));

            assumptionsLabel.setText("Filter by dataset properties:");

            testLabel.setText("Test:");
            scoreLabel.setText("Score:");

            GroupLayout layout = new GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addComponent(assumptionsLabel)
                                                            .addGroup(layout.createSequentialGroup()
                                                                    .addGap(12, 12, 12)
                                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                            .addComponent(gaussianVarChkBox)
                                                                            .addComponent(linearVarChkBox))))
                                                    .addGap(0, 0, Short.MAX_VALUE))
                                            .addGroup(layout.createSequentialGroup()
                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addComponent(testLabel)
                                                            .addComponent(scoreLabel))
                                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addComponent(scoreComboBox, 0, 239, Short.MAX_VALUE)
                                                            .addComponent(indTestComboBox, 0, 239, Short.MAX_VALUE))))
                                    .addContainerGap())
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(assumptionsLabel)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(linearVarChkBox)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(gaussianVarChkBox)
                                    .addGap(22, 22, 22)
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                            .addComponent(testLabel)
                                            .addComponent(indTestComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                            .addComponent(scoreComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                            .addComponent(scoreLabel))
                                    .addContainerGap())
            );
        }

    }

    /**
     * Resulting graph with bootstrap table - Zhou
     * 
     * @param graph
     * @return 
     */
    private JSplitPane createSearchResultPane(Graph graph) {
        // topBox contains the graphEditorScroll and the instructionBox underneath
        Box topBox = Box.createVerticalBox();       
        topBox.setPreferredSize(new Dimension(820, 400));
 
        // topBox graph editor
        JScrollPane graphEditorScroll = new JScrollPane();
        graphEditorScroll.setPreferredSize(new Dimension(820, 420));
        graphEditorScroll.setViewportView(new GraphWorkbench(graph));

        // Instruction with info button 
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(820, 40));
        
        JLabel label = new JLabel("More information on graph edge types");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Initialize helpSet
                String helpHS = "/resources/javahelp/TetradHelp.hs";

                try {
                    URL url = this.getClass().getResource(helpHS);
                    HelpSet helpSet = new HelpSet(null, url);

                    helpSet.setHomeID("graph_edge_types");
                    HelpBroker broker = helpSet.createHelpBroker();
                    ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                    listener.actionPerformed(e);
                } catch (Exception ee) {
                    System.out.println("HelpSet " + ee.getMessage());
                    System.out.println("HelpSet " + helpHS + " not found");
                    throw new IllegalArgumentException();
                }
            }
        });

        instructionBox.add(label);
        instructionBox.add(Box.createHorizontalStrut(2));
        instructionBox.add(infoBtn);
        
        // Add to topBox
        topBox.add(graphEditorScroll);
        topBox.add(instructionBox);

        // bottomBox contains bootstrap table
        Box bottomBox = Box.createVerticalBox();
        bottomBox.setPreferredSize(new Dimension(820, 150));

        bottomBox.add(Box.createVerticalStrut(5));
        
        // Put the table title label in a box so it can be centered
        Box tableTitleBox = Box.createHorizontalBox();
        JLabel tableTitle = new JLabel("Edges and Edge Type Probabilities");
        tableTitleBox.add(tableTitle);
        
        bottomBox.add(tableTitleBox);
        
        bottomBox.add(Box.createVerticalStrut(5));
        
        JScrollPane tablePane = BootstrapTable.renderBootstrapTable(graph);
        
        bottomBox.add(tablePane);
        
        // Use JSplitPane to allow resize the bottom box - Zhou
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // Set the top and bottom split panes
        splitPane.setTopComponent(topBox);
        splitPane.setBottomComponent(bottomBox);
        
        return splitPane;
    }
    
    private JPanel createAlgorithmPanel() {
        JButton resetSettingsBtn = new JButton("Reset All Settings");
        resetSettingsBtn.addActionListener((e) -> {
            resetAllSettings();
        });

        JPanel westMainSouthPanel = new JPanel(new BorderLayout(0, 10));
        westMainSouthPanel.add(new TestAndScorePanel(), BorderLayout.CENTER);
        westMainSouthPanel.add(resetSettingsBtn, BorderLayout.SOUTH);

        JPanel westMainWestPanel = new JPanel(new BorderLayout(0, 10));
        westMainWestPanel.add(createAlgorithmFilterPanel(), BorderLayout.CENTER);
        westMainWestPanel.add(westMainSouthPanel, BorderLayout.SOUTH);

        JPanel westMainPanel = new JPanel(new BorderLayout(5, 0));
        westMainPanel.add(westMainWestPanel, BorderLayout.WEST);
        westMainPanel.add(new AlgorithmListPanel(), BorderLayout.EAST);

        JPanel algoCard = new JPanel(new BorderLayout(10, 0));
        algoCard.add(westMainPanel, BorderLayout.WEST);
        algoCard.add(new AlgoDescPanel(), BorderLayout.CENTER);

        return new PaddingPanel(algoCard);
    }

    private void changeCard(String card) {
        CardLayout cardLayout = (CardLayout) mainPanel.getLayout();
        cardLayout.show(mainPanel, card);
    }

    private void paramCardFwdBtnAction(ActionEvent e) {
        doSearch(runner);
    }

    private void paramCardBackBtnAction(ActionEvent e) {
        changeCard(ALGORITHM_CARD);
    }

    private void graphCardBackBtnAction(ActionEvent e) {
        changeCard(PARAMETER_CARD);
    }

    private class AlgorithmListPanel extends JPanel {

        private static final long serialVersionUID = -7068543172769683902L;

        public AlgorithmListPanel() {
            initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(algorithmList);

            setBorder(BorderFactory.createTitledBorder("Choose Algorithm"));
            setPreferredSize(new Dimension(230, 300));

            GroupLayout layout = new GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 206, Short.MAX_VALUE)
                                    .addContainerGap())
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 254, Short.MAX_VALUE)
                                    .addContainerGap())
            );
        }

    }

    private class AlgoDescPanel extends JPanel {

        private static final long serialVersionUID = -4159055717661942076L;

        public AlgoDescPanel() {
            initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(algoDescTextArea);

            setBorder(BorderFactory.createTitledBorder("Algorithm Description"));
            setPreferredSize(new Dimension(235, 300));

            GroupLayout layout = new GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
                                    .addContainerGap())
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 254, Short.MAX_VALUE)
                                    .addContainerGap())
            );
        }

    }

    private class AlgorithmCard extends JPanel {

        private static final long serialVersionUID = -9096601292449012553L;

        public AlgorithmCard() {
            initComponents();
        }

        private void initComponents() {
            setLayout(new BorderLayout());

            add(new JScrollPane(createAlgorithmPanel()), BorderLayout.CENTER);
            add(new SouthPanel(paramSetFwdBtn), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = -4055772024145978761L;

            private final JButton forwardBtn;

            public SouthPanel(JButton forwardBtn) {
                this.forwardBtn = forwardBtn;
                initComponents();
            }

            private void initComponents() {
                Dimension buttonSize = new Dimension(268, 25);
                paramSetFwdBtn.setMinimumSize(buttonSize);
                paramSetFwdBtn.setMaximumSize(buttonSize);

                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(forwardBtn)
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(forwardBtn)
                                        .addContainerGap())
                );
            }
        }

    }

    private class ParameterCard extends JPanel {

        private static final long serialVersionUID = -3281593127079058064L;

        private JButton forwardBtn;
        private JButton backBtn;

        public ParameterCard() {
            initComponents();
        }

        private void initComponents() {
            setLayout(new BorderLayout());

            Dimension buttonSize = new Dimension(268, 25);

            backBtn = new JButton("<   Choose Algorithm");
            backBtn.setMinimumSize(buttonSize);
            backBtn.setMaximumSize(buttonSize);
            backBtn.addActionListener((e) -> {
                paramCardBackBtnAction(e);
            });

            forwardBtn = new JButton("Run Search & Generate Graph   >");
            forwardBtn.setMinimumSize(buttonSize);
            forwardBtn.setMaximumSize(buttonSize);
            forwardBtn.addActionListener((e) -> {
                paramCardFwdBtnAction(e);
            });

            add(new JScrollPane(new PaddingPanel(parametersPanel)), BorderLayout.CENTER);
            add(new SouthPanel(forwardBtn, backBtn), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = -4055772024145978761L;

            private final JButton forwardBtn;
            private final JButton backBtn;

            public SouthPanel(JButton forwardBtn, JButton backBtn) {
                this.forwardBtn = forwardBtn;
                this.backBtn = backBtn;
                initComponents();
            }

            private void initComponents() {
                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(backBtn)
                                        .addGap(18, 18, 18)
                                        .addComponent(forwardBtn)
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );

                layout.linkSize(SwingConstants.HORIZONTAL, new java.awt.Component[]{backBtn, forwardBtn});

                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(backBtn)
                                                .addComponent(forwardBtn))
                                        .addContainerGap())
                );
            }
        }

    }

    private class GraphCard extends JPanel {

        private static final long serialVersionUID = -4333810762051607855L;

        private JButton backBtn;
        private JPanel titlePanel;

        public GraphCard() {
            initComponents();
        }

        private void initComponents() {
            setLayout(new BorderLayout());

            Dimension buttonSize = new Dimension(268, 25);

            backBtn = new JButton("<   Set Parameters");
            backBtn.setMinimumSize(buttonSize);
            backBtn.setMaximumSize(buttonSize);
            backBtn.addActionListener((e) -> {
                graphCardBackBtnAction(e);
            });

            titlePanel = new JPanel();
            titlePanel.add(algorithmGraphTitle);

            add(titlePanel, BorderLayout.NORTH);
            add(graphContainer, BorderLayout.CENTER);
            add(new SouthPanel(backBtn), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = -4055772024145978761L;

            private final JButton backBtn;

            public SouthPanel(JButton backBtn) {
                this.backBtn = backBtn;
                initComponents();
            }

            private void initComponents() {
                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(backBtn)
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(backBtn)
                                        .addContainerGap())
                );
            }
        }

    }

}
