window.onload = function() {
    console.log("Script: onload()");
    document.addEventListener("deviceReady", onDeviceReady, false);
}

function onDeviceReady() {
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onReady");
        console.log("Script: onDeviceReady()");
}
 
var resizeTimer;
var username = "";
var password = "";
var numberCall = "";
var statusCallFlow = 0;

var isregister = false;


var ua = null; //UserAgent
var session;
var registered = false;

var username;
var password;

//var config = {
//		userAgentString: 'SIP.js/0.7.2-min Android',
//		traceSip: true,
//		register: true,
//		registerExpires: 1800,
//		session_timers: false,
//		wsServers: "ws://183.80.132.20:7080",
//		server: "183.80.132.20",
//		stunServers: "stun:42.117.9.24:3478",
//		hackIpInContact: true,
//		rtcpMuxPolicy: "negotiate"
//};
var config = {
  userAgentString: 'SIP.js/0.7.2-min Android',
  traceSip: true,
  register: true,
  registerExpires: 1800,
  session_timers: false,
  wsServers: "ws://sip.fpt.vn:7080",
  server: "sip.fpt.vn",
  stunServers: "stun:42.117.9.24:3478",
  hackIpInContact: true,
  rtcpMuxPolicy: "negotiate"
};

function login(user, pass, token) {
    if (ua != null) {
        ua.unregister(options);
        ua.stop();
        delete ua;
        ua = null;
    }

	var PhoneRTCMediaHandler = cordova.require('com.onsip.cordova.SipjsMediaHandler')(SIP);
	config['mediaHandlerFactory'] = PhoneRTCMediaHandler;

    console.log("Script: login from android..." + user + " " + pass + " " + token);

	config['displayName'] = user;
    config['uri'] = user + "@" + config.server;
    config['authorizationUser'] = user;
    config['password'] = pass;
    config['userAgentString'] = token;
    console.log("Script: createUA() call from Android");
    ua = new SIP.UA(config);
    handleUAEventsAndroid();
    ua.start();

    username = user;
    password = pass;
}

function registertoserver(user, pass, token) {
	console.log("Script: Register to server");
    config['userAgentString'] = token;
    if (ua != null) {
        ua.unregister(options);
        ua.register();
    } else {
		login(user, pass, token);
	}
	username = user;
    password = pass;
}

var options = {
    'all': true,
};

function logout() {

    if (session != null) {
        console.log("Script: logout: session");
        session.terminate();
        delete session;
        session = null;
    }

    if (ua != null) {
        console.log("Script: logout: unregister");
        ua.unregister(options);
        ua.stop();
        delete ua;
        ua = null;
    }

	cordova.exec(null, null, "PhoneFPTCallPlugin", "onLogout");
}


function hidden() {
    console.log("Script: hidden: unregister");
    if (ua != null) {
        ua.unregister(options);
    }
}


// A shortcut function to construct the media options for an SIP session.
function mediaOptions(audio, video, remoteRender, localRender) {
    return {
        media: {
            constraints: {
                audio: audio,
                video: video
            },
            render: {
                remote: remoteRender
                ,
                local: localRender
            }
        }
    };
}


function accept() {

    console.log("Script: accept");

    if (session == null) {
        console.log("Script: accept: session null");
        return;
    }

    var sesId = localStorage.getItem("sesId");
    cordova.exec(null, null, "PhoneRTCPlugin", "acceptAcall", [sesId]);

    var remote = document.querySelector('video#remoteVideo');
    var local = document.querySelector('video#localVideo');
    var options = mediaOptions(true, false, remote, local);
    session.accept(options);
}

function call(numberCall) {
    statusCallFlow = 2;
    console.log("Script: makeCall() numberCall: " + numberCall);

    var remote = document.querySelector('video#remoteVideo');
    var local = document.querySelector('video#localVideo');
    var options = mediaOptions(true, false, remote, local);
    var target = numberCall + "@" + config.server;

    console.log("Script: target " + target);

    var session_call = ua.invite("sip:" + target, options);
    setupSession(session_call);
}

//send dtmf
function sendDtmf(number){
    console.log("Script: sendDtmf; Tone: " + number);
    if(session != null)
        session.dtmf(number);
}


