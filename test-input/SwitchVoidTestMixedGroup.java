import lombok.experimental.Switch;

public class SwitchVoidTestMixedGroup extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1", groupName = "group1")
    public void method1() {
        say("method1");
    }

    @Switch(name = "switch2", value = "value1", groupName = "group1")
    public void method2() {
        say("method2");
    }
}
