/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * Creates the shared graph bootstrap table
 * 
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public final class BootstrapTable {
    public static JScrollPane renderBootstrapTable(Graph graph) {
        // Bootstrap table view
        // Create object of table and table model
        JTable table = new JTable();
 
        DefaultTableModel tableModel = new DefaultTableModel();

        // Set model into the table object
        table.setModel(tableModel);
        
        // Sorting, enable sorting on all columns except the edge type column (index = 1)
        RowSorter<TableModel> sorter = new TableRowSorter<TableModel>(tableModel) {
            @Override
            public boolean isSortable(int column) {
                return column != 1;
            };
        };
        table.setRowSorter(sorter);

        // Headers
        List<String> columnNames = new LinkedList<>();
        
        // The very left headers: node1, edge type, node2
        columnNames.add(0, "Node1");
        columnNames.add(1, "Interaction");
        columnNames.add(2, "Node2");

        // Edge Type probabilities
        columnNames.add(3, "Ensemble");
        columnNames.add(4, "No edge");
        columnNames.add(5, "-->");
        columnNames.add(6, "<--");
        columnNames.add(7, "o->");
        columnNames.add(8, "<-o");
        columnNames.add(9, "o-o");
        columnNames.add(10, "<->");
        columnNames.add(11, "---");
        columnNames.add(12, "Additional information");
        
        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());

        // Add new row to table
        Set<Edge> edges = graph.getEdges();
        List<Edge> edgeList = new ArrayList<>(edges);
        Edges.sortEdges(edgeList);
        
        edgeList.forEach(e->{
            String edgeType = "";
            Endpoint endpoint1 = e.getEndpoint1();
            Endpoint endpoint2 = e.getEndpoint2();

            Node n1 = e.getNode1();
        	Node n2 = e.getNode2();
        	
        	if(n1.getName().compareTo(n2.getName()) > 0) {
        		Endpoint tmp = endpoint1;
        		endpoint1 = endpoint2;
        		endpoint2 = tmp;
        		
        		Node temp = n1;
        		n1 = n2;
        		n2 = temp;
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
            // Produce a string representation of the edge
            edgeType = endpoint1Str + "-" + endpoint2Str;
            
            addRow(tableModel, n1.getName(), n2.getName(), edgeType, e.getProperties(), e.getEdgeTypeProbabilities());
        });
        
        
        // To be able to see the header, we need to put the table in a JScrollPane
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        table.getParent().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                if (table.getPreferredSize().width < table.getParent().getWidth()) {
                    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                } else {
                    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                }
            }
        });
 
        return tablePane;
    }
    
    // Add a new row to bootstrap table
    private static void addRow(DefaultTableModel tableModel, String node1, String node2, String edgeType, List<Edge.Property> properties, List<EdgeTypeProbability> edgeTypeProbabilities) {
        String[] row = new String[13];
        
        // node1
        row[0] = node1;
        
        // Edge interaction type with edge properties (dd, pd, nl, pl)
        if (!properties.isEmpty()) {
            row[1] = edgeType + " (" + properties.stream().map(e->e.name()).collect(Collectors.joining(",")) + ")";
        } else {
            row[1] = edgeType;
        }

        // node2
        row[2] = node2;
        
        // Ensemble, empty by default
        row[3] = "";
        
        for (EdgeTypeProbability edgeTypeProb : edgeTypeProbabilities) {
            String type = "";
            String probValue = String.format("%.4f", edgeTypeProb.getProbability());
            List<String> additionalInfoList = new LinkedList<>();
            
            switch (edgeTypeProb.getEdgeType()) {
                case nil: //"no edge"
                    row[4] = probValue;
                    break;
                case ta: //"-->";
                    type = "-->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[5] = probValue;
                    break;
                case at: //"<--";
                    type = "<--";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[6] = probValue;
                    break;
                case ca: //"o->";
                    type = "o->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[7] = probValue;
                    break;
                case ac: //"<-o";
                    type = "<-o";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[8] = probValue;
                    break;
                case cc: //"o-o";
                    type = "o-o";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[9] = probValue;
                    break;
                case aa: //"<->";
                    type = "<->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[10] = probValue;
                    break;
                case tt: //"---";
                    type = "---";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[11] = probValue;
                    break;
                default:
                    break;
            }

            // Additional info
            if (!edgeTypeProb.getProperties().isEmpty()) {
                type = node1 + " " + type + " " + node2;
                for (Edge.Property property : edgeTypeProb.getProperties()) {
                    type = type + " " + property.name();
                }
                additionalInfoList.add("[" + type + "]: " + probValue);
                
                String additionalInfo = additionalInfoList.stream().collect(Collectors.joining("; "));
                row[12] = additionalInfo;
            }

        }

        tableModel.addRow(row);
    }
}
