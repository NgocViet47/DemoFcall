package com.dooble.phonertc;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoTrack;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fpt.lib.Common.NumbersUtils;
import io.fpt.lib.FCallActivity;
import io.fpt.lib.Interface.FCallEventInterface;

public class Session {
    // Synchronize on quit[0] to avoid teardown-related crashes.
    private final Boolean[] _quit = new Boolean[]{false};
    PhoneRTCPlugin _plugin;
    CallbackContext _callbackContext;
    SessionConfig _config;
    String _sessionKey;
    MediaConstraints _sdpMediaConstraints;
    PeerConnection _peerConnection;
    private LinkedList<IceCandidate> _queuedRemoteCandidates;
    private Object _queuedRemoteCandidatesLocker = new Object();
    private MediaStream _localStream;
    private VideoTrack _videoTrack;
    private SDPObserver _sdpObserver = new SDPObserver();
    private PCObserver _pcObserver = new PCObserver();

    private boolean isReceiver;
    private SDPObserver sdpObserver = null;
    private MediaConstraints mediaConstraints = null;

    private FCallEventInterface fCallEventInterface;
    private Timer myTimer;
    private boolean complete;
    private long sent_byte;
    private long recv_byte;


    public Session(PhoneRTCPlugin plugin, CallbackContext callbackContext, SessionConfig config, String sessionKey) {
        _plugin = plugin;
        _callbackContext = callbackContext;
        _config = config;
        _sessionKey = sessionKey;

        fCallEventInterface = ((FCallActivity) (_plugin.getActivity()));
    }

    public void call() {
        _queuedRemoteCandidates = new LinkedList<IceCandidate>();
        _quit[0] = false;

        // Initialize ICE server list
        final LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
//        iceServers.add(new PeerConnection.IceServer("stun:42.117.9.24:3478"));
//        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
//        iceServers.add(new PeerConnection.IceServer(_config.getTurnServerHost(),
//                _config.getTurnServerUsername(),
//                _config.getTurnServerPassword()));

        // Initialize SDP media constraints
        _sdpMediaConstraints = new MediaConstraints();
        _sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"));
        _sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", _plugin.getVideoConfig() == null ? "false" : "true"));

        // Initialize PeerConnection
        MediaConstraints pcMediaConstraints = new MediaConstraints();
        pcMediaConstraints.optional.add(new MediaConstraints.KeyValuePair(
                "DtlsSrtpKeyAgreement", "true"));

        _peerConnection = _plugin.getPeerConnectionFactory()
                .createPeerConnection(iceServers, pcMediaConstraints, _pcObserver);

        // Initialize local stream
        createOrUpdateStream();

        // Create offer if initiator
        if (_config.isInitiator()) {
            _peerConnection.createOffer(_sdpObserver, _sdpMediaConstraints);
        }
    }

