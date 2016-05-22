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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.LogisticRegressionParams;
import edu.cmu.tetradapp.model.LogisticRegressionRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

/**
 * Allows the user to execute a logistic regression in the GUI. Contains a panel
 * that lets the user specify a target variable (which must be binary) and a
 * list of continuous regressors, plus a tabbed pane that includes (a) a text
 * area to show the report of the logistic regression and (b) a graph workbench
 * to show the graph of the target with significant regressors from the
 * regression as parents.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM
 *         Bayes estimator
 */
public class LogisticRegressionEditor extends JPanel {

    /**
     * Text area for display output.
     */
    private JTextArea modelParameters;


    /**
     * The number formatter used for printing reports.
     */
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();


    public LogisticRegressionEditor(LogisticRegressionRunner regressionRunner) {
        final LogisticRegressionRunner regRunner = regressionRunner;
        final GraphWorkbench workbench = new GraphWorkbench();
        this.modelParameters = new JTextArea();
        final JButton executeButton = new JButton("Execute");

        //this.modelParameters.setFont(new Font("Monospaced", Font.PLAIN, 12));

        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                regRunner.execute();
              //  modelParameters.setText(regRunner.getReport());
                print(regRunner.getLogisticRegression(), regRunner.getAlpha());
                Graph outGraph = regRunner.getOutGraph();
                GraphUtils.circleLayout(outGraph, 200, 200, 150);
                GraphUtils.fruchtermanReingoldLayout(outGraph);
                workbench.setGraph(outGraph);
                TetradLogger.getInstance().log("result", modelParameters.getText());
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(600, 400));
        tabbedPane.add("Model", new JScrollPane(this.modelParameters));
        tabbedPane.add("Output Graph", new JScrollPane(workbench));

        LogisticRegressionParams params =
                (LogisticRegressionParams) regRunner.getParams();
        RegressionParamsEditorPanel paramsPanel = new RegressionParamsEditorPanel(params, regRunner.getDataModel());

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(paramsPanel);
        b1.add(Box.createHorizontalStrut(5));
        b1.add(tabbedPane);
        b.add(b1);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(executeButton);
        b.add(buttonPanel);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }


    //============================== Private Methods =====================================//

    /**
     * Prints the info in the logisticRegression to the text area (doesn't use the results representation).
     */
    private void print(LogisticRegression logisticRegression, double alpha){
        if(logisticRegression == null){
            return;
        }
        // print cases
        String text = logisticRegression.getNy0() + " cases have " + logisticRegression.getTargetName()  + " = 0; ";
        text += logisticRegression.getNy1() + " cases have " + logisticRegression.getTargetName() + " = 1.\n\n";
        // print avgs/SD
        text += "Var\tAvg\tSD\n";
        for(int i = 1; i<=logisticRegression.getNumRegressors(); i++){
            text += logisticRegression.getRegressorNames().get(i - 1) + "\t";
            text += nf.format(logisticRegression.getxMeans()[i]) + "\t";
            text += nf.format(logisticRegression.getStdErrs()[i]) + "\n";
        }
        text += "\nCoefficients and Standard Errors:\n";
        text += "Var\tCoeff.\tStdErr\tProb.\tSig.\n";
        for(int i = 1; i<=logisticRegression.getNumRegressors(); i++){
            text += logisticRegression.getRegressorNames().get(i - 1) + "\t";
            text += nf.format(logisticRegression.getCoefs()[i]) + "\t";
            text += nf.format(logisticRegression.getStdErrs()[i]) + "\t";
            text += nf.format(logisticRegression.getProbs()[i]) + "\t";
            if(logisticRegression.getProbs()[i] < alpha){
                text += "*\n";
            } else {
                text += "\n";
            }
        }

        text+= "\n\nIntercept = " + nf.format(logisticRegression.getIntercept()) + "\n";
        
        this.modelParameters.setText(text);
    }



}




