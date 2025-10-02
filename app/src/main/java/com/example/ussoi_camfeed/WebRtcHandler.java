
package com.example.ussoi_camfeed;

import static androidx.core.content.ContextCompat.getSystemService;
import static com.example.ussoi_camfeed.SaveInputFields.streaming;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONObject;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class WebRtcHandler {
    private static final String TAG = "WebRtcHandler";
    private static WebRtcHandler instance;
    private final Context context;
    private final Gson gson = new Gson();

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private EglBase rootEgl;
    private WebSocketHandler webSocketHandler;

    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private CameraVideoCapturer cameraVideoCapturer;

    private WebRtcHandler(Context context) {
        this.context = context.getApplicationContext();
    }
    public static synchronized WebRtcHandler getInstance(Context context) {
        if (instance == null) {
            instance = new WebRtcHandler(context);
        }
        return instance;
    }
    private void changeConfig(JSONObject json){
        // code if payload contains then invoke that fn
        //        {
        //            "type": "config",
        //                "videoparams": {
        //            "width": 640,
        //                    "height": 480,
        //                    "fps": 15
        //        },
        //            "video":true,
        //                "audio": true,
        //                "bitrate":1000,
        //                "switchCamera": "toggle"
        //        }
        //
        if ( json.has("videoparams")){
            JSONObject videoParams = json.optJSONObject("videoparams");
            if (videoParams != null) {
                int width = videoParams.optInt("width", 640);
                int height = videoParams.optInt("height", 480);
                int fps = videoParams.optInt("fps", 10);
                changeCaptureFormat(width, height, fps);
            }
        }
        if ( json.has("video")){
            toggleVideo(json.optBoolean("video"));
        }
        if ( json.has("audio")){
            toggleAudio(json.optBoolean("audio"));
        }
        if ( json.has("bitrate")){
            setVideoBitrate(json.optInt("bitrate"));
        }
        if (json.optString("switchCamera").equals("toggle")){
            switchCamera();
        }
    }
    public void init() {
        // WebSocket and PeerConnectionFactory initialization remains the same...
        webSocketHandler = new WebSocketHandler(new WebSocketHandler.MessageCallback() {
            @Override
            public void onOpen() {
                Log.d(TAG, "WebSocket connected");
            }

            @Override
            public void onPayloadReceivedbyte(byte[] payload) {}

            @Override
            public void onPayloadReceivedtext(String payload) {
                Log.d(TAG, "Received signaling payload ");
                try {
                    JSONObject json = new JSONObject(payload);
                    String type = json.optString("type");
                    switch (type) {
                        case "answer":
                            handleAnswer(json.getString("sdp"));
                            break;
                        case "ice-candidate":
                            handleRemoteIceCandidate(json);
                            break;
                        case "config":
                            changeConfig(json);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse signaling payload", e);
                }
            }

            @Override
            public void onClosed() {
                Log.d(TAG, "WebSocket connection closed");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebSocket error: " + error);
            }
        });
        webSocketHandler.setupConnection(context, streaming);

        rootEgl = EglBase.create();
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        );

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEgl.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                rootEgl.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
    }
    // for SEND-ONLY:not using SurfaceViewRenderer.
    public static class IceCandidatePayload {
        String type;
        String candidate; // <- we'll map iceCandidate.sdp into this
        String sdpMid;
        int sdpMLineIndex;

        public IceCandidatePayload(String type, String cand, String mid, int index) {
            this.type = type;
            this.candidate = cand; // iceCandidate.sdp goes here
            this.sdpMid = mid;
            this.sdpMLineIndex = index;
        }
    }

    private void createPeerConnection() {
        // PeerConnection creation
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate: " + iceCandidate);
                IceCandidatePayload payload = new IceCandidatePayload(
                        "ice-candidate",
                        iceCandidate.sdp,
                        iceCandidate.sdpMid,
                        iceCandidate.sdpMLineIndex
                );
                webSocketHandler.connSendPayload(gson.toJson(payload));
            }
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.w(TAG, "onAddTrack called unexpectedly in send-only client.");
            }
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
            @Override
            public void onIceConnectionReceivingChange(boolean b) {}
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override
            public void onAddStream(MediaStream mediaStream) {}
            @Override
            public void onRemoveStream(MediaStream mediaStream) {}
            @Override
            public void onDataChannel(DataChannel dataChannel) {}
            @Override
            public void onRenegotiationNeeded() {}
        });
    }

    // MODIFIED: Method now takes initial video parameters
    public void startLocalStream(int width, int height, int fps) {
        if (factory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized.");
            return;
        }

        videoCapturer = createCameraCapturer(context);
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create camera capturer.");
            return;
        }
        if (videoCapturer instanceof CameraVideoCapturer) {
            cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEgl.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(width, height, fps);

        localVideoTrack = factory.createVideoTrack("VIDEO_TRACK_ID", videoSource);

        //  Create audio source track.
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("AUDIO_TRACK_ID", audioSource);

        if (peerConnection != null) {
            peerConnection.addTrack(localVideoTrack);
            peerConnection.addTrack(localAudioTrack);
        } else {
            Log.w(TAG, "PeerConnection is null, tracks will be added upon offer creation.");
        }
    }
    public void switchCamera() {
        if (cameraVideoCapturer != null) {
            Log.d(TAG, "Switching camera...");
            cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    Log.d(TAG, "Camera switch successful. New camera is " + (isFrontCamera ? "front" : "back"));
                }

                @Override
                public void onCameraSwitchError(String errorDescription) {
                    Log.e(TAG, "Camera switch failed: " + errorDescription);
                }
            });
        } else {
            Log.e(TAG, "Camera switching not supported with current capturer.");
        }

    }
    public void toggleAudio(boolean mute) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!mute);
            Log.d(TAG, mute ? "Audio muted" : "Audio unmuted");
        }
    }
    public void toggleVideo(boolean mute) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(!mute);
            Log.d(TAG, mute ? "Video muted" : "Video unmuted");
        }
    }

    // for  SEND-ONLY: We will not receive any media.
    private MediaConstraints getSdpConstraints() {
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        return sdpMediaConstraints;
    }

    public void createOffer() {
        if (peerConnection == null) {
            createPeerConnection();
        }
        if (localVideoTrack != null && localAudioTrack != null) {
            for (RtpSender sender : peerConnection.getSenders()) {
                if (sender.track() != null) {
                    peerConnection.removeTrack(sender);
                }
            }
            peerConnection.addTrack(localVideoTrack);
            peerConnection.addTrack(localAudioTrack);
        } else {
            Log.e(TAG, "Cannot create offer: local tracks are not initialized. Call startLocalStream() first.");
            return;
        }
        peerConnection.createOffer(new SdpObserverAdapter("Offer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(this, sessionDescription);
                sendSdp("offer", sessionDescription.description);
            }
        }, getSdpConstraints());
    }

    private void handleAnswer(String sdp) {
        peerConnection.setRemoteDescription(new SdpObserverAdapter("RemoteAnswer"), new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }

    // ... handleRemoteIceCandidate, sendSdp, createCameraCapturer, IceCandidatePayload remain the same ...

    private void handleRemoteIceCandidate(JSONObject json) {
        if (peerConnection == null) return;
        try {
            IceCandidate candidate = new IceCandidate(
                    json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
            peerConnection.addIceCandidate(candidate);
        } catch (Exception e) {
            Log.e(TAG, "Error adding remote ICE candidate", e);
        }
    }

    private void sendSdp(String type, String description) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", type);
            payload.put("sdp", description);
            webSocketHandler.connSendPayload(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error creating SDP payload", e);
        }
    }

    private VideoCapturer createCameraCapturer(Context context) {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        String[] deviceNames = enumerator.getDeviceNames();
        String frontCameraDeviceName = null;
        String backCameraDeviceName = null;

        // Find front and back cameras
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                frontCameraDeviceName = deviceName;
            } else {
                backCameraDeviceName = deviceName;
            }
        }

        // Prefer the front camera, but use the back camera if the front is not available.
        if (frontCameraDeviceName != null) {
            return enumerator.createCapturer(frontCameraDeviceName, null);
        } else if (backCameraDeviceName != null) {
            return enumerator.createCapturer(backCameraDeviceName, null);
        }

        return null;
    }



    // NEW METHOD: Change the capture format (resolution and FPS) while streaming
    public void changeCaptureFormat(int width, int height, int fps) {
        if (videoCapturer != null) {
            Log.d(TAG, "Changing capture format to " + width + "x" + height + "@" + fps);
            videoCapturer.changeCaptureFormat(width, height, fps);
        } else {
            Log.e(TAG, "Cannot change format, capturer is null.");
        }
    }

    // NEW METHOD: Set the maximum video bitrate while streaming
    public void setVideoBitrate(int bitratebps) {
        if (peerConnection == null || localVideoTrack == null) {
            Log.e(TAG, "Cannot set bitrate, PeerConnection or local track is null.");
            return;
        }

        for (RtpSender sender : peerConnection.getSenders()) {
            if (sender.track() != null && sender.track().id().equals(localVideoTrack.id())) {
                Log.d(TAG, "Setting bitrate for sender to " + bitratebps + " bps");
                RtpParameters parameters = sender.getParameters();
                if (parameters.encodings.size() > 0) {
                    parameters.encodings.get(0).maxBitrateBps = bitratebps;
                    sender.setParameters(parameters);
                }
                return;
            }
        }
    }

    public void stopAllServices() {
        Log.d(TAG, "Closing WebRTC session...");

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping capturer", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
            cameraVideoCapturer = null;
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (rootEgl != null) {
            rootEgl.release();
            rootEgl = null;
        }
    }

}

// Helper class to reduce boilerplate SdpObserver code
class SdpObserverAdapter implements SdpObserver {
    private final String logTag;
    public SdpObserverAdapter(String tag) { this.logTag = "SdpObserver:" + tag; }
    @Override public void onCreateSuccess(SessionDescription s) { Log.d(logTag, "onCreateSuccess"); }
    @Override public void onSetSuccess() { Log.d(logTag, "onSetSuccess"); }
    @Override public void onCreateFailure(String s) { Log.e(logTag, "onCreateFailure: " + s); }
    @Override public void onSetFailure(String s) { Log.e(logTag, "onSetFailure: " + s); }
}