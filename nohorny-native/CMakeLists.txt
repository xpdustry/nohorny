cmake_minimum_required(VERSION 3.25)

# Must be set before project(), oldest macOS version we support
set(CMAKE_OSX_DEPLOYMENT_TARGET 11.0 CACHE STRING "")

project(nohorny LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
# Same static runtime as OpenCV with BUILD_WITH_STATIC_CRT
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
# OpenCV enables ASM when it finds an assembler, on Windows that can be a MinGW GCC from the PATH
#   which sets MINGW and makes OpenCV link pthread. MSVC builds have no use for it since MLAS is disabled.
if(MSVC)
  set(CMAKE_ASM_COMPILER NOTFOUND)
endif()

set(NOHORNY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/output" CACHE PATH "Where to put the nohorny library")

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(FetchOpenCV)

# Only build what the dnn module needs, statically, without any optional dependency
fetch_opencv(
  VERSION 5.0.0
  SHA256 b0528f5a1d379d59d4701cb28c36e22214cc51cf64594e5b56f2d3e6c0233095
  MODULES core imgproc dnn
  CONFIG
    BUILD_SHARED_LIBS=OFF
    BUILD_TESTS=OFF
    BUILD_PERF_TESTS=OFF
    BUILD_EXAMPLES=OFF
    BUILD_DOCS=OFF
    BUILD_JAVA=OFF
    BUILD_opencv_apps=OFF
    BUILD_opencv_python3=OFF
    BUILD_PROTOBUF=ON
    BUILD_ZLIB=ON
    BUILD_WITH_STATIC_CRT=ON
    WITH_PROTOBUF=ON
    WITH_FLATBUFFERS=OFF
    WITH_OPENCL=OFF
    WITH_IPP=OFF
    WITH_ITT=OFF
    WITH_EIGEN=OFF
    WITH_LAPACK=OFF
    WITH_OPENMP=OFF
    WITH_TBB=OFF
    WITH_KLEIDICV=OFF
    WITH_VA=OFF
    WITH_VA_INTEL=OFF
    WITH_CUDA=OFF
    WITH_VULKAN=OFF
    WITH_OPENVINO=OFF
    WITH_ONNXRUNTIME=OFF
    WITH_ZLIB_NG=OFF
    OPENCV_DNN_OPENCL=OFF
    OPENCV_GENERATE_PKGCONFIG=OFF
    CV_TRACE=OFF
    OPENCV_PYTHON_SKIP_DETECTION=ON
)

add_library(nohorny SHARED src/nohorny.cpp)
target_include_directories(nohorny PRIVATE src)
target_include_directories(nohorny SYSTEM PRIVATE ${OpenCV_INCLUDE_DIRS})
target_link_libraries(nohorny PRIVATE ${OpenCV_LIBRARIES})
# The generator expression stops multi-config generators from appending a per-config subdirectory
set_target_properties(nohorny PROPERTIES
  LIBRARY_OUTPUT_DIRECTORY "$<1:${NOHORNY_OUTPUT_DIRECTORY}>"
  RUNTIME_OUTPUT_DIRECTORY "$<1:${NOHORNY_OUTPUT_DIRECTORY}>"
)

if(APPLE)
  target_link_options(nohorny PRIVATE LINKER:-dead_strip $<$<CONFIG:Release>:LINKER:-x>)
elseif(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
  target_link_options(nohorny PRIVATE
    LINKER:--exclude-libs,ALL
    LINKER:--as-needed
    LINKER:--gc-sections
    $<$<CONFIG:Release>:LINKER:--strip-all>
  )
  # Make the library self-contained when possible, the JVM host is unlikely to share our toolchain
  include(CheckLinkerFlag)
  check_linker_flag(CXX -static-libstdc++ NOHORNY_HAS_STATIC_LIBSTDCXX)
  if(NOHORNY_HAS_STATIC_LIBSTDCXX)
    target_link_options(nohorny PRIVATE -static-libstdc++ -static-libgcc)
  else()
    message(WARNING "Static libstdc++ not found, the nohorny library will depend on the system one")
  endif()
endif()
