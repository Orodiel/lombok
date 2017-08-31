package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface ToggleOn {
    String value();
    String groupName() default "";
    boolean removeProxyAnnotations() default false;
    boolean removeDelegateAnnotations() default true;
    boolean makeDelegatePackagePrivate() default false;
}
