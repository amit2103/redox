package io.redox.annotation;

import java.lang.annotation.*;

/**
 * Marks a class for REDox compile-time converter generation.
 * The annotation processor generates a {@code {ClassName}DoxConverter} in the same package.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DoxSerializable {}
