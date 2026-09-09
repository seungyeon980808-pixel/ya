import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ApkModuleXmlEncoder;

import java.io.File;

public final class BuildApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("BuildApk <input-dir> <output-apk>");
        File input = new File(args[0]);
        File output = new File(args[1]);
        ApkModuleXmlEncoder encoder = new ApkModuleXmlEncoder();
        encoder.setApkLogger(new APKLogger() {
            @Override public void logMessage(String msg) { System.out.println(msg); }
            @Override public void logError(String msg, Throwable tr) {
                System.err.println(msg);
                if (tr != null) tr.printStackTrace();
            }
            @Override public void logVerbose(String msg) {}
        });
        encoder.scanDirectory(input);
        ApkModule module = encoder.getApkModule();
        module.getAndroidManifest().refreshFull();
        module.refreshTable();
        module.writeApk(output);
        System.out.println("WROTE " + output.getAbsolutePath() + " (" + output.length() + " bytes)");
    }
}
