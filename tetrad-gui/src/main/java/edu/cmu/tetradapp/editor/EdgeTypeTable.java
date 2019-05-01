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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 *
 * Apr 30, 2019 2:30:18 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class EdgeTypeTable extends JPanel {

    private static final long serialVersionUID = -9104061917163909746L;

    private static final String[] EDGES = new String[]{
        "Node 1",
        "Interaction",
        "Node 2"
    };

    private static final String[] EDGES_AND_EDGE_TYPES = new String[]{
        "Node 1",
        "Interaction",
        "Node 2",
        "Ensemble",
        "No edge",
        "-->",
        "<--",
        "-->", // -G> pd nl
        "<--", // <G- pd nl
        "-->", // =G> dd nl
        "<--", // <G= dd nl
        "o->",
        "<-o",
        "o-o",
        "<->",
        "---"
    };

    private final JLabel title = new JLabel();
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable table = new JTable() {

        private static final long serialVersionUID = -2109240922540773533L;

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            // adjust each column width automatically to fit the content
            Component component = super.prepareRenderer(renderer, row, column);
            int rendererWidth = component.getPreferredSize().width;
            TableColumn tableColumn = getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(Math.max(rendererWidth + getIntercellSpacing().width, tableColumn.getPreferredWidth()));
            return component;
        }

    };

    public EdgeTypeTable() {
        initComponents();
    }

    private void initComponents() {
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setVerticalAlignment(SwingConstants.CENTER);

        table.setRowSorter(new TableRowSorter<TableModel>(tableModel) {
            @Override
            public boolean isSortable(int column) {
                return !(column == 1 || column == 12);
            }
        });
        table.setModel(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);

        setLayout(new BorderLayout(0, 10));
        add(title, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void update(Graph graph) {
        tableModel.setRowCount(0);

        if (hasEdgeProbabilities(graph)) {
            title.setText("Edges and Edge Type Probabilities");

            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            final JTableHeader header = table.getTableHeader();
            final Font boldFont = new Font(header.getFont().getFontName(), Font.BOLD, 18);
            final TableCellRenderer headerRenderer = header.getDefaultRenderer();
            header.setDefaultRenderer((tbl, value, isSelected, hasFocus, row, column) -> {
                Component comp = headerRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (column > 6 && column < 11) {
                    comp.setForeground(Color.GREEN);
                }
                if (column > 8 && column < 11) {
                    comp.setFont(boldFont);
                }

                return comp;
            });

            tableModel.setColumnIdentifiers(EDGES_AND_EDGE_TYPES);

            List<Edge> edges = graph.getEdges().stream().collect(Collectors.toList());
            Edges.sortEdges(edges);
            edges.forEach(edge -> {
                String[] rowData = new String[EDGES_AND_EDGE_TYPES.length];
                addEdgeData(edge, rowData);
                addEdgeProbabilityData(edge, rowData);

                tableModel.addRow(rowData);
            });
        } else {
            title.setText("Edges");

            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            tableModel.setColumnIdentifiers(EDGES);

            List<Edge> edges = graph.getEdges().stream().collect(Collectors.toList());
            Edges.sortEdges(edges);
            edges.forEach(edge -> {
                String[] rowData = new String[EDGES.length];
                addEdgeData(edge, rowData);

                tableModel.addRow(rowData);
            });
        }

        tableModel.fireTableDataChanged();
    }

    private void addEdgeProbabilityData(Edge edge, String[] rowData) {
        edge.getEdgeTypeProbabilities().forEach(edgeTypeProb -> {
            String probValue = String.format("%.4f", edgeTypeProb.getProbability());
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

        double maxEdgeProbability = edge.getEdgeTypeProbabilities().stream()
                .filter(e -> e.getEdgeType() != EdgeTypeProbability.EdgeType.nil)
                .mapToDouble(EdgeTypeProbability::getProbability)
                .max()
                .orElse(0);
        rowData[3] = String.format("%.4f", maxEdgeProbability);
    }

    private void addEdgeData(Edge edge, String[] rowData) {
        String node1Name = edge.getNode1().getName();
        String node2Name = edge.getNode2().getName();

        Endpoint endpoint1 = edge.getEndpoint1();
        Endpoint endpoint2 = edge.getEndpoint2();

        if (node1Name.compareTo(node2Name) > 0) {
            // swap endpoints
            Endpoint tmpEndpoint = endpoint1;
            endpoint1 = endpoint2;
            endpoint2 = tmpEndpoint;

            // swap node names
            String tmpStr = node1Name;
            node1Name = node2Name;
            node2Name = tmpStr;
        }

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

        String edgeType = endpoint1Str + "-" + endpoint2Str;

        rowData[0] = node1Name;
        rowData[1] = edgeType;
        rowData[2] = node2Name;
    }

    private boolean hasEdgeProbabilities(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            return !edge.getEdgeTypeProbabilities().isEmpty();
        }

        return false;
    }

}
