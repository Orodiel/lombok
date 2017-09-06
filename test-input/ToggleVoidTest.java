import lombok.experimental.ToggleOn;

public class ToggleVoidTest extends BaseToggleTestClass {
    @ToggleOn(name = "toggle1")
    public void method1() {
        say("method1");
    }
}