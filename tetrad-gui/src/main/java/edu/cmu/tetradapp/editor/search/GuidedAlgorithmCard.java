/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BlockScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.DeprecationUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.Tetrad;
import edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.Answers;
import edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.LatentChoice;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.model.*;
import edu.cmu.tetradapp.util.ParameterToolTips;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

/**
 * The guided algorithm chooser: the first card of the search box.
 * <p>
 * Added 2026-8-24, replacing {@link AlgorithmCard} as the default (the classic card is still available by setting the
 * user preference {@code useClassicAlgorithmCard} to true). The layout follows the "Guided wireframe" design:
 * <ul>
 * <li>Left column, "Tell me about your data": (1) a statement of what the connected data is, since the search box
 * already knows it and asking would only let the answer disagree with the data; (2) "Could something unmeasured cause
 * two of them?", which selects between the causal-sufficiency family and the latent-tolerant family; (3) design
 * facets (time series, background knowledge). Each option shows how many algorithms would be listed if it were
 * chosen.</li>
 * <li>Right column: the algorithms that fit, one row each, with the algorithm's name, its command (the same string
 * used in py-tetrad), what it needs (test, score, both), a one-line statement of what it assumes and returns, and the
 * first sentence of its manual description. Selecting a row expands it to show the full description and, where the
 * algorithm needs them, the independence tests and scores that fit the data, each with its own one-line
 * description.</li>
 * </ul>
 * The classic card's "dataset properties" filter (linear Gaussian / mixed / general / all) still exists but has moved
 * to the point where it takes effect: it sits above the test and score lists inside the expanded row, and defaults
 * per data type exactly as before.
 * <p>
 * Selections are persisted under the same keys the classic card used, so a session saved with either card restores in
 * the other.
 */
public class GuidedAlgorithmCard extends JPanel implements AlgorithmChooser, Scrollable {

    @Serial
    private static final long serialVersionUID = 4127598126540211735L;

    // Per-session keys (runner.getUserAlgoSelections()), shared with the classic card.
    private static final String ALGO_PARAM = "algo";
    private static final String IND_TEST_PARAM = "ind_test";
    private static final String SCORE_PARAM = "score";
    private static final String ALGO_TYPE_PARAM = "algo_type";
    private static final String DATASET_FILTER = "dataset_filter";
    private static final String KNOWLEDGE_PARAM = "knowledge";
    // Per-session keys new with the guided card.
    private static final String LATENT_PARAM = "guided.latent";
    private static final String TIME_SERIES_PARAM = "guided.time_series";
    private static final String EXPERIMENTAL_PARAM = "guided.experimental";

    // Cross-session keys (Parameters), shared with the classic card.
    private static final String UI_ALGO = "ui.search.algo";
    private static final String UI_IND_TEST = "ui.search.ind_test";
    private static final String UI_SCORE = "ui.search.score";
    private static final String UI_DATA_FILTER = "ui.search.dataset_filter";
    private static final String UI_KNOWLEDGE = "ui.search.knowledge";

    private static final String FAMILY_LINEAR_GAUSSIAN = "linear-gaussian";
    private static final String FAMILY_MIXED = "mixed";
    private static final String FAMILY_GENERAL = "general";
    private static final String FAMILY_ALL = "all";

    private final GeneralAlgorithmRunner algorithmRunner;
    private final BlockSpec blockSpec;
    private final DataType dataType;
    private final Parameters parameters;
    private final List<AlgorithmModel> allModels;

    private final Map<AlgorithmModel, Map<DataType, IndependenceTestModel>> defaultIndTestModels = new HashMap<>();
    private final Map<AlgorithmModel, Map<DataType, ScoreModel>> defaultScoreModels = new HashMap<>();

    // Answers.
    private LatentChoice latent = LatentChoice.ANY;
    private boolean timeSeries = false;
    private boolean knowledge = false;
    private boolean experimental = false;
    private String query = "";
    private String family = FAMILY_ALL;

    // Selection.
    private AlgorithmModel selectedAlgo;
    private IndependenceTestModel selectedTest;
    private ScoreModel selectedScore;

    // Widgets.
    private final JRadioButton latentAnyBtn = new JRadioButton();
    private final JRadioButton latentNoBtn = new JRadioButton();
    private final JRadioButton latentYesBtn = new JRadioButton();
    private final JLabel latentAnyCount = countLabel();
    private final JLabel latentNoCount = countLabel();
    private final JLabel latentYesCount = countLabel();
    private final JCheckBox timeSeriesChk = new JCheckBox("Time series / lagged data");
    private final JCheckBox knowledgeChk = new JCheckBox("I have background knowledge");
    private final JLabel timeSeriesCount = countLabel();
    private final JLabel knowledgeCount = countLabel();
    private final JCheckBox experimentalChk = new JCheckBox("Include experimental / hidden algorithms");
    private final JTextField queryField = new JTextField();
    private final JLabel matchLabel = new JLabel();
    private final JLabel summaryLabel = new JLabel();
    private final JPanel rowsPanel = new ScrollableColumn();
    private final JScrollPane rowsScroll = new JScrollPane(rowsPanel);
    private final ButtonGroup rowGroup = new ButtonGroup();
    private final Map<AlgorithmModel, JRadioButton> rowButtons = new HashMap<>();

    private boolean restoring = false;

    /**
     * Constructs the card.
     *
     * @param algorithmRunner the runner holding data, knowledge, and persisted selections.
     * @param blockSpec       non-null only for block (latent-structure) searches.
     */
    public GuidedAlgorithmCard(GeneralAlgorithmRunner algorithmRunner, BlockSpec blockSpec) {
        this.algorithmRunner = algorithmRunner;
        this.blockSpec = blockSpec;
        this.dataType = getDataType(algorithmRunner);
        this.parameters = algorithmRunner.getParameters();
        this.allModels = AlgorithmChooserLogic.allModels();
        this.experimental = Tetrad.enableExperimental;

        initComponents();
        initListeners();
        refresh();
    }

    //=========================== Public contract ===========================//

    @Override
    public AlgorithmModel getSelectedAlgorithm() {
        return this.selectedAlgo;
    }

    @Override
    public JComponent asComponent() {
        return this;
    }

