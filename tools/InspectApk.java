import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import java.io.File;
public final class InspectApk {
    public static void main(String[] args) throws Exception {
        ApkModule module = ApkModule.loadApkFile(new File(args[0]));
        module.getAndroidManifest().setPackageBlock(module.getTableBlock().pickOne());
        System.out.println(module.getAndroidManifest().serializeToXml());
        System.out.println("ENTRIES=" + module.getZipEntryMap().size());
        System.out.println("HAS_DEX=" + (module.getInputSource("classes.dex") != null));
        System.out.println("HAS_TABLE=" + module.hasTableBlock());
        ResXmlDocument config = module.getResXmlDocument("res/xml/accessibility_service_config.xml");
        if (config == null) throw new IllegalStateException("Missing accessibility config");
        config.setPackageBlock(module.getTableBlock().pickOne());
        System.out.println("ACCESSIBILITY_CONFIG_BEGIN");
        System.out.println(config.serializeToXml());
        System.out.println("ACCESSIBILITY_CONFIG_END");
    }
}
