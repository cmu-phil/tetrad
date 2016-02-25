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
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * A common way to add menu items to independence test menus to make these menus consistent across editors.
 *
 * @author Joseph Ramsey
 */
public class IndTestMenuItems {
    static void addIndependenceTestChoices(JMenu test, IndTestTypeSetter setter) {
        DataModel dataModel = setter.getDataModel();

        if (dataModel == null &&
                setter.getSourceGraph() != null) {
            addGraphTestMenuItems(test, setter);
        } else if (dataModel instanceof DataSet) {
            DataSet _dataSet = (DataSet) dataModel;

            if (_dataSet.isContinuous()) {
                addContinuousTestMenuItems(test, setter);
            } else if (_dataSet.isDiscrete()) {
                addDiscreteTestMenuItems(test, setter);
            } else if (_dataSet.isMixed()) {
                addMixedTestMenuItems(test, setter);
            } else {
                throw new IllegalArgumentException(
                        "Don't have any tests for mixed data sets right now.");
            }
        } else if (dataModel instanceof ICovarianceMatrix) {
            addCovMatrixTestMenuItems(test, setter);
        } else if (dataModel instanceof DataModelList) {
            DataModelList dataSets = (DataModelList) dataModel;

            boolean continuous = true;

            for (DataModel _dataModel : dataSets) {
                DataSet dataSet = (DataSet) _dataModel;
                if (!dataSet.isContinuous()) continuous = false;
            }

            if (!continuous) {
                throw new IllegalArgumentException("Multiple data sets must all be continuous.");
            }

            addMultiContinuousTestMenuItems(test, setter);
        }
    }

    static void addContinuousTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.FISHER_Z &&
                testType != IndTestType.FISHER_ZD &&
                testType != IndTestType.BIC_BUMP &&
                testType != IndTestType.FISHER_Z_BOOTSTRAP &&
//                testType != IndTestType.CORRELATION_T &&
                testType != IndTestType.CONDITIONAL_CORRELATION &&
                testType != IndTestType.LINEAR_REGRESSION &&
                testType != IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION) {
            setter.setTestType(IndTestType.FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem fishersZ = new JCheckBoxMenuItem("Fisher's Z");
        group.add(fishersZ);
        test.add(fishersZ);

//        JCheckBoxMenuItem fishersZD =
//                new JCheckBoxMenuItem("Fisher's Z - Generalized Inverse");
//        group.add(fishersZD);
//        test.add(fishersZD);
//
//        JCheckBoxMenuItem fishersZBootstrap =
//                new JCheckBoxMenuItem("Fisher's Z - Bootstrap");
//        group.add(fishersZBootstrap);
//        test.add(fishersZBootstrap);

        JCheckBoxMenuItem tTest = new JCheckBoxMenuItem("Correlation T");
        group.add(tTest);
        test.add(tTest);

        JCheckBoxMenuItem logr = new JCheckBoxMenuItem("Multinomial Logistic Regression");
        group.add(logr);
        test.add(logr);
        logr.setSelected(true);

        JCheckBoxMenuItem conditionalCorrelation = new JCheckBoxMenuItem("Conditional Correlation");
        group.add(conditionalCorrelation);
        test.add(conditionalCorrelation);

        JCheckBoxMenuItem linRegrTest = new JCheckBoxMenuItem("Linear Regression Test");
        group.add(linRegrTest);
        test.add(linRegrTest);

        JCheckBoxMenuItem bicBump = new JCheckBoxMenuItem("BIC Score Bump");
        group.add(bicBump);
        test.add(bicBump);

        logr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
            }
        });

        testType = setter.getTestType();
        if (testType == IndTestType.FISHER_Z) {
            fishersZ.setSelected(true);
//        } else if (testType == IndTestType.FISHER_ZD) {
//            fishersZD.setSelected(true);
//        } else if (testType == IndTestType.FISHER_Z_BOOTSTRAP) {
//            fishersZBootstrap.setSelected(true);
//        } else if (testType == IndTestType.CORRELATION_T) {
//            tTest.setSelected(true);
        } else if (testType == IndTestType.CONDITIONAL_CORRELATION) {
            conditionalCorrelation.setSelected(true);
        } else if (testType == IndTestType.LINEAR_REGRESSION) {
            linRegrTest.setSelected(true);
        } else if (testType == IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION) {
            logr.setSelected(true);
        } else if (testType == IndTestType.BIC_BUMP) {
            bicBump.setSelected(true);
        }

        fishersZ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z.");
            }
        });

//        fishersZD.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                setTestType(IndTestType.FISHER_ZD);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Fisher's Z - Generalized Inverse.");
//            }
//        });

//        fishersZBootstrap.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                setTestType(IndTestType.FISHER_Z_BOOTSTRAP);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Fisher's Z - Bootstrap.");
//            }
//        });

