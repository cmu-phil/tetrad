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
package edu.cmu.tetrad.algcomparison.independence.description;

import edu.cmu.tetrad.annotation.IndTestDescription;

/**
 *
 * Sep 1, 2017 3:03:03 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class IndTestDescriptionClass {

    private final Class clazz;
    private final IndTestDescription IndTestDescription;

    public IndTestDescriptionClass(Class clazz, IndTestDescription IndTestDescription) {
        this.clazz = clazz;
        this.IndTestDescription = IndTestDescription;
    }

    public Class getClazz() {
        return clazz;
    }

    public IndTestDescription getIndTestDescription() {
        return IndTestDescription;
    }

}
