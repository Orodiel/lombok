import lombok.experimental.Switch;

public class SwitchArgTest extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public void method1(String string) {
        say(string);
    }
}
