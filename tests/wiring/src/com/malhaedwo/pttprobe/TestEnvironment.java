package com.malhaedwo.pttprobe;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class TestEnvironment {
 static final class ContextImpl extends Context {
  final Resolver resolver=new Resolver(); final AlarmManager alarms=new AlarmManager(); final NotificationManager notifications=new NotificationManager();
  boolean alarmServiceAvailable=true;
  final Map<String,Integer> permissions=new HashMap<>(); final Map<String,Prefs> prefs=new HashMap<>();
  final File root,databaseDir,filesDir; File externalFilesDir;
  ContextImpl(){this(temporaryRoot());}
  ContextImpl(File root){this.root=root;databaseDir=new File(root,"databases");filesDir=new File(root,"files");externalFilesDir=new File(root,"external-files");databaseDir.mkdirs();filesDir.mkdirs();externalFilesDir.mkdirs();grant(android.Manifest.permission.READ_CALENDAR);grant(android.Manifest.permission.WRITE_CALENDAR);grant(android.Manifest.permission.POST_NOTIFICATIONS);}
  void grant(String p){permissions.put(p,PackageManager.PERMISSION_GRANTED);} void deny(String p){permissions.put(p,PackageManager.PERMISSION_DENIED);}
  void useExternalFilesDir(File directory){externalFilesDir=directory;}
  @Override public int checkSelfPermission(String p){return permissions.getOrDefault(p,PackageManager.PERMISSION_DENIED);}
  @Override public <T> T getSystemService(Class<T> t){if(t==AlarmManager.class)return alarmServiceAvailable?t.cast(alarms):null;if(t==NotificationManager.class)return t.cast(notifications);return null;}
  @Override public ContentResolver getContentResolver(){return resolver;}
  @Override public SharedPreferences getSharedPreferences(String n,int m){return prefs.computeIfAbsent(n,k->new Prefs());}
  @Override public File getDatabasePath(String name){return new File(databaseDir,name);}
  @Override public File getFilesDir(){return filesDir;}
  @Override public File getExternalFilesDir(String type){return externalFilesDir;}
  private static File temporaryRoot(){try{return Files.createTempDirectory("ptt-wiring-").toFile();}catch(IOException e){throw new IllegalStateException(e);}}
 }
 static final class Prefs implements SharedPreferences {
  final Map<String,Object> values=new HashMap<>(); public long getLong(String k,long d){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():d;} public String getString(String k,String d){Object v=values.get(k);return v==null?d:String.valueOf(v);} public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:d;} public Editor edit(){return new Editor(){public Editor putLong(String k,long v){values.put(k,v);return this;}public Editor putString(String k,String v){values.put(k,v);return this;}public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public void apply(){}};}
 }
 static final class Resolver extends ContentResolver {
  final LinkedHashMap<Long,Map<String,Object>> calendars=new LinkedHashMap<>(); final LinkedHashMap<Long,Map<String,Object>> events=new LinkedHashMap<>(); long nextEventId=100; int insertCount,updateCount,deleteCount; boolean throwInsert,throwUpdate,failDelete;
  Resolver(){Map<String,Object> c=new LinkedHashMap<>();c.put(CalendarContract.Calendars._ID,7L);c.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,"Test calendar");c.put(CalendarContract.Calendars.ACCOUNT_NAME,"test@example.invalid");c.put(CalendarContract.Calendars.ACCOUNT_TYPE,"LOCAL");c.put(CalendarContract.Calendars.IS_PRIMARY,1);c.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,700);c.put(CalendarContract.Calendars.VISIBLE,1);calendars.put(7L,c);}
  long addEvent(long id,long calendarId,String title,String description,String customUri){Map<String,Object> e=new LinkedHashMap<>();e.put(CalendarContract.Events._ID,id);e.put(CalendarContract.Events.CALENDAR_ID,calendarId);e.put(CalendarContract.Events.TITLE,title);e.put(CalendarContract.Events.DESCRIPTION,description);e.put(CalendarContract.Events.CUSTOM_APP_PACKAGE,"com.malhaedwo.pttprobe");e.put(CalendarContract.Events.CUSTOM_APP_URI,customUri);events.put(id,e);nextEventId=Math.max(nextEventId,id+1);return id;}
  @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){String u=uri.toString();if(u.equals(CalendarContract.Calendars.CONTENT_URI.toString()))return cursor(projection,filterCalendars());List<Map<String,Object>> rows=new ArrayList<>();Long id=eventId(uri);if(id!=null){Map<String,Object> e=events.get(id);if(e!=null)rows.add(e);}else for(Map<String,Object> e:events.values())if(eventMatches(e,selection,args))rows.add(e);return cursor(projection,rows);}
  @Override public Uri insert(Uri uri,ContentValues values){if(throwInsert)throw new IllegalStateException("provider insert failure");long id=nextEventId++;Map<String,Object> row=new LinkedHashMap<>();copy(values,row);row.put(CalendarContract.Events._ID,id);events.put(id,row);insertCount++;return Uri.parse(CalendarContract.Events.CONTENT_URI+"/"+id);}
  @Override public int update(Uri uri,ContentValues values,String selection,String[] args){if(throwUpdate)throw new IllegalStateException("provider update failure");Long id=eventId(uri);Map<String,Object> row=id==null?null:events.get(id);if(row==null)return 0;copy(values,row);updateCount++;return 1;}
  @Override public int delete(Uri uri,String selection,String[] args){Long id=eventId(uri);if(failDelete)return 0;if(id!=null&&events.remove(id)!=null){deleteCount++;return 1;}return 0;}
  private List<Map<String,Object>> filterCalendars(){return new ArrayList<>(calendars.values());}
  private boolean eventMatches(Map<String,Object> e,String s,String[] a){if(s==null)return true;if(s.contains(CalendarContract.Events.CUSTOM_APP_URI+"=?"))return num(e.get(CalendarContract.Events.CALENDAR_ID))==Long.parseLong(a[0])&&Objects.equals(e.get(CalendarContract.Events.CUSTOM_APP_PACKAGE),a[1])&&Objects.equals(e.get(CalendarContract.Events.CUSTOM_APP_URI),a[2]);if(s.contains(CalendarContract.Events.DESCRIPTION+" LIKE ?")){String needle=a[1].replace("%","");return num(e.get(CalendarContract.Events.CALENDAR_ID))==Long.parseLong(a[0])&&String.valueOf(e.get(CalendarContract.Events.DESCRIPTION)).contains(needle);}throw new UnsupportedOperationException("calendar selection: "+s);}
  private static Cursor cursor(String[] projection,List<Map<String,Object>> rows){String[] cols=projection==null?(rows.isEmpty()?new String[]{"_id"}:rows.get(0).keySet().toArray(new String[0])):projection;MatrixCursor c=new MatrixCursor(cols);for(Map<String,Object> r:rows){Object[] v=new Object[cols.length];for(int i=0;i<cols.length;i++)v[i]=r.get(cols[i]);c.addRow(v);}return c;}
  private static void copy(ContentValues v,Map<String,Object> row){for(Map.Entry<String,Object> e:v.valueSet())row.put(e.getKey(),e.getValue());}
  private static Long eventId(Uri u){String base=CalendarContract.Events.CONTENT_URI.toString()+"/";if(!u.toString().startsWith(base))return null;return Long.valueOf(u.getLastPathSegment());}
  private static long num(Object x){return x instanceof Number?((Number)x).longValue():Long.parseLong(String.valueOf(x));}
 }
}
