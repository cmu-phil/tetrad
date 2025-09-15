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

package edu.pitt.dbmi.data.reader.validation.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DatasetReader;
import edu.pitt.dbmi.data.reader.validation.Validation;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;

import java.util.List;

/**
 * Dec 12, 2018 10:57:09 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface TabularDataValidation extends Validation, DatasetReader {

    /**
     * Validate the data columns.
     *
     * @param dataColumns the data columns.
     * @param hasHeader   the flag indicating if the data has a header.
     * @return the validation results.
     */
    List<ValidationResult> validate(DataColumn[] dataColumns, boolean hasHeader);

}

