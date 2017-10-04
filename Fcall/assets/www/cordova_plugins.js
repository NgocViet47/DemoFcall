cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "id": "cordova-plugin-device.device",
        "file": "plugins/cordova-plugin-device/www/device.js",
        "pluginId": "cordova-plugin-device",
        "clobbers": [
            "device"
        ]
    },
    {
        "file": "plugins/com.dooble.phonertc/www/phonertc.js",
        "id": "com.onsip.cordova.PhoneRTC",
        "pluginId": "com.onsip.cordova.PhoneRTC",
        "clobbers": [
            "com.onsip.cordova"
        ]
    },
    {
        "file": "plugins/com.dooble.phonertc/www/index.js",
        "id": "com.onsip.cordova.SipjsMediaHandler",
        "pluginId": "com.onsip.cordova",
        "clobbers": [
            "cordova.plugins.phonertc.mediahandler"
        ]
    },
//    {
//        "file": "plugins/com.onsip.cordova/www/sip.js",
//        "id": "com.onsip.cordova.Sipjs",
//        "pluginId": "com.onsip.cordova",
//        "clobbers": [
//            "cordova.plugins.sipjs"
//        ]
//    }
];



module.exports.metadata = 
// TOP OF METADATA
{
    "com.onsip.cordova": "1.0.0",
    "cordova-plugin-console": "1.0.7",
    "cordova-plugin-device": "1.1.6",
    "cordova-plugin-whitelist": "1.3.2"
};
// BOTTOM OF METADATA
});