package io.fpt.lib.Interface;

import org.webrtc.StatsReport;

/**
 * Created by hoai nam on 6/14/2017.
 */

public interface FCallEventInterface {

    void onRegistered(String department, String curentTime, String Call_ID);

    void onUnregistered();

    void onRegistrationFailed(String response);

    void onConnected();

    void onDisconnected();

    void onConnecting();

    void onMessage();

    void onBye();

    void onChangeStatus(boolean isRegistered);

    void onTerminated(String cause, String data);

    void onRejected(final int code);

    void onProgress(final String code);

    void onFailed(String cause);

    void onAccepted();

    void onInvite(String number, String numberLocal, final String data);

    void onReady();

    void onLogout();

    void onLostConnect();

    void onGetStats(StatsReport[] statsReports);

    void bitrateStats(long bitrateSent, long bitrateRecv);

    void packetsStats(long packetsLostSent, long packetsSent, long packetsLostRecv, long packetsRecv);
}
