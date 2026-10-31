package app.veyra;
import android.app.*;
import android.os.*;
import app.veyra.data.NativeEngine;

/** Release native sockets after the app, including its PiP window, is no longer visible. */
public final class VeyraApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final Handler main=new Handler(Looper.getMainLooper());
    private int visible;
    private final Runnable idle=()->{if(visible==0&&!DownloadWorker.active()&&!EpisodeMonitor.active()&&!CastBridgeService.active())NativeEngine.closeAll();};
    void downloadFinished(){if(visible==0)main.postDelayed(idle,30000);}
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
    public void onActivityStarted(Activity activity){visible++;main.removeCallbacks(idle);}
    public void onActivityStopped(Activity activity){visible=Math.max(0,visible-1);if(visible==0)main.postDelayed(idle,30000);}
    public void onActivityCreated(Activity activity,Bundle state){}
    public void onActivityResumed(Activity activity){if(activity instanceof MainActivity){if(!ReleaseExperience.showIfNeeded(activity))AppUpdates.maybeCheck(activity);EpisodeMonitor.schedule(this);AutoBackup.schedule(this);AutoBackup.maybeRun(this);}}
    public void onActivityPaused(Activity activity){}
    public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    public void onActivityDestroyed(Activity activity){}
}
