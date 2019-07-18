/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import edu.cmu.tetrad.graph.Node;
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
    private int lastX;
    private int lastY;

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

        setFocusRow(0);
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
					
					return mean;
				} else {
					double sd = cgIm.getCgDiscreteNodeContinuousParentMeanStdDev(
							nodeIndex, rowIndex, colIndex, continuousParentIndex);
					
					return sd;
				}
			} else {
				int valIndex = columnIndex - numDiscreteParents - 2*numContinuousParents;
				
				if (valIndex == 0) {
					Node node = cgIm.getCgDiscreteNode(nodeIndex);
					
					return cgIm.getCgPm().getDiscreteCategory(node, colIndex);
				}
				
				double prob = cgIm.getCgDiscreteNodeProbability(nodeIndex, rowIndex, colIndex);
				
				return prob;
				
			}
		}
		
		public boolean isCellEditable(int row, int col) {
			return false;
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
