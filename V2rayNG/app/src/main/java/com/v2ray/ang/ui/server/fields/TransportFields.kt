package com.v2ray.ang.ui.server.fields

import android.app.Activity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils

class TransportFields(private val activity: Activity) {

    private val networks: Array<out String> = activity.resources.getStringArray(R.array.networks)
    private val tcpTypes: Array<out String> = activity.resources.getStringArray(R.array.header_type_tcp)
    private val kcpAndQuicTypes: Array<out String> = activity.resources.getStringArray(R.array.header_type_kcp_and_quic)
    private val grpcModes: Array<out String> = activity.resources.getStringArray(R.array.mode_type_grpc)
    private val xhttpMode: Array<out String> = activity.resources.getStringArray(R.array.xhttp_mode)
    private val browserDialerModes: Array<out String> = activity.resources.getStringArray(R.array.browser_dialer_mode)

    private val spNetwork: AutoCompleteTextView? = activity.findViewById(R.id.sp_network)
    private val spHeaderType: AutoCompleteTextView? = activity.findViewById(R.id.sp_header_type)
    private val tilHeaderType: TextInputLayout? = activity.findViewById(R.id.til_header_type)
    private val tilRequestHost: TextInputLayout? = activity.findViewById(R.id.til_request_host)
    private val tilPath: TextInputLayout? = activity.findViewById(R.id.til_path)
    private val etRequestHost: EditText? = activity.findViewById(R.id.et_request_host)
    private val etPath: EditText? = activity.findViewById(R.id.et_path)
    private val layoutKcp: View? = activity.findViewById(R.id.layout_kcp)
    private val etKcpMtu: EditText? = activity.findViewById(R.id.et_kcp_mtu)
    private val etKcpTti: EditText? = activity.findViewById(R.id.et_kcp_tti)
    private val layoutExtra: View? = activity.findViewById(R.id.layout_extra)
    private val etExtra: EditText? = activity.findViewById(R.id.et_extra)
    private val etFm: EditText? = activity.findViewById(R.id.et_fm)
    private val layoutBrowserDialer: TextInputLayout? = activity.findViewById(R.id.layout_browser_dialer)
    private val spBrowserDialerMode: AutoCompleteTextView? = activity.findViewById(R.id.sp_browser_dialer_mode)

    val extraText: String? get() = etExtra?.text?.toString()
    val finalMaskText: String? get() = etFm?.text?.toString()

    /** Notifies the caller whenever the user picks a different network type. */
    fun setOnNetworkChanged(onChanged: (network: String) -> Unit) {
        spNetwork?.setOnItemClickListener { parent, _, position, _ ->
            onChanged(parent.getItemAtPosition(position).toString())
        }
    }

    private fun transportTypes(network: String?): Array<out String> = when (network) {
        NetworkType.TCP.type -> tcpTypes
        NetworkType.KCP.type -> kcpAndQuicTypes
        NetworkType.GRPC.type -> grpcModes
        NetworkType.XHTTP.type -> xhttpMode
        else -> arrayOf("---")
    }

    /** Refreshes header-type options, hints, and per-network visibility. */
    fun updateForNetwork(network: String, config: ProfileItem?) {
        val types = transportTypes(network)
        spHeaderType?.isEnabled = types.size > 1

        val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, types)
        spHeaderType?.setAdapter(adapter)

        tilHeaderType?.hint = when (network) {
            NetworkType.GRPC.type -> activity.getString(R.string.server_lab_mode_type)
            NetworkType.XHTTP.type -> activity.getString(R.string.server_lab_xhttp_mode)
            else -> activity.getString(R.string.server_lab_head_type)
        }

        val headerTypeStr = when (network) {
            NetworkType.GRPC.type -> config?.mode
            NetworkType.XHTTP.type -> config?.xhttpMode
            else -> config?.headerType
        }.orEmpty()

        val typePos = Utils.arrayFind(types, headerTypeStr)
        val finalTypeIndex = if (typePos >= 0) typePos else 0
        spHeaderType?.setText(if (types.isNotEmpty()) types[finalTypeIndex] else "", false)

        etRequestHost?.text = Utils.getEditable(
            when (network) {
                NetworkType.GRPC.type -> config?.authority
                else -> config?.host
            }.orEmpty()
        )
        etPath?.text = Utils.getEditable(
            when (network) {
                NetworkType.KCP.type -> config?.seed
                NetworkType.GRPC.type -> config?.serviceName
                else -> config?.path
            }.orEmpty()
        )