function setupSession(s) {

    isEnded = false;
    isMusicWaiting = false;
    isOnTerminated = false;
    session = s;

    session.data = new Date().getTime().toString();

    session.on('accepted', onAccepted);
    session.on('cancel', cancelSession);
    session.on('bye', byeSession);
    session.on('failed', failedSession);

    session.on('rejected', function(response, cause) {
        console.log("Script: session: rejected: " + response.status_code);
        var code = response.status_code;
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onRejected", [code]);
    });

    session.on('terminated', function(message, cause) {
        console.log("Script: session: terminated " + cause);
        statusCallFlow = 0;
        onTerminated(cause);
    });


    session.on('progress', function(response) {
        console.log("Script: session: progress " + response.status_code);

        var code = 100;

        //ipphone error
        var number = session.remoteIdentity.uri.toString();
        if (number != null){
            var index = number.indexOf(':');
            if(index == -1) index = 0;
            var first = number.substring(index + 1, index + 2);

            if(first != null && first != 9){
                if (response.status_code === 183 && response.body && this.hasOffer && !this.dialog) {
                  if (!response.hasHeader('require') || response.getHeader('require').indexOf('100rel') === -1) {
                    if (this.mediaHandler.hasDescription(response)) {
                      // Mimic rel-100 behavior

                      if (!this.createDialog(response, 'UAC')) { // confirm the dialog, eventhough it's a provisional answer
                        return
                      }

                      isMusicWaiting = true

                      // this ensures that 200 will not try to set description
                      this.hasAnswer = true

                      this.dialog.pracked.push(response.getHeader('rseq'))

                      this.status = SIP.Session.C.STATUS_EARLY_MEDIA

                      // Mute local streams since we are going to establish early media.
                      this.mute()

                      this.mediaHandler
                        .setDescription(response.body, null, null)
                        .catch((reason) => {
                          this.logger.warn(reason)
                          this.failed(response, C.causes.BAD_MEDIA_DESCRIPTION)
                          this.terminate({ status_code: 488, reason_phrase: 'Bad Media Description' })
                        })

                      code = 183;
                    }
                  }
                }
            }
        }

        if(code != 183 && response.status_code === 183)
           cordova.exec(null, null, "PhoneFPTCallPlugin", "onProgress", [180]);
        else
           cordova.exec(null, null, "PhoneFPTCallPlugin", "onProgress", [response.status_code]);
    });
}

function endSession() {
    console.log("Script: endSession");
    if (session) {
        delete session;
        session = null;
        console.log("Script: endSession: delete session");
    }
}

function cancelSession() {
    console.log("Script: session: cancelSession");
}

function byeSession(request) {
    console.log("Script: session: byeSession");
    delete session;
    session = null;
	cordova.exec(null, null, "PhoneFPTCallPlugin", "onBye");
}

var failed = false;

function failedSession(response, cause) {
    console.log("Script: session: failedSession");
    failed = true;

	cordova.exec(null, null, "PhoneFPTCallPlugin", "onFailed", [cause]);

    if (statusCallFlow != 0) {
        statusCallFlow = 0;
        onTerminated(cause);
    }
}

var isOnTerminated = false;

function onTerminated(cause) {
    if (isOnTerminated)
        return;

    isEnded = true;
    isMusicWaiting = false;

    removeMedia(); //clear screen
    console.log("Script: endSession: clear screen");

    if(session != null)
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onTerminated", [cause, session.data]);
    else
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onTerminated", [cause, null]);

    isOnTerminated = true;
    endSession();
}

function onAccepted() {

    console.log("Script: onAccepted () ");

    if(!isEnded) {
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onAccepted");
    } else {
        onDestroy();
        console.log("Script: session destroy");
    }
}


function removeMedia() {
    console.log("Script: removeMedia");
    if(session != null)
        session.mediaHandler.render(null, document.querySelector('video#remoteVideo'));
    document.querySelector('video#remoteVideo').pause();
    document.querySelector('video#localVideo').pause();
    document.querySelector('video#remoteVideo').src = null;
    document.querySelector('video#localVideo').src = null;
}


