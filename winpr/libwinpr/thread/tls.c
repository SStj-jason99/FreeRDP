/**
 * WinPR: Windows Portable Runtime
 * Process Thread Functions
 *
 * Copyright 2012 Marc-Andre Moreau <marcandre.moreau@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <winpr/config.h>

#include <winpr/handle.h>

#include <winpr/thread.h>

/**
 * TlsAlloc
 * TlsFree
 * TlsGetValue
 * TlsSetValue
 */

#ifndef _WIN32

#include <pthread.h>

DWORD TlsAlloc(void)
{
	pthread_key_t key = 0;

	if (pthread_key_create(&key, nullptr) != 0)
		return TLS_OUT_OF_INDEXES;
	/* pthread_key_t is a signed int on some libc (e.g. Bionic); WINPR_ASSERTING_INT_CAST
	 * aborts the process if casting to DWORD (unsigned) would change sign/value, which we've
	 * observed pthread_key_create() actually returning on some Android versions. Treat that
	 * the same as "out of indexes" instead of crashing - matches the existing overflow check
	 * below in spirit. */
	if ((key < 0) || ((uintmax_t)key > UINT32_MAX))
		return TLS_OUT_OF_INDEXES;
	return (DWORD)key;
}

LPVOID TlsGetValue(DWORD dwTlsIndex)
{
	pthread_key_t key = (pthread_key_t)dwTlsIndex;
	return (LPVOID)pthread_getspecific(key);
}

BOOL TlsSetValue(DWORD dwTlsIndex, LPVOID lpTlsValue)
{
	pthread_key_t key = (pthread_key_t)dwTlsIndex;
	pthread_setspecific(key, lpTlsValue);
	return TRUE;
}

BOOL TlsFree(DWORD dwTlsIndex)
{
	pthread_key_t key = (pthread_key_t)dwTlsIndex;
	pthread_key_delete(key);
	return TRUE;
}

#endif
