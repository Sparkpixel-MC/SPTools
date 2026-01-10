cmake_minimum_required(VERSION 3.10)
project(TransactionServer)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(WIN32)
    add_definitions(-D_WIN32)
endif()

add_executable(server server.cpp)

if(WIN32)
    target_link_libraries(server ws2_32)
endif()

install(TARGETS server DESTINATION bin)