# Uncomment this if you're using STL in your project
# See CPLUSPLUS-SUPPORT.html in the NDK documentation for more information
APP_STL := c++_shared

APP_ABI := armeabi-v7a

# Min SDK level
APP_PLATFORM=android-21

# Conditional flags based on the target build type
# Same as: ifeq ($(strip $(NDK_DEBUG)),1)
ifeq ($(APP_DEBUG),true)
    APP_CPPFLAGS += -DDEBUG
else
    APP_CPPFLAGS += -DRELEASE
endif