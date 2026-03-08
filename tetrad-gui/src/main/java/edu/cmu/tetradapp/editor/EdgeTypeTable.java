package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TMath;

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
            "No edge",
            "--> dd pl",
            "<-- dd pl",
            "---",
            "--> pd nl",
            "<-- pd nl",
            "--> dd nl",
            "<-- dd nl",
            "o->",
            "<-o",
            "o-o",
            "<->"
    };

    private final JLabel title = new JLabel();
    private final JTable table = new EdgeInfoTable(new DefaultTableModel());
    private Graph graph;

    public EdgeTypeTable() {
        initComponents();
    }

    private void initComponents() {
        this.title.setHorizontalAlignment(SwingConstants.CENTER);
        this.title.setVerticalAlignment(SwingConstants.CENTER);

        setLayout(new BorderLayout(0, 10));
        add(this.title, BorderLayout.NORTH);
        add(new JScrollPane(this.table), BorderLayout.CENTER);

        refreshTheme();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshTheme();
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Font uiFont(String key, Font fallback) {
        Font f = UIManager.getFont(key);
        return f != null ? f : fallback;
    }

    private static boolean isDarkMode() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        return laf != null && laf.getName().toLowerCase().contains("dark");
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

    private void refreshTheme() {
        setBackground(uiColor("Panel.background", getBackground()));
        setForeground(uiColor("Label.foreground", getForeground()));

        if (title != null) {
            title.setForeground(uiColor("Label.foreground", Color.BLACK));
            title.setFont(uiFont("Label.font", title.getFont()));
        }

        if (table != null) {
            table.setBackground(uiColor("Table.background", Color.WHITE));
            table.setForeground(uiColor("Table.foreground", Color.BLACK));
            table.setSelectionBackground(uiColor("Table.selectionBackground", table.getSelectionBackground()));
            table.setSelectionForeground(uiColor("Table.selectionForeground", table.getSelectionForeground()));
            table.setGridColor(uiColor("Table.gridColor", table.getGridColor()));
            table.setFont(uiFont("Table.font", table.getFont()));
            table.setShowGrid(true);

            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setBackground(uiColor("TableHeader.background", header.getBackground()));
                header.setForeground(uiColor("TableHeader.foreground", header.getForeground()));
                header.setFont(uiFont("TableHeader.font", header.getFont()));
            }
        }

        repaint();
    }

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
            this.table.setTransferHandler(new DefaultTableTransferHandler(1));
            tableModel.setColumnIdentifiers(EdgeTypeTable.EDGES_AND_EDGE_TYPES);

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
                                if (p == Edge.Property.dd) dd = true;
                                if (p == Edge.Property.nl) nl = true;
                                if (p == Edge.Property.pd) pd = true;
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
                                if (p == Edge.Property.dd) dd = true;
                                if (p == Edge.Property.nl) nl = true;
                                if (p == Edge.Property.pd) pd = true;
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
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
            }
        }
    }

    private void addEdgeData(Edge edge, String[] rowData) {
        String node1Name = edge.getNode1().getName();
        String node2Name = edge.getNode2().getName();

        Endpoint endpoint1 = edge.getEndpoint1();
        Endpoint endpoint2 = edge.getEndpoint2();

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

    public Graph getGraph() {
        return graph;
    }

    private static class StripedRowTableCellRenderer extends DefaultTableCellRenderer {

        @Serial
        private static final long serialVersionUID = 4603884548966502824L;

        private static Color uiColor(String key, Color fallback) {
            Color c = UIManager.getColor(key);
            return c != null ? c : fallback;
        }

        private static boolean isDarkMode() {
            LookAndFeel laf = UIManager.getLookAndFeel();
            return laf != null && laf.getName().toLowerCase().contains("dark");
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

        private static Color getNonStripe() {
            return uiColor("Table.background", isDarkMode() ? new Color(43, 45, 48) : Color.WHITE);
        }

        private static Color getStripe() {
            Color base = getNonStripe();
            return isDarkMode() ? blend(base, Color.WHITE, 0.04) : blend(base, new Color(220, 235, 255), 0.55);
        }

        public StripedRowTableCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (isSelected) {
                label.setBackground(table.getSelectionBackground());
                label.setForeground(table.getSelectionForeground());
            } else {
                label.setBackground((row % 2 == 0) ? getNonStripe() : getStripe());
                label.setForeground(table.getForeground());
            }

            if (column == 0) {
                label.setText(Integer.toString(row + 1));
                label.setHorizontalAlignment(SwingConstants.CENTER);

                Font base = table.getFont();
                label.setFont(base.deriveFont(Font.BOLD));
            } else {
                label.setHorizontalAlignment(SwingConstants.LEADING);
                label.setFont(table.getFont());
            }

            return label;
        }
    }

    static class EdgeInfoTable extends JTable {

        @Serial
        private static final long serialVersionUID = -4052775309418269033L;

        public EdgeInfoTable(TableModel dm) {
            super(dm);
            initComponents();
        }

        private void initComponents() {
            setFillsViewportHeight(true);
            setDefaultRenderer(Object.class, new StripedRowTableCellRenderer());
            setOpaque(true);

            setBackground(UIManager.getColor("Table.background"));
            setForeground(UIManager.getColor("Table.foreground"));
            setSelectionBackground(UIManager.getColor("Table.selectionBackground"));
            setSelectionForeground(UIManager.getColor("Table.selectionForeground"));
            setGridColor(UIManager.getColor("Table.gridColor"));

            setRowSorter(new TableRowSorter<TableModel>(getModel()) {
                @Override
                public boolean isSortable(int column) {
                    return column != 0;
                }
            });
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (getModel() != null) {
                setDefaultRenderer(Object.class, new StripedRowTableCellRenderer());
            }

            Color bg = UIManager.getColor("Table.background");
            Color fg = UIManager.getColor("Table.foreground");
            Color sbg = UIManager.getColor("Table.selectionBackground");
            Color sfg = UIManager.getColor("Table.selectionForeground");
            Color grid = UIManager.getColor("Table.gridColor");

            if (bg != null) setBackground(bg);
            if (fg != null) setForeground(fg);
            if (sbg != null) setSelectionBackground(sbg);
            if (sfg != null) setSelectionForeground(sfg);
            if (grid != null) setGridColor(grid);

            Font tf = UIManager.getFont("Table.font");
            if (tf != null) setFont(tf);

            JTableHeader header = getTableHeader();
            if (header != null) {
                Color hbg = UIManager.getColor("TableHeader.background");
                Color hfg = UIManager.getColor("TableHeader.foreground");
                Font hf = UIManager.getFont("TableHeader.font");
                if (hbg != null) header.setBackground(hbg);
                if (hfg != null) header.setForeground(hfg);
                if (hf != null) header.setFont(hf);
            }
        }

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            int rendererWidth = component.getPreferredSize().width;
            TableColumn tableColumn = getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(TMath.max(rendererWidth + getIntercellSpacing().width, tableColumn.getPreferredWidth()));
            return component;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            // No op. Don't allow values in the table to be changed.
        }
    }
}