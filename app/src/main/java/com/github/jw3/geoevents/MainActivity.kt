package com.github.jw3.geoevents

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.esri.arcgisruntime.location.AndroidLocationDataSource
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.github.bobby.rxwsocket.RxWSEvent
import com.github.bobby.rxwsocket.RxWSocket
import io.reactivex.BackpressureStrategy
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request


class MainActivity : AppCompatActivity() {
    val request = Request.Builder()
            .get()
            .url("ws://10.0.2.2:9000/api/watch/device")
            .build()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val map = ArcGISMap(Basemap.Type.IMAGERY, 34.056295, -117.195800, 16)

        mapView.map = map

        val ld = mapView.locationDisplay

        val ds = AndroidLocationDataSource(applicationContext)



        ld.addLocationChangedListener { e ->
            val acc = e.location.horizontalAccuracy
            val lon = e.location.position.x
            val lat = e.location.position.y
            Toast.makeText(this@MainActivity, "$lon:$lat to $acc units", Toast.LENGTH_LONG).show()
        }

        ld.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
        if (!ld.isStarted)
            ld.startAsync()

        RxWSocket(OkHttpClient(), request)
                .webSocketFlowable(BackpressureStrategy.BUFFER)
                .subscribe {
                    when (it) {
                        is RxWSEvent.OpenEvent -> println("Opened Flowable")
                        is RxWSEvent.MessageStringEvent -> println("Receive Message String: " + it.text)
                        is RxWSEvent.MessageByteEvent -> println("Receive Message Byte: " + it.bytes)
                        is RxWSEvent.ClosingEvent -> println("Closing")
                        is RxWSEvent.FailureEvent -> println("Failure")
                        is RxWSEvent.ClosedEvent -> println("Closed")
                    }
                }
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }
}
