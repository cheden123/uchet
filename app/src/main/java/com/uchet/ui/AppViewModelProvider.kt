package com.uchet.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uchet.UchetApp
import com.uchet.ui.bha.BhaViewModel
import com.uchet.ui.editrun.EditRunViewModel
import com.uchet.ui.measure.MeasureViewModel
import com.uchet.ui.settings.SettingsViewModel
import com.uchet.ui.spopipes.SpoPipesViewModel
import com.uchet.ui.sposelect.SpoSelectViewModel
import com.uchet.ui.statistics.StatisticsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(app().container.repository) }
        initializer { MeasureViewModel(app().container.repository) }
        initializer { SpoSelectViewModel(app().container.repository) }
        initializer { StatisticsViewModel(app().container.repository) }
        initializer { SettingsViewModel(app().container.repository) }
        initializer { BhaViewModel(app().container.repository) }
        initializer { SpoPipesViewModel(app().container.repository) }
        initializer { EditRunViewModel(app().container.repository) }
    }
}

fun CreationExtras.app(): UchetApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UchetApp)
