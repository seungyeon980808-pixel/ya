import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public final class SignVerifyApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("SignVerifyApk <input> <output> <keystore> <alias> <storepass> <keypass>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        File keyStoreFile = new File(args[2]);
        String alias = args[3];
        char[] storePassword = args[4].toCharArray();
        char[] keyPassword = args[5].toCharArray();

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream stream = new FileInputStream(keyStoreFile)) {
            keyStore.load(stream, storePassword);
        }
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                alias, privateKey, Collections.singletonList(certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .setMinSdkVersion(31)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();

        ApkVerifier.Result result = new ApkVerifier.Builder(output)
                .setMinCheckedPlatformVersion(31)
                .build()
                .verify();
        System.out.println("VERIFIED=" + result.isVerified());
        System.out.println("V1=" + result.isVerifiedUsingV1Scheme());
        System.out.println("V2=" + result.isVerifiedUsingV2Scheme());
        System.out.println("V3=" + result.isVerifiedUsingV3Scheme());
        System.out.println("CERT_SHA256=" + sha256(certificate.getEncoded()));
        for (ApkVerifier.IssueWithParams warning : result.getWarnings()) {
            System.out.println("WARNING=" + warning);
        }
        for (ApkVerifier.IssueWithParams error : result.getErrors()) {
            System.out.println("ERROR=" + error);
        }
        if (!result.isVerified()) throw new IllegalStateException("APK signature verification failed");
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b));
        return out.toString();
    }
}
