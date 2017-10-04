package com.dooble.phonertc;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.view.View;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by hoai nam on 5/30/2017.
 */

public class PhoneRTCPlugin extends CordovaPlugin {
    protected final static String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public CallbackContext callbackContext;
    private AudioSource _audioSource;
    private AudioTrack _audioTrack;
    private VideoCapturer _videoCapturer;
    private VideoSource _videoSource;
    private PeerConnectionFactory _peerConnectionFactory;
    private Map<String, Session> _sessions;
    private VideoConfig _videoConfig;
    private SurfaceViewRenderer _videoView;
    private List<VideoTrackRendererPair> _remoteVideos;
    private VideoTrackRendererPair _localVideo;
    private WebView.LayoutParams _videoParams;
    private boolean _shouldDispose = true;
    private boolean _initializedAndroidGlobals = false;

    public PhoneRTCPlugin() {
        _remoteVideos = new ArrayList<VideoTrackRendererPair>();
        _sessions = new HashMap<String, Session>();
    }

    private static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {

        final CallbackContext _callbackContext = callbackContext;
        this.callbackContext = _callbackContext;

        if (action.equals("createSessionObject")) {
            final SessionConfig config = SessionConfig.fromJSON(args.getJSONObject(1));

            final String sessionKey = args.getString(0);
            _callbackContext.sendPluginResult(getSessionKeyPluginResult(sessionKey));

            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (!_initializedAndroidGlobals) {
                        PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true);
                        _initializedAndroidGlobals = true;
                    }

                    if (_peerConnectionFactory == null) {
                        //Create a new PeerConnectionFactory instance.
                        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
                        _peerConnectionFactory = new PeerConnectionFactory(options);
                    }

                    if (config.isAudioStreamEnabled() && _audioTrack == null) {
                        initializeLocalAudioTrack();
                    }

                    if (config.isVideoStreamEnabled() && _localVideo == null) {
                        //initializeLocalVideoTrack();
                    }

                    _sessions.put(sessionKey, new Session(PhoneRTCPlugin.this,
                            _callbackContext, config, sessionKey));

                    if (_sessions.size() > 1) {
                        _shouldDispose = false;
                    }
                }
            });

            return true;
        } else if (action.equals("acceptAcall")) {
            final String sessionKey = args.getString(0);
            _sessions.get(sessionKey).acceptACall();
        } else if (action.equals("call")) {
            JSONObject container = args.getJSONObject(0);
            final String sessionKey = container.getString("sessionKey");

            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if (_sessions.containsKey(sessionKey)) {
                            _sessions.get(sessionKey).call();
                            _callbackContext.success();
                        } else {
                            _callbackContext.error("No session found matching the key: '" + sessionKey + "'");
                        }
                    } catch (Exception e) {
                        _callbackContext.error(e.getMessage());
                    }
                }
            });

            return true;
        } else if (action.equals("receiveMessage")) {
            JSONObject container = args.getJSONObject(0);
            final String sessionKey = container.getString("sessionKey");
            final String message = container.getString("message");

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Session session = _sessions.get(sessionKey);
                    if (null != session) {
                        session.receiveMessage(message);
                        _callbackContext.success();
                    }
                }
            });

            return true;
        } else if (action.equals("renegotiate")) {
            JSONObject container = args.getJSONObject(0);
            final String sessionKey = container.getString("sessionKey");
            JSONObject jsonObject = new JSONObject(container.getString("config"));
            final SessionConfig config = SessionConfig.fromJSON(jsonObject);

            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    Session session = _sessions.get(sessionKey);
                    if (session == null)
                        return;
                    session.setConfig(config);
                    session.createOrUpdateStream();
                    //session.test();
                }
            });

        } else if (action.equals("disconnect")) {
            JSONObject container = args.getJSONObject(0);
            final String sessionKey = container.getString("sessionKey");

            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (_sessions.containsKey(sessionKey)) {
                        _sessions.get(sessionKey).disconnect(true);
                    }
                }
            });

            return true;
        } else if (action.equals("setVideoView")) {
            _videoConfig = VideoConfig.fromJSON(args.getJSONObject(0));

            // make sure it's not junk
            if (_videoConfig.getContainer().getWidth() == 0 || _videoConfig.getContainer().getHeight() == 0) {
                return false;
            }

            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (!_initializedAndroidGlobals) {
                        abortUnless(PeerConnectionFactory.initializeAndroidGlobals(cordova.getActivity(), true, true, true),
                                "Failed to initializeAndroidGlobals");
                        _initializedAndroidGlobals = true;
                    }

                    if (_peerConnectionFactory == null) {
                        _peerConnectionFactory = new PeerConnectionFactory();
                    }

                    _videoParams = new WebView.LayoutParams(
                            (int) (_videoConfig.getContainer().getWidth() * _videoConfig.getDevicePixelRatio()),
                            (int) (_videoConfig.getContainer().getHeight() * _videoConfig.getDevicePixelRatio()),
                            (int) (_videoConfig.getContainer().getX() * _videoConfig.getDevicePixelRatio()),
                            (int) (_videoConfig.getContainer().getY() * _videoConfig.getDevicePixelRatio()));

                    if (_videoView == null) {
                        //createVideoView();

                        if (_videoConfig.getLocal() != null && _localVideo == null) {
                            //initializeLocalVideoTrack();
                        }
                    } else {
                        _videoView.setLayoutParams(_videoParams);
                    }
                }
            });

            return true;
        } else if (action.equals("hideVideoView")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (_videoView != null) {
                        _videoView.setVisibility(View.GONE);
                    }
                }
            });
        } else if (action.equals("showVideoView")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (_videoView != null) {
                        _videoView.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else if (action.equals("checkPermissions")) {
            if (PermissionHelper.hasPermission(this, permissions[0]) && PermissionHelper.hasPermission(this, permissions[1]) && PermissionHelper.hasPermission(this, permissions[2])) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                return true;
            } else {
                try {
                    PermissionHelper.requestPermissions(this, 0, permissions);
                    return true;
                } catch (Exception e) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
                }
            }
        }
        callbackContext.error("Invalid action: " + action);
        return false;
    }

    void initializeLocalVideoTrack() {
        _videoCapturer = createVideoCapturer();
        _videoSource = _peerConnectionFactory.createVideoSource(_videoCapturer);
        _localVideo = new VideoTrackRendererPair(_peerConnectionFactory.createVideoTrack("ARDAMSv0", _videoSource), null);
        refreshVideoView();
    }

    int getPercentage(int localValue, int containerValue) {
        return (int) (localValue * 100.0 / containerValue);
    }

    void initializeLocalAudioTrack() {
        _audioSource = _peerConnectionFactory.createAudioSource(new MediaConstraints());
        _audioTrack = _peerConnectionFactory.createAudioTrack("ARDAMSa0", _audioSource);
    }

    public VideoTrack getLocalVideoTrack() {
        if (_localVideo == null) {
            return null;
        }

        return _localVideo.getVideoTrack();
    }

    public AudioTrack getLocalAudioTrack() {
        return _audioTrack;
    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return _peerConnectionFactory;
    }

    public Activity getActivity() {
        return cordova.getActivity();
    }

    public WebView getWebView() {
        return this.getWebView();
    }

    public VideoConfig getVideoConfig() {
        return this._videoConfig;
    }

    public void addRemoteVideoTrack(VideoTrack videoTrack) {
        _remoteVideos.add(new VideoTrackRendererPair(videoTrack, null));
        refreshVideoView();
    }

    public void removeRemoteVideoTrack(VideoTrack videoTrack) {
        for (VideoTrackRendererPair pair : _remoteVideos) {
            if (pair.getVideoTrack() == videoTrack) {
                if (pair.getVideoRenderer() != null) {
                    pair.getVideoTrack().removeRenderer(pair.getVideoRenderer());
                    pair.setVideoRenderer(null);
                }

                pair.setVideoTrack(null);

                _remoteVideos.remove(pair);
                refreshVideoView();
                return;
            }
        }
    }

    private void createVideoView() {
        Point size = new Point();
        size.set((int) (_videoConfig.getContainer().getWidth() * _videoConfig.getDevicePixelRatio()),
                (int) (_videoConfig.getContainer().getHeight() * _videoConfig.getDevicePixelRatio()));

        _videoView = new SurfaceViewRenderer(getActivity());
        _videoView.setMirror(true);

        EglBase rootEglBase = EglBase.create();
        _videoView.init(rootEglBase.getEglBaseContext(), null);

        ((WebView) webView.getView()).addView(_videoView, _videoParams);
    }

    private void refreshVideoView() {
        int n = _remoteVideos.size();

        for (VideoTrackRendererPair pair : _remoteVideos) {
            if (pair.getVideoRenderer() != null) {
                pair.getVideoTrack().removeRenderer(pair.getVideoRenderer());
            }

            pair.setVideoRenderer(null);
        }

        if (_localVideo != null && _localVideo.getVideoRenderer() != null) {
            _localVideo.getVideoTrack().removeRenderer(_localVideo.getVideoRenderer());
            _localVideo.setVideoRenderer(null);
        }

        if (_videoView != null) {
            ((WebView) webView.getView()).removeView(_videoView);
            _videoView = null;
        }
        // do not create video when only audio
        n = 0;
        if (n > 0) {
            createVideoView();

            int rows = n < 9 ? 2 : 3;
            int videosInRow = n == 2 ? 2 : (int) Math.ceil((float) n / rows);

            int videoSize = (int) ((float) _videoConfig.getContainer().getWidth() / videosInRow);
            int actualRows = (int) Math.ceil((float) n / videosInRow);

            int y = getCenter(actualRows, videoSize, _videoConfig.getContainer().getHeight());

            int videoIndex = 0;
            int videoSizeAsPercentage = getPercentage(videoSize, _videoConfig.getContainer().getWidth());

            for (int row = 0; row < rows && videoIndex < n; row++) {
                int x = getCenter(row < row - 1 || n % rows == 0 ?
                                videosInRow : n - (Math.min(n, videoIndex + videosInRow) - 1),
                        videoSize,
                        _videoConfig.getContainer().getWidth());

                for (int video = 0; video < videosInRow && videoIndex < n; video++) {
                    VideoTrackRendererPair pair = _remoteVideos.get(videoIndex++);

                    int widthPercentage = videoSizeAsPercentage;
                    int heightPercentage = videoSizeAsPercentage;
                    if ((x + widthPercentage) > 100) {
                        widthPercentage = widthPercentage - x;
                    }
                    if ((y + heightPercentage) > 100) {
                        heightPercentage = heightPercentage - y;
                    }

                    pair.setVideoRenderer(new VideoRenderer(_videoView));

                    pair.getVideoTrack().addRenderer(pair.getVideoRenderer());

                    x += videoSizeAsPercentage;
                }

                y += getPercentage(videoSize, _videoConfig.getContainer().getHeight());
            }

            if (_videoConfig.getLocal() != null && _localVideo != null) {
                _localVideo.getVideoTrack().addRenderer(new VideoRenderer(_videoView));
            }
        }
    }

    int getCenter(int videoCount, int videoSize, int containerSize) {
        return getPercentage((int) Math.round((containerSize - videoSize * videoCount) / 2.0), containerSize);
    }

    PluginResult getSessionKeyPluginResult(String sessionKey) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", "__set_session_key");
        json.put("sessionKey", sessionKey);

        PluginResult result = new PluginResult(PluginResult.Status.OK, json);
        result.setKeepCallback(true);

        return result;
    }

    public void onSessionDisconnect(String sessionKey) {
        _sessions.remove(sessionKey);


        if (_sessions.size() == 0) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (_localVideo != null) {
                        if (_localVideo.getVideoTrack() != null && _localVideo.getVideoRenderer() != null) {
                            _localVideo.getVideoTrack().removeRenderer(_localVideo.getVideoRenderer());
                        }

                        _localVideo = null;
                    }

                    if (_videoView != null) {
                        _videoView.setVisibility(View.GONE);
                        ((WebView) webView.getView()).removeView(_videoView);
                    }

                    if (_videoSource != null) {
                        if (_shouldDispose) {
                            _videoSource.dispose();
                        } else {
//                            _videoSource.stop();
                        }

                        _videoSource = null;
                    }

                    if (_videoCapturer != null) {
                        try {
                            _videoCapturer.dispose();
                        } catch (Exception e) {
                        }
                        _videoCapturer = null;
                    }

                    if (_audioSource != null) {
//                        _audioSource.dispose();
                        _audioSource = null;

                        _audioTrack = null;
                    }

                    if (_peerConnectionFactory != null) {
                        //_peerConnectionFactory.dispose();
                        _peerConnectionFactory = null;
                    }

                    _remoteVideos.clear();
                    _shouldDispose = true;
                }
            });
        }
    }

    public boolean shouldDispose() {
        return _shouldDispose;
    }


    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, 20));
                return;
            }
        }
        this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }


    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));

        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
}
