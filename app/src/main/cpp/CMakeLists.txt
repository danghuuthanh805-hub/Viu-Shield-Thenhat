
cmake_minimum_required(VERSION 3.22.1)

project("viu_guard")

add_library(
    viu_guard
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    viu_guard
    ${log-lib}
)
