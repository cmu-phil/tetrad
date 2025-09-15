package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;

public class JTextFieldWithPrompt extends JTextField {

    /**
     * Stores the prompt text.
     */
    private final String promptText;

    public JTextFieldWithPrompt(String promptText) {
        this.promptText = promptText;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("JTextField with Prompt Example");
            JTextFieldWithPrompt textField = new JTextFieldWithPrompt("Enter text here...");
            textField.setColumns(30);

            frame.add(textField);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (getText().isEmpty() && !isFocusOwner()) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setFont(getFont());
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.drawString(promptText, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top);
            g2d.dispose();
        }
    }
}