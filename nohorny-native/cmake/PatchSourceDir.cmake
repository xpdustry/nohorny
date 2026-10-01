# Replaces CMAKE_SOURCE_DIR by OpenCV_SOURCE_DIR in FILE, run as a FetchContent PATCH_COMMAND
file(READ "${FILE}" content)
string(REPLACE "\${CMAKE_SOURCE_DIR}" "\${OpenCV_SOURCE_DIR}" content "${content}")
file(WRITE "${FILE}" "${content}")
