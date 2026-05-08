cmake_minimum_required(VERSION 3.22.1)

project(tdoa)

add_library(tdoa SHARED tdoa.cpp)

find_library(log-lib log)
target_link_libraries(tdoa ${log-lib})
