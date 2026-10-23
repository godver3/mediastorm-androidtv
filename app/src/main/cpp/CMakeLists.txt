# The Dolby Vision bridge (player/dv7/DoviBridge.kt): libdovi's RPU conversion for Profile 7.
# Linked against the prebuilt static libdovi in app/libdovi/<abi>/ (scripts/sync-vendor.sh)
# when it's there; otherwise a stub that reports not ready, so Profile 7 strips to HDR10.
cmake_minimum_required(VERSION 3.22.1)
project(strmr_dovi LANGUAGES CXX)

set(STRMR_LIBDOVI_ROOT "" CACHE PATH "Folder holding android-<abi>/lib/libdovi.a")

add_library(dovi_bridge SHARED dovi_bridge.cpp)
target_compile_features(dovi_bridge PRIVATE cxx_std_17)
target_compile_options(dovi_bridge PRIVATE -Wall -Wextra -Wno-unused-parameter)
target_link_options(dovi_bridge PRIVATE "-Wl,-z,max-page-size=16384")

set(_abi_dirs_arm64-v8a android-arm64)
set(_abi_dirs_armeabi-v7a android-armeabi-v7a)
set(_abi_dirs_x86 android-x86)
set(_abi_dirs_x86_64 android-x86_64)
set(_libdovi "${STRMR_LIBDOVI_ROOT}/${_abi_dirs_${ANDROID_ABI}}/lib/libdovi.a")

if (STRMR_LIBDOVI_ROOT AND EXISTS "${_libdovi}")
    message(STATUS "dovi_bridge: linking ${_libdovi}")
    add_library(libdovi_static STATIC IMPORTED)
    set_target_properties(libdovi_static PROPERTIES IMPORTED_LOCATION "${_libdovi}")
    target_compile_definitions(dovi_bridge PRIVATE DOVI_REAL_LINKED=1)
    target_link_libraries(dovi_bridge PRIVATE libdovi_static log)
else()
    message(STATUS "dovi_bridge: no libdovi for ${ANDROID_ABI}; building the stub")
    target_compile_definitions(dovi_bridge PRIVATE DOVI_REAL_LINKED=0)
    target_link_libraries(dovi_bridge PRIVATE log)
endif()
