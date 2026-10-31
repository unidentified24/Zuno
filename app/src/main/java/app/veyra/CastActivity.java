package app.veyra;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.view.*;
import androidx.fragment.app.FragmentActivity;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.gms.cast.*;
import com.google.android.gms.cast.framework.*;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.*;
import java.util.concurrent.*;

/** Shared casting screen; discover protocol capabilities instead of promising every TV model. */
public final class CastActivity extends FragmentActivity {
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor(),discovery=Executors.newSingleThreadExecutor();
 private TextView status;private LinearLayout devices,googleSlot,browserPanel,seekPanel;private CastContext google;private MediaRouteButton castButton;private boolean scanning,visible;private String loadedUrl="";
 @Override public void onCreate(Bundle saved){super.onCreate(saved);Ui.configure(this);setTheme(R.style.Theme_Velixio_Cast);LinearLayout root=Ui.column(this);Ui.insets(this,root,20);Button back=Ui.quiet(this,"‹  Stop & return to player");back.setOnClickListener(v->stopAndReturn());root.addView(back);root.addView(Ui.text(this,"Watch on your TV",30,Ui.INK));status=Ui.text(this,"Preparing TV playback…",15,Ui.MUTED);root.addView(status);
  ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.column(this);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  body.addView(Ui.text(this,"Phone and receiver must be on the same local network. Your phone stays connected while casting.",14,Ui.MUTED));Button choose=Ui.primary(this,"Google Cast · Chromecast / Google TV");choose.setOnClickListener(v->googlePicker());body.addView(choose);googleSlot=Ui.column(this);body.addView(googleSlot);
  Button scan=Ui.chip(this,"Find DLNA receivers · compatible TVs");scan.setOnClickListener(v->scan());body.addView(scan);devices=Ui.column(this);body.addView(devices);
  Button browser=Ui.chip(this,"TV browser · show playback link");browser.setOnClickListener(v->browser());body.addView(browser);browserPanel=Ui.card(this);browserPanel.setVisibility(View.GONE);body.addView(browserPanel);
  body.addView(Ui.text(this,"Samsung / LG: use Google Cast or DLNA when the model supports it. Fire TV: use a compatible receiver or TV browser. Roku and unsupported TVs: use screen mirroring where available. Browser playback depends on the TV’s codecs and native HLS support.",14,Ui.MUTED));
  Button mirror=Ui.quiet(this,"Open Android screen mirroring");mirror.setOnClickListener(v->{CastBridgeService.stop(this);try{startActivity(new Intent("android.settings.CAST_SETTINGS"));}catch(ActivityNotFoundException e){Toast.makeText(this,"Use your device’s screen-mirroring controls",Toast.LENGTH_LONG).show();}finish();});body.addView(mirror);
  LinearLayout controls=new LinearLayout(this);Button play=Ui.chip(this,"Play"),pause=Ui.quiet(this,"Pause"),seek=Ui.quiet(this,"Seek");controls.addView(play);controls.addView(pause);controls.addView(seek);body.addView(controls);play.setOnClickListener(v->control(0,0));pause.setOnClickListener(v->control(1,0));seekPanel=Ui.card(this);seekPanel.setVisibility(View.GONE);body.addView(seekPanel);seek.setOnClickListener(v->seekPanel());
  Button stop=Ui.quiet(this,"Stop casting");stop.setOnClickListener(v->{CastBridgeService.stop(this);finish();});root.addView(stop);setContentView(root);
  if(saved==null&&getIntent().hasExtra("playback"))prepare();else if(!CastBridgeService.active())status.setText("Start casting from a playing title.");
 }
 private void prepare(){CastBridgeService.start(this,getIntent().getStringExtra("playback"),getIntent().getIntExtra("source",0),getIntent().getLongExtra("position",0));}
 private CastBridgeService.State ready(){CastBridgeService.State s=CastBridgeService.state;if(s==null)Toast.makeText(this,CastBridgeService.active()?"Preparing TV connection…":"Open a playing title and try again",Toast.LENGTH_SHORT).show();return s;}
 private void googlePicker(){CastBridgeService.State target=ready();if(target==null)return;if(target.dlna!=null){status.setText("Stop the DLNA session before choosing another receiver.");return;}try{if(google==null){google=CastContext.getSharedInstance(this);google.getSessionManager().addSessionManagerListener(listener,CastSession.class);castButton=new MediaRouteButton(this);castButton.setContentDescription("Choose a Google Cast receiver");CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),castButton);googleSlot.addView(castButton,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,48)));}CastSession current=google.getSessionManager().getCurrentCastSession();if(current!=null&&current.isConnected())loadGoogle(current);else castButton.showDialog();}catch(RuntimeException e){status.setText("Google Cast is unavailable on this device. Try a DLNA receiver or screen mirroring.");}}
 private void loadGoogle(CastSession session){CastBridgeService.State s=CastBridgeService.state;if(s==null||!session.isConnected()||s.dlna!=null)return;RemoteMediaClient remote=session.getRemoteMediaClient();if(remote==null||s.relay.mediaUrl().equals(loadedUrl))return;loadedUrl=s.relay.mediaUrl();if(s.loaded&&s.google&&remote.getMediaInfo()!=null&&s.relay.mediaUrl().equals(remote.getMediaInfo().getContentId())){status.setText(s.receiver);return;}s.google=true;
  MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);metadata.putString(MediaMetadata.KEY_TITLE,s.title);MediaInfo info=new MediaInfo.Builder(s.relay.mediaUrl()).setContentType(s.source.mimeType).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build();
  remote.load(new MediaLoadRequestData.Builder().setMediaInfo(info).setCurrentTime(s.startPosition).setAutoplay(true).build()).setResultCallback(result->{if(isDestroyed()||CastBridgeService.state!=s)return;if(result.getStatus().isSuccess()){s.loaded=true;s.receiver="Google Cast · "+(session.getCastDevice()==null?"TV":session.getCastDevice().getFriendlyName());status.setText(s.receiver);}else{loadedUrl="";s.loaded=false;status.setText("The Google Cast receiver could not play this stream ("+result.getStatus().getStatusCode()+"). Retry or use screen mirroring.");}});
 }
 private final SessionManagerListener<CastSession> listener=new SessionManagerListener<CastSession>(){
  public void onSessionStarting(CastSession s){}public void onSessionStarted(CastSession s,String id){loadGoogle(s);}public void onSessionStartFailed(CastSession s,int code){status.setText("Could not connect to Google Cast ("+code+").");}
  public void onSessionEnding(CastSession s){}public void onSessionEnded(CastSession s,int code){loadedUrl="";CastBridgeService.State state=CastBridgeService.state;if(state!=null&&state.google){CastBridgeService.stop(CastActivity.this);status.setText("Google Cast disconnected.");}}
  public void onSessionResuming(CastSession s,String id){}public void onSessionResumed(CastSession s,boolean suspended){loadGoogle(s);}public void onSessionResumeFailed(CastSession s,int code){status.setText("Google Cast could not reconnect.");}public void onSessionSuspended(CastSession s,int reason){status.setText("Google Cast connection interrupted. Reconnecting…");}
 };
 private void scan(){CastBridgeService.State s=ready();if(s==null||scanning)return;scanning=true;devices.removeAllViews();status.setText("Looking for compatible DLNA receivers…");discovery.execute(()->{try{List<DlnaReceiver> found=DlnaReceiver.discover(this,s.network);main.post(()->{scanning=false;if(isDestroyed()||CastBridgeService.state!=s)return;if(found.isEmpty())status.setText("No DLNA receivers found. Enable media sharing on your TV or try Google Cast / screen mirroring.");else status.setText("Choose a DLNA receiver");for(DlnaReceiver r:found){Button b=Ui.chip(this,r.name);devices.addView(b);b.setOnClickListener(v->connectDlna(s,r));}});}catch(Exception e){main.post(()->{scanning=false;if(!isDestroyed())status.setText("Discovery failed. Check your local-network permission and router’s device-isolation settings.");});}});}
 private void connectDlna(CastBridgeService.State s,DlnaReceiver r){if(s.google){status.setText("Stop the Google Cast session before choosing another receiver.");return;}status.setText("Connecting to "+r.name+"…");worker.execute(()->{try{if(CastBridgeService.state!=s)return;DlnaReceiver old=s.dlna;if(old!=null&&old!=r)old.stop();r.load(s.relay.mediaUrl(),s.title,s.source.mimeType,s.startPosition);if(CastBridgeService.state!=s||isDestroyed()){r.stop();return;}s.dlna=r;s.loaded=true;s.receiver="DLNA · "+r.name;main.post(()->{if(!isDestroyed()&&CastBridgeService.state==s)status.setText(s.receiver);});}catch(Exception e){main.post(()->{if(!isDestroyed())status.setText("The receiver rejected this stream or connection. Try Google Cast, another compatible receiver or screen mirroring.");});}});}
 private void browser(){
  CastBridgeService.State s=ready();if(s==null)return;browserPanel.removeAllViews();browserPanel.setVisibility(View.VISIBLE);
  browserPanel.addView(Ui.text(this,"Pair your TV browser",22,Ui.INK));
  browserPanel.addView(Ui.text(this,"Open this address in your TV browser",14,Ui.MUTED));
  TextView address=Ui.text(this,s.relay.pairingUrl(),20,Ui.INK);address.setTextIsSelectable(true);browserPanel.addView(address);
  TextView code=Ui.text(this,s.relay.newPairCode(),36,Ui.ACCENT);code.setLetterSpacing(.15f);code.setContentDescription("Six-digit TV pairing code: "+code.getText());browserPanel.addView(code);
  browserPanel.addView(Ui.text(this,"One use · expires in 2 minutes. Use a trusted home network. After pairing, use the TV’s video controls. Stop casting to close access.",14,Ui.MUTED));
  Button copy=Ui.chip(this,"Copy address"),renew=Ui.quiet(this,"New code"),close=Ui.quiet(this,"Hide pairing");
  browserPanel.addView(copy);browserPanel.addView(renew);browserPanel.addView(close);
  copy.setOnClickListener(v->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Velixio TV address",s.relay.pairingUrl())));
  renew.setOnClickListener(v->browser());close.setOnClickListener(v->browserPanel.setVisibility(View.GONE));
 }
 private void seekPanel(){
  seekPanel.removeAllViews();seekPanel.setVisibility(View.VISIBLE);seekPanel.addView(Ui.text(this,"Seek on your TV",20,Ui.INK));
  EditText minutes=new EditText(this);minutes.setTextColor(Ui.INK);minutes.setHintTextColor(Ui.MUTED);minutes.setHint("Minutes from the start");minutes.setContentDescription("Minutes from the start");minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});seekPanel.addView(minutes);
  Button go=Ui.chip(this,"Go to position"),cancel=Ui.quiet(this,"Cancel");seekPanel.addView(go);seekPanel.addView(cancel);
  go.setOnClickListener(v->{try{long value=Long.parseLong(minutes.getText().toString());if(value<0||value>1440)throw new NumberFormatException();control(2,value*60000);seekPanel.setVisibility(View.GONE);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(minutes.getWindowToken(),0);}catch(NumberFormatException e){minutes.setError("Enter a minute from 0 to 1440");}});cancel.setOnClickListener(v->seekPanel.setVisibility(View.GONE));
 }
 private void control(int action,long position){CastBridgeService.State s=ready();if(s==null)return;if(s.google&&google==null){googlePicker();return;}if(s.google&&google!=null){CastSession session=google.getSessionManager().getCurrentCastSession();RemoteMediaClient remote=session==null?null:session.getRemoteMediaClient();if(remote==null){status.setText("Google Cast is disconnected.");return;}if(action==0)remote.play();else if(action==1)remote.pause();else remote.seek(new MediaSeekOptions.Builder().setPosition(position).build());return;}if(s.dlna==null){status.setText("Choose Google Cast or DLNA for phone controls. In a TV browser, use its video controls.");return;}worker.execute(()->{try{if(action==0)s.dlna.play();else if(action==1)s.dlna.pause();else s.dlna.seek(position);}catch(Exception e){main.post(()->{if(!isDestroyed())status.setText("This receiver could not perform that action. Try its TV remote.");});}});}
 private final Runnable tick=new Runnable(){public void run(){CastBridgeService.State s=CastBridgeService.state;if(s==null&&!CastBridgeService.active())status.setText(CastBridgeService.error.isEmpty()?"Casting is stopped. Return to the player to start a session.":CastBridgeService.error);else if(s!=null&&status.getText().toString().equals("Preparing TV playback…"))status.setText("Ready · choose your TV or receiver");if(visible)main.postDelayed(this,1000);}};
 @Override protected void onStart(){super.onStart();visible=true;main.post(tick);}
 @Override protected void onStop(){visible=false;main.removeCallbacks(tick);super.onStop();}
 private void stopAndReturn(){CastBridgeService.stop(this);if(google!=null)try{google.getSessionManager().endCurrentSession(true);}catch(RuntimeException ignored){}finish();}
 @Override public void onBackPressed(){stopAndReturn();}
 @Override protected void onDestroy(){if(google!=null)google.getSessionManager().removeSessionManagerListener(listener,CastSession.class);worker.shutdownNow();discovery.shutdownNow();main.removeCallbacks(tick);super.onDestroy();}
}
