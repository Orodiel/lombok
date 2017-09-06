public class BaseTestClass {
    void say(String aWord) {
        System.out.println(String.format("Called %s", aWord));
    }

    String echo(String aWord) {
        say(aWord);
        return aWord;
    }
}
