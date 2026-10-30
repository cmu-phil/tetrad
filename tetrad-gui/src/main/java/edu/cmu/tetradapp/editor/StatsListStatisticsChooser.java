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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.CompareTwoGraphs;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Lets the user choose which statistics the Stats List comparison box displays. The choice is stored in the box's
 * Parameters under {@link #STATISTICS_KEY} as a String[] of statistic abbreviations, so it is saved with the session;
 * when the key is absent (e.g., sessions saved before this was added), the box shows the default list with output
 * unchanged.
 * <p>
 * The catalog offered is the default stats-list statistics, in their usual order, followed by every other statistic
 * in edu.cmu.tetrad.algcomparison.statistic that has a public no-argument constructor (found by the same reflection
 * scan the grid search tool uses), sorted by abbreviation. Statistics needing extra construction arguments (e.g., the
 * Markov checker statistics) are not offered. The dialog pattern — a checkbox table with a semicolon-separated regex
 * filter — follows the grid search tool's column chooser.
 *
 * @author josephramsey
 */
public final class StatsListStatisticsChooser {

    /**
     * The parameter key under which the chosen statistic abbreviations are stored.
     */
    public static final String STATISTICS_KEY = "statsListStatistics";

    /**
     * The statistic catalog, built once on first use.
     */
    private static List<Statistic> catalog;

    /**
     * Utility class.
     */
    private StatsListStatisticsChooser() {
    }

    /**
     * Returns the statistics a user may choose from, in display order: defaults first, then the rest by abbreviation.
     *
     * @return the catalog (unmodifiable).
     */
    public static synchronized List<Statistic> catalog() {
        if (catalog != null) {
            return catalog;
        }

        List<Statistic> list = new ArrayList<>(CompareTwoGraphs.defaultStatistics());
        Set<String> seen = new HashSet<>();

        for (Statistic statistic : list) {
            seen.add(statistic.getAbbreviation());
        }

        List<Statistic> others = new ArrayList<>();

        try {
            Set<Class<? extends Statistic>> classes = new Reflections("edu.cmu.tetrad.algcomparison.statistic",
                    Scanners.SubTypes).getSubTypesOf(Statistic.class);

            for (Class<? extends Statistic> clazz : classes) {
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())
                    || !Modifier.isPublic(clazz.getModifiers()) || clazz.isAnonymousClass()) {
                    continue;
                }

                try {
                    Statistic statistic = clazz.getConstructor().newInstance();
                    String abbreviation = statistic.getAbbreviation();

                    if (abbreviation != null && !abbreviation.isBlank() && seen.add(abbreviation)) {
                        others.add(statistic);
                    }
                } catch (NoSuchMethodException e) {
                    // Needs constructor arguments; not offered here.
                } catch (Throwable t) {
                    TetradLogger.getInstance().log("Skipping statistic " + clazz.getSimpleName() + ": " + t);
                }
            }
        } catch (Throwable t) {
            TetradLogger.getInstance().log("Could not scan for statistics; offering defaults only: " + t);
        }

        others.sort(Comparator.comparing(Statistic::getAbbreviation, String.CASE_INSENSITIVE_ORDER));
        list.addAll(others);
        catalog = Collections.unmodifiableList(list);
        return catalog;
    }

    /**
     * Returns the statistics chosen for display, in catalog order, or null if no choice has been stored (in which
     * case the caller should use the default table methods, whose output is unchanged from before). Stored
     * abbreviations that no longer match a catalog statistic are skipped.
     *
     * @param params the comparison box's parameters.
     * @return the chosen statistics, or null.
     */
    public static List<Statistic> selected(Parameters params) {
        if (params.getNumValues(STATISTICS_KEY) == 0) {
            return null;
        }

        Set<String> chosen = new HashSet<>();

        for (Object value : params.getValues(STATISTICS_KEY)) {
            if (value != null) {
                chosen.add(value.toString());
            }
        }

        List<Statistic> statistics = new ArrayList<>();

        for (Statistic statistic : catalog()) {
            if (chosen.contains(statistic.getAbbreviation())) {
                statistics.add(statistic);
            }
        }

        return statistics;
    }

    /**
     * Shows the chooser as a modal dialog. On OK, stores the choice in the parameters and returns true; on Cancel,
     * leaves the parameters unchanged and returns false.
     *
     * @param parent the component to center the dialog on.
     * @param params the comparison box's parameters.
     * @return true if the user pressed OK.
     */
    public static boolean show(Component parent, Parameters params) {
        List<Statistic> all = catalog();
        List<Statistic> current = selected(params);
        Set<String> defaults = new HashSet<>();

        for (Statistic statistic : CompareTwoGraphs.defaultStatistics()) {
            defaults.add(statistic.getAbbreviation());
        }

        boolean[] checked = new boolean[all.size()];

        for (int i = 0; i < all.size(); i++) {
            String abbreviation = all.get(i).getAbbreviation();
            checked[i] = current == null ? defaults.contains(abbreviation) : current.contains(all.get(i));
        }

        AbstractTableModel tableModel = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return all.size();
            }

            @Override
            public int getColumnCount() {
                return 3;
            }

            @Override
            public String getColumnName(int column) {
                return switch (column) {
                    case 0 -> "Show";
                    case 1 -> "Statistic";
                    default -> "Description";
                };
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }

            @Override
            public Object getValueAt(int row, int column) {
                return switch (column) {
                    case 0 -> checked[row];
                    case 1 -> all.get(row).getAbbreviation();
                    default -> all.get(row).getDescription();
                };
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                if (column == 0) {
                    checked[row] = Boolean.TRUE.equals(value);
                    fireTableCellUpdated(row, column);
                }
            }
        };

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);

        TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JTextField filterText = new JTextField(20);
        filterText.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String text = filterText.getText();

                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                    return;
                }

                List<RowFilter<Object, Object>> filters = new ArrayList<>();

                for (String part : text.split(";")) {
                    try {
                        if (!part.trim().isEmpty()) {
                            filters.add(RowFilter.regexFilter("(?i)" + part.trim(), 1, 2));
                        }
                    } catch (PatternSyntaxException ignored) {
                        // Incomplete regex while typing.
                    }
                }

                sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.orFilter(filters));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        // Buttons act on the rows currently visible through the filter, so "regex, then Check visible" works.
        JButton defaultsButton = new JButton("Defaults");
        JButton checkVisible = new JButton("Check visible");
        JButton uncheckVisible = new JButton("Uncheck visible");

        defaultsButton.addActionListener(e -> {
            for (int i = 0; i < all.size(); i++) {
                checked[i] = defaults.contains(all.get(i).getAbbreviation());
            }

            tableModel.fireTableDataChanged();
        });

        checkVisible.addActionListener(e -> setVisibleRows(table, checked, true, tableModel));
        uncheckVisible.addActionListener(e -> setVisibleRows(table, checked, false, tableModel));

        Box filterBox = Box.createHorizontalBox();
        filterBox.add(new JLabel("Filter (regexes, semicolon separated): "));
        filterBox.add(filterText);

        Box buttonBox = Box.createHorizontalBox();
        buttonBox.add(defaultsButton);
        buttonBox.add(Box.createHorizontalStrut(5));
        buttonBox.add(checkVisible);
        buttonBox.add(Box.createHorizontalStrut(5));
        buttonBox.add(uncheckVisible);
        buttonBox.add(Box.createHorizontalGlue());

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(filterBox, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(650, 450));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonBox, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Choose Statistics to Display",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        List<String> names = new ArrayList<>();

        for (int i = 0; i < all.size(); i++) {
            if (checked[i]) {
                names.add(all.get(i).getAbbreviation());
            }
        }

        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No statistics chosen; keeping the previous selection.");
            return false;
        }

        params.set(STATISTICS_KEY, names.toArray(new String[0]));
        return true;
    }

    private static void setVisibleRows(JTable table, boolean[] checked, boolean value, AbstractTableModel model) {
        for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
            checked[table.convertRowIndexToModel(viewRow)] = value;
        }

        model.fireTableDataChanged();
    }
}