        tilRequestHost?.hint = activity.getString(
            when (network) {
                NetworkType.TCP.type -> R.string.server_lab_request_host_http
                NetworkType.WS.type -> R.string.server_lab_request_host_ws
                NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_request_host_httpupgrade
                NetworkType.XHTTP.type -> R.string.server_lab_request_host_xhttp
                NetworkType.H2.type -> R.string.server_lab_request_host_h2
                NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                else -> R.string.server_lab_request_host
            }
        )

        tilPath?.hint = activity.getString(
            when (network) {
                NetworkType.KCP.type -> R.string.server_lab_path_kcp
                NetworkType.WS.type -> R.string.server_lab_path_ws
                NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                NetworkType.H2.type -> R.string.server_lab_path_h2
                NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                else -> R.string.server_lab_path
            }
        )

        etExtra?.text = Utils.getEditable(
            when (network) {
                NetworkType.XHTTP.type -> config?.xhttpExtra
                else -> null
            }.orEmpty()
        )
        etFm?.text = Utils.getEditable(config?.finalMask)

        layoutKcp?.visibility = if (network == NetworkType.KCP.type) View.VISIBLE else View.GONE
        etKcpMtu?.text = Utils.getEditable(config?.kcpMtu?.toString().orEmpty())
        etKcpTti?.text = Utils.getEditable(config?.kcpTti?.toString().orEmpty())

        layoutExtra?.visibility = if (network == NetworkType.XHTTP.type) View.VISIBLE else View.GONE

        layoutBrowserDialer?.visibility = when (network) {
            NetworkType.WS.type, NetworkType.XHTTP.type -> View.VISIBLE
            else -> View.GONE
        }
    }

    fun bind(config: ProfileItem) {
        val network = Utils.arrayFind(networks, config.network.orEmpty())
        if (network >= 0) {
            spNetwork?.setText(networks[network], false)
            updateForNetwork(networks[network], config)
        } else {
            updateForNetwork("", config)
        }

        val browserDialerMode = Utils.arrayFind(browserDialerModes, config.browserDialerMode.orEmpty())
        if (browserDialerMode >= 0) {
            spBrowserDialerMode?.setText(browserDialerModes[browserDialerMode], false)
        }
    }

    fun clear() {
        if (networks.isNotEmpty()) {
            spNetwork?.setText(networks[0], false)
            updateForNetwork(networks[0], null)
        }
        if (browserDialerModes.isNotEmpty()) {
            spBrowserDialerMode?.setText(browserDialerModes[0], false)
        }
    }

    /** Returns false (no-op) when this section isn't present in the current layout. */
    fun save(profileItem: ProfileItem): Boolean {
        val networkPos = Utils.arrayFind(networks, spNetwork?.text.toString())
        if (networkPos < 0) return false

        val types = transportTypes(networks[networkPos])
        val typePos = Utils.arrayFind(types, spHeaderType?.text.toString())
        if (typePos < 0) return false

        val requestHost = etRequestHost?.text?.toString()?.trim() ?: return false
        val path = etPath?.text?.toString()?.trim() ?: return false

        profileItem.network = networks[networkPos]
        profileItem.headerType = types[typePos]
        profileItem.host = requestHost
        profileItem.path = path
        profileItem.seed = path
        profileItem.quicSecurity = requestHost
        profileItem.quicKey = path
        profileItem.mode = types[typePos]
        profileItem.serviceName = path
        profileItem.authority = requestHost
        profileItem.xhttpMode = types[typePos]
        profileItem.xhttpExtra = extraText?.trim().nullIfBlank()
        profileItem.finalMask = finalMaskText?.trim()?.nullIfBlank()
        profileItem.kcpMtu = etKcpMtu?.text?.toString()?.toIntOrNull()
        profileItem.kcpTti = etKcpTti?.text?.toString()?.toIntOrNull()

        val browserDialerMode = spBrowserDialerMode?.text?.toString() ?: browserDialerModes.firstOrNull().orEmpty()
        val defaultDialerMode = browserDialerModes.firstOrNull().orEmpty()
        profileItem.browserDialerMode =
            if ((networks[networkPos] == NetworkType.WS.type || networks[networkPos] == NetworkType.XHTTP.type)
                && browserDialerMode != defaultDialerMode
            ) {
                browserDialerMode
            } else {
                null
            }

        return true
    }
}
