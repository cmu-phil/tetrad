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

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BlockScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.DeprecationUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.*;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.*;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

/**
 * Apr 15, 2019 11:31:10 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class AlgorithmCard extends JPanel {
    @Serial
    private static final long serialVersionUID = -7552068626783685630L;

    /**
     * The algorithm runner.
     */
    private final String ALGO_PARAM = "algo";

    /**
     * Independent test parameter.
     */
    private final String IND_TEST_PARAM = "ind_test";

    /**
     * Score parameter.
     */
    private final String SCORE_PARAM = "score";

    /**
     * Algorithm type parameter.
     */
    private final String ALGO_TYPE_PARAM = "algo_type";

    /**
     * Dataset filter parameter.
     */
    private final String DATASET_FILTER = "dataset_filter";

    /**
     * Knowledge parameter.
     */
    private final String KNOWLEDGE_PARAM = "knowledge";

    /**
     * Algorithm type options.
     */
    private final List<JRadioButton> algoTypeOpts = new ArrayList<>();

    /**
     * Algorithm models.
     */
    private final DefaultListModel<AlgorithmModel> algoModels = new DefaultListModel<>();

    /**
     * Algorithm filter button group.
     */
    private final ButtonGroup algoFilterBtnGrp = new ButtonGroup();

    /**
     * Dataset filter button group.
     */
    private final ButtonGroup datasetFilterBtnGrp = new ButtonGroup();

    /**
     * Default independence test models.
     */
    private final Map<AlgorithmModel, Map<DataType, IndependenceTestModel>> defaultIndTestModels = new HashMap<>();

    /**
     * Default score models.
     */
    private final Map<AlgorithmModel, Map<DataType, ScoreModel>> defaultScoreModels = new HashMap<>();

    /**
     * Knowledge checkbox.
     */
    private final JCheckBox knowledgeChkBox = new JCheckBox("accepts knowledge");

    /**
     * Linear, Gaussian radio button.
     */
    private final JRadioButton linearGaussianRadBtn = new JRadioButton("Linear, Gaussian");

    /**
     * Mixed, discrete, Gaussian radio button.
     */
    private final JRadioButton mixedRadBtn = new JRadioButton("Mixed Discrete/Continuous");

    /**
     * General radio button.
     */
    private final JRadioButton generalRadBtn = new JRadioButton("General");

    /**
     * All radio button.
     */
    private final JRadioButton allRadBtn = new JRadioButton("All");

    /**
     * Independence test combo box.
     */
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();

    /**
     * Score combo box.
     */
    private final JComboBox<ScoreModel> scoreComboBox = new JComboBox<>();

    /**
     * Algorithm list.
     */
    private final JList<AlgorithmModel> algorithmList = new JList<>(this.algoModels);

    /**
     * Algorithm description text area.
     */
    private final JTextArea algoDescTextArea = new JTextArea();

    /**
     * Score description text area.
     */
    private final JTextArea scoreDescTextArea = new JTextArea();

    /**
     * Test description text area.
     */
    private final JTextArea testDescTextArea = new JTextArea();

    /**
     * The algorithm runner.
     */
    private final GeneralAlgorithmRunner algorithmRunner;

    /**
     * The data type.
     */
    private final DataType dataType;

    /**
     * The desktop.
     */
    private final TetradDesktop desktop;

    /**
     * Multi-data algorithm.
     */
    private final boolean multiDataAlgo;
    private final BlockSpec blockSpec;
    private final Parameters parameters;
    // Persisted UI selections (stored in Parameters)
//    private static final String UI_IND_TEST = "ui.search.ind_test";
//    private static final String UI_SCORE    = "ui.search.score";

    /**
     * Updating test models.
     */
    private boolean updatingTestModels;

    /**
     * Updating score models.
     */
    private boolean updatingScoreModels;

    private static final String UI_ALGO        = "ui.search.algo";
    private static final String UI_IND_TEST    = "ui.search.ind_test";
    private static final String UI_SCORE       = "ui.search.score";
    private static final String UI_ALGO_TYPE   = "ui.search.algo_type";
    private static final String UI_DATA_FILTER = "ui.search.dataset_filter";
    private static final String UI_KNOWLEDGE   = "ui.search.knowledge";

