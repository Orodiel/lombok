import lombok.experimental.GenerateDispatcher;

public class ToggleTest {

    private String __evaluateSwitch(String name) {
        return ":C";
    }

    private int index = 0;
    public boolean getToggleState(String toggleName) {
        return index++ % 2 == 0;
    }

    @GenerateDispatcher(isToggle = false, switchName = "test", switchValue = "test")
    public void method1(Object a) {
        if (true) {
            System.out.println("Hello Kitty!");
            method1(a);
            return;
        }
        System.out.println("Hello Kitty!");
    }

    //@ToggleOn("ToggleName")
    public void method10(Object a) {
        System.out.println("Hello Kitty!");
    }

    //@ToggleOff("ToggleName")
    public void method2() {
        System.out.println("Hello World!");
    }

    //@ToggleOn("ToggleName2")
    public void method3() {
        System.out.println("Hello Kitty!");
    }

    //@ToggleOff("ToggleName2")
    public void method4() {
        System.out.println("Hello World!");
    }

    public static void main(String[] args) {
        ToggleTest test = new ToggleTest();
        test.method1(null);
        test.method1(null);
    }
}