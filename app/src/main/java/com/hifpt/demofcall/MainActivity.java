package com.hifpt.demofcall;

import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.webrtc.StatsReport;

import io.fpt.lib.FCallActivity;

public class MainActivity extends FCallActivity {

    private String user = "16026";

    private TextView txtRegistered, txtStatusCall, txtBitrate, txtPacket;
    private EditText editNumber;
    private Button btnCall, btnAccept, btnEnd;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtRegistered = (TextView) findViewById(R.id.txtRegisted);
        txtStatusCall = (TextView) findViewById(R.id.txtStatusCall);
        txtBitrate = (TextView) findViewById(R.id.txtBitrate);
        txtPacket = (TextView) findViewById(R.id.txtPacket);
        editNumber = (EditText) findViewById(R.id.editNumber);
        btnCall = (Button) findViewById(R.id.btnCall);
        btnAccept = (Button) findViewById(R.id.btnAccept);
        btnEnd = (Button) findViewById(R.id.btnEnd);


        txtRegistered.setText(user + " Register: " + false);

        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeACall(editNumber.getText().toString());
                txtStatusCall.setText("calling " + editNumber.getText().toString());
                txtBitrate.setText("");
                txtPacket.setText("");
            }
        });

        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rejectACall();
            }
        });
    }

    @Override
    public void onReady() {
        register(user, "ngocviet", "");
    }

    @Override
    public void onRegistered(String department, String curentTime, String Call_ID) {
        Toast.makeText(this, "onRegistered", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUnregistered() {

    }

    @Override
    public void onRegistrationFailed(String response) {
        Toast.makeText(this, "onRegistrationFailed", Toast.LENGTH_SHORT).show();
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onBye");
            }
        });
    }

    @Override
    public void onChangeStatus(final boolean isRegistered) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtRegistered.setText(user + " Register: " + isRegistered);
            }
        });
    }

    @Override
    public void onTerminated(String cause, String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("");
            }
        });
    }

    @Override
    public void onRejected(int code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onRejected");
            }
        });
    }

    @Override
    public void onProgress(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onProgress " + code);
            }
        });
    }

    @Override
    public void onFailed(String cause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onFailed");
            }
        });
    }

    @Override
    public void onAccepted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onAccepted");
            }
        });
    }

    @Override
    public void onInvite(final String number, String numberLocal, String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusCall.setText("onInvite " + number);
            }
        });
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
    public void bitrateStats(final long bitrateSent, final long bitrateRecv) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtBitrate.setText("Bitrate\n\nSent: " + bitrateSent + " byte/s" + "\n Recv: " + bitrateRecv + " byte/s");
            }
        });
    }

    @Override
    public void packetsStats(final long packetsLostSent, final long packetsSent, final long packetsLostRecv, final long packetsRecv) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtPacket.setText("Packets\n\nSent: " + packetsLostSent + "/" + packetsSent + "\nRecv: " + packetsLostRecv + "/" + packetsRecv);
            }
        });
    }
}