//        tTest.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                setter.setTestType(IndTestType.CORRELATION_T);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Correlation T.");
//            }
//        });

        conditionalCorrelation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.CONDITIONAL_CORRELATION);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Conditional Correlation.");
            }
        });

        linRegrTest.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.LINEAR_REGRESSION);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using linear regression test.");
            }
        });

        logr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using multinomial logistic regression test.");
            }
        });

        bicBump.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.BIC_BUMP);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using the BIC bump test.");
            }
        });

    }

    static void addMultiContinuousTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.POOL_RESIDUALS_FISHER_Z
                && testType != IndTestType.TIPPETT
                && testType != IndTestType.FISHER
                && testType != IndTestType.BIC_BUMP) {
            setter.setTestType(IndTestType.POOL_RESIDUALS_FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();

        JCheckBoxMenuItem fisher = new JCheckBoxMenuItem("Fisher (Fisher Z)");
        group.add(fisher);
        test.add(fisher);

        JCheckBoxMenuItem tippett = new JCheckBoxMenuItem("Tippett (Fisher Z)");
        group.add(tippett);
        test.add(tippett);

        JCheckBoxMenuItem fisherZPoolResiduals = new JCheckBoxMenuItem("Pool Residuals (Fisher Z)");
        group.add(fisherZPoolResiduals);
        test.add(fisherZPoolResiduals);

        JCheckBoxMenuItem bicBup = new JCheckBoxMenuItem("BIC Bump (IMaGES)");
        group.add(bicBup);
        test.add(bicBup);

        testType = setter.getTestType();

        if (testType == IndTestType.POOL_RESIDUALS_FISHER_Z) {
            fisherZPoolResiduals.setSelected(true);
        }

        if (testType == IndTestType.FISHER) {
            fisher.setSelected(true);
        }

        if (testType == IndTestType.TIPPETT) {
            tippett.setSelected(true);
        }

        if (testType == IndTestType.BIC_BUMP) {
            bicBup.setSelected(true);
        }

        fisherZPoolResiduals.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.POOL_RESIDUALS_FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Pooled Residuals Fisher Z");
            }
        });

        fisher.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.FISHER);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher");
            }
        });

        tippett.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.TIPPETT);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Tippett");
            }
        });

        bicBup.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.BIC_BUMP);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using BIC Bump (IMaGES)");
            }
        });
    }

    static void addCovMatrixTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.FISHER_Z
//                &&
//                testType != IndTestType.CORRELATION_T
                ) {
            setter.setTestType(IndTestType.FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem fishersZ = new JCheckBoxMenuItem("Fisher's Z");
        group.add(fishersZ);
        test.add(fishersZ);

        JCheckBoxMenuItem tTest = new JCheckBoxMenuItem("Cramer's T");
        group.add(tTest);
        test.add(tTest);

        testType = setter.getTestType();

        if (testType == IndTestType.FISHER_Z) {
            fishersZ.setSelected(true);
        }
//        else if (testType == IndTestType.CORRELATION_T) {
//            tTest.setSelected(true);
//        }

        fishersZ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z.");
            }
        });

//        tTest.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                setter.setTestType(IndTestType.CORRELATION_T);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Cramer's T.");
//            }
//        });
    }

    static void addDiscreteTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.CHI_SQUARE &&
                testType != IndTestType.G_SQUARE &&
                testType != IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION) {
            setter.setTestType(IndTestType.CHI_SQUARE);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem chiSquare = new JCheckBoxMenuItem("Chi Square");
        group.add(chiSquare);
        test.add(chiSquare);

        JCheckBoxMenuItem gSquare = new JCheckBoxMenuItem("G Square");
        group.add(gSquare);
        test.add(gSquare);

        JCheckBoxMenuItem logr = new JCheckBoxMenuItem("Multinomial Logistic Regression");
        group.add(logr);
        test.add(logr);
//
        if (setter.getTestType() == IndTestType.CHI_SQUARE) {
            chiSquare.setSelected(true);
        } else if (setter.getTestType() == IndTestType.G_SQUARE) {
            gSquare.setSelected(true);
        } else if (setter.getTestType() == IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION) {
            logr.setSelected(true);
        }

        chiSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.CHI_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Chi Square.");
            }
        });

        gSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.G_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using G square.");
            }
        });

        logr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using multinomial logistic regression test.");
            }
        });
    }

    static void addMixedTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION) {
            setter.setTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem logr = new JCheckBoxMenuItem("Multinomial Logistic Regression");
        group.add(logr);
        test.add(logr);
        logr.setSelected(true);

        logr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
            }
        });
    }

    public static void addGraphTestMenuItems(JMenu test, final IndTestTypeSetter setter) {
        IndTestType testType = setter.getTestType();
        if (testType != IndTestType.D_SEPARATION) {
            setter.setTestType(IndTestType.D_SEPARATION);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem dsep = new JCheckBoxMenuItem("D-Separation");
        group.add(dsep);
        test.add(dsep);
        dsep.setSelected(true);

        dsep.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setter.setTestType(IndTestType.D_SEPARATION);
            }
        });
    }

}



