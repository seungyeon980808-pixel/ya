package com.malhaedwo.pttprobe;
import android.app.*;
import android.content.*;
import android.os.Handler;
import java.nio.file.*;
public final class PttLifecycleTest {
 static int assertions;
 static final Context app=new Context();
 static void check(boolean v,String message){assertions++;if(!v)throw new AssertionError(message);}
 static PttService service(){PttService s=new PttService();s.onCreate();return s;}
 static Intent arm(){return new Intent(app,PttService.class).setAction(PttService.ACTION_ARM);}
 static void reset(){Context.stores.clear();Context.starts.clear();Context.microphone=true;Context.denyLaunch=false;Service.denyForeground=false;Service.promotionProbe=null;Handler.queue.clear();}
 public static void main(String[] args)throws Exception {
  reset();
  check(!PttReadiness.isEnabled(app),"fresh install defaults off");
  PttService.restoreIfEnabled(app); new VolumeKeyService().onServiceConnected();
  check(Context.starts.isEmpty(),"resume/accessibility never opt in");
  PttService s=service();
  check(s.onStartCommand(null,0,1)==Service.START_NOT_STICKY,"disabled null restart nonsticky");
  check(!PttService.isArmed(),"disabled null restart not armed");s.onDestroy();
  PttService.arm(app);check(PttReadiness.isEnabled(new Context()),"opt in persisted across contexts");
  check(Context.starts.size()==1,"explicit enable queues FGS");
  s=service();Service.promotionProbe=()->check(!PttService.isArmed(),"not ready before FGS accepted");
  check(s.onStartCommand(Context.starts.get(0),0,2)==Service.START_STICKY,"successful enable sticky");Service.promotionProbe=null;
  check(PttService.isArmed()&&s.foreground,"actual readiness after promotion");
  check(ScreenOffVolumeBridge.enabled,"existing screen-off bridge activated");
  check(WavRecorder.starts==0,"enable never records");
  PendingIntent notificationStop=Notification.lastStop;
  check("service".equals(notificationStop.type),"stop does not request microphone FGS");
  s.onDestroy();check(PttReadiness.isEnabled(app)&&!PttService.isArmed(),"destroy retains desired not actual");
  s=service();check(s.onStartCommand(null,0,3)==Service.START_STICKY,"enabled null sticky restart restored");
  check(WavRecorder.starts==0,"system restore never records");s.onDestroy();
  Context.starts.clear();new VolumeKeyService().onServiceConnected();check(Context.starts.size()==1,"bound accessibility requests restore");
  Intent queued=Context.starts.get(0);
  PttService.disarm(app);check(!PttReadiness.isEnabled(new Context()),"stop persists with instance null");
  s=service();check(s.onStartCommand(queued,0,4)==Service.START_NOT_STICKY&&!PttService.isArmed(),"queued ARM cannot resurrect explicit stop");
  check(s.onStartCommand(null,0,5)==Service.START_NOT_STICKY,"later system restart cannot resurrect stop");s.onDestroy();
  PttService.arm(app);s=service();s.onStartCommand(arm(),0,6);
  PttService.press(1,"screen");PttService.disarm(app);
  check(!PttService.isArmed(),"stop closes input gate immediately");Handler.drain();
  check(WavRecorder.starts==0,"queued press ignored after stop");
  check(!PttReadiness.isEnabled(app),"instance stop persisted");s.onDestroy();
  PttService.arm(app);s=service();s.onStartCommand(arm(),0,7);s.onDestroy();
  s=service();check(s.onStartCommand(notificationStop.intent,0,8)==Service.START_NOT_STICKY,"notification stop accepted after recreation");
  check(!PttReadiness.isEnabled(app),"notification durably disables");
  check(s.onStartCommand(arm(),0,9)==Service.START_NOT_STICKY,"notification stop blocks queued restore");s.onDestroy();
  PttReadiness.setEnabled(app,true);Context.microphone=false;
  Context.starts.clear();PttService.restoreIfEnabled(app);check(Context.starts.isEmpty(),"no launch without mic permission");
  s=service();check(s.onStartCommand(null,0,10)==Service.START_NOT_STICKY,"null restart permission denial nonsticky");
  check(!PttService.isArmed()&&PttReadiness.isEnabled(app),"permission denial waiting with intent retained");s.onDestroy();
  Context.microphone=true;Context.denyLaunch=true;PttService.restoreIfEnabled(app);
  check(!PttService.isArmed()&&PttReadiness.isEnabled(app),"FGS launch denial safely waiting");Context.denyLaunch=false;
  Service.denyForeground=true;s=service();
  check(s.onStartCommand(null,0,11)==Service.START_NOT_STICKY,"foreground promotion denial nonsticky");
  check(!PttService.isArmed()&&!s.foreground&&s.stopped,"promotion failure not false ready");
  check(PttReadiness.isEnabled(app),"promotion denial retains opt-in");s.onDestroy();Service.denyForeground=false;
  PttStore.clear(app);check(PttReadiness.isEnabled(app),"production diagnostic clear independent");
  check(app.getSharedPreferences("ptt_probe",0).getInt("error_count",0)==0,"diagnostics actually cleared");
  PttService.restoreIfEnabled(app);s=service();check(s.onStartCommand(null,0,12)==Service.START_STICKY,"later eligible recovery succeeds");
  check(WavRecorder.starts==0,"all recovery paths never auto record");s.onDestroy();
  String src=args[0];String main=Files.readString(Path.of(src,"MainActivity.java"));
  check(main.contains("super.onResume();\n        PttService.restoreIfEnabled(this);"),"foreground resume wired to tested controller");
  check(main.contains("disarmButton.setOnClickListener(v -> { PttService.disarm(this); refresh(); });"),"waiting state stop directly disables");
  String boot=Files.readString(Path.of(src,"BootReceiver.java"));check(!boot.contains("PttService"),"no boot microphone launch");
  System.out.println("PTT production lifecycle: "+assertions+" assertions PASS (host stubs, not device evidence)");
 }
}
