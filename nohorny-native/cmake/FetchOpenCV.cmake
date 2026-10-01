# Stripped down version of https://github.com/allelomorph/cmake_utils/blob/main/FetchOpenCV.cmake
#
# OpenCV still does not support being consumed through FetchContent, see:
#   - https://github.com/opencv/opencv/issues/20548
# Its module targets do not carry their include directories, so we rebuild the
# OpenCV_INCLUDE_DIRS and OpenCV_LIBRARIES variables find_package() would define.

include_guard(GLOBAL)
include(FetchContent)

# fetch_opencv(
#   VERSION <tag>
#   SHA256 <hash of the source archive>
#   MODULES <module>...
#   [CONFIG <var>=<value>...]
# )
function(fetch_opencv)
  cmake_parse_arguments(PARSE_ARGV 0 OCV "" "VERSION;SHA256" "MODULES;CONFIG")

  # Caching the configuration before FetchContent_MakeAvailable takes the place of
  #   passing -D flags, since FetchContent_Declare does not accept CMAKE_ARGS
  list(JOIN OCV_MODULES "," build_list)
  set(BUILD_LIST "${build_list}" CACHE STRING "" FORCE)
  foreach(config ${OCV_CONFIG})
    string(REGEX MATCH "^([^=]+)=(.*)$" _ "${config}")
    set(${CMAKE_MATCH_1} "${CMAKE_MATCH_2}" CACHE STRING "" FORCE)
  endforeach()

  # The vendored MLAS of OpenCV 5 assumes OpenCV is the top level project
  FetchContent_Declare(OpenCV
    URL "https://github.com/opencv/opencv/archive/refs/tags/${OCV_VERSION}.tar.gz"
    URL_HASH "SHA256=${OCV_SHA256}"
    DOWNLOAD_EXTRACT_TIMESTAMP ON
    PATCH_COMMAND "${CMAKE_COMMAND}"
      -D "FILE=3rdparty/mlas/CMakeLists.txt"
      -P "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/PatchSourceDir.cmake"
    EXCLUDE_FROM_ALL
    SYSTEM
  )
  FetchContent_MakeAvailable(OpenCV)

  # OPENCV_MODULES_PUBLIC contains the requested modules plus their dependencies
  set(include_dirs "${opencv_SOURCE_DIR}/include" "${OPENCV_CONFIG_FILE_INCLUDE_DIR}")
  set(libraries)
  foreach(module ${OPENCV_MODULES_PUBLIC})
    list(APPEND include_dirs "${OPENCV_MODULE_${module}_LOCATION}/include")
    list(APPEND libraries ${module})
  endforeach()

  set(OpenCV_INCLUDE_DIRS ${include_dirs} PARENT_SCOPE)
  set(OpenCV_LIBRARIES ${libraries} PARENT_SCOPE)
endfunction()
