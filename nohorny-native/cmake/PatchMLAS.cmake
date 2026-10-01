# Patches the vendored MLAS of OpenCV 5, run as a FetchContent PATCH_COMMAND
set(file "3rdparty/mlas/CMakeLists.txt")
file(READ "${file}" content)
# It assumes OpenCV is the top level project
string(REPLACE "\${CMAKE_SOURCE_DIR}" "\${OpenCV_SOURCE_DIR}" content "${content}")
# Its x86_64 kernels are GNU assembly that MSVC can't build, backport of https://github.com/opencv/opencv/pull/29343
string(REPLACE
  "set(OPENCV_DNN_MLAS_SKIP_REASON \"\" CACHE INTERNAL \"\" FORCE)"
  "set(OPENCV_DNN_MLAS_SKIP_REASON \"\" CACHE INTERNAL \"\" FORCE)\nif(WIN32)\n  return()\nendif()"
  content "${content}")
file(WRITE "${file}" "${content}")
