/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.annotation;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 *
 * Sep 6, 2017 12:03:48 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @param <T> annotation
 */
public class AbstractAnnotations<T extends Annotation> {

    protected final Map<String, AnnotatedClass> annoClassByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    protected final Map<String, String> nameByCommand = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public String getName(String command) {
        return nameByCommand.get(command);
    }

    /**
     * Get a list of annotation attribute names.
     *
     * @return annotation attribute names.
     */
    public List<String> getNames() {
        List<String> list = annoClassByName.keySet().stream()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * Get a list of annotation attribute commands.
     *
     * @return annotation attribute commands.
     */
    public List<String> getCommands() {
        List<String> list = nameByCommand.keySet().stream()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * Get the annotation by its attribute name.
     *
     * @param name annotation attribute name
     * @return annotation
     */
    public T getAnnotation(String name) {
        AnnotatedClass ac = annoClassByName.get(name);

        return (ac == null) ? null : (T) ac.getAnnotation();
    }

    /**
     * Get the class with annotation containing attribute name.
     *
     * @param name annotation attribute name
     * @return class
     */
    public Class getAnnotatedClass(String name) {
        AnnotatedClass ac = annoClassByName.get(name);

        return (ac == null) ? null : ac.getClazz();
    }

}
