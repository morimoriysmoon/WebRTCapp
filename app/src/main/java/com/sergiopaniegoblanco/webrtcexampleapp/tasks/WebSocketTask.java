package com.sergiopaniegoblanco.webrtcexampleapp.tasks;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.sergiopaniegoblanco.webrtcexampleapp.VideoConferenceActivity;
import com.sergiopaniegoblanco.webrtcexampleapp.managers.PeersManager;
import com.sergiopaniegoblanco.webrtcexampleapp.listeners.CustomWebSocketListener;
import com.sergiopaniegoblanco.webrtcexampleapp.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by sergiopaniegoblanco on 18/02/2018.
 */

public class WebSocketTask extends AsyncTask<VideoConferenceActivity, Void, Void> {

    private static final String TAG = "WebSocketTask";
    private static final String TOKEN_URL = "https://demos.openvidu.io:4443/api/tokens";
    private static final String AUTH_TOKEN = "Basic T1BFTlZJRFVBUFA6TVlfU0VDUkVU";
    private VideoConferenceActivity activity;
    private PeerConnection localPeer;
    private String sessionName;
    private String participantName;
    private String socketAddress;
    private PeerConnectionFactory peerConnectionFactory;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private PeersManager peersManager;
    private boolean isCancelled = false;
    private OkHttpClient client;
    private final TrustManager[] trustManagers = new TrustManager[]{ new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG,": authType: " + authType);
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG,": authType: " + authType);
        }
    }};

    public WebSocketTask(VideoConferenceActivity activity, PeersManager peersManager, String sessionName, String participantName, String socketAddress) {
        this.activity = activity;
        this.peersManager = peersManager;
        this.localPeer = peersManager.getLocalPeer();
        this.sessionName = sessionName;
        this.participantName = participantName;
        this.socketAddress = socketAddress;
        this.peerConnectionFactory = peersManager.getPeerConnectionFactory();
        this.localAudioTrack = peersManager.getLocalAudioTrack();
        this.localVideoTrack = peersManager.getLocalVideoTrack();
        this.client = new OkHttpClient();
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    @Override
    protected Void doInBackground(VideoConferenceActivity... parameters) {
        try {
            String json = "{\"session\": \"SessionA\"}";
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .header("Authorization", AUTH_TOKEN)
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            String responseString =  response.body().string();

            Log.d(TAG,"responseString: " + responseString);

            String token = "";
            try {
                JSONObject jsonObject = new JSONObject(responseString);
                token = (String) jsonObject.get("token");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            WebSocketFactory factory = new WebSocketFactory();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            factory.setSSLContext(sslContext);
            socketAddress = getSocketAddress();
            peersManager.setWebSocket(new WebSocketFactory().createSocket(socketAddress));
            peersManager.setWebSocketAdapter(new CustomWebSocketListener(parameters[0], peersManager, sessionName, participantName, activity.getViewsContainer(), socketAddress, token));
            peersManager.getWebSocket().addListener(peersManager.getWebSocketAdapter());
            if (!isCancelled) {
                peersManager.getWebSocket().connect();
            }
        } catch (IOException | KeyManagementException | WebSocketException | NoSuchAlgorithmException | IllegalArgumentException e) {
            Handler mainHandler = new Handler(activity.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(activity, activity.getResources().getString(R.string.no_connection), Toast.LENGTH_LONG);
                    toast.show();
                    activity.hangup();
                }
            };
            mainHandler.post(myRunnable);
            isCancelled = true;
        }
        return null;
    }

    private String getSocketAddress() {
        String baseAddress = socketAddress;
        String secureWebSocketPrefix = "wss://";
        String insecureWebSocketPrefix = "ws://";
        if (baseAddress.split(secureWebSocketPrefix).length == 1 && baseAddress.split(insecureWebSocketPrefix).length == 1) {
            baseAddress = secureWebSocketPrefix.concat(baseAddress);
        }
        String portSuffix = ":4443";
        if (baseAddress.split(portSuffix).length == 1 && !baseAddress.regionMatches(true, baseAddress.length() - portSuffix.length(), portSuffix, 0, portSuffix.length())) {
            baseAddress = baseAddress.concat(portSuffix);
        }
        return baseAddress;
    }

    @Override
    protected void onProgressUpdate(Void... progress) {
        Log.i(TAG,"PROGRESS " + Arrays.toString(progress));
    }

    @Override
    protected void onPostExecute(Void results) {
        if (!isCancelled) {
            MediaConstraints sdpConstraints = new MediaConstraints();
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
            MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
            stream.addTrack(localAudioTrack);
            stream.addTrack(localVideoTrack);
            localPeer.addStream(stream);
            peersManager.createLocalOffer(sdpConstraints);
        } else {
            isCancelled = false;
        }

    }
}