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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeTierStructure;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits the additional tier structures of a knowledge object: independent temporal orderings over
 * subsets of the variables, each with its own tiers, alongside the main tiers of the Tiers tab.
 * This is for data whose variables carry several orderings at once -- one over the exams, another
 * over the module checkpoints, and so on -- which a single tier list cannot represent without
 * forbidding edges between the orderings.
 *
 * <p>Each structure is shown as a box with its name, a list of the variables not in the structure,
 * and its tiers; variables are dragged between these lists, or moved in bulk with the pattern bar,
 * exactly as in the Tiers tab. Within a structure, edges from a later tier to an earlier tier are
 * forbidden, and a tier may forbid edges within itself; structures place no constraints on one
 * another.
 *
 * @author josephramsey
 */
class OtherTiersEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge being edited.
     */
    private final Knowledge knowledge;

    /**
     * Called after every edit of the knowledge, so the surrounding editor can report a model
     * change.
     */
    private final Runnable modelChange;

    /**
     * The number of tiers displayed for each structure, which may exceed the number of tiers the
     * structure has. Keyed by the structure object by IDENTITY: the structures are mutable and
     * their hash codes change as tiers are edited, so a hash map would lose the entry after any
     * edit -- which is what made the tier spinner snap back to its minimum.
     */
    private final Map<KnowledgeTierStructure, Integer> displayTiers = new IdentityHashMap<>();

    /**
     * The wildcard expression last typed into this tab's pattern field, the status message it last
     * produced, and the destination slot last chosen, preserved across rebuilds.
     */
    private String globText = "";

    private String globStatus = " ";

    private int globSlot = 0;

    /**
     * The scroll pane holding the structure boxes, and its last vertical scroll position,
     * restored after a rebuild so that repeated spinner clicks and drags do not lose the place.
     */
    private JScrollPane structuresScrollPane;

    private int structuresScrollValue;

    /**
     * True when the next rebuild follows adding a structure, in which case the view scrolls to
     * the new structure (at the bottom) and focuses its name field instead of staying where it
     * was. Without this, the new box appeared below the fold -- the first two boxes fill the
     * viewport -- and the Add button looked like it had stopped working.
     */
    private boolean showNewestOnRebuild;

    /**
     * The name field of the last (newest) structure box, focused after an add.
     */
    private JTextField newestNameField;

    /**
     * Constructs the editor.
     *
     * @param knowledge   the knowledge whose tier structures are edited
     * @param modelChange called after every edit of the knowledge
     */
    public OtherTiersEditor(Knowledge knowledge, Runnable modelChange) {
        if (knowledge == null) {
            throw new NullPointerException("The given knowledge must not be null");
        }

        this.knowledge = knowledge;
        this.modelChange = modelChange == null ? () -> {
        } : modelChange;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setOpaque(true);
        setBackground(getPanelBackground());

        rebuild();
    }

    //===================== Theme helpers (as in OtherGroupsEditor) =====================//

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

    private static Color getPanelBackground() {
        return uiColor("Panel.background", isDarkMode() ? new Color(43, 43, 43) : Color.WHITE);
    }

    private static Color getSubpanelBackground() {
        Color panel = getPanelBackground();
        return isDarkMode() ? brighten(panel, 0.03) : panel;
    }

    private static Color getPrimaryTextColor() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(230, 230, 230) : Color.BLACK);
    }

    private static Color getMutedTextColor() {
        Color fg = getPrimaryTextColor();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.30) : blend(fg, Color.WHITE, 0.20);
    }

    private static Color getBorderColor() {
        Color c = uiColor("Component.borderColor", null);
        if (c != null) return c;

        c = uiColor("Separator.foreground", null);
        if (c != null) return c;

        Color fg = getPrimaryTextColor();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.35) : darken(fg, 0.15);
    }

    private static Color getVariableFillColor() {
        if (isDarkMode()) {
            Color panel = uiColor("Panel.background", new Color(60, 63, 65));
            Color button = uiColor("Button.background", panel);
            return brighten(blend(panel, button, 0.5), 0.08);
        }

        Color base = uiColor("Button.background", new Color(230, 240, 240));
        return blend(base, new Color(153, 204, 204), 0.60);
    }

    private static Color getSelectedVariableFillColor() {
        if (isDarkMode()) {
            Color sel = UIManager.getColor("Table.selectionBackground");
            if (sel != null) return sel;
            return new Color(90, 130, 180);
        }

        return DisplayNodeUtils.getNodeSelectedFillColor();
    }

    private static void themePanel(JComponent c) {
        c.setOpaque(true);
        c.setBackground(getPanelBackground());
        c.setForeground(getPrimaryTextColor());
    }

    private static void themeSubpanel(JComponent c) {
        c.setOpaque(true);
        c.setBackground(getSubpanelBackground());
        c.setForeground(getPrimaryTextColor());
    }

    private static void themeScrollPane(JScrollPane pane) {
        pane.setOpaque(true);
        pane.setBackground(getPanelBackground());
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(getPanelBackground());
        pane.setBorder(new LineBorder(getBorderColor()));
    }

    private static void themeLabel(JLabel label) {
        label.setForeground(getPrimaryTextColor());
        label.setOpaque(false);
    }

    private static void themeButton(AbstractButton button) {
        button.setForeground(getPrimaryTextColor());
    }

    private static void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(getPrimaryTextColor());
    }

    //===================== Building =====================//

    private void rebuild() {
        if (this.structuresScrollPane != null) {
            this.structuresScrollValue = this.structuresScrollPane.getVerticalScrollBar().getValue();
        }

        removeAll();
        themePanel(this);
        add(buildComponent());
        revalidate();
        repaint();

        if (this.structuresScrollPane != null) {
            boolean showNewest = this.showNewestOnRebuild;
            this.showNewestOnRebuild = false;
            int value = this.structuresScrollValue;
            JScrollPane pane = this.structuresScrollPane;
            JTextField nameField = this.newestNameField;

            SwingUtilities.invokeLater(() -> {
                JScrollBar bar = pane.getVerticalScrollBar();

                if (showNewest) {
                    bar.setValue(bar.getMaximum());

                    if (nameField != null) {
                        nameField.requestFocusInWindow();
                        nameField.selectAll();
                    }
                } else {
                    bar.setValue(value);
                }
            });
        }
    }

    /**
     * Rebuilds the tab after the current event (a drop, a button press) finishes, so that the
     * components taking part in the event are not removed under it.
     */
    private void rebuildLater() {
        SwingUtilities.invokeLater(this::rebuild);
    }

    private void changed() {
        this.modelChange.run();
    }

    private Box buildComponent() {
        Box vBox = Box.createVerticalBox();
        themePanel(vBox);

        JButton addStructure = new JButton("Add New Tier Structure");
        themeButton(addStructure);
        addStructure.addActionListener(e -> {
            String name = "Structure " + (this.knowledge.getNumTierStructures() + 1);
            this.knowledge.addTierStructure(new KnowledgeTierStructure(name));
            changed();
            this.showNewestOnRebuild = true;
            rebuild();
        });

        Box buttons = Box.createHorizontalBox();
        buttons.setOpaque(false);
        buttons.add(addStructure);
        buttons.add(Box.createHorizontalStrut(10));

        JLabel hint = new JLabel("Each structure is an independent set of tiers over its own variables.");
        hint.setForeground(getMutedTextColor());
        buttons.add(hint);
        buttons.add(Box.createHorizontalGlue());

        vBox.add(buttons);
        vBox.add(Box.createVerticalStrut(5));
        vBox.add(globBar());
        vBox.add(Box.createVerticalStrut(5));

        Box structureBoxes = Box.createVerticalBox();
        themePanel(structureBoxes);

        List<KnowledgeTierStructure> structures = this.knowledge.getTierStructures();
        for (int i = 0; i < structures.size(); i++) {
            structureBoxes.add(buildStructureBox(i, structures.get(i)));
            structureBoxes.add(Box.createVerticalStrut(5));
        }
        structureBoxes.add(Box.createVerticalGlue());

        JScrollPane pane = new JScrollPane(structureBoxes);
        pane.setPreferredSize(new Dimension(500, 400));
        themeScrollPane(pane);

        // A Box view gives the scroll pane a unit increment of one pixel, which makes wheel
        // scrolling to the structures below the fold all but impossible.
        pane.getVerticalScrollBar().setUnitIncrement(16);

        this.structuresScrollPane = pane;
        vBox.add(pane);

        JLabel help = new JLabel("Use shift key to select multiple items.");
        help.setForeground(getMutedTextColor());
        Box helpBox = Box.createHorizontalBox();
        helpBox.setOpaque(false);
        helpBox.add(help);
        helpBox.add(Box.createHorizontalGlue());
        vBox.add(helpBox);

        return vBox;
    }

    private int displayTiersFor(KnowledgeTierStructure structure) {
        int shown = this.displayTiers.getOrDefault(structure, 0);
        return Math.max(2, Math.max(shown, structure.getNumTiers()));
    }

    private Box buildStructureBox(int index, KnowledgeTierStructure structure) {
        int numTiers = displayTiersFor(structure);
        this.displayTiers.put(structure, numTiers);

        Box vBox = Box.createVerticalBox();
        themeSubpanel(vBox);
        vBox.setBorder(new CompoundBorder(
                new LineBorder(getBorderColor()),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Header: name field, tier count spinner, remove button.
        Box header = Box.createHorizontalBox();
        header.setOpaque(false);

        JLabel nameLabel = new JLabel("Name:");
        themeLabel(nameLabel);
        header.add(nameLabel);
        header.add(Box.createHorizontalStrut(5));

        JTextField nameField = new JTextField(structure.getName(), 14);
        nameField.setMaximumSize(new Dimension(220, nameField.getPreferredSize().height));

        Runnable commitName = () -> {
            String newName = nameField.getText().trim();

            if (newName.isEmpty()) {
                nameField.setText(structure.getName());
                return;
            }

            if (!newName.equals(structure.getName())) {
                structure.setName(newName);
                changed();
                rebuildLater();
            }
        };

        nameField.addActionListener(e -> commitName.run());
        nameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitName.run();
            }
        });

        this.newestNameField = nameField; // boxes build in order, so the last one built wins

        header.add(nameField);
        header.add(Box.createHorizontalGlue());

        JLabel numTiersLabel = new JLabel("# Tiers = ");
        themeLabel(numTiersLabel);
        header.add(numTiersLabel);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(numTiers, 2, 100, 1);
        spinnerModel.addChangeListener(e -> {
            int newNum = spinnerModel.getNumber().intValue();
            this.displayTiers.put(structure, newNum);

            boolean edited = false;

            for (int i = newNum; i < structure.getNumTiers(); i++) {
                for (String var : structure.getTier(i)) {
                    structure.removeFromTiers(var);
                    edited = true;
                }

                if (structure.isTierForbiddenWithin(i)) {
                    structure.setTierForbiddenWithin(i, false);
                    edited = true;
                }
            }

            structure.trimTrailingEmptyTiers();

            if (edited) {
                changed();
            }

            rebuildLater();
        });

        JSpinner spinner = new JSpinner(spinnerModel);
        spinner.setMaximumSize(spinner.getPreferredSize());
        header.add(spinner);
        header.add(Box.createHorizontalStrut(10));

        JButton remove = new JButton("Remove");
        themeButton(remove);
        remove.setFont(remove.getFont().deriveFont(11f));
        remove.setMargin(new Insets(3, 4, 3, 4));
        remove.addActionListener(e -> {
            boolean hadEffect = !structure.isEmpty();
            this.knowledge.removeTierStructure(index);
            this.displayTiers.remove(structure);

            if (hadEffect) {
                changed();
            }

            rebuild();
        });
        header.add(remove);

        vBox.add(header);
        vBox.add(Box.createVerticalStrut(5));

        // The variables not in this structure.
        Box notInRow = Box.createHorizontalBox();
        notInRow.setOpaque(false);
        JLabel notInLabel = new JLabel("Not in this structure:");
        themeLabel(notInLabel);
        notInRow.add(notInLabel);
        notInRow.add(Box.createHorizontalGlue());
        vBox.add(notInRow);

        List<String> notIn = new ArrayList<>(this.knowledge.getVariables());
        notIn.removeAll(structure.getVariables());

        JScrollPane notInPane = new JScrollPane(new StructureDragDropList(structure, -1, notIn));
        notInPane.setPreferredSize(new Dimension(460, 50));
        themeScrollPane(notInPane);
        vBox.add(notInPane);
        vBox.add(Box.createVerticalStrut(5));

        // The tiers.
        for (int tier = 0; tier < numTiers; tier++) {
            Box tierHeader = Box.createHorizontalBox();
            tierHeader.setOpaque(false);

            JLabel tierLabel = new JLabel("Tier " + tier);
            themeLabel(tierLabel);
            tierHeader.add(tierLabel);
            tierHeader.add(Box.createHorizontalGlue());

            int _tier = tier;

            // Rendering must not edit the structure: rows beyond its tiers are displayed empty
            // without creating tiers in it (creating them changed its hash code mid-display,
            // which is also why the display map is now keyed by identity).
            boolean forbidden = tier < structure.getNumTiers() && structure.isTierForbiddenWithin(tier);
            JCheckBox forbidWithin = new JCheckBox("Forbid Within Tier", forbidden);
            styleCheckBox(forbidWithin);
            forbidWithin.addActionListener(e -> {
                structure.setTierForbiddenWithin(_tier, forbidWithin.isSelected());
                changed();
            });
            tierHeader.add(forbidWithin);

            vBox.add(tierHeader);

            List<String> tierContents = tier < structure.getNumTiers()
                    ? structure.getTier(tier) : new ArrayList<>();
            JScrollPane tierPane = new JScrollPane(
                    new StructureDragDropList(structure, tier, tierContents));
            tierPane.setPreferredSize(new Dimension(460, 50));
            themeScrollPane(tierPane);
            vBox.add(tierPane);
        }

        return vBox;
    }

    /**
     * Builds the pattern bar: a text field taking a wildcard expression in which {@code *} matches
     * any string and {@code ?} any single character, a dropdown naming a destination -- a tier of
     * one of the structures, or out of one of the structures -- and a Move button that sends every
     * matching variable there. This is the bulk equivalent of dragging names one at a time, which
     * matters when a structure covers dozens of variables.
     */
    private Box globBar() {
        Box bar = Box.createHorizontalBox();
        bar.setOpaque(false);

        JLabel patternLabel = new JLabel("Pattern:");
        themeLabel(patternLabel);
        bar.add(patternLabel);
        bar.add(Box.createHorizontalStrut(5));

        JTextField patternField = new JTextField(this.globText, 12);
        patternField.setToolTipText("<html>A wildcard over variable names: <b>*</b> matches any "
                + "string, <b>?</b> any single character.<br>"
                + "Separate alternatives with commas. Matching is case sensitive.<br>"
                + "Examples: <tt>X*</tt> &nbsp; <tt>*age*</tt> &nbsp; <tt>V??</tt> &nbsp; "
                + "<tt>X*, Y*</tt></html>");
        patternField.setMaximumSize(new Dimension(220, patternField.getPreferredSize().height));
        bar.add(patternField);
        bar.add(Box.createHorizontalStrut(5));

        JLabel toLabel = new JLabel("move to");
        themeLabel(toLabel);
        bar.add(toLabel);
        bar.add(Box.createHorizontalStrut(5));

        List<KnowledgeTierStructure> structures = this.knowledge.getTierStructures();

        // Slot layout: for structure i with n displayed tiers, its tiers come first, then an
        // entry removing matches from the structure.
        JComboBox<String> destination = new JComboBox<>();
        List<int[]> slots = new ArrayList<>(); // {structure index, tier or -1}

        for (int i = 0; i < structures.size(); i++) {
            KnowledgeTierStructure structure = structures.get(i);
            int numTiers = displayTiersFor(structure);

            for (int tier = 0; tier < numTiers; tier++) {
                destination.addItem(structure.getName() + ", Tier " + tier);
                slots.add(new int[]{i, tier});
            }

            destination.addItem("Out of " + structure.getName());
            slots.add(new int[]{i, -1});
        }

        destination.setMaximumSize(new Dimension(230, destination.getPreferredSize().height));

        if (this.globSlot >= 0 && this.globSlot < destination.getItemCount()) {
            destination.setSelectedIndex(this.globSlot);
        }

        bar.add(destination);
        bar.add(Box.createHorizontalStrut(5));

        JButton move = new JButton("Move");
        themeButton(move);
        bar.add(move);
        bar.add(Box.createHorizontalStrut(10));

        JLabel status = new JLabel(structures.isEmpty()
                ? "Add a structure first, then variables can be moved into it by pattern."
                : this.globStatus);
        status.setForeground(getMutedTextColor());
        bar.add(status);
        bar.add(Box.createHorizontalGlue());

        if (structures.isEmpty()) {
            patternField.setEnabled(false);
            destination.setEnabled(false);
            move.setEnabled(false);
            return bar;
        }

        ActionListener doMove = e -> {
            String spec = patternField.getText().trim();
            this.globText = spec;
            this.globSlot = destination.getSelectedIndex();

            if (spec.isEmpty()) {
                status.setText("Type a pattern, such as X*, first.");
                return;
            }

            List<String> matched = VariableGlob.match(spec, this.knowledge.getVariables());

            if (matched.isEmpty()) {
                status.setText("No variable names matched " + spec + ".");
                return;
            }

            int[] slot = slots.get(destination.getSelectedIndex());
            KnowledgeTierStructure structure = this.knowledge.getTierStructures().get(slot[0]);
            int tier = slot[1];

            for (String name : matched) {
                if (tier < 0) {
                    structure.removeFromTiers(name);
                } else {
                    structure.addToTier(tier, name);
                }
            }

            this.globStatus = tier < 0
                    ? "Removed " + VariableGlob.countPhrase(matched.size()) + " from "
                    + structure.getName() + "."
                    : "Moved " + VariableGlob.countPhrase(matched.size()) + " to "
                    + structure.getName() + ", Tier " + tier + ".";

            changed();
            rebuild();
        };

        move.addActionListener(doMove);
        patternField.addActionListener(doMove);

        return bar;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.knowledge != null) {
            rebuild();
        }
    }

    //===================== Drag and drop =====================//

    /**
     * A list of variable chips belonging to one slot of one structure: tier {@code >= 0} is that
     * tier of the structure, and {@code -1} is the "not in this structure" list. A drop moves the
     * dragged names into the slot in the knowledge, and the tab is then rebuilt from the
     * knowledge, so the lists always show the recorded state; this also keeps a drag between two
     * structures honest (the name joins the target structure and stays in the source structure,
     * since a variable may belong to several structures at once).
     */
    private class StructureDragDropList extends JList<String> {

        @Serial
        private static final long serialVersionUID = 23L;

        private final KnowledgeTierStructure structure;
        private final int tier;

        public StructureDragDropList(KnowledgeTierStructure structure, int tier, List<String> items) {
            this.structure = structure;
            this.tier = tier;

            setLayoutOrientation(JList.HORIZONTAL_WRAP);
            setVisibleRowCount(0);
            setDropMode(DropMode.ON_OR_INSERT);
            setDragEnabled(true);
            setOpaque(true);
            setBackground(getPanelBackground());
            setForeground(getPrimaryTextColor());

            setCellRenderer((JList<? extends String> list, String value, int index,
                             boolean isSelected, boolean cellHasFocus) -> {
                JLabel label = new JLabel(String.format("  %s  ", value));
                label.setOpaque(true);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(new CompoundBorder(
                        new MatteBorder(2, 2, 2, 2, getSubpanelBackground()),
                        new LineBorder(getBorderColor())
                ));
                label.setForeground(getPrimaryTextColor());
                label.setBackground(isSelected ? getSelectedVariableFillColor() : getVariableFillColor());
                return label;
            });

            setTransferHandler(new TransferHandler() {

                @Serial
                private static final long serialVersionUID = 23L;

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
                        list = new ArrayList<>();
                    }

                    return new ListTransferable(list);
                }

                @Override
                public int getSourceActions(JComponent c) {
                    return TransferHandler.MOVE;
                }

                @Override
                public boolean importData(TransferSupport info) {
                    if (!info.isDrop()) {
                        return false;
                    }

                    Transferable transferable = info.getTransferable();

                    try {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>)
                                transferable.getTransferData(ListTransferable.DATA_FLAVOR);

                        boolean edited = false;

                        for (String name : list) {
                            if (StructureDragDropList.this.tier >= 0) {
                                if (StructureDragDropList.this.structure.isInWhichTier(name)
                                    != StructureDragDropList.this.tier) {
                                    StructureDragDropList.this.structure
                                            .addToTier(StructureDragDropList.this.tier, name);
                                    edited = true;
                                }
                            } else {
                                if (StructureDragDropList.this.structure.isInWhichTier(name) >= 0) {
                                    StructureDragDropList.this.structure.removeFromTiers(name);
                                    edited = true;
                                }
                            }
                        }

                        if (edited) {
                            changed();
                        }

                        // The lists are rebuilt from the knowledge once the drop completes, so
                        // no list models are edited here or in exportDone.
                        rebuildLater();
                        return true;
                    } catch (IOException | UnsupportedFlavorException exception) {
                        exception.printStackTrace(System.err);
                        return false;
                    }
                }
            });

            DefaultListModel<String> listModel = new DefaultListModel<>();
            items.forEach(listModel::addElement);
            setModel(listModel);
        }
    }
}
