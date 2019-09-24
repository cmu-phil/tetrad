/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.editor.search;

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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import edu.cmu.tetradapp.ui.model.AlgorithmModels;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.ui.model.ScoreModel;
import edu.cmu.tetradapp.ui.model.ScoreModels;
import edu.cmu.tetradapp.util.DesktopController;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Apr 15, 2019 11:31:10 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmCard extends JPanel {

    private static final long serialVersionUID = -7552068626783685630L;

    private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmCard.class);

    private final String ALGO_PARAM = "algo";
    private final String IND_TEST_PARAM = "ind_test";
    private final String SCORE_PARAM = "score";
    private final String ALGO_TYPE_PARAM = "algo_type";
    private final String LINEAR_PARAM = "linear";
    private final String GAUSSIAN_PARAM = "gaussian";
    private final String KNOWLEDGE_PARAM = "knowledge";

    private final List<JRadioButton> algoTypeOpts = new ArrayList<>();

    private final DefaultListModel<AlgorithmModel> algoModels = new DefaultListModel<>();

    private final ButtonGroup algoFilterBtnGrp = new ButtonGroup();

    private final Map<AlgorithmModel, Map<DataType, IndependenceTestModel>> defaultIndTestModels = new HashMap<>();
    private final Map<AlgorithmModel, Map<DataType, ScoreModel>> defaultScoreModels = new HashMap<>();
    private final JCheckBox knowledgeChkBox = new JCheckBox("accepts knowledge");
    private final JCheckBox gaussianVarChkBox = new JCheckBox("Gaussian variables");
    private final JCheckBox linearVarChkBox = new JCheckBox("Variables with linear relationship");
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    private final JComboBox<ScoreModel> scoreComboBox = new JComboBox<>();
    private final JList<AlgorithmModel> algorithmList = new JList<>(algoModels);

    private final JTextArea algoDescTextArea = new JTextArea();
    private final JTextArea scoreDescTextArea = new JTextArea();
    private final JTextArea testDescTextArea = new JTextArea();

    private boolean updatingTestModels;
    private boolean updatingScoreModels;

    private final GeneralAlgorithmRunner algorithmRunner;
    private final DataType dataType;
    private final TetradDesktop desktop;
    private final boolean multiDataAlgo;

    public AlgorithmCard(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.dataType = getDataType(algorithmRunner);
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.multiDataAlgo = (algorithmRunner.getSourceGraph() == null)
                ? algorithmRunner.getDataModelList().size() > 1
                : false;

        initComponents();
        initListeners();

        resetAllSettings();
    }

    private void initComponents() {
        initDescriptionTextAreas();

        JButton resetSettingsBtn = new JButton("Reset All Settings");
        resetSettingsBtn.addActionListener(e -> {
            resetAllSettings();
        });

        JPanel westMainSouthPanel = new JPanel(new BorderLayout(0, 10));
        westMainSouthPanel.add(new TestAndScorePanel(), BorderLayout.CENTER);
        westMainSouthPanel.add(resetSettingsBtn, BorderLayout.SOUTH);

        JPanel westMainWestPanel = new JPanel(new BorderLayout(0, 10));
        westMainWestPanel.add(new AlgorithmFilterPanel(), BorderLayout.CENTER);
        westMainWestPanel.add(westMainSouthPanel, BorderLayout.SOUTH);

        JPanel westMainPanel = new JPanel(new BorderLayout(5, 0));
        westMainPanel.add(westMainWestPanel, BorderLayout.WEST);
        westMainPanel.add(new AlgorithmListPanel(), BorderLayout.EAST);

        JPanel testAndScoreDescPanel = new JPanel();
        testAndScoreDescPanel.setLayout(new BoxLayout(testAndScoreDescPanel, BoxLayout.Y_AXIS));
        testAndScoreDescPanel.add(new DescriptionPanel("Test Description", testDescTextArea));
        testAndScoreDescPanel.add(Box.createVerticalStrut(10));
        testAndScoreDescPanel.add(new DescriptionPanel("Score Description", scoreDescTextArea));

        JPanel centerMainPanel = new JPanel(new BorderLayout(0, 10));
        centerMainPanel.add(new DescriptionPanel("Algorithm Description", algoDescTextArea), BorderLayout.CENTER);
        centerMainPanel.add(testAndScoreDescPanel, BorderLayout.SOUTH);
        centerMainPanel.setPreferredSize(new Dimension(235, 200));

        setLayout(new BorderLayout(10, 0));
        add(westMainPanel, BorderLayout.WEST);
        add(centerMainPanel, BorderLayout.CENTER);

        setPreferredSize(new Dimension(800, 506));
    }

    private void initListeners() {
        knowledgeChkBox.addActionListener(e -> {
            refreshAlgorithmList();
        });
        linearVarChkBox.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        gaussianVarChkBox.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        algorithmList.addListSelectionListener(e -> {
            if (!(e.getValueIsAdjusting() || algorithmList.isSelectionEmpty())) {
                setAlgorithmDescription();
                refreshTestAndScoreList();
                validateAlgorithmOption();
            }
        });
        indTestComboBox.addActionListener(e -> {
            if (!updatingTestModels && indTestComboBox.getSelectedIndex() >= 0) {
                setIndepTestDescription();

                AlgorithmModel algoModel = algorithmList.getSelectedValue();
                Map<DataType, IndependenceTestModel> map = defaultIndTestModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    defaultIndTestModels.put(algoModel, map);
                }
                map.put(dataType, indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex()));
            }
        });
        scoreComboBox.addActionListener(e -> {
            if (!updatingScoreModels && scoreComboBox.getSelectedIndex() >= 0) {
                setScoreDescription();

                AlgorithmModel algoModel = algorithmList.getSelectedValue();
                Map<DataType, ScoreModel> map = defaultScoreModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    defaultScoreModels.put(algoModel, map);
                }
                map.put(dataType, scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex()));
            }
        });
    }

    private void initDescriptionTextAreas() {
        algoDescTextArea.setWrapStyleWord(true);
        algoDescTextArea.setLineWrap(true);
        algoDescTextArea.setEditable(false);

        scoreDescTextArea.setWrapStyleWord(true);
        scoreDescTextArea.setLineWrap(true);
        scoreDescTextArea.setEditable(false);
        scoreDescTextArea.setRows(6);

        testDescTextArea.setWrapStyleWord(true);
        testDescTextArea.setLineWrap(true);
        testDescTextArea.setEditable(false);
        testDescTextArea.setRows(6);
    }

    private DataType getDataType(final GeneralAlgorithmRunner algorithmRunner) {
        DataModelList dataModelList = algorithmRunner.getDataModelList();
        if (dataModelList.containsEmptyData()) {
            if (algorithmRunner.getSourceGraph() == null) {
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

    public AlgorithmModel getSelectedAlgorithm() {
        return algorithmList.getSelectedValue();
    }

    public IndependenceTestModel getSelectedIndependenceTest() {
        if (indTestComboBox.isEnabled()) {
            return indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex());
        }

        return null;
    }

    public ScoreModel getSelectedScore() {
        if (scoreComboBox.isEnabled()) {
            scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex());
        }

        return null;
    }

    private void rememberUserAlgoSelections(Map<String, Object> userAlgoSelections) {
        userAlgoSelections.put(IND_TEST_PARAM, indTestComboBox.getSelectedItem());
        userAlgoSelections.put(SCORE_PARAM, scoreComboBox.getSelectedItem());
        userAlgoSelections.put(ALGO_TYPE_PARAM, algoFilterBtnGrp.getSelection().getActionCommand());
        userAlgoSelections.put(LINEAR_PARAM, linearVarChkBox.isSelected());
        userAlgoSelections.put(GAUSSIAN_PARAM, gaussianVarChkBox.isSelected());
        userAlgoSelections.put(KNOWLEDGE_PARAM, knowledgeChkBox.isSelected());

        // When there's a search result, we store the algo string name from the search so we wont' lose it
        // when the upstream nodes change.
        // Otherwise, we use the one that users selcted on the UI - Zhou
        if (algorithmRunner.getGraphs() != null && algorithmRunner.getGraphs().size() > 0) {
            userAlgoSelections.put(ALGO_PARAM, algorithmRunner.getAlgorithm().getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name());
        } else {
            userAlgoSelections.put(ALGO_PARAM, algorithmList.getSelectedValue().toString());
        }
    }

    /**
     * This restore mechanism won't restore user selections other than selected
     * algo name when user changes the upstream (after clicking the "Execute"
     * button), because a new algo algorithmRunner is created and we lose the
     * stored models from the old algorithmRunner - Zhou
     *
     * @param models
     */
    private void restoreUserAlgoSelections(Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(LINEAR_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            linearVarChkBox.setSelected((Boolean) obj);
        }
        obj = userAlgoSelections.get(GAUSSIAN_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            gaussianVarChkBox.setSelected((Boolean) obj);
        }
        obj = userAlgoSelections.get(KNOWLEDGE_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            knowledgeChkBox.setSelected((Boolean) obj);
        }
        obj = userAlgoSelections.get(ALGO_TYPE_PARAM);
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

        // Restore the algo name from search when there's a search result.
        // Otherwise use the stored name form algorithmRunner.getModels(), which will be lost when the upstream nodes change - Zhou
        String selectedAlgoName = null;
        if (algorithmRunner.getGraphs() != null && algorithmRunner.getGraphs().size() > 0) {
            selectedAlgoName = algorithmRunner.getAlgorithm().getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
        } else {
            obj = userAlgoSelections.get(ALGO_PARAM);
            if ((obj != null) && (obj instanceof String)) {
                selectedAlgoName = (String) obj;
            }
        }

        Enumeration<AlgorithmModel> enums = algoModels.elements();
        while (enums.hasMoreElements()) {
            AlgorithmModel model = enums.nextElement();
            if (model.toString().equals(selectedAlgoName)) {
                algorithmList.setSelectedValue(model, true);
                break;
            }
        }

        obj = userAlgoSelections.get(IND_TEST_PARAM);
        if ((obj != null) && (obj instanceof IndependenceTestModel)) {
            String value = ((IndependenceTestModel) obj).toString();
            ComboBoxModel<IndependenceTestModel> comboBoxModels = indTestComboBox.getModel();
            int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                IndependenceTestModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    userAlgoSelections.put(IND_TEST_PARAM, model);
                    indTestComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }

        obj = userAlgoSelections.get(SCORE_PARAM);
        if ((obj != null) && (obj instanceof ScoreModel)) {
            String value = ((ScoreModel) obj).toString();
            ComboBoxModel<ScoreModel> comboBoxModels = scoreComboBox.getModel();
            int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                ScoreModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    userAlgoSelections.put(SCORE_PARAM, model);
                    scoreComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }
    }

    public void refresh() {
        restoreUserAlgoSelections(algorithmRunner.getUserAlgoSelections());
    }

    public void saveStates() {
        rememberUserAlgoSelections(algorithmRunner.getUserAlgoSelections());
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
        if (algorithm != null && algorithm instanceof TakesInitialGraph && algorithmRunner.getSourceGraph() != null && !algorithmRunner.getDataModelList().isEmpty()) {
            Algorithm initialGraph = new SingleGraphAlg(algorithmRunner.getSourceGraph());
            ((TakesInitialGraph) algorithm).setInitialGraph(initialGraph);
        }

        return algorithm;
    }

    public boolean isAllValid() {
        AlgorithmModel algoModel = algorithmList.getSelectedValue();
        IndependenceTestModel indTestModel = indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex());
        ScoreModel scoreModel = scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex());

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
            algorithmRunner.setAlgorithm(getAlgorithmFromInterface(algoModel, indTestModel, scoreModel));

            return true;
        }
    }

    private void validateAlgorithmOption() {
        firePropertyChange("algoFwdBtn", null, true);

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

            firePropertyChange("algoFwdBtn", null, false);
            JOptionPane.showMessageDialog(desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Check if initial graph is provided for those pairwise algorithms
            if (TakesInitialGraph.class.isAssignableFrom(algoClass)) {
                if (algorithmRunner.getSourceGraph() == null || algorithmRunner.getDataModelList().isEmpty()) {
                    try {
                        Object algo = algoClass.newInstance();
                        Method m = algoClass.getDeclaredMethod("setInitialGraph", Algorithm.class);
                        m.setAccessible(true);
                        try {
                            Algorithm algorithm = null;
                            m.invoke(algo, algorithm);
                        } catch (InvocationTargetException | IllegalArgumentException exception) {
                            firePropertyChange("algoFwdBtn", null, false);
                            JOptionPane.showMessageDialog(desktop, exception.getCause().getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (IllegalAccessException | InstantiationException | NoSuchMethodException exception) {
                        LOGGER.error("", exception);
                    }
                }
            }

            // Time-series (TsFci, TsGfci, TsImages) algorithms need lagged data
            String cmd = algoModel.getAlgorithm().getAnnotation().command();
            if (cmd.equalsIgnoreCase("ts-fci")
                    || cmd.equalsIgnoreCase("ts-gfci")
                    || cmd.equalsIgnoreCase("ts-imgs")) {
                DataModel dataModel = algorithmRunner.getDataModel();
                IKnowledge knowledge = algorithmRunner.getKnowledge();
                if ((knowledge == null || knowledge.isEmpty())
                        && (dataModel.getKnowledge() == null || dataModel.getKnowledge().isEmpty())) {
                    firePropertyChange("algoFwdBtn", null, false);
                    JOptionPane.showMessageDialog(desktop, "Time-series algorithm needs lagged data", "Please Note", JOptionPane.INFORMATION_MESSAGE);
                }
            }
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
                    algorithmModels.getModels(dataType, multiDataAlgo).stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> algoModels.addElement(e));
                } else {
                    algorithmModels.getModels(dataType, multiDataAlgo).stream()
                            .forEach(e -> algoModels.addElement(e));
                }
            } else {
                if (knowledgeChkBox.isSelected()) {
                    algorithmModels.getModels(AlgType.valueOf(algoType), dataType, multiDataAlgo).stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> algoModels.addElement(e));
                } else {
                    algorithmModels.getModels(AlgType.valueOf(algoType), dataType, multiDataAlgo).stream()
                            .forEach(e -> algoModels.addElement(e));
                }
            }

            if (algoModels.isEmpty()) {
                algoDescTextArea.setText("");
                firePropertyChange("algoFwdBtn", null, false);

            } else {
                algorithmList.setSelectedIndex(0);
                firePropertyChange("algoFwdBtn", null, true);
            }
        }
        scoreComboBox.setEnabled(scoreComboBox.getItemCount() > 0);
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
                models.forEach(e -> indTestComboBox.addItem(e));
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

        if (indTestComboBox.getSelectedIndex() == -1) {
            testDescTextArea.setText("");
        }
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
                models.forEach(e -> scoreModels.add(e));
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

        if (scoreComboBox.getSelectedIndex() == -1) {
            scoreDescTextArea.setText("");
        }
    }

    private void refreshTestAndScoreList() {
        refreshTestList();
        refreshScoreList();
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

    private void setAlgorithmDescription() {
        AlgorithmModel model = algorithmList.getSelectedValue();
        if (model == null) {
            algoDescTextArea.setText("");
        } else {
            algoDescTextArea.setText(model.getDescription());
            algoDescTextArea.setCaretPosition(0);
        }
    }

    private void setScoreDescription() {
        ScoreModel model = scoreComboBox.getItemAt(scoreComboBox.getSelectedIndex());
        if (model == null) {
            scoreDescTextArea.setText("");
        } else {
            scoreDescTextArea.setText(model.getDescription());
            scoreDescTextArea.setCaretPosition(0);
        }
    }

    private void setIndepTestDescription() {
        IndependenceTestModel model = indTestComboBox.getItemAt(indTestComboBox.getSelectedIndex());
        if (model == null) {
            testDescTextArea.setText("");
        } else {
            testDescTextArea.setText(model.getDescription());
            testDescTextArea.setCaretPosition(0);
        }
    }

    private class DescriptionPanel extends JPanel {

        private static final long serialVersionUID = 2329356999486712496L;

        final String borderTitle;
        final Component view;

        public DescriptionPanel(String borderTitle, Component view) {
            this.borderTitle = borderTitle;
            this.view = view;

            initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(view);

            setBorder(BorderFactory.createTitledBorder(borderTitle));
            setPreferredSize(new Dimension(235, 150));

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

    private class AlgorithmListPanel extends JPanel {

        private static final long serialVersionUID = -7068543172769683902L;

        public AlgorithmListPanel() {
            initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(algorithmList);

            setBorder(BorderFactory.createTitledBorder("Choose Algorithm"));

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

    private class AlgorithmFilterPanel extends JPanel {

        private static final long serialVersionUID = -3120503093689632462L;

        public AlgorithmFilterPanel() {
            populateAlgoTypeOptions();
            initComponents();
        }

        private void initComponents() {
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

            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Algorithm Filters"));
            add(new PaddingPanel(algoFiltersBox), BorderLayout.CENTER);
        }

        /**
         * Create new radio buttons and add them to both the radio button list
         * and radio button group.
         *
         * @param radioButtons
         */
        private void populateAlgoTypeOptions() {
            JRadioButton showAllRadBtn = new JRadioButton("show all");
            showAllRadBtn.setActionCommand("all");
            showAllRadBtn.addActionListener(e -> {
                refreshAlgorithmList();
            });
            algoTypeOpts.add(showAllRadBtn);
            algoFilterBtnGrp.add(showAllRadBtn);

            Arrays.stream(AlgType.values()).forEach(item -> {
                String name = item.name();

                JRadioButton radioButton = new JRadioButton(name.replace("_", " "));
                radioButton.setActionCommand(name);
                radioButton.addActionListener(e -> {
                    refreshAlgorithmList();
                });

                algoTypeOpts.add(radioButton);
                algoFilterBtnGrp.add(radioButton);
            });
        }

    }

    private class TestAndScorePanel extends JPanel {

        private static final long serialVersionUID = -1594897454478052884L;

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

}
