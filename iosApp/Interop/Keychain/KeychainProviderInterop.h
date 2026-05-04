#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface KeychainProviderInterop : NSObject

- (instancetype)init NS_DESIGNATED_INITIALIZER;

- (NSString * _Nullable)readStringForKey:(NSString *)key;
- (BOOL)writeString:(NSString *)value forKey:(NSString *)key;
- (BOOL)deleteValueForKey:(NSString *)key;

@end

NS_ASSUME_NONNULL_END
