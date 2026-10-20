package com.mistick.launcher

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Hosts [AppSearchFragment] (search bar over installed apps). */
class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, AppSearchFragment())
                .commit()
        }
    }
}
