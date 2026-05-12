cmake_minimum_required(VERSION 3.22.1)

project(next_native)

file(GLOB BYE_DPI_SRC byedpi/*.c)

add_library(byedpi SHARED ${BYE_DPI_SRC} native-lib.c)
target_include_directories(byedpi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} byedpi)
target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(byedpi PRIVATE ANDROID_APP)
target_link_libraries(byedpi PRIVATE android log)

set(IMGUI_DIR imgui)
set(IMGUI_SOURCES
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
    ${IMGUI_DIR}/backends/imgui_impl_android.cpp
)

set(NEXT_SOURCES
    connection.cpp
    antitamper.cpp
    api.cpp
    auth.cpp
    utils.cpp
    next-jni.cpp
)

find_package(sentry-native-ndk REQUIRED CONFIG)

add_library(next SHARED ${NEXT_SOURCES} ${IMGUI_SOURCES})
target_include_directories(next PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/${IMGUI_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/${IMGUI_DIR}/backends
)
target_compile_options(next PRIVATE -O2 -Wall -Wextra)
target_link_libraries(next PRIVATE android log GLESv3 EGL sentry-native-ndk::sentry)
