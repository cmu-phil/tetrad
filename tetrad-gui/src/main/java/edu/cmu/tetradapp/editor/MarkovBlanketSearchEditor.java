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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.MarkovBlanketSearchRunner;
import edu.cmu.tetradapp.model.MbSearchParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Editor + param editor for markov blanket searches.
 *
 * @author Tyler Gibson
 */
public class MarkovBlanketSearchEditor extends JPanel implements GraphEditable, IndTestTypeSetter {

    /**
     * The algorithm wrapper being viewed.
     */
    private MarkovBlanketSearchRunner algorithmRunner;


    /**
     * The button one clicks to executeButton the algorithm.
     */
    private JButton executeButton = new JButton();


    /**
     * The scrollpange for the result workbench.
     */
    private JScrollPane workbenchScroll;


    /**
     * Table used to display data.
     */
    private TabularDataJTable table;

    /**
     * True if the warning message that previously defined knowledge is being
     * used has already been shown and doesn't need to be shown again.
     */
    private boolean knowledgeMessageShown = false;


    /**
     * Constructs the eidtor.
     */
    public MarkovBlanketSearchEditor(MarkovBlanketSearchRunner algorithmRunner) {
        if (algorithmRunner == null) {
            throw new NullPointerException();
        }
        this.algorithmRunner = algorithmRunner;
        MbSearchParams params = algorithmRunner.getParams();
        List<String> vars = algorithmRunner.getSource().getVariableNames();
        if (params.getTargetName() == null && !vars.isEmpty()) {
            params.setTargetName(vars.get(0));
        }
        DataSet data;
        if (algorithmRunner.getDataModelForMarkovBlanket() == null) {
            data = algorithmRunner.getSource();
        } else {
            data = algorithmRunner.getDataModelForMarkovBlanket();
        }
        table = new TabularDataJTable(data);
        table.setEditable(false);
        table.setTableHeader(null);

        setup();
    }


    /**
     * @return the data model being viewed.
     */
    public DataModel getDataModel() {
        if (algorithmRunner.getDataModelForMarkovBlanket() != null) {
            return algorithmRunner.getDataModelForMarkovBlanket();
        }

        return algorithmRunner.getSource();
    }

    public Object getSourceGraph() {
        return getParams().getSourceGraph();
    }

    //===========================PRIVATE METHODS==========================//


    /**
     * Executes the algorithm. The execution takes place inside a thread, so one
     * cannot count on a result graph having been found when the method
     */
    private void execute() {
        Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!knowledgeMessageShown) {
                    IKnowledge knowledge = getAlgorithmRunner().getParams().getKnowledge();
                    if (!knowledge.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(),
                                "Using previously set knowledge. (To edit, use " +
                                        "the Knowledge menu.)");
                        knowledgeMessageShown = true;
                    }
                }

                try {
                    getAlgorithmRunner().execute();
                } catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();

                    if (e.getCause() != null) {
                        messageString = e.getCause().getMessage();
                    }

                    if (messageString == null) {
                        messageString = message;
                    }
                    setErrorMessage(messageString);

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }


                setLabel();
                DataSet modelForMarkovBlanket = algorithmRunner.getDataModelForMarkovBlanket();
                if (modelForMarkovBlanket != null) {
                    table.setDataSet(modelForMarkovBlanket);
                }
                table.repaint();
                getExecuteButton().setEnabled(true);
            }
        };

