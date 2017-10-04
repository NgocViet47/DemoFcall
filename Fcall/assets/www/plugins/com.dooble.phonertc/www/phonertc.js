cordova.define("com.onsip.cordova.PhoneRTC", function(require, exports, module) {
    var exec = require('cordova/exec');
    var videoViewConfig;
    function createUUID() {
      // http://www.ietf.org/rfc/rfc4122.txtre
      var s = [];
      var hexDigits = "0123456789abcdef";
      for (var i = 0; i < 36; i++) {
          s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
      }
      s[14] = "4";  // bits 12-15 of the time_hi_and_version field to 0010
      s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);  // bits 6-7 of the clock_seq_hi_and_reserved to 01
      s[8] = s[13] = s[18] = s[23] = "-";
      var uuid = s.join("");
      return uuid;
    }

    function Session(config) {
      // make sure that the config object is valid
      if (typeof config !== 'object') {
        throw {
          name: 'PhoneRTC Error',
          message: 'The first argument must be an object.'
        };
      }

      if (typeof config.isInitiator === 'undefined' ||
          typeof config.turn === 'undefined' ||
          typeof config.streams === 'undefined') {
        throw {
          name: 'PhoneRTC Error',
          message: 'isInitiator, turn and streams are required parameters.'
        };
      }

      var self = this;
      self.events = {};
      self.config = config;
      self.sessionKey = createUUID();

      localStorage.setItem("sesId", self.sessionKey);
      // make all config properties accessible from this object
      Object.keys(config).forEach(function (prop) {
        Object.defineProperty(self, prop, {
          get: function () { return self.config[prop]; },
          set: function (value) { self.config[prop] = value; }
        });
      });

      function callEvent(eventName) {
        if (!self.events[eventName]) {
          return;
        }

        var args = Array.prototype.slice.call(arguments, 1);
        self.events[eventName].forEach(function (callback) {
          callback.apply(self, args);
        });
      }

      function onSendMessage(data) {
        if (data.type === '__answered') {
          callEvent('answer');
        } else if (data.type === '__disconnected') {
          callEvent('disconnect');
        } else {
          callEvent('sendMessage', data);
        }
      }
    //    localStorage.setItem("sesId",self.sessionKey);
    //    localStorage.setItem("config",JSON.stringify(config));
        exec(onSendMessage, null, 'PhoneRTCPlugin', 'createSessionObject', [self.sessionKey, config]);
    };

    Session.prototype.on = function (eventName, fn) {
      // make sure that the second argument is a function
      if (typeof fn !== 'function') {
        throw {
          name: 'PhoneRTC Error',
          message: 'The second argument must be a function.'
        };
      }

      // create the event if it doesn't exist
      if (!this.events[eventName]) {
        this.events[eventName] = [];
      } else {
        // make sure that this callback doesn't exist already
        for (var i = 0, len = this.events[eventName].length; i < len; i++) {
          if (this.events[eventName][i] === fn) {
            throw {
              name: 'PhoneRTC Error',
              message: 'This callback function was already added.'
            };
          }
        }
      }

      // add the event
      this.events[eventName].push(fn);
    };

    Session.prototype.off = function (eventName, fn) {
      // make sure that the second argument is a function
      if (typeof fn !== 'function') {
        throw {
          name: 'PhoneRTC Error',
          message: 'The second argument must be a function.'
        };
      }

      if (!this.events[eventName]) {
        return;
      }

      var indexesToRemove = [];
      for (var i = 0, len = this.events[eventName].length; i < len; i++) {
        if (this.events[eventName][i] === fn) {
          indexesToRemove.push(i);
        }
      }

      indexesToRemove.forEach(function (index) {
        this.events.splice(index, 1);
      })
    };

    Session.prototype.call = function (success, error) {
      exec(success, error, 'PhoneRTCPlugin', 'call', [{
        sessionKey: this.sessionKey
      }]);
    };

    Session.prototype.receiveMessage = function (data, success) {
      exec(success, null, 'PhoneRTCPlugin', 'receiveMessage', [{
        sessionKey: this.sessionKey,
        message: JSON.stringify(data)
      }]);
    };


    Session.prototype.setVideo = function (config) {

      videoViewConfig = config;

      var container = config.container;

      if (container) {
        config.containerParams = getLayoutParams(container);
        delete config.container;
      }

      config.devicePixelRatio = window.devicePixelRatio || 2;

      exec(null, null, 'PhoneRTCPlugin', 'setVideoView', [config]);

      if (container) {
        config.container = container;
      }


    };


    Session.prototype.renegotiate = function () {
      exec(null, null, 'PhoneRTCPlugin', 'renegotiate', [{
        sessionKey: this.sessionKey,
        config: this.config
      }]);
    };

    Session.prototype.close = function () {
      exec(null, null, 'PhoneRTCPlugin', 'disconnect', [{
        sessionKey: this.sessionKey
      }]);
    };

    //checkPermission
    Session.prototype.checkPermission = function () {
      exec(null, null, 'PhoneRTCPlugin', 'checkPermissions', []);
    };


    exports.Session = Session;

    function getLayoutParams(videoElement) {
      var boundingRect = videoElement.getBoundingClientRect();

      if (cordova.platformId === 'android') {
        return {
          position: [boundingRect.left + window.scrollX, boundingRect.top + window.scrollY],
          size: [boundingRect.width, boundingRect.height]
        };
      }

      return {
        position: [boundingRect.left, boundingRect.top],
        size: [boundingRect.width, boundingRect.height]
      };
    }

    function setVideoView(config) {
      videoViewConfig = config;

      var container = config.container;

      if (container) {
        config.containerParams = getLayoutParams(container);
        delete config.container;
      }

      config.devicePixelRatio = window.devicePixelRatio || 2;

      exec(null, null, 'PhoneRTCPlugin', 'setVideoView', [config]);

      if (container) {
        config.container = container;
      }
    };

    document.addEventListener('touchmove', function () {
      if (videoViewConfig) {
        setVideoView(videoViewConfig);
      }
    });

    exports.setVideoView = setVideoView;
    exports.hideVideoView = function () {
      exec(null, null, 'PhoneRTCPlugin', 'hideVideoView', []);
    };

    exports.showVideoView = function () {
      exec(null, null, 'PhoneRTCPlugin', 'showVideoView', []);
    };

    exports.checkPermissions = function (success, fail) {
      exec(success, fail, 'PhoneRTCPlugin', 'checkPermissions', []);
    };

    var videoElements;

    function getLayoutParams (videoElement) {
      var boundingRect = videoElement.getBoundingClientRect();
      return {
          // get these values by doing a lookup on the dom
          x : boundingRect.left,
          y : boundingRect.top,
          width : videoElement.offsetWidth,
          height : videoElement.offsetHeight
        };
    }

    exports.setDescription = function (options) {
      exec(
        options.callBack,
        null,
        'PhoneRTCPlugin',
        'setDescription',
        [JSON.stringify(options)]);
    };

    exports.getDescription = function (options) {
      var execOptions = options || {};
      if (options.video) {
        videoElements = {
          localVideo: options.video.localVideo,
          remoteVideo: options.video.remoteVideo
        };
        execOptions.video = {
          localVideo: getLayoutParams(videoElements.localVideo),
          remoteVideo: getLayoutParams(videoElements.remoteVideo)
        };
      }

      exec(
        function (data) {
          if (data.type === '__answered' && options.answerCallback) {
            options.answerCallback();
          } else if (data.type === '__disconnected' && options.disconnectCallback) {
            options.disconnectCallback();
          } else {
            options.sendMessageCallback(data);
          }
        },
        null,
        'PhoneRTCPlugin',
        'getDescription',
        [JSON.stringify(execOptions)]);
    };

    exports.setEnabledMedium = function (mediumType, enabled) {
      exec(
        function () {},
        null,
        'PhoneRTCPlugin',
        'setEnabledMedium',
        [mediumType, enabled]);
    }

    exports.receiveMessage = function (data) {
      exec(
        null,
        null,
        'PhoneRTCPlugin',
        'receiveMessage',
        [JSON.stringify(data)]);
    };

    exports.disconnect = function () {
      exec(
        null,
        null,
        'PhoneRTCPlugin',
        'disconnect',
        []);
    };
});
