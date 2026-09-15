package android.content;
import java.io.File; import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0, RECEIVER_NOT_EXPORTED=4;
 public static boolean microphone=true, denyLaunch=false;
 public static final Map<String,Prefs> stores=new HashMap<>();
 public static final List<Intent> starts=new ArrayList<>();
 public int checkSelfPermission(String p){return microphone?0:-1;}
 public <T>T getSystemService(Class<T> type){try{return type.getDeclaredConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}}
 public SharedPreferences getSharedPreferences(String n,int m){return stores.computeIfAbsent(n,k->new Prefs());}
 public File getFilesDir(){return new File(System.getProperty("java.io.tmpdir"),"ptt-host-test");}
 public File getExternalFilesDir(String t){return null;}
 public Context getApplicationContext(){return this;}
 public String getPackageName(){return "com.malhaedwo.pttprobe";}
 public void sendBroadcast(Intent i){}
 public void startForegroundService(Intent i){if(denyLaunch)throw new IllegalStateException("background denied"); starts.add(i);}
 public boolean stopService(Intent i){return true;}
 public void registerReceiver(BroadcastReceiver r,IntentFilter f){} public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){} public void unregisterReceiver(BroadcastReceiver r){}
 public static class Prefs implements SharedPreferences {
 final Map<String,Object> disk=new HashMap<>();
 public int getInt(String k,int d){return ((Number)disk.getOrDefault(k,d)).intValue();}
 public long getLong(String k,long d){return ((Number)disk.getOrDefault(k,d)).longValue();}
 public String getString(String k,String d){return (String)disk.getOrDefault(k,d);}
 public boolean getBoolean(String k,boolean d){return (Boolean)disk.getOrDefault(k,d);}
 public Editor edit(){return new Editor(){final Map<String,Object> writes=new HashMap<>();boolean clear;
 public Editor putInt(String k,int v){writes.put(k,v);return this;} public Editor putLong(String k,long v){writes.put(k,v);return this;} public Editor putString(String k,String v){writes.put(k,v);return this;} public Editor putBoolean(String k,boolean v){writes.put(k,v);return this;} public Editor clear(){clear=true;return this;} public void apply(){commit();} public boolean commit(){if(clear)disk.clear();disk.putAll(writes);return true;}};}
 }
}