package io.fpt.lib.Plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import io.fpt.lib.Common.Constant;
import io.fpt.lib.FCallActivity;
import io.fpt.lib.Interface.FCallEventInterface;

/**
 * Created by hoai nam on 6/15/2017.
 */

public class FCallEventPlugin extends CordovaPlugin {
    public CallbackContext callbackContext;
    FCallEventInterface fCallEventInterface;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        final CallbackContext _callbackContext = callbackContext;
        this.callbackContext = _callbackContext;
        fCallEventInterface = ((FCallActivity) (webView.getContext()));

        if (action.equals(Constant.EVENT_ON_READY)) {
            fCallEventInterface.onReady();
        }
        if (action.equals(Constant.EVENT_ON_LOGOUT)) {
            fCallEventInterface.onLogout();
        }
        if (action.equals(Constant.EVENT_ON_REJECT)) {
            int code = args.getInt(0);
            fCallEventInterface.onRejected(code);
        }
        if (action.equals(Constant.EVENT_ON_PROGRESS)) {
            String code = args.getString(0);
            fCallEventInterface.onProgress(code);
        }
        if (action.equals(Constant.EVENT_ON_BYE)) {
            fCallEventInterface.onBye();
        }
        if (action.equals(Constant.EVENT_ON_FAILED)) {
            String cause = args.getString(0);
            fCallEventInterface.onFailed(cause);
        }
        if (action.equals(Constant.EVENT_ON_TERMINATED)) {
            String cause = args.getString(0);
            String data = args.getString(1);
            fCallEventInterface.onTerminated(cause, data);
        }
        if (action.equals(Constant.EVENT_ON_ACCEPTED)) {
            fCallEventInterface.onAccepted();
        }
        if (action.equals(Constant.EVENT_ON_REGISTERED)) {
            String department = args.getString(0);
            String curentTime = args.getString(1);
            String Call_ID = args.getString(2);
            fCallEventInterface.onRegistered(department, curentTime, Call_ID);
        }
        if (action.equals(Constant.EVENT_ON_CHANGE_STATUS)) {
            boolean isRegistered = args.getBoolean(0);
            fCallEventInterface.onChangeStatus(isRegistered);
        }
        if (action.equals(Constant.EVENT_ON_UNREGISTERED)) {
            fCallEventInterface.onUnregistered();
        }
        if (action.equals(Constant.EVENT_ON_REGISTRATION_FAILED)) {
            String response = args.getString(0);
            fCallEventInterface.onRegistrationFailed(response);
        }
        if (action.equals(Constant.EVENT_ON_CONNECTED)) {
            fCallEventInterface.onConnected();
        }
        if (action.equals(Constant.EVENT_ON_DISCONNECTED)) {
            fCallEventInterface.onDisconnected();
        }
        if (action.equals(Constant.EVENT_ON_CONNECTING)) {
            fCallEventInterface.onConnecting();
        }
        if (action.equals(Constant.EVENT_ON_INVITE)) {
            String number = args.getString(0);
            String numberLocal = args.getString(1);
            String data = args.getString(2);
            fCallEventInterface.onInvite(number, numberLocal, data);
        }
        if (action.equals(Constant.EVENT_ON_MESSAGE)) {
            fCallEventInterface.onMessage();
        }

        if (action.equals(Constant.EVENT_ON_LOST_CONNECT)) {
            fCallEventInterface.onLostConnect();
        }

        callbackContext.error("Invalid action: " + action);
        return false;
    }


}
