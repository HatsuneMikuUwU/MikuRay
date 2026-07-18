package com.v2ray.ang.ui.server.fields

import android.app.Activity
import android.widget.EditText
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.Utils

class AddressPortFields(activity: Activity) {

    private val etRemarks: EditText = activity.findViewById(R.id.et_remarks)
    private val etAddress: EditText = activity.findViewById(R.id.et_address)
    private val etPort: EditText = activity.findViewById(R.id.et_port)

    val remarksText: String get() = etRemarks.text.toString()
    val addressText: String get() = etAddress.text.toString()
    val portText: String get() = etPort.text.toString()

    fun bind(config: ProfileItem) {
        etRemarks.text = Utils.getEditable(config.remarks)
        etAddress.text = Utils.getEditable(config.server.orEmpty())
        etPort.text = Utils.getEditable(config.serverPort ?: DEFAULT_PORT.toString())
    }

    fun clear() {
        etRemarks.text = null
        etAddress.text = null
        etPort.text = Utils.getEditable(DEFAULT_PORT.toString())
    }

    fun save(config: ProfileItem) {
        config.remarks = remarksText.trim()
        config.server = addressText.trim()
        config.serverPort = portText.trim()
    }
}
