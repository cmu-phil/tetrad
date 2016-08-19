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
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeBoxEditor;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
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
    private final SimulationGraphEditor graphEditor;
    private final Parameters parameters;
    private JLabel whatYouChose;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.runner = runner;

        final List<AlgorithmDescription> descriptions = new ArrayList<>();

        descriptions.add(new AlgorithmDescription(AlgName.PC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPC, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CPCS, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.PCS, AlgType.Pattern, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.FGS, AlgType.Pattern, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.FCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.RFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.CFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.GFCI, AlgType.PAG, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.TsFCI, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.TsGFCI, AlgType.PAG, OracleType.Score));
        descriptions.add(new AlgorithmDescription(AlgName.CCD, AlgType.PAG, OracleType.Test));
        descriptions.add(new AlgorithmDescription(AlgName.LiNGAM, AlgType.DAG, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.MGM, AlgType.Markov_Random_Field, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_BDeu, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.IMaGES_SEM_BIC, AlgType.Pattern, OracleType.None));
        descriptions.add(new AlgorithmDescription(AlgName.TsIMaGES_SEM_BIC, AlgType.Pattern, OracleType.None));

        mappedDescriptions = new HashMap<>();

        for (AlgorithmDescription description : descriptions) {
            mappedDescriptions.put(description.getAlgName(), description);
        }

        this.parameters = runner.getParameters();
        graphEditor = new SimulationGraphEditor(new ArrayList<Graph>(), JTabbedPane.LEFT);
        graphEditor.replace(runner.getGraphs());
        setLayout(new BorderLayout());

        whatYouChose = new JLabel();

        // Initialize all of the dropdowns.
        for (TestType item : TestType.values()) {
            testDropdown.addItem(item);
        }

        for (ScoreType item : ScoreType.values()) {
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
        testDropdown.setSelectedItem(getTestType());
        scoreDropdown.setSelectedItem(getScoreType());

        testDropdown.setEnabled(true);
        scoreDropdown.setEnabled(false);

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
        pane.add("Output Graphs", graphEditor);

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
            private IKnowledge knowledge;
            private String name;
            private List<Node> variables;
            private List<String> varNames;

            public MyKnowledgeInput(IKnowledge knowledge, List<Node> variables, List<String> varNames) {
                this.knowledge = knowledge;
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
                = new MyKnowledgeInput(runner.getKnowledge(), runner.getDataModel().getVariables(),
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
                DataModelList dataList = runner.getDataModelList();
                runner.setGraphList(new ArrayList<Graph>());
                graphEditor.replace(new ArrayList<Graph>());

                if (dataList != null) {
                    List<Graph> graphList = new ArrayList<>();
                    int i = 0;

                    for (DataModel data : dataList) {
                        System.out.println("Analyzing data set # " + (++i));
                        DataSet dataSet = (DataSet) data;
                        Algorithm algorithm = runner.getAlgorithm();

                        if (algorithm instanceof HasKnowledge) {
                            ((HasKnowledge) algorithm).setKnowledge(runner.getKnowledge());
                        }

                        Graph graph = algorithm.search(dataSet, parameters);
                        graphList.add(graph);
                    }

                    if (runner.getKnowledge() != null) {
                        for (Graph graph : graphList) {
                            SearchGraphUtils.arrangeByKnowledgeTiers(graph, runner.getKnowledge());
                        }
                    } else {
                        for (Graph graph : graphList) {
                            GraphUtils.circleLayout(graph, 225, 200, 150);
                        }
                    }

                    pane.setSelectedIndex(2);
                    runner.setGraphList(graphList);
                    graphEditor.replace(graphList);
                    graphEditor.validate();
                    firePropertyChange("modelChanged", null, null);
                }
            }
        };
    }

    private void setAlgorithm() {
        AlgName name = (AlgName) algNamesDropdown.getSelectedItem();
        AlgorithmDescription description = mappedDescriptions.get(name);

        if (name == null) {
            return;
        }

        TestType test = (TestType) testDropdown.getSelectedItem();
        ScoreType score = (ScoreType) scoreDropdown.getSelectedItem();

        IndependenceWrapper independenceWrapper;

        switch (test) {
            case FisherZ:
                independenceWrapper = new FisherZ();
                break;
            case ChiSquare:
                independenceWrapper = new ChiSquare();
                break;
            default:
                throw new IllegalArgumentException("Please configure that test: " + test);
        }

        ScoreWrapper scoreWrapper;

        switch (score) {
            case SEM_BIC:
                scoreWrapper = new SemBicScore();
                break;
            case BDeu:
                scoreWrapper = new BdeuScore();
                break;
            default:
                throw new IllegalArgumentException("Please configure that score: " + score);
        }

        Algorithm algorithm;

        switch (name) {
            case PC:
                algorithm = new Pc(independenceWrapper);
                break;
            case CPC:
                algorithm = new Cpc(independenceWrapper);
                break;
            case CPCS:
                algorithm = new Cpcs(independenceWrapper);
                break;
            case PCS:
                algorithm = new Pcs(independenceWrapper);
                break;
            case FGS:
                algorithm = new Fgs(scoreWrapper);
                break;
            case FCI:
                algorithm = new Fci(independenceWrapper);
                break;
            case RFCI:
                algorithm = new Rfci(independenceWrapper);
                break;
            case CFCI:
                algorithm = new Cfci(independenceWrapper);
                break;
            case GFCI:
                algorithm = new Gfci(scoreWrapper);
                break;
            case TsFCI:
                algorithm = new TsFci(independenceWrapper);
                break;
            case TsGFCI:
                algorithm = new TsGfci(scoreWrapper);
                break;
            case CCD:
                algorithm = new Ccd(independenceWrapper);
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
            default:
                throw new IllegalArgumentException("Please configure that algorithm: " + name);

        }

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
        scroll.setPreferredSize(graphEditor.getPreferredSize());
        Box c = Box.createVerticalBox();

        JButton explain = new JButton("Explain This");

        explain.addActionListener(new ActionListener() {
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
        c.add(d3);

        Box d4 = Box.createHorizontalBox();
        JLabel label4 = new JLabel("WHICH ALGORITHM:");
        label4.setFont(new Font("Dialog", Font.BOLD, 12));
        d4.add(label4);
        d4.add(algNamesDropdown);
        c.add(d4);

        Box d1 = Box.createHorizontalBox();
        JLabel label1 = new JLabel("TEST TYPE (if needed):");
        label1.setFont(new Font("Dialog", Font.BOLD, 12));
        d1.add(label1);
        d1.add(testDropdown);
        c.add(d1);

        Box d2 = Box.createHorizontalBox();
        JLabel label2 = new JLabel("SCORE TYPE (if needed):");
        label2.setFont(new Font("Dialog", Font.BOLD, 12));
        d2.add(label2);
        d2.add(scoreDropdown);
        c.add(d2);
        c.add(Box.createVerticalStrut(5));

        Box d5 = Box.createHorizontalBox();
        whatYouChose = new JLabel("You chose: " + runner.getAlgorithm().getDescription());
        whatYouChose.setFont(new Font("Dialog", Font.BOLD, 12));
        d5.add(whatYouChose);
        d5.add(Box.createHorizontalGlue());
        d5.add(explain);
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
        return AlgName.valueOf(parameters.getString("algName", "PC"));
    }

    private void setAlgName(AlgName algName) {
        parameters.set("algName", algName.toString());
    }

    private Object getTestType() {
        return TestType.valueOf(parameters.getString("testType", "ChiSquare"));
    }

    private void setTestType(TestType testType) {
        parameters.set("testType", testType.toString());
    }

    private Object getScoreType() {
        return ScoreType.valueOf(parameters.getString("scoreType", "BDeu"));
    }

    private void setScoreType(ScoreType scoreType) {
        parameters.set("scoreType", scoreType.toString());
    }

    private class AlgorithmDescription {
        private AlgName algName = AlgName.PC;
        private AlgType algType = AlgType.PAG;
        private OracleType oracleType = OracleType.Test;

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
        PC, CPC, CPCS, PCS, FGS,
        FCI, RFCI, CFCI, GFCI, TsFCI, TsGFCI, CCD,
        LiNGAM, MGM,
        IMaGES_BDeu, IMaGES_SEM_BIC,
        TsIMaGES_SEM_BIC,
    }

    private enum OracleType {None, Test, Score, Both}

    private enum AlgType {Pattern, PAG, DAG, Markov_Random_Field, Pairwise}

    private enum TestType {ChiSquare, FisherZ}

    public enum ScoreType {BDeu, SEM_BIC}


}






