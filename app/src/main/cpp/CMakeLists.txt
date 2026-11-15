cmake_minimum_required(VERSION 3.22.1)

project(viu)

add_library(
    viu
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    viu
    ${log-lib}
)
