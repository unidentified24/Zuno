package app.veyra;
import android.app.Activity;import android.content.*;import android.view.*;import android.widget.*;
final class ReleaseExperience {
 static final String NOTES="• Cast to Google Cast and compatible DLNA receivers\n• Temporary TV-browser playback links\n• Queue downloads; pause, resume, retry and reorder\n• Search filters and genre preferences\n• Resume widget, TV remote focus and wide title pages\n• Followed-series checks and optional trailer previews\n• External caption timing and optional audio enhancements\n• Offline-readiness checks and tracked watch time";
 static boolean showIfNeeded(Activity a){try{String version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;SharedPreferences p=a.getSharedPreferences("release-experience",0);String previous=p.getString("seen","");if(version.equals(previous))return false;p.edit().putString("seen",version).apply();Intent i=new Intent(a,WhatsNewActivity.class);a.startActivity(i);return true;}catch(Exception e){return false;}}
}
