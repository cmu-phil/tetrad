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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetradapp.ThemeUtils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.ForbiddenGraphModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.KnowledgeSourceGraph;
import edu.cmu.tetradapp.model.RemoveNonSkeletonEdgesModel;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serial;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits knowledge of forbidden and required edges.
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public class KnowledgeBoxEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 959706288096545158L;

    /**
     * Edge limit for displaying edges in the edge panel
     */
    private static final long EDGE_LIMIT = 100;

    /**
     * Map from variable names to labels.
     */
    private final Map<String, JLabel> labelMap = new HashMap<>();

    /**
     * The variables in the knowledge.
     */
    private final List<Node> vars;

    /**
     * The variables in the first tier.
     */
    private final List<String> firstTierVars = new LinkedList<>();

    /**
     * The variables in the second tier.
     */
    private final List<String> secondTierVars = new LinkedList<>();

    /**
     * The knowledge box model.
     */
    private final KnowledgeBoxModel knowledgeBoxModel;

    /**
     * The tabbed pane.
     */
    private final JTabbedPane tabbedPane;

    /**
     * The text area of the "Text" tab, showing the knowledge in the form written by
     * {@code Save Knowledge...}. Recreated by {@link #resetTabbedPane()}.
     */
    private JTextArea knowledgeTextArea;

    /**
     * Apply/Revert buttons of the "Text" tab, enabled only while the tab has unapplied edits.
     */
    private JButton textApplyButton;

    private JButton textRevertButton;

    /**
     * True when the "Text" tab holds edits that have not been applied. Suppresses the automatic
     * re-render so that switching tabs does not discard work in progress.
     */
    private boolean textDirty;

    /**
     * The knowledge.
     */
    private Knowledge knowledge;

    /**
     * The knowledge, as text, when a model change was last reported (or when the editor opened). Null during
     * construction.
     */
    private String lastNotifiedKnowledge;

    /**
     * The edge workbench.
     */
    private KnowledgeWorkbench edgeWorkbench;

    /**
     * The number of tiers to display.
     */
    private JPanel tiersPanel;

    /**
     * The model of the Tiers tab's "# Tiers" spinner, so that {@link #setNumDisplayTiers} can keep
     * the spinner showing the number of tiers actually displayed. Before this, the spinner was
     * built once with the field's value and never updated, so after Load or Apply it could show,
     * say, 3 while four tiers were displayed -- and one down-click on that stale value silently
     * emptied the tiers beyond it.
     */
    private SpinnerNumberModel tierSpinnerModel;

    /**
     * True while {@link #setNumDisplayTiers} is updating the spinner to match the display, so the
     * spinner's listener does not treat the programmatic update as a user edit (which would fire a
     * spurious model-changed notification).
     */
    private boolean adjustingTierSpinner;

    /**
     * Preference keys for whether the Other Tiers and Other Groups tabs are shown. Most knowledge
     * boxes use neither, so both are hidden by default and turned on in the Show menu; a tab is
     * always shown, with its menu item checked and disabled, while the knowledge actually
     * contains tier structures or groups, so active constraints are never invisible.
     */
    private static final String SHOW_OTHER_TIERS_PREF = "knowledgeEditorShowOtherTiers";

    private static final String SHOW_OTHER_GROUPS_PREF = "knowledgeEditorShowOtherGroups";

    /**
     * The Show menu's items, restated by {@link #resetTabbedPane()} whenever the tabs are rebuilt.
     */
    private JCheckBoxMenuItem showOtherTiersItem;

    private JCheckBoxMenuItem showOtherGroupsItem;

    /**
     * The wildcard expression last typed into the Tiers tab's pattern field. Held here because the
     * tier panel is rebuilt from scratch after every move, which would otherwise clear the field.
     */
    private String tierGlobText = "";

    /**
     * The status message last produced by the Tiers tab's pattern field, preserved across rebuilds
     * for the same reason as {@link #tierGlobText}.
     */
    private String tierGlobStatus = " ";

    /**
     * The dropdown entry, following the tiers, that removes matching variables from every tier
     * rather than moving them into one.
     */
    private static final String NOT_IN_TIER = "Not in tier";

    /**
     * True if edges explicitly forbidden should be shown. On by default so the Edges tab does
     * not open looking empty when the knowledge in fact has forbidden edges; if there are more
     * than {@link #EDGE_LIMIT} of them, {@link #resetEdgeDisplay()} quietly turns this off
     * again and says so in the status line.
     */
    private boolean showForbiddenExplicitly = true;

    /**
     * True if edges forbidden by tiers should be shown.
     */
    private boolean showForbiddenByTiers;

    /**
     * True if edges required explicitly should be shown. On by default; see
     * {@link #showForbiddenExplicitly}.
     */
    private boolean showRequired = true;

    /**
     * True if edges required by groups should be shown.
     */
    private boolean showRequiredByGroups;

    /**
     * True if edges forbidden by groups should be shown.
     */
    private boolean showForbiddenByGroups;

    /**
     * The Edges tab's status line, which reports edge categories that were not drawn because
     * they exceed {@link #EDGE_LIMIT}, in place of the modal error dialogs used before.
     */
    private JLabel edgeStatusLabel;

    /**
     * The Edges tab's "Show" checkboxes, kept as fields so {@link #resetEdgeDisplay()} can keep
     * them in step with the flags when a category is quietly turned off for exceeding the edge
     * limit.
     */
    private JCheckBox showForbiddenByTiersBox;

    private JCheckBox showForbiddenByGroupsBox;

    private JCheckBox showForbiddenExplicitlyBox;

    private JCheckBox showRequiredByGroupsBox;

    private JCheckBox showRequiredExplicitlyBox;

    /**
     * The number of tiers to display.
     */
    private int numTiers = 3;

    /**
     * <p>Constructor for KnowledgeBoxEditor.</p>
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.ForbiddenGraphModel} object
     */
    public KnowledgeBoxEditor(ForbiddenGraphModel knowledgeBoxModel) {
        this((KnowledgeBoxModel) knowledgeBoxModel);
    }

    /**
     * <p>Constructor for KnowledgeBoxEditor.</p>
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.RemoveNonSkeletonEdgesModel} object
     */
    public KnowledgeBoxEditor(RemoveNonSkeletonEdgesModel knowledgeBoxModel) {
        this((KnowledgeBoxModel) knowledgeBoxModel);
    }

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names (that is, the list of all variable names to
     * be considered, which may vary from object to object even for the same knowledge), and possible source graph. The
     * source graph is used only to arrange nodes in the edge panel.
     *
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public KnowledgeBoxEditor(KnowledgeBoxModel knowledgeBoxModel) {
        this.vars = knowledgeBoxModel.getVariables();
        this.knowledge = knowledgeBoxModel.getKnowledge();
        this.knowledgeBoxModel = knowledgeBoxModel;

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(getPanelBackground());

        this.tabbedPane = new JTabbedPane(SwingConstants.TOP);
        this.tabbedPane.setOpaque(true);
        this.tabbedPane.setBackground(getPanelBackground());

        add(this.tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
        setPreferredSize(new Dimension(640, 500));

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                TetradLogger.getInstance().log("Edited Knowledge:");
                String message = KnowledgeBoxEditor.this.knowledge.toString();
                TetradLogger.getInstance().log(message);
            }
        });

        initComponents();
        resetTabbedPane();
        setNumDisplayTiers(this.knowledge.getNumTiers());
        this.lastNotifiedKnowledge = this.knowledge.toString();
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int b2 = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b2))
        );
    }

    private static Color darken(Color c, double amount) {
        return blend(c, Color.BLACK, amount);
    }

    private static Color brighten(Color c, double amount) {
        return blend(c, Color.WHITE, amount);
    }

    private Color getPanelBackground() {
        return uiColor("Panel.background", isDarkMode() ? new Color(43, 43, 43) : Color.WHITE);
    }

    private Color getSubpanelBackground() {
        Color panel = getPanelBackground();
        return isDarkMode() ? brighten(panel, 0.03) : panel;
    }

    private Color getUnselectedLabelBackground() {
        if (isDarkMode()) {
            Color panel = uiColor("Panel.background", new Color(60, 63, 65));
            Color button = uiColor("Button.background", panel);
            return brighten(blend(panel, button, 0.5), 0.08);
        }

        Color base = uiColor("Button.background", new Color(230, 240, 240));
        return blend(base, new Color(153, 204, 204), 0.60);
    }

