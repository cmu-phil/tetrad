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

package edu.cmu.tetradapp.ui;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Drag and drop between the two lists of a "move items back and forth" panel -- the pattern of a source list, a
 * selected list, and arrow buttons between them -- for lists backed by {@link DefaultListModel}. Dragging selected
 * items from one list and dropping them on the other moves them, inserting at the drop position; dropping items
 * back on the list they came from reorders them. The arrow buttons keep working as before; this adds the gesture
 * they stand for. Items already present in the target list (by identity) are not duplicated, only removed from the
 * source.
 * <p>
 * Usage: {@code DualListDragAndDrop.install(listA, listB, afterMove)}, where {@code afterMove} runs after any
 * successful drop (to update a model, refresh a view) and may be null. Both lists are given drag, the insert drop
 * mode, and a shared transfer handler; a drag from any other component is refused.
 *
 * @author josephramsey
 */
public final class DualListDragAndDrop {

    private DualListDragAndDrop() {
    }

    /**
     * Installs drag and drop between the two lists.
     *
     * @param a         one list; its model must be a {@link DefaultListModel}.
     * @param b         the other list; likewise.
     * @param afterMove run after any successful drop, or null.
     * @param <T>       the element type.
     */
    public static <T> void install(JList<T> a, JList<T> b, Runnable afterMove) {
        if (a == null || b == null) throw new NullPointerException("Both lists are required.");
        if (!(a.getModel() instanceof DefaultListModel) || !(b.getModel() instanceof DefaultListModel)) {
            throw new IllegalArgumentException("Both lists must be backed by DefaultListModel.");
        }
        Handler<T> handler = new Handler<>(Set.of(a, b), afterMove);
        for (JList<T> list : List.of(a, b)) {
            list.setDragEnabled(true);
            list.setDropMode(DropMode.INSERT);
            list.setTransferHandler(handler);
        }
    }

    /**
     * Moves items into a model at an index, the model-level half of a drop; public so it can be tested without a
     * display. If the two models are the same object the items are reordered: they are taken out first, and the
     * insertion point moves up by one for each item that was above it, so that dropping on the gap just below a
     * selected item leaves the list unchanged, as a user expects. Otherwise the items are removed from the source and
     * inserted into the target, skipping any already present in the target (by identity). An index below 0 or past
     * the end means the end.
     *
     * @param source the model the items come from.
     * @param target the model they go to; may be the same object.
     * @param items  the items, in the order they should land.
     * @param index  the insertion index in the target, before any removal.
     * @param <T>    the element type.
     * @return the indices in the target at which the items now sit, in order; empty if nothing was inserted.
     */
    public static <T> int[] moveItems(DefaultListModel<T> source, DefaultListModel<T> target, List<T> items,
                                      int index) {
        if (index < 0 || index > target.getSize()) index = target.getSize();

        if (source == target) {
            for (T item : items) {
                int at = indexOf(target, item);
                if (at < 0) continue;
                target.remove(at);
                if (at < index) index--;
            }
        } else {
            for (T item : items) {
                int at = indexOf(source, item);
                if (at >= 0) source.remove(at);
            }
        }

        List<Integer> placed = new ArrayList<>();
        for (T item : items) {
            if (source != target && indexOf(target, item) >= 0) continue;
            target.add(index, item);
            placed.add(index++);
        }
        return placed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static <T> int indexOf(DefaultListModel<T> model, T item) {
        for (int i = 0; i < model.getSize(); i++) if (model.get(i) == item) return i;
        return -1;
    }

    /**
     * The flavor of a drag within one JVM: a {@link Payload}.
     */
    private static final DataFlavor FLAVOR = new DataFlavor(Payload.class, "Dual list items");

    /**
     * What a drag carries: where it came from and what.
     */
    private record Payload<T>(JList<T> source, List<T> items) {
    }

    private static final class PayloadTransferable<T> implements Transferable {
        private final Payload<T> payload;

        PayloadTransferable(Payload<T> payload) {
            this.payload = payload;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!FLAVOR.equals(flavor)) throw new UnsupportedFlavorException(flavor);
            return payload;
        }
    }

    private static final class Handler<T> extends TransferHandler {
        private final Set<JList<T>> lists;
        private final Runnable afterMove;

        Handler(Set<JList<T>> lists, Runnable afterMove) {
            this.lists = lists;
            this.afterMove = afterMove;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Transferable createTransferable(JComponent c) {
            JList<T> list = (JList<T>) c;
            List<T> items = new ArrayList<>(list.getSelectedValuesList());
            if (items.isEmpty()) return null;
            return new PayloadTransferable<>(new Payload<>(list, items));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(FLAVOR)) return false;
            Payload<?> payload = payload(support);
            if (payload == null || !lists.contains(payload.source())) return false;
            support.setDropAction(MOVE);
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            Payload<T> payload = (Payload<T>) payload(support);
            if (payload == null) return false;

            JList<T> target = (JList<T>) support.getComponent();
            JList<T> source = payload.source();
            DefaultListModel<T> targetModel = (DefaultListModel<T>) target.getModel();
            DefaultListModel<T> sourceModel = (DefaultListModel<T>) source.getModel();

            JList.DropLocation location = (JList.DropLocation) support.getDropLocation();
            int index = location.getIndex();

            int[] placed = moveItems(sourceModel, targetModel, payload.items(), index);

            if (placed.length > 0) {
                target.setSelectedIndices(placed);
                target.ensureIndexIsVisible(placed[0]);
            }
            if (source != target) source.clearSelection();

            if (afterMove != null) afterMove.run();
            return true;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            // The import already removed the items from their source, so there is nothing to do here; a cancelled
            // drag leaves both lists as they were.
        }

        private static Payload<?> payload(TransferSupport support) {
            try {
                return (Payload<?>) support.getTransferable().getTransferData(FLAVOR);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return "DualListDragAndDrop over " + Arrays.toString(lists.toArray());
        }
    }
}
