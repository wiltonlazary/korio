package com.soywiz.korio.net.http

import com.soywiz.klock.DateFormat

//Sun, 06 Nov 1994 08:49:37 GMT  ; RFC 822, updated by RFC 1123
//Sunday, 06-Nov-94 08:49:37 GMT ; RFC 850, obsoleted by RFC 1036
//Sun Nov  6 08:49:37 1994       ; ANSI C's asctime() format
val HttpDate = DateFormat("EEE, dd MMM yyyy HH:mm:ss z")