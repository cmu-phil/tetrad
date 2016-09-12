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
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Lingam;
import edu.cmu.tetrad.algcomparison.algorithm.mixed.Mgm;
import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesBDeu;
import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.algorithm.multi.TsImagesSemBic;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.algorithm.other.Glasso;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.*;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.score.*;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeBoxEditor;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class GeneralAlgorithmEditor extends JPanel implements FinalizingEditor {
    private final HashMap<AlgName, AlgorithmDescription> mappedDescriptions;
    private final GeneralAlgorithmRunner runner;
    private final JButton searchButton1 = new JButton("Search");
    private final JButton searchButton2 = new JButton("Search");
    private final JTabbedPane pane;
    private final JComboBox<AlgType> algTypesDropdown = new JComboBox<>();
    private final JComboBox<AlgName> algNamesDropdown = new JComboBox<>();
    private final JComboBox<TestType> testDropdown = new JComboBox<>();
    private final JComboBox<ScoreType> scoreDropdown = new JComboBox<>();
    private final GraphSelectionEditor graphEditor;
    private final Parameters parameters;
    private final HelpSet helpSet;
    private Box knowledgePanel;
    private JLabel whatYouChose;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.runner = runner;

        String helpHS = "/resources/javahelp/TetradHelp.hs";

        try {
            URL url = this.getClass().getResource(helpHS);
            this.helpSet = new HelpSet(null, url);
        } catch (Exception ee) {
            System.out.println("HelpSet " + ee.getMessage());
            System.out.println("HelpSet " + helpHS + " not found");
            throw new IllegalArgumentException();
        }

        List<TestType> discreteTests = new ArrayList<>();
        discreteTests.add(TestType.ChiSquare);
        discreteTests.add(TestType.GSquare);
        discreteTests.add(TestType.Conditional_Gaussian_LRT);

        List<TestType> continuousTests = new ArrayList<>();
        continuousTests.add(TestType.Fisher_Z);
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

        final List<AlgorithmDescription> descriptions = new ArrayList<>();

        descriptions.add(new AlgorithmDescription(AlgName.PC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PCStable, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPCStable, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PcLocal, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PcMax, AlgType.Pattern, OracleType.Test));
//        descriptions.add(new AlgorithmDescription(AlgName.PcMaxLocal, AlgType.Pattern, OracleType.Test));
//        descriptions.add(new AlgorithmDescription(AlgName.JCPC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.FCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.RFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GFCI, AlgType.PAG, OracleType.Both));
        descriptions.add(new AlgorithmDescription(AlgName.FGS, AlgType.Pattern, OracleType.Score));
//        descriptions.add(new AlgorithmDescription(AlgName.FgsMeasurement, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.TsFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.TsGFCI, AlgType.PAG, OracleType.Both));
        descriptions.add(new AlgorithmDescription(AlgName.CCD, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GCCD, AlgType.PAG, OracleType.Score));

        descriptions.add(new AlgorithmDescription(AlgName.FgsMb, AlgType.Markov_Blanket, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.MBFS, AlgType.Markov_Blanket, OracleType.Score));
//        descriptions.add(new AlgorithmDescription(AlgName.Wfgs, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.FAS, AlgType.Undirected_Graph, OracleType.Test));

//        descriptions.add(new AlgorithmDescription(AlgName.LiNGAM, AlgType.DAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.MGM, AlgType.Undirected_Graph, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_BDeu, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_SEM_BIC, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.TsFCI, AlgType.PAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.TsGFCI, AlgType.PAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.TsImages, AlgType.PAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.GLASSO, AlgType.Undirected_Graph, OracleType.None));

        descriptions.add(new AlgorithmDescription(AlgName.EB, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R1, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R2, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R3, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.R4, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.RSkew, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.RSkewE, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.Skew, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.SkewE, AlgType.Pairwise, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.Tahn, AlgType.Pairwise, OracleType.None));

        mappedDescriptions = new HashMap<>();

        for (AlgorithmDescription description : descriptions) {
            mappedDescriptions.put(description.getAlgName(), description);
        }

        this.parameters = runner.getParameters();
        graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(runner.getGraphs(), new Parameters()));
        setLayout(new BorderLayout());

        whatYouChose = new JLabel();

