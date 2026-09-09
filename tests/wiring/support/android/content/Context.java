package android.content;
import java.io.File;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public abstract int checkSelfPermission(String permission);
 public abstract <T> T getSystemService(Class<T> type);
 public abstract ContentResolver getContentResolver();
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
 public abstract File getDatabasePath(String name);
 public abstract File getFilesDir();
 public abstract File getExternalFilesDir(String type);
 public Context getApplicationContext(){return this;}
 public String getPackageName(){return "com.malhaedwo.pttprobe";}
 public void sendBroadcast(Intent intent){}
}
