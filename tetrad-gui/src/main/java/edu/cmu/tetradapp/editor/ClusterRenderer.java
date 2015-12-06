///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Edits which variables get assigned to which clusters.
 *
 * @author Joseph Ramsey
 */
final class ClusterRenderer extends JPanel {
    private List<String> varNames;
    private Clusters clusters;
    private int numClusters;

    /**
     * Constructs an editor to allow the user to assign variables to clusters,
     * showing a list of variables to choose from.
     */
    public ClusterRenderer(Clusters clusters, List<String> varNames) {
        if (clusters == null) {
            throw new NullPointerException();
        }

        if (varNames == null) {
            throw new NullPointerException();
        }

        this.clusters = clusters;
        this.varNames = varNames;
        this.numClusters = clusters.getNumClusters();

        setLayout(new BorderLayout());
        add(clusterDisplay(), BorderLayout.CENTER);
    }

    private Box clusterDisplay() {
        Box b = Box.createVerticalBox();
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("# Clusters = " + getNumClusters()));
        b.add(b1);

        JPanel clustersPanel = new JPanel();
        clustersPanel.setLayout(new BorderLayout());
        clustersPanel.add(getClusterBoxes(getNumClusters()),
                BorderLayout.CENTER);

        b.add(clustersPanel);

        return b;
    }

    private Box getClusterBoxes(int numClusters) {
        Box c = Box.createVerticalBox();
        c.add(Box.createVerticalStrut(5));

        Box d = Box.createHorizontalBox();
        d.add(Box.createHorizontalGlue());

        for (int cluster = 0; cluster < numClusters; cluster++) {

            //            StringTextField field = new StringTextField(
            //                    getClusters().getClusterName(_cluster),
            //                    6) {
            //                public void setValue(String getValue) {
            //                    try {
            //                        getClusters().setClusterName(_cluster, value);
            //                    }
            //                    catch (Exception e) {
            //                        e.printStackTrace();
            ////                         Reinstate.
            //                    }
            //
            //                    super.setValue(getClusters().getClusterName(_cluster));
            //                }
            //            };
            //
            //            field.setHorizontalAlignment(JTextField.CENTER);
            //            field.setMaximumSize(new Dimension(70, 20));

            Box d1 = Box.createVerticalBox();
            Box d2 = Box.createHorizontalBox();
            d2.add(Box.createHorizontalGlue());
            //            d2.add(field);
            d2.add(new JLabel(getClusters().getClusterName(cluster)));
            d2.add(Box.createHorizontalGlue());
            d1.add(d2);
            d.add(d1);

            //Box d1 = Box.createVerticalBox();
            //Box d2 = Box.createHorizontalBox();
            //d2.add(Box.createHorizontalGlue());
            //
            //String s = clusters.getClusterName(cluster);
            //if (s == null) {
            //    s = "Cluster " + (cluster + 1);
            //}
            //
            //JLabel label = new JLabel(s);
            //label.setBorder(new EmptyBorder(2, 5, 2, 5));
            //d2.add(label);
            //d2.add(Box.createHorizontalGlue());
            //d1.add(d2);
            //d.add(d1);

            List clusterNames = getClusters().getCluster(cluster);

            JList clusterList = new DragDropList(clusterNames, cluster,
                    JList.VERTICAL_WRAP);

            JScrollPane scrollPane2 = new JScrollPane(clusterList);
            scrollPane2.setPreferredSize(new Dimension(50, 275));
            scrollPane2.setMaximumSize(new Dimension(200, 275));
            d1.add(scrollPane2);
            d.add(d1);
            d.add(Box.createHorizontalGlue());
        }

        JScrollPane scroll = new JScrollPane(d);
        scroll.setPreferredSize(new Dimension(400, 300));
        c.add(scroll);

        List varsNotInCluster =
                getClusters().getVarsNotInCluster(getVarNames());
        JList l1 =
                new DragDropList(varsNotInCluster, -1, JList.HORIZONTAL_WRAP);
        l1.setBorder(null);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Variables not yet clustered:"));
        b4.add(Box.createHorizontalGlue());
        c.add(b4);

        Box b2 = Box.createHorizontalBox();
        JScrollPane scrollPane = new JScrollPane(l1);
        scrollPane.setPreferredSize(new Dimension(400, 50));
        b2.add(scrollPane);
        c.add(b2);
        return c;
    }

    private Clusters getClusters() {
        return clusters;
    }

    private List<String> getVarNames() {
        return varNames;
    }

    private int getNumClusters() {
        return numClusters;
    }

    public class DragDropList extends JList implements DropTargetListener,
            DragSourceListener, DragGestureListener {
        private List<Object> movedList;

        /**
         * This is the cluster that this particular component is representing,
         * or -1 if it's the bin for unused variable names. It's needed so that
         * dropped gadgets can cause variable names to be put into the correct
         * cluster.
         */
        private int cluster;

        public DragDropList(List items, int cluster, int orientation) {
            if (cluster < -1) {
                throw new IllegalArgumentException();
            }

            this.cluster = cluster;

            setLayoutOrientation(orientation);
            setVisibleRowCount(0);
            this.setCellRenderer(new ListCellRenderer() {
                public Component getListCellRendererComponent(JList list,
                        Object value, int index, boolean isSelected,
                        boolean cellHasFocus) {
                    Color fillColor = new Color(153, 204, 204);
                    Color selectedFillColor = new Color(255, 204, 102);

                    JLabel comp = new JLabel(" " + value + " ");
                    comp.setOpaque(true);

                    if (isSelected) {
                        comp.setForeground(Color.BLACK);
                        comp.setBackground(selectedFillColor);
                    }
                    else {
                        comp.setForeground(Color.BLACK);
                        comp.setBackground(fillColor);
                    }

                    comp.setHorizontalAlignment(SwingConstants.CENTER);
                    comp.setBorder(new CompoundBorder(
                            new MatteBorder(2, 2, 2, 2, Color.WHITE),
                            new LineBorder(Color.BLACK)));

                    return comp;
                }
            });

            // Confession: We only care about ACTION_MOVE, but on my Windows
            // laptop, if I put that, I can only select and move simple
            // ranges using the shift key. I also want to be able to select
            // and move individual disconnected items using the control key.
            // ACTION_COPY_OR_MOVE lets me do this. Don't ask me why.
            new DropTarget(this, DnDConstants.ACTION_MOVE, this, true);
            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(this,
                    DnDConstants.ACTION_MOVE, this);

            setModel(new DefaultListModel());
            for (Object item : items) {
                ((DefaultListModel) getModel()).addElement(item);
            }
        }

        public int getCluster() {
            return cluster;
        }

        public void dragGestureRecognized(DragGestureEvent dragGestureEvent) {
            if (getSelectedIndex() == -1) {
                return;
            }

            List list = getSelectedValuesList();

            if (list == null) {
                getToolkit().beep();
            }
            else {
                this.movedList = list;
                ListSelection transferable = new ListSelection(list);
                dragGestureEvent.startDrag(DragSource.DefaultMoveDrop,
                        transferable, this);
            }
        }

        public void drop(DropTargetDropEvent dropTargetDropEvent) {
            try {
                Transferable tr = dropTargetDropEvent.getTransferable();
                DataFlavor flavor = tr.getTransferDataFlavors()[0];
                List<String> list = (List<String>) tr.getTransferData(flavor);

                for (String name : list) {
                    if (getCluster() >= 0) {
                        try {
                            getClusters().addToCluster(getCluster(), name);
                            DefaultListModel model =
                                    (DefaultListModel) getModel();
                            model.addElement(name);
                            sort(model);
                            dropTargetDropEvent.dropComplete(true);
                        }
                        catch (IllegalStateException e) {
                            String s = e.getMessage();

                            if (!"".equals(s)) {
                                s = "Drop could not be completed properly.";
                            }

                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(), s);
                            dropTargetDropEvent.dropComplete(false);
                        }
                    }
                    else {
                        getClusters().removeFromClusters(name);
                        DefaultListModel model = (DefaultListModel) getModel();
                        model.addElement(name);
                        sort(model);
                        dropTargetDropEvent.dropComplete(true);
                    }
                }
            }
            catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            if (!dsde.getDropSuccess()) {
                return;
            }

            if (this.movedList != null) {
                for (Object e : this.movedList) {
                    ((DefaultListModel) getModel()).removeElement(e);
                }

                this.movedList = null;
            }
        }

        public void dragEnter(DropTargetDragEvent dtde) {
        }

        public void dragOver(DropTargetDragEvent dtde) {
        }

        public void dropActionChanged(DropTargetDragEvent dtde) {
        }

        public void dragExit(DropTargetEvent dte) {
        }

        public void dragEnter(DragSourceDragEvent dsde) {
        }

        public void dragOver(DragSourceDragEvent dsde) {
        }

        public void dropActionChanged(DragSourceDragEvent dsde) {
        }

        public void dragExit(DragSourceEvent dse) {
        }

        private void sort(DefaultListModel model) {
            Object[] elements = model.toArray();
            Arrays.sort(elements);

            model.clear();

            for (Object element : elements) {
                model.addElement(element);
            }
        }
    }

    public static class ListSelection implements Transferable {

        /**
         * The list of graph nodes that constitutes the selection.
         */
        private List<Object> list;

        /**
         * Supported dataflavors--only one.
         */
        private DataFlavor[] dataFlavors = new DataFlavor[]{
                new DataFlavor(ListSelection.class, "String List Selection")};

        /**
         * Constructs a new selection with the given list of graph nodes.
         */
        public ListSelection(List<Object> list) {
            if (list == null) {
                throw new NullPointerException(
                        "List of list must " + "not be null.");
            }

            this.list = list;
        }

        /**
         * @return an object which represents the data to be transferred.  The
         * class of the object returned is defined by the representation class
         * of the flavor.
         *
         * @param flavor the requested flavor for the data
         * @throws java.io.IOException if the data is no longer available in the
         *                             requested flavor.
         * @throws java.awt.datatransfer.UnsupportedFlavorException
         *                             if the requested data flavor is not
         *                             supported.
         * @see java.awt.datatransfer.DataFlavor#getRepresentationClass
         */
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }

            return list;
        }

        /**
         * Returns whether or not the specified data flavor is supported for
         * this object.
         *
         * @param flavor the requested flavor for the data
         * @return boolean indicating whether or not the data flavor is
         *         supported
         */
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(getTransferDataFlavors()[0]);
        }

        /**
         * Returns an array of DataFlavor objects indicating the flavors the
         * data can be provided in.  The array should be ordered according to
         * preference for providing the data (from most richly descriptive to
         * least descriptive).
         *
         * @return an array of data flavors in which this data can be
         *         transferred
         */
        public DataFlavor[] getTransferDataFlavors() {
            return this.dataFlavors;
        }
    }
}




