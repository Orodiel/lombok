import lombok.experimental.ToggleOff;
import lombok.experimental.ToggleOn;

public class ToggleVoidTestTwoBranches extends BaseToggleTestClass {
    @ToggleOn(name = "toggle1")
    public void method1() {
        say("method1");
    }

    @ToggleOff(name = "toggle1")
    public void method2() {
        say("method2");
    }
}
