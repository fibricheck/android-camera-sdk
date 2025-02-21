package com.qompium.fibricheck.camerasdk.listeners

interface RawDataListener {
  fun onNewData(data: ByteArray, metadata: Map<String, String>);
}