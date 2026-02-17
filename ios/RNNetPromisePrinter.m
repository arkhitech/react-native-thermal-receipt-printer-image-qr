//
//  RNNetPromisePrinter.m
//  RNThermalReceiptPrinter
//
//  Created by MTT on 06/11/19.
//  Copyright © 2019 Facebook. All rights reserved.
//


#import "RNNetPromisePrinter.h"
#import "PrivateIP.h"
#import "PrinterSDK.h"
#include <ifaddrs.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <string.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NSString *const EVENT_PROMISE_SCANNER_RESOLVED = @"scannerResolved";
NSString *const EVENT_PROMISE_SCANNER_PARTIAL = @"scannerFoundDevice";
NSString *const EVENT_PROMISE_SCANNER_RUNNING = @"scannerRunning";

@implementation RNNetPromisePrinter

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
    return @[EVENT_PROMISE_SCANNER_RESOLVED, EVENT_PROMISE_SCANNER_PARTIAL, EVENT_PROMISE_SCANNER_RUNNING];
}

RCT_EXPORT_METHOD(init:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    connected_ip = nil;
    is_scanning = NO;
    _printerArray = [NSMutableArray new];
    resolve(@[@"Init successful"]);
}

RCT_EXPORT_METHOD(getDeviceList:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handlePrinterConnectedNotification:) name:PrinterConnectedNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleBLEPrinterConnectedNotification:) name:@"BLEPrinterConnected" object:nil];
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        [self scan:resolve rejecter: reject];
    });
}

RCT_EXPORT_METHOD(getAllNetworkDevices:(nonnull NSNumber *)port
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    if (is_scanning) {
        reject(@"already_scanning", @"Already scanning", nil);
        return;
    }

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        [self scanAllNetworkDevices:[port intValue] resolver:resolve rejecter:reject];
    });
}

- (void) scan:(RCTPromiseResolveBlock)resolve
              rejecter:(RCTPromiseRejectBlock)reject {
    @try {
        PrivateIP *privateIP = [[PrivateIP alloc]init];
        NSString *localIP = [privateIP getIPAddress];
        is_scanning = YES;
        [self sendEventWithName:EVENT_PROMISE_SCANNER_RUNNING body:@YES];
        _printerArray = [NSMutableArray new];

        NSString *prefix = [localIP substringToIndex:([localIP rangeOfString:@"." options:NSBackwardsSearch].location)];
        NSInteger suffix = [[localIP substringFromIndex:([localIP rangeOfString:@"." options:NSBackwardsSearch].location)] intValue];

        for (NSInteger i = 1; i < 255; i++) {
            if (i == suffix) continue;
            NSString *testIP = [NSString stringWithFormat:@"%@.%ld", prefix, (long)i];
            current_scan_ip = testIP;
            [[PrinterSDK defaultPrinterSDK] connectIP:testIP];
            [NSThread sleepForTimeInterval:0.5];
        }

        NSOrderedSet *orderedSet = [NSOrderedSet orderedSetWithArray:_printerArray];
        NSArray *arrayWithoutDuplicates = [orderedSet array];
        _printerArray = (NSMutableArray *)arrayWithoutDuplicates;

        [self sendEventWithName:EVENT_PROMISE_SCANNER_RESOLVED body:_printerArray];

        resolve(@[_printerArray]);
    } @catch (NSException *exception) {
        NSLog(@"No connection");
        reject(exception.name, exception.reason, nil);
    }
    [[PrinterSDK defaultPrinterSDK] disconnect];
    is_scanning = NO;
    [self sendEventWithName:EVENT_PROMISE_SCANNER_RUNNING body:@NO];
}

