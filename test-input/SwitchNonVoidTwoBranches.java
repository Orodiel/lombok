import lombok.experimental.Switch;

public class SwitchNonVoidTwoBranches extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public String method1() {
        return echo("method1");
    }

    @Switch(name = "switch1", value = "value2")
    public String method2() {
        return echo("method2");
    }
}
