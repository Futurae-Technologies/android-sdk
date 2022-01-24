package com.futurae.futuraedemo

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.*
import kotlin.random.Random

class CustomBackupAgent : BackupAgent() {

    private val TAG = CustomBackupAgent::class.simpleName
    private val PREFS_BACKUP_KEY = "prefs_shared_prefs_backup_key"

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor?) {
        try {

            //call SDK's backup agent
            com.futurae.sdk.BackupAgent.onBackupAccounts(this, oldState, data, newState)

            //back up your own data
            val sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val randomInt = Random.nextInt(10)
            sp.edit().putInt(PREF_RANDOM_STRING, randomInt).apply()

            val preferenceValue = randomInt.toString()
            Timber.d("Starting backup of the prefs configuration in $PREFS_BACKUP_KEY, store: $randomInt as $preferenceValue")

            val bufStream = ByteArrayOutputStream()
            val outWriter = DataOutputStream(bufStream)
            outWriter.writeUTF(preferenceValue)
            val buffer = bufStream.toByteArray()
            val len = buffer.size
            data.writeEntityHeader(PREFS_BACKUP_KEY, len)
            data.writeEntityData(buffer, len)
        } catch (e: IOException) {
            Timber.e("")
            throw e
        }
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        while (data.readNextHeader()) {
            //if it's your backup key, proceed with restoring backed-up values
            if (PREFS_BACKUP_KEY == data.key) {
                try {
                    val dataSize = data.dataSize
                    val dataBuf = ByteArray(dataSize)
                    data.readEntityData(dataBuf, 0, dataSize)
                    val baStream = ByteArrayInputStream(dataBuf)
                    val `in` = DataInputStream(baStream)
                    val storedValue = `in`.readUTF()
                    if (storedValue.isBlank()) {
                        return
                    }
                    Timber.d("onRestore: Prefs restore from the backup completed. Value: $storedValue, calling SDK backup agent")
                    val sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    sp.edit().putInt(PREF_RANDOM_STRING, storedValue.toInt()).apply()
                } catch (e: IOException) {
                    Timber.e(e)
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                }
            } else {
                com.futurae.sdk.BackupAgent.onRestoreAccountsData(this, data)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "PREFS_NAME_CUSTOM"
        const val PREF_RANDOM_STRING = "PREF_RANDOM_STRING"
    }
}