function handleUAEventsAndroid() {

    console.log("Script: handleUAEvents()");

    ua.on('registered', function(e) {
        console.log("Script: registered " + e);
        var department = e.getHeader('Department');
        var currentTime = e.getHeader('Current-Time');
        var callID = e.getHeader('Call-ID');
      
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onRegistered", [department, currentTime, callID]);
        isregister = true;

        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        localStorage.setItem("user", username);
        localStorage.setItem("pass", password);
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);
    });


    ua.on('unregistered', function(e) {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log("Script: unregistered " + isRegistered);
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onUnregistered");
    });

    ua.on('registrationFailed', function(cause, response) {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }

        console.log("Script: registrationFailed " + isRegistered);

		cordova.exec(null, null, "PhoneFPTCallPlugin", "onRegistrationFailed", [response]);
		
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        isregister = false;
    });

    ua.on('connected', function() {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log('Script: connected WebSocket server ' + isRegistered);
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onConnected");
    });

    ua.on('disconnected', function() {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log('Script: disconnected WebSocket server ' + isRegistered);
       
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onDisconnected");
    });


    ua.on('connecting', function() {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log('Script: ua connecting ' + isRegistered);
		
		cordova.exec(null, null, "PhoneFPTCallPlugin", "onConnecting");
    });

    //a remote invitation is calling
    ua.on('invite', function(incomingsession) {
        incomingsession.mute();
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log('Script: an invitation is comming ' + statusCallFlow);

        if (statusCallFlow == 0) {
            statusCallFlow = 1;
            setupSession(incomingsession);
            var number = session.remoteIdentity.uri.toString();
            var numberLocal = session.localIdentity.uri.toString();
			cordova.exec(null, null, "PhoneFPTCallPlugin", "onInvite", [number, numberLocal, session.data]);
        } else {
            incomingsession.reject();
        }
    });

    ua.on('message', function(msg) {
        var isRegistered = false;
        if (ua != null) {
            isRegistered = ua.isRegistered();
        }
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onChangeStatus", [isRegistered]);

        console.log("Script: msg: " + msg);
        console.log("Script: msg.body: " + msg.body);

		cordova.exec(null, null, "PhoneFPTCallPlugin", "onMessage");
    });
}

var isEnded = false;
var isMusicWaiting = false;

function onDestroy() {

    isEnded = true;
    isMusicWaiting = false;

    console.log("Script: onDestroy");
    if (session == null) {
        console.log("Script: session null");
        return;
    }
    if (session.startTime && !session.endTime) {
        console.log("Script: session bye");
        session.bye();
    } else {
        try {
            if (statusCallFlow == 1) {
                console.log("Script: inbound session reject");
                session.reject();
            } else {
                console.log("Script: outbound session cancle");
                session.cancel();
            }
        } catch (ex) {
            session.terminate();
            console.log("Script: session terminate");
        }
    }

    statusCallFlow = 0;
}

function lostConnect(){
    console.log("Script: lostConnect");
    if (session != null && session.startTime && !session.endTime) {
        cordova.exec(null, null, "PhoneFPTCallPlugin", "onLostConnect");
        onDestroy();
    }
}

function updateOnlineStatus(event) {
    var condition = navigator.onLine ? "online" : "offline";

    console.log("Script: beforeEnd; Event: " + event.type + "; Status: " + condition);
}

window.addEventListener('online', updateOnlineStatus);
window.addEventListener('offline', updateOnlineStatus);
window.addEventListener('onunload', onDestroy);

function renegotiate() {
    if (localStorage.getItem("sesId") != null && localStorage.getItem("config") != null) {
        var sessionId = localStorage.getItem("sesId");
        var configRenegotiate = localStorage.getItem("config");
        localStorage.removeItem("sesId");
        localStorage.removeItem("config");
        cordova.exec(function (winparam) {
            console.log("callback renegotiate");
            session.accept({
                    media: {
                        render: {
                            remote: remote,
                            local: local
                        }
                    }
                });
        }, null, 'PhoneRTCPlugin', 'renegotiate', [{
            sessionKey: sessionId,
            config: configRenegotiate
        }]);
    }
}