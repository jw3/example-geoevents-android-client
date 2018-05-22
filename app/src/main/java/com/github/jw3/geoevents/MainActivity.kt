package com.github.jw3.geoevents

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import android.content.Intent


class MainActivity : AppCompatActivity() {
    private val points = mutableMapOf<String, Point>()
    private val tracks = mutableMapOf<String, PointCollection>()
    private val locationGraphics = mutableMapOf<String, Graphic>()
    private val trackingGraphics = mutableMapOf<String, Graphic>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        val map = ArcGISMap(Basemap.Type.IMAGERY, 34.056295, -117.195800, 16)

        mapView.map = map
        val ld = mapView.locationDisplay

        ld.addLocationChangedListener { e ->
            val acc = e.location.horizontalAccuracy
            val lon = e.location.position.x
            val lat = e.location.position.y
            Toast.makeText(this@MainActivity, "$lon:$lat to $acc units", Toast.LENGTH_SHORT).show()
        }

        ld.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
        if (!ld.isStarted)
            ld.startAsync()

        val locationsLayer = GraphicsOverlay()
        val tracksLayer = GraphicsOverlay()
        tracksLayer.opacity = 0.25f
        val myTrackLayer = GraphicsOverlay()
        myTrackLayer.opacity = 0.25f

        mapView.graphicsOverlays.addAll(
                listOf(locationsLayer, myTrackLayer, tracksLayer)
        )

        val locationMarker = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, -0x10000, 10f)
        val othersTrackMarker = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0x10000, 5f)
        val myTrackMarker = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0x01110, 5f)

        client().newWebSocket(wsreq(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket?, text: String?) {
                text?.let { encoded ->
                    val split = encoded.split(":")
                    val id = split[0]
                    val x = split[1].toDouble()
                    val y = split[2].toDouble()
                    val pt = Point(x, y, SpatialReferences.getWgs84())

                    // api24; val g = locationGraphics.computeIfAbsent(id, { str -> Graphic(Point(0.0, 0.0)) })
                    if (!locationGraphics.containsKey(id)) {
                        points.put(id, pt)

                        val g = Graphic(pt, locationMarker)
                        locationGraphics.put(id, g)
                        locationsLayer.graphics.add(g)

                        val ptc = PointCollection(listOf(pt), SpatialReferences.getWgs84())
                        tracks.put(id, ptc)

                        val t = Graphic(Polyline(ptc), othersTrackMarker)
                        trackingGraphics.put(id, t)
                        tracksLayer.graphics.add(t)
                    }
                    locationGraphics[id]?.let { g ->
                        g.geometry = pt
                    }
                    tracks[id]?.let { ptc ->
                        ptc.add(pt)

                        trackingGraphics[id]?.let { g ->
                            g.geometry = Polyline(ptc)
                        }
                    }
                }
            }
        })

        drawTracks.setOnCheckedChangeListener { _, v ->
            tracksLayer.isVisible = v
        }

        newTrack.setOnCheckedChangeListener { _, v ->
            if (v) {
                // http call to new track endpoint
                client().newCall(track("start"))
                val pt = Point(9.0, 9.0)

                val ptc = PointCollection(listOf(pt), SpatialReferences.getWgs84())
                tracks.put("me", ptc)

                val t = Graphic(Polyline(ptc), myTrackMarker)

                trackingGraphics.put("me", t)
                myTrackLayer.graphics.add(t)
            } else {
                // http call to complete track endpoint
                client().newCall(track("stop"))
                // move tracking graphic from the new-track layer to the tracking layer
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun track(op: String): Request {
        return Request.Builder()
                .post(RequestBody.create(null, ""))
                .url("ws://10.0.2.2:9000/api/device/default/track/$op").build()
    }

    private fun client(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    private fun wsreq(): Request {
        return Request.Builder()
                .get().url("ws://10.0.2.2:9000/api/watch/device").build()
    }
}
