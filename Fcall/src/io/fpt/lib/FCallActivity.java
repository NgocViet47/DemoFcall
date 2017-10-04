package io.fpt.lib;

import android.os.Bundle;
import android.text.TextUtils;

import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaWebView;
import org.webrtc.StatsReport;

import io.fpt.lib.Interface.FCallEventInterface;


/**
 * Created by hoai nam on 6/16/2017.
 */

public class FCallActivity extends CordovaActivity implements FCallEventInterface {

    public static CordovaWebView webApp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initServer();
    }

    protected void initServer() {
        loadUrl(launchUrl);
        if (appView != null) {
            webApp = appView;
        }
    }


    public void register(String user, String pass, String tokenDevice) {

        if (TextUtils.isEmpty(user) || TextUtils.isEmpty(pass)) {
            return;
        }

        String u = "'" + user + "'";
        String p = "'" + pass + "'";
        String token = "'" + tokenDevice + " ANDROID " + tokenDevice + "'";
        final String cmd = "javascript:registertoserver(" + u + "," + p + "," + token + ")";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }


    public void logout() {
        final String cmd = "javascript:logout()";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }

    public void unregister() {
        final String cmd = "javascript:hidden()";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }

    public void makeACall(String number) {
        final String cmd = "javascript:call('" + number + "')";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }

    public void rejectACall() {
        final String cmd = "javascript:onDestroy()";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }

    public void acceptACall() {
        final String cmd = "javascript:accept()";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }


    //send dtmf function
    public void sendDtmf(String number) {
        final String cmd = "javascript:sendDtmf('" + number + "')";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appView.getEngine().evaluateJavascript(cmd, null);
            }
        });
    }


    @Override
    public void onRegistered(String department, String curentTime, String Call_ID) {

    }

    @Override
    public void onUnregistered() {

    }

    @Override
    public void onRegistrationFailed(String response) {

    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onMessage() {

    }

    @Override
    public void onBye() {

    }

    @Override
    public void onChangeStatus(boolean isRegistered) {

    }

    @Override
    public void onTerminated(String cause, String data) {

    }

    @Override
    public void onRejected(int code) {

    }

    @Override
    public void onProgress(String code) {

    }

    @Override
    public void onFailed(String cause) {

    }

    @Override
    public void onAccepted() {

    }

    @Override
    public void onInvite(String number, String numberLocal, String data) {

    }

    @Override
    public void onReady() {

    }

    @Override
    public void onLogout() {

    }

    @Override
    public void onLostConnect() {

    }

    @Override
    public void onGetStats(StatsReport[] statsReports) {

    }

    @Override
    public void bitrateStats(long bitrateSent, long bitrateRecv) {

    }

    @Override
    public void packetsStats(long packetsLostSent, long packetsSent, long packetsLostRecv, long packetsRecv) {

    }
}
