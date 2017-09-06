import lombok.experimental.ToggleOn;

public class ToggleArgTest extends BaseToggleTestClass {
    @ToggleOn(name = "toggle1")
    public void method1(String string) {
        say(string);
    }
}
