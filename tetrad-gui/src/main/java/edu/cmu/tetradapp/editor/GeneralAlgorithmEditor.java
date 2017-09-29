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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.score.*;
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
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import org.apache.commons.lang3.StringUtils;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements FinalizingEditor {

    private static final long serialVersionUID = -5719467682865706447L;

    private final GeneralAlgorithmRunner runner;
    private Box parametersBox;
    private Box graphContainer;
    private JComboBox<AnnotatedClassWrapper<TestOfIndependence>> testDropdown;
    private JComboBox<AnnotatedClassWrapper<Score>> scoreDropdown;
    private DefaultComboBoxModel<AnnotatedClassWrapper<TestOfIndependence>> testDropdownModel = new DefaultComboBoxModel<>();
    private DefaultComboBoxModel<AnnotatedClassWrapper<Score>> scoreDropdownModel = new DefaultComboBoxModel<>();
    private final GraphSelectionEditor graphEditor;
    private final Parameters parameters;
    private final TetradDesktop desktop;
    private HpcJobInfo hpcJobInfo;
    private String jsonResult;
    private final List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> algoWrappers;
    private final List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> algorithmsAcceptKnowledge;
    private final List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> algorithmsHandleUnmeasuredConfounder;
    private List<AnnotatedClassWrapper<TestOfIndependence>> tests;
    private List<AnnotatedClassWrapper<Score>> scores;
    private List<AnnotatedClassWrapper<TestOfIndependence>> filteredIndTests;
    private List<AnnotatedClassWrapper<Score>> filteredScores;
    private final DefaultListModel<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> suggestedAlgosListModel = new DefaultListModel<>();
    private final JList<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> suggestedAlgosList;
    private AlgType selectedAlgoType = null;
    private Boolean acceptKnowledgeFile = null;
    private Boolean handleUnmeasuredConfounders = null;
    private final ButtonGroup algoTypesBtnGrp = new ButtonGroup();
    private final ButtonGroup priorKnowledgeBtnGrp = new ButtonGroup();
    private final ButtonGroup unmeasuredConfoundersBtnGrp = new ButtonGroup();
    private AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm> selectedAgloWrapper;
    private final JTextArea algoDescriptionTextArea = new JTextArea();
    private ParameterPanel parametersPanel;
    private JDialog loadingIndicatorDialog = new JDialog();
    private JButton step1BackBtn;
    private JButton step2Btn;
    private JButton step2BackBtn;
    private JButton step3Btn;

    private JRadioButton algoTypeAllRadioBtn;
    private JRadioButton priorKnowledgeAllRadioBtn;
    private JRadioButton unmeasuredConfoundersAllRadioBtn;

    // Assumption checkboxes
    private JCheckBox linearVariablesCheckbox;
    private JCheckBox gaussianVariablesCheckbox;

    // Assumption flags
    private boolean linearRelationshipAssumption = false;
    private boolean gaussianVariablesAssumption = false;

    //=========================CONSTRUCTORS============================//
    /**
     * Opens up an editor to let the user view the given PcRunner.
     *
     * @param runner
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.runner = runner;

        this.desktop = (TetradDesktop) DesktopController.getInstance();

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

        // Use annotations to populate algo list
        // Only show algorithms that support multi dataset if there are multi datasets uploaded
        // Otherwise show all algorithms that take at least one dataset
        if (dataModelList.size() > 1) {
            algoWrappers = TetradAlgorithmAnnotations.getInstance().getAcceptMultipleDatasetNameWrappers();
        } else {
            algoWrappers = TetradAlgorithmAnnotations.getInstance().getNameWrappers();
        }

        // Algos that accept knowledge file
        // Later use this to filter algos based on the knowledge siwtch
        algorithmsAcceptKnowledge = TetradAlgorithmAnnotations.getInstance().getAcceptKnowledgeNameWrappers();

        // Algos that can handle unmeasured confounders
        // Later use this to filter algos based on the unmeasured confounders siwtch
        algorithmsHandleUnmeasuredConfounder = TetradAlgorithmAnnotations.getInstance().getUnmeasuredConfounderNameWrappers();

        // Use annotations to get the tests and scores based on different data types
        // Need to do this before calling createAlgoChooserPanel() - Zhou
        determineTestAndScore(dataModelList);

        // Create default models of test and score dropdowns
        setTestAndScoreDropdownModels(tests, scores);

        // Create default algos list model
        setDefaultAlgosListModel();

        // Suggested algo list
        suggestedAlgosList = new JList<>(suggestedAlgosListModel);

        this.parameters = runner.getParameters();

        graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(runner.getGraphs(), new Parameters()));

        // Embed the algo chooser panel into EditorWindow
        add(createAlgoChooserPanel(), BorderLayout.CENTER);

        // Default to select the first algo name in list
        setSelection();
    }

    private void determineTestAndScore(DataModelList dataModelList) {
        // Use annotations to get the tests based on data type
        TetradTestOfIndependenceAnnotations indTestAnno = TetradTestOfIndependenceAnnotations.getInstance();
        // Use annotations to get the scores based on data type
        TetradScoreAnnotations scoreAnno = TetradScoreAnnotations.getInstance();

        // Determine the test/score dropdown menu options based on dataset
        if (dataModelList.isEmpty()) {
            tests = indTestAnno.getNameWrappers(DataType.Graph);
            scores = scoreAnno.getNameWrappers(DataType.Graph);
        } else {
            // Check type based on the first dataset
            DataModel dataSet = dataModelList.get(0);

            // Covariance dataset is continuous at the same time - Zhou
            if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
                tests = indTestAnno.getNameWrappers(DataType.Continuous);
                scores = scoreAnno.getNameWrappers(DataType.Continuous);
            } else if (dataSet.isDiscrete()) {
                tests = indTestAnno.getNameWrappers(DataType.Discrete);
                scores = scoreAnno.getNameWrappers(DataType.Discrete);
            } else if (dataSet.isMixed()) {
                tests = indTestAnno.getNameWrappers(DataType.Mixed);
                scores = scoreAnno.getNameWrappers(DataType.Mixed);
            } else if (dataSet instanceof ICovarianceMatrix) { // Better to add an isCovariance() - Zhou
                tests = indTestAnno.getNameWrappers(DataType.Covariance);
                scores = scoreAnno.getNameWrappers(DataType.Covariance);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    // Use this inside the assumptions checkboxes event listener to update the tests and scores
    // based on the selections of those checkboxes
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

    private JPanel createAlgoChooserPanel() {
        // Overall container
        // contains data preview panel, loading params panel, and load button
        Box container = Box.createVerticalBox();
        // Must set the size of container
        container.setPreferredSize(new Dimension(940, 640));

        // Algo selection container, step 1
        // contains 3 columns, leftContainer, middleContainer, and rightContainer
        Box algoChooserContainer = Box.createHorizontalBox();
        algoChooserContainer.setPreferredSize(new Dimension(940, 560));

        // Parameters container, step 2
        Box parametersContainer = Box.createHorizontalBox();
        parametersContainer.setPreferredSize(new Dimension(940, 560));

        // Graph container, step 3
        graphContainer = Box.createHorizontalBox();
        graphContainer.setPreferredSize(new Dimension(940, 580));

        // Contains data description and result description
        Box leftContainer = Box.createVerticalBox();
        leftContainer.setPreferredSize(new Dimension(340, 560));

        Box middleContainer = Box.createVerticalBox();
        middleContainer.setPreferredSize(new Dimension(250, 560));

        // Contains algo list, algo description, test, score, and parameters
        Box rightContainer = Box.createVerticalBox();
        rightContainer.setPreferredSize(new Dimension(340, 560));

        // Describe your data and result using these filters
        Box algoFiltersBox = Box.createVerticalBox();
        algoFiltersBox.setMinimumSize(new Dimension(330, 550));
        algoFiltersBox.setMaximumSize(new Dimension(330, 550));
        algoFiltersBox.setAlignmentX(LEFT_ALIGNMENT);

        // Use a titled border with 5 px inside padding - Zhou
        String algoFiltersBoxBorderTitle = "Algorithm filters";
        algoFiltersBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(algoFiltersBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Filter based on algo types dropdown
        Box algoTypesBox = Box.createVerticalBox();

        // Algo types label box
        Box algTypesBoxLabelBox = Box.createHorizontalBox();
        algTypesBoxLabelBox.add(new JLabel("Filter algorithms that: "));
        algTypesBoxLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Add label to containing box
        algoTypesBox.add(algTypesBoxLabelBox);

        // All option
        Box algoTypeOptionAllBox = Box.createHorizontalBox();
        algoTypeOptionAllBox.setAlignmentX(LEFT_ALIGNMENT);

        algoTypeAllRadioBtn = new JRadioButton("All");

        // Add to button group
        algoTypesBtnGrp.add(algoTypeAllRadioBtn);

        // Add padding and option
        algoTypeOptionAllBox.add(Box.createRigidArea(new Dimension(10, 20)));
        algoTypeOptionAllBox.add(algoTypeAllRadioBtn);

        // Add all option to containing box
        algoTypesBox.add(algoTypeOptionAllBox);

        // Event listener on each radio button
        algoTypeAllRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Update the selected algo type to null
                selectedAlgoType = null;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

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
            algoTypeRadioBtn.addActionListener((ActionEvent actionEvent) -> {
                JRadioButton button = (JRadioButton) actionEvent.getSource();

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
        priorKnowledgeLabelBox.add(new JLabel("Filter algorithms that: "));
        priorKnowledgeLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option all
        Box priorKnowledgeOptionAllBox = Box.createHorizontalBox();
        priorKnowledgeOptionAllBox.setAlignmentX(LEFT_ALIGNMENT);

        priorKnowledgeAllRadioBtn = new JRadioButton("Both");

        // Event listener
        priorKnowledgeAllRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                acceptKnowledgeFile = null;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        priorKnowledgeOptionAllBox.add(Box.createRigidArea(new Dimension(10, 20)));
        priorKnowledgeOptionAllBox.add(priorKnowledgeAllRadioBtn);

        // Option 1
        Box priorKnowledgeOption1Box = Box.createHorizontalBox();
        priorKnowledgeOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton priorKnowledgeYesRadioBtn = new JRadioButton("accept prior knowledge file");

        // Event listener
        priorKnowledgeYesRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                acceptKnowledgeFile = true;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        priorKnowledgeOption1Box.add(Box.createRigidArea(new Dimension(10, 20)));
        priorKnowledgeOption1Box.add(priorKnowledgeYesRadioBtn);

        // Option 2
        Box priorKnowledgeOption2Box = Box.createHorizontalBox();
        priorKnowledgeOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton priorKnowledgeNoRadioBtn = new JRadioButton("don't accept prior knowledge file");

        // Event listener
        priorKnowledgeNoRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                acceptKnowledgeFile = false;

                // Update the list model
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        priorKnowledgeOption2Box.add(Box.createRigidArea(new Dimension(10, 20)));
        priorKnowledgeOption2Box.add(priorKnowledgeNoRadioBtn);

        // We need to group the radio buttons, otherwise all can be selected
        priorKnowledgeBtnGrp.add(priorKnowledgeAllRadioBtn);
        priorKnowledgeBtnGrp.add(priorKnowledgeYesRadioBtn);
        priorKnowledgeBtnGrp.add(priorKnowledgeNoRadioBtn);

        // Set All as the default selection
        priorKnowledgeAllRadioBtn.setSelected(true);

        // Add to containg box
        priorKnowledgeBox.add(priorKnowledgeLabelBox);
        priorKnowledgeBox.add(priorKnowledgeOptionAllBox);
        priorKnowledgeBox.add(priorKnowledgeOption1Box);
        priorKnowledgeBox.add(priorKnowledgeOption2Box);

        // Can algorithms handle unmeasured confounders?
        Box unmeasuredConfoundersBox = Box.createVerticalBox();

        // Add label into this label box to size
        Box unmeasuredConfoundersLabelBox = Box.createHorizontalBox();
        unmeasuredConfoundersLabelBox.add(new JLabel("Filter algorithms that: "));
        unmeasuredConfoundersLabelBox.setAlignmentX(LEFT_ALIGNMENT);

        // Option all
        Box unmeasuredConfoundersOptionAllBox = Box.createHorizontalBox();
        unmeasuredConfoundersOptionAllBox.setAlignmentX(LEFT_ALIGNMENT);

        unmeasuredConfoundersAllRadioBtn = new JRadioButton("Both");

        // Event listener
        unmeasuredConfoundersAllRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                handleUnmeasuredConfounders = null;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        unmeasuredConfoundersOptionAllBox.add(Box.createRigidArea(new Dimension(10, 20)));
        unmeasuredConfoundersOptionAllBox.add(unmeasuredConfoundersAllRadioBtn);

        // Option 1
        Box unmeasuredConfoundersOption1Box = Box.createHorizontalBox();
        unmeasuredConfoundersOption1Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton unmeasuredConfoundersYesRadioBtn = new JRadioButton("can handle unmeasured confounders");

        // Event listener
        unmeasuredConfoundersYesRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                handleUnmeasuredConfounders = true;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        unmeasuredConfoundersOption1Box.add(Box.createRigidArea(new Dimension(10, 20)));
        unmeasuredConfoundersOption1Box.add(unmeasuredConfoundersYesRadioBtn);

        // Option 2
        Box unmeasuredConfoundersOption2Box = Box.createHorizontalBox();
        unmeasuredConfoundersOption2Box.setAlignmentX(LEFT_ALIGNMENT);

        JRadioButton unmeasuredConfoundersNoRadioBtn = new JRadioButton("can't handle unmeasured confounders");

        // Event listener
        unmeasuredConfoundersNoRadioBtn.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();

            if (button.isSelected()) {
                // Set the flag
                handleUnmeasuredConfounders = false;

                // Update the list
                updateSuggestedAlgosList();
            }
        });

        // Add padding and option
        unmeasuredConfoundersOption2Box.add(Box.createRigidArea(new Dimension(10, 20)));
        unmeasuredConfoundersOption2Box.add(unmeasuredConfoundersNoRadioBtn);

        // We need to group the radio buttons, otherwise all can be selected
        unmeasuredConfoundersBtnGrp.add(unmeasuredConfoundersAllRadioBtn);
        unmeasuredConfoundersBtnGrp.add(unmeasuredConfoundersYesRadioBtn);
        unmeasuredConfoundersBtnGrp.add(unmeasuredConfoundersNoRadioBtn);

        // Set All as the default selection
        unmeasuredConfoundersAllRadioBtn.setSelected(true);

        // Add to containing box
        unmeasuredConfoundersBox.add(unmeasuredConfoundersLabelBox);
        unmeasuredConfoundersBox.add(unmeasuredConfoundersOptionAllBox);
        unmeasuredConfoundersBox.add(unmeasuredConfoundersOption1Box);
        unmeasuredConfoundersBox.add(unmeasuredConfoundersOption2Box);
        unmeasuredConfoundersBox.add(Box.createHorizontalGlue());

        // Reset filter selections
        JButton resetFilterSelectionsBtn = new JButton("Reset all filters");

        // Event listener of clearFilterSelectionsBtn
        resetFilterSelectionsBtn.addActionListener((ActionEvent actionEvent) -> {
            resetAlgoFilters();
        });

        // Items to put in data description box
        algoFiltersBox.add(algoTypesBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(priorKnowledgeBox);
        algoFiltersBox.add(Box.createVerticalStrut(10));
        algoFiltersBox.add(unmeasuredConfoundersBox);
        algoFiltersBox.add(Box.createVerticalStrut(20));
        algoFiltersBox.add(resetFilterSelectionsBtn);

        // Add to leftContainer
        leftContainer.add(algoFiltersBox);

        // Components in middleContainer
        // Show a list of filtered algorithms
        Box suggestedAlgosBox = Box.createVerticalBox();
        suggestedAlgosBox.setMinimumSize(new Dimension(240, 550));
        suggestedAlgosBox.setMaximumSize(new Dimension(240, 550));

        // Use a titled border with 5 px inside padding - Zhou
        String suggestedAlgosBoxBorderTitle = "Choose algorithm";
        suggestedAlgosBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(suggestedAlgosBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // suggestedAlgosList
        // Only allow single selection
        suggestedAlgosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Event listener
        suggestedAlgosList.addListSelectionListener((ListSelectionEvent e) -> {
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

        // Put the list in a scrollable area
        JScrollPane suggestedAlgosListScrollPane = new JScrollPane(suggestedAlgosList);
        suggestedAlgosListScrollPane.setMinimumSize(new Dimension(260, 550));
        suggestedAlgosListScrollPane.setMaximumSize(new Dimension(260, 550));

        suggestedAlgosBox.add(suggestedAlgosListScrollPane);

        middleContainer.add(suggestedAlgosBox);

        // Components in rightContainer
        // Algo description
        Box algoDescriptionBox = Box.createVerticalBox();
        algoDescriptionBox.setMinimumSize(new Dimension(330, 335));
        algoDescriptionBox.setMaximumSize(new Dimension(330, 335));

        // Use a titled border with 5 px inside padding - Zhou
        String algoDescriptionBoxBorderTitle = "Algorithm description";
        algoDescriptionBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(algoDescriptionBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Set line arap
        algoDescriptionTextArea.setWrapStyleWord(true);
        algoDescriptionTextArea.setLineWrap(true);

        // Read only
        algoDescriptionTextArea.setEditable(false);

        JScrollPane algoDescriptionScrollPane = new JScrollPane(algoDescriptionTextArea);
        algoDescriptionScrollPane.setMinimumSize(new Dimension(330, 335));
        algoDescriptionScrollPane.setMaximumSize(new Dimension(330, 335));

        algoDescriptionBox.add(algoDescriptionScrollPane);

        // Choose corresponding test and score based on algorithm
        Box testAndScoreBox = Box.createVerticalBox();
        testAndScoreBox.setMinimumSize(new Dimension(330, 200));
        testAndScoreBox.setMaximumSize(new Dimension(330, 200));

        // Use a titled border with 5 px inside padding - Zhou
        String testAndScoreBoxBorderTitle = "Choose Independence Test and Score";
        testAndScoreBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(testAndScoreBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Assumptions label box
        Box assumptionsLabelBox = Box.createHorizontalBox();
        assumptionsLabelBox.setPreferredSize(new Dimension(330, 20));
        JLabel assumptionsLabel = new JLabel("Filter by dataset properties: ");
        //assumptionsLabelBox.setAlignmentX(LEFT_ALIGNMENT);
        assumptionsLabelBox.add(assumptionsLabel);
        assumptionsLabelBox.add(Box.createHorizontalGlue());

        Box linearRelationshipBox = Box.createHorizontalBox();
        linearRelationshipBox.add(Box.createRigidArea(new Dimension(10, 20)));
        linearVariablesCheckbox = new JCheckBox("Variables with linear relationship");

        // Register event listener on checkbox
        linearVariablesCheckbox.addActionListener((ActionEvent actionEvent) -> {
            // Set the flag
            linearRelationshipAssumption = linearVariablesCheckbox.isSelected();
            // Recreate the dropdown
            updateTestAndScoreOptions();
        });

        //linearVariablesBox.setAlignmentX(LEFT_ALIGNMENT);
        linearRelationshipBox.add(linearVariablesCheckbox);
        linearRelationshipBox.add(Box.createHorizontalGlue());

        Box gaussianVariablesBox = Box.createHorizontalBox();
        gaussianVariablesBox.add(Box.createRigidArea(new Dimension(10, 20)));
        gaussianVariablesCheckbox = new JCheckBox("Gaussian variables");

        // Register event listener on checkbox
        gaussianVariablesCheckbox.addActionListener((ActionEvent actionEvent) -> {
            // Set the flag
            gaussianVariablesAssumption = gaussianVariablesCheckbox.isSelected();
            // Recreate the dropdown
            updateTestAndScoreOptions();
        });

        //gaussianVariablesBox.setAlignmentX(LEFT_ALIGNMENT);
        gaussianVariablesBox.add(gaussianVariablesCheckbox);
        gaussianVariablesBox.add(Box.createHorizontalGlue());

        // Test container
        Box testBox = Box.createHorizontalBox();

        Box testLabelBox = Box.createHorizontalBox();
        testLabelBox.setPreferredSize(new Dimension(55, 15));
        JLabel testLabel = new JLabel("Test:");
        testLabelBox.add(testLabel);

        Box testSelectionBox = Box.createHorizontalBox();

        // Initialize test dropdown menu
        testDropdown = new JComboBox<>(testDropdownModel);

        testDropdown.setPreferredSize(new Dimension(240, 15));

        // Event listener of test seleciton
        testDropdown.addActionListener((ActionEvent e) -> {
            // Don't use setAlgorithm() because we don't need to determine if
            // enable/disable the test and score dropdown menus again - Zhou
            if (testDropdown.getSelectedItem() != null) {
                setTestType(((AnnotatedClassWrapper<TestOfIndependence>) testDropdown.getSelectedItem()).getName());
            }
        });

        testSelectionBox.add(testDropdown);

        testBox.add(testLabelBox);
        testBox.add(testSelectionBox);

        // Score container
        Box scoreBox = Box.createHorizontalBox();

        Box scoreLabelBox = Box.createHorizontalBox();
        scoreLabelBox.setPreferredSize(new Dimension(55, 15));
        JLabel scoreLabel = new JLabel("Score:");
        scoreLabelBox.add(scoreLabel);

        Box scoreSelectionBox = Box.createHorizontalBox();

        // Initialize score dropdown menu
        scoreDropdown = new JComboBox<>(scoreDropdownModel);
        scoreDropdown.setPreferredSize(new Dimension(240, 15));

        // Event listener of score seleciton
        scoreDropdown.addActionListener((ActionEvent e) -> {
            // Don't use setAlgorithm() because we don't need to determine if
            // enable/disable the test and score dropdown menus again - Zhou
            if (scoreDropdown.getSelectedItem() != null) {
                setScoreType(((AnnotatedClassWrapper<Score>) scoreDropdown.getSelectedItem()).getName());
            }
        });

        scoreSelectionBox.add(scoreDropdown);

        scoreBox.add(scoreLabelBox);
        scoreBox.add(scoreSelectionBox);

        // Add to testAndScoreBox
        testAndScoreBox.add(assumptionsLabelBox);
        testAndScoreBox.add(linearRelationshipBox);
        testAndScoreBox.add(gaussianVariablesBox);

        // Add some gap
        testAndScoreBox.add(Box.createVerticalStrut(10));
        testAndScoreBox.add(testBox);
        // Add some gap
        testAndScoreBox.add(Box.createVerticalStrut(10));
        testAndScoreBox.add(scoreBox);

        // Parameters
        parametersBox = Box.createVerticalBox();
        parametersBox.setMinimumSize(new Dimension(940, 570));
        parametersBox.setMaximumSize(new Dimension(940, 570));

        // Use a titled border with 5 px inside padding - Zhou
        String parametersBoxBorderTitle = "Specify algorithm parameters";
        parametersBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(parametersBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Parameters
        // This is only the parameters pane of the default algorithm - Zhou
        parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());

        parametersPanel.setMinimumSize(new Dimension(920, 590));
        parametersPanel.setMaximumSize(new Dimension(920, 590));

        // Add to parameters box
        parametersBox.add(parametersPanel);

        // Add to parametersContainer
        parametersContainer.add(parametersBox);

        // Back to step 1 button
        step1BackBtn = new JButton("< Choose Algorithm");

        // Step 1 button listener
        step1BackBtn.addActionListener((ActionEvent e) -> {
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
        });

        // Hide step 2
        parametersContainer.setVisible(false);

        // Parameters button
        step2Btn = new JButton("Set Parameters >");
        step2BackBtn = new JButton("< Set Parameters");

        // Step 2 button listener
        step2Btn.addActionListener((ActionEvent e) -> {
            // Show parameters
            parametersContainer.setVisible(true);

            // Hide algo step 1
            algoChooserContainer.setVisible(false);

            // SHow back to step 1 button and search button
            step1BackBtn.setVisible(true);
            step3Btn.setVisible(true);

            // Hide step 2 button
            step2Btn.setVisible(false);
        });

        // Step 2 button listener
        step2BackBtn.addActionListener((ActionEvent e) -> {
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
        });

        // Step 3 button
        step3Btn = new JButton("Run Search & Generate Graph >");

        step3Btn.addActionListener((ActionEvent e) -> {
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
        });

        // Add to rightContainer
        rightContainer.add(Box.createVerticalStrut(10));
        rightContainer.add(algoDescriptionBox);
        rightContainer.add(Box.createVerticalStrut(10));
        rightContainer.add(testAndScoreBox);

        // Buttons container
        Box buttonsContainer = Box.createVerticalBox();

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

        // Default to only show step 2 forward button
        step1BackBtn.setVisible(false);
        step2BackBtn.setVisible(false);
        step3Btn.setVisible(false);

        // Add to buttons container
        buttonsContainer.add(Box.createVerticalStrut(10));
        buttonsContainer.add(buttonsBox);

        // Add to algoChooserContainer as the first column
        algoChooserContainer.add(leftContainer);

        // Add some gap
        algoChooserContainer.add(Box.createHorizontalStrut(10));

        // Add middleContainer
        algoChooserContainer.add(middleContainer);

        // Add some gap
        algoChooserContainer.add(Box.createHorizontalStrut(10));

        // Add to algoChooserContainer as the third column
        algoChooserContainer.add(rightContainer);

        // Add to big panel
        container.add(algoChooserContainer);

        container.add(parametersContainer);

        container.add(graphContainer);

        container.add(buttonsContainer);

        JPanel p = new JPanel(new BorderLayout());
        p.add(container, BoxLayout.X_AXIS);

        return p;
    }

    private void setDefaultAlgosListModel() {
        // Clear the list model
        suggestedAlgosListModel.removeAllElements();

        algoWrappers.forEach(e -> {
            suggestedAlgosListModel.addElement(e);
        });
    }

    private void setAlgoDescriptionContent() {
        if (!suggestedAlgosListModel.isEmpty() && selectedAgloWrapper != null) {
            edu.cmu.tetrad.annotation.Algorithm agloAnno = selectedAgloWrapper.getAnnotatedClass().getAnnotation();
            algoDescriptionTextArea.setText("Description of " + agloAnno.name() + ": " + agloAnno.description());
        } else {
            // Erase the previous content
            algoDescriptionTextArea.setText("");
        }
    }

    private void resetAlgoFilters() {
        // Reset algo type to All
        selectedAlgoType = null;

        algoTypesBtnGrp.setSelected(algoTypeAllRadioBtn.getModel(), true);

        // Also need to reset the knowledge file flag
        acceptKnowledgeFile = null;

        // Reset prior knowledge to All
        priorKnowledgeBtnGrp.setSelected(priorKnowledgeAllRadioBtn.getModel(), true);

        // Also need to reset the unmeasured confounders flag
        handleUnmeasuredConfounders = null;

        // Reset unmeasured confounders to All
        unmeasuredConfoundersBtnGrp.setSelected(unmeasuredConfoundersAllRadioBtn.getModel(), true);

        // Don't forget to update the list of algos
        setDefaultAlgosListModel();

        // Reset default selected algorithm
        setSelection();
    }

    private void updateSuggestedAlgosList() {
        // Clear the list model
        suggestedAlgosListModel.removeAllElements();

        // Algo type, knowledge file, unmeasured confounders
        List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> filteredAlgosByType = new LinkedList<>();
        List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> filteredAlgosByKnowledgeFile = new LinkedList<>();
        List<AnnotatedClassWrapper<edu.cmu.tetrad.annotation.Algorithm>> filteredAlgosByUnmeasuredConfounder = new LinkedList<>();

        // Don't assign algoWrappers directly to the above three lists since algoWrappers is unmodifiableList
        // Iterate over algoWrappers so all the three lists contain all algos at the beginning
        algoWrappers.forEach(algoWrapper -> {
            filteredAlgosByType.add(algoWrapper);
            filteredAlgosByKnowledgeFile.add(algoWrapper);
            filteredAlgosByUnmeasuredConfounder.add(algoWrapper);
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
        if (acceptKnowledgeFile != null) {
            algoWrappers.forEach(algoWrapper -> {
                Class clazz = algoWrapper.getAnnotatedClass().getClazz();

                // Remove algo if the the flag doesn't equal to the acceptKnowledge(clazz)
                if (acceptKnowledgeFile != TetradAlgorithmAnnotations.getInstance().acceptKnowledge(clazz)) {
                    filteredAlgosByKnowledgeFile.remove(algoWrapper);
                }
            });
        }

        if (handleUnmeasuredConfounders != null) {
            algoWrappers.forEach(algoWrapper -> {
                Class clazz = algoWrapper.getAnnotatedClass().getClazz();

                // Remove algo is flag doesn't equal to the handleUnmeasuredConfounder(clazz)
                if (handleUnmeasuredConfounders != TetradAlgorithmAnnotations.getInstance().handleUnmeasuredConfounder(clazz)) {
                    filteredAlgosByUnmeasuredConfounder.remove(algoWrapper);
                }
            });
        }

        // Now get intersections of all three lists
        // filteredAlgosByType now contains only the elements which are also contained in filteredAlgosByKnowledgeFile
        filteredAlgosByType.retainAll(filteredAlgosByKnowledgeFile);

        // filteredAlgosByUnmeasuredConfounder now contains only the elements which are also contained in filteredAlgosByType
        filteredAlgosByUnmeasuredConfounder.retainAll(filteredAlgosByType);

        // Add the filtered elements to suggestedAlgosListModel
        filteredAlgosByUnmeasuredConfounder.forEach(algoWrapper -> {
            suggestedAlgosListModel.addElement(algoWrapper);
        });

        // Reset default selected algorithm
        setSelection();
    }

    private void setSelection() {
        // Set default selected algorithm
        setDefaultSelectedAlgo();

        // Set description
        setAlgoDescriptionContent();

        // Set the selected algo and update the test and score dropdown menus
        setAlgorithm();
    }

    private void setDefaultSelectedAlgo() {
        if (!suggestedAlgosListModel.isEmpty()) {
            suggestedAlgosList.setSelectedIndex(0);
            selectedAgloWrapper = suggestedAlgosList.getSelectedValue();

            // Set the selected algo and update the test and score dropdown menus
            // also set other runner parameters
            setAlgorithm();
        }
    }

    private void doSearch(final GeneralAlgorithmRunner runner) {
        HpcAccount hpcAccount = null;

        switch (selectedAgloWrapper.getName()) {
            case "FGES":
            case "GFCI":
                hpcAccount = showRemoteComputingOptions(selectedAgloWrapper.getName());
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
        } else {
            try {
                doRemoteCompute(runner, hpcAccount);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private HpcAccount showRemoteComputingOptions(String name) {
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

            switch (selectedAgloWrapper.getName()) {
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

    /**
     * Initialize algorithm
     *
     * @return Algorithm
     */
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

//        if (algorithm instanceof HasKnowledge) {
//            ((HasKnowledge) algorithm).setKnowledge();
//        }
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
        AnnotatedClassWrapper<Score> score = (AnnotatedClassWrapper<Score>) scoreDropdown.getSelectedItem();
        Class scoreClass = score.getAnnotatedClass().getClazz();

        ScoreWrapper scoreWrapper = null;
        try {
            scoreWrapper = (ScoreWrapper) scoreClass.newInstance();
        } catch (IllegalAccessException | InstantiationException exception) {
            // log this error
            throw new IllegalArgumentException("Please configure that score: " + score);
        }

        if (scoreWrapper == null) {
            return null;
        }

        return scoreWrapper;
    }

    private IndependenceWrapper getIndependenceWrapper() {
        AnnotatedClassWrapper<TestOfIndependence> test = (AnnotatedClassWrapper<TestOfIndependence>) testDropdown.getSelectedItem();
        Class indTestClass = test.getAnnotatedClass().getClazz();

        IndependenceWrapper independenceWrapper = null;
        try {
            independenceWrapper = (IndependenceWrapper) indTestClass.newInstance();
        } catch (IllegalAccessException | InstantiationException exception) {
            // log this error
        }

        if (independenceWrapper != null) {
            // do independence test for each dataset
            List<IndependenceTest> tests = new ArrayList<>();
            for (DataModel dataModel : runner.getDataModelList()) {
                IndependenceTest _test = independenceWrapper.getTest(dataModel, parameters);
                tests.add(_test);
            }
            runner.setIndependenceTests(tests);
        }

        return independenceWrapper;
    }

    // Determine if enable/disable test dropdowns
    private void setTestDropdown() {
        // Get annotated algo
        TetradAlgorithmAnnotations algoAnno = TetradAlgorithmAnnotations.getInstance();
        Class algoClass = selectedAgloWrapper.getAnnotatedClass().getClazz();

        // Determine if enable/disable test and score dropdowns
        testDropdown.setEnabled(algoAnno.requireIndependenceTest(algoClass));
    }

    // Determine if enable/disable score dropdowns
    private void setScoreDropdown() {
        // Get annotated algo
        TetradAlgorithmAnnotations algoAnno = TetradAlgorithmAnnotations.getInstance();
        Class algoClass = selectedAgloWrapper.getAnnotatedClass().getClazz();

        // Determine if enable/disable test and score dropdowns
        scoreDropdown.setEnabled(algoAnno.requireScore(algoClass));
    }

    // Enable/disable the checkboxes of assumptions
    // based on if there are annotated tests/scores with assumption annotations
    private void setAssumptions() {
        // Disable assumptions checkboxes when both test and score dropdowns are disabled
        boolean disabled = !testDropdown.isEnabled() && !scoreDropdown.isEnabled();

        linearVariablesCheckbox.setEnabled(!disabled);
        gaussianVariablesCheckbox.setEnabled(!disabled);
    }

    private void setAlgorithm() {
        if (selectedAgloWrapper != null) {
            // Determine if enable/disable test and score dropdowns
            setTestDropdown();
            setScoreDropdown();

            // Determine if enable/disable the checkboxes of assumptions
            setAssumptions();

            // Set the algo on each selection change
            Algorithm algorithm = getAlgorithmFromInterface();

            runner.setAlgorithm(algorithm);

            // Set runner parameters for target algo
            parameters.set("testEnabled", testDropdown.isEnabled());
            parameters.set("scoreEnabled", scoreDropdown.isEnabled());

            parameters.set("algName", selectedAgloWrapper.getName());
            parameters.set("algType", selectedAgloWrapper.getAnnotatedClass().getAnnotation().algoType());

            setTestType(((AnnotatedClassWrapper<TestOfIndependence>) testDropdown.getSelectedItem()).getName());
            setScoreType(((AnnotatedClassWrapper<Score>) scoreDropdown.getSelectedItem()).getName());

            // Also need to update the corresponding parameters
            parametersPanel = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
            // Remove all and add new
            parametersBox.removeAll();
            parametersBox.add(parametersPanel);
        }
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

    private void setTestType(String testType) {
        parameters.set("testType", testType);
    }

    private void setScoreType(String scoreType) {
        parameters.set("scoreType", scoreType);
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

}
