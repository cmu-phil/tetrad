///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetradapp.model.MeasurementModelWrapper;
import org.apache.commons.math3.util.FastMath;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edits which variables get assigned to which clusters.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ClusterEditor extends JPanel {

    /**
     * The list of variable names.
     */
    private final List<String> varNames;

    /**
     * The clusters.
     */
    private final Clusters clusters;

    /**
     * The panel that holds the clusters.
     */
    private JPanel clustersPanel;

    /**
     * The list of name fields.
     */
    private ArrayList nameFields;

    /**
     * Constructs an editor to allow the user to assign variables to clusters, showing a list of variables to choose
     * from.
     *
     * @param clusters a {@link edu.cmu.tetrad.data.Clusters} object
     * @param varNames a {@link java.util.List} object
     */
    public ClusterEditor(Clusters clusters, List<String> varNames) {
        if (clusters == null) {
            throw new NullPointerException();
        }

        if (varNames == null) {
            throw new NullPointerException();
        }

        this.clusters = clusters;
        this.varNames = varNames;

        setLayout(new BorderLayout());
        add(clusterDisplay(), BorderLayout.CENTER);

        if (clusters.getNumClusters() == 0) {
            setNumDisplayClusters(3);
            clusters.setNumClusters(3);
        }
    }

    /**
     * <p>Constructor for ClusterEditor.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.MeasurementModelWrapper} object
     */
    public ClusterEditor(MeasurementModelWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        this.clusters = wrapper.getClusters();
        this.varNames = wrapper.getVarNames();

        setLayout(new BorderLayout());
        add(clusterDisplay(), BorderLayout.CENTER);

        if (this.clusters.getNumClusters() == 0) {
            setNumDisplayClusters(3);
            this.clusters.setNumClusters(3);
        }
    }

    /**
     * <p>Getter for the field <code>clusters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Clusters} object
     */
    public Clusters getClusters() {
//        return clusters;
        return new Clusters(this.clusters);
    }

    private Box clusterDisplay() {

        Box b = Box.createVerticalBox();
        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Not in cluster:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("# Clusters = "));
        int numClusters = this.clusters.getNumClusters();
        numClusters = FastMath.max(numClusters, 3);
        SpinnerNumberModel spinnerNumberModel
                = new SpinnerNumberModel(numClusters, 3, 100, 1);
        spinnerNumberModel.addChangeListener(e -> {
            SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            int numClusters1 = model.getNumber().intValue();
            setNumDisplayClusters(numClusters1);
            ClusterEditor.this.clusters.setNumClusters(numClusters1);
        });

        JSpinner spinner = new JSpinner(spinnerNumberModel);
        spinner.setMaximumSize(spinner.getPreferredSize());
        b1.add(spinner);
        b.add(b1);

        this.clustersPanel = new JPanel();
        this.clustersPanel.setLayout(new BorderLayout());
        this.clustersPanel.add(getClusterBoxes(this.clusters.getNumClusters()),
                BorderLayout.CENTER);

        b.add(this.clustersPanel);

        Box c = Box.createHorizontalBox();
        c.add(new JLabel("Use shift key to select multiple items."));
        c.add(Box.createGlue());
        b.add(c);

        return b;
    }

    private void setNumDisplayClusters(int numClusters) {
        if (numClusters < 0) {
            int numStoredClusters = getClustersPrivate().getNumClusters();
            int n = (int) FastMath.pow(getVarNames().size(), 0.5);
            int defaultNumClusters = n + 1;
            int numClusters2 = FastMath.max(numStoredClusters, defaultNumClusters);
            this.clusters.setNumClusters(numClusters2);
        } else {
            this.clusters.setNumClusters(numClusters);
        }

        this.clustersPanel.removeAll();
        this.clustersPanel.add(getClusterBoxes(this.clusters.getNumClusters()),
                BorderLayout.CENTER);
        this.clustersPanel.revalidate();
        this.clustersPanel.repaint();
    }

    private Box getClusterBoxes(int numClusters) {
        Box c = Box.createVerticalBox();

        List varsNotInCluster
                = getClustersPrivate().getVarsNotInCluster(getVarNames());
        JList l1
                = new DragDropList(varsNotInCluster, -1, JList.HORIZONTAL_WRAP);
        l1.setBorder(null);

        Box b2 = Box.createHorizontalBox();
        JScrollPane scrollPane = new JScrollPane(l1);
        scrollPane.setPreferredSize(new Dimension(400, 50));
        b2.add(scrollPane);
        c.add(b2);

        c.add(Box.createVerticalStrut(5));

        Box d = Box.createHorizontalBox();
        d.add(Box.createHorizontalGlue());

        this.nameFields = new ArrayList();

        for (int cluster = 0; cluster < numClusters; cluster++) {
            Box d1 = Box.createVerticalBox();
            Box d2 = Box.createHorizontalBox();
            d2.add(Box.createHorizontalGlue());
            d2.add(new JLabel(getClustersPrivate().getClusterName(cluster)));
            //            d2.add(field);
            d2.add(Box.createHorizontalGlue());
            d1.add(d2);
            d.add(d1);

            List clusterNames = getClustersPrivate().getCluster(cluster);

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
        return c;
    }

    private Clusters getClustersPrivate() {
        return this.clusters;
    }

    private List<String> getVarNames() {
        return this.varNames;
    }

    /**
     * <p>Getter for the field <code>nameFields</code>.</p>
     *
     * @return a {@link java.util.ArrayList} object
     */
    public ArrayList getNameFields() {
        return this.nameFields;
    }

    /**
     * A transferable object that represents a list of graph nodes.
     */
    public static class ListSelection implements Transferable {

        /**
         * The list of graph nodes that constitutes the selection.
         */
        private final List list;

        /**
         * Supported dataflavors--only one.
         */
        private final DataFlavor[] dataFlavors = {
                new DataFlavor(ListSelection.class, "String List Selection")};

        /**
         * Constructs a new selection with the given list of graph nodes.
         *
         * @param list a {@link java.util.List} object
         */
        public ListSelection(List list) {
            if (list == null) {
                throw new NullPointerException(
                        "List of list must " + "not be null.");
            }

            this.list = list;
        }

        /**
         * @param flavor the requested flavor for the data
         * @return an object which represents the data to be transferred. The class of the object returned is defined by
         * the representation class of the flavor.
         * @throws java.io.IOException                              if the data is no longer available in the requested
         *                                                          flavor.
         * @throws java.awt.datatransfer.UnsupportedFlavorException if the requested data flavor is not supported.
         * @see java.awt.datatransfer.DataFlavor#getRepresentationClass
         */
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }

            return this.list;
        }

        /**
         * Returns whether or not the specified data flavor is supported for this object.
         *
         * @param flavor the requested flavor for the data
         * @return boolean indicating whether or not the data flavor is supported
         */
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(getTransferDataFlavors()[0]);
        }

        /**
         * Returns an array of DataFlavor objects indicating the flavors the data can be provided in. The array should
         * be ordered according to preference for providing the data (from most richly descriptive to least
         * descriptive).
         *
         * @return an array of data flavors in which this data can be transferred
         */
        public DataFlavor[] getTransferDataFlavors() {
            return this.dataFlavors;
        }
    }

    /**
     * A list that supports drag and drop.
     */
    public class DragDropList extends JList implements DropTargetListener,
            DragSourceListener, DragGestureListener {

        /**
         * This is the cluster that this particular component is representing, or -1 if it's the bin for unused variable
         * names. It's needed so that dropped gadgets can cause variable names to be put into the correct cluster.
         */
        private final int cluster;

        /**
         * The list of items that are being moved.
         */
        private List movedList;

        /**
         * Constructs a new list with the given items, cluster, and orientation.
         *
         * @param items       a {@link java.util.List} object
         * @param cluster     a int
         * @param orientation a int
         */
        public DragDropList(List items, int cluster, int orientation) {
            if (cluster < -1) {
                throw new IllegalArgumentException();
            }

            this.cluster = cluster;

            setLayoutOrientation(orientation);
            setVisibleRowCount(0);
            this.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
                Color fillColor = new Color(153, 204, 204);
                Color selectedFillColor = new Color(255, 204, 102);

                JLabel comp = new JLabel(" " + value + " ");
                comp.setOpaque(true);

                if (isSelected) {
                    comp.setForeground(Color.BLACK);
                    comp.setBackground(selectedFillColor);
                } else {
                    comp.setForeground(Color.BLACK);
                    comp.setBackground(fillColor);
                }

                comp.setHorizontalAlignment(SwingConstants.CENTER);
                comp.setBorder(new CompoundBorder(
                        new MatteBorder(2, 2, 2, 2, Color.WHITE),
                        new LineBorder(Color.BLACK)));

                return comp;
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

        /**
         * <p>Getter for the field <code>cluster</code>.</p>
         *
         * @return a int
         */
        public int getCluster() {
            return this.cluster;
        }

        /**
         * <p>dragGestureRecognized.</p>
         *
         * @param dragGestureEvent a {@link java.awt.dnd.DragGestureEvent} object
         */
        public void dragGestureRecognized(DragGestureEvent dragGestureEvent) {
            if (getSelectedIndex() == -1) {
                return;
            }

            List list = getSelectedValuesList();

            if (list == null) {
                getToolkit().beep();
            } else {
                this.movedList = list;
                ListSelection transferable = new ListSelection(list);
                dragGestureEvent.startDrag(DragSource.DefaultMoveDrop,
                        transferable, this);
            }
        }

        /**
         * <p>dragEnter.</p>
         *
         * @param dropTargetDropEvent a {@link java.awt.dnd.DropTargetDragEvent} object
         */
        public void drop(DropTargetDropEvent dropTargetDropEvent) {
            try {
                Transferable tr = dropTargetDropEvent.getTransferable();
                DataFlavor flavor = tr.getTransferDataFlavors()[0];
                List list = (List) tr.getTransferData(flavor);

                for (Object aList : list) {
                    String name = (String) aList;

                    if (getCluster() >= 0) {
                        try {
                            getClustersPrivate().addToCluster(getCluster(), name);
                            DefaultListModel model
                                    = (DefaultListModel) getModel();
                            model.addElement(name);
                            sort(model);
                            dropTargetDropEvent.dropComplete(true);
                        } catch (IllegalStateException e) {
                            String s = e.getMessage();

                            if (!"".equals(s)) {
                                JOptionPane.showMessageDialog(
                                        JOptionUtils.centeringComp(), s);
                            } else {
                                JOptionPane.showMessageDialog(
                                        JOptionUtils.centeringComp(),
                                        "Could not drop properly.");
                            }

                            e.printStackTrace();
                            dropTargetDropEvent.dropComplete(false);
                        }
                    } else {
                        getClustersPrivate().removeFromClusters(name);
                        DefaultListModel model = (DefaultListModel) getModel();
                        model.addElement(name);
                        sort(model);
                        dropTargetDropEvent.dropComplete(true);
                    }
                }
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * <p>dragEnter.</p>
         *
         * @param dsde a {@link java.awt.dnd.DropTargetDragEvent} object
         */
        public void dragDropEnd(DragSourceDropEvent dsde) {
            if (!dsde.getDropSuccess()) {
                return;
            }

            if (this.movedList != null) {
                for (Object aMovedList : this.movedList) {
                    ((DefaultListModel) getModel()).removeElement(aMovedList);
                }

                this.movedList = null;
            }
        }

        /**
         * <p>dragEnter.</p>
         *
         * @param dtde a {@link java.awt.dnd.DropTargetDragEvent} object
         */
        public void dragEnter(DropTargetDragEvent dtde) {
        }

        /**
         * <p>dragOver.</p>
         *
         * @param dtde a {@link java.awt.dnd.DropTargetDragEvent} object
         */
        public void dragOver(DropTargetDragEvent dtde) {
        }

        /**
         * <p>dropActionChanged.</p>
         *
         * @param dtde a {@link java.awt.dnd.DropTargetDragEvent} object
         */
        public void dropActionChanged(DropTargetDragEvent dtde) {
        }

        /**
         * <p>dragExit.</p>
         *
         * @param dte a {@link java.awt.dnd.DropTargetEvent} object
         */
        public void dragExit(DropTargetEvent dte) {
        }

        /**
         * <p>dragEnter.</p>
         *
         * @param dsde a {@link java.awt.dnd.DragSourceDragEvent} object
         */
        public void dragEnter(DragSourceDragEvent dsde) {
        }

        /**
         * <p>dragOver.</p>
         *
         * @param dsde a {@link java.awt.dnd.DragSourceDragEvent} object
         */
        public void dragOver(DragSourceDragEvent dsde) {
        }

        /**
         * <p>dropActionChanged.</p>
         *
         * @param dsde a {@link java.awt.dnd.DragSourceDragEvent} object
         */
        public void dropActionChanged(DragSourceDragEvent dsde) {
        }

        /**
         * <p>dragExit.</p>
         *
         * @param dse a {@link java.awt.dnd.DragSourceEvent} object
         */
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
}
