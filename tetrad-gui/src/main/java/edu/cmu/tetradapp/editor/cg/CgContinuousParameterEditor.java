/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.pitt.dbmi.cg.CgIm;

/**
 * Jul 15, 2019 4:33:04 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgContinuousParameterEditor extends JPanel {

	private static final long serialVersionUID = 1L;

	private CgIm cgIm;
	private Node node;
	
	private JPanel parameterPanel;
	
	public CgContinuousParameterEditor(CgIm cgIm, Node node) {
		if (cgIm == null) {
            throw new NullPointerException();
        }

        if (node == null) {
            throw new NullPointerException();
        }

        this.cgIm = cgIm;
        this.node = node;
        
        setLayout(new BorderLayout());

        createEditor();
		
	}

	private void createEditor() {
		Box b1 = Box.createVerticalBox();
		
		Model model = new Model(node, cgIm);
		
		final JTable discreteParentTable = new JTable(model);
		discreteParentTable.getTableHeader().setReorderingAllowed(false);
		discreteParentTable.getTableHeader().setResizingAllowed(true);
		discreteParentTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		discreteParentTable.setCellSelectionEnabled(false);
		discreteParentTable.setRowSelectionAllowed(true);
		
		ListSelectionModel rowSelectionModel = discreteParentTable.getSelectionModel();
		rowSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel m = (ListSelectionModel) (e.getSource());
                int row = m.getAnchorSelectionIndex();
                if (row == -1) {
                    return;
                }
                discreteParentTable.setRowSelectionInterval(row, row);
                setConditionalCase(row);
            }
        });
		
		JScrollPane scroll = new JScrollPane(discreteParentTable);
        scroll.setPreferredSize(new Dimension(0, 100));
		
        b1.add(scroll);
        b1.add(Box.createVerticalStrut(10));
        
        add(b1, BorderLayout.NORTH);
        
        parameterPanel = new JPanel();
        
        add(parameterPanel, BorderLayout.CENTER);
        
		discreteParentTable.setRowSelectionInterval(0, 0);
	}
	
	private void setConditionalCase(final int rowIndex) {
		parameterPanel.removeAll();
		
		parameterPanel.setLayout(new BorderLayout());
		
		Box b1 = Box.createVerticalBox();
		
		int nodeIndex = cgIm.getCgContinuousNodeIndex(node);
		
		int contParentNum = cgIm.getCgContinuousNodeNumContinuousParents(nodeIndex);
		
		for(int i=0;i<=contParentNum;i++) {
			Box meanBox = createMeanParameterBox(rowIndex, i);
			b1.add(meanBox);
			b1.add(Box.createVerticalStrut(10));
		}
		
		for(int i=0;i<=contParentNum;i++) {
			Box stdDevBox = createStdDevParameterBox(rowIndex, i);
			b1.add(stdDevBox);
			b1.add(Box.createVerticalStrut(10));
		}
		
		for(int i=1;i<=contParentNum;i++) {
			Box coefBox = createCoefParameterBox(rowIndex, i);
			b1.add(coefBox);
			b1.add(Box.createVerticalStrut(10));
		}
		
		parameterPanel.add(b1, BorderLayout.CENTER);
		
		revalidate();
        repaint();
        firePropertyChange("modelChanged", null, null);
	}
	
	private Box createCoefParameterBox(int rowIndex, int continuousParentIndex) {
		int length = 8;
    	
    	Box b1 = Box.createVerticalBox();
    	
    	int nodeIndex = cgIm.getCgContinuousNodeIndex(this.node);
    	
    	int[] contParentArray = cgIm.getCgContinuousNodeContinuousParentNodeArray(nodeIndex);
    	
    	Node pNode = this.node;
    	if (continuousParentIndex > 0) {
    		int _pIndex = contParentArray[continuousParentIndex - 1];
    		pNode = cgIm.getCgContinuousNodeContinuousParentNode(_pIndex);
    	}
    	
    	Box stdDevBox = Box.createHorizontalBox();
    	stdDevBox.add(new JLabel((continuousParentIndex > 0?"Parent ":"") + "Linear Coefficient for " + pNode + " --> " + this.node));
    	stdDevBox.add(Box.createHorizontalGlue());
    	
    	double coefValue = cgIm.getCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, continuousParentIndex);
    	
    	final DoubleTextField coefValueField = new DoubleTextField(coefValue, length, 
        		NumberFormatUtil.getInstance().getNumberFormat());
    	coefValueField.setFilter(new DoubleTextField.Filter() {
			
			public double filter(double value, double oldValue) {
				try {
					double newValue = new Double(value);
					
					cgIm.setCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, continuousParentIndex, newValue);
					
					firePropertyChange("modelChanged", null, null);
					
					return value;
				}catch (Exception e) {
					return oldValue;
				}
			}
    	});
    	
    	stdDevBox.add(coefValueField);
    	stdDevBox.setBorder(BorderFactory.createLineBorder(Color.black));

    	b1.add(stdDevBox);
    	
    	return b1;
	}
	
	private Box createStdDevParameterBox(int rowIndex, int continuousParentIndex) {
		int length = 8;
    	
    	Box b1 = Box.createVerticalBox();
    	
    	int nodeIndex = cgIm.getCgContinuousNodeIndex(this.node);
    	
    	int[] contParentArray = cgIm.getCgContinuousNodeContinuousParentNodeArray(nodeIndex);
    	
    	Node node = this.node;
    	if (continuousParentIndex > 0) {
    		int _pIndex = contParentArray[continuousParentIndex - 1];
    		node = cgIm.getCgContinuousNodeContinuousParentNode(_pIndex);
    	}
    	
    	Box stdDevBox = Box.createHorizontalBox();
    	stdDevBox.add(new JLabel((continuousParentIndex > 0?"Parent ":"") + "Standard Deviation : " + node));
    	stdDevBox.add(Box.createHorizontalGlue());
    	
    	double stdDevValue = cgIm.getCgContinuousNodeContinuousParentMeanStdDev(nodeIndex, rowIndex, continuousParentIndex);
    	
    	final DoubleTextField stdDevValueField = new DoubleTextField(stdDevValue, length, 
        		NumberFormatUtil.getInstance().getNumberFormat());
    	stdDevValueField.setFilter(new DoubleTextField.Filter() {
			
			public double filter(double value, double oldValue) {
				try {
					double newValue = new Double(value);
					
					cgIm.setCgContinuousNodeContinuousParentMeanStdDev(nodeIndex, rowIndex, continuousParentIndex, newValue);
					
					firePropertyChange("modelChanged", null, null);
					
					return value;
				}catch (Exception e) {
					return oldValue;
				}
			}
    	});
    	
    	stdDevBox.add(stdDevValueField);
    	stdDevBox.setBorder(BorderFactory.createLineBorder(Color.black));

    	b1.add(stdDevBox);
    	
    	return b1;
	}
	
	private Box createMeanParameterBox(int rowIndex, int continuousParentIndex) {
		int length = 8;
    	
    	Box b1 = Box.createVerticalBox();
    	
    	int nodeIndex = cgIm.getCgContinuousNodeIndex(this.node);
    	
    	int[] contParentArray = cgIm.getCgContinuousNodeContinuousParentNodeArray(nodeIndex);
    	
    	Node node = this.node;
    	if (continuousParentIndex > 0) {
    		int _pIndex = contParentArray[continuousParentIndex - 1];
    		node = cgIm.getCgContinuousNodeContinuousParentNode(_pIndex);
    	}
    	
    	Box meanBox = Box.createHorizontalBox();
    	meanBox.add(new JLabel((continuousParentIndex > 0?"Parent ":"") + "Variable Mean: " + node));
    	meanBox.add(Box.createHorizontalGlue());

    	double meanValue = cgIm.getCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, continuousParentIndex);
    	
    	final DoubleTextField meanValueField = new DoubleTextField(meanValue, length, 
        		NumberFormatUtil.getInstance().getNumberFormat());
    	meanValueField.setFilter(new DoubleTextField.Filter() {
			
			public double filter(double value, double oldValue) {
				try {
					double newValue = new Double(value);
					
					cgIm.setCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, continuousParentIndex, newValue);
					
					firePropertyChange("modelChanged", null, null);
					
					return value;
				}catch (Exception e) {
					return oldValue;
				}
			}
    	});
    	
    	meanBox.add(meanValueField);
    	meanBox.setBorder(BorderFactory.createLineBorder(Color.black));

    	b1.add(meanBox);

		return b1;
	}
	
	private static final class Model extends AbstractTableModel {

		private CgIm cgIm;
		
		private int nodeIndex;
		
		public Model(Node node, CgIm cgIm) {
			if (cgIm == null) {
	            throw new NullPointerException();
	        }

	        if (node == null) {
	            throw new NullPointerException();
	        }

	        this.cgIm = cgIm;
	        this.nodeIndex = cgIm.getCgContinuousNodeIndex(node);
		}
		
		public String getColumnName(int columnIndex) {
			Node discreteParentNode = cgIm.getCgContinuousNodeDiscreteParentNode(columnIndex);
			
			return discreteParentNode.getName();
		}
		
		
		@Override
		public int getRowCount() {
			return cgIm.getCgContinuousNumRows(nodeIndex);
		}

		@Override
		public int getColumnCount() {
			return cgIm.getCgContinuousNodeNumDiscreteParents(nodeIndex);
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			int[] discretParentVals = cgIm.getCgContinuousNodeDiscreteParentValues(nodeIndex, rowIndex);
			int[] discreteParentArray = cgIm.getCgContinuousNodeDiscreteParentNodeArray(nodeIndex);
			
			Node discreteParentNode = cgIm.getCgContinuousNodeDiscreteParentNode(discreteParentArray[columnIndex]);
			
			if(discreteParentNode == null) {
				return "null";
			}
			try {
				return cgIm.getCgPm().getDiscreteCategory(discreteParentNode, discretParentVals[columnIndex]);
			}catch(Exception e) {
				return "Error";
			}
		}
		
		public boolean isCellEditable(int row, int col) {
			return false;
		}
		
	}
}
