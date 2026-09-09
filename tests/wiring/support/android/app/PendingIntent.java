package android.app;
import android.content.Context; import android.content.Intent; import java.util.*;
public final class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=1; public static final int FLAG_NO_CREATE=2; public static final int FLAG_IMMUTABLE=4;
 private static final Map<Key,PendingIntent> REGISTRY=new HashMap<>();
 private Intent intent; private final Key key;
 private PendingIntent(Key key,Intent intent){this.key=key;this.intent=intent.copy();}
 public static synchronized PendingIntent getBroadcast(Context c,int request,Intent i,int flags){return get("broadcast",request,i,flags);}
 public static synchronized PendingIntent getActivity(Context c,int request,Intent i,int flags){return get("activity",request,i,flags);}
 private static PendingIntent get(String type,int request,Intent i,int flags){Key k=new Key(type,request,i); PendingIntent old=REGISTRY.get(k); if((flags&FLAG_NO_CREATE)!=0)return old; if(old==null){old=new PendingIntent(k,i);REGISTRY.put(k,old);} else if((flags&FLAG_UPDATE_CURRENT)!=0)old.intent=i.copy(); return old;}
 public Intent getIntent(){return intent.copy();} public String identity(){return key.toString();}
 public static synchronized void reset(){REGISTRY.clear();} public static synchronized int registeredCount(){return REGISTRY.size();}
 static final class Key { final String type,action,data,component; final int request; Key(String t,int r,Intent i){type=t;request=r;action=i.getAction();data=i.getData()==null?null:i.getData().toString();component=i.getComponentClass()==null?null:i.getComponentClass().getName();} public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return request==k.request&&Objects.equals(type,k.type)&&Objects.equals(action,k.action)&&Objects.equals(data,k.data)&&Objects.equals(component,k.component);} public int hashCode(){return Objects.hash(type,request,action,data,component);} public String toString(){return type+":"+request+":"+action+":"+data+":"+component;} }
}
