package local.quiethome;
public final class PowerRequestTest {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        check(PowerRequest.command(false).contains("\"status\":false"));
        check(!PowerRequest.command(false).contains("requestReboot"));
        check(PowerRequest.command(true).contains("\"method\":\"requestReboot\""));
        check(PowerRequest.validKey("Example-0123456789!"));
        check(!PowerRequest.validKey("example\r\nOther: value"));
        check(!PowerRequest.validKey("embedded space"));
        check(!PowerRequest.validKey("tab\tvalue"));
        check(!PowerRequest.validKey("nonascii-\u0080"));
        check(!PowerRequest.validKey(""));
        check(!PowerRequest.validKey(null));
        check(PowerRequest.validKey(new String(new char[128]).replace('\0','x')));
        check(!PowerRequest.validKey(new String(new char[129]).replace('\0','x')));
        System.out.println("PowerRequestTest passed");
    }
}
