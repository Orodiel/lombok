public class BaseSwitchTestClass extends BaseTestClass {
    public static final int NUMBER_OF_VALUES = 5;
    int current = 0;
    String __evaluateSwitch(String name) {
        return "value" + (((current++ % NUMBER_OF_VALUES) + NUMBER_OF_VALUES) % NUMBER_OF_VALUES);
    }
}
