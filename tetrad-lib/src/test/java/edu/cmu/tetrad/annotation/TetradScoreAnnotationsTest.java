/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetrad.annotation;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Sep 22, 2017 1:40:50 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradScoreAnnotationsTest {

    public TetradScoreAnnotationsTest() {
    }

    @Test
    public void testAnnotatedNameAttributeForUniqueness() {
        List<AnnotatedClassWrapper<Score>> nameWrappers = TetradScoreAnnotations.getInstance().getNameWrappers();
        List<String> names = nameWrappers.stream().map(e -> e.annotatedClass.getAnnotation().name().toLowerCase()).collect(Collectors.toList());

        long actual = names.size();
        long expected = names.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'name' is not unique.", expected, actual);
    }

    @Test
    public void testAnnotatedCommandAttributeForUniqueness() {
        List<AnnotatedClassWrapper<Score>> nameWrappers = TetradScoreAnnotations.getInstance().getNameWrappers();
        List<String> names = nameWrappers.stream().map(e -> e.annotatedClass.getAnnotation().command().toLowerCase()).collect(Collectors.toList());

        long actual = names.size();
        long expected = names.stream().distinct().count();
        Assert.assertEquals("Annotation attribute 'command' is not unique.", expected, actual);
    }

}
