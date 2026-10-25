package local.quiethome;

/** The only two commands exposed by the launcher; never accept a URL or arbitrary method. */
final class PowerRequest {
    static String command(boolean restart) {
        return restart
            ? "{\"method\":\"requestReboot\",\"id\":1,\"params\":[],\"version\":\"1.0\"}"
            : "{\"method\":\"setPowerStatus\",\"id\":1,\"params\":[{\"status\":false}],\"version\":\"1.0\"}";
    }
    static boolean validKey(String value) {
        if(value==null||value.isEmpty()||value.length()>128)return false;
        for(int i=0;i<value.length();i++)if(value.charAt(i)<33||value.charAt(i)>126)return false;
        return true;
    }
}
