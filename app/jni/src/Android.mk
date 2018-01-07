LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := facesmash

SDL_PATH := ../SDL
SDL_IMAGE_PATH := ../SDL_image
SDL_TTF_PATH := ../SDL_ttf
SDL_MIXER_PATH := ../SDL_mixer
VISAGE_DIR := ../visageSDK

ENTT_DIR := entt
ENTT_SRC_DIR := $(ENTT_DIR)/src
FACESMASH_DIR := facesmash-sources
FACESMASH_SRC_DIR := $(FACESMASH_DIR)/src

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(SDL_PATH)/include \
	$(LOCAL_PATH)/$(SDL_IMAGE_PATH) \
	$(LOCAL_PATH)/$(SDL_TTF_PATH) \
	$(LOCAL_PATH)/$(SDL_MIXER_PATH) \
	$(LOCAL_PATH)/$(VISAGE_DIR)/include \
	$(LOCAL_PATH)/$(ENTT_SRC_DIR) \
	$(LOCAL_PATH)/$(FACESMASH_SRC_DIR)

# Add your application source files here...
LOCAL_SRC_FILES := $(FACESMASH_SRC_DIR)/game/game_env.cpp \
	$(FACESMASH_SRC_DIR)/game/game_loop.cpp \
	$(FACESMASH_SRC_DIR)/game/game_renderer.cpp \
	$(FACESMASH_SRC_DIR)/emotion/emo_detector.cpp \
	$(FACESMASH_SRC_DIR)/input/user_input_handler.cpp \
	$(FACESMASH_SRC_DIR)/resource/font_resource.cpp \
	$(FACESMASH_SRC_DIR)/resource/texture_resource.cpp \
	$(FACESMASH_SRC_DIR)/service/audio_null.cpp \
	$(FACESMASH_SRC_DIR)/service/audio_sdl.cpp \
	$(FACESMASH_SRC_DIR)/service/camera_android.cpp \
	$(FACESMASH_SRC_DIR)/service/camera_null.cpp \
	$(FACESMASH_SRC_DIR)/service/face_bus_service.cpp \
	$(FACESMASH_SRC_DIR)/system/animation_system.cpp \
	$(FACESMASH_SRC_DIR)/system/bonus_system.cpp \
	$(FACESMASH_SRC_DIR)/system/camera_system.cpp \
	$(FACESMASH_SRC_DIR)/system/destroy_later_system.cpp \
	$(FACESMASH_SRC_DIR)/system/face_modifier_system.cpp \
	$(FACESMASH_SRC_DIR)/system/face_smash_system.cpp \
	$(FACESMASH_SRC_DIR)/system/frame_system.cpp \
	$(FACESMASH_SRC_DIR)/system/hud_system.cpp \
	$(FACESMASH_SRC_DIR)/system/movement_system.cpp \
	$(FACESMASH_SRC_DIR)/system/rendering_system.cpp \
	$(FACESMASH_SRC_DIR)/system/scene_system.cpp \
	$(FACESMASH_SRC_DIR)/system/score_system.cpp \
	$(FACESMASH_SRC_DIR)/system/smash_button_system.cpp \
	$(FACESMASH_SRC_DIR)/system/the_game_system.cpp \
	$(FACESMASH_SRC_DIR)/system/timer_system.cpp \
	$(FACESMASH_SRC_DIR)/system/ui_button_system.cpp \
	$(FACESMASH_SRC_DIR)/time/clock.cpp \
	$(FACESMASH_SRC_DIR)/main.cpp \
	facesmash_binding.cpp

LOCAL_SHARED_LIBRARIES := SDL2 \
	SDL2_image \
    SDL2_ttf \
    SDL2_mixer \
    VisageVision \
    VisageAnalyser

LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -llog

# VISAGE_STATIC macro needed to silence declspec stuff
# _STLPORT_MAJOR macro needed to tell opencv and visage to not use GCC custom stl headers
LOCAL_CPPFLAGS += -std=c++1z -DVISAGE_STATIC -D_STLPORT_MAJOR -DDEBUG

include $(BUILD_SHARED_LIBRARY)