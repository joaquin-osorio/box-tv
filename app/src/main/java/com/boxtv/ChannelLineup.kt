package com.boxtv

import com.boxtv.source.Channel

/** The channels offered in the menu, in display order. The first one plays on launch. All are tvf90 ids. */
val ChannelLineup = listOf(
    Channel(id = "espn", title = "ESPN"),
    Channel(id = "dsports", title = "DSports"),
    Channel(id = "foxsports", title = "Fox Sports"),
    Channel(id = "tntsports", title = "TNT Sports"),
    Channel(id = "espnpremium", title = "ESPN Premium"),
    Channel(id = "tycsports", title = "TyC Sports")
)
