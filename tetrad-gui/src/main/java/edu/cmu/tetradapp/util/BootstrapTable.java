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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
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
        JTable table = new JTable(){
            @Override
            // The table will adjust each column width automatically to fit the content
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                int rendererWidth = component.getPreferredSize().width;
                TableColumn tableColumn = getColumnModel().getColumn(column);
                tableColumn.setPreferredWidth(Math.max(rendererWidth + getIntercellSpacing().width, tableColumn.getPreferredWidth()));
                return component;
             }
        };
        
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    
        // To be able to see the header, we need to put the table in a JScrollPane
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        DefaultTableModel tableModel = new DefaultTableModel();

        // Set model into the table object
        table.setModel(tableModel);
        
        // Sorting, enable sorting on all columns except the edge type column (index = 1)
        // and the additional info column (index = 12)
        RowSorter<TableModel> sorter = new TableRowSorter<TableModel>(tableModel) {
            @Override
            public boolean isSortable(int column) {
                return (column != 1 && column != 12);
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
        columnNames.add(7, "-->"); // -G> pd nl
        columnNames.add(8, "<--"); // <G- pd nl
        columnNames.add(9, "-->"); // =G> dd nl
        columnNames.add(10, "<--"); // <G= dd nl
        columnNames.add(11, "o->");
        columnNames.add(12, "<-o");
        columnNames.add(13, "o-o");
        columnNames.add(14, "<->");
        columnNames.add(15, "---");
        
        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());
        
        // Set custom header renderer
        final JTableHeader header = table.getTableHeader();
        final Font boldFont = new Font(header.getFont().getFontName(), Font.BOLD, 18);
        final TableCellRenderer headerRenderer = header.getDefaultRenderer();
        header.setDefaultRenderer(new TableCellRenderer() {
			
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				Component comp = headerRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if(column > 6 && column < 11) {
					comp.setForeground(Color.GREEN);
				}
				if(column > 8 && column < 11) {
					comp.setFont(boldFont);
				}
				return comp;
			}
		});

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

        return tablePane;
    }
    
    // Add a new row to bootstrap table
    private static void addRow(DefaultTableModel tableModel, String node1, String node2, String edgeType, List<Edge.Property> properties, List<EdgeTypeProbability> edgeTypeProbabilities) {
        String[] row = new String[tableModel.getColumnCount()];
        
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
        double maxProb = -1;
        String maxProbString = "";
        for (EdgeTypeProbability edgeTypeProb : edgeTypeProbabilities) {
            double prob = edgeTypeProb.getProbability();
            if(prob == 0.0) {
            	continue;
            }
            String probValue = String.format("%.4f", prob);
            
            // FInd the max value of edge type probability
            if(prob > maxProb) {
            	maxProb = prob;
            	maxProbString = probValue;
            }
   
            // Handle edge types with additional properties here
            switch (edgeTypeProb.getEdgeType()) {
                case nil: //"no edge"
                    row[4] = probValue;
                    break;
                case ta:
                	if(edgeTypeProb.getProperties().isEmpty()) {
                        row[5] = probValue;               		
                	}else {
                		boolean _pd = false;
                		boolean _dd = false;
                		boolean _nl = false;
                		for (Edge.Property property : edgeTypeProb.getProperties()) {
                            if(property == Edge.Property.pd) {
                            	_pd = true;
                            }
                            if(property == Edge.Property.dd) {
                            	_dd = true;
                            }
                            if(property == Edge.Property.nl) {
                            	_nl = true;
                            }
                        }
                		if(_pd && _nl) {
                			// row 7: pd nl
                			row[7] = probValue;
                		}else if(_dd && _nl) {
                			// row 9: dd nl
                			row[9] = probValue;
                		}else {
                			row[5] = probValue;   
                		}
                	}
                    break;
                case at:
                	if(edgeTypeProb.getProperties().isEmpty()) {
                        row[6] = probValue;
                	}else {
                		boolean _pd = false;
                		boolean _dd = false;
                		boolean _nl = false;
                		for (Edge.Property property : edgeTypeProb.getProperties()) {
                            if(property == Edge.Property.pd) {
                            	_pd = true;
                            }
                            if(property == Edge.Property.dd) {
                            	_dd = true;
                            }
                            if(property == Edge.Property.nl) {
                            	_nl = true;
                            }
                        }
                		if(_pd && _nl) {
                			// row 8: pd nl
                			row[8] = probValue;
                		}else if(_dd && _nl) {
                			// row 10: dd nl
                			row[10] = probValue;
                		}else {
                			row[6] = probValue;   
                		}
                	}
                    break;
                case ca:
                    row[11] = probValue;
                    break;
                case ac:
                    row[12] = probValue;
                    break;
                case cc:
                    row[13] = probValue;
                    break;
                case aa:
                    row[14] = probValue;
                    break;
                case tt:
                    row[15] = probValue;
                    break;
                default:
                	System.out.println("No filling edgeType: " + edgeType);
                    break;
            }

            // Additional info
            // Put all edge type probablities with some of properties (nl, dd, pl, pd) here
            /*if (!edgeTypeProb.getProperties().isEmpty()) {
                type = node1 + " " + type + " " + node2;
                for (Edge.Property property : edgeTypeProb.getProperties()) {
                    type = type + " " + property.name();
                }

                String additionalInfo = "[" + type + "]: " + probValue;
                
                if(row[12] != null) {
                    row[12] = row[12] +  "; " + additionalInfo;
                } else {
                    row[12] = additionalInfo;
                }
 
            }*/

        }
        
        // Use the max probability value for Ensemble
        row[3] = maxProbString;

        tableModel.addRow(row);
    }
}