//    private Color getSelectedLabelBackground() {
//        Color sel = uiColor("Table.selectionBackground", null);
//        if (sel != null) return sel;
//        return isDarkMode() ? new Color(90, 130, 180) :  new Color(255, 204, 102);
//    }

    private static Color getSelectedLabelBackground() {
        if (isDarkMode()) {
            Color sel = UIManager.getColor("Table.selectionBackground");
            if (sel != null) return sel;
            return new Color(90, 130, 180);
        }

        return DisplayNodeUtils.getNodeSelectedFillColor();
    }

    private Color getLabelForeground() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(230, 230, 230) : Color.BLACK);
    }

    private Color getMutedTextColor() {
        Color fg = getLabelForeground();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.30) : blend(fg, Color.WHITE, 0.20);
    }

    private Color getLabelBorderColor() {
        Color c = uiColor("Component.borderColor", null);
        if (c != null) return c;

        c = uiColor("Separator.foreground", null);
        if (c != null) return c;

        Color fg = getLabelForeground();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.35) : darken(fg, 0.15);
    }

    private void applyPanelTheme(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(getPanelBackground());
        c.setForeground(getLabelForeground());
    }

    private void applySubpanelTheme(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(getSubpanelBackground());
        c.setForeground(getLabelForeground());
    }

    private void applyScrollTheme(JScrollPane scrollPane) {
        if (scrollPane == null) return;
        scrollPane.setOpaque(true);
        scrollPane.setBackground(getPanelBackground());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(getPanelBackground());
        scrollPane.setBorder(new LineBorder(getLabelBorderColor()));
    }

    private void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(getLabelForeground());
    }

    private void styleLabel(JLabel label) {
        label.setForeground(getLabelForeground());
        label.setOpaque(false);
    }

    private void initComponents() {
        this.labelMap.clear();
        getKnowledge().getVariables().forEach(e -> this.labelMap.put(e, createJLabel(e)));
        getKnowledge().getVariablesNotInTiers().forEach(e -> this.labelMap.put(e, createJLabel(e)));
    }

    private JLabel createJLabel(String name) {
        JLabel label = new JLabel(String.format("  %s  ", name));
        label.setOpaque(true);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new CompoundBorder(
                new MatteBorder(2, 2, 2, 2, getPanelBackground()),
                new LineBorder(getLabelBorderColor())
        ));
        label.setForeground(getLabelForeground());
        label.setBackground(getUnselectedLabelBackground());
        return label;
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);

        JMenuItem loadKnowledge = new JMenuItem("Load Knowledge...");
        JMenuItem saveKnowledge = new JMenuItem("Save Knowledge...");

        file.add(loadKnowledge);
        file.add(saveKnowledge);

        JMenu show = new JMenu("Show");
        menuBar.add(show);

        this.showOtherTiersItem = new JCheckBoxMenuItem("Other Tiers");
        this.showOtherGroupsItem = new JCheckBoxMenuItem("Other Groups");

        this.showOtherTiersItem.addActionListener((e) -> {
            Preferences.userRoot().putBoolean(SHOW_OTHER_TIERS_PREF,
                    this.showOtherTiersItem.isSelected());
            resetTabbedPane();
            if (this.showOtherTiersItem.isSelected()) selectTab("Other Tiers");
        });

        this.showOtherGroupsItem.addActionListener((e) -> {
            Preferences.userRoot().putBoolean(SHOW_OTHER_GROUPS_PREF,
                    this.showOtherGroupsItem.isSelected());
            resetTabbedPane();
            if (this.showOtherGroupsItem.isSelected()) selectTab("Other Groups");
        });

        show.add(this.showOtherTiersItem);
        show.add(this.showOtherGroupsItem);

        loadKnowledge.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (ret1 != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                Knowledge knowledge = SimpleDataLoader.loadKnowledge(selectedFile, DelimiterType.WHITESPACE, "//");
                setKnowledge(knowledge);
                initComponents();
                resetTabbedPane();
                notifyKnowledge();
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e1.getMessage());
                e1.printStackTrace();
            }
        });

        saveKnowledge.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
            chooser.setCurrentDirectory(new File(sessionSaveLocation));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int ret1 = chooser.showSaveDialog(JOptionUtils.centeringComp());

            if (ret1 != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();

            if (selectedFile == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", selectedFile.getParent());

            try {
                DataWriter.saveKnowledge(this.knowledge, new FileWriter(selectedFile));
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e1.getMessage());
            }
        });

        return menuBar;
    }

    /**
     * <p>resetTabbedPane.</p>
     */
    public void resetTabbedPane() {
        this.tabbedPane.removeAll();

        // A knowledge box whose knowledge was derived from a graph (Forbidden Graph, Required
        // Graph, Remove Non-Skeleton Edges, Pairwise Forbidden Graph) opens on that graph,
        // which says far more about the knowledge than the tiers display, which for these
        // models is empty.
        if (this.knowledgeBoxModel instanceof KnowledgeSourceGraph provider) {
            JComponent sourceDisplay = sourceGraphDisplay(provider);

            if (sourceDisplay != null) {
                this.tabbedPane.add(provider.getKnowledgeSourceTabTitle(), sourceDisplay);
            }
        }

        this.tabbedPane.add("Tiers", tierDisplay());

        if (isOtherTiersShown()) {
            this.tabbedPane.add("Other Tiers",
                    new OtherTiersEditor(this.knowledge, this::notifyIfKnowledgeChanged));
        }

        if (isOtherGroupsShown()) {
            this.tabbedPane.add("Other Groups", new OtherGroupsEditor(this.knowledge,
                    this.knowledge.getVariables(), this::notifyIfKnowledgeChanged));
        }

        this.tabbedPane.add("Edges", edgeDisplay());
        this.tabbedPane.add("Text", textDisplay());

        updateShowMenuState();

        // removeAll() does not remove change listeners, and this method now runs on every
        // Apply from the Text tab as well as on Load, so clear them first; otherwise the
        // handlers below fire once per past reset. (Changed 2026-8-13.)
        for (ChangeListener cl : this.tabbedPane.getChangeListeners()) {
            this.tabbedPane.removeChangeListener(cl);
        }

        // The Other Tiers and Other Groups tabs are optional, so the tabs are identified by
        // title, not by index.
        this.tabbedPane.addChangeListener((e) -> {
            JTabbedPane pane = (JTabbedPane) e.getSource();
            int index = pane.getSelectedIndex();

            if (index < 0) {
                return;
            }

            String title = pane.getTitleAt(index);

            if ("Tiers".equals(title)) {
                setNumDisplayTiers(TMath.max(getNumTiers(), this.knowledge.getNumTiers()));
            } else if ("Edges".equals(title)) {
                resetEdgeDisplay();
            } else if ("Text".equals(title)) {
                refreshTextDisplay();
            }
        });
    }

    /**
     * Whether the Other Tiers tab is shown: it is while the knowledge contains tier structures,
     * and otherwise follows the Show menu.
     */
    private boolean isOtherTiersShown() {
        return this.knowledge.getNumTierStructures() > 0
               || Preferences.userRoot().getBoolean(SHOW_OTHER_TIERS_PREF, false);
    }

    /**
     * Whether the Other Groups tab is shown: it is while the knowledge contains groups, and
     * otherwise follows the Show menu.
     */
    private boolean isOtherGroupsShown() {
        return !this.knowledge.getKnowledgeGroups().isEmpty()
               || Preferences.userRoot().getBoolean(SHOW_OTHER_GROUPS_PREF, false);
    }

    /**
     * Restates the Show menu: an item is checked when its tab is shown, and disabled, with a
     * tooltip saying why, while the knowledge forces the tab to be shown.
     */
    private void updateShowMenuState() {
        if (this.showOtherTiersItem != null) {
            boolean forced = this.knowledge.getNumTierStructures() > 0;
            this.showOtherTiersItem.setSelected(isOtherTiersShown());
            this.showOtherTiersItem.setEnabled(!forced);
            this.showOtherTiersItem.setToolTipText(forced
                    ? "Shown because the knowledge contains tier structures." : null);
        }

        if (this.showOtherGroupsItem != null) {
            boolean forced = !this.knowledge.getKnowledgeGroups().isEmpty();
            this.showOtherGroupsItem.setSelected(isOtherGroupsShown());
            this.showOtherGroupsItem.setEnabled(!forced);
            this.showOtherGroupsItem.setToolTipText(forced
                    ? "Shown because the knowledge contains groups." : null);
        }
    }

    /**
     * Selects the tab with the given title, if present.
     */
    private void selectTab(String title) {
        int index = this.tabbedPane.indexOfTab(title);

        if (index >= 0) {
            this.tabbedPane.setSelectedIndex(index);
        }
    }

    /**
     * Builds the "Text" tab: a read-only monospaced view of the knowledge in the same text form
     * {@code Save Knowledge...} writes to disk, so that what is displayed is exactly what would be
     * saved. The content is regenerated whenever the tab is selected, since the other tabs edit the
     * knowledge in place.
     *
     * @return the scrolling text display
     */
    private JComponent textDisplay() {
        JTextArea area = new JTextArea();
        area.setEditable(true);
        area.setLineWrap(false);
        area.setFont(ThemeUtils.monospaced(12));
        area.setBorder(new EmptyBorder(5, 5, 5, 5));
        this.knowledgeTextArea = area;

        JButton apply = new JButton("Apply");
        JButton revert = new JButton("Revert");
        apply.setEnabled(false);
        revert.setEnabled(false);
        this.textApplyButton = apply;
        this.textRevertButton = revert;

        this.textDirty = false;
        refreshTextDisplay();

        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markTextDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markTextDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markTextDirty();
            }
        });

        apply.addActionListener((e) -> applyTextKnowledge());
        revert.addActionListener((e) -> {
            this.textDirty = false;
            refreshTextDisplay();
        });

        JScrollPane scroll = new JScrollPane(area,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(400, 400));

        Box buttons = Box.createHorizontalBox();
        applyPanelTheme(buttons);
        buttons.setBorder(new EmptyBorder(5, 5, 5, 5));
        buttons.add(new JLabel("Edit or paste a knowledge specification, then Apply."));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(revert);
        buttons.add(Box.createHorizontalStrut(5));
        buttons.add(apply);

        JPanel panel = new JPanel(new BorderLayout());
        applyPanelTheme(panel);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Explanatory header shown in the Text tab of a knowledge box that has no parent and no knowledge yet.
     * Every line is a comment, so applying the tab with the header still present is harmless.
     */
    private static final String PARENTLESS_HINT = String.join("\n",
            "// This knowledge box has no parent, so no variable names were supplied to it.",
            "// Type the knowledge here and press Apply; variable names are taken from what you type",
            "// and must match the names in the data or graph the box will be connected to.",
            "//",
            "// Syntax (whitespace separated; lines starting with // are ignored):",
            "//   addtemporal        one line per tier: <tier> <var> <var> ...",
            "//                      e.g.  1 X1 X2      2 X3      (append * to a tier, e.g. 2*, to forbid",
            "//                      edges within that tier)",
            "//   tierstructure <name>  an additional, independent tier structure; tier lines as above",
            "//   forbiddengroup     one group per pair of lines: the from-variables, then the to-variables",
            "//   requiredgroup      same, requiring the edges",
            "//   forbiddirect       one <from> <to> pair per line, forbidding from -> to",
            "//   requiredirect      one <from> <to> pair per line, requiring from -> to",
            "//",
            "// A header of '/knowledge comma' switches to comma-separated names, for variable",
            "// names containing spaces.",
            "//",
            "// The other tabs are rebuilt from the text after Apply.",
            "") + "\n";

    private void markTextDirty() {
        this.textDirty = true;
        if (this.textApplyButton != null) this.textApplyButton.setEnabled(true);
        if (this.textRevertButton != null) this.textRevertButton.setEnabled(true);
    }

    /**
     * Parses the contents of the "Text" tab and, if it parses, installs it as the editor's knowledge:
     * the model is updated, the other tabs are rebuilt from it, and listeners (hence child boxes) are
     * notified. Parsing goes through {@link SimpleDataLoader#loadKnowledge(Reader, DelimiterType,
     * String)}, the same parser {@code Load Knowledge...} uses, so text that loads from a file
     * behaves identically when pasted here.
     *
     * <p>On a parse failure the text is left exactly as the user typed it and the error is reported,
     * so nothing is lost to a typo.
     */
    private void applyTextKnowledge() {
        if (this.knowledgeTextArea == null) return;

        String text = this.knowledgeTextArea.getText();
        Knowledge parsed;

        try {
            parsed = SimpleDataLoader.loadKnowledge(
                    new StringReader(text), DelimiterType.WHITESPACE, "//");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The knowledge specification could not be parsed:\n\n"
                            + ex.getMessage()
                            + "\n\nThe text has been left unchanged.",
                    "Could Not Parse Knowledge", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setKnowledge(parsed);
        this.textDirty = false;

        initComponents();
        resetTabbedPane();
        notifyKnowledge();

        // Rebuilding replaced the tab contents; return the user to the Text tab, now
        // showing the canonical rendering of what was just parsed.
        selectTab("Text");
    }

    /**
     * Regenerates the "Text" tab content from the current knowledge, using the same writer
     * {@code Save Knowledge...} uses so the two cannot drift apart. Does nothing when the user has
     * unapplied edits in the tab, so that switching away and back does not discard them.
     */
    private void refreshTextDisplay() {
        if (this.knowledgeTextArea == null) return;
        if (this.textDirty) return;

        String text;

        try {
            StringWriter writer = new StringWriter();
            DataWriter.saveKnowledge(this.knowledge, writer);
            text = writer.toString();
        } catch (IOException e) {
            text = "Could not render the knowledge as text: " + e.getMessage();
        }

        // A box with no parent supplies no variables, so the tiers and edges tabs start blank and typing
        // here is the only way in. Say so, and show the syntax, as comments the parser ignores.
        if (this.vars.isEmpty() && this.knowledge.getVariables().isEmpty()) {
            text = PARENTLESS_HINT + text;
        }

        this.knowledgeTextArea.setText(text);
        this.knowledgeTextArea.setCaretPosition(0);

        this.textDirty = false;
        if (this.textApplyButton != null) this.textApplyButton.setEnabled(false);
        if (this.textRevertButton != null) this.textRevertButton.setEnabled(false);
    }

    private Box tierDisplay() {

        // Show at least as many tiers as the knowledge has. Without this, rebuilding the tab
        // after Load Knowledge or Apply in the Text tab used the stale display count, so a
        // knowledge with more tiers than that showed only some of them and the variables in the
        // hidden tiers were nowhere to be seen.
        int numTiers = getNumTiers();

        if (numTiers < 2) {
            int _default = (int) (TMath.pow(this.vars.size(), 0.5) + 1);
            numTiers = TMath.max(numTiers, _default);
        }

        setNumTiers(TMath.max(numTiers, getKnowledge().getNumTiers()));

        Box b = Box.createVerticalBox();
        applyPanelTheme(b);
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        Box b1 = Box.createHorizontalBox();
        applyPanelTheme(b1);

        JLabel notInTier = new JLabel("Not in tier:");
        styleLabel(notInTier);
        b1.add(notInTier);
        b1.add(Box.createHorizontalGlue());

        JLabel numTiersLabel = new JLabel("# Tiers = ");
        styleLabel(numTiersLabel);
        b1.add(numTiersLabel);

        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(getNumTiers(), 2, 100, 1);
        this.tierSpinnerModel = spinnerNumberModel;
        spinnerNumberModel.addChangeListener((e) -> {
            if (this.adjustingTierSpinner) {
                return;
            }

            SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            int numTiersSpun = model.getNumber().intValue();

            setNumDisplayTiers(numTiersSpun);
            setNumTiers(numTiersSpun);
            model.setValue(numTiersSpun);

            for (int i = getNumTiers(); i <= getKnowledge().getMaxTierForbiddenWithin(); i++) {
                getKnowledge().setTierForbiddenWithin(i, false);
            }

            notifyKnowledge();
        });

        JSpinner spinner = new JSpinner(spinnerNumberModel);
        spinner.setMaximumSize(spinner.getPreferredSize());
        b1.add(spinner);
        b.add(b1);

        this.tiersPanel = new JPanel(new BorderLayout());
        applyPanelTheme(this.tiersPanel);
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        b.add(this.tiersPanel);

        Box c = Box.createHorizontalBox();
        applyPanelTheme(c);

        JLabel help = new JLabel("Use shift key to select multiple items.");
        help.setForeground(getMutedTextColor());
        c.add(help);
        c.add(Box.createGlue());
        b.add(c);

        return b;
    }

    private void setNumDisplayTiers(int numTiers) {
        if (numTiers < 2) {
            int knowledgeTiers = getKnowledge().getNumTiers();
            int defaultTiers = (int) (TMath.pow(getVarNames().size(), 0.5) + 1);
            numTiers = TMath.max(knowledgeTiers, defaultTiers);
        }

        setNumTiers(numTiers);

        for (int i = numTiers; i < getKnowledge().getNumTiers(); i++) {
            List<String> vars = getKnowledge().getTier(i);
            for (String var : vars) {
                getKnowledge().removeFromTiers(var);
            }
        }

        this.tiersPanel.removeAll();
        this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
        this.tiersPanel.revalidate();
        this.tiersPanel.repaint();

        // Keep the spinner showing what is displayed; a stale spinner invited a "correction"
        // that deleted the tiers beyond it.
        if (this.tierSpinnerModel != null
            && this.tierSpinnerModel.getNumber().intValue() != getNumTiers()
            && getNumTiers() >= 2) {
            this.adjustingTierSpinner = true;

            try {
                this.tierSpinnerModel.setValue(getNumTiers());
            } finally {
                this.adjustingTierSpinner = false;
            }
        }
    }

    /**
     * If the knowledge box sees interventional variables it automatically places those variables in the first tier and
     * the rest of domain variables in second tier - Zhou
     */
    private void checkInterventionalVariables() {
        this.firstTierVars.clear();
        this.secondTierVars.clear();

        this.vars.forEach(e -> {
            if ((e.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS)
                    || (e.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE)) {
                this.firstTierVars.add(e.getName());
            } else if (e.getAttribute("fullyDeterminisedDomainVar") != null
                    && (boolean) e.getAttribute("fullyDeterminisedDomainVar")) {
                this.firstTierVars.add(e.getName());
            } else {
                this.secondTierVars.add(e.getName());
            }
        });
    }

    private Box getTierBoxes(int numTiers) {
        checkInterventionalVariables();

        if (getKnowledge().isEmpty() && !this.firstTierVars.isEmpty()) {
            getKnowledge().setTier(0, this.firstTierVars);
            getKnowledge().setTier(1, this.secondTierVars);
        }

        for (Node var : this.vars) {
            getKnowledge().addVariable(var.getName());
        }

        Box container = Box.createVerticalBox();
        applyPanelTheme(container);

        initComponents();

        List<String> varsNotInTiers = getKnowledge().getVariablesNotInTiers();
        JList<String> varsNotInTiersList = new DragDropList(varsNotInTiers, -1);
        varsNotInTiersList.setBorder(null);

        Box varsNotInTiersBox = Box.createHorizontalBox();
        applyPanelTheme(varsNotInTiersBox);

        JScrollPane jScrollPane1 = new JScrollPane(varsNotInTiersList);
        jScrollPane1.setPreferredSize(new Dimension(640, 50));
        applyScrollTheme(jScrollPane1);
        varsNotInTiersBox.add(jScrollPane1);

        Box tiersBox = Box.createVerticalBox();
        applyPanelTheme(tiersBox);

        List<JCheckBox> forbiddenCheckboxes = new LinkedList<>();

        for (int tier = 0; tier < numTiers; tier++) {
            Box textRow = Box.createHorizontalBox();
            applySubpanelTheme(textRow);

            JLabel tierLabel = new JLabel("Tier " + tier);
            styleLabel(tierLabel);
            textRow.add(tierLabel);

            int _tier = tier;

            textRow.add(Box.createHorizontalGlue());

            JCheckBox forbiddenCheckbox =
                    new JCheckBox("Forbid Within Tier", getKnowledge().isTierForbiddenWithin(_tier));
            styleCheckBox(forbiddenCheckbox);

            JCheckBox causesOnlyNextTierCheckbox =
                    new JCheckBox("Can Cause Only Next Tier", getKnowledge().isOnlyCanCauseNextTier(_tier));
            styleCheckBox(causesOnlyNextTierCheckbox);

            JComponent upReference = this;

            forbiddenCheckbox.addActionListener((e) -> {
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setTierForbiddenWithin(_tier, checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            forbiddenCheckboxes.add(forbiddenCheckbox);

            textRow.add(forbiddenCheckbox);

            causesOnlyNextTierCheckbox.addActionListener((e) -> {
                JCheckBox checkbox = (JCheckBox) e.getSource();
                try {
                    getKnowledge().setOnlyCanCauseNextTier(_tier, checkbox.isSelected());
                } catch (Exception e1) {
                    checkbox.setSelected(false);
                    JOptionPane.showMessageDialog(upReference, e1.getMessage());
                }

                notifyKnowledge();
            });

            if (tier + 2 < numTiers) {
                textRow.add(causesOnlyNextTierCheckbox);
            }

            tiersBox.add(textRow);

            List<String> tierNames = getKnowledge().getTier(tier);
            JList<String> tierList = new DragDropList(tierNames, tier);

            Box tierBox = Box.createHorizontalBox();
            applyPanelTheme(tierBox);

            JScrollPane jScrollPane = new JScrollPane(tierList);
            jScrollPane.setPreferredSize(new Dimension(600, 50));
            applyScrollTheme(jScrollPane);
            tierBox.add(jScrollPane);

            tiersBox.add(tierBox);
        }

        JScrollPane tiersScrollPane = new JScrollPane(tiersBox);
        tiersScrollPane.setPreferredSize(new Dimension(640, 400));
        applyScrollTheme(tiersScrollPane);

        // A Box view gives the scroll pane a unit increment of one pixel, which makes wheel
        // scrolling to the tiers below the fold all but impossible.
        tiersScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        if (!this.firstTierVars.isEmpty() && !forbiddenCheckboxes.isEmpty()) {
            forbiddenCheckboxes.get(0).setSelected(true);
            getKnowledge().setTierForbiddenWithin(0, true);
        }

        container.add(tierGlobBar(numTiers));
        container.add(Box.createVerticalStrut(5));
        container.add(varsNotInTiersBox);
        container.add(Box.createVerticalStrut(5));
        container.add(tiersScrollPane);

        return container;
    }

    /**
     * Builds the Tiers tab's wildcard bar: a text field taking an expression in which {@code *}
     * matches any string and {@code ?} any single character, a dropdown naming the destination, and
     * a Move button that sends every matching variable there.
     *
     * <p>The wildcard is resolved against the box's variable names by {@link VariableGlob}, and only
     * names that actually exist are handed to the knowledge object, so a typo cannot introduce a
     * variable that is not in the data. When nothing matches, the bar says so rather than failing
     * silently.
     *
     * @param numTiers the number of tiers currently displayed, which fixes the dropdown's contents
     * @return the bar, ready to be added to the tier panel
     */
    private Box tierGlobBar(int numTiers) {
        Box bar = Box.createHorizontalBox();
        applySubpanelTheme(bar);
        bar.setBorder(new EmptyBorder(2, 2, 2, 2));

        JLabel patternLabel = new JLabel("Pattern:");
        styleLabel(patternLabel);
        bar.add(patternLabel);
        bar.add(Box.createHorizontalStrut(5));

        JTextField patternField = new JTextField(this.tierGlobText, 12);
        patternField.setToolTipText("<html>A wildcard over variable names: <b>*</b> matches any "
                + "string, <b>?</b> any single character.<br>"
                + "Separate alternatives with commas. Matching is case sensitive.<br>"
                + "Examples: <tt>X*</tt> &nbsp; <tt>*age*</tt> &nbsp; <tt>V??</tt> &nbsp; "
                + "<tt>X*, Y*</tt></html>");
        patternField.setMaximumSize(new Dimension(220, patternField.getPreferredSize().height));
        bar.add(patternField);
        bar.add(Box.createHorizontalStrut(5));

        JLabel toLabel = new JLabel("move to");
        styleLabel(toLabel);
        bar.add(toLabel);
        bar.add(Box.createHorizontalStrut(5));

        JComboBox<String> destination = new JComboBox<>();

        for (int tier = 0; tier < numTiers; tier++) {
            destination.addItem("Tier " + tier);
        }

        destination.addItem(NOT_IN_TIER);
        destination.setMaximumSize(new Dimension(130, destination.getPreferredSize().height));
        bar.add(destination);
        bar.add(Box.createHorizontalStrut(5));

        JButton move = new JButton("Move");
        bar.add(move);
        bar.add(Box.createHorizontalStrut(10));

        JLabel status = new JLabel(this.tierGlobStatus);
        status.setForeground(getMutedTextColor());
        bar.add(status);
        bar.add(Box.createHorizontalGlue());

        ActionListener doMove = (e) -> {
            String spec = patternField.getText().trim();
            this.tierGlobText = spec;

            if (spec.isEmpty()) {
                status.setText("Type a pattern, such as X*, first.");
                return;
            }

            List<String> matched = VariableGlob.match(spec, getVarNames());

            if (matched.isEmpty()) {
                status.setText("No variable names matched " + spec + ".");
                return;
            }

            int selected = destination.getSelectedIndex();
            boolean removing = selected >= numTiers || selected < 0;

            try {
                for (String name : matched) {
                    if (removing) {
                        getKnowledge().removeFromTiers(name);
                    } else {
                        getKnowledge().addToTier(selected, name);
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
                return;
            }

            this.tierGlobStatus = removing
                    ? "Removed " + VariableGlob.countPhrase(matched.size()) + " from tiers."
                    : "Moved " + VariableGlob.countPhrase(matched.size())
                    + " to Tier " + selected + ".";

            notifyKnowledge();

            this.tiersPanel.removeAll();
            this.tiersPanel.add(getTierBoxes(getNumTiers()), BorderLayout.CENTER);
            this.tiersPanel.revalidate();
            this.tiersPanel.repaint();
        };

        move.addActionListener(doMove);
        patternField.addActionListener(doMove);

        return bar;
    }

    /**
     * Builds the tab showing the graph a derived knowledge box was made from, with a short
     * explanation of how the knowledge was read off it. The graph is display-only; the
     * knowledge itself is edited in the other tabs.
     *
     * @param provider the model supplying the graph and its explanation
     * @return the tab contents, or null if the model has neither a graph nor anything to say
     */
    private JComponent sourceGraphDisplay(KnowledgeSourceGraph provider) {
        Graph graph = provider.getKnowledgeSourceGraph();
        String text = provider.getKnowledgeSourceDescription();

        boolean hasGraph = graph != null && graph.getNumNodes() > 0;
        boolean hasText = text != null && !text.isBlank();

        if (!hasGraph && !hasText) {
            return null;
        }

        JPanel panel = new JPanel(new BorderLayout());
        applyPanelTheme(panel);

        if (hasText) {
            panel.add(readOnlyTextArea(text), BorderLayout.NORTH);
        }

        if (hasGraph && graph.getNumEdges() <= KnowledgeBoxEditor.EDGE_LIMIT) {
            // Display a copy with fresh nodes, so that moving nodes around here cannot move
            // the nodes of the graph the parent box displays.
            Graph display = copyForDisplay(graph);

            if (!hasLayout(display)) {
                LayoutUtil.circleLayout(display);
            }

            GraphWorkbench workbench = new GraphWorkbench(display);
            workbench.setAllowDoubleClickActions(false);
            workbench.setAllowEdgeReorientations(false);

            JScrollPane scroll = new JScrollPane(workbench);
            applyScrollTheme(scroll);
            panel.add(scroll, BorderLayout.CENTER);
        } else {
            String note = hasGraph
                    ? String.format("This graph has %d edges, more than the %d that can be drawn "
                                    + "here. The Text tab lists every constraint.",
                    graph.getNumEdges(), KnowledgeBoxEditor.EDGE_LIMIT)
                    : "There are too many variables to draw the graph here. The Text tab lists "
                      + "every constraint.";
            panel.add(readOnlyTextArea(note), BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * A themed, wrapping, non-editable text area for explanatory text.
     */
    private JTextArea readOnlyTextArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(getLabelForeground());
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        return area;
    }

    /**
     * Copies a graph for display, with fresh nodes, so that laying it out or dragging its nodes
     * cannot alter the node positions of the original.
     */
    private static Graph copyForDisplay(Graph graph) {
        Graph copy = new EdgeListGraph();
        Map<String, Node> byName = new HashMap<>();

        for (Node node : graph.getNodes()) {
            Node fresh = new GraphNode(node.getName());
            fresh.setNodeType(node.getNodeType());
            fresh.setCenter(node.getCenterX(), node.getCenterY());
            byName.put(node.getName(), fresh);
            copy.addNode(fresh);
        }

        for (Edge edge : graph.getEdges()) {
            Node n1 = byName.get(edge.getNode1().getName());
            Node n2 = byName.get(edge.getNode2().getName());

            if (n1 != null && n2 != null) {
                copy.addEdge(new Edge(n1, n2, edge.getEndpoint1(), edge.getEndpoint2()));
            }
        }

        return copy;
    }

    /**
     * True if any node of the graph carries a real position (the default is (-1, -1)).
     */
    private static boolean hasLayout(Graph graph) {
        for (Node node : graph.getNodes()) {
            if (node.getCenterX() > 0 || node.getCenterY() > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * A small arrow drawn in a given color, placed beside each "Show" checkbox in the Edges tab
     * so the checkboxes double as a legend for the edge colors.
     */
    private static class EdgeSwatchIcon implements Icon {

        private final Color color;

        EdgeSwatchIcon(Color color) {
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return 26;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(this.color);
            g2.setStroke(new BasicStroke(2f));
            int midY = y + getIconHeight() / 2;
            g2.drawLine(x + 2, midY, x + 20, midY);
            g2.drawLine(x + 20, midY, x + 14, midY - 4);
            g2.drawLine(x + 20, midY, x + 14, midY + 4);
            g2.dispose();
        }
    }

    private JPanel edgeDisplay() {
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        graph.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                notifyKnowledge();
            }
        });

        this.edgeWorkbench = new KnowledgeWorkbench(graph);

        // Adding or removing an edge in the Edges tab edits the knowledge. The workbench also
        // reports "modelChanged" whenever its graph is merely redrawn (a layout, a "Show ..."
        // checkbox), so only pass it on if the knowledge itself is different.
        this.edgeWorkbench.addPropertyChangeListener(evt -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                notifyIfKnowledgeChanged();
            }
        });

        this.edgeStatusLabel = new JLabel(" ");
        this.edgeStatusLabel.setForeground(getMutedTextColor());

        // The color beside each checkbox is the color the corresponding edges are drawn in, so
        // the checkbox row doubles as a legend.
        this.showForbiddenExplicitlyBox = new JCheckBox("Explicit", this.showForbiddenExplicitly);
        this.showForbiddenByTiersBox = new JCheckBox("From tiers", this.showForbiddenByTiers);
        this.showForbiddenByGroupsBox = new JCheckBox("From groups", this.showForbiddenByGroups);
        this.showRequiredExplicitlyBox = new JCheckBox("Explicit", this.showRequired);
        this.showRequiredByGroupsBox = new JCheckBox("From groups", this.showRequiredByGroups);

        styleCheckBox(this.showForbiddenExplicitlyBox);
        styleCheckBox(this.showForbiddenByTiersBox);
        styleCheckBox(this.showForbiddenByGroupsBox);
        styleCheckBox(this.showRequiredExplicitlyBox);
        styleCheckBox(this.showRequiredByGroupsBox);

        this.showForbiddenExplicitlyBox.setToolTipText(
                "Edges forbidden one at a time, here or in the Text tab.");
        this.showForbiddenByTiersBox.setToolTipText(
                "Edges forbidden because they would point from a later tier to an earlier one.");
        this.showForbiddenByGroupsBox.setToolTipText(
                "Edges forbidden by a group in the Other Groups tab.");
        this.showRequiredExplicitlyBox.setToolTipText(
                "Edges required one at a time, here or in the Text tab.");
        this.showRequiredByGroupsBox.setToolTipText(
                "Edges required by a group in the Other Groups tab.");

        resetEdgeDisplay();

        this.showForbiddenExplicitlyBox.addActionListener((e) ->
                setShowAndReset(() -> this.showForbiddenExplicitly =
                        this.showForbiddenExplicitlyBox.isSelected()));
        this.showForbiddenByTiersBox.addActionListener((e) ->
                setShowAndReset(() -> this.showForbiddenByTiers =
                        this.showForbiddenByTiersBox.isSelected()));
        this.showForbiddenByGroupsBox.addActionListener((e) ->
                setShowAndReset(() -> this.showForbiddenByGroups =
                        this.showForbiddenByGroupsBox.isSelected()));
        this.showRequiredExplicitlyBox.addActionListener((e) ->
                setShowAndReset(() -> this.showRequired =
                        this.showRequiredExplicitlyBox.isSelected()));
        this.showRequiredByGroupsBox.addActionListener((e) ->
                setShowAndReset(() -> this.showRequiredByGroups =
                        this.showRequiredByGroupsBox.isSelected()));

        JPanel workbenchPanel = new JPanel(new BorderLayout());
        applyPanelTheme(workbenchPanel);

        JScrollPane edgeScroll = new JScrollPane(this.edgeWorkbench);
        applyScrollTheme(edgeScroll);
        workbenchPanel.add(edgeScroll, BorderLayout.CENTER);
        workbenchPanel.setBorder(new TitledBorder(
                new LineBorder(getLabelBorderColor()),
                "Forbidden and Required Edges",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                getLabelForeground()
        ));

        JPanel display = new JPanel(new BorderLayout());
        applyPanelTheme(display);
        display.setPreferredSize(new Dimension(640, 450));

        JPanel toolbar = new KnowledgeEditorToolbar(this.edgeWorkbench, this.edgeWorkbench.getSourceGraph());
        toolbar.setOpaque(true);
        toolbar.setBackground(getPanelBackground());

        display.add(toolbar, BorderLayout.WEST);
        display.add(workbenchPanel, BorderLayout.CENTER);

        Box showOptionsBox = Box.createVerticalBox();
        applyPanelTheme(showOptionsBox);
        showOptionsBox.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel forbiddenLabel = new JLabel("Show forbidden:");
        JLabel requiredLabel = new JLabel("Show required:");
        styleLabel(forbiddenLabel);
        styleLabel(requiredLabel);

        // Give the two row labels the same width so the checkboxes line up.
        Dimension labelSize = new Dimension(
                TMath.max(forbiddenLabel.getPreferredSize().width,
                        requiredLabel.getPreferredSize().width) + 5,
                forbiddenLabel.getPreferredSize().height);
        forbiddenLabel.setPreferredSize(labelSize);
        requiredLabel.setPreferredSize(labelSize);
        forbiddenLabel.setMaximumSize(labelSize);
        requiredLabel.setMaximumSize(labelSize);

        Box forbiddenOptionsBox = Box.createHorizontalBox();
        forbiddenOptionsBox.setOpaque(false);
        forbiddenOptionsBox.add(forbiddenLabel);
        forbiddenOptionsBox.add(new JLabel(new EdgeSwatchIcon(
                KnowledgeDisplayEdge.DEFAULT_FORBIDDEN_EXPLICITLY_COLOR)));
        forbiddenOptionsBox.add(this.showForbiddenExplicitlyBox);
        forbiddenOptionsBox.add(Box.createHorizontalStrut(8));
        forbiddenOptionsBox.add(new JLabel(new EdgeSwatchIcon(
                KnowledgeDisplayEdge.DEFAULT_FORBIDDEN_BY_TIERS_COLOR)));
        forbiddenOptionsBox.add(this.showForbiddenByTiersBox);
        forbiddenOptionsBox.add(Box.createHorizontalStrut(8));
        forbiddenOptionsBox.add(new JLabel(new EdgeSwatchIcon(
                KnowledgeDisplayEdge.DEFAULT_FORBIDDEN_BY_GROUPS_COLOR)));
        forbiddenOptionsBox.add(this.showForbiddenByGroupsBox);
        forbiddenOptionsBox.add(Box.createHorizontalGlue());

        Box requiredOptionsBox = Box.createHorizontalBox();
        requiredOptionsBox.setOpaque(false);
        requiredOptionsBox.add(requiredLabel);
        requiredOptionsBox.add(new JLabel(new EdgeSwatchIcon(
                KnowledgeDisplayEdge.DEFAULT_REQUIRED_COLOR)));
        requiredOptionsBox.add(this.showRequiredExplicitlyBox);
        requiredOptionsBox.add(Box.createHorizontalStrut(8));
        requiredOptionsBox.add(new JLabel(new EdgeSwatchIcon(
                KnowledgeDisplayEdge.DEFAULT_REQUIRED_BY_GROUPS_COLOR)));
        requiredOptionsBox.add(this.showRequiredByGroupsBox);
        requiredOptionsBox.add(Box.createHorizontalGlue());

        JLabel hint = new JLabel("Edges point from cause to effect. Draw them with the Forbidden "
                                 + "and Required tools at the left; select an edge and press Delete to remove it.");
        hint.setForeground(getMutedTextColor());

        Box hintRow = Box.createHorizontalBox();
        hintRow.setOpaque(false);
        hintRow.add(hint);
        hintRow.add(Box.createHorizontalGlue());

        Box statusRow = Box.createHorizontalBox();
        statusRow.setOpaque(false);
        statusRow.add(this.edgeStatusLabel);
        statusRow.add(Box.createHorizontalGlue());

        showOptionsBox.add(forbiddenOptionsBox);
        showOptionsBox.add(requiredOptionsBox);
        showOptionsBox.add(Box.createVerticalStrut(2));
        showOptionsBox.add(hintRow);
        showOptionsBox.add(statusRow);

        display.add(showOptionsBox, BorderLayout.SOUTH);

        return display;
    }

    /**
     * Runs the given flag update, then redraws the edge display.
     */
    private void setShowAndReset(Runnable flagUpdate) {
        flagUpdate.run();
        resetEdgeDisplay();
    }

    /**
     * Redraws the Edges tab from the current knowledge and "Show" flags. A category with more
     * edges than {@link #EDGE_LIMIT} is not drawn: its flag and checkbox are turned off and the
     * status line says so, in place of the modal error dialog shown before (which was
     * especially unpleasant when the tab was merely being opened, not clicked).
     */
    private void resetEdgeDisplay() {
        Knowledge knowledge = getKnowledge();
        KnowledgeGraph graph = new KnowledgeGraph(getKnowledge());

        getVarNames().forEach(e -> {
            knowledge.addVariable(e);
            graph.addNode(new KnowledgeModelNode(e));
        });

        LayoutUtil.circleLayout(graph);

        List<String> notDrawn = new ArrayList<>();

        if (this.showRequiredByGroups) {
            List<KnowledgeEdge> list = new ArrayList<>();

            for (KnowledgeEdge e : knowledge.getListOfRequiredEdges()) {
                if (knowledge.isRequiredByGroups(e.getFrom(), e.getTo())) {
                    list.add(e);
                }
            }

            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showRequiredByGroups = false;
                notDrawn.add("required from groups (" + list.size() + ")");
            } else {
                list.forEach(e -> {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(e.getFrom());
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(e.getTo());

                    if (fromNode != null && toNode != null) {
                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.REQUIRED_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showForbiddenByGroups) {
            List<KnowledgeEdge> list = new ArrayList<>();

            for (KnowledgeEdge e : knowledge.getListOfForbiddenEdges()) {
                if (knowledge.isForbiddenByGroups(e.getFrom(), e.getTo())) {
                    list.add(e);
                }
            }

            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenByGroups = false;
                notDrawn.add("forbidden from groups (" + list.size() + ")");
            } else {
                list.forEach(e -> {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(e.getFrom());
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(e.getTo());

                    if (fromNode != null && toNode != null) {
                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_BY_GROUPS));
                    }
                });
            }
        }

        if (this.showRequired) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyRequiredEdges();

            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showRequired = false;
                notDrawn.add("required explicitly (" + list.size() + ")");
            } else {
                list.forEach(e -> {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(e.getFrom());
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(e.getTo());

                    if (!(fromNode == null || toNode == null)) {
                        graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.REQUIRED));
                    }
                });
            }
        }

        if (this.showForbiddenByTiers) {
            List<KnowledgeEdge> list = new ArrayList<>();

            for (KnowledgeEdge e : knowledge.getListOfForbiddenEdges()) {
                if (knowledge.isForbiddenByTiers(e.getFrom(), e.getTo())) {
                    list.add(e);
                }
            }

            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenByTiers = false;
                notDrawn.add("forbidden from tiers (" + list.size() + ")");
            } else {
                list.forEach(e -> {
                    String from = e.getFrom();
                    String to = e.getTo();
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(from);
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(to);

                    if (fromNode == null) {
                        graph.addNode(new KnowledgeModelNode(from));
                        fromNode = (KnowledgeModelNode) graph.getNode(from);
                    }

                    if (toNode == null) {
                        graph.addNode(new KnowledgeModelNode(to));
                        toNode = (KnowledgeModelNode) graph.getNode(to);
                    }

                    graph.addEdge(new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_BY_TIERS));
                });
            }
        }

        if (this.showForbiddenExplicitly) {
            List<KnowledgeEdge> list = knowledge.getListOfExplicitlyForbiddenEdges();

            if (list.size() > KnowledgeBoxEditor.EDGE_LIMIT) {
                this.showForbiddenExplicitly = false;
                notDrawn.add("forbidden explicitly (" + list.size() + ")");
            } else {
                list.forEach(e -> {
                    KnowledgeModelNode fromNode = (KnowledgeModelNode) graph.getNode(e.getFrom());
                    KnowledgeModelNode toNode = (KnowledgeModelNode) graph.getNode(e.getTo());

                    if (fromNode != null && toNode != null) {
                        KnowledgeModelEdge edge = new KnowledgeModelEdge(fromNode, toNode, KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
                        if (!graph.containsEdge(edge)) {
                            graph.addEdge(edge);
                        }
                    }
                });
            }
        }

        // Keep the checkboxes in step with the flags, which may have just been turned off.
        syncCheckBox(this.showForbiddenExplicitlyBox, this.showForbiddenExplicitly);
        syncCheckBox(this.showForbiddenByTiersBox, this.showForbiddenByTiers);
        syncCheckBox(this.showForbiddenByGroupsBox, this.showForbiddenByGroups);
        syncCheckBox(this.showRequiredExplicitlyBox, this.showRequired);
        syncCheckBox(this.showRequiredByGroupsBox, this.showRequiredByGroups);

        if (this.edgeStatusLabel != null) {
            this.edgeStatusLabel.setText(notDrawn.isEmpty() ? " "
                    : "Not drawn (more than " + KnowledgeBoxEditor.EDGE_LIMIT + " edges): "
                      + String.join("; ", notDrawn) + ". The Text tab lists every constraint.");
        }

        boolean arrangedAll = LayoutUtil.arrangeBySourceGraph(graph, this.edgeWorkbench.getGraph());

        if (!arrangedAll) {
            LayoutUtil.defaultLayout(graph);
        }

        // Redrawing the Edges tab is not a change to the knowledge.
        this.edgeWorkbench.setGraph(graph);
    }

    /**
     * Sets the checkbox's selection to the given value, if it exists and differs. (Programmatic
     * selection does not fire the checkbox's action listener, so this cannot recurse.)
     */
    private static void syncCheckBox(JCheckBox box, boolean selected) {
        if (box != null && box.isSelected() != selected) {
            box.setSelected(selected);
        }
    }

    private void notifyKnowledge() {
        this.lastNotifiedKnowledge = this.knowledge.toString();
        firePropertyChange("modelChanged", null, null);
    }

    /**
     * Reports a model change only if the knowledge differs from what it was at the last report (or when the editor
     * opened).
     */
    private void notifyIfKnowledgeChanged() {
        if (this.lastNotifiedKnowledge == null) {
            return; // Still constructing.
        }

        if (!this.knowledge.toString().equals(this.lastNotifiedKnowledge)) {
            notifyKnowledge();
        }
    }

    private Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
        this.knowledgeBoxModel.setKnowledge(knowledge);
    }

    private List<String> getVarNames() {
        return this.knowledge.getVariables();
    }

    private int getNumTiers() {
        return this.numTiers;
    }

    private void setNumTiers(int numTiers) {
        this.numTiers = numTiers;
    }

    @Override
    public void updateUI() {
        super.updateUI();

        setBackground(getPanelBackground());

        if (this.labelMap != null) {
            this.labelMap.values().forEach(label -> {
                label.setForeground(getLabelForeground());
                label.setBackground(getUnselectedLabelBackground());
                label.setBorder(new CompoundBorder(
                        new MatteBorder(2, 2, 2, 2, getPanelBackground()),
                        new LineBorder(getLabelBorderColor())
                ));
            });
        }

        if (this.tabbedPane != null) {
            resetTabbedPane();
        }

        revalidate();
        repaint();
    }

    private class DragDropList extends JList<String> {

        @Serial
        private static final long serialVersionUID = 7240458207688841986L;

        private final List<String> items;
        private final int tier;

        public DragDropList(List<String> items, int tier) {
            this.items = items;
            this.tier = tier;
            initComponents();
        }

        private void initComponents() {
            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            setDropMode(DropMode.ON_OR_INSERT);
            setDragEnabled(true);
            setOpaque(true);
            setBackground(getPanelBackground());
            setForeground(getLabelForeground());

            setCellRenderer((JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) -> {
                JLabel label = KnowledgeBoxEditor.this.labelMap.get(value);
                if (label == null) {
                    label = createJLabel(value);
                }

                label.setBackground(isSelected
                        ? KnowledgeBoxEditor.this.getSelectedLabelBackground()
                        : KnowledgeBoxEditor.this.getUnselectedLabelBackground());
                label.setForeground(KnowledgeBoxEditor.this.getLabelForeground());

                return label;
            });

            setTransferHandler(new TransferHandler() {

                @Serial
                private static final long serialVersionUID = 3109256773218160485L;

                @Override
                public boolean canImport(TransferSupport info) {
                    return info.isDataFlavorSupported(ListTransferable.DATA_FLAVOR);
                }

                @Override
                protected Transferable createTransferable(JComponent c) {
                    JList<?> source = (JList<?>) c;

                    List<?> list = source.getSelectedValuesList();
                    if (list == null) {
                        getToolkit().beep();
                        list = Collections.emptyList();
                    }

                    return new ListTransferable(list);
                }

                @Override
                public int getSourceActions(JComponent c) {
                    return TransferHandler.COPY_OR_MOVE;
                }

                @Override
                public boolean importData(TransferSupport info) {
                    if (!info.isDrop()) {
                        return false;
                    }

                    @SuppressWarnings("unchecked")
                    JList<String> source = (JList<String>) info.getComponent();
                    @SuppressWarnings("rawtypes")
                    DefaultListModel listModel = (DefaultListModel) source.getModel();
                    Knowledge knowledge = getKnowledge();

                    Transferable transferable = info.getTransferable();
                    try {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) transferable.getTransferData(ListTransferable.DATA_FLAVOR);
                        list.forEach(name -> {
                            if (DragDropList.this.tier >= 0) {
                                try {
                                    knowledge.removeFromTiers(name);
                                    knowledge.addToTier(DragDropList.this.tier, name);

                                    notifyKnowledge();

                                    listModel.addElement(name);
                                    sort(listModel);
                                } catch (IllegalStateException e) {
                                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
                                }
                            } else {
                                knowledge.removeFromTiers(name);

                                notifyKnowledge();
                                listModel.addElement(name);
                                sort(listModel);
                            }
                        });
                    } catch (IOException | UnsupportedFlavorException exception) {
                        exception.printStackTrace(System.err);
                        return false;
                    }

                    return true;
                }

                @Override
                protected void exportDone(JComponent c, Transferable data, int action) {
                    if (action == TransferHandler.MOVE) {
                        @SuppressWarnings("unchecked")
                        JList<String> source = (JList<String>) c;
                        DefaultListModel<String> listModel = (DefaultListModel<String>) source.getModel();
                        try {
                            @SuppressWarnings("unchecked")
                            List<String> list = (List<String>) data.getTransferData(ListTransferable.DATA_FLAVOR);
                            list.forEach(listModel::removeElement);
                        } catch (IOException | UnsupportedFlavorException ignored) {
                        }
                    }
                }
            });

            DefaultListModel<String> listModel = new DefaultListModel<>();
            this.items.forEach(listModel::addElement);
            setModel(listModel);
        }

        private void sort(DefaultListModel<String> listModel) {
            Object[] elements = listModel.toArray();
            String[] values = new String[elements.length];
            for (int i = 0; i < elements.length; i++) {
                values[i] = (String) elements[i];
            }

            // Unlagged names first, then increasing lag, natural base-name order within a lag
            // group; shared with DisplayNode.compareTo. The comparator this replaces compared
            // lag suffixes as strings, which put "X:10" before "X:2".
            Arrays.sort(values, NaturalSort.lagAscendingComparator());

            listModel.clear();
            Arrays.stream(values).forEach(listModel::addElement);
        }
    }
}