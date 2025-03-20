#import "RNUSBPromisePrinter.h"
#import "PrinterSDK.h"

@implementation RNUSBPromisePrinter

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(init:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    // TODO
    resolve(@[@"Init successful"]);
}

RCT_EXPORT_METHOD(getDeviceList:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    // TODO
    NSMutableArray *printerArray = [NSMutableArray new];
    resolve(@[printerArray]);
}

RCT_EXPORT_METHOD(connectPrinter:(NSInteger)vendorId
                  withProductID:(NSInteger)productId
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    // TODO
    reject(@"unsupported_function", @"This function is not supported", nil);
}

RCT_EXPORT_METHOD(printRawData:(NSString *)text
                  printerOptions:(NSDictionary *)options
                  resolver:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    // TODO
    reject(@"unsupported_function", @"This function is not supported", nil);
}

RCT_EXPORT_METHOD(closeConn:(nonnull RCTPromiseResolveBlock)resolve
                  rejecter:(nonnull RCTPromiseRejectBlock)reject) {
    // TODO
    reject(@"not_implemented", @"not Implemented", nil);
}

@end

