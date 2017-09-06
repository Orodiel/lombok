import lombok.experimental.Switch;

public class SwitchVoidTestTwoDispatchers extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public void method1() {
        say("method1");
    }

    @Switch(name = "switch2", value = "value1")
    public void method2() {
        say("method2");
    }
}
