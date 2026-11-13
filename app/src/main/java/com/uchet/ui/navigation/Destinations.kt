package com.uchet.ui.navigation

object Destinations {
    const val MEASURE = "measure"
    const val STATISTICS = "statistics"
    const val BHA = "bha"
    const val SPO_PIPES = "spo_pipes"
    const val SETTINGS = "settings"
    const val SELECT_SPO = "select_spo"
    const val EDIT_RUN = "edit_run/{runId}"

    fun editRun(runId: Long) = "edit_run/$runId"
}
