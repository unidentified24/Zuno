package app.veyra;
import android.content.Context;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.*;
import java.util.List;

/** Uses Google's public receiver; no developer receiver ID or account is required. */
public final class CastOptionsProvider implements OptionsProvider {
 public CastOptions getCastOptions(Context context) {
  return new CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
   .setResumeSavedSession(false).setStopReceiverApplicationWhenEndingSession(true).build();
 }
 public List<SessionProvider> getAdditionalSessionProviders(Context context){return null;}
}
