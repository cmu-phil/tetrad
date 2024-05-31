package edu.cmu.tetradapp.util;

import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * The TabCompletionExample class demonstrates the usage of tab completion in a JTextField.
 */
public class TabCompletionExample {
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