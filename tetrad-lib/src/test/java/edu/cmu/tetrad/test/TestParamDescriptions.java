/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.ParamDescriptions;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * May 10, 2019 2:31:48 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class TestParamDescriptions {
    @Test
    public void testParamsMissingValueType() {
        List<String> paramsMissingValueType = ParamDescriptions.getInstance().getParamsMissingValueType();
        
        paramsMissingValueType.forEach(e->{
            System.out.println("Parameter value type not specified in HTML manual: " + e);
        });
        
        // Require all params in edu.cmu.tetrad.util.Params have value type specified in HTML manual
        // Except the system paramters, like printStream
        assertEquals(paramsMissingValueType.size(), 0);
    }
}