//        getWorkbenchScroll().setBorder(
//                             new TitledBorder(getResultLabel()));
//                     Graph resultGraph = resultGraph();
//
//                     doDefaultArrangement(resultGraph);
//                     getWorkbench().setBackground(Color.WHITE);
//                     getWorkbench().setGraph(resultGraph);
//                     getGraphHistory().clear();
//                     getGraphHistory().add(resultGraph);
//                     getWorkbench().repaint();


        Thread watcher = new Thread() {
            public void run() {
                while (true) {
                    try {
                        sleep(300);

                        if (!process.isAlive()) {
                            getExecuteButton().setEnabled(true);
                            return;
                        }
                    }
                    catch (InterruptedException e) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                }
            }
        };

        watcher.start();
    }

    private void setLabel() {
        getWorkbenchScroll().setBorder(new TitledBorder(this.algorithmRunner.getSearchName()));
    }


    private JButton getExecuteButton() {
        return executeButton;
    }

    private MarkovBlanketSearchRunner getAlgorithmRunner() {
        return algorithmRunner;
    }


    /**
     * Sets up the editor, does the layout, and so on.
     */
    private void setup() {
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.WEST);
        add(workbenchScroll(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }


    /**
     * Creates param editor and tool bar.
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel();
        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                execute();
            }
        });

        Box b1 = Box.createVerticalBox();
        b1.add(getParamEditor());
        b1.add(Box.createVerticalStrut(10));
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        Box b3 = Box.createHorizontalBox();
        JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b3.add(label);
        b1.add(b3);

        toolbar.add(b1);
        return toolbar;
    }

    /**
     * Creates the param editor.
     */
    private JComponent getParamEditor() {
        Box box = Box.createVerticalBox();
        JComboBox comboBox = new JComboBox(this.algorithmRunner.getSource().getVariableNames().toArray());
        comboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                String s = (String) e.getItem();
                if (s != null) {
                    algorithmRunner.getParams().setTargetName(s);
                }
            }
        });
        DoubleTextField alphaField = new DoubleTextField(getParams().getAlpha(), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().setAlpha(value);
                    Preferences.userRoot().putDouble("alpha",
                            getParams().getAlpha());
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        box.add(comboBox);
        box.add(Box.createVerticalStrut(4));
        box.add(createLabeledComponent("Alpha", alphaField));


        box.setBorder(new TitledBorder("Parameters"));
        return box;
    }


    private Box createLabeledComponent(String label, JComponent comp) {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel(label));
        box.add(Box.createHorizontalStrut(5));
        box.add(comp);
        box.add(Box.createHorizontalGlue());

        return box;
    }


    private MbSearchParams getParams() {
        return this.algorithmRunner.getParams();
    }


    /**
     * Creates the workbench
     */
    private JScrollPane workbenchScroll() {
        this.workbenchScroll = new JScrollPane(table);
        this.workbenchScroll.setPreferredSize(new Dimension(500, 500));
        this.setLabel();

        return workbenchScroll;
    }


    /**
     * Creates the menubar for the search editor.
     */
    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new SaveDataAction(this)));
        file.add(new GraphFileMenu(this, getWorkbench()));
