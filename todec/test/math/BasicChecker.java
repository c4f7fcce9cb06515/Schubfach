package math;

class BasicChecker {

    static final boolean FAILURE_THROWS_EXCEPTION = true;

    static void assertTrue(boolean ok, String valueName) {
        if (ok) {
            return;
        }
        if (FAILURE_THROWS_EXCEPTION) {
            throw new RuntimeException(valueName);
        }
        System.err.println(valueName + " is not correct");
    }

}
