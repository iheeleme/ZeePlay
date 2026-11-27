package com.shilapi.xcertplay

import android.content.Context

/** The Zeekr 001 shows CarPlay calls on the instrument cluster through the ECARX adaptapi. */
internal fun installClusterCall(context: Context) = ZeekrClusterCall.install(context)
