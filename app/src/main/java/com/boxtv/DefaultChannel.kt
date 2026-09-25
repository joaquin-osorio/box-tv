package com.boxtv

import com.boxtv.source.Channel

/** MVP: the only channel the app plays, straight on launch. Resolved through the tvf90 adapter. */
val DefaultChannel = Channel(id = "dsports", title = "DSports")
