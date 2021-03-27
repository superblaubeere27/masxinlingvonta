package net.superblaubeere27.masxinlingvonta.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tells the compiler to compile the annotated method to native code
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Outsource {

}