    @Override
    public void refresh() {
        this.defaultIndTestModels.clear();
        this.defaultScoreModels.clear();
        restoreUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    @Override
    public void saveStates() {
        rememberUserAlgoSelections(this.algorithmRunner.getUserAlgoSelections());
    }

    @Override
    public boolean isAllValid() {
        AlgorithmModel algoModel = this.selectedAlgo;
        if (algoModel == null) {
            JOptionPane.showMessageDialog(this, "No algorithm fits the current answers. Loosen a filter on the left.",
                    "Please Note", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        boolean missingTest = algoModel.isRequiredTest() && this.selectedTest == null;
        boolean missingScore = algoModel.isRequiredScore() && this.selectedScore == null;
        String name = algoModel.getAlgorithm().annotation().name();

        if (missingTest && missingScore) {
            JOptionPane.showMessageDialog(this, name + " requires both a test and a score.",
                    "Please Note", JOptionPane.INFORMATION_MESSAGE);
            return false;
        } else if (missingTest) {
            JOptionPane.showMessageDialog(this, name + " requires an independence test.",
                    "Please Note", JOptionPane.INFORMATION_MESSAGE);
            return false;
        } else if (missingScore) {
            JOptionPane.showMessageDialog(this, name + " requires a score.",
                    "Please Note", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        this.algorithmRunner.setAlgorithm(getAlgorithmFromInterface(algoModel, this.selectedTest, this.selectedScore));
        return true;
    }

    /**
     * Builds the algorithm object from the chosen algorithm, test, and score, wiring in the source graph for
     * algorithms that take one. Same behavior as the classic card.
     *
     * @param algoModel    the algorithm.
     * @param indTestModel the test, or null.
     * @param scoreModel   the score, or null.
     * @return the configured algorithm.
     */
    public Algorithm getAlgorithmFromInterface(AlgorithmModel algoModel, IndependenceTestModel indTestModel,
                                               ScoreModel scoreModel) {
        @SuppressWarnings("unchecked")
        Class<? extends Algorithm> algoClass = (Class<? extends Algorithm>) algoModel.getAlgorithm().clazz();
        @SuppressWarnings("unchecked")
        Class<? extends edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper> indTestClass
                = (indTestModel == null) ? null
                : (Class<? extends edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper>)
                indTestModel.getIndependenceTest().clazz();
        @SuppressWarnings("unchecked")
        Class<? extends edu.cmu.tetrad.algcomparison.score.ScoreWrapper> scoreClass
                = (scoreModel == null) ? null
                : (Class<? extends edu.cmu.tetrad.algcomparison.score.ScoreWrapper>) scoreModel.getScore().clazz();

        Algorithm algorithm = null;
        try {
            algorithm = AlgorithmFactory.create(algoClass, indTestClass, scoreClass);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException exception) {
            TetradLogger.getInstance().log(exception.toString());
        }

        if (algorithm instanceof TakesExternalGraph && this.algorithmRunner.getSourceGraph() != null) {
            Algorithm externalGraph = new SingleGraphAlg(this.algorithmRunner.getSourceGraph());
            ((TakesExternalGraph) algorithm).setExternalGraph(externalGraph);
        }

        return algorithm;
    }

    /**
     * @return the currently selected independence test, or null.
     */
    public IndependenceTestModel getSelectedTest() {
        return this.selectedTest;
    }

    /**
     * @return the currently selected score, or null.
     */
    public ScoreModel getSelectedScore() {
        return this.selectedScore;
    }

    /**
     * @return the algorithms currently listed, in display order.
     */
    public List<AlgorithmModel> getListedAlgorithms() {
        return AlgorithmChooserLogic.filter(this.allModels, this.dataType, this.blockSpec != null, answers());
    }

    /**
     * Sets the latent-confounder answer programmatically (used by tests; the UI goes through the radio buttons).
     *
     * @param choice the choice.
     */
    public void setLatentChoice(LatentChoice choice) {
        this.latent = choice;
        syncWidgetsFromState();
        rebuildRows();
        saveStates();
    }

    //=========================== Scrollable ===========================//
    // The editor wraps each card in a JScrollPane; tracking the viewport in both directions makes the card fill it
    // so the inner list scrolls instead of the whole card.

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    //=========================== UI construction ===========================//

    private void initComponents() {
        setLayout(new BorderLayout(14, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

        JPanel left = buildLeftColumn();
        JScrollPane leftScroll = new JScrollPane(left, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setBorder(BorderFactory.createEmptyBorder());
        leftScroll.getVerticalScrollBar().setUnitIncrement(12);
        leftScroll.setPreferredSize(new Dimension(300, 10));
        leftScroll.setMinimumSize(new Dimension(260, 10));
        add(leftScroll, BorderLayout.WEST);

        add(buildRightColumn(), BorderLayout.CENTER);
    }

    private JPanel buildLeftColumn() {
        JPanel left = new ScrollableColumn();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        left.add(sectionHeading("TELL ME ABOUT YOUR DATA"));
        left.add(Box.createVerticalStrut(10));

        // 1. The data, stated rather than asked.
        left.add(questionTitle("1 \u00b7 Your variables"));
        left.add(Box.createVerticalStrut(4));
        JTextArea dataStatement = wrapped(describeData(), baseFont(), fg());
        dataStatement.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        left.add(boxed(dataStatement));
        left.add(Box.createVerticalStrut(16));

        // 2. Latents. Hidden for block searches, where the family is fixed by the runner.
        if (this.blockSpec == null) {
            left.add(questionTitle("2 \u00b7 Could something unmeasured cause two of them?"));
            left.add(Box.createVerticalStrut(4));
            ButtonGroup latentGroup = new ButtonGroup();
            latentGroup.add(this.latentAnyBtn);
            latentGroup.add(this.latentNoBtn);
            latentGroup.add(this.latentYesBtn);
            this.latentAnyBtn.setText(twoLine("Not sure yet", "show both families"));
            this.latentNoBtn.setText(twoLine("No \u2014 all causes are measured",
                    "causal sufficiency; CPDAG output"));
            this.latentNoBtn.setToolTipText("<html><div style='width:300px'>Assume every common cause of two "
                    + "measured variables is itself measured (causal sufficiency). Algorithms in this family "
                    + "return a CPDAG or a DAG over the measured variables.</div></html>");
            this.latentYesBtn.setToolTipText("<html><div style='width:300px'>Allow that two measured variables "
                    + "may share an unmeasured common cause. Algorithms in this family return a PAG, whose "
                    + "circle and bidirected marks record what the data leave undetermined.</div></html>");
            this.latentYesBtn.setText(twoLine("Yes, possibly", "latent-tolerant search; PAG output"));
            JPanel latentBox = new JPanel();
            latentBox.setLayout(new BoxLayout(latentBox, BoxLayout.Y_AXIS));
            latentBox.add(optionRow(this.latentAnyBtn, this.latentAnyCount));
            latentBox.add(optionRow(this.latentNoBtn, this.latentNoCount));
            latentBox.add(optionRow(this.latentYesBtn, this.latentYesCount));
            left.add(boxed(latentBox));
            left.add(Box.createVerticalStrut(16));
        }

        // 3. Design facets.
        left.add(questionTitle((this.blockSpec == null ? "3" : "2") + " \u00b7 Anything special about the design?"));
        left.add(Box.createVerticalStrut(4));
        JPanel extraBox = new JPanel();
        extraBox.setLayout(new BoxLayout(extraBox, BoxLayout.Y_AXIS));
        extraBox.add(optionRow(this.timeSeriesChk, this.timeSeriesCount));
        extraBox.add(optionRow(this.knowledgeChk, this.knowledgeCount));
        left.add(boxed(extraBox));
        left.add(Box.createVerticalStrut(16));

        // Experimental toggle and reset.
        this.experimentalChk.setFont(smallFont());
        this.experimentalChk.setAlignmentX(LEFT_ALIGNMENT);
        this.experimentalChk.setToolTipText("<html><div style='width:300px'>Also list algorithms, tests, and "
                + "scores marked experimental in the registry. This affects only this search box and is saved with "
                + "the session. To show them everywhere, use File > Settings > Show experimental algorithms "
                + "everywhere.</div></html>");
        left.add(this.experimentalChk);
        left.add(Box.createVerticalStrut(6));
        JButton resetBtn = new JButton("Start over");
        resetBtn.setFont(smallFont());
        resetBtn.setAlignmentX(LEFT_ALIGNMENT);
        resetBtn.setToolTipText("Clear the answers above and the name filter.");
        resetBtn.addActionListener(e -> {
            this.latent = LatentChoice.ANY;
            this.timeSeries = false;
            this.knowledge = false;
            this.query = "";
            this.experimental = Tetrad.enableExperimental;
            syncWidgetsFromState();
            rebuildRows();
            saveStates();
        });
        left.add(resetBtn);
        left.add(Box.createVerticalGlue());

        for (Component c : left.getComponents()) {
            if (c instanceof JComponent jc) jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        return left;
    }

    private JPanel buildRightColumn() {
        JPanel right = new JPanel(new BorderLayout(0, 6));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        this.matchLabel.setFont(baseFont().deriveFont(Font.BOLD));
        this.matchLabel.setAlignmentX(LEFT_ALIGNMENT);
        this.summaryLabel.setFont(smallFont());
        this.summaryLabel.setForeground(gray());
        this.summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(this.matchLabel);
        header.add(this.summaryLabel);
        header.add(Box.createVerticalStrut(6));
        this.queryField.putClientProperty("JTextField.placeholderText", "Filter by name\u2026");
        this.queryField.setToolTipText("Type part of an algorithm name or command, e.g. boss or fci.");
        this.queryField.setAlignmentX(LEFT_ALIGNMENT);
        this.queryField.setMaximumSize(new Dimension(Integer.MAX_VALUE, this.queryField.getPreferredSize().height));
        header.add(this.queryField);
        right.add(header, BorderLayout.NORTH);

        this.rowsPanel.setLayout(new BoxLayout(this.rowsPanel, BoxLayout.Y_AXIS));
        this.rowsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.rowsScroll.getVerticalScrollBar().setUnitIncrement(14);
        this.rowsScroll.setBorder(BorderFactory.createLineBorder(borderColor()));
        right.add(this.rowsScroll, BorderLayout.CENTER);

        return right;
    }

    private void initListeners() {
        this.latentAnyBtn.addActionListener(e -> onLatent(LatentChoice.ANY));
        this.latentNoBtn.addActionListener(e -> onLatent(LatentChoice.NO));
        this.latentYesBtn.addActionListener(e -> onLatent(LatentChoice.YES));
        this.timeSeriesChk.addActionListener(e -> {
            if (this.restoring) return;
            this.timeSeries = this.timeSeriesChk.isSelected();
            rebuildRows();
            saveStates();
        });
        this.knowledgeChk.addActionListener(e -> {
            if (this.restoring) return;
            this.knowledge = this.knowledgeChk.isSelected();
            rebuildRows();
            saveStates();
        });
        this.experimentalChk.addActionListener(e -> {
            if (this.restoring) return;
            this.experimental = this.experimentalChk.isSelected();
            rebuildRows();
            saveStates();
        });
        this.queryField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (GuidedAlgorithmCard.this.restoring) return;
                GuidedAlgorithmCard.this.query = GuidedAlgorithmCard.this.queryField.getText();
                rebuildRows();
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }
        });
    }

    private void onLatent(LatentChoice choice) {
        if (this.restoring) return;
        this.latent = choice;
        rebuildRows();
        saveStates();
    }

    //=========================== State -> widgets ===========================//

    private Answers answers() {
        return new Answers(this.latent, this.timeSeries, this.knowledge, this.experimental, this.query);
    }

    private int countWith(Answers a) {
        return AlgorithmChooserLogic.filter(this.allModels, this.dataType, this.blockSpec != null,
                a.withoutQuery()).size();
    }

    private void syncWidgetsFromState() {
        this.restoring = true;
        try {
            this.latentAnyBtn.setSelected(this.latent == LatentChoice.ANY);
            this.latentNoBtn.setSelected(this.latent == LatentChoice.NO);
            this.latentYesBtn.setSelected(this.latent == LatentChoice.YES);
            this.timeSeriesChk.setSelected(this.timeSeries);
            this.knowledgeChk.setSelected(this.knowledge);
            this.experimentalChk.setSelected(this.experimental);
            if (!Objects.equals(this.queryField.getText(), this.query)) {
                this.queryField.setText(this.query);
            }
        } finally {
            this.restoring = false;
        }
    }

    private void updateCounts() {
        Answers a = answers();
        this.latentAnyCount.setText(String.valueOf(countWith(a.withLatent(LatentChoice.ANY))));
        this.latentNoCount.setText(String.valueOf(countWith(a.withLatent(LatentChoice.NO))));
        this.latentYesCount.setText(String.valueOf(countWith(a.withLatent(LatentChoice.YES))));
        this.timeSeriesCount.setText(String.valueOf(countWith(a.withTimeSeries(true))));
        this.knowledgeCount.setText(String.valueOf(countWith(a.withKnowledge(true))));
    }

    /**
     * Rebuilds the algorithm rows from the current answers, keeping the current algorithm selected if it is still
     * listed and otherwise selecting the first listed algorithm.
     */
    private void rebuildRows() {
        updateCounts();

        List<AlgorithmModel> list = getListedAlgorithms();
        int shown = countWith(new Answers(LatentChoice.ANY, false, false, this.experimental, null));
        this.matchLabel.setText(list.size() + " of " + shown + " algorithms fit");
        this.summaryLabel.setText(filterSummary());

        AlgorithmModel keep = this.selectedAlgo != null && list.contains(this.selectedAlgo)
                ? this.selectedAlgo : (list.isEmpty() ? null : list.getFirst());
        boolean selectionChanged = keep != this.selectedAlgo;
        this.selectedAlgo = keep;
        // Derive the test and score before the rows are built, since the selected row's detail shows them.
        if (selectionChanged || this.selectedAlgo == null) {
            refreshTestAndScoreSelection();
        }

        this.rowsPanel.removeAll();
        this.rowButtons.clear();
        // A fresh ButtonGroup each rebuild; the old buttons are discarded with the old rows.
        ButtonGroup group = new ButtonGroup();

        if (list.isEmpty()) {
            JTextArea none = wrapped("No algorithm fits these answers. Loosen a filter on the left"
                    + (this.query.isBlank() ? "." : ", or clear the name filter."), baseFont(), gray());
            none.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
            this.rowsPanel.add(none);
        } else {
            for (AlgorithmModel m : list) {
                boolean selected = m == this.selectedAlgo;
                JRadioButton btn = new JRadioButton();
                btn.setSelected(selected);
                group.add(btn);
                this.rowButtons.put(m, btn);
                this.rowsPanel.add(buildRow(m, btn, selected));
            }
        }
        this.rowsPanel.add(Box.createVerticalGlue());

        firePropertyChange("algoFwdBtn", null, this.selectedAlgo != null);

        this.rowsPanel.revalidate();
        this.rowsPanel.repaint();
        // Wrapped text areas report a preferred height for their current width, which is only known after the first
        // layout pass, so validate once more once that pass has happened.
        SwingUtilities.invokeLater(() -> {
            this.rowsPanel.revalidate();
            this.rowsPanel.repaint();
            scrollSelectedRowIntoView();
        });
    }

    private String filterSummary() {
        List<String> parts = new ArrayList<>();
        parts.add(dataTypeLabel(this.dataType).toLowerCase(Locale.ROOT) + " data");
        if (this.latent == LatentChoice.NO) parts.add("no latents");
        if (this.latent == LatentChoice.YES) parts.add("latents allowed");
        if (this.timeSeries) parts.add("time series");
        if (this.knowledge) parts.add("knowledge");
        if (this.experimental) parts.add("experimental shown");
        if (!this.query.isBlank()) parts.add("name contains \"" + this.query.trim() + "\"");
        return String.join(" \u00b7 ", parts);
    }

    private void scrollSelectedRowIntoView() {
        JRadioButton btn = this.selectedAlgo == null ? null : this.rowButtons.get(this.selectedAlgo);
        if (btn == null) return;
        Component row = btn.getParent();
        while (row != null && row.getParent() != this.rowsPanel) row = row.getParent();
        if (row instanceof JComponent jc) {
            Rectangle r = jc.getBounds();
            this.rowsPanel.scrollRectToVisible(r);
        }
    }

    //=========================== Rows ===========================//

    private JPanel buildRow(AlgorithmModel m, JRadioButton btn, boolean selected) {
        edu.cmu.tetrad.annotation.Algorithm a = m.getAlgorithm().annotation();
        Class<?> c = m.getAlgorithm().clazz();

        JPanel row = new PinnedPanel(new BorderLayout());
        row.setOpaque(true);
        Color stripe = selected ? accent() : rowBackground(false);
        row.setBackground(rowBackground(selected));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, stripe),
                        BorderFactory.createEmptyBorder(8, 8, 8, 10))));
        row.setAlignmentX(LEFT_ALIGNMENT);

        // Header: radio, then name line, role line, one-liner.
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        btn.setOpaque(false);
        btn.setToolTipText("Select " + a.name());
        JPanel radioHolder = new JPanel(new BorderLayout());
        radioHolder.setOpaque(false);
        radioHolder.add(btn, BorderLayout.NORTH);
        header.add(radioHolder, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JPanel nameLine = new PinnedPanel(new WrapLayout(FlowLayout.LEFT, 8, 0));
        nameLine.setOpaque(false);
        JLabel name = new JLabel(a.name());
        name.setFont(baseFont().deriveFont(Font.BOLD));
        nameLine.add(name);
        JLabel cmd = new JLabel(a.command());
        cmd.setFont(monoFont());
        cmd.setForeground(gray());
        cmd.setToolTipText("The command name for this algorithm, as used in scripts and py-tetrad.");
        nameLine.add(cmd);
        JLabel needs = new JLabel(AlgorithmChooserLogic.needs(m));
        needs.setFont(smallFont());
        needs.setForeground(gray());
        nameLine.add(needs);
        if (c.isAnnotationPresent(Experimental.class)) {
            JLabel flag = new JLabel("experimental");
            flag.setFont(smallFont().deriveFont(Font.ITALIC));
            flag.setForeground(warnColor());
            nameLine.add(flag);
        }
        nameLine.setAlignmentX(LEFT_ALIGNMENT);
        text.add(nameLine);

        JLabel role = new JLabel(AlgorithmChooserLogic.role(a.algoType()));
        role.setFont(smallFont());
        role.setForeground(gray());
        role.setAlignmentX(LEFT_ALIGNMENT);
        text.add(role);
        text.add(Box.createVerticalStrut(3));

        JTextArea oneLine = wrapped(AlgorithmChooserLogic.firstSentence(m.getDescription()), baseFont(), fg());
        oneLine.setAlignmentX(LEFT_ALIGNMENT);
        text.add(oneLine);

        header.add(text, BorderLayout.CENTER);
        row.add(header, BorderLayout.NORTH);

        if (selected) {
            row.add(buildDetail(m), BorderLayout.CENTER);
        }

        // Clicking anywhere on the header selects the row.
        Runnable select = () -> {
            if (!btn.isSelected()) btn.setSelected(true);
            onRowSelected(m);
        };
        btn.addActionListener(e -> onRowSelected(m));
        installClick(header, select, btn);

        return row;
    }

    private void onRowSelected(AlgorithmModel m) {
        if (m == this.selectedAlgo) return;
        this.selectedAlgo = m;
        refreshTestAndScoreSelection();
        validateAlgorithmOption();
        rebuildRows();
        saveStates();
    }

    /**
     * The expanded part of the selected row: full description, the assumption-family filter, and the test and score
     * lists.
     */
    private JPanel buildDetail(AlgorithmModel m) {
        edu.cmu.tetrad.annotation.Algorithm a = m.getAlgorithm().annotation();
        JPanel detail = new PinnedPanel(null);
        detail.setOpaque(false);
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBorder(BorderFactory.createEmptyBorder(10, 30, 4, 4));

        detail.add(subHeading("What it does"));
        String desc = m.getDescription();
        if (AlgorithmChooserLogic.isPlaceholderDescription(desc)) {
            JTextArea warn = wrapped("\u26A0 No manual entry for `" + a.command()
                    + "`. A user cannot tell from this screen what the algorithm assumes or returns.", baseFont(), warnColor());
            warn.setAlignmentX(LEFT_ALIGNMENT);
            detail.add(warn);
        } else {
            JTextArea full = wrapped(desc.trim().replaceAll("\\s+", " "), baseFont(), fg());
            full.setAlignmentX(LEFT_ALIGNMENT);
            detail.add(full);
        }

        boolean wantsTest = m.isRequiredTest();
        boolean wantsScore = m.isRequiredScore();
        if (wantsTest || wantsScore) {
            detail.add(Box.createVerticalStrut(10));
            detail.add(buildFamilyRow(wantsTest, wantsScore));
        }

        if (wantsTest) {
            detail.add(Box.createVerticalStrut(8));
            List<IndependenceTestModel> tests = listTests(m);
            detail.add(subHeading("It needs an independence test \u2014 " + tests.size()
                    + (tests.size() == 1 ? " fits" : " fit") + " your data"));
            if (tests.isEmpty()) {
                detail.add(noneNote("No test fits this data type and family. Choose a different family above."));
            } else {
                ButtonGroup g = new ButtonGroup();
                for (IndependenceTestModel t : tests) {
                    JRadioButton b = new JRadioButton();
                    b.setSelected(t == this.selectedTest);
                    g.add(b);
                    b.addActionListener(e -> {
                        this.selectedTest = t;
                        rememberDefaultTest(m, t);
                        saveStates();
                    });
                    detail.add(choiceRow(b, t.getName(), t.getDescription()));
                }
            }
        }

        if (wantsScore) {
            detail.add(Box.createVerticalStrut(8));
            List<ScoreModel> scores = listScores(m);
            detail.add(subHeading("It needs a score \u2014 " + scores.size()
                    + (scores.size() == 1 ? " fits" : " fit") + " your data"));
            if (scores.isEmpty()) {
                detail.add(noneNote("No score fits this data type and family. Choose a different family above."));
            } else {
                ButtonGroup g = new ButtonGroup();
                for (ScoreModel s : scores) {
                    JRadioButton b = new JRadioButton();
                    b.setSelected(s == this.selectedScore);
                    g.add(b);
                    b.addActionListener(e -> {
                        this.selectedScore = s;
                        rememberDefaultScore(m, s);
                        saveStates();
                    });
                    detail.add(choiceRow(b, s.getName(), s.getDescription()));
                }
            }
        }

        detail.add(Box.createVerticalStrut(8));
        JLabel ready = new JLabel(readiness(m));
        ready.setFont(smallFont());
        ready.setForeground(gray());
        ready.setAlignmentX(LEFT_ALIGNMENT);
        detail.add(ready);

        for (Component c : detail.getComponents()) {
            if (c instanceof JComponent jc) jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        return detail;
    }

    private JPanel buildFamilyRow(boolean wantsTest, boolean wantsScore) {
        String what = wantsTest && wantsScore ? "tests and scores" : wantsTest ? "tests" : "scores";
        JPanel row = new PinnedPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        row.setOpaque(false);
        JLabel lbl = new JLabel("Show " + what + " for:");
        lbl.setFont(smallFont());
        lbl.setForeground(gray());
        row.add(lbl);
        ButtonGroup g = new ButtonGroup();
        row.add(familyButton(g, "Linear Gaussian", FAMILY_LINEAR_GAUSSIAN,
                "Tests and scores that assume linear relationships with Gaussian noise."));
        row.add(familyButton(g, "Mixed", FAMILY_MIXED,
                "Tests and scores for data with both continuous and discrete variables."));
        row.add(familyButton(g, "General / nonlinear", FAMILY_GENERAL,
                "Tests and scores that make no linearity assumption; usually slower."));
        row.add(familyButton(g, "All", FAMILY_ALL, "Every test or score compatible with the data type."));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private JRadioButton familyButton(ButtonGroup g, String label, String key, String tip) {
        JRadioButton b = new JRadioButton(label);
        b.setFont(smallFont());
        b.setOpaque(false);
        b.setSelected(key.equals(this.family));
        b.setToolTipText(tip);
        g.add(b);
        b.addActionListener(e -> {
            if (!key.equals(this.family)) {
                this.family = key;
                // The family changes which tests and scores are listed, so re-derive the defaults.
                refreshTestAndScoreSelection();
                rebuildRows();
                saveStates();
            }
        });
        return b;
    }

    private JPanel choiceRow(JRadioButton b, String name, String description) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        b.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(b, BorderLayout.NORTH);
        row.add(holder, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel nm = new JLabel(name);
        nm.setFont(baseFont().deriveFont(Font.BOLD));
        nm.setAlignmentX(LEFT_ALIGNMENT);
        text.add(nm);
        boolean placeholder = AlgorithmChooserLogic.isPlaceholderDescription(description);
        JTextArea d = wrapped(placeholder ? "\u26A0 no description in the manual"
                : AlgorithmChooserLogic.firstSentence(description), smallFont(), placeholder ? warnColor() : gray());
        d.setAlignmentX(LEFT_ALIGNMENT);
        text.add(d);
        row.add(text, BorderLayout.CENTER);

        if (!placeholder) {
            String tip = "<html><div style='width:360px'>" + ParameterToolTips.escape(
                    description.trim().replaceAll("\\s+", " ")) + "</div></html>";
            row.setToolTipText(tip);
            d.setToolTipText(tip);
            nm.setToolTipText(tip);
        }

        installClick(row, () -> {
            if (!b.isSelected()) b.doClick();
        }, b);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private String readiness(AlgorithmModel m) {
        List<String> need = new ArrayList<>();
        if (m.isRequiredTest() && this.selectedTest == null) need.add("a test");
        if (m.isRequiredScore() && this.selectedScore == null) need.add("a score");
        if (!need.isEmpty()) return "Still unset: " + String.join(" and ", need) + ".";
        if (!m.isRequiredTest() && !m.isRequiredScore()) return "Nothing else to choose. Set Parameters when ready.";
        return "Test and score set. Set Parameters when ready.";
    }

    //=========================== Tests and scores ===========================//

    private DataType effectiveDataType() {
        if (FAMILY_GENERAL.equals(this.family) && this.dataType == DataType.Continuous) {
            return DataType.ContinuousGeneral;
        }
        if (FAMILY_MIXED.equals(this.family)) {
            return DataType.Mixed;
        }
        return this.dataType;
    }

    private boolean familyPasses(Class<?> c) {
        return switch (this.family) {
            case FAMILY_LINEAR_GAUSSIAN -> c.isAnnotationPresent(LinearGaussian.class);
            case FAMILY_MIXED -> c.isAnnotationPresent(Mixed.class);
            case FAMILY_GENERAL -> c.isAnnotationPresent(General.class);
            default -> true;
        };
    }

    private List<IndependenceTestModel> listTests(AlgorithmModel m) {
        if (m == null || !m.isRequiredTest()) return List.of();
        List<IndependenceTestModel> out = new ArrayList<>();
        for (IndependenceTestModel t : IndependenceTestModels.getInstance().getModels(effectiveDataType())) {
            Class<?> c = t.getIndependenceTest().clazz();
            if (DeprecationUtils.isClassDeprecated(c)) continue;
            if (!familyPasses(c)) continue;
            boolean isBlocks = BlockIndependenceWrapper.class.isAssignableFrom(c);
            if ((this.blockSpec == null) == isBlocks) continue;
            if (!this.experimental && c.isAnnotationPresent(Experimental.class)) continue;
            out.add(t);
        }
        return out;
    }

    private List<ScoreModel> listScores(AlgorithmModel m) {
        if (m == null || !m.isRequiredScore()) return List.of();
        List<ScoreModel> out = new ArrayList<>();
        for (ScoreModel s : ScoreModels.getInstance().getModels(effectiveDataType())) {
            Class<?> c = s.getScore().clazz();
            if (DeprecationUtils.isClassDeprecated(c)) continue;
            if (!familyPasses(c)) continue;
            boolean isBlocks = BlockScoreWrapper.class.isAssignableFrom(c);
            if ((this.blockSpec == null) == isBlocks) continue;
            if (!this.experimental && c.isAnnotationPresent(Experimental.class)) continue;
            out.add(s);
        }
        return out;
    }

    /**
     * Chooses the test and score for the selected algorithm: the cross-session saved command if it is listed, else
     * the per-algorithm remembered choice, else the registry default for the data type, else the first listed.
     */
    private void refreshTestAndScoreSelection() {
        AlgorithmModel m = this.selectedAlgo;
        this.selectedTest = null;
        this.selectedScore = null;
        if (m == null) return;

        List<IndependenceTestModel> tests = listTests(m);
        if (!tests.isEmpty()) {
            IndependenceTestModel t = findTestByCommand(tests, savedString(uiIndTestKey()));
            if (t == null) {
                Map<DataType, IndependenceTestModel> map = this.defaultIndTestModels.get(m);
                if (map != null) t = map.get(effectiveDataType());
            }
            if (t == null) {
                IndependenceTestModel d = IndependenceTestModels.getInstance().getDefaultModel(effectiveDataType());
                if (d != null && tests.contains(d)) t = d;
            }
            if (t == null) t = tests.getFirst();
            this.selectedTest = t;
        }

        List<ScoreModel> scores = listScores(m);
        if (!scores.isEmpty()) {
            ScoreModel s = findScoreByCommand(scores, savedString(uiScoreKey()));
            if (s == null) {
                Map<DataType, ScoreModel> map = this.defaultScoreModels.get(m);
                if (map != null) s = map.get(effectiveDataType());
            }
            if (s == null) {
                ScoreModel d = ScoreModels.getInstance().getDefaultModel(effectiveDataType());
                if (d != null && scores.contains(d)) s = d;
            }
            if (s == null) s = scores.getFirst();
            this.selectedScore = s;
        }
    }

    private void rememberDefaultTest(AlgorithmModel m, IndependenceTestModel t) {
        this.defaultIndTestModels.computeIfAbsent(m, k -> new EnumMap<>(DataType.class)).put(effectiveDataType(), t);
        if (t != null && t.getIndependenceTest() != null && t.getIndependenceTest().annotation() != null) {
            this.parameters.set(uiIndTestKey(), t.getIndependenceTest().annotation().command());
        }
    }

    private void rememberDefaultScore(AlgorithmModel m, ScoreModel s) {
        this.defaultScoreModels.computeIfAbsent(m, k -> new EnumMap<>(DataType.class)).put(effectiveDataType(), s);
        if (s != null && s.getScore() != null && s.getScore().annotation() != null) {
            this.parameters.set(uiScoreKey(), s.getScore().annotation().command());
        }
    }

    private static IndependenceTestModel findTestByCommand(List<IndependenceTestModel> tests, String cmd) {
        if (cmd == null) return null;
        for (IndependenceTestModel t : tests) {
            if (cmd.equals(t.getIndependenceTest().annotation().command())) return t;
        }
        return null;
    }

    private static ScoreModel findScoreByCommand(List<ScoreModel> scores, String cmd) {
        if (cmd == null) return null;
        for (ScoreModel s : scores) {
            if (cmd.equals(s.getScore().annotation().command())) return s;
        }
        return null;
    }

    private static IndependenceTestModel findTestByName(List<IndependenceTestModel> tests, String name) {
        if (name == null) return null;
        for (IndependenceTestModel t : tests) {
            if (name.equals(t.toString())) return t;
        }
        return null;
    }

    private static ScoreModel findScoreByName(List<ScoreModel> scores, String name) {
        if (name == null) return null;
        for (ScoreModel s : scores) {
            if (name.equals(s.toString())) return s;
        }
        return null;
    }

    private String uiIndTestKey() {
        return UI_IND_TEST + "." + this.family;
    }

    private String uiScoreKey() {
        return UI_SCORE + "." + this.family;
    }

    //=========================== Validation ===========================//

    /**
     * Same checks as the classic card: non-executable algorithms, algorithms that need an external graph, and the
     * time-series algorithms that need lagged data.
     */
    private void validateAlgorithmOption() {
        AlgorithmModel algoModel = this.selectedAlgo;
        if (algoModel == null) {
            firePropertyChange("algoFwdBtn", null, false);
            return;
        }
        firePropertyChange("algoFwdBtn", null, true);
        Class<?> algoClass = algoModel.getAlgorithm().clazz();

        if (algoClass.isAnnotationPresent(Nonexecutable.class)) {
            String msg = "";
            try {
                Object algo = algoClass.getDeclaredConstructor().newInstance();
                Method mth = algoClass.getDeclaredMethod("getDescription");
                mth.setAccessible(true);
                try {
                    msg = String.valueOf(mth.invoke(algo));
                } catch (InvocationTargetException ignored) {
                }
            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                     InvocationTargetException exception) {
                TetradLogger.getInstance().log(exception.toString());
            }
            firePropertyChange("algoFwdBtn", null, false);
            JOptionPane.showMessageDialog(this, msg, "Please Note", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (TakesExternalGraph.class.isAssignableFrom(algoClass)
            && (this.algorithmRunner.getSourceGraph() == null || this.algorithmRunner.getDataModelList().isEmpty())) {
            try {
                Object algo = algoClass.getDeclaredConstructor().newInstance();
                Method mth = algoClass.getDeclaredMethod("setExternalGraph", Algorithm.class);
                mth.setAccessible(true);
                try {
                    mth.invoke(algo, (Algorithm) null);
                } catch (InvocationTargetException | IllegalArgumentException exception) {
                    firePropertyChange("algoFwdBtn", null, false);
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(this, cause.getMessage(), "Please Note",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                     InvocationTargetException exception) {
                TetradLogger.getInstance().log(exception.toString());
            }
        }

        String cmd = algoModel.getAlgorithm().annotation().command();
        if (cmd.equalsIgnoreCase("ts-fci") || cmd.equalsIgnoreCase("ts-gfci") || cmd.equalsIgnoreCase("ts-imgs")) {
            DataModel dataModel = this.algorithmRunner.getDataModel();
            Knowledge k = this.algorithmRunner.getKnowledge();
            boolean noRunnerKnowledge = k == null || k.isEmpty();
            boolean noDataKnowledge = dataModel == null || dataModel.getKnowledge() == null
                                      || dataModel.getKnowledge().isEmpty();
            if (noRunnerKnowledge && noDataKnowledge) {
                firePropertyChange("algoFwdBtn", null, false);
                JOptionPane.showMessageDialog(this, "Time-series algorithm needs lagged data", "Please Note",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    //=========================== Persistence ===========================//

    private void restoreUserAlgoSelections(Map<String, Object> sel) {
        this.restoring = true;
        try {
            // Latent answer: the guided key if present, else derived from the classic algo_type filter.
            Object l = sel.get(LATENT_PARAM);
            if (l instanceof String s) {
                this.latent = LatentChoice.parse(s);
            } else {
                Object at = sel.get(ALGO_TYPE_PARAM);
                if (at instanceof String s) {
                    if (AlgType.forbid_latent_common_causes.name().equals(s)) this.latent = LatentChoice.NO;
                    else if (AlgType.allow_latent_common_causes.name().equals(s)) this.latent = LatentChoice.YES;
                    else this.latent = LatentChoice.ANY;
                } else {
                    this.latent = LatentChoice.ANY;
                }
            }
            this.timeSeries = sel.get(TIME_SERIES_PARAM) instanceof Boolean b && b;
            Object k = sel.get(KNOWLEDGE_PARAM);
            this.knowledge = k instanceof Boolean b ? b : this.parameters.getBoolean(UI_KNOWLEDGE, false);
            Object x = sel.get(EXPERIMENTAL_PARAM);
            this.experimental = x instanceof Boolean b ? b : Tetrad.enableExperimental;
            this.family = savedFamily(sel);
            this.query = "";
        } finally {
            this.restoring = false;
        }
        syncWidgetsFromState();

        // Algorithm: by the per-session saved name, else the cross-session saved name, else the first listed.
        List<AlgorithmModel> list = getListedAlgorithms();
        String savedName = sel.get(ALGO_PARAM) instanceof String s && !s.isBlank()
                ? s : savedString(UI_ALGO);
        AlgorithmModel found = findAlgorithmByName(list, savedName);
        this.selectedAlgo = found != null ? found : (list.isEmpty() ? null : list.getFirst());

        refreshTestAndScoreSelection();
        // Per-session test/score names win over the derived defaults when they are still listed.
        if (this.selectedAlgo != null) {
            IndependenceTestModel t = findTestByName(listTests(this.selectedAlgo),
                    sel.get(IND_TEST_PARAM) instanceof String s ? s : null);
            if (t != null) this.selectedTest = t;
            ScoreModel sc = findScoreByName(listScores(this.selectedAlgo),
                    sel.get(SCORE_PARAM) instanceof String s ? s : null);
            if (sc != null) this.selectedScore = sc;
        }

        rebuildRows();
        // Write the normalized selection back so the guided keys exist even if the user never touches a control.
        rememberUserAlgoSelections(sel);
    }

    private String savedFamily(Map<String, Object> sel) {
        Object obj = sel.get(DATASET_FILTER);
        if (obj instanceof String s && !s.isBlank()) return s;
        String saved = savedString(UI_DATA_FILTER);
        if (saved != null) return saved;
        // Default per data type, as the classic card did.
        if (this.dataType == DataType.Mixed || this.dataType == DataType.Discrete) return FAMILY_MIXED;
        return FAMILY_LINEAR_GAUSSIAN;
    }

    private void rememberUserAlgoSelections(Map<String, Object> sel) {
        sel.put(LATENT_PARAM, this.latent.name());
        sel.put(TIME_SERIES_PARAM, this.timeSeries);
        sel.put(EXPERIMENTAL_PARAM, this.experimental);
        sel.put(KNOWLEDGE_PARAM, this.knowledge);
        sel.put(DATASET_FILTER, this.family);
        // Keep the classic card's filter key coherent so a session opened there behaves sensibly.
        sel.put(ALGO_TYPE_PARAM, switch (this.latent) {
            case NO -> AlgType.forbid_latent_common_causes.name();
            case YES -> AlgType.allow_latent_common_causes.name();
            default -> "all";
        });
        if (this.selectedAlgo != null) sel.put(ALGO_PARAM, this.selectedAlgo.toString());
        if (this.selectedTest != null) sel.put(IND_TEST_PARAM, this.selectedTest.toString());
        if (this.selectedScore != null) sel.put(SCORE_PARAM, this.selectedScore.toString());

        if (this.selectedAlgo != null && this.selectedAlgo.getAlgorithm().annotation() != null) {
            this.parameters.set(UI_ALGO, this.selectedAlgo.getAlgorithm().annotation().name());
        }
        this.parameters.set(UI_DATA_FILTER, this.family);
        this.parameters.set(UI_KNOWLEDGE, this.knowledge);
        if (this.selectedTest != null) {
            this.parameters.set(uiIndTestKey(), this.selectedTest.getIndependenceTest().annotation().command());
        }
        if (this.selectedScore != null) {
            this.parameters.set(uiScoreKey(), this.selectedScore.getScore().annotation().command());
        }
    }

    /**
     * Reads a cross-session string setting. {@code Parameters.getString(name, null)} returns the string "null" (it
     * goes through {@code String.valueOf}) and records the null as the value, so it cannot be used to test for
     * absence; this reads the raw object instead.
     */
    private String savedString(String key) {
        Object o = this.parameters.get(key, null);
        if (o instanceof String s && !s.isBlank() && !"null".equals(s)) return s;
        return null;
    }

    private static AlgorithmModel findAlgorithmByName(List<AlgorithmModel> list, String name) {
        if (name == null) return null;
        for (AlgorithmModel m : list) {
            if (name.equals(m.toString()) || name.equals(m.getAlgorithm().annotation().name())) return m;
        }
        return null;
    }

    //=========================== Data description ===========================//

    private static DataType getDataType(GeneralAlgorithmRunner runner) {
        DataModelList dataModelList = runner.getDataModelList();
        if (dataModelList.containsEmptyData()) {
            return runner.getSourceGraph() == null ? null : DataType.Graph;
        }
        DataModel dataSet = dataModelList.get(0);
        if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) return DataType.Continuous;
        if (dataSet.isDiscrete()) return DataType.Discrete;
        if (dataSet.isMixed()) return DataType.Mixed;
        if (dataSet instanceof ICovarianceMatrix) return DataType.Covariance;
        return null;
    }

    private static String dataTypeLabel(DataType t) {
        if (t == null) return "No";
        return switch (t) {
            case Continuous, ContinuousGeneral -> "Continuous";
            case Discrete -> "Discrete";
            case Mixed -> "Mixed";
            case Covariance -> "Covariance matrix";
            case Graph -> "Graph-only";
            default -> t.name();
        };
    }

    private String describeData() {
        DataModelList list = this.algorithmRunner.getDataModelList();
        if (this.dataType == null) {
            return "No data connected. Only algorithms that take no data are listed.";
        }
        if (this.dataType == DataType.Graph) {
            return "No data connected; a source graph is supplied. Only algorithms that work from a graph are listed.";
        }
        DataModel first = list.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(dataTypeLabel(this.dataType));
        int vars = first.getVariables() == null ? 0 : first.getVariables().size();
        if (first instanceof DataSet ds) {
            sb.append(": ").append(vars).append(vars == 1 ? " variable, " : " variables, ")
                    .append(ds.getNumRows()).append(ds.getNumRows() == 1 ? " row" : " rows");
            if (this.dataType == DataType.Mixed) {
                int disc = 0;
                for (edu.cmu.tetrad.graph.Node n : ds.getVariables()) if (n instanceof DiscreteVariable) disc++;
                sb.append(" (").append(vars - disc).append(" continuous, ").append(disc).append(" discrete)");
            }
        } else if (first instanceof ICovarianceMatrix cov) {
            sb.append(": ").append(vars).append(" variables, n = ").append(cov.getSampleSize());
        }
        sb.append(". Read from the connected data box.");
        if (list.size() > 1) {
            sb.append("\n\n").append(list.size())
                    .append(" data sets are connected. Test- and score-based searches can pool them; ")
                    .append("that switch is on the parameter step.");
        }
        return sb.toString();
    }

    //=========================== Small UI helpers ===========================//

    private static JLabel countLabel() {
        JLabel l = new JLabel("0");
        l.setFont(smallFont());
        l.setForeground(gray());
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        l.setToolTipText("How many algorithms would be listed with this answer.");
        return l;
    }

    private JPanel optionRow(AbstractButton b, JLabel count) {
        JPanel row = new PinnedPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 8));
        b.setOpaque(false);
        row.add(b, BorderLayout.CENTER);
        row.add(count, BorderLayout.EAST);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private static JComponent boxed(JComponent inner) {
        JPanel p = new PinnedPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createLineBorder(borderColor()));
        p.add(inner, BorderLayout.CENTER);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel sectionHeading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(smallFont().deriveFont(Font.BOLD));
        l.setForeground(gray());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent questionTitle(String text) {
        JTextArea a = wrapped(text, baseFont().deriveFont(Font.BOLD), fg());
        a.setAlignmentX(LEFT_ALIGNMENT);
        return a;
    }

    private static JLabel subHeading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(smallFont().deriveFont(Font.BOLD));
        l.setForeground(gray());
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        return l;
    }

    private static JTextArea noneNote(String text) {
        JTextArea a = wrapped(text, smallFont(), gray());
        a.setAlignmentX(LEFT_ALIGNMENT);
        return a;
    }

    private static String twoLine(String main, String note) {
        return "<html>" + ParameterToolTips.escape(main) + "<br><span style='color:gray'>"
               + ParameterToolTips.escape(note) + "</span></html>";
    }

    /**
     * A read-only, word-wrapped, transparent text area used wherever a label would need to wrap.
     */
    private static JTextArea wrapped(String text, Font font, Color color) {
        JTextArea a = new WrappedArea(text);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(font);
        a.setForeground(color);
        a.setDisabledTextColor(color);
        return a;
    }

    /**
     * A FlowLayout that reports a preferred height for the wrapped content at the container's current width, so it
     * can be stacked in a vertical BoxLayout without clipping when it wraps to a second line.
     */
    private static class WrapLayout extends FlowLayout {
        @Serial
        private static final long serialVersionUID = 1L;

        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension d = layoutSize(target, false);
            d.width -= (getHgap() + 1);
            return d;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0) {
                    Container p = target.getParent();
                    while (p != null && p.getWidth() == 0) p = p.getParent();
                    targetWidth = p == null ? Integer.MAX_VALUE : p.getWidth();
                }
                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + hgap * 2;
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                for (Component m : target.getComponents()) {
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }

    /**
     * A panel whose maximum height is its preferred height, so a vertical BoxLayout never stretches it.
     */
    private static class PinnedPanel extends JPanel {
        @Serial
        private static final long serialVersionUID = 1L;

        PinnedPanel(LayoutManager lm) {
            super(lm);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * A wrapping text area whose maximum height tracks its (width-dependent) preferred height.
     */
    private static class WrappedArea extends JTextArea {
        @Serial
        private static final long serialVersionUID = 1L;

        WrappedArea(String text) {
            super(text);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * Makes a click anywhere in the component tree (except on the given button, which handles itself) run the action.
     */
    private static void installClick(Component c, Runnable action, AbstractButton except) {
        if (c == except) return;
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) action.run();
            }
        });
        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) installClick(child, action, except);
        }
    }

    private static Font baseFont() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : f;
    }

    private static Font smallFont() {
        Font f = baseFont();
        return f.deriveFont(Math.max(9f, f.getSize2D() - 1f));
    }

    private static Font monoFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.max(9, baseFont().getSize() - 1));
    }

    private static Color fg() {
        Color c = UIManager.getColor("Label.foreground");
        return c == null ? Color.BLACK : c;
    }

    private static Color gray() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c == null ? Color.GRAY : c;
    }

    private static Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c == null ? Color.LIGHT_GRAY : c;
    }

    private static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = UIManager.getColor("List.selectionBackground");
        return c == null ? new Color(47, 93, 80) : c;
    }

    private static Color warnColor() {
        Color c = UIManager.getColor("Actions.Yellow");
        return c == null ? new Color(176, 120, 0) : c;
    }

    private static Color rowBackground(boolean selected) {
        Color base = UIManager.getColor("List.background");
        if (base == null) base = Color.WHITE;
        if (!selected) return base;
        // A light wash of the accent over the list background, readable in both themes.
        Color a = accent();
        double t = 0.10;
        return new Color(
                (int) Math.round(base.getRed() * (1 - t) + a.getRed() * t),
                (int) Math.round(base.getGreen() * (1 - t) + a.getGreen() * t),
                (int) Math.round(base.getBlue() * (1 - t) + a.getBlue() * t));
    }

    /**
     * A column panel that always matches the viewport width, so wrapped text inside it wraps at the visible width and
     * only vertical scrolling is needed.
     */
    private static class ScrollableColumn extends JPanel implements Scrollable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 14;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport vp && vp.getHeight() > getPreferredSize().height;
        }
    }
}
