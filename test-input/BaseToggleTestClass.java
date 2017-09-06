public class BaseToggleTestClass extends BaseTestClass {
    int current = 0;
    boolean __evaluateSwitch(String name) {
        return current++ % 2 == 0;
    }
}
