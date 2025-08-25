package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlockSpecTextCodec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Text-first BlockSpec editor using JTextPane + Highlighter. - Live validation via BlockSpecTextCodec (red=error,
 * orange=warning) - Tooltips for issues - Simple autocomplete for variable names (Cmd/Ctrl+Space) - Undo/Redo
 * (Cmd/Ctrl+Z, Shift+Cmd/Ctrl+Z) - Import/Export buttons - Canonicalize button that preserves comments &amp; line order
 * (rewrites RHS only) - Apply button invokes a user-supplied callback with the current BlockSpec
 */
public final class BlockSpecEditorPanel extends JPanel {

    // Reuse the same token pattern as the codec for canonicalize RHS
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|#(\\d+)|([^,\\s]+)");
    private final JTextPane textPane = new JTextPane();
    private final JLabel status = new JLabel("Ready");
    private final JButton btnImport = new JButton("Import…");
    private final JButton btnExport = new JButton("Export…");
    private final JButton btnVars = new JButton("List Variables");
    private final JButton btnAlphatize = new JButton("Alphabetize");
    private final JButton btnApply = new JButton("Keep Changes");
    private final JPopupMenu completionPopup = new JPopupMenu();
    private final JList<String> completionList = new JList<>();
    private final DefaultListModel<String> completionModel = new DefaultListModel<>();
    private final Highlighter.HighlightPainter errorPainter = new SquigglePainter(new Color(220, 50, 47));  // red
    private final Highlighter.HighlightPainter warnPainter = new SquigglePainter(new Color(203, 75, 22));  // orange
    private final Timer debounce;
    private final UndoManager undo = new UndoManager();
    private final AttributeSet COMMENT_STYLE =
            StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY,
                    StyleConstants.Foreground, Color.GRAY);
    private DataSet dataSet;
    private BlockSpec currentSpec;                 // last successful parse
    private List<BlockSpecTextCodec.Issue> issues; // last issues
    private Consumer<BlockSpec> onApply;           // user callback
    private String originalText = "";

    public BlockSpecEditorPanel(DataSet dataSet, String blockText) {
        super(new BorderLayout());
        this.dataSet = Objects.requireNonNull(dataSet, "DataSet is required");

        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textPane.setMargin(new Insets(8, 10, 8, 10));
        textPane.setCaretColor(Color.DARK_GRAY);

        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        // Undo/Redo support
        textPane.getDocument().addUndoableEditListener(undo);
        bindUndoRedo();

        // Debounce parsing
        debounce = new Timer(250, e -> parseAndRender());
        debounce.setRepeats(false);

        // Live validation
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });

        // Tooltips for issues under mouse
        textPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int pos = textPane.viewToModel2D(e.getPoint());
                BlockSpecTextCodec.Issue hit = issueAtOffset(pos);
                textPane.setToolTipText(hit == null ? null :
                        hit.message() + (hit.token() == null ? "" : " [" + hit.token() + "]"));
            }
        });

        // Autocomplete binding
        KeyStroke ksAutocomplete = KeyStroke.getKeyStroke(
                KeyEvent.VK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        textPane.getInputMap().put(ksAutocomplete, "AUTO_COMPLETE");
        textPane.getActionMap().put("AUTO_COMPLETE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showCompletion();
            }
        });

        // Apply binding (Shift+Enter)
        textPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "APPLY_SPEC");
        textPane.getActionMap().put("APPLY_SPEC", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyIfClean();
            }
        });

        // Completion popup
        completionList.setModel(completionModel);
        completionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        completionList.setVisibleRowCount(8);
        completionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) insertSelectedCompletion();
            }
        });
        completionList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) insertSelectedCompletion();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) completionPopup.setVisible(false);
            }
        });
        completionPopup.add(new JScrollPane(completionList));

        // Bottom bar
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        JPanel buttonsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JPanel buttonsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
//        buttonsRight.add(btnImport);
//        buttonsRight.add(btnExport);
        buttonsRight.add(btnVars);        // <= add here
        buttonsRight.add(btnAlphatize);
        buttonsRight.add(btnApply);
        bottom.add(status, BorderLayout.CENTER);