- (void)scanAllNetworkDevices:(int)port
                     resolver:(RCTPromiseResolveBlock)resolve
                     rejecter:(RCTPromiseRejectBlock)reject {
    @try {
        PrivateIP *privateIP = [[PrivateIP alloc] init];
        NSString *localIP = [privateIP getIPAddress];
        if (localIP == nil || [localIP length] == 0) {
            reject(@"no_connection", @"No connection", nil);
            return;
        }

        NSRange lastDot = [localIP rangeOfString:@"." options:NSBackwardsSearch];
        if (lastDot.location == NSNotFound) {
            reject(@"invalid_ip", @"Invalid local IP address", nil);
            return;
        }

        is_scanning = YES;
        [self sendEventWithName:EVENT_PROMISE_SCANNER_RUNNING body:@YES];

        NSString *prefix = [localIP substringToIndex:lastDot.location + 1];
        NSInteger suffix = [[localIP substringFromIndex:lastDot.location + 1] integerValue];

        NSMutableArray *arrayEvent = [NSMutableArray new];
        NSMutableArray *arrayPromise = [NSMutableArray new];

        for (NSInteger i = 0; i <= 255; i++) {
            if (i == suffix) {
                continue;
            }

            NSString *host = [NSString stringWithFormat:@"%@%ld", prefix, (long)i];
            BOOL isOpen = [self isPortOpen:host port:port timeoutMs:100];
            if (!isOpen) {
                continue;
            }

            NSDictionary *payload = @{@"host": host, @"port": @(port)};
            [arrayEvent addObject:payload];
            [arrayPromise addObject:payload];
            [self sendEventWithName:EVENT_PROMISE_SCANNER_PARTIAL body:@[payload]];
        }

        [self sendEventWithName:EVENT_PROMISE_SCANNER_RESOLVED body:arrayEvent];
        resolve(arrayPromise);
    } @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    } @finally {
        is_scanning = NO;
        [self sendEventWithName:EVENT_PROMISE_SCANNER_RUNNING body:@NO];
    }
}

- (BOOL)isPortOpen:(NSString *)host port:(int)port timeoutMs:(int)timeoutMs {
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        return NO;
    }

    int flags = fcntl(sockfd, F_GETFL, 0);
    if (flags < 0 || fcntl(sockfd, F_SETFL, flags | O_NONBLOCK) < 0) {
        close(sockfd);
        return NO;
    }

    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    if (inet_pton(AF_INET, [host UTF8String], &serverAddr.sin_addr) <= 0) {
        close(sockfd);
        return NO;
    }

    int connectResult = connect(sockfd, (struct sockaddr *)&serverAddr, sizeof(serverAddr));
    if (connectResult == 0) {
        close(sockfd);
        return YES;
    }

    if (errno != EINPROGRESS) {
        close(sockfd);
        return NO;
    }

    fd_set writefds;
    FD_ZERO(&writefds);
    FD_SET(sockfd, &writefds);

    struct timeval timeout;
    timeout.tv_sec = timeoutMs / 1000;
    timeout.tv_usec = (timeoutMs % 1000) * 1000;

    int selectResult = select(sockfd + 1, NULL, &writefds, NULL, &timeout);
    if (selectResult <= 0) {
        close(sockfd);
        return NO;
    }

    int socketError = 0;
    socklen_t len = sizeof(socketError);
    if (getsockopt(sockfd, SOL_SOCKET, SO_ERROR, &socketError, &len) < 0) {
        close(sockfd);
        return NO;
    }

    close(sockfd);
    return socketError == 0;
}

- (void)handlePrinterConnectedNotification:(NSNotification*)notification
{
    if (is_scanning) {
        [_printerArray addObject:@{@"host": current_scan_ip, @"port": @9100}];
    }
}

- (void)handleBLEPrinterConnectedNotification:(NSNotification*)notification
{
    connected_ip = nil;
}

