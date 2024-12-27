#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// specify the method names we will use for the plugin methods
CAP_PLUGIN(BarcodeScannerPlugin, "BarcodeScanner",
    CAP_PLUGIN_METHOD(prepare, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(hideBackground, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showBackground, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(startScan, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(stopScan, CAPPluginReturnPromise);
)
