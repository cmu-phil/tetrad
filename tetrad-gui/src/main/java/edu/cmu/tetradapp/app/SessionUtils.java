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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.AbstractAlgorithmRunner;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Some utility methods relevant to sessions.
 *
 * @author Joseph Ramsey
 */
final class SessionUtils {

    /**
     * Shows a dialog that says "Explanation of [Model type]" followed by a list
     * of each parent combination for that model type with buttons that link to
     * an explanations of how the model works with each combination of parent
     * values.
     *
     * @param warning   If true, shows a warning icon.
     * @param onlyModel If true, displays a message indicating that this is the
     *                  only model consistent with the parents.
     */
    public static void showPermissibleParentsDialog(final Class modelClass,
                                                    final JComponent centeringComp,
                                                    boolean warning,
                                                    boolean onlyModel) {
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        final SessionNodeConfig nodeConfig = config.getSessionNodeConfig(modelClass);
        if (nodeConfig == null) {
            throw new NullPointerException("No configuration for model: " + modelClass);
        }
        final SessionNodeModelConfig modelConfig = nodeConfig.getModelConfig(modelClass);
        String[][] parentCombinations =
                SessionUtils.possibleParentCombinations(modelClass);

        StringBuilder b = new StringBuilder();
        b.append("<html>");
        b.append("The combinations of parent models you can use for ").append(modelConfig.getName()).append(" are:");

        for (int i = 0; i < parentCombinations.length; i++) {
            String[] parentCombination = parentCombinations[i];

            b.append("\n  " + (i + 1) + ". ");

            if (parentCombination.length == 0) {
                b.append("No inputs");
            } else {
                for (int j = 0; j < parentCombination.length; j++) {
                    b.append(parentCombination[j]);

                    if (j < parentCombination.length - 1) {
                        b.append(" + ");
                    }
                }
            }
        }

        int messageType =  warning ? JOptionPane.INFORMATION_MESSAGE :
                        JOptionPane.INFORMATION_MESSAGE;

        JOptionPane.showMessageDialog(centeringComp, b.toString(),
                "Information on \"" + modelConfig.getName() + "\"", messageType);
    }

    /**
     * @return a string listing the combinations of legal parent models for a
     * given model class. The item at [i][j] is the jth parent model description
     * of the ith parent model combination.
     */
    private static String[][] possibleParentCombinations(Class modelClass) {
        List<List<String>> parentCombinations = new LinkedList<>();

        Constructor[] constructors = modelClass.getConstructors();
        boolean foundNull = false;

        PARENT_SET:
        for (Constructor constructor : constructors) {
            List _list = Arrays.asList(constructor.getParameterTypes());
            List parameterTypes = new LinkedList(_list);

            for (Iterator j = parameterTypes.iterator(); j.hasNext(); ) {
                Class parameterType = (Class) j.next();

                if (!(SessionModel.class.isAssignableFrom(parameterType) ||
                        (Parameters.class.isAssignableFrom(parameterType)))) {
                    continue PARENT_SET;
                }

                String descrip = getModelName(parameterType);

                if (descrip == null) {
                    j.remove();
                }
            }

            if (parameterTypes.isEmpty()) {
                foundNull = true;
                continue;
            }

            List<String> combination = new LinkedList<>();

            for (Object parameterType1 : parameterTypes) {
                Class parameterType = (Class) parameterType1;
                String descrip = getModelName(parameterType);
                combination.add(descrip);
            }

            parentCombinations.add(combination);
        }

        if (foundNull) {
            parentCombinations.add(0, new LinkedList<String>());
        }

        String[][] _parentCombinations =
                new String[parentCombinations.size()][];

        for (int i = 0; i < parentCombinations.size(); i++) {
            List<String> combination = parentCombinations.get(i);

            _parentCombinations[i] = new String[combination.size()];

            for (int j = 0; j < combination.size(); j++) {
                _parentCombinations[i][j] = combination.get(j);
            }
        }

        return _parentCombinations;
    }

    //======================================== Private Methods ==============================//

    /**
     * @return the name of the given model
     */
    private static String getModelName(Class model) {
        TetradApplicationConfig tetradConfig = TetradApplicationConfig.getInstance();
        SessionNodeConfig config = tetradConfig.getSessionNodeConfig(model);
        if (config == null) {
            if (AbstractAlgorithmRunner.class.equals(model)) {
                return "Algorithm";
            }

            return null;
        }
        SessionNodeModelConfig modelConfig = config.getModelConfig(model);
        return modelConfig.getName();
    }

//    private static void launchHelpForName(String helpName, String descrip) {
//        JHelp jhelp = new JHelp(TetradHelpBroker.getInstance().getHelpSet());
//        JComponent centeringComp = JOptionUtils.centeringComp();
//
//        try {
//            jhelp.setCurrentID(helpName);
//        }
//        catch (BadIDException e1) {
//            System.out.println("Expected ID = " + helpName);
//        }
//
//        jhelp.setPreferredSize(new Dimension(600, 500));
//        Object owner = centeringComp.getTopLevelAncestor();
//        JDialog dialog;
//
//        if (owner instanceof Dialog) {
//            dialog = new JDialog((Dialog) owner, "Help for \"" + descrip + "\"",
//                    true);
//        } else {
//            dialog = new JDialog((Frame) owner, "Help for \"" + descrip + "\"",
//                    true);
//        }
//
//        dialog.setResizable(true);
//        dialog.getContentPane().add(jhelp, BorderLayout.CENTER);
//        dialog.pack();
//        dialog.setSize(new Dimension(900, 600));
//        dialog.setLocationRelativeTo(centeringComp);
//        dialog.setVisible(true);
//    }


}





