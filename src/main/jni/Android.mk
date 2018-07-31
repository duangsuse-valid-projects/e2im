LOCAL_PATH := $(call my-dir)
include $(CLEAN_VARS)

LOCAL_CFLAGS := -Wall -Werror -std=c99 -lto -O3 -fno-stack-protector $(EXTRA_FLAGS)
LOCAL_MODULE := e2immutable
LOCAL_MODULE_FILENAME := libe2im.so
LOCAL_SRC_FILES := e2immutable.c

LOCAL_BUILD_SCRIPT := BUILD_EXECUTABLE
LOCAL_MAKEFILE := $(local-makefile)

my := TARGET_

$(call handle-module-built)

LOCAL_MODULE_CLASS := EXECUTABLE
include $(BUILD_SYSTEM)/build-module.mk