//    private static final java.util.prefs.Preferences PREFS =
//            java.util.prefs.Preferences.userRoot().node("/edu/cmu/tetradapp/editor/search");

    /**
     * <p>Constructor for AlgorithmCard.</p>
     *
     * @param algorithmRunner a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     */
    public AlgorithmCard(GeneralAlgorithmRunner algorithmRunner, BlockSpec blockSpec) {
        this.algorithmRunner = algorithmRunner;
        this.blockSpec = blockSpec; // typically null, only non-null for block tests and scores.
        this.dataType = getDataType(algorithmRunner);
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.multiDataAlgo = algorithmRunner.getSourceGraph() == null && algorithmRunner.getDataModelList().size() > 1;
        this.parameters = algorithmRunner.getParameters();

        initComponents();
        initListeners();

        resetAllSettings();

        this.algorithmList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void initComponents() {
        initDescriptionTextAreas();

//        JButton resetSettingsBtn = new JButton("Reset All Settings");
//        resetSettingsBtn.addActionListener(e -> {
//            resetAllSettings();
//        });

        JPanel westMainSouthPanel = new JPanel(new BorderLayout(0, 10));
        westMainSouthPanel.add(new TestAndScorePanel(), BorderLayout.CENTER);
//        westMainSouthPanel.add(resetSettingsBtn, BorderLayout.SOUTH);

        JPanel westMainWestPanel = new JPanel(new BorderLayout(0, 10));
        westMainWestPanel.add(new AlgorithmFilterPanel(), BorderLayout.CENTER);
        westMainWestPanel.add(westMainSouthPanel, BorderLayout.SOUTH);

        JPanel westMainPanel = new JPanel(new BorderLayout(5, 0));
        westMainPanel.add(westMainWestPanel, BorderLayout.WEST);
        westMainPanel.add(new AlgorithmListPanel(), BorderLayout.EAST);

        JPanel testAndScoreDescPanel = new JPanel();
        testAndScoreDescPanel.setLayout(new BoxLayout(testAndScoreDescPanel, BoxLayout.Y_AXIS));
        testAndScoreDescPanel.add(new DescriptionPanel("Test Description", this.testDescTextArea));
        testAndScoreDescPanel.add(Box.createVerticalStrut(10));
        testAndScoreDescPanel.add(new DescriptionPanel("Score Description", this.scoreDescTextArea));

        JPanel centerMainPanel = new JPanel(new BorderLayout(0, 10));
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

//    private void saveGlobalPreferences() {
//        AlgorithmModel algrememberUserAlgoSelectionsoModel = this.algorithmList.getSelectedValue();
//        if (algoModel != null && algoModel.getAlgorithm() != null && algoModel.getAlgorithm().annotation() != null) {
//            parameters.set(UI_ALGO, algoModel.getAlgorithm().annotation().name());
//        }
//
//        ButtonModel algoTypeSel = this.algoFilterBtnGrp.getSelection();
//        if (algoTypeSel != null) {
//            parameters.set(UI_ALGO_TYPE, algoTypeSel.getActionCommand());
//        }
//
//        ButtonModel dataFilterSel = this.datasetFilterBtnGrp.getSelection();
//        if (dataFilterSel != null) {
//            parameters.set(UI_DATA_FILTER, dataFilterSel.getActionCommand());
//        }
//
//        parameters.set(UI_KNOWLEDGE, this.knowledgeChkBox.isSelected());
//
//        IndependenceTestModel testModel =
//                (IndependenceTestModel) this.indTestComboBox.getSelectedItem();
//        if (testModel != null && testModel.getIndependenceTest() != null
//                && testModel.getIndependenceTest().annotation() != null) {
//            parameters.set(UI_IND_TEST, testModel.getIndependenceTest().annotation().command());
//        }
//
//        ScoreModel scoreModel =
//                (ScoreModel) this.scoreComboBox.getSelectedItem();
//        if (scoreModel != null && scoreModel.getScore() != null
//                && scoreModel.getScore().annotation() != null) {
//            parameters.set(UI_SCORE, scoreModel.getScore().annotation().command());
//        }
//    }

    private String getSavedAlgoName(Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(this.ALGO_PARAM);
        if (obj instanceof String s && !s.isBlank()) {
            return s;
        }

        String s = parameters.getString(UI_ALGO, null);
        if (s != null && !s.isBlank()) {
            return s;
        }

        return null;
    }

    private String getSavedAlgoType(Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(this.ALGO_TYPE_PARAM);
        if (obj instanceof String s && !s.isBlank()) {
            return s;
        }

        return parameters.getString(UI_ALGO_TYPE, "all");
    }

    private String getSavedDatasetFilter(Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(this.DATASET_FILTER);
        if (obj instanceof String s && !s.isBlank()) {
            return s;
        }

        return parameters.getString(UI_DATA_FILTER, "all");
    }

    private boolean getSavedKnowledgeFlag(Map<String, Object> userAlgoSelections) {
        Object obj = userAlgoSelections.get(this.KNOWLEDGE_PARAM);
        if (obj instanceof Boolean b) {
            return b;
        }

        return parameters.getBoolean(UI_KNOWLEDGE, false);
    }

    private IndependenceTestModel findTestByName(String name) {
        if (name == null) return null;

        for (int i = 0; i < this.indTestComboBox.getItemCount(); i++) {
            IndependenceTestModel m = this.indTestComboBox.getItemAt(i);
            if (m != null && name.equals(m.toString())) {
                return m;
            }
        }

        return null;
    }

    private ScoreModel findScoreByName(String name) {
        if (name == null) return null;

        for (int i = 0; i < this.scoreComboBox.getItemCount(); i++) {
            ScoreModel m = this.scoreComboBox.getItemAt(i);
            if (m != null && name.equals(m.toString())) {
                return m;
            }
        }

        return null;
    }

    private AlgorithmModel findAlgorithmByName(String name) {
        if (name == null) return null;

        Enumeration<AlgorithmModel> e = this.algoModels.elements();
        while (e.hasMoreElements()) {
            AlgorithmModel m = e.nextElement();
            if (name.equals(m.toString())
                    || (m.getAlgorithm() != null
                    && m.getAlgorithm().annotation() != null
                    && name.equals(m.getAlgorithm().annotation().name()))) {
                return m;
            }
        }

        return null;
    }

    private void initListeners() {
        this.knowledgeChkBox.addActionListener(e -> {
            refreshAlgorithmList();
            saveStates();
        });
        this.linearGaussianRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
            saveStates();
        });
        this.mixedRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
            saveStates();
        });
        this.generalRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
            saveStates();
        });
        this.allRadBtn.addActionListener(e -> {
            refreshTestAndScoreList();
            saveStates();
        });
        this.algorithmList.addListSelectionListener(e -> {
            if (!(e.getValueIsAdjusting() || this.algorithmList.isSelectionEmpty())) {
                setAlgorithmDescription();
                refreshTestAndScoreList();
                validateAlgorithmOption();
                saveStates();
            }
        });

        this.indTestComboBox.addActionListener(e -> {
            if (!this.updatingTestModels && this.indTestComboBox.getSelectedIndex() >= 0) {
                setIndepTestDescription();

                AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
                Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultIndTestModels.put(algoModel, map);
                }
                IndependenceTestModel sel = this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
                map.put(this.dataType, sel);

                if (sel != null && sel.getIndependenceTest() != null && sel.getIndependenceTest().annotation() != null) {
                    this.parameters.set(getUiIndTestKey(), sel.getIndependenceTest().annotation().command());
                }

                saveStates();
            }
        });

        this.scoreComboBox.addActionListener(e -> {
            if (!this.updatingScoreModels && this.scoreComboBox.getSelectedIndex() >= 0) {
                setScoreDescription();

                AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
                Map<DataType, ScoreModel> map = this.defaultScoreModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultScoreModels.put(algoModel, map);
                }
                ScoreModel sel = this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());
                map.put(this.dataType, sel);

                if (sel != null && sel.getScore() != null && sel.getScore().annotation() != null) {
                    this.parameters.set(getUiScoreKey(), sel.getScore().annotation().command());
                }

                saveStates();
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

    private DataType getDataType(GeneralAlgorithmRunner algorithmRunner) {
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

    private DataType getEffectiveDataType() {
        if (this.generalRadBtn.isSelected() && this.dataType == DataType.Continuous) {
            return DataType.ContinuousGeneral;
        }
        if (this.mixedRadBtn.isSelected() && this.dataType == DataType.Mixed) {
            return DataType.ContinuousMixed;
        }
        return this.dataType;
    }

    /**
     * <p>getSelectedAlgorithm.</p>
     *
     * @return a {@link edu.cmu.tetradapp.ui.model.AlgorithmModel} object
     */
    public AlgorithmModel getSelectedAlgorithm() {
        return this.algorithmList.getSelectedValue();
    }

//    private void rememberUserAlgoSelections(Map<String, Object> userAlgoSelections) {
//        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
//        IndependenceTestModel testModel =
//                (IndependenceTestModel) this.indTestComboBox.getSelectedItem();
//        ScoreModel scoreModel =
//                (ScoreModel) this.scoreComboBox.getSelectedItem();
//
//        ButtonModel algoTypeSel = this.algoFilterBtnGrp.getSelection();
//        ButtonModel dataFilterSel = this.datasetFilterBtnGrp.getSelection();
//
//        if (algoTypeSel != null) {
//            userAlgoSelections.put(this.ALGO_TYPE_PARAM, algoTypeSel.getActionCommand());
//        }
//
//        if (dataFilterSel != null) {
//            userAlgoSelections.put(this.DATASET_FILTER, dataFilterSel.getActionCommand());
//        }
//
//        userAlgoSelections.put(this.KNOWLEDGE_PARAM, this.knowledgeChkBox.isSelected());
//
//        if (algoModel != null) {
//            userAlgoSelections.put(this.ALGO_PARAM, algoModel.toString());
//        }
//
//        if (testModel != null) {
//            userAlgoSelections.put(this.IND_TEST_PARAM, testModel.toString());
//        }
//
//        if (scoreModel != null) {
//            userAlgoSelections.put(this.SCORE_PARAM, scoreModel.toString());
//        }
//
//        if (testModel != null && testModel.getIndependenceTest() != null
//                && testModel.getIndependenceTest().annotation() != null) {
//            this.parameters.set(UI_IND_TEST, testModel.getIndependenceTest().annotation().command());
//        }
//
//        if (scoreModel != null && scoreModel.getScore() != null
//                && scoreModel.getScore().annotation() != null) {
//            this.parameters.set(UI_SCORE, scoreModel.getScore().annotation().command());
//        }
//
//        if (algoModel != null && algoModel.getAlgorithm() != null
//                && algoModel.getAlgorithm().annotation() != null) {
//            this.parameters.set(UI_ALGO, algoModel.getAlgorithm().annotation().name());
//        }
//
//        saveGlobalPreferences();
//    }

    /**
     * This restore mechanism won't restore user selections other than selected algo name when user changes the upstream
     * (after clicking the "Execute" button), because a new algo algorithmRunner is created and we lose the stored
     * models from the old algorithmRunner - Zhou
     */
    private void restoreUserAlgoSelections(Map<String, Object> userAlgoSelections) {
        String datasetFilter = getSavedDatasetFilter(userAlgoSelections);
        for (Enumeration<AbstractButton> e = this.datasetFilterBtnGrp.getElements(); e.hasMoreElements(); ) {
            AbstractButton btn = e.nextElement();
            if (datasetFilter.equals(btn.getActionCommand())) {
                btn.setSelected(true);
                break;
            }
        }

        this.knowledgeChkBox.setSelected(getSavedKnowledgeFlag(userAlgoSelections));

        String algoType = getSavedAlgoType(userAlgoSelections);
        for (JRadioButton btn : this.algoTypeOpts) {
            if (algoType.equals(btn.getActionCommand())) {
                btn.setSelected(true);
                break;
            }
        }

        refreshAlgorithmList();

        String selectedAlgoName = getSavedAlgoName(userAlgoSelections);
        AlgorithmModel selectedAlgo = findAlgorithmByName(selectedAlgoName);
        if (selectedAlgo != null) {
            this.algorithmList.setSelectedValue(selectedAlgo, true);
        } else if (!this.algoModels.isEmpty()) {
            this.algorithmList.setSelectedIndex(0);
        }

        refreshTestAndScoreList();

        Object obj = userAlgoSelections.get(this.IND_TEST_PARAM);
        IndependenceTestModel savedTest = null;

        if (obj instanceof String s) {
            savedTest = findTestByName(s);
        }
        if (savedTest == null) {
            String savedCmd = this.parameters.getString(UI_IND_TEST, parameters.getString(UI_IND_TEST, null));
            savedTest = findTestByCommand(savedCmd);
        }
        if (savedTest != null) {
            this.updatingTestModels = true;
            this.indTestComboBox.setSelectedItem(savedTest);
            this.updatingTestModels = false;
            setIndepTestDescription();
        }

        obj = userAlgoSelections.get(this.SCORE_PARAM);
        ScoreModel savedScore = null;

        if (obj instanceof String s) {
            savedScore = findScoreByName(s);
        }
        if (savedScore == null) {
            String savedCmd = this.parameters.getString(UI_SCORE, parameters.getString(UI_SCORE, null));
            savedScore = findScoreByCommand(savedCmd);
        }
        if (savedScore != null) {
            this.updatingScoreModels = true;
            this.scoreComboBox.setSelectedItem(savedScore);
            this.updatingScoreModels = false;
            setScoreDescription();
        }

        setAlgorithmDescription();
    }

    /**
     * <p>refresh.</p>
     */
    public void refresh() {
        restoreUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    /**
     * <p>saveStates.</p>
     */
    public void saveStates() {
        rememberUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    /**
     * Initialize algorithm
     *
     * @param algoModel    a {@link edu.cmu.tetradapp.ui.model.AlgorithmModel} object
     * @param indTestModel a {@link edu.cmu.tetradapp.ui.model.IndependenceTestModel} object
     * @param scoreModel   a {@link edu.cmu.tetradapp.ui.model.ScoreModel} object
     * @return Algorithm
     */
    public Algorithm getAlgorithmFromInterface(AlgorithmModel algoModel, IndependenceTestModel indTestModel, ScoreModel scoreModel) {
        Class algoClass = algoModel.getAlgorithm().clazz();
        Class indTestClass = (indTestModel == null) ? null : indTestModel.getIndependenceTest().clazz();
        Class scoreClass = (scoreModel == null) ? null : scoreModel.getScore().clazz();

        Algorithm algorithm = null;

        try {
            algorithm = AlgorithmFactory.create(algoClass, indTestClass, scoreClass);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException exception) {
            TetradLogger.getInstance().log(exception.toString());
        }

        // Those pairwise algos (R3, RShew, Skew..) require source graph to initialize - Zhou
        if (algorithm instanceof TakesExternalGraph && this.algorithmRunner.getSourceGraph() != null /*&& !this.algorithmRunner.getDataModelList().isEmpty()*/) {
            Algorithm externalGraph = new SingleGraphAlg(this.algorithmRunner.getSourceGraph());
            ((TakesExternalGraph) algorithm).setExternalGraph(externalGraph);
        }

        return algorithm;
    }

    /**
     * <p>isAllValid.</p>
     *
     * @return a boolean
     */
    public boolean isAllValid() {
        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        IndependenceTestModel indTestModel = this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
        ScoreModel scoreModel = this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());

        boolean missingTest = algoModel.isRequiredTest() && (indTestModel == null);
        boolean missingScore = algoModel.isRequiredScore() && (scoreModel == null);
        if (missingTest && missingScore) {
            String msg = String.format("%s requires both test and score.",
                    algoModel.getAlgorithm().annotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingTest) {
            String msg = String.format("%s requires independence test.",
                    algoModel.getAlgorithm().annotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else if (missingScore) {
            String msg = String.format("%s requires score.",
                    algoModel.getAlgorithm().annotation().name());
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);

            return false;
        } else {
            this.algorithmRunner.setAlgorithm(getAlgorithmFromInterface(algoModel, indTestModel, scoreModel));

            return true;
        }
    }

    private void validateAlgorithmOption() {
        firePropertyChange("algoFwdBtn", null, true);

        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        Class<?> algoClass = algoModel.getAlgorithm().clazz();

        if (algoClass.isAnnotationPresent(Nonexecutable.class)) {
            String msg;
            try {
                Object algo = algoClass.getDeclaredConstructor().newInstance();
                Method m = algoClass.getDeclaredMethod("getDescription");
                m.setAccessible(true);
                try {
                    msg = String.valueOf(m.invoke(algo));
                } catch (InvocationTargetException exception) {
                    msg = "";
                }

            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                     InvocationTargetException exception) {
                TetradLogger.getInstance().log(exception.toString());
                msg = "";
            }

            firePropertyChange("algoFwdBtn", null, false);
            JOptionPane.showMessageDialog(this.desktop, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // Check if initial graph is provided for those pairwise algorithms
            if (TakesExternalGraph.class.isAssignableFrom(algoClass)) {
                if (this.algorithmRunner.getSourceGraph() == null || this.algorithmRunner.getDataModelList().isEmpty()) {
                    try {
                        Object algo = algoClass.getDeclaredConstructor().newInstance();
                        Method m = algoClass.getDeclaredMethod("setExternalGraph", Algorithm.class);
                        m.setAccessible(true);
                        try {
                            Algorithm algorithm = null;
                            m.invoke(algo, algorithm);
                        } catch (InvocationTargetException | IllegalArgumentException exception) {
                            firePropertyChange("algoFwdBtn", null, false);
                            JOptionPane.showMessageDialog(this.desktop, exception.getCause().getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                             InvocationTargetException exception) {
                        TetradLogger.getInstance().log(exception.toString());
                    }
                }
            }

            // SVAR (SvarFci, SvarGfci) algorithms need lagged data
            String cmd = algoModel.getAlgorithm().annotation().command();
            if (cmd.equalsIgnoreCase("ts-fci")
                    || cmd.equalsIgnoreCase("ts-gfci")
                    || cmd.equalsIgnoreCase("ts-imgs")) {
                DataModel dataModel = this.algorithmRunner.getDataModel();
                Knowledge knowledge = this.algorithmRunner.getKnowledge();
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

        ButtonModel selectedAlgoType = this.algoFilterBtnGrp.getSelection();
        if (selectedAlgoType != null) {
            AlgorithmModels algorithmModels = AlgorithmModels.getInstance();

            String algoType = selectedAlgoType.getActionCommand();

            // Base stream by type selection
            java.util.stream.Stream<AlgorithmModel> baseStream;
            if ("all".equals(algoType)) {
                baseStream = algorithmModels.getModels(this.dataType, this.multiDataAlgo).stream();
            } else {
                baseStream = algorithmModels.getModels(AlgType.valueOf(algoType), this.dataType, this.multiDataAlgo).stream();
            }

            // Optional HasKnowledge filter
            if (this.knowledgeChkBox.isSelected()) {
                baseStream = baseStream.filter(m ->
                        AcceptsKnowledge.class.isAssignableFrom(m.getAlgorithm().clazz()));
            }

            // Block-mode gating:
            // If blockSpec != null, keep ONLY algorithms that can accept block wrappers or are tagged.
            if (this.blockSpec != null) {
                baseStream = baseStream.filter(m -> {
                    Class<?> c = m.getAlgorithm().clazz();
                    return LatentStructureAlgorithm.class.isAssignableFrom(c);
                });
            } else {
                baseStream = baseStream.filter(m -> {
                    Class<?> c = m.getAlgorithm().clazz();
                    return !ExtraLatentStructureAlgorithm.class.isAssignableFrom(c);
                });
            }

            // Populate list model
            baseStream.forEach(this.algoModels::addElement);

            if (this.algoModels.isEmpty()) {
                this.algoDescTextArea.setText("");
                firePropertyChange("algoFwdBtn", null, false);
            } else {
                String savedAlgo = this.parameters.getString(UI_ALGO, parameters.getString(UI_ALGO, null));
                AlgorithmModel model = findAlgorithmByName(savedAlgo);
                if (model != null) {
                    this.algorithmList.setSelectedValue(model, true);
                } else if (this.algorithmList.getSelectedValue() == null) {
                    this.algorithmList.setSelectedIndex(0);
                }
                firePropertyChange("algoFwdBtn", null, true);
            }
        }

        this.scoreComboBox.setEnabled(this.scoreComboBox.getItemCount() > 0);
    }

//    private void refreshTestList() {
//        this.updatingTestModels = true;
//        this.indTestComboBox.removeAllItems();
//
//        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
//        if (algoModel != null && algoModel.isRequiredTest()) {
////            List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(this.dataType);
//            List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(getEffectiveDataType());
//
//            // 1) Radio-button based type filter
//            java.util.function.Predicate<IndependenceTestModel> typeFilter = m -> {
//                Class<?> c = m.getIndependenceTest().clazz();
//
//                if (DeprecationUtils.isClassDeprecated(c)) {
//                    return false;
//                }
//
//                if (this.linearGaussianRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(LinearGaussian.class);
//                } else if (this.mixedRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(Mixed.class);
//                } else if (this.generalRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(General.class);
//                } else if (this.allRadBtn.isSelected()) {
//                    return true;
//                }
//                return true;
//            };
//
//            // 2) Blocks gating based on presence of blockSpec
//            java.util.function.Predicate<IndependenceTestModel> blocksGate = m -> {
//                Class<?> c = m.getIndependenceTest().clazz();
//                boolean isBlocks = BlockIndependenceWrapper.class.isAssignableFrom(c);
//                if (this.blockSpec == null) {
//                    // No BlockSpec: hide block-based tests
//                    return !isBlocks;
//                } else {
//                    // Has BlockSpec: show only block-based tests
//                    return isBlocks;
//                }
//            };
//
//            // 3) Apply both filters and populate combo
//            models.stream()
//                    .filter(typeFilter)
//                    .filter(blocksGate)
//                    .forEach(this.indTestComboBox::addItem);
//        }
//
//        this.updatingTestModels = false;
//
//        if (this.indTestComboBox.getItemCount() > 0) {
//            this.indTestComboBox.setEnabled(true);
//
//            // 0) Try restore from Parameters first (if present and not filtered out)
//            IndependenceTestModel testModel = null;
//            String savedCmd = this.parameters.getString(UI_IND_TEST, null);
//            testModel = findTestByCommand(savedCmd);
//
//            // 1) Else fall back to your per-algo-per-datatype defaults
//            if (testModel == null) {
//                Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(algoModel);
//                if (map == null) {
//                    map = new EnumMap<>(DataType.class);
//                    this.defaultIndTestModels.put(algoModel, map);
//                }
//                testModel = map.get(this.dataType);
//            }
//
//            // 2) Else fall back to global default
//            if (testModel == null) {
////                testModel = IndependenceTestModels.getInstance().getDefaultModel(this.dataType);
//                testModel = IndependenceTestModels.getInstance().getDefaultModel(getEffectiveDataType());
//            }
//
//            // 3) Else first available
//            if (testModel == null) {
//                testModel = this.indTestComboBox.getItemAt(0);
//            }
//
//            this.updatingTestModels = true;        // <— important: prevent writing back while we programmatically select
//            this.indTestComboBox.setSelectedItem(testModel);
//            this.updatingTestModels = false;
//
//        } else {
//            this.indTestComboBox.setEnabled(false);
//        }
//
//        if (this.indTestComboBox.getSelectedIndex() == -1) {
//            this.testDescTextArea.setText("");
//        }
//    }

//    private void refreshScoreList() {
//        this.updatingScoreModels = true;
//        this.scoreComboBox.removeAllItems();
//
//        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
//        if (algoModel != null && algoModel.isRequiredScore()) {
////            List<ScoreModel> models = ScoreModels.getInstance().getModels(this.dataType);
//            List<ScoreModel> models = ScoreModels.getInstance().getModels(getEffectiveDataType());
//
//            // 1) Radio-button type filter
//            java.util.function.Predicate<ScoreModel> typeFilter = m -> {
//                Class<?> c = m.getScore().clazz();
//
//                if (DeprecationUtils.isClassDeprecated(c)) {
//                    return false;
//                }
//
//                if (this.linearGaussianRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(LinearGaussian.class);
//                } else if (this.mixedRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(Mixed.class);
//                } else if (this.generalRadBtn.isSelected()) {
//                    return c.isAnnotationPresent(General.class);
//                } else if (this.allRadBtn.isSelected()) {
//                    return true;
//                }
//                return true;
//            };
//
//            // 2) Blocks gating based on presence of blockSpec
//            java.util.function.Predicate<ScoreModel> blocksGate = m -> {
//                Class<?> c = m.getScore().clazz();
//                boolean isBlocks = BlockScoreWrapper.class.isAssignableFrom(c);
//                if (this.blockSpec == null) {
//                    // No BlockSpec: hide block-based scores
//                    return !isBlocks;
//                } else {
//                    // Has BlockSpec: show only block-based scores
//                    return isBlocks;
//                }
//            };
//
//            // 3) Apply both filters and populate combo
//            models.stream()
//                    .filter(typeFilter)
//                    .filter(blocksGate)
//                    .forEach(this.scoreComboBox::addItem);
//        }
//
//        this.updatingScoreModels = false;
//
//        if (this.scoreComboBox.getItemCount() > 0) {
//            this.scoreComboBox.setEnabled(true);
//
//            ScoreModel scoreModel = null;
//            String savedCmd = this.parameters.getString(UI_SCORE, null);
//            scoreModel = findScoreByCommand(savedCmd);
//
//            if (scoreModel == null) {
//                Map<DataType, ScoreModel> map = this.defaultScoreModels.get(algoModel);
//                if (map == null) {
//                    map = new EnumMap<>(DataType.class);
//                    this.defaultScoreModels.put(algoModel, map);
//                }
//                scoreModel = map.get(this.dataType);
//            }
//
//            if (scoreModel == null) {
////                scoreModel = ScoreModels.getInstance().getDefaultModel(this.dataType);
//                scoreModel = ScoreModels.getInstance().getDefaultModel(getEffectiveDataType());
//            }
//
//            if (scoreModel == null) {
//                scoreModel = this.scoreComboBox.getItemAt(0);
//            }
//
//            this.updatingScoreModels = true;
//            this.scoreComboBox.setSelectedItem(scoreModel);
//            this.updatingScoreModels = false;
//
//        } else {
//            this.scoreComboBox.setEnabled(false);
//        }
//
//        if (this.scoreComboBox.getSelectedIndex() == -1) {
//            this.scoreDescTextArea.setText("");
//        }
//    }

private String getUiIndTestKey() {
    if (this.generalRadBtn.isSelected()) return UI_IND_TEST + ".general";
    if (this.linearGaussianRadBtn.isSelected()) return UI_IND_TEST + ".linear-gaussian";
    if (this.mixedRadBtn.isSelected()) return UI_IND_TEST + ".mixed";
    return UI_IND_TEST;
}

    private String getUiScoreKey() {
        if (this.generalRadBtn.isSelected()) return UI_SCORE + ".general";
        if (this.linearGaussianRadBtn.isSelected()) return UI_SCORE + ".linear-gaussian";
        if (this.mixedRadBtn.isSelected()) return UI_SCORE + ".mixed";
        return UI_SCORE;
    }

    private void saveGlobalPreferences() {
        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.getAlgorithm() != null && algoModel.getAlgorithm().annotation() != null) {
            parameters.set(UI_ALGO, algoModel.getAlgorithm().annotation().name());
        }

        ButtonModel algoTypeSel = this.algoFilterBtnGrp.getSelection();
        if (algoTypeSel != null) {
            parameters.set(UI_ALGO_TYPE, algoTypeSel.getActionCommand());
        }

        ButtonModel dataFilterSel = this.datasetFilterBtnGrp.getSelection();
        if (dataFilterSel != null) {
            parameters.set(UI_DATA_FILTER, dataFilterSel.getActionCommand());
        }

        parameters.set(UI_KNOWLEDGE, this.knowledgeChkBox.isSelected());

        IndependenceTestModel testModel = (IndependenceTestModel) this.indTestComboBox.getSelectedItem();
        if (testModel != null && testModel.getIndependenceTest() != null
                && testModel.getIndependenceTest().annotation() != null) {
            parameters.set(getUiIndTestKey(), testModel.getIndependenceTest().annotation().command());
        }

        ScoreModel scoreModel = (ScoreModel) this.scoreComboBox.getSelectedItem();
        if (scoreModel != null && scoreModel.getScore() != null
                && scoreModel.getScore().annotation() != null) {
            parameters.set(getUiScoreKey(), scoreModel.getScore().annotation().command());
        }
    }

    private void rememberUserAlgoSelections(Map<String, Object> userAlgoSelections) {
        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        IndependenceTestModel testModel = (IndependenceTestModel) this.indTestComboBox.getSelectedItem();
        ScoreModel scoreModel = (ScoreModel) this.scoreComboBox.getSelectedItem();

        ButtonModel algoTypeSel = this.algoFilterBtnGrp.getSelection();
        ButtonModel dataFilterSel = this.datasetFilterBtnGrp.getSelection();

        if (algoTypeSel != null) {
            userAlgoSelections.put(this.ALGO_TYPE_PARAM, algoTypeSel.getActionCommand());
        }

        if (dataFilterSel != null) {
            userAlgoSelections.put(this.DATASET_FILTER, dataFilterSel.getActionCommand());
        }

        userAlgoSelections.put(this.KNOWLEDGE_PARAM, this.knowledgeChkBox.isSelected());

        if (algoModel != null) {
            userAlgoSelections.put(this.ALGO_PARAM, algoModel.toString());
        }

        if (testModel != null) {
            userAlgoSelections.put(this.IND_TEST_PARAM, testModel.toString());
        }

        if (scoreModel != null) {
            userAlgoSelections.put(this.SCORE_PARAM, scoreModel.toString());
        }

        if (testModel != null && testModel.getIndependenceTest() != null
                && testModel.getIndependenceTest().annotation() != null) {
            this.parameters.set(getUiIndTestKey(), testModel.getIndependenceTest().annotation().command());
        }

        if (scoreModel != null && scoreModel.getScore() != null
                && scoreModel.getScore().annotation() != null) {
            this.parameters.set(getUiScoreKey(), scoreModel.getScore().annotation().command());
        }

        if (algoModel != null && algoModel.getAlgorithm() != null
                && algoModel.getAlgorithm().annotation() != null) {
            this.parameters.set(UI_ALGO, algoModel.getAlgorithm().annotation().name());
        }

        saveGlobalPreferences();
    }

    private void refreshTestList() {
        this.updatingTestModels = true;
        this.indTestComboBox.removeAllItems();

        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredTest()) {
            List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(getEffectiveDataType());

            java.util.function.Predicate<IndependenceTestModel> typeFilter = m -> {
                Class<?> c = m.getIndependenceTest().clazz();

                if (DeprecationUtils.isClassDeprecated(c)) {
                    return false;
                }

                if (this.linearGaussianRadBtn.isSelected()) {
                    return c.isAnnotationPresent(LinearGaussian.class);
                } else if (this.mixedRadBtn.isSelected()) {
                    return c.isAnnotationPresent(Mixed.class);
                } else if (this.generalRadBtn.isSelected()) {
                    return c.isAnnotationPresent(General.class);
                } else if (this.allRadBtn.isSelected()) {
                    return true;
                }
                return true;
            };

            java.util.function.Predicate<IndependenceTestModel> blocksGate = m -> {
                Class<?> c = m.getIndependenceTest().clazz();
                boolean isBlocks = BlockIndependenceWrapper.class.isAssignableFrom(c);
                if (this.blockSpec == null) {
                    return !isBlocks;
                } else {
                    return isBlocks;
                }
            };

            models.stream()
                    .filter(typeFilter)
                    .filter(blocksGate)
                    .forEach(this.indTestComboBox::addItem);
        }

        this.updatingTestModels = false;

        if (this.indTestComboBox.getItemCount() > 0) {
            this.indTestComboBox.setEnabled(true);

            // 0) Try restore from per-filter-mode Parameters key
            IndependenceTestModel testModel = null;
            String savedCmd = this.parameters.getString(getUiIndTestKey(), null);
            testModel = findTestByCommand(savedCmd);

            // 1) Per-algo-per-datatype default
            if (testModel == null) {
                Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultIndTestModels.put(algoModel, map);
                }
                testModel = map.get(getEffectiveDataType());
            }

            // 2) Global default for effective data type
            if (testModel == null) {
                testModel = IndependenceTestModels.getInstance().getDefaultModel(getEffectiveDataType());
            }

            // 3) First available
            if (testModel == null) {
                testModel = this.indTestComboBox.getItemAt(0);
            }

            this.updatingTestModels = true;
            this.indTestComboBox.setSelectedItem(testModel);
            this.updatingTestModels = false;

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

        AlgorithmModel algoModel = this.algorithmList.getSelectedValue();
        if (algoModel != null && algoModel.isRequiredScore()) {
            List<ScoreModel> models = ScoreModels.getInstance().getModels(getEffectiveDataType());

            java.util.function.Predicate<ScoreModel> typeFilter = m -> {
                Class<?> c = m.getScore().clazz();

                if (DeprecationUtils.isClassDeprecated(c)) {
                    return false;
                }

                if (this.linearGaussianRadBtn.isSelected()) {
                    return c.isAnnotationPresent(LinearGaussian.class);
                } else if (this.mixedRadBtn.isSelected()) {
                    return c.isAnnotationPresent(Mixed.class);
                } else if (this.generalRadBtn.isSelected()) {
                    return c.isAnnotationPresent(General.class);
                } else if (this.allRadBtn.isSelected()) {
                    return true;
                }
                return true;
            };

            java.util.function.Predicate<ScoreModel> blocksGate = m -> {
                Class<?> c = m.getScore().clazz();
                boolean isBlocks = BlockScoreWrapper.class.isAssignableFrom(c);
                if (this.blockSpec == null) {
                    return !isBlocks;
                } else {
                    return isBlocks;
                }
            };

            models.stream()
                    .filter(typeFilter)
                    .filter(blocksGate)
                    .forEach(this.scoreComboBox::addItem);
        }

        this.updatingScoreModels = false;

        if (this.scoreComboBox.getItemCount() > 0) {
            this.scoreComboBox.setEnabled(true);

            // 0) Try restore from per-filter-mode Parameters key
            ScoreModel scoreModel = null;
            String savedCmd = this.parameters.getString(getUiScoreKey(), null);
            scoreModel = findScoreByCommand(savedCmd);

            // 1) Per-algo-per-datatype default
            if (scoreModel == null) {
                Map<DataType, ScoreModel> map = this.defaultScoreModels.get(algoModel);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    this.defaultScoreModels.put(algoModel, map);
                }
                scoreModel = map.get(getEffectiveDataType());
            }

            // 2) Global default for effective data type
            if (scoreModel == null) {
                scoreModel = ScoreModels.getInstance().getDefaultModel(getEffectiveDataType());
            }

            // 3) First available
            if (scoreModel == null) {
                scoreModel = this.scoreComboBox.getItemAt(0);
            }

            this.updatingScoreModels = true;
            this.scoreComboBox.setSelectedItem(scoreModel);
            this.updatingScoreModels = false;

        } else {
            this.scoreComboBox.setEnabled(false);
        }

        if (this.scoreComboBox.getSelectedIndex() == -1) {
            this.scoreDescTextArea.setText("");
        }
    }

    private IndependenceTestModel findTestByCommand(String cmd) {
        if (cmd == null) return null;
        for (int i = 0; i < this.indTestComboBox.getItemCount(); i++) {
            IndependenceTestModel m = this.indTestComboBox.getItemAt(i);
            if (m != null && m.getIndependenceTest() != null && m.getIndependenceTest().annotation() != null) {
                if (cmd.equals(m.getIndependenceTest().annotation().command())) return m;
            }
        }
        return null;
    }

    private ScoreModel findScoreByCommand(String cmd) {
        if (cmd == null) return null;
        for (int i = 0; i < this.scoreComboBox.getItemCount(); i++) {
            ScoreModel m = this.scoreComboBox.getItemAt(i);
            if (m != null && m.getScore() != null && m.getScore().annotation() != null) {
                if (cmd.equals(m.getScore().annotation().command())) return m;
            }
        }
        return null;
    }

    private void refreshTestAndScoreList() {
        refreshTestList();
        refreshScoreList();
    }

    private void resetAllSettings() {
        this.defaultIndTestModels.clear();
        this.defaultScoreModels.clear();

        Map<String, Object> remembered = this.algorithmRunner.getUserAlgoSelections();
        restoreUserAlgoSelections(remembered);
    }

    private void setAlgorithmDescription() {
        AlgorithmModel model = this.algorithmList.getSelectedValue();
        if (model == null) {
            this.algoDescTextArea.setText("");
        } else {
            this.algoDescTextArea.setText(model.getDescription());
            this.algoDescTextArea.setCaretPosition(0);
        }
    }

    private void setScoreDescription() {
        ScoreModel model = this.scoreComboBox.getItemAt(this.scoreComboBox.getSelectedIndex());
        if (model == null) {
            this.scoreDescTextArea.setText("");
        } else {
            this.scoreDescTextArea.setText(model.getDescription());
            this.scoreDescTextArea.setCaretPosition(0);
        }
    }

    private void setIndepTestDescription() {
        IndependenceTestModel model = this.indTestComboBox.getItemAt(this.indTestComboBox.getSelectedIndex());
        if (model == null) {
            this.testDescTextArea.setText("");
        } else {
            this.testDescTextArea.setText(model.getDescription());
            this.testDescTextArea.setCaretPosition(0);
        }
    }

    private static class DescriptionPanel extends JPanel {

        @Serial
        private static final long serialVersionUID = 2329356999486712496L;

        final String borderTitle;
        final Component view;

        public DescriptionPanel(String borderTitle, Component view) {
            this.borderTitle = borderTitle;
            this.view = view;

            initComponents();
        }

        private void initComponents() {
            JScrollPane scrollPane = new JScrollPane(this.view);

            setBorder(BorderFactory.createTitledBorder(this.borderTitle));
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

        @Serial
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

        @Serial
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

            // Add to containing box
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
         * Create new radio buttons and add them to both the radio button list and radio button group.
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

                // These have been moved to the Latent Cluster and Latent Structure buttons.
                if (name.equals(AlgType.search_for_structure_over_latents.name())) {
                    return;
                }

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

        @Serial
        private static final long serialVersionUID = -1594897454478052884L;

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

            JLabel assumptionsLabel = new JLabel();
            JLabel testLabel = new JLabel();
            JLabel scoreLabel = new JLabel();

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
                                                .addComponent(scoreLabel))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
            }
        }
    }

}

