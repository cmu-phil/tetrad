package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetradapp.model.HybridCgImWrapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Objects;
import java.util.Random;

public final class HybridCgImEditor extends JPanel {
    private final HybridCgImWrapper imWrapper;

    private final JTextArea summary = new JTextArea(12, 60);

    public HybridCgImEditor(HybridCgImWrapper wrapper) {
        this.imWrapper = Objects.requireNonNull(wrapper);
        setLayout(new BorderLayout(10,10));

        add(buildTop(), BorderLayout.NORTH);
        add(new JScrollPane(summary), BorderLayout.CENTER);
        refreshSummary();
    }

    private JComponent buildTop() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(new TitledBorder("Hybrid CG IM"));

        JButton randomize = new JButton("Randomize");
        randomize.addActionListener(ev -> {
            HybridCgIm im = imWrapper.getIm();
            try {
                java.lang.reflect.Method m = imWrapper.getClass().getDeclaredMethod("randomize", HybridCgIm.class, long.class);
                m.setAccessible(true);
                m.invoke(null, im, new Random().nextLong()); // uses wrapper's helper
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Randomize not available: " + ex.getMessage(),
                        "Info", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshSummary();
        });

        JButton export = new JButton("Simulate 1000 rows → DataSet");
        export.addActionListener(ev -> {
            HybridCgIm im = imWrapper.getIm();
            HybridCgIm.Sample sample = im.sample(1000, new Random());
            DataSet ds = im.toDataSet(sample);
            JOptionPane.showMessageDialog(this, "Generated DataSet with " + ds.getNumRows() + " rows.",
                    "Simulation", JOptionPane.INFORMATION_MESSAGE);
        });

        p.add(randomize);
        p.add(export);
        return p;
    }

    private void refreshSummary() {
        HybridCgIm im = imWrapper.getIm();
        summary.setText(im == null ? "(no IM)" : im.toString());
        summary.setCaretPosition(0);
    }
}