package edu.cmu.tetradapp.editor;

import javax.swing.*;
import java.awt.*;

public final class HybridCgImEditorWizard {

    private HybridCgImEditorWizard() {}

    public static JDialog create(Window owner, HybridCgImEditor editor) {
        JDialog dlg = new JDialog(owner, "Mixed IM Wizard", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout());
        content.add(editor.getEditorPanel(), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton load = new JButton("Load from Model");
        JButton apply = new JButton("Apply/Save");
        JButton close = new JButton("Close");

        load.addActionListener(e -> {
            // TODO: load parameters from an existing fitted Mixed/Cg model into the table
            JOptionPane.showMessageDialog(dlg, "Load not yet implemented.");
        });
        apply.addActionListener(e -> {
            // TODO: push back to your data structure or registry
            JOptionPane.showMessageDialog(dlg, "Apply not yet implemented.");
        });
        close.addActionListener(e -> dlg.dispose());

        south.add(load);
        south.add(apply);
        south.add(close);

        content.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.setSize(800, 500);
        dlg.setLocationRelativeTo(owner);
        return dlg;
    }
}