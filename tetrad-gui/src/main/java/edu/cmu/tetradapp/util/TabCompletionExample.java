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

package edu.cmu.tetradapp.util;

import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The TabCompletionExample class demonstrates the usage of tab completion in a JTextField.
 */
public class TabCompletionExample {

    /**
     * The main method is the entry point of the TabCompletionExample program. It creates a JFrame window with a
     * JTextField and adds tab completion logic to the text field. The list of words used for tab completion is provided
     * as an argument to the EditorUtils.addTabCompleteLogic method. Finally, the JFrame is set visible and the program
     * starts running.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("Tab Completion Example");

        JTextField textField = new JTextField(30);

        List<String> words = new ArrayList<>();
        words.add("apple");
        words.add("application");
        words.add("banana");
        words.add("cherry");
        words.add("date");
        words.add("grape");

        EditorUtils.addTabCompleteLogic(textField, words);

        frame.add(textField);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

    }

}