//        if (runner.getDataModelList() == null) {
//            throw new NullPointerException("No data has been provided.");
//        }

        List<TestType> tests;

        DataModelList dataModelList = runner.getDataModelList();

        if ((dataModelList.isEmpty() && runner.getSourceGraph() != null)) {
            tests = dsepTests;
        } else if (!(dataModelList.isEmpty())) {
            DataSet dataSet = (DataSet) dataModelList.get(0);

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
            DataSet dataSet = (DataSet) dataModelList.get(0);

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

        if ((dataModelList == null || dataModelList.isEmpty() && runner.getGraphs() != null)) {
            scores = dsepScores;
        } else if (!(dataModelList.isEmpty())) {
            DataSet dataSet = (DataSet) dataModelList.get(0);

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
            algTypesDropdown.addItem(item);
        }

        for (AlgorithmDescription description : descriptions) {
            if (description.getAlgType() == getAlgType()) {
                algNamesDropdown.addItem(description.getAlgName());
            }
        }

        algTypesDropdown.setSelectedItem(getAlgType());
        algNamesDropdown.setSelectedItem(getAlgName());

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
                    if (description.getAlgType() == algTypesDropdown.getSelectedItem()) {
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

        pane = new JTabbedPane();
        pane.add("Algorithm", getParametersPane());
        getAlgorithmFromInterface();
        pane.add("Output Graphs", graphEditor);
        add(pane, BorderLayout.CENTER);

        if (runner.getGraphs() != null && runner.getGraphs().size() > 0) {
            pane.setSelectedComponent(graphEditor);
        }

        searchButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSearch(runner);
            }
        });

        searchButton2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSearch(runner);
            }
        });
    }

    private Box getKnowledgePanel(GeneralAlgorithmRunner runner) {
        class MyKnowledgeInput implements KnowledgeBoxInput {
            private String name;
            private List<Node> variables;
            private List<String> varNames;

            public MyKnowledgeInput(List<Node> variables, List<String> varNames) {
                this.variables = variables;
                this.varNames = varNames;
            }

            @Override
            public Graph getSourceGraph() {
                return null;
            }

            @Override
            public Graph getResultGraph() {
                return null;
            }

            @Override
            public void setName(String name) {
                this.name = name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<Node> getVariables() {
                return variables;
            }

            @Override
            public List<String> getVariableNames() {
                return varNames;
            }
        }

        List<Node> variables = null;
        MyKnowledgeInput myKnowledgeInput;

        if (runner.getDataModel() != null) {
            DataModelList dataModelList = runner.getDataModelList();
            if (dataModelList.size() > 0) {
                variables = dataModelList.get(0).getVariables();
            }
        }

        if ((variables == null || variables.isEmpty()) && runner.getSourceGraph() != null) {
            variables = runner.getSourceGraph().getNodes();
        }

        if (variables == null) {
            throw new IllegalArgumentException("No source of variables!");
        }


        List<String> varNames = new ArrayList<>();

        for (Node node : variables) {
            varNames.add(node.getName());
        }

        myKnowledgeInput = new MyKnowledgeInput(variables, varNames);

        JPanel knowledgePanel = new JPanel();
        knowledgePanel.setLayout(new BorderLayout());
        KnowledgeBoxModel knowledgeBoxModel = new KnowledgeBoxModel(new KnowledgeBoxInput[]{myKnowledgeInput}, parameters);
        knowledgeBoxModel.setKnowledge(runner.getKnowledge());
        KnowledgeBoxEditor knowledgeEditor = new KnowledgeBoxEditor(knowledgeBoxModel);
        Box f = Box.createVerticalBox();
        f.add(knowledgeEditor);
        Box g = Box.createHorizontalBox();
        g.add(Box.createHorizontalGlue());
        g.add(searchButton2);
        g.add(Box.createHorizontalGlue());
        f.add(g);
        return f;
    }

    private void doSearch(final GeneralAlgorithmRunner runner) {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                runner.execute();
                graphEditor.replace(runner.getGraphs());
                graphEditor.validate();
                firePropertyChange("modelChanged", null, null);
                pane.setSelectedComponent(graphEditor);
            }
        };
    }

    public Algorithm getAlgorithmFromInterface() {
        AlgName name = (AlgName) algNamesDropdown.getSelectedItem();

        if (name == null) {
            throw new NullPointerException();
        }

        IndependenceWrapper independenceWrapper = getIndependenceWrapper();
        ScoreWrapper scoreWrapper = getScoreWrapper();

        Algorithm algorithm = getAlgorithm(name, independenceWrapper, scoreWrapper);

        if (algorithm instanceof HasKnowledge) {
            if (knowledgePanel == null) {
                knowledgePanel = getKnowledgePanel(runner);
            }

            pane.remove(graphEditor);
            pane.add("Knowledge", knowledgePanel);
            pane.add("Output Graphs", graphEditor);
        } else {
            pane.remove(knowledgePanel);
        }

        return algorithm;
    }

    private Algorithm getAlgorithm(AlgName name, IndependenceWrapper independenceWrapper, ScoreWrapper scoreWrapper) {
        Algorithm algorithm;


        switch (name) {
            case FGS:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new Fgs(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Fgs(scoreWrapper);
                }
                break;
//            case FgsMeasurement:
//                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
//                    algorithm = new FgsMeasurement(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
//                } else {
//                    algorithm = new FgsMeasurement(scoreWrapper);
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
                algorithm = new Cpcs(independenceWrapper);
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
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new Fci(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Fci(independenceWrapper);
                }
                break;
            case RFCI:
                algorithm = new Rfci(independenceWrapper);
                break;
            case CFCI:
                algorithm = new Cfci(independenceWrapper);
                break;
            case TsFCI:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new TsFci(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new TsFci(independenceWrapper);
                }
                break;
            case TsGFCI:
                algorithm = new TsGfci(independenceWrapper);
                break;
            case TsImages:
                algorithm = new TsImagesSemBic();
                break;
            case CCD:
                algorithm = new Ccd(independenceWrapper);
                break;
            case GCCD:
                algorithm = new GCcd(scoreWrapper);
                break;

            case FAS:
                algorithm = new FAS(independenceWrapper);
                break;
            case FgsMb:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new FgsMb(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new FgsMb(scoreWrapper);
                }
                break;
            case MBFS:
                algorithm = new MBFS(independenceWrapper);
                break;
            case PcLocal:
                algorithm = new PcLocal(independenceWrapper);
                break;
            case PcMax:
                if (runner.getSourceGraph() != null && !runner.getDataModelList().isEmpty()) {
                    algorithm = new PcMax(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new PcMax(independenceWrapper);
                }
                break;
            case PcMaxLocal:
                algorithm = new PcMaxLocal(independenceWrapper);
                break;
            case JCPC:
                algorithm = new Jcpc(independenceWrapper);
                break;
            case Wfgs:
                algorithm = new Wfgs();
                break;
            case LiNGAM:
                algorithm = new Lingam();
                break;
            case MGM:
                algorithm = new Mgm();
                break;
            case IMaGES_BDeu:
                algorithm = new ImagesBDeu();
                break;
            case IMaGES_SEM_BIC:
                algorithm = new ImagesSemBic();
                break;
            case GLASSO:
                algorithm = new Glasso();
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
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("I was expecting a data set to save out indepenedence tests, sorry.");
            }

            IndependenceTest _test = independenceWrapper.getTest((DataSet) dataModel, parameters);
            tests.add(_test);
        }

        runner.setIndependenceTests(tests);
        return independenceWrapper;
    }

    private void setAlgorithm() {
        AlgName name = (AlgName) algNamesDropdown.getSelectedItem();
        AlgorithmDescription description = mappedDescriptions.get(name);

        if (name == null) {
            return;
        }

        TestType test = (TestType) testDropdown.getSelectedItem();
        ScoreType score = (ScoreType) scoreDropdown.getSelectedItem();

        Algorithm algorithm = getAlgorithmFromInterface();

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

        runner.setAlgorithm(algorithm);

        setAlgName(name);
        setTestType(test);
        setScoreType(score);
        setAlgType((AlgType) algTypesDropdown.getSelectedItem());

        if (whatYouChose != null) {
            whatYouChose.setText("You chose: " + algorithm.getDescription());
        }

        if (pane != null) {
            pane.setComponentAt(0, getParametersPane());
        }

    }

    //=============================== Public Methods ==================================//


    private JPanel getParametersPane() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        helpSet.setHomeID("tetrad_overview");

        ParameterPanel comp = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
        final JScrollPane scroll = new JScrollPane(comp);
        scroll.setPreferredSize(new Dimension(800, 300));
        Box c = Box.createVerticalBox();

        JButton explain1 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain2 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain3 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        JButton explain4 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));

        explain1.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain2.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain3.setBorder(new EmptyBorder(0, 0, 0, 0));
        explain4.setBorder(new EmptyBorder(0, 0, 0, 0));


