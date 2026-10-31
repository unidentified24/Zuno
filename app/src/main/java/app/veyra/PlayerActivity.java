package app.veyra;

import android.app.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DataSource;
import app.veyra.data.StreamHttp;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import app.veyra.data.PlaybackJson;
import app.veyra.data.CatalogBackend;
import app.veyra.data.EpisodeSelection;
import app.veyra.data.WatchHistory;
import org.json.*;
import java.util.concurrent.*;
import app.veyra.model.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity {
    private PlaybackRequest request;
    private final ExecutorService resolver=Executors.newSingleThreadExecutor();
    private boolean resolving, started;
    private ExoPlayer player;
    private PlayerView view;
    private FrameLayout root;
    private LinearLayout header, footer, recovery;
    private TextView status, titleView;
    private Button miniButton, sourceButton;
    private String offlineId;private boolean offlinePinned;
    private int sizing;private final PlaybackMeter meter=new PlaybackMeter();private final PlayerAudio audioEffects=new PlayerAudio();
    private Runnable nextTicks;private android.app.Dialog nextPrompt;private View gestureLayer;private Button unlock;private boolean touchLocked,sleepAtEnd;private long sleepDeadline;private final Runnable sleepAction=()->{sleepDeadline=0;if(player!=null)player.pause();};private final Runnable nextAction=()->{if(started&&!isFinishing()){if(nextPrompt!=null)nextPrompt.dismiss();resolveSource(selectedType(),1);}};
    private final Runnable delayedBuffer=()->{if(player!=null&&player.getPlaybackState()==Player.STATE_BUFFERING)view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);};
    private boolean controlsVisible=true, cropVideo;
    private MediaSession session;
    private int sourceIndex;
    private long position;
    private float speed=1f;
    private TrackSelectionParameters trackPreferences;
    private boolean playWhenReady=true, enteringPip;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable saveProgress=new Runnable() {
        public void run() { meter.flush(PlayerActivity.this);remember(); handler.postDelayed(this,5000); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getIntent().putExtra("_profile",ProfileStore.id(this));Ui.configure(this);
        if(saved!=null){sleepDeadline=saved.getLong("sleepDeadline",0);sleepAtEnd=saved.getBoolean("sleepAtEnd",false);if(sleepDeadline>0)handler.postDelayed(sleepAction,Math.max(0,sleepDeadline-android.os.SystemClock.elapsedRealtime()));cropVideo=saved.getBoolean("cropVideo",false);sizing=saved.getInt("sizing",cropVideo?1:0);}
        else if(!"short".equals(getIntent().getStringExtra("kind")))setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if(saved==null && getSharedPreferences("settings",MODE_PRIVATE).getBoolean("rememberSpeed",true))speed=getSharedPreferences("settings",MODE_PRIVATE).getFloat("speed",1f);
        if(saved!=null){position=saved.getLong("position",0);playWhenReady=saved.getBoolean("playing",true);speed=saved.getFloat("speed",1f);}
        else try{position=app.veyra.ProfileStore.prefs(this,"resume").getLong(new JSONObject(getIntent().getStringExtra("playback")).optString("contentId"),0);}catch(Exception ignored){}
        offlineId=getIntent().getStringExtra("offlineId");
        try { request=offlineId!=null?OfflineStore.request(this,offlineId):PlaybackJson.parseInternal(saved!=null && saved.containsKey("playback")?saved.getString("playback"):getIntent().getStringExtra("playback")); }
        catch(Exception e) { if(getIntent().hasExtra("vodId"))recoverPlayback();else finish(); return; }
        position=app.veyra.ProfileStore.prefs(this,"resume").getLong(request.contentId,0);
        if(saved!=null) { sourceIndex=saved.getInt("source",0); position=saved.getLong("position",position); playWhenReady=saved.getBoolean("playing",true); speed=saved.getFloat("speed",1f); }
        if(sourceIndex<0 || sourceIndex>=request.sources.size()) sourceIndex=0;
        createPlayerUi();
    }
    private void createPlayerUi() {
        root=new FrameLayout(this);root.setBackgroundColor(android.graphics.Color.BLACK);
        view=(PlayerView)getLayoutInflater().inflate(R.layout.velixio_player,root,false);view.setShowSubtitleButton(false);view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        view.setContentDescription("Video player");view.setControllerShowTimeoutMs(3000);view.setControllerHideOnTouch(true);
        androidx.media3.ui.DefaultTimeBar timeline=view.findViewById(androidx.media3.ui.R.id.exo_progress);if(timeline!=null){timeline.setPlayedColor(Ui.ACCENT);timeline.setScrubberColor(Ui.ACCENT);}
        applySizing();applyCaptionAppearance();
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        header=Ui.column(this);header.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xcc000000,0x00000000}));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.quiet(this,"‹");back.setContentDescription("Back to title");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        titleView=Ui.text(this,request.title,14,Ui.INK);titleView.setMaxLines(1);titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(titleView,new LinearLayout.LayoutParams(0,-2,1));
        sourceButton=Ui.chip(this,sourceName());sourceButton.setContentDescription("Change Original, Dub or available version");sourceButton.setOnClickListener(v->chooseSource());
        Button rotate=Ui.icon(this,"rotate","Rotate screen");rotate.setOnClickListener(v->setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));bar.addView(rotate,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        miniButton=Ui.icon(this,"pip","Picture in picture · floating mini player");miniButton.setOnClickListener(v->enterMiniPlayer());miniButton.setVisibility(supportsPip()?View.VISIBLE:View.GONE);bar.addView(miniButton,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        Button settings=Ui.icon(this,"options","Player controls");settings.setContentDescription("Playback options");settings.setOnClickListener(v->playbackOptions());bar.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        header.addView(bar);root.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout tools=view.findViewById(R.id.velixio_player_tools);
        if(offlineId==null){sourceButton.setText(sourceName()+" ▾");tools.addView(sourceButton,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));}
        Button quality=Ui.quiet(this,"Quality");quality.setOnClickListener(v->chooseTracks(C.TRACK_TYPE_VIDEO));tools.addView(quality);
        Button size=Ui.quiet(this,new String[]{"Fit","Fill","Stretch"}[sizing]);size.setContentDescription("Picture size: Fit, Fill or Stretch");size.setOnClickListener(v->{sizing=(sizing+1)%3;applySizing();size.setText(new String[]{"Fit","Fill","Stretch"}[sizing]);});tools.addView(size);
        Button captions=Ui.quiet(this,"Captions");captions.setOnClickListener(v->{if(player!=null&&player.getCurrentTracks().isTypeSupported(C.TRACK_TYPE_TEXT))chooseTracks(C.TRACK_TYPE_TEXT);else Toast.makeText(this,"No captions are available for this version",Toast.LENGTH_SHORT).show();});tools.addView(captions);
        if(offlineId==null){Button cast=Ui.quiet(this,"Cast to TV");cast.setContentDescription("Cast this version to a TV or receiver");cast.setOnClickListener(v->openCast());tools.addView(cast);}
        if(getIntent().hasExtra("vodId")){Button episodes=Ui.quiet(this,"Episodes");episodes.setOnClickListener(v->showEpisodes());tools.addView(episodes);}

        footer=Ui.column(this);footer.setBackgroundColor(0x99000000);status=Ui.text(this,"",13,Ui.INK);status.setMaxLines(3);footer.addView(status);recovery=new LinearLayout(this);Button retryPlayback=Ui.chip(this,"Retry playback");retryPlayback.setOnClickListener(v->{if(offlineId==null&&getIntent().hasExtra("vodId"))refreshSource(selectedType());else if(player!=null){player.prepare();player.play();}});recovery.addView(retryPlayback);if(offlineId==null){Button switchVersion=Ui.quiet(this,"Switch version");switchVersion.setOnClickListener(v->chooseSource());recovery.addView(switchVersion);}recovery.setVisibility(View.GONE);footer.addView(recovery);
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);notice.topMargin=Ui.dp(this,56);root.addView(footer,notice);
        status.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){} public void onTextChanged(CharSequence t,int start,int before,int count){updateChrome();}public void afterTextChanged(android.text.Editable t){}});
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{controlsVisible=visibility==View.VISIBLE;if(gestureLayer!=null)gestureLayer.setVisibility((touchLocked||!controlsVisible)&&!isInPictureInPictureMode()?View.VISIBLE:View.GONE);updateChrome();});
        view.setFullscreenButtonClickListener(fullscreen->setRequestedOrientation(fullscreen?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT));
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updatePip());
        installGestureLayer();setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{
            int left=insets.getSystemWindowInsetLeft(),right=insets.getSystemWindowInsetRight(),top=insets.getSystemWindowInsetTop();
            if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());right=Math.max(right,cutout.getSafeInsetRight());top=Math.max(top,cutout.getSafeInsetTop());}
            View controller=view.findViewById(androidx.media3.ui.R.id.exo_controller);if(controller!=null)controller.setPadding(left,0,right,insets.getSystemWindowInsetBottom());
            header.setPadding(left+Ui.dp(this,8),top,right+Ui.dp(this,8),Ui.dp(this,8));footer.setPadding(left+Ui.dp(this,16),0,right+Ui.dp(this,16),0);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)footer.getLayoutParams();lp.topMargin=top+Ui.dp(this,56);footer.setLayoutParams(lp);
            return insets;
        });applyPlayerLayout();
    }
    private void installGestureLayer(){
        final android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);gestureLayer=new View(this);gestureLayer.setContentDescription("Player gestures: double tap to seek; swipe left for brightness or right for volume");gestureLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams zone=new FrameLayout.LayoutParams(-1,-1);root.addView(gestureLayer,zone);unlock=Ui.chip(this,"Unlock controls");unlock.setContentDescription("Unlock player touch controls");FrameLayout.LayoutParams key=new FrameLayout.LayoutParams(-2,Ui.dp(this,56),Gravity.TOP|Gravity.END);key.topMargin=Ui.dp(this,24);key.rightMargin=Ui.dp(this,20);root.addView(unlock,key);unlock.setVisibility(View.GONE);unlock.setOnClickListener(v->{touchLocked=false;unlock.setVisibility(View.GONE);view.showController();gestureLayer.setVisibility(View.GONE);updateChrome();});
        GestureDetector detector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){float initialBrightness;int initialVolume;boolean moved;public boolean onDown(android.view.MotionEvent e){moved=false;float b=getWindow().getAttributes().screenBrightness;initialBrightness=b<0?.5f:b;initialVolume=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);return true;}public boolean onSingleTapConfirmed(android.view.MotionEvent e){if(!touchLocked)view.showController();return true;}public boolean onDoubleTap(android.view.MotionEvent e){if(!touchLocked&&player!=null){long seek=player.getCurrentPosition()+(e.getX()<gestureLayer.getWidth()/2f?-10000:10000);player.seekTo(Math.max(0,Math.min(player.getDuration()>0?player.getDuration():Long.MAX_VALUE,seek)));}return true;}public boolean onScroll(android.view.MotionEvent start,android.view.MotionEvent end,float dx,float dy){if(touchLocked||start==null)return true;float delta=(start.getY()-end.getY())/Math.max(1,gestureLayer.getHeight());if(Math.abs(delta)<.04f&&!moved)return true;moved=true;if(start.getX()<gestureLayer.getWidth()/2f){android.view.WindowManager.LayoutParams lp=getWindow().getAttributes();lp.screenBrightness=Math.max(.05f,Math.min(1f,initialBrightness+delta));getWindow().setAttributes(lp);}else{int max=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,initialVolume+Math.round(delta*max))),0);}return true;}});gestureLayer.setOnTouchListener((v,event)->detector.onTouchEvent(event));gestureLayer.setVisibility(controlsVisible?View.GONE:View.VISIBLE);
    }
    private void sleepMenu(){ActionSheet.show(this,"Sleep timer",new String[]{"Off","15 minutes","30 minutes","60 minutes","At the end of this episode"},index->{handler.removeCallbacks(sleepAction);sleepDeadline=0;sleepAtEnd=index==4;if(index>0&&index<4){long delay=new long[]{0,15,30,60}[index]*60000;sleepDeadline=android.os.SystemClock.elapsedRealtime()+delay;handler.postDelayed(sleepAction,delay);}Toast.makeText(this,index==0?"Sleep timer off":index==4?"Will stop at the end of this episode":"Sleep timer set",Toast.LENGTH_SHORT).show();});}
    private void captionAppearance(){ActionSheet.show(this,"Caption appearance",new String[]{"Standard size","Large","Extra large","White on black","Yellow on black","White with shadow","Black on white","Monospace on black"},index->{androidx.media3.ui.SubtitleView captions=view.getSubtitleView();if(captions==null)return;android.content.SharedPreferences.Editor edit=getSharedPreferences("settings",0).edit();if(index<3)edit.putInt("captionSize",index);else edit.putInt("captionStyle",index);edit.apply();applyCaptionAppearance();});}
    private void applyCaptionAppearance(){androidx.media3.ui.SubtitleView captions=view.getSubtitleView();if(captions==null)return;captions.setApplyEmbeddedStyles(false);int size=getSharedPreferences("settings",0).getInt("captionSize",0),style=getSharedPreferences("settings",0).getInt("captionStyle",3);captions.setFractionalTextSize(new float[]{.0533f,.068f,.083f}[Math.max(0,Math.min(2,size))]);captions.setStyle(new androidx.media3.ui.CaptionStyleCompat(style==6?android.graphics.Color.BLACK:style==4?android.graphics.Color.YELLOW:android.graphics.Color.WHITE,style==5?android.graphics.Color.TRANSPARENT:style==6?android.graphics.Color.WHITE:android.graphics.Color.BLACK,android.graphics.Color.TRANSPARENT,style==5?androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW:style==6?androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE:androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,android.graphics.Color.BLACK,style==7?android.graphics.Typeface.MONOSPACE:null));}
    private void captionTiming(){String key=request.contentId+":"+request.sources.get(sourceIndex).id;int current=ProfileStore.prefs(this,"caption-timing").getInt(key,0);ActionSheet.show(this,"SRT / WebVTT timing · "+current+" ms",new String[]{"Earlier by 500 ms","Reset timing","Later by 500 ms"},index->{int offset=index==1?0:Math.max(-10000,Math.min(10000,current+(index==0?-500:500)));ProfileStore.prefs(this,"caption-timing").edit().putInt(key,offset).apply();release();if(started)startPlayer();Toast.makeText(this,"External SRT/WebVTT offset: "+offset+" ms",Toast.LENGTH_SHORT).show();});}
    private void nextCountdown(){if(nextPrompt!=null&&nextPrompt.isShowing())return;nextPrompt=new android.app.Dialog(this);LinearLayout panel=Ui.column(this);panel.setPadding(Ui.dp(this,24),Ui.dp(this,20),Ui.dp(this,24),Ui.dp(this,20));TextView countdown=Ui.text(this,"Next episode in 10 seconds",22,Ui.INK);panel.addView(countdown);nextTicks=new Runnable(){int seconds=10;public void run(){if(nextPrompt==null||!nextPrompt.isShowing())return;countdown.setText("Next episode in "+seconds+" seconds");if(seconds-->0)handler.postDelayed(this,1000);}};Button now=Ui.primary(this,"Play now"),cancel=Ui.quiet(this,"Cancel");panel.addView(now);panel.addView(cancel);now.setOnClickListener(v->{nextPrompt.dismiss();if(started)resolveSource(selectedType(),1);});cancel.setOnClickListener(v->nextPrompt.dismiss());nextPrompt.setContentView(panel);nextPrompt.setOnDismissListener(v->{handler.removeCallbacks(nextAction);if(nextTicks!=null)handler.removeCallbacks(nextTicks);});nextPrompt.show();handler.post(nextTicks);handler.postDelayed(nextAction,10000);}
    private void openCast(){if(request==null||offlineId!=null)return;long at=player==null?position:player.getCurrentPosition();if(player!=null)player.pause();remember();android.content.Intent intent=new android.content.Intent(this,CastActivity.class);if(!CastBridgeService.active())intent.putExtra("playback",getIntent().getStringExtra("playback")).putExtra("source",sourceIndex).putExtra("position",at);startActivity(intent);}
    private String sourceName(){if(offlineId!=null)return "Offline";if(request==null)return "Version";return request.sources.get(Math.max(0,Math.min(sourceIndex,request.sources.size()-1))).label;}
    private void applySizing(){int[] modes={androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL};sizing=Math.max(0,Math.min(2,sizing));view.setResizeMode(modes[sizing]);cropVideo=sizing==1;}
    private void playbackOptions(){
        view.showController();List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add("Speed · "+speed+"×");actions.add(()->ActionSheet.show(this,"Playback speed",new String[]{"0.75×","1× · Normal","1.25×","1.5×","2×"},index->{speed=new float[]{.75f,1f,1.25f,1.5f,2f}[index];if(player!=null)player.setPlaybackSpeed(speed);}));
        labels.add("Lock touch controls");actions.add(()->{touchLocked=true;view.hideController();header.setVisibility(View.GONE);gestureLayer.setVisibility(View.VISIBLE);unlock.setVisibility(View.VISIBLE);});
        labels.add("Screen mirroring");actions.add(()->{try{startActivity(new android.content.Intent("android.settings.CAST_SETTINGS"));}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Use your device’s screen-mirroring controls",Toast.LENGTH_LONG).show();}});
        if(offlineId==null){labels.add("Cast to TV");actions.add(()->openCast());}
        labels.add("Audio enhancement");actions.add(()->ActionSheet.show(this,"Audio enhancement",new String[]{"Original sound","Dialogue focus","Gentle volume boost","Night mode · level loud scenes"},mode->{getSharedPreferences("settings",0).edit().putInt("audioEnhancement",mode).apply();if(player!=null){int session=player.getAudioSessionId();if(mode!=0&&session<=0)Toast.makeText(this,"The effect will apply when audio starts",Toast.LENGTH_SHORT).show();else if(!audioEffects.attach(session,mode))Toast.makeText(this,"This effect isn’t available on this device or audio output",Toast.LENGTH_LONG).show();}}));
        labels.add("Sleep timer");actions.add(()->sleepMenu());
        labels.add("Caption appearance");actions.add(()->captionAppearance());if(request!=null&&request.sources.get(sourceIndex).subtitles.stream().anyMatch(t->!t.mimeType.equals("application/ttml+xml"))){labels.add("External caption timing");actions.add(()->captionTiming());}
        labels.add("Video quality");actions.add(()->chooseTracks(C.TRACK_TYPE_VIDEO));
        if(player!=null&&player.getCurrentTracks().isTypeSupported(C.TRACK_TYPE_TEXT)){labels.add("Captions");actions.add(()->chooseTracks(C.TRACK_TYPE_TEXT));}
        if(offlineId==null&&!getIntent().getBooleanExtra("trailer",false)){labels.add("Download this episode");actions.add(()->DownloadWorker.start(this,getIntent().getStringExtra("playback"),getIntent().getStringExtra("poster")));}
        if(getIntent().hasExtra("vodId")){labels.add("Episodes & connection");actions.add(()->ActionSheet.show(this,"Episode controls",new String[]{"Previous episode","Next episode","Reconnect current stream"},index->{if(index<2)resolveSource(selectedType(),index==0?-1:1);else refreshSource(selectedType());}));}
        ActionSheet.show(this,"Watching controls",labels.toArray(new String[0]),index->actions.get(index).run());
    }
    private void showEpisodes(){
        if(resolving)return;resolving=true;status.setText("Loading episodes…");
        resolver.execute(()->{try{JSONObject detail=CatalogBackend.get(this,getIntent().getStringExtra("provider")).detail(getIntent().getStringExtra("vodId"),selectedType(),getIntent().getStringExtra("kind"));JSONArray episodes=detail.getJSONArray("vod_collection");String[] names=new String[episodes.length()];for(int i=0;i<names.length;i++){String name=episodes.getJSONObject(i).optString("title");names[i]=name.isEmpty()||name.matches("[0-9. ]+")?"Episode "+(i+1):name;}runOnUiThread(()->{if(isFinishing()||isDestroyed())return;resolving=false;status.setText("");ActionSheet.show(this,"Episodes",names,index->{try{openListedEpisode(detail,episodes.getJSONObject(index));}catch(Exception e){status.setText("This episode could not be opened");}});});}catch(Exception e){runOnUiThread(()->{resolving=false;status.setText("Episodes could not load. Try again.");});}});
    }
    private void openListedEpisode(JSONObject detail,JSONObject episode){
        if(resolving)return;resolving=true;status.setText("Opening episode…");String provider=getIntent().getStringExtra("provider"),type=selectedType();resolver.execute(()->{try{String json=CatalogBackend.get(this,provider).playbackJson(detail,episode,sourceName());PlaybackRequest replacement=PlaybackJson.parseInternal(json);String key=EpisodeSelection.key(episode);String variants=detail.optJSONArray("audio_type_option")==null?"[]":detail.optJSONArray("audio_type_option").toString();runOnUiThread(()->{getIntent().putExtra("hasNextEpisode",EpisodeAvailability.hasNext(detail,episode));completeResolution(replacement,json,type,key,variants,1);});}catch(Exception e){runOnUiThread(()->{resolving=false;status.setText("This episode could not open. Current playback is preserved.");});}});
    }
    private void updateChrome(){if(gestureLayer!=null)gestureLayer.setVisibility(!isMini()&&(touchLocked||!controlsVisible)?View.VISIBLE:View.GONE);if(unlock!=null)unlock.setVisibility(!isMini()&&touchLocked?View.VISIBLE:View.GONE);if(touchLocked&&header!=null){header.setVisibility(View.GONE);footer.setVisibility(View.GONE);return;}
        if(header==null||footer==null)return;
        boolean visible=controlsVisible&&!isMini();header.setVisibility(visible?View.VISIBLE:View.GONE);
        footer.setVisibility(visible&&status.length()>0?View.VISIBLE:View.GONE);
    }
    private void immersive(){
        if(root==null||isMini())return;
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null){bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);bars.hide(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(attributes);}
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(Ui.tv(this)&&view!=null&&event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_CENTER&&!controlsVisible){view.showController();return true;}return super.dispatchKeyEvent(event);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)immersive();}
    private void recoverPlayback() {
        if(resolving)return;resolving=true;
        LinearLayout loading=Ui.column(this);loading.addView(Ui.text(this,"Restoring playback…",22,Ui.INK));
        TextView message=Ui.text(this,"Reconnecting to the current episode",16,Ui.MUTED);loading.addView(message);
        Button back=Ui.button(this,"Back to library");back.setOnClickListener(v->finish());loading.addView(back);setContentView(loading);Ui.insets(this,loading,20);
        resolver.execute(()->{try {
            String provider=getIntent().getStringExtra("provider");CatalogBackend backend=CatalogBackend.get(this,provider==null?"current":provider);
            JSONObject detail=backend.detail(getIntent().getStringExtra("vodId"),getIntent().getStringExtra("audioType")==null?"0":getIntent().getStringExtra("audioType"),getIntent().getStringExtra("kind"));
            JSONObject chosen=EpisodeSelection.select(detail.getJSONArray("vod_collection"),getIntent().getStringExtra("collection"),0);
            String json=backend.playbackJson(detail,chosen,"Selected source");
            PlaybackRequest restored=PlaybackJson.parseInternal(json);
            runOnUiThread(()->{
                if(isDestroyed() || isFinishing())return;
                resolving=false;request=restored;sourceIndex=0;getIntent().putExtra("playback",json);
                createPlayerUi();applyPlayerLayout();if(started)startPlayer();
            });
        }catch(Exception|LinkageError e){runOnUiThread(()->{if(!isDestroyed() && !isFinishing()){resolving=false;message.setText("Playback could not be restored. Return to the title to reconnect.");}});}});
    }
    private String selectedType() {
        String type=getIntent().getStringExtra("audioType");return type==null?"0":type;
    }
    private void refreshSource(String type) { resolveSource(type,0); }
    private void resolveSource(String type,int episodeOffset) {
        if(resolving)return;
        resolving=true;status.setText(episodeOffset==0?"Refreshing source…":"Opening episode…");
        String id=getIntent().getStringExtra("vodId"), collection=getIntent().getStringExtra("collection");
        String provider=getIntent().getStringExtra("provider"),kind=getIntent().getStringExtra("kind");
        resolver.execute(()->{
            try {
                CatalogBackend backend=CatalogBackend.get(this,provider==null?"current":provider);
                JSONObject detail=backend.detail(id,type,kind);
                JSONObject episode=EpisodeSelection.select(detail.getJSONArray("vod_collection"),collection,episodeOffset);
                String nextCollection=EpisodeSelection.key(episode);
                String label=type;JSONArray options=detail.optJSONArray("audio_type_option");
                if(options!=null)for(int i=0;i<options.length();i++){JSONObject o=options.getJSONObject(i);if(o.optString("type").equals(type))label=o.optString("type_name",type);}
                String json=backend.playbackJson(detail,episode,label);PlaybackRequest replacement=PlaybackJson.parseInternal(json);
                String variants=options==null?"[]":options.toString();
                runOnUiThread(()->{getIntent().putExtra("hasNextEpisode",EpisodeAvailability.hasNext(detail,episode));completeResolution(replacement,json,type,nextCollection,variants,episodeOffset);});
            }catch(Exception|LinkageError e){runOnUiThread(()->{
                if(!isDestroyed() && !isFinishing()){
                    resolving=false;
                    String message=e.getMessage();
                    status.setText("This is the first episode".equals(message) || "This is the last episode".equals(message)?message:"The source could not be refreshed. Please retry or return to the library.");
                }
            });}
        });
    }
    // Called on the UI thread after the backend resolves fresh playback data.
    void completeResolution(PlaybackRequest replacement,String json,String type,String collection,String variants,int offset) {
        if(isDestroyed() || isFinishing())return;
        resolving=false;release();request=replacement;sourceIndex=0;
        if(offset!=0){position=app.veyra.ProfileStore.prefs(this,"resume").getLong(request.contentId,0);playWhenReady=true;}
        if(trackPreferences!=null)trackPreferences=trackPreferences.buildUpon().clearOverrides().build();
        titleView.setText(request.title);sourceButton.setText(sourceName());
        getIntent().putExtra("playback",json).putExtra("audioType",type).putExtra("collection",collection).putExtra("variants",variants);
        if(started)startPlayer();
    }
    private void chooseSource() {
        try{JSONArray connections=new JSONArray(getIntent().getStringExtra("connections"));if(connections.length()>1){ActionSheet.show(this,"Playback source",new String[]{"Original / Dub / available versions","Change connection"},i->{if(i==0)chooseVersion();else{String[] labels=new String[connections.length()];for(int n=0;n<labels.length;n++)labels[n]="Connection "+(n+1)+(connections.optJSONObject(n).optString("provider").equals(getIntent().getStringExtra("provider"))?" · current":"");ActionSheet.show(this,"Available connections",labels,index->switchConnection(connections.optJSONObject(index)));}});return;}}catch(Exception ignored){}chooseVersion();
    }
    private void switchConnection(JSONObject choice){if(resolving||choice==null)return;resolving=true;status.setText("Opening connection…");String provider=choice.optString("provider"),id=choice.optString("id"),kind=getIntent().getStringExtra("kind");resolver.execute(()->{try{CatalogBackend backend=CatalogBackend.get(this,provider);JSONObject detail=backend.detail(id,"0",kind);JSONObject episode=EpisodeSelection.select(detail.getJSONArray("vod_collection"),getIntent().getStringExtra("collection"),0);String json=backend.playbackJson(detail,episode,"Selected version");PlaybackRequest replacement=PlaybackJson.parseInternal(json);String episodeKey=EpisodeSelection.key(episode);runOnUiThread(()->{if(isDestroyed()||isFinishing())return;getIntent().putExtra("hasNextEpisode",EpisodeAvailability.hasNext(detail,episode));getIntent().putExtra("provider",provider).putExtra("vodId",id);completeResolution(replacement,json,"0",episodeKey,detail.optJSONArray("audio_type_option")==null?"[]":detail.optJSONArray("audio_type_option").toString(),0);});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){resolving=false;status.setText("This connection could not open the episode. Current playback is preserved.");}});}});}
    private void chooseVersion() {
        if(getIntent().hasExtra("vodId")) {
            try {JSONArray variants=new JSONArray(getIntent().getStringExtra("variants"));
                if(variants.length()>0){String[] labels=new String[variants.length()];for(int i=0;i<labels.length;i++)labels[i]=variants.getJSONObject(i).getString("type_name");
                    ActionSheet.show(this,"Choose version",labels,i->{try{refreshSource(variants.getJSONObject(i).optString("type"));}catch(JSONException ignored){}});return;}
            }catch(Exception ignored){}
        }

        ActionSheet.show(this,"Choose version",request.sources.stream().map(source->source.label).toArray(String[]::new),index->{if(index!=sourceIndex){release();sourceIndex=index;startPlayer();sourceButton.setText(sourceName());}});
    }
    private void chooseTracks(int type) {
        if(player==null) return;
        List<String> labels=new ArrayList<>(); List<Runnable> choices=new ArrayList<>();
        labels.add(type==C.TRACK_TYPE_TEXT?"Off":"Auto");
        choices.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT).build()));
        for(Tracks.Group group:player.getCurrentTracks().getGroups()) {
            if(group.getType()!=type) continue;
            for(int i=0;i<group.length;i++) {
                if(!group.isTrackSupported(i)) continue;
                Format f=group.getTrackFormat(i); final int index=i;
                String label=type==C.TRACK_TYPE_VIDEO ? (f.height>0?f.height+"p":"Video")+(f.bitrate>0?String.format(Locale.US," · %.1f Mbps",f.bitrate/1000000f):"") : (f.label!=null?f.label:(f.language!=null?f.language:"Track "+(i+1)));
                labels.add(label+(group.isTrackSelected(i)?" ✓":""));
                choices.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).build()));
            }
        }
        if(labels.size()==1) { Toast.makeText(this,"No additional tracks in this stream",Toast.LENGTH_SHORT).show(); return; }
        ActionSheet.show(this,type==C.TRACK_TYPE_VIDEO?"Stream quality":"Captions",labels.toArray(new String[0]),i->{if(player!=null)choices.get(i).run();});
    }
    private void startPlayer() {
        if(!started || request==null || player!=null || view==null || isFinishing()) return;
        StreamSource source=request.sources.get(sourceIndex);
        if(offlineId==null && source.url.startsWith("http://127.0.0.1:") && !CatalogBackend.isActiveProxy(source.url)) {recoverPlayback();return;}

        if(offlineId!=null&&!offlinePinned){OfflineStore.pin(offlineId);offlinePinned=true;}
        DataSource.Factory http=offlineId==null?StreamHttp.factory(source):OfflineStore.readOnly(this,offlineId);if(offlineId==null){Set<String> shifted=new HashSet<>();for(SubtitleTrack caption:source.subtitles)if(!caption.mimeType.equals("application/ttml+xml"))shifted.add(caption.url);http=CaptionTiming.factory(http,shifted,ProfileStore.prefs(this,"caption-timing").getInt(request.contentId+":"+source.id,0));}
        player=new ExoPlayer.Builder(this).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if(state==Player.STATE_READY){recovery.setVisibility(View.GONE);view.setControllerShowTimeoutMs(3000);}handler.removeCallbacks(delayedBuffer);view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);if(state==Player.STATE_BUFFERING)handler.postDelayed(delayedBuffer,800);status.setText(state==Player.STATE_ENDED?"Playback finished":"");
                syncSession(); updatePip();
                if(state==Player.STATE_ENDED&&sleepAtEnd){sleepAtEnd=false;player.pause();return;}
                if(state==Player.STATE_ENDED && getIntent().hasExtra("vodId") && getSharedPreferences("settings",MODE_PRIVATE).getBoolean("autoplay",false)) {
                    remember();if(sleepAtEnd){sleepAtEnd=false;player.pause();}else if(getIntent().getBooleanExtra("hasNextEpisode",false))nextCountdown();
                }
            }
            @Override public void onIsPlayingChanged(boolean playing) {meter.state(PlayerActivity.this,playing); view.setKeepScreenOn(playing); syncSession(); updatePip(); }
            @Override public void onPlayWhenReadyChanged(boolean ready,int reason) { syncSession(); updatePip(); }
            @Override public void onAudioSessionIdChanged(int id){audioEffects.attach(id,getSharedPreferences("settings",0).getInt("audioEnhancement",0));}
            @Override public void onVideoSizeChanged(VideoSize size) { updatePip(); }
            @Override public void onPlaybackParametersChanged(PlaybackParameters params) { speed=params.speed;if(getSharedPreferences("settings",MODE_PRIVATE).getBoolean("rememberSpeed",true))getSharedPreferences("settings",MODE_PRIVATE).edit().putFloat("speed",speed).apply();syncSession(); }
            @Override public void onPlayerError(PlaybackException error) {
                recovery.setVisibility(View.VISIBLE);view.setControllerShowTimeoutMs(0);view.showController();status.setText(offlineId!=null?"This offline copy could not play. Delete it and download the title again.":"This stream could not play. It may have expired, lost its connection or require an unsupported format. Retry playback or switch version below."); syncSession(); updatePip();
            }
        });
        List<MediaItem.SubtitleConfiguration> captions=new ArrayList<>();
        for(SubtitleTrack caption:source.subtitles)captions.add(new MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(caption.url)).setId(caption.id).setLabel(caption.label).setLanguage(caption.language.isEmpty()?null:caption.language).setMimeType(caption.mimeType).build());
        if(trackPreferences==null) {
            TrackSelectionParameters.Builder preferences=player.getTrackSelectionParameters().buildUpon();
            int height=getSharedPreferences("settings",MODE_PRIVATE).getInt("qualityHeight",0);
            if(height>0)preferences.setMaxVideoSize(Integer.MAX_VALUE,height);
            if(height==-1)preferences.setForceHighestSupportedBitrate(true);
            trackPreferences=preferences.build();
        }
        player.setTrackSelectionParameters(trackPreferences);
        player.setMediaItem(new MediaItem.Builder().setMediaId(request.contentId+":"+source.id).setUri(source.url).setMimeType(source.mimeType).setSubtitleConfigurations(captions).build());
        view.setPlayer(player); player.seekTo(position); player.setPlaybackSpeed(speed); player.prepare(); player.setPlayWhenReady(playWhenReady);
        session=new MediaSession(this,"VelixioPlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if(player!=null)player.play(); }
            @Override public void onPause() { if(player!=null)player.pause(); }
            @Override public void onSeekTo(long p) { if(player!=null)player.seekTo(Math.max(0,p)); }
            @Override public void onStop() { finish(); }
        },handler);
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,request.title).build());
        session.setActive(true); syncSession(); handler.post(saveProgress);
    }
    private void syncSession() {
        if(session==null || player==null)return;
        int state=player.getPlayerError()!=null?PlaybackState.STATE_ERROR:player.isPlaying()?PlaybackState.STATE_PLAYING:player.getPlaybackState()==Player.STATE_BUFFERING?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(state,Math.max(0,player.getCurrentPosition()),player.isPlaying()?speed:0f).build());
    }

    private boolean supportsPip() { return Build.VERSION.SDK_INT>=26 && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE); }
    private boolean isMini() { return enteringPip || (Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()); }
    private boolean canAutoPip() { return player!=null && PipPolicy.autoEnter(player.getPlayWhenReady(),player.getPlayerError()!=null,player.getPlaybackState()==Player.STATE_IDLE,player.getPlaybackState()==Player.STATE_ENDED); }
    @androidx.annotation.RequiresApi(26)
    private PictureInPictureParams pipParams() {
        VideoSize size=player==null?VideoSize.UNKNOWN:player.getVideoSize();
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new Rational(PipPolicy.ratioThousandths(size.width,size.height,size.pixelWidthHeightRatio),1000));
        Rect rect=new Rect(); if(view.getGlobalVisibleRect(rect) && !rect.isEmpty())b.setSourceRectHint(rect);
        if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(canAutoPip()).setSeamlessResizeEnabled(true);
        return b.build();
    }
    private void updatePip() {
        if(Build.VERSION.SDK_INT<26 || !supportsPip() || view==null || isFinishing())return;
        try { setPictureInPictureParams(pipParams()); } catch(IllegalArgumentException|IllegalStateException ignored) { /* Device policy can disable PiP. */ }
    }
    private void enterMiniPlayer() {
        if(Build.VERSION.SDK_INT<26 || !supportsPip() || player==null)return;
        try { if(!enterPictureInPictureMode(pipParams()))Toast.makeText(this,"Allow picture-in-picture for Velixio in Android settings",Toast.LENGTH_LONG).show(); }
        catch(IllegalStateException|IllegalArgumentException e) { Toast.makeText(this,"Picture-in-picture is unavailable on this device",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if(Build.VERSION.SDK_INT<31 && canAutoPip())enterMiniPlayer(); }
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration config) { super.onPictureInPictureModeChanged(inPip,config); enteringPip=false; applyPlayerLayout(); }
    @androidx.annotation.RequiresApi(31)
    @Override public void onPictureInPictureUiStateChanged(PictureInPictureUiState state) {
        super.onPictureInPictureUiStateChanged(state);
        if(Build.VERSION.SDK_INT>=35 && state.isTransitioningToPip()) { enteringPip=true; applyPlayerLayout(); }
    }
    private void applyPlayerLayout() {
        if(root==null)return;
        boolean mini=isMini();view.setUseController(!mini);if(mini)view.hideController();
        updateChrome();view.setFullscreenButtonState(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);
        immersive();root.requestApplyInsets();
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); applyPlayerLayout(); updatePip(); }
    private void remember() {
        if(player==null || request==null||getIntent().getBooleanExtra("trailer",false))return;
        long current=player.getPlaybackState()==Player.STATE_ENDED?0:Math.max(0,player.getCurrentPosition());
        if(ProfileStore.prefs(this,"privacy").getBoolean("pauseHistory",false))return;
        android.content.SharedPreferences prefs=app.veyra.ProfileStore.prefs(this,"resume");
        android.content.SharedPreferences.Editor e=prefs.edit();
        if(prefs.getAll().size()>200)e.clear();
        e.putLong(request.contentId,current).apply();
        if(getIntent().hasExtra("vodId"))WatchHistory.remember(this,getIntent().getStringExtra("provider"),getIntent().getStringExtra("kind"),getIntent().getStringExtra("vodId"),getIntent().getStringExtra("collection"),selectedType(),getIntent().getStringExtra("catalogTitle")==null?request.title:getIntent().getStringExtra("catalogTitle"),getIntent().getStringExtra("poster"),current,player.getDuration(),player.getPlaybackState()==Player.STATE_ENDED);
    }
    private void release() {meter.state(this,false);audioEffects.close();
        handler.removeCallbacks(saveProgress);handler.removeCallbacks(delayedBuffer);
        if(player==null){if(offlinePinned){OfflineStore.unpin(offlineId);offlinePinned=false;}return;}
        trackPreferences=player.getTrackSelectionParameters();
        remember(); position=player.getPlaybackState()==Player.STATE_ENDED?0:Math.max(0,player.getCurrentPosition()); playWhenReady=player.getPlayWhenReady(); speed=player.getPlaybackParameters().speed;
        if(session!=null) { session.setActive(false); session.release(); session=null; }
        view.setPlayer(null); player.release(); player=null;if(offlinePinned){OfflineStore.unpin(offlineId);offlinePinned=false;} view.setKeepScreenOn(false); updatePip();
    }
    @Override protected void onStart() { super.onStart(); started=true; startPlayer(); applyPlayerLayout(); }
    // PiP pauses the Activity, but keeps it visible: release only when it stops.
    @Override protected void onStop() { started=false;if(nextPrompt!=null)nextPrompt.dismiss();handler.removeCallbacks(nextAction);release(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacks(sleepAction);handler.removeCallbacks(nextAction);resolver.shutdownNow(); release(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putLong("sleepDeadline",sleepDeadline);saved.putBoolean("sleepAtEnd",sleepAtEnd);saved.putInt("sizing",sizing);saved.putBoolean("cropVideo",cropVideo);saved.putString("playback",getIntent().getStringExtra("playback"));
        saved.putInt("source",sourceIndex); saved.putLong("position",player==null?position:player.getCurrentPosition());
        saved.putBoolean("playing",player==null?playWhenReady:player.getPlayWhenReady()); saved.putFloat("speed",speed); super.onSaveInstanceState(saved);
    }
}

