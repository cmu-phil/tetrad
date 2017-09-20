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

/**
 *
 * Sep 20, 2017 11:52:10 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AnnotationAttribute {

    protected final String attribute;

    protected final Class clazz;

    public AnnotationAttribute(String attribute, Class clazz) {
        this.attribute = attribute;
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return attribute;
    }

    public String getAttribute() {
        return attribute;
    }

    public Class getClazz() {
        return clazz;
    }

}
