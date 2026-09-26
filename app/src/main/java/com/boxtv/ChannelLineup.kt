package com.boxtv

import com.boxtv.source.Channel
import com.boxtv.source.tvf90.Tvf90Source

/** The fixed channels of the menu's Channels tab, in display order. All are tvf90 ids. */
val ChannelLineup = listOf(
    Channel(source = Tvf90Source.ID, id = "espn", title = "ESPN"),
    Channel(source = Tvf90Source.ID, id = "dsports", title = "DSports"),
    Channel(source = Tvf90Source.ID, id = "foxsports", title = "Fox Sports"),
    Channel(source = Tvf90Source.ID, id = "tntsports", title = "TNT Sports"),
    Channel(source = Tvf90Source.ID, id = "espnpremium", title = "ESPN Premium"),
    Channel(source = Tvf90Source.ID, id = "tycsports", title = "TyC Sports")
)
