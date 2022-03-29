/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Apr 30, 2019 2:30:18 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class EdgeTypeTable extends JPanel {

    private static final long serialVersionUID = -9104061917163909746L;

    private static final String[] EDGES = {
            "Node 1",
            "Interaction",
            "Node 2"
    };

    private static final String[] EDGES_AND_EDGE_TYPES = {
            "Node 1",
            "Interaction",
            "Node 2",
            "Ensemble",
            "No edge",
            "\u2192",
            "\u2190",
            "\u2192", // -G> pd nl
            "\u2190", // <G- pd nl
            "\u2192", // =G> dd nl
            "\u2190", // <G= dd nl
            "o->",
            "<-o",
            "o-o",
            "<->",
            "---"
    };

    private final JLabel title = new JLabel();
    private final JTable table = new EdgeInfoTable(new DefaultTableModel());

    public EdgeTypeTable() {
        initComponents();
    }

    private void initComponents() {
        this.title.setHorizontalAlignment(SwingConstants.CENTER);
        this.title.setVerticalAlignment(SwingConstants.CENTER);

        setLayout(new BorderLayout(0, 10));
        add(this.title, BorderLayout.NORTH);
        add(new JScrollPane(this.table), BorderLayout.CENTER);
    }

    public void update(final Graph graph) {
        final DefaultTableModel tableModel = (DefaultTableModel) this.table.getModel();

        tableModel.setRowCount(0);

        if (hasEdgeProbabilities(graph)) {
            this.title.setText("Edges and Edge Type Probabilities");

            this.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            final JTableHeader header = this.table.getTableHeader();
            final Font boldFont = new Font(header.getFont().getFontName(), Font.BOLD, 18);
            final TableCellRenderer headerRenderer = header.getDefaultRenderer();
            header.setDefaultRenderer((tbl, value, isSelected, hasFocus, row, column) -> {
                final Component comp = headerRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (column > 6 && column < 11) {
                    comp.setForeground(Color.GREEN);
                }
                if (column > 8 && column < 11) {
                    comp.setFont(boldFont);
                }

                return comp;
            });

            tableModel.setColumnIdentifiers(EdgeTypeTable.EDGES_AND_EDGE_TYPES);

            final List<Edge> edges = graph.getEdges().stream().collect(Collectors.toList());
            Edges.sortEdges(edges);
            edges.forEach(edge -> {
                final String[] rowData = new String[EdgeTypeTable.EDGES_AND_EDGE_TYPES.length];
                addEdgeData(edge, rowData);
                addEdgeProbabilityData(edge, rowData);

                tableModel.addRow(rowData);
            });
        } else {
            this.title.setText("Edges");

            this.table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            tableModel.setColumnIdentifiers(EdgeTypeTable.EDGES);

            final List<Edge> edges = graph.getEdges().stream().collect(Collectors.toList());
            Edges.sortEdges(edges);
            edges.forEach(edge -> {
                final String[] rowData = new String[EdgeTypeTable.EDGES.length];
                addEdgeData(edge, rowData);

                tableModel.addRow(rowData);
            });
        }

        tableModel.fireTableDataChanged();
    }

    private void addEdgeProbabilityData(final Edge edge, final String[] rowData) {
        edge.getEdgeTypeProbabilities().forEach(edgeTypeProb -> {
            final String probValue = String.format("%.4f", edgeTypeProb.getProbability());
            boolean nl, pd, dd;
            switch (edgeTypeProb.getEdgeType()) {
                case nil:
                    rowData[4] = probValue;
                    break;
                case ta:
                    nl = false;
                    pd = false;
                    dd = false;
                    for (Edge.Property p : edgeTypeProb.getProperties()) {
                        if (p == Edge.Property.dd) {
                            dd = true;
                        }
                        if (p == Edge.Property.nl) {
                            nl = true;
                        }
                        if (p == Edge.Property.pd) {
                            pd = true;
                        }
                    }
                    if (nl && dd) {
                        rowData[9] = probValue;
                    } else if (nl && pd) {
                        rowData[7] = probValue;
                    } else {
                        rowData[5] = probValue;
                    }
                    break;
                case at:
                    nl = false;
                    pd = false;
                    dd = false;
                    for (Edge.Property p : edgeTypeProb.getProperties()) {
                        if (p == Edge.Property.dd) {
                            dd = true;
                        }
                        if (p == Edge.Property.nl) {
                            nl = true;
                        }
                        if (p == Edge.Property.pd) {
                            pd = true;
                        }
                    }
                    if (nl && dd) {
                        rowData[10] = probValue;
                    } else if (nl && pd) {
                        rowData[8] = probValue;
                    } else {
                        rowData[6] = probValue;
                    }
                    break;
                case ca:
                    rowData[11] = probValue;
                    break;
                case ac:
                    rowData[12] = probValue;
                    break;
                case cc:
                    rowData[13] = probValue;
                    break;
                case aa:
                    rowData[14] = probValue;
                    break;
                case tt:
                    rowData[15] = probValue;
                    break;
            }
        });

        final double maxEdgeProbability = edge.getEdgeTypeProbabilities().stream()
                .filter(e -> e.getEdgeType() != EdgeTypeProbability.EdgeType.nil)
                .mapToDouble(EdgeTypeProbability::getProbability)
                .max()
                .orElse(0);
        rowData[3] = String.format("%.4f", maxEdgeProbability);
    }

    private void addEdgeData(final Edge edge, final String[] rowData) {
        final String node1Name = edge.getNode1().getName();
        final String node2Name = edge.getNode2().getName();

        final Endpoint endpoint1 = edge.getEndpoint1();
        final Endpoint endpoint2 = edge.getEndpoint2();

        // These should not be flipped.
//        if (node1Name.compareTo(node2Name) > 0) {
//            // swap endpoints
//            Endpoint tmpEndpoint = endpoint1;
//            endpoint1 = endpoint2;
//            endpoint2 = tmpEndpoint;
//
//            // swap node names
//            String tmpStr = node1Name;
//            node1Name = node2Name;
//            node2Name = tmpStr;
//        }

        String endpoint1Str = "";
        if (endpoint1 == Endpoint.TAIL) {
            endpoint1Str = "-";
        } else if (endpoint1 == Endpoint.ARROW) {
            endpoint1Str = "<";
        } else if (endpoint1 == Endpoint.CIRCLE) {
            endpoint1Str = "o";
        }

        String endpoint2Str = "";
        if (endpoint2 == Endpoint.TAIL) {
            endpoint2Str = "-";
        } else if (endpoint2 == Endpoint.ARROW) {
            endpoint2Str = ">";
        } else if (endpoint2 == Endpoint.CIRCLE) {
            endpoint2Str = "o";
        }

        final String edgeType = endpoint1Str + "-" + endpoint2Str;

        rowData[0] = node1Name;
        rowData[1] = edgeType;
        rowData[2] = node2Name;
    }

    private boolean hasEdgeProbabilities(final Graph graph) {
        for (final Edge edge : graph.getEdges()) {
            return !edge.getEdgeTypeProbabilities().isEmpty();
        }

        return false;
    }

    private class EdgeInfoTable extends JTable {

        private static final long serialVersionUID = -4052775309418269033L;

        public EdgeInfoTable(final TableModel dm) {
            super(dm);
            initComponents();
        }

        private void initComponents() {
            setFillsViewportHeight(true);
            setDefaultRenderer(Object.class, new StripedRowTableCellRenderer());
            setOpaque(true);

            setRowSorter(new TableRowSorter<TableModel>(getModel()) {
                @Override
                public boolean isSortable(final int column) {
                    return !(column == 1 || column == 12);
                }
            });
        }

        @Override
        public Component prepareRenderer(final TableCellRenderer renderer, final int row, final int column) {
            // adjust each column width automatically to fit the content
            final Component component = super.prepareRenderer(renderer, row, column);
            final int rendererWidth = component.getPreferredSize().width;
            final TableColumn tableColumn = getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(Math.max(rendererWidth + getIntercellSpacing().width, tableColumn.getPreferredWidth()));

            return component;
        }

    }

    private class StripedRowTableCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 4603884548966502824L;

        private final Color STRIPE = new Color(0.929f, 0.953f, 0.996f);
        private final Color NON_STRIPE = Color.WHITE;

        public StripedRowTableCellRenderer() {
            initComponents();
        }

        private void initComponents() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            final JComponent component = (JComponent) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                component.setBackground((row % 2 == 0) ? this.NON_STRIPE : this.STRIPE);
            }

            return component;
        }

    }

}
