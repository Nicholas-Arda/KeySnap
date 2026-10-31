package com.example.data

import android.content.res.Resources
import com.example.bridge.ConstraintNegation
import io.github.nicholasarda.keysnap.R

/** No Compose or device reads: the designer can use resources.constraintLabel(constraint). */
fun Resources.constraintLabel(constraint: ScriptConstraint): String {
    val title = ScriptConstraintCatalog.descriptor(constraint.type)?.let { getString(it.titleRes) } ?: constraint.type
    return constraintLabel(constraint, title, getString(R.string.constraint_not_prefix))
}

/** Pure formatter; labels imported unsupported negation honestly, even though evaluation fails closed. */
fun constraintLabel(constraint: ScriptConstraint, title: String, notPrefix: String): String =
    if (ConstraintNegation.requested(constraint.params)) "$notPrefix: $title" else title
