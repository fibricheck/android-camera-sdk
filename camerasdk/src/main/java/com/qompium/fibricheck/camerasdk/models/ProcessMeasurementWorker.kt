package com.qompium.fibricheck.camerasdk.models

import com.qompium.fibricheck.camerasdk.listeners.SensorListener.SENSOR_LISTENER_DATA_ACC
import com.qompium.fibricheck.camerasdk.listeners.SensorListener.SENSOR_LISTENER_DATA_GRAV
import com.qompium.fibricheck.camerasdk.listeners.SensorListener.SENSOR_LISTENER_DATA_GYRO
import com.qompium.fibricheck.camerasdk.listeners.SensorListener.SENSOR_LISTENER_DATA_ROTATION
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData
import com.qompium.fibricheck.camerasdk.measurement.MeasurementRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessMeasurementWorker() {
  var cancelled = false

  fun execute(
    measurementData: MeasurementData,
    measurementRawList: MutableList<MeasurementRaw>,
    addGyro: Boolean,
    addAcc: Boolean,
    addRotation: Boolean,
    addGrav: Boolean,
    onFinished: (data: MeasurementData) -> Unit,
    onCancelled: () -> Unit = {}
  ) {
    CoroutineScope(Dispatchers.Default).launch {
      val result = withContext(Dispatchers.IO) {
        process(
          measurementData,
          measurementRawList,
          addGyro,
          addAcc,
          addRotation,
          addGrav
        )
      }

      withContext(Dispatchers.Main) {
        if (cancelled)
          onCancelled()
        else
          onFinished(result)
      }
    }
  }
  private suspend fun process(
    measurementData: MeasurementData,
    measurementRawList: MutableList<MeasurementRaw>,
    addGyro: Boolean,
    addAcc: Boolean,
    addRotation: Boolean,
    addGrav: Boolean
  ): MeasurementData {
    measurementRawList.sort()
    for (it in measurementRawList) {
      if (cancelled) {
        break
      }

      val motionData = it.motionData

      measurementData.addQuadrant(it.quadrantData);
      if (addGyro) {
        measurementData.addGyro(motionData[SENSOR_LISTENER_DATA_GYRO]);
      }
      if (addAcc) {
        measurementData.addAcc(motionData[SENSOR_LISTENER_DATA_ACC]);
      }
      if (addRotation) {
        measurementData.addRotation(motionData[SENSOR_LISTENER_DATA_ROTATION]);
      }
      if (addGrav) {
        measurementData.addGrav(motionData[SENSOR_LISTENER_DATA_GRAV]);
      }
      measurementData.time.add(it.timestamp);
    }

    return measurementData
  }
}
