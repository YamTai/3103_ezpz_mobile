package geeone.ezpz;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/** @author Kevin Kowalewski */
@SuppressWarnings("WeakerAccess")
public class RootChecker {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3();
    }

    private static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean checkRootMethod2() {
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkRootMethod3() {
        Process process = null;
        BufferedReader in = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            in = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));  //b-06
            return (in.readLine() != null);
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null){
                process.destroy();
            }
            if (in != null){
                try{
                    in.close(); //  b-01
                }catch(IOException e){
                    e.printStackTrace();
                }

            }
        }
    }
}