//        bottom.add(buttonsLeft, BorderLayout.WEST);
        bottom.add(buttonsRight, BorderLayout.EAST);

        btnApply.addActionListener(e -> applyIfClean());
        btnAlphatize.addActionListener(e -> canonicalizePreservingComments());
        btnImport.addActionListener(e -> doImport());
        btnExport.addActionListener(e -> doExport());
        btnVars.addActionListener(e -> insertVariableListComment());

        btnApply.addActionListener(e -> applyIfClean());
        btnAlphatize.addActionListener(e -> canonicalizePreservingComments());
        btnImport.addActionListener(e -> doImport());
        btnExport.addActionListener(e -> doExport());

        add(new JScrollPane(textPane,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Seed with empty text
        setText(blockText);
    }

    private static int indexOfToken(String line, String token) {
        int p = line.indexOf(token);
        if (p >= 0) return p;
        String quoted = "\"" + token + "\"";
        int q = line.indexOf(quoted);
        return q >= 0 ? q + 1 : -1; // +1 to point inside the quotes
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("BlockSpec Editor");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            // TODO: replace with a real dataset from Tetrad runtime.
            DataSet ds = DemoData.smallDemoData();

            BlockSpecEditorPanel panel = new BlockSpecEditorPanel(ds, "");
            panel.setText("""
                    % Example
                    L1: X1, X2, "X 3"
                    L2: X4, X5
                    X6
                    """);
            f.setContentPane(panel);
            f.setSize(800, 600);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /**
     * Inserts a wrapped % comment block listing all variables.
     */
    private void insertVariableListComment() {
        String block = buildVariableListComment(72, /* includeIndices */ false);
        insertTextAtCaret(block);
    }

    // ---------------- Public API ----------------

    /**
     * Build a % comment block of available variables, wrapped to maxWidth columns.
     */
    private String buildVariableListComment(int maxWidth, boolean includeIndices) {
        StringBuilder sb = new StringBuilder();
        sb.append("%\n% Available variables:\n% ");
        int col = 2; // "% " already on the line
        List<Node> variables = dataSet.getVariables();
        Collections.sort(variables);

        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            Node v = variables.get(i);
            String token = includeIndices ? (v.getName() + " (#" + i + ")") : v.getName();

            // +1 for trailing space we're about to add
            if (col + token.length() + 1 > maxWidth) {
                sb.append("\n% ");
                col = 2;
            }
            sb.append(token).append(' ');
            col += token.length() + 1;
        }
        sb.append("\n%\n"); // blank comment line separator
        return sb.toString();
    }

    /**
     * Safe insert at caret, expanding undo correctly.
     */
    private void insertTextAtCaret(String text) {
        try {
            Document doc = textPane.getDocument();
            int pos = textPane.getCaretPosition();
            doc.insertString(pos, text, null);
        } catch (BadLocationException ignored) {
        }
    }

    public void setDataSet(DataSet ds) {
        this.dataSet = Objects.requireNonNull(ds);
        parseAndRender();
    }

    public void setOnApply(Consumer<BlockSpec> onApply) {
        this.onApply = onApply;
    }

    public void setText(String text, boolean markOriginal) {
        textPane.setText(text == null ? "" : text);
        textPane.setCaretPosition(textPane.getDocument().getLength());
        if (markOriginal) originalText = textPane.getText();
        undo.discardAllEdits();
        parseAndRender();
    }

    public String getText() {
        return textPane.getText();
    }

    public void setText(String text) {
        setText(text, true);
    }

    // ---------------- Core parsing & rendering ----------------

    public BlockSpec getCurrentSpec() {
        return currentSpec;
    }

    public boolean isClean() {
        return issues == null || issues.stream().noneMatch(i -> i.severity() == BlockSpecTextCodec.Severity.ERROR);
    }

    private void parseAndRender() {
        // Remove old highlights
        Highlighter hl = textPane.getHighlighter();
        for (Highlighter.Highlight h : hl.getHighlights()) {
            if (h.getPainter() == errorPainter || h.getPainter() == warnPainter) {
                hl.removeHighlight(h);
            }
        }

        BlockSpecTextCodec.ParseResult result = BlockSpecTextCodec.parse(textPane.getText(), dataSet);
        this.issues = result.issues();

        // Add highlights
        for (BlockSpecTextCodec.Issue is : issues) {
            try {
                int[] range = lineTokenRange(is.line(), is.token());
                if (range != null) {
                    hl.addHighlight(range[0], range[1],
                            is.severity() == BlockSpecTextCodec.Severity.ERROR ? errorPainter : warnPainter);
                }
            } catch (BadLocationException ignored) {
            }
        }

        // Status + spec
        long nErr = issues.stream().filter(i -> i.severity() == BlockSpecTextCodec.Severity.ERROR).count();
        long nWarn = issues.stream().filter(i -> i.severity() == BlockSpecTextCodec.Severity.WARNING).count();

        if (nErr == 0) {
            this.currentSpec = result.spec();
            try {
                BlocksUtil.validateBlocks(currentSpec.blocks(), currentSpec.dataSet());
            } catch (RuntimeException ex) {
                // Surface unexpected validation problems as errors in the status
                status.setText("Validation error: " + ex.getMessage());
                status.setForeground(new Color(220, 50, 47));
                btnApply.setEnabled(false);
                return;
            }
            status.setText("OK: " + currentSpec.blocks().size() + " blocks"
                           + (nWarn > 0 ? (" • " + nWarn + " warning(s)") : ""));
            status.setForeground(new Color(38, 139, 210)); // blue
        } else {
            this.currentSpec = null;
            status.setText("Errors: " + nErr + (nWarn > 0 ? (" • Warnings: " + nWarn) : ""));
            status.setForeground(new Color(220, 50, 47)); // red
        }
        btnApply.setEnabled(nErr == 0);
        colorCommentLines();
    }

    // call this at the end of parseAndRender()
    private void colorCommentLines() {
        try {
            StyledDocument doc = (textPane.getDocument() instanceof StyledDocument sd) ? sd : null;
            if (doc == null) return;
            Element root = doc.getDefaultRootElement();
            for (int i = 0; i < root.getElementCount(); i++) {
                Element line = root.getElement(i);
                String txt = doc.getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset());
                if (txt.stripLeading().startsWith("%")) {
                    doc.setCharacterAttributes(line.getStartOffset(),
                            line.getEndOffset() - line.getStartOffset(), COMMENT_STYLE, true);
                } else {
                    // reset to default (so non-comments don’t stay gray after edits)
                    doc.setCharacterAttributes(line.getStartOffset(),
                            line.getEndOffset() - line.getStartOffset(), SimpleAttributeSet.EMPTY, true);
                }
            }
        } catch (BadLocationException ignored) {
        }
    }

    private int[] lineTokenRange(int oneBasedLine, String token) throws BadLocationException {
        if (token == null) return null;
        Element root = textPane.getDocument().getDefaultRootElement();
        int lineIdx = oneBasedLine - 1;
        if (lineIdx < 0 || lineIdx >= root.getElementCount()) return null;
        Element lineEl = root.getElement(lineIdx);
        int start = lineEl.getStartOffset();
        int end = lineEl.getEndOffset();
        String lineText = textPane.getDocument().getText(start, end - start);
        int off = indexOfToken(lineText, token);
        if (off < 0) return null;
        return new int[]{start + off, start + off + token.length()};
    }

    // ---------------- Actions ----------------

    private BlockSpecTextCodec.Issue issueAtOffset(int pos) {
        if (issues == null) return null;
        Element root = textPane.getDocument().getDefaultRootElement();
        int lineIdx = root.getElementIndex(pos);
        for (BlockSpecTextCodec.Issue is : issues) {
            try {
                int[] r = lineTokenRange(is.line(), is.token());
                if (r != null && pos >= r[0] && pos <= r[1]) return is;
            } catch (BadLocationException ignored) {
            }
        }
        return null;
    }

    private void applyIfClean() {
        if (isClean() && currentSpec != null) {
            try {
                if (onApply != null) onApply.accept(currentSpec);
                status.setText("Applied • " + currentSpec.blocks().size() + " blocks");
                status.setForeground(new Color(38, 139, 210));
            } catch (Exception ex) {
                status.setText("Apply failed: " + ex.getMessage());
                status.setForeground(new Color(220, 50, 47));
                Toolkit.getDefaultToolkit().beep();
            }
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void doImport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import Blocks");
        String sessionSaveLocation = Preferences.userRoot().get(
                "fileSaveLocation", Preferences.userRoot().absolutePath());
        fc.setCurrentDirectory(new File(sessionSaveLocation));
        fc.resetChoosableFileFilters();
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".blocks");
            }

            @Override
            public String getDescription() {
                return "Blocks Files";
            }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String text = Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8);
                setText(text);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to read file:\n" + ex.getMessage(),
                        "Import", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doExport() {
        JFileChooser fc = new JFileChooser();
        String sessionSaveLocation = Preferences.userRoot().get(
                "fileSaveLocation", Preferences.userRoot().absolutePath());
        fc.setCurrentDirectory(new File(sessionSaveLocation));
        fc.resetChoosableFileFilters();
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".blocks");
            }

            @Override
            public String getDescription() {
                return "Blocks Files";
            }
        });
        fc.setDialogTitle("Export Blocks");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path path = fc.getSelectedFile().toPath();
                if (!path.toString().endsWith(".blocks")) {
                    path = Path.of(path.toString() + ".blocks");
                }
                Files.writeString(path, getText(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to write file:\n" + ex.getMessage(),
                        "Export", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---------------- Autocomplete ----------------

    /**
     * Canonicalize RHS in-place: - Keep comments & blank lines as-is - Keep block order & names/ranks as-is - For lines
     * with "lhs: rhs", rewrite rhs as sorted, de-duplicated member names - For singleton lines (no ":"), leave as-is
     */
    private void canonicalizePreservingComments() {
        Element root = textPane.getDocument().getDefaultRootElement();
        int lineCount = root.getElementCount();
        List<String> lines = new ArrayList<>(lineCount);
        try {
            for (int i = 0; i < lineCount; i++) {
                Element el = root.getElement(i);
                String line = textPane.getDocument().getText(el.getStartOffset(),
                        el.getEndOffset() - el.getStartOffset());
                // strip trailing newline the Document may include
                if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
                lines.add(line);
            }
        } catch (BadLocationException e) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        List<String> rewritten = new ArrayList<>(lines.size());
        Map<Integer, String> idxToName = new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            idxToName.put(i, dataSet.getVariable(i).getName());
        }
        Map<String, Integer> nameToIdx = idxToName.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        for (String raw : lines) {
            String s = raw.stripTrailing();
            if (s.isBlank() || s.stripLeading().startsWith("%")) {
                rewritten.add(raw); // keep comments/blanks exactly
                continue;
            }
            int colon = s.indexOf(':');
            if (colon < 0) {
                // singleton line (token) → leave as-is
                rewritten.add(raw);
                continue;
            }
            String lhs = s.substring(0, colon).trim();
            String rhs = s.substring(colon + 1);

            // Parse RHS tokens to names
            List<String> tokens = new ArrayList<>();
            Matcher mt = TOKEN.matcher(rhs);
            while (mt.find()) {
                String q = mt.group(1);
                String idx = mt.group(2);
                String bare = mt.group(3);
                if (q != null) tokens.add(q);
                else if (idx != null) tokens.add("#" + idx);
                else if (bare != null) tokens.add(bare);
            }

            // Map tokens to variable names (drop unknowns; we don’t alter them here)
            List<String> names = new ArrayList<>();
            for (String tok : tokens) {
                if (tok.startsWith("#")) {
                    try {
                        int k = Integer.parseInt(tok.substring(1));
                        String nm = idxToName.get(k);
                        if (nm != null) names.add(nm);
                    } catch (NumberFormatException ignored) {
                    }
                } else if (nameToIdx.containsKey(tok)) {
                    names.add(tok);
                }
            }

            // Sort & dedup names
            LinkedHashSet<String> uniq = new LinkedHashSet<>(names); // preserve first occurrence
            List<String> sorted = new ArrayList<>(uniq);
            Collections.sort(sorted);

            String joined = String.join(", ", sorted);
            String newLine = lhs + ": " + joined;
            // Preserve original trailing whitespace difference if desired; simple replace here:
            rewritten.add(newLine);
        }

        String newText = String.join("\n", rewritten);
        setText(newText, false); // don’t replace originalText; keep undo stack cleared
    }

    private void showCompletion() {
        completionModel.clear();

        // Names not yet used (based on currentSpec)
        Set<String> used = new HashSet<>();
        if (currentSpec != null) {
            for (List<Integer> b : currentSpec.blocks()) {
                for (int idx : b) used.add(currentSpec.dataSet().getVariable(idx).getName());
            }
        }
        List<String> candidates = dataSet.getVariables().stream()
                .map(Node::getName)
                .filter(n -> !used.contains(n))
                .sorted()
                .collect(Collectors.toList());
        for (String c : candidates) completionModel.addElement(c);

        if (completionModel.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        completionList.setSelectedIndex(0);

        try {
            Rectangle caret = textPane.modelToView2D(textPane.getCaretPosition()).getBounds();
            completionPopup.show(textPane, caret.x, caret.y + caret.height);
        } catch (BadLocationException ignored) {
            completionPopup.show(textPane, 8, textPane.getHeight() - 8);
        }
        completionList.requestFocusInWindow();
    }

    // ---------------- Undo/Redo ----------------

    private void insertSelectedCompletion() {
        String sel = completionList.getSelectedValue();
        if (sel == null) return;
        completionPopup.setVisible(false);
        try {
            Document doc = textPane.getDocument();
            int pos = textPane.getCaretPosition();
            String ins = sel;
            if (pos > 0) {
                String prev = doc.getText(Math.max(0, pos - 1), 1);
                if (!prev.isBlank() && !prev.equals(",") && !prev.equals(":")) ins = ", " + ins;
                else if (prev.equals(":")) ins = " " + ins;
            }
            doc.insertString(pos, ins, null);
        } catch (BadLocationException ignored) {
        }
    }

    private void bindUndoRedo() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        textPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), "UNDO");
        textPane.getActionMap().put("UNDO", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canUndo()) undo.undo();
                } catch (CannotUndoException ignored) {
                }
            }
        });

        textPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask), "REDO");
        textPane.getActionMap().put("REDO", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canRedo()) undo.redo();
                } catch (CannotRedoException ignored) {
                }
            }
        });

        // Also support Shift+Cmd/Ctrl+Z for redo (common on macOS)
        textPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK), "REDO");
    }

    // ---------------- Painter ----------------

    public String getBlockText() {
        return textPane.getText();
    }

    // ---------- Minimal demo ----------

    /**
     * Simple squiggle underline painter for error/warning highlights.
     */
    private static final class SquigglePainter implements Highlighter.HighlightPainter {
        private final Color color;
        private final int amplitude = 2;
        private final int wavelength = 4;

        SquigglePainter(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView2D(offs0).getBounds();
                Rectangle r1 = c.modelToView2D(offs1).getBounds();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);

                if (r0.y == r1.y) {
                    // Single line
                    drawSquiggle(g2, r0.x, r1.x, r0.y + r0.height - 2);
                } else {
                    // Multi-line: draw to end of first line, then full lines in between, then from start of last line
                    Rectangle rEndFirst = new Rectangle(r0.x, r0.y, c.getWidth(), r0.height);
                    drawSquiggle(g2, r0.x, rEndFirst.x + rEndFirst.width - 6, r0.y + r0.height - 2);

                    // (Simple version) we won’t try to paint intermediate lines perfectly; good enough for tokens
                    drawSquiggle(g2, r1.x - 6, r1.x, r1.y + r1.height - 2);
                }
                g2.dispose();
            } catch (BadLocationException ignored) {
            }
        }

        private void drawSquiggle(Graphics2D g2, int x0, int x1, int y) {
            int cur = x0;
            while (cur < x1) {
                int mid = Math.min(x1, cur + wavelength / 2);
                int nxt = Math.min(x1, cur + wavelength);
                g2.drawLine(cur, y, mid, y + amplitude);
                g2.drawLine(mid, y + amplitude, nxt, y);
                cur = nxt;
            }
        }
    }

    // Tiny stub so the demo runs; remove in production.
    private static final class DemoData {
        static DataSet smallDemoData() {
            var vars = new ArrayList<Node>();
            for (int i = 1; i <= 10; i++) {
                vars.add(new edu.cmu.tetrad.data.ContinuousVariable("X" + i));
            }
            return new edu.cmu.tetrad.data.BoxDataSet(new edu.cmu.tetrad.data.VerticalDoubleDataBox(10, 10), vars);
        }
    }
}