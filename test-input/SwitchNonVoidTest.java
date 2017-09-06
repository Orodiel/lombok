import lombok.experimental.Switch;

public class SwitchNonVoidTest extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public String method1() {
        return echo("method1");
    }
}