//        file.add(new SaveGraph(this, "Save Graph..."));

        JMenu edit = new JMenu("Edit");
        JMenuItem copyCells = new JMenuItem("Copy Cells");
        copyCells.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        copyCells.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Action copyAction = TransferHandler.getCopyAction();
                ActionEvent actionEvent = new ActionEvent(table,
                        ActionEvent.ACTION_PERFORMED, "copy");
                copyAction.actionPerformed(actionEvent);
            }
        });
        edit.add(copyCells);


        menuBar.add(file);
        menuBar.add(edit);

        JMenu independence = new JMenu("Independence");
        if (algorithmRunner.getSource().isContinuous()) {
            IndTestMenuItems.addContinuousTestMenuItems(independence, this);
            menuBar.add(independence);
        } else if (algorithmRunner.getSource().isDiscrete()) {
            IndTestMenuItems.addDiscreteTestMenuItems(independence, this);
            menuBar.add(independence);
        }


        menuBar.add(independence);

        return menuBar;
    }


    /**
     * Builds the ind test menu items for condinuous data and adds them to the given menu.
     */
    private void addContinuousTestMenuItems(JMenu test) {
        IndTestType testType = getParams().getIndTestType();
        if (testType != IndTestType.FISHER_Z &&
                testType != IndTestType.FISHER_ZD &&
                testType != IndTestType.FISHER_Z_BOOTSTRAP &&
//                testType != IndTestType.CORRELATION_T &&
                testType != IndTestType.LINEAR_REGRESSION) {
            getParams().setIndTestType(IndTestType.FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem fishersZ = new JCheckBoxMenuItem("Fisher's Z");
        group.add(fishersZ);
        test.add(fishersZ);

        JCheckBoxMenuItem fishersZD =
                new JCheckBoxMenuItem("Fisher's Z - Generalized Inverse");
        group.add(fishersZD);
        test.add(fishersZD);

        JCheckBoxMenuItem fishersZBootstrap =
                new JCheckBoxMenuItem("Fisher's Z - Bootstrap");
        group.add(fishersZBootstrap);
        test.add(fishersZBootstrap);

        JCheckBoxMenuItem tTest = new JCheckBoxMenuItem("Cramer's T");
        group.add(tTest);
        test.add(tTest);

        JCheckBoxMenuItem linRegrTest =
                new JCheckBoxMenuItem("Linear Regression Test");
        group.add(linRegrTest);
        test.add(linRegrTest);

        testType = getParams().getIndTestType();
        if (testType == IndTestType.FISHER_Z) {
            fishersZ.setSelected(true);
        } else if (testType == IndTestType.FISHER_ZD) {
            fishersZD.setSelected(true);
        } else if (testType == IndTestType.FISHER_Z_BOOTSTRAP) {
            fishersZBootstrap.setSelected(true);
//        } else if (testType == IndTestType.CORRELATION_T) {
//            tTest.setSelected(true);
        } else if (testType == IndTestType.LINEAR_REGRESSION) {
            linRegrTest.setSelected(true);
        }

        fishersZ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z.");
            }
        });

        fishersZD.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.FISHER_ZD);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z - Generalized Inverse.");
            }
        });

        fishersZBootstrap.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.FISHER_Z_BOOTSTRAP);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z - Bootstrap.");
            }
        });

//        tTest.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                getParams().setIndTestType(IndTestType.CORRELATION_T);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Cramer's T.");
//            }
//        });

        linRegrTest.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.LINEAR_REGRESSION);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using linear regression test.");
            }
        });
    }


    /**
     * Builds the ind test menu items for discrete data and adds them to the given menu.
     */
    private void addDiscreteTestMenuItems(JMenu test) {
        IndTestType testType = getParams().getIndTestType();
        if (testType != IndTestType.CHI_SQUARE &&
                testType != IndTestType.G_SQUARE) {
            getParams().setIndTestType(testType);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem chiSquare = new JCheckBoxMenuItem("Chi Square");
        group.add(chiSquare);
        test.add(chiSquare);

        JCheckBoxMenuItem gSquare = new JCheckBoxMenuItem("G Square");
        group.add(gSquare);
        test.add(gSquare);

        if (getParams().getIndTestType() == IndTestType.CHI_SQUARE) {
            chiSquare.setSelected(true);
        } else if (getParams().getIndTestType() == IndTestType.G_SQUARE) {
            gSquare.setSelected(true);
        }

        chiSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.CHI_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Chi Square.");
            }
        });

        gSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setIndTestType(IndTestType.G_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using G square.");
            }
        });
    }


    private JScrollPane getWorkbenchScroll() {
        return workbenchScroll;
    }

    public List getSelectedModelComponents() {
        throw new UnsupportedOperationException("Cannot return selected components.");
    }

    public void pasteSubsession(List sessionElements, Point upperLeft) {
        throw new UnsupportedOperationException("Cannot paste subsessions on a search editor.");
    }

    public GraphWorkbench getWorkbench() {
        return getWorkbench();
    }

    /**
     * Not supported.
     */
    public void setGraph(Graph g) {
        throw new UnsupportedOperationException("Cannot set the graph on a search editor.");
    }


    /**
     * @return the graph.
     */
    public Graph getGraph() {
        if (getWorkbench().getGraph() != null) {
            return getWorkbench().getGraph();
        }
        return new EdgeListGraph();
    }

    public void setTestType(IndTestType testType) {
        getParams().setIndTestType(testType);
    }

    public IndTestType getTestType() {
        return getParams().getIndTestType();
    }
}




