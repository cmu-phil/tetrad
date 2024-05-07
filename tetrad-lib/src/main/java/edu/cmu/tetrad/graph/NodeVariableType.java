/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetrad.graph;

/**
 * Node variable type.
 *
 * @author Zhou Yuan zhy19@pitt.edu
 * @version $Id: $Id
 */
public enum NodeVariableType {

    /**
     * The node variable type not intervened on.
     */
    DOMAIN,

    /**
     * The node variable type is intervened on with a specific status, such as treatment or control.
     */
    INTERVENTION_STATUS,

    /**
     * The node variable type is intervened on with a specific value.
     */
    INTERVENTION_VALUE
}
