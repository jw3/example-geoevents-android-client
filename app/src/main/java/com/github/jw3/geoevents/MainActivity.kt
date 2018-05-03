package com.github.jw3.geoevents

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.location.AndroidLocationDataSource
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private val graphics = mutableMapOf<String, Graphic>()

    private fun client(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    private fun wsreq(): Request {
        return Request.Builder()
                .get().url("ws://10.0.2.2:9000/api/watch/device").build()
    }

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

        val graphicsLayer = GraphicsOverlay()
        mapView.graphicsOverlays.add(graphicsLayer)

        val othersMarker = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, -0x10000, 10f)

        client().newWebSocket(wsreq(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket?, text: String?) {
                text?.let { encoded ->
                    val split = encoded.split(":")
                    val id = split[0]
                    val x = split[1].toDouble()
                    val y = split[2].toDouble()
                    // api24; val g = graphics.computeIfAbsent(id, { str -> Graphic(Point(0.0, 0.0)) })
                    if (!graphics.containsKey(id)) {
                        val g = Graphic(Point(x, y, SpatialReferences.getWgs84()), othersMarker)
                        graphics.put(id, g)
                        graphicsLayer.graphics.add(g)
                    }
                    graphics[id]?.let { g ->
                        g.geometry = Point(x, y, SpatialReferences.getWgs84())
                    }
                }
            }
        })
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
