package com.majyd.casa.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class CasaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, com.majyd.casa.CasaApplication::class.java.name, context)
    }
}

