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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps multiple GUI editor fields for the same parameter of the same {@link Parameters} object in sync.
 * <p>
 * A parameter (e.g., {@code alpha} or {@code penaltyDiscount}) can appear more than once in an extended parameter
 * list&mdash;for instance, once under a test's section and once under a score's section&mdash;with both fields editing
 * the same {@link Parameters} object. Since the {@link Parameters} object stores only one value per parameter name, an
 * edit in one field silently changes the effective value shown (stale) in the other. This registry lets each field
 * register a refresh action keyed by ({@link Parameters} identity, parameter name); when any registered field commits a
 * new value, all other registered fields for the same key are refreshed from the {@link Parameters} object.
 * <p>
 * Memory notes: the registry is keyed weakly on the {@link Parameters} object, and components are held via
 * {@link WeakReference}, so discarded panels do not leak. Each component's refresh action is stored as a client
 * property on the component itself so that its lifetime is exactly the component's lifetime. Dead references are pruned
 * lazily on registration and notification.
 * <p>
 * Reentrancy: a thread-local guard suppresses nested notifications, so a refresh that itself writes the (identical)
 * value back to the {@link Parameters} object cannot trigger a notification cascade. All calls are expected on the
 * Swing event dispatch thread, as they are driven by Swing editor events.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ParameterFieldSync {

    /**
     * Client property key under which a component's refresh action is stored.
     */
    private static final Object REFRESH_KEY = new Object();

    /**
     * Registry: Parameters (weak, identity) -&gt; parameter name -&gt; weakly held components editing it.
     */
    private static final Map<Parameters, Map<String, List<WeakReference<JComponent>>>> REGISTRY = new WeakHashMap<>();

    /**
     * Guard against notification cascades (refresh -&gt; set -&gt; notify -&gt; refresh -&gt; ...).
     */
    private static final ThreadLocal<Boolean> NOTIFYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ParameterFieldSync() {
    }

    /**
     * Registers a component as an editor of the given parameter for the given {@link Parameters} object. The refresh
     * action should re-read the parameter's current value from the {@link Parameters} object and update the component's
     * display accordingly; it is invoked whenever a different registered component commits a new value for the same
     * parameter of the same {@link Parameters} object.
     *
     * @param parameters the {@link Parameters} object the component edits.
     * @param parameter  the parameter name.
     * @param component  the editor component (or its containing box, for compound editors).
     * @param refresh    the action that refreshes the component's display from the {@link Parameters} object.
     */
    public static synchronized void register(Parameters parameters, String parameter, JComponent component,
                                             Runnable refresh) {
        if (parameters == null || parameter == null || component == null || refresh == null) {
            return;
        }

        component.putClientProperty(ParameterFieldSync.REFRESH_KEY, refresh);

        List<WeakReference<JComponent>> refs = ParameterFieldSync.REGISTRY
                .computeIfAbsent(parameters, k -> new HashMap<>())
                .computeIfAbsent(parameter, k -> new ArrayList<>());

        // Prune dead references and avoid duplicate registration of the same component.
        for (Iterator<WeakReference<JComponent>> it = refs.iterator(); it.hasNext(); ) {
            JComponent c = it.next().get();
            if (c == null) {
                it.remove();
            } else if (c == component) {
                return;
            }
        }

        refs.add(new WeakReference<>(component));
    }

    /**
     * Notifies all registered components (other than the source) that the given parameter of the given
     * {@link Parameters} object has been assigned a new value, causing them to refresh their displays. Call this after
     * a successful {@code parameters.set(parameter, value)} from an editor component.
     *
     * @param parameters the {@link Parameters} object whose parameter changed.
     * @param parameter  the parameter name.
     * @param source     the component that committed the change (excluded from refresh); may be null.
     */
    public static void valueChanged(Parameters parameters, String parameter, JComponent source) {
        if (parameters == null || parameter == null) {
            return;
        }

        if (ParameterFieldSync.NOTIFYING.get()) {
            return;
        }

        List<JComponent> targets = new ArrayList<>();

        synchronized (ParameterFieldSync.class) {
            Map<String, List<WeakReference<JComponent>>> byParam = ParameterFieldSync.REGISTRY.get(parameters);
            if (byParam == null) {
                return;
            }

            List<WeakReference<JComponent>> refs = byParam.get(parameter);
            if (refs == null) {
                return;
            }

            for (Iterator<WeakReference<JComponent>> it = refs.iterator(); it.hasNext(); ) {
                JComponent c = it.next().get();
                if (c == null) {
                    it.remove();
                } else if (c != source) {
                    targets.add(c);
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        ParameterFieldSync.NOTIFYING.set(Boolean.TRUE);

        try {
            for (JComponent c : targets) {
                Object refresh = c.getClientProperty(ParameterFieldSync.REFRESH_KEY);
                if (refresh instanceof Runnable) {
                    ((Runnable) refresh).run();
                }
            }
        } finally {
            ParameterFieldSync.NOTIFYING.set(Boolean.FALSE);
        }
    }
}
