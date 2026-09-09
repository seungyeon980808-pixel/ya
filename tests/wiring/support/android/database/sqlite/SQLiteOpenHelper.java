package android.database.sqlite;
import android.content.Context;
public abstract class SQLiteOpenHelper {
 private SQLiteDatabase db;
 private static int constructionCount;
 public SQLiteOpenHelper(Context c,String n,Object f,int v){constructionCount++;}
 public synchronized SQLiteDatabase getWritableDatabase(){if(db==null){db=new SQLiteDatabase();onCreate(db);}return db;}
 public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
 public static int constructionCount(){return constructionCount;}
 public static void resetConstructionCount(){constructionCount=0;}
 public abstract void onCreate(SQLiteDatabase db);
 public abstract void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion);
}
