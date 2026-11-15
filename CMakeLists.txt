
cmake_minimum_required(VERSION 3.22.1)

project("viu_guard")

add_library(
    viu_guard
    SHARED
    native-lib.cpp
)
