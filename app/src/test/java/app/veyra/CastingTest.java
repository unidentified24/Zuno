package app.veyra;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import androidx.media3.datasource.*;
import app.veyra.model.StreamSource;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class CastingTest {
 private static final String ORIGIN="https://example.invalid/video/master.m3u8?signature=A%2FB+C";
 private static StreamSource source(){return new StreamSource("0","Original",ORIGIN,"application/x-mpegURL",Collections.emptyMap());}
 @Test public void hlsRewritesNestedVariantsKeysMapsAndSegmentsWithoutLeakingUrls()throws Exception {
  List<String> registered=new ArrayList<>();String raw="#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://keys.invalid/a?signature=X%2BY+Z\"\n#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"100@0\"\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"audio/list.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=123\n720p/index.m3u8?auth=A%2FB+C\n";
  String changed=CastPlaylist.rewrite(raw,ORIGIN,u->{registered.add(u);return "http://192.168.1.2/s/"+registered.size();});
  assertEquals(List.of("https://keys.invalid/a?signature=X%2BY+Z","https://example.invalid/video/init.mp4","https://example.invalid/video/audio/list.m3u8","https://example.invalid/video/720p/index.m3u8?auth=A%2FB+C"),registered);assertFalse(changed.contains("signature"));assertTrue(changed.contains("BYTERANGE=\"100@0\""));assertTrue(changed.contains("BANDWIDTH=123"));
 }
 @Test public void unsupportedSteeringAndVariablesFailClosed()throws Exception {
  for(String tag:List.of("#EXT-X-DEFINE:NAME=\"x\",VALUE=\"y\"","#EXT-X-CONTENT-STEERING:SERVER-URI=\"https://x.invalid\"")){try{CastPlaylist.rewrite("#EXTM3U\n"+tag,ORIGIN,u->u);fail();}catch(IOException expected){}}
 }
 @Test public void relayDnsPolicyRejectsPrivateAndReservedDestinations()throws Exception {
  for(String ip:List.of("127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1","169.254.169.254","0.1.2.3","100.100.100.100","198.18.1.1","224.0.0.1","255.255.255.255","::1","fc00::1","fe80::1"))assertFalse(ip,app.veyra.data.StreamHttp.publicRelayAddress(InetAddress.getByName(ip)));
  assertTrue(app.veyra.data.StreamHttp.publicRelayAddress(InetAddress.getByName("8.8.8.8")));assertTrue(app.veyra.data.StreamHttp.publicRelayAddress(InetAddress.getByName("2606:4700:4700::1111")));
 }
 @Test public void castingScreenDoesNotStartGoogleServicesOrRelayWithoutAUserRequest(){
  org.robolectric.android.controller.ActivityController<CastActivity> controller=Robolectric.buildActivity(CastActivity.class).create();CastActivity a=controller.get();assertFalse(CastBridgeService.active());assertNotNull(a.findViewById(android.R.id.content));controller.destroy();
 }
 @Test public void rangeParserRejectsMultipleOverflowAndReversedRanges()throws Exception {
  assertArrayEquals(new long[]{0,-1},CastRelay.parseRange(null));assertArrayEquals(new long[]{123,10},CastRelay.parseRange("bytes=123-132"));assertArrayEquals(new long[]{-25,-1},CastRelay.parseRange("bytes=-25"));
  for(String value:List.of("bytes=2-1","bytes=0-1,4-5","bytes=-0","bytes=0-9223372036854775807","bytes=9999999999999999999999-","items=0-1"))try{CastRelay.parseRange(value);fail(value);}catch(IOException expected){}
 }
 private static DataSource.Factory upstream(Map<String,byte[]> values){return ()->new DataSource(){ByteArrayDataSource data;public void addTransferListener(TransferListener l){}public long open(DataSpec spec)throws IOException {byte[] content=values.get(spec.uri.toString());if(content==null)throw new IOException("No fixture");data=new ByteArrayDataSource(content);return data.open(spec);}public int read(byte[] b,int offset,int length)throws IOException{return data.read(b,offset,length);}public android.net.Uri getUri(){return data==null?null:data.getUri();}public void close(){if(data!=null)data.close();}};}
 private static String get(String url)throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);try{assertEquals(200,c.getResponseCode());return new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}finally{c.disconnect();}}
 @Test public void relayRequiresSessionCapabilityRewritesAndServesUnmodifiedMedia()throws Exception {
  Map<String,byte[]> values=new HashMap<>();values.put(ORIGIN,"#EXTM3U\n#EXTINF:1,\nclip.ts?key=X%2BY+Z\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8));values.put("https://example.invalid/video/clip.ts?key=X%2BY+Z",new byte[]{1,2,3,4});
  CastRelay relay=new CastRelay(InetAddress.getLoopbackAddress(),source(),upstream(values));String root=relay.mediaUrl();
  try{String playlist=get(root);assertFalse(playlist.contains("example.invalid"));assertFalse(playlist.contains("key="));String segment=Arrays.stream(playlist.split("\\n")).filter(l->l.startsWith("http://")).findFirst().orElseThrow();HttpURLConnection c=(HttpURLConnection)new URL(segment).openConnection();c.setRequestProperty("Range","bytes=1-2");assertEquals(206,c.getResponseCode());assertEquals("bytes 1-2/*",c.getHeaderField("Content-Range"));assertArrayEquals(new byte[]{2,3},c.getInputStream().readAllBytes());c.disconnect();
   URL u=new URL(root);HttpURLConnection missing=(HttpURLConnection)new URL("http://"+u.getAuthority()+"/wrong/session").openConnection();assertEquals(404,missing.getResponseCode());missing.disconnect();HttpURLConnection arbitrary=(HttpURLConnection)new URL(root+"?url=http://127.0.0.1/control").openConnection();assertEquals(404,arbitrary.getResponseCode());arbitrary.disconnect();
   assertFalse(get(relay.browserUrl()).contains("example.invalid"));
  }finally{relay.close();}
  try{get(root);fail("Session URL must stop working");}catch(IOException expected){}
 }
 @Test public void relayRejectsNonHttpReferencesAndMutatingRequests()throws Exception {
  Map<String,byte[]> values=Map.of(ORIGIN,"#EXTM3U\nfile:///private/key\n".getBytes(StandardCharsets.UTF_8));try(CastRelay relay=new CastRelay(InetAddress.getLoopbackAddress(),source(),upstream(values))){HttpURLConnection c=(HttpURLConnection)new URL(relay.mediaUrl()).openConnection();c.setRequestMethod("POST");assertEquals(405,c.getResponseCode());c.disconnect();try{get(relay.mediaUrl());fail();}catch(IOException expected){}}
 }
 private static int pair(CastRelay relay,String code)throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(relay.pairingUrl()+"pair").openConnection();c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);try{c.getOutputStream().write(("code="+code).getBytes(StandardCharsets.US_ASCII));return c.getResponseCode();}finally{c.disconnect();}}
 @Test public void browserPairingIsSingleUseAndLocksAfterFiveIncorrectAttempts()throws Exception {
  try(CastRelay relay=new CastRelay(InetAddress.getLoopbackAddress(),source(),upstream(Map.of()))){String code=relay.newPairCode();assertTrue(get(relay.pairingUrl()).contains("six-digit"));assertFalse(get(relay.pairingUrl()).contains(code));assertEquals(303,pair(relay,code));assertEquals(403,pair(relay,code));
   code=relay.newPairCode();String wrong=code.equals("000000")?"111111":"000000";for(int i=0;i<5;i++)assertEquals(403,pair(relay,wrong));assertEquals(403,pair(relay,code));assertEquals(303,pair(relay,relay.newPairCode()));
  }
 }
 @Test public void lanEndpointsMustBeLiteralPrivateAddressesInTheSameSubnet()throws Exception {
  CastNetwork lan=new CastNetwork(null,(Inet4Address)InetAddress.getByName("192.168.1.2"),24);assertTrue(lan.contains(InetAddress.getByName("192.168.1.55")));assertFalse(lan.contains(InetAddress.getByName("192.168.2.55")));assertFalse(lan.contains(InetAddress.getByName("127.0.0.1")));assertFalse(lan.contains(InetAddress.getByName("8.8.8.8")));
  for(String host:List.of("example.com","127.0.0.1","192.168.2.1","192.168.1.2"))try{lan.literal(host);fail(host);}catch(IOException expected){}
 }
 @Test public void discoveryRejectsCrossHostLocationsAndXmlExternalEntities()throws Exception {
  CastNetwork lan=new CastNetwork(null,(Inet4Address)InetAddress.getByName("192.168.1.2"),24);InetAddress peer=InetAddress.getByName("192.168.1.55");String response="HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.55:123/desc.xml\r\n";assertNotNull(DlnaReceiver.location(response,peer,lan));assertNull(DlnaReceiver.location(response.replace("192.168.1.55","127.0.0.1"),peer,lan));assertNull(DlnaReceiver.location(response.replace("192.168.1.55","192.168.1.56"),peer,lan));
  URI uri=URI.create("http://192.168.1.55:123/desc.xml");String xml="<root><device><friendlyName>Living Room &amp; TV</friendlyName><serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/control</controlURL></service></serviceList></device></root>";
  DlnaReceiver r=DlnaReceiver.parse(xml.getBytes(StandardCharsets.UTF_8),uri,lan);assertEquals("Living Room & TV",r.name);assertEquals("http://192.168.1.55:123/control",r.control.toString());
  try{DlnaReceiver.parse(xml.replace(">/control<",">http://192.168.1.56/control<").getBytes(StandardCharsets.UTF_8),uri,lan);fail();}catch(IOException expected){}
  try{DlnaReceiver.parse(("<!DOCTYPE root [<!ENTITY xxe SYSTEM 'file:///private'>]>"+xml).getBytes(StandardCharsets.UTF_8),uri,lan);fail();}catch(org.xml.sax.SAXException expected){}
 }
}
