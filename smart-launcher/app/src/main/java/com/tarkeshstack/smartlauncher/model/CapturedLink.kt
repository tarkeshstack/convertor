package com.tarkeshstack.smartlauncher.model

/** A link handed to us by another app's Share sheet, before the user turns it into a command. */
data class CapturedLink(val uri: String, val sourcePackage: String?)
