package app.veyra.data;
import app.veyra.model.StreamSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class StreamHttp {
    private StreamHttp() {}
    public static DataSource.Factory factory(StreamSource source) {
        return factory(source,false);
    }
    /** LAN casting must not turn a playlist into a relay to private network services. */
    public static DataSource.Factory castFactory(StreamSource source) { return factory(source,true); }
    public static boolean publicRelayAddress(java.net.InetAddress a) {
        if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
        byte[] bytes=a.getAddress();int first=bytes[0]&255;
        if(bytes.length==16)return (first&0xe0)==0x20; // Globally routed IPv6 only.
        int second=bytes[1]&255;
        return first!=0&&first<224&&!(first==100&&second>=64&&second<=127)&&!(first==198&&(second==18||second==19));
    }
    private static DataSource.Factory factory(StreamSource source,boolean casting) {
        HttpUrl origin=HttpUrl.get(source.url);
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
            .dns(host->{java.util.List<java.net.InetAddress> addresses=Dns.SYSTEM.lookup(host);if(casting)for(java.net.InetAddress a:addresses){boolean nativeHost=host.equals("127.0.0.1")&&origin.host().equals(host)&&CatalogBackend.isActiveProxy(source.url);if(!nativeHost&&!publicRelayAddress(a))throw new java.net.UnknownHostException("Private relay destination rejected");}return addresses;})
            .followSslRedirects(false).addNetworkInterceptor(chain->{
                Request original=chain.request();HttpUrl target=original.url();Request.Builder request=original.newBuilder();
                if(casting&&target.host().equals("127.0.0.1")&&(!target.encodedPath().startsWith("/resource")&&!target.encodedPath().matches(".*\\.(m3u8|ts|m4s|mp4|aac|key|vtt)$")))throw new IOException("Non-media relay destination rejected");
                if(!target.isHttps() && !(target.host().equals("127.0.0.1") && target.port()==origin.port() && CatalogBackend.isActiveProxy(source.url)))
                    throw new IOException("Insecure stream transport was rejected");
                // Signed headers belong only to the originating server, including after redirects.
                if(!target.scheme().equals(origin.scheme()) || !target.host().equals(origin.host()) || target.port()!=origin.port())
                    for(String name:source.headers.keySet())request.removeHeader(name);
                return chain.proceed(request.build());
            }).build();
        return new OkHttpDataSource.Factory(client).setDefaultRequestProperties(source.headers);
    }
}
