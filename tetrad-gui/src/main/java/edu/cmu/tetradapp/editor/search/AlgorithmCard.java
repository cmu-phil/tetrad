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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.*;
import edu.cmu.tetradapp.util.DesktopController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

/**
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
    private final String DATASET_FILTER = "dataset_filter";
    private final String KNOWLEDGE_PARAM = "knowledge";

    private final List<JRadioButton> algoTypeOpts = new ArrayList<>();

    private final DefaultListModel<AlgorithmModel> algoModels = new DefaultListModel<>();

    private final ButtonGroup algoFilterBtnGrp = new ButtonGroup();
    private final ButtonGroup datasetFilterBtnGrp = new ButtonGroup();

    private final Map<AlgorithmModel, Map<DataType, IndependenceTestModel>> defaultIndTestModels = new HashMap<>();
    private final Map<AlgorithmModel, Map<DataType, ScoreModel>> defaultScoreModels = new HashMap<>();
    private final JCheckBox knowledgeChkBox = new JCheckBox("accepts knowledge");
    private final JRadioButton linearGaussianRadBtn = new JRadioButton("Linear, Gaussian");
    private final JRadioButton mixedRadBtn = new JRadioButton("Mixed Discrete/Gaussian");
    private final JRadioButton generalRadBtn = new JRadioButton("General");
    private final JRadioButton allRadBtn = new JRadioButton("All");
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    private final JComboBox<ScoreModel> scoreComboBox = new JComboBox<>();
    private final JList<AlgorithmModel> algorithmList = new JList<>(this.algoModels);

    private final JTextArea algoDescTextArea = new JTextArea();
    private final JTextArea scoreDescTextArea = new JTextArea();
    private final JTextArea testDescTextArea = new JTextArea();
    private final GeneralAlgorithmRunner algorithmRunner;
    private final DataType dataType;
    private final TetradDesktop desktop;
    private final boolean multiDataAlgo;
    private boolean updatingTestModels;
    private boolean updatingScoreModels;

    public AlgorithmCard(final GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.dataType = getDataType(algorithmRunner);
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.multiDataAlgo = algorithmRunner.getSourceGraph() == null && algorithmRunner.getDataModelList().size() > 1;

        initComponents();
        initListeners();

        resetAllSettings();

        this.algorithmList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void initComponents() {
        initDescriptionTextAreas();

        final JButton resetSettingsBtn = new JButton("Reset All Settings");
        resetSettingsBtn.addActionListener(e -> {
            resetAllSettings();
        });

        final JPanel westMainSouthPanel = new JPanel(new BorderLayout(0, 10));
        westMainSouthPanel.add(new TestAndScorePanel(), BorderLayout.CENTER);
        westMainSouthPanel.add(resetSettingsBtn, BorderLayout.SOUTH);

        final JPanel westMainWestPanel = new JPanel(new BorderLayout(0, 10));
        westMainWestPanel.add(new AlgorithmFilterPanel(), BorderLayout.CENTER);
        westMainWestPanel.add(westMainSouthPanel, BorderLayout.SOUTH);

        final JPanel westMainPanel = new JPanel(new BorderLayout(5, 0));
        westMainPanel.add(westMainWestPanel, BorderLayout.WEST);
        westMainPanel.add(new AlgorithmListPanel(), BorderLayout.EAST);

        final JPanel testAndScoreDescPanel = new JPanel();
        testAndScoreDescPanel.setLayout(new BoxLayout(testAndScoreDescPanel, BoxLayout.Y_AXIS));
        testAndScoreDescPanel.add(new DescriptionPanel("Test Description", this.testDescTextArea));
        testAndScoreDescPanel.add(Box.createVerticalStrut(10));
        testAndScoreDescPanel.add(new DescriptionPanel("Score Description", this.scoreDescTextArea));

        final JPanel centerMainPanel = new JPanel(new BorderLayout(0, 10));
        centerMainPanel.add(new DescriptionPanel("Algorithm Description", this.algoDescTextArea), BorderLayout.CENTER);
        centerMainPanel.add(testAndScoreDescPanel, BorderLayout.SOUTH);
        centerMainPanel.setPreferredSize(new Dimension(235, 200));

        setLayout(new BorderLayout(10, 0));
        add(westMainPanel, BorderLayout.WEST);
        add(centerMainPanel, BorderLayout.CENTER);

        if (this.algorithmRunner.hasMissingValues()) {
            setPreferredSize(new Dimension(308, 291));
        } else {
            setPreferredSize(new Dimension(308, 241));
        }
    }

    private void initListeners() {
        this.knowledgeChkBox.addActionListener(e -> {
            refreshAlgorithmList();
        });
        this.linearGaussianRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        this.mixedRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        this.generalRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        this.allRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
        });
        this.algorithmList.addListSelectionListener(e -> {
            if (!(e.getValueIsAdjusting() || this.algorithmList.isSelectionEmpty())) {
                setAlgorithmDescription();
                refreshTestAndScoreList();
                validateAlgorithmOption();
            }
        });
        this.indTestComboBox.addActionListener(e -> {
            if (!this.updatingTestModels && this.indTestComboBox.getSelectedIndex() >= 0) {
                setIndepTestDescription();

                final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
                Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultIndTestModels.put(algoModel, map);
                }
                map.put(this.dataType, this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex()));
            }
        });
        this.scoreComboBox.addActionListener(e -> {
            if (!this.updatingScoreModels && this.scoreComboBox.getSelectedIndex() >= 0) {
                setScoreDescription();

                final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
                Map<DataType, ScoreModel> map = this.defaultScoreModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultScoreModels.put(algoModel, map);
                }
                map.put(this.dataType, this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex()));
            }
        });
    }

    private void initDescriptionTextAreas() {
        this.algoDescTextArea.setWrapStyleWord(true);
        this.algoDescTextArea.setLineWrap(true);
        this.algoDescTextArea.setEditable(false);

        this.scoreDescTextArea.setWrapStyleWord(true);
        this.scoreDescTextArea.setLineWrap(true);
        this.scoreDescTextArea.setEditable(false);
        this.scoreDescTextArea.setRows(6);

        this.testDescTextArea.setWrapStyleWord(true);
        this.testDescTextArea.setLineWrap(true);
        this.testDescTextArea.setEditable(false);
        this.testDescTextArea.setRows(6);
    }

    private DataType getDataType(final GeneralAlgorithmRunner algorithmRunner) {
        final DataModelList dataModelList = algorithmRunner.getDataModelList();
        if (dataModelList.containsEmptyData()) {
            if (algorithmRunner.getSourceGraph() == null) {
                return null;
            } else {
                return DataType.Graph;
            }
        } else {
            final DataModel dataSet = dataModelList.get(0);
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
        return this.algorithmList.getSelectedValue();
    }

    public IndependenceTestModel getSelectedIndependenceTest() {
        if (this.indTestComboBox.isEnabled()) {
            return this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
        }

        return null;
    }

    public ScoreModel getSelectedScore() {
        if (this.scoreComboBox.isEnabled()) {
            this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());
        }

        return null;
    }

    private void rememberUserAlgoSelections(final Map<String, Object> userAlgoSelections) {
        userAlgoSelections.put(this.IND_TEST_PARAM, this.indTestComboBox.getSelectedItem());
        userAlgoSelections.put(this.SCORE_PARAM, this.scoreComboBox.getSelectedItem());
        userAlgoSelections.put(this.ALGO_TYPE_PARAM, this.algoFilterBtnGrp.getSelection().getActionCommand());
        userAlgoSelections.put(this.DATASET_FILTER, this.datasetFilterBtnGrp.getSelection().getActionCommand());
        userAlgoSelections.put(this.KNOWLEDGE_PARAM, this.knowledgeChkBox.isSelected());

        // When there's a search result, we store the algo string name from the search so we wont' lose it
        // when the upstream nodes change.
        // Otherwise, we use the one that users selcted on the UI - Zhou
        if (this.algorithmRunner.getGraphs() != null && !this.algorithmRunner.getGraphs().isEmpty()) {
            userAlgoSelections.put(this.ALGO_PARAM, this.algorithmRunner.getAlgorithm().getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name());
        } else {
            userAlgoSelections.put(this.ALGO_PARAM, this.algorithmList.getSelectedValue().toString());
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
    private void restoreUserAlgoSelections(final Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(this.DATASET_FILTER);
        if ((obj != null) && (obj instanceof String)) {
            final String actCmd = String.valueOf(obj);
            for (final Enumeration<AbstractButton> e = this.datasetFilterBtnGrp.getElements(); e.hasMoreElements(); ) {
                final JRadioButton radBtn = (JRadioButton) e.nextElement();
                if (radBtn.getActionCommand().equals(actCmd)) {
                    radBtn.setSelected(true);
                    break;
                }
            }
        }

        obj = userAlgoSelections.get(this.KNOWLEDGE_PARAM);
        if ((obj != null) && (obj instanceof Boolean)) {
            this.knowledgeChkBox.setSelected((Boolean) obj);
        }
        obj = userAlgoSelections.get(this.ALGO_TYPE_PARAM);
        if ((obj != null) && (obj instanceof String)) {
            final String actCmd = String.valueOf(obj);
            final Optional<JRadioButton> opt = this.algoTypeOpts.stream()
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
        if (this.algorithmRunner.getGraphs() != null && this.algorithmRunner.getGraphs().size() > 0) {
            selectedAlgoName = this.algorithmRunner.getAlgorithm().getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class).name();
        } else {
            obj = userAlgoSelections.get(this.ALGO_PARAM);
            if ((obj != null) && (obj instanceof String)) {
                selectedAlgoName = (String) obj;
            }
        }

        final Enumeration<AlgorithmModel> enums = this.algoModels.elements();
        while (enums.hasMoreElements()) {
            final AlgorithmModel model = enums.nextElement();
            if (model.toString().equals(selectedAlgoName)) {
                this.algorithmList.setSelectedValue(model, true);
                break;
            }
        }

        obj = userAlgoSelections.get(this.IND_TEST_PARAM);
        if ((obj != null) && (obj instanceof IndependenceTestModel)) {
            final String value = obj.toString();
            final ComboBoxModel<IndependenceTestModel> comboBoxModels = this.indTestComboBox.getModel();
            final int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                final IndependenceTestModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    userAlgoSelections.put(this.IND_TEST_PARAM, model);
                    this.indTestComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }

        obj = userAlgoSelections.get(this.SCORE_PARAM);
        if ((obj != null) && (obj instanceof ScoreModel)) {
            final String value = obj.toString();
            final ComboBoxModel<ScoreModel> comboBoxModels = this.scoreComboBox.getModel();
            final int size = comboBoxModels.getSize();
            for (int i = 0; i < size; i++) {
                final ScoreModel model = comboBoxModels.getElementAt(i);
                if (model.toString().equals(value)) {
                    userAlgoSelections.put(this.SCORE_PARAM, model);
                    this.scoreComboBox.getModel().setSelectedItem(model);
                    break;
                }
            }
        }
    }

    public void refresh() {
        restoreUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    public void saveStates() {
        rememberUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    /**
     * Initialize algorithm
     *
     * @param algoModel
     * @param indTestModel
     * @param scoreModel
     * @return Algorithm
     */
    public Algorithm getAlgorithmFromInterface(final AlgorithmModel algoModel, final IndependenceTestModel indTestModel, final ScoreModel scoreModel) {
        final Class algoClass = algoModel.getAlgorithm().getClazz();
        final Class indTestClass = (indTestModel == null) ? null : indTestModel.getIndependenceTest().getClazz();
        final Class scoreClass = (scoreModel == null) ? null : scoreModel.getScore().getClazz();

        Algorithm algorithm = null;

        try {
            algorithm = AlgorithmFactory.create(algoClass, indTestClass, scoreClass);
        } catch (final IllegalAccessException | InstantiationException exception) {
            AlgorithmCard.LOGGER.error("", exception);
        }

        // Those pairwise algos (R3, RShew, Skew..) require source graph to initialize - Zhou
        if (algorithm != null && algorithm instanceof TakesExternalGraph && this.algorithmRunner.getSourceGraph() != null && !this.algorithmRunner.getDataModelList().isEmpty()) {
            final Algorithm externalGraph = new SingleGraphAlg(this.algorithmRunner.getSourceGraph());
            ((TakesExternalGraph) algorithm).setExternalGraph(externalGraph);
        }

        return algorithm;
    }

    public boolean isAllValid() {
        final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        final IndependenceTestModel indTestModel = this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
        final ScoreModel scoreModel = this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());

        final boolean missingTest = algoModel.isRequiredTest() && (indTestModel == null);
        final boolean missingScore = algoModel.isRequiredScore() && (scoreModel == null);
        if (missingTest && missingScore) {
            final String msg = String.format("%s requires both test and score.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingTest) {
            final String msg = String.format("%s requires independence test.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingScore) {
            final String msg = String.format("%s requires score.",
                    algoModel.getAlgorithm().getAnnotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else {
            this.algorithmRunner.setAlgorithm(getAlgorithmFromInterface(algoModel, indTestModel, scoreModel));

            return true;
        }
    }

    private void validateAlgorithmOption() {
        firePropertyChange("algoFwdBtn", null, true);

        final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        final Class algoClass = algoModel.getAlgorithm().getClazz();

        if (algoClass.isAnnotationPresent(Nonexecutable.class)) {
            String msg;
            try {
                final Object algo = algoClass.newInstance();
                final Method m = algoClass.getDeclaredMethod("getDescription");
                m.setAccessible(true);
                try {
                    msg = String.valueOf(m.invoke(algo));
                } catch (final InvocationTargetException exception) {
                    msg = "";
                }

            } catch (final IllegalAccessException | InstantiationException | NoSuchMethodException exception) {
                AlgorithmCard.LOGGER.error("", exception);
                msg = "";
            }

            firePropertyChange("algoFwdBtn", null, false);
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Check if initial graph is provided for those pairwise algorithms
            if (TakesExternalGraph.class.isAssignableFrom(algoClass)) {
                if (this.algorithmRunner.getSourceGraph() == null || this.algorithmRunner.getDataModelList().isEmpty()) {
                    try {
                        final Object algo = algoClass.newInstance();
                        final Method m = algoClass.getDeclaredMethod("setExternalGraph", Algorithm.class);
                        m.setAccessible(true);
                        try {
                            final Algorithm algorithm = null;
                            m.invoke(algo, algorithm);
                        } catch (final InvocationTargetException | IllegalArgumentException exception) {
                            firePropertyChange("algoFwdBtn", null, false);
                            JOptionPane.showMessageDialog(this.desktop, exception.getCause().getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (final IllegalAccessException | InstantiationException | NoSuchMethodException exception) {
                        AlgorithmCard.LOGGER.error("", exception);
                    }
                }
            }

            // SVAR (SvarFci, SvarGfci) algorithms need lagged data
            final String cmd = algoModel.getAlgorithm().getAnnotation().command();
            if (cmd.equalsIgnoreCase("ts-fci")
                    || cmd.equalsIgnoreCase("ts-gfci")
                    || cmd.equalsIgnoreCase("ts-imgs")) {
                final DataModel dataModel = this.algorithmRunner.getDataModel();
                final IKnowledge knowledge = this.algorithmRunner.getKnowledge();
                if ((knowledge == null || knowledge.isEmpty())
                        && (dataModel.getKnowledge() == null || dataModel.getKnowledge().isEmpty())) {
                    firePropertyChange("algoFwdBtn", null, false);
                    JOptionPane.showMessageDialog(this.desktop, "Time-series algorithm needs lagged data", "Please Note", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }

    }

    private void refreshAlgorithmList() {
        this.algoModels.clear();

        final ButtonModel selectedAlgoType = this.algoFilterBtnGrp.getSelection();
        if (selectedAlgoType != null) {
            final AlgorithmModels algorithmModels = AlgorithmModels.getInstance();
            final String algoType = selectedAlgoType.getActionCommand();
            if ("all".equals(algoType)) {
                if (this.knowledgeChkBox.isSelected()) {
                    algorithmModels.getModels(this.dataType, this.multiDataAlgo).stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> this.algoModels.addElement(e));
                } else {
                    algorithmModels.getModels(this.dataType, this.multiDataAlgo).stream()
                            .forEach(e -> this.algoModels.addElement(e));
                }
            } else {
                if (this.knowledgeChkBox.isSelected()) {
                    algorithmModels.getModels(AlgType.valueOf(algoType), this.dataType, this.multiDataAlgo).stream()
                            .filter(e -> HasKnowledge.class.isAssignableFrom(e.getAlgorithm().getClazz()))
                            .forEach(e -> this.algoModels.addElement(e));
                } else {
                    algorithmModels.getModels(AlgType.valueOf(algoType), this.dataType, this.multiDataAlgo).stream()
                            .forEach(e -> this.algoModels.addElement(e));
                }
            }

            if (this.algoModels.isEmpty()) {
                this.algoDescTextArea.setText("");
                firePropertyChange("algoFwdBtn", null, false);

            } else {
                this.algorithmList.setSelectedIndex(0);
                firePropertyChange("algoFwdBtn", null, true);
            }
        }
        this.scoreComboBox.setEnabled(this.scoreComboBox.getItemCount() > 0);
    }

    private void refreshTestList() {
        this.updatingTestModels = true;
        this.indTestComboBox.removeAllItems();
        final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredTest()) {
            final List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(this.dataType);
            if (this.linearGaussianRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(LinearGaussian.class))
                        .forEach(e -> this.indTestComboBox.addItem(e));
            } else if (this.mixedRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(Mixed.class))
                        .forEach(e -> this.indTestComboBox.addItem(e));
            } else if (this.generalRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getIndependenceTest().getClazz().isAnnotationPresent(General.class))
                        .forEach(e -> this.indTestComboBox.addItem(e));
            } else if (this.allRadBtn.isSelected()) {
                models.stream()
                        .forEach(e -> this.indTestComboBox.addItem(e));
            }
        }
        this.updatingTestModels = false;
        if (this.indTestComboBox.getItemCount() > 0) {
            this.indTestComboBox.setEnabled(true);

            Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(algoModel);
            if (map == null) {
                map = new EnumMap<>(DataType.class);
                this.defaultIndTestModels.put(algoModel, map);
            }

            IndependenceTestModel testModel = map.get(this.dataType);
            if (testModel == null) {
                testModel = IndependenceTestModels.getInstance().getDefaultModel(this.dataType);
                if (testModel == null) {
                    testModel = this.indTestComboBox.getItemAt(0);
                }
            }
            this.indTestComboBox.setSelectedItem(testModel);
        } else {
            this.indTestComboBox.setEnabled(false);
        }

        if (this.indTestComboBox.getSelectedIndex() == -1) {
            this.testDescTextArea.setText("");
        }
    }

    private void refreshScoreList() {
        this.updatingScoreModels = true;
        this.scoreComboBox.removeAllItems();
        final AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredScore()) {
            final List<ScoreModel> models = ScoreModels.getInstance().getModels(this.dataType);
            if (this.linearGaussianRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(LinearGaussian.class))
                        .forEach(e -> this.scoreComboBox.addItem(e));
            } else if (this.mixedRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(Mixed.class))
                        .forEach(e -> this.scoreComboBox.addItem(e));
            } else if (this.generalRadBtn.isSelected()) {
                models.stream()
                        .filter(e -> e.getScore().getClazz().isAnnotationPresent(General.class))
                        .forEach(e -> this.scoreComboBox.addItem(e));
            } else if (this.allRadBtn.isSelected()) {
                models.stream()
                        .forEach(e -> this.scoreComboBox.addItem(e));
            }
        }
        this.updatingScoreModels = false;
        if (this.scoreComboBox.getItemCount() > 0) {
            this.scoreComboBox.setEnabled(true);

            Map<DataType, ScoreModel> map = this.defaultScoreModels.get(algoModel);
            if (map == null) {
                map = new EnumMap<>(DataType.class);
                this.defaultScoreModels.put(algoModel, map);
            }

            ScoreModel scoreModel = map.get(this.dataType);
            if (scoreModel == null) {
                scoreModel = ScoreModels.getInstance().getDefaultModel(this.dataType);
                if (scoreModel == null) {
                    scoreModel = this.scoreComboBox.getItemAt(0);
                }
            }
            this.scoreComboBox.setSelectedItem(scoreModel);
        } else {
            this.scoreComboBox.setEnabled(false);
        }

        if (this.scoreComboBox.getSelectedIndex() == -1) {
            this.scoreDescTextArea.setText("");
        }
    }

    private void refreshTestAndScoreList() {
        refreshTestList();
        refreshScoreList();
    }

    private void resetAllSettings() {
        // clear cache
        this.defaultIndTestModels.clear();
        this.defaultScoreModels.clear();

        // uncheck all checkboxes
        this.datasetFilterBtnGrp.setSelected(this.allRadBtn.getModel(), true);
        this.knowledgeChkBox.setSelected(false);

        if (!this.algoTypeOpts.isEmpty()) {
            this.algoTypeOpts.get(0).setSelected(true);
        }
        refreshAlgorithmList();
        refreshTestList();
        refreshScoreList();
    }

    private void setAlgorithmDescription() {
        final AlgorithmModel model = this.algorithmList.getSelectedValue();
        if (model == null) {
            this.algoDescTextArea.setText("");
        } else {
            this.algoDescTextArea.setText(model.getDescription());
            this.algoDescTextArea.setCaretPosition(0);
        }
    }

    private void setScoreDescription() {
        final ScoreModel model = this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());
        if (model == null) {
            this.scoreDescTextArea.setText("");
        } else {
            this.scoreDescTextArea.setText(model.getDescription());
            this.scoreDescTextArea.setCaretPosition(0);
        }
    }

    private void setIndepTestDescription() {
        final IndependenceTestModel model = this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
        if (model == null) {
            this.testDescTextArea.setText("");
        } else {
            this.testDescTextArea.setText(model.getDescription());
            this.testDescTextArea.setCaretPosition(0);
        }
    }

    private class DescriptionPanel extends JPanel {

        private static final long serialVersionUID = 2329356999486712496L;

        final String borderTitle;
        final Component view;

        public DescriptionPanel(final String borderTitle, final Component view) {
            this.borderTitle = borderTitle;
            this.view = view;

            initComponents();
        }

        private void initComponents() {
            final JScrollPane scrollPane = new JScrollPane(this.view);

            setBorder(BorderFactory.createTitledBorder(this.borderTitle));
            setPreferredSize(new Dimension(235, 150));

            final GroupLayout layout = new GroupLayout(this);
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
            this.initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(algorithmList);

            this.setBorder(BorderFactory.createTitledBorder("Choose Algorithm"));

            GroupLayout layout = new GroupLayout(this);
            setLayout(layout);
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
            this.populateAlgoTypeOptions();
            this.initComponents();
        }

        private void initComponents() {
            // Filter based on algo types dropdown
            Box algoTypesBox = Box.createVerticalBox();

            // Algo types label box
            Box algTypesBoxLabelBox = Box.createHorizontalBox();
            algTypesBoxLabelBox.add(new JLabel("Show algorithms that: "));
            algTypesBoxLabelBox.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Add label to containing box
            algoTypesBox.add(algTypesBoxLabelBox);

            // All option
            Box algoTypeOptionAllBox = Box.createHorizontalBox();
            algoTypeOptionAllBox.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Add all option to containing box
            algoTypesBox.add(algoTypeOptionAllBox);

            // add radio buttons to panel
            if (!algoTypeOpts.isEmpty()) {
                Dimension indentSize = new Dimension(10, 20);
                algoTypeOpts.forEach(btn -> {
                    Box box = Box.createHorizontalBox();
                    box.setAlignmentX(Component.LEFT_ALIGNMENT);
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
            priorKnowledgeLabelBox.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Checkbox container
            Box priorKnowledgeOptionBox = Box.createHorizontalBox();
            priorKnowledgeOptionBox.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Add padding and option
            priorKnowledgeOptionBox.add(Box.createRigidArea(new Dimension(10, 20)));
            priorKnowledgeOptionBox.add(knowledgeChkBox);

            // Add to containg box
            priorKnowledgeBox.add(priorKnowledgeLabelBox);
            priorKnowledgeBox.add(priorKnowledgeOptionBox);

            Box algoFiltersBox = Box.createVerticalBox();
            algoFiltersBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            algoFiltersBox.add(algoTypesBox);
            algoFiltersBox.add(Box.createVerticalStrut(10));
            algoFiltersBox.add(priorKnowledgeBox);

            this.setLayout(new BorderLayout());
            this.setBorder(BorderFactory.createTitledBorder("Algorithm Filters"));
            this.add(new PaddingPanel(algoFiltersBox), BorderLayout.CENTER);
        }

        /**
         * Create new radio buttons and add them to both the radio button list
         * and radio button group.
         */
        private void populateAlgoTypeOptions() {
            JRadioButton showAllRadBtn = new JRadioButton("show all");
            showAllRadBtn.setActionCommand("all");
            showAllRadBtn.addActionListener(e -> {
                AlgorithmCard.this.refreshAlgorithmList();
            });
            algoTypeOpts.add(showAllRadBtn);
            algoFilterBtnGrp.add(showAllRadBtn);

            Arrays.stream(AlgType.values()).forEach(item -> {
                String name = item.name();

                JRadioButton radioButton = new JRadioButton(name.replace("_", " "));
                radioButton.setActionCommand(name);
                radioButton.addActionListener(e -> {
                    AlgorithmCard.this.refreshAlgorithmList();
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
            this.initComponents();
        }

        private void initComponents() {
            linearGaussianRadBtn.setActionCommand("linear-gaussian");
            mixedRadBtn.setActionCommand("mixed");
            generalRadBtn.setActionCommand("general");
            allRadBtn.setActionCommand("all");

            datasetFilterBtnGrp.add(linearGaussianRadBtn);
            datasetFilterBtnGrp.add(mixedRadBtn);
            datasetFilterBtnGrp.add(generalRadBtn);
            datasetFilterBtnGrp.add(allRadBtn);

            datasetFilterBtnGrp.setSelected(allRadBtn.getModel(), true);

            assumptionsLabel = new JLabel();
            testLabel = new JLabel();
            scoreLabel = new JLabel();

            this.setBorder(BorderFactory.createTitledBorder("Choose Statistical Test and Score"));

            assumptionsLabel.setText("Filter by dataset properties:");

            testLabel.setText("Test:");
            scoreLabel.setText("Score:");

            if (algorithmRunner.hasMissingValues()) {
                JLabel missingValueAlert = new JLabel();
                JLabel testwiseDeletionAlert = new JLabel();

                Color red = new Color(255, 0, 0);
                missingValueAlert.setForeground(red);
                missingValueAlert.setText("Dataset contains missing values;");

                testwiseDeletionAlert.setForeground(red);
                testwiseDeletionAlert.setText("testwise deletion will be used.");

                GroupLayout layout = new GroupLayout(this);
                setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                .addGroup(layout.createSequentialGroup()
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                .addComponent(testLabel)
                                                                .addComponent(scoreLabel))
//                                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                                                                .addComponent(indTestComboBox, 0, 239, Short.MAX_VALUE)
                                                                .addComponent(scoreComboBox, 0, 239, Short.MAX_VALUE)))
                                                .addComponent(assumptionsLabel)
                                                .addGroup(layout.createSequentialGroup()
//                                                        .addGap(6, 6, 6)
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                .addComponent(mixedRadBtn)
                                                                .addComponent(linearGaussianRadBtn)
                                                                .addComponent(generalRadBtn)
                                                                .addComponent(allRadBtn)))
                                                .addComponent(missingValueAlert)
                                                .addComponent(testwiseDeletionAlert))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(assumptionsLabel)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(linearGaussianRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(mixedRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(generalRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(allRadBtn)
                                        .addPreferredGap(ComponentPlacement.UNRELATED)
                                        .addComponent(missingValueAlert)
                                        .addPreferredGap(ComponentPlacement.RELATED)
                                        .addComponent(testwiseDeletionAlert)
                                        .addPreferredGap(ComponentPlacement.RELATED)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(testLabel)
                                                .addComponent(indTestComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addPreferredGap(ComponentPlacement.RELATED)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(scoreComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(scoreLabel))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
            } else {
                GroupLayout layout = new GroupLayout(this);
                setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                .addGroup(layout.createSequentialGroup()
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                .addComponent(testLabel)
                                                                .addComponent(scoreLabel))
                                                        .addPreferredGap(ComponentPlacement.RELATED)
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                                                                .addComponent(indTestComboBox, 0, 239, Short.MAX_VALUE)
                                                                .addComponent(scoreComboBox, 0, 239, Short.MAX_VALUE)))
                                                .addComponent(assumptionsLabel)
                                                .addGroup(layout.createSequentialGroup()
                                                        .addGap(6, 6, 6)
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                .addComponent(mixedRadBtn)
                                                                .addComponent(linearGaussianRadBtn)
                                                                .addComponent(generalRadBtn)
                                                                .addComponent(allRadBtn))))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(assumptionsLabel)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(linearGaussianRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(mixedRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(generalRadBtn)
//                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(allRadBtn)
                                        .addGap(18, 18, 18)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(testLabel)
                                                .addComponent(indTestComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addPreferredGap(ComponentPlacement.RELATED)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(AlgorithmCard.this.scoreComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(this.scoreLabel))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
            }
        }
    }

}
