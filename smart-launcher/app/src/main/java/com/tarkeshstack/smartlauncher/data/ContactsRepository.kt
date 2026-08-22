package com.tarkeshstack.smartlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a spoken/typed name like "mom" to a phone number, so commands such as
 * "call mom" or "message mom" work without the user typing digits.
 *
 * Only used when READ_CONTACTS has been granted at runtime; callers must check
 * [hasPermission] first and fall back to treating the text as a raw phone number.
 */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun findPhoneNumberByName(name: String): String? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (cursor.moveToFirst() && numberIndex >= 0) {
                return@withContext cursor.getString(numberIndex)
            }
        }
        null
    }
}
