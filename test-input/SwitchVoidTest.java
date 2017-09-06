import lombok.experimental.Switch;

public class SwitchVoidTest extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public void method1() {
        say("method1");
    }
}
