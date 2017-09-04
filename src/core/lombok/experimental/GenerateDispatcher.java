package lombok.experimental;

public @interface GenerateDispatcher {
    String DEFAULT_GROUP_NAME = "";

    boolean isToggle();
    String switchName();
    String switchValue();
    String groupName() default DEFAULT_GROUP_NAME;
    boolean removeProxyAnnotations() default false;
    boolean removeDelegateAnnotations() default true;
    boolean makeDelegatePackagePrivate() default false;
}
