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
set(OSSL_PERL "perl")
if(CMAKE_HOST_WIN32)
  # WIN32 reflects the *target* (Android) in this cross-compile, not the
  # build host, so check CMAKE_HOST_WIN32 instead.
  #
  # This whole external project -- Configure, then `make build_libs`, then
  # `make install_sw` -- is a genuine Unix build (Makefile, perl, sh, and
  # coreutils all called by name) and needs a real Unix toolchain on PATH,
  # not just a Unix-flavoured Perl for the Configure step: OpenSSL's
  # Configure also refuses to run under a "Windows-native" Perl (e.g.
  # Strawberry Perl) because it emits backslash paths. Prepend a Unix
  # tools directory (MSYS2 or Git for Windows) for all three commands if
  # one is available. This only affects OSSL_ENV_PATH, which is scoped to
  # this one external project's own commands -- it doesn't touch PATH for
  # the rest of the build (e.g. pkg-config.bat resolution elsewhere still
  # sees the normal, unmodified PATH).
  set(OSSL_UNIX_BIN "")
  foreach(_candidate "C:/msys64/usr/bin" "$ENV{ProgramFiles}/Git/usr/bin" "C:/Program Files/Git/usr/bin")
    if(EXISTS "${_candidate}/perl.exe")
      set(OSSL_UNIX_BIN "${_candidate}")
      set(OSSL_PERL "${_candidate}/perl.exe")
      break()
    endif()
  endforeach()

  string(REPLACE ";" "$<SEMICOLON>" OSSL_ENV_PATH_TAIL "$ENV{PATH}")
  if(OSSL_UNIX_BIN)
    set(OSSL_ENV_PATH "${OSSL_UNIX_BIN}$<SEMICOLON>${NDK_TOOLCHAIN_BIN}$<SEMICOLON>${OSSL_ENV_PATH_TAIL}")
  else()
    set(OSSL_ENV_PATH "${NDK_TOOLCHAIN_BIN}$<SEMICOLON>${OSSL_ENV_PATH_TAIL}")
  endif()
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
    ANDROID_NDK_HOME=${NDK_ROOT} CC=clang ${OSSL_PERL} <SOURCE_DIR>/Configure ${OSSL_ARCH} shared no-tests no-apps no-docs
    -U__ANDROID_API__ -D__ANDROID_API__=${NDK_API_LEVEL} --prefix=${DEPS_INSTALL_DIR} --libdir=${CMAKE_INSTALL_LIBDIR}
  BUILD_COMMAND ${CMAKE_COMMAND} -E env PATH=${OSSL_ENV_PATH} ANDROID_NDK=${NDK_ROOT}
                ANDROID_NDK_ROOT=${NDK_ROOT} ANDROID_NDK_HOME=${NDK_ROOT} make -j SHLIB_EXT=.so build_libs
  INSTALL_COMMAND
    ${CMAKE_COMMAND} -E env PATH=${OSSL_ENV_PATH} ANDROID_NDK=${NDK_ROOT} ANDROID_NDK_ROOT=${NDK_ROOT}
    ANDROID_NDK_HOME=${NDK_ROOT} make -j SHLIB_EXT=.so install_sw && ${CMAKE_COMMAND} -E copy_directory
    ${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}/ossl-modules ${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}
)
