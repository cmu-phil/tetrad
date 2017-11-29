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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.AnnotatedClassUtils;
import edu.cmu.tetrad.annotation.AnnotatedClassWrapper;
import edu.cmu.tetrad.annotation.Gaussian;
import edu.cmu.tetrad.annotation.Linear;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.annotation.TetradAlgorithmAnnotations;
import edu.cmu.tetrad.annotation.TetradScoreAnnotations;
import edu.cmu.tetrad.annotation.TetradTestOfIndependenceAnnotations;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
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
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.WatchedProcess;
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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
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
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements FinalizingEditor {

    private static final long serialVersionUID = -5719467682865706447L;

    private final String ALGORITHM_CARD = "algorithm card";
    private final String PARAMETER_CARD = "parameter card";
    private final String GRAPH_CARD = "graph card";

    private HpcJobInfo hpcJobInfo;
    private String jsonResult;
    private List<AnnotatedClassWrapper<TestOfIndependence>> tests;
    private List<AnnotatedClassWrapper<Score>> scores;
    private Box parametersBox;
    private ParameterPanel parametersPanel;
    private DataType dataType;
    private AlgType selectedAlgoType;
    private boolean onlyShowAlgoAcceptKnowledge;
    private AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm> selectedAgloWrapper;
    private boolean linearRelationshipAssumption;
    private boolean gaussianVariablesAssumption;
    private List<AnnotatedClassWrapper<TestOfIndependence>> filteredIndTests;
    private List<AnnotatedClassWrapper<Score>> filteredScores;

    private final GeneralAlgorithmRunner runner;
    private final TetradDesktop desktop;
    private final GraphSelectionEditor graphEditor;
    private final Parameters parameters;
    private final Map<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>, Map<DataType, AnnotatedClassWrapper<Score>>> algoDefaultScores = new HashMap<>();
    private final Map<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>, Map<DataType, AnnotatedClassWrapper<TestOfIndependence>>> algoDefaultTests = new HashMap<>();
    private final Box graphContainer = Box.createHorizontalBox();
    private final JTextArea algoDescriptionTextArea = new JTextArea();
    private final DefaultListModel<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> suggestedAlgosListModel = new DefaultListModel<>();
    private final JList<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> suggestedAlgosList = new JList<>(suggestedAlgosListModel);
    private final List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> algoWrappers;
    private final DefaultComboBoxModel<AnnotatedClassWrapper<TestOfIndependence>> testDropdownModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<AnnotatedClassWrapper<Score>> scoreDropdownModel = new DefaultComboBoxModel<>();
    private final JComboBox<AnnotatedClassWrapper<TestOfIndependence>> testDropdown = new JComboBox<>(testDropdownModel);
    private final JComboBox<AnnotatedClassWrapper<Score>> scoreDropdown = new JComboBox<>(scoreDropdownModel);
    private final JCheckBox priorKnowledgeCheckbox = new JCheckBox("accept knowledge");
    private final JCheckBox linearVariablesCheckbox = new JCheckBox("Variables with linear relationship");
    private final JCheckBox gaussianVariablesCheckbox = new JCheckBox("Gaussian variables");
    private final JRadioButton algoTypeAllRadioBtn = new JRadioButton("Show all");
    private final ButtonGroup algoTypesBtnGrp = new ButtonGroup();

    public GeneralAlgorithmEditor(GeneralAlgorithmRunner runner) {
        this.runner = runner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(runner.getGraphs(), new Parameters()));
        this.parameters = runner.getParameters();

        // Access to the uploaded dataset
        DataModelList dataModelList = runner.getDataModelList();

        // NOTE: the dataModelList.isEmpty() returns false even if there's no real dataset
        // Taht's because Joe's using an empty dataset to populate the empty spreadsheet - Zhou
        // Notify the users that we need input dataset or source graph
        // if the data model has no dataset
        try {
            if ((dataModelList.containsEmptyData() && runner.getSourceGraph() == null)) {
                throw new Exception("You need either some datasets or a graph as input.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(desktop, e.getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
        }

        algoWrappers = TetradAlgorithmAnnotations.getInstance().getNameWrappers();

        // Use annotations to get the tests and scores based on different data types
        // Need to do this before calling createAlgoChooserPanel() - Zhou
        determineTestAndScore(dataModelList);

        // Create default models of test and score dropdowns
        setTestAndScoreDropdownModels(tests, scores);

        // Create default algos list model
        setDefaultAlgosListModel();

        initComponents();

        // Repopulate all the previous selections if reopen the search box
        if (runner.getGraphs() != null && runner.getGraphs().size() > 0) {
            // show the generated graph if reopen the search box
            graphContainer.add(graphEditor);  // use the already generated graphEditor
            changeCard(GRAPH_CARD);

            if (parameters.getString("algName") != null) {
                String selectedAlgoName = parameters.getString("algName");

                for (AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm> algoWraper : algoWrappers) {
                    if (algoWraper.getName().equals(selectedAlgoName)) {
                        suggestedAlgosList.setSelectedValue(algoWraper, true);
                        break;
                    }
                }
            }

            // Calling setAlgorithm() populates the previous parameters of selected algo
            setAlgorithm();
        } else {
            // Default to select the first algo name in list
            setSelection();
        }
    }

    private void initComponents() {
        parametersBox = Box.createVerticalBox();

        testDropdown.setPreferredSize(new Dimension(248, 24));
        testDropdown.addActionListener((ActionEvent e) -> {
            // Don't use setAlgorithm() because we don't need to determine if
            // enable/disable the test and score dropdown menus again - Zhou
            if (testDropdown.getSelectedItem() != null) {
                AnnotatedClassWrapper<TestOfIndependence> testWrapper = (AnnotatedClassWrapper<TestOfIndependence>) testDropdown.getSelectedItem();
                setDefaultTest(testWrapper);
                setTestType(testWrapper.getName());
            }
        });

        scoreDropdown.setPreferredSize(new Dimension(248, 24));
        scoreDropdown.addActionListener((ActionEvent e) -> {
            // Don't use setAlgorithm() because we don't need to determine if
            // enable/disable the test and score dropdown menus again - Zhou
            if (scoreDropdown.getSelectedItem() != null) {
                AnnotatedClassWrapper<Score> scoreWrapper = (AnnotatedClassWrapper<Score>) scoreDropdown.getSelectedItem();
                setDefaultScore(scoreWrapper);
                setScoreType(scoreWrapper.getName());
            }
        });

        algoTypeAllRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Update the selected algo type to null
                selectedAlgoType = null;

                // Update the list
                updateSuggestedAlgosList();
            }
        });
        priorKnowledgeCheckbox.addActionListener((e) -> {
            onlyShowAlgoAcceptKnowledge = priorKnowledgeCheckbox.isSelected();
            updateSuggestedAlgosList();
        });
        linearVariablesCheckbox.addActionListener((ActionEvent actionEvent) -> {
            linearVariablesCheckboxActionPerformed(actionEvent);
        });
        gaussianVariablesCheckbox.addActionListener((ActionEvent actionEvent) -> {
            gaussianVariablesCheckboxActionPerformed(actionEvent);
        });

        graphContainer.setPreferredSize(new Dimension(940, 580));

        algoDescriptionTextArea.setWrapStyleWord(true);
        algoDescriptionTextArea.setLineWrap(true);
        algoDescriptionTextArea.setEditable(false);

        suggestedAlgosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestedAlgosList.addListSelectionListener((e) -> {
            // More about why use getValueIsAdjusting()
            // http://docs.oracle.com/javase/8/docs/api/javax/swing/ListSelectionModel.html#getValueIsAdjusting--
            if (!e.getValueIsAdjusting()) {
                // After selecting a different algo type, even though we set the selection index,
                // but it won't be captured here - Zhou
                // Seems this only captures mouse selection
                if (suggestedAlgosList.getSelectedValue() == null) {
                    return;
                }

                selectedAgloWrapper = suggestedAlgosList.getSelectedValue();

                // Set description
                setAlgoDescriptionContent();

                // Update the test and score dropdown menus
                // and set all other parameters
                setAlgorithm();
            }
        });

        setLayout(new CardLayout());

        add(new AlgorithmCard(), ALGORITHM_CARD);
        add(new ParameterCard(), PARAMETER_CARD);
        add(new GraphCard(), GRAPH_CARD);

        setPreferredSize(new Dimension(940, 640));
    }

    private void setDefaultScore(AnnotatedClassWrapper<Score> scoreWrapper) {
        Map<DataType, AnnotatedClassWrapper<Score>> map = algoDefaultScores.get(selectedAgloWrapper);
        if (map == null) {
            map = new EnumMap(DataType.class);
            algoDefaultScores.put(selectedAgloWrapper, map);
        }
        map.put(dataType, scoreWrapper);
    }

    private void setDefaultTest(AnnotatedClassWrapper<TestOfIndependence> testWrapper) {
        Map<DataType, AnnotatedClassWrapper<TestOfIndependence>> map = algoDefaultTests.get(selectedAgloWrapper);
        if (map == null) {
            map = new EnumMap(DataType.class);
            algoDefaultTests.put(selectedAgloWrapper, map);
        }
        map.put(dataType, testWrapper);
    }

    private void updateTestAndScoreOptions() {
        // Update the tests and scores list to show items that have @linear/Gaussian annotations
        filteredIndTests = tests;
        filteredScores = scores;

        if (linearRelationshipAssumption) {
            filteredIndTests = AnnotatedClassUtils.filterByAnnotations(Linear.class, tests);
            filteredScores = AnnotatedClassUtils.filterByAnnotations(Linear.class, scores);
        }

        if (gaussianVariablesAssumption) {
            filteredIndTests = AnnotatedClassUtils.filterByAnnotations(Gaussian.class, tests);
            filteredScores = AnnotatedClassUtils.filterByAnnotations(Gaussian.class, scores);
        }

        // Recreate the test and score dropdowns
        setTestAndScoreDropdownModels(filteredIndTests, filteredScores);
    }

    private void setSelection() {
        setDefaultSelectedAlgo();
        setAlgoDescriptionContent();
    }

    private void setAlgorithm() {
        if (selectedAgloWrapper != null) {
            // Determine if enable/disable test and score dropdowns
            setTestDropdown();
            setScoreDropdown();

            // Determine if enable/disable the checkboxes of assumptions
            setAssumptions();

            setAlgorithmRunner();

            // Set runner parameters for target algo
            parameters.set("testEnabled", testDropdown.isEnabled());
            parameters.set("scoreEnabled", scoreDropdown.isEnabled());

            parameters.set("algName", selectedAgloWrapper.getName());
            parameters.set("algType", selectedAgloWrapper.getAnnotatedClass().getAnnotation().algoType());

            Object obj = testDropdown.getSelectedItem();
            if (obj != null) {
                setTestType(((AnnotatedClassWrapper<TestOfIndependence>) obj).getName());
            }
            obj = scoreDropdown.getSelectedItem();
            if (obj != null) {
                setScoreType(((AnnotatedClassWrapper<Score>) obj).getName());
            }

            // Also need to update the corresponding parameters
            parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
            // Remove all and add new
            parametersBox.removeAll();
            parametersBox.add(parametersPanel);
        }
    }

    private Parameters getParameters() {
        return parameters;
    }

    private void setTestType(String testType) {
        parameters.set("testType", testType);
    }

    private void setScoreType(String scoreType) {
        parameters.set("scoreType", scoreType);
    }

    private void setAlgorithmRunner() {
        // Set the algo on each selection change
        Algorithm algorithm = getAlgorithmFromInterface();

        runner.setAlgorithm(algorithm);
    }

    public Algorithm getAlgorithmFromInterface() {
        if (selectedAgloWrapper == null) {
            throw new NullPointerException();
        }

        IndependenceWrapper independenceWrapper = getIndependenceWrapper();
        ScoreWrapper scoreWrapper = getScoreWrapper();

        Class algoClass = selectedAgloWrapper.getAnnotatedClass().getClazz();

//        Algorithm algorithm = getAlgorithm(selectedAlgoName, independenceWrapper, scoreWrapper);
        Algorithm algorithm = null;
        try {
            algorithm = AlgorithmFactory.create(algoClass, independenceWrapper, scoreWrapper);
        } catch (IllegalAccessException | InstantiationException exception) {
            // todo : use logger
            exception.printStackTrace(System.err);
        }

        // Those pairwise algos (EB, R1, R2,..) require source graph to initialize - Zhou
        if (algorithm != null && algorithm instanceof TakesInitialGraph) {
            Algorithm initialGraph = null;

            if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                initialGraph = new SingleGraphAlg(runner.getSourceGraph());
            }

            // Capture the exception message and show in a message dialog - Zhou
            try {
                // When the initialGraph is null, the setter will throw an exception - Zhou
                ((TakesInitialGraph) algorithm).setInitialGraph(initialGraph);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(desktop, e.getMessage(), "Please Note", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        return algorithm;
    }

    private ScoreWrapper getScoreWrapper() {
        ScoreWrapper scoreWrapper = null;

        Object obj = scoreDropdown.getSelectedItem();
        if (obj != null) {
            AnnotatedClassWrapper<Score> score = (AnnotatedClassWrapper<Score>) obj;
            Class scoreClass = score.getAnnotatedClass().getClazz();

            try {
                scoreWrapper = (ScoreWrapper) scoreClass.newInstance();
            } catch (IllegalAccessException | InstantiationException exception) {
                // log this error
                throw new IllegalArgumentException("Please configure that score: " + score);
            }
        }

        return scoreWrapper;
    }

    private IndependenceWrapper getIndependenceWrapper() {
        IndependenceWrapper independenceWrapper = null;

        Object obj = testDropdown.getSelectedItem();
        if (obj != null) {
            AnnotatedClassWrapper<TestOfIndependence> test = (AnnotatedClassWrapper<TestOfIndependence>) obj;
            Class indTestClass = test.getAnnotatedClass().getClazz();

            try {
                independenceWrapper = (IndependenceWrapper) indTestClass.newInstance();
            } catch (IllegalAccessException | InstantiationException exception) {
                // log this error
            }

            if (independenceWrapper != null) {
                // do independence test for each dataset
                List<IndependenceTest> testList = new ArrayList<>();
                for (DataModel dataModel : runner.getDataModelList()) {
                    IndependenceTest _test = independenceWrapper.getTest(dataModel, parameters);
                    testList.add(_test);
                }
                runner.setIndependenceTests(testList);
            }
        }

        return independenceWrapper;
    }

    private void setAssumptions() {
        // disable assumptions checkboxes when both test and score dropdowns are disabled
        boolean disabled = !testDropdown.isEnabled() && !scoreDropdown.isEnabled();

        linearVariablesCheckbox.setEnabled(!disabled);
        gaussianVariablesCheckbox.setEnabled(!disabled);
    }

    private void setTestDropdown() {
        // Get annotated algo
        TetradAlgorithmAnnotations algoAnno = TetradAlgorithmAnnotations.getInstance();
        Class algoClass = selectedAgloWrapper.getAnnotatedClass().getClazz();

        // Determine if enable/disable test and score dropdowns
        testDropdown.setEnabled(algoAnno.requireIndependenceTest(algoClass));
        if (testDropdown.isEnabled()) {
            if (parameters.getString("testType") != null) {
                String previousTestType = parameters.getString("testType");
                for (int i = 0; i < testDropdownModel.getSize(); i++) {
                    AnnotatedClassWrapper<TestOfIndependence> test = testDropdownModel.getElementAt(i);
                    if (test.getName().equalsIgnoreCase(previousTestType)) {
                        testDropdownModel.setSelectedItem(test);
                        break;
                    }
                }
            } else {
                Map<DataType, AnnotatedClassWrapper<TestOfIndependence>> map = algoDefaultTests.get(selectedAgloWrapper);
                if (map == null) {
                    map = new EnumMap<>(DataType.class);
                    algoDefaultTests.put(selectedAgloWrapper, map);
                }

                AnnotatedClassWrapper<TestOfIndependence> defaultTest = map.get(dataType);
                if (defaultTest == null) {
                    defaultTest = TetradTestOfIndependenceAnnotations.getInstance().getDefaultNameWrapper(dataType);
                    if (defaultTest == null && testDropdownModel.getSize() > 0) {
                        defaultTest = testDropdownModel.getElementAt(0);
                    }

                    map.put(dataType, defaultTest);
                }
                testDropdownModel.setSelectedItem(defaultTest);
            }
        }
    }

    private void setScoreDropdown() {
        // Get annotated algo
        TetradAlgorithmAnnotations algoAnno = TetradAlgorithmAnnotations.getInstance();
        Class algoClass = selectedAgloWrapper.getAnnotatedClass().getClazz();

        // Determine if enable/disable test and score dropdowns
        scoreDropdown.setEnabled(algoAnno.requireScore(algoClass));
        if (scoreDropdown.isEnabled()) {
            if (parameters.getString("scoreType") != null) {
                String previousScoreType = parameters.getString("scoreType");
                for (int i = 0; i < scoreDropdownModel.getSize(); i++) {
                    AnnotatedClassWrapper<Score> score = scoreDropdownModel.getElementAt(i);
                    if (score.getName().equalsIgnoreCase(previousScoreType)) {
                        scoreDropdownModel.setSelectedItem(score);
                        break;
                    }
                }
            } else {
                Map<DataType, AnnotatedClassWrapper<Score>> map = algoDefaultScores.get(selectedAgloWrapper);
                if (map == null) {
                    map = new EnumMap(DataType.class);
                    algoDefaultScores.put(selectedAgloWrapper, map);
                }

                AnnotatedClassWrapper<Score> defaultScore = map.get(dataType);
                if (defaultScore == null) {
                    defaultScore = TetradScoreAnnotations.getInstance().getDefaultNameWrapper(dataType);
                    if (defaultScore == null && scoreDropdownModel.getSize() > 0) {
                        defaultScore = scoreDropdownModel.getElementAt(0);
                    }

                    map.put(dataType, defaultScore);
                }
                scoreDropdownModel.setSelectedItem(defaultScore);
            }
        }
    }

    private void setTestAndScoreDropdownModels(List<AnnotatedClassWrapper<TestOfIndependence>> tests, List<AnnotatedClassWrapper<Score>> scores) {
        // First remove all elements from combox model before recreation
        testDropdownModel.removeAllElements();
        scoreDropdownModel.removeAllElements();

        // Recreate the dropdown menus
        tests.forEach((test) -> {
            testDropdownModel.addElement(test);
        });

        scores.forEach((score) -> {
            scoreDropdownModel.addElement(score);
        });
    }

    private void determineTestAndScore(DataModelList dataModelList) {
        // Use annotations to get the tests based on data type
        TetradTestOfIndependenceAnnotations indTestAnno = TetradTestOfIndependenceAnnotations.getInstance();
        // Use annotations to get the scores based on data type
        TetradScoreAnnotations scoreAnno = TetradScoreAnnotations.getInstance();

        // Determine the test/score dropdown menu options based on dataset
        if (dataModelList.isEmpty()) {
            dataType = DataType.Graph;
            tests = indTestAnno.getNameWrappers(DataType.Graph);
            scores = scoreAnno.getNameWrappers(DataType.Graph);
        } else {
            // Check type based on the first dataset
            DataModel dataSet = dataModelList.get(0);

            // Covariance dataset is continuous at the same time - Zhou
            if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
                dataType = DataType.Continuous;
                tests = indTestAnno.getNameWrappers(DataType.Continuous);
                scores = scoreAnno.getNameWrappers(DataType.Continuous);
            } else if (dataSet.isDiscrete()) {
                dataType = DataType.Discrete;
                tests = indTestAnno.getNameWrappers(DataType.Discrete);
                scores = scoreAnno.getNameWrappers(DataType.Discrete);
            } else if (dataSet.isMixed()) {
                dataType = DataType.Mixed;
                tests = indTestAnno.getNameWrappers(DataType.Mixed);
                scores = scoreAnno.getNameWrappers(DataType.Mixed);
            } else if (dataSet instanceof ICovarianceMatrix) { // Better to add an isCovariance() - Zhou
                dataType = DataType.Covariance;
                tests = indTestAnno.getNameWrappers(DataType.Covariance);
                scores = scoreAnno.getNameWrappers(DataType.Covariance);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private void setDefaultAlgosListModel() {
        // Clear the list model
        suggestedAlgosListModel.removeAllElements();

        algoWrappers.forEach(e -> {
            suggestedAlgosListModel.addElement(e);
        });
    }

    private void updateSuggestedAlgosList() {
        // Clear the list model
        suggestedAlgosListModel.removeAllElements();

        // Algo type, knowledge file
        List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> filteredAlgosByType = new LinkedList<>();
        List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> filteredAlgosByKnowledgeFile = new LinkedList<>();

        // Don't assign algoWrappers directly to the above three lists since algoWrappers is unmodifiableList
        // Iterate over algoWrappers so all the three lists contain all algos at the beginning
        algoWrappers.forEach(algoWrapper -> {
            filteredAlgosByType.add(algoWrapper);
            filteredAlgosByKnowledgeFile.add(algoWrapper);
        });

        // Remove algos that are not the selected type from filteredAlgosByType if a specific algo type is selected
        if (selectedAlgoType != null) {
            algoWrappers.forEach(algoWrapper -> {
                edu.cmu.tetrad.annotation.Algorithm annotation = algoWrapper.getAnnotatedClass().getAnnotation();

                if (annotation.algoType() != selectedAlgoType) {
                    filteredAlgosByType.remove(algoWrapper);
                }
            });
        }

        // Remove algos that don't meet the prior knowledge file selection
        if (onlyShowAlgoAcceptKnowledge) {
            algoWrappers.forEach(algoWrapper -> {
                Class clazz = algoWrapper.getAnnotatedClass().getClazz();

                // Remove algo if the the flag doesn't equal to the acceptKnowledge(clazz)
                if (!TetradAlgorithmAnnotations.getInstance().acceptKnowledge(clazz)) {
                    filteredAlgosByKnowledgeFile.remove(algoWrapper);
                }
            });
        }

        // Now get intersections of all filters
        // filteredAlgosByType now contains only the elements which are also contained in filteredAlgosByKnowledgeFile
        filteredAlgosByType.retainAll(filteredAlgosByKnowledgeFile);

        // Add the filtered elements to suggestedAlgosListModel
        filteredAlgosByType.forEach(algoWrapper -> {
            suggestedAlgosListModel.addElement(algoWrapper);
        });

        // Reset default selected algorithm
        setSelection();
    }

    private void doSearch(final GeneralAlgorithmRunner runner) {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                HpcAccount hpcAccount = null;

                String algoName = selectedAgloWrapper.getName().toUpperCase();

                switch (algoName) {
                    case "FGES":
                    case "GFCI":
                        hpcAccount = showRemoteComputingOptions(algoName);
                        break;
                    default:
                }

                if (hpcAccount == null) {
                    graphEditor.saveLayout();

                    runner.execute();

                    // Show graph
                    graphEditor.replace(runner.getGraphs());
                    graphEditor.validate();
                    firePropertyChange("modelChanged", null, null);

                    // Update the graphContainer
                    graphContainer.add(graphEditor);

                    changeCard(GRAPH_CARD);
                } else {
                    try {
                        doRemoteCompute(runner, hpcAccount);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

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
            String algorithmName;
            Algorithm algorithm = runner.getAlgorithm();
            System.out.println("Algorithm: " + algorithm.getDescription());

            switch (selectedAgloWrapper.getName().toUpperCase()) {
                case "FGES":
                    algorithmName = AbstractAlgorithmRequest.FGES;
                    if (dataModel.isDiscrete()) {
                        algorithmName = AbstractAlgorithmRequest.FGES_DISCRETE;
                    }
                    break;
                case "GFCI":
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

        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        } finally {
            progressDialog.setVisible(false);
            progressDialog.dispose();
        }

        (new HpcJobActivityAction("")).actionPerformed(null);

    }

    private HpcAccount showRemoteComputingOptions(String name) {
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

        int n = JOptionPane.showOptionDialog(this, "Would you like to execute a " + name + " search in the cloud?",
                "A Silly Question", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (n == 0) {
            return null;
        }
        return hpcAccounts.get(n - 1);
    }

    private void setDefaultSelectedAlgo() {
        if (!suggestedAlgosListModel.isEmpty()) {
            // setSelectedIndex() triggers the suggested algos list listener,
            // so no need to call setAlgorithm() to set the selected algo
            // and update the test and score dropdown menus.
            suggestedAlgosList.setSelectedIndex(0);

            selectedAgloWrapper = suggestedAlgosList.getSelectedValue();
        }
    }

    private void setAlgoDescriptionContent() {
        if (!suggestedAlgosListModel.isEmpty() && selectedAgloWrapper != null) {
            edu.cmu.tetrad.annotation.Algorithm agloAnno = selectedAgloWrapper.getAnnotatedClass().getAnnotation();
            algoDescriptionTextArea.setText(agloAnno.description());
            algoDescriptionTextArea.setCaretPosition(0);
        } else {
            // Erase the previous content
            algoDescriptionTextArea.setText("");
        }
    }

    public void setAlgorithmErrorResult(String errorResult) {
        JOptionPane.showMessageDialog(desktop, jsonResult);
        throw new IllegalArgumentException(errorResult);
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

            setBorder(BorderFactory.createTitledBorder("Choose Independence Test and Score"));
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
                                                                            .addComponent(gaussianVariablesCheckbox)
                                                                            .addComponent(linearVariablesCheckbox))))
                                                    .addGap(0, 0, Short.MAX_VALUE))
                                            .addGroup(layout.createSequentialGroup()
                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addComponent(testLabel)
                                                            .addComponent(scoreLabel))
                                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addComponent(scoreDropdown, 0, 239, Short.MAX_VALUE)
                                                            .addComponent(testDropdown, 0, 239, Short.MAX_VALUE))))
                                    .addContainerGap())
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(assumptionsLabel)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(linearVariablesCheckbox)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(gaussianVariablesCheckbox)
                                    .addGap(22, 22, 22)
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                            .addComponent(testLabel)
                                            .addComponent(testDropdown, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                            .addComponent(scoreDropdown, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                            .addComponent(scoreLabel))
                                    .addContainerGap())
            );
        }

    }

    private JPanel createParameterMainPannel() {
        // Parameters
        // This is only the parameters pane of the default algorithm - Zhou
        parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
        parametersBox.add(parametersPanel);

        JPanel parameterPanel = new JPanel(new BorderLayout());
        parameterPanel.setBorder(BorderFactory.createTitledBorder("Specify Algorithm Parameters"));
        parameterPanel.add(new PaddingPanel(parametersBox), BorderLayout.CENTER);

        return new PaddingPanel(parameterPanel);
    }

    private JPanel createAlgoFilterPannel() {
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

        // Add to button group
        algoTypesBtnGrp.add(algoTypeAllRadioBtn);

        // Add padding and option
        algoTypeOptionAllBox.add(Box.createRigidArea(new Dimension(10, 20)));
        algoTypeOptionAllBox.add(algoTypeAllRadioBtn);

        // Add all option to containing box
        algoTypesBox.add(algoTypeOptionAllBox);

        // Show each algo type as a radio button
        for (AlgType item : AlgType.values()) {
            String algoType = item.toString().replace("_", " ");

            // Option
            Box algoTypeOptionBox = Box.createHorizontalBox();
            algoTypeOptionBox.setAlignmentX(LEFT_ALIGNMENT);

            JRadioButton algoTypeRadioBtn = new JRadioButton(algoType);

            // Add to button group
            algoTypesBtnGrp.add(algoTypeRadioBtn);

            // Add padding and option
            algoTypeOptionBox.add(Box.createRigidArea(new Dimension(10, 20)));
            algoTypeOptionBox.add(algoTypeRadioBtn);

            // Add each option to containing box
            algoTypesBox.add(algoTypeOptionBox);

            // Event listener on each radio button
            algoTypeRadioBtn.addActionListener(e -> {
                JRadioButton button = (JRadioButton) e.getSource();
                if (button.isSelected()) {
                    // Update the selected algo type
                    selectedAlgoType = AlgType.valueOf(button.getText().replace(" ", "_"));

                    // Update the list
                    updateSuggestedAlgosList();
                }
            });
        }

        // Set All as the default selection
        algoTypeAllRadioBtn.setSelected(true);

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
        priorKnowledgeOptionBox.add(priorKnowledgeCheckbox);

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

    private JPanel createAlgorithmMainPanel() {
        JButton resetSettingsBtn = new JButton("Reset All Settings");
        resetSettingsBtn.addActionListener((e) -> {
            resetSettingsBtnActionPerformed(e);
        });

        JPanel westMainSouthPanel = new JPanel(new BorderLayout(0, 10));
        westMainSouthPanel.add(new TestAndScorePanel(), BorderLayout.CENTER);
        westMainSouthPanel.add(resetSettingsBtn, BorderLayout.SOUTH);

        JPanel westMainWestPanel = new JPanel(new BorderLayout(0, 10));
        westMainWestPanel.add(createAlgoFilterPannel(), BorderLayout.CENTER);
        westMainWestPanel.add(westMainSouthPanel, BorderLayout.SOUTH);

        JPanel westMainPanel = new JPanel(new BorderLayout(5, 0));
        westMainPanel.add(westMainWestPanel, BorderLayout.WEST);
        westMainPanel.add(new AlgoListPanel(), BorderLayout.EAST);

        JPanel algoCard = new JPanel(new BorderLayout(10, 0));
        algoCard.add(westMainPanel, BorderLayout.WEST);
        algoCard.add(new AlgoDescPanel(), BorderLayout.CENTER);

        return new PaddingPanel(algoCard);
    }

    private void resetSettingsBtnActionPerformed(ActionEvent actionEvent) {
        selectedAlgoType = null;  // reset algo type to All
        algoTypesBtnGrp.setSelected(algoTypeAllRadioBtn.getModel(), true);
        onlyShowAlgoAcceptKnowledge = false;  //also need to reset the knowledge file flag
        priorKnowledgeCheckbox.setSelected(false);  // uncheck prior knowledge checkbox
        gaussianVariablesCheckbox.setSelected(false);
        linearVariablesCheckbox.setSelected(false);
        setDefaultAlgosListModel();  // don't forget to update the list of algos
        setSelection();  // reset default selected algorithm

        gaussianVariablesCheckboxActionPerformed(actionEvent);
        linearVariablesCheckboxActionPerformed(actionEvent);
    }

    private void gaussianVariablesCheckboxActionPerformed(ActionEvent actionEvent) {
        gaussianVariablesAssumption = gaussianVariablesCheckbox.isSelected();  // set the flag
        updateTestAndScoreOptions();  // recreate the dropdown
    }

    private void linearVariablesCheckboxActionPerformed(ActionEvent actionEvent) {
        linearRelationshipAssumption = linearVariablesCheckbox.isSelected();  // set the flag
        updateTestAndScoreOptions();  // recreate the dropdown
    }

    private void algoCardFwdBtnActionPerformed(ActionEvent actionEvent) {
        setAlgorithm();
        changeCard(PARAMETER_CARD);
    }

    private void paramCardFwdBtnActionPerformed(ActionEvent actionEvent) {
        doSearch(runner);
    }

    private void paramCardBackBtnActionPerformed(ActionEvent actionEvent) {
        changeCard(ALGORITHM_CARD);
    }

    private void graphCardBackBtnActionPerformed(ActionEvent actionEvent) {
        changeCard(PARAMETER_CARD);
    }

    private void changeCard(String card) {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.show(this, card);
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

    private class AlgoListPanel extends JPanel {

        private static final long serialVersionUID = -7068543172769683902L;

        private JScrollPane suggestedAlgosListScrollPane;

        public AlgoListPanel() {
            initComponents();
        }

        private void initComponents() {
            suggestedAlgosListScrollPane = new JScrollPane(suggestedAlgosList);

            setBorder(BorderFactory.createTitledBorder("Choose Algorithm"));
            setPreferredSize(new Dimension(230, 300));

            GroupLayout layout = new GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(suggestedAlgosListScrollPane, GroupLayout.DEFAULT_SIZE, 206, Short.MAX_VALUE)
                                    .addContainerGap())
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(suggestedAlgosListScrollPane, GroupLayout.DEFAULT_SIZE, 254, Short.MAX_VALUE)
                                    .addContainerGap())
            );
        }

    }

    private class AlgoDescPanel extends JPanel {

        private static final long serialVersionUID = -4159055717661942076L;

        private JScrollPane scrollPane;

        public AlgoDescPanel() {
            initComponents();
        }

        private void initComponents() {
            scrollPane = new JScrollPane(algoDescriptionTextArea);

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

        private JButton forwardBtn;

        public AlgorithmCard() {
            initComponents();
        }

        private void initComponents() {
            setLayout(new BorderLayout());

            Dimension buttonSize = new Dimension(268, 25);

            forwardBtn = new JButton("Set Parameters   >");
            forwardBtn.setMinimumSize(buttonSize);
            forwardBtn.setMaximumSize(buttonSize);
            forwardBtn.addActionListener((e) -> {
                algoCardFwdBtnActionPerformed(e);
            });

            add(createAlgorithmMainPanel(), BorderLayout.CENTER);
            add(new SouthPanel(forwardBtn), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = -4055772024145978761L;

            private final JButton forwardBtn;

            public SouthPanel(JButton forwardBtn) {
                this.forwardBtn = forwardBtn;
                initComponents();
            }

            private void initComponents() {
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
                paramCardBackBtnActionPerformed(e);
            });

            forwardBtn = new JButton("Run Search & Generate Graph   >");
            forwardBtn.setMinimumSize(buttonSize);
            forwardBtn.setMaximumSize(buttonSize);
            forwardBtn.addActionListener((e) -> {
                paramCardFwdBtnActionPerformed(e);
            });

            add(createParameterMainPannel(), BorderLayout.CENTER);
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
                graphCardBackBtnActionPerformed(e);
            });

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
