package edu.cmu.tetradapp.util;

import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * The TabCompletionExample class demonstrates the usage of tab completion in a JTextField.
 */
public class TabCompletionExample {

    /**
     * The main method is the entry point of the TabCompletionExample program.
     * It creates a JFrame window with a JTextField and adds tab completion logic to the text field.
     * The list of words used for tab completion is provided as an argument to the EditorUtils.addTabCompleteLogic method.
     * Finally, the JFrame is set visible and the program starts running.
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