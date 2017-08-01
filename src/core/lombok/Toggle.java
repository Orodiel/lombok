package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Toggle {
    String value();
    ToggleState state() default ToggleState.ON;

    enum ToggleState {
        ON,
        OFF
    }
}
