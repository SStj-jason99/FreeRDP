# Strips debug symbols from every .so directly under STRIP_DIR.
#
# AGP only auto-strips native libraries it builds itself via its own cxx task; the
# dependencies here (openssl, opus, jpeg, libpng, webp, cjson, uriparser, and FreeRDP's
# own libfreerdp3/libwinpr3/libfreerdp-client3) are produced by separate CMake
# ExternalProject_Add() builds and merely copied into jniLibs, so AGP can't see them and
# ships them exactly as built -- full DWARF debug info and all, several MB per library.
# `--strip-unneeded` drops that debug info but keeps the dynamic symbol table shared libs
# need to link/load, matching what AGP's own stripping does for release builds.
#
# Usage: cmake -DSTRIP_TOOL=<path to llvm-strip> -DSTRIP_DIR=<dir> -P StripLibs.cmake

if(NOT STRIP_TOOL OR NOT EXISTS "${STRIP_TOOL}")
  message(WARNING "StripLibs: strip tool '${STRIP_TOOL}' not found, skipping")
  return()
endif()

file(GLOB _libs "${STRIP_DIR}/*.so")
foreach(_lib ${_libs})
  execute_process(COMMAND "${STRIP_TOOL}" --strip-unneeded "${_lib}" RESULT_VARIABLE _rc)
  if(NOT _rc EQUAL 0)
    message(WARNING "StripLibs: failed to strip ${_lib} (exit ${_rc})")
  endif()
endforeach()
