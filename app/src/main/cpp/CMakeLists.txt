cmake_minimum_required(VERSION 3.22.1)
project(shizuku_shell_local_native LANGUAGES CXX)

add_library(shizuku_shell_local SHARED native_marker.cpp)
find_library(log-lib log)
target_link_libraries(shizuku_shell_local ${log-lib})
target_compile_features(shizuku_shell_local PRIVATE cxx_std_17)
