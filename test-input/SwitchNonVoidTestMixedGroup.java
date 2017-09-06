import lombok.experimental.Switch;

public class SwitchNonVoidTestMixedGroup extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1", groupName = "group1")
    public String method1() {
        return echo("method1");
    }

    @Switch(name = "switch2", value = "value1", groupName = "group1")
    public String method2() {
        return echo("method2");
    }
}
