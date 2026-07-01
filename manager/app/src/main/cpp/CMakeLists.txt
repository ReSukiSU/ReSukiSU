# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

add_library(kernelsu
        SHARED
        jni.c
        ksu.c
        legacy.c
)

target_include_directories(kernelsu PRIVATE .)

find_library(log-lib log)

target_link_libraries(
        kernelsu
        ${log-lib}
)

# 禁用 DT_FLAGS_1 标志以兼容 Android 6
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_link_options(kernelsu PRIVATE "-Wl,-z,lazy")
endif()

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(adbroot SHARED adbroot.cc)
target_link_libraries(adbroot ${log-lib})
