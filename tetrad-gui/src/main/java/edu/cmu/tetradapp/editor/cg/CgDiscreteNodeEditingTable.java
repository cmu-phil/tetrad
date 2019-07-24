/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.FontMetrics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.editor.NumberCellEditor;
import edu.cmu.tetradapp.editor.NumberCellRenderer;
import edu.pitt.dbmi.cg.CgIm;

/**
 * Jul 17, 2019 4:45:05 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgDiscreteNodeEditingTable extends JTable {

	private static final long serialVersionUID = 1L;

	private int focusRow = 0;
    private int focusCol = 0;

    /**
     * Constructs a new editing table from a given editing table model.
     */
    public CgDiscreteNodeEditingTable(CgIm cgIm, Node node) {
		if (cgIm == null) {
            throw new NullPointerException();
        }

        if (node == null) {
            throw new NullPointerException();
        }

    	Model model = new Model(node, cgIm, this);
    	model.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
        setModel(model);
        
        setDefaultEditor(Number.class, new NumberCellEditor());
        setDefaultRenderer(Number.class, new NumberCellRenderer());
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);

        ListSelectionModel rowSelectionModel = getSelectionModel();

        rowSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel m = (ListSelectionModel) (e.getSource());
                setFocusRow(m.getAnchorSelectionIndex());
            }

        });

        ListSelectionModel columnSelectionModel = getColumnModel()
                .getSelectionModel();
        
        columnSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel m =
                        (ListSelectionModel) (e.getSource());
                setFocusColumn(m.getAnchorSelectionIndex());
            }
        });
        
        setFocusRow(0);
        setFocusColumn(0);
    }
    
    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        if (getModel() instanceof Model) {
            FontMetrics fontMetrics = getFontMetrics(getFont());
            Model model = (Model) getModel();

            for (int i = 0; i < model.getColumnCount(); i++) {
                TableColumn column = getColumnModel().getColumn(i);
                String columnName = model.getColumnName(i);
                int currentWidth = column.getPreferredWidth();

                if (columnName != null) {
                    int minimumWidth = fontMetrics.stringWidth(columnName) + 8;

                    if (minimumWidth > currentWidth) {
                        column.setPreferredWidth(minimumWidth);
                    }
                }
            }
        }
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
	private void setFocusRow(int row) {
		if (row == -1) {
            return;
        }

        Model editingTableModel = (Model) getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        this.focusRow = row;

        if (this.focusCol < getRowCount()) {
            setRowSelectionInterval(focusRow, focusRow);
            editCellAt(focusRow, focusCol);
        }
		
	}

    /**
     * Sets the focus column to the anchor column currently being selected.
     */
    private void setFocusColumn(int col) {
        Model editingTableModel = (Model) getModel();
        int failedCol = editingTableModel.getFailedCol();

        if (failedCol != -1) {
            col = failedCol;
            editingTableModel.resetFailedCol();
        }
    	
        int numColumns = getColumnCount();
        
        this.focusCol = numColumns - 1;
        
        setColumnSelectionInterval(focusCol, focusCol);
        editCellAt(focusRow, focusCol);
    }

	private static final class Model extends AbstractTableModel {
    	
    	private CgIm cgIm;
    	
        /**
         * This table can only display conditional probabilities for one node at
         * at time. This is the node.
         */
        private int nodeIndex;

        /**
         * The messageAnchor that takes the user through the process of editing
         * the probability tables.
         */
        private JComponent messageAnchor;

        private int failedRow = -1;
        private int failedCol = -1;
        private PropertyChangeSupport pcs;

        /**
         * Constructs a new editing table model for a given a node in a given
         * bayesIm.
         */
        public Model(Node node, CgIm cgIm, JComponent messageAnchor) {
        	if (cgIm == null) {
	            throw new NullPointerException();
	        }

	        if (node == null) {
	            throw new NullPointerException();
	        }

	        this.cgIm = cgIm;
	        this.nodeIndex = cgIm.getCgDiscreteNodeIndex(node);
	        this.messageAnchor = messageAnchor;
        }

		public String getColumnName(int columnIndex) {
			int numDiscreteParents = cgIm.getCgDiscreteNodeNumDiscreteParents(nodeIndex);
			int numContinuousParents = cgIm.getCgDiscreteNodeNumContinuousParents(nodeIndex);
			
			if(columnIndex < numDiscreteParents) {
				Node discreteParentNode = cgIm.getCgDiscreteNodeDiscreteParentNode(columnIndex);
				
				return discreteParentNode.getName();
			} else if (columnIndex < numDiscreteParents + 2*numContinuousParents) {
				int continuousParentIndex = columnIndex - numDiscreteParents;
				boolean meanContParent = continuousParentIndex % 2 == 0? true : false;
				
				continuousParentIndex = continuousParentIndex / 2;
				
				int[] continuousParentArray = cgIm.getCgDiscreteNodeContinuousParentNodeArray(nodeIndex);
				
				Node continuousParentNode = cgIm.getCgDiscreteNodeContinuousParentNode(continuousParentArray[continuousParentIndex]);
				if (meanContParent) {
					return "μ(" + continuousParentNode + ")";
				} else {
					return "sd(" + continuousParentNode + ")";
				}
			} else {
				int valIndex = columnIndex - numDiscreteParents - 2*numContinuousParents;
				
				Node node = cgIm.getCgDiscreteNode(nodeIndex);
				
				if (valIndex == 0) {
					return node.getName();
				}
				
				return "Probability";
			}
		}
		
		
		@Override
		public int getRowCount() {
			return cgIm.getCgDiscreteNumRows(nodeIndex) * cgIm.getCgDiscreteNumColumns(nodeIndex);
		}

		@Override
		public int getColumnCount() {
			int numDiscreteParents = cgIm.getCgDiscreteNodeNumDiscreteParents(nodeIndex);
			int numContinuousParents = cgIm.getCgDiscreteNodeNumContinuousParents(nodeIndex);
			int numColumns = 2;
			return numDiscreteParents + 2*numContinuousParents + numColumns;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
			int colIndex = rowIndex % numCategories;
			int numDiscreteParents = cgIm.getCgDiscreteNodeNumDiscreteParents(nodeIndex);
			int numContinuousParents = cgIm.getCgDiscreteNodeNumContinuousParents(nodeIndex);
			
			rowIndex = rowIndex / numCategories;
			
			DecimalFormat decimalFormat = new DecimalFormat("0.0###");
			
			if(columnIndex < numDiscreteParents) {
				int[] discretParentVals = cgIm.getCgDiscreteNodeDiscreteParentValues(nodeIndex, rowIndex);
				int[] discreteParentArray = cgIm.getCgDiscreteNodeDiscreteParentNodeArray(nodeIndex);
				
				Node discreteParentNode = cgIm.getCgDiscreteNodeDiscreteParentNode(discreteParentArray[columnIndex]);
				
				if(discreteParentNode == null) {
					return "null";
				}
				try {
					return cgIm.getCgPm().getDiscreteCategory(discreteParentNode, discretParentVals[columnIndex]);
				}catch(Exception e) {
					return "Error";
				}
			} else if (columnIndex < numDiscreteParents + 2*numContinuousParents) {
				int continuousParentIndex = columnIndex - numDiscreteParents;
				boolean meanContParent = continuousParentIndex % 2 == 0? true : false;
				
				continuousParentIndex = continuousParentIndex / 2;
				
				if (meanContParent) {
					double mean = cgIm.getCgDiscreteNodeContinuousParentMean(
							nodeIndex, rowIndex, colIndex, continuousParentIndex);
					
					return decimalFormat.format(mean);
				} else {
					double sd = cgIm.getCgDiscreteNodeContinuousParentMeanStdDev(
							nodeIndex, rowIndex, colIndex, continuousParentIndex);
					
					return decimalFormat.format(sd);
				}
			} else {
				int valIndex = columnIndex - numDiscreteParents - 2*numContinuousParents;
				
				if (valIndex == 0) {
					Node node = cgIm.getCgDiscreteNode(nodeIndex);
					
					return cgIm.getCgPm().getDiscreteCategory(node, colIndex);
				}
				
				double prob = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, colIndex);
				
				return decimalFormat.format(prob);
				
			}
		}
		
        /**
         * Sets the value of the cell at (rowIndex, columnIndex) to 'aValue'.
         */
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        	int currentRowIndex = rowIndex;
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
			int colIndex = rowIndex % numCategories;
			
			rowIndex = rowIndex / numCategories;
			
			if ("".equals(aValue) || aValue == null) {
				cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, colIndex, Double.NaN);
				fireTableRowsUpdated(currentRowIndex, currentRowIndex);
				getPcs().firePropertyChange("modelChanged", null, null);
                return;
			}
        	
			try {
				NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                double probability = Double.parseDouble((String) aValue);
                double sumInColumn = sumInColumn(rowIndex, columnIndex) + probability;
                
                double oldProbability = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, colIndex);
                
                if (!Double.isNaN(oldProbability)) {
                    oldProbability = Double.parseDouble(nf.format(oldProbability));
                }

                if (Math.abs(probability - oldProbability) <= .00005) {
                    return;
                }
                
                if (probabilityOutOfRange(probability)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Probabilities must be in range [0.0, 1.0].");
                    failedRow = currentRowIndex;
                    failedCol = getColumnCount() - 1;
                } else if (numNanRows(rowIndex) == 0) {
                	if (sumInColumn < 0.99995 || sumInColumn > 1.00005) {
                		emptyColumns(rowIndex);
                		cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, colIndex, probability);
                		if (numCategories == 2) {
                			fillInSingleRemainingRow(rowIndex);
                		}
                		fireTableRowsUpdated(currentRowIndex, currentRowIndex);
                        getPcs().firePropertyChange("modelChanged", null,
                                null);
                	}
                } else if (sumInColumn > 1.00005) {
                	JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Sum of probabilities in row must not exceed 1.0.");
                	failedRow = currentRowIndex;
                    failedCol = getColumnCount() - 1;
                } else {
                	cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, colIndex, probability);
                	fillInSingleRemainingRow(rowIndex);
                	fillInZerosIfSumIsOne(rowIndex);
                	fireTableRowsUpdated(currentRowIndex, currentRowIndex);
                    getPcs().firePropertyChange("modelChanged", null,
                            null);
                }
                
			} catch(Exception e) {
				e.printStackTrace();
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                failedRow = currentRowIndex;
                failedCol = getColumnCount() - 1;
			}
        }
		
        private boolean probabilityOutOfRange(double value) {
            return value < 0.0 || value > 1.0;
        }
        
        private int uniqueNanCol(int rowIndex) {
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
        	
        	for(int i=0;i < numCategories;i++) {
        		double probability = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, i);
        		
        		if(Double.isNaN(probability)) {
        			return i;
        		}
        	}
        	
        	return -1;
        }
        
        private void fillInZerosIfSumIsOne(int rowIndex) {
        	double sum = sumInColumn(rowIndex, -1);
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
        	
        	if (sum > 0.9995 && sum < 1.0005) {
        		for(int i=0;i < numCategories;i++) {
        			double probability = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, i);
        			
        			if(Double.isNaN(probability)) {
        				cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, i, 0.0);
            		}
        		}
        	}
        }
        
        private void fillInSingleRemainingRow(int rowIndex) {
        	int leftOverColumn = uniqueNanCol(rowIndex);
        	
        	if (leftOverColumn != -1) {
                double difference = 1.0 - sumInColumn(rowIndex, leftOverColumn);
                cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, leftOverColumn, difference);
        	}
        }
        
        private void emptyColumns(int rowIndex) {
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
        	
        	for(int i=0;i < numCategories;i++) {
        		cgIm.setCgDiscreteProbability(nodeIndex, rowIndex, i, Double.NaN);
        	}
        }
        
        private int numNanRows(int rowIndex) {
        	int count = 0;
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
        	
        	for(int i=0;i < numCategories;i++) {
        		double probability = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, i);
        		
        		if(Double.isNaN(probability)) {
        			count++;
        		}
        	}
        	
        	return count;
        }

        private double sumInColumn(int rowIndex, int skipColumnIndex) {
        	double sum = 0.0;
        	int numCategories = cgIm.getCgDiscreteNumColumns(nodeIndex);
        	
        	for(int i=0;i < numCategories;i++) {
        		double probability = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, i);
        		
        		if (i != skipColumnIndex && !Double.isNaN(probability)) {
        			NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                    probability = Double.parseDouble(nf.format(probability));

                    sum += probability;
        		}
        		
        	}
        	
        	return sum;
        }
        
		public boolean isCellEditable(int row, int col) {
			return col == getColumnCount() - 1;
		}
		
        public void addPropertyChangeListener(PropertyChangeListener l) {
            getPcs().addPropertyChangeListener(l);
        }

        private PropertyChangeSupport getPcs() {
            if (this.pcs == null) {
                this.pcs = new PropertyChangeSupport(this);
            }
            return this.pcs;
        }
		
        public JComponent getMessageAnchor() {
            return messageAnchor;
        }

        public int getFailedRow() {
            return failedRow;
        }

        public int getFailedCol() {
            return failedCol;
        }

        public void resetFailedRow() {
            failedRow = -1;
        }

        public void resetFailedCol() {
            failedCol = -1;
        }
        
    }
}
