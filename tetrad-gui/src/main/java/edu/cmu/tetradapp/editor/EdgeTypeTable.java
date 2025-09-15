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

import edu.cmu.tetrad.graph.*;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Apr 30, 2019 2:30:18 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class EdgeTypeTable extends JPanel {

    @Serial
    private static final long serialVersionUID = -9104061917163909746L;

    private static final String[] EDGES = {
            "",
            "Node 1",
            "Interaction",
            "Node 2"
    };

    private static final String[] EDGES_WITH_PROPERTIES = {
            "",
            "Node 1",
            "Interaction",
            "Node 2",
            "Property"
    };

    private static final String[] EDGES_AND_EDGE_TYPES = {
            "",
            "Node 1",
            "Interaction",
            "Node 2",
            "Ensemble",
            "Edge",
            "No edge",  // 6
            "--> dd pl",   // 7
            "<-- dd pl",   // 8
            "---",         // 9
            "--> pd nl", // -G> pd nl 10  nl pd
            "<-- pd nl", // <G- pd nl 11
            "--> dd nl", // =G> dd nl 12  nl dd
            "<-- dd nl", // <G= dd nl 13
            "o->",
            "<-o",
            "o-o",
            "<->"
    };

    /**
     * The title of the table.
     */
    private final JLabel title = new JLabel();

    /**
     * The table.
     */
    private final JTable table = new EdgeInfoTable(new DefaultTableModel());

    /**
     * The graph.
     */
    private Graph graph;

    /**
     * <p>Constructor for EdgeTypeTable.</p>
     */
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

    /**
     * <p>update.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void update(Graph graph) {
        List<Edge> edges = graph.getEdges().stream()
                .filter(edge -> !edge.isNull())
                .collect(Collectors.toList());
        Edges.sortEdges(edges);

        DefaultTableModel tableModel = (DefaultTableModel) this.table.getModel();
        tableModel.setRowCount(0);

        if (hasEdgeProbabilities(graph)) {
            this.title.setText("Edges and Edge Type Frequencies");
            this.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            this.table.setTransferHandler(new EdgeTypeTableTransferHandler());
            tableModel.setColumnIdentifiers(EdgeTypeTable.EDGES_AND_EDGE_TYPES);

//            JTableHeader header = this.table.getTableHeader();
//            Font boldFont = new Font(header.getFont().getFontName(), Font.BOLD, 18);
//            TableCellRenderer headerRenderer = header.getDefaultRenderer();
//            header.setDefaultRenderer((tbl, value, isSelected, hasFocus, row, column) -> {
//                Component comp = headerRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
//                if (column >= 10 && column <= 13) {
//                    comp.setForeground(Color.BLUE);
//                }
//                if (column >= 12 && column <= 13) {
//                    comp.setFont(boldFont);
//                }
//
//                return comp;
//            });

            edges.forEach(edge -> {
                String[] rowData = new String[EdgeTypeTable.EDGES_AND_EDGE_TYPES.length];
                addEdgeData(edge, rowData);
                addEdgeProbabilityData(edge, rowData);

                tableModel.addRow(rowData);
            });
        } else {
            boolean addProperty = hasEdgeProperties(graph);
            String[] edgeHeaders = addProperty ? EDGES_WITH_PROPERTIES : EDGES;

            this.title.setText("Edges");
            this.table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            tableModel.setColumnIdentifiers(edgeHeaders);

            edges.forEach(edge -> {
                String[] rowData = new String[edgeHeaders.length];
                addEdgeData(edge, rowData, addProperty);

                tableModel.addRow(rowData);
            });
        }

        tableModel.fireTableDataChanged();

        this.graph = graph;
    }

    private void addEdgeProbabilityData(Edge edge, String[] rowData) {
        edge.getEdgeTypeProbabilities().stream()
                .filter(edgeTypeProb -> edgeTypeProb.getProbability() > 0)
                .forEach(edgeTypeProb -> {
                    String probValue = String.format("%.4f", edgeTypeProb.getProbability());
                    boolean nl, pd, dd;
                    switch (edgeTypeProb.getEdgeType()) {
                        case nil:
                            rowData[6] = probValue;
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
                                rowData[12] = probValue;
                            } else if (nl && pd) {
                                rowData[10] = probValue;
                            } else {
                                rowData[7] = probValue;
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
                                rowData[13] = probValue;
                            } else if (nl && pd) {
                                rowData[11] = probValue;
                            } else {
                                rowData[8] = probValue;
                            }
                            break;
                        case tt:
                            rowData[9] = probValue;
                            break;
                        case ca:
                            rowData[14] = probValue;
                            break;
                        case ac:
                            rowData[15] = probValue;
                            break;
                        case cc:
                            rowData[16] = probValue;
                            break;
                        case aa:
                            rowData[17] = probValue;
                            break;
                    }
                });

        double maxEdgeProbability = edge.getEdgeTypeProbabilities().stream()
                .filter(e -> e.getEdgeType() != EdgeTypeProbability.EdgeType.nil)
                .mapToDouble(EdgeTypeProbability::getProbability)
                .max()
                .orElse(0);
        rowData[4] = String.format("%.4f", maxEdgeProbability);
        rowData[5] = String.format("%.4f", edge.getProbability());
    }

    private void addEdgeData(Edge edge, String[] rowData, boolean addProperty) {
        String node1Name = edge.getNode1().getName();
        String node2Name = edge.getNode2().getName();

        Endpoint endpoint1 = edge.getEndpoint1();
        Endpoint endpoint2 = edge.getEndpoint2();

        // These should not be flipped.
        String endpoint1Str = switch (endpoint1) {
            case TAIL -> "-";
            case ARROW -> "<";
            case CIRCLE -> "o";
            default -> "";
        };
        String endpoint2Str = switch (endpoint2) {
            case TAIL -> "-";
            case ARROW -> ">";
            case CIRCLE -> "o";
            default -> "";
        };
        String edgeType = endpoint1Str + "-" + endpoint2Str;

        rowData[1] = node1Name;
        rowData[2] = edgeType;
        rowData[3] = node2Name;

        if (addProperty) {
            List<Edge.Property> edgeProperties = edge.getProperties();
            if (edgeProperties.isEmpty()) {
                rowData[4] = "";
            } else {
                rowData[4] = edgeProperties.stream()
                        .map(e -> e.toString())
                        .collect(Collectors.joining(", "));
            }
        }
    }

    private void addEdgeData(Edge edge, String[] rowData) {
        String node1Name = edge.getNode1().getName();
        String node2Name = edge.getNode2().getName();

        Endpoint endpoint1 = edge.getEndpoint1();
        Endpoint endpoint2 = edge.getEndpoint2();

        // These should not be flipped.
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

        rowData[1] = node1Name;
        rowData[2] = edgeType;
        rowData[3] = node2Name;
    }

    private boolean hasEdgeProbabilities(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            return !edge.getEdgeTypeProbabilities().isEmpty();
        }

        return false;
    }

    private boolean hasEdgeProperties(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (!edge.getProperties().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>Getter for the field <code>graph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return graph;
    }

    private static class StripedRowTableCellRenderer extends DefaultTableCellRenderer {

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
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                label.setBackground((row % 2 == 0) ? this.NON_STRIPE : this.STRIPE);
            }

            if (column == 0) {
                setText(Integer.toString(row + 1));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setFont(new Font("SansSerif", Font.BOLD, 12));
            }

            return label;
        }

    }

    class EdgeInfoTable extends JTable {

        private static final long serialVersionUID = -4052775309418269033L;

        public EdgeInfoTable(TableModel dm) {
            super(dm);
            initComponents();
        }

        private void initComponents() {
            setFillsViewportHeight(true);
            setDefaultRenderer(Object.class, new StripedRowTableCellRenderer());
            setOpaque(true);

            setRowSorter(new TableRowSorter<TableModel>(getModel()) {
                @Override
                public boolean isSortable(int column) {
                    return !(column == 0);
                }
            });
        }

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            // adjust each column width automatically to fit the content
            Component component = super.prepareRenderer(renderer, row, column);
            int rendererWidth = component.getPreferredSize().width;
            TableColumn tableColumn = getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(FastMath.max(rendererWidth + getIntercellSpacing().width, tableColumn.getPreferredWidth()));

            return component;
        }

        public void setValueAt(Object value, int row, int col) {
            // No op. Don't allow values in the table to be changed.
        }

    }

}

