package app.veyra;
import android.app.*;
import android.content.*;
import android.os.*;
import app.veyra.data.*;
import app.veyra.model.*;
import java.util.concurrent.*;
import com.google.android.gms.cast.framework.CastContext;

/** User-started foreground lifetime for the phone's temporary LAN media bridge. */
public final class CastBridgeService extends Service {
 static final String STOP="app.veyra.cast.STOP";static volatile State state;static volatile String error="";
 static final class State {
  final CastNetwork network;final CastRelay relay;final StreamSource source;final String title;final long startPosition;
  volatile String receiver="Choose a receiver";volatile DlnaReceiver dlna;volatile boolean google,loaded;
  State(CastNetwork n,CastRelay r,StreamSource s,String t,long p){network=n;relay=r;source=s;title=t;startPosition=p;}
 }
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();private PowerManager.WakeLock wake;private int generation;private long created;
 private static volatile boolean running;
 static boolean active(){return running;}
 static void start(Context c,String json,int index,long position){Intent i=new Intent(c,CastBridgeService.class).putExtra("playback",json).putExtra("source",index).putExtra("position",position);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
 static void stop(Context c){c.stopService(new Intent(c,CastBridgeService.class));}
 @Override public void onCreate(){super.onCreate();running=true;error="";created=SystemClock.elapsedRealtime();NotificationManager nm=getSystemService(NotificationManager.class);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("casting","TV playback",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,CastActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CastBridgeService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder notice=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"casting"):new Notification.Builder(this);startForeground(7303,notice.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Velixio · TV playback").setContentText("Keep this device on the same network. Tap for controls.").setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"Stop casting",stop).build()).build());
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Velixio:casting");wake.acquire(12*60*60*1000L);main.postDelayed(check,10000);
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}String json=intent.getStringExtra("playback");if(json==null){if(state==null)stopSelf();return START_NOT_STICKY;}
  final int run=++generation;State old=state;state=null;close(old);error="";created=SystemClock.elapsedRealtime();int index=intent.getIntExtra("source",0);long position=Math.max(0,intent.getLongExtra("position",0));
  worker.execute(()->{State ready=null;try{if(json.length()>2*1024*1024)throw new IllegalArgumentException();PlaybackRequest request=PlaybackJson.parseInternal(json);StreamSource source=request.sources.get(index);if(source.url.startsWith("http://")&&!CatalogBackend.isActiveProxy(source.url))throw new IllegalStateException();CastNetwork lan=CastNetwork.find(this);ready=new State(lan,new CastRelay(lan,source),source,request.title,position);ready.relay.startPosition=position;State result=ready;main.post(()->{if(run!=generation||!running)result.relay.close();else state=result;});}catch(Exception e){if(ready!=null)ready.relay.close();main.post(()->{if(run==generation){error="TV playback could not open. Check that Wi-Fi is connected and this stream is still playing on your device. DASH streams need screen mirroring.";stopSelf();}});}});return START_NOT_STICKY;
 }
 private final Runnable check=new Runnable(){public void run(){State s=state;long now=SystemClock.elapsedRealtime();if(now-created>12*60*60*1000L||(s!=null&&(!s.network.available(CastBridgeService.this)||now-s.relay.lastRequest>10*60*1000L))||(s==null&&now-created>45000)){error="TV playback ended because the session expired or its network changed.";stopSelf();return;}main.postDelayed(this,10000);}};
 private void close(State s){if(s==null)return;s.relay.close();if(s.dlna!=null){DlnaReceiver receiver=s.dlna;new Thread(()->{try{receiver.stop();}catch(Exception ignored){}},"Velixio receiver stop").start();}if(s.google)try{CastContext.getSharedInstance(this).getSessionManager().endCurrentSession(true);}catch(RuntimeException ignored){}}
 @Override public void onTaskRemoved(Intent root){stopSelf();}
 @Override public void onDestroy(){running=false;++generation;main.removeCallbacks(check);State s=state;state=null;close(s);worker.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);((VeyraApplication)getApplication()).downloadFinished();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