//        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
//        Box c = Box.createVerticalBox();
//
////        JButton explain1 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
////        JButton explain2 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
////        JButton explain3 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
////        JButton explain4 = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
//
//        JButton explain1 = new JButton("Explain");
//        JButton explain2 = new JButton("Explain");
//        JButton explain3 = new JButton("Explain");
//        JButton explain4 = new JButton("Explain");
//
////        explain1.setBorder(new EmptyBorder(0, 0, 0, 0));
////        explain2.setBorder(new EmptyBorder(0, 0, 0, 0));
////        explain3.setBorder(new EmptyBorder(0, 0, 0, 0));
////        explain4.setBorder(new EmptyBorder(0, 0, 0, 0));

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
                String name = box.getSelectedItem().toString();
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
                String name = box.getSelectedItem().toString();
//                helpSet.setHomeID(name.toLowerCase());
                helpSet.setHomeID("under_construction");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
            }
        });

//        Box d8 = Box.createHorizontalBox();
//        JLabel label8 = new JLabel("You have a lot of options.");
//        label8.setFont(new Font("Dialog", Font.BOLD, 13));
//        d8.add(label8);
//        d8.add(Box.createHorizontalGlue());
//        c.add(d8);
//        c.add(Box.createVerticalStrut(10));

        Box d3 = Box.createHorizontalBox();
        JLabel label3 = new JLabel("First, choose a type of algorithm:");
        label3.setFont(new Font("Dialog", Font.BOLD, 13));
        d3.add(label3);
        d3.add(Box.createHorizontalGlue());
        algTypesDropdown.setMaximumSize(algTypesDropdown.getPreferredSize());
        d3.add(algTypesDropdown);
        d3.add(explain1);
        c.add(d3);
        c.add(Box.createVerticalStrut(10));

        Box d4 = Box.createHorizontalBox();
        JLabel label4 = new JLabel("Next, choose an algorithm of that type; only algorithms compatible with your data " +
                "will be shown:");
        label4.setFont(new Font("Dialog", Font.BOLD, 13));
        d4.add(label4);
        d4.add(Box.createHorizontalGlue());
        algNamesDropdown.setMaximumSize(algNamesDropdown.getPreferredSize());
        d4.add(algNamesDropdown);
        d4.add(explain2);
        c.add(d4);
        c.add(Box.createVerticalStrut(10));

        Box d7 = Box.createHorizontalBox();
        JLabel label7 = new JLabel("Next, choose a score or a test, or both. If they're not needed, they will be grayed out.");
        label7.setFont(new Font("Dialog", Font.BOLD, 13));
        d7.add(label7);
        d7.add(Box.createHorizontalGlue());
        c.add(d7);
        c.add(Box.createVerticalStrut(10));

        Box d1 = Box.createHorizontalBox();
        JLabel label1 = new JLabel("Test type:");
        label1.setFont(new Font("Dialog", Font.BOLD, 13));
        d1.add(label1);
        d1.add(testDropdown);
        d1.add(explain3);

        JLabel label2 = new JLabel("Score type:");
        label2.setFont(new Font("Dialog", Font.BOLD, 13));
        d1.add(Box.createHorizontalStrut(20));
        d1.add(label2);
        d1.add(scoreDropdown);
        d1.add(explain4);
        c.add(d1);
        c.add(Box.createVerticalStrut(10));

        Box d5 = Box.createHorizontalBox();

        Algorithm algorithm = getAlgorithmFromInterface();
        whatYouChose = new JLabel("You chose: " + algorithm.getDescription() + ".");
        whatYouChose.setFont(new Font("Dialog", Font.BOLD, 13));

        d5.add(whatYouChose);
        d5.add(Box.createHorizontalGlue());
        c.add(d5);
        c.add(Box.createVerticalStrut(15));

        Box d0 = Box.createHorizontalBox();
        JLabel label0 = new JLabel("Parameters for your algorithm are listed below. Please adjust the parameter values.");
        label0.setFont(new Font("Dialog", Font.BOLD, 13));
        d0.add(label0);
        d0.add(Box.createHorizontalGlue());
        c.add(d0);
        c.add(Box.createVerticalStrut(10));

        c.add(scroll);

        Box d6 = Box.createHorizontalBox();
        d6.add(Box.createHorizontalGlue());
        d6.add(searchButton1);
        d6.add(Box.createHorizontalGlue());
        c.add(d6);

        Box b = Box.createHorizontalBox();
        b.add(c);
        b.add(Box.createVerticalGlue());

        runner.setAlgorithm(algorithm);

        panel.add(b, BorderLayout.CENTER);

        return panel;
    }

    private Parameters getParameters() {
        return parameters;
    }

    private AlgType getAlgType() {
        return AlgType.valueOf(parameters.getString("algType", "Pattern"));
    }

    private void setAlgType(AlgType algType) {
        parameters.set("algType", algType.toString());
    }

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

        if (graphs == null || graphs.isEmpty()) {
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
        PC, PCStable, CPC, CPCStable, FGS, PcLocal, PcMax, PcMaxLocal, FAS,
        FgsMb, MBFS, Wfgs, JCPC, /*FgsMeasurement,*/
        FCI, RFCI, CFCI, GFCI, TsFCI, TsGFCI, TsImages, CCD, GCCD,
        LiNGAM, MGM,
        IMaGES_BDeu, IMaGES_SEM_BIC,
        GLASSO,
        EB, R1, R2, R3, R4, RSkew, RSkewE, Skew, SkewE, Tahn
    }

    private enum OracleType {None, Test, Score, Both}

    private enum AlgType {Pattern, PAG, /*DAG, */Markov_Blanket, Undirected_Graph, Pairwise}

    private enum TestType {
        ChiSquare, Conditional_Correlation, Conditional_Gaussian_LRT, Fisher_Z, GSquare,
        SEM_BIC, D_SEPARATION
    }

    public enum ScoreType {BDeu, Conditional_Gaussian_BIC, Discrete_BIC, SEM_BIC, D_SEPARATION}
}




