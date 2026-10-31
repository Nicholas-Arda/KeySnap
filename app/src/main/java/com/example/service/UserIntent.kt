package com.example.service

import android.content.Intent
import android.net.Uri
import com.example.bridge.ActionPayloads

/** Uses the same validation and literal argument values as the bridge command builder. */
internal fun userIntent(params: Map<String, String>): Intent {
    ActionPayloads.intentCommand(params)
    return Intent(params.getValue("action")).apply {
        params["uri"]?.takeIf { it.isNotEmpty() }?.let { data = Uri.parse(it) }
        params["package"]?.takeIf { it.isNotEmpty() }?.let { setPackage(it) }
        ActionPayloads.intentExtras(params["extras"]).forEach { putExtra(it.key, it.value) }
        if (ActionPayloads.isActivity(params)) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
