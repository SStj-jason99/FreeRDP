include(ExternalProject)
include(DepVersions)

# Map ANDROID_ABI to OpenSSL architecture names
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(OSSL_ARCH "android-arm64")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(OSSL_ARCH "android-arm")
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(OSSL_ARCH "android-x86_64")
elseif(ANDROID_ABI STREQUAL "x86")
  set(OSSL_ARCH "android-x86")
elseif(ANDROID_ABI STREQUAL "riscv64")
  set(OSSL_ARCH "android-riscv64")
else()
  message(FATAL_ERROR "ExternalOpenSSL: unsupported ABI '${ANDROID_ABI}'")
endif()

set(NDK_TOOLCHAIN_BIN "${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_PLATFORM}/bin")

# On Windows, $ENV{PATH} is ';'-separated; embedding it unescaped into a
# CONFIGURE_COMMAND/BUILD_COMMAND argument list makes CMake's own list
# splitting shred it into stray extra arguments. Escape ';' as $<SEMICOLON>
# so the whole PATH survives as a single argument.
if(CMAKE_HOST_WIN32)
  # WIN32 reflects the *target* (Android) in this cross-compile, not the
  # build host, so check CMAKE_HOST_WIN32 instead.
  string(REPLACE ";" "$<SEMICOLON>" OSSL_ENV_PATH_TAIL "$ENV{PATH}")
  set(OSSL_ENV_PATH "${NDK_TOOLCHAIN_BIN}$<SEMICOLON>${OSSL_ENV_PATH_TAIL}")
else()
  set(OSSL_ENV_PATH "${NDK_TOOLCHAIN_BIN}:$ENV{PATH}")
endif()

ExternalProject_Add(
  openssl
  DOWNLOAD_EXTRACT_TIMESTAMP OFF
  SOURCE_DIR ${CMAKE_SOURCE_DIR}/external/openssl
  BINARY_DIR ${CMAKE_BINARY_DIR}/external/openssl
  URL https://github.com/openssl/openssl/releases/download/${OPENSSL_VERSION}/${OPENSSL_VERSION}.tar.gz
  URL_HASH ${OPENSSL_HASH}
  LIST_SEPARATOR |
  PATCH_COMMAND
    ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/patches/15-android.conf
    <SOURCE_DIR>/Configurations/15-android.conf
  CONFIGURE_COMMAND
    ${CMAKE_COMMAND} -E env PATH=${OSSL_ENV_PATH} ANDROID_NDK=${NDK_ROOT} ANDROID_NDK_ROOT=${NDK_ROOT}
    ANDROID_NDK_HOME=${NDK_ROOT} CC=clang perl <SOURCE_DIR>/Configure ${OSSL_ARCH} shared no-tests no-apps no-docs
    -U__ANDROID_API__ -D__ANDROID_API__=${NDK_API_LEVEL} --prefix=${DEPS_INSTALL_DIR} --libdir=${CMAKE_INSTALL_LIBDIR}
  BUILD_COMMAND ${CMAKE_COMMAND} -E env PATH=${OSSL_ENV_PATH} ANDROID_NDK=${NDK_ROOT}
                ANDROID_NDK_ROOT=${NDK_ROOT} ANDROID_NDK_HOME=${NDK_ROOT} make -j SHLIB_EXT=.so build_libs
  INSTALL_COMMAND
    ${CMAKE_COMMAND} -E env PATH=${OSSL_ENV_PATH} ANDROID_NDK=${NDK_ROOT} ANDROID_NDK_ROOT=${NDK_ROOT}
    ANDROID_NDK_HOME=${NDK_ROOT} make -j SHLIB_EXT=.so install_sw && ${CMAKE_COMMAND} -E copy_directory
    ${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}/ossl-modules ${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}
)
