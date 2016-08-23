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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeBoxEditor;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class GeneralAlgorithmEditor extends JPanel {
    private final HashMap<AlgName, AlgorithmDescription> mappedDescriptions;
    private final GeneralAlgorithmRunner runner;
    private final JButton searchButton1 = new JButton("Search");
    private final JButton searchButton2 = new JButton("Search");
    private final JTabbedPane pane;
    private final JComboBox<TestType> testDropdown = new JComboBox<>();
    private final JComboBox<ScoreType> scoreDropdown = new JComboBox<>();
    private final JComboBox<AlgType> algTypesDropdown = new JComboBox<>();
    private final JComboBox<AlgName> algNamesDropdown = new JComboBox<>();
    private final GraphSelectionEditor graphEditor;
    private final Parameters parameters;
    private JLabel whatYouChose;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.runner = runner;

        List<TestType> discreteTests = new ArrayList<>();
        discreteTests.add(TestType.ChiSquare);
        discreteTests.add(TestType.GSquare);
        discreteTests.add(TestType.Conditional_Correlation);

        List<TestType> continuousTests = new ArrayList<>();
        continuousTests.add(TestType.FisherZ);
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
        discreteScores.add(ScoreType.Conditioanal_Gaussian_BIC);

        List<ScoreType> continuousScores = new ArrayList<>();
        continuousScores.add(ScoreType.SEM_BIC);
        continuousScores.add(ScoreType.Conditioanal_Gaussian_BIC);

        List<ScoreType> mixedScores = new ArrayList<>();
        mixedScores.add(ScoreType.Conditioanal_Gaussian_BIC);

        List<ScoreType> dsepScores = new ArrayList<>();
        dsepScores.add(ScoreType.D_SEPARATION);

        final List<AlgorithmDescription> descriptions = new ArrayList<>();

        descriptions.add(new AlgorithmDescription(AlgName.FGS, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.PC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPCS, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PCStable, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GFCI, AlgType.PAG, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.FCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.RFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.TsFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.TsGFCI, AlgType.PAG, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.CCD, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GCCD, AlgType.PAG, OracleType.Score));

        descriptions.add(new AlgorithmDescription(AlgName.FgsMb, AlgType.Markov_Blanket, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.MBFS, AlgType.Markov_Blanket, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.PcLocal, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.PcMax, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.PcMaxLocal, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.Wfgs, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.FAS, AlgType.Markov_Random_Field, OracleType.Test));

        descriptions.add(new AlgorithmDescription(AlgName.LiNGAM, AlgType.DAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.MGM, AlgType.Markov_Random_Field, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_BDeu, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_SEM_BIC, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.TsIMaGES_SEM_BIC, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.GLASSO, AlgType.Markov_Random_Field, OracleType.None));

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
        graphEditor = new GraphSelectionEditor(new GraphSelectionWrapper(new ArrayList<Graph>(), new Parameters()));
        setLayout(new BorderLayout());

        whatYouChose = new JLabel();

//        if (runner.getDataModelList() == null) {
//            throw new NullPointerException("No data has been provided.");
//        }

        List<TestType> tests;

        if (runner.getDataModelList() == null) {
            tests = dsepTests;
        } else {
            DataSet dataSet = (DataSet) runner.getDataModelList().get(0);

            if (dataSet.isContinuous()) {
                tests = continuousTests;
            } else if (dataSet.isDiscrete()) {
                tests = discreteTests;
            } else if (dataSet.isMixed()) {
                tests = mixedTests;
            } else {
                throw new IllegalArgumentException();
            }
        }

        for (TestType item : tests) {
            testDropdown.addItem(item);
        }

        List<ScoreType> scores;

        if (runner.getDataModelList() == null) {
            scores = dsepScores;
        } else {
            DataSet dataSet = (DataSet) runner.getDataModelList().get(0);

            if (dataSet.isContinuous()) {
                scores = continuousScores;
            } else if (dataSet.isDiscrete()) {
                scores = discreteScores;
            } else if (dataSet.isMixed()) {
                scores = mixedScores;
            } else {
                throw new IllegalArgumentException();
            }
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

        testDropdown.setEnabled(false);
        scoreDropdown.setEnabled(true);

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
        pane.add("Knowledge", getKnowledgePanel(runner));
        pane.add("Output Graph Selections", graphEditor);

        add(pane, BorderLayout.CENTER);

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

        MyKnowledgeInput myKnowledgeInput
                = new MyKnowledgeInput(runner.getDataModel().getVariables(),
                runner.getDataModel().getVariableNames());

        JPanel knowledgePanel = new JPanel();
        knowledgePanel.setLayout(new BorderLayout());
        KnowledgeBoxModel knowledgeBoxModel = new KnowledgeBoxModel(parameters, myKnowledgeInput);
        knowledgeBoxModel.setKnowledge(runner.getKnowledge());
        KnowledgeBoxEditor knowledgeEditor = new KnowledgeBoxEditor(
                knowledgeBoxModel);
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
                pane.setSelectedIndex(2);
            }
        };
    }

    public Algorithm getAlgorithmFromInterface() {
        AlgName name = (AlgName) algNamesDropdown.getSelectedItem();

        if (name == null) {
            throw new NullPointerException();
        }

        TestType test = (TestType) testDropdown.getSelectedItem();
        ScoreType score = (ScoreType) scoreDropdown.getSelectedItem();

        IndependenceWrapper independenceWrapper;

        switch (test) {
            case ChiSquare:
                independenceWrapper = new FisherZ();
                break;
            case Conditional_Correlation:
                independenceWrapper = new ConditionalCorrelation();
                break;
            case Conditional_Gaussian_LRT:
                independenceWrapper = new ConditionalGaussianLRT();
                break;
            case FisherZ:
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

        ScoreWrapper scoreWrapper;

        switch (score) {
            case BDeu:
                scoreWrapper = new BdeuScore();
                break;
            case Conditioanal_Gaussian_BIC:
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

        Algorithm algorithm;


        switch (name) {
            case FGS:
                if (runner.getSourceGraph() != null) {
                    algorithm = new Fgs(scoreWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Fgs(scoreWrapper);
                }
                break;
            case PC:
                if (runner.getSourceGraph() != null) {
                    algorithm = new Pc(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Pc(independenceWrapper);
                }
                break;
            case CPC:
                if (runner.getSourceGraph() != null) {
                    algorithm = new Cpc(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new Cpc(independenceWrapper);
                }
                break;
            case CPCS:
                algorithm = new Cpcs(independenceWrapper);
                break;
            case PCStable:
                if (runner.getSourceGraph() != null) {
                    algorithm = new PcStable(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new PcStable(independenceWrapper);
                }
                break;
            case GFCI:
                algorithm = new Gfci(scoreWrapper);
                break;
            case FCI:
                if (runner.getSourceGraph() != null) {
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
                if (runner.getSourceGraph() != null) {
                    algorithm = new TsFci(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new TsFci(independenceWrapper);
                }
                break;
            case TsGFCI:
                algorithm = new TsGfci(scoreWrapper);
                break;
            case CCD:
                algorithm = new Ccd(independenceWrapper);
                break;
            case GCCD:
                algorithm = new GCCD(scoreWrapper);
                break;

            case FAS:
                algorithm = new FAS(independenceWrapper);
                break;
            case FgsMb:
                if (runner.getSourceGraph() != null) {
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
                if (runner.getSourceGraph() != null) {
                    algorithm = new PcMax(independenceWrapper, new SingleGraphAlg(runner.getSourceGraph()));
                } else {
                    algorithm = new PcMax(independenceWrapper);
                }
                break;
            case PcMaxLocal:
                algorithm = new PcMaxLocal(independenceWrapper);
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
            case TsIMaGES_SEM_BIC:
                algorithm = new TsImagesSemBic();
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


    private Box getParametersPane() {
        ParameterPanel comp = new ParameterPanel(runner.getAlgorithm().getParameters(), getParameters());
        JScrollPane scroll = new JScrollPane(comp);
        scroll.setPreferredSize(new Dimension(1000, 300));
        Box c = Box.createVerticalBox();

//        button.setIcon(
//                new ImageIcon(ImageUtils.getImage(this, name + "3.gif")));
//        button.setMaximumSize(new Dimension(80, 40));
//        button.setPreferredSize(new Dimension(80, 40));



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
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Some insighful explanation will be put here by somebody");
            }
        });

        explain2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Some insighful explanation will be put here by somebody");
            }
        });

        explain3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Some insighful explanation will be put here by somebody");
            }
        });

        explain4.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Some insighful explanation will be put here by somebody");
            }
        });

        Box d0 = Box.createHorizontalBox();
        JLabel label0 = new JLabel("Pick an agorithm and parameterize it; then click Search.");
        label0.setFont(new Font("Dialog", Font.BOLD, 12));
        d0.add(label0);
        d0.add(Box.createHorizontalGlue());
        c.add(d0);
        c.add(Box.createVerticalStrut(5));

        Box d3 = Box.createHorizontalBox();
        JLabel label3 = new JLabel("TYPE OF ALGORITHM:");
        label3.setFont(new Font("Dialog", Font.BOLD, 12));
        d3.add(label3);
        d3.add(algTypesDropdown);
        d3.add(explain1);
        c.add(d3);

        Box d4 = Box.createHorizontalBox();
        JLabel label4 = new JLabel("WHICH ALGORITHM:");
        label4.setFont(new Font("Dialog", Font.BOLD, 12));
        d4.add(label4);
        d4.add(algNamesDropdown);
        d4.add(explain2);
        c.add(d4);

        Box d1 = Box.createHorizontalBox();
        JLabel label1 = new JLabel("TEST TYPE (if needed):");
        label1.setFont(new Font("Dialog", Font.BOLD, 12));
        d1.add(label1);
        d1.add(testDropdown);
        d1.add(explain3);
        c.add(d1);

        Box d2 = Box.createHorizontalBox();
        JLabel label2 = new JLabel("SCORE TYPE (if needed):");
        label2.setFont(new Font("Dialog", Font.BOLD, 12));
        d2.add(label2);
        d2.add(scoreDropdown);
        d2.add(explain4);
        c.add(d2);
        c.add(Box.createVerticalStrut(5));

        Box d5 = Box.createHorizontalBox();

        Algorithm algorithm = getAlgorithmFromInterface();
        whatYouChose = new JLabel("You chose: " + algorithm.getDescription());
        whatYouChose.setFont(new Font("Dialog", Font.BOLD, 12));

        d5.add(whatYouChose);
        d5.add(Box.createHorizontalGlue());
        c.add(d5);
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

        return b;
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
        return AlgName.valueOf(parameters.getString("algName", "FGS"));
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
        FGS, PC, CPC, CPCS, PCStable, FAS,
        GFCI, FCI, RFCI, CFCI, TsFCI, TsGFCI, CCD, GCCD,
        FgsMb, MBFS, PcLocal, PcMax, PcMaxLocal, Wfgs,
        LiNGAM, MGM,
        IMaGES_BDeu, IMaGES_SEM_BIC,
        TsIMaGES_SEM_BIC,
        GLASSO,
        EB, R1, R2, R3, R4, RSkew, RSkewE, Skew, SkewE, Tahn
    }

    private enum OracleType {None, Test, Score, Both}

    private enum AlgType {Pattern, PAG, DAG, Markov_Blanket, Markov_Random_Field, Pairwise}

    private enum TestType {
        ChiSquare, Conditional_Correlation, Conditional_Gaussian_LRT, FisherZ, GSquare,
        SEM_BIC, D_SEPARATION
    }

    public enum ScoreType {BDeu, Conditioanal_Gaussian_BIC, Discrete_BIC, SEM_BIC, D_SEPARATION}
}




