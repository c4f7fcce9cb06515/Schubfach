package math;

class BasicChecker {

    static final boolean FAILURE_THROWS_EXCEPTION = true;

    static void assertTrue(boolean ok, String valueName) {
        if (ok) {
            return;
        }
        String msg = valueName + " is not correct";
        if (FAILURE_THROWS_EXCEPTION) {
            throw new RuntimeException(msg);
        }
        System.err.println(msg);
    }

}
