package com.hypermarker

import android.app.Application
import com.hypermarker.storage.LibraryRepository

class HyperMarkerApplication : Application() {
    val repository by lazy { LibraryRepository(this) }
}
