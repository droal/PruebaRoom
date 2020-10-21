/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    /**Coroutines */

    //Job manage all coroutines.
    private var viewModelJob = Job()

    //Definir scope para las corrutinas.Determina en cual hilo se ejecutará la corrutina y el Job asociado
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    //Crear variable LiveData y usar corrutinas para inicializarla con datos de la BD
    private var tonight = MutableLiveData<SleepNight?>()

    //Obtener todos los registros de la BD, esto no requiere corrutinas dado que la BD retorna on LiveData
    private val nights = database.getAllNights()
    val nightsString = Transformations.map(nights){ nights ->
        formatNights(nights, application.resources)
    }


    //Variables para controlar el estado(visibilidad) de los botones usando Transformation map
    val startButtonVisible = Transformations.map(tonight){
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight){
        null != it
    }
    val clearButtonVisible = Transformations.map(nights){
        it?.isNotEmpty()
    }


    //Livedata para el evento de navegación, además permite pasar el objeto actual
    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    //Livedata para visualizar snackbar
    private val _showSnackbarEvent = MutableLiveData<Boolean>()
    val showSnackbarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent
    fun doneShowingSnackbar(){
        _showSnackbarEvent.value = false
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        //Usar corrutinas para obtener datos de la BD
        uiScope.launch {
            //Implementar una funcion con la anotacion "suspend" para obtener datos
            tonight.value = getTonightFromDB()
        }
    }

    private suspend fun getTonightFromDB(): SleepNight? {
        //Usar una nueva corrutina con contexto IO
        return withContext(Dispatchers.IO){
            var night = database.getTonight()
            if(night?.endTimeMilli != night?.startTimeMilli){
                night = null
            }
            night
        }
    }


    /**Buttons events*/

    //START BUTTON
    fun onStartTracking(){
        //Emeplar una corrutina para insertar un nuevo registro en la BD
        uiScope.launch {
            val newNight = SleepNight()
            //Implementar una suspend function
            insert(newNight)
            tonight.value = getTonightFromDB()
        }
    }

    private suspend fun insert(night: SleepNight){
        withContext(Dispatchers.IO){
            database.insert(night)
        }
    }

    //STOP BUTTON
    fun onStopTracking(){
        //Emeplar una corrutina para actualizar un registro en la BD
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)

            //Trigger navigation
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(night: SleepNight){
        withContext(Dispatchers.IO){
            database.update(night)
        }
    }

    //CLEAR BUTTON
    fun onClear(){
        //Emeplar una corrutina para limpiar la BD
        uiScope.launch {
            clear()
            tonight.value = null

            //trigger show snackbar
            _showSnackbarEvent.value = true
        }
    }

    private suspend fun clear(){
        withContext(Dispatchers.IO){
            database.clear()
        }
    }


    //Reset navigation variable para indicar al viewmodel que el evento ya ocurrió
    fun doneNavigating(){
        _navigateToSleepQuality.value = null
    }

    //Canceling coroutines
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}

