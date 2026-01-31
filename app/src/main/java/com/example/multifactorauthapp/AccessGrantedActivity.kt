package com.example.multifactorauthapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AccessGrantedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_access_granted)
    }
    override fun onStop() {
        super.onStop()
        // This closes the activity so it won't be there when the user returns
        finish()
    }
}