    public void receiveMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = (String) json.get("type");
            if (type.equals("candidate")) {
                final IceCandidate candidate = new IceCandidate(
                        (String) json.get("id"), json.getInt("label"),
                        (String) json.get("candidate"));

                synchronized (_queuedRemoteCandidatesLocker) {
                    if (_queuedRemoteCandidates != null) {
                        _queuedRemoteCandidates.add(candidate);
                    } else {
                        _plugin.getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                if (_peerConnection != null) {
                                    _peerConnection.addIceCandidate(candidate);
                                }
                            }
                        });
                    }
                }

            } else if (type.equals("answer") || type.equals("offer")) {
                final SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        preferISAC((String) json.get("sdp")));
                _plugin.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (_peerConnection != null)
                            _peerConnection.setRemoteDescription(_sdpObserver, sdp);
                    }
                });
            } else if (type.equals("bye")) {
                Log.d("com.dooble.phonertc", "Remote end hung up; dropping PeerConnection");

                _plugin.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        disconnect(false);
                    }
                });
            } else {
                // throw new RuntimeException("Unexpected message: " + message);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void createOrUpdateStream() {
        if (_localStream != null) {
            _peerConnection.removeStream(_localStream);
            _localStream = null;
        }

        _localStream = _plugin.getPeerConnectionFactory().createLocalMediaStream("ARDAMS");

        if (_config.isAudioStreamEnabled() && _plugin.getLocalAudioTrack() != null) {
            _localStream.addTrack(_plugin.getLocalAudioTrack());
        }

        if (_config.isVideoStreamEnabled() && _plugin.getLocalVideoTrack() != null) {
            _localStream.addTrack(_plugin.getLocalVideoTrack());
        }

        _peerConnection.addStream(_localStream);
        //addRemoteStream () ;

        final StatsObserver statsObserver = new StatsObserver() {
            @Override
            public void onComplete(StatsReport[] statsReports) {
                if (!complete) {
                    if (fCallEventInterface != null)
                        fCallEventInterface.onGetStats(statsReports);

                    long bitrateSent = 0;
                    long bitrateRecv = 0;
                    long packetsLostSent = 0;
                    long packetsSent = 0;
                    long packetsLostRecv = 0;
                    long packetsRecv = 0;

                    for (StatsReport stat :
                            statsReports) {
                        if (stat.id.contains("send")) {
                            for (StatsReport.Value value :
                                    stat.values) {
                                if (value.name.equals("bytesSent")) {
                                    long byteValue = NumbersUtils.parseLong(value.value);
                                    long bitrate = byteValue - sent_byte;
                                    if (bitrate > 0)
                                        bitrateSent = bitrate;
                                    sent_byte = byteValue;
                                }

                                if (value.name.equals("packetsLost")) {
                                    packetsLostSent = NumbersUtils.parseLong(value.value);
                                }

                                if (value.name.equals("packetsSent")) {
                                    packetsSent = NumbersUtils.parseLong(value.value);
                                }
                            }
                        }

                        if (stat.id.contains("recv")) {
                            for (StatsReport.Value value :
                                    stat.values) {
                                if (value.name.equals("bytesReceived")) {
                                    long byteValue = NumbersUtils.parseLong(value.value);
                                    long bitrate = byteValue - recv_byte;
                                    if (bitrate > 0)
                                        bitrateRecv = bitrate;
                                    recv_byte = byteValue;
                                }

                                if (value.name.equals("packetsLost")) {
                                    packetsLostRecv = NumbersUtils.parseLong(value.value);
                                }

                                if (value.name.equals("packetsReceived")) {
                                    packetsRecv = NumbersUtils.parseLong(value.value);
                                }
                            }
                        }
                    }

                    if (fCallEventInterface != null) {
                        fCallEventInterface.bitrateStats(bitrateSent, bitrateRecv);
                        fCallEventInterface.packetsStats(packetsLostSent, packetsSent, packetsLostRecv, packetsRecv);
                    }

                }
                complete = true;
            }
        };

        if (myTimer != null)
            myTimer.cancel();

        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (_peerConnection != null && _localStream.audioTracks.size() > 0) {
                    complete = false;
                    _peerConnection.getStats(statsObserver, _localStream.audioTracks.get(0));
                } else
                    myTimer.cancel();
            }
        }, 1000, 1000);
    }

    void sendMessage(JSONObject data) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, data);
        result.setKeepCallback(true);
        _callbackContext.sendPluginResult(result);
    }

    String preferISAC(String sdpDescription) {
        if (!sdpDescription.contains("a=rtcp-mux"))
            sdpDescription = sdpDescription + "a=rtcp-mux\r\n";

        String[] lines = sdpDescription.split("\r?\n");
        int mLineIndex = -1;
        String isac16kRtpMap = null;
        Pattern isac16kPattern = Pattern
                .compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
        for (int i = 0; (i < lines.length)
                && (mLineIndex == -1 || isac16kRtpMap == null); ++i) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i;
                continue;
            }
            Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1);
                continue;
            }
        }
        if (mLineIndex == -1) {
            Log.d("com.dooble.phonertc",
                    "No m=audio line, so can't prefer iSAC");
            return sdpDescription;
        }
        if (isac16kRtpMap == null) {
            Log.d("com.dooble.phonertc",
                    "No ISAC/16000 line, so can't prefer iSAC");
            return sdpDescription;
        }
        String[] origMLineParts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int origPartIndex = 0;
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(isac16kRtpMap).append(" ");
        for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
            if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
                newMLine.append(origMLineParts[origPartIndex]).append(" ");
            }
        }
        lines[mLineIndex] = newMLine.toString();
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    public void disconnect(boolean sendByeMessage) {
        synchronized (_quit[0]) {
            if (_quit[0]) {
                return;
            }

            _quit[0] = true;

            if (_videoTrack != null) {
                _plugin.removeRemoteVideoTrack(_videoTrack);
                _videoTrack = null;
            }

            if (sendByeMessage) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("type", "bye");
                    sendMessage(data);
                } catch (JSONException e) {
                }
            }

            if (_peerConnection != null) {
                if (_plugin.shouldDispose()) {
                    _peerConnection.dispose();
                } else {
                    _peerConnection.close();
                }

                _peerConnection = null;
            }

            try {
                JSONObject data = new JSONObject();
                data.put("type", "__disconnected");
                sendMessage(data);
            } catch (JSONException e) {
            }

            _plugin.onSessionDisconnect(_sessionKey);
        }
    }

    public void setConfig(SessionConfig config) {
        _config = config;
    }

    public void acceptACall() {
        if (this.sdpObserver == null)
            return;
        if (this.mediaConstraints == null)
            return;
        if (isReceiver && _peerConnection != null) {
            isReceiver = false;
            _peerConnection.createAnswer(this.sdpObserver,
                    this.mediaConstraints);
        }
    }

    private void setAnswer(SDPObserver _sdpObserver, MediaConstraints mediaConstraints) {
        this.sdpObserver = _sdpObserver;
        this.mediaConstraints = mediaConstraints;
    }

    private class PCObserver implements PeerConnection.Observer {

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            Log.d("Session", "onIceCandidate " + iceCandidate.toString());
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", "candidate");
                        json.put("label", iceCandidate.sdpMLineIndex);
                        json.put("id", iceCandidate.sdpMid);
                        json.put("candidate", iceCandidate.sdp);
                        sendMessage(json);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch bloc
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            Log.d("Session", "onAddStream " + stream.toString());
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (stream.videoTracks.size() > 0) {
                        _videoTrack = stream.videoTracks.get(0);

                        if (_videoTrack != null) {
                            _plugin.addRemoteVideoTrack(_videoTrack);
                        }
                    }

                    try {
                        JSONObject data = new JSONObject();
                        data.put("type", "__answered");
                        sendMessage(data);
                    } catch (JSONException e) {

                    }
                }
            });
        }

        @Override
        public void onDataChannel(DataChannel stream) {
            // TODO Auto-generated method stub
            Log.d("Session", "onDataChannel " + stream.toString());
        }


        @Override
        public void onIceConnectionChange(IceConnectionState arg0) {
            // TODO Auto-generated method stub
            Log.d("Session", "onIceConnectionChange " + arg0.toString());
            if (arg0.ordinal() == IceConnectionState.DISCONNECTED.ordinal() || arg0.ordinal() == IceConnectionState.FAILED.ordinal()) {
                final String cmd = "javascript:lostConnect()";
                _plugin.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        _plugin.webView.getEngine().evaluateJavascript(cmd, null);
                    }
                });
            }
        }

        @Override
        public void onIceGatheringChange(IceGatheringState arg0) {
            Log.d("Session", "onIceGatheringChange " + arg0.toString());
            try {
                JSONObject json = new JSONObject();
                json.put("type", "IceGatheringChange");
                json.put("state", arg0.name());
                sendMessage(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            Log.d("Session", "onRemoveStream " + stream.toString());
        }

        @Override
        public void onRenegotiationNeeded() {
            // TODO Auto-generated method stub
            Log.d("Session", "onRenegotiationNeeded ");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d("Session", "onAddTrack ");
        }

        @Override
        public void onSignalingChange(
                PeerConnection.SignalingState signalingState) {
            Log.d("Session", "onSignalingChange ");
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            Log.d("Session", "onIceCandidatesRemoved ");
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d("Session", "onIceConnectionReceivingChange " + receiving);
        }

    }

    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            Log.d("Session SDP", "onCreateSuccess");
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    SessionDescription sdp = new SessionDescription(
                            origSdp.type, preferISAC(origSdp.description));
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", sdp.type.canonicalForm());
                        json.put("sdp", sdp.description);
                        sendMessage(json);
                        _peerConnection.setLocalDescription(_sdpObserver, sdp);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            Log.d("Session SDP", "onSetSuccess");
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (_config.isInitiator()) {
                        if (_peerConnection == null)
                            return;
                        if (_peerConnection.getRemoteDescription() != null) {
                            // We've set our local offer and received & set the
                            // remote
                            // answer, so drain candidates.
                            drainRemoteCandidates();
                            createOrUpdateStream();
                        }
                    } else {
                        if (_peerConnection == null)
                            return;
                        if (_peerConnection.getLocalDescription() == null) {
                            // We just set the remote offer, time to create our
                            // answer.
                            isReceiver = true;
                            setAnswer(Session.SDPObserver.this, _sdpMediaConstraints);
                        } else {
                            // Sent our answer and set it as local description;
                            // drain
                            // candidates.
                            drainRemoteCandidates();
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            Log.d("Session SDP", "onCreateFailure");
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    throw new RuntimeException("createSDP error: " + error);
                }
            });
        }

        @Override
        public void onSetFailure(final String error) {
            Log.d("Session SDP", "onSetFailure");
            _plugin.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    Log.d("SDP", error);
                    //throw new RuntimeException("setSDP error: " + error);
                }
            });
        }


        private void drainRemoteCandidates() {
            synchronized (_queuedRemoteCandidatesLocker) {
                if (_queuedRemoteCandidates == null)
                    return;

                for (IceCandidate candidate : _queuedRemoteCandidates) {
                    _peerConnection.addIceCandidate(candidate);
                }

                _queuedRemoteCandidates = null;
            }
        }
    }

}