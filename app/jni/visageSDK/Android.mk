LOCAL_PATH := $(call my-dir)

###########################
#
# VisageVision shared library
#
###########################

include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
VISAGE_LIBS := $(LOCAL_PATH)/lib/armeabi-v7a
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
VISAGE_LIBS := $(LOCAL_PATH)/lib/arm64-v8a
endif
ifeq ($(TARGET_ARCH_ABI),x86)
VISAGE_LIBS := $(LOCAL_PATH)/lib/x86
endif
ifeq ($(TARGET_ARCH_ABI),x86_64)
VISAGE_LIBS := $(LOCAL_PATH)/lib/x86_64
endif

LOCAL_MODULE := VisageVision

VISAGE_HEADERS := $(LOCAL_PATH)/include
LOCAL_C_INCLUDES := $(VISAGE_HEADERS)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)

LOCAL_SRC_FILES := $(VISAGE_LIBS)/libVisageVision.so
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES
LOCAL_LDLIBS := -ldl -lGLESv1_CM -lGLESv2 -llog -landroid

include $(PREBUILT_SHARED_LIBRARY)
     

###########################
#
# VisageAnalyser shared library
#
###########################

include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
VISAGE_LIBS := $(LOCAL_PATH)/lib/armeabi-v7a
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
VISAGE_LIBS := $(LOCAL_PATH)/lib/arm64-v8a
endif
ifeq ($(TARGET_ARCH_ABI),x86)
VISAGE_LIBS := $(LOCAL_PATH)/lib/x86
endif
ifeq ($(TARGET_ARCH_ABI),x86_64)
VISAGE_LIBS := $(LOCAL_PATH)/lib/x86_64
endif

LOCAL_MODULE := VisageAnalyser

VISAGE_HEADERS := $(LOCAL_PATH)/include
LOCAL_C_INCLUDES := $(VISAGE_HEADERS)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)

LOCAL_SRC_FILES := $(VISAGE_LIBS)/libVisageAnalyser.so
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES
LOCAL_LDLIBS := -ldl -lGLESv1_CM -lGLESv2 -llog -landroid

include $(PREBUILT_SHARED_LIBRARY)

# OPENCV_LIB_TYPE := STATIC
# 
# OPENCV_MK_PATH := $(LOCAL_PATH)/dependencies/OpenCV-2.4.11-Android/jni/OpenCV.mk
# include $(OPENCV_MK_PATH)