package com.immortalworld

import android.app.Application
import androidx.room.Room
import com.immortalworld.data.*

class ImmortalWorldApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, GameDatabase::class.java, "immortal-world.db").build() }
    val saves by lazy { SaveRepository(database) }
    val preferences by lazy { Preferences(this) }
}
