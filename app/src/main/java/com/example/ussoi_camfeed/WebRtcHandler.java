package com.example.ussoi_camfeed;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

public class WebRtcHandler {
    private static final String TAG = "WebRtcHandel";
    private static WebRtcHandler instance;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private EglBase rootEgl;
    private WebSocketHandler WebSocketHandler;
    private Context context;

    public static synchronized WebRtcHandler getInstance() {
        if (instance == null) {
            instance = new WebRtcHandler();
        }
        return instance;
    }
    public void init(Context context) {
        this.context = context;
        WebSocketHandler = new WebSocketHandler(new WebSocketHandler.MessageCallback() {
            @Override
            public void onOpen() {
                Log.d("Signaling", "Connected to signaling server");
            }

            @Override
            public void onPayloadReceived(String payload) {
                Log.d("Signaling", " Received signaling payload: " + payload);

                // Example: parse JSON
                try {
                    JSONObject json = new JSONObject(payload);
                    String type = json.optString("type");
                    if ("offer".equals(type)) {
                        // handle WebRTC offer
                    } else if ("answer".equals(type)) {
                        // handle WebRTC answer
                    } else if ("candidate".equals(type)) {
                        // handle ICE candidate
                    }
                } catch (Exception e) {
                    Log.e("Signaling", "Failed to parse signaling payload", e);
                }
            }

            @Override
            public void onClosed() {
                Log.d("Signaling", "Signaling connection closed: " );
            }

            @Override
            public void onError(String error) {
                Log.e("Signaling", "Error in signaling: " + error);
            }
        });
        // Initialize EGL for video rendering
        rootEgl = EglBase.create();

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
        );
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                        rootEgl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEgl.getEglBaseContext()))
                .createPeerConnectionFactory();
    }
    public static class IceCandidatePayload {
        public String type;
        public String candidate;
        public String sdpMid;
        public int sdpMLineIndex;

        public IceCandidatePayload(String type, String candidate, String sdpMid, int sdpMLineIndex) {
            this.type = type;
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }
    }
    public void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) { }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) { }

            @Override
            public void onIceConnectionReceivingChange(boolean b) { }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) { }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                // Create a payload object
                IceCandidatePayload payload = new IceCandidatePayload(
                        "ice-candidate",
                        iceCandidate.sdp,
                        iceCandidate.sdpMid,
                        iceCandidate.sdpMLineIndex
                );

                // Send Signaling it via your WebSocket
                WebSocketHandler.connSendPayload(payload);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) { }

            @Override
            public void onAddStream(MediaStream mediaStream) { }

            @Override
            public void onRemoveStream(MediaStream mediaStream) { }

            @Override
            public void onDataChannel(DataChannel dataChannel) { }

            @Override
            public void onRenegotiationNeeded() { }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) { }
        });
    }
    // Initialize video capturer (front or back camera)
    private VideoCapturer createCameraCapturer(Context context) {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) return capturer;
            }
        }
        return null; // fallback
    }

    // Create Video Source & Track
    VideoSource videoSource = factory.createVideoSource(false);
    VideoCapturer capturer = createCameraCapturer(context);
    SurfaceTextureHelper surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEgl.getEglBaseContext());
            capturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            capturer.startCapture(720, 480, 30); // width, height, fps

    VideoTrack localVideoTrack = factory.createVideoTrack("VIDEO_TRACK_ID", videoSource);

}
