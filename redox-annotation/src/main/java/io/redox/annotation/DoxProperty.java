package io.redox.annotation;

import java.lang.annotation.*;

/** Overrides the JSON field name or ignores a field during binding. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface DoxProperty {
    /** JSON key name. Empty string means use the Java field name. */
    String value() default "";
    /** Skip this field entirely during serialization and deserialization. */
    boolean ignore() default false;
}
