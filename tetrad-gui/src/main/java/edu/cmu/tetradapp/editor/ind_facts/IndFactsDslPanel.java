package edu.cmu.tetradapp.editor.ind_facts;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class IndFactsDslPanel extends JPanel {

    private final JTextPane editor = new JTextPane();
    private final JTextArea output = new JTextArea();
    private final Highlighter highlighter;
    private final Highlighter.HighlightPainter squiggle = new WavyUnderlinePainter(Color.RED);

    private final List<String> varNames;
    private final Map<String, Set<String>> adjMap; // may be null

    private int previewLimit = 200;

    // For tooltips
    private final List<IndFactsDsl.ParseError> lastErrors = new ArrayList<>();

    public IndFactsDslPanel(List<String> varNames, Map<String, Set<String>> adjMap) {
        super(new BorderLayout());
        this.varNames = new ArrayList<>(Objects.requireNonNull(varNames));
        this.adjMap = adjMap;

        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setText("""
                # Examples:
                X10 _||_ X1 | X3 +k(0..2)
                ?x _||_ ?y
                X1 _||_ X2 | {X3..X5}
                """);

        // Tooltip plumbing: show error message if mouse is over a highlighted span
        editor.setToolTipText("");
        editor.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int pos = editor.viewToModel(e.getPoint());
                String tip = null;
                for (IndFactsDsl.ParseError pe : lastErrors) {
                    if (pos >= pe.startOffset() && pos <= pe.endOffset()) {
                        tip = pe.message();
                        break;
                    }
                }
                editor.setToolTipText(tip);
            }
        });

        highlighter = editor.getHighlighter();

        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setEditable(false);

        JButton preview = new JButton("Preview");
        preview.addActionListener(e -> runPreview());

        JSpinner limitSpin = new JSpinner(new SpinnerNumberModel(previewLimit, 10, 10000, 10));
        limitSpin.addChangeListener(e -> previewLimit = (Integer) limitSpin.getValue());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(preview);
        top.add(new JLabel("Preview limit:"));
        top.add(limitSpin);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(editor),
                new JScrollPane(output));
        split.setResizeWeight(0.6);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Re-validate squiggles on edit (lightweight)
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { validateOnly(); }
            @Override public void removeUpdate(DocumentEvent e) { validateOnly(); }
            @Override public void changedUpdate(DocumentEvent e) { validateOnly(); }
        });

        validateOnly();
    }

    private void validateOnly() {
        // Do a parse pass, highlight errors, but don't expand big previews automatically.
        IndFactsDsl.PreviewResult pr = IndFactsDsl.preview(editor.getText(), varNames, adjMap, 0);
        applyErrors(pr.errors());
    }

    private void runPreview() {
        IndFactsDsl.PreviewResult pr = IndFactsDsl.preview(editor.getText(), varNames, adjMap, previewLimit);
        applyErrors(pr.errors());

        StringBuilder sb = new StringBuilder();
        sb.append("Parse errors: ").append(pr.errors().size()).append("\n");
        sb.append("Total expanded: ").append(pr.totalExpanded()).append("\n");
        sb.append("Total kept: ").append(pr.totalKept()).append("\n");
        sb.append("Total skipped invalid: ").append(pr.totalSkippedInvalid()).append("\n\n");

        // Per-line stats
        if (!pr.lineStats().isEmpty()) {
            sb.append("Per-line:\n");
            for (IndFactsDsl.PreviewLineStats ls : pr.lineStats()) {
                sb.append("  Line ").append(ls.lineIndex0() + 1).append(": expanded ")
                        .append(ls.expanded()).append(", kept ").append(ls.kept())
                        .append(", skippedInvalid ").append(ls.skippedInvalid());
                if (ls.warningZeroKept()) sb.append("  [WARNING: 0 kept]");
                sb.append("\n");
            }
            sb.append("\n");
        }

        // First N facts
        if (!pr.firstFacts().isEmpty()) {
            sb.append("First ").append(pr.firstFacts().size()).append(" facts:\n");
            for (String f : pr.firstFacts()) sb.append("  ").append(f).append("\n");
        }

        output.setText(sb.toString());
        output.setCaretPosition(0);
    }

    private void applyErrors(List<IndFactsDsl.ParseError> errors) {
        lastErrors.clear();
        lastErrors.addAll(errors);

        highlighter.removeAllHighlights();
        for (IndFactsDsl.ParseError pe : errors) {
            try {
                highlighter.addHighlight(pe.startOffset(), pe.endOffset(), squiggle);
            } catch (BadLocationException ignored) {
            }
        }
    }

    // Simple demo main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            List<String> vars = new ArrayList<>();
            for (int i = 1; i <= 12; i++) vars.add("X" + i);

            // Example adjacency for pool(adj)
            Map<String, Set<String>> adj = new HashMap<>();
            for (String v : vars) adj.put(v, new HashSet<>());

            // A tiny chain: X1-X2-X3-X4
            link(adj, "X1", "X2");
            link(adj, "X2", "X3");
            link(adj, "X3", "X4");

            JFrame f = new JFrame("IndFacts DSL v0.1 Preview");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(new IndFactsDslPanel(vars, adj));
            f.setSize(900, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private static void link(Map<String, Set<String>> adj, String a, String b) {
        adj.get(a).add(b);
        adj.get(b).add(a);
    }
}