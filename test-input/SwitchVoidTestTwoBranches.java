import lombok.experimental.Switch;

public class SwitchVoidTestTwoBranches extends BaseSwitchTestClass {
    @Switch(name = "switch1", value = "value1")
    public void method1() {
        say("method1");
    }

    @Switch(name = "switch1", value = "value2")
    public void method2() {
        say("method2");
    }

}