RCT_EXPORT_METHOD(connectPrinter:(NSString *)host
                  withPort:(nonnull NSNumber *)port
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        BOOL isConnectSuccess = [[PrinterSDK defaultPrinterSDK] connectIP:host];
        !isConnectSuccess ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer %@", host] : nil;

        connected_ip = host;
        [[NSNotificationCenter defaultCenter] postNotificationName:@"NetPrinterConnected" object:nil];
        resolve(@[[NSString stringWithFormat:@"Connecting to printer %@", host]]);

    } @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(printRawData:(NSString *)base64String
                  printerOptions:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSNumber* beepPtr = [options valueForKey:@"beep"];
        NSNumber* cutPtr = [options valueForKey:@"cut"];

        BOOL beep = (BOOL)[beepPtr intValue];
        BOOL cut = (BOOL)[cutPtr intValue];

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        NSData *decodedData = [[NSData alloc] initWithBase64EncodedString:base64String options:0];
        NSString *text = [[NSString alloc] initWithData:decodedData encoding:NSUTF8StringEncoding];
        NSLog(@"%@", text);

        // [[PrinterSDK defaultPrinterSDK] printTestPaper];
        [[PrinterSDK defaultPrinterSDK] printText:text];
        beep ? [[PrinterSDK defaultPrinterSDK] beep] : nil;
        cut ? [[PrinterSDK defaultPrinterSDK] cutPaper] : nil;
        resolve(@"done");
    } @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(printImageData:(NSString *)imgUrl
                  printerOptions:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        NSURL* url = [NSURL URLWithString:imgUrl];
        NSData* imageData = [NSData dataWithContentsOfURL:url];

        NSString* printerWidthType = [options valueForKey:@"printerWidthType"];

        NSInteger printerWidth = 576;

        if(printerWidthType != nil && [printerWidthType isEqualToString:@"58"]) {
            printerWidth = 384;
        }

        if(imageData != nil){
            UIImage* image = [UIImage imageWithData:imageData];
            UIImage* printImage = [self getPrintImage:image printerOptions:options];

            [[PrinterSDK defaultPrinterSDK] setPrintWidth:printerWidth];
            [[PrinterSDK defaultPrinterSDK] printImage:printImage ];
        }
        resolve(@"done");

    } @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(printImageBase64:(NSString *)base64Qr
                  printerOptions:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        if(![base64Qr  isEqual: @""]){
            NSData *imageData = [[NSData alloc] initWithBase64EncodedString:base64Qr options:0];
            NSString* printerWidthType = [options valueForKey:@"printerWidthType"];

            NSInteger printerWidth = 576;

            if(printerWidthType != nil && [printerWidthType isEqualToString:@"58"]) {
                printerWidth = 384;
            }

            if(imageData != nil){
                UIImage* image = [UIImage imageWithData:imageData];
                UIImage* printImage = [self getPrintImage:image printerOptions:options];

                [[PrinterSDK defaultPrinterSDK] setPrintWidth:printerWidth];
                [[PrinterSDK defaultPrinterSDK] printImage:printImage ];
            }
            resolve(@"done");
        }
    } @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

-(UIImage *)getPrintImage:(UIImage *)image
           printerOptions:(NSDictionary *)options {

   NSNumber* nWidth = [options valueForKey:@"imageWidth"];
   NSNumber* nHeight = [options valueForKey:@"imageHeight"];
   NSNumber* nPaddingX = [options valueForKey:@"paddingX"];

   CGFloat newWidth = 150;
   if(nWidth != nil && ![[NSNull null] isEqual:nWidth]) {
       newWidth = [nWidth floatValue];
   }

   CGFloat newHeight = image.size.height;
   if(nHeight != nil && ![[NSNull null] isEqual:nHeight]) {
       newHeight = [nHeight floatValue];
   }

   CGFloat paddingX = 250;
   if(nPaddingX != nil && ![[NSNull null] isEqual:nPaddingX]) {
       paddingX = [nPaddingX floatValue];
   }

   CGFloat _newHeight = newHeight;
   CGSize newSize = CGSizeMake(newWidth, _newHeight);
   UIGraphicsBeginImageContextWithOptions(newSize, false, 0.0);
   CGContextRef context = UIGraphicsGetCurrentContext();
   CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
   CGImageRef immageRef = image.CGImage;
   CGContextDrawImage(context, CGRectMake(0, 0, newWidth, newHeight), immageRef);
   CGImageRef newImageRef = CGBitmapContextCreateImage(context);
   UIImage* newImage = [UIImage imageWithCGImage:newImageRef];

   CGImageRelease(newImageRef);
   UIGraphicsEndImageContext();

   UIImage* paddedImage = [self addImagePadding:newImage paddingX:paddingX paddingY:0];
   return paddedImage;

}

-(UIImage *)addImagePadding:(UIImage * )image
                   paddingX: (CGFloat) paddingX
                   paddingY: (CGFloat) paddingY
{
    CGFloat width = image.size.width + paddingX;
    CGFloat height = image.size.height + paddingY;

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 0.0);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context, [UIColor whiteColor].CGColor);
    CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
    CGContextFillRect(context, CGRectMake(0, 0, width, height));
    CGFloat originX = (width - image.size.width)/2;
    CGFloat originY = (height -  image.size.height)/2;
    CGImageRef immageRef = image.CGImage;
    CGContextDrawImage(context, CGRectMake(originX, originY, image.size.width, image.size.height), immageRef);
    CGImageRef newImageRef = CGBitmapContextCreateImage(context);
    UIImage* paddedImage = [UIImage imageWithCGImage:newImageRef];

    CGImageRelease(newImageRef);
    UIGraphicsEndImageContext();

    return paddedImage;
}

RCT_EXPORT_METHOD(closeConn:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        [[PrinterSDK defaultPrinterSDK] disconnect];
        connected_ip = nil;
        resolve(@"done");
    } @catch (NSException *exception) {
        NSLog(@"%@", exception.reason);
        reject(exception.name, exception.reason, nil);
    }
}

@end
