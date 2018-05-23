package com.github.jw3.geoevents

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
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


class MainActivity : AppCompatActivity() {
    private val Location_Permission_Request = 111;

    private val tracks = mutableMapOf<String, PointCollection>()
    private val locationGraphics = mutableMapOf<String, Graphic>()
    private val trackingGraphics = mutableMapOf<String, Graphic>()

    private val trackMarker = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0x01110, 5f)
    private val locationMarker = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, -0x10000, 10f)

    private var locationDisplay: LocationDisplay? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            Location_Permission_Request ->
                if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                    locationDisplay?.let { ld ->
                        ld.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
                        if (!ld.isStarted) ld.startAsync()

                        ld.addLocationChangedListener { e ->
                            val acc = e.location.horizontalAccuracy
                            accView.text = "${acc}m"

                            val deviceId = deviceId()
                            tracks[deviceId]?.let { ptc ->
                                ptc.add(e.location.position)
                                trackingGraphics[deviceId]?.let { g ->
                                    g.geometry = Polyline(ptc)
                                }
                            }
                        }
                    }
                }else {
                    accView.text = "viewer mode"
                    newTrack.isEnabled = false
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        val map = ArcGISMap(Basemap.Type.IMAGERY, 34.056295, -117.195800, 16)
        mapView.map = map
        locationDisplay = mapView.locationDisplay

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                + ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), Location_Permission_Request)
        }

        val locationsLayer = GraphicsOverlay()

        val activeTrackLayer = GraphicsOverlay()
        activeTrackLayer.opacity = 0.25f

        val pastTracksLayer = GraphicsOverlay()
        pastTracksLayer.opacity = 0.25f

        mapView.graphicsOverlays.addAll(listOf(locationsLayer, activeTrackLayer, pastTracksLayer))

        val deviceId = deviceId()
        val serverUrl = serverUrl()

        client().newWebSocket(wsreq(serverUrl), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket?, text: String?) {
                text?.let { encoded ->
                    val split = encoded.split(":")
                    val id = split[0]
                    val x = split[1].toDouble()
                    val y = split[2].toDouble()
                    val pt = Point(x, y, SpatialReferences.getWgs84())

                    // api24; val g = locationGraphics.computeIfAbsent(id, { str -> Graphic(Point(0.0, 0.0)) })
                    if (!locationGraphics.containsKey(id)) {
                        val g = Graphic(pt, locationMarker)
                        locationGraphics.put(id, g)
                        locationsLayer.graphics.add(g)
                    }
                    locationGraphics[id]?.let { g ->
                        g.geometry = pt
                    }
                }
            }
        })

        drawTracks.setOnCheckedChangeListener { _, v ->
            pastTracksLayer.isVisible = v
        }

        locationDisplay?.let { ld ->
            newTrack.setOnCheckedChangeListener { _, v ->
                if (v) {
                    // http call to new track endpoint
                    client().newCall(track("start", serverUrl))
                    val pt = ld.location.position

                    val ptc = PointCollection(listOf(pt), SpatialReferences.getWgs84())
                    tracks.put(deviceId, ptc)

                    val t = Graphic(Polyline(ptc), trackMarker)
                    trackingGraphics.put(deviceId, t)
                    activeTrackLayer.graphics.add(t)
                } else {
                    // http call to complete track endpoint
                    client().newCall(track("stop", serverUrl))

                    tracks.remove(deviceId)
                    val g = trackingGraphics[deviceId]
                    activeTrackLayer.graphics.remove(g)
                    pastTracksLayer.graphics.add(g)
                }
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

    private fun track(op: String, url: String): Request {
        val deviceId = deviceId()
        return Request.Builder()
                .post(RequestBody.create(null, ""))
                .url("ws://$url/api/device/$deviceId/track/$op").build()
    }

    private fun client(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    private fun wsreq(url: String): Request {
        return Request.Builder()
                .get().url("ws://$url/api/watch/device").build()
    }

    private fun prefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    private fun deviceId(): String {
        return prefs().getString("preference_device_name", "unknown")
    }

    private fun serverUrl(): String {
        return prefs().getString("preference_server_url", "unknown")
    }
}
