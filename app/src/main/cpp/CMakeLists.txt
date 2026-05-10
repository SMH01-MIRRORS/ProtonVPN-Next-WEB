cmake_minimum_required(VERSION 3.22.1)

project(next_native)

file(GLOB BYE_DPI_SRC byedpi/*.c)

add_library(byedpi SHARED ${BYE_DPI_SRC} native-lib.c)
target_include_directories(byedpi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} byedpi)
target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(byedpi PRIVATE ANDROID_APP)
target_link_libraries(byedpi PRIVATE android log)

add_library(next SHARED next-vpn.cpp next-jni.cpp)
target_include_directories(next PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(next PRIVATE -O2 -Wall -Wextra)
target_link_libraries(next PRIVATE android log)
